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
        // cgutman AndroidADB: als lokales Modul/JitPack einbinden
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ADB Remote (reconstructed)"
include(":app")
include(":server")
include(":navhelper")
