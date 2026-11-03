import test from 'node:test'
import assert from 'node:assert/strict'
import { USERNAME_HTML_PATTERN, usernameValidationMessage } from '../src/utils/username.js'

const valid = ['abc','Alice','alice.smith','alice-smith','alice_smith','A01','A'.repeat(30)]
const invalid = ['','ab','1alice','_alice','alice smith','alice\tfield','иван','Aléa','alice@home','A'.repeat(31)]

test('HTML pattern is valid in UnicodeSets (v) mode used by current browsers',()=>{
  const regex = new RegExp(`^(?:${USERNAME_HTML_PATTERN})$`,'v')
  for (const value of valid) assert.equal(regex.test(value),true,value)
  for (const value of invalid) assert.equal(regex.test(value),false,value)
})

test('username validation matches the backend DTO for representative inputs',()=>{
  const regex = /^[A-Za-z][A-Za-z0-9._-]{2,29}$/
  for (const value of valid) assert.equal(usernameValidationMessage(value),'',value)
  for (const value of invalid) {
    assert.ok(usernameValidationMessage(value),value)
    assert.equal(regex.test(value),false,value)
  }
})

test('username containing spaces gets a clear message',()=>{
  assert.match(usernameValidationMessage('petya petya'), /Пробелы/)
})
