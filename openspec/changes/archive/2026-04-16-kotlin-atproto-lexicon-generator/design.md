## Context

AT Protocol publishes its entire wire schema as versioned JSON lexicon files through `@atproto/lex` on npm. Existing Kotlin SDKs (notably kbsky — the one serious KMP option, MIT, actively maintained) are 100% hand-written: 378 manually maintained `.kt` files, zero coverage of `tools.ozone.*`, patchy coverage of `com.atproto.{admin,sync,identity}`, Java-style mutable `var` records, abstract-class unions that defeat `when` exhaustiveness, silent field-clearing bugs stemming from `explicitNulls = false`, and per-union membership hard-coded in two places that must be edited in lockstep. Every new lexicon revision upstream triggers a large hand-edit churn downstream.

This change creates a code generator that consumes the lexicon JSON directly and emits idiomatic Kotlin, eliminating the maintenance class entirely. The immediate consumer is a future Kikinlex Android Bluesky client, but the generator and runtime are intended to be reusable by any Kotlin/JVM/KMP project targeting AT Protocol.

Design was driven by pressure-testing the generator architecture against the real lexicon corpus (notably `app.bsky.embed.record` and `app.bsky.embed.recordWithMedia`, which exercise mutual recursion, open unions, nested unions in arrays, `unknown` wildcard fields, and local-vs-cross-file ref resolution in a single pair of files).

## Goals / Non-Goals

**Goals:**

- Eliminate hand-written models. 100% of lexicon-derived Kotlin source is generated.
- Match or exceed the breadth of the upstream lexicon — including `tools.ozone.*`, the full `com.atproto.*` surface, and `chat.bsky.*` — at zero marginal maintenance cost.
- Preserve lossless three-state optionality on mutation paths so that operations like `putPreferences` can distinguish "absent," "null," and "value."
- Preserve lossless round-trip on open unions even when the client is older than the server's lexicon.
- Produce idiomatic Kotlin: immutable `data class` with `val` and required ctor params, sealed interface unions with exhaustive `when`, typed value classes over `String`, suspend functions over Ktor.
- Split runtime and models into separately versioned Maven Central artifacts so that runtime bug fixes and lexicon version bumps evolve independently.
- Deterministic, reproducible codegen output — byte-identical builds across runs.

**Non-Goals:**

- CBOR / DAG-CBOR encoding (V1 is JSON/XRPC over HTTPS only).
- Firehose / `subscribeRepos` / WebSocket subscriptions.
- CAR file parsing.
- The downstream Android client itself — that is a separate project consuming the published artifacts.
- OAuth/DPoP runtime implementation details — flagged as a separate runtime-layer work item, informed by kbsky's prior art but tracked outside this change.
- A Gradle *plugin* that downstream projects apply and run locally at build time. This change publishes the generator's *output* as artifacts; the generator itself runs in Kikinlex CI, not in consumer builds.

## Decisions

### Decision 1: Three-state optionality via `sealed AtField<T>`, applied context-sensitively

`AtField<T>` is a sealed interface with `Missing`, `Null`, and `Defined(T)` variants. The generator emits it for fields owned by objects reached from mutation contexts (records, `ProcedureDef.input`, `QueryDef.parameters`, `SubscriptionDef.parameters`, and any object transitively reachable from these). Fields owned by objects reached only from read contexts (`QueryDef.output`, `#view` objects) use plain `T? = null`.

The serialization story rests on a quiet kotlinx-serialization invariant: a field's custom serializer is only invoked when the key is present in the input. So `AtField.Missing` is the Kotlin default parameter for every optional field, paired with `@EncodeDefault(EncodeDefault.Mode.NEVER)`. On decode, an absent key never reaches the serializer and the default `Missing` takes effect; on encode, `Missing == Missing` matches the default and the annotation causes the field to be omitted. `AtField.Null` encodes as a literal `null` token and decodes from `JsonNull`. `AtField.Defined(x)` delegates to the inner serializer. The Json config keeps `explicitNulls = true`.

**Alternatives considered:**

- `T?` everywhere with `explicitNulls = false` (kbsky's approach). Rejected: silently drops explicit nulls on the wire, making `putPreferences`-style "clear this field" operations impossible without dropping to raw JSON. A genuine semantic bug, not just an ergonomic one.
- `AtField<T>` everywhere, uniformly. Rejected: forces every read-path consumer to pattern-match three states for a distinction the UI never acts on. Timeline rendering is the hot path and deserves clean `null`-coalescing syntax.
- `Optional<T?>` wrapper. Rejected: same semantic as `AtField` but with worse Kotlin ergonomics and no room for future variants.

### Decision 2: Open unions always emit a sealed interface plus `Unknown(type, raw)` fallback

Every `UnionType` emits as a sealed interface with one member per resolved ref plus a generated `Unknown(type: String, raw: JsonObject)` data class. Deserialization goes through a generated `JsonContentPolymorphicSerializer` subclass that switches on the `$type` discriminator; an unknown value captures the full original JsonObject verbatim. Serialization of an `Unknown` variant emits the stored `raw` JsonObject directly, producing byte-equivalent output for a round trip. The discriminator matcher normalizes `nsid` and `nsid#main` as the same value (the AT Protocol spec allows both).

**Alternatives considered:**

- `@JsonClassDiscriminator("$type")` with built-in polymorphism. Rejected: kotlinx-serialization's built-in polymorphism throws on unknown discriminator values and has no escape hatch for open unions. It also can't normalize `nsid` vs `nsid#main`.
- Abstract class with `as*` helpers (kbsky's approach). Rejected: no exhaustive `when`, shared mutable membership list edited in two places, `Unknown` fallback is a printed warning rather than a typed value.
- Skip the `Unknown` arm entirely and throw on unknown discriminators. Rejected: makes the library brittle to any upstream lexicon revision; a downstream project on stale models would see every new variant as a crash.

### Decision 3: Typed value classes for AT Protocol string formats

`Did`, `Handle`, `AtUri`, `Cid`, `Nsid`, `RecordKey`, `Tid`, `Datetime`, `AtIdentifier`, `Language`, `Uri` are all `@JvmInline value class` wrappers over `String`. Zero runtime cost. Wherever a lexicon field declares `format: did`, codegen emits `Did`, not `String`. Accidentally passing a `Handle` to a function expecting a `Cid` becomes a compile error.

**Alternatives considered:**

- Plain `String` (kbsky's approach). Rejected: zero type safety, easy to swap arguments, every function signature loses self-documenting capability.
- Regular data classes. Rejected: heap allocation per identifier on the timeline-render hot path is unnecessary when `@JvmInline` gives identical safety at zero cost.

### Decision 4: Flat class emission with naming matrix — no nested classes

Every generated class is a top-level declaration. Secondary definitions inside a record file (e.g. `app.bsky.feed.post#replyRef`) become `PostReplyRef`, not `Post.ReplyRef`. The reason is that lexicon documents model `defs` as a flat map with no parent-child relationship — there is no "owner" in the schema, and inventing one in Kotlin creates problems when cross-file refs reach a non-main definition (e.g. a hypothetical `app.bsky.feed.thread` referencing `app.bsky.feed.post#replyRef` would have to import `Post.ReplyRef`, implying a dependency on `Post` that doesn't exist in the schema). Keeping emission flat makes one uniform strategy work across record files, procedure files, and `.defs` files (which have no `main`).

Secondary definitions inside `.defs` files use their bare fragment name (`ProfileView` from `app.bsky.actor.defs#profileView`). Packages flatten by dropping the `.defs` segment so `actor.defs` and `actor.profile` land in the same Kotlin package.

**Alternatives considered:**

- Nesting (`Post.ReplyRef`). Rejected on the "artificial ownership" grounds above — also would require a bifurcated generation strategy for `.defs` files that have no `main`.
- Full NSID prefixing (`AppBskyFeedPostReplyRef`). Rejected: verbose at every call site with no additional disambiguation benefit once packages are namespaced.

### Decision 5: Contextual split only triggers when shapes actually differ

When an object is reached from both mutation and read contexts, naive splitting would emit two classes (`Foo` and `FooInput`). But if the object has no optional fields (`required == properties.keys`), the `AtField` version and the `T?` version are structurally identical — same fields, same types. The generator detects this and emits a single class, used from both contexts.

This almost entirely eliminates the split in practice: `com.atproto.repo.strongRef`, `app.bsky.richtext.facet`, and several other commonly shared objects have no optional fields and become single-class emissions. The `Input` suffix remains in the rulebook for the genuine split case but is nearly invisible at consumer call sites.

**Alternatives considered:**

- Always split. Rejected: generates unnecessary duplicate classes, forces consumers to type `StrongRefInput` in every mutation even when the class would be byte-identical to `StrongRef`.
- Never split (always use one shape). Rejected: either loses mutation fidelity or inflicts `AtField` on all read paths.
- Package-separated identical names (`model.read.StrongRef` vs `model.write.StrongRef`). Rejected: "import the wrong one" footgun, doubles the package tree, worse discoverability.

### Decision 6: Verification pass with four invariants and pair-keyed overrides

Between IR resolution and KotlinPoet emission, a verification pass asserts four invariants: no `DefKey` maps to multiple `FqName`s (INV-1), no `FqName` is claimed by multiple `DefKey`s (INV-2), no two union arms within the same union share a Kotlin member name (INV-3), no two union arms within the same union share a `$type` discriminator string (INV-4). Any violation halts codegen with a diagnostic naming both colliding `DefKey`s.

Manual disambiguation overrides are keyed on the *unordered pair* of colliding `DefKey`s, not on the contested `FqName`. This prevents an override from silently absorbing a second future collision: if a new `DefKey` starts colliding with an already-overridden one, the unordered pair changes and the verification pass throws fresh. The pass runs twice — once before applying overrides, once after — so that override-induced collisions are also caught.

**Alternatives considered:**

- Trusting the lexicon to be collision-free. Rejected: `.defs` flattening creates a legitimate hazard (`feed.defs#post` hypothetically colliding with `feed.post#main`), and the cost of silent corruption is catastrophic (a consumer's generated JAR would have the wrong class under a given name with no compile error).
- Keying overrides on `FqName`. Rejected: see above, silently absorbs future collisions.

### Decision 7: Two-phase IR with symbol-table ref resolution

Phase 1 is a straight kotlinx-serialization parse of the lexicon JSON into a parsed IR that mirrors the wire shape 1:1. Refs are stored as raw strings. Phase 2 walks every file and registers every definition under its `DefKey` in a symbol table, then validates that every `RefType` and `UnionType` member resolves to a known key. Refs are never converted into Kotlin object references — resolution is always a lookup. This keeps the data graph acyclic even when the logical schema is mutually recursive, which is essential because `app.bsky.embed.record#view` and `app.bsky.embed.recordWithMedia#view` reach each other through `viewRecord.embeds[]`.

**Alternatives considered:**

- Eager ref linking (store `ResolvedRef(val target: Definition)`). Rejected: mutual recursion makes construction order unspecifiable and causes stack overflow during parsing of real lexicon sets.

### Decision 8: Split-artifact publishing with independent versioning

`:at-protocol-runtime` (hand-written KMP library) versions on standard SemVer. `:at-protocol-models` (100% generated, depends on runtime) versions with a string hard-mapped to the `@atproto/lex` release it was generated against. The Kikinlex CI pipeline runs the generator, not consumer builds — consumers drop in pre-compiled `.jar`/`.klib` files like any other Maven Central dependency. This gives: zero build tax on consumers, crystal-clear versioning (a `build.gradle.kts` reads `models:0.13.0` and the reader knows exactly which upstream lexicon snapshot that is), and independent evolution (a runtime-only bug fix can ship as `runtime:1.2.1` without touching models).

**Alternatives considered:**

- Single fat artifact. Rejected: every runtime bug fix forces a full models republish, and consumers can't see at a glance which lexicon version they're compiling against.
- Gradle plugin that runs the generator in consumer builds. Rejected: forces every consumer to have Node/npm + KotlinPoet + generator on their build classpath, inflates build times, makes version pinning awkward, and couples consumer build success to generator bugs.

## Risks / Trade-offs

- **`AtField` ergonomics in hand-written mutation code** → The first time a consumer constructs a record, they have to wrap everything in `AtField.Defined(...)`. Mitigation: provide an optional `AtField.of(T?)` factory and a Kotlin DSL-friendly builder on common records. If this still hurts, revisit in a future change; the sealed type design leaves room to add ergonomic helpers without breaking the wire semantics.
- **Codegen refactors can silently change the public API** → A generator change that renames a class breaks every downstream consumer. Mitigation: golden-file tests comparing generator output to a checked-in reference set (covering every IR feature: recursion, unions, blobs, `AtField` split, `.defs` flattening, collisions). Any public-API delta is visible as a diff in the PR.
- **Reproducibility depends on determinism at every level** → Hash-ordered iteration anywhere in the generator breaks byte-reproducible output and defeats the "did the lexicon actually change?" model-version diffing. Mitigation: enforce sorted traversal as a generator invariant; add a CI check that runs the generator twice and diffs the output.
- **`@atproto/lex` breaking changes** → Upstream could restructure the package or publish malformed lexicons. Mitigation: pin `package-lock.json` and gate version bumps on successful generator + golden-test runs before publishing a new `models` release.
- **V1 excludes CBOR / firehose** → Some AT Protocol use cases (notably relay/firehose consumers) can't adopt V1. Mitigation: the IR is designed to accept a CBOR emitter as a later addition; v1's public API shouldn't need to change when that ships. Explicit non-goal, tracked for a future change.
- **Hypothetical split case may never fire** → The "Input suffix" machinery is architecture for a case that may not exist in the current lexicon corpus. Mitigation: accept the architectural cost (small) in exchange for correctness against any future lexicon revision that *does* trigger it.
- **Subscription definitions are silently skipped in V1** → A generator that silently warns-and-skips risks making consumers think a feature exists when it doesn't. Mitigation: the warning is loud in build output, and generated code never includes a half-baked subscription stub that would mislead callers.

## Migration Plan

Greenfield change, no existing consumers. Staging:

1. Land `:at-protocol-runtime` first with only the base plumbing (value classes, `AtField`, `XrpcClient`, open-union infrastructure). No generator, no models yet. This establishes the runtime API that codegen will target.
2. Land `:at-protocol-generator` with parser, resolver, verification pass, and a single golden-file test against a hand-picked minimal lexicon (e.g. `com.atproto.repo.strongRef` + `app.bsky.feed.post`) before any broad emission.
3. Expand generator to all four emitters (models, unions, XRPC, naming matrix); run against the full lexicon set; triage any verification-pass failures with manual overrides.
4. Publish `runtime:1.0.0` and `models:<lexicon-snapshot>` to Maven Central.
5. Kikinlex Android client consumes the published artifacts in a separate downstream change.

Rollback is trivial per stage — each module lands independently, nothing is wired into existing code.

## Open Questions

- ~~**Exact Maven coordinates and group ID.**~~ **Resolved**: we use
  `io.github.kikin81.atproto` as both the Kotlin root package and the Maven
  Central group ID. Rationale: Sonatype's Central Publisher Portal requires
  you to prove ownership of the TLD for `com.*` / `net.*` / `org.*` group
  IDs (e.g. `com.kikinlex.*` would require owning `kikinlex.com`). The
  explicit fast-track for individual OSS developers is `io.github.<username>`
  — Sonatype verifies ownership via a one-off GitHub repo handshake and
  publishing Just Works. Picking this up front avoids a painful rename
  later. Artifacts are `io.github.kikin81.atproto:at-protocol-runtime`
  and `io.github.kikin81.atproto:at-protocol-models` (module names
  preserved from the current Gradle layout so the `project(...)`
  coordinates in `:samples:android` remain unchanged).
- **Kotlin value class serializer strategy** — per-format `KSerializer<Did>` vs a shared `StringFormatSerializer<T>` base. Probably the former for clarity; pending prototyping.
- **How far to go with `@atproto/lex` automation in CI.** Auto-bump on upstream releases (with human review of the PR)? Manual bumps? Bounded auto-bump (patch versions only)? Defer until we've shipped the first few model releases by hand and know what the cadence feels like.
- **Whether to model subscription definitions at all in V1 IR.** Current decision: parse them but skip emission with a warning. Alternative: silently drop them at parse time. Leaning toward the former because the IR should be complete even if the emitter isn't.
- **Gradle task ergonomics for local regeneration.** The generator runs in CI, but developers working on the generator itself need a cheap local loop. Probably a `generateModels` task with an up-to-date check on `lexicons/`.
