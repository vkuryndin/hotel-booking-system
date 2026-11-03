import test from 'node:test'
import assert from 'node:assert/strict'
import { getPageWindow } from '../src/utils/paging.js'
import { createPromiseCache } from '../src/utils/promiseCache.js'
import { accountLogin, arrayOf, fullName, nights, normalRoom, statusName } from '../src/utils/format.js'

test('pagination stays within bounds for the first, middle and last pages',()=>{
  assert.deepEqual(getPageWindow(0,12).pages,[0,1,2,3,4])
  assert.deepEqual(getPageWindow(6,12).pages,[4,5,6,7,8])
  assert.deepEqual(getPageWindow(11,12).pages,[7,8,9,10,11])
  assert.equal(getPageWindow(100,12).current,11)
  assert.deepEqual(getPageWindow(0,1).pages,[0])
})
test('pagination safely clamps invalid numbers',()=>{
  assert.deepEqual(getPageWindow(-1,0).pages,[0])
  assert.equal(getPageWindow('garbage','garbage').current,0)
})
test('in-flight data is fetched only once per key',async()=>{
  let calls=0
  const cache=createPromiseCache()
  const a=cache('123',()=>{calls++;return Promise.resolve({id:123})})
  const b=cache('123',()=>{calls++;return Promise.resolve({id:123})})
  assert.strictEqual(a,b)
  assert.deepEqual(await a,{id:123})
  assert.equal(calls,1)
})
test('failed requests are not cached',async()=>{
  let calls=0
  const cache=createPromiseCache()
  await assert.rejects(cache('x',()=>{calls++;throw Error('offline')}))
  await Promise.resolve() // Flush microtasks after rejection cleanup.
  await cache('x',()=>{calls++;return Promise.resolve(true)})
  assert.equal(calls,2)
})
test('cache expires and is bounded',async()=>{
  let now=0,calls=0
  const cache=createPromiseCache({maxEntries:2,ttlMs:10,now:()=>now})
  await cache('a',()=>++calls)
  await cache('b',()=>++calls)
  await cache('c',()=>++calls)
  await cache('a',()=>++calls)
  assert.equal(calls,4)
  now=11
  await cache('a',()=>++calls)
  assert.equal(calls,5)
})
test('booking DTO helpers tolerate missing fields and real status values',()=>{
  assert.deepEqual(arrayOf({content:[{id:1}]}),[{id:1}])
  assert.equal(fullName({firstName:'Иван',lastName:'Петров'}),'Иван Петров')
  assert.equal(nights('2026-11-02','2026-11-05'),3)
  assert.equal(normalRoom({roomNumber:'101'}).number,'101')
  assert.equal(statusName('CANCELLING'),'Отмена выполняется')
})

test('navigation identifies the signed-in account by username, not first and last name',()=>{
  assert.equal(accountLogin({username:'petya',firstName:'Petya',lastName:'Petya',email:'petya@example.com'}),'petya')
  assert.equal(accountLogin({email:'petya@example.com'}),'petya@example.com')
  assert.equal(accountLogin({id:42}),'Учётная запись #42')
})
