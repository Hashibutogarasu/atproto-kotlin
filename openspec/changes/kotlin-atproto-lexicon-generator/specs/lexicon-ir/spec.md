## ADDED Requirements

### Requirement: Parsed IR SHALL mirror lexicon JSON structure

The system SHALL provide a parsed intermediate representation that is 1:1 with the AT Protocol lexicon JSON schema. The parsed IR SHALL model every lexicon primitive type (`null`, `boolean`, `integer`, `string`, `bytes`, `cid-link`), every container type (`array`, `object`, `blob`, `params`), every primary definition type (`record`, `query`, `procedure`, `subscription`), and every non-primary definition type (`object`, `token`, `params`). References (`ref`, `union`, `unknown`) SHALL be stored as raw strings in the parsed IR, never as resolved object pointers.

#### Scenario: Parsing a simple record lexicon

- **WHEN** the parser reads `app.bsky.feed.like.json` (a record lexicon with a `subject` ref to `com.atproto.repo.strongRef` and a `createdAt` datetime)
- **THEN** the parsed IR contains a `LexiconDocument` with `id = "app.bsky.feed.like"`, a `RecordDef` at `defs["main"]`, an `ObjectDef` with `required = {"subject", "createdAt"}`, a `RefType(ref = "com.atproto.repo.strongRef")` for the `subject` property, and a `StringType(format = Datetime)` for the `createdAt` property

#### Scenario: Parsing preserves open vs closed union distinction

- **WHEN** the parser reads a lexicon containing a `union` field with `"closed": true`
- **THEN** the parsed IR represents that field as a `UnionType` with `closed = true`
- **AND WHEN** the parser reads a `union` field without a `closed` key
- **THEN** the parsed IR represents it as `UnionType` with `closed = false`

#### Scenario: Parsing a field with default and const

- **WHEN** the parser reads an integer field with `"default": 50` and `"maximum": 100`
- **THEN** the parsed IR represents it as `IntegerType(maximum = 100, default = JsonPrimitive(50))`

### Requirement: DefKey SHALL uniquely identify every lexicon definition

The system SHALL provide a `DefKey(nsid: Nsid, name: String)` value type that uniquely identifies every definition across the entire lexicon set. Bare NSIDs (e.g. `"app.bsky.feed.post"`) SHALL resolve to `DefKey(nsid, "main")`. NSID-with-fragment (e.g. `"app.bsky.embed.record#viewRecord"`) SHALL resolve to `DefKey(nsid, "viewRecord")`. Local-only fragments (e.g. `"#viewRecord"`) SHALL resolve against the NSID of the file they appear in.

#### Scenario: Resolving a local fragment reference

- **WHEN** the ref `"#viewRecord"` appears inside `app.bsky.embed.record.json`
- **THEN** the resolver produces `DefKey(Nsid("app.bsky.embed.record"), "viewRecord")`

#### Scenario: Resolving a bare NSID reference

- **WHEN** the ref `"com.atproto.repo.strongRef"` appears in any file
- **THEN** the resolver produces `DefKey(Nsid("com.atproto.repo.strongRef"), "main")`

#### Scenario: Resolving a cross-file NSID-with-fragment reference

- **WHEN** the ref `"app.bsky.actor.defs#profileViewBasic"` appears in `app.bsky.feed.defs.json`
- **THEN** the resolver produces `DefKey(Nsid("app.bsky.actor.defs"), "profileViewBasic")`

### Requirement: Resolved IR SHALL resolve refs via symbol table without back-references

The system SHALL provide a resolved IR phase that builds a symbol table of `DefKey -> Definition`. Ref resolution SHALL be performed by lookup, not by storing Kotlin object references. The data graph of the resolved IR SHALL remain acyclic even when the underlying lexicon schema contains mutual recursion (e.g. `app.bsky.embed.record#view` references `app.bsky.embed.recordWithMedia#view` and vice versa).

#### Scenario: Resolving a mutually recursive lexicon pair

- **WHEN** the resolver processes `app.bsky.embed.record` and `app.bsky.embed.recordWithMedia` together (noting that `embed.record#viewRecord.embeds[]` contains a union that references `embed.recordWithMedia#view`, which in turn references `embed.record#view`)
- **THEN** the resolver terminates without stack overflow
- **AND** both `DefKey`s are present in the symbol table
- **AND** resolving either ref via lookup returns the corresponding `Definition`

#### Scenario: Missing ref halts resolution

- **WHEN** a `RefType` or `UnionType` member points at a `DefKey` that is not present in the symbol table
- **THEN** the resolver fails with a diagnostic naming the unresolved ref and the file it appeared in

### Requirement: Traversal order SHALL be deterministic

The system SHALL iterate over definitions in a deterministic order (lexicographically sorted by NSID, then by definition name) whenever the IR is walked for code generation or verification. The generator output SHALL be byte-reproducible across runs for identical input.

#### Scenario: Running the generator twice produces identical output

- **WHEN** the generator is invoked twice in a row against the same `lexicons/` directory
- **THEN** the emitted Kotlin source files are byte-identical
