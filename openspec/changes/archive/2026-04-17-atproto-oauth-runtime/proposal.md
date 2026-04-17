## Why

The Android sample authenticates via `com.atproto.server.createSession`
with app-passwords — deprecated by Bluesky, explicitly labeled "NOT FOR
PRODUCTION" in the sample's README and login screen. Every real third-party
AT Protocol app must use the standard OAuth 2.0 flow (PAR + PKCE + DPoP).
Without an OAuth module in the SDK, consumers either reimplement the full
spec from scratch or can't ship production apps at all.

The runtime already has a pluggable `AuthProvider` interface on `XrpcClient`,
and the Android sample already has `SessionStore` / `AtClientFactory` wiring.
The OAuth module slots into these existing contracts: it implements
`AuthProvider` with DPoP proof-of-possession and adds the browser-based
authorization dance that produces the tokens.

## What Changes

- **New module: `:at-protocol-oauth`** — JVM-only (not KMP initially; iOS
  deferred to avoid the expect/actual crypto puzzle). Contains:
  - AT Protocol OAuth flow orchestrator: handle → DID → PDS →
    authorization-server discovery, PAR request, PKCE S256
    challenge generation, authorization URL construction, token exchange
  - DPoP JWT signer using `java.security` EC P-256 (NIST P-256 / secp256r1)
  - `DpopAuthProvider : AuthProvider` that attaches `Authorization: DPoP <token>`
    + a fresh DPoP proof JWT on every XRPC request, handling server-issued
    `DPoP-Nonce` rotation transparently
  - Session model: access token + refresh token + DPoP keypair +
    PDS URL + DID + handle, with refresh-on-401 and DPoP-bound rotation
  - `OAuthSessionStore` interface for persistence (consumers provide the
    platform-specific impl)

- **Android sample migration**: replace the `createSession` app-password
  flow in `:samples:android` with the new OAuth flow. Custom Tabs for the
  browser step, redirect via intent filter, `EncryptedSharedPreferences`
  for the DPoP keypair + token persistence. The "NOT FOR PRODUCTION"
  banner goes away.

- **Client metadata prerequisite**: consumers must host a static JSON file
  at a public URL declaring their `client_id`, `redirect_uris`, supported
  grant types, DPoP requirement, and requested scopes. Documented as a
  manual prerequisite, not automated. For the sample, hosted on GitHub Pages
  or a simple static site.

- **New dependency**: evaluate `com.nimbusds:nimbus-jose-jwt` for JWT/JWK/DPoP
  signing vs. raw `java.security`. Nimbus is the de-facto JVM JWT library
  (used by Spring Security OAuth, Keycloak, etc.) and handles the ES256
  JWK-to-JWT signing, JWK thumbprint computation, and DPoP proof
  construction natively. If `java.security` alone suffices with reasonable
  code, prefer that to minimize the dependency tree.

## Capabilities

### New Capabilities

- `oauth-flow`: The end-to-end AT Protocol OAuth 2.0 authorization flow
  (discovery → PAR → browser → token exchange → session), the DPoP proof
  signer, and the `DpopAuthProvider` that plugs into `XrpcClient`.
- `oauth-android-integration`: Android-specific OAuth wiring (Custom Tabs
  launcher, redirect intent handling, encrypted session persistence) and
  the migration of `:samples:android` off app-passwords.

### Modified Capabilities

- `atproto-runtime`: The `AuthProvider` interface contract may need a
  minor extension — currently `bearerToken(): String?` returns a plain
  token, but DPoP auth needs to attach BOTH a DPoP proof header AND
  the token. If the existing contract can't express this, a delta spec
  adds a second method or widens the return type. Investigate during
  design; may end up being zero runtime changes if the `DpopAuthProvider`
  can monkey-patch the headers via Ktor's request pipeline instead.

## Impact

- **New module**: `:at-protocol-oauth` (Kotlin/JVM, added to
  `settings.gradle.kts`).
- **New dependency** (oauth module only): nimbus-jose-jwt or equivalent.
  Does NOT propagate to `:at-protocol-runtime` or `:at-protocol-models`
  — the OAuth module is an optional add-on consumers pull when they want
  OAuth, not a transitive dependency of the core library.
- **Modified module**: `:samples:android` — LoginScreen rewrites from
  app-password to OAuth browser flow, SessionStore gains DPoP fields,
  the "NOT FOR PRODUCTION" banner is removed.
- **Possibly modified**: `:at-protocol-runtime` — if `AuthProvider`
  needs a DPoP-aware extension. Minimal surface change, backward
  compatible.
- **External prerequisite**: consumers must host a `client-metadata.json`
  at a public HTTPS URL before OAuth works. Documented in the sample
  README and the module's own README.
- **Unblocks**: production-ready third-party Bluesky/atproto apps.
  The Android sample becomes a reference for "how to ship a real app."
