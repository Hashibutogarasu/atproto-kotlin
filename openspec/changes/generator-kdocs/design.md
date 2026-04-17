## Overview

Add KDoc documentation and `@Deprecated` annotations to all generated Kotlin
code by extracting `description` and `deprecated` fields from the Lexicon
JSON corpus and wiring them through the IR into KotlinPoet emission.

## IR Changes

### Deprecation fields on Definition

Add two fields to every `Definition` variant (`RecordDef`, `QueryDef`,
`ProcedureDef`, `ObjectDef`, `TokenDef`, `SubscriptionDef`):

```kotlin
val deprecated: Boolean = false
val deprecatedMessage: String? = null
```

The Lexicon JSON uses `"deprecated"` as either a boolean (`true`) or a string
(the reason). The parser handles both forms:

```kotlin
// In the @Serializable Definition classes
@SerialName("deprecated")
private val _deprecated: JsonElement? = null

val deprecated: Boolean
    get() = _deprecated != null && _deprecated != JsonNull

val deprecatedMessage: String?
    get() = (_deprecated as? JsonPrimitive)?.contentOrNull
```

### Deprecation fields on FieldType

The same pattern applies to `FieldType` variants that carry property-level
descriptions. `ObjectType`, `StringType`, `IntegerType`, `BooleanType`,
`ArrayType`, `RefType`, `UnionType`, `BytesType`, `CidLinkType`, and
`BlobType` already carry `description: String?`. Add `deprecated: Boolean`
and `deprecatedMessage: String?` using the same `JsonElement` backing
approach.

### Description on LexiconDocument

`LexiconDocument.description` is already captured. No changes needed.

## KDoc Sanitization

### The problem

KotlinPoet's `.addKdoc(format, args...)` interprets `%` as a format
specifier (`%T`, `%S`, `%L`, etc.). Raw `%` in a Lexicon description
causes `IllegalFormatException` at generation time. Similarly, Kotlin's
`$` in a KDoc string would be interpreted as string interpolation in the
generated source, causing compile errors.

### The solution

A single extension function shared by all emitters:

```kotlin
/**
 * Escapes a raw Lexicon description for safe passage through
 * KotlinPoet's addKdoc() and Kotlin's string interpolation.
 */
fun String.sanitizeForKdoc(): String =
    replace("%", "%%")
        .replace("$", "\${'$'}")
```

This lives in a new `KdocUtils.kt` file in the `emit` package. Every
emitter calls `description.sanitizeForKdoc()` before passing to
`.addKdoc()`.

### Emission pattern

```kotlin
// On a TypeSpec (class)
definition.description?.let { desc ->
    builder.addKdoc("%L", desc.sanitizeForKdoc())
}

// On a PropertySpec (constructor property)
fieldType.description?.let { desc ->
    propBuilder.addKdoc("%L", desc.sanitizeForKdoc())
}

// On a FunSpec (service method)
queryDef.description?.let { desc ->
    funBuilder.addKdoc("%L", desc.sanitizeForKdoc())
}
```

Using `%L` (literal) avoids double-escaping. The sanitizer handles the
two dangerous characters; `%L` passes everything else through unchanged.

## Deprecation Mapping

### Annotation emission

When a Definition or FieldType has `deprecated = true`, emit:

```kotlin
@Deprecated("reason or default message")
```

The default message when no reason is provided:
`"Deprecated in the AT Protocol Lexicon"`

### Implementation

```kotlin
if (definition.deprecated) {
    val message = definition.deprecatedMessage
        ?: "Deprecated in the AT Protocol Lexicon"
    builder.addAnnotation(
        AnnotationSpec.builder(Deprecated::class)
            .addMember("%S", message)
            .build()
    )
}
```

### Scope

Deprecation applies to:
- **Classes**: `TypeSpec` for records, objects, request/response types
- **Properties**: `PropertySpec` for constructor parameters
- **Service methods**: `FunSpec` for query/procedure wrappers
- **Unions**: The sealed interface `TypeSpec` (not individual arms, since
  deprecation is on the union definition, not its members)

## Affected Emitters

| Emitter | KDoc | @Deprecated |
|---------|------|-------------|
| `ModelGenerator` | Class + properties | Class + properties |
| `XrpcGenerator` | Request/Response classes + properties | Request/Response classes + properties |
| `UnionGenerator` | Sealed interface | Sealed interface |
| `ServiceGenerator` | Service methods | Service methods |

## Golden File Updates

The generator's `GoldenFileTest` compares emitted output byte-for-byte
against checked-in fixtures under `test/resources/golden/`. Adding KDoc
and annotations changes every golden file. The update process:

1. Run the generator against the test lexicons
2. Capture the new output as the updated golden files
3. Verify `GoldenFileTest` passes with the new fixtures

## Non-goals

- Markdown rendering in KDoc (Lexicon descriptions are plain text)
- `@param` / `@return` structured KDoc tags (descriptions are
  single-paragraph prose, not per-parameter docs)
- Deprecation on individual enum/token values (Lexicon doesn't support
  this granularity)
