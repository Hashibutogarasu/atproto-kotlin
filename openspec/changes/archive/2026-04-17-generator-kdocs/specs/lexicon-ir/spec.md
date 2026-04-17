## ADDED Requirements

### Requirement: Parsed IR SHALL capture deprecation metadata from Lexicon nodes

The system SHALL capture the optional `"deprecated"` field from Lexicon definition and property nodes into the parsed IR. When the Lexicon JSON provides `"deprecated": true`, the IR SHALL store a deprecation marker. When the Lexicon JSON provides a string value (e.g. `"deprecated": "Use foo instead"`), the IR SHALL store both the marker and the reason string. When the field is absent, the IR SHALL treat the element as non-deprecated.

#### Scenario: Parsing a deprecated definition

- **WHEN** the parser reads a Lexicon definition containing `"deprecated": true`
- **THEN** the parsed IR represents it with `deprecated = true` and `deprecatedMessage = null`

#### Scenario: Parsing a definition with a deprecation reason

- **WHEN** the parser reads a Lexicon definition containing `"deprecated": "Use com.atproto.identity.resolveHandle instead"`
- **THEN** the parsed IR represents it with `deprecated = true` and `deprecatedMessage = "Use com.atproto.identity.resolveHandle instead"`

#### Scenario: Parsing a non-deprecated definition

- **WHEN** the parser reads a Lexicon definition with no `"deprecated"` field
- **THEN** the parsed IR represents it with `deprecated = false` and `deprecatedMessage = null`
