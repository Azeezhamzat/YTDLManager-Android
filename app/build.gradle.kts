plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from the environment so no key material ever lives in
// the repository. CI decodes the keystore from a repository secret; locally
// you can export the same variables (see README > Signing). Without them,
// `assembleRelease` still works and produces *unsigned* APKs.
val releaseKeystore: String? = System.getenv("YTDLM_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.azeezhamzat.ytdlmanager"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.azeezhamzat.ytdlmanager"
        // 29 = Android 10: the first release with MediaStore.Downloads, which
        // is how files reach the public Downloads folder without any storage
        // permission.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("YTDLM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YTDLM_KEY_ALIAS")
                keyPassword = System.getenv("YTDLM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Off on purpose: the APK is ~95% native Python/FFmpeg payload, so
            // R8 would save little, and the library parses JSON reflectively.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // One APK per CPU type keeps downloads small (each ABI carries its own
    // Python + FFmpeg build), plus a universal APK for anyone unsure.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            // youtubedl-android runs Python/FFmpeg/QuickJS as executables from
            // the native library directory, so they must be extracted on
            // install (this sets android:extractNativeLibs="true").
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
