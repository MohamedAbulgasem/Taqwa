# Taqwa Ayah Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A daily-ayah home-screen widget on Android (Glance, bitmap-rendered card) and iOS (WidgetKit), fed by a pool mirror the app writes, rotating through fifty ayahs with no repeats per round, opening the ayah on tap, previewed in Settings.

**Architecture:** `:widgetcore` (linked by the iOS extension) gets the pool references, the rotation maths and the mirror format. `shared` writes the mirror from the Quran database and routes widget taps to the reader. `androidApp` draws the card text to a bitmap with the Hafs face inside the existing Glance card; the iOS extension draws it in SwiftUI with the font bundled.

**Tech Stack:** Kotlin 2.4 / Compose Multiplatform 1.12 / Glance 1.1 / SQLDelight 2.2 / WidgetKit + SwiftUI / xcodeproj gem.

Spec: `docs/superpowers/specs/2026-09-09-taqwa-ayah-widget-design.md` — every task's requirements include it; exact values live there and are quoted here.

## Global Constraints

- Card metrics (spec §2): padding 14 dp; radius 19 dp; Arabic line-height 1.75×, translation 1.45×; translation = primary at 62 % alpha; tertiary = 42 %; footer rule = text at 12 % alpha; footer gap 9 dp + 8 dp (compact: no rule, 6 dp); Arabic auto-fit 28 → 17 sp, translation = Arabic × 0.6 clamped 11.5–15 sp; Arabic never cut; compact footer under 170 dp drawn height.
- Footer content (spec §2): Latin UI `SurahLatin · s:a` at start + Arabic surah name (Hafs 16 sp / 14 sp compact) at end; Arabic UI Arabic name (Hafs 15 sp) at start + `s:a` in Arabic-Indic digits at end.
- Pool (spec §3): the fifty references in the spec's order; ≤ 24 Arabic words; ≤ 245 Saheeh characters.
- Mirror (spec §4): key `ayah_pool`, seed key `ayah_seed`, version 1, U+001E between entries, U+001F between fields, null on malformed.
- Rotation (spec §5): `epochDay` = Hinnant `days_from_civil`; `round = floorDiv(day, size)`, `position = floorMod(day, size)`; Fisher–Yates with SplitMix64 seeded `seed xor (round * 0x9E3779B97F4A7C15)`; boundary swap of `order[0]` with `order[1]` when `order[0] == basePermutation(round − 1).last()`.
- Android (spec §6): cell 4×3 default, min 4×2, unbounded max; `updatePeriodMillis` 21600000; alarm action `world.taqwa.app.AYAH_WIDGET_REFRESH`, request code 0x7A9B, window next local midnight 00:00–00:05, non-waking RTC.
- iOS (spec §7): kind `TaqwaAyahWidget`, families medium + large, URL `taqwa://ayah/<s>/<a>`, font PostScript name `KFGQPCHAFSUthmanicScript-Regula`.
- Strings: every new string in `values/strings.xml` and `values-ar/strings.xml` (Compose) or `res/values` + `res/values-ar` (Android app) or `Localizable.strings` en + ar (iOS extension). Arabic copy formal, short.
- Commit per task, by path, never `git add -A`. Agents test on the emulator / simulator only, never on a physical phone.

---

### Task 1: Pool references and rotation (`:widgetcore`)

**Files:**
- Create: `widgetcore/src/commonMain/kotlin/world/taqwa/app/widget/AyahPool.kt`
- Create: `widgetcore/src/commonMain/kotlin/world/taqwa/app/widget/AyahRotation.kt`
- Test: `widgetcore/src/commonTest/kotlin/world/taqwa/app/widget/AyahPoolTest.kt`
- Test: `widgetcore/src/commonTest/kotlin/world/taqwa/app/widget/AyahRotationTest.kt`

**Interfaces — Produces:**
```kotlin
object AyahPool {
    /** (surah, ayah) in the spec's fixed order. */
    val REFS: List<Pair<Int, Int>>   // 50 entries
}
object AyahRotation {
    fun epochDay(year: Int, month: Int, day: Int): Long
    fun permutation(round: Long, seed: Long, size: Int): List<Int>
    fun indexFor(epochDay: Long, seed: Long, size: Int): Int
}
```

- [ ] **Step 1: Tests first** — `AyahPoolTest`: size 50, no duplicates, surah in 1..114, ayah ≥ 1. `AyahRotationTest`: `epochDay(1970,1,1)==0`, `(2000,3,1)==11017`, `(2026,9,9)==20705`, `(1969,12,31)==-1`; `permutation(r, seed, 50)` is a permutation of `0 until 50` for r in −3..3 and three seeds; `indexFor` over any 50 consecutive days starting at a round boundary covers all 50 exactly once; for 20 rounds and 5 seeds, `indexFor(lastDayOfRound) != indexFor(firstDayOfNextRound)`; `indexFor` is deterministic; size 1 always returns 0; negative days work.
- [ ] **Step 2: Run** `./gradlew :widgetcore:testDebugUnitTest --tests 'world.taqwa.app.widget.AyahRotationTest'` → fails to compile.
- [ ] **Step 3: Implement.** `AyahPool.REFS` = the spec §3 list as `listOf(9 to 51, 65 to 3, …)`. `AyahRotation`:
```kotlin
object AyahRotation {
    fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
    fun permutation(round: Long, seed: Long, size: Int): List<Int> {
        val order = basePermutation(round, seed, size)
        if (size > 1 && order[0] == basePermutation(round - 1, seed, size).last()) {
            val t = order[0]; order[0] = order[1]; order[1] = t
        }
        return order
    }
    fun indexFor(epochDay: Long, seed: Long, size: Int): Int {
        require(size > 0)
        val round = epochDay.floorDiv(size.toLong())
        val position = epochDay.mod(size.toLong()).toInt()
        return permutation(round, seed, size)[position]
    }
    private fun basePermutation(round: Long, seed: Long, size: Int): MutableList<Int> {
        val rng = SplitMix64(seed xor (round * GOLDEN))
        val order = MutableList(size) { it }
        for (i in size - 1 downTo 1) { val j = rng.nextInt(i + 1); val t = order[i]; order[i] = order[j]; order[j] = t }
        return order
    }
    private const val GOLDEN = -7046029254386353131L          // 0x9E3779B97F4A7C15
    private class SplitMix64(private var state: Long) {
        fun next(): Long {
            state += GOLDEN
            var z = state
            z = (z xor (z ushr 30)) * -4658895280553007687L     // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -7723592293110705685L     // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }
        fun nextInt(bound: Int): Int = ((next() ushr 33) % bound).toInt()
    }
}
```
(`floorDiv`/`mod` are Kotlin stdlib, so they work on iOS too.) The boundary rule compares against the *base* permutation's last element, which the swap never touches for size > 2, so the rule is stable.
- [ ] **Step 4: Run** the two test classes → PASS. Also `./gradlew :widgetcore:compileKotlinIosSimulatorArm64`.
- [ ] **Step 5: Commit** `feat(widgetcore): ayah pool references and daily rotation`.

### Task 2: Pool mirror format (`:widgetcore`)

**Files:**
- Create: `widgetcore/src/commonMain/kotlin/world/taqwa/app/widget/AyahPoolMirror.kt`
- Test: `widgetcore/src/commonTest/kotlin/world/taqwa/app/widget/AyahPoolMirrorTest.kt`

**Interfaces — Consumes:** `KeyValueStore`, `AyahRotation`. **Produces:**
```kotlin
data class AyahPoolEntry(val surah: Int, val ayah: Int, val surahLatin: String, val surahArabic: String, val arabic: String, val translation: String)
data class AyahPoolMirror(val languageTag: String, val translationId: String, val translationRtl: Boolean, val entries: List<AyahPoolEntry>) {
    val showsTranslation: Boolean get() = translationId != "none" && entries.any { it.translation.isNotEmpty() }
    fun entryFor(epochDay: Long, seed: Long): AyahPoolEntry? = entries.takeIf { it.isNotEmpty() }?.let { it[AyahRotation.indexFor(epochDay, seed, it.size)] }
    companion object {
        const val KEY = "ayah_pool"; const val SEED_KEY = "ayah_seed"; const val VERSION = 1
        const val ENTRY_SEP = '\u001E'; const val FIELD_SEP = '\u001F'
        fun serialize(m: AyahPoolMirror): String
        fun deserialize(raw: String): AyahPoolMirror?       // null, never throws
        fun read(store: KeyValueStore): AyahPoolMirror?
        fun write(store: KeyValueStore, m: AyahPoolMirror)
        fun seed(store: KeyValueStore): Long?               // parses SEED_KEY
        fun writeSeed(store: KeyValueStore, seed: Long)
    }
}
```
Serialised form: header `"1<FS><languageTag><FS><translationId><FS><0|1>"` then one `<RS>`-separated block per entry `"s<FS>a<FS>latin<FS>arabic<FS>text<FS>translation"` (RS = U+001E, FS = U+001F). Deserialise: split on RS; header must have exactly 4 fields and version `"1"`; each entry exactly 6 fields with integer s/a; any deviation → null. If widgetcore's commonTest has no in-memory `KeyValueStore` double yet, write one in the test file.

- [ ] Tests: round trip with `|`, `;`, `=`, newlines and Arabic in texts; empty translations; `deserialize("")`, garbage, version 2, an entry with 5 fields → null; `entryFor` uses the rotation (compare against `AyahRotation.indexFor`); seed read/write; `seed` on a non-numeric value → null.
- [ ] Implement; run `./gradlew :widgetcore:testDebugUnitTest :widgetcore:compileKotlinIosSimulatorArm64`; commit `feat(widgetcore): ayah pool mirror`.

### Task 3: Writer, launch requests, database queries (`shared`)

**Files:**
- Modify: `shared/src/commonMain/sqldelight/world/taqwa/app/quran/db/Quran.sq` — add
  `ayahByRef: SELECT surah, number, text_uthmani FROM ayah WHERE surah = ? AND number = ?;` and
  `translationByRef: SELECT text FROM ayah_translation WHERE translation_id = ? AND surah = ? AND number = ?;`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/quran/QuranRepository.kt` — `QuranSource` gains `suspend fun ayahText(surah: Int, ayah: Int): String?` and `suspend fun translationText(translationId: String, surah: Int, ayah: Int): String?`; implement; update `shared/src/commonTest/.../FakeQuranSource.kt`.
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/widget/AyahPoolMirrorWriter.kt`:
```kotlin
object AyahPoolMirrorWriter {
    /** Reads the fifty pool ayahs and the [settings] translation, writes the mirror and, once per install, the seed. Returns the mirror written. */
    suspend fun write(store: KeyValueStore, quran: QuranSource, settings: ReadingSettings, languageTag: String, newSeed: () -> Long = { Random.nextLong() }): AyahPoolMirror
}
```
  translation text is `""` when `settings.translationId == ReadingSettings.NO_TRANSLATION` or the row is missing; `translationRtl = settings.translationId.substringBefore('.') in RTL_TRANSLATION_LANGUAGES` (move that set from `AyahCard.kt` into `quran/TranslationLanguages.kt` and reference it from both places); surah names from `quran.surah(n)`; seed written only if `AyahPoolMirror.seed(store) == null`.
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/nav/LaunchRequests.kt`:
```kotlin
object LaunchRequests {
    private val _pendingAyah = MutableStateFlow<Pair<Int, Int>?>(null)
    val pendingAyah: StateFlow<Pair<Int, Int>?> get() = _pendingAyah
    fun openAyah(surah: Int, ayah: Int) { _pendingAyah.value = surah to ayah }
    fun consume() { _pendingAyah.value = null }
}
```
  plus a top-level `fun openAyahFromWidget(surah: Int, ayah: Int) = LaunchRequests.openAyah(surah, ayah)` in `shared/src/iosMain/kotlin/world/taqwa/app/nav/LaunchRequests.ios.kt` so Swift sees `LaunchRequests_iosKt.openAyahFromWidget`.
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`:
  - a `LaunchedEffect(Unit)` that collects `LaunchRequests.pendingAyah`; on a value: read the reading settings once; Mushaf mode → `navigator.push(Screen.Mushaf(container.quranRepository.pageOf(s, a)))`, else `navigator.push(Screen.Reader(s, a))`; then `LaunchRequests.consume()`. Not while onboarding is showing (wait until it is not).
  - a `LaunchedEffect(Unit)` that (1) writes the pool mirror on start, (2) collects `settings.readingSettings(tag).map { it.translationId }.distinctUntilChanged().drop(1)` and rewrites on each change, every write followed by `refreshWidgets()`; work on `Dispatchers.Default`; failures swallowed with `runCatching`. `languageTag` from `LocalPlatformFormat.current.languageTag()` captured at the effect.
- Tests: `shared/src/androidUnitTest/kotlin/world/taqwa/app/widget/AyahPoolDbTest.kt` (real bundled DB the way `QuranRepositoryDbTest` opens it): every `AyahPool.REFS` exists; Arabic words (split on spaces, trimmed) ≤ 24; `en.sahih` length ≤ 245; `AyahPoolMirrorWriterTest` there too: 50 entries in pool order, translations non-empty for en.sahih, empty for NO_TRANSLATION, `translationRtl` true for `ur.junagarhi`, seed written once (a second write keeps it). `shared/src/commonTest/kotlin/world/taqwa/app/nav/LaunchRequestsTest.kt`: openAyah then consume.
- Run `./gradlew :shared:testDebugUnitTest --tests '*AyahPool*' --tests '*LaunchRequests*'` and `:shared:compileKotlinIosSimulatorArm64`; commit `feat(shared): pool mirror writer, launch requests, ayah lookups`.

### Task 4: Android widget

**Files:**
- Create: `androidApp/src/androidMain/kotlin/world/taqwa/app/widget/AyahCardRenderer.kt` — pure `android.graphics`; `Typeface`s via `Typeface.createFromAsset(context.assets, "composeResources/world.taqwa.app.resources/font/<file>")` for `uthmanic_hafs.ttf`, `Manrope-Regular.ttf`, `Manrope-SemiBold.ttf`, cached in a lazy map. API:
```kotlin
class AyahCardInput(val widthPx: Int, val heightPx: Int, val density: Float, val entry: AyahPoolEntry, val arabicUi: Boolean, val translationRtl: Boolean, val showTranslation: Boolean, val textArgb: Int, val accentArgb: Int)
object AyahCardRenderer { fun render(context: Context, input: AyahCardInput): Bitmap }
```
  Algorithm: for arabicSp in 28 downTo 17: build the Arabic `StaticLayout` (`TextDirectionHeuristics.RTL`, `ALIGN_NORMAL`, `SpannableString` with `ForegroundColorSpan(accent)` over the trailing Arabic-Indic digit run, spacing multiplier 1.75), the translation layout if shown (size = clamp(arabicSp×0.6, 11.5, 15), colour text @ 62 %, direction from `translationRtl`, multiplier 1.45), the footer height (compact when heightDp < 170); if the total ≤ inner height → draw and return. If none fit, use 17/11.5 and rebuild the translation with `maxLines` = the remaining lines and `TextUtils.TruncateAt.END`. Translation off → Arabic centred vertically in the space above the footer. Footer per Global Constraints; reference digits Arabic-Indic under `arabicUi` (`WidgetDigits.localize`). Transparent ARGB_8888 bitmap.
- Create: `androidApp/src/androidMain/kotlin/world/taqwa/app/widget/TaqwaAyahGlanceWidget.kt` — `SizeMode.Exact`. In `provideGlance`, before `provideContent`: read the store, mirror and seed; if the mirror is null, write it with `AyahPoolMirrorWriter.write(store, appContainer.quranRepository, ReadingSettings.defaultsFor(tag), tag)` (this mirror changes only on translation change, when the app requests a redraw, so reading it before `provideContent` is fine here — unlike the prayer mirror). Today's entry via `java.time.LocalDate.now()` → `AyahRotation.epochDay(y, m, d)`. Palette via `WidgetPalette.colorsFor` and the `widget_background` key as the prayer widget reads it. Layout: make the prayer widget's private `WidgetCard` an `internal` composable taking the click action, reuse it with `clickable(actionStartActivity<MainActivity>(actionParametersOf(OpenSurahKey to s, OpenAyahKey to a)))`, containing one `Image(ImageProvider(bitmap), null, GlanceModifier.fillMaxSize(), ContentScale.Fit)` where the bitmap is rendered at `LocalSize.current` × density.
- Create: `TaqwaAyahWidgetReceiver` (in `TaqwaWidgetReceivers.kt`) and `AyahWidgetScheduler.kt` + `TaqwaAyahRefreshReceiver` (same shape as `WidgetRefreshScheduler` / `TaqwaWidgetRefreshReceiver`; constants from Global Constraints; next local midnight from `java.time.ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(zone)`). `TaqwaWidgets.updateAyah(context)`; `androidAyahWidgetUpdateHook` next to `androidWidgetUpdateHook` in `shared/src/androidMain/kotlin/world/taqwa/app/widget/WidgetRefresher.android.kt`, set in `TaqwaApplication`, invoked by `refreshWidgets()` and by `SystemEventReceiver` on TIME_SET / TIMEZONE_CHANGED.
- Create: `androidApp/src/androidMain/res/xml/widget_ayah_info.xml` (`minWidth` 250dp, `minHeight` 180dp, `minResizeWidth` 250dp, `minResizeHeight` 110dp, `maxResizeWidth` 0dp, `maxResizeHeight` 0dp, `targetCellWidth` 4, `targetCellHeight` 3, `updatePeriodMillis` 21600000, `resizeMode="horizontal|vertical"`, `widgetCategory="home_screen"`, `description="@string/widget_ayah_description"`, `previewImage="@drawable/widget_ayah_preview"`); strings en («Today's ayah, with its translation.») / ar («آية اليوم مع ترجمتها.»); manifest receivers (`TaqwaAyahWidgetReceiver` with APPWIDGET_UPDATE + meta-data, `TaqwaAyahRefreshReceiver` with the action, both not exported).
- Modify: `MainActivity.kt` — `onCreate` and `onNewIntent`: `getIntExtra("open_surah", 0)` / `"open_ayah"`; both > 0 → `LaunchRequests.openAyah`. The Glance keys are `ActionParameters.Key<Int>("open_surah")` / `("open_ayah")`.
- Preview PNG: once the widget renders on the emulator, crop the 4×3 card from a screenshot to `androidApp/src/androidMain/res/drawable-nodpi/widget_ayah_preview.png` (≤ 300 KB).
- Verify on the emulator (screenshots under the scratchpad, listed in the report): add the widget through the launcher UI with `adb shell input` (long-press home → Widgets → Taqwa → drag), capture 4×3 English light, resized 4×2 and 4×4, Arabic UI (`adb shell cmd locale set-app-locales world.taqwa.app --locales ar-LY`, open the app once so the mirror rewrites, then `--locales ""` at the end), dark (`cmd uimode night yes` / `no`), translation off (switch in the reading sheet), tap → the reader opens on the ayah both cold (force-stop first) and warm. Zero FATAL in logcat.
- Commit `feat(android): ayah widget`.

### Task 5: iOS widget

**Files:**
- Create: `tools/add-widget-font.rb` (xcodeproj gem, idempotent): copies `shared/src/commonMain/composeResources/font/uthmanic_hafs.ttf` to `iosApp/TaqwaWidget/Resources/uthmanic_hafs.ttf`, adds it and the two `Localizable.strings` (below) to the `TaqwaWidget` target's resources build phase and group. Run it once; commit the font copy and the pbxproj.
- Modify: `iosApp/TaqwaWidget/Info.plist` — `UIAppFonts` = `[uthmanic_hafs.ttf]`. `iosApp/iosApp/Info.plist` — `CFBundleURLTypes` with `CFBundleURLSchemes` `[taqwa]`, `CFBundleURLName` `world.taqwa.app`.
- Create: `iosApp/TaqwaWidget/en.lproj/Localizable.strings` and `ar.lproj/Localizable.strings`: `ayah_widget_name` («Ayah of the day» / «آية اليوم»), `ayah_widget_description` («Today's ayah, with its translation.» / «آية اليوم مع ترجمتها.»), `ayah_widget_placeholder` («Open Taqwa once to load today's ayah» / «افتح تقوى مرة لتحميل آية اليوم»).
- Create: `iosApp/TaqwaWidget/TaqwaAyahWidget.swift`: `AyahEntry: TimelineEntry { date, entry: AyahPoolEntry?, showTranslation, translationRtl, arabicUi, background }`; `AyahTimelineProvider` (mirror via `AyahPoolMirror.companion.read(store:)`, seed via `.seed(store:)`, entries for now + the next six local midnights, `.atEnd`; `getSnapshot` in preview with no mirror → a built-in sample for Ar-Ra'd 13:28 with its Saheeh text so the gallery card is real); `TaqwaAyahWidgetView` per spec §2/§7 using the bundle's `taqwaSurface` helper (Arabic in `Font.custom("KFGQPCHAFSUthmanicScript-Regula", size:)` with the digits in the accent via `AttributedString`, `.environment(\.layoutDirection, .rightToLeft)`, `minimumScaleFactor` 0.6; translation system font, `lineLimit` 3 Medium / 8 Large, `minimumScaleFactor` 0.8; footer per Global Constraints; placeholder string when `entry == nil`); `TaqwaAyahWidget: Widget` with `.configurationDisplayName(LocalizedStringKey("ayah_widget_name"))`, `.description(LocalizedStringKey("ayah_widget_description"))`, `.supportedFamilies([.systemMedium, .systemLarge])`, `.widgetURL(URL(string: "taqwa://ayah/\(s)/\(a)"))` on the view. Add to `TaqwaWidgetBundle`.
- Modify: `iosApp/iosApp/iOSApp.swift` — `.onOpenURL { url in … if url.scheme == "taqwa" && url.host == "ayah" and the two path components are Ints → LaunchRequests_iosKt.openAyahFromWidget(surah:ayah:) }`. Extend the hidden `-taqwaWidgetPreview` route in `TaqwaWidgetPreviews.swift` with the ayah medium and large views so they can be screenshotted on the simulator.
- Verify: `./scripts/ios-build.sh` succeeds; on the iPhone 17 Pro simulator, screenshots of the preview route in English and Arabic (`-AppleLanguages "(ar)" -AppleLocale ar_LY`), light and dark (`xcrun simctl ui booted appearance dark`); `xcrun simctl openurl booted "taqwa://ayah/13/28"` opens the reader on the ayah. Commit `feat(ios): ayah widget`.

### Task 6: Settings preview

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AyahWidgetPreview.kt` — `@Composable fun AyahWidgetPreview(background: WidgetBackground, systemIsDark: Boolean, modifier: Modifier = Modifier)`: reads `AyahPoolMirror.read(createWidgetKeyValueStore())` and the seed once (`remember`), picks today's entry (`kotlinx.datetime` local date → `AyahRotation.epochDay`), falls back to the sample (Ar-Ra'd 13:28, its Saheeh text, `translationRtl = false`); draws the 4×3 card as a Compose stand-in inside the same `PreviewWallpaper` frame `WidgetPreview` uses (19 dp radius, palette colours, `mushafFamily()` for the Arabic and the Arabic surah name, Manrope for the rest, the spec footer), aspect 320:236, full width.
- Modify: `AppearanceSettingsScreen.kt` — after the existing preview: `Spacer(14.dp)`, `SectionLabel(stringResource(Res.string.appearance_ayah_widget_preview_label))`, `AyahWidgetPreview(...)`. Strings `appearance_ayah_widget_preview_label` («AYAH WIDGET» / «ودجة الآية») en + ar.
- Verify on the emulator (English + Arabic, light + dark, each background option) with screenshots; `./gradlew :shared:testDebugUnitTest` still green. Commit `feat(settings): ayah widget preview`.

### Task 7: Docs and finish

- `docs/BUILD-LOG.md` entry; `README.md` widget list; spec pointers; memory `taqwa-project.md`.
- Full suite `./scripts/test.sh`; whole-branch review; device round on the S23 and iPhone 12 (controller, not agents); `scripts/bump-version.sh 0.3.0 4`; release build; merge is Mohamed's call.
