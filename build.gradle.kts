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
    kotlin("jvm") version "2.2.0" apply false
    kotlin("android") version "2.2.0" apply false
    kotlin("plugin.compose") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
}
