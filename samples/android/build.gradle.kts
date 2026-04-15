plugins {
    // AGP 9+ has built-in Kotlin support, so we do NOT apply
    // `org.jetbrains.kotlin.android` — the plugin is rejected as of AGP 9.0.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kikinlex.atproto.samples.bluesky"
    // compileSdk 35+ is required by androidx.core:core-ktx:1.15.0.
    // We bump to 36 to pick up the current Android runtime behavior set.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kikinlex.atproto.samples.bluesky"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-sample"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9+ has built-in Kotlin and picks up `src/main/kotlin` automatically,
    // so we don't need to customize sourceSets or set kotlinOptions here —
    // jvmTarget follows compileOptions above.

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    // The whole point of this sample: consume the library via `project(...)`
    // with zero Maven Central involvement. Any generator change is picked up
    // automatically on the next build.
    implementation(project(":at-protocol-runtime"))
    implementation(project(":at-protocol-models"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    // AGP 9's built-in Kotlin doesn't expose the `kotlin("test")` DSL helper,
    // so we pin kotlin-test-junit explicitly (pulls in JUnit 4 as the
    // Android unit-test runner backend).
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${libs.versions.kotlin.get()}")
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
