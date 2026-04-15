plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":at-protocol-runtime"))
            }
            // Pick up generator output so published `:at-protocol-models`
            // includes the emitted lexicon sources.
            kotlin.srcDir(
                layout.buildDirectory.dir("generated/source/lexicon/commonMain/kotlin"),
            )
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Regenerate lexicon sources before compiling this module. No-ops when
// `:at-protocol-generator/lexicons/` is empty (fresh clone without lexicons).
tasks.matching {
    it.name.startsWith("compileKotlin") ||
        it.name == "compileCommonMainKotlinMetadata" ||
        it.name.endsWith("SourcesJar") ||
        it.name == "sourcesJar"
}.configureEach {
    dependsOn(":at-protocol-generator:generateModels")
}
