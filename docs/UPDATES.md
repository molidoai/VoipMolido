# MOLIDO update policy (PHASE 1)

## PWA
On HTTPS, the service worker is registered. It checks for a new worker when the page becomes visible and once an hour while open. A new worker replaces old cached assets and refreshes the page. Navigations use network-first with offline fallback. On iOS, update checks depend on Safari/WebKit lifecycle; an update cannot be guaranteed while the PWA is closed. This preview has no active calls, but future releases must defer reloads while a call is active.

## Android (planned, not implemented)
The native APK will use signed GitHub Releases and a signed release manifest with versionCode, APK URL, SHA-256, and minimum supported version. Updates will be downloaded over HTTPS, hash-verified, and installed only after Android user confirmation. GitHub Actions will build and sign the APK after signing secrets are configured; do not commit keystores or credentials. Silent installation is not available to ordinary apps.

## Limitations
This is a preview, not a functioning SIP/PBX or Android application. GitHub Pages is static hosting; it cannot host a persistent SIP registrar or media relay.
