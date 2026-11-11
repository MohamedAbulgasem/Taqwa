// The Taqwa watch face (spec 3): a Watch Face Format face, which is an APK of resources and no code.
// The system's face runtime reads res/raw/watchface.xml and draws it; the face computes nothing and
// reads Taqwa's own complication data sources (:wearApp) through fixed slots.
//
// Watch Face Push (spec 3.1) wants the face's package to be `<the app's package>.watchfacepush.<name>`,
// the face to be resource-only with a minSdk of 33, and the APK to be signed with a key other than
// the watch app's. The release key is the owner's, made and kept with his release keystore, so this
// file signs nothing in a release build. A debug build is signed with the throwaway key that
// scripts/face-debug-key.sh makes in ~/.android: for the emulator and for sideloading onto the
// owner's own watch.
plugins {
    alias(libs.plugins.androidApplication)
}

// Where the throwaway key lives: outside the repository, never in it. -Ptaqwa.faceDebugKeystore=<path>
// names another (plan 5 Task 4), and :wearApp's FaceBundleTask reads the same property: a path with
// no key there, as on CI, builds the watch app with no face in it.
val faceDebugKeystore: File = providers.gradleProperty("taqwa.faceDebugKeystore").orNull?.let { File(it) }
    ?: File(System.getProperty("user.home"), ".android/taqwa-face-debug.keystore")

android {
    namespace = "world.taqwa.app.watchface"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // A resource-only APK: no Kotlin, so no stdlib, so no classes.dex for the validator to find.
    enableKotlin = false

    defaultConfig {
        applicationId = "world.taqwa.app.watchfacepush.taqwa"
        // Watch Face Format 2 runs on Wear OS 4 (API 33) and up; the face itself is for Wear OS 6.
        minSdk = 33
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 45
        versionName = "1.6.0"
    }

    // The four findings lint makes of a face are all about its being resources and no code, which
    // is the point: everything in res/ is named by res/raw/watchface.xml, which lint does not read
    // (so every picture, string and font looks unused); the resource shrinker is off on purpose
    // (below); a Watch Face Format picture belongs in drawable/ with no densities; and the face
    // is chosen in the face picker and never launched, so it has no launcher icon.
    lint {
        disable += setOf("UnusedResources", "NotShrinkingResources", "IconLocation", "MissingApplicationIcon")
    }

    signingConfigs {
        create("faceDebug") {
            storeFile = faceDebugKeystore
            storePassword = "android"
            keyAlias = "taqwaface"
            keyPassword = "android"
        }
    }

    // Even with no Kotlin and no sources, AGP puts the R classes and a set of API stubs into a
    // classes.dex, and a Watch Face Format face has no code to ship. R8 removes all of it, which is
    // why both builds minify, and why neither shrinks resources: the runtime finds res/raw/watchface.xml
    // by name and no code mentions it, so the resource shrinker would delete the face itself.
    buildTypes {
        debug {
            // Nothing in a face runs, so there is nothing to debug; and AGP warns of a debuggable
            // build that minifies.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("faceDebug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            // Left unsigned on purpose: the release key is the owner's, made and kept with his
            // release keystore, and is never handled here.
        }
    }
}

// A debug build without the key would fail somewhere inside the signing step with a message about a
// keystore; say what to do instead, before anything else runs.
val checkFaceDebugKey = tasks.register("checkFaceDebugKey") {
    val keystore = faceDebugKeystore
    doLast {
        if (!keystore.exists()) {
            throw GradleException(
                "The Taqwa face's debug key is missing (${keystore.path}): run scripts/face-debug-key.sh",
            )
        }
    }
}
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(checkFaceDebugKey) }
