const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'cache-control': 'no-store',
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer'
};

const SESSION_COOKIE = 'molido_session';
const SESSION_TTL_SECONDS = 60 * 60 * 12;
const PBKDF2_ITERATIONS = 210000;
const MAX_LOGIN_FAILURES = 5;
const LOCK_MINUTES = 15;

const json = (data, status = 200, extra = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...extra }
  });

const bad = (message, status = 400) => json({ error: message }, status);

const normalizeUsername = value =>
  String(value || '').trim().toLowerCase();

const validUsername = value =>
  /^[a-z0-9._-]{3,64}$/.test(value);

const validPassword = value =>
  typeof value === 'string' &&
  value.length >= 10 &&
  value.length <= 128;

const bytesToB64 = bytes => {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
};

const b64ToBytes = value => {
  const binary = atob(value);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
};

const randomB64 = size => {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return bytesToB64(bytes);
};

const sha256Hex = async value => {
  const bytes = new TextEncoder().encode(value);
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
  return [...digest].map(b => b.toString(16).padStart(2, '0')).join('');
};

const hashPassword = async (password, saltB64, iterations = PBKDF2_ITERATIONS) => {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      hash: 'SHA-256',
      salt: b64ToBytes(saltB64),
      iterations
    },
    key,
    256
  );
  return bytesToB64(new Uint8Array(bits));
};

const timingSafeEqual = (a, b) => {
  const x = new TextEncoder().encode(String(a));
  const y = new TextEncoder().encode(String(b));
  if (x.length !== y.length) return false;
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
};

const readJson = async request => {
  const type = request.headers.get('content-type') || '';
  if (!type.toLowerCase().includes('application/json')) {
    throw new Error('JSON_REQUIRED');
  }
  const text = await request.text();
  if (text.length > 32_768) throw new Error('BODY_TOO_LARGE');
  return JSON.parse(text || '{}');
};

const cookieValue = (request, name) => {
  const raw = request.headers.get('cookie') || '';
  for (const part of raw.split(';')) {
    const [k, ...rest] = part.trim().split('=');
    if (k === name) return decodeURIComponent(rest.join('='));
  }
  return '';
};

const sessionCookie = token =>
  `${SESSION_COOKIE}=${encodeURIComponent(token)}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${SESSION_TTL_SECONDS}`;

const clearCookie =
  `${SESSION_COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0`;

const nowIso = () => new Date().toISOString();
const futureIso = seconds => new Date(Date.now() + seconds * 1000).toISOString();
const newId = prefix => `${prefix}_${crypto.randomUUID()}`;

const audit = async (env, actorUserId, action, targetType = null, targetId = null, tenantId = null) => {
  await env.DB.prepare(
    `INSERT INTO audit_logs
      (id, tenant_id, actor_user_id, action, target_type, target_id)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).bind(
    newId('aud'), tenantId, actorUserId, action, targetType, targetId
  ).run();
};

const getSession = async (request, env) => {
  const token = cookieValue(request, SESSION_COOKIE);
  if (!token) return null;
  const tokenHash = await sha256Hex(token);

  const row = await env.DB.prepare(
    `SELECT s.id AS session_id, s.user_id, s.expires_at,
            u.username, u.status
       FROM sessions s
       JOIN users u ON u.id = s.user_id
      WHERE s.token_hash = ?
      LIMIT 1`
  ).bind(tokenHash).first();

  if (!row || row.status !== 'active') return null;
  if (Date.parse(row.expires_at) <= Date.now()) {
    await env.DB.prepare('DELETE FROM sessions WHERE id = ?')
      .bind(row.session_id).run();
    return null;
  }

  return row;
};

const requireSession = async (request, env) => {
  const session = await getSession(request, env);
  if (!session) throw new Error('UNAUTHENTICATED');
  return session;
};

const requireTenantRole = async (env, userId, tenantId, allowed) => {
  const member = await env.DB.prepare(
    `SELECT role FROM tenant_members
      WHERE tenant_id = ? AND user_id = ? LIMIT 1`
  ).bind(tenantId, userId).first();

  if (!member || !allowed.includes(member.role)) {
    throw new Error('FORBIDDEN');
  }
  return member.role;
};

const bootstrap = async (request, env) => {
  const count = await env.DB.prepare('SELECT COUNT(*) AS c FROM users').first();
  if (Number(count?.c || 0) > 0) return bad('BOOTSTRAP_CLOSED', 409);

  const body = await readJson(request);
  const username = normalizeUsername(body.username);
  const password = body.password;
  const pbxName = String(body.pbxName || 'MOLIDO PBX').trim().slice(0, 80);

  if (!validUsername(username)) return bad('INVALID_USERNAME');
  if (!validPassword(password)) return bad('WEAK_PASSWORD');

  const userId = newId('usr');
  const tenantId = newId('pbx');
  const salt = randomB64(16);
  const passwordHash = await hashPassword(password, salt);
  const slug = `pbx-${crypto.randomUUID().slice(0, 8)}`;

  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO users
       (id, username, password_hash, password_salt, password_iterations)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(userId, username, passwordHash, salt, PBKDF2_ITERATIONS),

    env.DB.prepare(
      `INSERT INTO tenants (id, name, slug, created_by)
       VALUES (?, ?, ?, ?)`
    ).bind(tenantId, pbxName, slug, userId),

    env.DB.prepare(
      `INSERT INTO tenant_members (tenant_id, user_id, role)
       VALUES (?, ?, 'owner')`
    ).bind(tenantId, userId)
  ]);

  await audit(env, userId, 'BOOTSTRAP_OWNER_CREATED', 'tenant', tenantId, tenantId);
  return json({ ok: true, userId, tenantId }, 201);
};

const login = async (request, env) => {
  const body = await readJson(request);
  const username = normalizeUsername(body.username);
  const password = body.password;

  if (!validUsername(username) || typeof password !== 'string') {
    return bad('INVALID_CREDENTIALS', 401);
  }

  const user = await env.DB.prepare(
    `SELECT id, username, password_hash, password_salt,
            password_iterations, status, failed_login_count, locked_until
       FROM users
      WHERE username = ?
      LIMIT 1`
  ).bind(username).first();

  if (!user || user.status !== 'active') {
    return bad('INVALID_CREDENTIALS', 401);
  }

  if (user.locked_until && Date.parse(user.locked_until) > Date.now()) {
    return bad('ACCOUNT_TEMPORARILY_LOCKED', 423);
  }

  const candidate = await hashPassword(
    password,
    user.password_salt,
    Number(user.password_iterations || PBKDF2_ITERATIONS)
  );

  if (!timingSafeEqual(candidate, user.password_hash)) {
    const failures = Number(user.failed_login_count || 0) + 1;
    const lockedUntil =
      failures >= MAX_LOGIN_FAILURES
        ? new Date(Date.now() + LOCK_MINUTES * 60_000).toISOString()
        : null;

    await env.DB.prepare(
      `UPDATE users
          SET failed_login_count = ?,
              locked_until = ?,
              updated_at = CURRENT_TIMESTAMP
        WHERE id = ?`
    ).bind(failures, lockedUntil, user.id).run();

    return bad(
      lockedUntil ? 'ACCOUNT_TEMPORARILY_LOCKED' : 'INVALID_CREDENTIALS',
      lockedUntil ? 423 : 401
    );
  }

  await env.DB.prepare(
    `UPDATE users
        SET failed_login_count = 0,
            locked_until = NULL,
            updated_at = CURRENT_TIMESTAMP
      WHERE id = ?`
  ).bind(user.id).run();

  const token = randomB64(32);
  const tokenHash = await sha256Hex(token);

  await env.DB.prepare(
    `INSERT INTO sessions
      (id, user_id, token_hash, expires_at)
     VALUES (?, ?, ?, ?)`
  ).bind(
    newId('ses'),
    user.id,
    tokenHash,
    futureIso(SESSION_TTL_SECONDS)
  ).run();

  await audit(env, user.id, 'LOGIN');

  return json(
    { ok: true, user: { id: user.id, username: user.username } },
    200,
    { 'set-cookie': sessionCookie(token) }
  );
};

const logout = async (request, env) => {
  const token = cookieValue(request, SESSION_COOKIE);
  if (token) {
    const hash = await sha256Hex(token);
    await env.DB.prepare('DELETE FROM sessions WHERE token_hash = ?')
      .bind(hash).run();
  }
  return json({ ok: true }, 200, { 'set-cookie': clearCookie });
};

const me = async (request, env) => {
  const session = await requireSession(request, env);

  const tenants = await env.DB.prepare(
    `SELECT t.id, t.name, t.slug, tm.role
       FROM tenant_members tm
       JOIN tenants t ON t.id = tm.tenant_id
      WHERE tm.user_id = ? AND t.status = 'active'
      ORDER BY t.created_at ASC`
  ).bind(session.user_id).all();

  return json({
    user: { id: session.user_id, username: session.username },
    tenants: tenants.results || []
  });
};

const createTenant = async (request, env) => {
  const session = await requireSession(request, env);
  const body = await readJson(request);
  const name = String(body.name || '').trim().slice(0, 80);

  if (name.length < 2) return bad('INVALID_PBX_NAME');

  const tenantId = newId('pbx');
  const slug = `pbx-${crypto.randomUUID().slice(0, 8)}`;

  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO tenants (id, name, slug, created_by)
       VALUES (?, ?, ?, ?)`
    ).bind(tenantId, name, slug, session.user_id),

    env.DB.prepare(
      `INSERT INTO tenant_members (tenant_id, user_id, role)
       VALUES (?, ?, 'owner')`
    ).bind(tenantId, session.user_id)
  ]);

  await audit(env, session.user_id, 'PBX_CREATED', 'tenant', tenantId, tenantId);
  return json({ id: tenantId, name, slug, role: 'owner' }, 201);
};

const createUser = async (request, env, tenantId) => {
  const session = await requireSession(request, env);
  await requireTenantRole(
    env,
    session.user_id,
    tenantId,
    ['owner', 'admin']
  );

  const body = await readJson(request);
  const username = normalizeUsername(body.username);
  const password = body.password;
  const role = String(body.role || 'user');
  const extension = String(body.extension || '').trim();
  const displayName = String(body.displayName || username).trim().slice(0, 80);

  if (!validUsername(username)) return bad('INVALID_USERNAME');
  if (!validPassword(password)) return bad('WEAK_PASSWORD');
  if (!['admin','operator','user'].includes(role)) return bad('INVALID_ROLE');
  if (extension && !/^[0-9]{2,8}$/.test(extension)) return bad('INVALID_EXTENSION');

  const existing = await env.DB.prepare(
    'SELECT id FROM users WHERE username = ? LIMIT 1'
  ).bind(username).first();
  if (existing) return bad('USERNAME_EXISTS', 409);

  const userId = newId('usr');
  const salt = randomB64(16);
  const passwordHash = await hashPassword(password, salt);

  const statements = [
    env.DB.prepare(
      `INSERT INTO users
       (id, username, password_hash, password_salt, password_iterations)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(userId, username, passwordHash, salt, PBKDF2_ITERATIONS),

    env.DB.prepare(
      `INSERT INTO tenant_members (tenant_id, user_id, role)
       VALUES (?, ?, ?)`
    ).bind(tenantId, userId, role)
  ];

  if (extension) {
    statements.push(
      env.DB.prepare(
        `INSERT INTO extensions
         (tenant_id, extension, display_name, user_id)
         VALUES (?, ?, ?, ?)`
      ).bind(tenantId, extension, displayName, userId)
    );
  }

  await env.DB.batch(statements);
  await audit(
    env,
    session.user_id,
    'USER_CREATED',
    'user',
    userId,
    tenantId
  );

  return json(
    { id: userId, username, role, extension: extension || null },
    201
  );
};

const listTenantUsers = async (request, env, tenantId) => {
  const session = await requireSession(request, env);
  await requireTenantRole(
    env,
    session.user_id,
    tenantId,
    ['owner','admin','operator','user']
  );

  const rows = await env.DB.prepare(
    `SELECT u.id, u.username, u.status, tm.role,
            e.extension, e.display_name
       FROM tenant_members tm
       JOIN users u ON u.id = tm.user_id
       LEFT JOIN extensions e
         ON e.tenant_id = tm.tenant_id
        AND e.user_id = u.id
      WHERE tm.tenant_id = ?
      ORDER BY u.username ASC`
  ).bind(tenantId).all();

  return json({ users: rows.results || [] });
};

const routeApi = async (request, env, url) => {
  if (url.pathname === '/api/auth/bootstrap' && request.method === 'POST') {
    return bootstrap(request, env);
  }
  if (url.pathname === '/api/auth/login' && request.method === 'POST') {
    return login(request, env);
  }
  if (url.pathname === '/api/auth/logout' && request.method === 'POST') {
    return logout(request, env);
  }
  if (url.pathname === '/api/auth/me' && request.method === 'GET') {
    return me(request, env);
  }
  if (url.pathname === '/api/pbx' && request.method === 'POST') {
    return createTenant(request, env);
  }

  const usersMatch = url.pathname.match(/^\/api\/pbx\/([^/]+)\/users$/);
  if (usersMatch && request.method === 'POST') {
    return createUser(request, env, usersMatch[1]);
  }
  if (usersMatch && request.method === 'GET') {
    return listTenantUsers(request, env, usersMatch[1]);
  }

  return json({ error: 'NOT_FOUND' }, 404);
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    try {
      if (url.pathname === '/health' && request.method === 'GET') {
        return json({
          service: 'molido-voip',
          phase: 'secure-multitenant-0.6.0',
          status: 'foundation',
          productionReady: false
        });
      }

      if (url.pathname.startsWith('/api/')) {
        return await routeApi(request, env, url);
      }

      return json({ error: 'NOT_FOUND' }, 404);
    } catch (err) {
      const code = String(err?.message || err);

      if (code === 'UNAUTHENTICATED') return bad('UNAUTHENTICATED', 401);
      if (code === 'FORBIDDEN') return bad('FORBIDDEN', 403);
      if (code === 'JSON_REQUIRED') return bad('JSON_REQUIRED', 415);
      if (code === 'BODY_TOO_LARGE') return bad('BODY_TOO_LARGE', 413);

      return json({ error: 'INTERNAL_ERROR' }, 500);
    }
  }
};
