import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const sw = readFileSync(new URL('../sw.js', import.meta.url), 'utf8');
const app = readFileSync(new URL('../app.js', import.meta.url), 'utf8');
test('service worker uses versioned cache and deletes older MOLIDO caches', () => {
  assert.match(sw, /molido-voip-preview-v0\.2\.0/);
  assert.match(sw, /caches\.delete/);
});
test('update checks occur when app returns to foreground', () => {
  assert.match(app, /visibilitychange/);
  assert.match(app, /registration\.update\(\)/);
});
test('PWA refreshes after worker takes control', () => {
  assert.match(app, /controllerchange/);
  assert.match(app, /location\.reload\(\)/);
});
