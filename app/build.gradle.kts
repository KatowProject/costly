plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.katowproject.costly"
    compileSdk {
        version = release(37)
    }

    val envVersionName = System.getenv("APP_VERSION_NAME")
        ?: project.findProperty("versionName") as? String
    val cleanVersionName = envVersionName?.removePrefix("v")?.trim()?.ifBlank { null } ?: "1.0.0"

    val envVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull()
        ?: (project.findProperty("versionCode") as? String)?.toIntOrNull()
        ?: run {
            val parts = cleanVersionName.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size >= 2) {
                val major = parts.getOrElse(0) { 0 }
                val minor = parts.getOrElse(1) { 0 }
                val patch = parts.getOrElse(2) { 0 }
                major * 10000 + minor * 100 + patch
            } else 1
        }

    defaultConfig {
        applicationId = "com.katowproject.costly"
        minSdk = 33
        targetSdk = 37
        versionCode = envVersionCode
        versionName = cleanVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val envKeystore = System.getenv("KEYSTORE_FILE")
            val keystoreFile = when {
                !envKeystore.isNullOrBlank() && file(envKeystore).exists() -> file(envKeystore)
                !envKeystore.isNullOrBlank() && rootProject.file(envKeystore).exists() -> rootProject.file(envKeystore)
                file("release.keystore").exists() -> file("release.keystore")
                rootProject.file("release.keystore").exists() -> rootProject.file("release.keystore")
                else -> null
            }
            if (keystoreFile != null) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("KEY_ALIAS") ?: "costly"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
