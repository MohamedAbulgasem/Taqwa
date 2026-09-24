# Taqwa city prayer-time pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a page per city with this month's and next month's prayer times, in English and the city's own languages, computed by the app's own engine and rebuilt every night without anyone touching it.

**Architecture:** A standalone JVM Gradle build (`tools/timetables`) compiles the app's prayer-time, Qibla and Hijri source files unchanged and writes one JSON document of fully localised values; `site/build.py` renders city pages, per-language indexes, the home section and the sitemap from it; the Pages workflow runs both on every relevant push and every night.

**Tech Stack:** Kotlin Multiplatform (jvm target only) with adhan2 and kotlinx-datetime from the app's version catalog; JDK 21 CLDR for formatting; Python 3.12 + markdown for the site; vanilla JS for the live countdown; GitHub Actions + Pages.

**Spec:** `docs/superpowers/specs/2026-09-25-taqwa-city-pages-design.md`

## Global Constraints

- The numbers on a city page are exactly what the app shows a user in that city with default settings: method from `CalculationMethodDefaults.forCountry`, Asr Hanafi only for PK, IN, BD, AF, high-latitude rule AUTOMATIC, no minute adjustments.
- The generator compiles the app's files by path; it never copies or forks them.
- Digits, clock times and dates follow CLDR for `Locale(pageLanguage, cityCountry)`, as `AndroidPlatformFormat` does; English pages use en-GB.
- Clock times are 24-hour, hour unpadded, minute padded: `5:35`, `13:00`.
- Prayer, method and madhab names come from the app's `strings.xml`; Hijri month names from `HijriMonthNames`.
- Every page language is one of en, ar, fr, tr, id, ur, bn. English exists for every city.
- The page script makes no network request and uses no storage.
- Kotlin test names in backticks contain no commas (breaks the iOS target).
- `python3 site/build.py --check` must pass; `scripts/test.sh` must pass for app changes.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: Hijri month names in their own file

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/hijri/HijriMonthNames.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/hijri/HijriFormatter.kt`

**Interfaces:**
- Produces: `object HijriMonthNames { fun of(month: Int, language: UiLanguage): String }`; `HijriFormatter.monthName` delegates to it.

- [ ] Move the private `MONTHS` table and the lookup into `HijriMonthNames.kt`, importing only `UiLanguage`.
- [ ] `HijriFormatter.monthName(month, language) = HijriMonthNames.of(month, language)`.
- [ ] `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.hijri.HijriFormatterLanguagesTest` passes unchanged.
- [ ] Commit `hijri: month names in a file of their own, so the site generator can compile them`.

### Task 2: The generator build compiles the app's sources and passes the app's tests on the JVM

**Files:**
- Create: `tools/timetables/settings.gradle.kts`, `tools/timetables/build.gradle.kts`, `tools/timetables/gradle.properties`

**Interfaces:**
- Produces: `./gradlew -p tools/timetables jvmTest` runs the app's `PrayerTimesEngineTest`, `HighLatitudeSelectorTest`, `CalculationMethodDefaultsTest`, `QiblaMathTest`, `TabularHijriCalendarTest` on the JVM.

- [ ] `settings.gradle.kts`: plugin and dependency repositories (google, mavenCentral, gradlePluginPortal); version catalog `libs` from `../../gradle/libs.versions.toml`.
- [ ] `build.gradle.kts`: `alias(libs.plugins.kotlinMultiplatform)`, `jvm()`, `jvmToolchain(21)`; `commonMain` srcDirs `../../shared/src/commonMain/kotlin` and `../../widgetcore/src/commonMain/kotlin` filtered to the files in the spec §3.1; `commonTest` srcDir `../../shared/src/commonTest/kotlin` filtered to the five tests; dependencies adhan2, kotlinx-datetime, kotlin-test.
- [ ] Run `./gradlew -p tools/timetables jvmTest`; expect every included test green. If a test needs a file outside the list, add that file only if it is pure.
- [ ] Commit `timetables: a JVM build of the app's own prayer-time, Qibla and Hijri code`.

### Task 3: `Formats` mirrors the app's Android formatter

**Files:**
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Formats.kt`
- Test: `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/FormatsTest.kt`

**Interfaces:**
- Produces: `class Formats(language: String, country: String)` with `digits(n: Int)`, `clock(hour, minute)`, `longDate(LocalDate)`, `monthYear(year, month)`, `weekday(LocalDate)`, `weekdayShort(LocalDate)`, `distance(km: Int)` (grouped), `degrees(Int)`, `countryName(code)`, `locale: Locale`.

- [ ] Tests first: `Formats("ar","LY").clock(5,35) == "5:35"`; `Formats("ar","EG").clock(5,35) == "٥:٣٥"`; `Formats("bn","BD").clock(13,0) == "১৩:০০"`; `Formats("ur","PK").clock(13,0) == "13:00"`; `Formats("en","LY").longDate(2026-09-25) == "25 September 2026"`; `Formats("fr","MA").monthYear(2026,9)` is French; `countryName("LY")` in Arabic is `ليبيا`; `Formats("en","ZA").longDate(2026-09-05)` has no leading zero.
- [ ] Implement with `NumberFormat.getIntegerInstance(locale)` (no grouping) for digits, the same LONG-pattern trick as `AndroidPlatformFormat` for dates, `DateTimeFormatter.ofPattern("LLLL y")` with `DecimalStyle.of(locale)` for month titles, `Locale.getDisplayCountry`. English uses en-GB whatever the country.
- [ ] Commit.

### Task 4: `AppStrings` reads the app's translations

**Files:**
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/AppStrings.kt`
- Test: `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/AppStringsTest.kt`

**Interfaces:**
- Produces: `class AppStrings(resourcesDir: File)` with `get(language, key): String` (Indonesian reads `values-in`), `prayer(language, Prayer)`, `method(language, CalculationMethodId)`, `madhab(language, AsrMadhab)`, `qiblaDetail(language, bearing, distance)`.

- [ ] Tests first against the real resources: `prayer("ar", DHUHR) == "الظهر"`, `method("en", MUSLIM_WORLD_LEAGUE) == "Muslim World League"`, missing key throws naming the key and language, XML entities (`&amp;`) and Android escapes (`\'`) decoded.
- [ ] Implement with `javax.xml.parsers.DocumentBuilderFactory`.
- [ ] Commit.

### Task 5: `Catalog` reads and validates the curated list

**Files:**
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Catalog.kt`
- Test: `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/CatalogTest.kt`

**Interfaces:**
- Produces: `data class City(slug, id, name: Map<lang,String>, countryCode, countryNameEn, region, lat, lon, tz, languages: List<String>, madhab: AsrMadhab, featured: Set<String>)`; `object Catalog { fun load(listFile: File, appFiles: File): List<City> }` throwing `CatalogError` with every problem listed.

- [ ] Tests first with small fixture files: unknown id, duplicate slug, slug with uppercase or spaces, unknown language, a page language with no name and no override, English missing from languages: each is an error listing the row. A valid row resolves coordinates and zone from `cities.csv` and names from `city-names-<lang>.csv`, overrides win.
- [ ] `madhab` defaults to HANAFI for PK, IN, BD, AF, else STANDARD, and a row may override it.
- [ ] Commit.

### Task 6: `Timetable` and `Main` write the JSON

**Files:**
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Timetable.kt`, `Json.kt`, `Main.kt`
- Modify: `tools/timetables/build.gradle.kts` (a `generate` JavaExec task)
- Test: `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/TimetableTest.kt`

**Interfaces:**
- Produces: `./gradlew -p tools/timetables generate -Pout=<file> [-Ptoday=YYYY-MM-DD]` writes `{generated, today, cities: [...]}` where each city carries facts and `pages[lang]` with every display string, `months[2]` of `days[]` (date, weekday, hijri, six formatted times, six epoch seconds, `friday`, `highLatitude`), plus `qibla` and `method` strings.

- [ ] Tests first: Tripoli 2026-09-13 reads Fajr 5:26, Dhuhr 13:04, Asr 16:34, Maghrib 19:16, Isha 20:35 (the app's own screen); months cover the first of this month to the last of next; a London day across the October DST change has correct local times; Karachi uses Hanafi Asr; an Arabic Libyan page uses Latin digits.
- [ ] Minimal JSON writer (strings escaped, no dependency).
- [ ] Commit.

### Task 7: The curated list

**Files:**
- Create: `site/cities.tsv`

- [ ] About 170 rows per the spec's language rules; overrides for known bad names (Al Khums in Arabic).
- [ ] Run the generator; fix every catalog error; spot-check twenty cities' coordinates and zones.
- [ ] Apply the research verdicts: comment out held-back countries with the reason.
- [ ] Commit.

### Task 8: Rendering city pages, indexes and the home section

**Files:**
- Create: `site/timetables.py`, `site/templates/city.html`, `site/templates/cities-index.html`
- Modify: `site/build.py`, `site/templates/base.html`, `site/assets/style.css`, `site/pages/*/meta.json`, `site/pages/*/home.html`, `site/pages/*/support.html`

**Interfaces:**
- Consumes: the JSON from Task 6.
- Produces: `/{lang}/prayer-times/` and `/{lang}/prayer-times/{slug}/`, the home section, "Prayer times" in the nav, sitemap entries with correct alternates.

- [ ] Sentences per language in `meta.json → timetable` (all seven, written with the site's existing voice).
- [ ] City page per the mockup; index grouped by region and country; home section at the bottom of each home page; support page anchor `#prayer-time` for "why your mosque may differ".
- [ ] Picker links to twins or that language's index; hreflang only real twins.
- [ ] `BreadcrumbList` JSON-LD on city pages.
- [ ] Commit.

### Task 9: The live script

**Files:**
- Create: `site/assets/timetable.js`

- [ ] Reads the page's inline JSON; today in the city's zone via `Intl.DateTimeFormat(..., {timeZone})`; lights the row, fills the Today card, ring and countdown every second; folds past days on narrow screens behind a button; filters the index. No network, no storage, respects reduced motion.
- [ ] Commit.

### Task 10: `--check` guards the new pages

**Files:**
- Modify: `site/build.py`

- [ ] Fail on: a city page without two month tables, a day count that is not the month's length, a hreflang not reciprocated, an index link to a missing page, a home section link to a missing page.
- [ ] Commit.

### Task 11: Automation

**Files:**
- Modify: `.github/workflows/pages.yml`

- [ ] Triggers: push paths (site, tools/timetables, every app file the generator compiles or reads, the catalog), schedule `7 0 * * *`, workflow_dispatch.
- [ ] Steps: checkout, setup-java 21 (pinned SHA), setup-gradle (pinned SHA), generator tests + generate, setup-python, markdown, build `--check`, upload, deploy; on schedule, `PUT /actions/workflows/pages.yml/enable` with `actions: write`.
- [ ] Commit.

### Task 12: Verification

- [ ] Cross-check a sample of cities and dates against the Aladhan API with the matching method.
- [ ] Render a city page (desktop, phone, Arabic, dark), the index and the home section; look once, fix, and move on.
- [ ] Independent code review of the branch.

### Task 13: The Jumuʿah pill in the app (separate branch, done by a subagent)

- [ ] Merge its branch after reviewing the diff, tests and screenshots.

### Task 14: Ship

- [ ] README website section; build log entry.
- [ ] Merge `origin/main`, run every check, push to `main`, watch the deploy, verify pages live and the sitemap.
