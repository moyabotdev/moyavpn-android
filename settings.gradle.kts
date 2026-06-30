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
        // JitPack — fuer die spaetere AmneziaWG-Tunnel-Library (org.amnezia.awg)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MoyaVPN"
include(":app")
