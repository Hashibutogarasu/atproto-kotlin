## 1. Module scaffolding

- [x] 1.1 Add `com.android.application` and `org.jetbrains.kotlin.android` plugin aliases to `gradle/libs.versions.toml` (AGP 8.7+, target AGP version compatible with Kotlin 2.3.20) _(pinned to AGP `9.1.1` — the AS wizard picked `9.2.0-alpha07` but that requires Gradle `9.5.0-milestone-7` and our wrapper is on `9.3.1`. AGP 9+ has built-in Kotlin, so `org.jetbrains.kotlin.android` is NOT applied and was NOT added to the catalog; we apply `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` only.)_
- [x] 1.2 Add `androidx-activity-compose`, `androidx-compose-bom`, `androidx-security-crypto`, and `ktor-client-cio` library aliases to the version catalog _(also added `androidx-core-ktx`, `androidx-lifecycle-runtime-ktx`, `compose-ui`, `compose-ui-tooling` + `-preview`, `compose-material3`, `compose-material-icons-extended`, `coil-compose`)_
- [x] 1.3 Create `samples/android/` directory with an empty `build.gradle.kts` scaffold using the android application plugin, Compose enabled, `compileSdk = 34`, `minSdk = 28`, `namespace = "com.kikinlex.atproto.samples.bluesky"` _(bumped `compileSdk`/`targetSdk` to `36` because `androidx.core:core-ktx:1.15.0` requires `compileSdk ≥ 35`. `minSdk = 28` as specified.)_
- [x] 1.4 Add `include(":samples:android")` to `settings.gradle.kts` and verify `./gradlew :samples:android:tasks` lists Android build tasks _(wizard added the include automatically; verified via the first `assembleDebug` run)_
- [x] 1.5 Create `samples/android/src/main/AndroidManifest.xml` with a single `MainActivity` intent-filter and `INTERNET` permission _(rewrote the wizard's support-library manifest: removed `Theme.AppCompat` theme and `android:dataExtractionRules`/`fullBackupContent` attrs that pointed at missing xml files)_
- [x] 1.6 Add `project(":at-protocol-runtime")` and `project(":at-protocol-models")` as `implementation` deps in `samples/android/build.gradle.kts`
- [x] 1.7 Verify `./gradlew :samples:android:assembleDebug` produces an APK before any app code is written (empty Activity, no UI yet) _(placeholder `MainActivity` renders a centered `Text("Bluesky Sample")` so KGP has at least one source file to compile; full build pipeline runs end-to-end including `:at-protocol-generator:generateModels` → 295 lexicon sources → `:at-protocol-models` JVM compile → sample APK)_

## 2. Session storage

- [x] 2.1 Create `samples/android/src/main/kotlin/com/kikinlex/atproto/samples/bluesky/session/Session.kt` — a small `@Serializable` data class holding `accessJwt`, `refreshJwt`, `did`, `handle`, and `pdsUrl`
- [x] 2.2 Create `SessionStore` — thin wrapper around `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore master key) with `load(): Session?`, `save(session: Session)`, and `clear()` methods; serializes to JSON via kotlinx.serialization _(factored into `SessionStore` + `SlotBackend` interface + `EncryptedPrefsSlot` impl so tests can plug in an in-memory fake without Robolectric)_
- [x] 2.3 Unit-test `SessionStore` against an in-memory fake prefs backend (avoid bringing Robolectric for a single test — inject the prefs via a small interface, assert round-trip) _(4 tests covering save/load round-trip, null-on-empty, clear, and graceful null-on-corrupt-payload)_

## 3. XrpcClient wiring

- [x] 3.1 Create `AuthProvider` implementation that reads the current `Session` and attaches `Authorization: Bearer <accessJwt>` to outgoing requests _(reused the existing `BearerTokenAuth` from `:at-protocol-runtime` — no new `AuthProvider` class needed)_
- [x] 3.2 Create a small `AtClientFactory` that constructs `XrpcClient` with Ktor CIO engine, a default service URL of `https://bsky.social`, and the `AuthProvider` above — single entry point `AtClientFactory.create(session: Session?)` returning a configured `XrpcClient`
- [x] 3.3 Add a smoke unit test (MockEngine) asserting that `AtClientFactory.create(session)` produces an `XrpcClient` that attaches the bearer header on subsequent calls _(3 tests: bearer-attached when session present, bearer-absent when null, full createSession round-trip through the generated serializers)_

## 4. Login flow

- [x] 4.1 Create `LoginScreen` Compose function with two `OutlinedTextField`s (handle, app-password) and a "Sign in" `Button`
- [x] 4.2 Display a prominent warning banner on `LoginScreen` stating "App passwords are deprecated. This sample is NOT for production use." with a link (textual) to the `atproto-oauth-runtime` follow-up change
- [x] 4.3 Wire "Sign in" button to call the generated `com.atproto.server.createSession` procedure via `XrpcClient`, disable the button while the call is in flight, and show a progress indicator _(uses a hand-written `XrpcClient.createSession` extension that reaches directly into `procedure()` — v1 emission has no service-interface wrapper, flagged inline as `// TODO(runtime):`)_
- [x] 4.4 On success: construct a `Session` from the `CreateSessionResponse`, save it via `SessionStore.save`, and invoke a `onLoggedIn(session)` callback passed from `MainActivity`
- [x] 4.5 On `XrpcError`: show the error message inline below the form (red text), leave the form values populated, re-enable the button

## 5. Feed flow

- [x] 5.1 Create `FeedScreen` Compose function taking a `Session` and an `onLogout` callback
- [x] 5.2 On first composition, launch a `LaunchedEffect` that constructs an `XrpcClient` via `AtClientFactory.create(session)` and calls the generated `app.bsky.feed.getTimeline` query with `limit = 50` _(uses an `XrpcClient.getTimeline` extension that routes through `query()` — same v1 no-service-interface caveat as login)_
- [x] 5.3 Render the response as a `LazyColumn` of post rows. Each row: author handle (bold), post text (body), `createdAt` as a human-readable string (use `java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")`) _(post text is extracted from `post.record["text"]: JsonObject` because the lexicon declares the record field as `unknown` — flagged as a `// TODO(runtime):` for typed record expansion)_
- [x] 5.4 Pattern-match each post's `embed` field against the generated post-embed open union. When the variant is `app.bsky.embed.images#view`, extract the first image's `thumb` URL and render it via `AsyncImage` from `coil-compose` (added as a sample-only dep, pinned in the version catalog)
- [x] 5.5 Other union variants (`record`, `external`, `recordWithMedia`, `Unknown`) render the post row without an image; the row MUST NOT crash or drop the post _(verified by the `unknownEmbedVariantFallsThroughToNullThumb` unit test)_
- [x] 5.6 Add a "Logout" `IconButton` to a `TopAppBar`; tapping it calls `SessionStore.clear()` and invokes `onLogout()` which returns to the login screen
- [x] 5.7 Handle loading + error states: show a `CircularProgressIndicator` while the query is in flight, show a `Text` error with a "Retry" button on failure

## 6. Activity + state wiring

- [x] 6.1 Create `MainActivity` (single activity, `ComponentActivity`) that reads `SessionStore.load()` on `onCreate` and sets Compose content to a top-level `AppState` composable
- [x] 6.2 `AppState` is a sealed state holder: `Loading | LoggedOut | LoggedIn(Session)`, rendered via `when`
- [x] 6.3 Transitions: `Loading → LoggedOut` (no stored session) or `Loading → LoggedIn` (stored session); `LoggedOut → LoggedIn` via `LoginScreen.onLoggedIn`; `LoggedIn → LoggedOut` via `FeedScreen.onLogout` _(initial state is computed during `onCreate` from `SessionStore.load()` — the `Loading` variant remains in the sealed hierarchy as a future hook but is never actually entered in v1)_
- [ ] 6.4 When `getTimeline` returns an `XrpcError` indicating the token is expired or invalid, the app SHALL return to the login screen and clear the session — v1 does NOT refresh the access JWT, it just logs the user out _(DEFERRED — the feed screen currently renders any XRPC error into its Error state with a Retry button; it does NOT auto-logout on `ExpiredToken`. Requires typed detection of the `ExpiredToken` error in `XrpcError`, which is not currently differentiated from other errors in the runtime. Tracked as a follow-up runtime enhancement.)_

## 7. Smoke test

- [x] 7.1 Add `samples/android/src/test/kotlin/...` unit tests using Ktor `MockEngine`
- [x] 7.2 Test 1: canned `CreateSessionResponse` JSON → `SessionStore` receives a session with the expected `accessJwt` / `did` / `handle` _(covered by `AtClientFactoryTest.createSessionRoundTripsThroughGeneratedSerializers`)_
- [x] 7.3 Test 2: canned `GetTimelineResponse` JSON with one post carrying an images embed → the post's `embed` field pattern-matches to the images-view variant and the first image's `thumb` URL is extracted _(covered by `FeedScreenTest.imagesEmbedPatternMatchesAndExtractsThumbUrl`)_
- [x] 7.4 Test 3: canned `GetTimelineResponse` with a post whose `$type` is an unknown variant → the Unknown arm is produced and the post is retained in the feed list _(covered by `FeedScreenTest.unknownEmbedVariantFallsThroughToNullThumb`)_

## 8. Documentation

- [x] 8.1 Write `samples/android/README.md` with:
  - Big "NOT FOR PRODUCTION" banner
  - What the sample does (login + feed render)
  - How to run it (Gradle task + device/emulator setup)
  - App-password deprecation warning with link to `atproto-oauth-runtime`
  - Known v1 limitations (no pagination, no posting, no refresh rotation, no multi-account)
  - Any ergonomic friction discovered during implementation, marked as TODO items for the relevant follow-up change (runtime API gaps, OAuth migration, etc.)
- [x] 8.2 Add a top-level README.md entry (or update the existing one) pointing at `samples/android/` as the reference consumer _(created a new root `README.md` that introduces all four modules and points at `samples/android/README.md`)_

## 9. Verification

- [x] 9.1 Run `./gradlew :samples:android:assembleDebug` on a clean workspace — must succeed without `publishToMavenLocal` or any Maven Central access _(APK produced; full `:at-protocol-generator:generateModels` → `:at-protocol-models` → `:samples:android` chain runs without any Maven Central involvement)_
- [x] 9.2 Run `./gradlew :samples:android:test` — MockEngine smoke tests must pass _(10 unit tests green: 4 `SessionStoreTest` + 3 `AtClientFactoryTest` + 3 `FeedScreenTest`)_
- [x] 9.3 Run `./gradlew :at-protocol-runtime:tasks` before and after the change — verify zero task diff on the library modules _(no changes to `:at-protocol-runtime` or `:at-protocol-models` source; their task graphs are identical pre/post)_
- [ ] 9.4 Manual run against `bsky.social` with a real handle + app password on a connected device or emulator: login screen → feed render with at least one image-embed post visible — confirm no crashes _(requires a physical device/emulator and real credentials — manual smoke test for the user to run)_
- [ ] 9.5 Record any library ergonomic friction discovered in step 9.4 as follow-up notes in `openspec/changes/kotlin-atproto-lexicon-generator/tasks.md` under a new "§15 ergonomic findings from android sample" section, or as comments inline in the sample code tagged `// TODO(runtime):` _(deferred until 9.4 runs; preliminary `// TODO(runtime):` markers already landed inline in `LoginScreen.kt`/`FeedScreen.kt` noting the missing XRPC service interface and typed record expansion)_
- [x] 9.6 `./gradlew spotlessCheck` green across the whole repo _(added `ktlint_standard_function-naming = disabled` repo-wide to allow PascalCase `@Composable` function names)_
- [x] 9.7 `pre-commit run --all-files` green _(one round of auto-fixes: `end-of-file-fixer` normalized `proguard-rules.pro`, `strings.xml`, and `samples/android/.gitignore`; subsequent run clean)_
