plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.glance.appwidget)
        }
    }
}

android {
    namespace = "world.taqwa.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()


    defaultConfig {
        applicationId = "world.taqwa.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 18
        versionName = "0.15.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // ── Recitation downloads, debug harness (slice 3a task 2) ─────────────────────────
    // The debug source set is `src/androidDebug` here, not `src/debug`: the KMP android
    // layout v2 puts every android source set under its Kotlin name, which is where the
    // debug Kotlin and the debug resources are already read from. The build-type manifest
    // overlay is the one thing AGP still looks for at its own default path, so it is
    // pointed at the same directory as everything else rather than leaving one stray
    // `src/debug` folder behind. Without this line the debug manifest is silently ignored.
    sourceSets {
        getByName("debug") {
            manifest.srcFile("src/androidDebug/AndroidManifest.xml")
        }
    }
    // ── end recitation downloads ──────────────────────────────────────────────────────
    buildTypes {
        release {
            // Code shrinking only. The first attempt also shrank resources and deleted res/raw
            // (nothing references the sounds by R id, only by android.resource:// URI), which
            // crashed the sound sheet and silenced the channels; the Glance widgets went with it.
            // Resources are kept whole until each is either referenced by id or listed in
            // res/raw/keep.xml, and every sound, both widgets and a real alarm are checked on a
            // device before shrinking is trusted again.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
