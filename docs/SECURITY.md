# Security and local device access

Netsira device integrations are read-only during Phase 2.

## Credentials

- Credentials are not stored by default.
- UI fields keep credentials only for the current process/session.
- Diagnostic reports must never include passwords, session cookies, CSRF values, or authorization tokens.

## Legacy local HTTP

Some airOS devices expose their management UI over plain HTTP or use a self-signed HTTPS certificate.

Android permits cleartext traffic because the device address is user-supplied and legacy LAN management is a core use case. Netsira never automatically sends device credentials to a discovered internet host. Users should prefer HTTPS where their device provides a usable certificate.

Desktop exposes an explicit checkbox before accepting an invalid/self-signed TLS certificate. Certificate validation remains enabled by default.

A future milestone should narrow cleartext policy further where platform APIs allow a practical per-LAN destination policy.
