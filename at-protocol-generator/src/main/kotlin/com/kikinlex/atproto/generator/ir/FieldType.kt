package com.kikinlex.atproto.generator.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Inline field type appearing inside an object's `properties`, an array's `items`,
 * a params block, a query/procedure input or output schema, etc.
 *
 * Refs are represented as raw strings ([RefType.ref], [UnionType.refs]) in the
 * parsed IR — resolution happens in a separate phase via [com.kikinlex.atproto.generator.resolved.RefResolver].
 */
@Serializable
public sealed interface FieldType {
    public val description: String?
}

@Serializable
@SerialName("null")
public data class NullType(
    override val description: String? = null,
) : FieldType

@Serializable
@SerialName("boolean")
public data class BooleanType(
    override val description: String? = null,
    public val default: Boolean? = null,
    public val const: Boolean? = null,
) : FieldType

@Serializable
@SerialName("integer")
public data class IntegerType(
    override val description: String? = null,
    public val minimum: Long? = null,
    public val maximum: Long? = null,
    public val enum: List<Long>? = null,
    public val default: JsonElement? = null,
    public val const: JsonElement? = null,
) : FieldType

@Serializable
@SerialName("string")
public data class StringType(
    override val description: String? = null,
    public val format: String? = null,
    public val minLength: Int? = null,
    public val maxLength: Int? = null,
    public val minGraphemes: Int? = null,
    public val maxGraphemes: Int? = null,
    public val knownValues: List<String>? = null,
    public val enum: List<String>? = null,
    public val default: String? = null,
    public val const: String? = null,
) : FieldType

@Serializable
@SerialName("bytes")
public data class BytesType(
    override val description: String? = null,
    public val minLength: Int? = null,
    public val maxLength: Int? = null,
) : FieldType

@Serializable
@SerialName("cid-link")
public data class CidLinkType(
    override val description: String? = null,
) : FieldType

@Serializable
@SerialName("blob")
public data class BlobType(
    override val description: String? = null,
    public val accept: List<String>? = null,
    public val maxSize: Long? = null,
) : FieldType

@Serializable
@SerialName("array")
public data class ArrayType(
    override val description: String? = null,
    public val items: FieldType,
    public val minLength: Int? = null,
    public val maxLength: Int? = null,
) : FieldType

@Serializable
@SerialName("object")
public data class ObjectType(
    override val description: String? = null,
    public val required: List<String>? = null,
    public val nullable: List<String>? = null,
    public val properties: Map<String, FieldType> = emptyMap(),
) : FieldType

@Serializable
@SerialName("params")
public data class ParamsType(
    override val description: String? = null,
    public val required: List<String>? = null,
    public val properties: Map<String, FieldType> = emptyMap(),
) : FieldType

@Serializable
@SerialName("ref")
public data class RefType(
    override val description: String? = null,
    public val ref: String,
) : FieldType

@Serializable
@SerialName("union")
public data class UnionType(
    override val description: String? = null,
    public val refs: List<String> = emptyList(),
    public val closed: Boolean = false,
) : FieldType

@Serializable
@SerialName("unknown")
public data class UnknownType(
    override val description: String? = null,
) : FieldType

@Serializable
@SerialName("token")
public data class TokenType(
    override val description: String? = null,
) : FieldType
