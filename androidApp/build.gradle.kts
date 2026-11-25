import java.util.Properties

// A plain Android application compiled by AGP's built-in Kotlin: since AGP 9 an application
// module cannot also apply the Kotlin Multiplatform plugin. Everything shared lives in `shared`.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.glance.appwidget)
    // The debug build's watch card gallery (src/debug) gives its Prayer view model a settings store
    // of its own. DataStore already ships inside :shared at this version; :shared takes it as
    // `implementation`, so only the debug source set's compiler needs to be shown it.
    debugImplementation(libs.datastore.preferences)
}

// ── Store signing ─────────────────────────────────────────────────────────────────
// The upload key lives outside the repository. `keystore.properties` at the repository root
// (git-ignored; keystore.properties.example shows the shape) names it. When the file is absent
// the release build is left unsigned, as before, so any machine can still build and a debug key
// can sign a device copy by hand. scripts/release.sh builds the store artefacts.
// -Ptaqwa.unsignedRelease=true leaves the release unsigned whatever the file says, without reading it:
// every release-type build but scripts/release.sh's passes it, and :wearApp reads it the same way
// (plan 5 Task 3, scripts/test-unsigned-release.sh). Any value but true or false stops the build, so
// a misspelt one never signs.
val unsignedRelease: Boolean = when (val value = providers.gradleProperty("taqwa.unsignedRelease").orNull) {
    null, "false" -> false
    "true" -> true
    else -> error("taqwa.unsignedRelease is true or false, not '$value'")
}
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { !unsignedRelease && it.isFile }
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
        versionCode = 46
        versionName = "1.7.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

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
