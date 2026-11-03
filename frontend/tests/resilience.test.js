import test from 'node:test'
import assert from 'node:assert/strict'
import { createBlobCache, createTaskQueue, createTimedCache, isRetryableReadError, REQUEST_TIMEOUT_MS, retryRead } from '../src/utils/resilience.js'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

test('safe read retries a 503 twice, with bounded incremental delays', async () => {
  let attempts = 0
  const delays = []
  const result = await retryRead(async () => {
    attempts++
    if (attempts <= 2) throw { status: 503 }
    return { ok: true }
  }, { sleep: async ms => { delays.push(ms) }, jitter: () => 0 })
  assert.deepEqual(result, { ok: true })
  assert.equal(attempts, 3)
  assert.deepEqual(delays, [400, 800])
})

test('authentication and rate-limit errors are never retried', async () => {
  for (const status of [400, 401, 403, 409, 429]) {
    let attempts = 0
    await assert.rejects(retryRead(async () => { attempts++; throw { status } },
      { sleep: async () => { throw Error('Unexpected retry') } }), error => error.status === status)
    assert.equal(attempts, 1)
  }
})

test('abort during retry backoff stops further attempts', async () => {
  const controller = new AbortController()
  let count = 0
  await assert.rejects(retryRead(async () => { count++; throw { status: 502 } }, {
    signal: controller.signal,
    sleep: async () => { controller.abort() },
    jitter: () => 0,
  }), error => error.name === 'AbortError')
  assert.equal(count, 1)
})

test('queue limits active image downloads and cancels queued work', async () => {
  const queue = createTaskQueue(1)
  const first = deferred()
  const events = []
  const a = queue.run(async () => { events.push('a'); await first.promise; return 1 })
  const controller = new AbortController()
  const b = queue.run(async () => { events.push('b'); return 2 }, controller.signal)
  const c = queue.run(async () => { events.push('c'); return 3 })
  controller.abort()
  await assert.rejects(b, error => error.name === 'AbortError')
  first.resolve()
  assert.equal(await a, 1)
  assert.equal(await c, 3)
  await Promise.resolve() // Queue releases its slot after settling the task.
  assert.deepEqual(events, ['a', 'c'])
  assert.deepEqual(queue.stats(), { active: 0, queued: 0 })
})

test('timed GET cache expires, retains explicit stale data briefly and has LRU bounds', () => {
  let time = 100
  const cache = createTimedCache({ maxEntries: 2, now: () => time })
  cache.put('a', 1, 100, 200)
  assert.equal(cache.get('a'), 1)
  time = 201
  assert.equal(cache.get('a'), undefined)
  assert.equal(cache.stale('a'), 1)
  cache.put('b', 2, 100, 200)
  cache.put('c', 3, 100, 200)
  assert.equal(cache.stale('a'), undefined)
  assert.equal(cache.size(), 2)
  time = 502
  assert.equal(cache.stale('b'), undefined)
  cache.clear()
  assert.equal(cache.size(), 0)
})

test('image cache shares a download across viewers and one viewer may cancel', async () => {
  const cache = createBlobCache()
  const future = deferred()
  const controller = new AbortController()
  let calls = 0
  const loader = () => { calls++; return future.promise }
  const first = cache.get('room/1', loader, controller.signal)
  const second = cache.get('room/1', loader)
  controller.abort()
  await assert.rejects(first, error => error.name === 'AbortError')
  const image = { size: 200, id: 'blob-1' }
  future.resolve(image)
  assert.strictEqual(await second, image)
  assert.equal(calls, 1)
  assert.strictEqual(await cache.get('room/1', () => { throw Error('cache miss') }), image)
})

test('image LRU obeys memory limit and clear forces refetch', async () => {
  const cache = createBlobCache({ maxBytes: 10, maxEntries: 2 })
  await cache.get('a', () => ({ size: 6 }))
  await cache.get('b', () => ({ size: 6 }))
  assert.deepEqual(cache.stats(), { entries: 1, bytes: 6, pending: 0 })
  let loaded = 0
  await cache.get('a', () => { loaded++; return { size: 4 } })
  assert.equal(loaded, 1)
  cache.clear()
  assert.deepEqual(cache.stats(), { entries: 0, bytes: 0, pending: 0 })
  await cache.get('a', () => { loaded++; return { size: 4 } })
  assert.equal(loaded, 2)
})

test('image cache cancels a shared download after last viewer leaves', async () => {
  const cache = createBlobCache()
  const controller = new AbortController()
  let canceled = false
  const task = cache.get('abandoned', signal => new Promise((resolve, reject) => {
    if (signal.aborted) { canceled = true; reject(Object.assign(Error('aborted'), { name: 'AbortError' })); return }
    signal.addEventListener('abort', () => { canceled = true; reject(Object.assign(Error('aborted'), { name: 'AbortError' })) }, { once: true })
  }), controller.signal)
  await Promise.resolve() // Start the shared loader.
  controller.abort()
  await assert.rejects(task, error => error.name === 'AbortError')
  assert.equal(canceled, true)
  assert.equal(cache.stats().pending, 0)
})

test('photo cache TTL expires and refreshes a changed image', async () => {
  let now = 0
  const cache = createBlobCache({ ttlMs: 100, now: () => now })
  let calls = 0
  const fetch = () => ({ size: 3, id: ++calls })
  assert.equal((await cache.get('photo', fetch)).id, 1)
  now = 99
  assert.equal((await cache.get('photo', fetch)).id, 1)
  now = 100
  assert.equal((await cache.get('photo', fetch)).id, 2)
  assert.equal(calls, 2)
})


test('slow-server deadlines leave time for reads, writes and photos', () => {
  assert.deepEqual(REQUEST_TIMEOUT_MS, { read: 60_000, write: 90_000, image: 60_000 })
})

test('a client-side timeout does not trigger more requests against the overloaded server', async () => {
  let attempts = 0
  const timeout = { status: 0, code: 'REQUEST_TIMEOUT' }
  await assert.rejects(retryRead(async () => { attempts++; throw timeout }, {
    sleep: async () => { throw Error('Unexpected retry') },
  }), error => error === timeout)
  assert.equal(attempts, 1)
  assert.equal(isRetryableReadError({ status: 0, code: 'NETWORK_ERROR' }), true)
  assert.equal(isRetryableReadError({ status: 503 }), true)
  assert.equal(isRetryableReadError({ status: 0, code: 'IMAGE_TIMEOUT' }), false)
})
