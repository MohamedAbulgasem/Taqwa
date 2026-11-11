# The Apple Watch pass: the evidence

Plan 4's simulator evidence, task by task. Task 11 completes this index.

## Task 6: the complications and the Smart Stack (30 October 2026, Lane A)

### Step 5: the gallery on the SE 3 (40mm), watchOS 27.0

Taken on `FCA316A7-A49B-4795-9610-7D61FD958A8A` under the `sim-se3` lock, with `xcrun simctl io <udid> screenshot`, from the Debug watch app built at the task's head. Fix round 1 re-shot every set (20:04–20:09) from its own build. It replaced the 14:58 parts 1 and 2, which changed, and added the 13:00 part 1 shots. The other shots it took again were the same as these below the status bar, so they are kept. Each shot is a launch with `-taqwaPage gallery -taqwaFrozen 1`, plus the arguments below.

The gallery draws the widget's own views (`WatchShared/NextPrayerViews.swift`) at the SE 3's accessory sizes: circular 42 × 42, corner 32 × 32 and rectangular 150 × 47 points. The SE 3's screen holds a third of the gallery at a time, so `-taqwaGalleryPart` picks the part, in the plan's order:
- **part 1:** each family. Top row: the circular, then the corner. A face draws the corner's line along its bezel; here it sits under the corner. Then the inline line, in the system font at `faces.js`'s 11 pt. Then the rectangular.
- **part 2:** the Smart Stack card: the rectangular under its header, on `#1A1D1C`, 20-point corners.
- **part 3:** each family without its countdown. That is the form the hour's last entry and the late entries draw (the Lead's call 5, erratum p4 #10).

The status bar's clock is the simulator's real time. The gallery's instant is the scripted one.

| Shots (parts 1, 2, 3) | Arguments | What they show |
|---|---|---|
| `gallery-capetown-1458-part{1,2,3}.png` | `-taqwaSampleFeed capetown -taqwaAt 14:58` | "ASR", "16:10", "in 1 h 11 min", the ring and the bar at 145/217, Manrope, tracked capitals. The gallery draws the timeline's first entry, which from 14:58:00 is the minute that holds (fix round 1): 72 minutes hold at that instant alone |
| `gallery-capetown-1300-part1.png` | `-taqwaSampleFeed capetown -taqwaAt 13:00` | both rings at 27/217, clockwise from 12 o'clock: the left-to-right control for the ring measurements below |
| `gallery-en12-1458-part{1,2,3}.png` | `-taqwaSampleFeed en12 -taqwaAt 14:58` | the bare "4:10" (the complications draw the day's `clock`; the day period is spoken only) |
| `gallery-ar-ly12-1458-part{1,2,3}.png` | `-taqwaSampleFeed ar-ly12 -taqwaAt 14:58` | العصر, "4:10", "بعد 1 س 11 د" in Western digits, right to left: both rings fill anticlockwise from 12 o'clock (measured below) and the bar fills from the right; the system font, no tracking; the header تقوى |
| `gallery-ar-ly12-1300-part1.png` | `-taqwaSampleFeed ar-ly12 -taqwaAt 13:00` | both rings at 27/217, anticlockwise from 12 o'clock |
| `gallery-ar-sa-1458-part{1,2,3}.png` | `-taqwaSampleFeed ar-sa -taqwaAt 14:58` | "١٦:١٠" and "بعد ١ س ١١ د" in Arabic-Indic digits, right to left |
| `gallery-ar-sa-1300-part1.png` | `-taqwaSampleFeed ar-sa -taqwaAt 13:00` | both rings at 27/217, anticlockwise from 12 o'clock |
| `gallery-capetown-160930-part{1,2,3}.png` | `-taqwaSampleFeed capetown -taqwaAt 16:09:30` | "in under a minute", the ring and the bar all but full |
| `gallery-nofeed-part{1,2,3}.png` | `-taqwaNoFeed 1` | the empty form: the arch alone (circular, corner), "Taqwa" (inline), "Taqwa" above "Open Taqwa on your phone" (rectangular, card), in the extension's own words |
| `gallery-nofeed-newer-part{1,2,3}.png` | `-taqwaNoFeed 1 -taqwaNewerSeen 1` | the same with "Update Taqwa on your watch" (plan 3's call 5) |

Against `faces.js` (`aCircular`, `aAnalog`'s corner, `aRect`, `aUtility` and `aStack`), every family has the drawing's words, digits and direction. Nothing is clipped, and the bar fills in reading order. Where the build differs from the drawing:
- The time in the rectangular is Manrope SemiBold: the drawing's weight 700 has no bundled face (Light, SemiBold and ExtraBold are bundled).
- Outside Latin letters the prayer's label is 13/11.5 of the Latin size, the ratio `faces.js` gives the Galaxy's label.
- The circular's track is the system gauge's own track, tinted amber at low opacity, not the hairline `#232825`. The corner's ring is the drawing's own (`RingGauge`: a hairline track and an amber arc).

#### Fix round 1: the rings' direction, measured

Review round 1 found that the first shots' right-to-left corner ring started at 6 o'clock. SwiftUI mirrors a rotation under a right-to-left layout, and that and `RingGauge`'s explicit flip together misplaced the arc. `RingGauge` now draws its track and arc under a fixed left-to-right environment, and turns the arc by its flip alone.

Each ring was read every 10° clockwise from 12 o'clock, along the stroke (the corner at radius 26–30 px, the circular at 36–40 px). The table gives the amber run, in degrees clockwise from 12. At 27/217 a right fill covers 45°, and at 145/217 it covers 240°:

| Shot | Corner (`RingGauge`) | Circular (the system gauge) |
|---|---|---|
| capetown 13:00 | 0–50 | 0–50 |
| capetown 14:58 | 0–240 | 0–240 |
| ar-sa 13:00 | 310–0 | 310–0 |
| ar-sa 14:58 | 120–0 | 120–0 |
| ar-ly12 13:00 | 310–0 | 310–0 |
| ar-ly12 14:58 | 120–0 | 120–0 |
| ar-sa 14:58, as first committed (30fbd47b) | 300–180 (wrong: from 6 o'clock) | 120–0 |
| ar-ly12 14:58, as first committed (30fbd47b) | 300–180 (wrong: from 6 o'clock) | 120–0 |

Each run includes the round cap's 10°. Every right-to-left reading mirrors its left-to-right one, and the corner now agrees with the system's gauge in each shot.

The same was first seen off-device. A probe draws the old and the fixed `RingGauge` with `ImageRenderer` on the Mac, at 27/217, 145/217 and 1/2, in both directions. The old code was wrong in all three right-to-left cases, and the fixed code was right in all six.

### Step 6: Taqwa on Apple's faces and in the Smart Stack, on the Series 12 (46mm)

Run on 30 October from 23:20 to 23:40 SAST, under the `ios-pair` lock (taken 23:20, released 00:07). The iOS Simulator control tool drove the watch, with Mohamed's grant of access, and the shots are `xcrun simctl io <udid> screenshot`. The apps are the Debug build of branch `p46-faces` at `44cfbd8d` (`watch-companion`, with P4.6 and P4.7 landed), from `scripts/ios-build.sh` and `scripts/watch-build.sh`. The afternoon's attempts (19:21, 19:48 and 20:10) had no answer to the access request, so nothing could be placed then. At about 23:03, for Ruling 64 items 7 and 8, Lane A took the lock and found the Series 12 already booted, with the desktop app's simulator panel attached and no lock held. No lane had booted it, so Lane A left it exactly as found and made no request (Ruling 64 item 8).

Found: no pair. The iPhone 17 Pro was shut down, with 40 apps, Taqwa 1.2.0 (38) among them, which has no watch app inside it. The Series 12 was booted, with the desktop app's simulator panel attached (its log stream had run since 21:50), 15 Apple apps, no Taqwa app or group container, `en-US` and `en_US@rg=zazzzz`. Its face library held Numerals Duo alone, and its Smart Stack one pinned widget (two placeholder icons and Messages).

What ran:
1. The two were paired (`09760D5E-DDA9-4811-AEFA-529536AB6D71`), and `Taqwa.app/Watch/TaqwaWatch.app` was installed on the Series 12 and launched with `-taqwaSampleFeed capetown`.
2. Taqwa was in neither the face editor's complication list nor the Smart Stack's widget list, though chronod held its descriptor (`TaqwaNextPrayer`, "Next prayer", five families) and NanoTimeKit its complication descriptor.
3. Booting the iPhone connected the pair, and the watch's `appconduitd` then **uninstalled the watch app**: `_AppCounterpartIsInstalledOnPhoneForWatchBundleID: Failed to verify whether companion counterpart for world.taqwa.ios.watchkitapp is installed`, then `Uninstalling world.taqwa.ios.watchkitapp`. The phone's Taqwa was 1.2.0 (38), with no watch counterpart.
4. So the 1.2.0 (38) bundle was copied aside, and the debug `Taqwa.app` (with `Watch/TaqwaWatch.app`) went on the iPhone. The watch app was installed again and launched with the sample. Taqwa then appeared in both lists.

**For P4.10 Step 5, P4.11 and plan 5's simulator checks:** a dependent watch app's complications are offered only while the paired phone holds its companion with that watch counterpart. A watch app installed alone on a paired watch is never listed, and booting the phone removes it. The Global Constraints' "installs the watch app as well as the phone's" is therefore required, not optional.

Placed and shot at the simulator's real clock with the `capetown` sample (the next start is Fajr, 04:33):

| Shot | Face and slot | What it shows |
|---|---|---|
| `series12-picker-rectangular-capetown.png` | Modular's complication picker, Taqwa's entry | "FAJR 4:33", "in 4 h 58 min" and the bar, under the widget's name "Next prayer" |
| `series12-modular-capetown.png` (23:35:32) | Modular, Multicolor: the middle rectangular and the bottom-left circular | "FAJR 4:33", "in 4 h 57 min" (4 h 57 min 28 s left) and the bar in reading order; the circular "FAJR / 4:33" in its amber gauge |
| `series12-infograph-corner-capetown.png` (23:37:08) | Infograph, an analog face with corners: the top-left corner | the ring with the arch, and "FAJR 4:33" along the bezel |
| `series12-utility-inline-capetown.png` (23:39:21) | Utility: the bottom slot, the inline family | "FAJR IN 4 H 53 MIN" (4 h 53 min 39 s left). The face sets inline text in capitals in its own grey; the inline's text is "FAJR in 4 h 53 min" |
| `series12-smart-stack-capetown.png` (23:40:13) | the Smart Stack, with the widget added by hand | the card: the arch and "Taqwa" in mist above "FAJR 4:33", "in 4 h 52 min" and the bar |

Every countdown is the one that holds for its second. The words, the digits, the direction and the colours are those of the SE 3 gallery (Step 5), and nothing is clipped at the Series 12's sizes. The extension logged each family's box: the Modular rectangular 196 × 80, the Smart Stack card 194 × 80, the circular 51 × 51, the corner 34 × 34 and the inline 13 × 13 points. Each `getTimeline` gave 72 entries in 1 to 3 ms, with `reloadAt` an hour on, since no start fell inside the hour (for example `TaqwaWatch timeline 72 1 reloadAt 1793399678 accessoryRectangular 196x80` at 23:34:38).

Not seen here: whether the Smart Stack lifts Taqwa by itself in the half hour before a start (question 2's relevance). The card was added by hand.

### Step 7: the timeline with the extension idle, on the Series 12's Modular face

The Modular face from Step 6, with Taqwa's rectangular and circular, was the active face throughout. The run:
1. At 23:42:04 the app was launched with `-taqwaStartIn 12`: `TaqwaWatch debug start in 12 min: accepted, next start at 1793397300, launched at 1793396524`. The start is 23:55:00, the first whole minute 12 minutes or more after the launch. Then came `debug timelines reloaded`, and WidgetKit's three `getTimeline` calls (below).
2. At 23:42:21 the app was terminated (`xcrun simctl terminate C803D94B-7239-435B-9CEB-2A6AD75F141F world.taqwa.ios.watchkitapp`) and the face brought back with the control tool's `button HOME`.
3. A loop in `build/timeline/loop.sh` (untracked) took `xcrun simctl io C803D94B-7239-435B-9CEB-2A6AD75F141F screenshot build/timeline/t<n>.png` every 60 s from 23:43:30 to 00:03:30: 21 shots, each at 30 s past its minute. Nothing touched the watch during the loop. The shots are in `series12-timeline/` (`t00.png` to `t20.png`).

The rectangular's countdown, read from each shot (zoomed), against the rule's value for that instant:

| Shot | Taken | Left until the next start | Expected | Read on the face |
|---|---|---|---|---|
| t00 | 23:43:30 | 11 min 30 s | ASR 16:10, in 11 min | ASR 16:10, in 11 min |
| t01 | 23:44:30 | 10 min 30 s | in 10 min | in 10 min |
| t02 | 23:45:30 | 9 min 30 s | in 9 min | in 9 min |
| t03 | 23:46:30 | 8 min 30 s | in 8 min | in 8 min |
| t04 | 23:47:30 | 7 min 30 s | in 7 min | in 7 min |
| t05 | 23:48:30 | 6 min 30 s | in 6 min | in 6 min |
| t06 | 23:49:30 | 5 min 30 s | in 5 min | in 5 min |
| t07 | 23:50:30 | 4 min 30 s | in 4 min | in 4 min |
| t08 | 23:51:30 | 3 min 30 s | in 3 min | in 3 min |
| t09 | 23:52:30 | 2 min 30 s | in 2 min | in 2 min |
| t10 | 23:53:30 | 1 min 30 s | in 1 min | in 1 min |
| t11 | 23:54:30 | 30 s | in under a minute | in under a minute |
| | 23:55:00 | Asr's start (the sample's 16:10) | | |
| t12 | 23:55:30 | 2 h 53 min 30 s (Maghrib at 02:49:00) | MAGHRIB 19:04, in 2 h 53 min | MAGHRIB 19:04, in 2 h 53 min |
| t13 | 23:56:30 | 2 h 52 min 30 s | in 2 h 52 min | in 2 h 52 min |
| t14 | 23:57:30 | 2 h 51 min 30 s | in 2 h 51 min | in 2 h 51 min |
| t15 | 23:58:30 | 2 h 50 min 30 s | in 2 h 50 min | in 2 h 50 min |
| t16 | 23:59:30 | 2 h 49 min 30 s | in 2 h 49 min | in 2 h 49 min |
| t17 | 00:00:30 | 2 h 48 min 30 s | in 2 h 48 min | in 2 h 48 min |
| t18 | 00:01:30 | 2 h 47 min 30 s | in 2 h 47 min | in 2 h 47 min |
| t19 | 00:02:30 | 2 h 46 min 30 s | in 2 h 46 min | in 2 h 46 min |
| t20 | 00:03:30 | 2 h 45 min 30 s | in 2 h 45 min | in 2 h 45 min |

All 21 minutes show their own value: none is stale, skipped or merged, and the face moved every minute with the app terminated. The rollover came from the first timeline's own entries, at the start, before WidgetKit's reload. The bar had filled almost to its end by t11 and began again from empty for Maghrib at t12. The circular followed: "ASR 16:10" with its gauge all but full at t11, then "MAGHRIB 19:04" with its gauge starting again from 12 o'clock at t12.

The extension's log for the run, every `TaqwaWatch` line between the launch and 00:03:30 that is not the app's handshake:

```
23:42:04.258 TaqwaWatch debug start in 12 min: accepted, next start at 1793397300, launched at 1793396524
23:42:04.303 TaqwaWatch timeline 72 2 reloadAt 1793397300 accessoryCircular 51x51
23:42:04.457 TaqwaWatch timeline 72 1 reloadAt 1793397300 accessoryRectangular 194x80
23:42:05.416 TaqwaWatch timeline 72 1 reloadAt 1793397300 accessoryRectangular 196x80
23:55:51.658 TaqwaWatch timeline 72 11 reloadAt 1793400951 accessoryCircular 51x51
23:55:51.902 TaqwaWatch timeline 72 2 reloadAt 1793400951 accessoryRectangular 194x80
23:55:53.319 TaqwaWatch timeline 72 2 reloadAt 1793400953 accessoryRectangular 196x80
```

- No `getTimeline` came between 23:42:05 and the start.
- The reload came once per placed family: the Modular's rectangular (196 × 80), its circular, and the Smart Stack card (194 × 80), which was still configured. It came 51 to 53 s after the start that `reloadAt` named, so WidgetKit treats `.after(date)` as "no earlier than". The face did not wait for it: t12 drew Maghrib at 23:55:30.
- Each call made 72 entries in 1 to 11 ms: 62 within the hour (at most 62 while every start is on a whole minute, as the phone's are and as this run's were, Ruling 64 item 3) and 10 late ones (erratum p4 #10). After the reload, `reloadAt` is an hour on, since Maghrib is 2 h 54 min away.
- This run's build (`44cfbd8d`) predates Ruling 64 item 2, by which a turn of the date that changes the answer takes one of the 10 late entries. Here midnight changed nothing the face drew: t17 (00:00:30) still reads "MAGHRIB 19:04", "in 2 h 48 min".

The Lead's call 5 holds on the simulator: the face shows each minute's entry while the extension is idle. Two things are still plan 5's to see on her watch (gate):
- The simulator's screen stayed lit for the whole run, with no wrist-down Always On state, so the low-power cadence was not seen.
- Inactive faces: after the run, the face switcher's preview of Utility still read "FAJR IN 4 H 28 MIN", from the `capetown` timeline made at 23:39. The `-taqwaStartIn` launch's reload had reached only the active face's families and the Smart Stack card. What an inactive face draws when it next becomes the active face was not checked.

The `-taqwaStartIn` sample keeps the sample's clock strings, so the face read "ASR 16:10" and "MAGHRIB 19:04" at other hours. Only the countdown and the rollover are read from it.

Put back as found by 00:07:
- The Series 12:
  - Modular, Infograph and Utility were removed from its library, and Numerals Duo was selected again.
  - The Taqwa card was removed from the Smart Stack, which holds its one pinned widget as found.
  - The watch app was uninstalled: 15 Apple apps, the same identifiers as found, and no Taqwa group container.
  - `en-US` and `en_US@rg=zazzzz` were unchanged.
- The iPhone: its Taqwa 1.2.0 (38) was reinstalled from the saved bundle, every file's hash equal to the found bundle's, with 40 apps as found. It was shut down.
- The pair made here was unpaired (`xcrun simctl list pairs` empty).
- The unpair also shut the Series 12 down (Shutting Down at 00:06). It was booted again at 00:06, back to Numerals Duo. The control tool reports the panel still attached, but the desktop app's own log stream on the watch, running since 21:50, ended at that shutdown and did not come back by itself.
- **For the next `ios-pair` task:** unpairing a pair whose watch is booted shuts that watch down.

## Task 7: the app's pages, and Today (30 October 2026, Lane A)

### Step 4: Today and the pages on the SE 3 (40mm), watchOS 27.0

Taken on `FCA316A7-A49B-4795-9610-7D61FD958A8A` under the `sim-se3` lock (21:03 to 21:16, and the text-size shot 21:21 to 21:22), with `xcrun simctl io <udid> screenshot`, from the Debug watch app built at the task's tree. Each shot is a launch with the arguments below. The status bar's clock is the simulator's real time; Today's instant is the scripted one. The shots are at 14:58:24, the drawing's own "1:11:36" (at 14:58:00 the instant reads "1:12:00").

| Shot | Arguments | What it shows |
|---|---|---|
| `today-capetown-145824.png` | `-taqwaSampleFeed capetown -taqwaAt 14:58:24 -taqwaFrozen 1 -taqwaPage today` | `aToday`: "ASR IN" in amber capitals, "1:11:36" in Manrope Light, "16:10" in mist; the ring clockwise from 12 o'clock to 8,724/13,020; the list opened on the lit Dhuhr (amber, in its halo), then Asr and Maghrib (hollow pips) and Isha dimmed at the foot |
| `today-en12-145824.png` | `-taqwaSampleFeed en12 …` | "4:10 PM" under the countdown, and every row with its day period ("12:33 PM") |
| `today-ar-ly12-145824.png` | `-taqwaSampleFeed ar-ly12 …` | "متبقٍّ على العصر" in the system font, no tracking; "1:11:36" in Western digits; "4:10 م"; the ring anticlockwise from 12 o'clock (the mirror of Cape Town's); the rows right to left, the pips on the right |
| `today-ar-sa-145824.png` | `-taqwaSampleFeed ar-sa …` | the countdown "1:11:36" in Western digits (the phone ring's `ringDigitZero`) under "١٦:١٠", and every row's time in Arabic-Indic digits; the ring anticlockwise |
| `today-nofeed.png` | `-taqwaNoFeed 1 -taqwaPage today` | the watch's own "Open Taqwa on your phone" |
| `today-nofeed-newer.png` | `-taqwaNoFeed 1 -taqwaNewerSeen 1 -taqwaPage today` | the watch's own "Update Taqwa on your watch" (plan 3's call 5) |
| `today-capetown-145824-newer.png` | `-taqwaSampleFeed capetown -taqwaNewerSeen 1 -taqwaAt 14:58:24 -taqwaFrozen 1 -taqwaPage today` | the ring still counting from the last feed, and "Update Taqwa / on your watch" in amber in the time's place, both lines inside the ring |
| `today-ar-ly12-145824-newer.png` | the same with `ar-ly12` | "حدِّث تقوى / على ساعتك" in the time's place |
| `tasbeeh-capetown.png`, `qibla-capetown.png` | `-taqwaSampleFeed capetown -taqwaAt 14:58:24 -taqwaFrozen 1 -taqwaPage tasbeeh` (or `qibla`) | the two pages until Tasks 8 and 9: the feed's `words.tasbeeh` and `words.qibla`; the page dots on the second and third page |
| `tasbeeh-ar-ly12.png`, `qibla-ar-ly12.png` | the same with `ar-ly12` | "التسبيح" and "القبلة" |
| `tasbeeh-nofeed-newer.png` | `-taqwaNoFeed 1 -taqwaNewerSeen 1 -taqwaPage tasbeeh` | the second page with the watch's own update line |
| `today-capetown-145824-axl.png` | the capetown shot's arguments and `-UIPreferredContentSizeCategoryName UICTContentSizeCategoryAccessibilityXL` (the app's process alone; no setting of the SE 3 changed) | the rows grown to 1.3 times, their cap, and the ring at its own size. At `UICTContentSizeCategoryXXXL` the shot was the same as at the default: the rows read their scale from a 1-point `@ScaledMetric`, which the pixel grid rounds to 1.0 at every standard size (found by Task 8's review round 2; whether Today takes Task 8's 100-point base is the Lead's call) |

Against `faces.js`'s `aToday` every shot has the drawing's words, digits, direction and places. The ring's arc clears the system clock at the top right (zoomed).

The ring's fill was measured along its stroke (radius 95 px about the centre column x = 162 px, every 10° clockwise from 12 o'clock): capetown and en12 read amber 0–240°, ar-ly12 and ar-sa 120–0° (amber at 0° and from 120° to 350°), the mirror. 8,724/13,020 of a turn is 241°. Where the build differs from the drawing:
- The page dots (`TabView`'s `.page` style) sit at the foot, over the dimmed fourth row. The drawing has none.
- The update notice, which the drawing does not draw, is SemiBold 9.5 on two lines in a box 64 wide (the first shots, at 10.5 in a box 82 wide, cut "on your watch" against the ring's stroke).

`scripts/watch-build.sh test` on the final tree: TEST SUCCEEDED, 5 tests, 0 failures (`LaunchTests` 3, `TodayReadingOrderTests` 2). Three mutants were each seen red on the SE 3 first: the ring declared after the rows (`testTodayReadsTheRingThenTheDay` read the rows first), the Tasbeeh page with no feed ignoring the notice (`testANewerFormatWithNoFeedSaysUpdate`, "the tasbeeh page says the update"), and the test's own swipe removed (the Tasbeeh page never came: the pager keeps no page beside the one shown).

Put back as found at 21:16: the watch app and its UI-test runner uninstalled (the 15 Apple apps as found, no Taqwa group container), `en-US` and `en_US@rg=zazzzz`, unpaired, shut down.

## Task 8: Tasbeeh with the crown and undo (31 October 2026, Lane A)

### Step 4: the crown measured on the SE 3 (40mm), watchOS 27.0

Under the `sim-se3` lock (00:37 to 01:07), a throwaway UI test (deleted after) turned the crown with `XCUIDevice.rotateDigitalCrown(delta:velocity:)` on a fresh counter and read the counter's value:

| Turn | Counted |
|---|---|
| `delta: 1.0`, `.slow` (twice) | 8 |
| `delta: 1.0`, `.fast` (twice) | 9 |
| `delta: -0.5`, `.slow` or `.fast` | −4 |
| `delta: 1/8`, `.slow` (four times) | 1 each |
| `delta: 2/8`, `.slow` | 2 |
| `delta: 3/8`, `.slow` | 3 |
| `delta: 3/8`, `.fast` (three times) | 3 each |
| `delta: -1/8`, `.slow` (twice) | −1 each |

So `crownDetent` is 1/8 (`TasbeehCrownTests.swift`). The crown accelerates by one detent over a whole fast turn, but not over three detents, so `testAFastSpinFrom99OpensRoundTwo` asserts "ROUND 2" and the fast detents less one, 2, as the plan wrote it. The counter's crown is `digitalCrownRotation(detent:by: 1, sensitivity: .medium)`.

### Step 5: Tasbeeh on the SE 3 (40mm), watchOS 27.0

From the Debug watch app built at the task's tree, each a launch with `-taqwaFresh 1 -taqwaPage tasbeeh`, an English watch, and the arguments below, shot with `xcrun simctl io <udid> screenshot` (the counter) or attached by a throwaway UI test that opened the list (the list and the dialog). The status bar's clock is the simulator's real time.

| Shot | Arguments | What it shows |
|---|---|---|
| `tasbeeh-astaghfirullah-64.png` | `-taqwaSampleFeed capetown -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64` | `aTasbeeh`, the mockup's: أَسْتَغْفِرُ ٱللَّٰهَ in the system font over "Astaghfirullah"; the ring clockwise from 12 o'clock to 64 %; "ROUND 1" in amber, "64" in Manrope Light, "of 100"; the crown's arrows at the top right under the clock; "Turn the crown or tap" at the foot, above the page dots |
| `tasbeeh-set-40.png` | `-taqwaSampleFeed capetown -taqwaTasbeehCount 40` | the after-prayer set at 40: Alhamdulillah (the second part), the notch at 33 cutting the arc, the parts' dots (slate, amber, hollow) |
| `tasbeeh-ar-ly12-40.png` | `-taqwaSampleFeed ar-ly12 -taqwaTasbeehCount 40` | Arabic: no transliteration, "الجولة 1", "من 100" and the hint in Arabic; the dots right to left; the ring still clockwise |
| `tasbeeh-ar-sa-astaghfirullah-64.png` | `-taqwaSampleFeed ar-sa -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64` | Saudi Arabic: "٦٤", "الجولة ١", "من ١٠٠" in Arabic-Indic digits |
| `tasbeeh-his-phrase-7.png` | `-taqwaSampleFeed capetown -taqwaPhrase "اللهم صل على محمد" -taqwaTasbeehPhrase debug-phrase -taqwaTasbeehCount 7` | a phrase of his own form, as typed, with no transliteration, counting to its 33 |
| `tasbeeh-set-0-round-2.png` | `-taqwaSampleFeed capetown -taqwaTasbeehCount 100` | a finished set opened: it held its full ring for a second and rolled over to "ROUND 2", 0 |
| `tasbeeh-list-capetown-top.png`, `tasbeeh-list-capetown-his.png` | `-taqwaSampleFeed capetown -taqwaPhrase "اللهم صل على محمد"`, the dhikr's line tapped | the list: After prayer first, chosen (amber border and name, "33·33·34"), then the six; scrolled to the end, the sample's second phrase and his "اللهم صل على محمد", "33", the last of the phrases after the seven (the UI test reads all ten in order), then Reset |
| `tasbeeh-reset-question.png` | the same, Reset tapped | "Reset the count for this dhikr?" with "Reset" and the system's close |
| `tasbeeh-list-ar-sa-top.png`, `tasbeeh-list-ar-sa-his.png` | `-taqwaSampleFeed ar-sa -taqwaPhrase "اللهم صل على محمد"` | the list in Arabic, the rows right to left, the set's parts "٣٣، ٣٣، ٣٤" with the Arabic comma; "إعادة العدّ" at the foot; the close button and title stay on the watch's own sides |

Against `faces.js`'s `aTasbeeh` every counter shot has the drawing's words, digits and places but one: the dhikr's line stands at 44 and 57 (14 points), not 30 and 45, because watchOS's clock takes the top 28 points, so the drawing's line sat under the clock and could not open the list (first shot, `build/`, not kept). The Task 7 shots `tasbeeh-capetown.png` and `tasbeeh-ar-ly12.png` show the page before this task (its title alone) and are kept as Task 7's record.

`scripts/watch-build.sh test` on the final tree: TEST SUCCEEDED, 13 tests, 0 failures (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 3, `TodayReadingOrderTests` 4). The six Tasbeeh tests were each seen red first on the SE 3 (no `tasbeeh.counter`).

Put back as found at 01:07: the watch app and its UI-test runner uninstalled (the 15 Apple apps as found, 39 groups, 38 data containers, no Taqwa group container), `en-US` and `en_US@rg=zazzzz`, unpaired, shut down.

### Fix round 1: a tap anywhere, and the text size, on the SE 3 (40mm), watchOS 27.0

Under the `sim-se3` lock (01:42 to 02:08), from the Debug watch app built at the fixed tree.

**The tap.** `testATapOnTheHintCounts` and `testATapAnywhereBelowTheClockCounts` were seen red on the first build's page (the hint, and the band at y 33 beside the dhikr's line, counted nothing), then green. A throwaway probe (never committed) then tapped a grid on a fresh counter and read the value after each tap:

| Rows (y, points of 197) | Columns (x, points of 162) | Counted |
|---|---|---|
| 3, 10, 18, 24 | 6.5, 24.3, 81.0, 137.7, 155.5 | every tap but x 137.7 at y 10, 18 and 24: the clock's own digits |
| 27 to 58 (8 rows) | 6.5, 155.5 (beside the dhikr's line) | every tap |
| 100, 157, 165, 171, 175, 179, 183, 187, 191, 195, 196.5 | all five | every tap: the ring, the hint (y 176 to 189) and the page dots' row |

The dhikr's line at y 30 still opened the list, and a swipe right from the page, and one from the hint's band, still reached Today. The counter's element read (0, 0, 162, 197): the whole screen.

**The text size,** by `-UIPreferredContentSizeCategoryName` for the app's process alone (`xcrun simctl ui content_size` answers `unsupported` on watchOS, so no setting of the SE 3 was changed), shot by a throwaway UI test (`XCUIScreen` screenshots, never committed):

| Shot | Arguments | What it shows |
|---|---|---|
| `tasbeeh-astaghfirullah-64-axl.png` | `-taqwaSampleFeed capetown -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64` and `UICTContentSizeCategoryAccessibilityXL` | the dhikr at 1.15 times, the transliteration, "ROUND 1" and "of 100" at 1.3, the count, the ring and the hint at their drawn sizes; the dhikr's marks clear of the clock's digits |
| `tasbeeh-ar-ly12-40-axl.png` | `-taqwaSampleFeed ar-ly12 -taqwaTasbeehCount 40` and the same | the Arabic lines in the system font, grown alike |
| `tasbeeh-list-capetown-top-axl.png` | `-taqwaSampleFeed capetown -taqwaPhrase "اللهم صل على محمد"` and the same, the dhikr's line tapped | the rows' names and targets at 1.3 or the largest step whose widest word fits ("Alhamdulillah" one line, smaller than "SubhanAllah"), the system's title grown by itself |
| `tasbeeh-list-capetown-his-axl.png` | the same, scrolled to the end | his phrase on two lines, and Reset grown |

Every standard size (S, XL, XXL, XXXL) drew `tasbeeh-astaghfirullah-64` exactly as the default, and every accessibility size (AX1, AX2, AXL, AX5) exactly as AXL. Review round 2 found that this was not the platform's behaviour but the 1-point base's: the pixel grid rounded it to 1.0 at every standard size and 1.5 at every accessibility size. Fix round 2, below, reads the size from 100 points. At the default, the five counter shots and the two list shots above were taken again and are the committed ones pixel for pixel outside the status bar's clock.

`scripts/watch-build.sh test` on the fixed tree: TEST SUCCEEDED, 15 tests, 0 failures (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 5, `TodayReadingOrderTests` 4).

Put back as found at 02:08: the watch app and its UI-test runner uninstalled (the 15 Apple apps as found, 39 groups, 38 data containers, no Taqwa group container), `en-US` and `en_US@rg=zazzzz`, VoiceOver untouched, unpaired, shut down.

### Fix round 2: the standard text sizes, on the SE 3 (40mm), watchOS 27.0

Under the `sim-se3` lock (02:46 to 03:00), from the Debug watch app built at the fixed tree. The size is set by `-UIPreferredContentSizeCategoryName` for the app's process alone, as before; no setting of the SE 3 changed. A throwaway UI test in a scratch copy took the shots (`XCUIScreen` screenshots, never committed).

`testTheStandardSizesAboveTheDefaultGrowTheLines` was seen red with the old base of 1 point (Astaghfirullah's transliteration 61.0 points wide at the default and at XL), then green on the base of 100. The lines' widths, from the accessibility tree:

| Size | Body at a base of 100 (review round 2's probe) | Astaghfirullah's dhikr (cap 1.15) | Its transliteration (cap 1.3) |
|---|---|---|---|
| the default, and L | 100 | 62.5 | 61.0 |
| XL | 105.5 | 65.5 | 64.0 |
| XXXL | 116 | 71.5 (its cap) | 70.5 |
| AccessibilityM | 127 | 71.5 | 77.0 |
| AccessibilityXL | 138 | 71.5 | 79.0 (its cap) |

XS, S and M read 89, 94.5 and 100 (the probe), and draw as the default: the floor is 1.

| Shot | Arguments | What it shows |
|---|---|---|
| `tasbeeh-astaghfirullah-64-xxxl.png` | `-taqwaSampleFeed capetown -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64` and `UICTContentSizeCategoryXXXL` | the dhikr at its 1.15 cap, the transliteration, "ROUND 1" and "of 100" at 1.16 times, the count, the ring and the hint at their drawn sizes |
| `tasbeeh-ar-ly12-40-xxxl.png` | `-taqwaSampleFeed ar-ly12 -taqwaTasbeehCount 40` and the same | the Arabic labels grown alike, the dots and the ring unchanged |
| `tasbeeh-list-capetown-top-xxxl.png` | `-taqwaSampleFeed capetown -taqwaPhrase "اللهم صل على محمد"` and the same, the dhikr's line tapped | the rows' names and targets at 1.16 times, every word whole, the system's title grown by itself |
| `tasbeeh-list-capetown-his-xxxl.png` | the same, scrolled to the end | his phrase on two lines, and Reset grown |

Taken again on this tree, the seven default shots (`tasbeeh-astaghfirullah-64`, `tasbeeh-set-40`, `tasbeeh-ar-ly12-40`, `tasbeeh-ar-sa-astaghfirullah-64`, `tasbeeh-his-phrase-7`, `tasbeeh-list-capetown-top`, `tasbeeh-list-capetown-his`) and the four AccessibilityXL shots are the committed ones pixel for pixel outside the status bar's clock (x 220 to 303, y 20 to 43 of 324 × 394 pixels). The AccessibilityXL ones are unchanged because 1.38 is above every cap. At L the four scenes drew as the default, and at XL, XXXL, AccessibilityM and AccessibilityXL each differs from the size before it.

`scripts/watch-build.sh test` on the fixed tree: TEST SUCCEEDED, 16 tests, 0 failures (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 6, `TodayReadingOrderTests` 4).

Put back as found at 03:00: the watch app and its UI-test runner uninstalled (the 15 Apple apps as found, the same 39 groups and 38 data containers, no Taqwa group container), `en-US` and `en_US@rg=zazzzz`, `com.apple.Accessibility` as found (VoiceOver off), unpaired, shut down.

### Fix round 3: the watch's own default text size, on the Series 12 (46mm), watchOS 27.0

Review round 3 asked whether the 100-point base grows the lines at the default size of a 44mm or larger watch. Apple gives 40, 41 and 42mm watches Large and 44, 45, 46 and 49mm ones XLarge as their default ("Supporting multiple watch sizes"), and every measurement until now was on the 40mm SE 3.

Under the `ios-pair` lock (04:09 to 04:24), on the Series 12 `C803D94B` alone: it was found booted and unpaired, was not paired, and was left booted. A throwaway probe in a scratch copy of `cdae29de` (never committed) wrote each launch's readings into the accessibility tree. The size was set by `-UIPreferredContentSizeCategoryName` for the app's process alone; "the default" is a launch without it, where SwiftUI's `dynamicTypeSize` was `.xLarge` and `WKInterfaceDevice` said `UICTContentSizeCategoryXL`, as Apple's table gives the 46mm.

| Size | Body at a base of 100 | `UIFontMetrics.default` of 100 | Body's point size | Astaghfirullah's dhikr (cap 1.15) | Its transliteration (cap 1.3) |
|---|---|---|---|---|---|
| XS | 84.5 | 84.5 | 14 | 79.5 | 78.0 |
| S | 89.5 | 89.5 | 15 | 79.5 | 78.0 |
| M and L | 95 | 95 | 16 | 79.5 | 78.0 |
| **the default, XL** | **100** | **100** | **17** | **79.5** | **78.0** |
| XXL | 105 | 105 | 18 | 83.5 | 82.0 |
| XXXL | 110.5 | 110.5 | 19 | 87.5 | 86.0 |
| AccessibilityM | 120.5 | 120.5 | 21 | 91.0 (its cap) | 94.0 |
| AccessibilityL | 125.5 | 125.5 | 22 | 91.0 | 98.0 |
| AccessibilityXL to XXXL | 131 | 131 | 23 | 91.0 | 101.5 (its cap) |

So the body style scales from the watch's own default, not from Large: the SE 3 reads 100 at L and the 46mm 100 at XL. On both, the base read back is 1 at the default and the lines are the drawing's. A child view given `.environment(\.dynamicTypeSize, .large)` read 95 and one given `.xLarge` read 100, at every launch size. A 1-point base (Today's rows) read 1.0 from XS to AccessibilityM and 1.5 from AccessibilityL.

Shots, by `XCUIScreen` in a throwaway UI test in a scratch copy of the fixed tree (never committed), each scene launched at the default, XS, L, XL and XXL:
- Astaghfirullah at 64: the default, XS, L and XL are one pixel for pixel (416 × 496); XXL differs inside the lines' box.
- The after-prayer set at 40: the default and XS are one; L and XL differ from it only in the clock's digits (x 367 to 384, y 39 to 64 pixels), the minute having turned.
- The list's top: the default and XL are one. XS and L differ only above y 104 pixels, in the sheet's own title, which follows the text size itself. Every row is the same.

| Shot | Arguments | What it shows |
|---|---|---|
| `tasbeeh-astaghfirullah-64-46mm.png` | `-taqwaSampleFeed capetown -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64`, the default size | `aTasbeeh` on the 46mm at its own default: the drawing's lines at the drawing's sizes. As this round took it, every place was scaled by the screen's width alone, and the page dots sat on the hint; fix round 4, below, raised the hint and took this shot again |
| `tasbeeh-list-capetown-top-46mm.png` | `-taqwaSampleFeed capetown -taqwaPhrase "اللهم صل على محمد"`, the default size, the dhikr's line tapped | the list's rows at the drawing's 13 and 11 points, as on the 40mm |

Tests on the Series 12, by `WATCH_DEST` (the map names the SE 3 for Lane A's UI tests; these two runs are this finding's evidence):
- Fix round 2's `testTheStandardSizesAboveTheDefaultGrowTheLines` was red there: XL is the 46mm's default, so its transliteration was 78.0 points at both.
- The new `testTheWatchsOwnDefaultSizeDrawsTheDrawing` and the rewritten `testTheStandardSizesAboveTheDefaultGrowTheLines` were green.
- A mutant that takes the scale from Large (the base over 95, in a scratch copy) was red in the new test: 82.0 points at the default against 78.0 at XS.

Put back as found at 04:24: the watch app and its UI-test runner uninstalled (15 apps), the same 38 data containers, `com.apple.Accessibility` as found (four keys XCUITest added, deleted), the Preferences list as found (five Apple daemons' plists and the app's dormancy plist, removed), `en-US` and `en_US@rg=zazzzz`, no pair made, left booted, as found. Not put back: two Apple group containers that `amsondevicestoraged` made at 04:21, on the uninstall (`group.com.apple.amsondevicestoraged`, `group.com.apple.servicesanalytics`, as the SE 3 already has). They were left, rather than delete a running daemon's containers. The iPhone 17 Pro was not used.

Then, under `sim-se3` (04:25 to 04:37): `scripts/watch-build.sh test` on the SE 3, TEST SUCCEEDED, 17 tests, 0 failures (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 7, `TodayReadingOrderTests` 4). The SE 3 was put back as found and shut down. No drawing code changed in this round, so the SE 3's shots stand.

The 41, 42, 44, 45 and 49mm sizes were not measured: no simulator of theirs is in the execution map's locks.

### Fix round 4: the hint clear of the page dots, on the SE 3 (40mm) and the Series 12 (46mm), watchOS 27.0

Review round 4 found the system's page dots on the hint "Turn the crown or tap" in fix round 3's own 46mm shot, and across its Arabic letters in the reviewer's ar-ly12 and ar-sa shots. Every place on the page was `aTasbeeh`'s scaled by the screen's width, and the 46mm screen (208 × 248 points) is 205 of the drawing's units high, not the 40mm's 209, while the dots stand from the screen's foot. The drawing's hint baseline at 198 units fell at 239.4 points, and the dots start at 240.

Measured first, by a throwaway probe (never committed) under `sim-se3` and `ios-pair`. Pixel rows are of 2× `simctl io` shots:

| | SE 3 (40mm), 162 × 197 | Series 12 (46mm), 208 × 248 |
|---|---|---|
| The page's own frame (the safe area the pager leaves) | y 40 to 171 | y 53 to 212 |
| The pager's element valued "page 2 of 3" | y 183 to 206 | y 230 to 256 |
| The dots in the shot | 3 points, y 193 to 196 | 6 points, y 240 to 246 |
| The dots' top under the page's foot | 22 points, 23.4 units | 28 points, 23.2 units |
| The hint's frame before the fix (Latin; Arabic) | to 189.5; to 189 | to 243.4; to 242.4 |
| The hint's lowest ink before the fix | 189 (the "p"); 189 | 242.5; 241.5 |

So the dots stand about 23 units under the page's own foot on both watches, and the element's frame reaches 10 points beyond the dots above and below on both. The fix (`TasbeehFoot` in WatchCore) keeps the drawing's baseline but never lets it fall more than 16.45 units under the page's own foot, which `TasbeehPage` reads from a `GeometryReader` inside the safe area and passes to the drawing. On the SE 3, where `aTasbeeh` is drawn, the baseline is the drawing's exactly (16.44 units under its foot). On the Series 12 it is 231.9 points, 7.5 higher, and nothing else on the page moves.

After the fix, on the Series 12: the Latin hint's ink ends at 235 (row 469 pixels), 5 points above the dots (on the SE 3, 4). The Arabic hint's ink also ends at 235. The hint's capitals start 11.5 points under the ring's ink (on the SE 3, 15).

Test-first:
- WatchCore's `testTheHintStandsAboveThePageDotsOnEveryWatch` was red before `TasbeehFoot` existed. It holds the SE 3's exact place, the Series 12's place under its foot, at least 6.5 units of baseline above both watches' dots, and a taller screen keeping the drawing's place.
- The UI test `testTheHintStandsClearOfThePageDots` checks Latin and Arabic: the hint's frame (`tasbeeh.hint`) must end at least 3 points above the dots' top, the pager element's top plus 10. It was RED on the Series 12 before the fix (243.4 against 237.0) and green on the SE 3 (3.5 points). It is green on both after the fix.
- `testATapOnTheHintCounts` now taps the hint's own frame, wherever the hint stands.

| Shot | Arguments | What it shows |
|---|---|---|
| `tasbeeh-astaghfirullah-64-46mm.png` | `-taqwaSampleFeed capetown -taqwaTasbeehPhrase astaghfirullah -taqwaTasbeehCount 64`, the default size | taken again: the hint 5 points above the page dots. Against fix round 3's shot, only the hint's rows (y 446 to 485 pixels) and the clock differ |
| `tasbeeh-ar-ly12-40-46mm.png` | `-taqwaSampleFeed ar-ly12 -taqwaTasbeehCount 40`, the default size | the Arabic hint "أدِر التاج أو اضغط" whole above the dots, its lowest ink at row 469 against the dots' 480 |

On the SE 3 the fix changes nothing drawn. The five default counter shots taken again (`tasbeeh-astaghfirullah-64`, `tasbeeh-set-40`, `tasbeeh-ar-ly12-40`, `tasbeeh-ar-sa-astaghfirullah-64`, `tasbeeh-his-phrase-7`) and the two AccessibilityXL ones (`tasbeeh-astaghfirullah-64-axl`, `tasbeeh-ar-ly12-40-axl`) are the committed ones pixel for pixel outside the clock (x 219 to 304, y 20 to 43 pixels).

Runs:
- Under `sim-se3`, 05:07 to 05:30: `TasbeehTests` 8/0 and then `scripts/watch-build.sh test`, TEST SUCCEEDED, 18 tests, 0 failures (`LaunchTests` 3, `TasbeehCrownTests` 3, `TasbeehTests` 8, `TodayReadingOrderTests` 4).
- Under `ios-pair`, 05:08 to 05:32, on the Series 12 alone, by `WATCH_DEST`'s generic build: `TasbeehTests` 8/0.

Both simulators were put back as found (the ledger has the details). The SE 3 was shut down. The Series 12 was left booted, as found, and not paired.

The 41, 42, 44, 45 and 49mm sizes were not measured, because no simulator of theirs is in the execution map's locks. The rule takes each screen's own safe area, so it relies on the dots standing as far under the page's foot on those as on these two (about 23 units). The 42mm, which is the same shape as the 46mm, is the one most like the case found.

## Task 9: Qibla with the watch's compass (31 October 2026, Lane A)

### Step 5: Qibla on the SE 3 (40mm), watchOS 27.0

Taken on `FCA316A7-A49B-4795-9610-7D61FD958A8A` under the `sim-se3` lock (07:00 to 07:05, and again after Ruling 66 item 8, 08:41 to 08:56), with `xcrun simctl io <udid> screenshot`, from the Debug watch app built at the task's tree. Each shot is a launch with `-taqwaPage qibla`, an English watch, and the arguments below, shot 6 s after it (23 s for BestEffort). The simulator has no magnetometer, so the DEBUG scripted compass stands in: `-taqwaHeading` is the magnetic heading, a sample every 50 ms, made true by the sample's declination (−27°) as the real compass's is. The status bar's clock is the simulator's real time.

| Shot | Arguments | What it shows |
|---|---|---|
| `qibla-capetown-facing.png` | `-taqwaSampleFeed capetown -taqwaHeading 50` | true 23, facing: `aQibla` itself, N to the upper left, the marker and the needle straight up, the rim lit in amber with its halo; "Facing qibla" in amber over "6,600 km" |
| `qibla-capetown-120.png` | `-taqwaSampleFeed capetown -taqwaHeading 147` | true 120: the dial turned, N at 240°, the marker at −97° on the left with the needle to it; "23°" over "6,600 km" (VoiceOver: "Turn left about 90 degrees") |
| `qibla-capetown-low.png` | `-taqwaSampleFeed capetown -taqwaHeading 50 -taqwaHeadingAccuracy 60` | after a second low: the dial dimmed, no needle, marker, hub or letters; "Compass needs calibrating" on two lines under the rim, no readout |
| `qibla-capetown-besteffort.png` | the same, 23 s | after twenty seconds: "Compass unavailable" over "23° · 6,600 km" |
| `qibla-capetown-nocompass.png` | `-taqwaSampleFeed capetown -taqwaNoCompass 1` | "No compass sensor" over "23° · 6,600 km" |
| `qibla-capetown-own-compass.png` | `-taqwaSampleFeed capetown` (the watch's own compass) | the SE 3 has none (`headingAvailable` false): "No compass sensor", and no prompt |
| `qibla-ar-sa-facing.png`, `-120.png`, `-low.png`, `-besteffort.png`, `-nocompass.png` | the same with `ar-sa` | the Saudi words and digits: "أنت باتجاه القبلة", "٢٣°", "٦٬٦٠٠ كم", the titles in the system font; ش, ق, ج and غ where N, E, S and W stand, the dial unmirrored |
| `qibla-capetown-120-xxxl.png`, `-low-xxxl.png`, `-nocompass-xxxl.png`, `qibla-ar-sa-facing-xxxl.png`, `-nocompass-xxxl.png` | the same with `-UIPreferredContentSizeCategoryName UICTContentSizeCategoryXXXL`, the largest before the accessibility sizes (the app's process alone) | the caption, the detail and the letters grown (1.16 on the SE 3); a two-line title keeps its drawn size, under the rim |
| `qibla-capetown-facing-before-r66.png`, `qibla-capetown-nocompass-before-r66.png`, `qibla-ar-sa-facing-before-r66.png`, `qibla-ar-sa-nocompass-before-r66.png` | as above, built before Ruling 66 item 8 | the long phrases drawn ("Makkah · 6,600 km away", "23° · 6,600 km to Makkah"), for the before and after Ruling 66 item 9 puts to Mohamed; VoiceOver still hears these |

Against `faces.js`'s `aQibla` and `qiblaDial` every shot has the drawing's dial, words and places: the dial at (86, 90), radius 62, its 24 ticks, the letters and the Kaaba's marker turned by the true heading, the caption at y 180 and the detail at y 196, the drawing fitted to the screen (`QiblaLayout`: on the SE 3 by its height, 197 / 212 points a unit, 1.1 points of margin either side). Facing (true 23) the dial is `aQibla`'s own: N to the upper left, the marker and the needle straight up, the rim lit with its halo. At true 120 the N is at 240°, the marker at 23 − 120 = −97°, to the left, and the needle points at it. Arabic turns the same way (ش where N is): the dial never mirrors. The bad states dim the dial to alpha 0.4 and draw no needle, marker, hub or letters; "Compass needs calibrating" takes two lines, its last at y 180 and its first 16/15 of its size above, under the dial's rim.

The debug log of the launch without the scripted compass read `TaqwaWatch heading available 0, authorizationStatus 0 (never asked)`: Core Location's `notDetermined`, and the simulator's compass absent, so the page shows "No compass sensor" (`qibla-capetown-own-compass.png`). No permission prompt was drawn. The real heading, and the needle against a real compass, are plan 5's, on her watch.

`scripts/watch-build.sh test` on the task's tree: TEST SUCCEEDED, 23 tests, 0 failures (`LaunchTests` 3, `QiblaTests` 5, `TasbeehCrownTests` 3, `TasbeehTests` 8, `TodayReadingOrderTests` 4). The five Qibla tests were each seen red first on the SE 3 (no `qibla.dial`), and Ruling 66 item 8's two asserts (the drawn detail "6,600 km" and "23° · 6,600 km") red on the model before it.

Ruling 66 item 8, on the Apple Watch: the dial draws the watch's short forms (`distanceOnDial`, "6,600 km"; `qiblaDetailOnDial`, "23° · 6,600 km"), one line, never cut, from 10 grown with the text size to 1.3 times; VoiceOver hears the phone's long phrases unchanged ("Makkah · 6,600 km away", "23° · 6,600 km to Makkah", and before the first reading "23°, Makkah · 6,600 km away"). The readout stands below the dial, so nothing of the Galaxy's fitting against the turning letters (items 3 to 5) is needed. The Arabic detail with the bearing draws the degree sign to the left of the digits ("°٢٣ · ٦٬٦٠٠ كم"): a neutral sign after Arabic-Indic digits takes the paragraph's direction, as in the phone's own strings; it is the feed's text, for plan 5's native review.

Put back as found each time: the watch app and its UI-test runner uninstalled, `.GlobalPreferences` and `com.apple.Accessibility` as found (by md5), the same 39 groups, 38 data containers and 149 Preferences files, unpaired, shut down.

## Task 11: the languages, VoiceOver's reading order, and what is left for her watch (31 October 2026, Lane A)

### Step 1: the language pass, end to end, on the Series 12 (46mm) paired with the iPhone 17 Pro

Run on 31 October from 16:28 to 17:15 SAST under the `ios-pair` lock, with the Debug build of `watch-apple` at `735c2960` (`scripts/ios-build.sh`; its app code is `17363c00`'s, since `735c2960` added `LanguagesTests` alone).

Found: no pair. The iPhone was shut down, holding the store's Taqwa 1.2.0 (38) with its city set by hand to Doha (MANUAL) and the 24-hour clock (no `clock_format` key). The Series 12 was booted, as Lane A's `ios-pair` runs had each put it back since it was first found booted on 30 October at 23:03 (with the desktop app's simulator panel attached, by no lane, Ruling 64 item 8; the last before this pass was P4.10 review round 2's), with no lock held, unpaired, with 15 Apple apps, no Taqwa, `en-US` and `en_US@rg=zazzzz`. The phone's `data` folder was APFS-cloned before anything changed. The two were paired, the debug `Taqwa.app` (with `Watch/TaqwaWatch.app`) went on the phone over the store build (erratum p4 #16), and its watch app on the Series 12.

How each configuration was set, with the phone app stopped:
- **The phone app's language** is the per-app language: `AppleLanguages` in Taqwa's own preferences domain inside its container, which is what Settings › Taqwa › Language writes, with `AppleLocale` beside it for the region, so ar-SA has its Arabic-Indic digits (`xcrun simctl spawn <phone> defaults write <container>/Library/Preferences/world.taqwa.ios …`).
- **12 or 24 hours** is Taqwa's own setting, `clock_format` in its `taqwa.preferences_pb`, written by a DataStore writer that keeps every other entry's bytes.
- The watch app was launched, then the phone app; the phone's launch sent the new feed, and the watch held it 4 to 6 s later every time. The watch app was then launched on each page in turn (`-taqwaPage today`, `tasbeeh`, `qibla` with `-taqwaHeading 7`, and `gallery` parts 1 to 3) and shot. Nothing set the watch's state by a debug sample: every surface drew the phone's feed.

Each strip in `languages/` is one configuration's six shots, left to right: Today, Tasbeeh, Qibla, and the gallery's three parts (the circular and corner with the inline; the Smart Stack card; the families without their countdown). They are reduced to 256 colours.

| Phone | The feed the watch held | Watch in English | Watch in Arabic |
|---|---|---|---|
| en-GB, 24 h | `en-GB`, left to right, Western digits, Asr "14:31" | `watch-en-en-24.png` | `watch-ar-en-24.png` |
| en-GB, 12 h | Asr drawn "2:31 pm" (en-GB writes it lowercase), the complications "2:31" | `watch-en-en-12.png` | `watch-ar-en-12.png` |
| ar-LY, 24 h | `ar-LY`, right to left, Western digits, "العصر" "14:31" | `watch-en-ar-LY-24.png` | `watch-ar-ar-LY-24.png` |
| ar-LY, 12 h | "2:31 م" | `watch-en-ar-LY-12.png` | `watch-ar-ar-LY-12.png` |
| ar-SA, 24 h | `ar-SA`, digit zero "٠", ring digit zero "0": "١٤:٣١", the distance "١٬٣٠٠" | `watch-en-ar-SA-24.png` | `watch-ar-ar-SA-24.png` |
| ur-PK, 24 h | `ur-PK`, right to left, "عصر" | `watch-en-ur-24.png` | `watch-ar-ur-24.png` |
| ur-PK, 12 h | "2:31 PM" | `watch-en-ur-12.png` | `watch-ar-ur-12.png` |
| bn-BD, 24 h | `bn-BD`, Western digits as the phone sent them, "আসর" | `watch-en-bn-24.png` | `watch-ar-bn-24.png` |
| bn-BD, 12 h | "2:31 PM" | `watch-en-bn-12.png` | `watch-ar-bn-12.png` |
| fr-FR, 24 h | `fr-FR`, the distance "1 300" | `watch-en-fr-24.png` | `watch-ar-fr-24.png` |
| fr-FR, 12 h | "2:31 PM" | `watch-en-fr-12.png` | `watch-ar-fr-12.png` |
| tr-TR, 24 h | `tr-TR`, "İkindi" | `watch-en-tr-24.png` | `watch-ar-tr-24.png` |
| tr-TR, 12 h | "ÖS 2:31", the day period first | `watch-en-tr-12.png` | `watch-ar-tr-12.png` |
| id-ID, 24 h | `id-ID`, "Ashar" | `watch-en-id-24.png` | `watch-ar-id-24.png` |
| id-ID, 12 h | "2:31 PM" | `watch-en-id-12.png` | `watch-ar-id-12.png` |

What the shots show, against Step 1's expected:
- **Every Taqwa surface speaks the phone's language, digits, clock and direction, whatever the watch's own.** The Arabic-watch strips equal the English-watch ones but for the system's clock in the corner, which moves to the left and follows the watch (`4:55`, the watch's own 12-hour clock under ar_LY).
- On 12 hours, Today draws the time with its day period ("6:27 pm", "6:27 م", "ÖS 6:27") and the complications the bare clock ("6:27").
- On ar-SA, Today's ring reads "0:52:14" in Western digits above "١٨:٢٧", and the tasbeeh's count, the Qibla's degrees and the gallery's times are Arabic-Indic.
- In Arabic and Urdu the rows run right to left and Today's ring fills anticlockwise; the tasbeeh ring fills clockwise and the crown's arrows stay on the crown's side; the Qibla dial never mirrors (north at the top, east on the right, the feed's own letters).
- The Smart Stack card's header is the feed's `appName` ("Taqwa", "تقوى", "তাকওয়া").
- Bengali: this phone (`bn_BD`) sent digit zero "0", so the watch drew Western digits, the digits the phone's feed asked for (no shot was taken of the phone's own screen in Bengali). Plan 3's call 9 assumed Bengali digits on a Bengali phone; that is the phone's rule, not the watch's (for the Lead).

**Taqwa on Apple's faces in Arabic, right to left and tinted, and the system's lists (fix round 1).** The pass above held the Arabic and right-to-left complications by the gallery's shots alone, and left the faces and the lists to plan 5 for the budget. Review round 1 asked for them here, and fix round 1 ran them below.

Seen once, at 16:39: the watch's `appconduitd` refused the phone's push of the watch app ("Need 8192 bytes to install world.taqwa.ios.watchkitapp but only have 0 bytes"; the Mac's disk was 97 % full), and from then the phone answered `isWatchAppInstalled` false, so it sent nothing. Installing the watch app again with `simctl` ended it; the five configurations it cut were run again, and the pass script installs again by itself after 40 s without a feed.

**Ruling 69 item 3 on the simulator:** after the pass, the phone's `Library/Application Support/com.apple.watchconnectivity/<watch>/WatchContent/<hash>/taqwa-watch-key` carries `com.apple.metadata:com_apple_backup_excludeItem: com.apple.MobileBackup` itself, and its folder none.

Put back as found by 17:15: the watch app uninstalled and the watch's language set back; the phone shut down; the pair unpaired, which shut the Series 12 down and re-made it; the phone's `data` folder swapped back to the clone (its entry list equal to the found one, 151 data containers, 250 Preferences, the store bundle, the three unreadable `locationd` caches moved back as they were); the Series 12 booted again: unpaired, 15 apps, no Taqwa, `en-US`, `en_US@rg=zazzzz`, 38 data containers.

### Fix round 1: Taqwa on an Apple face in Arabic, right to left and tinted, and the system's lists, on the Series 12 (46mm)

Run on 31 October from 18:09 to 18:27 SAST under the `ios-pair` lock, with the iOS Simulator control tool on the Series 12 (no access prompt came: Mohamed's grant, Ruling 64 item 7, held). The apps are the Debug build of `watch-apple` at fix round 1's tree, signed by `scripts/ios-build.sh` (its app code is `d94748c0`'s; the round changed one comment in `iOSApp.swift`).

Found as the pass above left it: no pair; the iPhone shut down with the store's Taqwa 1.2.0 (38); the Series 12 booted (the pass's own put-back), unpaired, 15 Apple apps, no Taqwa, `en-US`, `en_US@rg=zazzzz`. The phone's `data` folder was APFS-cloned first. The two were paired, the debug `Taqwa.app` went on the phone over the store build and its watch app on the Series 12, the watch's own language was set to Arabic (`ar`, `ar_LY`) and the watch restarted, and the watch app stored the `ar-ly12` sample (`-taqwaSampleFeed ar-ly12`: the phone's Arabic at 12 hours, right to left).

Placed: the face gallery's "New watch faces" offered a face with an inline slot above the time, the face's own rectangle under it, and three circular slots ("Siri تركيبية"); it was added, and Taqwa placed in its inline slot and its right-hand circular slot. The face's rectangle then showed Taqwa's own card by itself. The face kept its default colour, "فاتح" (light), which is a tinted rendering: Apple's own Activity ring beside Taqwa is drawn in the one colour too, not in its red, green and blue.

| Shot | What it shows |
|---|---|
| `series12-ar-complication-apps.png` | the circular slot's list of apps, in Arabic: "تقوى" with the arch (the app's name in the system's lists follows the watch) |
| `series12-ar-entry-circular.png`, `series12-ar-entry-inline.png` | Taqwa's entries under the header "تقوى": "الصلاة التالية", the widget's name in the watch's language (the widgets' `ar.lproj`) |
| `series12-ar-face-tinted.png` (18:25) | the face, right to left: the inline "المغرب بعد 38 د"; the card's header "تقوى" with the arch on the right, "المغرب 7:04", "بعد 38 د" and the bar; the circular "المغرب / 7:04" in its gauge. Tinted: the accent shapes in the tint (the bar's fill `#D7D5D0`, the name), the rest in the face's white and grey (the bar's track `#A3A3A3`). The bar fills from the right, in reading order, and the gauge anticlockwise from the top, as Today's ring does in a right-to-left language |
| `series12-ar-face-tinted-first-draw.png` (18:21) | the first draw after the signed watch app went on: the bar's track drawn as bright as its fill (`#E5E5E5` across) and the gauge's ring whole. After a restart of the watch the same face drew them apart (the shot above). Which of the two a real watch shows is for plan 5's tinted check on her faces |
| `series12-ar-list-view-chosen.png`, `series12-ar-app-view-grid.png` | Settings › App View set to "عرض كقائمة" (List View; the watch's `LauncherViewMode` 2), and the app view still drawn as the grid after a restart: this simulator draws no list, so the app's name in the List View is not seen here |

Seen once: the watch app first installed was the CI command's unsigned build left in `build/ios-dd` (linker-signed, with no `__entitlements`), so it had no app group, stored the sample in its own container, and the complications drew their empty form (`TaqwaWatch timeline 1 0 reloadAt never`). `scripts/ios-build.sh`'s signed build ended it (`timeline 72 … reloadAt 1793466240`). Plan 5's simulator checks install from a signed build.

Not seen here, and plan 5's on her watch (P5.15): another colour of the face, and the full-colour (multicolour) setting, which are chosen with the crown, and the control tool turns no crown; Modular's rectangular slot in Arabic (this face's rectangle is the system's, not a slot); the List View's names.

Put back by 18:27: the watch app uninstalled, the watch's language set back, the phone shut down, the pair unpaired (which shut the Series 12 down and re-made it, its face and app view with it), the phone's `data` folder swapped back to the clone (its entry list equal to the found one, 151 data containers, 83 groups, 250 Preferences with the same checksums, the store's 1.2.0 (38), the three unreadable `locationd` caches moved back), and the Series 12 booted again: unpaired, 15 apps, no Taqwa, `en-US`, `en_US@rg=zazzzz`, 38 data containers.

### Step 2: VoiceOver's reading order with the phone samples, on the SE 3 (40mm)

`scripts/watch-build.sh test` under the `sim-se3` lock ran 32 UI tests, 0 failures. Beside the 23 that read `capetown` (English, 24 hours; two layout tests read `ar-ly12` too), `LanguagesTests` read Today, Tasbeeh and Qibla with `en12`, `ar-ly12` and `ar-sa`, the watch's own language English:
- Today: the ring first, then the six rows in the order of the day; the ring's sentence "Asr in 1 hour 13 minutes, at ⁨4:10 PM⁩" (en12), "العصر بعد 1 ساعة و13 دقيقة، الساعة ⁨4:10 م⁩" (ar-ly12) and "العصر بعد ١ ساعة و١٣ دقيقة، الساعة ١٦:١٠" (ar-sa); each row "<name>, <time>" with the name as the phone writes it, never the capitals drawn, and on a 12-hour feed every time with its day period, isolated (FSI/PDI); each row's state ("Passed", "انقضت", …). What is drawn beside it: the ring's "1:13:00" in Western digits on ar-sa, Asr's row "١٦:١٠".
- Tasbeeh: the counter named as the phone says the dhikr ("SubhanAllah", "سبحان الله") and valued with plan 3's spoken count before and after a tap ("1 of 100", "1 من 100", "١ من ١٠٠"), never the drawn "of" label or the round's capitals.
- Qibla: while searching, the turn in the feed's words and digits ("Turn right about 45 degrees", "استدر يمينًا حوالي 45 درجة", "استدر يمينًا حوالي ٤٥ درجة"), with the distance as the value.

Its first run held 8 of 9: the ar-sa expectation carried isolates around a 24-hour time, which `WatchRules.spokenClock` rightly leaves bare (digits and a colon alone), as capetown's "at 16:10" is. The expectation was corrected; the app was not changed.

**Fix round 1: every reading in the four samples.** Review round 1 found that run short of the step's "every UI test with the phone samples in en, en12, ar-ly12 and ar-sa": the other readings had been heard in `capetown` alone, and the Qibla's value was only checked as non-empty, so Ruling 66's long phrase was not held in Arabic. `LanguagesTests` now holds every reading the other suites hold on `capetown`, again in `en12`, `ar-ly12` and `ar-sa` (34 tests; 57 UI tests in all, 0 failures on the SE 3 from 17:52 to 18:20 under the `sim-se3` lock). The expected words are the samples' own (`watchcore/vectors/sample-feed-capetown-*.json`), filled as the phone fills them:
- Qibla: searching says the turn, and its value is the long phrase ("Makkah · 6,600 km away", "مكة · على بُعد 6,600 كم", "مكة · على بُعد ٦٬٦٠٠ كم") while the dial draws the short one ("6,600 km", "6,600 كم", "٦٬٦٠٠ كم"); facing likewise ("Facing qibla", "أنت باتجاه القبلة"); no compass says the reason, then the bearing and the distance in the long phrase, in the feed's digits ("٢٣° · ٦٬٦٠٠ كم إلى مكة"), with the short form drawn ("٢٣° · ٦٬٦٠٠ كم"); a low compass says its title ("البوصلة تحتاج إلى معايرة") and nothing after it.
- Tasbeeh: the crown's steps from 31 ("٣٢ من ١٠٠", "٣٣ من ١٠٠. التالي: الحمد لله", "٣٤ من ١٠٠", and back again across the part's end); a fast spin from 99 through the hundred, which opens round 2 at "٢ من ١٠٠" with the counter named "سبحان الله، الجولة ٢" (never the drawn round); the phrase list's rows, each "<name>, <target>" in the feed's words and digits, the one counted selected, and a row chosen counted from 0 and by the crown; Reset, said in the feed's words ("إعادة العدّ"), its question ("إعادة العدّ لهذا الذكر؟") and back to 0.
- Today: with a newer format seen while a feed is held, the ring says its sentence, then the feed's own "Update Taqwa on your watch" ("حدِّث تقوى على ساعتك"), drawn where the time stands; in `capetown` too, which no other suite reads with a feed. Asr's drawn time is now looked for in Asr's own row, since the ring draws the same time.

A mutant that gave VoiceOver the dial's short forms (the searching and no-compass values) failed `testQiblaInArLy12`, `testQiblaInArSa` and `testNoCompassInArSa`, and was reverted. The first run of the new tests held 31 of 34: the three phrase-list tests could not bring Astaghfirullah's row onto the screen, since a quick drag flung the samples' eight rows 266 points, from below the screen to above it and back, on every try. `TasbeehTests.reveal` now drags slowly and holds still before it lifts, and the list stops where the finger stops; `capetown`'s own list tests pass with it too.

What reads `capetown` alone, and why: the tests that measure places and sizes, not words (where a tap counts, the hint and the Qibla's readout clear of the page dots, which also run in `ar-ly12`, the text sizes, which measure the Latin transliteration only a Latin-script feed draws, and the list's scroll on the lit row), the crown's measured detent, and the launch's lines before a feed, which are the watch's own words. Step 2's expected is a list of readings, and each reading is held in the four samples.

**Held only by the accessibility tree, for plan 5's VoiceOver gate:** the simulator has no VoiceOver, so these tests read what VoiceOver would be given (the labels, values and order), not what it says. The language each spoken string carries (erratum p4 #5) is not visible to XCUITest; `GlanceTests` and `NextPrayerContentTests` hold it in `swift test`. Hearing it on her watch, with an English watch reading an Arabic feed, the faces' complications as one element each, the tasbeeh's announcements and the Qibla's turn announced while searching, is Ruling 38's gate.

The SE 3 was put back as found after each run: the app and its UI-test runner uninstalled, 15 apps, no Taqwa container, `en-US`, unpaired, shut down (after fix round 1's runs, 152 Preferences files against 150 found: the system's daemons wrote two during the 57 tests; Taqwa's containers are gone).
