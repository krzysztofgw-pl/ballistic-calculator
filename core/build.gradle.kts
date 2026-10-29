plugins {
    kotlin("multiplatform")
}

// The solver is shared: the JVM target serves the Android app, the JS target the web app (see web/).
kotlin {
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    // Tests run on Node.js; the web app bundles the solver for the browser itself.
    js {
        nodejs()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
