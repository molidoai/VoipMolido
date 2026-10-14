PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS tenants (
 id TEXT PRIMARY KEY,
 name TEXT NOT NULL,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS users (
 id TEXT PRIMARY KEY,
 external_subject TEXT NOT NULL UNIQUE,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS tenant_members (
 tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 role TEXT NOT NULL CHECK(role IN ('owner','admin','member')),
 PRIMARY KEY(tenant_id,user_id)
);
CREATE TABLE IF NOT EXISTS extensions (
 tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
 extension TEXT NOT NULL,
 display_name TEXT NOT NULL,
 user_id TEXT REFERENCES users(id),
 PRIMARY KEY(tenant_id,extension)
);
CREATE INDEX IF NOT EXISTS idx_extensions_user ON extensions(user_id);
