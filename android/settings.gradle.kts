pluginManagement {
    resolutionStrategy {
        eachPlugin {
            // Some mirrors do not carry Gradle's plugin-marker artifact even
            // when the actual KSP Gradle plugin is available. Resolve the
            // published implementation directly, keeping its declared version.
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        maven("https://maven.myket.ir")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.myket.ir")
        google()
        mavenCentral()
    }
}
rootProject.name = "Reedance"
include(":app")
