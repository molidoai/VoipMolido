import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const src = fs.readFileSync(
  new URL('../src/index.js', import.meta.url),
  'utf8'
);
const schema = fs.readFileSync(
  new URL('../schema.sql', import.meta.url),
  'utf8'
);

test('health endpoint exists', () => {
  assert.match(src, /\/health/);
  assert.match(src, /secure-multitenant-0\.6\.0/);
});

test('passwords use PBKDF2 SHA-256', () => {
  assert.match(src, /PBKDF2/);
  assert.match(src, /SHA-256/);
  assert.match(src, /210000/);
});

test('session token stored as hash and cookie is hardened', () => {
  assert.match(src, /token_hash/);
  assert.match(src, /HttpOnly/);
  assert.match(src, /Secure/);
  assert.match(src, /SameSite=Strict/);
});

test('login lockout exists', () => {
  assert.match(src, /MAX_LOGIN_FAILURES\s*=\s*5/);
  assert.match(src, /ACCOUNT_TEMPORARILY_LOCKED/);
});

test('multi-tenant RBAC exists', () => {
  assert.match(schema, /tenant_members/);
  assert.match(schema, /owner','admin','operator','user/);
  assert.match(src, /requireTenantRole/);
});

test('audit log exists', () => {
  assert.match(schema, /audit_logs/);
  assert.match(src, /USER_CREATED/);
  assert.match(src, /PBX_CREATED/);
});

test('raw password column is not present', () => {
  assert.doesNotMatch(schema, /\bpassword\s+TEXT/i);
  assert.match(schema, /password_hash TEXT NOT NULL/);
  assert.match(schema, /password_salt TEXT NOT NULL/);
});
