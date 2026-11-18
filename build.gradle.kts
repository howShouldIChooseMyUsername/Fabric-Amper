plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "2.4.21"
}

group = "dev.fabricamper"
version = "0.3.1"
description = "Fabric Amper: YAML-driven Fabric Gradle Settings and Project plugins"

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

// Plugin-build-only G13/G25 matrix switch; consumers cannot select Loom via mod.yaml.
// The released/default plugin continues to bundle 1.17.21 unless explicitly overridden.
val bundledLoomVersion = providers.gradleProperty("fabricAmperBundledLoomVersion").getOrElse("1.17.21")
require(bundledLoomVersion in setOf("1.17.21", "1.18.4", "1.19.4")) {
    "Unsupported fabricAmperBundledLoomVersion '$bundledLoomVersion'; " +
        "supported plugin-build matrix values: 1.17.21 (default), 1.18.4, 1.19.4"
}

dependencies {
    // The Settings plugin must transitively supply these plugin implementations:
    // a consumer's settings.gradle.kts must not need a second plugin declaration.
    implementation("net.fabricmc:fabric-loom:$bundledLoomVersion")
    // Single Settings plugin owns both loader backends. Never apply MDG to a
    // Fabric project: only its implementation is present on the plugin classpath.
    implementation("net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.148")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    implementation("org.snakeyaml:snakeyaml-engine:2.10")
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    // Compare version predicates against the official Fabric Loader implementation in tests.
    testImplementation("net.fabricmc:fabric-loader:0.20.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.0")
}

gradlePlugin {
    plugins {
        create("fabricAmperSettings") {
            id = "dev.fabric-amper.settings"
            implementationClass = "dev.fabricamper.plugin.FabricAmperSettingsPlugin"
            displayName = "Fabric Amper Settings"
            description = "Bootstraps a Fabric mod build from mod.yaml without build.gradle"
        }
        create("fabricAmperProject") {
            id = "dev.fabric-amper.project"
            implementationClass = "dev.fabricamper.plugin.FabricAmperProjectPlugin"
            displayName = "Fabric Amper Project"
            description = "Configures Fabric Loom and metadata for mod.yaml consumers"
        }
    }
}

// G29 only: publish plugin JAR/POM and generated plugin markers to an isolated
// local repository. No publishing repository is registered in normal builds.
if (providers.gradleProperty("fabricAmperProbeMavenRepo").getOrElse("false").toBoolean()) {
    publishing {
        repositories {
            maven {
                name = "G29Probe"
                url = layout.buildDirectory.dir("g29-probe/maven-repo").get().asFile.toURI()
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Heavy consumer fixtures remain opt-in for ordinary builds.
    if (providers.gradleProperty("fabricAmperTestkitFull").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_FULL", "true")
    }
    // Legacy JDK 21 consumer fixtures require a separate explicit opt-in.
    if (providers.gradleProperty("fabricAmperTestkitLegacy").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_LEGACY", "true")
    }
    // G13/G25 matrix consumer tests are independent opt-ins for each Loom flavor.
    if (providers.gradleProperty("fabricAmperTestkitMatrix").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_MATRIX", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitMatrixLoom1184").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1184", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitMatrixLoom1194").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_MATRIX_LOOM_1194", "true")
    }
    // Opt-in runtime consumer fixture; forward the native Gradle executable verbatim.
    if (providers.gradleProperty("fabricAmperTestkitRuntime").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_RUNTIME", "true")
        providers.gradleProperty("fabricAmperRuntimeGradleBin").orNull?.let { gradleBin ->
            environment("FABRIC_AMPER_RUNTIME_GRADLE_BIN", gradleBin)
        }
    }
    // G10 runtime JIJ verification is separately opt-in from client smoke tests.
    if (providers.gradleProperty("fabricAmperTestkitRuntimeJij").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_RUNTIME_JIJ", "true")
    }
    // G17 compile and real Loader runtime fixtures have independent opt-ins.
    if (providers.gradleProperty("fabricAmperTestkitG17").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G17", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitRuntimeG17").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_RUNTIME_G17", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitG27").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G27", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitG19Real").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G19_REAL", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitApiPublishing").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_API_PUBLISHING", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitPublishPlan").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_PUBLISH_PLAN", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitG9ApiPublish").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G9_API_PUBLISH", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitG24Repackage").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G24_REPACKAGE", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitRemoteMavenCc").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_REMOTE_MAVEN_CC", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitG19RealKgp").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G19_REAL_KGP", "true")
    }
    if (providers.gradleProperty("fabricAmperTestkitG20Composite").getOrElse("false").toBoolean()) {
        environment("FABRIC_AMPER_TESTKIT_G20_COMPOSITE", "true")
    }
}
