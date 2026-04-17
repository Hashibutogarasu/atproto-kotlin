## 1. IR: Capture deprecation metadata

- [x] 1.1 Add `deprecated: Boolean` and `deprecatedMessage: String?` (backed by `JsonElement`) to all `Definition` variants in `Definition.kt` (`RecordDef`, `QueryDef`, `ProcedureDef`, `ObjectDef`, `TokenDef`, `SubscriptionDef`)
- [x] 1.2 Add `deprecated: Boolean` and `deprecatedMessage: String?` to all `FieldType` variants in `FieldType.kt` that carry `description` (`StringType`, `IntegerType`, `BooleanType`, `ObjectType`, `ArrayType`, `RefType`, `UnionType`, `BytesType`, `CidLinkType`, `BlobType`)
- [x] 1.3 Unit-test: parse a Lexicon node with `"deprecated": true` and verify `deprecated == true`, `deprecatedMessage == null`
- [x] 1.4 Unit-test: parse a Lexicon node with `"deprecated": "Use foo instead"` and verify `deprecated == true`, `deprecatedMessage == "Use foo instead"`
- [x] 1.5 Unit-test: parse a Lexicon node with no `"deprecated"` field and verify `deprecated == false`

## 2. KDoc sanitization utility

- [x] 2.1 Create `KdocUtils.kt` in the `emit` package with `fun String.sanitizeForKdoc(): String` that escapes `%` → `%%` and `$` → `${'$'}`
- [x] 2.2 Unit-test: `"100% of quota"` → `"100%% of quota"`
- [x] 2.3 Unit-test: `"Uses $type for dispatch"` → `"Uses ${'$'}type for dispatch"`
- [x] 2.4 Unit-test: string with no special characters passes through unchanged
- [x] 2.5 Unit-test: string with both `%` and `$` escapes both

## 3. KDoc emission — ModelGenerator

- [x] 3.1 Emit KDoc on `TypeSpec` for record and object classes from `Definition.description`
- [x] 3.2 Emit KDoc on `PropertySpec` for constructor properties from `FieldType.description`
- [x] 3.3 Verify: generate a record with descriptions and inspect output for KDoc blocks
- [x] 3.4 Verify: generate a record without descriptions and confirm no KDoc is emitted

## 4. KDoc emission — XrpcGenerator

- [x] 4.1 Emit KDoc on Request/Response `TypeSpec` from `QueryDef.description` / `ProcedureDef.description`
- [x] 4.2 Emit KDoc on Request/Response properties from `FieldType.description`
- [x] 4.3 Verify: generate a query with descriptions and inspect output

## 5. KDoc emission — UnionGenerator

- [x] 5.1 Emit KDoc on the sealed interface `TypeSpec` from the `UnionType.description` or owning `Definition.description`
- [x] 5.2 Verify: generate a union with a description and inspect output

## 6. KDoc emission — ServiceGenerator

- [x] 6.1 Emit KDoc on service method `FunSpec` from `QueryDef.description` / `ProcedureDef.description`
- [x] 6.2 Verify: generate a service with method descriptions and inspect output

## 7. @Deprecated annotation — ModelGenerator

- [x] 7.1 Emit `@Deprecated` on `TypeSpec` when `Definition.deprecated == true`, using `deprecatedMessage` or the default message
- [x] 7.2 Emit `@Deprecated` on `PropertySpec` when `FieldType.deprecated == true`
- [x] 7.3 Unit-test: deprecated definition with reason emits `@Deprecated("reason")`
- [x] 7.4 Unit-test: deprecated definition without reason emits `@Deprecated("Deprecated in the AT Protocol Lexicon")`
- [x] 7.5 Unit-test: non-deprecated definition emits no `@Deprecated`

## 8. @Deprecated annotation — XrpcGenerator

- [x] 8.1 Emit `@Deprecated` on Request/Response classes when the owning `QueryDef`/`ProcedureDef` is deprecated
- [x] 8.2 Emit `@Deprecated` on Request/Response properties when the `FieldType` is deprecated

## 9. @Deprecated annotation — UnionGenerator + ServiceGenerator

- [x] 9.1 Emit `@Deprecated` on the sealed interface when the owning definition is deprecated
- [x] 9.2 Emit `@Deprecated` on service methods when the `QueryDef`/`ProcedureDef` is deprecated

## 10. Golden file updates

- [x] 10.1 Run the generator against the test lexicon corpus and capture updated golden files
- [x] 10.2 Verify `GoldenFileTest` passes with the new fixtures
- [x] 10.3 Spot-check: confirm KDoc appears on a representative record, query, union, and service method
- [x] 10.4 Spot-check: confirm `@Deprecated` appears on at least one element in the generated output (if any upstream lexicon uses `deprecated`)

## 11. Full build verification

- [x] 11.1 `./gradlew :at-protocol-generator:test` passes
- [x] 11.2 `./gradlew :at-protocol-models:compileKotlinJvm` succeeds (generated KDoc compiles cleanly)
- [x] 11.3 `./gradlew spotlessCheck` passes
- [x] 11.4 `pre-commit run --all-files` passes
