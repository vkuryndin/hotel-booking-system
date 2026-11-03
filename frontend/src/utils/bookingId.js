/** A fresh, syntactically valid UUID v4 for every new booking request.
 * crypto.randomUUID is only available in secure contexts; crypto.getRandomValues
 * also works on ordinary HTTP. The header is an idempotency identifier, not a secret.
 */
export function createBookingRequestId(cryptoApi = globalThis.crypto, random = Math.random) {
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()

  const bytes = new Uint8Array(16)
  if (typeof cryptoApi?.getRandomValues === 'function') {
    cryptoApi.getRandomValues(bytes)
  } else {
    // Legacy browser fallback: this identifier must be unique, not unguessable.
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(random() * 256)
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
