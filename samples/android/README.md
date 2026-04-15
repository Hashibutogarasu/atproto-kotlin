# Bluesky Sample (Android)

> ## ⚠️ NOT FOR PRODUCTION
>
> This sample authenticates with **app passwords**, which are deprecated by
> Bluesky. OAuth 2.0 + DPoP is the blessed authentication path for third-party
> apps and is tracked as a separate change: **`atproto-oauth-runtime`**. Do
> not ship an app built on this sample's auth flow to real users.
>
> The sample exists to dogfood the code-generated AT Protocol API surface
> (`:at-protocol-runtime` + `:at-protocol-models`) against a live server.
> Every ergonomic friction you hit here is a gap we want to close in the
> library — please open a follow-up or leave a `// TODO(runtime):` comment.

## What it does

One-Activity Compose app with two screens:

1. **Login** — handle + app password, calls the generated
   `com.atproto.server.createSession` procedure, persists the returned
   `{accessJwt, refreshJwt, did, handle}` in `EncryptedSharedPreferences`.
2. **Feed** — calls the generated `app.bsky.feed.getTimeline` query with
   `limit = 50`, renders a `LazyColumn` of posts showing the author handle,
   post text, `createdAt`, and (if present) the first image thumbnail from
   the `app.bsky.embed.images#view` arm of the open-union `embed` field.

## Running it

### Prerequisites

- A local Android SDK with at least API 36 installed (Android Studio
  installs this automatically; see `$ANDROID_HOME`).
- A Bluesky account with an **app password**. Generate one at
  [bsky.app → Settings → Privacy and security → App passwords](https://bsky.app/settings/app-passwords).
  Do **not** use your main account password.
- The generator's lexicon corpus installed:

  ```bash
  cd at-protocol-generator && npx lex install --ci && cd -
  ```

### Build and install

```bash
./gradlew :samples:android:installDebug
```

Launch **Bluesky Sample** from your device or emulator's app drawer. On the
login screen:

- **Handle**: `alice.bsky.social` (no leading `@`)
- **App password**: `xxxx-xxxx-xxxx-xxxx`

On success you're dropped into the feed. Tap the log-out icon in the
top-right to clear the stored session.

## Unit tests

```bash
./gradlew :samples:android:testDebugUnitTest
```

The sample ships MockEngine-backed JVM unit tests covering:

- `SessionStoreTest` — save/load/clear round-trip against an in-memory
  `SlotBackend` (no Robolectric).
- `AtClientFactoryTest` — wires the factory to a `MockEngine`, verifies the
  `Bearer <accessJwt>` header is attached when a `Session` is provided
  and omitted when `null`, and round-trips a canned
  `CreateSessionResponse` through the generated serializers.
- `FeedScreenTest` — validates open-union pattern matching against three
  canned `GetTimelineResponse` payloads: an `ImagesView` embed (thumb URL
  extracted), a forward-compat `Unknown` embed (falls through cleanly,
  post is retained), and a post with no embed at all.

None of the unit tests require a device, an emulator, or network access.

## Known v1 limitations

- **App-password only.** OAuth migration is a separate change.
- **No pagination / no pull-to-refresh / no infinite scroll.** First 50
  posts, once per login.
- **Read-only.** No posting, liking, following, or notifications.
- **Single-account.** One session slot in `EncryptedSharedPreferences`; no
  account switching.
- **No session refresh.** When the access JWT expires, the user is logged
  out and returns to the login screen. Refresh-token rotation is tracked
  as a runtime-side enhancement (outside this sample's scope).
- **Blob rendering.** v1 emits typed `Blob` references, but only image
  thumbnails on `ImagesView` embeds are rendered — record quotes,
  external links, video, and record-with-media embeds render the post
  text without a preview and do not crash.
- **Hand-rolled XRPC calls.** The sample reaches directly into
  `XrpcClient.query()` / `XrpcClient.procedure()` with the generated
  serializers. When the code generator's XRPC service-interface wrapper
  lands (`kotlin-atproto-lexicon-generator §13 polish`), these become
  one-liner calls like `client.feedGetTimeline(request)` and the
  `// TODO(runtime):` helpers in `LoginScreen.kt` / `FeedScreen.kt` go
  away.

## Architecture

```
samples/android/src/main/kotlin/com/kikinlex/atproto/samples/bluesky/
├── MainActivity.kt         # single Activity, Compose AppState dispatch
├── AppState.kt             # sealed: Loading | LoggedOut | LoggedIn
├── session/
│   ├── Session.kt          # @Serializable session data class
│   └── SessionStore.kt     # EncryptedSharedPreferences + SlotBackend interface
├── atproto/
│   └── AtClientFactory.kt  # XrpcClient with Ktor CIO + BearerTokenAuth
└── ui/
    ├── LoginScreen.kt      # handle + app-password form + createSession call
    └── FeedScreen.kt       # LazyColumn feed + getTimeline + embed dispatch
```

No DI framework, no navigation library, no ViewModel layer — state lives in
`remember { mutableStateOf(...) }` inside `MainActivity` and transitions
through callback lambdas. See the `samples-android-bluesky-feed` OpenSpec
change for the design rationale.
