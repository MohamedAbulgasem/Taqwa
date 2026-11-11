plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// The watch feed: the JSON document the phone sends the watch, and the rules every watch surface
// applies to it. Deliberately *not* a Compose module, and deliberately small: the Wear OS app and
// its complication services read the feed through this, and the phone writes it through this.
// Its one dependency beyond the standard library is kotlinx-serialization-json, and every
// dependency added here lands in the watch apps.
//
// It is a module of its own, not a part of `widgetcore`, because `widgetcore` must stay
// dependency-free: the iPhone widget extension links that framework and nothing else, under a
// ~30 MB memory ceiling. The extension never links this one.
kotlin {
    android {
        namespace = "world.taqwa.app.watchcore"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // The common tests run on the JVM through this, as `widgetcore`'s do.
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "watchcore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// VectorsTest (src/androidHostTest) holds the files in vectors/ to what the rules answer today, and
// rewrites them when Gradle is given -Ptaqwa.updateVectors:
//
//   ./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors
//
// The files are the test input the Swift port of the rules runs as well, and the sample feed the
// Wear app's debug build starts with. Gradle does not count them as a test input on its own: a
// hand-edited vector would otherwise leave the test UP-TO-DATE, green from the file before. And
// a run that rewrites them has a side effect, so it is never skipped as up to date or taken from
// the build cache.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree("vectors"))
        .withPropertyName("vectors")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    if (providers.gradleProperty("taqwa.updateVectors").map { it != "false" }.getOrElse(false)) {
        systemProperty("taqwa.updateVectors", "true")
        outputs.upToDateWhen { false }
        outputs.doNotCacheIf("it rewrites the vectors") { true }
    }
}
