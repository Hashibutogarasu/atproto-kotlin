## Why

We've landed the runtime, generator, and 295 generated models, but we have
zero evidence that a real consumer can actually *use* the generated API. Unit
tests and golden files prove the emitter is correct in the abstract; they do
not surface ergonomic gaps that only appear when someone types
`client.getTimeline(...)` in a real screen and tries to render the result.
A minimal Android sample closes that gap, dogfoods the codegen output against
a live Bluesky AppView, and gives the kotlin-atproto-lexicon-generator change
its §14.3 "scratch consumer calls `app.bsky.feed.getTimeline`" validation
without gating on Maven publishing.

## What Changes

- Add a new top-level module `samples/android/` wired into
  `settings.gradle.kts` as `:samples:android`.
- Module consumes `project(":at-protocol-runtime")` and
  `project(":at-protocol-models")` directly — no Maven coordinates, no
  publication dependency. This is the first time a downstream consumer
  exercises the generated surface end-to-end.
- Single-Activity Android app built with Jetpack Compose. Two screens:
  1. **Login** — text fields for handle + app password, "Sign in" button,
     calls the generated `com.atproto.server.createSession` procedure via
     `XrpcClient`.
  2. **Feed** — on successful login, calls the generated
     `app.bsky.feed.getTimeline` query and renders a `LazyColumn` of posts
     showing author handle, post text, `createdAt` timestamp, and the first
     image thumbnail if an `app.bsky.embed.images#view` embed is present.
- App-password auth only. The `{accessJwt, refreshJwt}` returned by
  `createSession` is stored in `EncryptedSharedPreferences` and injected
  into `XrpcClient`'s `AuthProvider` as a plain `Authorization: Bearer
  <accessJwt>` header. No DPoP, no OAuth, no refresh rotation in v1.
- Use Ktor CIO engine for the XrpcClient on Android.
- No DI framework, no navigation library — one `Activity` + Compose
  `when` on an in-memory auth state holder. Keeps the sample readable and
  the ergonomic gaps attributable to the library, not the scaffolding.
- Sample README loudly labels app-password as a stopgap and points at the
  follow-up `atproto-oauth-runtime` change as the blessed path. The sample
  is explicitly **NOT** for production use.
- Android SDK 34 minimum, AGP setup lives inside `samples/android/` only —
  does **NOT** add an android target to `:at-protocol-runtime` or
  `:at-protocol-models`. Those remain KMP-jvm+ios as configured; Android
  consumes the JVM variant.

## Capabilities

### New Capabilities

- `android-sample`: Minimal Android reference app demonstrating how a
  consumer wires `:at-protocol-runtime` + `:at-protocol-models` into a
  real UI, covering the login → session persistence → authenticated XRPC
  call → feed render loop.

### Modified Capabilities

None at the proposal stage. If real use surfaces concrete gaps in the
runtime's `AuthProvider` or `XrpcClient` contracts (e.g., session refresh
hooks, typed session state, better handling of `ExpiredToken` errors), we'll
add delta specs during implementation and amend the proposal.

## Impact

- **New module**: `samples/android/` (Kotlin + Compose + AGP).
- **Build**: `settings.gradle.kts` gains one `include(":samples:android")`.
- **Dependencies added (sample module only)**: Android Gradle Plugin,
  `androidx.compose.*`, `androidx.activity:activity-compose`,
  `androidx.security:security-crypto` for `EncryptedSharedPreferences`,
  Ktor CIO client engine.
- **No changes to existing modules.** `:at-protocol-runtime` and
  `:at-protocol-models` stay exactly as they are. If anything needs to
  change there it's a separate delta spec.
- **Unblocks**: §14.3 of the kotlin-atproto-lexicon-generator change
  ("scratch KMP project can consume both artifacts and call
  `app.bsky.feed.getTimeline` against the public Bluesky AppView") is
  effectively satisfied by this sample running against a real account.
- **Does not unblock**: Maven Central publishing (§14.1, §14.2). Those
  remain separate because the sample uses `project(...)` deps.
- **Follow-up tracked separately**: `atproto-oauth-runtime` change will
  add `:at-protocol-oauth` (PAR + PKCE + DPoP) and migrate this sample
  off app-password. Scope for that change: EC P-256 signing, JWT/DPoP
  proof generation, Custom Tabs launcher, hosted client-metadata.json,
  `DpopAuthProvider` wiring. Explicitly out of scope here.
