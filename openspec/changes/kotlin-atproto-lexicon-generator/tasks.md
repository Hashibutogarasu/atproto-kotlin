## 1. Project scaffolding

- [x] 1.1 Add `:at-protocol-runtime` KMP module with targets jvm, iosX64, iosArm64, iosSimulatorArm64 _(android target deferred — requires AGP setup)_
- [x] 1.2 Add `:at-protocol-models` KMP module with the same target matrix and a dependency on `:at-protocol-runtime`
- [x] 1.3 Add `:at-protocol-generator` JVM-only module with KotlinPoet dependency
- [ ] 1.4 Configure Maven Central publishing for `runtime` and `models` with independent version strings (runtime on SemVer, models pinned to the upstream `@atproto/lex` version)
- [x] 1.5 Wire `npm install @atproto/lex` into `:at-protocol-generator` and commit `package.json` + `package-lock.json` _(uses the `lex install <nsid>` CLI which fetches lexicons live from the AT network via DID-hosted `com.atproto.lexicon.schema` records and pins each by CID in `lexicons.json`)_
- [x] 1.6 Verify the npm install populates `at-protocol-generator/lexicons/` with the full lexicon tree _(installed 87 lexicon documents across `app.bsky.*`, `com.atproto.*`, `chat.bsky.*`, `tools.ozone.*` via a broad seed-set + transitive dependency resolution)_

## 2. Runtime: value classes and serializers

- [x] 2.1 Implement `@JvmInline value class` wrappers for `Did`, `Handle`, `AtIdentifier`, `AtUri`, `Cid`, `Nsid`, `RecordKey`, `Tid`, `Datetime`, `Language`, `Uri`
- [x] 2.2 Implement per-format `KSerializer` for each value class (or a shared base — decide during implementation) _(used `@Serializable` on the value class; the compiler plugin generates the inline-string serializer automatically — no custom per-format serializer needed)_
- [x] 2.3 Add unit tests asserting each value class round-trips via kotlinx-serialization as a plain JSON string

## 3. Runtime: AtField three-state type

- [x] 3.1 Implement `sealed interface AtField<out T>` with `Missing`, `Null`, `Defined(T)` variants
- [x] 3.2 Implement generic `AtFieldSerializer<T>(inner: KSerializer<T>)` with deserialize mapping `JsonNull → Null`, other → `Defined`
- [x] 3.3 Make `serialize(Missing)` throw `SerializationException` with a clear diagnostic about `@EncodeDefault(NEVER)`
- [x] 3.4 Add round-trip test for all three states on a sample data class (`{key absent → Missing, "key":null → Null, "key":"v" → Defined("v")}`)
- [x] 3.5 Add a test that deliberately omits `@EncodeDefault(NEVER)` and asserts the serializer throws

## 4. Runtime: open-union Unknown infrastructure

- [x] 4.1 Define a base class or utility for generated polymorphic serializers that captures unknown `$type` variants _(implemented as `OpenUnionSerializer<T>` — a direct `KSerializer<T>` instead of `JsonContentPolymorphicSerializer` subclass, because the latter's `serialize` is `final` and we need to inject `$type` on encode for concrete members)_
- [x] 4.2 Implement `Unknown(type: String, raw: JsonObject)` as a common interface that generated sealed unions can add as a member _(implemented as `UnknownOpenUnionMember` interface + `UnknownMemberSerializer<T>` base class; each generated union emits its own `Unknown` data class implementing the interface, so sealed-interface type checking works end-to-end)_
- [x] 4.3 Implement a helper for normalizing `$type` values (`nsid` and `nsid#main` are equivalent)
- [x] 4.4 Add a test that round-trips an unknown union variant and asserts the re-serialized JSON equals the input

## 5. Runtime: XrpcClient

- [x] 5.1 Implement `XrpcClient` wrapping Ktor `HttpClient`
- [x] 5.2 Implement query execution (`GET /xrpc/<nsid>?...`) with array-repeats-key parameter encoding
- [x] 5.3 Implement procedure execution (`POST /xrpc/<nsid>`) with body encoding per `input.encoding`
- [x] 5.4 Implement pluggable `AuthProvider` attached at client or per-call level (not as a field on request DTOs)
- [x] 5.5 Implement sealed `XrpcError` hierarchy with typed declared errors and `XrpcError.Unknown(name, message, status)` fallback _(base class + `XrpcErrorMapper` interface; generated code subclasses `XrpcError` per declared error name)_
- [x] 5.6 Add integration test hitting a local fake HTTP server for one query and one procedure _(used Ktor `MockEngine` in commonTest; covers query, procedure, null-param omission, array-repeats-key, auth header, per-call auth override, typed error mapping, unknown error fallback)_

## 6. Generator: parsed IR

- [x] 6.1 Define kotlinx-serialization data classes mirroring lexicon JSON 1:1 (`LexiconDocument`, `Definition` sealed hierarchy, `FieldType` sealed hierarchy, `PrimaryDefinition` marker)
- [x] 6.2 Implement `LexiconParser` that reads every `*.json` file under `lexicons/` into `LexiconDocument`
- [x] 6.3 Store refs as raw strings in the parsed IR (no early resolution)
- [x] 6.4 Add unit tests covering every primitive type, every container type, every definition type, and both `closed` and open unions

## 7. Generator: resolved IR

- [x] 7.1 Implement `DefKey(nsid, name)` value type with `toString()` producing canonical `nsid` or `nsid#name` form
- [x] 7.2 Implement ref resolver: `#localName` against origin file, bare NSID → `DefKey(nsid, "main")`, `nsid#name` → `DefKey(nsid, name)`
- [x] 7.3 Build symbol table `Map<DefKey, Definition>` from all parsed documents
- [x] 7.4 Validate every `RefType` and `UnionType` member resolves via the symbol table
- [x] 7.5 Implement context tagging: walk primary definitions and tag every reachable `ObjectDef` as `mutation`, `read`, or both
- [x] 7.6 Add a test that processes `app.bsky.embed.record` + `app.bsky.embed.recordWithMedia` and asserts mutual recursion resolves without stack overflow

## 8. Generator: verification pass

- [x] 8.1 Implement INV-1 check (`DefKey → FqName` uniqueness)
- [x] 8.2 Implement INV-2 check (`FqName → DefKey` uniqueness)
- [x] 8.3 Implement INV-3 check (union arm Kotlin name uniqueness within owning union)
- [x] 8.4 Implement INV-4 check (`$type` discriminator uniqueness within owning union)
- [x] 8.5 Implement collision override config keyed on unordered `DefKey` pairs with a per-key rename map
- [x] 8.6 Run the pass twice — once before overrides, once after — and throw on any failure in either pass _(pre-pass tolerates INV-2 collisions only when an override is registered for the pair; post-pass is strict so override-induced collisions still throw)_
- [x] 8.7 Add tests for each invariant: construct a minimal IR that violates it and assert the pass throws with a diagnostic naming both colliding entities
- [x] 8.8 Add a test that an override-induced collision is caught on the second pass

## 9. Generator: naming matrix

- [x] 9.1 Implement package derivation: NSID segments dropped-`.defs`, prefixed with `io.github.kikin81.atproto` _(drops the trailing NSID segment regardless of whether it's `.defs` or a sibling like `.profile`, matching design.md Decision 4)_
- [x] 9.2 Implement primary-def naming: query/procedure → `<Terminal>Request` + `<Terminal>Response`, record → `<Terminal>`
- [x] 9.3 Implement secondary-def naming in `.defs` files: bare fragment name in PascalCase
- [x] 9.4 Implement secondary-def naming in non-`.defs` files: `<PrimaryName><FragmentName>` (flat, not nested)
- [x] 9.5 Implement contextual-split rule: single class when `required == properties.keys`, otherwise `Foo` + `FooInput` _(only fires when context is `Both`; single-context cases always collapse to one class)_
- [x] 9.6 Add unit tests for every rule in the matrix using real lexicon fragments

## 10. Generator: KotlinPoet emitters

- [x] 10.1 `ModelGenerator` — emit `@Serializable data class` for every `ObjectDef` and `RecordDef`
- [x] 10.2 Field emission: apply context-sensitive optionality (`AtField<T> = AtField.Missing` + `@EncodeDefault(NEVER)` on mutation side; `T? = null` on read side) _(uses `@OptIn(ExperimentalSerializationApi::class)` on classes with `@EncodeDefault`)_
- [x] 10.3 Field emission: substitute typed value classes wherever a `StringFormat` is declared
- [x] 10.4 `UnionGenerator` — emit `sealed interface` per union with one member per resolved ref plus `Unknown(type, raw)` fallback _(target classes gain the sealed interface as a supertype via `EmissionPlan.unionMembership`; Unknown is a nested data class implementing `UnknownOpenUnionMember`)_
- [x] 10.5 `UnionGenerator` — emit companion `JsonContentPolymorphicSerializer` subclass dispatching on `$type` with nsid/nsid#main normalization _(uses the runtime's `OpenUnionSerializer<T>` + `UnknownMemberSerializer<T>` base classes; normalization handled by the runtime base)_
- [x] 10.6 `XrpcGenerator` — emit request/response data classes and suspend-fun interface signatures for every `QueryDef` and `ProcedureDef` _(v1: Request/Response data classes only; no service interface wrapper — deferred to §13 triage)_
- [x] 10.7 Deterministic traversal: sort all iteration by `DefKey` (NSID then def name) to guarantee reproducible output _(verified by a round-trip determinism test)_
- [x] 10.8 Silently skip `SubscriptionDef` with a non-fatal warning (V1 scope)

## 11. Generator: Gradle task wiring

- [x] 11.1 Add a `generateModels` task in `:at-protocol-generator` with `lexicons/` as input and `:at-protocol-models/build/generated/` as output _(implemented as a `JavaExec` task invoking `io.github.kikin81.atproto.generator.MainKt`; marked `notCompatibleWithConfigurationCache` since codegen tasks rarely benefit from cc anyway)_
- [x] 11.2 Configure Gradle up-to-date checks (skip regeneration when lexicon inputs unchanged) _(verified: second invocation reports `generateModels UP-TO-DATE`)_
- [x] 11.3 Wire `:at-protocol-models` source set to include the generated directory _(via `kotlin.srcDir(...)` on `commonMain`; KMP compile tasks depend on `:at-protocol-generator:generateModels`)_
- [x] 11.4 Verify that running `generateModels` twice in a row produces byte-identical output (determinism check) _(confirmed via `diff -rq` on two successive `--rerun-tasks` invocations)_

## 12. Golden-file test suite

- [x] 12.1 Pick 6-8 representative lexicons that exercise every IR feature: recursion (`embed.record` + `embed.recordWithMedia`), open union (`embed.record#view.record`), shared object no-split (`strongRef`), shared object with-split (pick a real one or synthesize), `.defs` flattening (`actor.defs`), blob (`embed.images`), `unknown` field (`embed.record#viewRecord.value`) _(used 8 synthetic `example.*` lexicons: `strongRef`, `actor.defs`, `embed.images`, `embed.record`, `embed.recordWithMedia`, `feed.post`, `split`, `sendShared` — self-contained closure, no dependency on `lex install`)_
- [x] 12.2 Run the generator against them and commit the output as reference files under `at-protocol-generator/src/test/resources/golden/` _(21 reference files committed under `golden/kotlin/`)_
- [x] 12.3 Add a test that re-runs the generator and diffs byte-for-byte against the reference files, failing on any drift _(verified by intentionally perturbing one file and watching the test fail with a per-file unified-ish diff)_
- [x] 12.4 Document how to regenerate the golden files when the generator is legitimately updated _(documented inline in `GoldenFileTest`'s class docblock: `GOLDEN_UPDATE=1 ./gradlew :at-protocol-generator:test --tests '*GoldenFileTest*'`)_

## 13. Full lexicon run and triage

- [ ] 13.1 Run the generator against the full `@atproto/lex` corpus
- [x] 13.2 Triage any verification-pass failures — add collision overrides or fix the naming matrix as needed _(wired `VerificationPass` into `CodeGenerator.generate()` via a `buildVerificationInput(plan)` helper that flattens `plan.classes` into `NameEntry(DefKey, role, FqName)` tuples and `plan.unionSites` into `UnionArms`; extended `VerificationInput` to key on composite `(DefKey, role)` so query/procedure Request/Response/Message slots don't trip INV-1; added an end-to-end collision test in `CodeGeneratorTest`. Full 87-lexicon smoke run passes the pass cleanly — no override config needed yet)_
- [x] 13.3 Compile `:at-protocol-models` and fix any emitted code that fails to compile _(compiles cleanly against all 87 installed lexicons → 295 emitted files on JVM + 3 iOS targets; cumulative polish landed across this chunk plus the initial §13 triage: typed `Blob`/`CidLink` runtime classes replacing `JsonObject` fall-throughs, cosmetic `@OptIn` only when a field actually carries `@EncodeDefault`, plus all the earlier fixes — `ArrayDefTopLevel`, protected→public overrides, sealed→interface for cross-package unions, `StringDefTopLevel` typealias, union name `Union` suffix, query `RefType` output → typealias, role-specific union owner FqNames)_
- [ ] 13.4 Record the `@atproto/lex` version the generator was run against in the `models` artifact version string

## 14. Publication

- [ ] 14.1 Publish `at-protocol-runtime:1.0.0` to Maven Central
- [ ] 14.2 Publish `at-protocol-models:<lexicon-version>` to Maven Central
- [ ] 14.3 Verify a scratch KMP project can consume both artifacts and call `app.bsky.feed.getTimeline` against the public Bluesky AppView
- [ ] 14.4 Document consumer usage in a short README (dependency coordinates, minimal example, versioning model)

## 15. Backlog (deferred until consumer signal)

Items below are intentionally not-in-scope for v1 but are worth tracking
so they're not forgotten when the signal to act on them arrives.

- [ ] 15.1 Split `:at-protocol-models` into `:at-protocol-models-core` (`com.atproto.*`) and `:at-protocol-models-bsky` (`app.bsky.*`, `chat.bsky.*`, `tools.ozone.*`) once a real third-party atproto-but-not-Bluesky consumer shows up, OR a Bluesky consumer complains about the `tools.ozone.*` / `chat.bsky.moderation.*` surface. _Preconditions are zero — current packages (`io.github.kikin81.atproto.app.bsky.*`, `io.github.kikin81.atproto.com.atproto.*`, etc.) are already clean namespace boundaries. The generator change required is a ~50-line `EmissionPlan.moduleFor(pkg)` mapping plus splitting the Gradle module configuration. No call-site impact on consumers._
