PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (
 id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE COLLATE NOCASE,
 password_hash TEXT NOT NULL, password_salt TEXT NOT NULL,
 password_iterations INTEGER NOT NULL DEFAULT 210000,
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled')),
 failed_login_count INTEGER NOT NULL DEFAULT 0, locked_until TEXT,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS tenants (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, slug TEXT NOT NULL UNIQUE COLLATE NOCASE,
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled')),
 created_by TEXT NOT NULL REFERENCES users(id), created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS tenant_members (
 tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 role TEXT NOT NULL CHECK(role IN ('owner','admin','operator','user')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,user_id)
);
CREATE TABLE IF NOT EXISTS extensions (
 tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 extension TEXT NOT NULL, display_name TEXT NOT NULL,
 user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled')),
 caller_id_privacy INTEGER NOT NULL DEFAULT 0 CHECK(caller_id_privacy IN (0,1)),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,extension)
);
CREATE TABLE IF NOT EXISTS sessions (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash TEXT NOT NULL UNIQUE, expires_at TEXT NOT NULL,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS audit_logs (
 id TEXT PRIMARY KEY, tenant_id TEXT REFERENCES tenants(id) ON DELETE SET NULL,
 actor_user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
 action TEXT NOT NULL, target_type TEXT, target_id TEXT,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pbx_settings (
 tenant_id TEXT PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
 timezone TEXT NOT NULL DEFAULT 'UTC', language TEXT NOT NULL DEFAULT 'fa',
 outbound_prefix TEXT, recording_enabled INTEGER NOT NULL DEFAULT 0 CHECK(recording_enabled IN (0,1)),
 updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS dids (
 id TEXT PRIMARY KEY, tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 number TEXT NOT NULL, label TEXT,
 destination_type TEXT NOT NULL DEFAULT 'extension' CHECK(destination_type IN ('extension','ivr','external')),
 destination_value TEXT, status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','disabled')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(tenant_id,number)
);
CREATE TABLE IF NOT EXISTS trunks (
 id TEXT PRIMARY KEY, tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 name TEXT NOT NULL, registrar TEXT, auth_username TEXT,
 transport TEXT NOT NULL DEFAULT 'tls' CHECK(transport IN ('tls','tcp','udp')),
 port INTEGER, status TEXT NOT NULL DEFAULT 'disabled' CHECK(status IN ('active','disabled')),
 credential_status TEXT NOT NULL DEFAULT 'not_configured' CHECK(credential_status IN ('not_configured','configured')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ivrs (
 id TEXT PRIMARY KEY, tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 name TEXT NOT NULL, welcome_prompt TEXT, timeout_seconds INTEGER NOT NULL DEFAULT 8,
 invalid_destination TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ivr_options (
 ivr_id TEXT NOT NULL REFERENCES ivrs(id) ON DELETE CASCADE,
 digit TEXT NOT NULL, destination_type TEXT NOT NULL CHECK(destination_type IN ('extension','ivr','external')),
 destination_value TEXT NOT NULL, PRIMARY KEY(ivr_id,digit)
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_members_user ON tenant_members(user_id);
CREATE INDEX IF NOT EXISTS idx_extensions_user ON extensions(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_time ON audit_logs(tenant_id,created_at);
CREATE INDEX IF NOT EXISTS idx_dids_tenant ON dids(tenant_id);
CREATE INDEX IF NOT EXISTS idx_trunks_tenant ON trunks(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ivrs_tenant ON ivrs(tenant_id);
