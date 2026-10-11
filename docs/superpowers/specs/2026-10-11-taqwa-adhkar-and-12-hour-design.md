# Adhkar and the 12-hour clock — design

11 October 2026. Branch `adhkar-12h` off `origin/main` d04d67ed, worktree `.claude/worktrees/adhkar`.
Source: the first Play review ("add a 12-hour time format, and morning and evening supplications"),
the options page Mohamed reviewed on 11 October (https://claude.ai/artifact/8PyqrMEHfzu2CxZxGcyvMe),
and his choices that night:

- The 12-hour clock as designed: a row in Prayer times settings, 24-hour stays the default.
- Adhkar entry point **C**: a second glyph beside the misbaha, the horizon.
- The Adhkar screen in the **Session** direction (one dhikr at a time, the Tasbeeh grammar).
- The list: **only the undisputed items**; so there is no "Authentic only" switch and no grade text.
- Sound: **the Quran items only**, recited by the app's reciters. No du'a audio, no phone voice.
- Reminders and settings as drawn (one toggle in Settings › Notifications after Tahajjud).
- Translations: **in house**, one reviewer per language; the drafts are listed for review.

Standing rules this design obeys: the Quran is never voiced by a phone voice; every third-party
text has its licence recorded verbatim; nothing on the Prayer, Quran or Settings roots changes
beyond the one new glyph; no ads, accounts, streaks, scores or lifetime tallies; commits carry no
`Co-Authored-By`; nothing is pushed by this branch.

## Scope

| # | Part | What ships |
|---|------|------------|
| 1 | 12-hour clock | `ClockFormat` setting, the formatter flag, a Time format row and sheet, every clock surface follows |
| 2 | Adhkar entry | `drawHorizon` glyph in a 44 dp button beside the misbaha, portrait and landscape |
| 3 | Adhkar screen | Morning / Evening sets, pager of pages, tap-to-count on the Tasbeeh engine, progress, done page, reading sheet |
| 4 | Content | 17 undisputed Hisn al-Muslim items as JSON resources, Arabic + English + transliteration from OpenDua (CC BY 4.0), five in-house translations |
| 5 | Quran pages | Text from `quran.db`, the chosen reciter through the player, bundled Alafasy clips for the first run |
| 6 | Reminder | One opt-in notification half an hour after Fajr and after Asr, cancelled once the set is done |
| 7 | Credits, docs, tests, devices | Licence trail, strings in eight folders, unit tests, emulator/simulator pass, build log |

Out of scope: du'a audio of any kind, a phone voice, the contested seven items, after-prayer and
sleep sets, widgets or Live Activities, streaks, a "Follow phone" clock option, a reminder minutes
picker, a per-item source sheet, virtue lines, a per-ayah download cache.

## 1. The 12-hour clock

### 1.1 Model and persistence

- `enum class ClockFormat { TWENTY_FOUR_HOUR, TWELVE_HOUR }` in `world.taqwa.app.i18n`.
- `SettingsKeys.CLOCK_FORMAT = stringPreferencesKey("clock_format")`; `SettingsRepository.clockFormat: Flow<ClockFormat>`
  (absent → `TWENTY_FOUR_HOUR`, read with `toEnumOr`) and `setClockFormat`, on the `themeMode` pattern.
- Default 24-hour for upgrades and fresh installs. Nothing on any screen changes until the user picks 12-hour.

### 1.2 The formatter

`PlatformFormat.clockTime(hour, minute)` stays the one seam. The interface does not change (the
seven test fakes are untouched); the two actuals gain a constructor flag:

```kotlin
expect fun createPlatformFormat(clockFormat: ClockFormat = ClockFormat.TWENTY_FOUR_HOUR): PlatformFormat
```

Twelve-hour rendering, both platforms:

- Hour `h12 = hour % 12`, with 0 → 12; unpadded, as today's 24-hour hour is; minute padded; the
  existing locale digit formatters, so Egyptian Arabic keeps its Arabic-Indic digits and Libyan
  Arabic its Western ones.
- The day-period marker comes from the platform's symbols, never from our strings: Android
  `java.text.DateFormatSymbols.getInstance(locale).amPmStrings`; iOS `NSDateFormatter` `AMSymbol` /
  `PMSymbol` on `NSLocale(localeIdentifier = languageTag())` (language locale, so British English
  gets "pm" and American "PM" from the same interface language).
- Joined with U+00A0 (Manrope has no glyph for the narrow no-break space CLDR uses). Turkish puts
  the marker before the digits ("ÖS 6:32"); every other interface language after ("6:32 PM",
  "٦:٣٢ م", "১২:১০ PM"). The position comes from a one-line table keyed on the language, not from
  a platform pattern (iOS rewrites patterns under the 24-Hour Time switch).
- The pure mapping `twelveHourParts(hour): Pair<Int, Boolean>` and the join rule live in
  `commonMain` with unit tests; the actuals only supply digits and symbols.

### 1.3 Where the setting is read

The four bare `createPlatformFormat()` call sites take the stored value:
`App.kt` (the `remember(uiLanguage)` becomes `remember(uiLanguage, clockFormat)` with the flow
collected beside the language), `NotificationCoordinator.plan` (reads `clockFormat.first()`),
`WidgetMirrorRefresher.refresh` (its default format is built from the setting, or callers pass
one built the same way) and `TodayViewModel.widgetFormat`. Every surface in the pipeline then
follows with no per-surface code: the ring's clock, the timeline rows, the sub-lines, the Timetable
"times today" rows, About these times, Match my mosque's sentences, Manual adjustments, the
notification bodies, both widgets (they draw the strings the app writes into the mirror), and the
Appearance/onboarding widget preview (its sample table gains a 12-hour twin).

### 1.4 The setting's UI

Settings › Prayer times, the last card, between "Show sunrise" and "About these times":
`TaqwaRow(label = Time format, value = 24-hour | 12-hour, onClick)`. The tap opens a
`TaqwaBottomSheet` in the "Remind me before" idiom: title "Time format", one card, two option
rows (`TaqwaRow(selectable, selected, trailing CheckMark)`), each with a subtitle showing the same
sample time, 18:32, rendered by that format ("18:32" / "6:32 PM"; "ÖS 6:32" on a Turkish phone;
"٦:٣٢ م" on an Egyptian one). Picking writes through the screen's `write {}` helper, so
`refreshForNewTimes` re-bakes the notifications and rewrites both widget mirrors as a language
change does today.

Strings (eight Compose folders): `prayer_times_time_format` "Time format", `time_format_24`
"24-hour", `time_format_12` "12-hour".

### 1.5 Widths and follow-ups

- "12:10 PM" is about 28 dp wider than "12:10" at 17 sp Manrope tabular; every plain timeline row
  still fits on a 360 dp phone; pill rows wrap under the name sooner, which the row already does.
- Android Glance: re-derive `LONGEST_ROW_EM` and `TWO_COLUMN_MIN_WIDTH` with the longer time (about
  230 → 255 dp) and verify a 3×2 widget still takes two columns.
- iOS `accessoryRectangular`: check the "Maghrib in" + "12:10 PM" line on a lock screen.
- Notification copy: wrap `{time}` in bidi isolates when splicing, so a Latin marker inside an
  Urdu or Arabic body lays out.
- Mosque-board input needs nothing: the matcher already reasons on a 12-hour dial.

### 1.6 Tests

`commonTest` for `twelveHourParts` and the join/position rule across the seven languages (with a
fake symbol source); the first `androidHostTest` on the real `AndroidPlatformFormat` for en_US,
en_GB, ar_EG, ar_LY, tr, id, bn, ur in both formats; a "5:10 PM" fixture in the widget wire
round-trip test (a space survives the wire); the notification body test with an isolated time.

## 2. Adhkar — the way in

### 2.1 The glyph

`drawHorizon` in `Glyphs.kt`, on the 16-unit grid at `glyphStroke()`: a horizon line from (2, 12)
to (14, 12); a half sun, the upper arc of radius 3.4 centred on (8, 12); three rays, (8, 5.6)→(8, 3.4),
(3.9, 8.2)→(2.5, 6.8) and (12.1, 8.2)→(13.5, 6.8); round caps.

### 2.2 The button

The horizon's `HeaderGlyphButton` beside the misbaha's in `CityAndDates`: the same 44 dp circle
(`HeaderGlyphSize`), 22 dp glyph in `textPrimary`, the same −4 dp nudge (`HeaderGlyphNudge`), no
ripple, `contentDescription = today_open_adhkar` ("Adhkar").
Order at the end of the city's row: the city, then the horizon, then the misbaha. The two buttons
overlay the city's row only (the title keeps 88 dp clear for them); the date line beneath spans the
block's full width, as it did before the second button, so the glyphs never change how the date is
displayed (the owner's rule, 11 October 2026). It does start lower, at the owner's request after an
iPhone pass the same day: the targets reach 40 dp down the block (44 dp, nudged up 4) against a
one-line city of 29 dp in English and 30 in Arabic, so the date line began inside the glyphs'
targets, and on the owner's iPhone the ⓘ at its end sat inside the horizon's, where a tap on it
could open the adhkar. The city's line is held to at least the targets' reach plus 2 dp
(`CityLineMinHeight`), which moves the date down 13 dp in English and 12 in Arabic on the iPhone
and everything below it 11, the block's 48 dp minimum having already held the other 1–2 dp, so the
air between the date and the ring is now the 36 dp the portrait body plans for rather than 38 (37
in Arabic). A two-line city, or a text size from about 1.45× up, already clears the targets and
moves nothing; in between, the minimum adds less the larger the text: 9–10 dp at 1.1×, 3–5 at
1.3×, 0–2 at 1.4×. Both the portrait and landscape bodies carry it; sideways the left pane is
centred, so there the block's 11 dp shows as the city and both glyphs rising 6 dp and the ring
dropping 5. Nothing else on the Prayer screen changes: no line, no card, no count, no tint.

### 2.3 What it opens

`navigator.push(Screen.Adhkar(set = null))`. `null` resolves to the half-day's set from the
location's own day: from Fajr until Asr → morning, from Asr until the next Fajr → evening. The
screen's segmented control reaches the other set.

## 3. The Adhkar screen

Pushed on the Prayer tab like Tasbeeh: full screen, `BackChevron`, no tab bar, no player bar. The
screen keeps the Tasbeeh rules: the Arabic is identical under every interface language;
transliteration and translation appear under a Latin interface, Bengali and Urdu add their
translation, the Arabic interface shows the Arabic with its page label and reference alone.
`KeepScreenOn` while open, as Tasbeeh and the compass do.

### 3.1 Structure, top to bottom (portrait)

1. **Header row**, the Tasbeeh header idiom: `BackChevron`; the title "Morning adhkar" /
   "Evening adhkar" in `rowLabel` ExtraBold with a caption line under it stating the phase
   (3.4); at the end two 44 dp glyph buttons: a speaker (present on Quranic pages only, becomes
   the equaliser while playing, the reader-header shape) and "Aa" (text, 13 sp ExtraBold) opening
   the reading sheet (3.7).
2. 12 dp, then a hugging `TaqwaSegmented(Morning | Evening)` centred. Switching sets never touches
   progress.
3. 12 dp, then the progress row at the gutter: "4 of 18" in caption `textSecondary` and a 3 dp
   `hairline`/`accent` bar (the continue-reading card's bar), pages counted ÷ pages.
4. **The pager**: `HorizontalPager`, one page per item, the three Quls as three pages, mirrored
   under RTL. It opens on the first undone page, or on the done page when the set is complete.
5. **The foot**, 64 dp above the navigation inset: previous chevron (44 dp target, 20 dp canvas,
   `textTertiary`), a 64 dp `RingArc` with the count inside in the `countdown` style at 22 sp,
   shrinking in 0.5 sp steps to fit at large font scales, "of 3" / "of 100" in caption
   `textSecondary` beside it, next chevron. On once-only pages the ring and caption are hidden and
   the slot kept, so nothing jumps. Above the foot, "Tap anywhere to count" (`tasbeeh_hint`) in
   12 sp `textTertiary`, shown only while the session has no count yet.

Landscape: the same column, narrower; the pager fills the height.

### 3.2 A page

A scrolling column at the 24 dp gutter:

- Page label, `sectionLabel` in `accent`, upper-cased by the locale-aware rule: "3 TIMES", "ONCE",
  "10 TIMES", "100 TIMES", "100 TIMES TODAY" (the daily istighfar).
- The Arabic. Du'a text: `FontFamily.Default` (the OS face, Manrope has no Arabic), the reader's
  Arabic size minus 2 sp, 1.8× line height, `letterSpacing = 0`, right-aligned inside an RTL
  layout-direction provider. Quran text: `TaqwaText.quran(size)` from `quran.db` with the roundel
  via `QuranText.withMarker`, exactly as `AyahCard`; above Ayat al-Kursi the istiʿadhah in the OS
  face at size − 6 in `textSecondary`; above each Qul the reader's basmala line. The two faces tell
  the reader what is Quran.
- Transliteration (Latin UIs only, when the reader's transliteration setting is on): caption 13 sp
  italic `textTertiary`, LTR.
- Translation (when the Adhkar translation toggle is on, default on; never under the Arabic UI):
  caption 14 sp, line height 21, `textSecondary`, direction by its language. Quran pages show the
  reader's chosen translation from `quran.db`.
- Reference row, caption 12 `textTertiary`: the collections and numbers ("Abu Dawud 5088 ·
  Tirmidhi 3388"; "Al-Baqarah 2:255"), collection names from strings so they read in each language.

The whole page body is one ripple-less tap surface; a tap counts, a drag scrolls.

### 3.3 Counting and progress

- Each page is a one-part `TasbeehPreset`; a tap runs `TasbeehEngine.tap`: `Tick` → `Haptics.count()`;
  a page's completion fires `Haptics.partComplete()`, and the last undone page's completion fires
  `Haptics.setComplete()` instead, once (the session is the set). The count bumps 1.06× for 120 ms
  and the arc tweens 250 ms (snapped under reduced motion), as Tasbeeh does.
- At the target the page waits about 400 ms and advances to the next undone page. Taps on a
  completed page do nothing. Swiping past a page leaves it undone: **progress is counted, never
  visited**, so "done" is true when it shows.
- Every tap is written at once, once the set is open (below; the istighfar always): `adhkar_morning`
  / `adhkar_evening` = `"<date>|c1,c2,…"` (counts in page order) and `adhkar_istighfar` =
  `"<date>|n"` (one counter shared by both sets, keyed by the day's own date, the local date of its
  Fajr, like the sets). A set's date is the local date of its own prayer (that Fajr, that Asr); a
  new date overwrites, nothing is kept, no history.
- **A set counts from its opening prayer** (the Lead's ruling, 11 Oct, after the device pass saw an
  evening set finished at 07:12 withdraw that day's 16:55 reminder): the morning from Fajr, the
  evening from Asr, of the day `dayFor` reads against; the closing is unchanged, the make-up phases
  still count. Before it — only ever the evening, between Fajr and Asr, since `dayFor` never gives
  a day whose Fajr is ahead — the set is a free read: the pager, counting, ring, advance and
  haptics work, nothing of the set is written, no done mark, no reminder withdrawn, and leaving
  (or switching sets) starts it again from the first page. The header caption says "From Asr ·
  16:55" (`adhkar_opens_at`, the time through `formatClock` and `isolatedClock`), and a free read
  finished shows its done page with that caption in place of "Evening adhkar · done". The open
  check is made per tap: the first tap after Asr writes the whole of a read begun before it and
  from then on it is the day's set; a read finished before Asr has no tap left and stays a free
  read. The istighfar keeps its day count either way.
- **Done page**: after the last page, a page with a 44 dp accent-0.18 halo holding the 20 dp
  `CheckMark`, "Morning adhkar · done" in `rowLabel`, a caption naming when the other set opens
  ("Evening adhkar open after Asr"), and `TaqwaTextLink("Back to prayer times")` that pops. The
  firmer double tick plays once when the last page completes. No streak, total or share.

### 3.4 Windows and phases

Derived from the location's `DayPrayerTimes` for the day (always computed, whatever "Show sunrise"
says), in the location's zone:

- Morning: best from Fajr until sunrise; make-up from sunrise until Dhuhr.
- Evening: best from Asr until the **shown** Maghrib; make-up from Maghrib until Isha.

The header caption says the phase through `formatClock` (so it follows the clock setting): "Until
sunrise · 6:12", "After sunrise · until 12:46", "Until Maghrib · 18:37", "After Maghrib · until
19:56", and outside both windows the plain window name ("Fajr to sunrise", "Asr to Maghrib") —
except before the set opens, when a free read says when it does ("From Asr · 16:55", 3.3).
Nothing locks: a set opened at any hour reads as usual, but before its opening prayer it is a free
read that keeps nothing (3.3). On a polar day without a sunrise the morning window runs to Dhuhr.

### 3.5 Quran pages and the reciter

- Items: Ayat al-Kursi (2:255), Al-Ikhlas, Al-Falaq, An-Nas. The text and the chosen translation
  come from `quran.db` at runtime by reference; the JSON never carries a copy of the Quran.
- Play: the speaker in the header plays the page's ayah or surah with the reciter chosen in
  Settings › Recitation, through the existing player, with `NowPlayingText("Morning adhkar",
  "Ayat al-Kursi · Al-Baqarah 255")` on the lock screen. New: an ayah-subset load — the common
  `load` gains an optional `ayahs: List<Int>` that Android turns into
  `RecitationQueue(surah, ayahs, gapMs, spoken = null)` and iOS into a filtered split — and the
  controller's "next surah on `surahEnds`" advance is suppressed for this session. Playback stops
  when the page or the screen is left. Audio never counts.
- Download: when the surah is not downloaded for the chosen reciter, the existing `DownloadSheet`
  offers it (Al-Baqarah is 58 MB for Alafasy; the Quls are tiny).
- First run: the four Quranic items are bundled for the default reciter (Alafasy) as clips cut
  from his own `.taqa` containers (2:255 ≈ 0.5 MB; 112, 113, 114 tiny), under
  `composeResources/files/adhkar/clips/ar.alafasy/<surah>-<ayah>.mp3`, produced by a script in
  `tools/quran-audio` beside the previews and checked like them. The clip plays through
  `ClipPlayer` only when Alafasy is the chosen reciter and the surah is not downloaded; a reader
  who chose another reciter never hears a voice they did not pick.
- Screen readers: Quranic blocks `clearAndSetSemantics` to their reference line and are never
  voiced; the reciter is their voice. Du'a Arabic carries an `ar` locale span.

### 3.6 Reading sheet ("Aa")

A `TaqwaBottomSheet` titled "Reading", built from `ReadingSheet` parts: the Arabic size slider on
the reader's own key (one knob for the whole app, with the live preview line showing a du'a
fragment), the Transliteration toggle on the reader's key (Latin UIs only), and a Translation
toggle on a new key `adhkar_translation` (default on; absent under the Arabic UI). No voice rows:
the Quran reciter is chosen in Settings › Recitation, and there is no du'a audio.

### 3.7 Accessibility and RTL

- Each page is one semantic group reading "{page label}. {translation, or the Arabic under an
  Arabic UI}. {reference}"; the count surface exposes a "Count" custom action; the count is a
  polite live region ("3 of 7"); previous, next, speaker and Aa are labelled.
- Arabic UI: the pager, the chevrons and the ring's sweep mirror; Urdu adds Urdu (RTL); Bengali
  adds Bengali (LTR); no transliteration under non-Latin UIs; digits through `localizedDigits`;
  the Turkish "i" through `Casing`.
- 200 % font: the count shrinks, the page scrolls, nothing clips. The iOS back handler stays
  VoiceOver-gated (the a11y round's rule); the chevron is the way out.

## 4. Content

### 4.1 The items

Hisn al-Muslim's morning-and-evening chapter (sunnah.com `hisn:75`–`98`), **the undisputed items
only** in the book's order. Hisn numbers in brackets; counts as verified against the primary
collections (R1 of the research notes):

| Page | Hisn | Opening words | Count | Sets | Sources |
|------|------|---------------|-------|------|---------|
| 1 | 75 | Ayat al-Kursi (Quran 2:255, with the istiʿadhah) | 1× | both | an-Nasa'i, al-Hakim; Sahih at-Targhib |
| 2–4 | 76 | Al-Ikhlas, Al-Falaq, An-Nas (Quran 112, 113, 114) | 3× each | both | Abu Dawud 5082, Tirmidhi 3575 |
| 5 | 77 | أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ … | 1× | both, evening variant | Muslim 2723 |
| 6 | 78 | اللَّهُمَّ بِكَ أَصْبَحْنَا … | 1× | both, evening variant | Abu Dawud 5068, Tirmidhi 3391, Ibn Majah 3868 |
| 7 | 79 | اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلاَّ أَنْتَ … (Sayyid al-Istighfar) | 1× | both | Bukhari 6306 |
| 8 | 84 | اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ … | 1× | both | Abu Dawud 5074, Ibn Majah 3871 |
| 9 | 85 | اللَّهُمَّ عَالِمَ الغَيْبِ وَالشَّهَادَةِ … | 1× | both | Tirmidhi 3392, Abu Dawud 5067 |
| 10 | 86 | بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ … | 3× | both | Abu Dawud 5088, Tirmidhi 3388, Ibn Majah 3869 |
| 11 | 88 | يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغيثُ … | 1× | both | al-Hakim 1/545; Sahih at-Targhib 661 |
| 12 | 90 | أَصْبَحْنا عَلَى فِطْرَةِ الْإِسْلاَمِ … | 1× | morning only | Ahmad, an-Nasa'i al-Kubra; as-Silsilah as-Sahihah 2989 |
| 13 | 91 | سُبْحَانَ اللَّهِ وَبِحَمْدِهِ | 100× | both | Muslim 2692 |
| 14 | 92 | لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ … | 10× | both | an-Nasa'i (ʿAmal al-Yawm), Abu Dawud 5077 |
| 15 | 93 | the same tahlil | 100× | morning only | Bukhari 6403, Muslim 2691 |
| 16 | 94 | سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ … | 3× | morning only | Muslim 2726 |
| 17 | 95 | اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْماً نَافِعاً … | 1× | morning only | Ibn Majah 925 |
| 18 | 96 | أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ | 100× a day, one shared counter | both | Bukhari 6307, Muslim 2702 |
| 19 | 97 | أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ | 3× | evening only | Abu Dawud 3898, Ibn Majah 3518 |

Morning: 18 pages (1–18). Evening: 15 pages (1–11, 13, 14, 18, 19). Not shipped (contested
grading): Hisn 80, 81, 82, 83, 87, 89 and 98, and the evening form of 90.

Wording follows Hisn's print. Evening variants (أمسينا…) exist for pages 5 and 6; page 6 keeps
Hisn's النشور in the morning and المصير in the evening.

### 4.2 Sources and licences

- Arabic, counts, order, variants: the OpenDua catalogue v0.0.4 (Human Development Fund / Ihsan
  Labs, CC BY 4.0 for its structure and editorial text; it claims nothing over the religious text),
  checked word by word against the hisnmuslim.com chapter text and the printed Hisn before it
  ships. English translation and the ALA-LC-like transliteration (ʿ, ā) from OpenDua, read through.
- Quran items: `quran.db` by reference (Tanzil text, the app's licensed translations and
  transliteration).
- Bengali, French, Indonesian, Turkish, Urdu: no licensed source exists. In-house translations from
  the Arabic, listed in the report for one reviewer per language; a language's file ships only
  when its translations are present and reviewed, else that language shows Arabic, transliteration
  (Latin UIs) and the Quran pages' existing translation.
- Credit "Based on Hisn al-Muslim by Saʿid ibn ʿAli ibn Wahf al-Qahtani" with the OpenDua
  attribution string, verbatim, in `docs/licences/OpenDua-CC-BY-4.0.txt` (dated copy of the
  developers page), a bullet in `docs/ATTRIBUTION.md`, a clause in `NOTICE`, the README credits,
  `credit_adhkar` / `credit_adhkar_detail` in eight folders and a `Credit(…, source = "opendua.org")`
  on the Attribution screen. The clips carry the reciters' existing credit.
- Not used: sunnah.com text or API, hisnmuslim.com's JSON as a shipped source, "no licence" GitHub
  scrapes, GPL app databases.

### 4.3 Data shape

Compose resources, not a database: `files/adhkar/items.json` — per item `id`, `hisn`, `kind`
(`dua` | `quran`), `sets` (`morning`, `evening`), `count`, `countDaily` (the istighfar), `quran`
(`surah`, `ayah?`, `prefix`: `istiadhah` | `basmala` | none), `arabic` (`morning`, `evening`
variants or one text), `transliteration` (same shape), `sources` (list of `{collection, number}`) —
and `files/adhkar/text/<lang>.json` for `en`, `bn`, `fr`, `id`, `tr`, `ur` (per item a translation
pair). Parsed once with `ignoreUnknownKeys`, like the audio manifest. A `commonTest` checks the
structure (every shipped language has every du'a id, no empty strings, counts and sets as the
table, 18 morning and 15 evening pages); an `androidHostTest` resolves every Quran reference
against the bundled `quran.db`.

## 5. Reminder

- Settings › Notifications, a card after Tahajjud, built like it: `TaqwaRow("Morning and evening
  adhkar", subtitle "Half an hour after Fajr and Asr", toggle)`; `CardDivider`; the Sound row
  with Tahajjud's sheet while on. Off by default. `NotificationSettings.adhkar` and `adhkarSound`.
- Planner: `NotificationKind.ADHKAR_MORNING` at Fajr + 30 min clamped to sunrise − 5 min, and
  `ADHKAR_EVENING` at Asr + 30 min clamped to Maghrib − 5 min; ids `ADHKAR-MORNING-<date>` /
  `ADHKAR-EVENING-<date>`; `windowDaysFor` counts them. The plan is filled prayers first, then
  before-prayer reminders, then Tahajjud, then adhkar, each in time order, so an adhkar entry never
  displaces a prayer on iOS's 64 slots.
- Android: inexact alarms for adhkar kinds (`setAndAllowWhileIdle`), channel `adhkar_<sound>` with
  `allAdhkarChannelIds()` so channels stay deletable. Copy: new `Words` fields in all seven
  languages ("Morning adhkar" / "Until sunrise · {time}", "Evening adhkar" / "Until Maghrib ·
  {time}"), `{time}` isolated.
- Done → no nag: when a set completes, the day's pending adhkar notification is cancelled by id on
  both schedulers (`NotificationScheduler.cancel(ids)`). A set finished before it opens (3.3) is
  not the day's set and does not withdraw its reminder.
- Tap: Android content intent carries `open_adhkar = morning | evening`, stripped in
  `MainActivity` like `open_playing`, surfaced as a one-shot `LaunchRequests.pendingAdhkar`,
  consumed in `AppEffects` as `selectTab(PRAYER); push(Screen.Adhkar(set))`. iOS: a
  `UNUserNotificationCenterDelegate.didReceive` calling a Kotlin entry — last task, cut first if
  the night runs short; a tap then foregrounds the app.
- Reminder state lives in DataStore like every setting, so it survives updates.

## 6. Strings and i18n

Every new Compose string in all eight folders (`values`, `-ar`, `-bn`, `-fr`, `-id`, `-in`, `-tr`,
`-ur`; `values-in` byte-identical to `values-id`), appended at the end of each file so other
branches' additions after `settings_attribution` never collide; `scripts/check-strings.sh` clean;
typographic apostrophes; Bengali in NFC. Keys: `today_open_adhkar`, `adhkar_morning`,
`adhkar_evening`, `adhkar_segment_morning`, `adhkar_segment_evening`, `adhkar_times` (plural),
`adhkar_once`, `adhkar_times_today`, `adhkar_progress`, `adhkar_until_sunrise`,
`adhkar_after_sunrise`, `adhkar_until_maghrib`, `adhkar_after_maghrib`, `adhkar_window_morning`,
`adhkar_window_evening`, `adhkar_done_morning`, `adhkar_done_evening`, `adhkar_next_evening`,
`adhkar_next_morning`, `adhkar_back`, `adhkar_reading`, `adhkar_translation`, `adhkar_of`,
`adhkar_a11y_count`, `adhkar_opens_at`, `source_bukhari`, `source_muslim`, `source_abu_dawud`,
`source_tirmidhi`, `source_ibn_majah`, `source_nasai`, `source_hakim`, `source_ahmad`,
`notifications_adhkar`, `notifications_adhkar_detail`, `prayer_times_time_format`,
`time_format_24`, `time_format_12`, `credit_adhkar`, `credit_adhkar_detail`. `tasbeeh_hint` is reused. The translations are mine and
are listed for Mohamed's review in the report.

The debug screenshot harness gains `"adhkar"` in `DebugScreenRequests`.

## 7. Verification

- `scripts/test.sh`, `scripts/check-strings.sh`, `:androidApp:assembleDebug`, `scripts/ios-build.sh`
  green before each commit that touches them.
- Unit tests: `twelveHourParts` and the marker rule; the Android formatter host test; the widget
  wire fixture; the JSON structure and Quran-reference tests; the phase/window rule; the progress
  store (date keying, shared istighfar); the planner entries, clamps and priority fill; the glyph
  compiles under both bodies.
- Devices: a throwaway emulator (port 5558, `-read-only`) for the 12-hour timeline, widgets and
  notification bodies at 100 % and 200 % text in English, Arabic, Turkish and Bengali; the Adhkar
  screen in each phase (mocked clock), counting, the done page, the reading sheet, the Quran page
  with the bundled clip and with a downloaded surah, the reminder; the iOS simulator for the same
  where the user allows it; the S23 only if the Lead is not using it.
- A `docs/BUILD-LOG.md` entry and a morning report with the translation drafts.

## 8. Risks

- A diacritic error in a du'a is the failure this category does not forgive: the OpenDua Arabic is
  checked against the hisnmuslim chapter text and the printed Hisn before it ships.
- The al-Qahtani compilation layer is unlicensed everywhere; the credit and per-item hadith
  citations mitigate.
- The five translations gate the languages, not the build: a language whose file is unreviewed
  ships without du'a translations rather than with unreviewed ones.
- The first play of 2:255 for a non-default reciter costs a 58 MB download; the sheet says so.
- With every reminder on, iOS holds under five days of notifications; prayers are planned first.
- Nothing on the branch is pushed; the Lead merges with Mohamed's OK.
