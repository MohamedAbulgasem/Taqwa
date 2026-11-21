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
 *
 * They live in three modules, `:shared`, `:widgetcore` and `:watchcore` (whose `QiblaAngles` the
 * Qibla maths delegates to, so both watches judge alignment with the phone's own code). A file
 * moved between them must move here too; `scripts/test.sh` compiles this build to catch it.
 */
val appSources = listOf(
    "world/taqwa/app/prayer/**",
    "world/taqwa/app/domain/TimelineState.kt",
    "world/taqwa/app/domain/Prayer.kt",
    "world/taqwa/app/domain/PrayerSettings.kt",
    "world/taqwa/app/domain/GeoLocation.kt",
    "world/taqwa/app/qibla/QiblaMath.kt",
    "world/taqwa/app/qibla/QiblaAngles.kt",
    "world/taqwa/app/hijri/TabularHijriCalendar.kt",
    "world/taqwa/app/hijri/HijriMonthNames.kt",
    "world/taqwa/app/i18n/UiLanguage.kt",
    "world/taqwa/app/i18n/CountdownDigits.kt",
    "world/taqwa/app/i18n/DigitGrouping.kt",
)

/** The app's own tests for those files, run here on the JVM to prove this build computes what
 * the app computes. */
val appTests = listOf(
    "world/taqwa/app/prayer/PrayerTimesEngineTest.kt",
    "world/taqwa/app/qibla/QiblaMathTest.kt",
    "world/taqwa/app/hijri/TabularHijriCalendarTest.kt",
    "world/taqwa/app/i18n/DigitGroupingTest.kt",
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
            kotlin.srcDir(repoRoot.resolve("watchcore/src/commonMain/kotlin"))
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

// The Tromsø probe is not part of the suite: it runs alone, for an hour a grid, through the `tromsoProbe` task below.
tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching("world.taqwa.timetables.probe.*") }
}

tasks.withType<Test>().configureEach {
    requireArchive()
    systemProperty("taqwa.repoRoot", repoRoot.path)
    systemProperty("taqwa.official", officialRoot.get())
    // -PgateGroup / -Pentry narrow NeverEarlyGateTest as they narrow the gate task (one group's proof;
    // not -Pgroup, which is Gradle's own project group).
    systemProperty("taqwa.gateGroups", providers.gradleProperty("gateGroup").getOrElse(""))
    systemProperty("taqwa.gateEntries", providers.gradleProperty("entry").getOrElse(""))
    // NeverEarlyGateTest runs the whole gate in the test's own JVM, so it takes what the gate task takes: on the
    // merged line of 14 Nov 2026 two gigabytes ran out there too ("Java heap space" in Gate.evaluate).
    maxHeapSize = "4g"
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
    // GpsCountryTest reads the gate points listed as lying in another country (official/gps-country/, ruling R258).
    inputs.files(fileTree(repoRoot.resolve("tools/timetables/official/gps-country")) { include("*.tsv") })
        .withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("gpsCountry")
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
 * The Tromsø probe (ruling R321, kept since the final review of 20 Nov 2026; `probe/TromsoProbeTest`): IRN's Tromsø table
 * replayed across `no.irn.tromso`'s circle, densely, through the real Registry, Gate and PrayerEngine paths. Outside the
 * suite, and run once a grid:
 *
 *     ./gradlew -p tools/timetables tromsoProbe -Pofficial=<archive> -PprobeOut=<dir> -PprobeGrids=refine [-PprobeTag=refine]
 *
 * The grids by name are `refine`, `dense`, `edges` and `micro` (the four of R321; each about ten to twenty minutes), or
 * any the test's KDoc lists. `-PprobeYear`, `-PprobeThreads`, `-PprobeGateEvery`, `-PprobeEngineEvery`, `-PprobeLimit`, `-PprobeDetail`,
 * `-PprobeDump` and `-PprobeCompare` are handed on as the test says. MONITOR.md's December IRN step runs it on all four.
 */
tasks.register<Test>("tromsoProbe") {
    group = "verification"
    description = "Replays IRN's Tromsø table across its unit's circle (ruling R321's probe)."
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    filter { includeTestsMatching("world.taqwa.timetables.probe.TromsoProbeTest") }
    for (name in listOf("Out", "Grids", "Year", "Tag", "Threads", "GateEvery", "EngineEvery", "Limit", "Detail", "Dump", "Compare")) {
        providers.gradleProperty("probe$name").orNull?.let { systemProperty("probe.${name.replaceFirstChar { it.lowercase() }}", it) }
    }
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true }
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
 * Writes `official/gate/<group>-reach.tsv` for every entry of `ReachRows.SPECS` (Umm al-Qura's and QMDB's units, ISNA's
 * mosques, BIK's base table, the Kyrgyz Muftiate's localities, ESA's Egyptian towns, Oman's localities and Türkiye's
 * il centres): each
 * measured unit's own tables replayed across its area, on a fixed grid and at each event's worst point of a dense sweep
 * against the archive's tables (`units/GenerateReachRows.kt`, the reviews of 9 and 10 Oct 2026). Re-run after a unit,
 * its reach or its tables change, then run the gate and commit. Takes a few minutes on every core; `--only <entry>`
 * writes one entry's file, and is the only way to write a pending entry's (`ReachRows.PENDING`: an entry whose reach
 * rows the gate does not pass yet, so its file is not committed; none since ruling R302 of 17 Nov 2026: Diyanet's cities left it
 * with ruling R293 on 16 Nov, Norway's IRN, the last, with R302).
 *
 *     ./gradlew -p tools/timetables generateReachRows [-Pthreads=N] [--args="--only eg.esa"]
 */
tasks.register<JavaExec>("generateReachRows") {
    group = "application"
    description = "Writes the reach rows: each measured unit's own tables across its area (ReachRows.SPECS)."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.GenerateReachRowsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "4g"
    val root = repoRoot.path
    val official = officialRoot
    val threads = providers.gradleProperty("threads").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--official", official.get()) +
            (if (threads.get().isNotBlank()) listOf("--threads", threads.get()) else emptyList())
    })
}

/**
 * Writes `ReachLimits.kt` (ruling R270): each unit of the entries `ReachLimitsFile.ENTRIES` names whose own tables, read
 * across its reach, were measured later than its own limit, at the stamp's measured worst (`units/GenerateReachLimits.kt`).
 * Run after the gate has written those entries' stamps with their reach rows (`-Pentry`), then run the gate again.
 *
 *     ./gradlew -p tools/timetables generateReachLimits
 */
tasks.register<JavaExec>("generateReachLimits") {
    group = "application"
    description = "Writes ReachLimits.kt: each unit's measured worst across its reach, where over its own limit (R270)."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.GenerateReachLimitsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "2g"
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider { listOf("--repo", root) })
}

/**
 * Writes the envelope's promise-only rows, `official/gate/<group>-envelope.tsv`: every table printed for its own point
 * replayed at every app city within its bound reach, whichever table the city follows, and the round's named claims
 * (`units/GenerateEnvelopeRows.kt`, the city-point round 2 review of 11 Oct 2026). Reads only the gate files and the
 * app's cities. Re-run after such a table, its rows or its reach change, then run the gate and commit.
 *
 *     ./gradlew -p tools/timetables generateEnvelopeRows
 */
tasks.register<JavaExec>("generateEnvelopeRows") {
    group = "application"
    description = "Writes the envelope's promise-only rows: each table printed for its own point at every app city its bound reach holds."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.GenerateEnvelopeRowsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "2g"
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider { listOf("--repo", root) })
}

/**
 * Writes nothing: names every envelope file `generateEnvelopeRows` would write, change or remove now, and exits 1 when
 * there is one (the audit of 13 Nov 2026). The generator is a pure function of the gate's group files, the engine's
 * reaches and the app's cities; the committed files fall behind when `prove` appends a new capture's rows to a group
 * file, and this is the check that says so. Run it at every release cut and after any change to a table printed for
 * its own point; when it names a file, run `generateEnvelopeRows`, then the gate, and commit.
 *
 *     ./gradlew -p tools/timetables checkEnvelopeRows
 */
tasks.register<JavaExec>("checkEnvelopeRows") {
    group = "verification"
    description = "Fails while a committed envelope file is not what generateEnvelopeRows would write now."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.GenerateEnvelopeRowsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "2g"
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider { listOf("--repo", root, "--check") })
}

/**
 * Measures a unit set's overlaps and writes nothing into the repository (`units/OverlapSweep.kt`, the audit of 12 Nov
 * 2026, ruling R205): every measured unit's own tables on a lattice across its reach wherever another unit's reach
 * holds the point too; whether a place that follows another unit is ever before this table, and how late a place is
 * against its own table inside a neighbour's reach.
 *
 *     ./gradlew -p tools/timetables overlapSweep -Pentry=kg.default [-Pstep=1.5] [-Ppaths=-2027] [-Pyear=2027] [-Pout=<file>] [-Pthreads=N]
 */
tasks.register<JavaExec>("overlapSweep") {
    group = "application"
    description = "Measures a unit set's overlaps: each unit's own tables wherever another unit's reach holds the place too."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.OverlapSweepKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "4g"
    val root = repoRoot.path
    val official = officialRoot
    val options = listOf("entry", "step", "paths", "year", "out", "threads").map { it to providers.gradleProperty(it).orElse("") }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--official", official.get()) +
            options.flatMap { (name, value) -> if (value.get().isNotBlank()) listOf("--$name", value.get()) else emptyList() }
    })
}

/**
 * The ground past an authority's inner lines (ruling R260; `units/EdgeSweep.kt`), read-only: each unit's own tables on
 * rings past its reach, belt or ride where it is the nearest unit and the place is on the edge, with and without the
 * rule, and at the app's cities there.
 *
 *     ./gradlew -p tools/timetables edgeSweep -Pentry=kg.default|eg.esa|om.mara [-Pthreads=N] [-Pout=<file>]
 */
tasks.register<JavaExec>("edgeSweep") {
    group = "application"
    description = "Measures the ground past an authority's inner lines: each unit's own tables where it is the nearest unit on the edge."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.EdgeSweepKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "4g"
    val root = repoRoot.path
    val official = officialRoot
    val options = listOf("entry", "out", "threads").map { it to providers.gradleProperty(it).orElse("") }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--official", official.get()) +
            options.flatMap { (name, value) -> if (value.get().isNotBlank()) listOf("--$name", value.get()) else emptyList() }
    })
}

/**
 * QMDB's overlapping places (ruling R249; `units/KazakhRide.kt`), read-only: what resolving a place and computing a day
 * cost where many reaches hold it, what waiting for every such place's table moves against the nearest alone, and (`pairs`,
 * with the archive) how far QMDB's own tables of neighbouring places stand apart.
 *
 *     ./gradlew -p tools/timetables kazakhRide -Pmode=time|cost|pairs [-Pstep=0.05] [-Pyear=2027] [-Pout=<file>] [-Pthreads=4]
 */
tasks.register<JavaExec>("kazakhRide") {
    group = "application"
    description = "Measures QMDB's overlapping places: the cost in time on a device, and in minutes against the nearest place alone."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.KazakhRideKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "4g"
    val root = repoRoot.path
    val official = officialRoot
    val options = listOf("mode", "step", "year", "out", "threads").map { it to providers.gradleProperty(it).orElse("") }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--official", official.get()) + options.flatMap { (name, value) -> if (value.get().isNotBlank()) listOf("--$name", value.get()) else emptyList() }
    })
}

/**
 * Writes `official/own-points-worst.tsv`: each own-point row's own worst lateness per event (Umm al-Qura's and QMDB's
 * group files; `units/OwnPointWorst.kt`), which `CityUnitsTest` holds every such row to. Re-run when a change is meant
 * to move an own point's lateness, or to take in rows `prove` added, then commit.
 *
 *     ./gradlew -p tools/timetables generateOwnPointWorst
 */
tasks.register<JavaExec>("generateOwnPointWorst") {
    group = "application"
    description = "Writes each Umm al-Qura and QMDB own-point row's worst lateness, which CityUnitsTest holds."
    requireArchive()
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.units.OwnPointWorstKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    maxHeapSize = "2g"
    val root = repoRoot.path
    val official = officialRoot
    argumentProviders.add(CommandLineArgumentProvider { listOf("--repo", root, "--official", official.get()) })
}

/**
 * Writes `DiyanetEuropeCurves.kt`, the per-city curves of Diyanet's European tables, from the
 * captures DIYANET_EUROPE_CITIES names for each city (restricted, read from the official root: the
 * research's and the monitor's of 28 September 2026, pinned, never the monitor's live files); the
 * generator's KDoc says how a slot is derived. Re-run when a pinned capture is added to that list,
 * then run the gate and commit.
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
    // The whole gate keeps every entry's counted days (ruling R214) beside its cautious cells: with Egypt's envelope and
    // Oman's reach rows in the same run, two gigabytes ran out (14 Nov 2026). The other long tasks already take four.
    maxHeapSize = "4g"
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
 * The sun-sanity pass (engine survey of 11 Nov 2026): every app city, unit point and region point, and each Settings
 * choice at a sample of the app cities where it applies, every day of the window, both Asr schools, held to the real
 * sun at the point (no Dhuhr before transit, no Maghrib before sunset, no Fajr or end of eating after sunrise, no Isha
 * at or before Maghrib). Needs no archive. Fails on a finding not listed in `official/sun-sanity/open-findings.tsv`, an
 * excused group not listed in `official/sun-sanity/excused.tsv`, a listed one past its ceilings, or any Dhuhr before
 * transit or Maghrib before sunset at all (the engine's own floor, ruling R180); reports what that floor moved.
 *
 *     ./gradlew -p tools/timetables sunSanity [-Pfrom=<yyyy-mm-dd, today>] [-Pto=<yyyy-mm-dd, 13 months on>] [-PeveryDays=1]
 *         [-PeveryCity=1] [-PchosenPerEntry=40] [-PotherPerEntry=400] [-Pout=<report file>]
 */
tasks.register<JavaExec>("sunSanity") {
    group = "verification"
    description = "Holds every app city's and unit's days to the real sun (transit, sunrise, sunset)."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.sanity.SunSanityMainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    maxHeapSize = "4g"
    workingDir = repoRoot
    val root = repoRoot.path
    val options = listOf("from", "to", "everyDays", "everyCity", "chosenPerEntry", "otherPerEntry", "threads", "out").associateWith { providers.gradleProperty(it).orElse("") }
    argumentProviders.add(CommandLineArgumentProvider {
        val flags = mapOf("from" to "--from", "to" to "--to", "everyDays" to "--every-days", "everyCity" to "--every-city", "chosenPerEntry" to "--chosen-per-entry", "otherPerEntry" to "--other-per-entry", "threads" to "--threads", "out" to "--out")
        listOf("--repo", root) + options.flatMap { (name, value) -> value.get().takeIf { it.isNotBlank() }?.let { listOf(flags.getValue(name), it) }.orEmpty() }
    })
}

/**
 * Spec §5 / ruling R86: `release.sh` and `ios-release.sh` refuse to build on a stale or red stamp;
 * this is that check, run first by both. Fails, naming each entry, where a stamp is red (`broken` >
 * 0), stale (its `engineHash` no longer matches what the current engine core and methods give at the
 * manifest's own points), missing (the manifest has rows for an entry with no stamp file), or where
 * `ProofStamps.kt` no longer matches what `generateProofStamps` would write from the committed
 * stamps. Needs **no archive** — see `CheckStamps.kt`'s own comment for why that is enough. For the
 * Ramadan build (spec §5's Ramadan release rule, ruling R146) `-Pramadan=<Hijri year>` also fails on
 * an A/B entry with a unit whose Fajr, end of eating and Maghrib were not each checked on every day of
 * that tabular Ramadan (ruling R148); the release scripts pass it from `TAQWA_RAMADAN`.
 *
 *     ./gradlew -p tools/timetables checkStamps [-Pramadan=1448]
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
    // The monitor's check runs the whole gate in this JVM (MonitorMain's fullGate), so it takes what the gate task takes
    // (ruling R280, the second audit's G17): two gigabytes ran out on the whole gate of the merged line of 14 Nov 2026.
    maxHeapSize = "4g"
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
    // The whole gate, as the gate task takes it (ruling R280): prove keeps one whole result at a time (the base's is let
    // go before the gate runs again), so the gate's four gigabytes are enough; three were not, holding two.
    maxHeapSize = "4g"
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
    description = "Fails on a stale or red stamp, a missing one, or a stale ProofStamps.kt (spec §5); with -Pramadan=<Hijri year>, " +
        "on an A/B unit whose Fajr, end of eating and Maghrib were not each checked on every day of that Ramadan and that is not " +
        "covered under O4 (ruling R185: that rule reads the monitor's index from -Pofficial); with -Prelease=true, while a reach " +
        "proof is owed (ReachRows.outstanding). The stamps themselves need no archive."
    classpath = files(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("world.taqwa.timetables.gate.CheckStampsKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir = repoRoot
    val ramadan = providers.gradleProperty("ramadan").orElse("")
    // Ruling R185: the month-by-month rule (O4) reads the monitor's index from the official root; a standing demotion
    // stops a release, and only the monitor's own run (scripts/prove.sh) passes -PallowDemoted=true.
    val official = officialRoot
    val allowDemoted = providers.gradleProperty("allowDemoted").orElse("false")
    // A release (scripts/release.sh, scripts/ios-release.sh) passes -Prelease=true: it is refused while a reach proof is
    // owed (a unit left out of its entry's reach rows, a pending entry, a gated entry with measured units and no spec, or
    // a reach limit over 4 min the owner has not ruled on: ReachRows.outstanding, ruling R270). Every run also fails on a
    // ReachLimits.kt the committed stamps no longer give.
    val release = providers.gradleProperty("release").orElse("false")
    val root = repoRoot.path
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--repo", root, "--allow-demoted", allowDemoted.get(), "--release", release.get()) +
            (if (ramadan.get().isNotBlank()) listOf("--ramadan", ramadan.get(), "--official", official.get()) else emptyList())
    })
}
