import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

/**
 * The app's own files this generator compiles, by path, unchanged. They are the prayer-time
 * engine and everything it reads, the Qibla maths, the tabular Hijri calendar and the Hijri month
 * names: every number and every month name on a city page comes out of these files, exactly as
 * the app computes them. They must stay free of Compose and platform code; a change that breaks
 * that fails this build, and with it the website's nightly run, which is the point.
 */
val appSources = listOf(
    "world/taqwa/app/prayer/PrayerTimesEngine.kt",
    "world/taqwa/app/prayer/HighLatitudeSelector.kt",
    "world/taqwa/app/prayer/CalculationMethodDefaults.kt",
    "world/taqwa/app/domain/Prayer.kt",
    "world/taqwa/app/domain/PrayerSettings.kt",
    "world/taqwa/app/domain/GeoLocation.kt",
    "world/taqwa/app/qibla/QiblaMath.kt",
    "world/taqwa/app/hijri/TabularHijriCalendar.kt",
    "world/taqwa/app/hijri/HijriMonthNames.kt",
    "world/taqwa/app/i18n/UiLanguage.kt",
    "world/taqwa/app/i18n/CountdownDigits.kt",
)

/** The app's own tests for those files, run here on the JVM to prove this build computes what
 * the app computes. */
val appTests = listOf(
    "world/taqwa/app/prayer/PrayerTimesEngineTest.kt",
    "world/taqwa/app/prayer/HighLatitudeSelectorTest.kt",
    "world/taqwa/app/prayer/CalculationMethodDefaultsTest.kt",
    "world/taqwa/app/qibla/QiblaMathTest.kt",
    "world/taqwa/app/hijri/TabularHijriCalendarTest.kt",
)

val repoRoot: File = rootDir.resolve("../..").canonicalFile

kotlin {
    // adhan2's JVM artifact is compiled to Java 21 class files.
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(repoRoot.resolve("shared/src/commonMain/kotlin"))
            kotlin.srcDir(repoRoot.resolve("widgetcore/src/commonMain/kotlin"))
            kotlin.include(appSources)
            dependencies {
                implementation(libs.adhan2)
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest {
            kotlin.srcDir(repoRoot.resolve("shared/src/commonTest/kotlin"))
            kotlin.include(appTests)
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("taqwa.repoRoot", repoRoot.path)
    // The tests read the app's strings and city files through that path, so they are inputs:
    // without this a changed strings.xml would let a cached test result stand.
    inputs.files(
        fileTree(repoRoot.resolve("shared/src/commonMain/composeResources")) {
            include("values*/strings.xml", "files/cities.csv", "files/city-names-*.csv")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("appResources")
}

/**
 * Writes the timetable document the site build renders.
 *
 *     ./gradlew -p tools/timetables generate -Pout=_data/timetables.json [-Pnow=2026-09-25T00:07:00Z]
 *
 * Paths are relative to the repository root. `now` defaults to the moment the task runs; each
 * city gets the whole of its own current month and the next, by its own calendar at that moment.
 */
val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
tasks.register<JavaExec>("generate") {
    group = "application"
    description = "Writes the city prayer timetables for the website."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.MainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    val out = providers.gradleProperty("out").orElse("_data/timetables.json")
    val now = providers.gradleProperty("now").orElse("")
    val cities = providers.gradleProperty("cities").orElse("site/cities.tsv")
    val app = providers.gradleProperty("app").orElse("shared/src/commonMain/composeResources")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--cities", cities.get(), "--app", app.get(), "--out", out.get()) +
            (if (now.get().isNotBlank()) listOf("--now", now.get()) else emptyList())
    })
}
