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
}

/**
 * Writes the timetable document the site build renders.
 *
 *     ./gradlew -p tools/timetables generate -Pout=_data/timetables.json [-Ptoday=2026-09-25]
 *
 * Paths are relative to the repository root. `today` defaults to the current UTC date; the
 * document covers the whole of that month and the next.
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
    val today = providers.gradleProperty("today").orElse("")
    val cities = providers.gradleProperty("cities").orElse("site/cities.tsv")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--cities", cities.get(), "--app", "shared/src/commonMain/composeResources", "--out", out.get()) +
            (if (today.get().isNotBlank()) listOf("--today", today.get()) else emptyList())
    })
}
