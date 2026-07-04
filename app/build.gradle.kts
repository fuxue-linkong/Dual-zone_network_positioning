import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.radioarealocator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.radioarealocator"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "signing/release.keystore"
            val keystoreFile = file(keystorePath)
            val envStorePassword = System.getenv("KEYSTORE_PASSWORD")
            val envKeyAlias = System.getenv("KEY_ALIAS")
            val envKeyPassword = System.getenv("KEY_PASSWORD")

            if (keystoreFile.exists() &&
                envStorePassword != null &&
                envKeyAlias != null &&
                envKeyPassword != null
            ) {
                // CI release：使用真正的 release keystore（由 release.yml 注入 secrets）
                storeFile = keystoreFile
                storePassword = envStorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            } else {
                // 本地打包回退到 debug keystore，保证签名稳定且可重复
                // ~/.android/debug.keystore 由 Android SDK 维护，签名指纹在不同构建间保持一致
                storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")

    // Satellite prediction
    implementation("com.github.davidmoten:predict4java:1.3.1")
    // predict4java 运行时依赖 Apache Commons Logging
    implementation("commons-logging:commons-logging:1.2")

    // HTTP client for TLE data
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
