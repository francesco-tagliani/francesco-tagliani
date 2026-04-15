pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://maven.aliyun.com/repository/public")
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/public")
        mavenCentral()
        google()
    }
}

rootProject.name = "WhatsAppStickerUploader"
include(":app")
