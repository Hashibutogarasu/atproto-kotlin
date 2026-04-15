## ADDED Requirements

### Requirement: Codegen SHALL emit @Serializable data classes for every object and record definition

The system SHALL emit a Kotlin `@Serializable` data class for every `ObjectDef` and `RecordDef` reachable in the resolved IR. Required fields SHALL be emitted as non-nullable constructor parameters without defaults. The emitted class SHALL live in a Kotlin package derived from the NSID domain segments, with the `.defs` suffix dropped when present (so `app.bsky.feed.defs` and `app.bsky.feed.post` both emit into the same Kotlin package).

#### Scenario: Emitting a record with all required fields

- **WHEN** the generator processes `com.atproto.repo.strongRef` (required: `uri`, `cid`)
- **THEN** it emits `data class StrongRef(val uri: AtUri, val cid: Cid)` in package `com.kikinlex.atproto.com.atproto.repo`
- **AND** the class is annotated `@Serializable`

### Requirement: Codegen SHALL apply context-sensitive optionality

The system SHALL emit optional fields as `AtField<T> = AtField.Missing` with `@EncodeDefault(EncodeDefault.Mode.NEVER)` when the owning object is reached from a mutation context (`RecordDef.record`, `ProcedureDef.input`, `QueryDef.parameters`, `SubscriptionDef.parameters`, or any object transitively reachable from one of those). The system SHALL emit optional fields as plain `T? = null` when the owning object is reached only from a read context (`QueryDef.output`, `ProcedureDef.output`, or `#view`-style objects). Context SHALL propagate through ref traversal.

#### Scenario: Optional field in a mutation input

- **WHEN** an `ObjectDef` tagged mutation-reachable contains property `displayName` of type `StringType` not listed in `required`
- **THEN** the generator emits `@EncodeDefault(EncodeDefault.Mode.NEVER) val displayName: AtField<String> = AtField.Missing`

#### Scenario: Optional field in a read output

- **WHEN** an `ObjectDef` tagged read-only contains property `description` of type `StringType` not listed in `required`
- **THEN** the generator emits `val description: String? = null`

### Requirement: Shared objects SHALL split only when the split produces a materially different class

The system SHALL emit a single Kotlin class for any object reached from both mutation and read contexts when `properties.keys == required` (no optional fields). The system SHALL emit two distinct Kotlin classes (the read version unsuffixed, the mutation version with `Input` suffix) only when the object has at least one optional field and is reached from both contexts.

#### Scenario: Shared object with no optional fields emits once

- **WHEN** `com.atproto.repo.strongRef` is reached from both `app.bsky.feed.like` (mutation) and `app.bsky.feed.defs#postView` (read)
- **AND** `strongRef` has no optional fields
- **THEN** the generator emits exactly one `StrongRef` class, used from both sites

#### Scenario: Shared object with optional fields emits twice

- **WHEN** a hypothetical shared object has at least one optional field and is reached from both mutation and read contexts
- **THEN** the generator emits a `Foo` class (using `T?`) for read consumers and a `FooInput` class (using `AtField<T>`) for mutation consumers

### Requirement: Unions SHALL emit as sealed interfaces with Unknown fallback and $type polymorphic serializer

The system SHALL emit every `UnionType` as a sealed interface whose members are the resolved ref targets. Every emitted union SHALL include a generated `Unknown(type: String, raw: JsonObject)` variant that preserves the full original JSON object verbatim for lossless round-trip. The system SHALL emit a `JsonContentPolymorphicSerializer` subclass that dispatches on the `$type` discriminator. The discriminator matching SHALL normalize `nsid` and `nsid#main` as equivalent.

#### Scenario: Serializing an unknown variant round-trips the raw JSON

- **WHEN** a union field receives a JSON object whose `$type` is not in the known refs list (e.g. `{"$type":"app.bsky.embed.futureThing","field":"value"}`)
- **THEN** the generated serializer deserializes it to `Unknown("app.bsky.embed.futureThing", JsonObject(...))`
- **AND** re-serializing that value produces an object with the same `$type` and the same fields as the input

#### Scenario: Discriminator normalizes nsid and nsid#main

- **WHEN** a union arm references `"com.example.foo"` and the wire value has `"$type": "com.example.foo#main"`
- **THEN** the polymorphic serializer routes to the same generated variant as if `"$type": "com.example.foo"` had been received

### Requirement: Codegen SHALL apply the naming matrix

The system SHALL derive Kotlin class names from `DefKey` according to a fixed matrix: queries/procedures at `defs.main` emit `<NsidTerminal>Request` and `<NsidTerminal>Response` classes; records at `defs.main` emit `<NsidTerminal>` as PascalCase; secondary definitions in a `.defs` file emit their fragment name in PascalCase; secondary definitions inside a primary def file emit `<PrimaryName><FragmentName>` (flat, no nesting). All emissions SHALL be top-level classes in their Kotlin package; nested class emission is NOT permitted.

#### Scenario: Secondary definition inside a record file

- **WHEN** the generator processes `app.bsky.feed.post#replyRef` (a non-main definition in the `post.json` record file)
- **THEN** it emits a top-level `PostReplyRef` class in package `com.kikinlex.atproto.app.bsky.feed`
- **AND** it does NOT emit it as a nested class inside `Post`

#### Scenario: Secondary definition inside a .defs file

- **WHEN** the generator processes `app.bsky.actor.defs#profileView`
- **THEN** it emits a top-level `ProfileView` class in package `com.kikinlex.atproto.app.bsky.actor` (the `.defs` suffix is dropped from the package)

### Requirement: Verification pass SHALL halt codegen on invariant violation

The system SHALL run a verification pass between IR resolution and KotlinPoet emission that enforces four invariants and halts with a diagnostic on any violation:

- **INV-1**: No `DefKey` maps to more than one target `FqName`.
- **INV-2**: No `FqName` is claimed by more than one `DefKey`.
- **INV-3**: No two union arms within the same owning union have the same Kotlin member name.
- **INV-4**: No two union arms within the same owning union have the same `$type` discriminator string.

Manual disambiguation overrides SHALL be keyed on the unordered pair of colliding `DefKey`s (not on the resulting `FqName`), so that a second collision landing on the same name triggers a fresh failure rather than being silently absorbed. The verification pass SHALL run twice — once before applying overrides and once after — so that an override-induced collision is itself caught.

#### Scenario: FqName collision halts the build

- **WHEN** two `DefKey`s resolve to the same Kotlin `FqName` and no override covers the pair
- **THEN** the verification pass throws a diagnostic naming both `DefKey`s and the contested `FqName`
- **AND** no Kotlin source files are written

#### Scenario: Override-induced collision is caught on second pass

- **WHEN** an override renames `DefKey A` to a Kotlin name that already belongs to `DefKey B`
- **THEN** the second verification pass detects the collision and halts with a diagnostic
