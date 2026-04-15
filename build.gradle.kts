plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "com.kikinlex.atproto"
}

// Spotless + ktlint across every subproject. Skips build/ (including generated
// lexicon sources under :at-protocol-models/build/generated), node_modules,
// and the installed @atproto/lex lexicons directory.
val ktlintVersion = libs.versions.ktlint.get()

subprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            // `golden/**` is a checked-in fixture of raw KotlinPoet output
            // consumed by GoldenFileTest; it must NOT be reformatted by
            // Spotless or the byte-diff test fails on every `spotlessApply`.
            targetExclude("**/build/**", "**/generated/**", "**/test/resources/golden/**")
            ktlint(ktlintVersion).editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_filename" to "disabled",
                    // Generator output uses long import paths; keep humans unconstrained.
                    "max_line_length" to "off",
                ),
            )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion).editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "max_line_length" to "off",
                ),
            )
        }
    }
}
