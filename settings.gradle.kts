pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "fabric-amper"

// Producer release metadata. Keep aligned with build.gradle.kts and the Git tag.
extra["fabricAmperReleaseVersion"] = "0.1.2"
