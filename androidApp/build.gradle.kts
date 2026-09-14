import java.util.Properties

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

// ── Store signing ─────────────────────────────────────────────────────────────────
// The upload key lives outside the repository. `keystore.properties` at the repository root
// (git-ignored; keystore.properties.example shows the shape) names it. When the file is absent
// the release build is left unsigned, as before, so any machine can still build and a debug key
// can sign a device copy by hand. scripts/release.sh builds the store artefacts.
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().also { props -> file.inputStream().use { props.load(it) } } }

android {
    namespace = "world.taqwa.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    // The app is written in seven languages; the other seventy-odd locales in the APK were
    // AndroidX's own strings. Android's resource system still spells Indonesian "in". The bundle
    // keeps every language on every phone because the app can be switched to any of them on any
    // phone (locales_config.xml) and the widget labels must follow.
    androidResources {
        localeFilters += listOf("en", "ar", "fr", "tr", "in", "ur", "bn")
    }
    bundle {
        language {
            enableSplit = false
        }
    }


    defaultConfig {
        applicationId = "world.taqwa.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 26
        versionName = "0.20.1"
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
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
