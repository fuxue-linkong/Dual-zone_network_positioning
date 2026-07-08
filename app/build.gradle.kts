import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import java.io.File
import java.util.Base64
import java.util.Properties
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM 加密 API Key。
 * 明文 key 仅存在于 local.properties（不进 git），密文注入 BuildConfig。
 * 运行时通过 ApiKeyCrypto.decrypt() 解密。
 */
fun encryptApiKey(plain: String): String {
    if (plain.isEmpty()) return ""
    val masterKey = byteArrayOf(
        0x97.toByte(), 0x5F.toByte(), 0xAE.toByte(), 0x7F.toByte(),
        0xBB.toByte(), 0x1A.toByte(), 0xE7.toByte(), 0xC4.toByte(),
        0xB2.toByte(), 0x72.toByte(), 0x6D.toByte(), 0xFC.toByte(),
        0xB3.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0xE2.toByte(),
        0xB3.toByte(), 0x9C.toByte(), 0x8C.toByte(), 0xCE.toByte(),
        0xDD.toByte(), 0x1C.toByte(), 0xF8.toByte(), 0x1F.toByte(),
        0xF9.toByte(), 0x12.toByte(), 0x14.toByte(), 0xE6.toByte(),
        0x8D.toByte(), 0xD5.toByte(), 0x72.toByte(), 0x60.toByte()
    )
    val iv = ByteArray(12)
    SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(masterKey, "AES"),
        GCMParameterSpec(128, iv)
    )
    val cipherText = cipher.doFinal(plain.toByteArray())
    val combined = ByteArray(12 + cipherText.size)
    System.arraycopy(iv, 0, combined, 0, 12)
    System.arraycopy(cipherText, 0, combined, 12, cipherText.size)
    return Base64.getEncoder().encodeToString(combined)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    jacoco
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

        // 从 local.properties 读取 API Key（不进 git）
        val localProps = Properties().apply {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { load(it) }
            }
        }
        // 高德 Web 服务 Key（天气API）：AES-GCM 加密后注入 BuildConfig
        buildConfigField(
            "String",
            "AMAP_API_KEY_ENCRYPTED",
            "\"${encryptApiKey(localProps.getProperty("amap.api.key", ""))}\""
        )
        // 高德 Android SDK Key（地图SDK）：AES-GCM 加密后注入 BuildConfig（运行时解密）
        buildConfigField(
            "String",
            "AMAP_SDK_KEY_ENCRYPTED",
            "\"${encryptApiKey(localProps.getProperty("amap.sdk.key", ""))}\""
        )

        // SDK Key 通过 manifestPlaceholders 注入 AndroidManifest meta-data
        // 高德地图 SDK V11.x 在 MapView 创建时读取 meta-data，必须在 manifest 中配置
        // Key 安全性：1) 绑定 SHA1+包名 2) Release 启用 R8 混淆 3) local.properties 不进 git
        manifestPlaceholders["AMAP_SDK_KEY"] = localProps.getProperty("amap.sdk.key", "")
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
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // JaCoCo 覆盖率报告配置：排除自动生成与框架代码，聚焦业务逻辑
    testOptions {
        unitTests {
            all {
                it.extensions.configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf(
                        "jdk.internal.*",
                        "android.*",
                        "androidx.*",
                        "com.android.*",
                        "*_Factory",
                        "*_MembersInjector",
                        "*Hilt*",
                        "Dagger*",
                        "*Binding*"
                    )
                }
            }
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

// JaCoCo 工具版本配置（顶层 Gradle Jacoco 插件扩展）
jacoco {
    toolVersion = "0.8.12"
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
    implementation("androidx.compose.material:material-icons-extended")
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

    // Image loading (GitHub avatars in About)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 高德地图 SDK：3D地图（含缩放手势、Marker、控件）
    // 文档：https://lbs.amap.com/api/android-sdk/summary
    implementation("com.amap.api:3dmap:latest.integration")

    // WorkManager: 用于日程提醒的每日刷新周期任务
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
