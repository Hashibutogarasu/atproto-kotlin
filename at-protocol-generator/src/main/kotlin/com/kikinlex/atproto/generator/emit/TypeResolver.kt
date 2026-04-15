package com.kikinlex.atproto.generator.emit

import com.kikinlex.atproto.generator.ir.ArrayType
import com.kikinlex.atproto.generator.ir.BlobType
import com.kikinlex.atproto.generator.ir.BooleanType
import com.kikinlex.atproto.generator.ir.BytesType
import com.kikinlex.atproto.generator.ir.CidLinkType
import com.kikinlex.atproto.generator.ir.FieldType
import com.kikinlex.atproto.generator.ir.IntegerType
import com.kikinlex.atproto.generator.ir.NullType
import com.kikinlex.atproto.generator.ir.ObjectType
import com.kikinlex.atproto.generator.ir.ParamsType
import com.kikinlex.atproto.generator.ir.RefType
import com.kikinlex.atproto.generator.ir.StringType
import com.kikinlex.atproto.generator.ir.TokenType
import com.kikinlex.atproto.generator.ir.UnionType
import com.kikinlex.atproto.generator.ir.UnknownType
import com.kikinlex.atproto.generator.resolved.DefKey
import com.kikinlex.atproto.generator.resolved.Nsid
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT

internal val RUNTIME_PKG = "com.kikinlex.atproto.runtime"
internal val DID = ClassName(RUNTIME_PKG, "Did")
internal val HANDLE = ClassName(RUNTIME_PKG, "Handle")
internal val AT_IDENTIFIER = ClassName(RUNTIME_PKG, "AtIdentifier")
internal val AT_URI = ClassName(RUNTIME_PKG, "AtUri")
internal val CID = ClassName(RUNTIME_PKG, "Cid")
internal val NSID_CLASS = ClassName(RUNTIME_PKG, "Nsid")
internal val RECORD_KEY = ClassName(RUNTIME_PKG, "RecordKey")
internal val TID = ClassName(RUNTIME_PKG, "Tid")
internal val DATETIME = ClassName(RUNTIME_PKG, "Datetime")
internal val LANGUAGE = ClassName(RUNTIME_PKG, "Language")
internal val URI_CLASS = ClassName(RUNTIME_PKG, "Uri")
internal val AT_FIELD = ClassName(RUNTIME_PKG, "AtField")
internal val BLOB = ClassName(RUNTIME_PKG, "Blob")
internal val CID_LINK = ClassName(RUNTIME_PKG, "CidLink")

internal val JSON_OBJECT = ClassName("kotlinx.serialization.json", "JsonObject")

/**
 * Translates [FieldType]s to KotlinPoet [TypeName]s using the runtime value
 * classes for known string formats and the [EmissionPlan] for ref/union
 * lookups.
 */
public class TypeResolver(
    private val plan: EmissionPlan,
) {
    /**
     * Resolve a field type at [origin]. [ownerFqName] and [fieldName] are
     * required so UnionType resolution can find the synthesized union in the
     * plan by (owner, fieldName).
     */
    public fun resolve(
        ft: FieldType,
        origin: Nsid,
        ownerFqName: com.kikinlex.atproto.generator.verify.FqName,
        fieldName: String,
    ): TypeName = when (ft) {
        is NullType -> UNIT
        is BooleanType -> BOOLEAN
        is IntegerType -> LONG
        is StringType -> stringFormatType(ft.format)
        is BytesType -> BYTE_ARRAY
        is CidLinkType -> CID_LINK
        is BlobType -> BLOB
        is ArrayType -> LIST.parameterizedBy(resolve(ft.items, origin, ownerFqName, fieldName))
        is ObjectType -> JSON_OBJECT.also {
            System.err.println("[warn] inline nested object field '$fieldName' in $ownerFqName emitted as JsonObject (v1)")
        }
        is ParamsType -> JSON_OBJECT.also {
            System.err.println("[warn] inline nested params field '$fieldName' in $ownerFqName emitted as JsonObject (v1)")
        }
        is RefType -> {
            val target = DefKey.resolve(ft.ref, origin)
            val fq = plan.primaryFqName(target)
            if (fq != null) {
                ClassName(fq.pkg, fq.simpleName)
            } else {
                System.err.println("[warn] ref '${ft.ref}' (target $target) has no emitted class; using JsonObject")
                JSON_OBJECT
            }
        }
        is UnionType -> {
            val site = plan.unionSites.firstOrNull {
                it.owner == ownerFqName && it.fieldName == fieldName
            }
            if (site != null) {
                ClassName(site.fqName.pkg, site.fqName.simpleName)
            } else {
                System.err.println("[warn] union field '$fieldName' in $ownerFqName missing from plan; using JsonObject")
                JSON_OBJECT
            }
        }
        is UnknownType -> JSON_OBJECT
        is TokenType -> STRING
    }

    private fun stringFormatType(format: String?): ClassName = when (format) {
        "did" -> DID
        "handle" -> HANDLE
        "at-identifier" -> AT_IDENTIFIER
        "at-uri" -> AT_URI
        "cid" -> CID
        "nsid" -> NSID_CLASS
        "record-key" -> RECORD_KEY
        "tid" -> TID
        "datetime" -> DATETIME
        "language" -> LANGUAGE
        "uri" -> URI_CLASS
        else -> STRING
    }
}
