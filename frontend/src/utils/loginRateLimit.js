/** Parse Retry-After seconds (or HTTP date) supplied by the API Gateway. */
export function parseRetryAfterSeconds(header, now = Date.now()) {
  if (typeof header !== 'string' || !header.trim()) return null
  const value = header.trim()
  if (/^\d+$/.test(value)) {
    const seconds = Number(value)
    return Number.isSafeInteger(seconds) ? Math.max(1, Math.min(seconds, 3600)) : null
  }
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return null
  const seconds = Math.ceil((timestamp - now) / 1000)
  return seconds > 0 ? Math.min(seconds, 3600) : null
}

// A local guard prevents accidentally sending further requests during the server's wait period.
// The backend remains the authority; a new 429 can still extend the cooldown.
const STORAGE_KEY = 'hotel_booking_login_cooldown'
const FALLBACK_SECONDS = 60

export function saveLoginCooldown(retryAfterSeconds, now = Date.now()) {
  const exact = Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0
  const seconds = exact ? Math.min(Math.ceil(retryAfterSeconds), 3600) : FALLBACK_SECONDS
  const cooldown = { until: now + seconds * 1000, exact }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cooldown))
  return cooldown
}

export function readLoginCooldown(now = Date.now()) {
  try {
    const parsed = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
    if (Number.isFinite(parsed?.until) && parsed.until > now) {
      return { until: parsed.until, exact: parsed.exact === true }
    }
  } catch { /* Corrupt browser storage must never block login. */ }
  sessionStorage.removeItem(STORAGE_KEY)
  return null
}

export function clearLoginCooldown() {
  sessionStorage.removeItem(STORAGE_KEY)
}
