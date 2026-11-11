# Plan 3 Task 9: the watch app's emulator pass

Lane W (`watch-wear`), 31 October 2026. Plan 3 Task 9 (P3.9): every screen and the Tile of the
Galaxy watch app on `emulator-5558` (`Taqwa_Wear_OS_6`, Wear OS 6, API 36), in English and Arabic,
on both clocks and at the largest text, heard with TalkBack, with the watch's own language set to
English and then to Arabic.

- **Base** `024cd93e` (`git merge --ff-only watch-companion`: already up to date). It holds P3.4 to
  P3.8 and P3.11, P3.10's words, and Lane P's P3.7 (Qibla) as landed in `ba3c7cc7`. Lane P's R63b
  and R66 (the Qibla detail) are not on `watch-companion` yet; see "Qibla" below.
- **The builds shot:** the debug APK of the base (`7c6ab03c`), for the after-fix shots of Step 4
  that of the Today fix (`64551a6b`), and for the after-review shots that of review round 1's fix
  (`17498de4`), each installed with `adb -s emulator-5558 install -r`.
- **Commits:** the one defect the pass found in `:wearApp` (Today's list at font 1.3, Step 4) is fixed
  test-first in its own commit before the pass's, and its review round 1 lowered that fix's cap (Step
  4, below); nothing else in the app changed.
- **Devices:** `emulator-5558` alone, under the `wear-5558` lock, put back as found each time (the
  ledger names every file and setting). The plugged-in phone `KB2604110095`, the work iPhone 12, Dev
  1's `emulator-5572` and every simulator were not touched. No release key was read or used. No phone
  emulator ran, so every no-feed check ran with no paired phone.

## The folder

| Path | What it holds |
|---|---|
| `ref/` | `ref.py`'s drawings and their `textmask`s: `today-<t>` (and `-rtl`), `list-<t>` (the Tile's rows, its line masked, for Today's list), `tasbeeh-<n>`, `tasbeeh-list`, `qibla-searching` (heading 340), `qibla-aligned` (23), `tile-<t>` |
| `watch-en/<feed>/`, `watch-ar/<feed>/` | Step 1's matrix with the watch in English and in Arabic, for the feeds `en` (24 hours), `en12`, `ar-ly12` and `ar-sa`; `words.txt` is each shot's accessibility tree, in tree order |
| `compare.tsv` | every matrix shot against its drawing (`compare.py`, words masked, the right-to-left Tile and lists mirrored) |
| `states/watch-en/`, `states/watch-ar/` | Step 2's states on every page and the Tile, with `words.txt` and `broadcasts.txt` |
| `phone-words/` | Ruling 58 item 5's watch half: the phone's own Urdu and Libyan goldens on the three pages |
| `talkback-walks.txt` | Step 3: everything TalkBack said, from its own log |
| `large-text/` | Step 4 at font scale 1.0, at the watch's largest and at 1.3; `after-fix/`, Today with the pass's 1.24 cap; `after-review/`, Today's list end with review round 1's cap, 1.12, and `ink.txt`, its measures |
| `settings/` | The watch's own Font size page at "Largest" (Step 4) |
| `talkback/` | The Tile in front while TalkBack read it, in en24, en12 and ar-sa (Step 3) |
| `face/` | The face's 14:58 pass on Arch, awake and always-on (Step 5) |
| `tile-late/` | Ruling 65 item 4: a start with the renderer late, a doze across a start, TalkBack on a two-sided entry |
| `qibla-minors/` | Ruling 62 item 5's carried minors |
| `drivers/` | The scripts that drove the pass and Step 4's hand-made feeds, committed under Ruling 68 (`drivers/README.md`) |
| `qibla-r68/` | Ruling 68 item 4: the Qibla rows shot again after R66, and the captions over a turning dial clear of the letters in seven languages (its `README.md`) |

## The errata and rulings this pass carried

| Item | How it was met |
|---|---|
| Erratum 1 (`git merge --ff-only watch-companion`) | Already up to date at `024cd93e` |
| p3 #3 (the drawings are English and 24-hour) | Every shot in another language or clock is set beside its drawing by its geometry, its words masked (`ref.py textmask=`, `compare.py mask=`), the right-to-left Tile and lists mirrored and Today's ring drawn `rtl`; the words are read from the tree (`words.txt`) |
| p3 #4 (the tracker scan) | Plan 2 Task 5 Step 1's pattern, and `comm -13` against the list recorded before plan 3 (the library check's, `2026-10-23-wearable-library-check.md`: `android.adservices` and its nine subpackages, D8's debug stubs, and `com.google.firebase`): empty for the release and for the debug APK (Step 5) |
| p3 #13, Ruling 58 item 5 (`ui.screens` in the phone's language) | The watch's half, below; the phone's half needs a phone running the app beside the watch, which is plan 2 Task 7's pass (after SIGN): handed to it in the ledger |
| p3 #14 and #15, Ruling 62 item 5, Ruling 63 item 2 (the Qibla detail) | Ruling 66 replaced 62 item 2 and 63 item 2, and Lane P's R66 (not on `watch-companion` yet) carries its own device pass (Ruling 66 item 9). This pass records the landed dial, below, and Ruling 62 item 5's three carried minors |
| p3 #16, Ruling 65 item 4 (the Tile and a late renderer) | Seen on the final Tile code, below |
| p3 #17, Ruling 66 item 9 | Lane P's R66 turn on emulator-5558 (05:51 to 06:32); after R66 lands, the Qibla rows of this matrix are shot again (for the Lead) |
| Ruling 55 item 6 (test vectors out of every APK) | Already held: the debug APK's assets are the four sample feeds alone, the release's none |
| Rulings 39, 41, 42, 43 | Untouched: the watch shows the city, the bearing and the distance as the phone rounded them and asks for no location; no face file changed (the face's 14:58 pass is 0 px from plan 1's); the face's order and the always-on share (11.8%) are as they were |

## Step 1: the matrix

At font 1.0, the watch in English and then in Arabic (`ar-EG`, set with plan 2's `SetLocale.java` as the
harness README says; English again after), the four feeds: Today at 14:58, 22:30 and 04:10 with its
list scrolled to its end; Tasbeeh at 0 (the hint), 27, 33 (with the part's flash, and after it), 100
(the set's flash) and the next round's 0, the phrase list and its end with his two phrases and Reset;
Qibla Searching (true 340), Aligned (true 23), Low (600 µT, "Magnetic interference") and BestEffort
("Compass unavailable", 22 s later); the Tile at 14:58 and 06:40.

**Against the drawings** (`compare.tsv`; pixels that differ by more than 8, words masked):

| Screen | Differs | Where |
|---|---|---|
| Today's ring, 14:58 / 22:30 / 04:10 | 0.83% / 0.26–0.27% / 0.83–0.85% | the ring's antialiased edge, and its arc a few seconds on (the drawing is at :00, the shots at :23) |
| Today's list at its end, against `gTile`'s rows | 0.00–0.05% | the pips' edges; at 04:10 no row is lit, by the Prayer screen's rule |
| Tasbeeh 0, 27, 33, 100, the next round | 0.07–0.95% | the ring's edge, the flashes; the Arabic feeds' part dots mirror, as Task 6 built them |
| The phrase list | 1.90–1.92% | Manrope's 700 drawn SemiBold, and the drawing's faded fifth row, as Task 6 found |
| Qibla Searching / Aligned | 0.27–0.30% / 1.06–1.09% | the ticks', needle's and lit rim's edges at the fused heading (340.1 for 340) |
| The Tile, 14:58 / 06:40 | 0.11–0.18% / 0.32–0.37% | the pip column; at 06:40 the widgets' rule lights Fajr |

Low and BestEffort have no drawing (the phone's dimmed dial, Task 7).

**The watch's language changes nothing the app draws.** Every one of the 80 shot pairs (`en-vs-ar-watch.txt`)
is pixel for pixel the same on the English and the Arabic watch, the Qibla needle's edge alone
excepted (at most 87 px, the fused heading's own noise from one run to the next): the Arabic feed is
right to left, in Arabic words and the phone's digits, on an English watch, and the English feed left
to right on an Arabic watch, Tile included. No page shows a time, page dots or a scroll bar, and no node
of a page's tree holds the watch's clock.

## Step 2: the states

With no paired phone (none ran), on every page and the Tile (`states/`):

| State | English watch | Arabic watch |
|---|---|---|
| No feed (`--ez clear true`) | "Open Taqwa on your phone" | "افتح تقوى على هاتفك" |
| No feed, then `--ei version 2` | "Update Taqwa on your watch" | "حدِّث تقوى على ساعتك" |
| The same after `am force-stop` and a reopen | unchanged | unchanged |
| Spent (the clock 40 days on) | Today: its title and the feed's "Open Taqwa on your phone"; the Tile the same; Tasbeeh and Qibla at work | the same, in the feed's English |
| Newer with a feed | Today: "Update Taqwa on your watch" in the time's place, said after the ring's sentence; the Tile: the update in the city's place under the rows; Tasbeeh and Qibla from the last feed | the same |
| A plain `DEBUG_FEED` | the sample again | the sample again |

Beyond the step: a spent feed with a newer format seen says the update on Today (call 5's order,
`theUpdateOutranksTheSpentNotice`).

## Ruling 58 item 5: the screens' words arrive in the phone's language

**The watch's half, seen.** The phone's own golden feeds, written by `:shared`'s builder with each
language's words (`watchcore/vectors/phone/`), stored byte for byte (`phone-words/`):

| Golden | Today | Tasbeeh | Qibla |
|---|---|---|---|
| `phone-capetown-ur24` | "۱ گھنٹہ ۱۱ منٹ بعد عصر، ۱۶:۱۰ بجے" | "سبحان اللہ، چکر ۲" | "۲۳°", "مکہ مکرمہ · ۶٬۶۰۰ کلومیٹر دور", "تقریباً ۴۵ درجے دائیں مڑیں" |
| `phone-tripoli-ar-ly12` | "العصر بعد 1 ساعة و6 دقائق، الساعة ⁨4:05 م⁩" | "سبحان الله، الجولة 2" | "109°", "مكة · على بُعد 2,900 كم", "استدر يمينًا حوالي 105 درجات" |

**The phone's half, not run here.** No test calls `AppContainer.watchFeedFor`, and its
`watchScreenWords(language)` reads the phone's resource environment, which only a phone running the
app has. No phone emulator is paired with the watch before plan 2 Task 7 (Mohamed's sign-in, SIGN), so
the check goes to P2.7's pass, Lane W's next task: set the phone app's language to Arabic, and read
"التسبيح", "الجولة" and "القبلة" on the watch's Tasbeeh and Qibla (ledger, for the Lead).

## Step 3: TalkBack

`talkback-walks.txt` holds every line, in en24, en12 and ar-sa, on each page and the Tile.

- **Today:** opening it, TalkBack says the app ("Taqwa") and then the ring's sentence, with no time
  before it: "Asr in 1 hour 11 minutes, at 16:10"; on en12 "… at ⁨4:10 PM⁩"; on ar-sa
  "العصر بعد ١ ساعة و١١ دقيقة، الساعة ١٦:١٠". Untouched for 10 s mid-minute it says nothing (0); across
  the turn of a minute it says the ring's new sentence once. The walk goes from the ring through
  "Passed. Fajr, 4:33" … "Isha, 20:24" and "Cape Town" to the end, then starts again at Fajr.
- **Tasbeeh:** the counter is "27 of 100. SubhanAllah. Page" ("٢٧ من ١٠٠. سبحان الله" on ar-sa).
  `tap2` counts (28 stored; heard "٢٨ من ١٠٠" on ar-sa). `actions` opens the TalkBack menu with
  "Actions"; its `tap2` lists "Take one away" ("أنقِص واحدة"), which says "27 of 100" and stores 27;
  the next action is "Choose a dhikr" ("اختر ذكرًا"), which opens the phrase list, its pane title said
  ("Tasbeeh"). The list reads "Selected. After prayer, 100", the other built-ins "Not selected. …, 100",
  his two phrases, then "Reset. Button".
- **Qibla:** at true 340 the walk reads "23°", "Makkah · 6,600 km away", then the dial's "Turn right
  about 45 degrees". Turned to true 23 untouched, it says "Turn right about 30 degrees", "15", then
  "Facing qibla" once; turned back, "15", "23°", "30", "45". At a steady heading it says nothing.
- **The Tile:** the line's sentence ("Asr in 1 hour 10 minutes, at ⁨4:10 PM⁩" on en12), the six rows
  with their states first, then the city.
- **From one page to the next with TalkBack alone:** the walk does not leave a page. From Today's city
  it starts again at Fajr; from the counter, it reaches the end and comes back to the counter, never
  Qibla. So TalkBack's way between pages is its two-finger swipe, which the console's one finger
  cannot draw: **named for plan 5's Galaxy pass**. Turned with TalkBack on by a swipe sent with `adb
  shell input swipe`, which reaches the app as a sighted wearer's swipe would, the pager says each
  page's title as it comes: "Tasbeeh", "Qibla", "Tasbeeh", "Today", and on ar-sa
  "التسبيح", "القبلة", "اليوم".
- Words of the watch's own system, in the watch's language as the plan has them: TalkBack's "Page",
  and Compose's "Selected" and "Not selected" (Task 6's note). Arabic rows join their name and
  time with a Latin comma ("الفجر, ٤:٣٣"), as the phone's own row does: ruled for plan 5's native
  review (the ledger, 30 October, item 6).
- The drawn screens are the same with TalkBack off as in Step 1 (TalkBack was off for every shot).

## Step 4: large text

The watch's Settings (Accessibility › Text size › Font size) go to **1.24** ("Largest"; 1.06, 1.12,
1.18 and 1.24), so the step ran at 1.0, 1.24 and again at 1.3, in en24, en12 and ar-sa, with the
bundle's longest city (Bengali, 33 characters) and his 36-character phrase in each feed (hand-made
copies of the samples), and Low(CALIBRATION) from a copy whose interference title is the calibration
one (`large-text/`).

- **Today's countdown and Tasbeeh's count keep the drawing's size:** their ink boxes are the same to
  the pixel at 1.0, 1.24 and 1.3 in all three feeds (the countdown 281 × 63 px; "27" 108 × 76, and
  85 × 58 in Arabic-Indic digits; "0" 47 × 77).
- Every other line grows to its cap; the dhikr, his phrase (two lines) and the notices shrink to fit;
  the longest city ends in an ellipsis inside the round edge, on Today and on the Tile.
- **The phrase list and Today's list scroll**, and the Tile keeps its pitches: its rows' middles stand
  within 1.5 px of 1.0's while their words grow. On this emulator the renderer grows a Tile's
  text no further than 1.24: at 1.3 the Tile is the 1.24 one to the pixel.
- **Found and fixed (Lane W's own Today, P3.5), test-first in its own commit:** at 1.3, Today's list
  scrolled to its end stood 45 units higher than the drawing, and its first row's time ran off the
  round screen (en "4:33" cut; ar-sa's "الفجر" and en12's "AM" within 1 px of the edge;
  `large-text/today-list-end-1.3-before-after.png`). By the step's own remedy, the list's lines (row
  names and times, Sunrise, the city) were capped at 1.24, Wear OS's own largest
  (`large-text/after-fix/`).
- **Found again in review round 1, and fixed by the same remedy:** at 1.24 the 12-hour list still
  broke the frame's inset. Its column of times moves out for "Maghrib 7:04 PM" (to about 166 units),
  and the column is held at each row's round edge where the drawing has the rows. At the list's end,
  Fajr stands higher, so its "4:33 AM" reached 226 px from the middle at 1.24 and 1.3 (the screen's
  edge is 227, the inset's 213.4). The guard test had checked only the 24-hour column's 150 units.
  The list's lines now cap at **1.12**, Wear OS's step below 1.18 (`TodayType.LIST_CAP`). Up to a
  growth of about 1.153, every row at the list's end keeps the widest column the times can take (Isha's round
  edge in the drawing, 167.3 units) inside the inset, on either clock and in any language. 1.12 is the
  largest of Wear OS's own sizes within that. A list grown to 1.15 kept every time inside, but cut a
  12-hour Friday's "Jumuʿah" to "Jumuʿ…" (`after-review/cap-1.15-en12-fri-1.24-list-end.png`); at
  1.12 it is whole. On the device (`after-review/`, `ink.txt`), in en24, en12, a 12-hour Friday,
  ar-sa and ar-ly12 with the longest city: 1.18, 1.24 and 1.3 are each the 1.12 list to the pixel,
  and 1.0 and 1.12 are as before the fix. The farthest ink in the top half is 191.0 px on 12 hours,
  197.9 px on the Friday and 176.8 px in English and Arabic, and the city's is 206.0 px, all inside
  the inset (`after-review/today-list-end-1.24-before-after.png`). Tests:
  `atItsEndTheListKeepsEveryRowsTimeOnTheRoundScreenAtEveryFontSize` now checks every row against
  the widest column, with each name at its largest or shrunk and each time at its largest, on round
  and square screens (red at font 1.16 on the 1.24 cap). Also
  `theWidestColumnOfTimesEndsAtIshasEdgeInTheDrawing`,
  `theCapIsTheLargestWearOsSizeTheRoundScreenAllows`, `theListGrowsWithTheFontUpToItsCap` and
  `aboveItsCapTheListIsAsItIsThere`. Four mutants are each red: a cap of 1.16, a cap of 1.06, the
  city at 1.3, and the times at 1.24.
- **The Qibla dial on the landed code (P3.7, before R63b and R66):** at 1.24 and 1.3 the English
  detail "Makkah · 6,600 km away" runs onto the tick band at both ends, and on every font the turning
  letters meet the detail and the tilt notice at some headings ("E" against "level", `qibla-minors/`).
  Ruling 66 replaces the detail (Lane P's R66, its own device pass); the tilt notice is not in it (for
  the Lead, below).

## Qibla: Ruling 62 item 5's carried minors

- **The state when the page leaves:** facing the Qibla (true 23), swiped to Tasbeeh, the heading moved
  to true 340 off the page, swiped back: 0.25 s, 0.75 s and 1.75 s after the swipe the dial already
  shows the new heading and "23°", never the old "Facing qibla" (`qibla-minors/stale-*`).
- **The letters and the tilt notice:** tilted 45°, the dial turned through 24 headings at 1.0 and 1.3
  (`tilt-10-sheet.png`, `tilt-13-sheet.png`): at some headings "E" meets the last "l" of "Hold your wrist
  level", and "S" or "E" crosses "Makkah · 6,600 km away" (`tilt-10-m000.png`, `tilt-13-m180.png`).
  Lane P's (P3.7): written in the ledger for the Lead.
- **`QiblaInkTest`'s margin of about 0:** a host test's, unchanged by this pass.

## Ruling 65 item 4: the Tile with the renderer late

On the final Tile (`d5cf6127`, in the base), the English sample, the watch in English (`tile-late/r65.txt`):

- **A full start with the renderer late:** asked for at 16:08:10, the renderer set its change for
  16:10:00 with a window of +1m22s. The Tile said "Asr in under a minute" with Dhuhr lit to 16:09:54,
  then from 16:10:00 "Maghrib in 2 hours 53 minutes" with Asr lit, and "52 minutes" at 16:11:03, while
  the 16:10:00 change was still pending at 16:11:03: the entry before Asr drew both sides.
- **A doze across a start:** asked for at 19:01:10, the change set for 19:02:00 (+37 s), then
  `dumpsys deviceidle force-idle`. The change stayed pending through 19:05:38. The line said "Maghrib
  in 1 minute" to 19:02:54, then "Maghrib, 19:04" (the start's time) from 19:03:04 on, never a passed
  countdown. Asr stayed lit past 19:04 until the doze was lifted, when the Tile said "Isha in 1 hour 18
  minutes" with Maghrib lit at once (19:05:44). That is the documented limit of a renderer later than
  `shownUntil` (Task 8's round 4): with the screen on and the Tile in front, a doze cannot happen on a
  watch, and the line stays true; plan 5's Galaxy pass measures a real watch's lateness.
- **TalkBack on a two-sided entry** (Isha 20:24, asked for at 20:21:30): at 20:22:19 "Isha in 1 minute,
  at 20:24", the rows with Maghrib current and Isha next, the city; at 20:24:03 "Fajr in 8 hours 8
  minutes, at 4:33", the rows with Isha current, the city. Each item once; the hidden side never read.

## Step 5: the checks

On the final tree (the base, the Today fix and its review fix, `8adb903e`; corrected under Ruling 68,
from review round 2's run):

- `scripts/test.sh`: exit 0. Versions agree at 1.3.0 (39) and (100039), `test-bump-version.sh` and
  `test_privacy_check.py` PASS (privacy-check clean but the 186 rows awaiting M3), `swift test` 172/0
  (and WatchViews 4/0), wearApp 498 tests (492 before; 4 added by the Today fix, 2 by its review
  fix), shared host 2274, widgetcore host 111,
  watchcore host 224, shared iOS 2218, watchcore iOS 197, widgetcore iOS 109, none failed; no
  `EOFException`. `./gradlew :wearApp:testDebugUnitTest --rerun-tasks` on the base: 492/0.
- `scripts/ios-build.sh`: BUILD SUCCEEDED; `check-watch-bundle` every rule holds.
- `./gradlew :wearApp:assembleRelease :wearApp:lint` (and `lintRelease --rerun`): 0 errors, the one old
  warning (MissingApplicationIcon). The merged debug and release manifests ask for `VIBRATE` and
  `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone.
- `tools/watchface/validate.sh`: both validators pass. The face's 14:58 pass on Arch: awake and
  always-on 0 px from plan 1's `pass-arch-en-1458-awake.png` and `-aod.png` (`face/`), the always-on
  lit share 11.8%.
- **The tracker scan (erratum p3 #4):** `apkanalyzer dex packages --defined-only` with plan 2's pattern
  finds `com.google.firebase` alone in the release (play-services-basement's three exception classes,
  already on the list recorded at P2.6), and the debug APK the same plus D8's ten `android.adservices`
  stubs; `comm -13` against the list recorded before plan 3 prints nothing for either.
- `face-*.py --check` up to date; `tools/i18n-check.py` clean.

## For the Lead

1. **Ruling 58 item 5's phone half** goes to P2.7's paired pass (above).
2. **The tilt notice and the turning letters** (Ruling 62 item 5, Lane P's P3.7): "E" meets "level" at
   some headings at 1.0 and 1.3; R66 makes the detail keep its air from the letters, not the notice.
   Done under Ruling 68 item 4 (`qibla-r68/`): every caption over a turning dial keeps the air.
3. **After R66 lands:** shoot the matrix's Qibla rows again (four feeds, both watches) and Ruling 66
   item 9's large-text shots, since this pass saw the landed detail. Done under Ruling 68 item 4
   (`qibla-r68/`).
4. **Today's list at a Galaxy's largest font size** (Step 4): from Wear OS's 1.12 step on, the
   list is the 1.12 one, which the round screen holds at its end on both clocks (review round 1). Plan
   5's Galaxy pass sees that list at its own sizes.
5. **The Tile under a doze** shows the rows from before a start until the renderer moves on (the
   line stays true): Task 8's documented limit, for plan 5's lateness measurement.
6. **TalkBack's two-finger swipe** between pages and to and from the phrase list: plan 5's Galaxy pass.

## Put back

Each of the three turns found the emulator off, and each left it as found: the found app (`2b2d77c9`,
100038) and face (`d4f50b1d`) reinstalled with `install -r -d`, `feed.json` (`0b3f92ef`),
`nudge.anchor` and `profileInstalled` byte for byte, no `shared_prefs` or `complications.asked`, the
Tile removed, TalkBack off, font 1.0, en-US with `system_locales` deleted, automatic time, the
console's sensors as found, deep idle not forced; the face on Arch, Amber, its slots empty; then
stopped by serial. The first boot of the second turn hung (adb offline for 600 s while the console
answered): stopped by serial and booted again. The scripts that drove the pass are in the session's
scratchpad (`p39/bin/`); since Ruling 68 they are committed beside this report, in `drivers/`, so the
Qibla rows can be shot again with them (`drivers/README.md`).
