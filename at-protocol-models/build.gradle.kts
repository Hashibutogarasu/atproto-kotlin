plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

// iOS targets are declared only on macOS hosts — see
// `:at-protocol-runtime/build.gradle.kts` for the rationale.
val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

kotlin {
    jvmToolchain(17)

    jvm()
    if (isMacHost) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

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

// Publishing config — see `:at-protocol-runtime` for the rationale.
// The same pattern applies: GitHub Packages for interim; iOS publications
// are skipped on non-macOS hosts so Linux CI produces JVM + metadata.
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kikin81/atproto-kotlin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("at-protocol-models")
            description.set(
                "Code-generated AT Protocol lexicon models (records, queries, " +
                    "procedures, open unions) for the Kotlin Multiplatform SDK. " +
                    "Depends on :at-protocol-runtime for shared base types.",
            )
            url.set("https://github.com/kikin81/atproto-kotlin")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("kikin81")
                    name.set("Francisco Velazquez")
                    url.set("https://github.com/kikin81")
                }
            }
            scm {
                url.set("https://github.com/kikin81/atproto-kotlin")
                connection.set("scm:git:git://github.com/kikin81/atproto-kotlin.git")
                developerConnection.set("scm:git:ssh://git@github.com/kikin81/atproto-kotlin.git")
            }
        }
    }
}
