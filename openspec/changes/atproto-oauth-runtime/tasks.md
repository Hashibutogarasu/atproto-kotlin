## 1. Module scaffolding

- [x] 1.1 Create `:at-protocol-oauth` as a Kotlin/JVM module in `at-protocol-oauth/`, add to `settings.gradle.kts`
- [x] 1.2 Add `nimbus-jose-jwt` (or `java.security`-only — decide after prototyping in 3.1) to the version catalog and module deps
- [x] 1.3 Add `implementation(project(":at-protocol-runtime"))` dependency (for `AuthProvider`, `XrpcClient`, Ktor client types)
- [x] 1.4 Wire `maven-publish` + vanniktech plugin so the module publishes alongside runtime + models
- [x] 1.5 Verify `./gradlew :at-protocol-oauth:compileKotlin` passes with an empty module

## 2. AuthProvider contract widening (BREAKING CHANGE)

- [x] 2.1 Design the new `AuthProvider` API: `suspend fun authHeaders(method: String, url: String): Map<String, String>` (or finalized alternative from prototyping)
- [x] 2.2 Update `AuthProvider` interface in `:at-protocol-runtime` with the new signature
- [x] 2.3 Update `BearerTokenAuth` → returns `mapOf("Authorization" to "Bearer $token")`
- [x] 2.4 Update `NoAuth` → returns `emptyMap()`
- [x] 2.5 Update `XrpcClient` internals to call `authHeaders(method, url)` and apply all returned headers to the request
- [x] 2.6 Update `:samples:android`'s `AtClientFactory` to use the new API
- [x] 2.7 Update all existing tests (runtime `XrpcClientTest`, sample `AtClientFactoryTest`) for the new contract
- [x] 2.8 Commit as `BREAKING CHANGE:` so semantic-release bumps the major version (1.x → 2.0.0)

## 3. DPoP JWT signer

- [x] 3.1 Prototype: build a minimal DPoP proof JWT with `java.security` EC P-256 (KeyPairGenerator + Signature + manual JWT header/payload/signature construction). Evaluate complexity vs. Nimbus JOSE+JWT. Make the build-vs-buy decision.
- [x] 3.2 Implement `DpopSigner` class: `generateKeyPair()`, `sign(method: String, url: String, accessTokenHash: String?, nonce: String?): String` returning a signed JWT string. JWT header: `{"typ":"dpop+jwt","alg":"ES256","jwk":<public-key>}`. JWT payload: `{"jti","htm","htu","iat","exp"(optional),"nonce"(if provided),"ath"(if provided)}`. Omit `iss` from all DPoP proofs.
- [x] 3.3 Implement JWK thumbprint computation (RFC 7638) for the DPoP proof header's `jwk` field
- [x] 3.4 Implement `ath` computation: SHA-256 hash of the access token, base64url-encoded (same as PKCE S256). Only included in DPoP proofs for PDS resource requests, NOT for PAR or token endpoint requests.
- [x] 3.5 Unit-test: generate a DPoP proof, decode it, verify ES256 signature, verify claims (`htm`, `htu`, `jti`, `iat`), verify JWK thumbprint matches the signing key
- [x] 3.6 Unit-test: DPoP proof with a server-issued nonce includes the `nonce` claim
- [x] 3.7 Unit-test: DPoP proof for PDS request includes `ath` claim; DPoP proof for token request omits `ath`
- [x] 3.8 Unit-test: DPoP proof omits `iss` claim

## 4. AT Protocol discovery chain

- [x] 4.1 Implement handle → DID resolution (DNS `_atproto.<handle>` TXT record → `did=did:plc:...`, with HTTP fallback via `/.well-known/atproto-did`)
- [x] 4.2 Implement DID → PDS resolution (fetch the DID document from `plc.directory` or the DID method's resolution endpoint, extract the `#atproto_pds` service endpoint)
- [x] 4.3 Implement PDS → authorization server resolution (fetch `<pds>/.well-known/oauth-protected-resource` → extract `authorization_servers[0]`, then fetch `<authserver>/.well-known/oauth-authorization-server` → extract `authorization_endpoint`, `token_endpoint`, `pushed_authorization_request_endpoint`, `dpop_signing_alg_values_supported`)
- [x] 4.4 Implement bidirectional handle verification: after resolving handle → DID, fetch the DID document and verify the `alsoKnownAs` field claims the original handle. Critical security requirement per spec.
- [x] 4.5 Implement hardened HTTP client for discovery: enforce timeouts, max response body size, URL validation, reject local/private IP ranges (SSRF mitigation)
- [x] 4.6 Unit-test the full discovery chain against MockEngine with canned DNS/HTTP responses
- [x] 4.7 Unit-test: handle that fails DNS resolution falls through to HTTP `/.well-known/atproto-did`
- [x] 4.8 Unit-test: missing or malformed discovery responses throw `OAuthDiscoveryException`
- [x] 4.9 Unit-test: bidirectional handle verification fails when DID document doesn't claim the handle

## 5. OAuth flow orchestrator

- [x] 5.1 Implement `AtOAuth` class with constructor taking `clientMetadataUrl: String` + `sessionStore: OAuthSessionStore` + optional `httpClient: HttpClient`
- [x] 5.2 Implement `beginLogin(handle: String): AuthorizationUrl` — runs the discovery chain, generates PKCE verifier+challenge, generates or loads DPoP keypair, sends PAR request with `login_hint=<handle>` + DPoP proof (handles the expected `use_dpop_nonce` 401 → retry cycle transparently), returns the authorization URL (`authorization_endpoint?client_id=...&request_uri=...`)
- [x] 5.3 Implement `completeLogin(redirectUri: Uri): OAuthSession` — validates `state` parameter matches the stored state, validates `iss` parameter matches the discovered authorization server, extracts the authorization code, exchanges it at the token endpoint with PKCE verifier + DPoP proof, verifies `sub` in token response matches the resolved DID, persists the session
- [x] 5.4 Implement `createClient(): XrpcClient` — constructs an `XrpcClient` with a `DpopAuthProvider` wired to the current session
- [x] 5.5 Implement `logout()` — clears the session from `OAuthSessionStore`
- [x] 5.6 Define `OAuthSessionStore` interface: `suspend fun load(): OAuthSession?`, `suspend fun save(session: OAuthSession)`, `suspend fun clear()`
- [x] 5.7 Define `OAuthSession` data class: `accessToken`, `refreshToken`, `dpopPrivateKey` (serialized), `dpopPublicKey` (serialized), `did`, `handle`, `pdsUrl`, `tokenEndpoint`, `authServerNonce`, `pdsNonce` (two separate nonces per spec)
- [x] 5.8 Unit-test the full `beginLogin → completeLogin → createClient` flow against MockEngine with canned PAR nonce-error + PAR success + token responses
- [x] 5.9 Unit-test: `completeLogin` rejects mismatched `iss` in callback URI
- [x] 5.10 Unit-test: `completeLogin` rejects mismatched `sub` in token response (throws `OAuthAccountMismatchException`)

## 6. DpopAuthProvider

- [x] 6.1 Implement `DpopAuthProvider(session: OAuthSession, signer: DpopSigner, sessionStore: OAuthSessionStore)` — implements the widened `AuthProvider.authHeaders(method, url)` returning `Authorization: DPoP <token>` + `DPoP: <proof>`. Uses `ath` (access token hash) in DPoP proofs for PDS requests. Uses the PDS nonce (not the auth server nonce) for PDS requests.
- [x] 6.2 Implement DPoP-Nonce tracking per-server: on 401 with `DPoP-Nonce` header from PDS, update the PDS nonce; on nonce from auth server (during refresh), update the auth server nonce. Retry with the updated nonce included in the next proof.
- [x] 6.3 Implement transparent refresh with mutex: on 401 indicating expired token, acquire a refresh lock (prevent concurrent refreshes), call the token endpoint with `grant_type=refresh_token` + DPoP proof (using auth server nonce), persist new tokens, release lock, retry the original request. Concurrent 401s wait for the first refresh to complete.
- [x] 6.4 Implement refresh failure: if the refresh token is invalid/revoked, clear the session and throw `OAuthSessionExpiredException`
- [x] 6.5 Unit-test: DPoP-Nonce rotation is transparent (consumer never sees the 401)
- [x] 6.6 Unit-test: auth server and PDS nonces are tracked independently
- [x] 6.7 Unit-test: expired access token triggers automatic refresh and retry
- [x] 6.8 Unit-test: concurrent expired-token 401s serialize into a single refresh call (mutex)
- [x] 6.9 Unit-test: revoked refresh token throws `OAuthSessionExpiredException`

## 7. Android sample migration

- [x] 7.1 Add `androidx.browser:browser` dependency to `:samples:android` for Custom Tabs
- [x] 7.2 Create `OAuthRedirectActivity` (or add intent filter to `MainActivity`) for the redirect URI scheme (e.g. `atproto-kotlin-sample://oauth-redirect`)
- [x] 7.3 Implement `AndroidOAuthSessionStore` wrapping `EncryptedSharedPreferences` — stores `OAuthSession` including the serialized DPoP keypair
- [x] 7.4 Replace `LoginScreen`'s `ServerService.createSession` call with `AtOAuth.beginLogin` → Custom Tabs launch
- [x] 7.5 Wire redirect capture: `OAuthRedirectActivity` receives the redirect URI and calls `AtOAuth.completeLogin`
- [x] 7.6 Replace `AtClientFactory.create(session)` with `oauth.createClient()` — the `DpopAuthProvider` is wired automatically
- [x] 7.7 Remove the `StopgapBanner` composable and all "NOT FOR PRODUCTION" references from `LoginScreen.kt`
- [x] 7.8 Remove the `NoXrpcParams`-based `ServerService.createSession` call path — it's no longer needed
- [x] 7.9 Update `FeedScreen` to handle `OAuthSessionExpiredException` by transitioning to `AppState.LoggedOut`
- [x] 7.10 Update `SessionStore` / `SessionStoreTest` to work with `OAuthSession` instead of the old `Session` data class

## 8. Client metadata

- [x] 8.1 Create a sample `client-metadata.json` with all required AT Protocol OAuth fields including `application_type: "native"`, `dpop_bound_access_tokens: true`, `token_endpoint_auth_method: "none"`, `grant_types: ["authorization_code", "refresh_token"]`, `redirect_uris`, `scope: "atproto transition:generic"`
- [x] 8.2 Host it at a public HTTPS URL (GitHub Pages under `kikin81.github.io` or similar)
- [x] 8.3 Document the required fields and hosting instructions in `at-protocol-oauth/README.md`
- [x] 8.4 Reference the hosted URL in `samples/android/MainActivity.kt`'s `AtOAuth` constructor

## 9. Documentation

- [x] 9.1 Write `at-protocol-oauth/README.md` covering: module purpose, consumer integration example, client metadata setup, redirect URI configuration, security considerations (DPoP, PKCE, custom-scheme risks)
- [x] 9.2 Update `samples/android/README.md` to reflect the OAuth flow (remove app-password references, add Custom Tabs setup, add redirect URI scheme)
- [x] 9.3 Update root `README.md` module table to include `:at-protocol-oauth`

## 10. Testing + verification

- [x] 10.1 Full MockEngine integration test: `beginLogin → (mock browser redirect) → completeLogin → createClient → FeedService.getTimeline` — end-to-end with canned responses
- [x] 10.2 `./gradlew :at-protocol-oauth:test` passes all unit tests
- [x] 10.3 `./gradlew :samples:android:testDebugUnitTest` passes with OAuth-migrated tests
- [x] 10.4 `./gradlew :samples:android:assembleDebug` produces an APK
- [x] 10.5 Manual test: install on emulator/device, log in with a real Bluesky account via Custom Tabs, verify feed renders
- [x] 10.6 `./gradlew spotlessCheck` green across the whole repo
- [x] 10.7 `pre-commit run --all-files` green

## 11. Publication

- [ ] 11.1 Verify `./gradlew :at-protocol-oauth:publishToMavenLocal` produces a valid artifact
- [x] 11.2 Commit the `AuthProvider` change as `BREAKING CHANGE:` to trigger major version bump (2.0.0)
- [ ] 11.3 After release, verify `io.github.kikin81.atproto:at-protocol-oauth:2.0.0` resolves from `mavenCentral()`
- [x] 11.4 Update the root README's consumer snippet to include the OAuth module coordinate
