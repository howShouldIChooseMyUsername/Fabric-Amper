pluginManagement {
    repositories {
        maven { url = uri("file:///C:/Users/user/Documents/vbc/Fabric%20Amper/analysis/isolated-acceptance-2026-11-13/build/g29-probe/maven-repo/") }
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins { id("dev.fabric-amper.settings") version "0.1.0" }
