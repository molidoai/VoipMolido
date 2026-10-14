import test from 'node:test';
import assert from 'node:assert/strict';
import handler from '../src/index.js';
test('health exposes non-production state', async () => {
 const response = await handler.fetch(new Request('https://example.test/health'), {});
 assert.equal(response.status, 200);
 assert.equal((await response.json()).productionReady, false);
});
test('protected API fails closed until auth exists', async () => {
 const response = await handler.fetch(new Request('https://example.test/api/tenants', { method: 'POST' }), {});
 assert.equal(response.status, 501);
});
