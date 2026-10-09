import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

/**
 * The app's own files this generator compiles, by path, unchanged. They are the prayer-time
 * engine (the whole `prayer` package, the new engine and its registry included) and everything it
 * reads, the Qibla maths, the tabular Hijri calendar and the Hijri month names: every number and
 * every month name on a city page, and every day the gate checks, comes out of these files,
 * exactly as the app computes them. They must stay free of Compose and platform code; a change
 * that breaks that fails this build, and with it the website's nightly run, which is the point.
 */
val appSources = listOf(
    "world/taqwa/app/prayer/**",
    "world/taqwa/app/domain/TimelineState.kt",
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

/**
 * The archive root the gate reads official tables from: `-Pofficial=<dir>`, else the environment's
 * `TAQWA_OFFICIAL`, else this checkout's own `tools/timetables/official`, whose `archive/` is
 * git-ignored (restored from the local-only branch, see that folder's README). Gate rows name files
 * relative to it, except `open/…` tables, which are read from this checkout's own
 * `tools/timetables/official/open`.
 *
 * Where no root is given and this checkout holds no `archive/`: on CI (`CI` set) the root is that
 * missing folder, so every row is skipped and the gate passes with "0 rows checked"; anywhere else
 * the gate and the tests stop at once and say how to restore it ([requireArchive]). Where a root
 * holds its archive, a file it does not hold fails.
 */
val checkoutOfficial: File = repoRoot.resolve("tools/timetables/official")
val explicitOfficial: Provider<String> = providers.gradleProperty("official")
    .orElse(providers.environmentVariable("TAQWA_OFFICIAL"))
val onCi: Boolean = providers.environmentVariable("CI").orNull.let { !it.isNullOrBlank() && it != "false" }
val archiveHeld: Boolean = checkoutOfficial.resolve("archive").isDirectory
val officialRoot: Provider<String> = explicitOfficial.orElse(
    if (archiveHeld || !onCi) checkoutOfficial.path else checkoutOfficial.resolve("archive").path,
)

/**
 * Stops the task before it reads anything where the root is this checkout's own and its `archive/`
 * is missing, off CI: a gate that skipped every archived row would prove nothing and still pass.
 */
fun Task.requireArchive() {
    val missing = !explicitOfficial.isPresent && !onCi && !archiveHeld
    val archive = checkoutOfficial.resolve("archive").path
    doFirst {
        if (missing) {
            throw GradleException(
                "No official archive at $archive. The gate reads the official timetables from this " +
                    "checkout's tools/timetables/official/archive (git-ignored; tools/timetables/official/README.md " +
                    "says how to restore it). Restore it there, pass -Pofficial=<dir> (or TAQWA_OFFICIAL) for a root " +
                    "that holds archive/, or set CI=true to skip every archived row.",
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    requireArchive()
    systemProperty("taqwa.repoRoot", repoRoot.path)
    systemProperty("taqwa.official", officialRoot.get())
    // -PgateGroup / -Pentry narrow NeverEarlyGateTest as they narrow the gate task (one group's proof;
    // not -Pgroup, which is Gradle's own project group).
    systemProperty("taqwa.gateGroups", providers.gradleProperty("gateGroup").getOrElse(""))
    systemProperty("taqwa.gateEntries", providers.gradleProperty("entry").getOrElse(""))
    maxHeapSize = "2g"
    // The tests read the app's strings and city files through that path, so they are inputs:
    // without this a changed strings.xml would let a cached test result stand.
    inputs.files(
        fileTree(repoRoot.resolve("shared/src/commonMain/composeResources")) {
            include("values*/strings.xml", "files/cities.csv", "files/city-names-*.csv")
        },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("appResources")
    // NeverEarlyGateTest reads the gate rows and every official table they name.
    inputs.files(fileTree(repoRoot.resolve("tools/timetables/official/gate")) { include("*.tsv") })
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("gateRows")
    // UkMawaqitSurveyTest reads its survey's calendars, faults and outliers (official/survey/).
    inputs.files(fileTree(repoRoot.resolve("tools/timetables/official/survey")) { include("**/*.tsv") })
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("surveys")
    inputs.files(fileTree(officialRoot.get()) { include("open/**", "archive/tables/**", "archive/raw/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("officialTables")
    // open/ tables are read from this checkout, not the archive root: a changed one must rerun the gate.
    inputs.files(fileTree(repoRoot.resolve("tools/timetables/official/open")))
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("openTables")
}

/**
 * Writes the timetable document the site build renders.
 *
 *     ./gradlew -p tools/timetables generate -Pout=_data/timetables.json [-Pnow=2026-09-25T00:07:00Z]
 *
 * Paths are relative to the repository root. `now` defaults to the moment the task runs; each
 * city gets the whole of its own current month and the next, by its own calendar at that moment.
 *
 * The proven rule and the "How Taqwa checks" page read this checkout's `official/stamps`,
 * `official/gate` and `official/survey` (spec §2, §5, §9.4): they are inputs, so a stamp refresh
 * rebuilds the pages. The archive root (`-Pofficial`) is the gate's, not this task's: the stamps
 * are committed here.
 */
val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
tasks.register<JavaExec>("generate") {
    group = "application"
    description = "Writes the city prayer timetables for the website."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.MainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    inputs.files(
        fileTree(checkoutOfficial) { include("stamps/*.json", "gate/*.tsv", "survey/**/*.tsv") },
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("proof")
    val out = providers.gradleProperty("out").orElse("_data/timetables.json")
    val now = providers.gradleProperty("now").orElse("")
    val cities = providers.gradleProperty("cities").orElse("site/cities.tsv")
    val app = providers.gradleProperty("app").orElse("shared/src/commonMain/composeResources")
    val official = checkoutOfficial.path
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--cities", cities.get(), "--app", app.get(), "--out", out.get(), "--official", official) +
            (if (now.get().isNotBlank()) listOf("--now", now.get()) else emptyList())
    })
}

/**
 * The gate (spec §5): every official day held locally, checked against the engine.
 *
 *     ./gradlew -p tools/timetables gate [-Pofficial=<dir>] [-PgateGroup=sg-muis,ru-dumrt] [-Pentry=sg.muis]
 *         [-Pfit=sg.muis] [-Pstamps=false]
 *
 * Prints a table per entry and event (days, early, late ends, 0/1/2/3+ minutes late, exact share),
 * writes each checked entry's stamp to `official/stamps/<entry>.json`, and with `-Pfit` prints that
 * entry's never-early margins from its fit rows and the held-out table they give. Fails when a
 * start is early, an end late, lateness is over the entry's limit, or a day is out of order.
 */
/**
 * Task 11's proof-stamp table: writes `ProofStamps.kt` (the About-times screen's headline numbers)
 * from every committed `official/stamps/<entry>.json`. Re-run after any `gate` run that changes a
 * stamp, and commit the result — see `GenerateProofStamps.kt`'s own comment.
 *
 *     ./gradlew -p tools/timetables generateProofStamps [-Pstamps=<dir>] [-Pout=<file>]
 */
tasks.register<JavaExec>("generateProofStamps") {
    group = "application"
    description = "Writes ProofStamps.kt (the About-times screen's headline numbers) from the committed stamps."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.gate.GenerateProofStampsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    val stampsDir = providers.gradleProperty("stamps").orElse("")
    val out = providers.gradleProperty("out").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", repoRoot.path) +
            (if (stampsDir.get().isNotBlank()) listOf("--stamps", stampsDir.get()) else emptyList()) +
            (if (out.get().isNotBlank()) listOf("--out", out.get()) else emptyList())
    })
}

/**
 * Writes the golden vector (spec §3.2, ruling R84):
 * `shared/src/commonTest/kotlin/world/taqwa/app/prayer/engine/golden/GoldenVectorData.kt`.
 *
 *     ./gradlew -p tools/timetables generateGoldenVector [-Pout=<file>]
 *
 * `out` is relative to the repository root; the default is the committed file itself. Regenerate
 * after any change to the engine, the registry or the generator (`tools/timetables/.../golden/`)
 * and commit the result (`GenerateGoldenVectorKt`'s own KDoc).
 */
tasks.register<JavaExec>("generateGoldenVector") {
    group = "application"
    description = "Writes the golden vector shared/commonTest reads (spec 3.2)."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.golden.GenerateGoldenVectorKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    val out = providers.gradleProperty("out")
        .orElse("shared/src/commonTest/kotlin/world/taqwa/app/prayer/engine/golden/GoldenVectorData.kt")
    argumentProviders.add(CommandLineArgumentProvider { listOf("--out", out.get()) })
}

/**
 * Writes `QmdbPlaceList.kt`: every distinct point of QMDB's own city list as a kz.qmdb unit, each with its reach
 * measured by the engine's own rule (`units/GenerateQmdbPlaces.kt`; the owner's decision of 9 Oct 2026). Re-run after
 * any change to kz.qmdb's method, bands or curves, then run the gate and commit. Takes a few minutes on every core.
 *
 *     ./gradlew -p tools/timetables generateQmdbPlaces [-Pcities=<cities.json.gz>] [-Pout=<file>] [-Pthreads=N]
 */
tasks.register<JavaExec>("generateQmdbPlaces") {
    group = "application"
    description = "Writes QmdbPlaceList.kt: QMDB's places as kz.qmdb units, each reach measured."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.GenerateQmdbPlacesKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "4g"
    val cities = providers.gradleProperty("cities").orElse(officialRoot.map { "$it/archive/raw/manual/kz-qmdb/2026-10-06/cities.json.gz" })
    val out = providers.gradleProperty("out").orElse("")
    val threads = providers.gradleProperty("threads").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", repoRoot.path, "--cities", cities.get()) +
            (if (out.get().isNotBlank()) listOf("--out", out.get()) else emptyList()) +
            (if (threads.get().isNotBlank()) listOf("--threads", threads.get()) else emptyList())
    })
}

/**
 * Writes `official/gate/sa-ummalqura-reach.tsv` and `kz-qmdb-reach.tsv`: each measured Umm al-Qura and QMDB unit's
 * own tables replayed across its area (`units/GenerateReachRows.kt`, the review of 9 Oct 2026). No archive needed: it
 * reads the gate files and the registry only. Re-run after a unit or its reach changes, then run the gate and commit.
 *
 *     ./gradlew -p tools/timetables generateReachRows
 */
tasks.register<JavaExec>("generateReachRows") {
    group = "application"
    description = "Writes the reach rows: each measured Umm al-Qura and QMDB unit's own tables across its area."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.GenerateReachRowsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "2g"
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider { listOf("--repo", root) })
}

/**
 * Writes `DiyanetEuropeCurves.kt`, the per-city curves of Diyanet's European tables, from every held
 * capture of each city (restricted, read from the official root); the generator's KDoc says how a
 * slot is derived. Re-run when a capture is added to the archive, then run the gate and commit.
 *
 *     ./gradlew -p tools/timetables generateDiyanetEuropeCurves [-Pofficial=<dir>] [-Pout=<file>]
 */
tasks.register<JavaExec>("generateDiyanetEuropeCurves") {
    group = "application"
    description = "Writes DiyanetEuropeCurves.kt from the held Diyanet city tables."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.curves.DiyanetEuropeCurveGeneratorKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    val out = providers.gradleProperty("out").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", repoRoot.path, "--official", officialRoot.get()) +
            (if (out.get().isNotBlank()) listOf("--out", out.get()) else emptyList())
    })
}

tasks.register<JavaExec>("gate") {
    group = "verification"
    description = "Checks the engine against every official day held locally and writes the stamps."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.gate.GateMainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    maxHeapSize = "2g"
    workingDir = repoRoot
    val official = officialRoot
    val groups = providers.gradleProperty("gateGroup").orElse("")
    val entries = providers.gradleProperty("entry").orElse("")
    val fit = providers.gradleProperty("fit").orElse("")
    val stamps = providers.gradleProperty("stamps").orElse("true")
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider {
        fun each(option: String, values: String) =
            values.split(',').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { listOf(option, it) }
        listOf("--repo", root, "--official", official.get()) + each("--group", groups.get()) +
            each("--entry", entries.get()) + each("--fit", fit.get()) +
            (if (stamps.get() == "false") listOf("--no-stamps") else emptyList())
    })
}

/**
 * Spec §5 / ruling R86: `release.sh` and `ios-release.sh` refuse to build on a stale or red stamp;
 * this is that check, run first by both. Fails, naming each entry, where a stamp is red (`broken` >
 * 0), stale (its `engineHash` no longer matches what the current engine core and methods give at the
 * manifest's own points), missing (the manifest has rows for an entry with no stamp file), or where
 * `ProofStamps.kt` no longer matches what `generateProofStamps` would write from the committed
 * stamps. Needs **no archive** — see `CheckStamps.kt`'s own comment for why that is enough.
 *
 *     ./gradlew -p tools/timetables checkStamps
 */
/**
 * The weekly monitor's check half (spec §5, brief P; `scripts/monitor.sh` runs it after the fetchers):
 * each new or changed fetched table at its own point, the whole gate and every survey, the data
 * horizons and the manual sources' due dates, written as a report under the monitor's state folder.
 *
 *     ./gradlew -p tools/timetables monitor -Pofficial=<root> [-Pmonitor=<state dir>] [-Ptoday=yyyy-mm-dd]
 *         [-Ponly=a,b] [-PcheckAll=true] [-PskipFull=true]
 *
 * The exit code is the report's (0 green, 1 attention), read by the shell from `last-run.json`, so
 * a red report is not a failed build here. Needs the archive, like the gate, and an explicit root:
 * without `-Pofficial` or `TAQWA_OFFICIAL` the state and the reports (which quote dates and minutes
 * from restricted tables) would land in this checkout's `tools/timetables/official/monitor/`.
 */
tasks.register<JavaExec>("monitor") {
    group = "verification"
    description = "Checks the fetched tables, the gate, the surveys and the horizons, and writes the monitor's report."
    requireArchive()
    val explicit = explicitOfficial.isPresent
    doFirst {
        if (!explicit) {
            throw GradleException(
                "The monitor needs its root named: pass -Pofficial=<root> or set TAQWA_OFFICIAL (the folder that holds archive/ and " +
                    "monitor/), never this checkout's own tools/timetables/official.",
            )
        }
    }
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.monitor.MonitorMainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    maxHeapSize = "2g"
    workingDir = repoRoot
    isIgnoreExitValue = true
    val official = officialRoot
    val monitorDir = providers.gradleProperty("monitor").orElse("")
    val today = providers.gradleProperty("today").orElse("")
    val only = providers.gradleProperty("only").orElse("")
    val checkAll = providers.gradleProperty("checkAll").orElse("false")
    val skipFull = providers.gradleProperty("skipFull").orElse("false")
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--official", official.get()) +
            (if (monitorDir.get().isNotBlank()) listOf("--monitor", monitorDir.get()) else emptyList()) +
            (if (today.get().isNotBlank()) listOf("--today", today.get()) else emptyList()) +
            only.get().split(',').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { listOf("--only", it) } +
            (if (checkAll.get() == "true") listOf("--check-all") else emptyList()) +
            (if (skipFull.get() == "true") listOf("--skip-full") else emptyList())
    })
}

/**
 * `prove` (docs/MONITOR.md): the monitor's new captures into proof. For each capture a recipe of
 * `official/monitor/recipes.tsv` names whose days the gate does not hold yet: a copy pinned under
 * `archive/tables/pinned/<source>/<date>/`, the recipe's rows appended (split test), the whole gate
 * run, every new row that breaks a promise removed again and reported (never loosened), until green;
 * then the gate files and the stamps written. `scripts/prove.sh` runs it and every check after it.
 *
 *     ./gradlew -p tools/timetables prove -Pofficial=<root> [-Pdate=yyyy-mm-dd] [-Ponly=a,b] [-Preport=<file>] [-PdryRun=true]
 */
tasks.register<JavaExec>("prove") {
    group = "verification"
    description = "Turns the monitor's new captures into gate rows, runs the whole gate and writes the stamps when green."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.monitor.ProveMainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    maxHeapSize = "3g"
    workingDir = repoRoot
    val official = officialRoot
    val date = providers.gradleProperty("date").orElse("")
    val only = providers.gradleProperty("only").orElse("")
    val report = providers.gradleProperty("report").orElse("")
    val dryRun = providers.gradleProperty("dryRun").orElse("false")
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--official", official.get()) +
            (if (date.get().isNotBlank()) listOf("--date", date.get()) else emptyList()) +
            only.get().split(',').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { listOf("--only", it) } +
            (if (report.get().isNotBlank()) listOf("--report", report.get()) else emptyList()) +
            (if (dryRun.get() == "true") listOf("--dry-run") else emptyList())
    })
}

tasks.register<JavaExec>("checkStamps") {
    group = "verification"
    description = "Fails on a stale or red stamp, a missing one, or a stale ProofStamps.kt (spec §5). Needs no archive."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.gate.CheckStampsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    argumentProviders.add(CommandLineArgumentProvider { listOf("--repo", repoRoot.path) })
}
