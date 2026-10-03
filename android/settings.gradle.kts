// In the name of God, the Most Gracious, the Most Merciful
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
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
    }
}

rootProject.name = "Herz"
include(":app")

// Pure Kotlin
include(":core:model")
include(":core:common")
include(":core:domain")

// Android core
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:notifications")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

// Video: API + swappable players
include(":core:video")
include(":core:video-rtsp")
include(":core:video-webrtc")

// Direct LAN cameras (dev only, behind CameraSource)
include(":camera:discovery")
include(":camera:onvif")

// Features (never depend on each other)
include(":feature:auth")
include(":feature:dashboard")
include(":feature:cameras")
include(":feature:liveview")
include(":feature:alarms")
include(":feature:playback")
include(":feature:recordings")
include(":feature:doors")
include(":feature:devices")
include(":feature:rules")
include(":feature:users")
include(":feature:settings")
include(":feature:discovery")
