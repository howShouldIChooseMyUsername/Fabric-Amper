pluginManagement {
    repositories {
        maven { url = uri("../../build/g29-probe/maven-repo") }
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("dev.fabric-amper.settings") version "0.1.0"
}
