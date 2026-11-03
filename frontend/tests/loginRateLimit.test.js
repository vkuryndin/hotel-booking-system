import test from 'node:test'
import assert from 'node:assert/strict'
import { parseRetryAfterSeconds, saveLoginCooldown, readLoginCooldown, clearLoginCooldown } from '../src/utils/loginRateLimit.js'

test('Retry-After seconds are parsed without guessing', () => {
  assert.equal(parseRetryAfterSeconds('25'), 25)
  assert.equal(parseRetryAfterSeconds('0'), 1)
  assert.equal(parseRetryAfterSeconds('invalid'), null)
  assert.equal(parseRetryAfterSeconds(null), null)
})

test('Retry-After also supports HTTP date and expired values', () => {
  const now=Date.parse('2026-11-03T12:00:00.000Z')
  assert.equal(parseRetryAfterSeconds('Tue, 03 Nov 2026 12:00:30 GMT',now), 30)
  assert.equal(parseRetryAfterSeconds('Tue, 03 Nov 2026 11:59:59 GMT',now), null)
})

test('429 cooldown survives navigation and expires based on the server hint', () => {
  const store=new Map()
  const old=globalThis.sessionStorage
  globalThis.sessionStorage={getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)}
  try {
    const now=Date.parse('2026-11-03T12:00:00.000Z')
    const cooldown=saveLoginCooldown(15,now)
    assert.equal(cooldown.until,now+15_000)
    assert.deepEqual(readLoginCooldown(now+5_000), cooldown)
    assert.equal(readLoginCooldown(now+15_000),null)
    assert.equal(store.size,0)
  } finally {if(old===undefined)delete globalThis.sessionStorage;else globalThis.sessionStorage=old}
})

test('unknown Retry-After uses a short approximate cooldown', () => {
  const store=new Map()
  const old=globalThis.sessionStorage
  globalThis.sessionStorage={getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)}
  try {
    const cooldown=saveLoginCooldown(null,1000)
    assert.equal(cooldown.until,61000)
    assert.equal(cooldown.exact,false)
    clearLoginCooldown()
    assert.equal(readLoginCooldown(1000),null)
  } finally {if(old===undefined)delete globalThis.sessionStorage;else globalThis.sessionStorage=old}
})
