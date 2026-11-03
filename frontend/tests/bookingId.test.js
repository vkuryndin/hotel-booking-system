import test from 'node:test'
import assert from 'node:assert/strict'
import { createBookingRequestId } from '../src/utils/bookingId.js'

const validUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

test('uses native randomUUID on secure origins when available', () => {
  const expected = 'a9999999-9999-4999-8999-999999999999'
  assert.equal(createBookingRequestId({ randomUUID: () => expected }), expected)
})

test('creates valid and distinct UUID v4 on HTTP origins without crypto.randomUUID', () => {
  let seed = 0
  const cryptoApi = { getRandomValues(bytes) {
    for (let i = 0; i < bytes.length; i++) bytes[i] = (seed++ * 17) & 0xff
    return bytes
  } }
  const first = createBookingRequestId(cryptoApi)
  const second = createBookingRequestId(cryptoApi)
  assert.match(first, validUuid)
  assert.match(second, validUuid)
  assert.notEqual(first, second)
})

test('legacy fallback still creates backend-compatible UUID when Web Crypto is missing', () => {
  let seed = 0
  const requestId = createBookingRequestId(null, () => ((seed++ * 37) % 256) / 256)
  assert.match(requestId, validUuid)
})
