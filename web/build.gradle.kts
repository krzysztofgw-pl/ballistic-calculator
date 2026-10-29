plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// The web app (PWA): the same solver compiled to JavaScript, with a plain DOM UI.
// `./gradlew :web:jsBrowserDistribution` writes the site to web/build/dist/js/productionExecutable.
kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "app.js" }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":core"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }
    }
}
