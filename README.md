# atproto-kotlin

Code-generated [AT Protocol](https://atproto.com) Kotlin Multiplatform SDK
for Bluesky and atproto apps. Parses the upstream `@atproto/lex` lexicon
corpus at build time and emits idiomatic Kotlin: immutable `data class`
records, sealed-equivalent open unions with `$type` dispatch, typed value
classes for every string format, and `suspend fun` XRPC service interfaces
ready to drop into a Ktor client.

[![CI](https://github.com/kikin81/atproto-kotlin/actions/workflows/ci.yaml/badge.svg)](https://github.com/kikin81/atproto-kotlin/actions/workflows/ci.yaml)
[![Release](https://github.com/kikin81/atproto-kotlin/actions/workflows/release.yaml/badge.svg)](https://github.com/kikin81/atproto-kotlin/actions/workflows/release.yaml)
[![Latest release](https://img.shields.io/github/v/release/kikin81/atproto-kotlin?label=release&color=blue)](https://github.com/kikin81/atproto-kotlin/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Modules

| Module | What it is |
| --- | --- |
| `:at-protocol-runtime` | KMP library (JVM + iOS) holding the hand-written base: typed value classes for every string format (`Did`, `Handle`, `AtUri`, `Cid`, `Datetime`, …), `sealed AtField<T>` for three-state optionality on mutation paths, `OpenUnionSerializer<T>` + `UnknownOpenUnionMember` for open-union `$type` dispatch, typed `Blob` + `CidLink`, and an `XrpcClient` built on Ktor with a pluggable `AuthProvider`. |
| `:at-protocol-generator` | JVM-only code generator. Parses the lexicon JSON corpus, resolves refs, tags mutation/read usage contexts, runs a verification pass (INV-1..4 + pair-keyed collision overrides), then emits idiomatic Kotlin via KotlinPoet: records, sealed-equivalent open unions, request/response pairs for queries and procedures, and `<Namespace>Service` classes wrapping `XrpcClient`. |
| `:at-protocol-models` | KMP library that picks up the generator's output at build time via `:at-protocol-generator:generateModels`. This is what downstream consumers depend on. |
| `:at-protocol-oauth` | JVM library implementing AT Protocol OAuth 2.0 for public clients: handle/DID/PDS/auth-server discovery, PAR with PKCE (S256) + DPoP (EC P-256), browser-based authorization, token exchange, and transparent refresh with DPoP-bound rotation. See [`at-protocol-oauth/README.md`](at-protocol-oauth/README.md). |
| `:samples:android` | **Reference consumer.** A minimal Compose app that authenticates via OAuth 2.0 + DPoP and renders a Bluesky timeline. Dogfoods the generated API surface and the OAuth module end-to-end. See [`samples/android/README.md`](samples/android/README.md). |

## Sample app

The Android reference consumer lives at [`samples/android/`](samples/android/).
It authenticates via **AT Protocol OAuth 2.0** (PAR + PKCE + DPoP) using
Chrome Custom Tabs and renders a feed from `app.bsky.feed.getTimeline`.

```bash
./gradlew :samples:android:installDebug
```

See [`samples/android/README.md`](samples/android/README.md) for run
instructions and architecture details.

## Getting started as a contributor

```bash
# 1. Install the upstream lexicon corpus (pinned by CID in lexicons.json).
cd at-protocol-generator && npx lex install --ci && cd -

# 2. Run the whole test suite (runtime, generator, sample, golden files).
./gradlew build
```

Local prerequisites: **JDK 17** (tracked by `.java-version` / `.sdkmanrc`),
**Node 22+** (for `npx lex install`), and the **Android SDK** if you want to
build the sample. Spotless + ktlint run on every commit via pre-commit hooks
and on every push via the CI workflow.

## Consuming the library

Every release is published to **Maven Central** ([`io.github.kikin81.atproto`](https://central.sonatype.com/namespace/io.github.kikin81.atproto))
and simultaneously to **GitHub Packages** as a secondary channel. For
almost every consumer, Maven Central is what you want — no credentials,
no extra repository configuration.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts (or the KMP module's common source set)
dependencies {
    implementation("io.github.kikin81.atproto:at-protocol-runtime:1.1.2")
    implementation("io.github.kikin81.atproto:at-protocol-models:1.1.2")
    implementation("io.github.kikin81.atproto:at-protocol-oauth:1.1.2") // OAuth 2.0 + DPoP
}
```

Check the [latest release](https://github.com/kikin81/atproto-kotlin/releases/latest)
for the current version. Artifacts are GPG-signed and include POM
metadata, Gradle Module Metadata, and sources JARs.

**iOS** consumers: only JVM + metadata publications are cut from Linux
CI runners today. The Kotlin Multiplatform Gradle Module Metadata
describes the JVM target and allows iOS consumers to resolve the
runtime dependency graph, but the iOS klibs themselves aren't on
Maven Central yet. A macOS release runner will land in a follow-up.

### GitHub Packages (pre-release / staging)

Every release is also pushed to [GitHub Packages](https://github.com/kikin81/atproto-kotlin/packages)
as a secondary channel. This is mostly useful if you want to pick up
the exact same artifacts without routing through Maven Central's CDN,
or for early-access testing of unreleased builds. **It requires
authentication with a GitHub PAT (`read:packages` scope) even for
public packages** — a persistent GitHub Packages limitation. Most
consumers should stick with Maven Central.

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/kikin81/atproto-kotlin")
    credentials {
        username = System.getenv("GITHUB_ACTOR") ?: "<your-github-username>"
        password = System.getenv("GITHUB_TOKEN") ?: "<your-pat-with-read-packages>"
    }
}
```

## Releases

Every push to `main` runs through `.github/workflows/release.yaml`, which
drives [`semantic-release`](https://semantic-release.gitbook.io/) via
`open-turo/actions-jvm/release`. The commit-analyzer reads
[Conventional Commits](https://www.conventionalcommits.org/) and cuts a
version on `feat:` / `fix:` / `BREAKING CHANGE`:

- `feat:` → minor bump (e.g. `1.1.2 → 1.2.0`)
- `fix:` → patch bump (e.g. `1.1.2 → 1.1.3`)
- `BREAKING CHANGE:` footer → major bump
- `chore:` / `ci:` / `docs:` / `test:` / `refactor:` → no release

The release workflow runs a single **release** job: semantic-release
analyzes commits, bumps `gradle.properties`, creates a git tag + GitHub
release, then runs `./gradlew publish` via the
`gradle-semantic-release-plugin` to upload artifacts to **both** GitHub
Packages and Maven Central (via the
[vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/)).

Currently Central uploads go into **USER_MANAGED** state (pending
release) — review in the [Central Portal deployments dashboard](https://central.sonatype.com/publishing/deployments)
and click **Publish** to promote to `repo1.maven.org`. Once the
pipeline has proven itself over a few more cycles, we'll flip
`automaticRelease = true` in `:at-protocol-runtime/build.gradle.kts`
and `:at-protocol-models/build.gradle.kts` so every version auto-releases.

## OpenSpec

This project uses [OpenSpec](https://github.com/kikin81/openspec)-style
change proposals under [`openspec/`](openspec/). Active work lives under
`openspec/changes/<name>/` with `proposal.md` + `design.md` +
`specs/<capability>/spec.md` + `tasks.md`; archived changes land under
`openspec/changes/archive/<date>-<name>/` and their requirements are
promoted into permanent main specs at `openspec/specs/<capability>/`.

Run `openspec list` to see active + archived changes, or
`openspec status --change <name>` for artifact-level progress.

## License

[MIT](LICENSE) © 2026 Francisco Velazquez. See [`LICENSE`](LICENSE) for the
full text.
