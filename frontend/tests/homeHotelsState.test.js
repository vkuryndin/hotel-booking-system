import test from 'node:test'
import assert from 'node:assert/strict'
import { homeHotelsDisplayState } from '../src/utils/homeHotelsState.js'

test('home shows loading rather than a false empty result before hotels arrive', () => {
  assert.equal(homeHotelsDisplayState({ authLoading: false, authenticated: true, loading: true, error: '', count: 0 }), 'loading')
  assert.equal(homeHotelsDisplayState({ authLoading: true, authenticated: true, loading: false, error: '', count: 0 }), 'loading')
})

test('home declares hotels absent only after a completed successful empty response', () => {
  assert.equal(homeHotelsDisplayState({ authLoading: false, authenticated: true, loading: false, error: '', count: 0 }), 'empty')
  assert.equal(homeHotelsDisplayState({ authLoading: false, authenticated: true, loading: false, error: '', count: 3 }), 'cards')
})

test('home does not mistake an API failure for an empty catalogue', () => {
  assert.equal(homeHotelsDisplayState({ authLoading: false, authenticated: true, loading: false, error: '503', count: 0 }), 'error')
  assert.equal(homeHotelsDisplayState({ authLoading: false, authenticated: true, loading: false, error: '503', count: 3 }), 'cards')
  assert.equal(homeHotelsDisplayState({ authLoading: false, authenticated: false, loading: false, error: '', count: 0 }), 'sign-in')
})
