## Context

The kotlin-atproto-lexicon-generator change lands the runtime, generator,
and models modules, but no downstream consumer has ever touched the emitted
API outside unit/golden/smoke tests. We need a concrete dogfood target that
forces us to type against generated classes in a real app, run against a
live AT Protocol server, and surface ergonomic issues we can't detect from
tests alone. The sample also serves as §14.3 validation for the generator
change (scratch consumer calls `getTimeline` against the public AppView)
without blocking on Maven Central publishing.

Android is the target because (a) Kikinlex's intended client is Android,
(b) Android uses the JVM variant of our KMP library so no new targets are
required on the runtime or models modules, and (c) Compose + Activity +
EncryptedSharedPreferences gives us just enough UI + storage surface to
exercise the library without bringing in a ton of ceremony.

Auth is the hard part. AT Protocol's blessed auth is OAuth 2.0 + PAR + PKCE
+ DPoP, which is a week of work in crypto, hosted client metadata, browser
integration, and nonce rotation — most of which has nothing to do with
what this sample is actually validating. App-password (`createSession`)
is deprecated but trivial: one POST, two JWTs, plain Bearer header. We
accept the stopgap cost explicitly to unblock the dogfood now, and track
OAuth as a separate `atproto-oauth-runtime` change.

## Goals / Non-Goals

**Goals:**

- Prove end-to-end that a consumer can `project(":at-protocol-models")`,
  `project(":at-protocol-runtime")`, construct an `XrpcClient`, log in, and
  render a timeline without hand-editing generated code or writing glue
  that should live in the library.
- Exercise the parts of the generated API that are easy to get wrong and
  hard to test: `AtField<T>` on optional post fields, open-union dispatch
  on `app.bsky.embed.images#view` / `embed.record#view` / etc., `Blob`
  typing on image thumbnails, `Datetime` on `createdAt`, `StrongRef` on
  replies.
- Produce a reference wiring consumers can copy: `AuthProvider`
  implementation, session persistence, Ktor CIO engine configuration, XRPC
  error handling at the UI layer.
- Keep the sample code readable — a developer opening
  `samples/android/src/main/kotlin/` should be able to understand the
  whole app in under 10 minutes.

**Non-Goals:**

- **Production auth.** App-password is a stopgap. No attempt at OAuth,
  DPoP, PKCE, PAR, or hosted client metadata. Migration to OAuth happens
  in a separate change.
- **Pagination, refresh-on-pull, infinite scroll.** First 50 posts only.
- **Posting, liking, following, threading, notifications.** Read-only.
- **Rich UI.** No theme, no dark mode polish, no Material 3 niceties
  beyond the defaults. The sample is a functional test, not a design
  showcase.
- **Multi-account / account switching.** One session slot in
  EncryptedSharedPreferences, single-user.
- **Offline cache.** Every feed render hits the network.
- **Navigation library** (androidx.navigation, Voyager, Decompose, etc.).
  Single Activity with Compose `when` on an in-memory auth state.
- **DI framework** (Hilt, Koin, Anvil). Manual wiring in `MainActivity`.
- **Adding an Android target to `:at-protocol-runtime` or
  `:at-protocol-models`.** Both modules stay KMP-jvm+ios; Android consumes
  the JVM variant. AGP lives only in `samples/android/`.
- **Session refresh.** When the access JWT expires, the app logs the user
  out and returns to the login screen. Refresh-token rotation is a
  runtime-side enhancement, tracked separately.
- **Instrumented tests.** No Espresso, no Compose UI tests. A unit test
  that constructs `XrpcClient` with a `MockEngine` and asserts the wiring
  behaves is sufficient for this sample.

## Decisions

### Decision 1: App-password auth via the generated `createSession` procedure

`com.atproto.server.createSession` is already in the emitted models. The
sample calls it via `XrpcClient`, stores the returned `accessJwt` +
`refreshJwt` + `did` + `handle` + `pdsUrl` in `EncryptedSharedPreferences`,
and constructs an `AuthProvider` that attaches `Authorization: Bearer
<accessJwt>` on every subsequent call.

**Alternatives considered:**

- **Full OAuth 2.0 + DPoP flow.** Rejected as a v1 scope — adds ~a week of
  crypto + Custom Tabs + client metadata hosting, none of which exercises
  the codegen. Tracked as the separate `atproto-oauth-runtime` change
  which will also migrate this sample.
- **Backend-mediated auth** (Kikinlex-hosted token broker that holds a
  confidential client registration). Rejected: requires shipping a backend
  before shipping an SDK sample, inverts the AT Protocol data model.
- **Manually pasting an access token into the app.** Rejected: bypasses
  the `createSession` generated call entirely, which is half the point of
  the dogfood.

### Decision 2: Depend on `project(":at-protocol-runtime")` and `project(":at-protocol-models")` directly

The sample lives in the same Gradle build as the library, so it consumes
both modules via `project(...)` deps. This gives us incremental dogfooding
without waiting for Maven Central publication (§14.1/§14.2 of the
generator change), and every generator change is immediately visible in
the sample on the next build.

**Alternatives considered:**

- **Consume published artifacts from `mavenCentral()`.** Rejected: blocks
  on §14 publishing, slows the feedback loop, and makes the sample stop
  working whenever a generator change lands but isn't yet republished.
  Worth doing as a follow-up once the library is actually published, but
  not now.
- **Consume published artifacts from a local Maven repo
  (`publishToMavenLocal`).** Rejected: adds a build step
  (`./gradlew publishToMavenLocal` before `./gradlew :samples:android:…`)
  that's easy to forget and hard to debug when stale. `project(...)` deps
  are automatic and correct.

### Decision 3: `EncryptedSharedPreferences` for session storage

`androidx.security:security-crypto` ships `EncryptedSharedPreferences` with
AES-256-GCM encryption keyed off a master key in the Android Keystore.
It's the standard Android answer to "store a token at rest" and it's a
~20-line integration. The sample stores a single JSON blob containing
`{accessJwt, refreshJwt, did, handle, pdsUrl}`, loaded on
`MainActivity.onCreate` to decide whether to show the login or feed screen.

**Alternatives considered:**

- **Plain `SharedPreferences`.** Rejected: storing JWTs in plaintext on
  disk is a teaching-by-bad-example anti-pattern, even for a sample.
- **Android Keystore directly** (no SharedPreferences). Rejected: more
  code than EncryptedSharedPreferences for no benefit at this scope.
- **DataStore with a custom Serializer.** Rejected: more boilerplate than
  warranted; DataStore shines for reactive reads, we just need one load
  on startup and one save on login.

### Decision 4: Single Activity + Compose `when` state, no navigation library

`MainActivity` holds an in-memory `AuthState` (sealed: `Loading | LoggedOut
| LoggedIn(session)`) and renders either `LoginScreen` or `FeedScreen`
based on it. State transitions happen through callback lambdas passed into
the screens. No `Navigation`, no `ViewModel`, no `StateFlow` plumbing
library beyond stdlib + Compose.

**Alternatives considered:**

- **`androidx.navigation` compose.** Rejected: two screens don't justify a
  navigation library. Adds a declarative layer that obscures the direct
  callback path from "login succeeded" → "show feed".
- **ViewModel + StateFlow.** Rejected for the same reason: two screens and
  one network call don't warrant the ceremony. If the sample grows, this
  is the first thing to add.
- **Decompose / Voyager / Circuit.** Same rejection — the sample is
  deliberately minimal and opinionated toward "library first, framework
  second."

### Decision 5: Ktor CIO engine

The runtime's `XrpcClient` takes a Ktor `HttpClient` (engine-agnostic).
Android has three viable engines: OkHttp (native Android HTTP stack),
CIO (Ktor's pure-Kotlin engine), and Android (deprecated). CIO is the
choice because it's pure Kotlin, has no OkHttp version coupling, and is
the same engine the runtime's own tests use via MockEngine. OkHttp would
be fine in real production apps — noted in the README.

**Alternatives considered:**

- **OkHttp.** Reasonable alternative; most Android apps already have it.
  Slightly more realistic for production but adds a dependency the
  library itself doesn't care about and doesn't demonstrate anything the
  library does differently.
- **Android engine.** Deprecated upstream, ignored.

### Decision 6: AGP lives only in `samples/android/`, runtime and models stay KMP-jvm+ios

Adding an `android()` target to `:at-protocol-runtime` or
`:at-protocol-models` would require AGP in the root build, which ripples
into every subproject and forces consumers who don't care about Android
to pay an AGP setup tax. Instead, `samples/android/` is a standalone AGP
module that depends on the JVM variant of the KMP library. The
runtime/models KMP setup stays exactly as it is.

**Alternatives considered:**

- **Add `androidTarget()` to the KMP modules.** Rejected per above — adds
  AGP to the root build. Will be reconsidered once we actually need
  Android-specific code in the runtime (OAuth's Custom Tabs / Keystore).
  At that point an `:at-protocol-runtime-android` split is more likely
  than a full KMP `androidTarget()`.

## Risks / Trade-offs

- **App-password is deprecated.** → Loudly label it in the sample README,
  in a code comment above the `createSession` call, and in the login
  screen text. Track the OAuth migration as a hard dependency on any
  "production-ready" label for the sample.
- **Compose ergonomic gaps may be mistaken for library gaps.** → Keep
  Compose code aggressively minimal. Any friction in
  `AtField` / union dispatch / Blob handling that shows up in the sample
  should be attributable to the library surface, not UI scaffolding.
- **Network-dependent sample is fragile in CI.** → The sample itself isn't
  CI-gated. A smoke unit test in the sample module constructs
  `XrpcClient` with `MockEngine` and verifies the login + feed flow works
  against canned responses. The live-server path is developer-only.
- **AGP introduces its own Gradle complexity.** → Keep the Android
  module's `build.gradle.kts` as small as possible, pin AGP + Kotlin
  versions through the existing version catalog, and don't share
  configuration with the KMP modules. If AGP breaks the root build, we
  revert just the sample module without touching the library.
- **The sample's session store uses a single hardcoded slot.** →
  Documented as a simplification. Multi-account is an explicit non-goal.
- **Running against the real `bsky.social` AppView may hit rate limits.**
  → The sample pages through 50 posts once per login, not continuously.
  Developers hitting a limit will see it in the error UI and can retry.

## Migration Plan

Greenfield sample module, no existing code, no migration. Staging:

1. Create `samples/android/` with a bare AGP + Compose skeleton, wired into
   `settings.gradle.kts`. Verify `./gradlew :samples:android:build` is
   green before adding any app code.
2. Implement the session store (`EncryptedSharedPreferences` wrapper +
   `AuthProvider` bridge). Unit-test in isolation.
3. Implement the `createSession` login path. Test against `bsky.social`
   from a developer machine with a real handle + app password.
4. Implement the `getTimeline` feed render path. Iterate on the Compose
   post-row layout until the open-union dispatch (`embed: PostEmbedUnion`)
   feels right. Document any ergonomic friction in comments as
   `// TODO(oauth-change): …` or `// TODO(runtime): …` so the
   follow-up changes pick them up.
5. Add the unit smoke test (MockEngine) before merging.
6. Write the README with the app-password stopgap warning.

Rollback is trivial — delete `samples/android/` and remove one line from
`settings.gradle.kts`.

## Open Questions

- **AGP version.** We'll pin AGP 8.7+ for Kotlin 2.3.20 compatibility, but
  the exact minor is TBD until the first build run. Decide during task 1.
- **Redirect URI.** App-password flow has no redirect, so this is moot for
  v1. When OAuth lands, we'll need to register a redirect scheme (probably
  `kikinlex-sample://oauth-redirect`) and add an intent filter. Tracked
  in `atproto-oauth-runtime`.
- **Client metadata hosting for OAuth.** GitHub Pages under
  `kikinlex/kikinlex` is the likely target. Decide during the OAuth
  change, not here.
- **Do we demonstrate the contextual-split case** (a mutation object with
  an `AtField<T>`)? The sample is read-only, so no. We'll rely on the
  golden files and generator tests to cover that path. If the sample is
  later extended with a "create post" screen, that's the natural place to
  exercise `AtField` wrapping.
