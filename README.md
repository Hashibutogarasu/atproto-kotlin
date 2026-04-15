# kikinlex

Kikinlex's [AT Protocol](https://atproto.com) Kotlin Multiplatform SDK — a
code-generated client library consuming the upstream Bluesky / `@atproto/lex`
lexicon corpus.

## Modules

| Module | What it is |
| --- | --- |
| `:at-protocol-runtime` | KMP library (JVM + iOS) holding the hand-written base: typed value classes for string formats (`Did`, `Handle`, `AtUri`, `Cid`, `Datetime`, …), `sealed AtField<T>` for three-state optionality on mutation paths, `OpenUnionSerializer<T>` + `UnknownOpenUnionMember` for open-union `$type` dispatch, typed `Blob` + `CidLink`, and an `XrpcClient` built on Ktor with a pluggable `AuthProvider`. |
| `:at-protocol-generator` | JVM-only code generator. Parses the lexicon JSON corpus, resolves refs, tags mutation/read usage contexts, runs a verification pass (INV-1..4 + pair-keyed collision overrides), then emits idiomatic Kotlin via KotlinPoet (`@Serializable data class` records, sealed-equivalent open unions, request/response pairs for queries and procedures). |
| `:at-protocol-models` | KMP library skeleton that picks up the generator's output at build time via `:at-protocol-generator:generateModels`. This is what downstream consumers depend on. |
| `:samples:android` | **Reference consumer.** A minimal Compose app that logs in to Bluesky (app-password — see note below) and renders a timeline. Dogfoods every interesting part of the generated API surface: `AtField`, open unions on embeds, typed `Blob`, `Datetime`. See [`samples/android/README.md`](samples/android/README.md). |

## Getting started

```bash
# 1. Install the upstream lexicon corpus (pinned by CID in lexicons.json).
cd at-protocol-generator && npx lex install --ci && cd -

# 2. Run the whole test suite.
./gradlew build
```

## Sample app

[`samples/android/`](samples/android/) is the reference consumer. It
authenticates via the deprecated app-password flow (a stopgap — OAuth is
tracked separately as the `atproto-oauth-runtime` OpenSpec change) and
renders a feed from `app.bsky.feed.getTimeline`. **Do not ship a production
app built on this sample's auth flow.**

```bash
./gradlew :samples:android:installDebug
```

See [`samples/android/README.md`](samples/android/README.md) for run
instructions and known v1 limitations.

## OpenSpec

All in-flight changes live under [`openspec/changes/`](openspec/changes/).
The two active changes are:

- `kotlin-atproto-lexicon-generator` — the runtime, generator, models,
  and gradle wiring. Most tasks shipped.
- `samples-android-bluesky-feed` — the Android reference consumer.

Run `openspec list` to see state.
