// AGP is only put on the classpath when the app module is included (see
// settings.gradle.kts), so the solver builds on machines without Google Maven.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        if (findProject(":app") != null) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("com.google.gms:google-services:4.4.2")
        }
    }
}

plugins {
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("android") version "2.2.0" apply false
    kotlin("plugin.compose") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
}

// Kotlin/JS would add the Node.js download site as a project repository, which
// settings.gradle.kts forbids; the repository is declared there instead.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
