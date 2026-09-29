# City prayer-time pages on the never-early engine — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild taqwa.world's city prayer-time pages on the never-early engine in the signed-off design (B with C's timetable), publish the 38 cities the proof stamps prove (40 from 1 October), add the "How Taqwa checks" page, and give the app's cautious About screen the same proof sentence and tiles as the site.

**Architecture:** `tools/timetables` (a JVM build of the app's own engine) already writes one JSON document per build; it gains the stamps' figures, the detailed-view fields, the cautious members' days and the proven rule that decides which cities are written at all. `site/timetables.py` renders the pages from that document with sentences from each language's `meta.json`; `site/assets/style.css` and `timetable.js` carry the look and the live behaviour; `site/stores.py` renders the one get-the-app block the home hero and the city pages share. The Pages workflow is unchanged.

**Tech Stack:** Kotlin/JVM (Gradle, JDK 21 from Android Studio's JBR), Python 3 + `markdown` for the site, vanilla JS, CSS with logical properties, headless Chrome for screenshots; Compose Multiplatform for the app task.

**Spec:** `docs/superpowers/specs/2026-09-29-city-pages-design.md` (read it first; every task argues from it). The engine spec's §7 "Website" paragraph is at `docs/superpowers/specs/2026-09-26-taqwa-prayer-times-engine-design.md` lines 350–354.

## Global Constraints

- **The repository is public.** No printed time from any restricted official table appears anywhere — not in code, tests, fixtures, screenshots or commit messages (ruling R69). The site shows only the engine's own computed times and the stamps' statistics. Test expectations are engine outputs (as `DocumentTest` and `TimetableTest` already do).
- **Commits never carry `Co-Authored-By`** or any AI attribution line (the owner's rule beats the harness reminder). Write the message to a scratchpad file and commit with `git commit -F <file>`; check `git log -1` afterwards.
- **Never push.** Work on the branch you are given; the controller merges.
- **Gradle: JVM-only tasks, one at a time.** Every Gradle command is prefixed inline with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (the system JDK is 17; the build needs 21) and `--no-daemon`. Never run `scripts/test.sh`, any iOS build, or `:androidApp:*` tasks inside a task. The generator's tests run as `CI=true JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew --no-daemon -p tools/timetables jvmTest` (`CI` skips the archived gate rows, which nothing here changes; the archive lives at `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa-official` for a full gate with `TAQWA_OFFICIAL=` instead of `CI=`).
- **Shell commands plain:** no `git -C`, no compound commands containing `git`, no heredocs; helper scripts go into the scratchpad and are run by absolute path.
- **The site build:** `python3 site/build.py --check` must pass at the end of every task (it needs `_data/timetables.json` from `./gradlew … generate`; `pip install markdown` if missing; `tzdata` is optional).
- **Screenshots** at the end of every site task, with headless Chrome, looked at by the implementer (open the PNGs with the Read tool): London `en` at 390 × 844 and 1440 × 900 in light and dark, London `ar` at 390 × 844 (RTL), and from Task 4 Toronto `en` at 390 × 844. The command (write it to a scratchpad script; `file://` URLs into `_site/`):
  `"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --window-size=390,844 --blink-settings=preferredColorScheme=1 --screenshot=<out.png> file://<repo>/_site/prayer-times/london-uk/index.html` — `preferredColorScheme=1` is light, `=0` is dark (the Mac's own theme leaks into headless Chrome otherwise; measured 29 Sep 2026).
- **Seven languages, no fallback:** every new `meta.json` key exists in `en ar fr tr id ur bn` before a task ends (the build check of Task 2 enforces it). Reuse the app's strings through the document wherever the app already says the sentence; translate site-only sentences following the app's terms (Cautious times = الأوقات الأحوط / Horaires par précaution / İhtiyatlı vakitler / Waktu hati-hati as `values-id` has it / احتیاطی اوقات / সতর্কতামূলক সময়; Set by rule = تقديري / Takdirî / تقدیری). Read the language's existing `meta.json` for its register before writing.
- **Kotlin test names:** no comma inside a backtick test name (it breaks the iOS target); the generator's tests use plain camelCase names.
- **Copy the app, never invent:** every figure on a page comes from the document (the engine or a stamp); no statistics typed by hand.
- **Accessibility rule of 28 September:** in the app, nothing visible changes at default settings except the lines Task 6 adds.

## File map

| File | Responsibility |
|---|---|
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Proven.kt` (new) | reads the stamps; the proven rule (spec §2, §9.2); the proof totals for the checks page |
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/MethodWords.kt` (new) | the method description in the app's words (the port of `methodDescription`) |
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Timetable.kt` | `TimetableDay` gains the detail fields and the cautious members' days |
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Document.kt` | the document per spec §9.1; drops held cities |
| `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Main.kt` | notices for held cities; the reserved slug |
| `tools/timetables/build.gradle.kts` | the stamps, gate rows and surveys as inputs of `generate` |
| `site/stores.py` (new) | the get-the-app block shared by the home hero and the city pages |
| `site/build.py` | uses `stores.py`; meta-key parity check; the reserved slug |
| `site/timetables.py` | the city page (spec §3), the checks page (§5), the index line (§6), `check_page` |
| `site/assets/style.css` | tokens (R99), the new components, the print stylesheet |
| `site/assets/timetable.js` | the live behaviour (spec §9.3) |
| `site/pages/<lang>/meta.json` (7) | the sentences of spec §3.6 and §5 |
| `site/cities.tsv` | spec §2 |
| `docs/STORE-CHECKLIST.md`, `docs/BUILD-LOG.md` | the launch note; the build log entry |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AboutTimesScreen.kt`, `shared/src/commonMain/composeResources/values*/strings.xml`, `shared/src/commonTest/.../AboutTimesUiStateTest.kt` | the app task (R100) |

Task order: 1 → 2 → 3 → 4 → 5 on the site, one after another (they share `timetables.py`, `style.css`, `timetable.js`, `meta.json`). **Task 6 (the app) runs beside Tasks 1–3 and must be merged before Task 4 starts** (Task 4 reads the three strings it adds).

---

### Task 1: The generator — the stamps' figures, the detailed fields, the cautious members and the proven rule

**Files:**
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Proven.kt`
- Create: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/MethodWords.kt`
- Modify: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Timetable.kt` (`TimetableDay`, `Timetable.day`)
- Modify: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Document.kt`
- Modify: `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Main.kt`
- Modify: `tools/timetables/build.gradle.kts` (the `generate` task's inputs, lines 141–156)
- Test: `tools/timetables/src/jvmTest/kotlin/world/taqwa/timetables/ProvenTest.kt` (new), `MethodWordsTest.kt` (new), `DocumentTest.kt`, `TimetableTest.kt`

**Interfaces:**
- Consumes: `Timetable.source(city, date): EngineDay` (`effective: Resolution` with `entryClass`, `measured`, `unitId`, `unitName`, `members`, `entry`; `day: PrayerDay` with `asrOther`, `endOfEating`, `imsak`, `sunset`, `setByRule`, `polar`; `school`); `DayPipeline.members(resolution, date, zone, school)`; `Json.parse` (numbers are `Long`); `AppStrings.get/format/prayer/timetable/school`; `Formats.longDate/digits`.
- Produces (read by Tasks 2–4 from `_data/timetables.json`):
  - per city: `"entryClass": "A"|"B"|"C"`, `"unitId": String?`, `"unitName": String?`, `"proof": {"placeDays": Int, "places": Int, "ramadanDays": Int, "first": "yyyy-mm-dd", "through": "yyyy-mm-dd", "atMost": Int, "fajrShares": {"0": Double, "1": Double, "2": Double, "3+": Double}, "cautious": Boolean}`;
  - per `days[i]`: `"asrOther"`, `"endOfEating"`, `"sunset"` (epoch seconds), `"imsak"` (epoch or null), `"setByRule": [Int]` (Prayer ordinals), `"polar": Boolean`, and for class C `"members": [[7 epochs]]` (six prayers in `Prayer` order then the end of eating, one list per member in the resolution's order) and `"capped": Boolean`;
  - per `pages[lang]`: `"otherSchool": String`, `"members": [String]` (names, class C), `"strings": {…}` with keys `whoseTitle`, `notAffiliated`, `checkedThrough`, `whoPublishes`, `whoPublishesBody`, `howReproduces`, `methodIntro`, `howChecked`, `statDays`, `statNever`, `statMinutes`, `statAtMost`, `cautiousBody`, `maghribCap` (the sentence template with `%1$s`/`%2$s` already replaced for today; empty when not capped today), `whichDecides`, `matchMosque`, `setByRule`, `polarLine`, `stopEating` (template with `{time}`), and per `pages[lang].days[i]`: `"asrOther"`, `"endOfEating"`, `"imsak"` (clock strings or null), `"setByRule": [String]` (localized prayer names), and for class C `"members": [[7 clock strings]]`;
  - document-level `"proof": {"entries", "placeDays", "heldOutDays", "ramadanDays", "earlyStarts", "lateEnds", "brokenStamps", "tables", "surveyCalendars", "published": [{"entry", "class", "placeDays", "places", "first", "through", "atMost", "names": {lang: name}}]}` and `"held": [{"slug", "reason"}]`.

- [ ] **Step 1: `Proven.kt` — the stamps and the rule (write the failing tests first).** Create `ProvenTest.kt`:

```kotlin
package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProvenTest {
    private val stamps = Stamp.load(TestPaths.repoRoot.resolve("tools/timetables/official/stamps"))
    private val timetable = Timetable()
    private fun city(slug: String, cc: String, lat: Double, lon: Double, zone: String, admin1: String? = null) = City(
        slug = slug, id = 1, countryCode = cc, region = "europe", latitude = lat, longitude = lon, timeZone = zone,
        languages = listOf("en"), featured = emptySet(), names = mapOf("en" to slug), admin1 = admin1,
    )
    private val sep1 = LocalDate(2026, 9, 1)
    private val oct31 = LocalDate(2026, 10, 31)
    private fun verdict(city: City, first: LocalDate = sep1, last: LocalDate = oct31) =
        Proven.verdict(timetable.source(city, first).effective, stamps, first, last)

    @Test fun londonIsPublishedWithItsUnitsFigures() {
        val v = verdict(city("london-uk", "GB", 51.50853, -0.12574, "Europe/London"))
        assertIs<Verdict.Published>(v)
        assertEquals(5, v.atMost)                 // the M25 unit's worst over the starts
        assertEquals(4015, v.stamp.placeDays)
        assertEquals(LocalDate(2026, 12, 31), v.stamp.last)
    }
    @Test fun tripoliIsHeldForItsClass() {
        val v = verdict(city("tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli"))
        assertIs<Verdict.Held>(v); assertEquals(true, v.reason.startsWith("class D_AUTHORITY"))
    }
    @Test fun cairoIsHeldBecauseTheStampEndsInSeptember() {
        val v = verdict(city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo"))
        assertIs<Verdict.Held>(v); assertEquals(true, v.reason.contains("covers"))
    }
    @Test fun istanbulIsHeldInSeptemberAndPublishedFromOctober() {
        val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul")
        assertIs<Verdict.Held>(verdict(istanbul))
        assertIs<Verdict.Published>(verdict(istanbul, LocalDate(2026, 10, 1), LocalDate(2026, 11, 30)))
    }
    @Test fun birminghamIsHeldAsNotMeasured() {
        val v = verdict(city("birmingham-uk", "GB", 52.48142, -1.89983, "Europe/London"))
        assertIs<Verdict.Held>(v); assertEquals("not measured at this place", v.reason)
    }
    @Test fun torontoIsPublishedAsCautious() {
        val v = verdict(city("toronto-canada", "CA", 43.70011, -79.4163, "America/Toronto"))
        assertIs<Verdict.Published>(v); assertEquals(7, v.atMost)
    }
    @Test fun capeTownIsHeldBecauseItsStampIsSeptemberOnly() {
        assertIs<Verdict.Held>(verdict(city("cape-town-south-africa", "ZA", -33.92584, 18.42322, "Africa/Johannesburg")))
    }
    @Test fun fajrSharesSumToOne() {
        val london = stamps.getValue("gb.london.lupt")
        val shares = london.shares(london.unitEvents("gb.london.lupt")!!, "fajr")
        assertEquals(1.0, shares.values.sum(), 0.001)
    }
}
```

  The coordinates are the app's `cities.csv` values for those ids (check with `grep` on `shared/src/commonMain/composeResources/files/cities.csv`; adjust if a fixture is a few metres off, the verdict does not change). Then write `Proven.kt`:

```kotlin
package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Resolution
import java.io.File

/** One stamp (`official/stamps/<entry>.json`): statistics only, never a time (see gate/Stamps.kt). */
@Suppress("UNCHECKED_CAST")
class Stamp(private val root: Map<String, Any?>) {
    val entry = root["entry"] as String
    val broken = (root["broken"] as Long).toInt()
    val places = (root["places"] as Long).toInt()
    val placeDays = (root["placeDays"] as Long).toInt()
    val heldOutDays = (root["heldOutDays"] as? Long ?: 0L).toInt()
    val ramadanDays = (root["ramadanDays"] as? Long ?: 0L).toInt()
    val first: LocalDate = LocalDate.parse(root["first"] as String)
    val last: LocalDate = LocalDate.parse(root["last"] as String)
    val events: Map<String, Any?> = root["events"] as Map<String, Any?>
    val units: Map<String, Any?>? = root["units"] as? Map<String, Any?>
    val cautious: Boolean get() = (root["class"] as String).startsWith("C")

    fun unitEvents(unitId: String): Map<String, Any?>? = (units?.get(unitId) as? Map<String, Any?>)?.get("events") as? Map<String, Any?>
    fun unitBroken(unitId: String): Int = ((units?.get(unitId) as? Map<String, Any?>)?.get("broken") as? Long ?: 0L).toInt()

    /** Early starts and late ends over every event: both 0 on a green stamp. */
    fun early(): Int = events.values.sumOf { ((it as Map<String, Any?>)["early"] as? Long ?: 0L).toInt() }
    fun lateEnds(): Int = events.values.sumOf { ((it as Map<String, Any?>)["lateEnd"] as? Long ?: 0L).toInt() }

    /** The worst lateness over the starts of [events] (the app's `measuredStartsWorst`), or null when [events] lacks a start [entryEvents] has. */
    fun worstStarts(events: Map<String, Any?>, entryEvents: Map<String, Any?> = this.events): Int? {
        fun starts(keys: Set<String>) = keys.filter { it in STARTS }.map { if (it.startsWith("asr")) "asr" else it }.toSet()
        if (!starts(events.keys).containsAll(starts(entryEvents.keys))) return null
        return events.entries.filter { it.key in STARTS }.mapNotNull { ((it.value as Map<String, Any?>)["worstLate"] as? Long)?.toInt() }.maxOrNull()
    }

    /** The late histogram of [event] in [events] as shares of its own checked count ("0", "1", "2", "3+"), four decimals. */
    fun shares(events: Map<String, Any?>, event: String): Map<String, Double> {
        val stats = events[event] as Map<String, Any?>
        val late = stats["late"] as Map<String, Any?>
        val total = late.values.sumOf { (it as Long).toDouble() }.takeIf { it > 0 } ?: return emptyMap()
        return late.mapValues { Math.round((it.value as Long) / total * 10_000) / 10_000.0 }
    }

    companion object {
        val STARTS = setOf("fajr", "dhuhr", "asr", "asrStandard", "asrHanafi", "maghrib", "isha")
        fun load(dir: File): Map<String, Stamp> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .map { Stamp(Json.parse(it.readText()) as Map<String, Any?>) }.associateBy { it.entry }
    }
}

sealed interface Verdict {
    /** [events] is the row the figures come from: the unit's where the stamp has units, else the entry's. */
    data class Published(val stamp: Stamp, val events: Map<String, Any?>, val atMost: Int) : Verdict
    data class Held(val reason: String) : Verdict
}

/** Spec §2 (ruling R101): a city has a page only where the stamps prove every day it shows. */
object Proven {
    fun verdict(resolution: Resolution, stamps: Map<String, Stamp>, firstShown: LocalDate, lastShown: LocalDate): Verdict {
        val cls = resolution.entryClass
        if (cls != EntryClass.A && cls != EntryClass.B && cls != EntryClass.C) return Verdict.Held("class ${cls.name} is not proven")
        if (!resolution.measured) return Verdict.Held("not measured at this place")
        val stamp = stamps[resolution.entry.id] ?: return Verdict.Held("no stamp for ${resolution.entry.id}")
        if (stamp.broken != 0) return Verdict.Held("${stamp.entry}'s stamp is red")
        val events = if (stamp.units != null) {
            val unitId = resolution.unitId ?: return Verdict.Held("${stamp.entry} is checked by unit and this place is in none")
            val unit = stamp.unitEvents(unitId) ?: return Verdict.Held("unit $unitId is not in ${stamp.entry}'s stamp")
            if (stamp.unitBroken(unitId) != 0) return Verdict.Held("unit $unitId is red")
            unit
        } else stamp.events
        if (stamp.first > firstShown || stamp.last < lastShown) {
            return Verdict.Held("${stamp.entry}'s stamp covers ${stamp.first}..${stamp.last}; the page shows $firstShown..$lastShown")
        }
        val atMost = stamp.worstStarts(events) ?: return Verdict.Held("no start is measured at this unit")
        return Verdict.Published(stamp, events, atMost)
    }
}

/** The totals the "How Taqwa checks" page prints (spec §5), read from the files, never typed. */
object ProofTotals {
    fun gateRows(official: File): Int = (official.resolve("gate").listFiles { f -> f.extension == "tsv" } ?: emptyArray())
        .sumOf { file -> file.readLines().drop(1).count { it.isNotBlank() && !it.startsWith("#") } }
    fun surveyCalendars(official: File): Int = (official.resolve("survey").listFiles { f -> f.isDirectory } ?: emptyArray())
        .sumOf { dir -> dir.resolve("calendars.tsv").takeIf { it.isFile }?.readLines()?.drop(1)?.count { it.isNotBlank() && !it.startsWith("#") } ?: 0 }
}
```

  Check the gate files' first line is the header (`head -1 tools/timetables/official/gate/sg-muis.tsv`); if a file starts with comment lines before its header, count `lines.filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)` instead. Expected tonight: 588 rows, 125 calendars (`python3` over the same files gives these; assert both in `ProvenTest`).

- [ ] **Step 2: Run the tests to see them fail, then pass.** `CI=true JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew --no-daemon -p tools/timetables jvmTest --tests world.taqwa.timetables.ProvenTest`.

- [ ] **Step 3: `MethodWords.kt` — the description in the app's words.** Test first (`MethodWordsTest.kt`): London's English description equals exactly
  `dawn (Fajr) at a twilight angle that changes through the year, between 11.9° and 16.3° below the horizon, nightfall (Isha) at a twilight angle that changes through the year, between 8.6° and 15.6° below the horizon, its own minutes (Sunrise −3, Dhuhr +5, Maghrib +3), and each start rounded up to the next minute (sunrise and the end of eating down)` and İstanbul's equals `the sun’s position taken once a day, dawn (Fajr) at 18° below the horizon, nightfall (Isha) at 17° below the horizon, its own minutes (Sunrise −7, Dhuhr +5, Asr +4, Maghrib +7), and each start rounded up to the next minute (sunrise and the end of eating down)` (the app's own sentences, `AboutTimesScreen.methodDescription`; the apostrophe in "sun’s" is the app's typographic one — copy it from `strings.xml`). Get the method as `timetable.source(city, date).effective.method!!`. Then:

```kotlin
package world.taqwa.timetables

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.astro.SunModel
import world.taqwa.app.prayer.engine.method.IshaRule
import world.taqwa.app.prayer.engine.method.TimetableMethod

/** `AboutTimesScreen.methodDescription`, without Compose: the same parts, the same strings, the same order. */
object MethodWords {
    private const val DEFAULT_HORIZON_DEG = -0.8333

    fun listComma(language: String) = if (language == "ar" || language == "ur") "، " else ", "

    fun describe(strings: AppStrings, language: String, method: TimetableMethod, f: Formats): String {
        val comma = listComma(language)
        val parts = mutableListOf<String>()
        if (method.sunModel == SunModel.DAILY_0H_UT) parts += strings.get(language, "about_method_daily_sun")
        val fajrCurve = method.fajrAngleByDayOfYear
        parts += if (fajrCurve != null && fajrCurve.min() != fajrCurve.max()) {
            strings.format(language, "about_method_fajr_curve", angle(fajrCurve.min(), f), angle(fajrCurve.max(), f))
        } else strings.format(language, "about_method_fajr_angle", angle(method.fajrAngle, f))
        parts += when (val isha = method.isha) {
            is IshaRule.Angle -> {
                val curve = method.ishaAngleByDayOfYear
                if (curve != null && curve.min() != curve.max()) strings.format(language, "about_method_isha_curve", angle(curve.min(), f), angle(curve.max(), f))
                else strings.format(language, "about_method_isha_angle", angle(isha.degrees, f))
            }
            is IshaRule.AfterMaghrib -> strings.format(language, "about_method_isha_after_maghrib", f.digits(isha.minutes))
        }
        if (method.horizonDeg != DEFAULT_HORIZON_DEG || method.twilightDipDeg != 0.0) parts += strings.get(language, "about_method_horizon_dip")
        val minutes = Prayer.entries.mapNotNull { p -> method.authorityMinutes[p].takeIf { it != 0 }?.let { "${strings.prayer(language, p)} ${signed(it, f)}" } }
        if (minutes.isNotEmpty()) parts += strings.format(language, "about_method_minutes_note", minutes.joinToString(comma))
        parts += strings.get(language, "about_method_rounding_note")
        return parts.joinToString(comma)
    }

    private fun signed(minutes: Int, f: Formats) = (if (minutes < 0) "−" else "+") + f.digits(kotlin.math.abs(minutes))

    /** Whole degrees where whole (18°), else one decimal (19.5°), digits localized, the point literal (the app's `formatAngle`). */
    private fun angle(value: Double, f: Formats): String {
        val tenths = Math.round(kotlin.math.abs(value) * 10).toInt()
        val whole = f.digits(tenths / 10)
        return (if (value < 0) "−" else "") + if (tenths % 10 == 0) whole else "$whole.${f.digits(tenths % 10)}"
    }
}
```

  If `method.authorityMinutes[p]` does not compile, read `EventOffsets` in `shared/.../prayer/engine/method/` for its accessor and use that. If the app's `methodDescription` differs from this port in any branch, the app is right: match it.

- [ ] **Step 4: `Timetable.kt` — the detail fields and the members' days.** `TimetableDay` gains `asrOther: Instant`, `endOfEating: Instant`, `imsak: Instant?`, `sunset: Instant`, `setByRule: Set<Prayer>`, `polar: Boolean`, `members: List<List<Instant>>` (empty unless cautious; each seven instants: `fajr, sunrise, dhuhr, asr, maghrib, isha, endOfEating` of that member's own `PrayerDay`) and `capped: Boolean`. In `Timetable.day`: `val day = source.day`; `members = if (source.effective.members.isEmpty()) emptyList() else DayPipeline.members(source.effective, date, TimeZone.of(city.timeZone), source.school).map { listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha, it.endOfEating) }`; `capped = members.any { it[4] > day.maghrib }`. Keep `setByRule = day.setByRule.isNotEmpty() || day.polar` as the old boolean under a new name `highLatitude` only if `Document` still needs it; otherwise drop it and write the set. Test in `TimetableTest`: Toronto on 2026-10-01 has three members with seven instants each and every member's Fajr ≤ the shown Fajr; London has no members and `asrOther` earlier than `asr` (Hanafi shown, Standard other); `endOfEating ≤ fajr`; `sunset ≤ maghrib`.

- [ ] **Step 5: `Document.kt` — the fields of spec §9.1 and the proven rule.** Constructor `Document(strings, timetable = Timetable(), stamps: Map<String, Stamp>, official: File)`; `build(cities, now)`: for each city compute `months`, `today`, `source = timetable.source(city, today)`, `verdict = Proven.verdict(source.effective, stamps, months.first().days.first().date, months.last().days.last().date)`; held cities go to `"held"` with their reason; published ones are written with the additions. Per city map add `"entryClass" to source.effective.entryClass.name`, `"unitId"`, `"unitName"`, `"proof"` (`placeDays`, `places`, `ramadanDays`, `first`, `through` = `stamp.last`, `atMost` = `verdict.atMost`, `fajrShares` = `stamp.shares(verdict.events, "fajr")`, `cautious` = `entryClass == C`). Per day add the epochs of `asrOther`, `endOfEating`, `sunset`, `imsak` (or null), `"setByRule" to day.setByRule.map { it.ordinal }`, `"polar"`, and for class C `"members"` (epochs) and `"capped"`. Per page add `"otherSchool" to strings.school(language, if (source.school == AsrSchool.HANAFI) AsrSchool.STANDARD else AsrSchool.HANAFI)`, `"members" to source.effective.members.map { strings.get(language, it.nameKey) }`, `"strings" to …` (fill exactly as `AboutTimesScreen` fills them: `whoseTitle` = `today_whose_checked_title(authority)` or `timetable_cautious` when C; `notAffiliated` = `about_not_affiliated(authority)`; `checkedThrough` = `about_checked_through(authority, f.longDate(through))` or, when C, `about_cautious_checked_through(f.longDate(through))` (Task 6's string; until it lands, fill `about_checked_through` with the members joined so the build does not fail, and switch in Task 4); `whoPublishes` = `about_who_publishes`; `whoPublishesBody` = `about_who_publishes_body(authority, unitName ?: city)`; `howReproduces`; `methodIntro` = `about_method_intro(authority, MethodWords.describe(…))` (empty for C); `howChecked`; `statDays` = `about_stat_days_at_places(f.digits(places))`; `statNever` = `about_stat_never_before(authority)`; `statMinutes` = `about_stat_minutes_value(f.digits(atMost))`; `statAtMost` = `about_stat_at_most_after`; `cautiousBody` = `about_cautious_body(city, members joined with MethodWords.listComma)`; `maghribCap` = `about_cautious_maghrib_cap(followedName, laterNames)` for the built day when `capped` (the followed member is the first whose Maghrib equals the shown, else the first member; the later ones those whose Maghrib is after the shown), else ""; `whichDecides`; `matchMosque` = `timetable_match_mosque`; `setByRule` = `today_set_by_rule`; `polarLine` = `today_polar_line`; `stopEating` = `about_stop_eating` with `%1$s` replaced by `{time}`). Per page day add the clock strings of `asrOther`, `endOfEating`, `imsak`, `"setByRule"` as localized names, `"members"` clocks for C. Digits for `placeDays` go through `f.digits` (the app's `localizedDigits`; the thousands separator is the app's: check `PlatformFormat.localizedDigits` — if the app groups thousands ("4,015"), match it with `Formats.distance`'s grouping rule; the tile must read as the app's). Document level: `"proof"` totals from all stamps (`entries`, sums, `early()`/`lateEnds()` sums, `brokenStamps`, `tables` = `ProofTotals.gateRows(official)`, `surveyCalendars`) and `"published"` rows (one per distinct entry of the published cities, sorted by id, `atMost` = `stamp.worstStarts(stamp.events)`, `names` = `strings.timetable(lang, entry)` for every site language, or `timetable_cautious` for C, and `throughText` = the stamp's `last` as `Formats(lang, country).longDate` per site language, the country being that of the entry's first published city), `"held"`. The existing "no name yet" check stays.

- [ ] **Step 6: `Main.kt` and `build.gradle.kts`.** `Main` builds `Stamp.load(File(app, "../../../../tools/timetables/official/stamps"))` — no: take a fourth option `--official <dir>` (default `tools/timetables/official` under the working directory, which `generate` sets to the repo root) and pass it; refuse a `cities.tsv` slug `how-taqwa-checks` (`usage("the slug how-taqwa-checks is reserved for the checks page")`); after writing, print `::notice::{slug}: held — {reason}` per held city and, per published city whose stamp `last` is before the last day of the month after the two shown, `::warning::{slug}: {entry}'s stamp ends {last}; the page after next month would be held — run the gate on the new table`; the final line reads `Wrote N cities (M held) and P pages …`. In `build.gradle.kts`'s `generate` task add `inputs.files(fileTree(repoRoot.resolve("tools/timetables/official")) { include("stamps/*.json", "gate/*.tsv", "survey/**/*.tsv") })` and the `--official` argument. `DocumentTest` gains: London published with `proof.atMost == 5` and `fajrShares` of four keys; Tripoli absent from `cities` and present in `held`; the page's `strings.whoseTitle == "London Unified timetable"`; `proof.published` has a `gb.london.lupt` row with English name "London Unified".

- [ ] **Step 7: Run everything.** `CI=true JAVA_HOME=… ./gradlew --no-daemon -p tools/timetables jvmTest` green; `JAVA_HOME=… ./gradlew --no-daemon -p tools/timetables generate` prints the notices (expect 158 held of the 196 rows once Task 5 un-holds the list; tonight, with every row still held, it prints 0 cities — run it once against a scratchpad copy of `cities.tsv` with the `# ` removed from the London, Toronto, Tripoli and Cairo rows: `-Pcities=<abs path>` `-Pout=<scratch>/probe.json`, and read the notices: Tripoli and Cairo held, London and Toronto written). `python3 site/build.py --check` still passes with zero cities (the section stays off until Task 5).

- [ ] **Step 8: Commit** — `timetables: the stamps' figures, the detailed fields, the cautious members and the proven rule` (message via `git commit -F`, no trailer).

---

### Task 2: The city page — tokens, the Today card, the folded explainer, the app section

**Files:**
- Create: `site/stores.py`
- Modify: `site/build.py` (`load_stores`, `store_block`, `app_banner` move to `stores.py`; a meta-key parity check and the reserved slug in `check_site`)
- Modify: `site/timetables.py` (`city_body`, `today_card`, new `whence`, `app_section`, `after`; `check_page`)
- Modify: `site/assets/style.css`, `site/assets/timetable.js` (the Today card's compact head only)
- Modify: `site/pages/{en,ar,fr,tr,id,ur,bn}/meta.json`
- Modify: `docs/STORE-CHECKLIST.md` (§1 store-badges bullet, §4 site bullet)

**Interfaces:**
- Consumes: the document of Task 1 (`entryClass`, `proof`, `pages[lang].strings`, `pages[lang].members`, days' `sunset`/`setByRule`/`polar`).
- Produces: `stores.block(cfg, lang, *, beta_anchor: bool) -> str` (the hero passes `beta_anchor='id="beta"' in body`, the city page `False`), `stores.app_banner() -> str`, `stores.STORES`; the page markup of spec §3 with these ids/classes: `section.today` (`.today-head`, `.ring-box`, `.ring-text` with `[data-tt=label|count|at]`, `ol.tl li[data-p]`, `.today-foot`, `p.tt-stale`), `.qibla-line`, `details.whence#about` (`summary` → `.whence-line`, `.whence-proof`, `.whence-label`; body `ol.steps`, `.tiles`, `figure.ruler`, `a.more`), `section.months` (empty placeholder this task: the existing month sections stay until Task 3), `section.app#app`, `section.after`; CSS token `--accent-text`; `meta.json` keys of spec §3.6 except the table/print/cautious/checks ones (Tasks 3–4).

- [ ] **Step 1: `site/stores.py`.** Move `STORE_ADDRESSES`, `load_stores`, the badge tables, `store_block` and `app_banner` from `build.py` into it unchanged in behaviour, with `block(cfg, lang, *, beta_anchor)`: badges per live store, the beta pill only when `beta_anchor`, then the `.soon` line (`coming_*` + `source`). `build.py` imports it (`from stores import STORES, block as store_block, app_banner`) and `timetables.py` too. `python3 site/build.py --check` passes unchanged (diff the built home pages before and after: `diff -r` of two `_site` copies is empty).

- [ ] **Step 2: Tokens and the new components in `style.css`.** Add `--accent-text: #8A6407` under `:root` and `--accent-text: #F0B429` in the dark block; change `a { color: var(--accent-text) }`, `.tag`, `.ring-label`, `.tl li.now b, .tl li.now .t`, `.tt tr.fri …`, `.tt tr.is-today th.d b`, `.tt tr.fold button b`, `.chips a.all` to `--accent-text`. Then the components (logical properties only — `margin-inline-start`, `padding-inline`, `inset-inline-start`, `text-align: start`):
  - `.today-head` (phone, ≤ 720 px): `display:flex; align-items:center; gap:16px; width:100%`; `.ring-box { width:64px; height:64px }`; `.ring-text { position:static; text-align:start; align-items:flex-start; padding:0 }`; `.ring-count { font-size:30px }`; `.ring-at` stays the bare time (as today; no "at" word, which would need seven translations for nothing). Desktop keeps the 196 px ring with the text inside (existing rules, moved under `@media (min-width: 721px)`).
  - `.tl` six rows (sunrise 15 px, `li.sun`); `.today-foot { display:flex; align-items:center; gap:12px; padding:12px 0 14px; border-top:1px solid var(--hair); width:100% }` with the glyph, `.foot-text` (14 px, first sentence 700) and `a.pill.quiet.small`.
  - `.qibla-line { display:flex; align-items:center; gap:12px; font-size:14px; color:var(--t2) }` with the 40 px dial.
  - `details.whence { border-top:1px solid var(--hair); margin-top:24px }`; `summary { list-style:none; cursor:pointer; padding-block:16px; display:grid; gap:6px }`; `summary::-webkit-details-marker { display:none }`; `.whence-line { font-size:14px; color:var(--t2) } .whence-line b { color:var(--t1) }`; `.whence-proof { font-size:14px; color:var(--t2) }`; `.whence-label { font-weight:800; color:var(--accent-text); display:inline-flex; gap:6px; min-height:44px; align-items:center }`; `details[open] .chev { transform: rotate(90deg) }` (draw the chevron as the site's 12 px stroke SVG; in RTL rotate −90 from a mirrored base: `html[dir=rtl] .chev { transform: scaleX(-1) }`, open `rotate(90deg) scaleX(-1)`).
  - `ol.steps` (B: numbered amber circles on a hairline spine; three columns ≥ 721 px with `grid-template-columns: repeat(3, minmax(0,1fr))`); `.tiles { display:grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap:8px }` `.tile b { font-size:22px; font-weight:800 }` `.tile span { font-size:12px; color:var(--t2) }` `.tile.zero b { color: var(--accent) }`; `figure.ruler svg { width:100%; height:auto }`.
  - `section.app` (the card of spec §3.5; `ul.points li` with the amber check glyph; `.stores` reused); `.city-hero` grid areas for desktop (`"copy today" "qibla today" "pitch today"`), phone order copy → today → qibla, `.pitch` hidden ≤ 720 px; `section.after` chips (existing `.chips`).
- [ ] **Step 3: `timetables.py` — the page.** Rewrite `city_body` to spec §3.1/§3.2 order: hero (`.city-copy`: crumbs, h1, `.city-meta`; `.today`; `.qibla-line`; `.pitch` with `store_block(cfg, lang, beta_anchor=False)`), then `whence(city, lang)`, then the existing month sections (unchanged this task; Task 3 replaces them), then `app_section`, then `after`. `today_card`: six rows (the card lists all six `Prayer` entries; the sunrise `li` gets `class="sun"`; keep the `data-p` ordinals), the set-by-rule `.tag` after the name where `days[today].setByRule` names the prayer (`hidden` otherwise, toggled by the script), the polar line under the list when `polar`, the foot. `whence`: the summary's three lines from `page["strings"]` and the site sentences; the body for checked places (steps 1–3 with the tiles from `proof` and `strings`, the ruler from `proof.fajrShares` via a `ruler_svg(shares, rtl, words)` function; the "How Taqwa checks every city" link and chip are added by Task 4 with the page they point to — do not write them now, the build's dangling-link check would fail); for cautious places write only the summary and the cautious body's first step this task (Task 4 completes it). The ruler: viewBox `0 0 342 100`, the hatched "before" box, four bars whose heights are `48 * share / max(shares)`, labels as percentages (`f"{round(share*100)} %"` — thin no-break space before `%` in French; the page's digits via `digits()`), `role="img"` with `aria-label` = `ruler_alt` filled; for `rtl` compute every x as `342 - x - width` and set `text-anchor` accordingly. Descriptions: `description_checked` / `description_cautious` per class (the `method` placeholder is the authority name). `check_page`: additionally require one `<details class="whence"` and one `<section class="app"` per city page.
- [ ] **Step 4: `build.py` checks.** In `check_site`, before walking: for every language, `set(cfg["timetable"]) == set(langs["en"]["timetable"])` (also the nested `regions`), else `problem(f"meta: {lang} timetable keys differ from English: missing {…}, extra {…}")`; and `problem` if any city slug equals `how-taqwa-checks`. In `store_block` callers nothing else changes.
- [ ] **Step 5: `meta.json` × 7.** Add the keys of spec §3.6 for this task: `description_checked`, `description_cautious`, `reproduced`, `combined`, `not_affiliated_any`, `whence`, `no_copy`, `replays`, `ruler_caption`, `ruler_minute`, `ruler_before`, `ruler_same`, `ruler_one`, `ruler_two`, `ruler_three`, `ruler_none`, `ruler_alt`, `how_checks_link`, `card_foot`, `pitch_title`, `pitch_body`, `app_title`, `app_body`, `app_point_adhan`, `app_point_widgets`, `app_point_qibla`, `app_point_free`, `app_platforms`, `more_times`, `questions`; remove `method`, `asr`, `why`, `note`, `high_latitude`, `cta_label`, `cta_title`, `cta_body`, `description` (keep `cta_button` = "Get Taqwa"). English first, then the six translations in the same commit (the build's parity check fails otherwise).
- [ ] **Step 6: `timetable.js`** — only what the new card needs: the `.today-foot` needs nothing; the compact head reuses the existing `[data-tt]` spans; the set-by-rule tags: `li .tag[data-rule]` toggled per day from `days[i].r` (the generator writes `"r": [ordinals]` into the live data; the polar line likewise). Keep everything else for Task 3.
- [ ] **Step 7: `docs/STORE-CHECKLIST.md`.** In §1's "The site's store badges" bullet append: "The city pages' three app places — the Today card's foot, the desktop pitch and the section `#app` at the foot of every city page (`site/timetables.py`, through `site/stores.py`, the same function as the hero) — switch with the same edit: after the build open one city page and check the badge shows in the pitch and in `#app`." In §4's "Site:" bullet append "and the city pages' app sections with them (no edit there: `site/stores.json` drives both)."
- [ ] **Step 8: Verify.** `CI=true JAVA_HOME=… ./gradlew --no-daemon -p tools/timetables jvmTest`; generate with a scratchpad `cities.tsv` holding the London (`en ur bn ar`) and Toronto rows un-held (`-Pcities=<abs>`); `python3 site/build.py --check`; screenshots per the constraints (light + dark, 390 and 1440, London en and ar). Acceptance: in the 390 × 844 light screenshot every one of the six times of the Today card is inside the image (the Isha row above the bottom edge); the details are closed and the two summary lines read "London Unified timetable · reproduced and checked by Taqwa · Taqwa is not affiliated with London Unified." and "Checked against London Unified's published timetable through 31 December 2026."; for the open state, copy the built page into the scratchpad, add `open` to its `<details>` (never in the source) and screenshot that copy; the app section shows "Coming to Google Play and the App Store. Source on GitHub." and no button; in the Arabic screenshot the ring's arc runs the other way and the chevron points left; the WCAG contrast of `#8A6407` on `#FBFAF7`, `#FFFFFF` and the today tint is ≥ 4.5 (compute with a five-line Python script; expected 5.15, 5.38, 4.83). Then temporarily set both addresses in `site/stores.json` (`https://play.google.com/store/apps/details?id=world.taqwa.app`, `https://apps.apple.com/app/id6814975544`), build, check the two badges appear in the pitch and in `#app` and the Smart App Banner meta appears on the city page, then restore `stores.json` to nulls before committing (`git diff site/stores.json` empty).
- [ ] **Step 9: Commit** — `site: the city page rebuilt on the engine — the Prayer screen card, the folded explainer, the app section`.

---

### Task 3: The months — C's tables, the detailed view, the fold everywhere, print

**Files:**
- Modify: `site/timetables.py` (`month_section`, notes, `check_page`)
- Modify: `site/assets/style.css` (tables, switch, print stylesheet)
- Modify: `site/assets/timetable.js` (current-prayer rule, fold, switch, print)
- Modify: `site/pages/*/meta.json` (7)

**Interfaces:**
- Consumes: days' `asrOther`, `endOfEating`, `imsak`, `sunset`, `setByRule`; pages' `otherSchool`, `strings.setByRule`; live data from `live_data`.
- Produces: `section.months` → `.dv-switch` (`#dv-label`, `button.dv-toggle[role=switch][aria-checked]`), per month `section.month#m-YYYY-MM[aria-labelledby]` with `.month-head` (h2, p, `button.print[data-month]`), `table.tt[data-month]` (`caption.sr`, `thead th[scope=col]`, `tbody tr[data-i]` → `th.d`, `td.h`, six `td.t` each `span.v` + optional `small.dv` + optional `span.tag.dv`), notes; live data keys per day: `t` (six clocks), `o` (other Asr clock), `x` (end of eating clock), `e` (epochs), `s` (sunset epoch), `r` (set-by-rule ordinals), `f`, `full`, `hijri`; `main.dv` when the view is on; `html[data-print]`.

- [ ] **Step 1: The table markup (`month_section`).** Columns Date, Hijri, Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha; each time cell `<td class="t">` (Friday's Dhuhr `<td class="t jm">`): `<span class="v">5:26</span>`, then for Fajr `<small class="dv">{eat_by} {endOfEating}</small>`, for Asr `<small class="dv">{otherSchool} {asrOther}</small>`, and after a set-by-rule time `<span class="tag dv">{strings.setByRule}</span>`. Drop the jump pill and the `other` parameter; the head becomes `.month-head` with the print button `<button type="button" class="print" data-month="m-YYYY-MM" hidden>{print_month}</button>` (the script un-hides it). Notes: the clock change (existing), `note_detailed` (with `{fajr}`, `{asr}`, `{isha}`, `{school}` = `otherSchool`) followed by `note_no_rule` when no shown day has `setByRule`, the Ramadan note (existing); after the last month `note_engine`. `check_page` counts `<td class="t` = 6 per row and requires `<details` (Task 2) and `class="dv-switch"`.
- [ ] **Step 2: CSS.** `.tt thead th` 11 px desktop / 10 px phone (`letter-spacing: .04em`), `--t2`; `.tt td.t small.dv { display:none; font-size:11px; color:var(--t2); line-height:1.3 }` and `main.dv .tt td.t small.dv { display:block }`; `.tag.dv { display:none } main.dv .tag.dv { display:inline-block }`; `.dv-switch { display:flex; justify-content:space-between; align-items:center; gap:14px; padding:14px 0; border-block:1px solid var(--hair) }` with the 60 × 44 switch (track 52 × 32, knob 26; `aria-checked=true` fills the track with `--t1`, the knob moves with `margin-inline-start:auto`); `button.print` as a `.pill.quiet.small` with the printer glyph; the fold row as today but at every width. The print stylesheet at the end of the file:

```css
@media print {
  :root { color-scheme: light; --bg: #fff; --surface: #fff; --t1: #000; --t2: #333; --t3: #555; --hair: #999; --accent: #000; --accent-text: #000; --ring: #000; }
  .top, .langs, .crumbs, .today, .qibla-line, .pitch, .whence, .dv-switch, .app, .after, .foot, .tt-stale, button.print, tr.fold { display: none !important; }
  .city-hero { display: block; padding: 0; }
  .month { break-before: page; border: 0; padding: 12px 0; }
  .month:first-of-type { break-before: auto; }
  .tt-wrap { border: 0; overflow: visible; }
  .tt tr[hidden] { display: table-row; }
  .tt .h { display: table-cell; }
  .tt tr.is-today > * { background: none; }
  html[data-print] .month { display: none; }
  html[data-print] .month[data-print-target] { display: block; }
}
```
  No per-month selectors: the script sets `data-print` on `<html>` and `data-print-target` on the chosen month's section before `window.print()` and removes both on `afterprint`. Keep the `html[dir=rtl]` table rules (`text-align: start` already).
- [ ] **Step 3: `timetable.js`.** (a) The current-prayer rule: in `state(now)`, `current` is the last obligatory prayer whose epoch ≤ now, then `if (current === FAJR && now >= today.e[SUNRISE]) current = null; if (current === ASR && now >= today.s) current = null;` — `s` is the day's sunset epoch (add to `live_data`); the ring's interval is unchanged (previous start → next start). (b) The fold: drop the `matchMedia` guard so it folds at every width (keep `shown < 4` → no fold); the button gets `aria-expanded` and `min-height: 44px`. (c) The switch: `var toggle = document.querySelector(".dv-toggle"); toggle.hidden = false; toggle.addEventListener("click", function () { var on = toggle.getAttribute("aria-checked") !== "true"; toggle.setAttribute("aria-checked", String(on)); document.querySelector("main").classList.toggle("dv", on); });` and the row's `hidden` cleared. (d) Print: for each `button.print`, un-hide, and on click `document.documentElement.setAttribute("data-print", id); section.setAttribute("data-print-target", ""); window.print();` with `window.addEventListener("afterprint", clear)` removing both. (e) `showDay(i)` also writes `days[i].o`/`x` into the small lines and toggles the `.tag.dv` per `r`. Still no request, no storage.
- [ ] **Step 4: `meta.json` × 7:** `detailed_title`, `detailed_body`, `eat_by`, `print_month`, `note_detailed`, `note_no_rule`, `note_engine`.
- [ ] **Step 5: Verify.** Tests and build as before; screenshots (London en light/dark at both widths, ar phone) — the switch off: six plain columns; a second screenshot set of a scratchpad copy of the built page with `class="dv"` added to `<main>` (never in the source) showing the second lines; a print check: `"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --print-to-pdf=<scratch>/london.pdf --no-pdf-header-footer file://…/london-uk/index.html` and read the PDF (the Read tool renders PDFs): two pages, each one month, every day, the Hijri column, no header/app card; then the same for `ar/prayer-times/london-uk/` (Arabic digits and names print) and, once Task 5 lands, `bn`. Console: `--enable-logging=stderr` shows no JS error on load.
- [ ] **Step 6: Commit** — `site: C's month tables, the detailed view, the fold at every width and printing a month`.

---

### Task 4: Cautious pages, the "How Taqwa checks" page and the index line

**Files:**
- Modify: `site/timetables.py` (`whence` cautious body, `today_card` cautious line, `checks_body`, `build_checks`, `index_body`, `check_page`), `site/build.py` (if `write_page` needs a `current` for the checks page: reuse `prayer_times`)
- Modify: `site/assets/style.css` (`.members`, `.decides`, the chips, `.checks` table), `site/assets/timetable.js` (the decides table and the Fajr ruler for today; the cautious line opening the details)
- Modify: `site/pages/*/meta.json` (7)

**Interfaces:**
- Consumes: Task 1's `members`, `capped`, `pages[lang].members`, `strings.cautiousBody/maghribCap/whichDecides/matchMosque/stopEating`, `proof` (document-level) and Task 6's strings through the document (`about_cautious_checked_through`, `about_stat_never_before_any`, `about_stat_at_most_after_earliest`: switch `Document.kt`'s `checkedThrough` for class C to `about_cautious_checked_through` and add `statNeverAny`, `statAtMostEarliest` to `strings` — a small Task 1 follow-up inside this task, with a `DocumentTest` line for Toronto).
- Produces: `/prayer-times/how-taqwa-checks/` per language (`main.wrap.prose.checks`, `SITEMAP` entry with twins, `current="prayer_times"`); the cautious explainer body (`.members`, `figure.ruler.fajr` with `p.ruler-text` fallback, `.decides` rows `[data-p]` with `.chip.on`/`.chip`); live data keys `m` (per day: member clocks, `[[7 strings]]`), `me` (member epochs), `mn` (member names), `cap` (per day boolean), `capText` (the built day's sentence; the script hides it on days not capped and shows it on capped days with the same text — the followed/later names are the document's built-day ones, so on a day with a different split the script recomputes the two lists from `me` and fills `capT` = the sentence template with `%1$s`/`%2$s` placeholders, which the generator also writes).

- [ ] **Step 1: The cautious explainer.** In `whence`, for `entryClass == "C"`: step 1 `cautiousBody` + `<ul class="members">` (name; the first with `<span class="tag">{most_followed}</span>`) + `not_affiliated_any`; step 2 `combine_heading`/`combine_body`, the `maghribCap` sentence (`p.cap`, `hidden` unless `capped` today), `figure.ruler.fajr` holding only `<figcaption>{fajr_ruler_caption}</figcaption><p class="ruler-text">{fajr_ruler_text filled for the built day}</p>` (the script replaces the `p` with the SVG), then `<h4>{whichDecides}</h4>` and the `.decides` rows for the built day (prayer name, shown clock, chips: members whose clock equals the shown are `.chip.on`, others `.chip` with "{name} {clock}"), the legend, `stopEating` for the built day, `match_prompt` + `<a href="#app" class="more">{matchMosque} ›</a>`; step 3 `replays_cautious`, tiles (`statDays`, `statNeverAny`, `statMinutes` / `statAtMostEarliest`), the checks link. The Today card gains `<a class="cautious-line" href="#about">{whoseTitle} ›</a>` (44 px, `--t2`) under the list on cautious pages; the script opens the details on click (`details.open = true`).
- [ ] **Step 2: The script.** `decides(i)`: for the day's `m[i]` and the shown `t[i]`, per prayer p in 0..5 the members whose `m[i][k][p] === t[i][p]` are on; rewrite the chips' classes and texts; `x[i]` (end of eating) into the stop-eating line; `cap[i]` toggles `p.cap`. `fajrRuler(i)`: an SVG `viewBox 0 0 342 104` — the scale from `e0 = min(members' end of eating)` to `shown = t[i][0]` in minutes (epochs `me`), x = 24 + (minute − e0) / span × 288; a hatched box from the earliest member's Fajr to the shown one labelled `fajr_ruler_not_all`; a marker per distinct member Fajr minute with the names joined by " · " (members sharing a minute share a label), the stop-eating marker at `e0` labelled `fajr_ruler_stop`, the shown minute in `--accent` with `fajr_ruler_shown`; `aria-label` from `fajr_ruler_text`. In RTL mirror the x formula (`342 − x`) and use `text-anchor` end/start swapped. Call both from `showDay`. The no-script page keeps the built day's `p.ruler-text`.
- [ ] **Step 3: The checks page.** `build_checks(lang, template, write_page)` writes `index_dir(lang) + "how-taqwa-checks/"` with twins in every language, `meta` from `checks_title`, `checks_og_title`, `checks_description`, body per spec §5 from `doc["proof"]` (numbers through `digits()` with the page's digit set and the language's grouping — reuse `Formats.distance`'s convention by formatting in Python the same way: a comma every three digits, then the digit set), the published table (`table.checks-table` with `th[scope=col]`: `checks_head_timetable`, `_kind`, `_days`, `_places`, `_through`, `_at_most`; kind = `checks_kind_checked` / `checks_kind_cautious`; `through` from the row's `throughText[lang]`), and the two links of §5.6. Register it in `Timetables.build` after the indexes; the index's lede gains `checks_index_line` with the link; `check_page` treats the slug as the checks page (asserts the four totals appear as digits) rather than a city. Every city page's `how_checks_link` and the "How Taqwa checks" chip now point to `../how-taqwa-checks/`.
- [ ] **Step 4: `meta.json` × 7:** `members_intro`, `most_followed`, `combine_heading`, `combine_body`, `decides_legend`, `match_prompt`, `replays_cautious`, `fajr_ruler_caption`, `fajr_ruler_stop`, `fajr_ruler_not_all`, `fajr_ruler_shown`, `fajr_ruler_text`, `begins_at`, `app_point_match`, `app_point_differ`, `checks_nav`, `checks_title`, `checks_og_title`, `checks_description`, `checks_h1`, `checks_lede`, `checks_rule_h`, `checks_rule_body`, `checks_gate_h`, `checks_gate_body`, `checks_survey_body`, `checks_pages_h`, `checks_pages_body`, `checks_head_timetable`, `checks_head_kind`, `checks_head_days`, `checks_head_places`, `checks_head_through`, `checks_head_at_most`, `checks_kind_checked`, `checks_kind_cautious`, `checks_source_h`, `checks_source_body`, `checks_index_line` (English in spec §5; six translations).
- [ ] **Step 5: Verify.** Tests, generate with a scratchpad list holding London and Toronto, build `--check`; screenshots: Toronto en 390 × 844 light with the details opened in a scratchpad copy (members, ruler as text without script — and a second copy with the script run: use `--virtual-time-budget=2000` so the SVG is drawn), the checks page en and ar at 390 and 1440; `--dump-dom` of Toronto's page contains `about_cautious_checked_through`'s English ("Checked against the timetables followed here through 31 December 2026.") and the tiles "1,400", "0", "7 min". Check the checks page's totals equal `python3` counts over the same files (588 tables, 137,835 place-days, 71 timetables, 0, 0, 125 calendars tonight).
- [ ] **Step 6: Commit** — `site: cautious pages say whose times they combine, and How Taqwa checks`.

---

### Task 5: Publish — `cities.tsv`, the launch note, the seven-language sweep, the build log

**Files:**
- Modify: `site/cities.tsv`
- Modify: `docs/BUILD-LOG.md` (a new section at the end)
- Modify: `docs/STORE-CHECKLIST.md` (only if Task 2's note needs the final section names)
- Modify: anything the sweep finds in `site/` (small fixes only; a structural change goes back to its task's owner via the controller)

- [ ] **Step 1: `cities.tsv`.** Rewrite the file's head comment to the new rule (spec §2: "a row is un-held when the stamps prove every day its page shows — class A, B or C, measured at the city, a green stamp for the entry and its unit covering both months; the generator applies the rule on every build and holds a row it rejects, with a notice"). Un-hold exactly the rows of spec §2.1 (delete the leading `# ` of): makkah, madinah, riyadh, jeddah, dammam, taif, tabuk, abha, buraydah; dubai; doha; muscat; tunis, sfax; algiers; istanbul, ankara; singapore; jakarta, bandung, makassar, banda-aceh; kuala-lumpur, george-town, johor-bahru, kota-bharu; bandar-seri-begawan; johannesburg, pretoria, durban; london; paris; sarajevo; tirana; kazan; dublin; oslo; chicago; toronto, mississauga. Replace each country's stale pre-engine reason comment with the current reason from spec §2.2 (one line per held group, e.g. `# held: Egypt — eg.esa's stamp ends 30 Sep 2026; Mansoura, Tanta and Assiut are outside its checked units (class D)`). Keep names, languages and featured columns as they are.
- [ ] **Step 2: Build.** `JAVA_HOME=… ./gradlew --no-daemon -p tools/timetables generate` prints exactly two notices (istanbul-turkiye and ankara-turkiye held for coverage until 1 October) and "Wrote 38 cities (2 held)" — or, run after 21:00 UTC on 30 September (already 1 October in Türkiye, so the pages show October and November), no notice and "Wrote 40 cities (0 held)"; `python3 site/build.py --check` passes and prints the page count (the cities × their languages + 7 indexes + 7 checks pages). Confirm `_site/prayer-times/index.html` lists 38 cities and no held one; `_site/sitemap.xml` has no held city.
- [ ] **Step 3: The sweep.** Screenshots of one city page per language at 390 × 844 light (Makkah ar, Paris fr, İstanbul tr — from a scratch run with `-Pnow=2026-10-01T00:00:00Z`, Jakarta id, London ur, London bn, Toronto en) and Makkah en at 1440 dark; read each: no English leaking into another language's page (grep the built page for the English key values of `meta.json`), the RTL pages mirrored (ring, chevron, table alignment), Bengali digits in the tables and Western digits in the countdown (existing rule), the Makkah page's "The Kaaba is here" line, Fridays marked, the fold present where today ≥ the 4th, the detailed view's second lines under `main.dv` in a scratchpad copy. Print one Arabic and one Bengali month to PDF and read them. Fix small things in place (a wrapped label, a missing logical property); anything larger is reported, not hacked.
- [ ] **Step 4: `docs/BUILD-LOG.md`.** Append "## The city pages, rebuilt on the engine (29 September)": what changed (B with C's tables; the proven rule and the 38/40 counts with the held groups in one paragraph; the folded explainer; the detailed view; printing; How Taqwa checks; the coming-soon app sections; the accent-text token; the app's cautious tiles), the surprises found while building, and the launch note's location. No printed official time anywhere in it.
- [ ] **Step 5: Commit** — `site: 38 proven cities are live again, with How Taqwa checks` (the tsv, the log, the sweep's fixes).

---

### Task 6 (app; runs beside Tasks 1–3, before Task 4): the cautious About screen's proof tiles and sentence (R100)

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AboutTimesScreen.kt` (`AboutTimesUiState.Cautious`, `aboutTimesUiState`, `CautiousContent`)
- Modify: `shared/src/commonMain/composeResources/values/strings.xml` and `values-ar`, `values-fr`, `values-tr`, `values-id`, `values-ur`, `values-bn` (then `python3 tools/sync-indonesian.py` for `values-in`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/settings/AboutTimesUiStateTest.kt`

**Interfaces:**
- Consumes: `ProofStamps.of(entryId)`, `measuredStartsWorst(resolution, stamp)` (already in the file), `StatTilesRow`, `SectionHeading/SectionBody`, `CardDivider`.
- Produces: string keys `about_stat_never_before_any` ("starts before any of them"), `about_stat_at_most_after_earliest` ("at most after the earliest of them"), `about_cautious_checked_through` ("Checked against the timetables followed here through %1$s.") in all seven locales; `AboutTimesUiState.Cautious.stamp: ProofStamp?`.

- [ ] **Step 1: Tests first.** In `AboutTimesUiStateTest`, extend the Toronto test: `assertEquals("ca.toronto", state.stamp?.entryId)` and a new test `cautiousAtMostIsTheStampsWorstStart`: `measuredStartsWorst(state.resolution, state.stamp!!) == 7`. Run `JAVA_HOME=… ./gradlew --no-daemon :shared:testAndroidHostTest --tests world.taqwa.app.feature.settings.AboutTimesUiStateTest` — compilation fails on `stamp`.
- [ ] **Step 2: The state.** `data class Cautious(override val resolution: Resolution, val members: …, val combined: PrayerDay, val stamp: ProofStamp?)`; `aboutTimesUiState` passes `ProofStamps.of(resolution.entry.id)`.
- [ ] **Step 3: The strings.** Add the three keys to `values/strings.xml` after `about_stat_at_most_after` and `about_checked_through`, with a comment `<!-- About, cautious times (ruling R100, site parity): the proof tiles and sentence a cautious place shows. -->`; translations in the six locales (Arabic e.g. `بداية قبل أيٍّ منها` / `أقصى تأخر عن أبكرها` / `قورنت بالجداول المتّبعة هنا حتى %1$s.`; the others in the register of the neighbouring `about_*` strings); typographic apostrophes only; run `python3 tools/sync-indonesian.py` then `scripts/check-strings.sh` (must print `i18n: clean`).
- [ ] **Step 4: The screen.** In `CautiousContent`, after `CautiousTable(...)` and before the Match-my-mosque block:

```kotlin
    val stamp = state.stamp
    val worst = if (stamp != null && state.resolution.measured) measuredStartsWorst(state.resolution, stamp) else null
    if (stamp != null && worst != null) {
        CardDivider()
        SectionHeading(stringResource(Res.string.about_how_checked))
        StatTilesRow(
            tiles = listOf(
                format.localizedDigits(stamp.placeDays) to
                    stringResource(Res.string.about_stat_days_at_places, format.localizedDigits(stamp.places)),
                format.localizedDigits(0) to stringResource(Res.string.about_stat_never_before_any),
                stringResource(Res.string.about_stat_minutes_value, format.localizedDigits(worst)) to
                    stringResource(Res.string.about_stat_at_most_after_earliest),
            ),
        )
        stamp.provenThrough?.let { through ->
            SectionBody(stringResource(Res.string.about_cautious_checked_through, format.longDate(LocalDate.parse(through))))
        }
    }
```
  Add the three imports (`about_stat_never_before_any`, `about_stat_at_most_after_earliest`, `about_cautious_checked_through`).
- [ ] **Step 5: Verify.** `JAVA_HOME=… ./gradlew --no-daemon :shared:testAndroidHostTest --tests world.taqwa.app.feature.settings.AboutTimesUiStateTest` green; `JAVA_HOME=… ./gradlew --no-daemon :shared:compileAndroidMain`; `scripts/check-strings.sh` clean; `CI=true JAVA_HOME=… ./gradlew --no-daemon -p tools/timetables jvmTest --tests world.taqwa.timetables.AppStringsTest` (the generator reads the same file). One screenshot on `emulator-5556` only (boot `Pixel_8_Pro_2 -port 5556` if needed; `ANDROID_SERIAL=emulator-5556`): install the debug build, set the location to Toronto with the mock-location recipe of the toolchain notes, open Settings › Prayer times › About these times, `adb -s emulator-5556 exec-out screencap -p > <scratch>/about-toronto.png`, read it: the three tiles and the sentence under the decides table, nothing else changed. Do not touch `emulator-5554`, the S23 or any iPhone.
- [ ] **Step 6: Commit** — `About: a cautious place shows its proof tiles and sentence, as the site does`.

---

## Self-review against the spec

- §2 proven rule → Task 1 (code), Task 5 (the list). §3.1–3.2 → Task 2 (hero, card, explainer, app, after), Task 3 (months). §3.3 checked body and ruler → Task 2; cautious body and Fajr ruler → Task 4. §3.4 → Task 3. §3.5 and §7 → Task 2 (+ STORE-CHECKLIST). §3.6 keys → Tasks 2, 3, 4 (each its own keys, all seven languages). §4 states: cautious → 4; detailed, fold, print, no-script → 3; RTL, dark → every task's screenshots; stale → existing. §5 → Task 4. §6 → Task 4 (index line) and Task 5 (the strip's count). §8 → Task 2 (token, targets) and 3 (heads). §9.1–9.2 → Task 1; §9.3 → 2–4; §9.4 → Task 1 (inputs). §10 → Task 6. §11 → each task's verification. §12 out of scope: nothing planned.
- Names used across tasks: `Stamp`, `Verdict.Published/Held`, `Proven.verdict`, `ProofTotals.gateRows/surveyCalendars`, `MethodWords.describe/listComma` (Task 1 → Tasks 2, 4 through the document's keys `entryClass`, `proof`, `strings.*`, `members`, `capped`, `otherSchool`); `stores.block/app_banner/STORES` (Task 2 → 4, 5); `main.dv`, `td.t small.dv`, `button.dv-toggle`, `button.print[data-month]`, `html[data-print]` (Task 3 → 5); live data keys `t o x e s r f full hijri m me mn cap` (Task 3 defines `o x s r`, Task 4 adds `m me mn cap`).
