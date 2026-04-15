## ADDED Requirements

### Requirement: Runtime SHALL provide AtField three-state sealed type and serializer

The system SHALL provide a `sealed interface AtField<out T>` with exactly three variants: `Missing` (data object), `Null` (data object), and `Defined<T>(value: T)`. The system SHALL provide a generic `AtFieldSerializer<T>(inner: KSerializer<T>)` that: on deserialize, reads the JSON element and returns `AtField.Null` if it is `JsonNull` or `AtField.Defined` otherwise; on serialize, emits a JSON null for `Null`, emits the wrapped value for `Defined`, and throws `SerializationException` if asked to serialize `Missing`. `Missing` SHALL only be reachable via the Kotlin default parameter mechanism combined with `@EncodeDefault(EncodeDefault.Mode.NEVER)`; the runtime Json configuration SHALL keep `explicitNulls = true`.

#### Scenario: Round-trip of all three states

- **GIVEN** a data class `Foo(val a: AtField<String> = AtField.Missing, val b: AtField<String> = AtField.Missing, val c: AtField<String> = AtField.Missing)` with `@EncodeDefault(NEVER)` on each field
- **WHEN** a `Foo(a = AtField.Missing, b = AtField.Null, c = AtField.Defined("hello"))` is encoded to JSON and decoded back
- **THEN** the encoded JSON is `{"b":null,"c":"hello"}` (key `a` absent)
- **AND** the decoded value equals the original

#### Scenario: Serializing Missing fails loud

- **WHEN** `AtFieldSerializer.serialize` is invoked on `AtField.Missing` (e.g. because the caller forgot `@EncodeDefault(NEVER)`)
- **THEN** it throws `SerializationException` with a diagnostic explaining the required annotations

### Requirement: Runtime SHALL provide typed value classes for AT Protocol string formats

The system SHALL provide `@JvmInline value class` wrappers over `String` for the following AT Protocol string formats: `Did`, `Handle`, `AtIdentifier`, `AtUri`, `Cid`, `Nsid`, `RecordKey`, `Tid`, `Datetime`, `Language`, `Uri`. Each wrapper SHALL provide a serializer that reads/writes the underlying string. Codegen SHALL emit these types in place of `String` wherever a lexicon field declares the corresponding `format`.

#### Scenario: Serializing a Did wrapper

- **WHEN** a field of type `Did` holding `Did("did:plc:abc123")` is serialized
- **THEN** the JSON output is the plain string `"did:plc:abc123"` (not a wrapped object)

### Requirement: Runtime SHALL provide a Ktor-backed XrpcClient

The system SHALL provide an `XrpcClient` that wraps a Ktor `HttpClient` and exposes suspend functions for executing queries and procedures. Queries SHALL be issued as `GET /xrpc/<nsid>` with parameters serialized into the query string (arrays repeat the key). Procedures SHALL be issued as `POST /xrpc/<nsid>` with the parameters in the query string and the input body serialized per its declared encoding. The client SHALL surface `Bearer`-style auth via a pluggable `AuthProvider` attached per call or per client instance, not as a field on request DTOs. Error responses SHALL be decoded into a sealed `XrpcError` hierarchy with a generic `XrpcError.Unknown(name, message, status)` fallback.

#### Scenario: Issuing a query

- **WHEN** the client issues `app.bsky.feed.getTimeline` with parameters `limit = 50`
- **THEN** the underlying HTTP request is `GET /xrpc/app.bsky.feed.getTimeline?limit=50`

#### Scenario: Issuing a procedure

- **WHEN** the client issues `com.atproto.server.createSession` with input body `{"identifier":"alice","password":"..."}`
- **THEN** the underlying HTTP request is `POST /xrpc/com.atproto.server.createSession` with content-type `application/json` and the JSON body as input

#### Scenario: Decoding a declared error

- **WHEN** the server returns HTTP 401 with body `{"error":"AuthMissing","message":"..."}` for a known endpoint
- **THEN** the client raises a typed error case matching `AuthMissing`, not a generic HTTP exception

### Requirement: Runtime SHALL provide open-union Unknown infrastructure

The system SHALL provide a base class or utility that generated `JsonContentPolymorphicSerializer` subclasses use to capture unknown `$type` variants. The captured `Unknown(type: String, raw: JsonObject)` value SHALL preserve the full original JSON object (including the `$type` key) so that re-serialization is lossless.

#### Scenario: Lossless round-trip of an unknown union variant

- **GIVEN** a union field that does not know about `$type = "app.bsky.embed.futureThing"`
- **WHEN** a value carrying that type is deserialized and then re-serialized
- **THEN** the output JSON is semantically equal to the input (same keys, same values)
