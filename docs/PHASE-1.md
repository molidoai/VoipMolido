# PHASE 1 — Installable web preview

## Implemented
- Responsive RTL Persian navigation, demo dashboard, extensions, provisioning and compatibility views.
- Installable PWA metadata and offline static asset cache (requires HTTPS).
- No live data, SIP, WebRTC media or credential provisioning is represented as active.

## Security gates
- No stored credentials or hardcoded SIP passwords.
- Static demo is safe to host; Worker protected APIs still return 501.
- Service worker only caches same-origin static GET requests; never caches /api/ routes.
- Before production: identity provider, session management, tenant authorization, secure QR, TURN, abuse controls, cross-device testing.

## Acceptance tests
- `cd worker && npm test`
- `node --check web/app.js && node --check web/sw.js`
- `node --test web/test/*.test.js`
- Real Android/iOS install and offline checks still pending.

## Deploy
- GitHub Pages: publish `/web` using GitHub Actions (workflow included). Public demo is non-functional telephony.
- Cloudflare Worker is optional for this preview; it remains fail-closed.
- Do not treat GitHub Pages as a SIP server.
