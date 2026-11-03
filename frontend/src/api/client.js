import { USERNAME_RULES } from '../utils/username.js'
import { parseRetryAfterSeconds } from '../utils/loginRateLimit.js'
import { createBlobCache, createTaskQueue, createTimedCache, isTemporaryError, retryRead } from '../utils/resilience.js'

const BASE = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const KEY = 'hotel_booking_token'
const READ_CACHE_MS = { hotel: 5 * 60_000, room: 3 * 60_000, photoList: 10 * 60_000 }
const readCache = createTimedCache({ maxEntries: 180 })
const imageCache = createBlobCache({ maxBytes: 28 * 1024 * 1024, maxEntries: 90 })
const readQueue = createTaskQueue(6)
const photoQueue = createTaskQueue(4)
const pendingReads = new Map()
const latestReadStart = new Map()
let readSerial = 0
let generation = 0

/** Invalidate on logout, new JWT and successful mutations, never cache across accounts. */
export function clearClientCaches({ images = true } = {}) {
  generation++
  readCache.clear()
  pendingReads.clear()
  latestReadStart.clear()
  if (images) imageCache.clear()
}
export const tokenStorage = {
  get: () => sessionStorage.getItem(KEY),
  set: value => {
    if (sessionStorage.getItem(KEY) !== value) clearClientCaches()
    sessionStorage.setItem(KEY, value)
  },
  clear: () => { clearClientCaches(); sessionStorage.removeItem(KEY) },
}
export class ApiError extends Error {
  constructor(message, status, code, traceId, retryAfterSeconds = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
    this.retryAfterSeconds = retryAfterSeconds
  }
}
function errorText(data, response) {
  if (typeof data === 'string' && data.trim()) return data
  if (data?.code === 'VALIDATION_ERROR' && data?.fieldErrors?.username) return USERNAME_RULES
  const messages = { USERNAME_ALREADY_REGISTERED:'Этот логин уже занят. Выберите другой.', EMAIL_ALREADY_REGISTERED:'Этот email уже зарегистрирован.', PASSWORD_CHANGE_REQUIRED:'Для продолжения смените временный пароль.', REQUEST_IN_PROGRESS:'Бронирование уже обрабатывается. Проверьте историю заказов.', ROOM_ALREADY_BOOKED:'Этот номер уже занят на выбранные даты.', NO_AVAILABLE_ROOMS:'Нет свободных номеров на выбранные даты.', IDEMPOTENCY_KEY_CONFLICT:'Данные заказа изменились. Начните новое бронирование.', INVALID_CREDENTIALS:'Неверный email или пароль.', TOO_MANY_REQUESTS:'Слишком много запросов. Повторите попытку позже.' }
  return messages[data?.code] || data?.message || data?.detail || ({400:'Проверьте введённые данные.',401:'Для продолжения войдите в аккаунт.',403:'Недостаточно прав для этой операции.',404:'Данные не найдены.',409:'Конфликт данных. Обновите страницу и повторите действие.',429:'Слишком много запросов. Повторите попытку позже.',502:'Сервис временно недоступен.',503:'Сервис временно недоступен.'}[response.status]) || `Ошибка сервера (${response.status})`
}
function cacheLifetime(path) {
  // Availability, recommendations, user data, bookings and mutations are never cached.
  if (/^\/hotels(?:\?|$)/.test(path) || /^\/hotels\/\d+$/.test(path)) return READ_CACHE_MS.hotel
  if (/^\/rooms(?:\?|$)/.test(path) || /^\/rooms\/\d+$/.test(path)) return READ_CACHE_MS.room
  if (/^\/(?:hotels|rooms)\/\d+\/photos(?:\?|$)/.test(path)) return READ_CACHE_MS.photoList
  if (/^\/hotels\/\d+\/room-selection-strategy$/.test(path)) return READ_CACHE_MS.hotel
  return 0
}
async function fetchOnce(path, { method, body, auth, headers, signal, timeoutMs }) {
  const controller = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, timeoutMs)
  const abortFromParent = () => controller.abort()
  if (signal?.aborted) controller.abort()
  else signal?.addEventListener('abort', abortFromParent, { once: true })
  const token = tokenStorage.get()
  const h = { ...headers }
  if (auth && token) h.Authorization = `Bearer ${token}`
  if (body !== undefined && !(body instanceof FormData)) h['Content-Type'] = 'application/json'
  try {
    const response = await fetch(`${BASE}${path}`, { method, headers: h,
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal })
    const contentType = response.headers.get('content-type') || ''
    const data = response.status === 204 ? null : contentType.includes('application/json')
      ? await response.json().catch(() => null) : await response.text().catch(() => '')
    if (!response.ok) {
      if (response.status === 401 && auth && token) window.dispatchEvent(new Event('hotel:auth-expired'))
      if (response.status === 403 && data?.code === 'PASSWORD_CHANGE_REQUIRED' && auth && token)
        window.dispatchEvent(new Event('hotel:password-change-required'))
      throw new ApiError(errorText(data, response), response.status, data?.code,
        response.headers.get('X-Trace-Id') || data?.traceId,
        parseRetryAfterSeconds(response.headers.get('Retry-After')))
    }
    return data
  } catch (error) {
    if (error.name === 'AbortError') {
      if (signal?.aborted) throw error // Route changed; do not show an error or retry.
      throw new ApiError('Время ожидания запроса истекло.', 0, timedOut ? 'REQUEST_TIMEOUT' : 'REQUEST_ABORTED')
    }
    if (error instanceof TypeError) throw new ApiError('Нет соединения с API Gateway.', 0, 'NETWORK_ERROR')
    throw error
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', abortFromParent)
  }
}

export async function request(path, { method = 'GET', body, auth = true, headers = {}, signal,
  allowStaleOnError = false, onStale, forceRefresh = false } = {}) {
  const isRead = method.toUpperCase() === 'GET'
  const ttl = isRead ? cacheLifetime(path) : 0
  if (ttl && !forceRefresh) {
    const fresh = readCache.get(path)
    if (fresh !== undefined) return fresh
  }
  const epoch = generation
  const load = async () => {
    const serial = ++readSerial
    if (ttl) latestReadStart.set(path, serial)
    try {
      const execute = () => fetchOnce(path, { method, body, auth, headers, signal,
        timeoutMs: isRead ? 12_000 : 20_000 })
      // Writes are never retried: their result may have committed before the connection failed.
      const result = isRead
        ? await retryRead(() => readQueue.run(execute, signal), { signal })
        : await execute()
      if (isRead && ttl && epoch === generation && latestReadStart.get(path) === serial)
        readCache.put(path, result, ttl)
      if (!isRead) clearClientCaches({ images: /\/photos(?:\/|$)/.test(path) })
      return result
    } catch (error) {
      if (isRead && ttl && allowStaleOnError && isTemporaryError(error) && epoch === generation) {
        const snapshot = readCache.stale(path)
        if (snapshot !== undefined) { onStale?.(error); return snapshot }
      }
      throw error
    }
  }
  // Share identical reads without caller-owned cancellation. Signal-bearing requests
  // instead use real abort to avoid piling up obsolete filters/searches.
  if (!isRead || !ttl || signal || forceRefresh) return load()
  let current = pendingReads.get(path)
  if (!current) {
    current = load().finally(() => {
      if (pendingReads.get(path) === current) pendingReads.delete(path)
    })
    pendingReads.set(path, current)
  }
  return current
}

/** Bounded, coalesced, abort-aware image loader; components own/revoke their Blob URLs. */
export async function imageBlob(path, signal) {
  const epoch = generation
  return imageCache.get(`${epoch}:${path}`, async sharedSignal => {
    return retryRead(() => photoQueue.run(async () => {
      const controller = new AbortController()
      let timedOut = false
      const timer = setTimeout(() => { timedOut = true; controller.abort() }, 16_000)
      const abortShared = () => controller.abort()
      if (sharedSignal.aborted) controller.abort()
      else sharedSignal.addEventListener('abort', abortShared, { once: true })
      try {
        const token = tokenStorage.get()
        const response = await fetch(`${BASE}${path}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {}, signal: controller.signal })
        if (!response.ok) {
          if (response.status === 401 && token) window.dispatchEvent(new Event('hotel:auth-expired'))
          const data = await response.json().catch(() => null)
          if (response.status === 403 && data?.code === 'PASSWORD_CHANGE_REQUIRED' && token)
            window.dispatchEvent(new Event('hotel:password-change-required'))
          throw new ApiError(errorText(data, response), response.status, data?.code)
        }
        return await response.blob()
      } catch (error) {
        if (error.name === 'AbortError') {
          if (sharedSignal.aborted) throw error
          throw new ApiError('Время загрузки фотографии истекло.', 0,
            timedOut ? 'IMAGE_TIMEOUT' : 'IMAGE_ABORTED')
        }
        if (error instanceof TypeError) throw new ApiError('Не удалось загрузить фотографию.', 0, 'NETWORK_ERROR')
        throw error
      } finally {
        clearTimeout(timer)
        sharedSignal.removeEventListener('abort', abortShared)
      }
    }, sharedSignal), { signal: sharedSignal })
  }, signal)
}
// Verified against RoomPhotoController in the backend archive supplied 2026-10-31.
const roomPhotoPath = roomId => `/rooms/${encodeURIComponent(roomId)}/photos`

export const api = {
  login: data => request('/users/login', { method:'POST', body:data, auth:false }),
  register: data => request('/users/register', { method:'POST', body:data, auth:false }),
  // ADMIN users: contracts verified against Booking Service controllers / DTOs.
  adminUsers: (query, options) => request(`/users?${new URLSearchParams(query)}`, options),
  adminUser: id => request(`/users/${encodeURIComponent(id)}`),
  adminCreateUser: data => request('/users', {method:'POST', body:data}),
  adminUpdateUser: (id,data) => request(`/users/${encodeURIComponent(id)}`, {method:'PATCH', body:data}),
  adminDeleteUser: id => request(`/users/${encodeURIComponent(id)}`, {method:'DELETE'}),
  adminResetPassword: (id,newPassword) => request(`/users/${encodeURIComponent(id)}/reset-password`, {method:'POST', body:{newPassword}}),
  me: () => request('/users/me'),
  editMe: data => request('/users/me', { method:'PATCH', body:data }),
  changeMyPassword: data => request('/users/me/change-password', { method:'POST', body:data }),
  hotelsPage: (query, options) => request(`/hotels?${new URLSearchParams(query)}`, options),
  roomsPage: (query, options) => request(`/rooms?${new URLSearchParams(query)}`, options),
  room: (id, options) => request(`/rooms/${encodeURIComponent(id)}`, options),
  hotel: (id, options) => request(`/hotels/${id}`, options),
  createHotel: data => request('/hotels',{method:'POST',body:data}),
  editHotel: (id,data) => request(`/hotels/${id}/presentation`,{method:'PATCH',body:data}),
  recommend: (query, options) => request(`/rooms/recommend?${new URLSearchParams(query)}`, options),
  createRoom: data => request('/rooms',{method:'POST',body:data}),
  editRoom: (id,data) => request(`/rooms/${id}/details`,{method:'PATCH',body:data}),
  renameRoom: (id,number) => request(`/rooms/${id}`,{method:'PUT',body:{number}}),
  updateHotelIdentity: (id,name,address) => request(`/hotels/${id}`,{method:'PUT',body:{name,address}}),
  bookingsMy: (query, options) => request(`/bookings/my${query ? '?'+new URLSearchParams(query) : ''}`, options),
  booking: id => request(`/bookings/${encodeURIComponent(id)}`),
  bookingsAdmin: (query, options) => request(`/bookings${query ? '?'+new URLSearchParams(query) : ''}`, options),
  createBooking: (data,key) => request('/bookings',{method:'POST',body:data,headers:{'Idempotency-Key':key}}),
  cancelBooking: id => request(`/bookings/${id}/cancel`, {method:'PATCH'}),
  statistics: (id, options) => request(`/hotels/${id}/statistics`, options),
  // Hotel-wide configuration. An explicit strategy in an old client overrides this default.
  roomSelectionStrategy: id => request(`/hotels/${id}/room-selection-strategy`),
  setRoomSelectionStrategy: (id, strategy) => request(`/hotels/${id}/room-selection-strategy`, { method:'PUT', body:{strategy} }),
  // Room photographs; binary bytes always go through authenticated imageBlob().
  roomPhotos: (roomId, options) => request(roomPhotoPath(roomId), options),
  roomPhotoFilePath: (roomId, photoId) => `${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}/file`,
  roomPhotoUpload: (roomId, file) => { const form=new FormData(); form.append('file',file); return request(roomPhotoPath(roomId), {method:'POST',body:form}) },
  roomPhotoPrimary: (roomId, photoId) => request(`${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}/primary`, {method:'PATCH'}),
  roomPhotoOrder: (roomId, photoId, sortOrder) => request(`${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}/order?sortOrder=${encodeURIComponent(sortOrder)}`, {method:'PATCH'}),
  roomPhotoDelete: (roomId, photoId) => request(`${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}`, {method:'DELETE'}),
  photos: (id, options) => request(`/hotels/${id}/photos`, options),
  photoUpload: (id,file) => { const form=new FormData(); form.append('file',file); return request(`/hotels/${id}/photos`,{method:'POST',body:form}) },
  photoPrimary: (id,pid) => request(`/hotels/${id}/photos/${pid}/primary`,{method:'PATCH'}),
  photoDelete: (id,pid) => request(`/hotels/${id}/photos/${pid}`,{method:'DELETE'}),
}
