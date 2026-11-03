const BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/,'')
const KEY = 'hotel_booking_token'
export const tokenStorage = {
  get: () => sessionStorage.getItem(KEY),
  set: value => sessionStorage.setItem(KEY, value),
  clear: () => sessionStorage.removeItem(KEY),
}
export class ApiError extends Error {
  constructor(message, status, code, traceId) { super(message); this.name='ApiError'; this.status=status; this.code=code; this.traceId=traceId }
}
function errorText(data, response) {
  if (typeof data === 'string' && data.trim()) return data
  const messages = { PASSWORD_CHANGE_REQUIRED:'Для продолжения смените временный пароль.', REQUEST_IN_PROGRESS:'Бронирование уже обрабатывается. Проверьте историю заказов.', ROOM_ALREADY_BOOKED:'Этот номер уже занят на выбранные даты.', NO_AVAILABLE_ROOMS:'Нет свободных номеров на выбранные даты.', IDEMPOTENCY_KEY_CONFLICT:'Данные заказа изменились. Начните новое бронирование.', INVALID_CREDENTIALS:'Неверный email или пароль.' }
  return messages[data?.code] || data?.message || data?.detail || ({400:'Проверьте введённые данные.',401:'Для продолжения войдите в аккаунт.',403:'Недостаточно прав для этой операции.',404:'Данные не найдены.',409:'Конфликт данных. Обновите страницу и повторите действие.',502:'Сервис временно недоступен.',503:'Сервис временно недоступен.'}[response.status]) || `Ошибка сервера (${response.status})`
}
export async function request(path, { method='GET', body, auth=true, headers={}, signal }={}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 20000)
  const abortFromParent = () => controller.abort()
  if (signal) {
    if (signal.aborted) controller.abort()
    else signal.addEventListener('abort', abortFromParent, { once: true })
  }
  const token = tokenStorage.get()
  const h = { ...headers }
  if (auth && token) h.Authorization = `Bearer ${token}`
  if (body !== undefined && !(body instanceof FormData)) h['Content-Type']='application/json'
  try {
    const response = await fetch(`${BASE}${path}`, { method, headers:h, body:body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body), signal:controller.signal })
    const contentType = response.headers.get('content-type') || ''
    const data = response.status === 204 ? null : contentType.includes('application/json') ? await response.json().catch(()=>null) : await response.text().catch(()=> '')
    if (!response.ok) {
      if (response.status===401 && auth && token) window.dispatchEvent(new Event('hotel:auth-expired'))
      if (response.status===403 && data?.code==='PASSWORD_CHANGE_REQUIRED' && auth && token) window.dispatchEvent(new Event('hotel:password-change-required'))
      throw new ApiError(errorText(data, response), response.status, data?.code, response.headers.get('X-Trace-Id') || data?.traceId)
    }
    return data
  } catch (err) {
    if (err.name === 'AbortError') throw new ApiError('Время ожидания истекло. Проверьте соединение.', 0)
    if (err instanceof TypeError) throw new ApiError('Не удалось подключиться к API Gateway. Проверьте, что backend запущен.', 0)
    throw err
  } finally { clearTimeout(timer); signal?.removeEventListener('abort', abortFromParent) }
}
export async function imageBlob(path, signal) {
  const controller = new AbortController()
  const abortFromParent = () => controller.abort()
  if (signal) {
    if (signal.aborted) controller.abort()
    else signal.addEventListener('abort', abortFromParent, { once: true })
  }
  const timeout = setTimeout(() => controller.abort(), 20000)
  try {
    const token = tokenStorage.get()
    const response = await fetch(`${BASE}${path}`, {headers:token?{Authorization:`Bearer ${token}`}:{}, signal:controller.signal})
    if (!response.ok) {
      if (response.status===401 && token) window.dispatchEvent(new Event('hotel:auth-expired'))
      const data = await response.json().catch(()=>null)
      if (response.status===403 && data?.code==='PASSWORD_CHANGE_REQUIRED' && token)
        window.dispatchEvent(new Event('hotel:password-change-required'))
      throw new ApiError(errorText(data,response), response.status, data?.code)
    }
    return await response.blob()
  } catch(err) {
    if (err.name==='AbortError') {
      if (signal?.aborted) throw err // An unmounted photo is not a network error.
      throw new ApiError('Время загрузки фотографии истекло.',0,'IMAGE_TIMEOUT')
    }
    if (err instanceof TypeError) throw new ApiError('Не удалось загрузить фотографию. Проверьте соединение.',0)
    throw err
  } finally {clearTimeout(timeout);signal?.removeEventListener('abort',abortFromParent)}
}
// Verified against RoomPhotoController in the backend archive supplied 2026-10-31.
const roomPhotoPath = roomId => `/rooms/${encodeURIComponent(roomId)}/photos`

export const api = {
  login: data => request('/users/login', { method:'POST', body:data, auth:false }),
  register: data => request('/users/register', { method:'POST', body:data, auth:false }),
  // ADMIN users: contracts verified against Booking Service controllers / DTOs.
  adminUsers: query => request(`/users?${new URLSearchParams(query)}`),
  adminUser: id => request(`/users/${encodeURIComponent(id)}`),
  adminCreateUser: data => request('/users', {method:'POST', body:data}),
  adminUpdateUser: (id,data) => request(`/users/${encodeURIComponent(id)}`, {method:'PATCH', body:data}),
  adminDeleteUser: id => request(`/users/${encodeURIComponent(id)}`, {method:'DELETE'}),
  adminResetPassword: (id,newPassword) => request(`/users/${encodeURIComponent(id)}/reset-password`, {method:'POST', body:{newPassword}}),
  me: () => request('/users/me'),
  editMe: data => request('/users/me', { method:'PATCH', body:data }),
  changeMyPassword: data => request('/users/me/change-password', { method:'POST', body:data }),
  hotelsPage: query => request(`/hotels?${new URLSearchParams(query)}`),
  roomsPage: query => request(`/rooms?${new URLSearchParams(query)}`),
  room: id => request(`/rooms/${encodeURIComponent(id)}`),
  hotel: id => request(`/hotels/${id}`),
  createHotel: data => request('/hotels',{method:'POST',body:data}),
  editHotel: (id,data) => request(`/hotels/${id}/presentation`,{method:'PATCH',body:data}),
  recommend: query => request(`/rooms/recommend?${new URLSearchParams(query)}`),
  createRoom: data => request('/rooms',{method:'POST',body:data}),
  editRoom: (id,data) => request(`/rooms/${id}/details`,{method:'PATCH',body:data}),
  renameRoom: (id,number) => request(`/rooms/${id}`,{method:'PUT',body:{number}}),
  updateHotelIdentity: (id,name,address) => request(`/hotels/${id}`,{method:'PUT',body:{name,address}}),
  bookingsMy: query => request(`/bookings/my${query ? '?'+new URLSearchParams(query) : ''}`),
  booking: id => request(`/bookings/${encodeURIComponent(id)}`),
  bookingsAdmin: query => request(`/bookings${query ? '?'+new URLSearchParams(query) : ''}`),
  createBooking: (data,key) => request('/bookings',{method:'POST',body:data,headers:{'Idempotency-Key':key}}),
  cancelBooking: id => request(`/bookings/${id}/cancel`, {method:'PATCH'}),
  statistics: id => request(`/hotels/${id}/statistics`),
  // Hotel-wide configuration. An explicit strategy in an old client overrides this default.
  roomSelectionStrategy: id => request(`/hotels/${id}/room-selection-strategy`),
  setRoomSelectionStrategy: (id, strategy) => request(`/hotels/${id}/room-selection-strategy`, { method:'PUT', body:{strategy} }),
  // Room photographs; binary bytes always go through authenticated imageBlob().
  roomPhotos: roomId => request(roomPhotoPath(roomId)),
  roomPhotoFilePath: (roomId, photoId) => `${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}/file`,
  roomPhotoUpload: (roomId, file) => { const form=new FormData(); form.append('file',file); return request(roomPhotoPath(roomId), {method:'POST',body:form}) },
  roomPhotoPrimary: (roomId, photoId) => request(`${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}/primary`, {method:'PATCH'}),
  roomPhotoOrder: (roomId, photoId, sortOrder) => request(`${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}/order?sortOrder=${encodeURIComponent(sortOrder)}`, {method:'PATCH'}),
  roomPhotoDelete: (roomId, photoId) => request(`${roomPhotoPath(roomId)}/${encodeURIComponent(photoId)}`, {method:'DELETE'}),
  photos: id => request(`/hotels/${id}/photos`),
  photoUpload: (id,file) => { const form=new FormData(); form.append('file',file); return request(`/hotels/${id}/photos`,{method:'POST',body:form}) },
  photoPrimary: (id,pid) => request(`/hotels/${id}/photos/${pid}/primary`,{method:'PATCH'}),
  photoDelete: (id,pid) => request(`/hotels/${id}/photos/${pid}`,{method:'DELETE'}),
}
