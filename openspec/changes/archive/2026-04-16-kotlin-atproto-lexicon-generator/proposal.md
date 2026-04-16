## Why

Building an Android Bluesky client from scratch today means either hand-writing ~400 lexicon model files (the path kbsky took — 378 `.kt` files, zero coverage of `tools.ozone.*`, patchy `com.atproto.*`, abstract-class unions that break `when` exhaustiveness) or depending on that same hand-maintenance burden. AT Protocol publishes its entire schema via `@atproto/lex` on npm as a source of truth; a code generator can consume that directly and eliminate the drift problem at its root. This change creates a Kotlin-first, fully code-generated AT Protocol SDK so that downstream client work (starting with a Kikinlex Android Bluesky client) can focus on product, not plumbing.

## What Changes

- New Gradle build module `:at-protocol-generator` (JVM-only) that consumes a `lexicons/` directory populated by `npm install @atproto/lex` and emits Kotlin source via KotlinPoet.
- New KMP library `:at-protocol-runtime` containing hand-written plumbing: a Ktor-backed `XrpcClient`, auth/session plumbing, typed value classes (`Did`, `Handle`, `AtUri`, `Cid`, `Nsid`, `RecordKey`, `Tid`, `Datetime`), the `AtField<T>` sealed hierarchy and its serializer, custom `$type` polymorphic serializers, and the open-union `Unknown` fallback infrastructure.
- New KMP library `:at-protocol-models` containing 100% generated output: `@Serializable` data classes for every lexicon `object`/`record`, sealed interface hierarchies for every `union`, and suspend-fun request/response shapes for every `query`/`procedure`. Depends on `:at-protocol-runtime`.
- Split-artifact publishing to Maven Central: `runtime` versions on standard SemVer, `models` versions hard-mapped to upstream `@atproto/lex` releases. Consumers upgrade each axis independently.
- Context-sensitive optionality: mutation inputs (records, `ProcedureDef.input`, `QueryDef.parameters`, `SubscriptionDef.parameters`) use `AtField<T>` to preserve three-state semantics required for operations like `putPreferences`; read outputs (`QueryDef.output`, `#view` objects) use plain `T? = null`.
- Generator verification pass that halts CI on four invariant violations (duplicate `FqName`, ambiguous `DefKey`, colliding union arm names, colliding `$type` discriminators) with unordered-pair override keying so manual disambiguation can't silently absorb a second collision.

## Capabilities

### New Capabilities
- `lexicon-ir`: Parsed and resolved intermediate representation for AT Protocol lexicon JSON files, including NSID modelling, definition types, field types, and ref resolution across mutually recursive cross-file references.
- `lexicon-codegen`: KotlinPoet-based emission of Kotlin source from the resolved IR — data classes, sealed interface unions with `Unknown` fallback, XRPC request/response shapes, naming matrix, contextual optionality split, and collision verification.
- `atproto-runtime`: Hand-written KMP runtime consumed by generated code — `XrpcClient` over Ktor, typed value classes, `AtField<T>` and its serializer, `$type` polymorphic infrastructure, and error modelling.
- `lexicon-toolchain`: Build-time integration wiring `@atproto/lex` (npm) → `lexicons/` directory → generator task → generated sources, with deterministic traversal and reproducible output.

### Modified Capabilities
(none — greenfield work, no existing specs)

## Impact

- **New modules**: `:at-protocol-generator`, `:at-protocol-runtime`, `:at-protocol-models` added to the Gradle build.
- **New dependencies**: KotlinPoet (generator), Ktor client + kotlinx-serialization JSON (runtime), Node/npm toolchain at build time for `@atproto/lex` resolution.
- **Publishing**: two new Maven Central coordinates with independent release cadences. CI must bump `models` automatically against upstream `@atproto/lex` releases.
- **Downstream**: unblocks a future Kikinlex Android Bluesky client as a separate project that consumes the published artifacts.
- **Out of scope** (explicit non-goals for this change): firehose / `subscribeRepos`, CBOR / DAG-CBOR encoding, CAR file parsing, OAuth/DPoP runtime implementation details (runtime layer work, tracked separately), the Android app itself.
