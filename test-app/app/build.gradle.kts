import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.okhsunrog.vpnhide.test"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.okhsunrog.vpnhide.test"
        minSdk = 26
        targetSdk = 35
        versionCode = 401
        versionName = "0.4.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["password"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["password"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

// Build the Rust native library via cargo-ndk and copy to jniLibs
// before Gradle processes native libraries.
// Build Rust native library via cargo-ndk. Always runs (cargo handles
// its own incremental build caching), then copies the .so to jniLibs.
val buildRustNative by tasks.registering {
    // Never skip — cargo's own up-to-date check is authoritative.
    outputs.upToDateWhen { false }

    doLast {
        exec {
            workingDir = file("../native")
            commandLine("cargo", "ndk", "-t", "arm64-v8a", "build", "--release")
        }
        val src = file("../native/target/aarch64-linux-android/release/libvpnhide_test.so")
        val dst = file("src/main/jniLibs/arm64-v8a/libvpnhide_test.so")
        dst.parentFile.mkdirs()
        src.copyTo(dst, overwrite = true)
    }
}

tasks.named("preBuild") {
    dependsOn(buildRustNative)
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
