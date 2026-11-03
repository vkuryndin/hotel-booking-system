import test from 'node:test'
import assert from 'node:assert/strict'
import { api, clearClientCaches, imageBlob, request, tokenStorage } from '../src/api/client.js'

const store = new Map()
globalThis.sessionStorage = {
  getItem: key => store.get(key) ?? null,
  setItem: (key, value) => store.set(key, String(value)),
  removeItem: key => store.delete(key),
}
globalThis.window = { dispatchEvent: () => {} }
const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { 'Content-Type': 'application/json' },
})
const reset = () => { clearClientCaches(); store.clear() }

test('catalog GET returns cached data on a second visit without hitting the backend', async () => {
  reset()
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({ content: [{ id: 1 }] }) }
  const a = await api.hotelsPage({ page: 0, size: 3 })
  const b = await api.hotelsPage({ page: 0, size: 3 })
  assert.deepEqual(a, b)
  assert.equal(calls, 1)
})

test('a successful mutation invalidates catalogue GET cache, without retrying the mutation', async () => {
  reset()
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push(`${options.method}:${url}`)
    return options.method === 'POST' ? json({ id: 5 }, 201) : json({ content: [] })
  }
  await api.hotelsPage({ page: 0, size: 3 })
  await api.createHotel({ name: 'A', address: 'B' })
  await api.hotelsPage({ page: 0, size: 3 })
  assert.deepEqual(calls.map(s => s.split(':')[0]), ['GET', 'POST', 'GET'])
})

test('JWT switch clears cached metadata and images so users do not share private responses', async () => {
  reset()
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({ name: `Hotel ${calls}` }) }
  tokenStorage.set('token-a')
  assert.equal((await api.hotel(7)).name, 'Hotel 1')
  tokenStorage.set('token-b')
  assert.equal((await api.hotel(7)).name, 'Hotel 2')
  assert.equal(calls, 2)
})

test('availability/recommendation is never served from a cached result', async () => {
  reset()
  let requests = 0
  globalThis.fetch = async () => { requests++; return json([{ id: requests }]) }
  await api.recommend({ hotelId: 1, checkIn: '2026-12-14', checkOut: '2026-12-18', guestCount: 2 })
  await api.recommend({ hotelId: 1, checkIn: '2026-12-14', checkOut: '2026-12-18', guestCount: 2 })
  assert.equal(requests, 2)
})

test('429 is passed to caller without retry, preserving Retry-After header', async () => {
  reset()
  let requests = 0
  globalThis.fetch = async () => { requests++; return new Response('', { status: 429, headers: { 'Retry-After': '610' } }) }
  await assert.rejects(api.login({ email: 'a@example.com', password: 'wrong' }),
    error => error.status === 429 && error.retryAfterSeconds === 610)
  assert.equal(requests, 1)
})

test('two image viewers share one authenticated Blob download', async () => {
  reset()
  let calls = 0
  globalThis.fetch = async (url, opts) => {
    assert.match(url, /\/rooms\/3\/photos\/4\/file/)
    assert.equal(opts.method, undefined)
    calls++
    return new Response(new Blob(['fake image'], { type: 'image/jpeg' }), { status: 200 })
  }
  const [a, b] = await Promise.all([
    imageBlob('/rooms/3/photos/4/file'), imageBlob('/rooms/3/photos/4/file'),
  ])
  assert.equal(calls, 1)
  assert.strictEqual(a, b)
})

test('older data can only be used as explicitly marked stale after temporary failure', async () => {
  reset()
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({ id: 3 }) }
  await request('/hotels/3')
  globalThis.fetch = async () => { calls++; return json({ message: 'Not available' }, 503) }
  // Forced refresh failure with a saved response is allowed to return a clearly labelled fallback.
  let stale = false
  const result = await request('/hotels/3', {
    forceRefresh: true, allowStaleOnError: true, onStale: () => { stale = true },
  })
  assert.deepEqual(result, { id: 3 })
  assert.equal(stale, true)
  assert.equal(calls, 4)
})

test('concurrent identical reads populate the cache once, not just deduplicate in-flight', async () => {
  reset()
  let calls = 0
  let release
  globalThis.fetch = () => {
    calls++
    return new Promise(resolve => { release = () => resolve(json({ name: 'One' })) })
  }
  const a = api.hotel(17)
  const b = api.hotel(17)
  await Promise.resolve()
  await Promise.resolve()
  // Wait for the async queue to schedule its first task.
  for (let index = 0; index < 20 && !release; index++) await Promise.resolve()
  assert.equal(calls, 1)
  release()
  await Promise.all([a, b])
  assert.deepEqual(await api.hotel(17), { name: 'One' })
  assert.equal(calls, 1)
})
