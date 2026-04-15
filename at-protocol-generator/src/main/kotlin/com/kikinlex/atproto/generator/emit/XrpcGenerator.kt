package com.kikinlex.atproto.generator.emit

import com.kikinlex.atproto.generator.ir.ObjectType
import com.kikinlex.atproto.generator.ir.ProcedureDef
import com.kikinlex.atproto.generator.ir.QueryDef
import com.kikinlex.atproto.generator.ir.RefType
import com.kikinlex.atproto.generator.naming.NameRole
import com.kikinlex.atproto.generator.resolved.DefKey
import com.kikinlex.atproto.generator.verify.FqName
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Unified emission envelope so XrpcGenerator can produce either a data class or
 * a typealias per Request/Response slot. Typealiases are needed when a query's
 * output.schema is a bare `ref` to a named view def — the Response is then just
 * another name for that view class, not a new class.
 */
public sealed class XrpcEmitted {
    public abstract val fqName: FqName
    public data class Class(override val fqName: FqName, val typeSpec: TypeSpec) : XrpcEmitted()
    public data class Alias(override val fqName: FqName, val aliasSpec: TypeAliasSpec) : XrpcEmitted()
}

/**
 * Emits Request / Response data classes for a Query or Procedure def.
 *
 * v1 simplification: we do not emit an XRPC service interface. Callers can
 * construct request objects directly and decode response JSON into the
 * response data classes. A future pass will add `suspend` method wrappers.
 */
public class XrpcGenerator(
    private val plan: EmissionPlan,
    private val models: ModelGenerator,
) {

    public fun emitQuery(key: DefKey, def: QueryDef): List<XrpcEmitted> {
        val out = mutableListOf<XrpcEmitted>()
        val request = plan.classes[key]?.firstOrNull { it.role == NameRole.Request }
        val response = plan.classes[key]?.firstOrNull { it.role == NameRole.Response }

        if (request != null && def.parameters != null) {
            out += XrpcEmitted.Class(
                request.fqName,
                models.buildFromParamsType(
                    fqName = request.fqName,
                    origin = key.nsid,
                    params = def.parameters,
                    shape = Shape.MutationShape,
                ),
            )
        }
        if (response != null) {
            emitResponse(key, response.fqName, def.output?.schema)?.let { out += it }
        }
        return out
    }

    public fun emitProcedure(key: DefKey, def: ProcedureDef): List<XrpcEmitted> {
        val out = mutableListOf<XrpcEmitted>()
        val request = plan.classes[key]?.firstOrNull { it.role == NameRole.Request }
        val response = plan.classes[key]?.firstOrNull { it.role == NameRole.Response }

        if (request != null) {
            val inputSchema = def.input?.schema
            when {
                inputSchema is ObjectType -> out += XrpcEmitted.Class(
                    request.fqName,
                    models.buildFromObjectType(
                        fqName = request.fqName,
                        origin = key.nsid,
                        obj = inputSchema,
                        shape = Shape.MutationShape,
                    ),
                )
                def.parameters != null -> out += XrpcEmitted.Class(
                    request.fqName,
                    models.buildFromParamsType(
                        fqName = request.fqName,
                        origin = key.nsid,
                        params = def.parameters,
                        shape = Shape.MutationShape,
                    ),
                )
                else -> System.err.println(
                    "[warn] procedure $key has request role but no input/params; skipping Request",
                )
            }
        }
        if (response != null) {
            emitResponse(key, response.fqName, def.output?.schema)?.let { out += it }
        }
        return out
    }

    private fun emitResponse(
        key: DefKey,
        responseFq: FqName,
        schema: com.kikinlex.atproto.generator.ir.FieldType?,
    ): XrpcEmitted? = when (schema) {
        is ObjectType -> XrpcEmitted.Class(
            responseFq,
            models.buildFromObjectType(
                fqName = responseFq,
                origin = key.nsid,
                obj = schema,
                shape = Shape.ReadShape,
            ),
        )
        is RefType -> {
            // Resolve the ref and emit a typealias to its Primary class.
            val target = com.kikinlex.atproto.generator.resolved.DefKey.resolve(schema.ref, key.nsid)
            val targetFq = plan.primaryFqName(target)
            if (targetFq == null) {
                System.err.println("[warn] $key output.schema ref '${schema.ref}' → no primary class; skipping Response")
                null
            } else {
                val alias = TypeAliasSpec.builder(
                    responseFq.simpleName,
                    com.squareup.kotlinpoet.ClassName(targetFq.pkg, targetFq.simpleName),
                ).addModifiers(com.squareup.kotlinpoet.KModifier.PUBLIC).build()
                XrpcEmitted.Alias(responseFq, alias)
            }
        }
        null -> null
        else -> {
            System.err.println("[warn] $key output.schema type ${schema::class.simpleName} unsupported; skipping Response")
            null
        }
    }
}
