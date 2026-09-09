# Taqwa ayah widget

Status: **decided 9 September 2026** with Mohamed (design page reviewed, footer placement chosen, transliteration dropped by Claude's call, settings preview yes, onboarding no). Extends `2026-09-06-taqwa-prayer-times-qibla-design.md` §widgets and `2026-09-07-taqwa-quran-reader-design.md` §2.3 (the ayah card).

## 1. What it is

A home-screen widget that shows one ayah a day, drawn exactly like the reader's ayah card: the Uthmani Arabic in the Hafs face with its roundel number, the translation the reader is set to beneath it (or nothing, when translation is off), and one new line at the bottom naming the surah. The ayah comes from a curated pool of fifty; every ayah in the pool is shown once before any repeats. Tapping the widget opens that ayah in the app.

Non-goals: transliteration on the widget (it does not fit Medium/4×2 and only displaces the translation on Large), a configurable pool, "share" from the widget, notifications, an in-app "ayah of the day" screen, the onboarding page (it stays about the prayer countdown).

## 2. Appearance

The card is the reader's card (spec 2a §2.3) with these fixed values, on both platforms:

- **Padding** 14 dp. **Corner radius** 19 dp, no border (the prayer widget's rule: the launcher clips to its own radius). **Background** the existing widget background setting — System / Light / Dark / Translucent — through `WidgetPalette.colorsFor`, exactly as the prayer widget.
- **Arabic**: `text_uthmani` + `QuranText.MARKER_SEPARATOR` + `QuranText.arabicIndic(ayah)`, Hafs face, right-to-left, start-aligned (right), primary text colour, the digit run in the accent. Line height 1.75×.
- **Translation** (only when `ReadingSettings.translationId != NO_TRANSLATION`): the reader's translation, secondary text colour (primary at 62 % alpha, as `secondaryText()` in the prayer widget), Manrope Regular on Android, the system face on iOS, line height 1.45×, direction from the translation's language (`RTL_TRANSLATION_LANGUAGES`: ar, ur, fa), 8 dp below the Arabic.
- **Footer**, 9 dp below the translation (or the Arabic), a 1 px rule in the text colour at 12 % alpha, then 8 dp, then one row. Amended 10 September 2026: the footer is pinned to the bottom of the card, with the Arabic and translation centred in the space above it; the 9 dp is a minimum that the auto-fit checks, not a fixed offset.
  - Latin UI: start = surah Latin name (Manrope SemiBold 12 sp, primary) + " · " + `surah:ayah` (Manrope Regular 12 sp, tertiary = primary at 42 %); end = surah Arabic name (Hafs 16 sp, primary).
  - Arabic UI (`languageTag` starts with `ar`): start = surah Arabic name (Hafs 15 sp, primary); end = `surah:ayah` in Arabic-Indic digits (12 sp, tertiary). No Latin. Amended 10 September 2026: the digits pass through `WidgetDigits.localize(languageTag)`, so ar-LY/MA/TN/DZ keep Western digits, like the rest of the app.
  - **Compact** footer when the drawn height is under 170 dp (Android 4×2, iOS Medium): no rule, 6 dp gap, Arabic name 14 sp.
- **Auto-fit**: the Arabic size is the largest value from 28 sp down to 17 sp in 1 sp steps at which Arabic + translation + footer fit the drawn height; translation size = (Arabic × 0.6) clamped to 11.5–15 sp. If nothing fits at 17 / 11.5, the translation is clamped to the lines that remain, ellipsised at the end; the Arabic is never cut. With translation off, the Arabic is vertically centred in the space above the footer and may reach 28 sp.
  - Amended 10 September 2026: when even the Arabic alone at 17 sp outgrows the space above the footer, the footer is dropped rather than overdrawn.
- **Placeholder** (iOS only, before the app has ever written the pool): the card with «Open Taqwa once to load today's ayah» / «افتح تقوى مرة لتحميل آية اليوم» in the secondary colour, centred. Android never needs it (§4).

Sizes: Android default cell **4×3** (`targetCellWidth` 4, `targetCellHeight` 3, `minWidth` 250 dp, `minHeight` 180 dp), resizable horizontally and vertically down to 4×2 (`minResizeWidth` 250 dp, `minResizeHeight` 110 dp) and up without a cap (`maxResizeWidth`/`maxResizeHeight` 0). iOS **systemMedium** and **systemLarge**.

## 3. The pool

Fifty ayahs, each complete on its own, at most 24 Arabic words and 245 characters of Saheeh International (what a 4×3 cell holds with the Arabic at ≥ 17 sp), by reference (surah:ayah), in `AyahPool.REFS` in `:widgetcore`, in this order (the order is never shown; it only fixes each ayah's index):

9:51, 65:3, 3:173, 9:129, 39:36, 64:11, 11:6, 39:53, 2:186, 40:60, 4:110, 21:107, 2:153, 2:156, 94:6, 3:139, 29:69, 47:7, 93:5, 13:28, 2:152, 14:7, 33:41, 93:11, 30:21, 50:16, 36:82, 51:56, 67:2, 99:7, 53:39, 18:46, 74:38, 41:34, 49:13, 16:90, 25:63, 31:18, 55:60, 112:1, 20:14, 59:22, 6:162, 1:5, 2:201, 25:74, 3:8, 23:118, 17:82, 16:97.

A database test guards the list: every reference exists, the word and character budgets hold, no duplicates. Changing the pool later is a one-line edit plus that test; the rotation (§5) copes with a changed size because each round's order is derived, not stored.

## 4. Data flow: the pool mirror

Widgets never open the Quran database (the iOS extension cannot; the Android widget should not pay for it on every draw). The app writes a **pool mirror** into the widget `KeyValueStore` (SharedPreferences on Android, the app-group `UserDefaults` on iOS), key `ayah_pool`, and both widgets read only that.

- **Contents** (`AyahPoolMirror` in `:widgetcore`): `version` (1), `languageTag`, `translationId` (or `none`), `translationRtl`, and one entry per pool ayah in `AyahPool.REFS` order: `surah`, `ayah`, `surahLatin`, `surahArabic`, `arabic` (text_uthmani), `translation` (empty when off). Serialised with U+001E between entries and U+001F between fields, so no ayah or translation character can collide with a separator; `deserialize` returns null on any malformed input and never throws. About 30 KB.
- **Seed**: key `ayah_seed`, a random `Long` written once, the first time the mirror is written, and never rewritten; it fixes the rotation order for this install. Amended 10 September 2026: the seed is written before the mirror, not after, so a concurrent widget draw never finds a full pool next to a missing seed.
- **When written** (`AyahPoolMirrorWriter` in `shared`, suspend, off the main thread): on every app start; whenever `ReadingSettings.translationId` changes; whenever the UI language changes (the writer receives the current `languageTag`). Each write is followed by the existing `refreshWidgets()` so both platforms redraw. Reading fifty ayahs needs two new queries in `Quran.sq`: `ayahByRef(surah, number)` and `translationByRef(translationId, surah, number)`. Amended 10 September 2026: the mirror is also rewritten when the UI language changes, driven by an effect keyed on the resource-driven language (the same string resource `isRtlLocale()` reads) rather than the tag captured at first composition, so a language switch without an activity recreation still rewrites the mirror.
- **Android before first launch**: the Glance widget runs in the app process, so when its draw finds no mirror it calls the writer itself (with the default `ReadingSettings.defaultsFor(languageTag)`) and then draws. iOS shows the placeholder until the app has run once.

## 5. Rotation

Pure Kotlin in `:widgetcore`, `AyahRotation`:

- `epochDay(year, month, day)`: days since 1970-01-01 for a proleptic Gregorian civil date (Hinnant's `days_from_civil`), so Android, iOS and the tests agree on what "today" is without a date library in the widget module. "Today" is the device's local calendar date.
- `indexFor(epochDay, seed, size)`: `round = floorDiv(epochDay, size)`, `position = floorMod(epochDay, size)`, `order = permutation(round, seed, size)`, result `order[position]`.
- `permutation(round, seed, size)`: Fisher–Yates over `0 until size` driven by a SplitMix64 generator seeded with `seed xor (round * 0x9E3779B97F4A7C15)`; then, when `size > 1` and `order[0] == permutation(round − 1).last()` (the previous round's own last element — the swap below never changes a last element for size > 2), swap `order[0]` and `order[1]`. That is the "no repeat until all fifty have shown, and never the same ayah two days running" rule.
- Properties the tests pin: every round is a permutation of the pool; consecutive rounds never share the boundary ayah; the function is total for negative epoch days; a fixed (seed, day) always yields the same index.

`AyahPoolMirror.entryFor(epochDay)` = `entries[AyahRotation.indexFor(epochDay, seed, entries.size)]`.

## 6. Android

- `TaqwaAyahWidgetReceiver` / `TaqwaAyahGlanceWidget` (`SizeMode.Exact`), `res/xml/widget_ayah_info.xml` with the §2 sizes, `updatePeriodMillis` 21600000 (6 h, the self-healing floor), `android:description` `@string/widget_ayah_description` («Today's ayah, with its translation.» / «آية اليوم مع ترجمتها.»), `previewImage` `@drawable/widget_ayah_preview` (a PNG cropped from the emulator's own 4×3 render).
- **Drawing**: Glance cannot use custom fonts, so `AyahCardRenderer` (androidMain) draws the card's *text* to a transparent `ARGB_8888` bitmap at the exact `LocalSize` × density, with `Typeface`s loaded from the Compose resource assets (`assets/composeResources/world.taqwa.app.resources/font/uthmanic_hafs.ttf`, `Manrope-Regular.ttf`, `Manrope-SemiBold.ttf`, cached), `StaticLayout` for each block, and `ForegroundColorSpan` for the roundel digits. The Glance tree is the prayer widget's `WidgetCard` (background colour, 19 dp corners, `appWidgetBackground`, clickable) holding one `Image(ImageProvider(bitmap))`. Only text is in the bitmap, so Translucent keeps working.
- **Tap**: `actionStartActivity<MainActivity>(actionParametersOf(OpenSurah to s, OpenAyah to a))`; `MainActivity` reads the two extras in `onCreate` and `onNewIntent` and hands them to `LaunchRequests.openAyah(s, a)` (§8). Amended 10 September 2026: an explicit `Intent` (action `world.taqwa.app.OPEN_AYAH`, data `taqwa://ayah/s/a`, flags `NEW_TASK | CLEAR_TOP | SINGLE_TOP`) replaced this `actionParametersOf` form, which stacked a second `MainActivity` whose dead composition consumed the request; extras are cleared after being recorded.
- **Refresh**: `AyahWidgetScheduler` arms one inexact, non-waking `setWindow(RTC)` alarm for the next local midnight (window 00:00–00:05, request code 0x7A9B, action `world.taqwa.app.AYAH_WIDGET_REFRESH`) from the receiver's `onEnabled`/`onUpdate` and from `TaqwaAyahRefreshReceiver` after each delivery; cancelled in `onDisabled` when no ayah widget remains. `SystemEventReceiver` (TIME_SET, TIMEZONE_CHANGED) and the existing `TaqwaWidgets.updateAll` also redraw it, so a pool rewrite from the app refreshes it immediately. Amended 10 September 2026: the ayah widget is deliberately kept outside `TaqwaWidgets.updateAll`, so the unlock refresh does not redraw it; its actual callers are the midnight alarm, `refreshWidgets()` via `androidAyahWidgetUpdateHook`, TIME_SET/TIMEZONE_CHANGED, and `updatePeriodMillis`, and every one of those redraw paths re-arms the alarm. The rendered bitmap is capped at a quarter of the display's pixels, floored at 500 000 and ceilinged at 1.2 million.

## 7. iOS

- Third widget in `TaqwaWidgetBundle`: `TaqwaAyahWidget`, kind `TaqwaAyahWidget`, `supportedFamilies([.systemMedium, .systemLarge])`, display name «Ayah of the day» / «آية اليوم», description as the Android one (localised through the extension's `Localizable.strings`, en + ar).
- `AyahTimelineProvider`: reads the mirror through the Kotlin `AyahPoolMirror.read(store)` and produces one entry for now and one at each of the next six local midnights (`Calendar.current`), policy `.atEnd`; each entry carries `entryFor(epochDay)` computed by `AyahRotation` for that entry's date. Amended 10 September 2026: the timeline is 7 entries under `.atEnd`, as built; the app reloads timelines whenever the ayah pool mirror changes (the widget-reload dedupe key includes it); the epoch day is computed from a proleptic Gregorian calendar pinned to the current time zone, never `Calendar.current`, which returns Hijri components when the device's own calendar is set to Islamic.
- View: `VStack` — Arabic `Text` from an `AttributedString` (digits in the accent) in `Font.custom("KFGQPCHAFSUthmanicScript-Regula", size:)`, `.environment(\.layoutDirection, .rightToLeft)`, `minimumScaleFactor` 0.6; translation in the system font with `lineLimit` 3 (Medium) / 8 (Large), `minimumScaleFactor` 0.8; the §2 footer. Background through the bundle's existing palette helper. `.widgetURL(URL(string: "taqwa://ayah/\(surah)/\(ayah)"))`. Amended 10 September 2026: the card's internal padding is 0 inside the widget container on iOS 17+ (where WidgetKit's automatic content margins apply) and 14 pt on iOS 16.
- Font: `uthmanic_hafs.ttf` copied to `iosApp/TaqwaWidget/Resources/` and added to the extension target's resources (a `tools/add-widget-font.rb` xcodeproj script, run once, pbxproj committed), `UIAppFonts` in `TaqwaWidget/Info.plist`.
- App: `CFBundleURLTypes` with scheme `taqwa` in `iosApp/Info.plist`; `.onOpenURL` in `iOSApp.swift` parses `taqwa://ayah/<s>/<a>` and calls `LaunchRequestsKt.openAyah(surah:ayah:)`.

## 8. Opening the ayah

`LaunchRequests` (shared, `nav/`): a `MutableStateFlow<Screen?>` `pending` with `openAyah(surah, ayah)` and `consume()`. `App` collects it: when non-null, it pushes `Screen.Reader(surah, ayah)` in Translation mode or `Screen.Mushaf(pageOf(surah, ayah))` in Mushaf mode — the same rule search hits follow — and consumes it. Works cold (the request is set before `App` composes) and warm (the app is already open).

## 9. Settings preview

On the Appearance screen, under the prayer widget preview, a second labelled preview «AYAH WIDGET» / «ودجة الآية»: `AyahWidgetPreview`, a Compose stand-in of the 4×3 card using the same `WidgetPaletteColors`, showing today's entry from the pool mirror when there is one and Ar-Ra'd 13:28 with Saheeh International otherwise, following the background choice live. Onboarding is unchanged. Amended 10 September 2026: the preview's footer follows the pool mirror's `languageTag`, not the device locale; the Arabic size follows word count (≤ 8 words → 22 sp, ≤ 16 → 20 sp, else 18 sp) and the translation size follows its character length.

## 10. Tests

`:widgetcore` commonTest: `AyahRotationTest` (§5 properties, epoch-day cases: 1970-01-01 = 0, 2000-03-01 = 11017, 2026-09-09 = 20705, 1969-12-31 = −1), `AyahPoolMirrorTest` (round trip with separators and newlines in text, null on garbage and on a wrong version), `AyahPoolTest` (fifty, unique, valid ranges). `shared` androidUnitTest: `AyahPoolDbTest` (every ref exists; ≤ 24 words; Saheeh ≤ 245 characters), `AyahPoolMirrorWriterTest` (writes fifty entries with the right translation, empty translations when off, seed written once). `LaunchRequestsTest`. The renderer and both widgets are verified on the emulator/simulator and then on the S23 and the iPhone 12 in English and Arabic, light and dark, 4×2 / 4×3 / 4×4 and Medium / Large, translation on and off, tap-through cold and warm, and a forced date change. Amended 10 September 2026: `AyahCardRenderer` is device-verified only, with no unit test; `nextMidnight` (moved into `shared` as `AyahMidnight.nextMidnight`) gained unit tests covering DST and non-existent local midnights. The compact footer branch, iOS 16 padding fallback, and a forced midnight rollover were not exercised on a device.

## 11. Release

Ships as 0.3.0 (code 4), a minor bump per the versioning rule.
