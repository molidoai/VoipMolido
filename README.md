# MOLIDO Universal VoIP — PHASE 0 + PHASE 1 preview

A no-VPS **demo/control-plane starter**, not a production VoIP server.

## Included
- `web/`: installable RTL Persian PWA **preview**, offline static shell, example extension list. No real calls or QR provisioning.
- `worker/`: Cloudflare Worker health endpoint and deliberately disabled authenticated APIs; D1 multi-tenant schema draft.
- `.github/workflows/validate.yml`: Node tests and syntax checks.
- `.github/workflows/pages.yml`: manually triggered static PWA deployment via GitHub Pages (enable Pages > GitHub Actions first).
- `docs/`: implementation boundaries, security and phase checklists.

## Test
`cd worker && npm test` then `node --test ../web/test/*.test.js`.

## Publish from a phone
1. Extract the ZIP and upload **its contents** (not the outer directory) to repository root, preserving `.github/`.
2. GitHub > Settings > Pages > Build and deployment > GitHub Actions.
3. Actions > Deploy static PWA preview > Run workflow.
4. Visit the resulting HTTPS GitHub Pages URL; use browser Add to Home Screen where supported.

## Limitations
GitHub Pages cannot operate a SIP registrar/media relay. Native Android APK, iOS native binary, multi-tenant authentication, WebRTC signaling, TURN, SIP trunk and 3CX provisioning are **not implemented**. Cross-vendor compatibility must be verified per product/version. No secrets or demo passwords are provided.

## Automatic updates (preview)
The PWA now checks for new deployments when reopened and periodically while running; see `docs/UPDATES.md`. Android APK updates are specified but **not implemented** yet.
