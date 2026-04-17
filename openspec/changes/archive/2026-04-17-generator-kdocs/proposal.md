## Why

The generated AT Protocol Kotlin SDK ships classes, properties, and service
methods with zero documentation. Consumers land in an IDE and see bare
identifiers with no hints about what a field means, what constraints it has,
or whether it's deprecated upstream. Meanwhile, the Lexicon JSON corpus already
carries rich `"description"` strings and `"deprecated"` markers on most
definitions, parameters, and properties — we're just not emitting them.

Adding KDoc and `@Deprecated` annotations makes the generated API
self-documenting, reduces trips to the AT Protocol spec site, and surfaces
upstream deprecations as IDE warnings before they become runtime surprises.

## What Changes

- **IR extraction**: Capture `"deprecated"` fields from Lexicon definitions
  and properties into the existing IR model (alongside the already-parsed
  `description`).
- **KDoc emission**: Emit `description` strings as KDoc on generated classes,
  constructor properties, and service methods via KotlinPoet's `.addKdoc()`.
- **KotlinPoet sanitization**: Introduce a string sanitizer that escapes `%`
  (KotlinPoet format specifier) and `$` (Kotlin string interpolation) in raw
  Lexicon descriptions before passing them to `.addKdoc()`.
- **Deprecation mapping**: Emit `@Deprecated("<reason>")` on generated
  elements whose Lexicon node carries `"deprecated": true` or a deprecation
  reason string.
- **Golden file updates**: Update the generator's golden-file test fixtures
  to include KDoc and deprecation annotations.

## Capabilities

### New Capabilities

_(none — this enriches existing generation, does not introduce new modules)_

### Modified Capabilities

- `lexicon-ir`: IR models gain a `deprecated` field on Definition and FieldType nodes.
- `lexicon-codegen`: Code emission gains KDoc generation, `%`/`$` sanitization, and `@Deprecated` annotation mapping.

## Impact

- **at-protocol-generator**: Parser, IR models, and all emitters
  (`ModelGenerator`, `XrpcGenerator`, `UnionGenerator`, `ServiceGenerator`)
  are modified.
- **at-protocol-models**: All generated source files will change (KDoc added).
  No behavioral change — only documentation and annotations.
- **Consumers**: IDE experience improves immediately. `@Deprecated` warnings
  may surface on code that uses deprecated Lexicon endpoints or fields.
- **Breaking changes**: None. KDoc and annotations are additive.
