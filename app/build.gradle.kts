import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: either a local keystore.properties file, or CI environment
// variables (IRIS_KEYSTORE / IRIS_STORE_PASSWORD / IRIS_KEY_ALIAS / IRIS_KEY_PASSWORD).
// When nothing is configured the release build falls back to debug signing so
// the pipeline never breaks.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
}
val releaseStorePath: String? =
    keystoreProps.getProperty("storeFile") ?: System.getenv("IRIS_KEYSTORE")
val hasReleaseKeystore: Boolean =
    !releaseStorePath.isNullOrBlank() && File(releaseStorePath).exists()

android {
    namespace = "com.irisx.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.irisx.ai"
        // Android 10 and above
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = File(releaseStorePath!!)
                storePassword = keystoreProps.getProperty("storePassword")
                    ?: System.getenv("IRIS_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias")
                    ?: System.getenv("IRIS_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: System.getenv("IRIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 is off for now: the tool registry, Vosk and ML Kit paths need
            // proper keep rules before shrinking can be trusted.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // On-device OCR + image understanding (offline after first model fetch)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.8")

    // Camera vision
    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:" + cameraX)
    implementation("androidx.camera:camera-camera2:" + cameraX)
    implementation("androidx.camera:camera-lifecycle:" + cameraX)
    implementation("androidx.camera:camera-view:" + cameraX)

    // True offline speech recognition (model downloaded once at runtime)
    implementation("com.alphacephei:vosk-android:0.3.47")
}
