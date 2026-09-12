plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget()

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
            // public surface (SettingsRepository, TodayViewModel, androidApp's Glance widgets),
            // and `export` above requires an `api` dependency.
            api(project(":widgetcore"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            // repeatOnLifecycle + LocalLifecycleOwner for common code: gates TodayViewModel's
            // tick loop on Lifecycle.State.STARTED so it stops the moment the screen is stopped
            // (backgrounded, screen off) instead of running for as long as the process lives.
            implementation(libs.jetbrains.lifecycle.runtime.compose)
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
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
        val androidUnitTest by getting {
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

compose.resources {
    publicResClass = true
    packageOfResClass = "world.taqwa.app.resources"
    generateResClass = always
}

android {
    namespace = "world.taqwa.app.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()


    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}
