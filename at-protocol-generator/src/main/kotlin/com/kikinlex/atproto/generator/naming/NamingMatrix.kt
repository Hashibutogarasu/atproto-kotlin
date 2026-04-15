package com.kikinlex.atproto.generator.naming

import com.kikinlex.atproto.generator.ir.Definition
import com.kikinlex.atproto.generator.ir.ObjectDef
import com.kikinlex.atproto.generator.ir.ParamsDefTopLevel
import com.kikinlex.atproto.generator.ir.ProcedureDef
import com.kikinlex.atproto.generator.ir.QueryDef
import com.kikinlex.atproto.generator.ir.RecordDef
import com.kikinlex.atproto.generator.ir.StringDefTopLevel
import com.kikinlex.atproto.generator.ir.SubscriptionDef
import com.kikinlex.atproto.generator.ir.TokenDef
import com.kikinlex.atproto.generator.resolved.DefKey
import com.kikinlex.atproto.generator.resolved.Nsid
import com.kikinlex.atproto.generator.resolved.UsageContext
import com.kikinlex.atproto.generator.verify.FqName

/**
 * Role of an emitted class. Most defs produce a single class in role [Primary].
 * Queries and procedures emit a `Request` class for their parameters/input and a
 * `Response` class for their output. Subscriptions emit a `Message` class.
 *
 * Contextual split (design.md Decision 5) can introduce an extra [Input] variant
 * when a shared object needs a distinct mutation-side shape.
 */
public enum class NameRole { Primary, Request, Response, Message, Input }

public data class EmittedClass(
    public val source: DefKey,
    public val role: NameRole,
    public val fqName: FqName,
)

/**
 * Computes Kotlin `FqName`s for every definition in the lexicon corpus, per the
 * rules in design.md Decisions 4, 5. Stateless; the caller supplies a
 * [UsageContext] map (from [com.kikinlex.atproto.generator.resolved.ContextTagger])
 * so that the contextual-split rule can fire.
 */
public class NamingMatrix(
    public val rootPackage: String = "com.kikinlex.atproto",
) {
    /** Package for a given NSID. Drops the trailing segment so `.defs` and sibling files collapse together. */
    public fun packageFor(nsid: Nsid): String {
        val segments = nsid.raw.split('.').dropLast(1)
        return if (segments.isEmpty()) rootPackage else "$rootPackage.${segments.joinToString(".")}"
    }

    /**
     * Produces every [EmittedClass] required for [key]/[def]. For most defs
     * that's one class; for query/procedure it's a request + response pair; for
     * contextual-split cases it's a primary + input pair.
     *
     * [contextOf] maps a DefKey to its usage context. Only [ObjectDef] consults
     * this — other def kinds are fixed-role regardless.
     */
    public fun namesFor(
        key: DefKey,
        def: Definition,
        contextOf: (DefKey) -> UsageContext? = { null },
    ): List<EmittedClass> {
        val pkg = packageFor(key.nsid)
        val terminal = terminalPascal(key.nsid)
        return when (def) {
            is RecordDef -> listOf(EmittedClass(key, NameRole.Primary, FqName(pkg, terminal)))
            is QueryDef -> listOfNotNull(
                if (def.parameters != null) EmittedClass(key, NameRole.Request, FqName(pkg, "${terminal}Request")) else null,
                if (def.output?.schema != null) EmittedClass(key, NameRole.Response, FqName(pkg, "${terminal}Response")) else null,
            )
            is ProcedureDef -> listOfNotNull(
                if (def.input != null || def.parameters != null) {
                    EmittedClass(key, NameRole.Request, FqName(pkg, "${terminal}Request"))
                } else {
                    null
                },
                if (def.output?.schema != null) {
                    EmittedClass(key, NameRole.Response, FqName(pkg, "${terminal}Response"))
                } else {
                    null
                },
            )
            is SubscriptionDef -> listOfNotNull(
                if (def.parameters != null) EmittedClass(key, NameRole.Request, FqName(pkg, "${terminal}Request")) else null,
                if (def.message?.schema != null) EmittedClass(key, NameRole.Message, FqName(pkg, "${terminal}Message")) else null,
            )
            is ObjectDef -> namesForObject(key, def, pkg, contextOf(key))
            is ParamsDefTopLevel -> namesForParams(key, def, pkg, contextOf(key))
            is TokenDef -> listOf(EmittedClass(key, NameRole.Primary, FqName(pkg, secondaryPascal(key))))
            is StringDefTopLevel -> listOf(EmittedClass(key, NameRole.Primary, FqName(pkg, secondaryPascal(key))))
            is com.kikinlex.atproto.generator.ir.ArrayDefTopLevel -> emptyList() // v1: typedef-style array defs not emitted as classes
        }
    }

    private fun namesForObject(
        key: DefKey,
        def: ObjectDef,
        pkg: String,
        context: UsageContext?,
    ): List<EmittedClass> {
        val base = if (key.name == "main") terminalPascal(key.nsid) else secondaryPascal(key)
        return contextualSplit(key, base, pkg, def.required, def.properties.keys, context)
    }

    private fun namesForParams(
        key: DefKey,
        def: ParamsDefTopLevel,
        pkg: String,
        context: UsageContext?,
    ): List<EmittedClass> {
        val base = if (key.name == "main") terminalPascal(key.nsid) else secondaryPascal(key)
        return contextualSplit(key, base, pkg, def.required, def.properties.keys, context)
    }

    /**
     * Decides whether a shared object collapses to one class or splits into
     * `Foo` + `FooInput`. Single class when all properties are required (or when
     * the context is anything other than [UsageContext.Both]).
     */
    private fun contextualSplit(
        key: DefKey,
        base: String,
        pkg: String,
        required: List<String>?,
        propertyNames: Set<String>,
        context: UsageContext?,
    ): List<EmittedClass> {
        val bothContexts = context == UsageContext.Both
        val allRequired = (required?.toSet() ?: emptySet()) == propertyNames
        return if (bothContexts && !allRequired) {
            listOf(
                EmittedClass(key, NameRole.Primary, FqName(pkg, base)),
                EmittedClass(key, NameRole.Input, FqName(pkg, "${base}Input")),
            )
        } else {
            listOf(EmittedClass(key, NameRole.Primary, FqName(pkg, base)))
        }
    }

    /**
     * Computes the Kotlin simple-name for a non-main fragment def. In `.defs`
     * files it's just the fragment PascalCased. In non-`.defs` files it's
     * `<PrimaryName><FragmentName>` — flat, no nesting.
     */
    public fun secondaryPascal(key: DefKey): String {
        val fragment = pascalCase(key.name)
        return if (isDefsFile(key.nsid)) fragment else "${terminalPascal(key.nsid)}$fragment"
    }

    /** PascalCased last segment of the NSID. */
    public fun terminalPascal(nsid: Nsid): String {
        val last = nsid.raw.substringAfterLast('.')
        return pascalCase(last)
    }

    public fun isDefsFile(nsid: Nsid): Boolean = nsid.raw.substringAfterLast('.') == "defs"

    private fun pascalCase(s: String): String = if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)
}
