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
    }
}

rootProject.name = "colony"
include(":sim")
// The Android app needs the Android SDK. Set -PwithApp (or have ANDROID_HOME set) to include it.
if (providers.gradleProperty("withApp").isPresent || providers.environmentVariable("ANDROID_HOME").isPresent) {
    include(":app")
}
