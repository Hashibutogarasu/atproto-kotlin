## ADDED Requirements

### Requirement: Codegen SHALL emit KDoc from Lexicon descriptions

The system SHALL emit a KDoc comment on every generated class, constructor property, and service method whose corresponding Lexicon node carries a non-null `description` string. The KDoc SHALL reproduce the Lexicon description verbatim (after sanitization). Elements without a `description` SHALL be emitted with no KDoc.

#### Scenario: Class with a Lexicon description

- **WHEN** the generator processes a `RecordDef` whose `description` is `"A declaration of a like."`
- **THEN** the emitted data class has a KDoc block: `/** A declaration of a like. */`

#### Scenario: Property with a Lexicon description

- **WHEN** the generator processes an `ObjectDef` property `subject` whose `FieldType.description` is `"The subject of the like."`
- **THEN** the emitted constructor parameter has a KDoc: `/** The subject of the like. */`

#### Scenario: Service method with a Lexicon description

- **WHEN** the generator processes a `QueryDef` at `app.bsky.feed.getTimeline` whose `description` is `"Get a view of the requesting account's home timeline."`
- **THEN** the emitted `getTimeline` method has a KDoc reproducing that description

#### Scenario: Element without a description

- **WHEN** the generator processes a definition whose `description` is null
- **THEN** the emitted element has no KDoc block

### Requirement: Codegen SHALL sanitize descriptions for KotlinPoet

The system SHALL sanitize Lexicon description strings before passing them to KotlinPoet's `.addKdoc()` by escaping `%` as `%%` (KotlinPoet format specifier) and `$` as `${'$'}` (Kotlin string interpolation). The sanitizer SHALL be a reusable extension function that all emitters share.

#### Scenario: Description containing a percent sign

- **WHEN** a Lexicon description is `"Rate limited to 100% of quota"`
- **THEN** the string passed to `.addKdoc()` is `"Rate limited to 100%% of quota"`
- **AND** the compiled Kotlin KDoc reads `Rate limited to 100% of quota`

#### Scenario: Description containing a dollar sign

- **WHEN** a Lexicon description is `"Uses $type for dispatch"`
- **THEN** the string passed to `.addKdoc()` is `"Uses ${'$'}type for dispatch"`
- **AND** the compiled Kotlin KDoc reads `Uses $type for dispatch`

### Requirement: Codegen SHALL emit @Deprecated for deprecated Lexicon nodes

The system SHALL emit a Kotlin `@Deprecated` annotation on every generated class, property, or service method whose corresponding Lexicon node is marked deprecated. When the Lexicon provides a deprecation reason string, it SHALL be used as the annotation's `message` parameter. When no reason is provided, the annotation SHALL use a default message of `"Deprecated in the AT Protocol Lexicon"`.

#### Scenario: Deprecated definition with a reason

- **WHEN** the generator processes a `QueryDef` with `deprecated = true` and `deprecatedMessage = "Use resolveHandle instead"`
- **THEN** the emitted service method carries `@Deprecated("Use resolveHandle instead")`
- **AND** the emitted Request/Response classes carry the same annotation

#### Scenario: Deprecated definition without a reason

- **WHEN** the generator processes a definition with `deprecated = true` and `deprecatedMessage = null`
- **THEN** the emitted element carries `@Deprecated("Deprecated in the AT Protocol Lexicon")`

#### Scenario: Non-deprecated definition

- **WHEN** the generator processes a definition with `deprecated = false`
- **THEN** the emitted element has no `@Deprecated` annotation
