pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // mavenLocal() // uncomment when consuming the SDK via `./gradlew :bambusercalls-shopper:publishToMavenLocal`
    }
}

rootProject.name = "BambuserCallsShopperSDK-Android"

include(":bambusercalls-shopper")
include(":app")
