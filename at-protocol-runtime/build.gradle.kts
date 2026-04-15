plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

kotlin {
    jvmToolchain(17)

    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Publishing config — GitHub Packages for the interim, Maven Central
// later. The `kotlin-multiplatform` plugin auto-registers publications
// for every declared target plus a `kotlinMultiplatform` metadata
// publication, so we only need to configure the repository here.
//
// iOS publications require macOS runners to produce klibs; skip them on
// non-macOS hosts so Linux CI can publish the JVM + metadata pair
// without failing on missing iOS artifacts. iOS consumers wait for
// the Maven Central cut on a macOS publisher.
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
            name.set("at-protocol-runtime")
            description.set(
                "Hand-written runtime for the AT Protocol Kotlin Multiplatform SDK " +
                    "(value classes, AtField, open-union serializers, XrpcClient).",
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

// Gate iOS publish tasks behind macOS — Linux runners can publish
// JVM + metadata only. The iOS klib publications fail at task
// execution time on Linux because the underlying klibs aren't built.
val isMacOs = System.getProperty("os.name").lowercase().contains("mac")
tasks.withType<PublishToMavenRepository>().configureEach {
    val isIosPublication = publication.name.startsWith("ios")
    onlyIf { !isIosPublication || isMacOs }
}
