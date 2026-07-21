// Baseline Profile 生成器模块。
//
// 用途：本地连真机/模拟器跑 `:baselineprofile:generateBaselineProfile`，
// 模拟应用冷启动 + 主页交互，捕获运行时被 JIT 编译的关键代码路径，
// 生成 baseline-prof.txt 写入 app/src/release/generated/baselineProfiles/。
//
// 后续所有 release APK 构建会自动嵌入该 profile，ART 在安装时 AOT 预编译
// 关键类和方法，避免冷启动期间 JIT 编译阻塞主线程（FloatingBottomBar、
// BasicComponent 等 Compose 组件首帧分配 6-7MB 内存的问题由此缓解）。
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.radioarealocator.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR-BUILD,LOW-BATTERY,UNLOCKED,ACTIVITY-MISSING"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // create() 用于创建 non-default build type，基准 profile 生成器跑在这个 build type
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
}
