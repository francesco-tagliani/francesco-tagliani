pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://repo.maven.apache.org/maven2")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven(url = "https://repo.maven.apache.org/maven2")
    }
}

rootProject.name = "WhatsAppStickerUploader"
include(":app")
