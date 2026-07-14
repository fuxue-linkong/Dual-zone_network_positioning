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
        // JitPack（MPAndroidChart 等 GitHub 开源库）
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RadioAreaLocator"
include(":app")
