## Context

AT Protocol's OAuth 2.0 profile for public clients (mobile/desktop apps)
combines several modern web-security standards into a single flow:

1. **Handle → DID → PDS → Authorization Server discovery** — the client
   doesn't hardcode any auth URL; it resolves the user's handle to a DID
   (via DNS or HTTP), the DID to a PDS (via the DID document), and the
   PDS to an authorization server (via `/.well-known/oauth-protected-resource`
   → `/.well-known/oauth-authorization-server`).

2. **Pushed Authorization Requests (PAR)** — the client pushes the
   authorization request payload to the auth server's PAR endpoint,
   getting back a `request_uri` that the browser presents to the user.
   This keeps the authorization URL short and avoids leaking sensitive
   parameters in browser history.

3. **PKCE (S256)** — standard Proof Key for Code Exchange. The client
   generates a `code_verifier`, derives a `code_challenge` via SHA-256,
   sends the challenge in the PAR request, and proves possession of the
   verifier during token exchange.

4. **DPoP (Demonstrating Proof of Possession)** — every token request
   AND every subsequent XRPC request must carry a DPoP proof JWT signed
   by a client-held EC P-256 private key. The access token is *bound* to
   that key; a stolen token without the key is useless. The auth server
   may also issue a `DPoP-Nonce` that must be echoed in the next proof.

5. **Browser-based authorization** — on Android, the client opens a
   Custom Tab to the auth server's `/authorize` endpoint. The user
   authenticates on their PDS's web UI. On approval, the PDS redirects
   back to the app's registered redirect URI (deep link or custom scheme).

6. **Token lifecycle** — access tokens are short-lived (~1 hour). Refresh
   tokens are long-lived and DPoP-bound. The client refreshes
   transparently when the access token expires. Refresh tokens rotate on
   each use.

The existing `:at-protocol-runtime` module provides `XrpcClient` with a
pluggable `AuthProvider` interface (`bearerToken(): String?`), but that
interface is too narrow for DPoP: it only returns a token string, whereas
DPoP requires BOTH an `Authorization: DPoP <token>` header AND a separate
`DPoP: <proof-jwt>` header on every request. The design must either widen
`AuthProvider` or bypass it.

## Goals / Non-Goals

**Goals:**

- A consumer can log in to any AT Protocol PDS (not just `bsky.social`)
  using the standard OAuth flow, from a Kotlin/JVM or Android app, with
  ~10 lines of integration code.
- The DPoP proof is generated transparently on every request — the
  consumer never handles JWTs, nonces, or key management directly.
- Session refresh (access token expiry → automatic refresh with the
  DPoP-bound refresh token) is transparent. The consumer's XRPC call
  retries automatically after a 401 → refresh cycle.
- The module is an *optional add-on*: `implementation("io.github.kikin81.atproto:at-protocol-oauth:$version")`.
  The runtime and models modules don't depend on it. Consumers who don't
  need OAuth (e.g. server-to-server with service tokens) aren't forced
  to pull it in.
- The Android sample migrates off app-passwords and becomes a production-
  quality reference for OAuth integration.

**Non-Goals:**

- **Confidential client support.** Server-side apps that hold a client
  secret / signed client assertion are out of scope. This module targets
  *public clients* only (no client secret, PKCE required).
- **iOS-specific adapters.** `ASWebAuthenticationSession`, Keychain, and
  any Apple-platform crypto are deferred. The module is JVM-only; iOS
  consumers can use it via KMP's JVM variant on server, but native iOS
  OAuth requires a future `:at-protocol-oauth-ios` module.
- **KMP expect/actual for crypto.** EC P-256 signing uses `java.security`
  directly. A future KMP abstraction can wrap it, but the initial
  delivery is JVM-only.
- **Client metadata hosting automation.** The consumer must host
  `client-metadata.json` themselves. We document the required shape and
  provide a sample file, but don't ship a hosting solution.
- **CBOR, firehose, subscriptions.** Unrelated protocols, out of scope.
- **Token storage encryption.** The module defines an `OAuthSessionStore`
  interface; the *implementation* (e.g. `EncryptedSharedPreferences` on
  Android) is the consumer's responsibility. The sample provides a
  reference implementation.

## Decisions

### Decision 1: JVM-only module, not KMP

EC P-256 signing requires platform-specific crypto. On JVM/Android,
`java.security.KeyPairGenerator` + `java.security.Signature` handle
everything natively. On iOS, you'd need CommonCrypto or Security
framework. Rather than adding an expect/actual layer for the initial
delivery, ship a JVM-only `:at-protocol-oauth` module that Android
consumes directly (Android IS JVM). iOS gets its own module later.

**Alternatives considered:**

- **KMP with expect/actual crypto.** Rejected for v1: the crypto surface
  is small (key generation + JWT signing), but the testing and CI matrix
  doubles (macOS runners for iOS tests). Ship JVM-only, learn from the
  API surface, then extract to KMP when an iOS consumer actually exists.
- **Third-party KMP crypto library** (`dev.whyoleg.cryptography`,
  `ArtifactKt/swift-klib-plugin`, etc.). Rejected: adds an unstable
  dependency for a small surface area. `java.security` is battle-tested
  and sufficient.

### Decision 2: Widen AuthProvider to support DPoP headers

The current `AuthProvider.bearerToken(): String?` returns a single token
string. The `XrpcClient` attaches it as `Authorization: Bearer <token>`.
DPoP requires TWO headers: `Authorization: DPoP <token>` (note: `DPoP`
scheme, not `Bearer`) and `DPoP: <proof-jwt>`.

**Approach:** add a second method to `AuthProvider`:

```kotlin
public fun interface AuthProvider {
    public suspend fun bearerToken(): String?

    // New: called by XrpcClient to get additional headers. Default
    // returns empty (backward compatible for BearerTokenAuth / NoAuth).
    public suspend fun additionalHeaders(): Map<String, String> = emptyMap()
}
```

`DpopAuthProvider` overrides both: `bearerToken()` returns the access
token (XrpcClient prefixes it with `DPoP` instead of `Bearer` based on
the presence of `additionalHeaders`), and `additionalHeaders()` returns
`{"DPoP": "<proof-jwt>"}`.

Actually, a cleaner approach: change `AuthProvider` to return the full
`Authorization` header value (including the scheme) instead of just the
token:

```kotlin
public fun interface AuthProvider {
    public suspend fun authHeaders(method: String, url: String): Map<String, String>
}
```

This is more general: `BearerTokenAuth` returns `{"Authorization": "Bearer <token>"}`,
`DpopAuthProvider` returns `{"Authorization": "DPoP <token>", "DPoP": "<proof>"}`,
`NoAuth` returns empty map. The `method` and `url` parameters are needed
because the DPoP proof JWT includes the HTTP method and target URL as claims.

**This is a breaking change** to `AuthProvider`. Existing consumers using
`BearerTokenAuth` or custom `AuthProvider` impls must update. Since the
library is pre-1.0 semantics (we just cut 1.1.3 but the API hasn't
stabilized), this is acceptable. Provide a migration guide.

**Alternatives considered:**

- **Keep `bearerToken()` and add DPoP as a Ktor plugin.** Rejected:
  couples the OAuth module to Ktor internals (feature plugins, pipeline
  interception) and makes the auth logic invisible to consumers who want
  to debug or extend it. The `AuthProvider` interface is the established
  extension point.
- **Have `DpopAuthProvider` wrap the `HttpClient` instead of
  `AuthProvider`.** Rejected: violates the `XrpcClient` contract which
  expects auth to flow through `AuthProvider`. Two auth mechanisms in
  parallel (interface + Ktor plugin) would be confusing.

### Decision 3: Nimbus JOSE+JWT for JWT/JWK operations

AT Protocol DPoP requires constructing ES256-signed JWTs with specific
claims (`htm`, `htu`, `ath`, `jti`, `iat`, `nonce`) and a JWK thumbprint
in the header. `java.security` can sign bytes with ECDSA, but building
the JWT header/payload/signature structure, computing JWK thumbprints
(RFC 7638), and handling the ES256 → DER → JWS conversion is ~200 lines
of manual code that's easy to get wrong.

Nimbus JOSE+JWT (`com.nimbusds:nimbus-jose-jwt`) is the standard JVM JWT
library: used by Spring Security, Keycloak, and most Java OAuth stacks.
It handles JWT construction, ES256 signing, JWK generation, and
thumbprint computation natively. The dependency is ~500 KB, well-
maintained, and has zero transitive dependencies beyond `json-smart`.

**Alternatives considered:**

- **Raw `java.security` + manual JWT construction.** Rejected: the code
  is testable but brittle. JWT header encoding, ES256 signature format
  (IEEE P1363 vs DER), and JWK thumbprint computation are all fiddly
  crypto plumbing that Nimbus has already solved with thousands of
  downstream consumers validating it daily.
- **`jose4j`.** Viable alternative to Nimbus; slightly less popular but
  also well-maintained. Nimbus wins on ecosystem familiarity.
- **`kotlinx-crypto` (hypothetical).** Doesn't exist as a first-party
  JetBrains library. No Kotlin-native JWT library is mature enough.

### Decision 4: Android Custom Tabs, not WebView

The browser-based authorization step opens the auth server's `/authorize`
URL. On Android, the options are Custom Tabs (`androidx.browser:browser`)
or a WebView. Custom Tabs are strongly preferred:

- The user sees their existing browser session (cookies, saved passwords)
  — they may already be logged in to their PDS.
- The browser's security sandbox is separate from the app — no credential
  leakage to the embedding app.
- Google's OAuth policy explicitly prohibits WebView-based OAuth for
  third-party apps.
- AT Protocol's OAuth spec follows the same IETF BCP 212 recommendation.

**Alternatives considered:**

- **WebView.** Rejected: security risk (app can inject JS and intercept
  credentials), violates platform guidelines, and the AT Protocol
  community explicitly discourages it.

### Decision 5: Redirect via custom scheme, not App Links

Android redirect URIs can use either a custom scheme
(`kikinlex-sample://oauth-redirect`) or an App Link
(`https://app.kikinlex.com/oauth/redirect` with
`assetlinks.json` verification). Custom schemes are simpler:

- No domain ownership verification required.
- No `assetlinks.json` hosting.
- Works on emulators and debug builds without extra setup.
- Sufficient for AT Protocol OAuth (the redirect carries an
  authorization code, not sensitive data).

App Links are more secure (verified domain ownership prevents other apps
from intercepting the redirect), but the additional setup isn't justified
for a sample app. Document both options in the README.

**Alternatives considered:**

- **App Links.** Rejected for v1: requires domain ownership, hosting
  `assetlinks.json`, and debug-vs-release signing key management. Add
  as an optional advanced configuration in the README.

### Decision 6: Session refresh is transparent, retry-on-401

When `XrpcClient.query()` or `.procedure()` gets a 401 response:

1. `DpopAuthProvider` intercepts the 401.
2. If the response includes a `DPoP-Nonce` header, update the stored
   nonce and retry the request with a new DPoP proof (nonce rotation).
3. If the 401 indicates an expired access token, call the token endpoint
   with the refresh token + a fresh DPoP proof to get a new access/refresh
   pair. Retry the original request.
4. If refresh fails (e.g. refresh token revoked), clear the session and
   signal the consumer to re-authenticate.

This retry logic lives inside `DpopAuthProvider`, not in `XrpcClient`.
The consumer sees either a successful response or an "authentication
required" exception — never an intermediate 401 from token expiry.

**Alternatives considered:**

- **Expose 401s to the consumer and let them decide.** Rejected: every
  consumer would reimplement the same retry logic. Transparent refresh
  is the standard pattern in every OAuth SDK (AppAuth, MSAL, etc.).
- **Proactive refresh** (refresh before expiry based on `exp` claim).
  Nice-to-have but not v1: adds timer management, background threading,
  and a dependency on parsing the JWT `exp` claim. Retry-on-401 is
  simpler and covers all cases.

## Risks / Trade-offs

- **`AuthProvider` breaking change.** → Existing consumers using
  `BearerTokenAuth` or custom impls must update. Migration is mechanical
  (wrap the token string in a map with `"Authorization"` key). Document
  in the release notes as a `BREAKING CHANGE:` commit so semantic-release
  bumps the major version.
- **Nimbus dependency weight.** → ~500 KB jar, acceptable for a JVM
  module. Does NOT propagate to runtime or models (the OAuth module is
  an optional add-on).
- **Custom scheme redirect hijacking.** → A malicious app could register
  the same custom scheme and intercept the redirect. PKCE mitigates this:
  the interceptor can't exchange the authorization code without the
  `code_verifier`. Document the risk and recommend App Links for
  production apps that want verified-domain redirects.
- **DPoP-Nonce clock skew.** → If the client's clock is significantly
  off from the auth server's, the `iat` claim in the DPoP proof will be
  rejected. Mitigation: use the server's `Date` response header to
  compute a clock offset and adjust `iat` accordingly. Implement in v1.
- **Refresh token rotation failure.** → If the refresh request fails
  partway (network error after the server rotated but before the client
  received the new token), the session is irrecoverable. Mitigation:
  persist the refresh token BEFORE sending the refresh request, and
  persist the new tokens AFTER receiving them. If the client crashes
  mid-rotation, the old refresh token is still valid for one more attempt
  (AT Protocol servers typically allow a short grace period for the
  previous refresh token).

## Migration Plan

1. **Land `:at-protocol-oauth` module** with the OAuth flow, DPoP signer,
   and `DpopAuthProvider`. Unit-test the full flow against MockEngine
   with canned PAR/token/refresh responses. No Android integration yet.

2. **Widen `AuthProvider`** in `:at-protocol-runtime` to the new
   `authHeaders(method, url)` contract. Update `BearerTokenAuth`,
   `NoAuth`, and `XrpcClient` internals. Update the sample's
   `AtClientFactory`. This is the `BREAKING CHANGE:` commit.

3. **Migrate `:samples:android`** to OAuth. Replace `LoginScreen`'s
   `createSession` call with `AtOAuth.beginLogin()` → Custom Tabs →
   `AtOAuth.completeLogin()`. Update `SessionStore` to persist the
   DPoP keypair alongside the tokens. Remove the "NOT FOR PRODUCTION"
   banner. Add the redirect Activity + intent filter.

4. **Host client metadata** for the sample at a public URL (GitHub Pages
   or similar). Document the required JSON shape in the module's README.

5. **End-to-end test** against a real PDS. Log in with a real Bluesky
   account, fetch the timeline, verify the feed renders. This is the
   acceptance test.

Rollback: each step is a separate commit. If OAuth breaks, revert to
the app-password flow (the `createSession` code is still in the codebase
until the migration is confirmed working).

## Open Questions

- **`AuthProvider` contract shape.** The exact method signature
  (`authHeaders(method, url)` vs. `applyAuth(requestBuilder)` vs.
  something else) needs prototyping. The design above is a proposal;
  the implementation may find a cleaner API once the DPoP proof
  construction is coded up.
- **Nimbus vs. java.security.** Prototype both approaches during the
  DPoP signer task and make a final call based on code complexity and
  test coverage. If `java.security` is clean enough (<100 lines of JWT
  construction), prefer it over the Nimbus dependency.
- **Client metadata hosting.** GitHub Pages under `kikin81.github.io`
  is the likely target for the sample's `client-metadata.json`. The
  exact domain and path are TBD. This is a prerequisite for the
  end-to-end test but doesn't block any code work.
- **Redirect URI scheme for the sample.** Probably
  `atproto-kotlin-sample://oauth-redirect`. Needs to be globally unique
  enough to avoid collision with other AT Protocol apps.
- **Should `:at-protocol-oauth` be published to Maven Central?** Yes,
  as an optional artifact alongside runtime and models. Same
  vanniktech pipeline, same `publishToMavenCentral` task.

## Corrections from Official Spec Research

After reviewing the AT Protocol OAuth spec (https://atproto.com/specs/oauth)
and Bluesky's practical guide (https://docs.bsky.app/docs/advanced-guides/oauth-client),
several details in the original design above need correction or addition:

### DPoP is required on PAR requests, not just token + XRPC

The original design only mentioned DPoP on token exchange and XRPC calls.
The spec requires a DPoP proof header on the **PAR request itself**. The
flow is: first PAR request is sent without a nonce → server responds
with HTTP 401 + `use_dpop_nonce` error + `DPoP-Nonce` header → client
retries the PAR with the nonce included in the DPoP proof. This nonce-
discovery-via-error is the **expected happy path**, not an error case.

### Two separate DPoP nonce stores, not one

The authorization server (token endpoint) and the resource server (PDS
XRPC endpoints) issue **independent** `DPoP-Nonce` values. The module
must track nonces per-server, not globally. Typically this means storing
`auth_server_nonce` and `pds_server_nonce` separately in the session.

### `ath` (access token hash) only on PDS resource requests

The DPoP proof JWT includes an `ath` claim (SHA-256 hash of the access
token, base64url-encoded) **only when making resource server (PDS) requests**,
not when calling the PAR or token endpoints. During PAR and token exchange,
there's no access token yet, so `ath` is omitted.

### `iss` claim should be omitted from DPoP proofs

The spec says "Currently clients SHOULD NOT include this value, or any
value, in the `iss` field" for DPoP JWTs sent to the PDS. Omit `iss`
from all DPoP proofs.

### `exp` claim is optional but recommended

DPoP proof JWTs should include an `exp` claim with a near-future
expiration. The spec doesn't mandate it but recommends it for defense
against proof replay.

### Token response includes `sub` (DID) — must verify

The token exchange response includes a `sub` field containing the
authorized account's DID (e.g. `did:plc:exampleuserid123`). The client
**MUST** verify that this matches the DID resolved during the discovery
chain. A mismatch means the user authorized a different account than
expected — the client must reject the session.

### Callback includes `iss` (authorization server) — must verify

The redirect URI back from the authorization server includes `iss` (the
authorization server's issuer URL) alongside `code` and `state`. The
client **MUST** verify that `iss` matches the authorization server URL
discovered via the resource server metadata. This prevents mix-up
attacks where a malicious auth server redirects to a legitimate app.

### `login_hint` parameter in PAR request

The PAR request should include `login_hint=<handle-or-did>` to pre-fill
the authorization server's login form. Not strictly required but improves
UX when the client already knows the user's handle.

### Client metadata requires `application_type: "native"`

The `client-metadata.json` for native/mobile apps must include
`"application_type": "native"`. This field was missing from the original
design's metadata example. Full required fields:

```json
{
  "client_id": "https://app.example.com/oauth/client-metadata.json",
  "application_type": "native",
  "client_name": "...",
  "client_uri": "...",
  "dpop_bound_access_tokens": true,
  "grant_types": ["authorization_code", "refresh_token"],
  "redirect_uris": ["com.example.app://oauth/callback"],
  "response_types": ["code"],
  "scope": "atproto transition:generic",
  "token_endpoint_auth_method": "none"
}
```

### Refresh token mutex

"Care must be taken to ensure that concurrent resource requests don't
result in concurrent token refresh requests." The `DpopAuthProvider`
must use a mutex/lock to serialize refresh operations. Two concurrent
401s from expired tokens must not produce two concurrent refresh calls
— the second must wait for the first's result.

### Hardened HTTP client

All HTTP requests in the discovery chain must use a hardened client:
timeouts, resource limits (max response body size), URL validation, and
rejection of requests to local/private IP ranges (SSRF mitigation).

### `token_type` in response is `"DPoP"`, not `"Bearer"`

Confirms the `Authorization: DPoP <token>` scheme (not `Bearer`). The
original design had this right; documenting explicitly for clarity.
