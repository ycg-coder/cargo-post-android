pluginManagement {
    repositories {
        mavenLocal()
        google()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "CargoPostAndroid"
include(":app")
