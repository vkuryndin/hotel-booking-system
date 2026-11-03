/** Small dependency-free primitives used by API and image reads. */
// A slow EC2 instance can legitimately take longer than the original 12/16/20s.
// Keep finite safety limits so abandoned requests cannot occupy browser slots forever.
export const REQUEST_TIMEOUT_MS = Object.freeze({
  read: 60_000,
  write: 90_000,
  image: 60_000,
})
export function abortError() {
  const error = new Error('Запрос отменён')
  error.name = 'AbortError'
  return error
}

export function wait(ms, signal) {
  if (signal?.aborted) return Promise.reject(abortError())
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { signal?.removeEventListener('abort', onAbort); resolve() }, ms)
    const onAbort = () => { clearTimeout(timeout); signal?.removeEventListener('abort', onAbort); reject(abortError()) }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

export function isTemporaryError(error) {
  return error?.status === 0 || [502, 503, 504].includes(error?.status)
}

// A timed-out request might still be running on the server. Retrying it immediately
// adds load to an already slow instance. It may still use explicitly labelled stale data.
export function isRetryableReadError(error) {
  return isTemporaryError(error) && !['REQUEST_TIMEOUT', 'IMAGE_TIMEOUT',
    'REQUEST_ABORTED', 'IMAGE_ABORTED'].includes(error?.code)
}

/** Only call for idempotent reads. Never retry account changes or bookings here. */
export async function retryRead(load, { retries = 2, signal, sleep = wait, jitter = Math.random } = {}) {
  for (let attempt = 0; ; attempt++) {
    if (signal?.aborted) throw abortError()
    try { return await load() }
    catch (error) {
      if (signal?.aborted || error?.name === 'AbortError' || !isRetryableReadError(error) || attempt >= retries) throw error
      // Backoff with jitter avoids a synchronized thundering herd after service recovery.
      await sleep(Math.min(1800, 400 * 2 ** attempt) + Math.floor(jitter() * 200), signal)
    }
  }
}

/** Abortable FIFO queue. Do not queue login or writes behind image downloads. */
export function createTaskQueue(limit = 4) {
  if (!Number.isInteger(limit) || limit < 1) throw new RangeError('Invalid concurrency limit')
  let active = 0
  const pending = []
  function drain() {
    while (active < limit && pending.length) {
      const item = pending.shift()
      if (item.signal?.aborted) { item.cancel(); continue }
      item.signal?.removeEventListener('abort', item.cancel)
      active++
      Promise.resolve().then(item.task).then(item.resolve, item.reject).finally(() => { active--; drain() })
    }
  }
  return {
    run(task, signal) {
      if (signal?.aborted) return Promise.reject(abortError())
      return new Promise((resolve, reject) => {
        const item = { task, resolve, reject, signal, cancel: null }
        item.cancel = () => {
          const index = pending.indexOf(item)
          if (index !== -1) { pending.splice(index, 1); signal?.removeEventListener('abort', item.cancel); reject(abortError()) }
        }
        pending.push(item)
        signal?.addEventListener('abort', item.cancel, { once: true })
        drain()
      })
    },
    stats: () => ({ active, queued: pending.length }),
  }
}

/** Bounded, time-aware cache. Expired entries may be shown ONLY as explicitly labelled stale data. */
export function createTimedCache({ maxEntries = 200, now = Date.now } = {}) {
  const entries = new Map()
  return {
    get(key) {
      const entry = entries.get(key)
      if (!entry || entry.freshUntil <= now()) return undefined
      entries.delete(key); entries.set(key, entry)
      return entry.value
    },
    stale(key) {
      const entry = entries.get(key)
      if (!entry) return undefined
      if (entry.keepUntil <= now()) { entries.delete(key); return undefined }
      return entry.value
    },
    put(key, value, ttlMs, staleMs = 30 * 60_000) {
      entries.delete(key)
      entries.set(key, { value, freshUntil: now() + ttlMs, keepUntil: now() + ttlMs + staleMs })
      while (entries.size > maxEntries) entries.delete(entries.keys().next().value)
    },
    clear() { entries.clear() },
    size: () => entries.size,
  }
}

/** Bounded Blob LRU. Fetches are deduplicated; aborting ONE viewer won't cancel other viewers. */
export function createBlobCache({ maxBytes = 28 * 1024 * 1024, maxEntries = 90, ttlMs = 30 * 60_000, now = Date.now } = {}) {
  const blobs = new Map()
  const pending = new Map()
  let bytes = 0
  let epoch = 0
  function store(key, blob) {
    if (!blob || typeof blob.size !== 'number' || blob.size > maxBytes) return
    if (blobs.has(key)) bytes -= blobs.get(key).blob.size
    blobs.delete(key); blobs.set(key, { blob, created: now() }); bytes += blob.size
    while ((bytes > maxBytes || blobs.size > maxEntries) && blobs.size) {
      const oldest = blobs.keys().next().value
      bytes -= blobs.get(oldest).blob.size
      blobs.delete(oldest)
    }
  }
  function subscribe(entry, signal) {
    if (signal?.aborted) return Promise.reject(abortError())
    entry.waiters++
    return new Promise((resolve, reject) => {
      let settled = false
      function finish(callback, value) {
        if (settled) return
        settled = true
        signal?.removeEventListener('abort', onAbort)
        entry.waiters--
        callback(value)
      }
      function onAbort() {
        finish(reject, abortError())
        if (!entry.waiters) {
          if (pending.get(entry.key) === entry) pending.delete(entry.key)
          entry.controller.abort()
        }
      }
      signal?.addEventListener('abort', onAbort, { once: true })
      entry.promise.then(blob => finish(resolve, blob), error => finish(reject, error))
    })
  }
  return {
    get(key, loader, signal) {
      if (signal?.aborted) return Promise.reject(abortError())
      if (blobs.has(key)) {
        const entry = blobs.get(key)
        if (now() - entry.created < ttlMs) {
          blobs.delete(key); blobs.set(key, entry)
          return Promise.resolve(entry.blob)
        }
        bytes -= entry.blob.size
        blobs.delete(key)
      }
      let entry = pending.get(key)
      if (!entry) {
        const controller = new AbortController()
        entry = { key, controller, waiters: 0, promise: null }
        const currentEpoch = epoch
        pending.set(key, entry)
        entry.promise = Promise.resolve().then(() => loader(controller.signal)).then(blob => {
          if (epoch === currentEpoch && pending.get(key) === entry) store(key, blob)
          return blob
        }).finally(() => { if (pending.get(key) === entry) pending.delete(key) })
      }
      return subscribe(entry, signal)
    },
    clear() {
      epoch++
      blobs.clear(); bytes = 0
      for (const entry of pending.values()) entry.controller.abort()
      pending.clear()
    },
    stats: () => ({ entries: blobs.size, bytes, pending: pending.size }),
  }
}
