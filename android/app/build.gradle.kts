plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.shadowur0.netsira"

    val releaseStoreFile = System.getenv("NETSIRA_ANDROID_KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("NETSIRA_ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("NETSIRA_ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("NETSIRA_ANDROID_KEY_PASSWORD")

    signingConfigs {
        if (!releaseStoreFile.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.shadowur0.netsira"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.0-preview.5"
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
