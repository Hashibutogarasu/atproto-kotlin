package com.kikinlex.atproto.generator.emit

import com.kikinlex.atproto.generator.ir.FieldType
import com.kikinlex.atproto.generator.ir.ObjectDef
import com.kikinlex.atproto.generator.ir.ObjectType
import com.kikinlex.atproto.generator.ir.ParamsDefTopLevel
import com.kikinlex.atproto.generator.ir.ParamsType
import com.kikinlex.atproto.generator.ir.RecordDef
import com.kikinlex.atproto.generator.resolved.DefKey
import com.kikinlex.atproto.generator.resolved.Nsid
import com.kikinlex.atproto.generator.resolved.UsageContext
import com.kikinlex.atproto.generator.verify.FqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
internal val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")
internal val ENCODE_DEFAULT = ClassName("kotlinx.serialization", "EncodeDefault")
internal val EXPERIMENTAL_SER_API =
    ClassName("kotlinx.serialization", "ExperimentalSerializationApi")
internal val OPT_IN = ClassName("kotlin", "OptIn")

/**
 * Which encoding strategy a class uses for optional fields.
 *
 *  - [ReadShape] → nullable `T? = null`.
 *  - [MutationShape] → `AtField<T> = AtField.Missing` with `@EncodeDefault(NEVER)`.
 */
public enum class Shape { ReadShape, MutationShape }

public class ModelGenerator(
    private val plan: EmissionPlan,
    private val typeResolver: TypeResolver,
    private val contexts: Map<DefKey, UsageContext>,
) {

    /**
     * Emits all data classes for a single named definition. Returns a list of
     * [TypeSpec]s to be added to the owner FileSpec. Most callers produce one
     * TypeSpec; contextually split ObjectDefs produce two.
     */
    public fun emitForObjectDef(key: DefKey, def: ObjectDef): List<TypeSpec> = emitObjectBacked(
        key = key,
        required = def.required.orEmpty().toSet(),
        properties = def.properties,
    )

    public fun emitForParamsDef(key: DefKey, def: ParamsDefTopLevel): List<TypeSpec> = emitObjectBacked(
        key = key,
        required = def.required.orEmpty().toSet(),
        properties = def.properties,
    )

    public fun emitForRecordDef(key: DefKey, def: RecordDef): List<TypeSpec> {
        val primary = plan.primaryFqName(key)
            ?: error("RecordDef $key missing Primary FqName in plan")
        // Records are mutation-rooted; their primary shape is the mutation shape.
        val type = buildClass(
            fqName = primary,
            origin = key.nsid,
            required = def.record.required.orEmpty().toSet(),
            properties = def.record.properties,
            shape = Shape.MutationShape,
            supertypes = plan.unionMembership[key] ?: emptySet(),
        )
        return listOf(type)
    }

    private fun emitObjectBacked(
        key: DefKey,
        required: Set<String>,
        properties: Map<String, FieldType>,
    ): List<TypeSpec> {
        val primary = plan.primaryFqName(key) ?: return emptyList()
        val input = plan.inputFqName(key)
        val ctx = contexts[key]
        val supertypes = plan.unionMembership[key] ?: emptySet()

        return if (input != null) {
            // Contextual split — Primary is the read-shape, Input is the mutation-shape.
            listOf(
                buildClass(primary, key.nsid, required, properties, Shape.ReadShape, supertypes),
                buildClass(input, key.nsid, required, properties, Shape.MutationShape, emptySet()),
            )
        } else {
            val shape = when (ctx) {
                UsageContext.Mutation -> Shape.MutationShape
                UsageContext.Both -> Shape.MutationShape
                UsageContext.Read, null -> Shape.ReadShape
            }
            listOf(buildClass(primary, key.nsid, required, properties, shape, supertypes))
        }
    }

    internal fun buildClass(
        fqName: FqName,
        origin: Nsid,
        required: Set<String>,
        properties: Map<String, FieldType>,
        shape: Shape,
        supertypes: Set<FqName>,
    ): TypeSpec {
        val sortedPropsPreview = properties.toSortedMap()
        val builder = TypeSpec.classBuilder(fqName.simpleName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(SERIALIZABLE)
        if (sortedPropsPreview.isNotEmpty()) {
            builder.addModifiers(KModifier.DATA)
        }

        for (st in supertypes.sortedBy { it.toString() }) {
            builder.addSuperinterface(ClassName(st.pkg, st.simpleName))
        }

        val ctor = FunSpec.constructorBuilder()
        val sortedProps = sortedPropsPreview
        // Track whether any property actually picked up an experimental-API
        // annotation (currently only `@EncodeDefault(NEVER)` on mutation-side
        // optional fields). If not, we skip the class-level `@OptIn` to keep
        // the emitted source cosmetically clean — no reason to pull in
        // `ExperimentalSerializationApi` on a class with all-required fields.
        var needsOptIn = false

        for ((name, ft) in sortedProps) {
            val baseType = typeResolver.resolve(ft, origin, fqName, name)
            val isRequired = name in required
            val (propType, defaultCode, extraAnnotations) =
                fieldShape(baseType, isRequired, shape)

            val param = ParameterSpec.builder(name, propType)
            if (defaultCode != null) param.defaultValue(defaultCode)
            ctor.addParameter(param.build())

            val prop = PropertySpec.builder(name, propType)
                .initializer(name)
            extraAnnotations.forEach { ann ->
                prop.addAnnotation(ann)
                if (ann.typeName == ENCODE_DEFAULT) needsOptIn = true
            }
            builder.addProperty(prop.build())
        }

        if (needsOptIn) {
            builder.addAnnotation(
                AnnotationSpec.builder(OPT_IN)
                    .addMember("%T::class", EXPERIMENTAL_SER_API)
                    .build(),
            )
        }

        builder.primaryConstructor(ctor.build())
        return builder.build()
    }

    private fun fieldShape(
        baseType: TypeName,
        isRequired: Boolean,
        shape: Shape,
    ): Triple<TypeName, CodeBlock?, List<AnnotationSpec>> {
        if (isRequired) {
            return Triple(baseType, null, emptyList())
        }
        return when (shape) {
            Shape.ReadShape -> Triple(
                baseType.copy(nullable = true),
                CodeBlock.of("null"),
                emptyList(),
            )
            Shape.MutationShape -> {
                val wrapped = ClassName(RUNTIME_PKG, "AtField").parameterizedBy(baseType)
                val encodeDefaultAnn = AnnotationSpec.builder(ENCODE_DEFAULT)
                    .addMember("%T.Mode.NEVER", ENCODE_DEFAULT)
                    .build()
                // Per-field `@Serializable(with = AtFieldSerializer::class)` is
                // load-bearing: without it, the compiler-synthesized serializer
                // for the owning `@Serializable` data class falls back to
                // polymorphic dispatch on the sealed `AtField<T>` type, which
                // fails at runtime on `Defined<T>` (generic subtype can't be
                // auto-registered in the polymorphic scope). The runtime
                // `AtField` itself is NOT annotated at the class level, so
                // every use site must carry this annotation.
                val withSerializerAnn = AnnotationSpec.builder(SERIALIZABLE)
                    .addMember("with = %T::class", AT_FIELD_SERIALIZER)
                    .build()
                Triple(
                    wrapped,
                    CodeBlock.of("%T.Missing", AT_FIELD),
                    listOf(encodeDefaultAnn, withSerializerAnn),
                )
            }
        }
    }

    /**
     * Helper used by XrpcGenerator to synthesize request/response classes
     * against an explicit FqName and ObjectType.
     */
    public fun buildFromObjectType(
        fqName: FqName,
        origin: Nsid,
        obj: ObjectType,
        shape: Shape,
    ): TypeSpec = buildClass(
        fqName = fqName,
        origin = origin,
        required = obj.required.orEmpty().toSet(),
        properties = obj.properties,
        shape = shape,
        supertypes = emptySet(),
    )

    public fun buildFromParamsType(
        fqName: FqName,
        origin: Nsid,
        params: ParamsType,
        shape: Shape,
    ): TypeSpec = buildClass(
        fqName = fqName,
        origin = origin,
        required = params.required.orEmpty().toSet(),
        properties = params.properties,
        shape = shape,
        supertypes = emptySet(),
    )
}
