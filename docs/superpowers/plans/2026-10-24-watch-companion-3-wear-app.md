# Watch companion 3: the Galaxy watch app, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** the Taqwa watch app on the Wear OS 6 emulator has its three screens (Today, Tasbeeh and Qibla) and its Tile. All four draw from the feed alone, in the phone's language, digits and clock:
- the bezel counts the tasbeeh and takes counts away;
- the watch's own compass turns the Qibla dial, with no location permission;
- TalkBack reads every screen and the Tile.

**Architecture:**
- **`:watchcore`** gains the phone's tasbeeh rules and the phone's compass rules. They move there unchanged, in their own packages (`world.taqwa.app.tasbeeh`, `world.taqwa.app.qibla`), so the watch counts and judges its compass with the phone's own code. Beside them go the new pieces both watches share:
  - undo;
  - the dial's state machine over the feed's bearing;
  - the turn guidance a screen reader speaks;
  - the feed's new defaulted fields: the screens' words, the 12-hour clock as the app writes it, and the digits of the ring's countdown;
  - `WatchTime`, the key of a day's six times (Sunrise among them), by which every time is drawn and said.
- **`:wearApp`** gains:
  - one activity: a pager of three pages, each page's model a pure function of (feed, words, now) tested on the JVM, drawn with Compose for Wear OS from `faces.js`'s 200-unit drawings scaled to the screen;
  - a `TileService` in ProtoLayout;
  - a compass source for the wrist;
  - the phone's haptics;
  - in the face's sources, one change to what is said: a 12-hour time with its day period (Task 11).
- **`:shared`** changes only by the moves. Task 10 also fills the new words in plan 2's builder, once that builder exists.

**Tech stack:**
- Kotlin 2.4.10, AGP 9.4.0, kotlinx-serialization-json 1.11.0;
- Compose for Wear OS 1.7.1 (`compose-material3`, `compose-foundation`), activity 1.13.0, lifecycle 2.11.0 (the catalog's);
- `androidx.wear.tiles:tiles` and `androidx.wear.protolayout:protolayout` beside plan 1's `protolayout-expression` 1.4.2: the newest stable Tiles release whose POM names protolayout 1.4.x;
- `androidx.concurrent:concurrent-futures-ktx` (`SuspendToFutureAdapter`), so there is no Guava call and no Horologist;
- the emulator `Taqwa_Wear_OS_6` (Wear OS 6, API 36) on port 5558, and the harness in `tools/watchface/`.

**Spec:** `docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`:
- 1.3: the bundle's tasbeeh, Qibla and app words, which plan 2's Lead's call 6 hands to this plan;
- 1.4, 2.1, 2.4, 2.5, 2.6 and 6;
- 7: calls 4, 6, 7 and 9.

The drawings are in `docs/superpowers/specs/2026-10-19-watch-companion/faces.js`:
- the screens: `gToday`, `gTasbeeh`, `gTasbeehList`, `gQibla` and `gTile`;
- their helpers: `appRing`, `pip`, `rowStatus` and `qiblaDial`.

`mockups.html` shows them in place. The 200-unit drawings fill the round screen.

**Binding rulings** (`.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`):
- **22:** the re-request safety net. This plan adds it at app start and from the Tile. The daily nudge is plan 2's Task 6.
- **36:** the Moon's option (d). Nothing here touches the Moon or anything the face draws. Task 11 changes only what the face's next prayer and day dial say.
- **38:** TalkBack is a ship gate. It applies to every screen and to the Tile.
- **39, amended:** the watch shows the city, and the bearing and the distance, exactly as the phone rounded them. It never learns where it is. The app's promise is "Only your own devices can see your location."
- **42** (which replaced 40): the face's reading order belongs to the face, the time, the prayer, then the dates. This plan changes no slot, box or order. The app's own surfaces read top to bottom, the next prayer first.

**When this plan runs, and what it may assume:**
- Plan 1 is closed (Tasks 11, 12 and 13) before Task 3, which regenerates the vectors plan 1's Task 12 changed, and before Tasks 4–11. Tasks 1 and 2 touch no file of plan 1's and may start before it closes (the execution map).
  - No file under `watchface/` changes, and none under `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/` but Task 11's one reviewed exception (below).
  - Elsewhere this plan only calls `ComplicationSources.requestUpdates` and `CountdownExpression`.
- **Task 11, the one exception to plan 1's freeze.** Plan 2 handed this plan the 12-hour spoken time's day period, "and `ComplicationFields` speaks it" (plan 2's hand-off to plan 3), and plans 4 and 5 do not take it. So Task 11 changes the face's spoken text alone: `NextPrayerFields` gains a defaulted `spokenTime`, and `nextPrayerSaid`, `prayerSpoken` and `dialSpoken` say it. Nothing drawn, no slot, no box and no reading order changes (Ruling 40). Every 24-hour line stays byte for byte as it is. The three existing tests that expect a bare 12-hour time change with it, and Task 11 names them.
- From plan 2 it assumes only the feed model and the sample feeds, as they are on the branch.
  - Tasks 1–9 and 11 build on those alone.
  - Task 10 waits for plan 2's Tasks 1, 3, 5 and 8:
    - Tasks 1 and 3 give `WatchWords`, `watchUiLanguage`, `WatchFeedInputs` and `WatchFeedBuilder`;
    - Tasks 5 and 8 give two of the five size tests that Task 10 runs again with the words in: `aFeedAtTheBudgetIsWithinTheMessageLimit` and `theLargestContextFitsTheSmallestLimit`.
- **Overlaps for the execution map.** Whichever lands second rebases onto the other:
  - Tasks 1–2 and plan 2 Task 1 add the same line, `implementation(project(":watchcore"))` in `shared/build.gradle.kts`. A task that finds the line there leaves it.
  - Task 3 and plan 2 Task 3 both change `Fixtures.kt` and regenerate `watchcore/vectors/`. Run them one after the other, and regenerate with `-Ptaqwa.updateVectors` after the rebase.
  - Task 4 and plan 2 Task 6 both touch `StatusActivity.kt`, `TaqwaWearApp.kt`, `FeedStore.kt` and the manifest. Task 8 touches `FeedStore.kt` too.
    - Task 4 deletes `StatusActivity`. Plan 2 Task 6's `onResume` lines (Ruling 22's app-start request and `askIfDue(AskWhy.OPENED, now)`) go into `TaqwaWearApp.onAppOpened`, in whichever task lands second.
    - Task 4's `FeedStore.clear()` also deletes plan 2 Task 6's `deliveryFile` (`feed.json.delivery`, Ruling 50's name for what Ruling 48 called `fingerprintFile`) and its temporary file, in whichever task lands second. Task 8's `onNewerVersion` listener sits beside plan 2's `write(json, delivery)` the same way.
    - As built (30 October): plan 2 Task 6 landed first, so Task 4 moved its two `onResume` lines and wrote plan 2 Task 6's checks for their new place in plan 2's own text (erratum p3 #1).
  - **Task 4 and plan 2 Task 7.** Plan 2 Task 7's pass reads the status screen in three places: check 1 ("The watch app's screen shows 'London'"), check 7 ("The status screen says 'Update Taqwa on your watch'") and check 8 (`am start` of `StatusActivity`). Task 4 does not wait for it.
    - If plan 2 Task 7 has already run, nothing changes.
    - If Task 4 lands first, Task 4's last step hands the Lead three line edits for plan 2 Task 7, which he applies before it runs. Check 1 reads "London" on Today (Task 4's frame draws the city under the title, and Task 5's list ends in it). Check 7 reads "Update Taqwa on your watch" on Today, in place of the next prayer's clock time (call 5). Check 8 starts `world.taqwa.app/world.taqwa.app.wear.app.WearMainActivity`. The plan 2 hand-off "The app's screens shake hands as `StatusActivity` does" is met by `onAppOpened`.
  - Task 2 moves files that Dev 1's Qibla PR (branch `fix/qibla-b20-b21-b40-b41`: B20, B21, B40, B41) and the phone's accessibility round 2 (branch `a11y-round-2`, its Task 9) edit. Task 2 starts only once Dev 1's PR is on `main` and `watch-companion` has taken it (the ledger, 24 Oct, the coordination with Dev 1). `WatchDialParityTest` is the guard that says when the phone and the watch part.
- **Parallel lanes:** Task 1 first. Task 3 follows Task 1, because its words test reads `TasbeehPresets` in `:watchcore`. Task 2 follows Task 1 and Dev 1's PR. Tasks 2 and 3 touch disjoint files and may run side by side. Task 4 follows Task 3; Task 7 also needs Task 2.
  - Tasks 5, 6 and 7 each own a package of their own, and run side by side after Task 4.
  - Task 8 follows Task 5, and runs beside 6 and 7. It builds on Task 5's `RowModel`, `dayRows` and `LongestCity`, so it does not compile in a worktree without them. Of Tasks 5–8, only Task 8 edits the manifest, `wearApp/build.gradle.kts`, the catalog, `TaqwaWearApp.kt`, `FeedStore.kt` and `DebugFeedReceiver.kt`.
  - Task 11 follows Task 3. It touches only `complication/` and its tests, so it runs beside 4–8.
  - The emulator steps take turns on 5558.
  - Task 9 follows 5–8 and 11. Task 10 follows Task 3 and plan 2's Tasks 1, 3, 5 and 8.

## Open questions for Mohamed

No task waits on these. Each is built as its default, and he judges it on his Galaxy in plan 5.

1. **Reaching the phrase list from the counter.**
   - Default: swipe up, the same gesture that gives Today's list. A tap counts everywhere on the counter, and the bezel counts too.
   - With TalkBack on, the one-finger swipe belongs to TalkBack. So the counter also offers the action "Choose a dhikr" (`phrasesAction`), beside "Take one away".
   - Alternative: a long press.
2. **Starting again on the watch.**
   - Default: a "Reset" row at the end of the phrase list. It asks the phone's own question, "Reset the count for this dhikr?" (`tasbeeh_reset`, `tasbeeh_reset_confirm`).
   - Alternative: no reset on the watch. The bezel takes counts away, and rounds roll on.
3. **The screen during Tasbeeh.**
   - Default: the watch's own timeout, as in every Wear app. The count is saved 300 ms after the last change, and the next raise of the wrist comes back to it when the system allows.
   - Alternative: the screen kept on while the counter shows, as the phone does. That costs battery.
4. **"Hold your wrist level"** (spec 1.3).
   - Default: it takes the caption's place while the screen tilts more than 40° from level, until the tilt is back under 30°. The needle keeps pointing, because the heading is tilt-compensated.
   - Alternative: a fixed line shown before the first good heading.
5. **The compass's bad states.**
   - Default: the phone's titles alone: "Compass needs calibrating", "Magnetic interference", "Compass unavailable" and "No compass sensor". The phone's help lines are too long for a 1.4-inch screen.
   - Alternative: one short new line for calibration, "Move your wrist in a figure of eight", in seven languages.
6. **The time and the page dots.**
   - Default: neither, as `faces.js` draws the three screens. Wear's `TimeText` writes the watch's own 12/24 setting and digits, not the phone's, and TalkBack would read it before the next prayer on every page.
   - Alternative: Wear's time at the top and page dots at the bottom. Both would need room taken from the ring and the bezel hint, and the time would break the rule that the watch writes time as the phone does.

## The Lead's calls in this plan

The tasks build each default. A different ruling changes the named task alone.

1. **Where the shared rules go (Tasks 1, 2).**
   - They go into `:watchcore`, in their own packages, so no caller's import changes.
   - `QiblaMath` keeps `bearing` and `distanceKm`, which need adhan2. Its `angleDelta` and `isAligned` call the new `QiblaAngles`.
   - These stay in `:shared`: `WorldMagneticModel`, `CompassSource`, the phone's two compass sources and `Haptics`.
2. **Fonts (Task 4).**
   - Manrope comes as byte-identical copies of the phone's four files.
   - The built-in dhikr, Arabic, Bengali and his phrases use the system font, as the phone does (`TasbeehScreen.DhikrBlock` uses `FontFamily.Default`). On the emulator that font is Noto Naskh Arabic.
   - Alternative: bundle Noto Naskh Arabic Bold, so a Samsung watch draws Naskh too.
3. **The screens' words (Task 3)** are one nested field, `WatchUi.screens`, with every field defaulted. A blank word is `WatchScreenWords.ENGLISH`'s word: only a development feed ever lacks them, since nothing ships before plan 5.
4. **The pager runs left to right in every language (Task 4):** Today, then Tasbeeh, then Qibla.
   - Wear OS closes an app with a swipe to the right, so a mirrored pager would make that same swipe mean "next page" on Today.
   - Compose lays a `HorizontalPager` out in reverse under `LayoutDirection.Rtl`. So the scaffold and the pager are composed under `LayoutDirection.Ltr`, and each page's content under `WatchTheme(rtl)`, which gives it the phone's direction.
5. **The states without a usable feed (Task 4), in plan 1's order (`statusHeadline`, Ruling 13): a newer format outranks the rest, with a feed or without.**
   - **No feed, nothing newer seen:** every page and the Tile show the watch's own "Open Taqwa on your phone".
   - **No feed, a newer format seen:** every page and the Tile show the watch's own "Update Taqwa on your watch". The phone has already sent what this build cannot read, so "Open Taqwa on your phone" would send the wearer to the wrong device. This is the freshly installed old build that gets a version-2 feed as its first.
   - **A spent feed:** Today and the Tile show the feed's "Open Taqwa on your phone". Tasbeeh and Qibla keep working: neither reads a time.
   - **A feed, and a newer format seen:** every page draws from the last feed. Today shows "Update Taqwa on your watch" in place of the next prayer's clock time, and the Tile in place of the city. Spent as well, Today and the Tile say the update, not "Open Taqwa on your phone".
6. **The Tile's countdown (Task 8)** is a dynamic string, plan 1's `CountdownExpression.countdown`, so it moves each minute with the app's process dead. On a Tile renderer without dynamic values (schema below 1.2), the line reads "ASR 16:10" instead, never a stale countdown.
7. **The Tile is drawn in the system font (Task 8).** ProtoLayout cannot load a font.
8. **Alignment (Task 2)** uses the phone's own rule, with one buzz on entry: Dev 1's B40, entered within `ALIGNED_ENTER_DEGREES` (5°) and left only beyond `ALIGNED_EXIT_DEGREES` (7°), `QiblaMath.isAligned(heading, bearing, wasAligned)`. It is on `main` before Task 2 (the lanes), and Task 2 moves it into `QiblaAngles` unchanged, as Dev 1 and the Lead agreed on 24 October.
   - `WatchDialParityTest` fails the day the two part.
9. **The phone's 12-hour and countdown forms (Task 3).**
   - 12-hour times carry their day period as the app writes it, in the new `WatchDay.clockTime`. The feed holds it bare. The watch draws it as it is (`WatchRules.clockText`), and wraps it in a first-strong isolate where it splices it into a sentence (`WatchRules.spokenClock`), by the phone's `isolatedClock` rule, so an Urdu "6:32 PM" is never said "PM 6:32".
   - Both are keyed by the new `WatchTime`, the six times of a day. `WatchPrayer` has no Sunrise, and Today's rows, the Tile's rows and the face's day dial all draw or say Sunrise (the rows and the dial's spoken times only while the phone shows it, since 9 November 2026), so one six-valued key serves every caller. A prayer reaches it through `WatchTime.of(prayer)`.
   - Every spoken line on the watch says `spokenClock`: Today's ring and rows, the Tile, and the face (Task 11).
   - Today's H:MM:SS takes the phone ring's digits, the new `WatchUi.ringDigitZero`. On ar-SA, ar-EG and Bengali phones those are Western digits (`CountdownDigits.westernFallback`).
   - Transliterations show only under a Latin-script interface, the new `WatchUi.latinScript`.
10. **Counts (Task 6)** live in the watch's own `SharedPreferences`, per phrase, and are never sent anywhere. The selection falls back to After prayer when its phrase has gone from the phone.
11. **The app's accent is amber.** The app cannot read the colour the face is set to.
12. **The face says the 12-hour day period (Task 11).** It is a narrow exception to plan 1's freeze, made because plan 2 handed it here and no other plan takes it. Only spoken text changes.
13. **The wrist keeps hearing the field (Task 7).** This is a deliberate departure from the phone. The phone stops listening to its magnetometer once its rotation vector is trusted, so on a working rotation vector it can never say "Magnetic interference" (B6: the S23 showed a needle in a 179 µT field). The watch keeps the magnetometer registered beside a trusted rotation vector while the Qibla page is active, and uses it only for the field check and the debug log, never for the heading.
14. **One buzz for each turn (Task 6).** A new `vibrate()` from the app cancels the one still playing (`CANCELLED_SUPERSEDED`), so a tick sent just after a part's buzz cuts it short. Each count, undo or turn sends at most one haptic, the strongest it crossed: the set, then the part, then the tick. No tick is sent while a part's 140 ms or a set's 400 ms waveform still plays. The phone's taps are too slow to need this.
15. **Text size (Task 4).** Each line has a size in units and a cap. The wearer's font size grows it up to its cap, and no further than its box holds (`TextAutoSize` between the two). A smaller font size never shrinks it below 100%, the phone dial's letters' rule. Only two lines may shrink below 100%: the dhikr, for his own long phrases, and the notices. Every line has a `maxLines` and an ellipsis.
    - The size is worked out in units, then in dp, and only then turned into sp, through the screen's own `Density` (`with(density) { dp.toSp() }`). From a font scale of 1.03, Android's sp-to-dp conversion is not linear: Compose's `FontScaling` applies `FontScaleConverterFactory`'s tables, and so does the platform's. Those tables draw 30 to 100 sp almost one to one at 1.15 and 1.3, so dividing by the font scale would shrink the big lines. Measured on ui-unit 1.10.0 at 227 dp: Tasbeeh's 46-unit count (52.21 dp) would draw at 45.4 dp at 1.15 and 40.2 dp at 1.3, and Today's 38-unit countdown (43.13 dp) at 37.5 and 33.2 dp. `toSp` inverts the same tables, so both draw at their units at every scale.
16. **No time text, no page dots, no bezel on the pager (Task 4).** `AppScaffold` gets an empty `timeText` (its default is `TimeText()`), and `HorizontalPagerScaffold` gets `pageIndicator = null` (question 6). Wear foundation's `VerticalPager` turns pages on rotary input by default (`RotaryScrollableDefaults.snapBehavior`), so Tasbeeh's is given `rotaryScrollableBehavior = null`, and the counter consumes every rotary event. The `HorizontalPager`'s default is already null.

## Global Constraints

- **Where and how:**
  - Work in the worktree `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site`, on branch `watch-companion`, or in the lane worktree the execution map assigns. The ledger stays in `site` (it is git-ignored), appended by its absolute path.
  - Start every shell command with `source ~/.zprofile; source ~/.zshrc`, and run Gradle from the worktree root.
  - Commit with `git commit -F <file>`, then check `git log -1`. No `Co-Authored-By` and no AI attribution line, ever.
  - Make WIP commits as you go. Nothing is pushed.
- **The ledger:** add short dated lines to `.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`, under a "Plan 3" heading, as each task starts and ends.
- **Packages and keys:**
  - `:wearApp` is `world.taqwa.app`, debug-signed with `~/.android/debug.keystore`.
  - Never read or use release keys.
  - Never run `:androidApp:assembleRelease`, `bundleRelease` or `installRelease`.
- **Devices:**
  - Never touch the plugged-in phone `KB2604110095`.
  - Every `adb` names its serial with `-s`.
  - Install with `adb -s emulator-5558 install -r wearApp/build/outputs/apk/debug/wearApp-debug.apk`, never with a Gradle `install*` task.
  - The Wear emulator is `Taqwa_Wear_OS_6` on 5558; `tools/watchface/emulator.sh` boots it or reuses it. The Lead's phone is 5556 and Dev 2's is 5560.
  - Stop the emulator only with `adb -s emulator-5558 emu kill`, never with `pkill`.
  - Leave it as plan 1 left it: the face on Arch, Amber, the wearer's slots empty, automatic time, TalkBack off, font scale 1.0, the watch's language English.
  - A paired phone emulator refills an empty watch (plan 2's `STARTED` ask). Run the no-feed checks with no phone paired and running; if one is, ask the Lead. Never stop another's emulator.
- **The face:** no file under `watchface/` changes. Under `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/`, only Task 11 changes `ComplicationFields.kt` and `NextPrayerSource.kt`, and only their spoken text.
- **Permissions:**
  - `:wearApp` declares exactly one permission: `android.permission.VIBRATE`.
  - It has no `INTERNET`, no location of any kind and no `BODY_SENSORS`: the magnetometer needs no permission.
  - The merged release manifest holds VIBRATE and androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, and nothing else.
- **No time is computed on the watch:**
  - Every instant comes from the feed, through `WatchRules`. The watch's clock gives `now` and nothing more.
  - `:wearApp` never links `:shared`.
- **Words:**
  - Every word a screen or the Tile writes or speaks comes from the feed: `feed.words()` (`ui.screens.orEnglish()`), the day's `names`, the clock strings, `nextIn`, the countdown and spoken phrases, the city and the distance.
  - `:wearApp`'s own resources hold only spec 1.3's system list:
    - the app's name;
    - the Tile's and the complications' picker names and descriptions;
    - the two lines a watch shows before it holds any feed: `open_taqwa_on_phone`, and `update_taqwa_on_watch` when a newer format has been seen (call 5).
  - Those ship in seven folders: `values`, `-ar`, `-fr`, `-tr`, `-in`, `-ur` and `-bn`. Android resources spell Indonesian `in`, as `androidApp/src/main/res` does and `tools/i18n-check.py`'s `ANDROID_LANGS` and plan 5's Task 8 expect; the `values-id` twin is the Compose resources' rule alone.
  - A phrase the phone already has is the phone's own text. Every other one is marked `<!-- draft, to native review (plan 5) -->`.
  - Prose uses ’ (U+2019), never a straight apostrophe.
- **The phone's language, never the watch's:**
  - The layout direction is `feed.ui.rtl`.
  - Numbers are in `digitZero`'s digits, and Today's H:MM:SS in `ringDigitZero ?: digitZero`'s.
  - Capitals only when `latinScript`, cased in `Locale.forLanguageTag(feed.ui.language)`, as `ComplicationFields.nextPrayer` does.
  - Arabic is never tracked: its `letterSpacing` is 0.
- **12 and 24 hours:** a drawn time is `WatchRules.clockText(day, time)`, and a spoken one is `WatchRules.spokenClock(day, time)`, for the day that holds it, with `time` a `WatchTime` (call 9). Sunrise goes through them as the five prayers do. No line passes the bare `clock` to a spoken phrase, or draws it where `clockText` would give a day period.
- **Directions:**
  - Today's ring mirrors as the phone's `RingArc` does: anticlockwise in RTL.
  - The tasbeeh ring fills clockwise in every language.
  - The Qibla dial never mirrors.
  - The pager runs left to right (call 4): the scaffold and the pager are composed under `LayoutDirection.Ltr`, and each page under `WatchTheme(rtl)`.
- **Drawing:**
  - The `faces.js` drawings are the reference for every position, size and colour, in 200 units across the screen's shorter side (`WatchUnits`).
  - Palette: black `#000000`, ink `#F1F3F1`, mist `#8C948F`, slate `#5A625D`, hairline `#232825`, surface `#131614`, accent `#F0B429`.
  - Manrope weights: Light for the count and the countdown, ExtraBold for weight 800, SemiBold for 600 and 700.
  - Counts and times use tabular figures (`fontFeatureSettings = "tnum"`).
  - **Text size (call 15):** each line is a `Line` from its screen's type table, sized by `Line.range(fontScale)` and drawn with `BasicText`'s `TextAutoSize.StepBased` over that range. The range's ends become sp only through `WatchUnits.sp(units, density)`, never by dividing by the font scale. So at font scale 1.0 every size is the drawing's, and a line capped at 1.0 keeps its size at every scale. It sits at its drawn baseline, inside the round screen's chord at that height (`WatchUnits.chordUnits`), with its `maxLines` and `TextOverflow.Ellipsis`.
  - **Nothing the drawings lack:** no `TimeText`, no page indicator and no scroll indicator on any page (call 16).
- **Accessibility:**
  - Every screen and the Tile carry real TalkBack text.
  - Only semantics are added. Nothing drawn changes at default settings.
  - Counts are announced through `stateDescription`.
  - Today's description changes once a minute, never each second.
  - Only the Qibla screen uses live regions (the phone's round-2 rule).
  - **Getting from one screen to the next.** With TalkBack on, one-finger swipes and the bezel belong to TalkBack. A TalkBack user reaches the next page through the pagers' scroll actions, by a two-finger swipe or by TalkBack's own scroll at the end of a page, and reaches the phrase list through the counter's `phrasesAction`. Both pagers stay scrollable to accessibility: no `clearAndSetSemantics` sits on a pager or above one.
  - TalkBack is driven only through `tools/watchface/talkback.sh` (`on`, `walk`, `idle`, `off`, and Task 4's `tap2` and `actions`). A check the harness cannot drive is named for plan 5's device pass, never claimed as heard.
- **Battery:** the compass and the one-second tick run only while their page is the current page and the activity is resumed.
- **Tests:**
  - JVM unit tests, `./gradlew :wearApp:testDebugUnitTest`, with no Robolectric.
  - `./gradlew :watchcore:allTests` on the host and the iOS simulator.
  - `scripts/test.sh` is green at every commit.

## Review Focus

1. **The bezel turned fast, bumped, or turned back.**
   - Expected behaviour:
     - exactly one count for each detent, either way;
     - an undo never goes below 0 and never into the round before;
     - a turn back inside the one-second hold keeps the round;
     - a fast spin across a part's end gives the part's buzz whole, never cut short by the next tick;
     - the pager never takes the bezel from the counter.
   - Tests:
     - Task 1: `undoAtZeroDoesNothing` and `undoOnACompletedSetStaysInItsRound`;
     - Task 6: `RotaryCounterTest` (all), `aTurnBackInsideTheHoldStaysInTheRound`, `aFastTurnAcrossAPartsEndBuzzesItAlone`, `noTickCutsThePartsBuzz` and `noTickCutsTheSetsBuzz`, and Step 5's `dumpsys vibrator_manager` check (FINISHED, never `CANCELLED_SUPERSEDED`).
2. **A feed that runs out, or is newer than the watch, while a screen is open, or before any feed.**
   - Expected behaviour:
     - Today past the last start says "Open Taqwa on your phone", never a negative or frozen countdown;
     - the Tile's timeline ends in that line;
     - a newer feed says "Update Taqwa on your watch" and keeps drawing the last feed;
     - a watch with no feed that has been sent a newer one says "Update Taqwa on your watch" on every page and the Tile, never "Open Taqwa on your phone".
   - Tests:
     - Task 4: `AppStateTest` (all), `aNewerFormatWithNoFeedAsksForTheUpdate` among them;
     - Task 5: `pastTheLastStartThereIsNoRing` and `aNewerVersionTakesTheTimesPlace`;
     - Task 8: `theTimelineEndsInTheNotice`, `aNewerVersionAsksForTheUpdateInTheCitysPlace` and `withNoFeedTheTileSaysWhatThePagesSay`.
3. **The phone's language is not the watch's.**
   - An Arabic phone on an English watch is right to left, in Arabic words and the phone's digits. An English phone on an Arabic watch is left to right.
   - Whatever the phone's language, a swipe left from Today goes to Tasbeeh, and a swipe right closes the app.
   - The system's own strings (the app's name, the line before any feed) are in the watch's language.
   - Tests:
     - Task 4: `theDirectionIsThePhonesNotTheWatchs`, `SystemStringsTest`, and Step 5's swipes on the ar-sa sample;
     - Task 8: `theTileIsLaidOutInThePhonesOrder` over both the feed's and the host's direction, and Step 5's Tile shots with the watch in Arabic;
     - Task 9's matrix, with the watch set to Arabic.
4. **The wrist and its compass.**
   - The cases:
     - a watch on the right wrist (the display turned 180°);
     - a tilted wrist;
     - a dead rotation vector;
     - an implausible field, beside a rotation vector that says it is confident (B6);
     - no magnetometer.
   - Expected behaviour: the dial points true, or says why it cannot, and never shows a needle it has not earned.
   - Tests:
     - Task 7: `WristCompassPolicyTest` (all), `aTrustedVectorStillHearsAnImplausibleField` among them, `aDisplayTurnedHalfAroundTurnsTheHeadingBack`, `TiltTest` and `QiblaModelTest`'s low and best-effort cases;
     - Task 2: `WatchQiblaTest`.
5. **His own phrases.**
   - The cases:
     - 60 characters long, emoji, any script;
     - a target of 0, 1, 1000 or 5000;
     - an id that is a built-in's;
     - a phrase removed on the phone while it is selected on the watch.
   - Expected behaviour: each shows as typed, in two lines at most on the counter, shrinking to 11 units before an ellipsis; it counts to its clamped target; and a removed phrase falls back to After prayer.
   - Tests: Task 6's `PhraseListTest` (all), `aPhraseRemovedOnThePhoneFallsBackToAfterPrayer` and `aLongPhraseOfHisOwnMayTakeTwoLines`, and Task 9 Step 4's shot of his 36-character phrase.

---

### Task 1: The tasbeeh rules move into `:watchcore`, and a turn back takes the last count away

**Files:**
- Move with `git mv`, keeping the package `world.taqwa.app.tasbeeh`:
  - `shared/src/commonMain/kotlin/world/taqwa/app/tasbeeh/{Dhikr,TasbeehEngine,TasbeehPresets}.kt` → `watchcore/src/commonMain/kotlin/world/taqwa/app/tasbeeh/`;
  - `shared/src/commonTest/kotlin/world/taqwa/app/tasbeeh/{TasbeehEngineTest,TasbeehPresetsTest}.kt` → `watchcore/src/commonTest/kotlin/world/taqwa/app/tasbeeh/`.
- Modify:
  - `shared/build.gradle.kts`: commonMain gets `implementation(project(":watchcore"))`, unless it is already there. It is not exported.
  - `shared/src/commonMain/kotlin/world/taqwa/app/feature/tasbeeh/TasbeehViewModel.kt`: its `SET_COMPLETE_HOLD_MS` becomes `= TasbeehEngine.SET_COMPLETE_HOLD_MS`.
  - `shared/src/commonMain/kotlin/world/taqwa/app/settings/TasbeehStore.kt`: its `MAX_TARGET` becomes `= TasbeehPresets.MAX_TARGET`.
- Test: `watchcore/src/commonTest/kotlin/world/taqwa/app/tasbeeh/TasbeehUndoTest.kt`

**Interfaces:**
- Produces, in `:watchcore`, unchanged: `Dhikr`, `DhikrPart`, `TasbeehPreset`, `TasbeehState`, `TapEvent`, `TasbeehEngine.{tap, currentPart, reset, nextRound, partEnds}` and `TasbeehPresets.{DEFAULT_ID, builtIn, default, custom, byId}`.
- Produces, new:
  - `fun TasbeehEngine.undo(state: TasbeehState, preset: TasbeehPreset): TasbeehState?` answers `count − 1` in the same round, and null at count 0. So it never reopens the round before.
  - `const val TasbeehEngine.SET_COMPLETE_HOLD_MS = 1_000L`: the hold before a finished set rolls over. It is the phone view model's value, moved.
  - `const val TasbeehPresets.MAX_TARGET = 1_000`: the bound the phone's store keeps.

- [ ] **Step 1: Move the five files**, with nothing in them changed but their place.
- [ ] **Step 2: Write the failing tests** in `TasbeehUndoTest`, on `TasbeehPresets.default` (33 · 33 · 34):
  - `undoTakesTheLastCountAway`: `(5, round 1)` gives `(4, 1)`.
  - `undoAtZeroDoesNothing`: `(0, 1)` and `(0, 3)` give null.
  - `undoBackOverAPartsEndShowsThePartBefore`:
    - `(33, 1)` gives `(32, 1)`, and `currentPart` is 0;
    - `(34, 1)` gives `(33, 1)`, and `currentPart` is 1.
  - `undoOnACompletedSetStaysInItsRound`: `(100, 1)` gives `(99, 1)`.
  - `countingAgainAfterUndoBuzzesThePartAgain`: `tap((32, 1))` gives `(33, 1)` with `PartComplete(0)`.
- [ ] **Step 3:** Run `./gradlew :watchcore:allTests`. Expected: FAIL, unresolved `undo`.
- [ ] **Step 4:** Implement `undo` and the two constants, and change the two phone constants to refer to them.
- [ ] **Step 5: Run the checks:**
  - `./gradlew :watchcore:allTests :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`, then `scripts/test.sh` and `scripts/ios-build.sh`. Expected: all pass. The moved tests pass unchanged.
  - Then `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, and grep `shared.h` for `Tasbeeh`. Record what the grep finds in the ledger: a type that reaches the header now carries watchcore's prefix.
  - `grep -rn "Tasbeeh" iosApp --include='*.swift'` finds nothing, so no Swift is affected.
- [ ] **Step 6: Commit** with the message "Watch core: the tasbeeh rules move beside the feed, so the watch counts with the phone's own code, and a turn back takes the last count away".

### Task 2: The compass rules move into `:watchcore`, with the dial's state machine and the turn guidance

**Files:**
- Move with `git mv`, keeping the package `world.taqwa.app.qibla`:
  - `shared/src/commonMain/kotlin/world/taqwa/app/qibla/{HeadingFilter,TrueNorth,CompassAccuracyGate,CompassAccuracyRules}.kt` → `watchcore/src/commonMain/kotlin/world/taqwa/app/qibla/`;
  - their tests `{HeadingFilterTest,TrueNorthTest,CompassAccuracyGateTest,CompassAccuracyRulesTest}.kt` → `watchcore/src/commonTest/kotlin/world/taqwa/app/qibla/`.
  - `IphoneSessionReplayTest` and `IphoneCapeTownSession` stay in `:shared`.
- Create in `watchcore/src/commonMain/kotlin/world/taqwa/app/qibla/`:
  - `CompassReading.kt`: `CompassReading`, moved out of `CompassSource.kt` unchanged;
  - `QiblaAngles.kt`, `WatchQibla.kt` and `QiblaGuidance.kt`.
- Modify:
  - `shared/.../qibla/CompassSource.kt`: `CompassReading` leaves it.
  - `shared/.../qibla/QiblaMath.kt`: `angleDelta`, both `isAligned` and the two `ALIGNED_*_DEGREES` constants delegate to `QiblaAngles`, with the same names and signatures (Dev 1's B40 included).
  - `HeadingFilter.kt` and `HeadingFilterTest.kt`: they use `QiblaAngles.angleDelta`.
- Test:
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/qibla/{QiblaAnglesTest,WatchQiblaTest,QiblaGuidanceTest}.kt`;
  - `shared/src/commonTest/kotlin/world/taqwa/app/feature/qibla/WatchDialParityTest.kt`.

**Interfaces:**
- Produces `object QiblaAngles`, with `const val ALIGNED_ENTER_DEGREES = 5.0`, `const val ALIGNED_EXIT_DEGREES = 7.0`, `fun angleDelta(a: Double, b: Double): Double`, `fun isAligned(heading: Double, bearing: Double, thresholdDegrees: Double = ALIGNED_ENTER_DEGREES): Boolean` and `fun isAligned(heading: Double, bearing: Double, wasAligned: Boolean): Boolean`. Every body is `QiblaMath`'s as Dev 1's B40 left it; `QiblaMathTest`'s B40 cases move to `QiblaAnglesTest` unchanged.
- Produces the turn guidance, which is the design of the phone's accessibility round 2: its plan's Task 9 Step 1 code, here and public, so that round 2 uses this one and does not write its own.
  - `enum class Turn { LEFT, RIGHT }` and `data class Guidance(val turn: Turn, val degrees: Int)`.
  - `object QiblaGuidance { const val STEP = 15; fun of(delta: Double): Guidance }`: RIGHT for a delta of 0 or more. The degrees are `|delta|` rounded to a step of 15, between 15 and 180.
  - `class QiblaGuidanceTracker { fun update(nowMillis: Long, delta: Double?): Guidance?; fun reset() }`:
    - it takes a new guidance only when its turn changes, or when `|delta|` is more than 10.5° (`STEP / 2.0 + 3.0`) from the degrees it speaks;
    - it takes one at most once in 1,500 ms;
    - null clears it, and the next delta is taken at once.
- Produces the dial's state machine:
  - `sealed interface DialState`, with:
    - `data object NoSensor`;
    - `data class Searching(val heading: Double, val guidance: Guidance?)`;
    - `data class Aligned(val heading: Double)`;
    - `data class Low(val reason: CompassLowReason)`;
    - `data class BestEffort(val reason: CompassLowReason)`.
  - `data class DialStep(val state: DialState, val buzz: Boolean)`.
  - `class WatchQibla(bearing: Double, filter: HeadingFilter = HeadingFilter(), gate: CompassAccuracyGate = CompassAccuracyGate(), guidance: QiblaGuidanceTracker = QiblaGuidanceTracker())`, with `fun reset()` and `fun onReading(reading: CompassReading): DialStep`.
  - `onReading` is `QiblaViewModel.collectWhileActive`'s body, restated over a bearing:
    - the gate first, then the filter on every sample;
    - BestEffort or Low clears the alignment;
    - Good is Aligned when `QiblaAngles.isAligned(smoothed, bearing, wasAligned)`, `wasAligned` being whether the last step was Aligned, as `QiblaViewModel` keeps it after Dev 1's B40; `buzz` is true only on entry. Otherwise it is Searching, with `guidance.update(reading.timestampMillis, angleDelta(smoothed, bearing))`;
    - every other state gives the tracker null.
  - `reset()` resets all three.

- [ ] **Step 1: Move the files,** and make the two delegations.
- [ ] **Step 2: Write the failing tests:**
  - `QiblaAnglesTest`:
    - `angleDelta(350.0, 10.0)` is 20.0;
    - `angleDelta(10.0, 350.0)` is −20.0;
    - `angleDelta(0.0, 180.0)` is 180.0;
    - `isAligned(18.4, 23.35)` is true, and `isAligned(18.3, 23.35)` is false;
    - with memory: `isAligned(29.0, 23.35, wasAligned = true)` is true (5.65° off), `isAligned(29.0, 23.35, wasAligned = false)` is false, and `isAligned(30.5, 23.35, wasAligned = true)` is false (7.15° off).
  - `QiblaGuidanceTest`: the round-2 plan's cases, verbatim:
    - `of(28.0)` is RIGHT 30, `of(-44.0)` LEFT 45, `of(6.0)` RIGHT 15, `of(179.9)` RIGHT 180 and `of(-179.9)` LEFT 180;
    - the tracker's sequence from `update(0, 31.0)` to `update(3_300, 33.0)`, and null then non-null.
  - `WatchQiblaTest`, with bearing 23.35 and good readings at 50 ms steps:
    - `aGoodHeadingWithinFiveDegreesIsAlignedAndBuzzesOnce`: headings 21, 22 and 24 give Aligned three times, with buzz true, false, false.
    - `leavingAndComingBackBuzzesAgain`: 21, then 60 for 2 s, then 23 gives two buzzes in all.
    - `onceAlignedItHoldsToSevenDegrees` (B40): Aligned at a steady 23, then a steady 29.5 (6.15° off) stays Aligned with no second buzz, and a steady 31 (7.65° off) turns Searching.
    - `searchingSaysWhichWayToTurn`: a steady 340 gives `Searching(340.0, Guidance(RIGHT, 45))`.
    - `aSecondOfLowSamplesIsLowWithItsReason`: INTERFERENCE samples for 1,000 ms give `Low(INTERFERENCE)`.
    - `twentySecondsLowIsBestEffortAndStaysUntilGood`.
    - `resetForgetsTheHeadingTheGateAndTheGuidance`.
  - `WatchDialParityTest` (`:shared`): two sequences go through `QiblaViewModel` and through `WatchQibla(QiblaMath.bearing(capeTown))`:
    - `IphoneCapeTownSession`'s samples, turned into `CompassReading`s as `IphoneSessionReplayTest` does;
    - a scripted run: good, aligned twice, 1.2 s low, 21 s low, good again.

    It drives the view model as Dev 1's branch leaves it (B20's bearing on NoSensor, B40's hysteresis, B41's no needle on LowAccuracy), with a fake `CompassSource` and a fake `Haptics`, as `QiblaViewModelTest` does. At every step the state's kind and heading are the same (Searching, Aligned, LowAccuracy as Low, BestEffort), and the view model's `tick()` count equals the number of buzzes.
- [ ] **Step 3:** Run `./gradlew :watchcore:allTests :shared:testAndroidHostTest`. Expected: FAIL, unresolved `QiblaAngles` and `WatchQibla`.
- [ ] **Step 4:** Implement the three files.
- [ ] **Step 5:** Run `scripts/test.sh` and `scripts/ios-build.sh`. Expected: all pass, the moved tests unchanged.
- [ ] **Step 6: Commit** with the message "Watch core: the compass rules move beside the feed, with the dial's states over the phone's bearing and the turn a screen reader is told".

### Task 3: The feed carries the screens' words, the 12-hour clock as the app writes it, and the ring's digits

**Files:**
- Create:
  - `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchScreenWords.kt`;
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/WatchScreenWordsTest.kt`.
- Modify:
  - `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchFeed.kt` (the new fields, `WatchTime` and the public `WatchSix.of`);
  - `WatchRules.kt` (two helpers made public, and `clockText` and `spokenClock`);
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/WatchRulesTest.kt`;
  - `watchcore/src/commonTest/kotlin/world/taqwa/app/watch/{Fixtures,WatchFeedJsonTest}.kt`;
  - the generated `watchcore/vectors/sample-feed-capetown*.json` and `watch-rules-vectors.json`, and `watchcore/vectors/phone/` if plan 2 has made it.

**Interfaces:**
- Produces `@Serializable data class WatchScreenWords`. Every field is `""` or empty by default; the English value is given in parentheses, and the field names are the JSON keys:
  - **Titles:** `today` ("Today"), `tasbeeh` ("Tasbeeh"), `qibla` ("Qibla").
  - **Notices, once a feed is held:** `openOnPhone` ("Open Taqwa on your phone"), `updateOnWatch` ("Update Taqwa on your watch").
  - **Rows, spoken:** `rowCurrent` ("Current prayer"), `rowPassed` ("Passed"), `rowNext` ("Next prayer").
  - **Tasbeeh:**
    - `round` ("ROUND %1$s"), `of` ("of %1$s"), `bezelHint` ("Turn the bezel or tap"), `reset` ("Reset"), `resetConfirm` ("Reset the count for this dhikr?");
    - `presets: Map<String, String>`, from each built-in id to its name ("after_prayer" to "After prayer" … "subhanallahi_wa_bihamdihi" to "SubhanAllahi wa bihamdihi").
  - **Tasbeeh, spoken:** `countSpoken` ("%1$s of %2$s"), `nextSpoken` ("%1$s. Next, %2$s"), `setCompleteSpoken` ("%1$s. Round complete"), `roundSpoken` ("Round complete. %1$s, round %2$s"), `nameRoundSpoken` ("%1$s, round %2$s"), `countAction` ("Count"), `undoAction` ("Take one away"), `phrasesAction` ("Choose a dhikr").
  - **Qibla:**
    - `facingQibla` ("Facing qibla"), `distance` ("Makkah · %1$s km away"), `bearing` ("%1$s°"), `qiblaDetail` ("%1$s° · %2$s km to Makkah");
    - `cardinals: List<String>` (N, E, S, W);
    - `holdLevel` ("Hold your wrist level"), `needsCalibrating` ("Compass needs calibrating"), `interference` ("Magnetic interference"), `bestEffort` ("Compass unavailable"), `noSensor` ("No compass sensor").
  - **Qibla, spoken:** `turnRight` and `turnLeft`, each a `List<WatchCount>` (`[WatchCount(180, "Turn right about %1$s degrees")]`, and its left).
- Produces `companion object { val ENGLISH: WatchScreenWords }`, holding the values above, and `fun orEnglish(): WatchScreenWords`:
  - every blank string, empty list and empty map is ENGLISH's;
  - `presets` is ENGLISH's map overlaid by the feed's.
- Produces on `WatchUi`, each defaulted, so `WatchFeedJson.VERSION` stays 1:
  - `val screens: WatchScreenWords = WatchScreenWords()`;
  - `val ringDigitZero: Char? = null`: the zero of the phone ring's H:MM:SS, and null means `digitZero`;
  - `val latinScript: Boolean = true`: the app's `isLatinScriptUi()`.
- Produces on `WatchDay`: `val clockTime: WatchSix<String>? = null`: the phone's own `clockTime` with its day period ("4:10 PM"), bare, with no isolate, for all six times. Only a 12-hour phone sends it, and null means `clock`.
- Produces in `WatchFeed.kt`, beside `WatchSix`, the key of a day's six times:
  - `enum class WatchTime { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }`, in the order of the day, with `companion object { fun of(prayer: WatchPrayer): WatchTime }` mapping each prayer to its namesake;
  - `fun <T> WatchSix<T>.of(time: WatchTime): T`, public. It is the accessor every surface uses. `ComplicationFields`' private `WatchSix.of(prayer)` stays as it is (plan 1's freeze), and a later clean-up may point it here.
- Produces on `WatchRules`, public:
  - `fun filled(pattern: String, vararg parts: String): String` (today the private `fill`);
  - `fun inDigits(text: String, zero: Char): String`: maps 0–9 in `text` to the set from `zero`;
  - `fun clockText(day: WatchDay, time: WatchTime): String`: the drawn time, `day.clockTime?.of(time) ?: day.clock.of(time)`;
  - `fun spokenClock(day: WatchDay, time: WatchTime): String`: the time spliced into a spoken sentence. It is `clockText`, wrapped in FSI and PDI (U+2068, U+2069) when it holds anything but digits (`Char.isDigit`) and ':'. That is the phone's `isolatedClock` rule (`shared/.../i18n/BidiIsolate.kt`), so every 24-hour line stays byte for byte what it is.
- Produces `fun WatchFeed.words(): WatchScreenWords = ui.screens.orEnglish()`.
- Produces in `Fixtures`:
  - `english24()` carries `screens = WatchScreenWords.ENGLISH`;
  - `arabicLibya()` carries `ARABIC_SCREENS` and `latinScript = false`. Each field of `ARABIC_SCREENS` is its key's value in `shared/src/commonMain/composeResources/values-ar/strings.xml` (Task 10's table). Where round 2's string table gives one, it is that Arabic. The new phrases are Arabic drafts.
  - `arabicSaudi()` adds `ringDigitZero = '0'`;
  - `english12()`, and the `ar-ly12` sample, carry `clockTime` on every day: each of the day's six `clock` strings with the app's day period after it, " AM"/" PM" in English and " ص"/" م" in Arabic. Day 0's English is ("4:33 AM", "6:01 AM", "12:33 PM", "4:10 PM", "7:04 PM", "8:24 PM"), and its Arabic Asr "4:10 م";
  - `capeTownSample()` carries two phrases of his own: `WatchPhrase("custom_1730000000000", "سبحان الله وبحمده، سبحان الله العظيم", 100)` and `WatchPhrase("custom_1730000000001", "Astaghfirullah wa atubu ilayh", 33)`.

- [ ] **Step 1: Write the failing tests:**
  - `WatchScreenWordsTest`:
    - `blankFieldsTakeTheEnglish`: `WatchScreenWords(round = "الجولة %1$s").orEnglish()` has that `round`, `of` "of %1$s" and all seven `presets`.
    - `everyEnglishPatternHasItsPlaces`:
      - `round`, `of`, `distance` and `bearing` hold `%1$s`;
      - the four two-place spoken phrases and `qiblaDetail` hold `%1$s` before `%2$s`;
      - `cardinals` has 4 entries;
      - `presets`' keys are `TasbeehPresets.builtIn`'s ids, in order.
    - `theWordsTakeAFewKilobytesAtMost`: the UTF-8 size of the Arabic Libya sample's encoding, less the same feed with `screens` at its default, is at most 4,096 bytes. Task 10 measures again in all seven languages.
  - `WatchFeedJsonTest`:
    - `aFeedFromBeforeTheScreensStillReads`: a literal sample without `screens`, `ringDigitZero`, `latinScript` or `clockTime` decodes, with `screens == WatchScreenWords()`, `ringDigitZero` null, `latinScript` true and `clockTime` null.
    - `roundTripKeepsEveryField`, extended to the new fields.
  - `WatchRulesTest`, with FSI and PDI the test's own constants for U+2068 and U+2069:
    - `aTwelveHourTimeIsDrawnWithItsDayPeriod`: `clockText` of day 0's ASR in `capeTown(ui = english12())` is "4:10 PM"; in `capeTown()`, "16:10".
    - `aTwelveHourTimeIsSpokenWhole`: `spokenClock` of the same ASR in `capeTown(ui = english12())` is FSI + "4:10 PM" + PDI, and in the `ar-ly12` sample FSI + "4:10 م" + PDI.
    - `aTwentyFourHourTimeIsSpokenBare`: `spokenClock` of that ASR in `capeTown()` is "16:10", and in `capeTown(ui = arabicSaudi())` "١٦:١٠", with no isolate.
    - `aTwelveHourSunriseCarriesItsDayPeriod`: day 0's SUNRISE in `capeTown(ui = english12())` has `clockText` "6:01 AM" and `spokenClock` FSI + "6:01 AM" + PDI. In `capeTown()` it is "6:01" for both.
    - `everyTimeHasItsOwnSlot`: over `WatchSix("f", "s", "d", "a", "m", "i")`, `of` gives "f", "s", "d", "a", "m" and "i" for `WatchTime.entries` in order; and `WatchTime.of(p).name == p.name` for each `WatchPrayer`.
- [ ] **Step 2:** Run `./gradlew :watchcore:allTests`. Expected: FAIL.
- [ ] **Step 3:** Implement the code, then regenerate with `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`.
- [ ] **Step 4:** Run `./gradlew :watchcore:allTests :wearApp:testDebugUnitTest`, then `scripts/test.sh`. Expected: PASS. `SampleFeedTest` reads the new samples, and the face's tests are unchanged.
- [ ] **Step 5: Commit** with the message "Watch core: the feed carries the words of the watch's screens, the 12-hour times as the app writes them and the digits of the phone's ring".

### Task 4: The watch app's frame: one activity, three pages, the phone's language and the states without a usable feed

**Files:**
- Create, in `wearApp/src/main/kotlin/world/taqwa/app/wear/app/`:
  - `WearMainActivity.kt`, `AppState.kt`, `WatchTheme.kt` (with `WatchColors`, `WatchFonts` and `WatchUnits`), `WatchType.kt`, `Pages.kt` and `WatchHaptics.kt`.
- Create the fonts and strings:
  - `wearApp/src/main/res/font/manrope_{light,regular,semibold,extrabold}.ttf`, copied from `shared/src/commonMain/composeResources/font/`;
  - `wearApp/src/main/res/values-{ar,fr,tr,in,ur,bn}/strings.xml` (Android's `in` for Indonesian, as the phone's own resources; no `values-id`).
- Create the harness: `tools/watchface/app.sh`, which opens a page and shoots it.
- Delete `StatusActivity.kt` and `StatusHeadlineTest.kt`. Their rule moves into `AppState` unchanged: a newer format outranks the rest, with a feed or without (`statusHeadline`, Ruling 13). The debug-only "written" line goes with it, which closes plan 1's parked item (ledger, "StatusActivity gates a debug-only detail").
- Modify:
  - `wearApp/src/main/AndroidManifest.xml`: VIBRATE, the launcher activity, and the comments. "VIBRATE joins when the tasbeeh counter does" is now done.
  - `TaqwaWearApp.kt`: `onAppOpened`.
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/FeedStore.kt`: `clear()`.
  - `wearApp/src/main/res/values/strings.xml`: `tile_label` ("Prayer times") and `tile_description` ("Today's prayers and the next one counted down").
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/DebugFeedReceiver.kt`:
    - `--ez clear true` runs `store.clear()`, then `SampleFeed.hold(filesDir)`, then `ComplicationSources.requestUpdates`. Task 8 adds `TileUpdates.request`. Its result line is `CLEARED`.
    - `--ei version 2` offers the sample with its `version` set to 2. The store turns it away as `NEWER_VERSION`, so the notice is set, and whatever was stored (a feed, or none) stays.
    - `--es city <name>` stores the sample with `place.city` set to `<name>`, for the longest-city shots (Tasks 5 and 8).
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/SampleFeed.kt`: the hold.
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/DebugWearApp.kt`: its KDoc says that a held sample is not installed at start.
  - `tools/watchface/ref.py`: `screen=g-today|g-tasbeeh|g-tasbeeh-list|g-qibla|g-tile`, with `count=`, `round=`, `heading=` and `flash=`, drawn from `faces.js`'s `SCREENS`.
  - `tools/watchface/talkback.sh`: `tap2` and `actions`.
  - `tools/watchface/README.md`.
- Test:
  - in `wearApp/src/test/kotlin/world/taqwa/app/wear/app/`: `AppStateTest.kt`, `WatchUnitsTest.kt`, `WatchTypeTest.kt`, `HapticPatternsTest.kt`, `WearManifestTest.kt`, `SystemStringsTest.kt` and `FontCopiesTest.kt`;
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/FeedStoreTest.kt` (add);
  - `wearApp/src/testDebug/kotlin/world/taqwa/app/wear/SampleFeedTest.kt` (add).

**Interfaces:**
- Consumes `FeedStore.changes` and `newerVersionSeen`, `WatchRules.moment`, `WatchFeed.words()` and `ComplicationSources.requestUpdates`.
- Produces `internal sealed interface AppState`, with:
  - `data class NoFeed(val update: Boolean)`;
  - `data class Ready(val feed: WatchFeed, val spent: Boolean, val update: Boolean)`.

  It comes from `internal fun appState(feed: WatchFeed?, newerVersionSeen: Boolean, now: Long): AppState`:
  - with no feed it is `NoFeed(update = newerVersionSeen)`;
  - `spent` is `WatchRules.moment(feed, now) == null`;
  - `update` is `newerVersionSeen`.
- Produces `@StringRes internal fun noFeedLine(update: Boolean): Int`: `R.string.update_taqwa_on_watch` when `update`, else `R.string.open_taqwa_on_phone`. Every page and the Tile (Task 8) draw `NoFeed` with it.
- Produces `internal fun AppState.Ready.notice(words: WatchScreenWords): String?`: `words.updateOnWatch` when `update`, else `words.openOnPhone` when `spent`, else null. The update outranks the spent line.
- Produces on `FeedStore`, `internal fun clear()`:
  - under the write lock, it deletes the feed, its temporary file, the notice and the notice's temporary file, and plan 2 Task 6's `deliveryFile` and its temporary file;
  - then it sets `changes` to null and `newerVersionSeen` to false;
  - it tells no listener: the debug receiver asks for the redraws itself;
  - it exists for the debug build's `--ez clear true` and for tests, and its KDoc says so.
- Produces in the debug build, on `SampleFeed`, `const val HOLD = "sample.held"`, `fun hold(filesDir: File)` and `fun held(filesDir: File): Boolean`:
  - `installIfEmpty` installs nothing while the hold file exists, so a cleared watch stays empty when its process starts again (`DebugWearApp.onCreate`);
  - `install` deletes the hold file when the store answers `ACCEPTED`, and keeps it otherwise. So a version-2 offer to a cleared watch keeps it feedless;
  - the internal overloads gain `filesDir: File`, so the JVM tests reach them.
- Produces `internal enum class Page { TODAY, TASBEEH, QIBLA }`, and `const val EXTRA_PAGE = "world.taqwa.app.wear.PAGE"`, which takes the page's name.
- Produces `@Composable internal fun WatchTheme(rtl: Boolean?, content: @Composable () -> Unit)`. It wraps each page's content, never the pager (call 4), and provides the colours, the fonts and `LocalLayoutDirection` = `layoutDirectionOf(rtl, system)`.
  - `system` is the activity's own direction, read from `LocalConfiguration.current.layoutDirection` (`View.LAYOUT_DIRECTION_RTL` is Rtl). It is never `LocalLayoutDirection.current`, which the frame has set to Ltr for the pager.
  - So with no feed (`rtl` null), a page has the watch's direction.
- Produces `internal data class WatchUnits(val dp: Float)`, with `companion object { fun of(widthDp: Int, heightDp: Int): WatchUnits }`: 200 units across the shorter side.
  - `fun x(units: Float): Dp` is `(units * dp).dp`: the size the drawing gives, and the target every text size is tested against.
  - `fun sp(units: Float, density: Density): TextUnit` is `with(density) { x(units).toSp() }`. The density's `Dp.toSp` inverts the font-scale converter that text layout applies (call 15), so a size from `Line.range` is drawn at exactly that many units at every font scale. The font scale is applied once, by `range`. A page passes `LocalDensity.current`.
  - `fun chordUnits(y: Float, round: Boolean, inset: Float = 6f): Float` is the width a line at height `y` may take, in units: `2·sqrt(100² − (y − 100)²) − 2·inset` on a round screen (`Configuration.isScreenRound`), and `200 − 2·inset` otherwise.
- Produces in `WatchType.kt` `internal data class Line(val units: Float, val cap: Float, val maxLines: Int = 1, val floor: Float = units)`, with `fun range(fontScale: Float): ClosedFloatingPointRange<Float>` = `floor..units * fontScale.coerceIn(1f, cap)` (call 15).
  - A page draws a `Line` with `BasicText`: its `maxLines`, `TextOverflow.Ellipsis`, and `autoSize = TextAutoSize.StepBased(minFontSize, maxFontSize, stepSize)`. With `density = LocalDensity.current`, `range(density.fontScale)`'s two ends go through `WatchUnits.sp(…, density)`, and the step is 0.5 units through the same call. The step only sets how finely the size is searched, so its own conversion error is no rule. The line sits inside `chordUnits` at its baseline.
  - Each screen's own table holds its lines: `TodayType`, `TasbeehType`, `QiblaType` and `TileType`, each in its model's file.
  - This task's is `FrameType`: `title` = `Line(12f, 1.3f)`, `notice` = `Line(15f, 1.2f, maxLines = 3, floor = 11f)` and `city` = `Line(10.5f, 1.3f)`.
- Produces `internal interface Feel { fun count(); fun partComplete(); fun setComplete(); fun aligned() }`, and `class WatchHaptics(context: Context) : Feel`, with the phone's four effects (`Haptics.android.kt`):
  - `count`: `EFFECT_TICK`, or 20 ms below API 29;
  - `partComplete`: the waveform `HapticPatterns.PART`;
  - `setComplete`: the waveform `HapticPatterns.SET`;
  - `aligned`: one shot of 30 ms.

  Each is sent under `USAGE_TOUCH` from API 33, inside `runCatching`.
- Produces `internal object HapticPatterns`, pure, so the JVM tests reach it: `val PART = longArrayOf(0, 40, 60, 40)`, `val SET = longArrayOf(0, 60, 70, 60, 70, 140)`, `const val PART_MS = 140L` and `const val SET_MS = 400L`. The two lengths are the windows in which Task 6 sends no tick (call 14).
- Produces the page slots, each filled by its own task: `@Composable internal fun TodayPage(feed: WatchFeed, active: Boolean, update: Boolean)`, `TasbeehPage(feed: WatchFeed, active: Boolean)` and `QiblaPage(feed: WatchFeed, active: Boolean)`.
  - `active` is "the current page, and the activity resumed".
  - Here each one draws its title alone (`FrameType.title`), and Today also draws the feed's city under it (`FrameType.city`), or `notice(words)` when there is one.
- Produces `fun TaqwaWearApp.onAppOpened()`: `ComplicationSources.requestUpdates(this)` (Ruling 22's app start). Task 8 adds the Tile, and plan 2 Task 6's `askIfDue(AskWhy.OPENED, now)` joins it (the overlaps above).

**The frame:**
- `WearMainActivity` (the launcher, `taskAffinity=""`):
  - it calls `onAppOpened()` in `onResume`;
  - `setContent` draws `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`, then `AppScaffold(timeText = {})` over `HorizontalPagerScaffold(pagerState, pageIndicator = null)` and a `HorizontalPager` of three pages, in `Page` order, left to right (call 4). Each page's content is `WatchTheme(rtl) { … }`, so the pager never mirrors and the page always follows the phone. The `HorizontalPager`'s `rotaryScrollableBehavior` is left at its default, null, so the bezel never turns a page;
  - no page uses `ScreenScaffold`'s scroll indicator (call 16);
  - it starts on `EXTRA_PAGE`, or TODAY.
- **Each page's state** (call 5):
  - `NoFeed`: every page shows `noFeedLine(update)` as `FrameType.notice`, centred, in the watch's language and direction.
  - `Ready` with `spent` and no `update`: Today shows `words.openOnPhone`, and Tasbeeh and Qibla draw as usual.
  - `Ready` with `update`: every page draws from the last feed. Today shows `notice(words)`, which is `words.updateOnWatch`, in place of the next prayer's clock time, spent or not.
- **A screen reader:**
  - each page carries `paneTitle` = `words.today`, `words.tasbeeh` or `words.qibla`, so a page change is announced;
  - a notice is one node, read as it is written;
  - the `HorizontalPager` keeps its own scroll semantics, so TalkBack can turn its pages (Global Constraints, "Getting from one screen to the next").

- [ ] **Step 1: Write the failing tests:**
  - `AppStateTest`, on `Fixtures.capeTownSample()` (via `TestFeeds`):
    - `withNoFeedTheWatchAsksForThePhone`: `appState(null, false, now)` is `NoFeed(update = false)`, and `noFeedLine(false)` is `R.string.open_taqwa_on_phone`.
    - `aNewerFormatWithNoFeedAsksForTheUpdate`: `appState(null, true, now)` is `NoFeed(update = true)`, and `noFeedLine(true)` is `R.string.update_taqwa_on_watch`. This is `StatusHeadlineTest.aNewerFormatAsksForAnUpdateWhateverElseIsStored`'s case without a feed, kept.
    - `aNewerVersionKeepsTheLastFeedAndAsksForTheUpdate`: at 14:58 on day 0, `appState(sample, true, now)` is `Ready(sample, spent = false, update = true)`, and its `notice` is "Update Taqwa on your watch".
    - `pastTheLastStartTheFeedIsSpent`: 31 days on it is spent, and its `notice` is "Open Taqwa on your phone". At 14:58 on day 0 it is not.
    - `theUpdateOutranksTheSpentNotice`: spent with `update` true, the `notice` is "Update Taqwa on your watch".
    - `aFeedInPlaceNeedsNoNotice`: at 14:58, with nothing newer, the `notice` is null.
  - `WatchUnitsTest`:
    - 227 × 227 dp gives 1.135 dp a unit; 192 × 240 gives 0.96;
    - `aLineIsDrawnAtItsUnitsWhateverTheFontScale`: on 227 × 227, for `Density(2f, s)` at s = 1.0, 1.15, 1.24 and 1.3, `with(d) { units.sp(u, d).toDp() }` is `units.x(u)` within 0.01 dp, for u = 46 (52.21 dp), 38 (43.13 dp) and 10.5. This runs Compose's real converter on the JVM. On Android, `Density(density, fontScale)`'s `toSp` and `toDp` are `FontScaling`'s defaults, which consult `FontScaleConverterFactory` from 1.03, and the factory is plain Kotlin over `androidx.collection`, so it needs no Robolectric.
    - `theConverterIsNotLinear`, which guards the test above: at `Density(2f, 1.3f)`, `(52.21f / 1.3f).sp.toDp()` is below 41 dp (40.16 measured). The old `units * dp / fontScale` formula would draw the count about 12 dp small.
    - `theChordAt184Is108Units`: `chordUnits(184f, round = true, inset = 0f)` is 108.5 within 0.1, and `chordUnits(100f, round = true)` is 188;
    - `aSquareScreensChordIsItsWidth`: `chordUnits(184f, round = false)` is 188.
  - `WatchTypeTest`:
    - `atTheDefaultSizeEveryLineIsTheDrawings`: `Line(10.5f, 1.3f).range(1.0f)` is 10.5..10.5;
    - `aSmallerSizeNeverShrinksALine`: `range(0.85f)` is 10.5..10.5;
    - `aLargerSizeGrowsToTheCap`: `range(1.15f)` is 10.5..12.075 within 1e-3, and `range(2.0f)` is 10.5..13.65;
    - `aLineCappedAtOneNeverGrows`: `Line(38f, 1f).range(2.0f)` is 38..38;
    - `aFloorLetsANamedLineShrinkToFit`: `FrameType.notice.range(1.0f)` is 11..15, with `maxLines` 3.
  - `HapticPatternsTest.theQuietWindowsAreTheWaveformsLengths`:
    - `PART.sum()` is `PART_MS` (140), and `SET.sum()` is `SET_MS` (400);
    - both equal the two `longArrayOf` lists in `shared/src/androidMain/kotlin/world/taqwa/app/qibla/Haptics.android.kt`, read from the file, so the watch's buzzes stay the phone's.
  - `FeedStoreTest`:
    - `clearForgetsTheFeedAndTheNotice`: after a stored sample and a version-2 write, `clear()` leaves `changes` null and `newerVersionSeen` false, with no feed, notice or temporary file on disk. A new `FeedStore` over the same file reads null and false.
    - `clearTellsNobody`: the listener is not told.
  - `SampleFeedTest`:
    - `aHeldSampleIsNotInstalled`: with the hold, `installIfEmpty` answers false, and the store stays empty.
    - `anAcceptedInstallLiftsTheHold`: `install` answers `ACCEPTED`, and `held` is false.
    - `aNewerOfferKeepsTheHold`: the sample offered at version 2 answers `NEWER_VERSION`, and `held` is still true.
  - `theDirectionIsThePhonesNotTheWatchs`: a Compose test cannot run without Robolectric, so the rule is the pure `layoutDirectionOf(rtl: Boolean?, system: LayoutDirection)`, tested: true is Rtl, false is Ltr, and null is the system's.
  - `WearManifestTest.theWatchAsksOnlyToVibrate`, on `src/main/AndroidManifest.xml`:
    - `uses-permission` is exactly `{android.permission.VIBRATE}`;
    - no `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` or `BODY_SENSORS`;
    - the launcher is `.app.WearMainActivity`, and no `StatusActivity` is declared.
  - `SystemStringsTest`:
    - every key in `values/strings.xml` is in each of the six other folders, with the same `%n$s` places, `open_taqwa_on_phone` and `update_taqwa_on_watch` among them;
    - there is no `values-id` folder: Indonesian is `values-in`, as in `androidApp/src/main/res`;
    - no value holds a straight apostrophe.
  - `FontCopiesTest`: each `manrope_*.ttf` is byte-identical to its phone file.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement the frame.**
  - The seven languages' strings:
    - `app_name` is the phone's own in each language;
    - the rest are drafts, as the Global Constraints say. That includes plan 1's picker names and previews.
  - Extend `ref.py`, `DebugFeedReceiver` and `SampleFeed`, and write `app.sh`.
  - Extend `talkback.sh`:
    - `tap2 x y`: two one-finger taps at (x, y) through the console's `event mouse`, the second within TalkBack's double-tap window (`ViewConfiguration`'s 300 ms) of the first. If separate `adb emu` calls are too slow for that, the four events go through one console session: port 5558, after `auth` with `~/.emulator_console_auth_token`. The README records the gap measured.
    - `actions`: the one-finger gesture that Wear TalkBack gives its actions menu, through `event mouse`. Read the gesture from TalkBack's own settings on the emulator ("Customize gestures"), and record it in the README.
    - The usage line names both. Task 6 Step 5 proves them on the counter.
  - Add `app.sh`, the receiver's three new extras and the two TalkBack commands to the README's table.
- [ ] **Step 4: Run the checks:**
  - `./gradlew :wearApp:testDebugUnitTest :wearApp:assembleDebug :wearApp:assembleRelease :wearApp:lint`. Expected: PASS.
  - The merged release manifest (`processReleaseMainManifest`'s output) lists only VIBRATE and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- [ ] **Step 5: Verify on emulator-5558,** with no paired phone running (Global Constraints):
  - Install, open the app, and swipe through the three titles. Expected:
    - no page shows a time, page dots or a scroll bar;
    - `adb -s emulator-5558 shell uiautomator dump` holds no node with the watch's clock text, and its pager node has `scrollable="true"`;
    - `input rotaryencoder scroll --axis SCROLL,1` on Today and on Qibla does not change the page (erratum p3 #10: the shell refuses `-a SCROLL=1`, so the check would pass on an error). First prove that the command reaches the app in front: the same events turn Settings' own list.
  - **The pager's direction under a right-to-left feed** (call 4). Store the ar-sa sample (`DEBUG_FEED` with `--es sample ar-sa`), and take the coordinates from `adb -s emulator-5558 shell wm size` (454 × 454 below). Then:
    - open the app on Today, and swipe left, `adb -s emulator-5558 shell input swipe 400 227 60 227 200`. Expected: `uiautomator dump` holds the feed's Arabic Tasbeeh title, and no longer Today's;
    - reopen on Today, and swipe right from the left edge, `input swipe 5 227 420 227 300` (as built: `20 227 420 227 200` closed the app two or three times in four, in either language, and this one every time). Expected: the app closes, and `adb -s emulator-5558 shell dumpsys activity activities | grep -i resumed` no longer names `WearMainActivity`;
    - the same two swipes on the en sample give the same two results;
    - a plain `DEBUG_FEED` at the end.
  - `--ez clear true`, with the app open: every page says "Open Taqwa on your phone" at once. Then `adb -s emulator-5558 shell am force-stop world.taqwa.app`, and reopen: it still does, because the sample is held.
  - `--ei version 2`, still with no feed: every page says "Update Taqwa on your watch". Force-stop and reopen: it still does, because the notice is on disk.
  - A plain `DEBUG_FEED`, in the `-n` form: `ACCEPTED`, the hold and the notice gone, and Today shows its title and "Cape Town".
  - `--ei version 2` over that feed: Today says "Update Taqwa on your watch" under its title, in the city's place. Tasbeeh and Qibla draw their titles.
  - A plain `DEBUG_FEED` again, so the next task starts from the sample.
  - The face is unchanged: `tools/watchface/shoot.sh` at 14:58 on Arch equals the committed pass within 3 levels.
- [ ] **Step 6: Plan 2 Task 7.** If it has not run yet, write the three line edits from the overlaps (checks 1, 7 and 8) into the ledger, under "Plan 3", for the Lead to apply before it runs. If it has run, write that nothing changes.
- [ ] **Step 7: Commit** with the message "Wear: the watch app's own screens begin: one activity, three pages in the phone's language and direction, and the lines for a watch with no feed or an old one, a newer format first as the status screen had it".

**As built (30 October, Lane W):**
- `WatchHaptics.count` is `EFFECT_TICK` alone. The watch's minSdk is 30, so lint's `ObsoleteSdkInt` refuses the phone's branch for API 28 and below as dead code. `HapticPatterns` also holds `ALIGNED_MS` (30), the phone's `tick`, which `HapticPatternsTest` reads from the phone's file beside the two waveforms.
- `AppState` has `ready(feed, newerVersionSeen, now)` beside `appState`. The frame takes the no-feed branch from `appState`; Today sees for itself, on its own clock, whether the feed is spent.
- The frame's drawing helpers, for Tasks 5 to 7: `UnitsBox` (a page in the drawing's 200 units, each line kept at its own size, so a screen reader's focus is the line's), `Modifier.atUnits(y, centred)`, and `LineText`. `LineText` sizes a `Line` by `range` with `TextAutoSize.StepBased`, sets it inside the chord at its line's far edge, and tracks it unless it is in Arabic script (`trackingFor`). `WatchFonts.weight` turns `faces.js`'s weights into Manrope's four, with 700 as SemiBold. `systemDirectionOf` reads the watch's direction from its configuration, and `pageOf` reads `EXTRA_PAGE`.
- **The strings.** The phone's own words are used where `androidApp` has them: `app_name` (also for `complication_mark` and its description), `widget_small_label` for `complication_next_prayer`, and `widget_medium_label` for `tile_label`. Every other string is a draft. `SystemStringsTest` holds both rules. The drafted previews use each language's own digits as the phone writes them: Bengali in Bengali, the rest in Western.
- **Erratum p3 #3, how the screens' shots in another language or clock are checked.** `faces.js` draws the screens in English and on 24 hours only. So `ref.py`'s `textmask=` writes where the drawing's words lie, and `compare.py`'s `mask=` leaves them out of the count. Each line is masked over its own box and the room another language may take on its rows, which stops inside the screen's ring, so the rings, the dial's ticks, needle and Kaaba marker, and the pips are compared (review round 1: full-width bands had hidden the marker and most of the dial; README, "What the mask leaves compared"). The words themselves are read from `uiautomator dump`. `ref.py`'s `rtl` draws Today's ring anticlockwise, and `compare.py`'s `mirror` turns a right-to-left shot round for a screen whose whole layout mirrors, such as the Tile's rows. Tasks 5, 6, 8 and 9 compare their en12, ar-ly12 and ar-sa shots this way (`tools/watchface/README.md`, "The watch app's screens").
- **TalkBack.** Wear TalkBack 16 gives the actions menu no gesture of its own in "Customize gestures". "Swipe down then right" opens the TalkBack menu, so `talkback.sh actions` draws that. Whether the watch's menu lists the counter's actions is for Task 6 Step 5 to see. `tap2` goes through one console session: its taps last 54 and 62 ms, 125 ms apart (measured with `getevent`).

### Task 5: Today: the phone's ring, and the day's list beneath it

**Files:**
- Create, in `wearApp/src/main/kotlin/world/taqwa/app/wear/today/`: `TodayModel.kt` (pure, with `TodayType`) and `TodayPage.kt`.
- Test:
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/today/TodayModelTest.kt`;
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/LongestCity.kt` (test helper: `fun name(): String`, the name with the most UTF-8 bytes in `../shared/src/commonMain/composeResources/files/cities.csv` and `city-names-*.csv`, read from the module directory as `FontCopiesTest` reads the phone's fonts. On 24 October it is a Bengali name of 33 characters and 91 bytes. Task 8 uses it too.)

**Interfaces:**
- Consumes `WatchRules.{moment, today, litOnList, litOnGlance, spokenCountdown, digits, inDigits, filled, clockText, spokenClock}`, Task 3's `WatchTime` and `WatchSix.of(time)`, `WatchFeed.words()`, and Task 4's `Line` and `AppState.Ready.notice`.
- Produces `internal enum class RowStatus { CURRENT, PASSED, UPCOMING }` and `internal enum class LitRule { LIST, GLANCE }`. LIST is `litOnList`, the Prayer screen's rule. GLANCE is `litOnGlance`, the widgets' rule, for the Tile.
- Produces `internal data class RowModel(val name: String, val time: String, val status: RowStatus, val small: Boolean, val spoken: String, val state: String?)`.
- Produces `internal fun dayRows(feed: WatchFeed, now: Long, rule: LitRule): List<RowModel>`. These are the six rows of `WatchRules.today(feed, now)`'s day, one for each `WatchTime.entries`, in that order (since the owner's decision of 9 November 2026, Sunrise is left out while the phone's Show sunrise is off, `ui.showSunrise`, leaving the five prayers):
  - **Name:** `day.names.of(time)`.
  - **Status:** CURRENT for the lit prayer; PASSED when its start is at or before `now`; UPCOMING otherwise.
  - **Sunrise** is `small` and is never CURRENT.
  - **Time:** `clockText(day, time)`, Sunrise's included.
  - **Spoken:** the phone's `prayerRowDescription`, the name and `spokenClock(day, time)` joined by ", ".
  - **State:** `rowCurrent` for CURRENT, `rowPassed` for PASSED, `rowNext` for the next start, and null otherwise.
- Produces `internal data class RingModel(val label: String, val countdown: String, val time: String, val progress: Float, val rtl: Boolean, val spoken: String, val notice: Boolean)`.
- Produces `internal data class TodayModel(val ring: RingModel, val rows: List<RowModel>, val city: String)`, and `internal fun today(feed: WatchFeed, nowSeconds: Long, update: Boolean = false): TodayModel?`. It is null when the feed is spent.
  - `label` is `filled(nextIn, name)`, in capitals under `latinScript`.
  - `countdown` is `H:MM:SS`, the hours unpadded, in `inDigits(…, ringDigitZero ?: digitZero)`.
  - `time` is the next start's `clockText(day, WatchTime.of(next.prayer))`, from its own day. With `update` it is `words.updateOnWatch` instead, and `notice` is true (call 5).
  - `spoken` is `spokenCountdown(feed, name, spokenClock(day, WatchTime.of(next.prayer)), secondsLeft)`, or `"$label $time"` when the feed has no spoken words. With `update`, `words.updateOnWatch` follows it after the phone's comma: "، " when `rtl`, else ", " (`ComplicationFields.spokenSeparator`'s rule).
  - `city` is `place.city`, whole. The layout cuts it, never the model.
- Produces `internal object TodayType` (call 15):
  - `label` = `Line(10.5f, 1.3f)`, `countdown` = `Line(38f, 1f)` and `time` = `Line(13f, 1.3f)`;
  - `notice` = `Line(13f, 1.2f, maxLines = 2, floor = 9f)`, the update in the time's place;
  - `rowName` and `rowTime` = `Line(12f, 1.3f)`, `sunrise` = `Line(10f, 1.3f)`, and `city` = `Line(10.5f, 1.3f)`.
  - A row's pitch grows with its text: 21 units (17 for Sunrise), times the name's size as drawn over its `units`.

**The page:** a `TransformingLazyColumn`, with no scroll indicator. Its first item is the ring, at the screen's full height (`gToday`):
- the ring: the track and the arc at r 90, 7.4 units wide, mirrored in RTL;
- the label at y 78 (`TodayType.label`), ExtraBold, in the accent, tracked 1.6 (0 in Arabic);
- the countdown at y 117 (`TodayType.countdown`), Light, ink;
- the time at y 137 (`TodayType.time`), SemiBold, mist. A notice there is `TodayType.notice`, centred on y 140, in the accent;
- the chevron at y 160–166, in slate.

Then the rows, and the city, as `gTile` draws them, with pips by `pip()`. The city is one line inside its chord, cut with an ellipsis. The rotary scrolls the column.

Spent (`today(…)` is null), the page is `notice(words)` alone, as Task 4's frame draws it: the update when a newer format has been seen, else "Open Taqwa on your phone".

The tick runs once a second, on the second, only while `active`.

For a screen reader:
- the ring item is `clearAndSetSemantics { contentDescription = ring.spoken }`;
- each row is `clearAndSetSemantics { contentDescription = spoken; stateDescription = state }`, as the phone's `PrayerTimeline` row does;
- the chevron is not read.

- [ ] **Step 1: Write the failing tests,** on `Fixtures` via `TestFeeds`:
  - `atTwoFiftyEightTheRingCountsDownToAsr`, at 14:58:24 on day 0:
    - label "ASR IN", countdown "1:11:36", time "16:10";
    - progress 8,724/13,020 within 1e-4;
    - spoken "Asr in 1 hour 11 minutes, at 16:10".
  - `arabicKeepsTheNameAndMirrorsTheRing`: Libya gives label "متبقٍّ على العصر" and `rtl` true.
  - `saudiTimesInArabicIndicAndTheRingInWestern`: time "١٦:١٠", countdown "1:11:36".
  - `twelveHourRowsCarryTheDayPeriod`: `english12` gives Asr's row the time "4:10 PM" and the spoken "Asr, " + FSI + "4:10 PM" + PDI (U+2068, U+2069).
  - `aTwelveHourSunriseRowCarriesItsDayPeriod`: `english12` at 14:58 gives the Sunrise row (the second) the time "6:01 AM", `small` true, and the spoken "Sunrise, " + FSI + "6:01 AM" + PDI. Every one of the six rows' times ends in " AM" or " PM".
  - `twelveHourSpeechSaysTheDayPeriod`: `english12` at 14:58:24 gives the ring the time "4:10 PM" and the spoken "Asr in 1 hour 11 minutes, at " + FSI + "4:10 PM" + PDI.
  - `aNewerVersionTakesTheTimesPlace`: at 14:58:24 with `update` true, the time is "Update Taqwa on your watch", `notice` is true, the countdown is still "1:11:36", and the spoken is "Asr in 1 hour 11 minutes, at 16:10, Update Taqwa on your watch".
  - `atTheLargestFontTheCountdownKeepsItsSize`: `TodayType.countdown.range(1.3f)` is 38..38, and `TodayType.label.range(1.3f)` is 10.5..13.65.
  - `theLongestCityIsKeptWhole`: with `place.city` = `LongestCity.name()`, `today(…).city` is that name, unchanged, and `TodayType.city.maxLines` is 1.
  - `theListFollowsThePrayerScreensRule`:
    - at 14:58, Fajr is PASSED, Sunrise is passed and small, Dhuhr is CURRENT with state "Current prayer", and Asr is UPCOMING with state "Next prayer";
    - at 06:40, no row is CURRENT;
    - at 04:10 on day 1, no row is CURRENT.
  - `theGlanceRuleKeepsFajrUntilDhuhr`: `dayRows(…, 06:40, GLANCE)` has Fajr CURRENT.
  - `afterIshaTheNextIsTomorrowsFajr`: at 22:30, label "FAJR IN", time "4:33", and day 0's rows with Isha CURRENT.
  - `aFridayNamesJumuah`: day 6 (Friday 23 October; the sample's day 0 is Saturday 17 October, since it starts yesterday) at 11:00 gives "JUMUʿAH IN", and day 5, a Thursday, gives "DHUHR IN".
  - `pastTheLastStartThereIsNoRing`: `today(…)` is null.
  - `aRowIsSaidAsThePhoneSaysIt`: Asr's spoken text is "Asr, 16:10".
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the model, then the page.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Verify on emulator-5558** with `tools/watchface/clock.sh 14:58`, the samples en, en12, ar-ly12 and ar-sa, and `app.sh today`. Expected:
  - each shot matches `ref.py screen=g-today` within 3 px at 454 px, with the accent exact;
  - the list matches `gTile`'s rows;
  - at 22:30, Fajr is next;
  - `--es city` with `LongestCity`'s name: the list ends in one line of it, cut with an ellipsis inside the round edge;
  - TalkBack on (`talkback.sh on`): `walk 3` says the ring's sentence first, with no time read before it, then "Fajr, 4:33, Passed" and so on;
  - **On past the last row:** from the ring, `walk 9` goes through the six rows and the city, and then moves on to the next page: TalkBack scrolls the pager and says Tasbeeh's pane title. Write the lines heard into the ledger.
    - If the walk stops at the city, the pager is reached only by the two-finger swipe. Record that in the ledger and tell the Lead, who decides whether Today's last item needs an action of its own. The two-finger page change is in plan 5's Galaxy pass either way;
  - `idle 10` says nothing;
  - TalkBack is off at the end, and a plain `DEBUG_FEED` puts the sample's city back.
- [ ] **Step 6: Commit** with the message "Wear: Today, the phone's ring counting down to the next prayer and the day's times beneath it, each said as the phone says it".

**As built (30 October, Lane W):**
- **The 12-hour forms (erratum p3 #7)** are Task 3's: a no-break space before the day period ("4:10\u00a0PM"), so `TodayModelTest` writes its 12-hour times with an NBSP constant, and "every row ends in ' AM' or ' PM'" is held as NBSP + AM or PM.
- **The model** is as the Interfaces say, with what the tests found besides: capitals only under `latinScript`, cased in the feed's language (`tr` gives "ASR İN"); under a minute the ring says the phone's `countdownSoon`; a feed with no spoken words says "ASR IN 16:10"; a time has passed from its own instant on. "Day 6 (Friday 23 October)" is the sample's `days[6]`, `TestFeeds.at(5, 11, 0)`.
- **The list is a second screen** (`TodayLayout`): 49.5 units of room above the first row, each row's baseline 14.5 units below its top, and the city's room 28.5 units with its baseline at 12.5. So at the drawing's size it is 200 units, and scrolled to its end it is `gTile`'s rows at 64, 85, 102, 123, 144 and 165 and the city at 184. A row's pitch follows its name's size as drawn, which the row reports through `onTextLayout`. Each item's edges are the list's, rounded to pixels from its top (`TodayLayout.px`), because rounding each item's height alone put the rows 2 to 3 px high on the device.
- **The times are a column with one edge** (`TodayLayout.timeEnd`): 150, as drawn, unless the widest row's name and time need more room, when the whole column moves towards the round edge, as far as the narrowest row allows (Isha's, 167.3 units). A name with less room shrinks to its line's size, then ends in an ellipsis. Without this, a 12-hour Friday's "Jumuʿah" beside "12:33 PM" would be cut, and on 12 hours Maghrib's time alone stood 7 px right of the others. The gap between a name and its time is 3 units.
- **The ring's clock time is proportional** (no `tnum`), as the phone's ring writes it (`TaqwaText.caption`) and `gToday` draws it: tabular, it drew 6 px wider than the drawing. The countdown and the rows' times are tabular.
- **The spent page** is Task 4's frame, its title and the notice (`NoticePage` in `Pages.kt`). `TodayPage` now lives in `today/TodayPage.kt`, and `WatchType.farEdge` is internal, for the rows' round edges.
- **`LongestCity`** reads the phone's city files from the module directory, as `FontCopiesTest`'s fallback does. They are not a declared test input, because Task 5 does not edit `wearApp/build.gradle.kts` (the lanes). Task 8 edits it and uses `LongestCity` too, so it can add them.
- **Step 5 (erratum p3 #3):** the en shots are compared whole, by each line's box; en12, ar-ly12 and ar-sa are compared with `ref.py`'s `textmask` (`rtl` for the Arabic rings), and their lists with `compare.py mirror`. Their words are read from `uiautomator dump`. The ring is shot at the drawing's 14:58:24: the clock is set to 14:58:22, and the shot is taken once the device's clock reads :24. The list's end is reached with `input rotaryencoder scroll --axis SCROLL,-4` four times (erratum p3 #10's syntax; negative turns the list down).
- **TalkBack (Step 5):** opening the page, TalkBack says "Taqwa", then the ring's sentence. Wear TalkBack 16 says a row's state before it ("Passed. Fajr, 4:33"), which is its own default order (state, name, type). The nodes are the phone's: the content description, then the state. `walk 9` from the ring reads the six rows and the city, then reaches the end of the list and wraps. It does not scroll the pager on to Tasbeeh, so the next page is reached by the two-finger swipe. This is recorded in the ledger for the Lead. Mid-minute, `idle 10` hears nothing. Across a minute, the focused ring is read again once, because its description changes once a minute, as the phone's ring's does.

### Task 6: Tasbeeh: the counter, the bezel and its undo, the haptics, his phrases, and the counts kept on the watch

**Files:**
- Create, in `wearApp/src/main/kotlin/world/taqwa/app/wear/tasbeeh/`:
  - `TasbeehMemory.kt`, `WatchTasbeeh.kt`, `RotaryCounter.kt`, `TasbeehSpeech.kt`, `PhraseList.kt`;
  - `TasbeehPage.kt`, the counter, and `PhrasesPage.kt`.
- Test, in `wearApp/src/test/kotlin/world/taqwa/app/wear/tasbeeh/`: `WatchTasbeehTest.kt`, `RotaryCounterTest.kt`, `TasbeehSpeechTest.kt`, `PhraseListTest.kt` and `TasbeehCodecTest.kt`.

**Interfaces:**
- Consumes Task 1's engine, with `undo` and `SET_COMPLETE_HOLD_MS`, Task 4's `Feel`, `HapticPatterns` and `Line`, and `WatchFeed.words()`.
- Produces `internal interface TasbeehMemory { fun selected(): String?; fun select(id: String); fun state(id: String): String?; fun save(id: String, raw: String) }`.
  - `class PrefsTasbeehMemory(context: Context) : TasbeehMemory` uses the `SharedPreferences` file `tasbeeh`, with the keys `selected` and `count.<id>`.
  - The tests use an in-memory fake.
- Produces `internal object TasbeehCodec`:
  - `fun encode(state: TasbeehState): String` writes `"<count>,<round>"`;
  - `fun decode(id: String, raw: String?, total: Int): TasbeehState` reads it back. Malformed input gives `(0, 1)`, the count is held to 0..total, and the round is at least 1.
- Produces `internal object PhraseList { fun of(feed: WatchFeed): List<TasbeehPreset> }`:
  - `TasbeehPresets.builtIn` first, then `feed.tasbeeh` in its order, as `TasbeehPresets.custom(id, phrase, target.coerceIn(1, TasbeehPresets.MAX_TARGET))`;
  - a blank phrase is skipped, and so is an id that is a built-in's or one already listed.
- Produces `internal data class TasbeehUi(val preset: TasbeehPreset, val state: TasbeehState, val currentPart: Int, val ticks: List<Float>, val flash: TapEvent?)`.
- Produces `internal class WatchTasbeeh(memory: TasbeehMemory, feel: Feel, scope: CoroutineScope, clock: () -> Long = SystemClock::uptimeMillis)`, with `val ui: StateFlow<TasbeehUi>` and `fun setPhrases(list: List<TasbeehPreset>)`, `fun count()`, `fun undo()`, `fun turn(detents: Int)`, `fun select(id: String)`, `fun reset()` and `fun flush()`. The tests pass `clock = { testScheduler.currentTime }`.
  - `turn` applies one count for each positive detent and one undo for each negative one, in order, to the state, so every count is counted.
  - These are `TasbeehViewModel`'s rules, restated:
    - A set's end holds for `SET_COMPLETE_HOLD_MS`, then rolls over to `nextRound`. A count, an undo, a selection or a reset cancels the hold.
    - The write is debounced `WRITE_DEBOUNCE_MS` = 300.
    - `flush` settles a hold first, so what is stored is the round about to open.
    - An undo counts as a tick, and is silent when `TasbeehEngine.undo` answers null.
    - `setPhrases` falls back to `TasbeehPresets.DEFAULT_ID` when the selected id has gone.
  - **The haptics, the watch's own rule (call 14):**
    - each `count()`, `undo()` or `turn(n)` sends at most one haptic: the strongest event it crossed. `SetComplete` sends `setComplete`, else `PartComplete` sends `partComplete`, else a count or an undo sends `count`;
    - a `partComplete` or `setComplete` is always sent, and starts a quiet window of `HapticPatterns.PART_MS` (140) or `SET_MS` (400) from `clock()`;
    - no `count` is sent inside a quiet window. The count itself is still counted.
- Produces `internal object TasbeehType` (call 15), in `WatchTasbeeh.kt`:
  - `fun dhikr(transliterated: Boolean): Line`: `Line(16.5f, 1.15f, maxLines = 1)` when `transliterated`, else `Line(16.5f, 1.15f, maxLines = 2, floor = 11f)`. A long phrase of his own takes the transliteration's place as its second line, and shrinks to fit down to 11 units before an ellipsis;
  - `transliteration` and `round` = `Line(10f, 1.3f)`, `count` = `Line(46f, 1f)`, `of` = `Line(11f, 1.3f)`, and `hint` = `Line(10f, 1f)`;
  - on the phrases page, `title` = `Line(12.5f, 1.3f)`, `rowName` = `Line(11f, 1.3f)` and `rowTarget` = `Line(10f, 1.3f)`. A row's height grows with its name: 25 units times the size drawn over 11.
- Produces `internal class RotaryCounter(private val detentPx: Float, private val idleResetMillis: Long = 500)`, with `fun onScroll(pixels: Float, uptimeMillis: Long): Int`.
  - It answers the signed whole detents to apply. Positive is clockwise, and counts.
  - The remainder is dropped after `idleResetMillis` without a scroll, or when the direction changes.
  - `detentPx` is `ViewConfiguration.get(context).scaledVerticalScrollFactor`: one `AXIS_SCROLL` unit, which is one click of the Galaxy's bezel.
- Produces `internal object TasbeehSpeech`, which is round 2's rules (its spec §1):
  - `fun name(ui: TasbeehUi, words: WatchScreenWords, digits: (Int) -> String): String` is the dhikr's spoken name: `words.presets[dhikr.id]`, or the phrase as typed. From round 2 on, it is `nameRoundSpoken(name, round)`.
  - `fun state(ui: TasbeehUi, words: WatchScreenWords, digits: (Int) -> String): String` is `countSpoken(count, total)`. At a part's end it is `nextSpoken(…, the next part's name)`; at the set's end, `setCompleteSpoken(…)`; at count 0 of round 2 or later, `roundSpoken(…, round)`.
  - `fun actions(words: WatchScreenWords): List<CounterAction>` is the counter's custom actions, in order: `CounterAction(words.undoAction, UNDO)` and `CounterAction(words.phrasesAction, PHRASES)`, with `internal data class CounterAction(val label: String, val does: CounterDoes)` and `internal enum class CounterDoes { UNDO, PHRASES }`. The page turns each into a `CustomAccessibilityAction`.

**The pages** (`gTasbeeh`, `gTasbeehList`). The Tasbeeh page is a `VerticalPager` of two pages, the counter and the phrases. The pager is given `rotaryScrollableBehavior = null`: Wear foundation's default (`RotaryScrollableDefaults.snapBehavior`) would turn to the phrases on a bezel click (call 16).

- **The counter:**
  - The ring: r 92, 5.6 units wide, clockwise always. Its notches sit at the parts' ends, in the track's colour, and show once the arc has passed them. A flash halo, 10 units for a part and 14 for the set at alpha 0.22, lasts 240 or 520 ms.
  - The dhikr at y 56 (`TasbeehType.dhikr`), weight 700, the system font.
  - The transliteration at y 72 (`TasbeehType.transliteration`), SemiBold, mist, only under `latinScript`.
  - "ROUND n" at y 92 (`TasbeehType.round`), ExtraBold, in the accent, tracked 1.3 (0 in Arabic).
  - The count at y 134 (`TasbeehType.count`), Light, ink, in `digitZero`'s digits.
  - "of N" at y 151 (`TasbeehType.of`), SemiBold, mist.
  - The part dots at y 168, 13 units apart.
  - The bezel hint at y 184 (`TasbeehType.hint`), only at count 0 of round 1.
  - A tap anywhere counts. The page owns rotary focus while it is the current page (`requestFocusOnHierarchyActive`). `onRotaryScrollEvent` goes through `RotaryCounter`: each positive detent counts, and each negative one undoes. It answers true for every event, a remainder of 0 detents included, so nothing above it takes the bezel.
- **The phrases:**
  - The title at y 34.
  - The rows: 144 × 25 units, corner 12.5, on the surface colour. The selected row has a 1.3-unit outline in the accent.
  - The name sits at the start; the target ("33·33·34" or "100", in the feed's digits) sits at the end.
  - His phrases follow the built-ins, then a "Reset" row (question 2), which asks with an `AlertDialog`.
  - The rotary scrolls this page. Choosing a row returns to the counter.
- **A screen reader:**
  - The counter is one node: `clickable(onClickLabel = words.countAction) { count() }`, then `clearAndSetSemantics { contentDescription = name; stateDescription = state; customActions = … }`. The actions are `TasbeehSpeech.actions(words)`: UNDO runs `undo()`, and PHRASES launches `verticalPagerState.animateScrollToPage(1)` on the page's scope. Each answers true.
  - With TalkBack on, one-finger swipes and the rotary belong to TalkBack. So a double tap counts, "Take one away" takes one away, and "Choose a dhikr" turns to the phrase list. That is the TalkBack route to the list and its Reset row, beside the `VerticalPager`'s own scroll action (a two-finger swipe).
  - The `clearAndSetSemantics` sits on the counter's own node, inside the `VerticalPager`'s page, so the pager keeps its scroll semantics.
  - Each phrase row has its name, then ", " and its total, and `selected`. The Reset row is the list's last item, so a walk down the list reaches it.

- [ ] **Step 1: Write the failing tests:**
  - `RotaryCounterTest`, with `detentPx` 64:
    - `oneDetentEachWay`: 64 gives +1, and −64 gives −1;
    - `halvesAddUp`: 32 gives 0, then 32 gives +1;
    - `aFastTurnCountsEveryDetent`: 192 gives +3;
    - `aPauseDropsTheRemainder`: 40, then 40 600 ms later, gives 0 both times;
    - `turningBackDropsTheOtherWaysRemainder`: 40, then −64, gives −1.
  - `WatchTasbeehTest`, on `runTest` with the class's scope and clock on the test scheduler, a fake `Feel` that records its calls in order, and the in-memory memory:
    - `thirtyThreeCountsBuzzThePartOnce`: 33 counts, 200 ms apart, give 32 `count` calls, then 1 `partComplete`.
    - `aHundredBuzzesTheSetThenRollsOverAfterASecond`: after the 100th, `(100, 1)`; at 999 ms, still 100; at 1,000 ms, `(0, 2)`.
    - `aTurnBackInsideTheHoldStaysInTheRound`: the 100th, then `undo`, gives `(99, 1)`, and there is no rollover after 1 s.
    - `aFastTurnAcrossAPartsEndBuzzesItAlone`: from 31, `turn(3)` gives 34, with exactly one call, `partComplete`. From 1, `turn(-3)` gives 0, with one `count` alone.
    - `noTickCutsThePartsBuzz`: from 31, `turn(3)` at 0 ms gives `partComplete`. Then `turn(1)` at 100 ms gives 35 with no call, and `turn(1)` at 140 ms gives 36 with one `count`.
    - `noTickCutsTheSetsBuzz`: from 98, `turn(2)` at 0 ms gives `(100, 1)` and `setComplete`. Then `undo` at 399 ms gives 99 with no call, and `undo` at 400 ms gives 98 with one `count`.
    - `undoAtZeroIsSilent`: no `Feel` call.
    - `aLongPhraseOfHisOwnMayTakeTwoLines`: `TasbeehType.dhikr(false)` has `maxLines` 2 and `range(1.0f)` 11..16.5; `dhikr(true)` has `maxLines` 1 and `range(1.0f)` 16.5..16.5.
    - `atTheLargestFontTheCountKeepsItsSize`: `TasbeehType.count.range(1.3f)` is 46..46, and `TasbeehType.of.range(1.3f)` is 11..14.3.
    - `aBurstIsWrittenOnce`: 10 counts within 100 ms give one save, 300 ms after the last.
    - `flushSettlesTheHoldAndWritesTheNextRound`: `flush` inside the hold stores `"0,2"`.
    - `eachPhraseKeepsItsOwnCount`.
    - `aPhraseRemovedOnThePhoneFallsBackToAfterPrayer`.
  - `PhraseListTest`:
    - `builtInsFirstInThePhonesOrder`;
    - `hisPhrasesFollowInTheFeedsOrder`, on the sample's two;
    - `targetsAreHeldToOneToAThousand`: 0 gives 1, 1 gives 1, 1,000 gives 1,000, and 5,000 gives 1,000;
    - `aBlankPhraseOrABuiltInsIdIsSkipped`;
    - `aSixtyCharacterPhraseAndEmojiAreKeptWhole`.
  - `TasbeehSpeechTest`:
    - English, on After prayer: at 13, the name "SubhanAllah" and the state "13 of 100"; at 33, "33 of 100. Next, Alhamdulillah"; at 100, "100 of 100. Round complete"; at 0 of round 2, "Round complete. 0 of 100, round 2", with the name "SubhanAllah, round 2".
    - Saudi Arabic at 13: "١٣ من ١٠٠".
    - A phrase of his own is named as typed.
    - `theCounterOffersUndoAndThePhrases`: `actions(WatchScreenWords.ENGLISH)` is `[CounterAction("Take one away", UNDO), CounterAction("Choose a dhikr", PHRASES)]`, and on the ar-sa sample's words (`TestFeeds.other("ar-sa").words()`) the labels are that feed's `undoAction` and `phrasesAction`, neither of them English.
  - `TasbeehCodecTest`: a round trip; malformed input, a negative count and a count over the total.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the code, then the two pages.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Verify on emulator-5558:**
  - The rotary: `adb -s emulator-5558 shell input rotaryencoder scroll -a SCROLL=-1`. Check which sign is clockwise first, against the emulator's own rotary control, and record it in the README.
  - Shots, with `ref.py screen=g-tasbeeh`: 0 (with the hint), 27, 33 (the part's flash) and 100, then the rollover to 0 of round 2.
  - The phrase list with the sample's two phrases, against `screen=g-tasbeeh-list`.
  - The same in ar-sa: Arabic-Indic counts, no transliteration, the ring still clockwise.
  - The bezel on the counter never turns to the phrases: 40 detents each way leave the counter on screen, and the count moves by exactly 40 and back.
  - The haptics: from 31, one `input rotaryencoder scroll` of 3 detents clockwise. Then `adb -s emulator-5558 shell dumpsys vibrator_manager` lists the part's waveform as FINISHED, never `CANCELLED_SUPERSEDED`, with no tick after it. The tick and the set's waveform appear in their own tries.
  - Both pagers are scrollable to accessibility: with the counter showing, `adb -s emulator-5558 shell uiautomator dump` has the `HorizontalPager`'s node and the `VerticalPager`'s node each with `scrollable="true"`. The counter's own node is beneath the `VerticalPager`'s, not in its place.
  - TalkBack (`talkback.sh on`), on the counter at 27: `walk` says its name and "27 of 100". `tap2` at the screen's centre says "28 of 100", which proves Task 4's `tap2`. `actions`, then "Take one away", says "27 of 100".
    - `actions`, then "Choose a dhikr": the phrase list shows (`uiautomator dump` holds its title and rows), and TalkBack says its first row. Then `walk` down the list reaches the Reset row, and choosing a row with `tap2` returns to the counter.
    - If `actions` cannot open TalkBack's actions menu from the console, the README says so. The two actions are then named for plan 5's device pass on his Galaxy (Ruling 38), and Task 9 does not claim them heard.
    - `talkback.sh off` at the end.
- [ ] **Step 6: Commit** with the message "Wear: Tasbeeh counted as the phone counts, a tap or a bezel click for each, a turn back taking one away, his own phrases from the phone and the counts kept on the watch".

**As built (30 October, Lane W):**
- **Where the counter lives (erratum p3 #8).** `WearMainActivity` holds the one `WatchTasbeeh`, on its `lifecycleScope`, above the pager. It gives the counter his phrases (`PhraseList.of`) from every feed stored, on the main thread before the first frame. The page flushes it whenever the counter stops being in front and active (a swipe away, the phrase list, a pause), and when it is disposed; the activity flushes it in `onPause` as well. `flush` writes at once, on the caller's thread: `TasbeehMemory` is synchronous, so a scope that ends after it loses nothing (`aFlushedCountOutlivesItsScope`). On the device, a page left 0.46 s after a set's end, then force-stopped, had stored "0,2"; a count paused by HOME and force-stopped 0.15 s later, before the debounce, had stored it.
- **Erratum p3 #9 and Ruling 55 item 6** were already done (`kotlinx-coroutines-test` is a test dependency, and the debug APK carries the four sample feeds alone). Task 6 does not touch `wearApp/build.gradle.kts`.
- **The haptic rule (call 14)** is one haptic for each `count`, `undo` or `turn`: the strongest end crossed, the latest of its kind, else a tick. A turn across a part's end and the set's buzzes the set (`aTurnAcrossAPartsEndAndTheSetsBuzzesTheSet`), and every detent of a turn through the set's end is counted into the next round, with no hold, because the set did not stand at its end. A change that changes nothing, an undo at 0, is silent and writes nothing.
- **The models beside the plan's.** `counterLines(feed, ui)` writes what the counter draws: the dhikr (a built-in's Arabic, his phrase as typed), its transliteration only under `latinScript`, "ROUND n" cased in the phone's language, the count and "of N" in the feed's digits, and the hint at 0 of round 1 alone. `phraseRows(feed, phrases, selected)` writes the list's rows. A set's target is "33·33·34", except in Arabic-Indic and Urdu digits, whose nought is itself a dot: the device showed "٣٣·٣٣·٣٤" as one long number, so those take the Arabic comma, "٣٣، ٣٣، ٣٤". `TasbeehUi.of` derives the part, the notches and the flash.
- **The hint keeps no inset** (`TasbeehType.HINT_INSET` = 0, `LineText`'s new `inset`, default 6). `gTasbeeh` sets "Turn the bezel or tap" across the ring, 96.9 units wide at 10. With the frame's 6-unit inset, the room at its descent is 88.4 units, and the device cut it to "Turn the bezel or …". With none it is 100.4, and it draws whole, 120 to 333 px against the drawing's 116 to 336. The French, Turkish and Indonesian hints (141.3, 149.5 and 106.3 units in Manrope SemiBold) are still wider than that room. They end in an ellipsis until plan 5's strings shorten them, or the Lead lets the hint shrink (call 15 lets only the dhikr and the notices shrink).
- **The pages.** The dots mirror for a right-to-left phone, as the phone's part reminder row does; the ring never mirrors. His rows in the list use the system font, as call 2 has his phrases drawn, in SemiBold, the list's 700. The rows and the counter take no press indication, as the phone's chips do. The Reset row is the list's last item, in mist, centred. Its `AlertDialog` asks `resetConfirm`. Its confirm is the accent's check, which is said as `words.reset`. Its dismiss keeps the library's own icon and description ("Dismiss", in the watch's language), because the feed has no word for it.
- **The rotary's sign (Step 5):** a negative `AXIS_SCROLL` is clockwise and counts. `--axis SCROLL,-1` counts one and `SCROLL,1` takes one away. The emulator's own rotary knob has no console command, so the sign was checked against the lists, which turn down on it as a Galaxy's bezel turned clockwise does (README).
- **TalkBack (Step 5).** Walking to the counter at 27 says "27 of 100. SubhanAllah. Page": the state first, as Wear TalkBack 16 orders it. `tap2` counts, and TalkBack says "29 of 100" (a state change, `CONTENT_CHANGE_TYPE_STATE_DESCRIPTION`). `actions` opens the TalkBack menu with "Actions" first, and its `tap2` lists "Take one away" and "Choose a dhikr". "Take one away" says "28 of 100". "Choose a dhikr" (after `walk 1`: a double tap activates the item focused) shows the list, and TalkBack says "Tasbeeh". `walk` reads "Selected. After prayer, 100", the rest "Not selected. …", then "Reset. Button". A row chosen with `tap2` returns to the counter: "0 of 100. SubhanAllahi wa bihamdihi". "Selected" and "Not selected" are Compose's own words, in the watch's language.
- **His phrase in two lines (review round 1).** `TextAutoSize` fits a width and a number of lines, never a height, and two lines grew down from y 56 by the system font's own spacing. The watch's Naskh (`NotoNaskhArabic-Bold`) sets its lines 1.70 of its size apart, with a descent of 0.63, so his "سبحان الله وبحمده، سبحان الله العظيم" ran into "ROUND 1" (8 rows shared at font 1.0, more at 1.15 and 1.3). Two changes, because either alone falls short:
  - The two-line dhikr's lines stand 1.2 of its size apart (`Line.lineHeight`). The first line's top and the last line's bottom stay the font's own, so a dhikr of one line draws as before.
  - Its box must end 1 unit above ROUND's letters (`TasbeehType.dhikrBottom`, which rises as the font size grows ROUND). `LineText`'s `bottom` draws it at the largest of StepBased's own steps whose box ends there (`fitAbove`). At 11 it drops its last line, with an ellipsis, before it runs lower. With the font's own spacing, two lines at 11 would not fit above ROUND at 1.3.
  - On emulator-5558 his phrase draws in two lines at about 14.5 units at font 1.0, 14 at 1.15 and 13 at 1.3, its ink 17 to 19 px above ROUND, on an English and an Arabic phone. 60-character phrases in Arabic and in Latin stay whole in two lines at 1.0 and 1.3. The built-ins draw pixel for pixel as before, and his Latin phrase's second line sits 1 px lower. At a larger font size his long phrase is drawn smaller, because ROUND grows into the same room.
- **His phrase in two lines (review round 2), replacing round 1's spacing of 1.2.** Two changes:
  - At its floor, a block keeps as many of its lines as end within its room (`fitAbove`). Round 1's fallback started one line short, so a phrase too long for two lines even at 11 (60 capitals, or 60 code points of emoji) was drawn in one. It now draws in two, with the ellipsis at the end of the second, as 14dfb275 drew it.
  - The dhikr's lines stand as its fonts set them, but never more than 1.55 of its size apart (`Line.maxLineHeight`, through `lineSpacing`, with the spacing measured by `LineText`'s TextMeasurer). Roboto (1.17) and the emoji font keep their own. The watch's Naskh (1.70) is held to 1.55.
    - At round 1's 1.2, a vowelled phrase's marks met across its lines: the kasra under ٱللَّٰهِ sat 2.2 px above the dagger alif over the next line's ٱللَّٰهِ.
    - A HarfBuzz simulation (`NotoNaskhArabic-Bold`) wrapped 3,888 vowelled phrases from the adhkar and 377 ayahs as the counter wraps them. The most ink any of them brings across a break is 1.45 of the size, so 1.55 leaves a tenth of the size clear. At the reviewer's 1.35, 7 of 1,891 phrases still met.
    - Two lines at 11 still fit above ROUND at font 1.3 once Android has rounded them to whole pixels, on every screen from 384 to 480 px across. The phone's own 1.6 would not fit on 454 px.
  - On emulator-5558:
    - The 60 capitals and the 60 emoji draw in two lines at 11 at font 1.0, 1.15 and 1.3. At 1.0 they match 14dfb275 pixel for pixel.
    - The vowelled phrase draws at about 12 units at 1.0 and 11 at 1.3, with 11.7 to 13.9 px of clear ink between its lines. The two worst phrases of the simulation keep 7.3 and 9.2 px.
    - His 36-character phrase stays in two lines (about 12, 11.5 and 11 units), 14 to 16 px above ROUND, on an English and an Arabic phone.
    - His Latin phrase keeps the drawing's size, 14 px above ROUND at 1.0 and 7 to 8 px at 1.15 and 1.3.
    - The built-ins at 27, in English and in ar-sa, match 0857cf2f pixel for pixel.

### Task 7: Qibla: the watch's own compass, and the dial filling the screen

**Files:**
- Create, in `wearApp/src/main/kotlin/world/taqwa/app/wear/qibla/`:
  - `WristCompassPolicy.kt` (pure), `WristCompass.kt` (the sensor shell);
  - `Tilt.kt` (pure), `QiblaModel.kt` (pure);
  - `QiblaPage.kt`.
- Test, in `wearApp/src/test/kotlin/world/taqwa/app/wear/qibla/`: `WristCompassPolicyTest.kt`, `TiltTest.kt` and `QiblaModelTest.kt`.

**Interfaces:**
- Consumes Task 2's `WatchQibla`, `DialState`, `CompassReading`, `CompassAccuracyRules` (with `fieldMagnitude` and `magneticFieldIsImplausible`) and `TrueNorth`, and Task 4's `Feel` and `Line`.
- Produces `internal enum class RotationVerdict { USE, PROBATION, DISTRUST, DROP, NOTICE }`, and `internal class WristCompassPolicy(rawPairAvailable: Boolean)` with `fun onRotationVector(nowMillis: Long, x: Float, y: Float, z: Float): RotationVerdict` and `val rawPairActive: Boolean`. It is the phone's `AndroidCompassSource` decision logic, with its constants:
  - `DEGENERATE_GIVE_UP_MILLIS` = 2,000, `RETRUST_DWELL_MILLIS` = 500 and `DEGENERATE_NOTICE_INTERVAL_MILLIS` = 200;
  - a degenerate sample is `CompassAccuracyRules.androidRotationVectorIsDegenerate`.
  - The verdicts:
    - **USE:** emit from the rotation vector.
    - **PROBATION:** still within the dwell. The raw pair emits.
    - **DISTRUST:** trust is lost. Register the raw pair again.
    - **DROP:** two seconds of lies with a raw pair present. Unregister the rotation vector.
    - **NOTICE:** no raw pair. Emit one low CALIBRATION sample, at most every 200 ms.
  - `rawPairActive` says whether the accelerometer and the magnetometer are the heading's source. It turns false once the rotation vector is trusted, as on the phone.
- Produces on `WristCompassPolicy` the field check, the watch's departure from the phone (call 13):
  - `fun onField(nowMillis: Long, x: Float, y: Float, z: Float)` keeps the magnetometer's last |B| (`CompassAccuracyRules.fieldMagnitude`) and when it came;
  - `val fieldMicroTesla: Double?` is that |B|, for the log;
  - `fun lowReason(nowMillis: Long, accuracy: Int): CompassLowReason?` is the low reason of a sample from either source. It is INTERFERENCE when a field heard within `FIELD_FRESH_MILLIS` = 1,000 is `magneticFieldIsImplausible`, whatever the source and its trust. Otherwise it is CALIBRATION when `androidAccuracyIsLow(accuracy)`, and null otherwise. INTERFERENCE wins over CALIBRATION.
- Produces `internal data class WristReading(val reading: CompassReading, val tiltDegrees: Double)`.
- Produces `internal class WristCompass(context: Context, declination: Double)`, with `val hasSensor: Boolean` and `val readings: Flow<WristReading>`:
  - `hasSensor` is a rotation vector, or an accelerometer and a magnetometer;
  - `readings` is a `callbackFlow` on the main looper, at `SENSOR_DELAY_GAME`, registered while it is collected.
  - It is the phone's source, with these differences:
    - the declination is the feed's, so no sample is ever low for want of one;
    - each sample carries the tilt;
    - **the magnetometer stays registered beside a trusted rotation vector**, for as long as `readings` is collected (the Qibla page active). Its samples go to `onField` alone, never to the heading, and every sample's low reason is `lowReason`. So a rotation vector that says it is confident in a 179 µT field (B6, the S23) is INTERFERENCE: the dial dims and shows no needle;
    - a debug build logs `TaqwaWearQibla` once a second: the rotation vector's and the magnetometer's accuracy, `fieldMicroTesla`, the trust, and the tilt. That is for plan 5's Galaxy pass.
  - What it keeps of the phone's source:
    - the accuracy is read on every sample;
    - the remap by `display.rotation` and a `DisplayListener`, exactly as the phone's `azimuthDegrees`, which turns the heading back for a watch on the right wrist with its display turned 180°.
- Produces `internal object Tilt`:
  - `fun degrees(rotationMatrix: FloatArray): Double` is `acos(m[8].coerceIn(-1f, 1f))`, in degrees;
  - `class Level(private val enterAbove: Double = 40.0, private val leaveBelow: Double = 30.0)`, with `fun tilted(tiltDegrees: Double): Boolean`.
- Produces `internal data class QiblaScreen(val needle: Boolean, val marker: Boolean, val dimmed: Boolean, val lit: Boolean, val heading: Double, val caption: String, val captionAccent: Boolean, val detail: String, val spoken: String?)`, and `internal fun qiblaScreen(state: DialState, tilted: Boolean, feed: WatchFeed): QiblaScreen`. Here `bearing` is `feed.place.qiblaBearing.toInt()` in the feed's digits.
  - **Searching:** needle and marker. The caption is `bearing`, or `holdLevel` while tilted. The detail is `distance` (since Ruling 66 it draws `distanceOnDial`, "6,600 km", and its description is `distance`). Spoken is the guidance's `turnRight` or `turnLeft` phrase (`WatchRules.counted`), or `holdLevel`.
  - **Aligned:** `lit`, needle and marker. The caption is `facingQibla`, in the accent, and is spoken.
  - **Low:** `dimmed`, with no needle and no marker (the phone's B41 rule). The caption is `needsCalibrating` or `interference`.
  - **BestEffort and NoSensor:** `dimmed`, with no needle or marker. The caption is `bestEffort` or `noSensor`. The detail is `qiblaDetail(bearing, km)` (since Ruling 66 it draws `qiblaDetailOnDial`, "23° · 6,600 km", and the caption's description says `qiblaDetail`).
- Produces `internal object QiblaType` (call 15), in `QiblaModel.kt`:
  - `bearing` = `Line(15f, 1.2f)` and `facing` = `Line(13f, 1.2f)`;
  - `title` = `Line(15f, 1.2f, maxLines = 2, floor = 11f)`, for the bad states' titles ("Compass needs calibrating" and the rest). A second line sits above, its last line keeping y 141 and its first at y 125, and the detail keeps y 155 (as built since review round 2; until then a second line sat below, at y 155, and the detail moved to y 169);
  - `detail` = `Line(10f, 1.3f, floor = 9f)`, one line always, never wrapped and never ellipsised, sized on a grid of halves against the turning letters' glyph ink and the ticks, on the half anchor (Ruling 66, `QiblaFit`; until then Ruling 62's two-line detail, `Line(10f, 1.3f, maxLines = 2, floor = 9f)`, inside the ticks by Ruling 63, and `Line(10f, 1.3f)` until review round 3);
  - `north` = `Line(11.55f, 1.3f)` and `cardinal` = `Line(9.975f, 1.3f)`: `qiblaDial`'s 11 and 9.5 units at k 1.05, drawn on the turning dial, growing and never shrinking as the phone dial's letters do.

**The page** (`gQibla`, with `qiblaDial` at k 1.05):
- **The dial** fills the screen, its rim at r 92 on the screen's edge. Its 24 ticks, its cardinals (`words.cardinals`) and its rim turn with `−heading`, and are never mirrored. The Kaaba marker sits at `bearing − heading`, and the needle points at it.
- **Aligned:** the rim takes a 9-unit halo at alpha 0.18, and the 2.6-unit rim lights.
- **Dimmed:** the dial is at alpha 0.4.
- **The text:** the caption at y 141 (`QiblaType.bearing`, ExtraBold, ink; `facing` in the accent when facing; `title` for a bad state), and the detail at y 155 (`QiblaType.detail`, SemiBold, mist), at the drawing's font size; as it grows, on Ruling 66's half anchor (`QiblaFit.captionY` and `detailY`).
- **While `active`:** it collects `WristCompass.readings` into a `WatchQibla` (`reset` on each start) and a `Tilt.Level`, calling `feel.aligned()` on each `buzz`. With `!hasSensor`, the state is `NoSensor`, and nothing is registered.
- **A screen reader:** the dial node has `liveRegion = Polite` and `contentDescription = spoken` while Searching; the caption node has `liveRegion = Polite` (the round-2 rule).

- [ ] **Step 1: Write the failing tests:**
  - `WristCompassPolicyTest`, restating the phone's behaviours:
    - `aHealthyRotationVectorIsTrustedAfterHalfASecond`: real samples give PROBATION up to 499 ms and USE at 500 ms, and `rawPairActive` turns false.
    - `oneGoodSampleAmongLiesNeverEarnsTrust`.
    - `aDeadRotationVectorLeavesTheRawPairInCharge`: only zero vectors for 2 s give DROP, and `rawPairActive` stays true.
    - `aTrustedVectorThatStartsLyingIsDistrustedAfterTwoSeconds`: DISTRUST, and `rawPairActive` is true again.
    - `noRawPairAndALyingVectorSaysSoEveryTwoHundredMs`.
  - `WristCompassPolicyTest`, the watch's own field check:
    - `aTrustedVectorStillHearsAnImplausibleField`: trusted at 500 ms (`rawPairActive` false), `onField(600, 600f, 0f, 0f)` makes `lowReason(600, 3)` INTERFERENCE. Then `onField(700, 22f, 0f, -40f)` (45.7 µT) makes `lowReason(700, 3)` null.
    - `aConfidentVectorInAStrongFieldIsInterference`: B6's case, accuracy 3 beside a 179 µT field, is INTERFERENCE.
    - `interferenceWinsOverCalibration`: an implausible field with accuracy 1 is INTERFERENCE, and a plausible one with accuracy 1 is CALIBRATION.
    - `aFieldOlderThanASecondIsNotHeld`: an implausible field at 0 ms, then `lowReason(1_001, 3)`, is null.
    - `theFieldIsKeptForTheLog`: after `onField(0, 600f, 0f, 0f)`, `fieldMicroTesla` is 600.0.
  - `TiltTest`:
    - `m[8]` = 1 gives 0°, and cos 45° gives 45°;
    - the `Level`: 35 gives false, 41 true, 35 still true, and 29 false.
  - `aDisplayTurnedHalfAroundTurnsTheHeadingBack`: the pure remap (`remapFor(rotation)`, which the shell passes to `SensorManager.remapCoordinateSystem`) gives ROTATION_180 the axes (MINUS_X, MINUS_Y), as the phone does.
  - `QiblaModelTest`, on the sample (bearing 23.35, `qiblaKm` as the fixture holds it):
    - Searching at 340 gives the caption "23°", the detail "${km} km" (`distanceOnDial`, since Ruling 66; its description is the phone's "Makkah · ${km} km away") and the spoken "Turn right about 45 degrees";
    - tilted gives the caption "Hold your wrist level";
    - Aligned gives `lit` and "Facing qibla";
    - Low(CALIBRATION) gives "Compass needs calibrating", dimmed, with no needle or marker;
    - Low(INTERFERENCE) gives "Magnetic interference";
    - BestEffort gives "Compass unavailable" and the detail "23° · ${km} km" (`qiblaDetailOnDial`, since Ruling 66; the caption's description is "Compass unavailable, 23° · ${km} km to Makkah");
    - NoSensor gives "No compass sensor";
    - Saudi Arabic gives the caption "٢٣°", the cardinals from `ARABIC_SCREENS`, and the same heading (never mirrored).
    - `aBadStatesTitleMayTakeTwoLines`: `QiblaType.title` has `maxLines` 2 and `range(1.0f)` 11..15; `QiblaType.bearing.range(1.3f)` is 15..18.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the code, then the page.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Verify on emulator-5558.**
  - Check `dumpsys sensorservice` first: it should list a rotation vector, an accelerometer and a magnetometer.
  - Drive the heading with the console: `adb -s emulator-5558 emu sensor set acceleration 0:0:9.81`, and `sensor set magnetic-field` with x = −22·sin H, y = 22·cos H, z = −40. Check the formula at H = 0 and H = 90 against `TaqwaWearQibla` before the shots.
  - Shots against `ref.py screen=g-qibla`. Let D be the stored sample's declination: −25.4 before plan 2's Task 3, −25.0 once it has rounded it (read it from the stored `feed.json`). True = magnetic + D:
    - magnetic 340 − D (true 340) puts the marker to the right;
    - magnetic 23 − D (true 23) says "Facing qibla" with the rim lit, and one 30 ms vibration in `dumpsys vibrator_manager`;
    - a 600 µT field gives "Magnetic interference" after 1 s, and "Compass unavailable" after 20 s. The `TaqwaWearQibla` log shows the rotation vector trusted throughout, with `fieldMicroTesla` near 600: the field is heard beside a working rotation vector;
    - acceleration 0:6.9:6.9 (45° of tilt) gives "Hold your wrist level".
  - The right wrist (erratum p3 #11): `adb -s emulator-5558 shell wm fixed-to-user-rotation enabled`, then `wm user-rotation lock 2`, turns the display (Wear refuses a half turn without the first). The console's sensors do not turn with it, so `TaqwaWearQibla` reads H+180, and the Kaaba marker points the same physical way on the turned screen. Then `wm user-rotation lock 0`, `wm fixed-to-user-rotation default` and `wm user-rotation free`.
  - ar-sa: Arabic cardinals, the dial unmirrored.
  - TalkBack hears "Turn right about 45 degrees", then "Facing qibla".
- [ ] **Step 6: Commit** with the message "Wear: Qibla, the app's dial across the whole screen, turned by the watch's own compass and set true by the phone's declination, with no location asked".

**As built (30 October, Lane P):**
- **One listener for each sensor.** On emulator-5558 the rotation vector is AOSP's fusion of the Goldfish accelerometer, gyroscope and magnetometer, and it emits on accelerometer samples. The sensor service asks for the fusion's accelerometer under the connection of the app that holds the vector, and `SensorManager` opens one connection per listener. So with one listener for all three, as the phone's `AndroidCompassSource` has, the trusted vector letting the accelerometer go switched the hardware accelerometer off, and the vector fell silent ("Active sensors" listed the magnetometer alone). `WristCompass` gives each sensor its own listener. The phone shares one listener the same way, and would stall on a phone whose rotation vector is AOSP's fusion (a note for the Lead in the report).
- **"Hold your wrist level"** is its own line, `QiblaType.level` (15, cap 1.2, one line, shrinking to 11), held inside the dial's innermost marks (`QiblaType.DIAL_ROOM`, 92 − 0.95 · 11 · 1.05, `ref.py`'s room for `g-qibla`): it is drawn over a dial that still turns and points. A bad state's `title` keeps inside the dial's rim, 6 units in (`QiblaType.RIM_INSET`), at the far edge of its last line, since its dial is dimmed and has no letters. Its two lines grow upward, and the detail sits 14 units under the caption's last line, y 155 at font scale 1.0 (both since review round 2; until then the title kept to the round screen's chord at its second line's height, its second line went below at y 155, and the detail to y 169).
- **The dial's letters, its hub and what it says.** No N, E, S and W without a heading (a bad state, or a heading that is not a number), as the phone's dimmed dial has none; no hub without the needle. The dial says only the turn: "Facing qibla" and "Hold your wrist level" are said by the caption, a live region, so they are said once.
- **On the emulator:** the sample's declination is −27.0 now (true = magnetic − 27). The fused vector converges over a few seconds after a `sensor set magnetic-field`, so wait about 15 s at each heading before a shot. `store.sh` installs the worktree's own `watchface-debug.apk`: build the face first (`./gradlew :watchface:assembleDebug`), or it installs whatever face that worktree last built.

**After review round 1 (30 October, Lane P):**
- **Every state is said as it comes.** A change of a text's words alone reaches TalkBack as a content change of undefined type, which it did not say for the live region (its log on emulator-5558; review round 2 found the event in Compose 1.12.1's bytecode, where round 1 had written that none is sent); a change of size comes as a SUBTREE change, which it says. So a live region of words is heard only when its size changes with them. "Magnetic interference" to "Compass unavailable", two lines each in the same box, was never said. Two rules follow, both in `QiblaModel.kt` and `QiblaPage.kt`:
  - A dimmed dial's caption (Low, BestEffort, NoSensor) has a content description, `QiblaScreen.captionDescription`. It is the title, and in BestEffort and NoSensor the title with the bearing and the distance after it, joined by `ComplicationFields.spokenSeparator` (", ", or "، " right to left). So "Compass unavailable, 23° · 6,600 km to Makkah" is one announcement, title first.
  - A pointing dial's caption has none. TalkBack queues a live region's description in the group the dial's turns are in (`ttsClearQueueGroup=5`), and the next turn clears it, so a described "23°" coming back as the wrist left the Qibla was lost. A pointing dial's captions ("23°", "Facing qibla", "Hold your wrist level") are three sizes in three boxes, so each change is heard.
  - The detail is composed in every state. It is hidden from a screen reader where it is empty (Low) or said with the caption (`QiblaScreen.detailRead`), so no change of state adds or removes a node.
- **A tilt notice too wide for one line at 11 takes a second line, upward** (`QiblaType.captionBox`, `LEVEL_LINES` = 2). The notice is measured at 11 on one line first, so English stays one line at about 13.5. A phrase that would lose its end, such as Indonesian's "Jaga pergelangan tetap datar" (about 159 units against 135), goes on two lines, the last at y 141 and the first 14 above at y 127 (16 above, at y 125, since review round 2), in the same box. The distance keeps y 155. On emulator-5558 a second line at y 155 put the distance at y 169, across the ticks and the "S" of the dial that still turns under it; a title's dial is dimmed and has no letters, so a title still grew downward (until review round 2, below).
- **TalkBack on the emulator:** do not run `uiautomator dump` while TalkBack is on. It pauses TalkBack, and when TalkBack comes back it raises its phone-access prompt again.
- **For Task 9's matrix and plan 5** (the matrix's feeds are English and Arabic only; review round 1 measured the Latin languages with `tools/watchface/ttf.py`, and fix round 1 shaped them with HarfBuzz in the app's Manrope and shot fr, id, ur, bn and ar-sa from scratch samples on emulator-5558):
  - **Open, the Lead's call:** the French BestEffort and NoSensor detail, "23° · 6 600 km jusqu’à La Mecque", is about 155 units at its floor of 10, under French's two-line titles at y 169 (127.8 units), and is drawn "23° · 6 600 km jusqu’à La …" at font scale 1.0 and 1.3. It comes from the plan's `detail` = `Line(10f, 1.3f)` and the phone's `prayer_qibla_detail`. The choices are a lower floor (about 8.2 is needed at 1.0, and less at 1.3), two lines, or a shorter French string at native review.
  - **Fixed, to look at in the matrix:** Indonesian's tilt notice on two lines. It is a draft (`WatchWords.kt`); a shorter phrase at native review would bring it back to one line.
  - **Seen whole on the emulator at 1.0:** the tilt notice, Low and BestEffort in fr, id, ur, bn and ar-sa, the French detail excepted; English at 1.3 (its BestEffort detail fits at about 10.1 in 120 units). Arabic-script titles on two lines (`TITLE_PITCH` 14 at 15 units, near the line height where Lane W's vowelled Naskh marks met) were not seen. Arabic and Urdu take one line in the two states shot, Low by interference and BestEffort. "Compass needs calibrating" and "No compass sensor", which the emulator cannot show, are likely to take two in Urdu ("قطب نما کو درست کرنے کی ضرورت ہے") and in Arabic.

**After review round 2 (30 October, Lane P):**
- **A caption's two lines stand 16 units apart** (`QiblaType.TITLE_PITCH`, 1.067 of the 15-unit size; 14 before). At 14 (0.933) English joined glyphs across its two lines at font size 1.0: the g of "Magnetic" into the f of "interference" (the reviewer's shot of 74738c19 on emulator-5558) and the p of "Compass needs" into the l of "calibrating". Manrope ExtraBold's ink reaches 0.81 of the size above the baseline ("à") and 0.255 below it ("g"), 1.065 in all, so at 16 no glyph of one line can meet a glyph of the other wherever the text breaks. `QiblaInkTest` holds this against the ink of every character the phone sends in these lines in each of its languages (`:shared`'s strings and `WatchWords.holdLevel`, read as test inputs, and every digit); 14 and 15 fail it. Arabic and Bengali letters are drawn in the watch's own fonts, which a host test cannot read: HarfBuzz/raqm in layoutlib's Noto fonts (Naskh UI and elegant, Sans Bengali) found every shipped two-line title and tilt notice clear at 16 at font sizes 1.0 to 1.3 (the least 1.4 units, English's g over f; Arabic, Urdu and Bengali 2.6 or more), and the emulator shots below agree.
- **A title of two lines grows upward, as the tilt notice does** (`QiblaType.captionBox`): its last line keeps y 141 and its first stands 16 above, at y 125 (19.2 above at font size 1.3), and the detail keeps its place under the last line. Downward, the larger pitch had nowhere to go: with the detail 14 under a second line at y 157, the English and Indonesian BestEffort details were cut at font size 1.3 (117.5 and 118.5 units in a 114.5-unit box at y 174.2), and with the detail kept where it was (y 169, and 171.8 at 1.3) it met or touched the second line at 1.3 in French, Turkish, Indonesian and Arabic (−0.3 to 0.0 units; −2.2 in the elegant Naskh). The title's box is the chord of the dial's rim at its last line's far edge, 6 units in (148.8 units at 1.0, where the old box was 149.8), so every shipped title breaks at font size 1.0 where rounds 1 and 2 saw it break (HarfBuzz, all seven languages); at 1.3 some break later, in a box 147.9 wide where it was 144.5.
- **The detail grows away from the caption with the font size** (`QiblaType.detailY`): 14 units under the caption's last line at 1.0 (y 155, unchanged), grown as the faster-growing line grows (the detail, to 1.3; the caption stops at 1.2), y 159.2 at 1.3. Held at y 155, the caption's descenders met the detail's ascenders as both grew: at 1.3 Bengali's "কিবলামুখী" over its distance (−1.8 units in the model), Arabic's BestEffort title and tilt notice and Urdu's tilt notice over theirs (−0.7 to −3.2). `QiblaInkTest` holds the gap against the same ink at font sizes from 0.85 to 2; a fixed y 155 fails it from 1.2.
- **Seen on emulator-5558** (font size 1.0 and 1.3, the fix's debug build and a scratch build of it with French, Indonesian, Urdu and Bengali samples and four hand-made ones that put "Compass needs calibrating" and "No compass sensor" in the interference and BestEffort slots, in English, French, Arabic and Urdu): the two English titles whole and apart (no lit pixel joins the lines; 3 px, 1.3 units, the least gap, g over f), as are the French, Indonesian, Arabic, Urdu and Bengali two-line titles, the Indonesian two-line tilt notice, and every detail over its caption at 1.0 and 1.3, the French excepted. The searching dial's caption and distance at 1.0 are pixel for pixel the reviewer's shot of 74738c19. The emulator draws Arabic in the compact (UI) Naskh.
- **For Task 9's matrix and plan 5:**
  - **Ruled since (Ruling 62), built in fix round 3 below:** the French BestEffort and NoSensor detail, now at y 155 under the caption's last line, is drawn "23° · 6 600 km jusqu’à La Mecq…". It is 155.8 units at 10 in a 151.6-unit box at 1.0 (148.0 at 1.15, 144.2 at 1.3 at y 159.2), so the choices are now a floor of about 9.7 at 1.0 (9.25 at 1.3; 9.0 holds both), two lines, or a shorter French string at native review ("23° · 6 600 km de La Mecque" is 133.3 units). A longer bearing or distance than Cape Town's needs more room.
  - **Fixed in passing:** Urdu's BestEffort and NoSensor details, cut at font size 1.3 under their two-line titles at y 171.8 (137.7 units in 120.2), are whole at 1.3: the detail now sits under the caption's last line at y 159.2, in a 144.2-unit box, however many lines the title takes (seen: NoSensor's title on two lines there).
  - **Close, not met:** in the elegant Naskh, Urdu's tilt notice over its distance at 1.0 is −0.1 units in the model (+1.7 in the compact Naskh the emulator draws, and clear in the shot), unchanged by this fix; the turning dial's letters still meet the tilt notice's ends at some headings ("E" at "level" at 1.3, "ق" at the Arabic notice), as review round 2 noted.

**After review round 3 (30 October, Lane P): the detail is never cut (Ruling 62).**
- **The rule** (`QiblaType.detailBox`, `detail` = `Line(10f, 1.3f, maxLines = 2, floor = 9f)`), in the ruling's order:
  - (a) one line at the drawing's 10 units grown by the font size to 1.3, where it fits, in the round screen's chord at its line (the box `LineText` gave it: 151.6 units at 1.0, 148.0 at 1.15, 144.2 at 1.3);
  - (b) else that line shrinks, by half a unit, down to 9;
  - (c) only if one line at 9 is still too wide, two lines, at the largest size from the one-line size down to 9 that two lines fit. The first keeps fix round 2's place under the caption (y 155 at 1.0, 159.2 at 1.3); the second stands `DETAIL_PITCH` (16/15, a caption's ratio; SemiBold's ink is 1.035) of the size under it, growing down. Their box is the chord of `TICK_ROOM` (92 − 9 · 1.05, where the ticks begin) at the second line's descent, so no glyph reaches a tick or the rim: 84.1 units at 1.3 at 9. The round screen's chord there (128.8) would have put the line's ends past the rim.
  - The page measures the detail as it lays it out (the caption's way), and draws it at the size found, so a two-line detail's size and box agree.
- **The guard** holds the worst real figures, not Cape Town's: every whole-degree bearing (the widest measured), every distance the phone's two-figure rounding gives from 10,000 to 20,000 km (Tureia, in French Polynesia, is about 19,800 km from Mecca and rounds to 20,000; Mangareva about 19,400), grouped with ",", ".", the no-break space or French's narrow one.
  - `QiblaInkTest.theWorstRealFiguresAreNeverCut` runs them through `detailBox` in Manrope's own advances (Java2D, kerned) for English, French, Turkish and Indonesian, at Wear OS's font sizes from 0.85 to 1.3 and 2, under the captions each is drawn under, and again measured 3 % wider: never cut, never under 9, and a distance never on two lines (a pointing dial's letters turn where a second line would go). That last assertion held in the round screen's chord; Ruling 63's box inside the ticks breaks it, and since R63b's fix round 1 it is `aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead`, below. French's narrow no-break space, which Manrope lacks, counts a quarter em (Roboto's is 0.248, HarfBuzz's own 0.1), so the French worst figures measure 166.9 units at 10 here, against the review's 165.4.
  - `frenchsWorstFiguresShrinkOnOneLineAtTheDrawingsSizeAndTakeTwoAboveIt`: "359° · 19 000 km jusqu’à La Mecque" is one line at 9 at 1.0, and two lines at 1.15 and 1.3, "359° · 19 000 km" over "jusqu’à La Mecque". (Since Ruling 63 it is `frenchsDetailSaysDeLaMecqueAndKeepsInsideTheTicksOnTwoLines`, with "de La Mecque", below.)
  - Arabic (Saudi and Western digits), Urdu and Bengali (both digit sets) are drawn in the watch's own fonts. A HarfBuzz model in layoutlib's Noto Naskh Arabic (UI and elegant) and Noto Sans Bengali UI, by font run, the same rule and figures, at nine font sizes from 1.0 to 1.3: every detail and distance on one line, none cut. Arabic and Bengali keep their full size (13 at 1.3). Urdu's BestEffort detail ("359° · مکہ مکرمہ تک 20,000 کلومیٹر", 146.8 units at 10) shrinks to 9.5 from 1.24. English, Turkish and Indonesian keep one line at 10 or more at every size, and French alone takes two, from about 1.06 with the widest figures.
- **Seen on emulator-5558** (a scratch build of this fix's code with hand-made worst-figure samples), BestEffort unless said:
  - French "359° · 19 000 km jusqu’à La Mecque": whole on one line at 1.0, and at 1.15 (the device measures it narrower than either model, so it kept one line at 9 there); at 1.3 on two lines, "359° · 19 000 km" over "jusqu’à La Mecque", inside the ticks (the ink at most 80.5 units from the middle).
  - French "200° · 20 000 km", the widest: one line at 1.0, two at 1.15 and 1.3 (82.4 and 80.5 units out). Under "Pas de capteur de boussole" in the BestEffort slot (NoSensor's title, which the emulator cannot show): one line at 1.0, two at 1.3, under a two-line title.
  - English "359° · 20,000 km to Makkah" at 1.0 and 1.3, Indonesian "200° · 20.000 km ke Makkah" at 1.3, Urdu at 1.0 and 1.3 (and under its two-line NoSensor title), Bengali "৩৫৯° · মক্কা ২০,০০০ কিমি" and Saudi Arabic "٣٥٩° · ٢٠٬٠٠٠ كم إلى مكة" at 1.3: whole on one line. The Searching distances at 1.3 in French, Indonesian and English are whole on one line.
  - The accessibility tree (TalkBack off) in French at 1.3: Searching reads "359°", then "La Mecque · à 19 000 km"; BestEffort's caption says "Boussole indisponible, 359° · 19 000 km jusqu’à La Mecque", the two-line detail hidden, as before.
- **Left, for Task 9's device pass:** a one-line detail at the full width of its box reaches the dial's rim at its ends. That box is the screen's chord, as `LineText` gave it and Ruling 62 item 2(b) keeps. The shots measure it 90 to 92.5 units from the middle against the rim's 92: the French worst figures at 1.0 and 1.15, and English, Indonesian, Urdu and Bengali at 1.3. Review round 3 saw the same at 1.3 on 74738c19. Keeping one line inside the rim instead (its chord, 6 in: 131.6 units at 1.0) would shrink or split shipped details at the drawing's size (French at 1.0 on two lines). That is the Lead's call, not this fix's.
  **Answered by Ruling 63 item 2, below: the one-line box is inside the ticks.**

**After Ruling 63 items 2 and 3 (30 October, Lane P): the detail keeps inside the ticks, and French says "de La Mecque".**
- **The one-line box is inside the ticks** (`QiblaType.detailBox`, the left item above answered): the chord of `TICK_ROOM` (82.55 units from the middle, where `qiblaDial`'s ticks begin) at the line's far edge, as the two-line box already was, and taken at each size the line is tried at, as the two-line box is. So no glyph of the detail sits on the tick band or the rim, at any heading: 118.5 units at 1.0 at y 155 (151.6 in the screen's chord before), 108.0 at 1.3 at y 159.2 (144.2 before). Ruling 62's order is unchanged: the drawing's 10 grown to 1.3, else shrunk on one line to 9, else two lines. `detailBox` no longer takes the screen's shape: the dial is a circle on any screen.
- **One source for the dial's measures:** `QiblaDialMeasures` (`QiblaModel.kt`) holds the rim (92), the scale (1.05), the ticks (24, from 9 to 5 units in) and the Kaaba marker (11, its halo 0.95 of it). `QiblaPage` draws the ticks and the marker from it, and `QiblaType.RIM_RADIUS`, `TICK_ROOM` and `DIAL_ROOM` are made from it. Named apart from the phone's `QiblaDial` composable.
- **The two-line wiring is pinned:** the page takes the detail's style from its box (`detailStyle(box, units, density)`), and `QiblaInkTest.thePageSetsATwoLineDetailsLinesDetailPitchApart` holds a two-line box's line height at 16/15 of its size and a one-line box's at the font's own; dropping either fails it. `QiblaInkTest.advance` caches by font and text.
- **French `prayer_qibla_detail`** is "%1$s° · %2$s km de La Mecque" (`:shared`'s `values-fr`, a draft to native review), which the phone, the watch and the site's French timetables share.
- **What the box costs** (Manrope by Java2D on the host; HarfBuzz in layoutlib's Noto fonts for Arabic, Urdu and Bengali; the emulator measures a little narrower than either):
  - French BestEffort and NoSensor take two lines at every font size by the host's measure ("359° · 19 000 km de" over "La Mecque" at 1.0, "… km" over "de La Mecque" from 1.15); Cape Town's kept one line at 9 at 1.0 on the emulator;
  - the English, Indonesian and Turkish details with 359° and 20,000 km keep one line at 9 at 1.0 and take two from 1.1, 1.06 and 1.2, and with 19,000 km from 1.15, 1.12 and at 1.3 (R63b fix round 3's figures); with the widest bearings (200°, 300°, 206°) from 1.06, 1.06 and 1.18, with 19,000 km from 1.12, 1.1 and 1.24, and in English and Indonesian the widest four-figure ones from 1.24 (R63b fix round 4's figures); Urdu's BestEffort detail with the farthest figures takes two at every size ("359° · مکہ مکرمہ تک" over "20,000 کلومیٹر"), Cape Town's from 1.15; the second line often begins at "km" ("359° · 20,000" over "km to Makkah");
  - Indonesian's distance with the farthest figures takes two lines by the host's measure (one on the emulator at 1.0), under a pointing dial, so its second line passes over the turning letters at some headings, as the tilt notice does (Ruling 62 item 5, Task 9); review round 1 saw it on the emulator at 1.3 ("Makkah · 20.000" over "km dari sini", the "S" on "km" at true 340 and 346), and by the host's measure Cape Town's own figure takes two from 1.2. Review round 2 found it is not the farthest figures alone: on the emulator every five-figure Indonesian distance it was shown takes two lines, 10.000 from 1.2 and 12.000 and 15.000 at 1.3 (the "S" over "km" at true 340), while 6.600 and 9.000 keep one at 1.3 and 10.000 one at 1.15. Eastern Indonesia gets those figures (by `QiblaMath`'s haversine and the feed's two-figure rounding over the phone's `cities.csv`): nine places at 10.000 (Ambon at about 9,990 km, Amahai, Masohi, Waisai, Sorong, Teminabuan, Fakfak, Bintuni and Manokwari) and fourteen at 11.000 (Ransiki, Tual, Dobo, Biak, Nabire, Timika, Mulia, Tiom, Agats, Wamena, Sentani, Abepura, Jayapura and Merauke). Review round 3 showed 11.000 on the emulator: two lines at 1.3 ("Makkah · 11.000" over "km dari sini", the "S" over "km" at true 340), one at 1.15, 1.2 and 1.24; by the host's measure it takes two from 1.15. Review round 4 found the four-figure figures do too, by the host's measure: every one from 1.000 to 9.900 km but 1.100 takes two lines from 1.18 to 1.3 (measured 3 % wider, every one from 1.06 to 1.24), so every one of the phone's 448 Indonesian places, from Banda Aceh's 6.200 to Jayapura's 11.000, has its distance on two lines on a pointing dial from some font size (R63b fix round 4 below);
  - at 1.3 the one-line details shrink towards 9 (English's Cape Town distance to 9.5 and its BestEffort detail to 9, where they were 12.5 and 12), smaller than at 1.0, since the chord narrows as the line moves down; Indonesian's distance for Medan, Jakarta and Surabaya is 9 at every size it keeps one line at, 1.0 among them. Arabic and Bengali keep 10 to 13.
- **Seen on emulator-5558** (a scratch debug build of this change with hand-made samples, 30 October 23:28 to 23:48 under `wear-5558`; true headings 340, 346 and 352, so the ticks turn under the line's ends, and BestEffort): French Cape Town and worst at 1.0 and 1.3, English worst at 1.3, Urdu and Bengali worst at 1.3, Indonesian's farthest distance at 1.0. In every shot the detail's ink keeps inside the ticks' circle (the farthest 81.1 units, against 82.55), and no lit glyph joins a tick.

**R63b fix round 1 (31 October, Lane P): round 3's one-line guard back where it holds, and three questions for the Lead.**
- **Round 3's guard is back where it holds.** Ruling 63's change dropped `theWorstRealFiguresAreNeverCut`'s "a distance never on two lines" without saying so (review round 1, Important 3). Inside the ticks it fails: by the host's measure Indonesian's distance takes two lines at every font size (the 20,000 km figures at 1.0, Cape Town's own "Makkah · 6.600 km dari sini" from 1.2, nearly every figure when measured 3 % wider), and, measured 3 % wider, English's 20,000 km from 1.24 and most of Turkish's five-figure distances at 1.24 and 1.3. `QiblaInkTest.aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead` now holds one line for every other pointing-dial distance, and names those cases (`heldForTheLead`) until the Lead rules.
- **The letters are measured from one place:** `QiblaDialMeasures.LETTERS` (19 units inside the rim) and `LETTER_DROP` (3.6), which the page draws from, and `QiblaType.LETTER_RADIUS` (72.05). `QiblaModelTest.aSecondLineOfTheDetailWouldStandOnTheTurningLettersPath` holds why a pointing dial's distance keeps to one line: at 1.0, 1.15 and 1.3, at every size two lines may take, the letters' circle crosses the second line's band inside its box.
- **One style for the detail:** the page measures and draws it with `detailStyle(box, units, density)` alone, so the private one-line style that review round 1's surviving mutant used is gone. A mutant at the draw site that hands the style another box (`detailStyle(detailBox.copy(maxLines = 1), …)`) still survives every test (review round 2): the page's composition does not run in these JVM tests (no Robolectric), so the draw call is held by reading it and by Task 9's shots.
- **For the Lead (none built; each departs from Ruling 62 or 63, which this round keeps):**
  1. A pointing dial's distance on two lines (Importants 1 and 3). Keeping it on one line needs a floor of 8 for the distance alone (Indonesian at 1.3 with 3 % more width; 8.5 for English and Turkish), or a shorter Indonesian `qibla_distance` at native review (without "dari sini"), or the Lead's word that it is a Task 9 item, as Ruling 62 item 5 made the tilt notice.
  2. A larger font size drawing the detail smaller (Important 2): at 1.3 English's Cape Town distance is 9.5 and its BestEffort detail 9, Turkish's 9.5, against 10 at 1.0 (12.5 and 12 before Ruling 63), and Indonesian's Cape Town distance is 9 at 1.0 already. Ruling 62's order (one line down to 9 before two lines) and fix round 2's gap make it so. The remedies in the R63b report (fit each glyph's own ink; two lines at the grown size before going below 10, for the dimmed dial at least) change one of them.
  3. Whichever is ruled, `heldForTheLead` becomes the accepted cases or goes.

**R63b fix round 2 (31 October, Lane P): the same two questions, still waiting on the Lead's ruling, with review round 2's facts.**
- No ruling on them was in the ledger when the round started. Rulings 62 and 63 are kept, and no remedy is built.
- **Question 1 is not only about the farthest figures.** On emulator-5558, every five-figure Indonesian distance that review round 2 tested took two lines under a pointing dial: 10.000 from 1.2, and 12.000 and 15.000 at 1.3, with the "S" over "km" at true 340. Real places get these figures. Ambon, Sorong and Fakfak are at 10.000. Biak, Nabire, Timika, Wamena, Jayapura and Merauke are at 11.000; that figure was not shown in round 2 (review round 3 has since shown it: see fix round 3), and its width lies between 9.000 (one line at 1.3) and 12.000. Cape Town's 6.600 keeps one line at 1.3 on the emulator; it takes two from 1.2 only by the host's measure. `QiblaInkTest` now also holds that Ambon's 10.000 takes two lines at 1.2, 1.24 and 1.3 by its measure.
- **Question 2:** the emulator's ink agrees with the host's sizes. The English sample's distance is 109.7 by 9.7 units at 1.0 and 103.5 by 8.8 at 1.3. The Indonesian sample's is 115.0 wide at 1.0 and 108.4 at 1.3. The dial's letters still grow.
- **Three minors fixed, with no change to the drawing:** the page draws the letters at `QiblaType.LETTER_RADIUS` and the major ticks every `TICKS / 4`. `heldForTheLead`'s KDoc says how much wider than the failing cases it is. The one-style note above names the draw-site mutant that survives.

**R63b fix round 3 (31 October, Lane P): still waiting on the Lead's ruling; the held cases are now exactly the measured ones.**
- No ruling on the two questions was in the ledger when the round started (02:11). Rulings 62 and 63 are kept, and no remedy is built.
- **Question 1, review round 3's new fact:** on emulator-5558, "Makkah · 11.000 km dari sini" takes two lines at 1.3 ("Makkah · 11.000" over "km dari sini", the "S" over "km" at true 340) and keeps one at 1.15, 1.2 and 1.24; 10.000 took two at 1.2 and 1.3 again. Fourteen of the phone's cities round to 11.000 and nine to 10.000 (the as-built bullet above names them). `44cfbd8d` drew them all on one line.
- **Question 2, review round 3's figures:** the emulator's English Cape Town distance has ink 110.1 by 10.1 units at 1.0 and 104.0 by 9.3 at 1.3, and its BestEffort detail 113.2 by 8.8 and 106.6 by 8.4. By the host's measure the sizes at 1.0 / 1.1 / 1.15 / 1.2 / 1.24 / 1.3 are 10 / 9.5 / 9.5 / 9.5 / 9.5 / 9 for that BestEffort detail, where `44cfbd8d` drew 10 / 11 / 11.5 / 12 / 12.4 / 12.
- **`heldForTheLead` is the cases, no wider** (review rounds 1 to 3's minor, and the part of Important 4 that needs no ruling): a table of the language's folder, whether measured 3 % wider, and the distance's thousands, each with the smallest font size it takes two lines from. Indonesian measured as Java2D measures: 20.000 from 0.85, 10, 12, 14, 15, 16, 18 and 19 thousand from 1.06, 13 and 17 from 1.1, 11 from 1.15, Cape Town's 6.600 from 1.2; measured 3 % wider: 6.600 from 1.1 and every five-figure one from 0.85. English measured 3 % wider: 20,000 from 1.3. Turkish measured 3 % wider: 20.000 from 1.24, and 10, 12, 13, 14, 15, 16, 18 and 19 thousand from 1.3. Each keeps two lines at every size above its own, and beyond the cap. The guard fails on any two-line case outside the table or below its size, and on any entry that keeps one line at its own size. So a longer Indonesian string, which `93eaa8d7`'s guard let through, now fails it, and a remedy fails it until the table shrinks or goes. `QiblaInkTest` also pins 11.000 on two lines from 1.15 by its measure.
- **The KDoc's breaks corrected:** by the host's measure the widest English, Indonesian and Turkish BestEffort details take two lines from 1.1, 1.06 and 1.2 (19,000 km: 1.15, 1.12 and 1.3), not "from 1.15" and "at 1.3"; the as-built bullet above says so too.

**R63b fix round 4 (31 October, Lane P): the guard over every figure the phone sends, and question 1 is every Indonesian place.**
- No ruling on the two questions was in the ledger when the round started (02:49). Rulings 62 and 63 are kept, and no remedy is built.
- **The guard measured too few figures** (review round 4, Important 3). `QiblaInkTest.details` took the five-figure distances and 6,600 alone, so "every other case keeps one line" held only for those. It now takes every figure the phone sends (`FIGURES`, from `WatchFeedBuilder.roundedKm`'s two significant figures: the whole kilometre to 99, tens to 990, hundreds to 9,900 and thousands to 20,000). The never-cut and inside-the-ticks tests take them too, and still pass.
- **`heldForTheLead` is keyed by the figure**, the distance in km, not its thousands (which made 7.000 and 7.900 one case). As Java2D measures, Indonesian takes two lines with every figure from 1.000 to 20.000 km but 1.100: 20.000 from 0.85, the other five-figure ones from 1.06 to 1.15, and the four-figure ones from 1.18 (6.000), 1.2, 1.24 or 1.3. Measured 3 % wider, every one: the five-figure ones from 0.85 and the four-figure ones from 1.06 to 1.24. English and Turkish are unchanged, with no four-figure case. The guard now also holds each entry on two lines at every size above its own; its KDoc said so, but it checked only the entry's own size.
- **For the Lead, question 1 covers every Indonesian place, not only 23.** The phone's `cities.csv` has 448 Indonesian places, from Banda Aceh (6.200 km) to Jayapura (11.000). By the host's measure, every one of them has its distance on two lines on a pointing dial from some font size:
  - as Java2D measures, 9 places from 1.06, 14 from 1.15, 185 from 1.2 (Medan's 6.600, Bandung's 8.000, Surabaya's 8.600, Mataram's and Palu's 9.000 among them), 198 from 1.24 (Jakarta's 7.900 among them) and 42 at 1.3 (Makassar's 9.100 among them);
  - measured 3 % wider, 23 at every size, 296 from 1.1, 77 from 1.12, 24 from 1.15 and 28 from 1.18.

  On emulator-5558, 6.600 and 9.000 kept one line at 1.3. Review round 4 puts the watch about 1.5 % from the break there: 9.000 is 126.5 units at 10 and kept one line, and 11.000 is 128.4 and took two. The remedies under fix round 1 are unchanged. The floor of 8 was measured on the five-figure figures, and the four-figure ones have a digit fewer.
- **Question 2, review round 4's new fact:** by the host's measure, Indonesian's distance for Medan, Jakarta and Surabaya is drawn at 9 at every font size it keeps one line at, 1.0 among them. Review round 4 gives `44cfbd8d`'s as 10 at 1.0 and 11.5 from 1.15.
- **Minors fixed:** the BestEffort breaks in `detailBox`'s KDoc and the as-built bullet are the widest bearings' (they were 359°'s). The guard's comment gives 10.000's break by the test's measure (1.06, not 1.2). `details` reads each string once per language, not once per text and font size, so `QiblaInkTest` runs in about 4 s with every figure (5.2 s before with a tenth of them).

**After Ruling 66 items 1 to 7 (31 October, Lane P): the detail is the watch's short form on one line, clear of the turning letters at every heading and never smaller as the font grows.** Report: `docs/superpowers/reports/2026-10-31-wear-qibla-short-detail.md`.
- **The words (item 1).** `WatchScreenWords` gains `distanceOnDial` ("%1$s km") and `qiblaDetailOnDial` ("%1$s° · %2$s km"), from `WatchWords.SCREENS`: "km" in every language but Arabic's كم, Bengali and Urdu among them, drafts to native review. The turning dial (Searching, Aligned, tilted) draws `distanceOnDial`, the dimmed dial (BestEffort, NoSensor) `qiblaDetailOnDial`. What is heard is unchanged: the detail's description is `distance` (`QiblaScreen.detailSpoken`), and a given-up compass's caption says `qiblaDetail` after its title. The vectors are the Kotlin writer's, regenerated; the Swift port reads the two fields too (see the report: its `testANullInEveryWordIsRefused` counts every key, so `swift test` could not stay green without them); `QiblaDialModel` takes them in P4.9.
- **The half anchor (item 2).** With c the caption's growth and d the drawn detail size over 10: the caption's last baseline 155 − 14·c, the detail's 155 + 14·max(0, d − c), y 141 and 155 at 1.0 (`QiblaFit.captionY`, `detailY`). Two-line captions grow upward from their last line by `TITLE_PITCH`·c; `captionWidth` and `captionBox` take the new baseline.
- **The size and the box (item 3).** `QiblaType.detail` = `Line(10f, 1.3f, floor = 9f)`, `maxLines` 1, `softWrap` false, overflow `Visible`, drawn at its own width and centred by `UnitsBox`; the second-line path, `DETAIL_PITCH` and `detailBox` are gone. The sizes are 10, 9.5, 9 at 1.0 or less, and above it from floor(20·min(f, 1.3))/2 down by halves to the size at 1.0; the first that keeps `LETTER_AIR` (1.0 unit) from every turning letter's ink at every heading, judged above 1.0 against the letters at their 1.3 size, and stays `LETTER_AIR` inside `TICK_ROOM`, is drawn (`QiblaFit.detail`). The letters judged are `QiblaScreen.dialLetters`, the feed's cardinals on every turning dial, Searching before its first reading among them. The gap is exact: 72.05 less the farthest corner of G − L, glyph box by glyph box.
- **The ink (item 4).** `GlyphInk` lays a line's runs out by `java.text.Bidi` in the page's own direction (Compose 1.12.1 resolves `TextDirection.Unspecified` to the layout's, so the Urdu page draws "6,600 km" as "km 6,600"); `QiblaInk` measures each run's glyph outlines, `TextRunShaper.shapeTextRun` at the size drawn and each glyph's `Font.getGlyphBounds` read at 1,000 px and scaled (at the size drawn they are the mask rounded out to whole pixels, a digit 0.79 em where its outline is 0.75), on API 31+, `Paint.getTextPath` per grapheme cluster on API 30, remembered per text, weight and size. On the host, `HostInk`: Java2D's Manrope outlines, and the HarfBuzz model `tools/watchface/qibla-ink.py` writes (`fallback-ink.json`: Noto Naskh Arabic Bold UI and elegant, Noto Sans Bengali UI and elegant at 600 and 800). emulator-5558 draws its Arabic in the elegant Naskh (its glyph bounds and its pixels), not the compact one the review rounds above took it for; the tests hold both.
- **The insurance (item 5).** Each frame each letter is drawn at `QiblaFit.letterAlpha`, clamp(gap / `LETTER_AIR`, 0, 1); idle wherever the size kept the air (`QiblaFitTest.t7TheInsuranceIsIdleWhereTheAirIsKept`).
- **The tests (item 6)**, `QiblaFitTest` over `QiblaSweep` (every two-significant-figure distance from 100 to 20,000 km, ten forms of the seven languages, fonts 0.85 to 2.0 in hundredths, both dials, 3 % wider, both system-font variants), judged by its own geometry (`Judge`): T1 to T7. T8 is `QiblaModelTest.theDialDrawsOnlyTheFeedsFiguresAndTheUnitAndSaysThePhonesPhrase`, `WatchWordsTest`, `WatchScreenWordsTest` and the Swift `WatchScreenWordsTests`. The least gap to a turning letter in the sweep is 1.00 unit (Bengali in Western digits, 4,400 km, at 1.3).
- **Seen on emulator-5558 (item 9's Wear half):** before (`44cfbd8d`) and after, seven languages at 1.0, 1.15 and 1.3, Searching at true 340, 145 and 215, Aligned in Cape Town, Jakarta, Paris, Istanbul and Dhaka, Indonesian tilted at 1.3, Low and BestEffort with 359° · 20,000; the debug layout log agrees with T2's table within 0.5 units and with its sizes but for English's 20,000 km at 1.3 (12 on the watch, 12.5 on the host); TalkBack reads "Makkah · 6,600 km away". The report has the shots and the table.
- **Rulings 62 and 63 (item 7).** Ruling 62 item 2 and Ruling 63 item 2 are replaced: `heldForTheLead`, `aPointingDialsDistanceKeepsOneLineSaveTheCasesHeldForTheLead`, `thePageSetsATwoLineDetailsLinesDetailPitchApart`, `aDetailsTwoLinesStandFurtherApartThanItsGlyphsReach`, `theWorstRealFiguresAreNeverCut`, `theDetailsInkKeepsInsideTheTicksAtAnyHeading`, `theDetailStandsClearOfTheCaptionAtEveryFontSize`, the French two-line case and `QiblaModelTest`'s box and second-line tests are deleted (T1 to T6 hold what they held). Ruling 63's one-source constants (`QiblaDialMeasures`, now with the hub) stay; the phone's French "de La Mecque" stays (it is what the dimmed dial says). `faces.js` gQibla and aQibla draw '6,558 km'; `ref.py`'s g-qibla mask is made from faces.js's text boxes as it renders, so it is redrawn with no change of its own.
- **Ruling 67 item 2 (31 October, Lane P): the tests hold what they claim, and nothing drawn changes.** The sweep is 48,896 series, 5,671,936 rows (708,992 on the turning dial; T1 prints it). The dimmed dial's bearings are the five whose measured ink reaches nearest the ticks (`QiblaSweep.bearingReach`), not the widest advance: Bengali's ২০০° to ২০৪° where the advance took ১০০ to ১০৪ (`t3TheDimmedSweepTakesTheBearingsWhoseInkReachesNearestTheTicks`). T6's caption gap is held in all seven languages (`t6TheCaptionGapIsNeverTighterThanAtTheDrawingsSizeInEveryLanguage`): the room from the lowest ink of each caption's last line, as the page breaks it, to the highest of the detail's, in HostInk's model of the watch's Naskh and Bengali fonts, which `qibla-ink.py` now writes with the captions' words; the least is 0.59 units, Arabic's tilt notice in the elegant Naskh over "100 كم". `t2TheDialsLettersGrowAsTheirLinesSay` pins `QiblaType.cardinal`'s cap and floor to north's growth, which the page draws every letter at. `WatchWordsTest` holds the short forms' shape, not each form, and HostInk draws a format character with no ink, so a native reviewer's form is measured as the watch draws it and ships if `QiblaFitTest` passes. T1's KDoc says what binds it. Report: `docs/superpowers/reports/2026-10-31-ruling-67-tests-and-docs.md`.
  - **After its review round 1: what a reviewer's form touches, and the caption's space.** No test pins a short form: `QiblaModelTest`, `WatchScreenWordsTest`, `WatchFeedJsonTest` and the Swift `WatchScreenWordsTests` read the feed's, the fixture's or the fallback's own. What a form touches besides, by language: French, Turkish, Indonesian and Bengali: `WatchWords.kt` alone. Urdu: also its golden `watchcore/vectors/phone/phone-capetown-ur24.json`, regenerated (`./gradlew :shared:testAndroidHostTest -Ptaqwa.updateVectors`). Arabic: also `Fixtures.ARABIC_SCREENS`, `:watchcore`'s hand copy, which `WatchFeedSampleTest` holds to the builder; then `:watchcore`'s vectors (`./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`: the two Arabic samples, `feed-vectors.json`, `watch-rules-vectors.json`), then the golden `phone-tripoli-ar-ly12.json`, regenerated as Urdu's is. English: also the watch's own fallback, `WatchScreenWords.ENGLISH` in `:watchcore` and the Swift port's `english` (main source: a feed without the field draws it), then the vectors and the three English goldens the same way. A new unit in Arabic or Bengali letters also reruns `tools/watchface/qibla-ink.py`. Seen whole on reviewer's forms written as `\u` escapes (English and Arabic with no-break spaces, Arabic with a right-to-left mark before كم, Urdu with the mark and km first): those files alone changed, the ink model stayed the fonts', and `scripts/test.sh` passed. On `watch-companion`, plan 4 Task 9's Swift tests (P4.9) pin English's and Arabic's drawn forms too (`QiblaScreenTests`, `QiblaCompassTests`, `WatchQiblaTests`, `TaqwaWatchUITests/QiblaTests`), and `compass-vectors.json` regenerates with the rest. The space between two Arabic, Urdu or Bengali words is Manrope's (0.200 em), as minikin draws it (U+0020 is not one of its sticky marks, and the first family that has a character draws it), and each word comes whole from the model, shaped alone (`t7TheSpaceBetweenTheSystemFontsWordsIsManropes`); `HostInk`'s path that set a caption from its words and the font's own space was never reached, and is gone.
  - **After its review round 2: the no-break space.** A no-break space (U+00A0) is one of minikin's sticky marks and both system fonts' cmaps have it, so on the watch it is the font's own after Bengali digits or letters (0.26 em), between Arabic-Indic or Extended digits grouped by it and after Arabic letters (Naskh's 0.2207), the Arabic letter mark between or not, and Manrope's (0.200) where it opens its run (between Arabic-Indic digits and كم on a right-to-left page) or follows a direction mark, embedding or isolate (emulator-5558, 31 October). HostInk had Manrope's everywhere, 0.02 to 0.06 em narrow; now `qibla-ink.py` holds each font's own no-break space, every single held to the font's cmap, and checks a grouped number and each dial word on both sides of one against it shaped whole; `HostInk` sets such a run from its parts, carries the run's font across the Arabic letter mark and the other format characters but for the bidirectional controls, and `t7ANoBreakSpaceIsTheFontOfWhatStandsBeforeItInItsRun` holds eleven lines to their parts and, in the elegant fonts, to the watch's advance (within 0.0012 em). The narrow no-break space (U+202F) stays the stand-in's quarter em: neither font's cmap has it, and the watch draws its next fallback's 0.2485. The round-1 forms seen whole put their no-break spaces where Manrope's is the watch's. Report: the same, its last section.
- **Ruling 68 item 4 (6 November, Lane P): every caption over a turning dial keeps its air from the letters, by the half anchor's rule.** Ruling 62 item 5's turning "E" met the last "l" of "Hold your wrist level" (P3.9's `qibla-minors/`), and Turkish and Indonesian "Facing qibla" (French at 1.3) met letters too: by Ruling 66 item 3's exact geometry 4,332 caption lines of the sweep came within `LETTER_AIR`, the least −15.0 (Bengali's notice at 1.3). Now a caption over a turning dial (the bearing, "Facing qibla", the tilt notice) has one box at every font size (`QiblaType.captionBox` with a `Turning`, `turningBox`): the letters' room at its last baseline (`QiblaFit.captionRoom`, the widest box from its glyphs' highest to lowest ink that keeps the air at every heading: w/2 = √((72.05 − 1)² − Y²) − a), at the drawing's font size and at 1.3, never wider than `captionWidth`; one line while it fits there at its floor, else two growing upward on the half anchor (its last line keeps 155 − 14c, its first a pitch above, both at the size drawn); two lines no larger than keeps the first line's ink `HUB_AIR` (2) from the needle's hub with the caption at its highest (English 17 at 1.3, not 18), and set from the size TextAutoSize draws them at in that box (`drawnSize`: the largest of its steps from the floor whose lines fit; review round 1 found the box set from its largest size, so French "Gardez le / poignet à plat", drawn at 13.5 in a box of 15, stood 1.6 units off the anchor at 1.0 and 3.2 at 1.1 to 1.3, and a largest size between two steps lifted Turkish and Indonesian "Facing qibla" 0.37 at 1.1). The page gives `TextAutoSize` the box's size a thousandth of a unit over, so its last step up to a size on its grid is never lost to a float's rounding of pixels. The dial is never cleared. So English, French, Turkish, Urdu and Bengali draw the notice on two lines, Turkish and Indonesian "Facing qibla" too (T9's table), Arabic keeps one line (at 12); Indonesian's notice is a shorter draft, "Pegang jam mendatar" (the phone's approved "Pegang ponsel mendatar" with the watch for the phone: "Jaga pergelangan tetap datar" fitted no two lines at 11, its "pergelangan" alone 95.7 units at 11 against a room of 95.4), to native review. Tests, red first: `QiblaCaptionTest` T9 (every caption line ≥ `LETTER_AIR` from every letter at every heading, never cut, ≥ 2 from the hub, never shrinking nor changing its lines or box as the font grows; ten forms, both system-font variants, fonts 0.85 to 2.0, 3 % wider; the room against the judge); `QiblaFitTest` T6 holds the caption's box before the first reading as with one, and T6's Indonesian hub test became T9's for every caption. Least air 2.50 since review round 1 (French's notice measured 3 % wider at 1.0, its last line now on the anchor; 3.15 before, Urdu's notice in the elegant Naskh at 1.1), least room to the hub 2.33 (Turkish at 1.2); T9 also holds every caption's last baseline on `QiblaFit.captionY` (red on the base: 1,001 rows, the farthest French's notice at 1.1, 3.20 high), and `QiblaModelTest` the size drawn and the room at the letters' largest (a box judged at 1.0 alone had passed every Qibla test). A dimmed dial's title is still set from its largest size, as since Task 7 (up to 0.43 units high where it is drawn between two steps, Wear's 1.06, 1.12 and 1.18). Report: `docs/superpowers/reports/2026-10-24-wear-app-pass/qibla-r68/README.md`.

### Task 8: The Tile

**Starts after Task 5** (the lanes): it builds on Task 5's `RowModel` and `dayRows`.

**Files:**
- Create, in `wearApp/src/main/kotlin/world/taqwa/app/wear/tile/`:
  - `TilePlan.kt` (pure, with `TileType`), `TileLayout.kt` (ProtoLayout), `TodayTileService.kt`, `TileUpdates.kt`, `Throttle.kt` (pure);
  - `wearApp/src/main/res/drawable/tile_preview.png`: the English 14:58 shot from Step 5.
- Modify:
  - `gradle/libs.versions.toml` and `wearApp/build.gradle.kts`: `wear-tiles`, `wear-protolayout` (1.4.2) and `concurrent-futures-ktx`, the newest stable;
  - `wearApp/src/main/AndroidManifest.xml`: the service, with `BIND_TILE_PROVIDER` and the preview meta-data;
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/FeedStore.kt`: the `onNewerVersion` listener;
  - `TaqwaWearApp.kt`: the feed store's `onStored` and `onNewerVersion`, and `onAppOpened`, also call `TileUpdates.request(this)`;
  - `wearApp/src/debug/kotlin/world/taqwa/app/wear/DebugFeedReceiver.kt`: `--ez clear true` also calls `TileUpdates.request`.
- Test:
  - in `wearApp/src/test/kotlin/world/taqwa/app/wear/tile/`: `TilePlanTest.kt` and `ThrottleTest.kt`;
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/FeedStoreTest.kt` (add).

**Interfaces:**
- Consumes:
  - Task 5's `dayRows(…, LitRule.GLANCE)`, `RowModel` and the `LongestCity` test helper;
  - Task 4's `noFeedLine`, `Line`, `WatchUnits.chordUnits`, `WatchUnits.sp` and `EXTRA_PAGE`;
  - Task 3's `WatchRules.clockText` and `spokenClock`, and `WatchTime`;
  - plan 1's `CountdownExpression.countdown` and `CountdownExpression.spoken` (the dynamic countdown and its sentence), and `ComplicationSources.requestUpdates`.
- Produces on `FeedStore` a third constructor parameter, `onNewerVersion: () -> Unit = {}`. It is told after each write that answered `NEWER_VERSION`, outside the write's lock, and a listener that throws cannot fail the write, as `onStored`'s rule is. A newer format is then on the Tile at once, not at its next refresh.
- Produces `internal data class TileNext(val label: String, val clock: String, val spokenClock: String, val startAt: Long, val endAt: Long, val staticCountdown: String)`. `clock` is `clockText` and `spokenClock` is `spokenClock`, of `WatchTime.of(next.prayer)` on the next start's own day. The rows' times, Sunrise's among them, are `dayRows`', so they take the same two functions.
- Produces `internal data class TileEntry(val from: Long, val until: Long?, val next: TileNext?, val rows: List<RowModel>, val city: String, val notice: String?, val update: String?)`.
- Produces `internal object TilePlan`:
  - `fun entries(feed: WatchFeed?, newerVersionSeen: Boolean, now: Long): List<TileEntry>`:
    - entries break at every instant of every day and at every midnight, up to 10 prayer periods ahead (about two days);
    - they are contiguous, and none overlaps another;
    - `city` is `place.city`, whole: the layout cuts it, never the plan;
    - `update` is `words.updateOnWatch` in every entry when `newerVersionSeen`, and null otherwise. The layout draws it in the city's place, in the accent (call 5);
    - the last entry is the notice, with a null `until`: `words.updateOnWatch` when `newerVersionSeen`, else `words.openOnPhone`;
    - with no feed, the result is empty.
  - `@StringRes fun emptyLine(newerVersionSeen: Boolean): Int` is Task 4's `noFeedLine(newerVersionSeen)`. The service draws it, with no feed, as the pages do.
  - `fun freshnessMillis(entries: List<TileEntry>, now: Long): Long`: until the notice's entry, at most six hours.
- Produces `internal object TileType` (call 15). The Tile cannot scroll, so its pitches stay the drawing's, and every line is capped at 1.1:
  - `label` = `Line(10.5f, 1.1f)` and `countdown` = `Line(12f, 1.1f)`;
  - `rowName` and `rowTime` = `Line(12f, 1.1f)`, and `sunrise` = `Line(10f, 1.1f)`;
  - `city` = `Line(10.5f, 1.1f)`: one line, cut with an ellipsis inside `chordUnits(184f, round)`.
  - ProtoLayout has no auto-size, so a Tile line is drawn at `range(fontScale).endInclusive`, with its `maxLines` and `TEXT_OVERFLOW_ELLIPSIZE`. `fontScale` is the request's `deviceConfiguration.fontScale`.
  - ProtoLayout takes font sizes in sp only, and its renderer turns sp into pixels through the platform's own non-linear conversion. So a size is `WatchUnits.sp(units, Density(deviceConfiguration.screenDensity, fontScale))`, the pages' own call, which inverts Compose's copy of the platform's tables.
  - The residual is the difference between that copy and the renderer's tables, and it is small at the Tile's sizes. Even the old `units × unitDp / fontScale` would draw at most 1.6% large there: measured on ui-unit 1.10.0, 15.22 dp for a 14.98 dp target, 12 units at a font scale of 1.1. Within its 21-unit pitch that cannot overlap. `TileType`'s KDoc records this, and Task 9 Step 4's large-text Tile shot is the renderer's own answer.
- Produces `internal object Throttle { fun due(lastAt: Long?, now: Long, gapSeconds: Long): Boolean }`. It is used with `complications.asked` in `filesDir` and a 21,600 s gap.
- Produces `class TodayTileService : TileService`:
  - `onTileRequest` answers through `SuspendToFutureAdapter.launchFuture` with a `Timeline` of `TilePlan.entries(feedStore.changes.value, feedStore.newerVersionSeen.value, now)`, or, when that is empty, one entry of `emptyLine`;
  - it calls `ComplicationSources.requestUpdates` when `Throttle.due`, which is Ruling 22 from the Tile;
  - a tap is a `LaunchAction` to `WearMainActivity` with `EXTRA_PAGE` = `TODAY`.
- Produces `object TileUpdates { fun request(context: Context) }`, which calls `TileService.getUpdater(context).requestUpdate(TodayTileService::class.java)`.

**The layout** (`gTile`, in units of `deviceConfiguration.screenWidthDp / 200`):
- **The countdown line at y 40:** the label (`TileType.label`, 800 weight, in the accent) and a Text whose `StringProp` takes the dynamic countdown, its static value `staticCountdown`, and a layout constraint the longest of the four phrases at 99 h 59 min (`TileType.countdown`).
  - Below renderer schema 1.2, the line is the label and `clock` instead (call 6).
- **The rows from y 64**, 21 units apart, Sunrise 17 (as built since 9 November 2026: without Sunrise, where the phone's Show sunrise is off, the five share the six rows' room, 24.4 units apart, from 64 to 161.6): the pip at x 46 (current: a 5.6-unit halo at alpha 0.2 and a 3.3-unit disc; passed: a 2.4-unit slate dot; upcoming: a hollow 2.3-unit ring), the name at x 56, and the time ending at x 150.
- **The city at y 184** (`TileType.city`), or `update` in its place.
- **Right to left.** What is seen follows `feed.ui.rtl`, never the watch's locale. But the renderer inflates a Row in the host's own layout direction, which comes from the watch's language: ProtoLayout documents `HORIZONTAL_ALIGN_START` and `END` as adjusted for the locale. On an Arabic watch it would then mirror an explicitly ordered row a second time. So:
  - In `TilePlan.kt`, pure: `internal enum class RowPart { PIP, NAME, TIME }`, and `internal fun <T> inTileOrder(parts: List<T>, rtl: Boolean, hostRtl: Boolean): List<T>`, which is `parts` reversed when `rtl xor hostRtl`, and `parts` otherwise. `fun rowOrder(rtl: Boolean, hostRtl: Boolean): List<RowPart>` is `inTileOrder(listOf(PIP, NAME, TIME), rtl, hostRtl)`. The countdown line's label and countdown go through `inTileOrder` the same way.
  - `hostRtl` is `resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL`, read in the service's own process at each request. The service's configuration follows the watch's language, as the renderer's does.
  - Positions come from fixed widths and spacers, never from a START or END alignment. So a row's children order is the one thing the host's direction can turn.
  - Step 5's first check proves the renderer does mirror. If it does not, the service passes `hostRtl = false`, the ledger records why, and the tests stay as they are.
- **A screen reader:** the line's `Semantics` content description is `CountdownExpression.spoken(spoken, zero, name, next.spokenClock, endAt, longest)`, dynamic, so a 12-hour Tile says its day period. Each row's is `spoken`, with its state, and `update`, when there is one, is read after the rows.

- [ ] **Step 1: Write the failing tests:**
  - `TilePlanTest`, on the sample, with `newerVersionSeen` false unless named:
    - `atTwoFiftyEightDhuhrIsLitAndAsrIsNext`: the first entry runs from now to 16:10, Dhuhr is CURRENT, the label is "ASR", and the static countdown is "in 1 h 12 min" at 14:58:00.
    - `theTileKeepsFajrLitUntilDhuhr`: at 06:40, Fajr is CURRENT. Today's list differs there (Task 5).
    - `entriesAreContiguousAndNeverOverlap`.
    - `theTimelineEndsInTheNotice`: the last entry's `notice` is "Open Taqwa on your phone", with a null `until`.
    - `aSpentFeedIsTheNoticeAlone`.
    - `aNewerVersionAsksForTheUpdateInTheCitysPlace`: with `newerVersionSeen` true, every entry's `update` is "Update Taqwa on your watch", the rows are still drawn, and the last entry's `notice` is "Update Taqwa on your watch".
    - `withNoFeedTheTileSaysWhatThePagesSay`: `entries(null, false, now)` and `entries(null, true, now)` are empty; `emptyLine(false)` is `R.string.open_taqwa_on_phone`, and `emptyLine(true)` is `R.string.update_taqwa_on_watch`.
    - `aTwelveHourTileSaysItsDayPeriod`: on `english12` at 14:58, `next.clock` is "4:10 PM", and `next.spokenClock` is FSI + "4:10 PM" + PDI. The first entry's Sunrise row has the time "6:01 AM" and the spoken "Sunrise, " + FSI + "6:01 AM" + PDI.
    - `theLongestCityIsCarriedWhole`: with `place.city` = `LongestCity.name()`, every entry's `city` is that name, unchanged. `TileType.city.maxLines` is 1.
    - `theTileGrowsLittleBecauseItCannotScroll`: `TileType.rowName.range(1.3f)` is 12..13.2, and `range(1.0f)` is 12..12.
    - `aTileLineIsDrawnAtItsUnits`: on 227 × 227 and `Density(2f, s)` at s = 1.0, 1.1 and 1.3, each `TileType` line at `range(s).endInclusive`, through `WatchUnits.sp`, converts back (`toDp`) to `units.x` of that size within 0.01 dp.
    - `aFarNorthNightHasNoEmptyEntry`: `tromsoMay` has no entry with `from == until`.
    - `freshnessIsAtMostSixHours`.
    - `theTileIsLaidOutInThePhonesOrder`, over both inputs:
      - `rowOrder(rtl = false, hostRtl = false)` and `rowOrder(true, true)` are `[PIP, NAME, TIME]`: an English feed on an English watch, and an Arabic feed on an Arabic watch, whose renderer mirrors it into Arabic order;
      - `rowOrder(true, false)` and `rowOrder(false, true)` are `[TIME, NAME, PIP]`;
      - `inTileOrder(listOf("label", "countdown"), …)` follows the same four cases.
  - `ThrottleTest`: null is due; 21,599 s is not; 21,600 s is.
  - `FeedStoreTest.aNewerFeedIsToldToTheNewerListenerAndNothingElseIs`: a version-2 write tells `onNewerVersion` once and `onStored` never. An accepted write and a broken one do not tell it. A listener that throws does not fail the write that reported `NEWER_VERSION`.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the code. Add the libraries, and record the release dependency diff in the ledger. Only AndroidX and `com.google.guava:listenablefuture` may join, and no permission.
- [ ] **Step 4:** Run the tests, `assembleRelease`, `lint` and the merged-manifest check. Expected: PASS, and the permissions as Task 4 left them.
- [ ] **Step 5: Verify on emulator-5558:**
  - Add the Tile: `adb -s emulator-5558 shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component world.taqwa.app/world.taqwa.app.wear.tile.TodayTileService`. Then `show-tile`.
  - **First, the renderer's direction.** Set the watch's language to Arabic in its Settings, and record the taps in the README, since Task 9 reuses them. Shoot the Tile at 14:58 on the ar-ly12 sample and on the en sample. Expected: the Arabic feed's pips on the right with the times on the left, and the English feed's pips on the left with the times on the right. That is the same as with the watch in English.
    - If both come out reversed, `hostRtl` is wrong for this renderer: it does not mirror. Pass `hostRtl = false`, record it in the ledger, and shoot again.
    - Set the watch's language back to English (Global Constraints).
  - Shots at 14:58 and 06:40, in en, en12, ar-ly12 and ar-sa, against `ref.py screen=g-tile`. The countdown moves a minute later with the app's process dead (`pidof`).
  - `--es city` with `LongestCity`'s name: the city is one line, cut with an ellipsis inside the round edge.
  - The states, with no paired phone running:
    - `--ei version 2` over the sample: "Update Taqwa on your watch" in the city's place, at once, with the rows drawn;
    - `--ez clear true`: "Open Taqwa on your phone" alone;
    - then `--ei version 2`: "Update Taqwa on your watch" alone;
    - a plain `DEBUG_FEED` brings the rows back.
  - A tap opens Today.
  - TalkBack walks the line and the rows. On en12 the line is said with "PM".
  - A newly stored feed (`DEBUG_FEED`) redraws the Tile.
  - Remove the Tile at the end.
- [ ] **Step 6: Commit** with the message "Wear: the Tile, the day's times with the widgets' lit row and the next prayer counted down every minute without the app running, and the watch's own line when there is no feed".

**As built (30 October, Lane W):**
- **The renderer's quota (erratum p3 #6, CRITICAL), measured in a Step 0 spike** on emulator-5558 (renderer schema 1.500), with throwaway Tiles never committed. A Tile's dynamic values share a quota of 200 nodes. A fixed value costs nothing, every operation costs one, and a repeated part of an expression is not shared. Over the quota, the renderer logs "Dynamic type expression limit reached" and keeps the static value. The evidence: ten Texts of 50 such nodes bound 4, of 20 bound all 10, of 21 bound 9, and of 24 bound 8. Plan 1's `CountdownExpression.countdown` for the night is 380 such nodes (663 counting its fixed values), and 796 with `CountdownExpression.spoken` beside it, so the plan's line would never move. A 108-node countdown moved each minute with the app's process killed (`run-as … kill -9`, `pidof` empty).
- **The fallback, built test-first:**
  - The Tile's countdown and its sentence are **chains of the rules' own words** (`TileCountdown`). Each link says "while no more than m minutes are left, say `WatchRules.countdown` (or `spokenCountdown`) for m". A link costs five nodes. The words every link shares are said once, around the chain, for the timeline's weight. Every language, digit set and plural is the rules' by construction.
  - So an entry is at most **ten minutes** of the time left long (`TilePlan.STEP_SECONDS`, cut at 15:00, 15:10 and so on for a start at 16:10, and since review fix round 2 at 16:08, the last two minutes being an entry of their own, `LAST_MINUTE_SECONDS`), and its chains hold a margin of half a minute either side (`MARGIN_SECONDS`; a minute before review fix round 1), for a renderer that changes entries a little late. **Since review fix round 3** an entry is six minutes at most (five since review fix round 4), shorter towards each change, and its chain reaches as far past its end as the renderer may still show it (below). The heaviest entry is 188 nodes (172 since review fix round 4), every node and the bindings counted, as Google counts them, against the quota of 200 (`TileLayoutTest.everyEntryIsWithinTheRenderersQuota`, over the four samples, the phone's Urdu and Tromsø).
  - Each entry carries its whole layout: an hour of ten-minute entries is about 35 KB of the one binder call the Tile is sent in, so ten periods of moving countdown would be about 1.7 MB. The countdown therefore moves for the timeline's **first three hours** (`MOVING_SECONDS`; **80 minutes since review fix round 3**). Past them, each entry's line says the start's time in the countdown's place, call 6's form ("ASR 16:10", said "Asr, 16:10"), true for as long as it is shown. The timeline still holds ten periods and ends in the notice. The whole Tile is 152 to 166 KB in the four samples, and up to 178 KB with the update (`MAX_TIMELINE_BYTES` 200 KB; 189.6 KB at most over 48 hours of requests after review fix round 2, and since review fix round 3 168 KB, 178 KB at Tromsø). Those are English's figures: in the phone's other languages, on 12 hours and with a long city Tromsø's nights weigh more, and since review fix round 5 the moving part ends sooner there (below).
  - The Tile is fresh for **two hours** at most (`MAX_FRESH_MILLIS`, within Ruling 22's six), an hour short of the moving part, so the system asks again while the countdown still moves. A request it misses leaves the Tile saying what is true, never a stale countdown. (**Since review fix round 3**: 45 minutes, short of the 80 even if the system's own request comes three quarters late; a missed request leaves the start's time, never a countdown, but past the moving part a start can come late on the Tile, below.)
- **The Interfaces, as built:** `TileNext` also carries `name` (said in sentences), `staticSpoken` (gone since review fix round 4, whose static values are call 6's form) and `minutes` (the chain's `CountdownStep`s, empty past the moving hours). `FeedStore`'s new listener is its **second** parameter, before `onStored`: as the third, every `FeedStore(file) { … }` trailing lambda (`TaqwaWearApp`'s and six tests') would have become the newer-format listener, which those tests caught.
- **The layout.** The rows' baselines are Today's list's (`TodayLayout.list`), so the Tile is that list's end. Since review fix round 1 the Tile is a stack and each line has a box its own fonts' height (below); before it, a text sat in a row's box by its padding, with its baseline 0.35 of its size below the box's middle, which cut every Arabic line. On emulator-5558 the English Tile at 14:58 matches `gTile` to 1 or 2 px a row, and its pips to 1 px. The pips are rounded boxes: the halo at alpha 0.2 with its disc, the passed dot, and the upcoming ring (a black disc, its 1.3-unit hairline border). The label and the countdown stand 7.5 units apart, `gTile`'s 20 px, measured, whatever word the countdown shows: since review fix round 2 the words are set against the label in their room, the widest of the entry's words, not centred in it. The weights are ProtoLayout's stable bold or normal plus the system font's own weight axis (`FontSetting.weight`); its medium is still experimental. Counts and times take `FontSetting.tabularNum`. A no-feed and a notice Tile are one centred line at 15 units, three lines at most.
- **The times' column is Today's** (`TodayLayout.timeEnd`): at 150, as drawn, unless a row's name and time need more room, when the column moves towards the round edge. Each name and time is measured on the watch before the Tile is sent (`TileMeasure`, `PaintMeasure`: the system font at the drawing's weight, tabular for the times). At 150 alone, a 12-hour Friday's "Jumuʿah" was cut to "Jum…" beside "12:33 PM" on the device. Each measured width keeps 1.5 units to spare (`MEASURE_SLACK`): "Jumuʿah", measured to fill its box exactly, was still drawn "Jumuʿ…", because the renderer rounds a box to whole pixels and a text view asks a pixel more than its advance. With it, the column ends at 155.5 units on that Friday and the name is whole. The name's box takes what the time leaves (`weight`), aligned LEFT or RIGHT, which ProtoLayout keeps absolute.
- **The renderer's direction (Step 5's first check).** The system language was set to Arabic with plan 2's `SetLocale.java`, since Wear OS 6's Settings say "The watch language is the same as your phone's". With the service reading its configuration's direction, both feeds came out reversed: the Arabic feed's pips on the left, the English feed's on the right. The renderer lays a Row out left to right whatever the watch's language, so the service passes `hostRtl = false` (`TodayTileService.HOST_RTL`), as the plan's fallback says, and the tests stay as they are.
- **The city and the update are said.** With no speech of their own, TalkBack passed the city by. The city's box now says the city, and the update's says the update whole, after the rows.
- **The update in the city's place (call 5).** At the city's 10.5 units the English line is about half as wide again as the chord at 184. ProtoLayout's own sizes (`FontStyle.setSizes`) drew it on one line, cut to "Update Taqwa…", on the device. So it is fitted on the measured widths (`fitUpdate`): one line at the city's 184 when it fits; else two lines, the second at 191 and the first 1.2 of the size above it, below Isha's row (whose box then ends at 168). The size is the largest from the city's 10.5 down to 8 units at which the words, as many on the first line as fit it, leave a second that fits its chord, with the same slack ("Update Taqwa on your", 0.3 units inside its line, was drawn "Update Taqwa on y…"). On the device the English update is "Update Taqwa on / your watch" at 10.5, and ar-sa's is one line. Each line is its own Text, and their box is said whole.
- **A tap brings the one activity forward.** `WearMainActivity` is a single task, and `onNewIntent` turns the pager to the page `EXTRA_PAGE` names. An intent that names none, the launcher's, leaves the page as it was. Before this, every Tile tap would start a second activity in a task of its own, with its own tasbeeh counts, which the first could write over when it came back (P3.6's carried minor). `TileManifestTest` holds it.
- **The preview** is the Step 5 shot of the English Tile at 14:58, density-less (`drawable-nodpi`, which lint asks for). Lint's request for a square preview beside it is ignored on the meta-data with the reason: the Tile draws the same on a square screen.
- **The libraries** are `androidx.wear.tiles:tiles` 1.6.2, the newest stable, whose POM names protolayout 1.4.2, plus `protolayout` 1.4.2 and `concurrent-futures-ktx` 1.3.0. The release classpath gains those, `tiles-proto`, `protolayout-material3` and `protolayout-material-core`, and `concurrent-futures` moves from 1.1.0 to 1.3.0; `listenablefuture` 1.0 was already in. The merged release manifest still asks for VIBRATE and androidx.core's permission alone. It gains the library's `<queries>` for `BIND_UPDATE_REQUESTER` and its optional `uses-library wear-sdk`.
- **Step 5, on emulator-5558 (two passes, under the `wear-5558` lock, put back as found each time).** Wear OS 6's debug surface adds and removes the Tile but has no `show-tile`, so the Tile is reached by a swipe from the face and found by its words (README).
  - The eight shots (en, en12, ar-ly12 and ar-sa at 14:58 and 06:40) match `gTile` with the words masked, the Arabic ones mirrored: 0.13% of the pixels differ at 14:58 and 0.34% at 06:40, all in the pip column. At 06:40 the difference is the widgets' rule lighting Fajr until Dhuhr, which the drawing's list rule does not. The words, read from the accessibility tree, are the phone's in each language and digit set, 12-hour times with their day period.
  - The countdown and its sentence moved a minute later with the app's process killed (1 h 10 to 1 h 9, `pidof` empty for 70 s). The renderer may bind the service again at once after a kill, so `pidof` is checked through the minute.
  - The longest city (Bengali, 33 characters) is one line, cut with an ellipsis inside the round edge.
  - `--ei version 2` over the sample drew the update in the city's place at once, with the rows. `--ez clear true` drew "Open Taqwa on your phone" alone, then `--ei version 2` "Update Taqwa on your watch" alone, and a plain `DEBUG_FEED` brought the rows back. Every stored feed redrew the Tile.
  - A tap with the app open on Tasbeeh brought its one task forward, on Today.
  - TalkBack reads the line ("Asr in 1 hour 10 minutes, at 4:10 PM" on en12), the six rows (five without Sunrise, since 9 November 2026) with their states first ("Passed. Fajr, 4:33 AM"), then the city, or the update whole.
- **Review fix round 1 (30 October, Lane W), the five Importants of `acda73e4..11782f2e`:**
  - **The notice runs on.** The last entry had a start alone, so an end of 0, and the renderer shows an entry only while start ≤ now < end, else the closest one: past the last start it kept counting down to a prayer that had begun (seen on emulator-5558). Its end is now the last instant there is (`TileLayout.OPEN_END`). `TileLayoutTest.theNoticeIsShownOnceTheLastStartHasCome` reads the built timeline by the renderer's own rule (`shownAt`). On the device an en12 Tile drew the notice when the clock crossed Tuesday's 12:33 by itself, with no new request.
  - **Every line in its own fonts' height.** The Tile is a stack the drawing's 200 units tall. Each line is a child with a box as tall as its line in the fonts it is drawn in, measured on the watch (`TileMeasure.extent`, `PaintMeasure`: one `StaticLayout` line with its spacing from the fallback fonts, as a text view sets it, from API 28), placed on the drawing's baseline and free to reach into its neighbours' room, as the drawing's glyphs do. The watch's Naskh sets a line 1.70 of its size tall to Roboto's 1.17, and the Roboto-sized boxes cut every Arabic Sunrise, the Urdu rows and the update, and Isha above the update. A pip's middle stays 0.35 of its row's size above the baseline. Each box runs across the screen from its line to the stack's bottom, because Android hands a screen reader a view group's children by place: boxes all beginning at the stack's top were read tallest first, the city before the line (TalkBack, on the device). `noLineIsCutAndEachStandsOnTheDrawingsBaseline` and `aScreenReaderTakesTheLineTheRowsThenTheCity` hold both. On the device the English Tile is within a pixel of the first pass's, and nearer `gTile`.
  - **The update fitted where it is drawn.** At each size from 10.5 down to 8 it is first one line, at the lowest baseline from 184 upwards where it fits, its line never reaching above Isha's row (`UPDATE_TOP`), then two lines, the second at 191 and the first its fonts' pitch above it, 1.2 to 1.55 of its size; every line is checked against the chord at its own baseline. A one-line result had been checked at the first line's chord and drawn at 191, which cut both Arabic samples' update. On the device the Arabic and the phone's Urdu updates are one line, whole.
  - **The countdown line fitted.** `fitLine` fits the label with its tracking, `gTile`'s gap and the widest of the entry's words, measured, inside the chord at 40, at the largest size from the line's own down to `TileType.countdown`'s floor of 8 (the label's 7), the label, its tracking and the gap shrinking with it; both Texts end in an ellipsis. The countdown's room (`StringLayoutConstraint`) is the widest of its words as measured, not the one of most characters. At 14:58 the line is still `gTile`'s; "MAGHRIB in under a minute" and the French "MAGHRIB d’ici une minute" draw whole and smaller. `theCountdownLineFitsInEachOfTheSevenLanguages` holds the seven languages' widest labels beside their "99 h 59 min" and under-a-minute, in every digit set, at 1.0 and 1.1, in the emulator's Roboto's own advances.
  - **The quota counts every node.** `LayoutWalk.quotaCost` counts the fixed values too, as `DynamicNodes`' KDoc and Google's page do, and the binding. Counted so, a minute's margin made 204 nodes in 17 of 20 moving entries; half a minute makes twelve minutes a chain and 188 at most.
  - Report: `docs/superpowers/reports/2026-10-30-wear-tile.md`, "Review fix round 1".
- **Review fix round 2 (30 October, Lane W), the one Important of `acda73e4..1538d8ad`:**
  - **The last minute is an entry of its own.** Each chain reaches half a minute past its entry's end, so the last ten-minute entry of every moving period held "in under a minute", and `fitLine` fitted the whole line to it for all ten minutes (10 units before Maghrib in Roboto's advances, 11 before Dhuhr, 11.5 before Fajr and Isha). The period is now cut two minutes before its start too (`TilePlan.LAST_MINUTE_SECONDS`): the minute and its margin, rounded out to the whole minute every other cut falls on, so the entry before it still reads right for a whole minute past its end. Only that entry says under a minute; the eight minutes before it say "in 10 min" at most and keep the drawing's 12 and 10.5 units (`theLastMinuteHasAnEntryOfItsOwn`, `theTenMinutesBeforeMaghribKeepTheDrawingsSizeAndGap`, `theLastMinuteIsDrawnSmallerAndWholeInItsOwnEntry`).
  - **The countdown is set against the label.** Its room is the widest of the entry's words, and ProtoLayout's layout constraint centres the words in it by default, so a shorter word stood further from the label ("in 59 min" about 10 units more in the room of "in 1 h 10 min"). The renderer sets the words in a frame that wide at the constraint's alignment, its start and end in the host's direction (ProtoLayoutInflater's `inflateText`), so the alignment is the start of the order the row is handed: START when the feed's direction and the host's agree, END when they differ (`TileLayout.towardsTheName`). `everyWordTheCountdownShowsStandsAtTheDrawingsGapFromTheName` reads every word of every moving entry from 14:58 and 18:50, in four samples and the phone's Urdu, on both hosts, at font 1.0 and 1.1, as the renderer places it.
  - On emulator-5558: "ASR in 1 h" at 15:09 in the room of "in 1 h 1 min" stands 18 px from the name, as at 14:58, and ar-sa's 19 px; "MAGHRIB in 8 min" at 18:55 is at the drawing's size; the last minute's entry is smaller and whole. The English Tile at 14:58 is pixel-identical to round 1's, and is now the preview.
  - **Found, for the Lead (CRITICAL, not fixed):** the renderer changes entries late. SysUI's timeline manager sets each change with an inexact `AlarmManager.set(RTC)`, whose window is three quarters of the time from the entry's first showing to its end, and on emulator-5558 the change came at the window's end: the countdown said "in 59 min" from 15:10:47 to 15:13:41 while the rules said 58 to 56. So since round 0 the Tile can show a frozen countdown, and at a start the old rows, for up to three quarters of an entry's length. The options are in the report.
  - Report: `docs/superpowers/reports/2026-10-30-wear-tile.md`, "Review fix round 2".
- **Review fix round 3 (30–31 October, Lane W), the Critical both reviews of `acda73e4..67f7e06d` raised (round 2's own escalation; no Lead ruling had come, so it was fixed within the plan's own rules, calls 6 and spec 2's minute-accurate update):**
  - **The renderer's change, modelled.** SysUI's timeline manager moves on with `AlarmManager.set(RTC, …)`, never sooner than a minute after it last moved, and an inexact alarm may come three quarters of the time it was set for late. So an entry from `from` to `until` may still be shown until `until + max(¾ (until − from), 105 s) + 10 s` (`TilePlan.shownUntil`); the three latest changes `dumpsys alarm` showed on emulator-5558 fall within it (`theLatenessCoversWhatEmulator5558Showed`).
  - **No frozen countdown.** Each chain reaches from the half minute before its entry to the last second the renderer may still show it. An entry of the moving part is therefore six minutes at most (`STEP_SECONDS` 360: its twelve minutes of chain, the half minute before and the four and a half after, are 188 nodes, as round 1's ten-minute entry was). Only the entry that ends at a start says "in under a minute"; an earlier one shown late into the last minute says the start's time there, so its line keeps the drawing's size (round 2's rule kept).
  - **No old prayer lit after a start.** The entries before each change of the rows or the next start (each start, each time of the day, each turn of the date) shorten towards it, 2, 4, 9 and 15 minutes before it and then every six minutes (`nextCut`), so that none but the last can still be shown when it comes (`noEntryButTheLastBeforeAChangeCanStillBeShownWhenItComes`). The last, two minutes long, carries what is drawn after the change (`TileEntry.after`: the rows, the line counting down to the next start, or the notice), and the layout draws both, each in a box shown only on its own side of the change by the platform's clock (`Modifiers.setVisible`, schema 1.3, still experimental in protolayout 1.4.2; `TileScreen.hides`). A hidden box draws nothing and is not read. On a renderer before schema 1.3 the entry draws what comes before alone, and its line says the start's time from the start on ("ASR 16:10"), for 115 s at most.
  - **The weight.** Six-minute entries and the doubled entry before each change weigh more: two hours of moving countdown made 216 KB (Urdu at Maghrib) and an hour and a half 199 KB (Tromsø at night in May, five changes within the hour). The countdown moves for **80 minutes** (`MOVING_SECONDS`), at most 168 KB in the Cape Town feeds and 178 KB at Tromsø over 48 hours of requests, and the Tile is fresh for **45 minutes** (`MAX_FRESH_MILLIS`). Past the moving part an entry says the start's time and carries nothing after it: if the system's request never comes, a start there can still show late, the old prayer lit beside its time, for up to three quarters of that entry again (an hour at most by `AlarmManager`'s cap). For the Lead, with plan 5's Galaxy pass.
  - **Tests** (red first: `TileLateChangeTest` failed on `67f7e06d`, "in 1 h 9 min" at 15:01:05 for a Tile asked for at 14:58): `TileLateChangeTest` plays the renderer's rule out at its latest and on time (`LateRenderer.changes`) for Tiles asked for at thirteen moments in four feeds, and reads what is shown (`Seen`, which honours `visible`) every five seconds and either side of every change through the moving part, against the rules: the line, its sentence, the rows and their states; and on a renderer before 1.3, the same with the start's time and the rows before it allowed for 115 s after a change. Mutants: without the hidden boxes it fails at 16:10:00 ("ASR 16:10" beside Dhuhr lit); with 60 s allowed below 1.3 it fails at 16:11:05.
  - **On emulator-5558** (debug `5946a20f`): asked for at 19:01:50, the renderer set its change for 19:04:00 with a window of +1m16.7s (to 19:05:16.7), and the Tile said "MAGHRIB in under a minute" with Asr lit until 19:03:55 and "ISHA in 1 h 19 min" with Maghrib lit from 19:04:00 (`b-190356.png`, `b-190402.png`), only that side in the accessibility tree; asked for at 19:09:00, the change set for 19:15:00 with +4m29.7s, and the countdown moved on at each minute through it.
  - Report: `docs/superpowers/reports/2026-10-30-wear-tile.md`, "Review fix round 3".
- **Review fix round 4 (31 October, Lane W), Ruling 65's items 2 and 3, which both reviews of `acda73e4..6251254a` found not built:**
  - **The static values are call 6's form.** A renderer that drops a dynamic value (over its quota, on an evaluation error, or in the frame before it binds it) keeps its static value, which was the entry's first countdown. It is now the start's time, "16:10", said "Asr, 16:10" (`TileNext.clock`, `clockSentence`), on both sides of the entry before a change, and it is among the words the countdown's room is fitted to. Every function that builds a dynamic value carries `@RequiresSchemaVersion` (1.200, and 1.300 for `shownWhile`), so lint's `ProtoLayoutMinSchema` holds every caller; the two gates on the request's own schema (`TileScreen.dynamic`, `.hides`) suppress it where they test it.
  - **Past its last link a chain says the start's time,** never its last countdown words: one link more once fewer minutes are left than the chain counts (or, after "in under a minute", from the start), so a renderer later than `shownUntil` still says something true. That is eight nodes a chain, which six-minute entries could not hold (204), so **an entry of the moving part is five minutes at most** (`STEP_SECONDS` 300; cuts 2 and 4 minutes before each change, then every five): 172 nodes at most every 53 s through two days in six feeds, and 193.2 KB on Tromsø's heaviest night, within 200 and 200 KB, in English on 24 hours alone (review fix round 5).
  - **On emulator-5558** (debug `10a87924`): the 14:58 shot pixel-identical to `en-1458.png`; under a forced doze the renderer's 15:01:00 change stayed pending to 15:05:26, and the line said "ASR 16:10" from 15:04:10, past its entry's `shownUntil` (15:03:40) and its chain, where the old chain said a passed "in 1 h 6 min".
  - Report: `docs/superpowers/reports/2026-10-30-wear-tile.md`, "Review fix round 4".
- **Review fix round 5 (31 October, Lane W), the one Important both reviews of `acda73e4..d5299f20` raised:**
  - **The timeline broke its own bound far north.** On Tromsø's nights in May, where five changes fall within the hour, the 80 minutes hold 25 moving entries, and in the phone's multi-byte languages, on 12 hours and with a long city the whole plan came to 242 KB at most (Bengali on 12 hours, the size tests' 300-byte city), against `MAX_TIMELINE_BYTES`' 200 KB; the test had weighed English on 24 hours alone. **The Tile that is sent now ends the moving part where the timeline fits** (`TileLayout.fitted`, which the service sends): it lays out and weighs each entry as the protobuf it is sent in, and while the whole is over the bound ends the moving part where its last entry began (`TilePlan.entries`' `movingUntil`), so the entries from there say the start's time (call 6). The entries before the end are the same, and each is laid out once. Where it fits, as through every Cape Town day in every language with the bundle's longest city, nothing changes. Far north the moving part is 54 minutes at the shortest (Bengali on 12 hours, the worst city), and a request is at most 199,999 bytes.
  - **Asked for again while it still moves.** `TilePlan.freshnessMillis` is at most four sevenths of a moving part that ends before the notice, so a request three quarters late still comes within it: 45 minutes for 80, as before; 30 min 53 s at the shortest far north; 45 with nothing moving.
  - **Tests:** `PhoneFeeds` writes a feed in the phone's seven languages on both clocks from the phone's own strings and forms (held to the phone's golden Urdu feed and the Saudi and English 12-hour samples); `theTimelineIsSmallEnoughToSend` weighs the Tile as sent through Tromsø's nights in all fourteen, with the worst city or the update, and holds the moving part to 45 minutes at the least; `theMovingPartEndsAtTheLatestOfItsEntriesAtWhichTheTimelineFits`, `theMovingPartIsSentWholeWhereverItFits`, `theWeightIsThatOfTheProtobufSent`, and two in `TilePlanTest`.
  - **On emulator-5558** (debug `bb47e298`): the 14:58 shot pixel-identical to `en-1458.png`; the sample with a 1,200-byte city, a 265 KB whole plan, sent as 195 KB with 10 entries counting down, said "ASR in 1 h 11 min" at 14:58 and, past its moving part with no new request, "ASR 16:10" at 15:46:30.
  - Report: `docs/superpowers/reports/2026-10-30-wear-tile.md`, "Review fix round 5".
- **Ruling 68 item 2 (31 October, Lane W), review round 6's minors:**
  - **(a) Never asked for more than once a minute.** `TilePlan.freshnessMillis` is 0 (the notice showing) or at least `MIN_FRESH_MILLIS`, a minute, the Tiles reference's floor: a moving part the weight ends within 105 s, or a notice within a minute, is asked for a minute on, and the line says the start's time in between (call 6). Test: `theTileIsNeverAskedForMoreThanOnceAMinute` (16:07:58 was 1,142 ms).
  - **(b) The city cut as a last resort.** Where even nothing moving is too heavy, which takes a city far longer than the phone's worst (300 bytes), `TileLayout.fitted` cuts the city to half its letters, again and again, with an ellipsis, until the timeline fits, and fits the moving part again for the cut city; where the update takes the city's place the city is never cut. `MAX_TIMELINE_BYTES` now holds for every feed the phone writes and whatever the city's length. Tests: `aCityNoPhoneWritesIsCutBeforeTheTimelinePassesItsBound` (the 6,000- and 12,000-byte cities; 6,000 made 308 KB at 14:58) and `assertSmallEnough`, which now also holds every sweep's city whole.
  - **(c) The tests' inputs.** `watchcore/vectors/phone/phone-capetown-en.json` and LongestCity's `cities.csv` and `city-names-*.csv` are declared inputs of every `Test` task (`taqwa.phoneCities`), seen stale first.
  - **(d) The binder call is one-way.** `TileCallback.updateTileData` transacts with `FLAG_ONEWAY` (javap of tiles 1.6.2), so the timeline lands in the renderer's async space, half its binder buffer of about a megabyte; the KDocs say so. `onTileResourcesRequest` is not deprecated in tiles 1.6.2 (only `onResourcesRequest`, `onTileEnterEvent` and `onTileLeaveEvent` are), against round 5's report.
  - Report: `docs/superpowers/reports/2026-10-31-wear-tile-ruling-68.md`.
- **Ruling 68 item 2, review fix round 1 (31 October, Lane W), the one Important:** the committed drivers put the watch back as found. `restore.sh` writes back every file `pullfound.sh` found in the app's `files/` and `shared_prefs/` and removes every other (the Tile's stamp `complications.asked` among them); `state.sh` names the app by its APK's sha1, not its install path, and counts its pending alarms alone, not the removal history. On emulator-5558, after a Tile turn with the debug build of `9e372c2a`, `restore.sh` printed `STATE AS FOUND`, and again after leftovers written by hand. Report: the same, "Review fix round 1".

### Task 9: The full pass, TalkBack throughout, and the phone's language over the watch's

**Files:**
- Create `docs/superpowers/reports/2026-10-24-wear-app-pass/`, with `README.md` indexing every shot and walk.
- Modify:
  - `tools/watchface/README.md`: the app's section;
  - the spec: 2.4–2.6, a short "Settled in plan 3" for each. They hold calls 4–7 and 13–16, the words in the feed, what TalkBack reads, and how a TalkBack user gets from one screen to the next. 2.6's note adds that a watch with no feed which has been sent a newer one says "Update Taqwa on your watch".

- [ ] **Step 1: The matrix,** with `app.sh` and `shoot`, at font scale 1.0. The watch's language is English, then Arabic, set in the watch's Settings as Task 8 Step 5 recorded in the README. The feeds are en24, en12, ar-ly12 and ar-sa:
  - Today at 14:58, 22:30 and 04:10, with its list;
  - Tasbeeh at 0, 27, 33 and 100, and the list;
  - Qibla Searching, Aligned, Low and BestEffort;
  - the Tile at 14:58 and 06:40.

  Each shot sits beside its `ref.py` drawing in the folder. No page shows a time, page dots or a scroll bar.
- [ ] **Step 2: The states,** with no paired phone running, the watch in English and then in Arabic:
  - **no feed** (`--ez clear true`): "Open Taqwa on your phone", in the watch's language, on every page and the Tile;
  - **no feed, a newer format seen** (then `--ei version 2`): "Update Taqwa on your watch", in the watch's language, on every page and the Tile. After `am force-stop world.taqwa.app` and a reopen, it is unchanged;
  - **spent:** Today and the Tile;
  - **newer, with a feed:** Today's time line and the Tile's city line say the update, and all three pages draw from the last feed;
  - a plain `DEBUG_FEED` at the end.
- [ ] **Step 3: TalkBack** (`talkback.sh`), on each page and the Tile, in en24, en12 and ar-sa:
  - the walk's lines are written down in `talkback-walks.txt`. On Today the ring's sentence comes first, with no time before it, and on en12 it says "PM";
  - `idle 10` on Today is 0;
  - on Tasbeeh, `tap2` is heard as a count. "Take one away" and "Choose a dhikr" are heard through `actions`, the second reaching the phrase list and its Reset row. Where Task 6 found that the console cannot drive TalkBack's actions menu, the report names plan 5's device pass for them and does not claim them;
  - **from one page to the next, with TalkBack alone:** one walk from Today's ring goes on through the rows and the city to Tasbeeh, and from the counter on to Qibla. The pane titles are heard at each change. Write down where the walk goes, as heard. A change the walk does not make is reached only by the two-finger swipe, which the harness cannot drive, so the report names it for plan 5's Galaxy pass;
  - Qibla's turn and "Facing qibla" are heard.

  The drawn screens are the same with TalkBack off as in Step 1.
- [ ] **Step 4: Large text** (call 15):
  - In the watch's Settings, choose the largest font size. Read `adb -s emulator-5558 shell settings get system font_scale`, and record it in the README. If it is below 1.3, run this step a second time at `settings put system font_scale 1.3`, because a Galaxy's largest may be larger.
  - Shoot, in en24, en12 and ar-sa, with `LongestCity`'s name through `--es city`:
    - Today's ring at 14:58 and its list;
    - Tasbeeh at 0 (with the hint) and 27, his 36-character phrase, and the phrase list;
    - Qibla Searching, Aligned and Low(CALIBRATION);
    - the Tile at 14:58;
    - the no-feed line.
  - Expected:
    - no line overlaps another, the ring or the dial's ticks, and none runs past the round edge;
    - a line that is cut ends in an ellipsis;
    - Today's countdown and Tasbeeh's count are the same size as at 1.0: their `uiautomator dump` bounds, or their glyphs' heights in the shots, equal the 1.0 shots' within 1 px. Every other line is larger, up to its cap;
    - the phrase list and Today's list scroll, and the Tile keeps its pitches.
  - The remedies:
    - A line capped at 1.0 that is not its 1.0 size is a conversion fault, not a cap. Fix it in Task 4's `WatchUnits.sp` and its `WatchUnitsTest`, and look for a size that skips `WatchUnits.sp`.
    - Any other line that fails gets a lower cap in its type table and its model test.
    - Record either kind in the ledger. The shots sit in the folder's `large-text/`.
  - `settings put system font_scale 1.0` at the end.
- [ ] **Step 5: The checks:**
  - `scripts/test.sh` and `scripts/ios-build.sh`;
  - `./gradlew :wearApp:assembleRelease :wearApp:lint` and the merged-manifest permissions;
  - the face's validators (`tools/watchface/validate.sh`) and its pass at 14:58 on Arch, both unchanged;
  - `apkanalyzer dex packages` on the release finds no tracker package.
- [ ] **Step 6: Commit** with the message "Wear: the watch app's emulator pass, every screen and the Tile in English and Arabic on both clocks and at the largest text, heard with TalkBack".

**As built (31 October, Lane W):** the report is `docs/superpowers/reports/2026-10-24-wear-app-pass/README.md`.
- **Erratum p3 #4:** the tracker scan is plan 2 Task 5 Step 1's pattern and `comm -13` against the list recorded at P2.6, before plan 3: empty for the release (`com.google.firebase`, play-services-basement's three exceptions, is on that list) and for the debug APK.
- **The watch's own largest font size is 1.24** on emulator-5558 (Settings › Accessibility › Text size › Font size), so Step 4 ran at 1.0, 1.24 and 1.3.
- **Found in Step 4 and fixed test-first (its own commit):** at 1.3, Today's list scrolled to its end stood 45 units higher than the drawing, and its first row's time ran off the round screen. Today's list lines (`TodayType.rowName`, `rowTime`, `sunrise`, `city`) were capped at `TodayType.LIST_CAP`, 1.24, Wear OS's own largest.
- **Review round 1, by the same remedy:** at 1.24 a 12-hour column, moved out for "Maghrib 7:04 PM", still ran Fajr's "4:33 AM" to the round edge at the list's end, and the guard test had checked only the 24-hour column. The cap is now 1.12, Wear OS's step below 1.18: the largest of its sizes at which every row, at the list's end, keeps the widest column the times may take (`TodayLayout.maxEnds`, Isha's edge in the drawing, 167.3 units) inside the frame's inset (the bound is about 1.153). 1.15 also kept the times inside, but cut a 12-hour Friday's "Jumuʿah". Tests: `atItsEndTheListKeepsEveryRowsTimeOnTheRoundScreenAtEveryFontSize` (every row against the widest column), `theCapIsTheLargestWearOsSizeTheRoundScreenAllows`, `aboveItsCapTheListIsAsItIsThere`. On emulator-5558, 1.18, 1.24 and 1.3 are each the 1.12 list to the pixel, and nothing changes up to 1.12.
- **Ruling 58 item 5:** the watch's half (the phone's Urdu and Libyan goldens on the three pages) is seen; the phone's half, `AppContainer.watchFeedFor` in the phone's own resource environment, needs a phone running the app beside the watch, so it moves to plan 2 Task 7's paired pass.
- **Getting from one page to the next with TalkBack:** a one-finger walk never leaves its page; the two-finger swipe is plan 5's Galaxy pass. A swipe sent by `adb shell input swipe` with TalkBack on reaches the app as a sighted wearer's would, and each page's title is said as it comes.
- **The Qibla rows** were seen on P3.7 as landed; Ruling 66 (Lane P's R66) replaces the detail and carries its own device pass, and the matrix's Qibla rows are shot again once it lands.

### Task 10: After plan 2's builder: the phone fills the screens' words

**Starts only when** plan 2's Tasks 1, 3, 5 and 8 are on the branch, after this plan's Task 3:
- Tasks 1 and 3 give `WatchWords`, `watchUiLanguage`, `WatchFeedInputs` and `WatchFeedBuilder`;
- Task 5 gives `aFeedAtTheBudgetIsWithinTheMessageLimit` (`AndroidWatchLinkTest`), and Task 8 gives `theLargestContextFitsTheSmallestLimit` (`IosWatchLinkTest`). Step 1 runs both again with the words in, so neither may land after this task with inputs that lack them.

If plan 2's Task 6 is on the branch too, Step 4 also wires the open handshake.

**Files:**
- Create:
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchScreenWordsReader.kt`;
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/WatchScreenWordsReaderTest.kt`;
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/BundledScreenStrings.kt` (test helper, beside plan 2's `BundledSpoken`);
  - `shared/src/iosTest/kotlin/world/taqwa/app/watchfeed/WatchScreenWordsResourceTest.kt`.
- Modify:
  - `shared/src/androidHostTest/kotlin/world/taqwa/app/watchfeed/BundledSpoken.kt`: its CLDR item rule moves to a function both helpers call, `fun cldrItem(language: UiLanguage, n: Int): String`, unchanged;
  - `shared/.../watchfeed/{WatchFeedBuilder,WatchWords}.kt`;
  - `WatchFeedInputs`, gaining `screens: WatchScreenWords` with no default, so the compiler finds every construction. Its caller in plan 2's sync resolves the words beside `watchUiLanguage()`;
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WorstFeeds.kt`: `val SCREENS: WatchScreenWords`;
  - the builder's tests, and the size tests' inputs in `WatchFeedSampleTest`, `AndroidWatchLinkTest` and `IosWatchLinkTest` (Step 1);
  - `Fixtures.kt` and the vectors and goldens, regenerated;
  - plan 2's table under Ruling 39: one row, "the screens' words: the app's own text".
  - With plan 2's Task 6 present: `wearApp/.../TaqwaWearApp.kt`.

**Interfaces:**
- **The seam**, as plan 2 built `SpokenStrings` for the same reason: `getString` reads the ambient resource environment, which a `language` argument cannot change, so a host test cannot ask it for seven languages.
  - `internal interface ScreenStrings { suspend fun text(key: String): String; suspend fun counted(key: String, n: Int): String }`, keyed by the phone's resource name (`"tasbeeh_round"`).
  - `internal object ResourceScreenStrings : ScreenStrings` holds an explicit `Map<String, StringResource>` and `Map<String, PluralStringResource>` of the table's keys. It reads `getString(res, "%1$s", "%2$s")`, so each pattern comes back with its places, and `getPluralString(res, n, "%1$s")`. A key not in its maps throws.
  - `BundledScreenStrings.of(language: UiLanguage): ScreenStrings` is the host's stand-in. It reads `src/commonMain/composeResources/values<suffix>/strings.xml` from the module directory, as `WatchWordsResourcesTest` does, and falls back to `values/` for a key the folder lacks, as Compose does. It picks a plural's item by `cldrItem`.
  - The real resource path is tested on the iOS simulator, in the English environment the other iOS tests run in, as plan 2's `WatchSpokenResourceTest` is.
- Produces `internal suspend fun watchScreenWords(language: UiLanguage, strings: ScreenStrings = ResourceScreenStrings): WatchScreenWords`. `language` is `watchUiLanguage()`'s answer, the app's `ui_language`. The default `strings` reads in the resource environment that answer comes from, never by `UiLanguage.of(tag)` or the device's locale, and `language` picks the `WatchWords` phrases. Each field is read as its pattern, the way plan 2 reads `prayer_a11y_countdown`:

  | Field | From |
  |---|---|
  | `today`, `tasbeeh`, `qibla` | `qibla_back`, `tasbeeh_title`, `qibla_title` |
  | `rowCurrent`, `rowPassed`, `rowNext` | `prayer_a11y_prayer_current`, `_passed`, `_next` |
  | `round`, `of`, `reset`, `resetConfirm` | `tasbeeh_round`, `tasbeeh_of`, `tasbeeh_reset`, `tasbeeh_reset_confirm` |
  | `presets` | `tasbeeh_preset_<id>` for each built-in |
  | `facingQibla`, `distance`, `bearing`, `qiblaDetail` | `qibla_facing_qibla`, `qibla_distance`, `qibla_bearing`, `prayer_qibla_detail` |
  | `cardinals` | `qibla_cardinal_north`, `_east`, `_south`, `_west` |
  | `needsCalibrating`, `interference`, `bestEffort`, `noSensor` | `qibla_needs_calibrating`, `qibla_interference_title`, `qibla_best_effort_title`, `qibla_no_sensor_title` |
  | the six tasbeeh spoken phrases and `turnRight`/`turnLeft` | round 2's keys (`tasbeeh_a11y_*`, and the plurals `qibla_a11y_turn_*` asked at 15, 30, … 180 and merged into `WatchCount` runs) once round 2 is on the branch; until then `WatchWords`, with round 2's own table's values |
  | `bezelHint`, `holdLevel`, `undoAction`, `phrasesAction`, `openOnPhone`, `updateOnWatch` | `WatchWords`: new phrases, seven languages, drafts; the last two equal `:wearApp`'s own strings |
- Produces in `WatchFeedBuilder`:
  - `ui.screens` = `inputs.screens`;
  - `ui.latinScript` = `language.latinScript`;
  - `ui.ringDigitZero` = `'0'` when `CountdownDigits.westernFallback(format.languageTag())` holds, else the feed's zero;
  - on a 12-hour phone, each day's `clockTime` = `format.clockTime(h, m)` for all six times, Sunrise's included, run through the feed's digit rule (`WatchDigits.write`), bare: the watch isolates it where it splices it (Task 3's `spokenClock`).
- Produces in commonTest `WorstFeeds.SCREENS: WatchScreenWords`, the words a worst-case feed carries where no language's real words can be read: on the iOS simulator, whose resources are English. Each string field, each `presets` value, each cardinal and each `WatchCount` phrase is "ঔ" (U+0994, three bytes in UTF-8) repeated twice the length of ENGLISH's own.

- [ ] **Step 1: Write the failing tests:**
  - `WatchScreenWordsReaderTest` (host), each word read as `watchScreenWords(language, BundledScreenStrings.of(language))`:
    - `everyLanguageHasEveryWord`: for each `UiLanguage`:
      - no field is blank, and `cardinals` has 4 entries;
      - each pattern holds the same set of `%n$s` places as ENGLISH's same field. Only the set is checked, never the order, because a language may rightly put `%2$s` first: round 2's table writes `tasbeeh_a11y_count` as "%2$s-এর মধ্যে %1$s" in Bengali and "%2$s میں سے %1$s" in Urdu. The order is checked for English alone, in Task 3's `everyEnglishPatternHasItsPlaces`;
    - `aPlaceMayComeFirst`: `WatchRules.filled(bengali.countSpoken, "১৩", "১০০")` is "১০০-এর মধ্যে ১৩", and `filled(urdu.countSpoken, "13", "100")` is "100 میں سے 13".
    - `theWordsAreThePhonesOwn`: Arabic's `round` is `values-ar`'s `tasbeeh_round`, and so on, for one field of each source;
    - `theWatchAndThePhoneSayTheNoticesAlike`: `openOnPhone` and `updateOnWatch` equal `wearApp/src/main/res/values-<x>/strings.xml`'s, for each language;
    - `bundledEnglishIsTheEnglishDefault`: for ENGLISH it equals `WatchScreenWords.ENGLISH`, which ties the host's helper to the simulator's test below;
    - `aReadThatThrowsIsNotGuessed`: a fake `ScreenStrings` whose `text` throws makes `watchScreenWords` throw it.
  - `WatchScreenWordsResourceTest.theDefaultReadsThePhonesOwnStrings` (iOS simulator): `watchScreenWords(ENGLISH)`, with the default `ResourceScreenStrings`, equals `WatchScreenWords.ENGLISH`.
  - The builder:
    - `aTwelveHourPhoneSendsItsOwnClockTimes`;
    - `aTwentyFourHourPhoneSendsNone`;
    - `aSaudiPhonesRingIsWestern`;
    - `aBengaliPhoneHidesTheTransliteration`.
  - Plan 2's five size tests, run again with the words in. Each takes them from its `WatchFeedInputs.screens`:
    - On the host, `theTimesAloneLeaveRoomForThePhrases`, `theLargestFeedStaysWithinItsBudget` and `phrasesGoWhileTheyFit` (`WatchFeedSampleTest`), and `aFeedAtTheBudgetIsWithinTheMessageLimit` (`AndroidWatchLinkTest`), pass each feed's own language's words: `watchScreenWords(language, BundledScreenStrings.of(language))`, beside `BundledSpoken.of(language)`. Over `PhoneTags.TAGS` that is all seven languages.
    - On the iOS simulator, `theLargestContextFitsTheSmallestLimit` (`IosWatchLinkTest`) passes `WorstFeeds.SCREENS`.
    - `theWorstWordsOutweighEveryLanguage` (host, in `WatchScreenWordsReaderTest`): the UTF-8 size of `Json.encodeToString(WatchScreenWords.serializer(), WorstFeeds.SCREENS)` is at least that of the same encoding of every language's `watchScreenWords(language, BundledScreenStrings.of(language))`. So the simulator's case stays the worst when a translation grows, as `theWorstCityIsLongerThanAnyTheBundleNames` keeps the city's.

    Record the largest feed beside the budget, with its language.
- [ ] **Step 2:** Run `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`. Expected: FAIL.
- [ ] **Step 3:** Implement the code. Then change `Fixtures` wherever the phone writes otherwise (the fixture moves, never the builder), and regenerate with `-Ptaqwa.updateVectors`.
- [ ] **Step 4:** With plan 2's Task 6 present, `TaqwaWearApp.onAppOpened` also runs `PhoneAsk(this, feedStore).askIfDue(AskWhy.OPENED, now)`, on the app's scope, within 10 s. When Task 4 landed after plan 2's Task 6 (the execution map's order), Task 4 already moved it there, and this step only checks it.
- [ ] **Step 5:** Run `scripts/test.sh` and `scripts/ios-build.sh`. Expected: PASS, and the framework header names no `WatchFeed`, `WatchUi` or `WatchScreenWords`.
- [ ] **Step 6: Commit** with the message "Watch feed: the phone sends the words of the watch's screens in its own language, its 12-hour times as it writes them and the digits of its ring".

### Task 11: The face says a 12-hour time with its day period

**Starts after Task 3** (it needs `WatchDay.clockTime`, `WatchTime` and `WatchRules.spokenClock`), and after plan 1's close. It is the one reviewed exception to plan 1's freeze (call 12): only the face's spoken text changes. It takes plan 2's hand-off, "The 12-hour spoken time's day period … and `ComplicationFields` speaks it", which closes plan 1 Task 12's minor ("at 4:10" says no AM or PM).

**Files:**
- Modify:
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/ComplicationFields.kt`: `NextPrayerFields.spokenTime`, `prayerSpoken` and `dialSpoken`;
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/NextPrayerSource.kt`: `nextPrayerSaid`.
- Test, in `wearApp/src/test/kotlin/world/taqwa/app/wear/complication/`: `ComplicationFieldsTest.kt` (add) and `SpokenTest.kt` (the three changes named in Step 1).

**Interfaces:**
- Consumes Task 3's `WatchRules.spokenClock`, `WatchTime` with `WatchSix.of(time)`, and the samples' `clockTime`.
- Produces on `NextPrayerFields` a last, defaulted field, `val spokenTime: String = time`. `nextPrayer` fills it with `WatchRules.spokenClock(day, WatchTime.of(prayer))`. `time`, the drawn one, stays `day.clock.of(prayer)`, bare, as plan 2 rules.
- `nextPrayerSaid` passes `fields.spokenTime` to `WatchRules.spokenCountdown` and to `CountdownExpression.spoken`, and `prayerSpoken` says `spokenTime`.
- `dialSpoken` says each of the six times over `WatchTime.entries`: `"${day.names.of(time)} ${WatchRules.spokenClock(day, time)}"`, Sunrise's included (since 9 November 2026 only while the phone's Show sunrise is on), joined as today by `spokenSeparator`. Its six `names.x to clock.x` pairs go.
- Nothing else in `complication/` changes: no slot, box, order, drawn text or timeline (Ruling 42).
- The names above are those on the branch on 24 October, while plan 1's Task 12 is still being built. If Task 12 has moved a spoken line, the rule holds for whatever is there at plan 1's close: every spoken line that splices the next prayer's time or the day's times says `spokenClock`. Afterwards, a grep of `complication/` finds no `day.clock` or `fields.time` passed to a spoken phrase.

- [ ] **Step 1: Write the failing tests,** with FSI and PDI the test's constants for U+2068 and U+2069:
  - `ComplicationFieldsTest`:
    - `aTwelveHourPhoneSaysItsDayPeriod`: on `capeTown(ui = english12())` at 14:58:00 on day 0, `nextPrayer(…).time` is "4:10" (drawn, unchanged) and `spokenTime` is FSI + "4:10 PM" + PDI. `nextPrayerSaid(…)`'s words are "Asr in 1 hour 12 minutes, at " + FSI + "4:10 PM" + PDI.
    - `theDialSaysTwelveHourTimesWithTheirDayPeriod`: on the same feed at 14:58 on day 0, `dialSpoken` is "Fajr " + iso("4:33 AM") + ", Sunrise " + iso("6:01 AM") + ", Dhuhr " + iso("12:33 PM") + ", Asr " + iso("4:10 PM") + ", Maghrib " + iso("7:04 PM") + ", Isha " + iso("8:24 PM"), where `iso(t)` is FSI + t + PDI. Sunrise carries its day period as the five prayers do.
    - `aTwentyFourHourFeedIsSaidAsBefore`: on `capeTown()`, `spokenTime` equals `time`, and `dialSpoken` and `nextPrayerSaid`'s words are byte for byte what the file's other tests already expect.
  - **The three existing tests that expect a bare 12-hour time change.** Task 3 gives the en12 and ar-ly12 samples `clockTime`, and this task says it, so each now expects the isolated time with its day period:
    - `SpokenTest.onTwelveHoursTheTimeIsThePhones` (SpokenTest.kt:122): `prayerSays(english12, at(0, 14, 58))` is "Asr in 1 hour 12 minutes, at " + FSI + "4:10 PM" + PDI;
    - `SpokenTest.inArabicThePhonesWordsAndDigitsAndNoLetterAlone` (:129): `prayerSays(libya, at(0, 14, 58))` is "العصر بعد 1 ساعة و12 دقيقة، الساعة " + FSI + "4:10 م" + PDI. Its Saudi lines, which are on 24 hours, and its no-lone-letter loop stay as they are;
    - `SpokenTest.theLineSaysWhatTheRulesSayAtEveryStepOfTwoDays` (:79), which walks en12 and ar-ly12 already: its expected sentence passes `fields.spokenTime` to `WatchRules.spokenCountdown` in place of `fields.time`. The dynamic sentence then equals the rules' at every step, on both clocks.

    No other existing assertion changes. Every other assertion in `complication/`'s tests that says a time is on a 24-hour feed, and `datesSay(libya, …)` (:172) says no time.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3:** Implement the code.
- [ ] **Step 4: Run the checks:**
  - `./gradlew :wearApp:testDebugUnitTest`. Expected: PASS. Every 24-hour assertion is byte for byte as before, and `git diff` of the test files shows only Step 1's new tests and its three named changes.
  - `tools/watchface/validate.sh`, and the face's pass at 14:58 on Arch. Expected: both unchanged, because nothing drawn changes.
  - On emulator-5558, with the `en12` sample: `talkback.sh on`, then `walk` on Arch. Expected: wherever the walk reads the next prayer's sentence, it ends in "4:10 PM". The report quotes the line, and `talkback.sh off` follows.
- **As built (31 October):**
  - Erratum p3 #6: Task 3 wrote `clockTime` as `TwelveHourClock.render` does, with a NO-BREAK SPACE (U+00A0) before the day period. So every "4:10 PM", "6:01 AM" and "4:10 م" in Step 1 is held with the tests' `NBSP` constant: FSI + "4:10" + NBSP + "PM" + PDI.
  - The samples are `TestFeeds.other("en12")` and `other("ar-ly12")`: `capeTown(ui = english12())` is `:watchcore`'s fixture, and `:wearApp`'s tests read the committed samples it writes.
  - `aTwelveHourPhoneSaysItsDayPeriod` also holds `prayerSpoken` (the short text's description) and the screen's words of a feed written before the spoken ones. `aTwentyFourHourFeedIsSaidAsBefore` also holds the Saudi digits at seven moments, and a twelve-hour feed without `clockTime`.
  - The remaining `day.clock` and `fields.time` in `complication/` are all drawn (the next prayer's `time`, the ring's and the short text's text) or compared (`answerChangesAt`). None is passed to a spoken phrase.
- [ ] **Step 5:** Add a ledger line under "Plan 3": plan 2's hand-off on the day period is closed by this commit.
- [ ] **Step 6: Commit** with the message "Face: a twelve-hour phone's times are said with their day period, as the Prayer screen says them, and nothing drawn changes".

## What plans 4 and 5 take from this plan

**Plan 4 (the Apple Watch):**
- The Swift port runs the same rules: `TasbeehEngine.undo`, `WatchQibla`, `QiblaGuidanceTracker`, `WatchRules.clockText` and `spokenClock` keyed by `WatchTime` (six values, Sunrise among them, with `WatchTime.of(prayer)`), and `WatchScreenWords.orEnglish`. Vectors to add in plan 4: the undo cases, `WatchQiblaTest`'s runs, round 2's guidance sequence, and Task 3's clock cases. The clock vectors cover all six times, Sunrise's "6:01 AM" (FSI…PDI when spoken) included, not the five prayers alone.
- Its Tasbeeh counter offers the same two actions to VoiceOver, `undoAction` and `phrasesAction` ("Choose a dhikr"), in `TasbeehSpeech.actions`' order.
- Its decoder reads the samples and goldens as Task 3 and Task 10 regenerated them.
- The crown counts and undoes as the bezel does, with Task 6's haptic rule: one haptic for each turn, the strongest it crossed, and no tick while a part's or a set's buzz plays.
- Its words, the crown hint and the Smart Stack's, join `WatchScreenWords` the way Task 10 adds them.
- **The states without a usable feed, as call 5 orders them:** with no feed and a newer format seen, every screen shows "Update Taqwa on your watch", not "Open Taqwa on your phone". Plan 4's Task 7 (its `MessageView` with no feed) aligns to this.
- Every spoken time says `spokenClock`, so a 12-hour iPhone's day period is said.

**Plan 5 (ship), the device pass on his Galaxy Watch 8 Classic:**
- the bezel's sign and detent;
- the haptics' feel, and that a fast spin across 33 gives the part's buzz whole on Samsung's vibrator;
- question 3 (the screen during Tasbeeh), and question 6 (the time and the page dots);
- the compass, from `TaqwaWearQibla`'s log: the field beside a trusted rotation vector, the accuracy and B6's Samsung behaviour, the right wrist, and the tilt threshold;
- the Tile on Samsung's renderer: dynamic text, right to left, the system font;
- TalkBack and Samsung's screen reader on the three screens and the Tile (Ruling 38), including the counter's two actions ("Take one away", "Choose a dhikr") when Task 6 found that the console cannot drive TalkBack's actions menu;
- getting from one screen to the next with the screen reader alone: the two-finger swipe from page to page and from the counter to the phrase list and back, which the harness cannot drive, and the walk past each page's last item that Tasks 5 and 9 recorded;
- large text at the Galaxy's own largest font size, and in Bengali and Turkish feeds once the phone sends them: the countdown and the count at their 1.0 size, and the Tile's lines on Samsung's renderer;
- the Tile on a watch whose own language is Arabic: Samsung's renderer mirrors a row as Task 8 Step 5 found the emulator's does, or not;
- the watch app's strings and the new phrases, through native review, with the bezel hint fitting one line in every language;
- the "Add the Taqwa face" row on Today (Watch Face Push);
- a Wear OS 4 emulator, for the Tile's renderer below schema 1.2;
- the Tile's preview image, refreshed with the final look.
