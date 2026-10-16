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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    // PREFER_PROJECT rather than FAIL_ON_PROJECT_REPOS: the Kotlin wasm/JS toolchain adds a
    // Node.js distribution repository of its own (org.nodejs:node lives at nodejs.org, not in
    // any Maven repo), and the stricter modes reject or ignore it.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PersonalMoney"
include(":composeApp")
