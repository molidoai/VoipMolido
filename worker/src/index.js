const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } });
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return json({ service: 'molido-voip', phase: 0, status: 'foundation', productionReady: false });
    if (url.pathname.startsWith('/api/')) return json({ error: 'NOT_IMPLEMENTED', message: 'Authenticated, tenant-scoped API will be enabled in PHASE 2.' }, 501);
    return json({ error: 'NOT_FOUND' }, 404);
  }
};
