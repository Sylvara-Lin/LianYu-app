pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 厂商 Push SDK 仓库：华为 HMS
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

rootProject.name = "恋语"
include(":app")
include(":core:domain")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:security")
include(":core:ui-common")
include(":feature:companion")
include(":feature:chat")
include(":feature:groupchat")
include(":feature:memory")
include(":feature:notification")
include(":feature:profile")
include(":feature:settings")
include(":feature:localmodel")
include(":feature:wechat")
include(":feature:qqbot")
include(":feature:backup")
include(":feature:coffee")
