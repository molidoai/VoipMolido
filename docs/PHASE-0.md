# PHASE 0 — Architecture and lock criteria

## Scope
Cloudflare Worker + D1 for future tenant-aware control plane; static PWA shell; future Android client and WebRTC media. SIP trunking and third-party PBX integration require an actual SIP/RTP-compatible service later.

## Threat model
- Every query must be scoped by authenticated tenant membership, never client-provided tenant alone.
- QR provisioning tokens must be single-use, expiring and revocable; never embed long-lived passwords.
- Secrets belong in Cloudflare secrets, never in GitHub.
- Use TLS, rate limiting, audit events, strict CORS and input validation.
- TURN may be required for reliable WebRTC connectivity and can incur cost.

## Phase lock
[ ] Authentication implemented and tested
[ ] Tenant isolation tests
[ ] QR replay/expiry tests
[ ] Worker API deployment tested
[ ] GitHub Actions green
[ ] Explicit approval before merging to main

Current state: NOT LOCKED. Protected APIs intentionally unavailable.
