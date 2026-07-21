pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 高德地图 SDK Maven 仓库（国内镜像，稳定可靠）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // JitPack（MPAndroidChart、predict4java 等 GitHub 开源库）
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RadioAreaLocator"
include(":app")
// Baseline Profile 生成器模块：本地跑 :baselineprofile:generateBaselineProfile
// 会在 app/src/release/generated/baselineProfiles/ 下生成 baseline-prof.txt，
// AGP 自动嵌入 APK，安装时 ART 会 AOT 预编译关键路径，消除冷启动期间 JIT 阻塞主线程。
include(":baselineprofile")
