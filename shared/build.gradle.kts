plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "world.taqwa.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // res/raw holds the notification sounds, which the app plays by android.resource:// URI.
        androidResources {
            enable = true
        }
        // The JVM tests in src/androidHostTest: the bundled database and city files, and the
        // Android-only mappings.
        withHostTest {}
    }

    // `SurahDownloader` is an `expect class` (spec 3a §7): the two platforms' downloaders are
    // whole objects with state, not a function each, and the scheduler behind them differs
    // completely. Expect/actual classes are still flagged Beta by the compiler; the flag says
    // the shape is deliberate rather than leaving a warning in every build.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Compose Multiplatform 1.12.0 no longer publishes iosX64 (Intel simulator) artifacts.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            // The app's Swift names these types through `import shared`; exporting keeps that
            // working now that they live in `:widgetcore`. The widget extension links
            // `widgetcore.framework` directly instead — see tools/add-widget-target.rb.
            export(project(":widgetcore"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: the widget model's types are part of `shared`'s own
            // public surface (SettingsRepository, PrayerViewModel, androidApp's Glance widgets),
            // and `export` above requires an `api` dependency.
            api(project(":widgetcore"))
            implementation(project(":watchcore"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            // repeatOnLifecycle + LocalLifecycleOwner for common code: gates PrayerViewModel's
            // tick loop on Lifecycle.State.STARTED so it stops the moment the screen is stopped
            // (backgrounded, screen off) instead of running for as long as the process lives.
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            // @Preview in common code: the Prayer screen's states render in the IDE.
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.adhan2)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.datastore.preferences)
            implementation(libs.sqldelight.coroutines)
            // Slice 3a. The recitation manifest and the `.taqa` container index are JSON, and
            // the container is read by byte range off disk on both platforms.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // The `.taqa` and library tests build containers in memory rather than on the
            // machine running the tests; FakeFileSystem is okio's own in-memory FileSystem.
            implementation(libs.okio.fakefilesystem)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
            implementation(libs.sqldelight.android)
            // Slice 3a. A surah download has to survive the app being backgrounded and the
            // process being killed, which on Android is WorkManager and nothing else.
            api(libs.androidx.work.runtime)
            // Slice 3a task 3: recitation playback. The ExoPlayer lives in a MediaSessionService
            // so the notification and the lock screen come from the platform rather than from
            // anything this app draws.
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
            // Plan 2 Task 5: the watch feed goes to the user's own watch over the Wear Data Layer's
            // MessageClient, to nodes directly connected at the send (never DataClient). Mohamed's
            // condition for this one Google library: no tracker and no new permission, checked on
            // the merged manifest and the APK (docs/superpowers/reports/2026-10-23-wearable-library-check.md).
            implementation(libs.play.services.wearable)
            // Plan 5 Task 6: Settings › Watch opens Taqwa's Play page, or the Taqwa face's screen, on
            // the user's own watch (RemoteActivityHelper). Checked like the one above: no tracker and no
            // new permission (docs/superpowers/reports/2026-11-06-phone-watch-rows.md).
            implementation(libs.wear.remote.interactions)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
        iosTest.dependencies {
            // Compose UI tests — semantics assertions on the shared controls — run on the iOS
            // simulator target, the one target where a Compose scene composes under kotlin.test
            // without Robolectric. scripts/test.sh reaches them through :shared:allTests.
            implementation(libs.compose.ui.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.sqldelight.sqlite)
            }
        }
    }
}

sqldelight {
    databases {
        create("QuranDatabase") {
            packageName.set("world.taqwa.app.quran.db")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.2.1")
            // The database ships prebuilt; the schema below exists for code generation only.
            deriveSchemaFromMigrations.set(false)
        }
    }
}

// The tasbeeh rules live in :watchcore, which the Compose compiler does not see: this file declares
// them stable again, as they inferred while they lived here (Ruling 45).
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

compose.resources {
    publicResClass = true
    packageOfResClass = "world.taqwa.app.resources"
    generateResClass = always
}

kotlin {
    jvmToolchain(21)
}

// The androidHostTest classes that check the bundled quran.db and city files read them straight
// from src/commonMain/composeResources/files, which Gradle does not otherwise count as a test
// input: a regenerated database on its own left those tests UP-TO-DATE, green from the file before.
// WatchWordsResourcesTest reads the values*/strings.xml files the same way. Today a value edit also
// changes the generated accessors' content hash, but that is Compose's hot-reload aid, off under
// org.jetbrains.compose.resources.content.hash.generation.disable, and the test was then left
// UP-TO-DATE, green over a changed prayer_jumuah.
tasks.withType<Test>().configureEach {
    inputs.dir("src/commonMain/composeResources/files")
        .withPropertyName("bundledFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree("src/commonMain/composeResources") { include("values*/strings.xml") })
        .withPropertyName("stringResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // WatchFeedGoldenTest holds the golden feeds in watchcore/vectors/phone/ to what the builder
    // writes from the frozen times in its times/ folder, and rewrites the feeds (never the times)
    // under -Ptaqwa.updateVectors; -Ptaqwa.recordTimes records the times from the engine, once.
    // Each flag reaches the test as the system property of its name, and a run with either has a
    // side effect, so it is never skipped as up to date or taken from the build cache.
    // WatchFeedSampleTest reads :watchcore's sample feeds beside them, so those are inputs too.
    inputs.files(rootProject.fileTree("watchcore/vectors/phone"))
        .withPropertyName("phoneVectors")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.fileTree("watchcore/vectors") { include("sample-feed-capetown*.json") })
        .withPropertyName("sampleFeeds")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // FaceFormsTest holds tools/watchface/phone-forms.json, which the face's fit tools read, to the
    // forms the phone writes (PhoneForms), and rewrites it under -Ptaqwa.updateVectors.
    inputs.files(rootProject.fileTree("tools/watchface") { include("phone-forms.json") })
        .withPropertyName("phoneForms")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // WatchScreenWordsReaderTest holds the two notices the phone sends the watch to the Wear app's
    // own lines (open_taqwa_on_phone, update_taqwa_on_watch), so an edit to its strings re-runs it.
    inputs.files(rootProject.fileTree("wearApp/src/main/res") { include("values*/strings.xml") })
        .withPropertyName("wearStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // PhoneWatchListenerServiceTest holds :androidApp's listener filters and its Wear capability to
    // WatchLinkContract's constants (Ruling 54 item 2), so an edit to either file re-runs it.
    inputs.files(rootProject.files("androidApp/src/main/AndroidManifest.xml", "androidApp/src/main/res/values/wear.xml"))
        .withPropertyName("phoneWearDeclarations")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    for (flag in listOf("taqwa.updateVectors", "taqwa.recordTimes")) {
        if (providers.gradleProperty(flag).map { it != "false" }.getOrElse(false)) {
            systemProperty(flag, "true")
            outputs.upToDateWhen { false }
            outputs.doNotCacheIf("it rewrites the vectors") { true }
        }
    }
}
