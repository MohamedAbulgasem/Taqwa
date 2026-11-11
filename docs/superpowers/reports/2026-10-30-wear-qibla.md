# Plan 3 Task 7: Qibla on the wrist, the watch's own compass and the dial across the screen

Lane P (`watch-feed`), 30 October 2026. Plan 3 Task 7 (P3.7), built test-first on `watch-companion`'s head.

- Base: `3e309b78` (`git merge --ff-only watch-companion`: already up to date, erratum 1). It holds P3.2 (the compass rules in `:watchcore`), P3.4 (the frame), P3.5 (Today) and P3.10 (the screens' words). P3.6 (Tasbeeh) is on `watch-wear` and not yet on `watch-companion`, so this branch has the Tasbeeh page's stub.
- Commit: one, with the plan's Step 6 message, Taqwa identity, no trailer, nothing pushed. The green WIPs were folded in by a soft reset.
- Devices: `emulator-5558` alone, under the `wear-5558` lock, 14:35 to 14:54 (Step 5); found off, put back as found and stopped by its serial. The plugged-in phone `KB2604110095`, the work iPhone 12 and every simulator were not touched: no `xcrun simctl` was called. No release key was read or used.

## What was built

All of it in `wearApp/src/main/kotlin/world/taqwa/app/wear/qibla/`, the task's own package, and in `wearApp/src/{debug,release}/kotlin/world/taqwa/app/wear/qibla/QiblaLog.kt`.

| File | What it is |
|---|---|
| `WristCompassPolicy.kt` (pure) | `RotationVerdict` and `WristCompassPolicy`: the phone's `AndroidCompassSource` decisions with its three constants (trust after 500 ms of real samples, distrust or drop after 2 s of lies, a notice every 200 ms with no raw pair), and the watch's field check (call 13): `onField`, `fieldMicroTesla`, and `lowReason`, INTERFERENCE while a field heard in the last 1,000 ms is implausible whatever the source and its trust, else CALIBRATION at LOW accuracy or worse. Also `remapFor(rotation)`, the phone's remap axes. |
| `Tilt.kt` (pure) | `Tilt.degrees(m)` = acos of `m[8]` held to [-1, 1]; `Tilt.Level` (in above 40°, out under 30°). |
| `QiblaModel.kt` (pure) | `QiblaType` (the plan's six lines, the tilt notice's `level`, the dial's room, the caption's and the detail's places, `captionWidth`, `detailY`), `QiblaScreen` and `qiblaScreen(state, tilted, feed)`. |
| `WristCompass.kt` | `WristReading` and `WristCompass(context, declination)`: `hasSensor`, and `readings`, a `callbackFlow` on the main looper at `SENSOR_DELAY_GAME`, registered while collected, with one listener for each sensor (Step 5, below). |
| `QiblaPage.kt` | The page: the compass into a `WatchQibla` (reset on each start) and a `Tilt.Level` while active, one `feel.aligned()` per buzz, `NoSensor` with nothing registered when there is no compass; the dial drawn from `qiblaDial(100, 100, 92, heading, bearing, A, 1.05)`, the caption at y 141 and the detail under it. |
| `QiblaLog.kt` (debug / release) | The `TaqwaWearQibla` line, once a second, in a debug build; the release build's is an inline no-op that never builds the line (the release dex holds no `TaqwaWearQibla`). |

Outside the package, two lines: `Pages.kt` loses the Task 4 stub `QiblaPage`, and `WearMainActivity.kt` imports `world.taqwa.app.wear.qibla.QiblaPage`. The pager's call, `Page.QIBLA -> QiblaPage(state.feed, active)`, is unchanged.

## Decisions beside the plan's text

1. **"Hold your wrist level" has a line of its own, `QiblaType.level`** (15 units, cap 1.2, one line, shrinking to 11), held inside the dial's innermost marks, `QiblaType.DIAL_ROOM` = 92 − 0.95 · 11 · 1.05 ≈ 81.0 units from the middle (the Kaaba marker's halo, inside the ticks; `ref.py`'s room for `g-qibla`'s words). It is the one long caption drawn over a dial that still turns and points. In `bearing`'s line, which is the plan's one-line caption, and in `title`'s, it fitted one line at 15 in the screen's chord (147.6 units drawn, 150.2 by `tools/watchface/ttf.py`'s advances), and on the emulator at true 340 it ran across the ticks and over the "E". Now it shrinks to about 13.7 units and stays inside the ticks, and the detail keeps its place at y 155. A turning letter can still meet its end at some headings (the letters sit inside that room), and the text is drawn over it, as on the phone's dial.
2. **(Superseded in fix round 2: a two-line title grows upward, in the chord of the dial's rim 6 units in at its last line.)** **A bad state's title box is held to the round screen's chord at its last line** (`QiblaType.captionWidth`): y 155 for a two-line title (149.8 units at font scale 1.0), so a second line keeps inside the screen as the first does. `LineText` sizes a box from its first line alone, which is right for one line. A title's dial is dimmed and has no letters, so it keeps to the screen's chord, not the dial's room. "Compass needs calibrating" (200.9), "Magnetic interference" (166.1) and "Compass unavailable" (157.5) take two lines in English, and "No compass sensor" (143.4) one.
3. **(Superseded in fix round 2: the detail keeps its place under the caption's last line, y 155 at 1.0, growing with the font size; and in fix round 3, Ruling 62, it is never cut.)** **The detail moves with the title:** y 155 under one line, and 14 units below the second line under two: y 169 at the drawing's size, lower as the font size grows the title (`QiblaType.detailY`). The page knows the line count before it draws (it measures the caption at its line's largest size, which is the size `TextAutoSize` keeps whenever that fits in two lines), so the detail never flashes at 155 under a second line.
4. **No letters without a heading.** The phone's dimmed dial draws no N, E, S and W, because a dial at rest, north up, would name a north the device does not know. The watch does the same in Low, BestEffort and NoSensor, and while Searching with a heading that is not a number. The ticks and the rim stay.
5. **The hub goes with the needle**, as on the phone: no centre dot without a needle.
6. **The dial says only the turn.** The caption is a polite live region, so the dial's description (`QiblaScreen.dialSpoken`) is `spoken` only where the caption does not already say it: the turn while searching. "Facing qibla" and "Hold your wrist level" are said once, by the caption. With both nodes live and both changing at once, TalkBack would otherwise be handed the same sentence twice.
7. **Low shows no detail**, as the plan has it and as the phone's low state shows no readout. Facing the Qibla keeps the distance under "Facing qibla" (`gQibla` draws both).
8. **Before the first reading** the dial is `Searching(NaN, null)`: the bearing and the distance, no needle, no marker, no letters, so a page that has just opened shows no direction it has not been given (the same path as Ruling 58 item 1).
9. **The colour of a bad state's title** is ink, as the plan's caption is, accent only when facing. The phone draws its low titles in amber ("amber, never red"); on the watch the amber caption keeps one meaning, facing the Qibla, as the lit rim does.
10. **The accelerometer is heard only while the raw pair gives the heading**, and its last value is forgotten when it is unregistered, so a pair heard again after a distrust waits for a fresh gravity rather than fusing a stale one. The phone keeps the stale one; nothing else of its logic changes.
11. **"Not yet" is null in the policy, never 0.** The phone's `healthySince == 0L` and `degenerateSince == 0L` sentinels work on `elapsedRealtime`, which is never 0; the policy's tests run on a clock from 0, where the sentinel would restart the dwell on the second sample. So the first notice with no raw pair comes at once, as on the phone.

## Tests

| Test | Cases |
|---|---|
| `WristCompassPolicyTest` (14) | the plan's five restated behaviours (trust at 500 ms, not at 499; one real sample among lies never trusted over 5 s; a dead vector dropped at 2 s with the raw pair in charge; a trusted vector that lies distrusted at 2 s and dropped 2 s later; with no raw pair a notice at 0, 200, … 3,000 ms and never a drop), and two more: trust earned again by the same dwell after a distrust, and a trusted vector with no raw pair distrusted and then noticed. The field check's five (`aTrustedVectorStillHearsAnImplausibleField`, `aConfidentVectorInAStrongFieldIsInterference`, `interferenceWinsOverCalibration` with UNRELIABLE and NO_CONTACT, `aFieldOlderThanASecondIsNotHeld` with 1,000 ms still heard, `theFieldIsKeptForTheLog`), `withNoFieldHeardOnlyTheAccuracyCounts`, and `aDisplayTurnedHalfAroundTurnsTheHeadingBack` (ROTATION_180 is `AXIS_MINUS_X, AXIS_MINUS_Y`, and the phone's other two). |
| `TiltTest` (6) | 0°, 45°, 90° and 180°; a matrix a hair past ±1 is 0° or 180°, never NaN; the plan's 35, 41, 35, 29; the edges (40 and 30 themselves do not change it); a tilt that is not known keeps the answer. |
| `QiblaModelTest` (18) | the plan's ten (Searching at 340, tilted, Aligned, both Lows, BestEffort, NoSensor, Saudi Arabic's "٢٣°", letters and unmirrored heading, `aBadStatesTitleMayTakeTwoLines`), Ruling 58 item 1's screen test through `WatchQibla` (`aHeadingThatIsNotANumberDrawsNoNeedleAndSaysNoTurn`: 340 then NaN gives Searching, no needle or marker or letters, no turn, the caption "23°", and "Hold your wrist level" when tilted), and seven more: a left turn, no turn yet, facing while tilted, a low compass not asked to hold level, an infinite heading, and the three geometry tests (`aTwoLineTitleKeepsBothItsLinesInsideTheRoundScreen`, `theDetailMovesDownUnderATwoLineTitle`, `theTiltNoticeStaysInsideTheDialsMarksOnOneLine`). |

**Red first.** The three files written first failed to compile: `Unresolved reference 'WristCompassPolicy'`, `'remapFor'`, `'Tilt'`, `'qiblaScreen'` (Step 2's expected failure). The letters rule was added test-first after the model was green: six cases failed on the model as it was, then passed. The geometry tests failed on `Unresolved reference 'captionWidth'` and `'detailY'` first, and the tilt notice's line on `Unresolved reference 'level'`. The shell (`WristCompass`, `QiblaPage`) has no JVM test by the plan's rule (no Robolectric); its red was seen on the device (Step 5's stall) and its green there too.

**Mutants.** 28 one-line changes to the pure code, each run against the three test classes: 28 killed, none by a compile error. The policy: the field's freshness ignored or strict, the field heard only while the raw pair is active (the phone's rule), CALIBRATION before INTERFERENCE, the dwell inclusive, a lie not breaking the healthy run, a drop with no raw pair, a distrust keeping its old start, a notice on every sample, `rawPairActive` ignoring trust, ROTATION_180's x axis. The tilt: 40° inclusive, no hysteresis, no clamp, an unknown tilt resetting the level. The model: a needle on NaN, a turn on an infinite heading, the tilt over "Facing qibla", no de-duplication, a detail in Low, the left turn's phrase, letters in a bad state, the box from the first line, the detail not growing, the bearing in Western digits, the tilt notice in the screen's chord, the tilt notice as a title, the room at the ticks instead of the marker's halo.

## Checks

- `./gradlew :wearApp:testDebugUnitTest`: 305, 0 failures (267 before, 38 new).
- `:wearApp:assembleDebug` and `:wearApp:assembleRelease`; `lintDebug` and `lintRelease`: 0 errors, the same 3 old warnings (`tile_label`, `tile_description` unused until Task 8; `MissingApplicationIcon`). No compiler warning in the changed files.
- The merged release manifest holds `VIBRATE` and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone: the magnetometer, the accelerometer and the rotation vector need no permission, and nothing asks for a location. The release dex holds no `TaqwaWearQibla`; the debug dex holds it once.
- `scripts/test.sh` with the host suites and the Kotlin/Native test links named, exit 0: versions agree at 1.3.0 (39) and (100039), `test-bump-version.sh` PASS, shared host 2269, widgetcore 111, watchcore 217 (up to date: no input of theirs changed), wearApp 305, 0 failures, no `EOFException`; `:shared`, `:watchcore` and `:widgetcore` `linkDebugTestIosSimulatorArm64` built. `swift test --package-path iosApp/WatchCore`: 19 tests, 0 failures.
- **Not run (erratum 8):** the Kotlin/Native simulator test halves of a whole `scripts/test.sh` (`:shared`, `:widgetcore` and `:watchcore` `iosSimulatorArm64Test`), because Xcode 27.2's first launch is still pending (`xcodebuild -checkFirstLaunchStatus` exit 69); no `xcrun simctl` was called. `scripts/ios-build.sh` was not run: the task changes no file under `iosApp/`, `shared/` or `scripts/`.
- `git diff --check` clean.

## Step 5 on emulator-5558

Under `wear-5558`, taken at 14:35:19 after Lane W's P3.6 fix round released it, and released at 14:54:12. Shots, logs and dumps are in the session scratchpad, `p37/step5/`.

- **The state found:** the emulator off. Booted with `tools/watchface/emulator.sh`, then recorded: automatic time, TalkBack off, font scale 1.0, en-US, rotation free at 0 (`fixed-to-user-rotation` default), the app at code 100038 (installed 14:32 by the run before), its `feed.json` (the English sample, re-dated), the console's acceleration `0:9.77631:0.812349` and field `0:9.88766:-47.7452`. The found APK and `feed.json` were pulled first, to be put back.
- **The sensors** (`dumpsys sensorservice`): Goldfish's accelerometer, gyroscope and magnetometer, and AOSP's fused rotation vector (`Rotation Vector Sensor | AOSP`), as the step expects.
- **A stall, found and fixed on the device.** With the field set by the console, the heading did not move. The vector's last event came at the very moment the trusted vector let the accelerometer go (14:36:50, and again at 14:38:33.9 when the page was opened afresh, its first samples reading the new heading). "Active sensors" then listed the magnetometer alone. AOSP's rotation vector emits on accelerometer samples, and the sensor service asks for the fusion's accelerometer under the app's own connection, which `SensorManager` opens per listener. So one listener for all three sensors, which is the phone's way, turned the hardware accelerometer off with the app's own. Now each sensor has its own listener. Afterwards, on the page, the app holds two connections, the vector's and the magnetometer's, with the accelerometer let go after trust and the vector still live; off the page (Tasbeeh, or HOME) it holds none.
- **The field's formula:** x = −22·sin H, y = 22·cos H, z = −40 (45.7 µT). At H = 0 the log read `magnetic=360.0 true=333.0` (D = −27.0, read from the stored `feed.json`). At H = 90 it read 75.9 four seconds on, and 90 once settled: AOSP's fusion converges over a few seconds, so each shot waited about 15 s. Clockwise is right.
- **True 340** (magnetic 7): the log read `magnetic=7.1 true=340.1`; the needle and the marker to the right, "23°", "Makkah · 6,600 km away", and the dial's node "Turn right about 45 degrees". Against `ref.py screen=g-qibla heading=340` with its `textmask` (erratum p3 #3): 1,095 pixels differ (0.53%), on the ticks', needle's and marker's antialiased edges at 340.1 against 340.
- **True 23** (magnetic 50): `true=23.1`; "Facing qibla" in amber, the rim lit with its halo, the needle and the marker straight up. `dumpsys vibrator_manager` gained exactly one entry for `world.taqwa.app`: `Step=30ms`, usage TOUCH, finished. Against `heading=23`: 2,631 pixels (1.28%), on the lit rim's and halo's edges.
- **600 µT** (`sensor set magnetic-field 600:0:0`): "Magnetic interference" at 2.5 s, and "Compass unavailable" with "23° · 6,600 km to Makkah" at 22.5 s; dimmed, no needle, marker or letters, each title on two lines 32 px (14 units) apart and the detail at y 169 (383 px). The `TaqwaWearQibla` log showed `trusted=true` in all 28 lines, `vector=3` (HIGH), `uT=600.0` and `low=INTERFERENCE`: B6's case, heard beside a working, confident rotation vector. With the Earth's field back, the dial searched again.
- **Tilt** (`acceleration 0:6.9:6.9`): the log read `tilt=45.0`, the caption "Hold your wrist level", the needle still pointing, the dial silent (the caption says it). Back flat: the turn and "23°". The first build drew the notice on one line at 15 in the screen's chord, over the ticks and the "E": decision 1 above, then shot again.
- **The right wrist (erratum p3 #11):** `wm user-rotation lock 2` alone left `mRotation=0`, since Wear refuses a half turn by default. With `wm fixed-to-user-rotation enabled` first, the display turned (`rotation=2` in the log), and the heading read `magnetic=187.0 true=160.0`, which is H+180, with "Turn left about 135 degrees". The marker sat at the screen's lower left, which with the display turned is the physical upper right: the same physical way as before. Put back with `lock 0`, `fixed-to-user-rotation default` and `free`: `mRotation=0`, `user_rotation` 0, accelerometer rotation 1, and the live display listener turned the heading back to true 340.
- **ar-sa** (`SAMPLE=ar-sa feed.sh`): ش ق ج غ at north, east, south and west, east on the right, the dial never mirrored; the caption "٢٣°" (drawn "°٢٣" by the bidi rule: the degree sign follows the number in a right-to-left paragraph, exactly as the phone's own `Readout` draws `qibla_bearing` under its right-to-left layout), "مكة · على بُعد ٦٬٦٠٠ كم", and the dial's "استدر يمينًا حوالي ٤٥ درجة". Against `heading=340` with the mask: 632 pixels (0.31%).
- **TalkBack** (`talkback.sh on`, the page at true 340): the walk read "23°", "Makkah · 6,600 km away", the end, then "Turn right about 45 degrees" (the dial, where the walk wraps to the first item). Untouched at a steady heading, it said nothing in 8 s. Turned to true 23 untouched, TalkBack said "Turn right about 30 degrees", "Turn right about 15 degrees" as the fused heading swept in, then "Facing qibla", once. Turned back, it said "Turn right about 15 degrees", "23°" (the caption coming back), then 30 and 45. `talkback.sh off`: `enabled_accessibility_services` null, as found.
- **Not seen on the emulator:** a watch with no compass (the emulator has all three sensors; `noSensorShowsTheBearingAndTheDistanceWithNoNeedle` holds the page's side), a dead or lying vector, and the Galaxy's own vendor rotation vector: plan 5's device pass.
- **Put back as found:** the found APK (code 100038) with `install -r -d`, byte for byte, and the found `feed.json` byte for byte (`store.sh`); automatic time (never changed), TalkBack off, font scale 1.0, en-US, rotation free at 0; every setting compared with the record. The face: `store.sh` reinstalls the worktree's own face build, and this worktree's was a stale 24 October build, so the face went back as `watch-wear`'s 25 October build, whose `watchface.xml` is today's face source byte for byte (versionCode 38). The face found was 38 or older (a plain `install -r` cannot downgrade), but its exact build was not recorded before `store.sh` replaced it. The face showed Arch, amber, the wearer's slots empty. Then `adb -s emulator-5558 emu kill`, as found off, and the lock released.

## Errata and rulings

1. **Erratum 1** (fast-forward first): done, already up to date at `3e309b78`.
2. **Ruling 58 item 1** (a heading that is not finite): no needle, no marker, no turn spoken, the Searching caption (the bearing, or "Hold your wrist level" while tilted); `aHeadingThatIsNotANumberDrawsNoNeedleAndSaysNoTurn` pins it through `WatchQibla`, and `anInfiniteHeadingIsNoHeadingEvenWithATurn` beside it. **Item 2** (the 180 flip stays): `QiblaGuidanceTracker` is untouched; the page speaks whatever turn it is given.
3. **Erratum 3** (Step 5 on emulator-5558 under wear-5558, put back as found, released at once): taken at 14:35:19 after a logged wait, released at 14:54:12, the device put back as above, the face to its current build.
4. **Errata p3** (`plan-errata-2026-10-24.json`): #3 (the drawings are English and 24-hour): the Arabic shot is compared by its geometry, its words masked (`ref.py textmask=`, `compare.py mask=`). #11 (the right wrist): Step 5 read H+180 with the display turned, the marker the same physical way, and the plan's Step 5 says so now, with the `fixed-to-user-rotation` the emulator needs. #12 (Ruling 58 item 1): as item 2 above. The others are Tasks 4–6 and 8–9's.
5. **Rulings 39, 41, 42, 43, 50, 52–58.** Ruling 39: the watch is never told where it is. The page reads the feed's city-level figures alone (the whole-degree bearing, the phone's distance string, the whole-degree declination); `:wearApp` declares no location permission and has no location code; nothing is sent from the watch; the privacy promise "Only your own devices can see your location." and its strings are untouched. Rulings 41–43: no file under `watchface/` or `complication/` changed. Rulings 50 and 52–57 govern the feed, the link and the Swift port, none of which this task touches.
6. **WIP commits:** three green WIPs (`1db864bf`, `6869362e`, `efb38682`), folded into the task's commit. No red step was committed.
7. **The Gradle flake:** not seen.

## For the Lead

- **The phone's compass source may stall the same way.** `AndroidCompassSource` registers the rotation vector, the accelerometer and the magnetometer on one listener, and once the vector is trusted unregisters the other two from it. On a phone whose rotation vector is AOSP's fusion rather than the vendor's, that turns the fusion's accelerometer and magnetometer off with the app's own, and the needle would freeze where it was, with no low state (no degenerate sample arrives: none arrives at all). Seen here on the Wear emulator only, and not checked on any phone: the phone's Qibla points on the S23, so the S23's vector does not stall this way, and the iPhone reads Core Location, another path. A one-listener-per-sensor change in `:shared` is Dev 1's or a phone task's, not this one's.
- **`store.sh` installs the worktree's own face build**, which can be stale in a lane worktree. The plan's as-built note says to build the face first.

- **Merging beside P3.6.** `git merge-tree` against `watch-wear` (0857cf2f) conflicts in `Pages.kt` alone: P3.6 removes the Tasbeeh stub and this task the Qibla stub, adjacent lines. Keep neither. After both, `Pages.kt`'s imports of `WatchFeed` and `words` are unused (a warning only). `WearMainActivity.kt` (its new import sits after `today.TodayPage`, away from P3.6's import block) and the plan merge clean. Against `watch-companion` the merge is clean.

## Fix round 1 (30 October, review round 1's two Importants)

On `watch-feed` from `d7b56e07`. Both reviews' Importants are fixed below. Their minors (9 from the code lens, 7 from the spec lens) were not passed into this round and stay open as the reviewers wrote them (ledger).

### 1. TalkBack never said the change from Low to BestEffort

- **The cause, from TalkBack's own log on emulator-5558:**
  - A change of a text's words alone reached TalkBack as a content change of undefined type, which it did not say for the live region (review round 2 found that event in Compose 1.12.1's bytecode; this line first said that none is sent). The caption's earlier announcements came as `CONTENT_CHANGE_TYPE_SUBTREE` events, from a change in its size: "23°" on one line becoming a title on two, or a change of box.
  - "Magnetic interference" to "Compass unavailable" is two lines in the same box, so no event came and nothing was said. A detail node that appeared beside it was not a live region, so its events were silent too.
  - I first kept the detail composed in every state. That alone made TalkBack say the detail and still not the title.
  - Then a content description on the caption in every state made the title heard. But it lost the "23°" that a pointing dial's caption says as the wrist leaves the Qibla, 2 of 2. TalkBack queues a live region's description with `ttsClearQueueGroup=5`, the group the dial's turns use, and the next turn cleared it. The original build says it, 2 of 2 in an A/B run.
  - A semantics key of the page's own, meant to send a plain change event, sent none (its log), and was taken out.
- **The fix:**
  - A dimmed dial's caption (Low, BestEffort, NoSensor) has a content description, `QiblaScreen.captionDescription`. It is the title, and in BestEffort and NoSensor the title with the bearing and the distance after it, joined by `ComplicationFields.spokenSeparator` (as Today joins its lines). A dimmed dial says no turn, so nothing clears it.
  - A pointing dial's caption has none. It is heard by its change of size, as before: "23°", "Facing qibla" and "Hold your wrist level" are three sizes in three boxes.
  - The detail is composed in every state. It is hidden from a screen reader where it is empty (Low) or said with the caption (`QiblaScreen.detailRead`).
  - The English drawings are unchanged. Shots of the original build (64cfead9, the reviewer's) against the fix at true 340, tilted, Low, BestEffort and back: 0 differing pixels in the static states, and at most 21 on the Kaaba marker's edge from heading noise.
- **Heard on emulator-5558, final build (this fix's code, the APK 06156bbd), 2 of 2:**
  - 600 µT: "Magnetic interference" at about 2 s, then "Compass unavailable, 23° · 6,600 km to Makkah" at about 21 s.
  - The Earth's field back: "Turn right about 45 degrees", "23°".
  - Turning to true 23: "Turn right about 30 degrees", then 15, then "Facing qibla".
  - Turning back to 340: 15, "23°", 30, 45, as the original build says them.
  - Tilt: "Hold your wrist level"; flat again: the turn, then "23°".
- **The walks:**
  - Searching: "23°", "Makkah · 6,600 km away", the end, the turn.
  - Low: "Magnetic interference" alone, with no empty stop.
  - BestEffort: one item, "Compass unavailable, 23° · 6,600 km to Makkah".
  - Untouched for 8 s: nothing said.
- **Not driven on the emulator:** "Compass needs calibrating" (the console cannot lower a sensor's accuracy) and NoSensor. Low by calibration to Low by interference is a description change of the same kind as Low to BestEffort, and `aDimmedDialsCaptionIsDescribedSoEveryChangeOfItIsSaid` holds the model's side.
- **For the harness:** do not run `uiautomator dump` while TalkBack is on. It pauses TalkBack, and when TalkBack comes back it raises its phone-access prompt again. That prompt covered the page in two runs here.

### 2. Qibla lines cut by an ellipsis in shipped languages

- **(a) The tilt notice, fixed:**
  - `QiblaType.captionBox` measures the notice at its floor, 11, on one line in its one-line box. If it fits there it stays on one line, so English is still about 13.5 on one line.
  - If it does not fit, it takes `LEVEL_LINES` = 2, growing upward: the last line at y 141 and the first 14 above it at y 127 (14 × the font-size growth), in the same box, because the last line still comes nearest the rim. The distance keeps y 155.
  - Why upward: the first build of this fix put the second line at y 155 and the distance at y 169. On emulator-5558 that ran the distance across the ticks and over the "S" of the dial that still turns under it. A title's dial is dimmed and has no letters, so a title keeps growing downward.
  - On the emulator, Indonesian shows "Jaga pergelangan" / "tetap datar" at 15, and "Makkah · 6.600 km dari sini" stays whole at y 155.
- **(b) The French BestEffort and NoSensor detail, left as the Lead's call** (as the review asks):
  - "23° · 6 600 km jusqu’à La Mecque" is drawn "23° · 6 600 km jusqu’à La …" at font scale 1.0 and 1.3.
  - By HarfBuzz in the app's Manrope SemiBold it is 154.8 units at the detail's floor of 10, under French's two-line titles at y 169: 127.8 units at 1.0, and 120.2 at 1.3 (y 171.8).
  - The choices: a floor of about 8.2 at 1.0 (lower at 1.3), two lines, or a shorter French string at native review.
- **The measurement** (scratch tool `qibla_fit.py`, HarfBuzz `hb-shape` in `wearApp/src/main/res/font`, with the boxes of `QiblaModel.kt`; Latin scripts only):

  | | tilt notice at 1.0 | its distance | BestEffort title | BestEffort detail at 1.0 / 1.3 |
  |---|---|---|---|---|
  | en | 1 line, 13.5 | 113.3 in 151.6 | 2 lines, 15 | 117.5 in 127.8 / 120.2 |
  | fr | 1 line, 11.5 | 107.9 in 151.6 | 2 lines, 15 | **154.8** in 127.8 / 120.2: cut |
  | tr | 1 line, 14.0 | 115.6 in 151.6 | 2 lines, 15 | 113.5 in 127.8 / 120.2 |
  | id | **2 lines, 15** (was cut) | 126.3 in 151.6 | 2 lines, 15 | 118.5 in 127.8 / 120.2 |

  The detail shrinks from 10 × the font size (cap 1.3) back to 10, so English at 1.3 fits at about 10.1. That was seen on the emulator.
- **Seen whole on emulator-5558** from a scratch build (a git-archive copy with French, Indonesian, Urdu and Bengali samples, not committed): the tilt notice, Low and BestEffort in fr, id, ur, bn and ar-sa, the French detail excepted.
- **Not seen:** Arabic-script titles on two lines (the title's 14-unit pitch at 15 units). Arabic and Urdu take one line in the two states shot. "Compass needs calibrating" and "No compass sensor" are likely to take two in Urdu and Arabic. Both items are in plan 3 Task 7's as-built note "For Task 9's matrix and plan 5".

### Tests and checks

- `QiblaModelTest`, 24 tests (18 before):
  - the tilt notice keeping one line when it fits at its floor, asked once at 11;
  - taking two lines upward when it does not, at 1.0 and 1.3, on a round and a square screen, the detail staying at y 155;
  - every other caption's box, never measured;
  - a pointing dial's caption with no description and its detail read;
  - a dimmed dial's caption described, for both low reasons;
  - BestEffort and NoSensor said title first, with the right-to-left comma in Saudi Arabic.
- **Red first:** each compiled red on `captionBox`, `LEVEL_LINES`, `captionSpoken` (later `captionDescription`) and `CaptionBox.y`.
- **Mutants:** 13 one-line mutants of the new code, all killed.
- **Checks:**
  - `./gradlew :wearApp:testDebugUnitTest`: 311, 0 failures.
  - `scripts/test.sh` with the host suites and the Kotlin/Native test links named, exit 0: versions agree, bump PASS; shared host 2269, widgetcore 111, watchcore 217, wearApp 311.
  - `assembleDebug` and `assembleRelease` (unsigned, no release key); `lintDebug` and `lintRelease`: 0 errors, the same 3 warnings.
  - The merged debug and release manifests: `VIBRATE` and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone. No `TaqwaWearQibla` in the release dex.
  - i18n clean, `git diff --check` clean.
  - Not run: the Kotlin/Native simulator halves (Ruling 59).
- **Devices:** `emulator-5558` under `wear-5558`, 15:49 to 16:35 and 16:50 to 16:57, found off both times, put back as found and stopped by its serial. The plugged-in phone and the work iPhone 12 were not touched.
- **Rulings 39, 41, 42 and 43 hold:**
  - The watch still reads only the feed's whole-degree bearing, the phone's distance string and the declination.
  - It still asks for no location.
  - No face or complication file changed.
  - The one complication symbol used, `ComplicationFields.spokenSeparator`, is read and not changed.

## Fix round 2 (30 October, review round 2's two Importants)

On `watch-feed` from `74738c19`.

### 1. A two-line title's glyphs joined across its lines, in English (fixed)

- **The cause.** The title's two lines stood `TITLE_PITCH` = 14 units apart at 15 (0.933 of the size). Manrope ExtraBold's ink reaches 0.81 of the size above the baseline (the grave of "à") and 0.255 below it (the tail of "g"), 1.065 in all. So "Magnetic" / "interference" joined the g to the f, as the reviewer's shot of 74738c19 shows, and "Compass needs" / "calibrating" joined the p to the l in a raster model (−0.5 units at 10 px a unit, the reviewer's), which the emulator's console cannot show. A raster model in HarfBuzz/raqm (Manrope, and layoutlib's Noto Naskh UI and elegant and Noto Sans Bengali) reproduced the reviewer's figures: English −0.6, Arabic NoSensor +0.6.
- **The pitch is 16** (1.067 of the size). No glyph of one line can then meet a glyph of the other, wherever the text breaks.
- **The reviewer's plain fix had no room at font size 1.3.** The model ran every state in all seven languages at 1.0, 1.15 and 1.3:
  - With the title still growing downward and the detail 14 under its second line, the detail goes to y 171 (174.2 at 1.3). The English and Indonesian BestEffort details were then cut at 1.3: 117.5 and 118.5 units in a 114.5-unit box.
  - With the detail kept where it was, y 169 (171.8 at 1.3), the second line met it at 1.3 in French, Turkish, Indonesian and Arabic.
- **So a title of two lines now grows upward, as the tilt notice does.**
  - Its last line keeps y 141 and its first stands 16 above it, at y 125.
  - The detail keeps its place under the last line.
  - The box is the chord of the dial's rim at the last line's far edge, 6 units in: 148.8 units at 1.0, where the old box was 149.8. So every shipped title breaks at 1.0 where rounds 1 and 2 saw it break.
- **The detail now grows away from the caption with the font size.** It sits 14 units under the caption's last line, grown as the faster of the two lines grows: y 155 at 1.0, unchanged, and y 159.2 at 1.3.
  - Held at y 155, the model found the lines meeting at 1.3. That was already so before this round: Bengali's "কিবলামুখী" over its distance (−1.8 units), Arabic's BestEffort title (−1.0 in the compact Naskh, −3.2 in the elegant), and the Arabic and Urdu tilt notices (−0.7 to −2.5).
  - The fixed y also cut Urdu's BestEffort and NoSensor details at 1.3, under their two-line titles (137.7 units in 120.2). They are whole now.
- **The guard:** `QiblaInkTest`, a JVM test that reads the app's Manrope through `java.awt`. Its ink agrees with HarfBuzz: 0.81 above and 0.255 below.
  - It reads every character the phone sends in these lines: `:shared`'s strings in each `values*` folder and `WatchWords.holdLevel`, now Gradle test inputs, plus every digit.
  - It holds `TITLE_PITCH` against the highest and lowest ink of those characters. 14 and 15 fail it.
  - It holds the detail's gap against the caption's lowest ink and the detail's highest at font sizes 0.85 to 2. A fixed y 155 fails it from 1.2.
  - Arabic and Bengali letters are drawn in the watch's own fonts, which a host test cannot read. They are measured by the model and the shots.
- **Model, after the fix, every state and language:**
  - lines at least 1.4 units apart at 1.0 (English's g over f), 1.5 at 1.15 and 1.7 at 1.3, in the compact Naskh; 2.6, 3.1 and 3.3 in the elegant;
  - every detail at least 1.5 units clear of its caption in the compact Naskh, at all three sizes;
  - in the elegant Naskh, the one close case is Urdu's tilt notice over its distance at 1.0, −0.1 units. It is unchanged by this round, and clear on the emulator, which draws the compact Naskh.

### 2. The French BestEffort and NoSensor detail (open, the Lead's call; ruled since by Ruling 62 and built in fix round 3)

- No ruling is in the ledger. The plan's `detail` = `Line(10f, 1.3f)` and the phone's `prayer_qibla_detail` stand, so the detail is still cut.
- Item 1's change moved the detail up to y 155 at 1.0. It now draws "23° · 6 600 km jusqu’à La Mecq…", where it drew "…La …". It is 155.8 units at 10 in a 151.6-unit box at 1.0, 148.0 at 1.15, and 144.2 at 1.3 (y 159.2).
- **The choices for the Lead:**
  - a floor of about 9.7 at 1.0, and 9.25 at 1.3 (9.0 holds both);
  - two lines;
  - a shorter French string at native review. "23° · 6 600 km de La Mecque" is 133.3 units and fits at every size; "23° · La Mecque à 6 600 km" is 127.0.
  - Any of them needs room for a longer bearing or distance than Cape Town's.

### Seen on emulator-5558

- **Builds:** the fix's debug build (5f9de5d8), and a scratch build of it (a git-archive copy, not committed). The scratch build carries the French, Indonesian, Urdu and Bengali samples, and four hand-made samples that put "Compass needs calibrating" and "No compass sensor" in the interference and BestEffort slots, in English, French, Arabic and Urdu. Shots were taken at font size 1.0 and 1.3, and the lock was held 17:46 to 18:11.
- **English:**
  - "Magnetic" / "interference": no lit pixel joins the two lines. The least gap is 3 px (1.3 units), g over f. The reviewer's shot has one lit group that joins the g and the f.
  - "Compass needs" / "calibrating" and "Compass" / "unavailable": whole and apart.
  - The searching dial's caption and distance at 1.0 are pixel for pixel the reviewer's shot of 74738c19.
  - At 1.3, every state is whole: Searching, the tilt notice, Low, BestEffort with its detail at y 159.2, and Facing.
- **Other languages:**
  - Whole at 1.0 and 1.3, every detail clear of its caption: the French, Indonesian, Arabic, Urdu and Bengali two-line titles (Urdu "قطب نما کو درست کرنے" / "کی ضرورت ہے", Arabic "لا توجد بوصلة في هذا" / "الجهاز"), and the Indonesian two-line tilt notice.
  - At 1.3, Bengali's "কিবলামুখী" stands clear of its distance, and so do Arabic's BestEffort title and the Arabic and Urdu tilt notices.
  - The French detail is cut, as above.
- **Not heard with TalkBack this round.** No semantics changed, only where the caption sits.
- **Put back as found, then stopped by its serial:**
  - the app byte-equal (2b2d77c9);
  - the English sample stored again, so the face's slots follow (feed 0b3f92ef byte-equal);
  - the face on Arch in English, and font size 1.0;
  - system_locales unset again (the font-size change had written en-US);
  - the console's sensors as found.

### Tests and checks

- `QiblaModelTest`, 25 tests:
  - the title's box inside the rim, at 1.0 and 1.3, on any screen;
  - a title's lines asked once at its largest size, growing upward, with 1, 2 and 3 lines and at 1.3;
  - the tilt notice's first line 16 above;
  - a one-line caption never measured;
  - the detail at y 155 under every caption at 1.0, growing with the font size to 1.3 and stopping there.
- `QiblaInkTest`, 3 tests.
- **Red first:** 9 one-line mutants of the new code, all killed. The pitch at 14 or at 15, and a fixed y 155, fail `QiblaInkTest`. Every other mutant fails `QiblaModelTest`.
- **Checks:**
  - `./gradlew :wearApp:testDebugUnitTest --rerun-tasks`: 315, 0 failures.
  - `scripts/test.sh` with the host suites and the Kotlin/Native test links named, exit 0: versions agree, bump PASS; shared host 2269, widgetcore 111, watchcore 217, wearApp 315.
  - `swift test`: 19, 0 failures.
  - `assembleDebug` and `assembleRelease` (unsigned, no release key); `lintDebug` and `lintRelease`: 0 errors, the same 3 warnings.
  - The merged manifests: `VIBRATE` and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone. `TaqwaWearQibla` appears once in the debug dex and never in the release dex.
  - i18n clean, `git diff --check` clean.
  - Not run: the Kotlin/Native simulator halves (Ruling 59; `checkFirstLaunchStatus` 69; no shared or watchcore source changed).
- **Merge:** `git merge-tree` with `watch-companion` (71dc53d7) conflicts in `Pages.kt` alone, the known stub conflict (keep neither); `wearApp/build.gradle.kts` merges clean. That resolution, built in a scratch copy, gives wearApp 390, 0 failures.
- **Also fixed:** the round-2 code-lens minor that the "Compose sends no event" cause was misstated, in `QiblaPage.kt`, `QiblaModelTest.kt`, the plan's note and this report.
- **Rulings 39, 41, 42 and 43 hold:** nothing the watch reads or asks for changed, and no face or complication file changed.

## Fix round 3 (30 October, review round 3's Important: Ruling 62)

On `watch-feed` from `92e2fd6a`.

### The detail line is never cut

- **The finding.** On 92e2fd6a the French BestEffort and NoSensor detail was drawn "23° · 6 600 km jusqu’à La Mecq…" at 1.0 and "…La Mec…" at 1.3. Ruling 62 (the Lead, 30 October about 18:45) makes a cut detail an Important. It orders the remedies: the line's size where it fits, then a shrink to 9 on that line, then a second line, growing down, never onto the ticks. The guard must hold the worst real figures.
- **Built** (`QiblaType.detailBox`; `detail` = `Line(10f, 1.3f, maxLines = 2, floor = 9f)`; the page draws the detail in the box and at the size found, measured as the caption is measured):
  - one line at 10 grown to 1.3, in the round screen's chord at its line, the box `LineText` gave it;
  - else that line shrinks by half a unit to 9;
  - else two lines, at the largest size two lines fit, the first in fix round 2's place under the caption, the second `DETAIL_PITCH` (16/15 of the size) under it. Their box is the chord of `TICK_ROOM` (92 − 9 · 1.05 units from the middle, where the ticks begin) at the second line's descent, 84.1 units at 1.3 at 9, so no glyph reaches a tick or the rim.
- **The worst real figures:** every whole-degree bearing, of which the widest are measured, and every five-figure distance the phone's two-figure rounding gives (Ruling 39), up to 20,000 km. Tureia, in French Polynesia, is about 19,800 km from Mecca, which rounds to 20,000. Each is grouped with any of the phone's marks.
- **The guard.** `QiblaInkTest.theWorstRealFiguresAreNeverCut` runs them in Manrope's own advances (Java2D, kerned) through `detailBox`, for English, French, Turkish and Indonesian, at font sizes 0.85 to 1.3 and 2, under the captions each is drawn under, and again measured 3 % wider. None is cut, none is under 9, and no distance takes two lines. `frenchsWorstFiguresShrinkOnOneLineAtTheDrawingsSizeAndTakeTwoAboveIt` holds the ruling's case: one line at 9 at 1.0, then two lines at 1.15 and 1.3. `aDetailsTwoLinesStandFurtherApartThanItsGlyphsReach` holds the pitch against SemiBold's ink (0.795 above, 0.24 below).
- **The model, for Arabic, Urdu and Bengali** (HarfBuzz, layoutlib's Noto Naskh Arabic UI and elegant and Noto Sans Bengali UI, by font run; both digit sets; nine font sizes): no detail or distance is cut or takes two lines. Urdu's BestEffort detail shrinks to 9.5 from 1.24. The others keep their full size.
- **Seen on emulator-5558:**
  - The setup: a scratch debug build of this fix's code with hand-made worst-figure samples (85f173e4, not committed), under `wear-5558`, 19:16 to 19:33. The emulator was found off, put back as found, and stopped by its serial.
  - French "359° · 19 000 km jusqu’à La Mecque": whole on one line at 1.0 and at 1.15 (the device measures it a little narrower than the models), and on two lines at 1.3, "359° · 19 000 km" over "jusqu’à La Mecque", inside the ticks.
  - French "200° · 20 000 km": one line at 1.0, two at 1.15 and 1.3.
  - The NoSensor title in the BestEffort slot: one line at 1.0, two at 1.3.
  - English, Indonesian, Urdu, Bengali and Saudi Arabic with the worst figures, and the Searching distances: whole on one line.
  - The accessibility tree: unchanged. BestEffort's caption says the title, then the whole detail.
- **Left (minor, for the Lead and Task 9's device pass):** a one-line detail at its box's full width reaches the rim at its ends, 90 to 92.5 units from the middle against the rim's 92. This is the screen-chord box that Ruling 62 item 2(b) keeps, the same as review round 3 saw at 1.3 on 74738c19. A box inside the rim would change shipped details at the drawing's size.

### Tests and checks

- `QiblaModelTest` 30 tests (5 new: the size where it fits, the shrink to 9 and its steps, two lines inside the ticks, two lines as large as they fit, the floor) and `QiblaInkTest` 6 (3 new).
- **Red first:** the new tests failed to compile against 92e2fd6a (no `detailBox`, `DetailBox` or `DETAIL_PITCH`). Five mutants of the new code were all killed:
  - one line only: `QiblaInkTest` found French cut;
  - a pitch of 1.0: the ink test;
  - no shrink: the English distance on two lines at 1.2, and the French case;
  - the screen's chord for two lines: `QiblaModelTest`;
  - a floor of 8: both.
- **Checks:**
  - `./gradlew :wearApp:testDebugUnitTest`: 323, 0 failures.
  - `assembleDebug` and `assembleRelease` (unsigned, no release key).
  - `lintDebug` and `lintRelease`: 0 errors, the same 3 warnings.
  - `scripts/test.sh` whole: in the ledger line for this round.
- **Rulings 39, 41, 42 and 43 hold.** The watch reads the same feed figures and asks for no location, and the privacy promise "Only your own devices can see your location." is untouched. No face or complication file changed.
