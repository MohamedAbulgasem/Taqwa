# Plan 5 Task 12: the watch's emulator gates

Lane W, on `watch-wear`. Each step has its own section. Step 2 passed (Ruling 78). Step 4 times the
Tile's refresh, puts the devices back and closes the report, with what the gates proved and what goes
to Task 13.

## Step 0: the observation and the compass made readable, and the Tile's preview

6 November 2026, base `f601d14d` (`git merge --ff-only watch-companion` from `b358b204`). The
evidence is in `2026-10-24-watch-gates/step-0/`.

### What was built

- **The sources' line.** `timelineLogLine(source, type, steps, lastUntil)`
  (`complication/ComplicationSources.kt`) replaces each source's old line at `Log.d` under
  `TaqwaComplications`: `next prayer, RANGED_VALUE: 13 steps, last until 2026-11-08T14:34:00Z`.
  - `last until` is where the timeline stops saying anything, `saysUntil()`: the end of the plan's
    last step with fields. That is the empty step's start, not its own end a month later, which is
    never drawn. A plan that is only the empty step reads `last until none`.
  - NextPrayer and Dates name the type they were asked for, as before. Day and the day dial serve
    one type and name none, as their old lines did. One step reads `1 step`.
- **Why the sources were asked again.** `ComplicationSources.requestUpdates(context, why = "unknown")`
  logs `asked again (<why>)` at `Log.i` once every source has been asked. One that cannot be asked
  keeps its `Log.w` line, and the `Log.i` line is not written. The callers pass:
  - `feed`: the feed store's `onStored`;
  - `app start`: `TaqwaWearApp.onAppOpened`;
  - `tile`: the Tile's `askComplicationsIfDue`;
  - `daily`: `DailyNudge.nudge`;
  - `debug`: the debug build's `DebugFeedReceiver`, its rebind and its clear. The plan names no word
    for it, and `unknown` would say less.
- **The compass on request.**
  - `QIBLA_TAG = "TaqwaWearQibla"`.
  - `qiblaLogOn(debugBuild, tagEnabled)` is true when either is. `WristCompass` reads it once
    each time `readings` starts being collected, with `Log.isLoggable(QIBLA_TAG, Log.DEBUG)` and
    `QiblaLog.IN_DEBUG_BUILD`. That constant is `true` in `src/debug` and `false` in `src/release`,
    which the staging build also takes. Plan 3's debug-only log seam stays the switch, now read as
    a value.
  - `qiblaLogLine(...)` is the line once a second:
    `rv 3, mag 3, field 48.8 µT, trusted, raw pair off, tilt 85°, rotation 0, heading 0°`. Degrees
    are whole, digits are Western whatever the watch's language, and a value not yet heard, or not
    a number, is `none`.
  - **The heading is the magnetic one,** remapped for the display's rotation, before the feed's
    declination is added. The line takes no Qibla bearing, no declination and no place (Ruling 39).
    The old debug line logged the magnetic and the true heading side by side, and their difference
    was the declination, the place's figure. One heading answers every Galaxy check in Task 13:
    the right wrist against the left, the tilt switch and which source drove the heading. The
    magnetic one also matches the console's `H` on the emulator, as plan 3's formula check uses it.
  - The old line's `low=` reason is not in the plan's line. The trust, the field and the two
    accuracies show it.
  - The Qibla page's layout lines (the detail it draws, with the bearing and the distance) stay
    debug-only through `QiblaLog.line`, a no-op in release and staging. So a store build logs the
    compass's line alone, even with the tag enabled.
- **Tests, red first.** `TimelineLogTest` has 2 tests: the plan's example, the type-less form and
  `none`, and where the timeline ends. `QiblaLogTest` has 3: the four cases of `qiblaLogOn` and the
  tag's name; the example and the nulls; and whole degrees with Western digits under `ar-EG`,
  359.6° reading 0° and a heading that is not a number reading `none`. Each test was first run
  against stubs with the right signatures and wrong values: 5 of 5 failed on their assertions.

### Checks

- `./gradlew :wearApp:testDebugUnitTest`: 591 tests, 0 failures. Before this step there were 586.
- `:wearApp:assembleStaging -Ptaqwa.unsignedRelease=true` and `:wearApp:assembleDebug` build.
- `:wearApp:lintDebug`: "No issues found".
- `tools/wear-release-check.sh staging` passes, at 4,979,189 B. It finds:
  - no `DEBUG_FEED`, `SampleFeed` or sample feed;
  - no sample feed or test vector among the APK's files (Ruling 55 item 6);
  - the permissions exactly as `wearApp/permissions.txt` lists them;
  - an APK that is not debuggable, with `wear-sdk` not required.
- The staging dex holds `asked again (`, `last until`, `TaqwaWearQibla` and `raw pair`, each once.

### On emulator-5558, staging over the debug build

The device step held `wear-5558`, then `phone-5562` for the phone's feed.

1. **A feed stored, and both lines** (`step-0/logs.txt` section 1).
   - The found watch held the phone's feed with its delivery. The delivery file was removed through
     the debug build's `run-as`, so the staging build's start would heal by a handshake, as
     Ruling 50 does for a watch without one.
   - Then `install -r` of staging. The paired phone logged `TaqwaWatch WATCH_ASKED SENT 59ms` and
     `ACK 84`.
   - The watch logged `asked again (feed)`, then a line for each source the face shows:
     `next prayer, RANGED_VALUE: 13 steps, last until 2026-11-08T14:34:00Z`, LONG_TEXT the same,
     `day: 13 steps`, `day dial: 13 steps`, and `dates, LONG_TEXT: 4 steps` and SHORT_TEXT. All
     end at the same instant, the next-prayer horizon.
   - Each fresh open logged `asked again (app start)`. So R8 kept both lines.
2. **The compass on request** (`step-0/logs.txt` section 2). Qibla was opened afresh each time with
   `tools/watchface/app.sh qibla`.
   - Tag unset, as found: 0 lines in 6 s.
   - After `setprop log.tag.TaqwaWearQibla DEBUG` and a fresh open: 8 lines, one a second. The first
     came before any sample, with nothing heard yet:
     `rv 3, mag none, field none, not trusted, raw pair on, tilt none, rotation 0, heading none`.
     The rest were `rv 3, mag 3, field 48.8 µT, trusted, raw pair off, tilt 85°, rotation 0,
     heading 0°`, from the console's resting sensors.
   - After `setprop … INFO` and a fresh open: 0 lines in the 8 s after the open.
   - A first INFO window counted one line. It was stamped 0.17 s before that open's
     `asked again (app start)`: the last line of the DEBUG round's collection, before the
     activity was cleared. The second window, above, starts after the open.
   - The property was then set back to empty, as found.
3. **The Tile's preview** (`step-0/tile-1458-staging.png`). The shot was taken as plan 3 Task 8
   Step 5 shot it, then compared with the committed preview:
   - The debug build stored the English sample for the mock day (`clock.sh 14:58`, `feed.sh 14:58`)
     and wrote `refresh.asked` at now. Then staging went on over it with `install -r`, and its
     start sent no handshake. The phone's log shows no ask between 18:03:26 and the restore.
   - Add the Tile, wait 22 s, then `tile_shot` found "Cape Town" at the first try (14:58:48 on the
     watch).
   - Full screen, 454 × 454, as the preview is. Against `wearApp/src/main/res/drawable-nodpi/tile_preview.png`,
     `compare.py` counts 0 differing pixels. An exact count at 0 levels finds 0 pixels that differ
     at all, the largest channel difference being 0.
   - **The file is not replaced:** Rulings 65 and 68 changed the Tile's timeline, not its look at
     14:58.
   - **The Tile picker shows it** (`step-0/tile-picker.png`). With the Tile removed, the Timer
     tile's Tile Management opens "Add", whose "Prayer times" entry draws this preview. The
     list leaves out a Tile that is already added. Nothing was added there.

### Ruling 71 item 5: the face at the same version code

Every `install -r` at code 41 had Watch Face Push replace the face in Taqwa's slot at the same code
(`step-0/logs.txt` section 3). That was 5 of 5: staging, debug, staging, debug, then the found app.
Each time the face's `lastUpdateTime` moved to the install's minute:

- the found face `f13548e6` became `e35e76f5`, this tree's debug face, on the first staging
  install;
- it stayed `e35e76f5` (the same bytes, replaced again) through the next three;
- it went back to `f13548e6`, the found app's bundled face, when `restore.sh` reinstalled the found
  app.

The face stayed active through staging's first install (`isActive true` at once). After a debug
install it read `isActive false` for a while and published `taqwa_face_addable`, as `restore.sh`'s
notes describe. The restore's starts brought it back to "seen 41 active".

The third time reads 18 October because the clock was on the mock day.

### Put back as found

- The watch went back through `pullfound.sh`/`restore.sh` (the wear-app-pass drivers), with the
  debug build put on first so that `restore.sh` could stamp it.
- `restore.sh` reported one difference: `auto_time_zone`. It sets 1, and the found watch had 0. It
  was set back to 0.
- After that, `state.sh` matches the record on every line:
  - app `109521c0` (100041), face 41 `f13548e6` active, the phone's feed `183d1d92` with its
    delivery;
  - automatic time, font scale 1.0, TalkBack off, en-US, `system_locales` null, no Tile.
- Every file of the app's own data is the same byte for byte. No handshake or feed came while
  `restore.sh` ran.
- `log.tag.TaqwaWearQibla` is empty again. `emulator-5558` and `emulator-5562` are left running and
  paired.
- One thing the restore cannot reach: the phone recorded sending delivery 84, and the watch holds
  the found delivery 68. The watch's next due handshake echoes 68, and the phone sends again
  (Ruling 50's heal).

### Notes for the Lead

1. The `last until` instant is a prayer's start. It is a time, not a place, and only the watch's
   own log holds it. The 12-hour observation needs it.
2. A partial failure of `requestUpdates` writes no `asked again` line, only the failing source's
   `Log.w`. The observation reads an `asked again` line as "every source was asked".
3. The P2.7 round-2 minors that Ruling 70 item 3 carries to P5.12 (the pairing scripts' README,
   `test-pairing-scripts.sh`) belong with the steps that use those scripts (1 and 3). Step 0
   neither runs nor changes them.

## Step 1: Wear OS 4 and Wear OS 3 on the emulators

6 November 2026, on `watch-wear` at `ad120273` (`git merge --ff-only watch-companion`: already up
to date). Wear OS 4 on `emulator-5566` from 19:03 to 19:19, then Wear OS 3 on `emulator-5568` from
19:20 to 19:41, one at a time, each under its lock (`wear-5566`, `wear-5568`). The app is the debug
Wear app built from this worktree (`wearApp-debug.apk`, 1.5.0 (100041), SHA-256 `6672fa0bd2364071…`),
which holds the sample feed, installed with `adb -s <serial> install -r`. The shots and the lines
cited are in `2026-10-24-watch-gates/step-1/` (`logs.txt`); the full captures stay in the session
scratchpad.

### The emulators

- **`scripts/wear-old-emulator.sh <30|33>`** creates `Taqwa_Wear_OS_4` (API 33, port 5566) or
  `Taqwa_Wear_OS_3` (API 30, port 5568) the first time, with the images the plan names
  (`android-wear`, arm64-v8a) and the Wear OS Large Round profile (454 × 454, 320 dpi, round), as
  `Taqwa_Wear_OS_6` is. It boots headless and waits for `sys.boot_completed`. It never downloads an
  image, and it leaves alone a port another AVD holds.
- **It also puts each watch on its own** (see "For the Lead" 1). As soon as adbd answers, it makes
  adbd root (both images are userdebug). Once netd has laid out its chains, it has the watch refuse
  every new TCP connection and all UDP off the loopback, in IPv4 and IPv6. It checks again after
  boot, and fails if a link to port 5601 is open or Play services reaches any Wear node. Nothing in
  this step needs the network; adb reaches the emulator through its own pipe.
- **Tested by use:**
  - both AVDs created and booted, in 13 s and 18 s;
  - the "already running" path on 5566, which isolated it and passed its own check;
  - the usage errors: no argument, `31`, and a bad flag each exit 2;
  - on 5568, isolated from its first boot: 292 SYNs and 628 UDP packets refused. Port 5601 was
    never reached, and "no reachable nodes" held at boot and at the end.
- **The images:** API 33 is `TWR7.230913.001.E7` (Wear OS 4) and API 30 is `RWDA.230114.008.S4`
  (Wear OS 3). Both run in en-US, at font scale 1.0, in Africa/Johannesburg, the sample's zone. Both
  were taken off their virtual charger.
- **`wear-sdk`:** API 33's system lists it among its libraries, and API 30's does not. The
  installed app declares it optional on both (erratum p5 #7), and `install -r` succeeded on both.

### What each watch showed

| Expected | Wear OS 4 (API 33) | Wear OS 3 (API 30) |
|---|---|---|
| The app starts with `logcat -b crash` empty | Empty after the first start and at the end. The buffer was already empty before the install. | Empty after the first start. Before the install it held 77 lines, cleared then: four crashes of the image's own Play services (`NullPointerException: NfcAdapter is null`), none of them Taqwa's. |
| The Tile on a feed | `api33-tile.png`: "ISHA 20:24", the six times, Cape Town | `api30-tile.png`: the same |
| The complications on a system face, from the feed | Shapes: NextPrayer `RANGED_VALUE`, "ISHA 20:24" over the ring (`api33-shapes-1913.png`). Utility: NextPrayer and Dates as `SHORT_TEXT` (`api33-utility.png`). | Shapes: the same ring (`api30-ring-*.png`). Utility: Dates and NextPrayer as `SHORT_TEXT` (`api30-utility.png`). |
| Today shows no face row | Scrolled to its end, the list stops at "Cape Town", with no "Add the Taqwa face" node (`api33-today-end.png`, uiautomator) | The same (`api30-today-end.png`) |
| No `taqwa_face_addable` | Play services' capability table lists only `taqwa_watch` for `world.taqwa.app` on this node. Each publish logs `TaqwaFace capability unchanged 4007`: the removal of a capability that was never added. | The same |
| `MY_PACKAGE_REPLACED` (`install -r`) | Crash buffer empty. No `TaqwaFace update` line. The receiver ran: a second `TaqwaFace installed - none`. | The same |
| Cold start under `screenrecord --time-limit 3` | The system's splash: the launcher icon (white disc, dark arch, amber lamp) on black, at 60 dp (120 px), then Today counting down (`api33-cold-start-sheet.png`, `api33-splash.png`) | `core-splashscreen`'s own: for about 0.2 s the launcher's dark grey circle opens behind the icon, then the icon on black at 48 dp (96 px), then Today (`api30-cold-start-sheet.png`, `api30-splash.png`) |
| The Tile's renderer schema, from the Tile's request | `schema 1.0`, so the label and the clock: "ISHA 20:24", no countdown | `schema 1.0`, the same |

- **Ruling 71 item 5.** Neither watch has Watch Face Push. Every face call logged
  `TaqwaFace installed - none`, so `FacePush` answered UNSUPPORTED. No face package was installed,
  so the same-code replacement seen on Wear OS 6 cannot occur here.
- **The Tile's countdown is never reached on these images.** Both renderers report 1.0, below 1.2,
  so each Tile drew plan 3's call 6 form, as a renderer under 1.2 must. The counting-down form is the
  Wear OS 6 emulator's (plan 3 Task 8) and his Galaxy's (Task 13).
- **On Wear OS 4 the ring moves by itself.** Play services evaluates the dynamic value about once a
  second (`ComplicationDataRateLimitedEvaluator`, "New evaluation"). Shapes' ring measured 44.2°
  at 19:13:23 and 69.7° at 19:18:49. That is the Maghrib period's share, 360° over 80 minutes, a
  little over because of the arc's round cap (`api33-shapes-1913.png`, `api33-shapes-1918.png`).
- **Utility takes only `SHORT_TEXT`,** on both images. Its log refuses `RANGED_VALUE` ("w") and
  `LONG_TEXT` ("k") in every slot. No face tried offered a slot that takes `LONG_TEXT`: Utility on
  both images, and on API 30 Pilot and Artist (which refused the complication outright) and
  Athletica, Numérique and Time in Time (which refused `LONG_TEXT`).

### The countdown below API 33 (API 30)

NextPrayer as `RANGED_VALUE` on Shapes, a system face, shot at an entry's start and 5 minutes
later. The watch's clock was set to 19:02:30 (`cmd alarm set-time`), and the sample was then stored
(`DEBUG_FEED`), so the timeline was built at that moment. Its next entry, the Maghrib period, starts
at 19:04:00 and runs to Isha at 20:24.

| Shot (the watch's clock) | Reads | Ring |
|---|---|---|
| 19:02:35, before the start (`api30-ring-1902.png`) | "MAGHRIB 19:04". The face clips the M at the slot's left edge. | Nearly full: the Asr period at 99 % |
| **19:04:11, the entry's start** (`api30-ring-1904.png`) | **"ISHA 20:24"** | **Empty: the grey track alone** |
| **19:09:11, 5 minutes later** (`api30-ring-1909.png`) | **"ISHA 20:24"** | **Empty: the grey track alone** |

- **The same words in both, the known limit** (`NextPrayerSource.kt:44-46`).
  - The two shots have no coloured ring pixel between 12 o'clock and 45°. The ring's right half
    differs by 0 pixels. The only other differences are where the hands moved.
  - A dynamic ring would have been lit 22.5° from 12 o'clock at 19:09:11 (6.25 % of the period).
  - `api30-ring-strip.png` sets the three side by side.
- **The entry changed by itself at the start.** No source request was logged between 19:02:30
  and the clock's reset after the second shot. So on Wear OS 3 the face takes the timeline's next
  entry at its start, as on Wear OS 4 and 6. The title and the time, static by design, are right
  for the whole period. What is frozen is what a dynamic value would move:
  - the ring stays at the entry's starting value, empty for all 80 minutes;
  - the countdown keeps its words in `LONG_TEXT`, where no system face here could show it;
  - so, by the code, does the description a screen reader says: below API 33 it is
    `SpokenLine.fallback()`, the words at the entry's start ("Isha in 1 hour 20 minutes, at
    20:24"), for the whole period. That was not heard here: TalkBack was not run, and the
    uiautomator tree of a system face (Utility) exposes only "Watch face" and the time.
- **A first run was spoiled by the clock,** and is kept only as a note (scratchpad
  `api30/07-run1-start.png`, `08-run1-after-clock-sync.png`).
  - Wear OS 3's own time sync (`clockwork_auto_time` 0, "sync from phone", falling back to the
    modem's NITZ) set the clock back to the host's at 19:05:26 on the watch's clock: "Time changed
    by +23m11s".
  - That run's second shot came 24.5 minutes into the period. It too read "ISHA 20:24" over an
    empty ring, though no request had come since the start.
  - The pair above was shot again with `clockwork_auto_time 2` (off), and the setting was put back
    to 0 after.
  - `auto_time` 0 alone does not stop that sync. `tools/watchface/clock.sh` sets only `auto_time`,
    which is enough on Wear OS 6.

### Also checked on these watches

- **QiblaInk's API 30 path** (`Paint.getTextPath`; erratum p5 #22, Ruling 67 item 3).
  - The Qibla page was opened on Wear OS 3 nine times: English, Saudi Arabic (`ar-sa`) and
    Libyan Arabic with Western digits (`ar-ly12`), each at font 1.0, 1.15 and 1.3.
  - The debug layout log's detail line was set beside T2's table, `QiblaFitTest`'s `[table]` rows,
    run on the host from this tree.
  - Every size and baseline is the same as the table's.
  - Every gap is within 0.5 units: the largest difference is +0.35 (`ar-ly12` at 1.0 against the
    elegant Naskh row). The watch keeps a little more air, because a cluster shaped alone is
    measured in its isolated form.
  - A digit's ink is 0.750 em, the outline itself. Wear OS 6's `Font.getGlyphBounds` gives 0.766.

  | Layout | Watch (size, y, gap) | T2's table, ui / elegant |
  |---|---|---|
  | en '6,600 km', 1.0 / 1.15 / 1.3 | 10, 155, 7.64 / 11.5, 155, 5.21 / 13, 156.4, 1.89 | 10, 155, 7.41 / 11.5, 155, 5.26 / 13, 156.4, 1.72 |
  | ar-sa '٦٬٦٠٠ كم', 1.0 / 1.15 / 1.3 | 10, 155, 7.62 / 11.5, 155, 5.59 / 13, 156.4, 2.23 | 7.36 or 7.27 / 5.43 or 5.33 / 2.12 or 2.01, the same sizes and baselines |
  | ar-ly12 '6,600 كم', 1.0 / 1.15 / 1.3 | 10, 155, 6.05 / 11.5, 155, 3.39 / 12, 155, 2.07 | 5.78 or 5.70 / 3.41 or 3.32 / 1.94 or 1.95, the same sizes and baselines |

  The page draws as it should at 1.3 in Arabic (`api30-qibla-ar-sa-1.3.png`) and in English at
  1.0 (`api30-qibla-en-1.0.png`). The emulator's resting sensors put it in the "hold your wrist
  level" state. Urdu's 'km 6,600' order is the native review's (Ruling 67 item 4), not this step's.
- **The daily nudge across a force stop on API 30 to 34.** This is P2.6's case, which the Lead
  sent to these images on 30 October. It was seen on both watches:
  - `am force-stop` removed the alarm. The PendingIntent outlived it: the same
    `PendingIntentRecord` (`20463fb` on 33, `d5d3412` on 30).
  - The next start logged `NUDGE armed in 85821s found=true stopped=true` on 33 and
    `NUDGE armed in 85325s found=true stopped=true` on 30.
  - The alarm was then back on its own schedule, within 0.3 s on 33 and 0.8 s on 30.
  - The reinstall's start logs `stopped=true` too, and keeps the same schedule. Before API 34 an
    update's kill reads as "user requested", which the P2.6 review called harmless.

### For the Lead

1. **A Wear emulator joins the paired phone by itself.** `Taqwa_Wear_OS_4` was booted unpaired at
   19:03, before the script isolated anything.
   - Its Play services dialled 10.0.2.2:5601, the host's 5601. That is `scripts/wear-pair.sh`'s
     forward to `emulator-5562`. The route ran through `netsimd`, uid 10068 (gms).
   - So it joined the phone's Wear network, and through it the cloud route. Its own route map
     then reached the phone (`4e7c91b9`) in 2 hops through the cloud, and `emulator-5558` (node
     `c76e057a`) in 3. It listed the phone with a link to `5a3042ca`, the new watch.
   - At 19:03:53 the debug app's start handshake (`ASK STARTED sent`) got the phone's own feed and
     acknowledged it: `ACCEPTED 90/5cb4f7c2…`, `ACK 90/… sent`. That feed has the phone's city. Data
     items of 5558's synced to it too.
   - At 19:06 I cut it, on 5566 only: adbd root, then SYNs to 5601 refused. Play services was
     force-stopped on 5566 so that its open link closed, and then everything that leaves was
     refused. After that 5566 reached no node.
   - Through all of it, the host's 5601 held 5558's own link (adb's reverse, `127.0.0.1:49473`),
     ESTABLISHED, the same socket before and after. 5558 and 5562 were not sent any command.
   - Still, the phone's delivery counter moved to 90 while 5558 holds 68, so its next due handshake
     heals it (Ruling 50). The phone may still list the new node, unreachable since the cut and
     gone once its AVD stopped. Please weigh that before Step 2's observation, which reads the
     phone's sends.
   - `scripts/wear-old-emulator.sh` now isolates its watches at every boot, before Play services is
     up. `Taqwa_Wear_OS_3` never reached 5601.
   - Any other Wear emulator started on this Mac while 5562's forward is up joins the same way.
2. **The Lead's call 6: the evidence.**
   - On Wear OS 3, every complication's words are right for the whole period: the prayer and its
     time ("ISHA 20:24"). The entries change at each start by themselves.
   - What does not move is what a dynamic value carries:
     - the ring stays at its starting value for the whole period, empty on Shapes from Maghrib to
       Isha;
     - the countdown keeps its starting words: a `LONG_TEXT` slot's "in 1 h 20 min", and, by the
       code (`SpokenLine.fallback()`; not heard here), the screen reader's "Isha in 1 hour 20
       minutes, at 20:24".
   - No face tried here has a `LONG_TEXT` slot, so the frozen words were not drawn. A third-party
     face with one would draw them, and the description is the one a screen reader gets on any
     face.
   - On Wear OS 4 the ring moved by itself (44° to 70° in 5.4 minutes).
   - On both old images the Tile is already countdown-free: renderer 1.0, "ISHA 20:24".
   - **My recommendation is (b):** below API 33 the sources send the countdown-free form, the next
     prayer and its time, in the long text and the description. The reasons:
     - it is the one form that is true for the whole period;
     - it matches what the Tile already draws on these same watches, so the watch says one thing;
     - it keeps Wear OS 3 watches, so spec 2.1 and Mohamed's question do not move;
     - its follow-up is small: the `TIRAMISU` branch in `NextPrayerSource.longText` and in
       `SpokenLine`'s fallback, with a test.
   - (a) would ship words that are false a minute in, including to a screen reader, against the
     rule Ruling 65 item 2 set for the Tile's static fallback: the clock form, never a countdown.
   - (c) drops the watches still on Wear OS 3 to cure what (b) cures.
   - (b) leaves the ring frozen at its starting value. If that matters, the follow-up could also
     shorten the ring's entries below API 33, the spike's "static timeline" candidate. That is
     beyond the call's three options, so it is only noted here. I make no ruling.
3. **Notes.**
   - Wear OS 3 needs `clockwork_auto_time 2` before a set clock holds.
   - Shapes clips "MAGHRIB" at the slot's left edge. That is the system face's own text layout.
   - Ruling 70 item 3's P2.7 minors (the pairing scripts' README and `test-pairing-scripts.sh`)
     stay with Step 3, which pairs; this step pairs nothing.
   - The two AVDs keep their data (the debug app, the sample feed, Shapes and Utility with Taqwa's
     complications) for the next use. No snapshot was saved.

### Put back

- `emulator-5566` and `emulator-5568` were stopped by serial (`adb -s … emu kill`), and both locks
  released.
- On 5568, `clockwork_auto_time` went back to 0 and the font scale to 1.0 before the stop.
- `emulator-5558` and `emulator-5562` are still running. Neither was sent a command; the one effect
  on them is in "For the Lead" 1.
- `KB2604110095`, `emulator-5572`, the iPhone 17 Pro Max simulator and the work iPhone 12 were not
  touched.

## Step 2: the 12-hour observation (Ruling 22)

### The result: PASS, by Ruling 78

The Lead ended the observation on 7 November at 08:54 and ruled it a pass (Ruling 78, the ledger's
08:58 line). I read both captures against his line numbers and times: they agree.

- **The path that fired: the system's own update period,** the six hours every source's manifest
  asks for (`UPDATE_PERIOD_SECONDS` 21600). Nothing else asked. The last `asked again` line is
  20:31:02.718, before the install and before the capture. So it was not the safety net, the Tile
  (there was none) or the phone (stopped since 20:44:05).
- **The three requests,** each of all six lines (`step-2/observation-since-2044.txt`, and
  `observation.log`'s lines 305–316 for the first):

  | Request (SAST) | After the one before | Steps | `last until` |
  |---|---|---|---|
  | 6 Nov 20:40:31 (the staging install) | | 13 and 4 | 2026-11-08T17:57:00Z |
  | 7 Nov 02:41:49 | 6 h 01 min 18 s | 12 and 3 | 2026-11-08T17:57:00Z, the same |
  | 7 Nov 08:45:32 | 6 h 03 min 43 s | 13 and 4 | 2026-11-09T05:26:00Z, later |

  - The middle request lost a step and kept the end. London's date had turned at 02:00 SAST, but no
    prayer had started since 20:40 (the night's Isha was under way, and Fajr was at 7:23 SAST), so
    the ten periods ahead were the same ones.
  - The last one comes after Fajr, so the timeline reaches one prayer further: Fajr on the 9th.
- **Disclosed: the later-ended request came 66 s past the 12 h.** The capture started at 20:44:26,
  so the plan's 12 h ended at 08:44:26, and the request is at 08:45:32: 12 h 1 min 6 s. The one
  inside the 12 h, at 02:41:49, had the same end as the first. By the plan's letter ("within the
  12 h … later than the first one's") this misses by 66 s. The Lead counts it as a pass: the
  mechanism fired twice on schedule, and the fallback clause (a request before the recorded end, 8
  November 19:57 SAST) is met by a day and a half. From the install it is 12 h 5 min 1 s, for the
  same reason: a period of 6 h that came 1 and 4 minutes late.
- **The face at the end against the feed: the same.** The shot at 08:53:55
  (`step-2/face-at-end-0853.png`), 8 minutes after the request with the watch untouched, reads 26
  Jumada al-Ula, SAT 7 NOV, DHUHR 11:49, in 4 h 55 min.
  - Staging cannot `run-as`, so in Step 4 the debug build went over it (same key) to read the stored
    file: delivery 7, `5cb4f7c2d90cbbe6`, the feed the watch held throughout
    (`step-2/stored-feed.json`: the city and the whole-degree bearing, no coordinates).
  - `WatchRules` over that file, in a one-off host test (`step-2/StoredFeedCheck.kt.txt`, its output
    in `stored-feed-check.txt`; not left in the tree): at 08:53:55 `DHUHR 11:49, in 4 h 55 min`, and at
    the start's 20:45:08 `FAJR 5:23, in 10 h 37 min`, the two shots' words. Its row for the 7th reads
    26 Jumada al-Ula 1448 and SAT 7 NOV; the face leaves the year out.
  - 11:49 is London's Dhuhr, 13:49 on the watch's SAST clock, 4 h 55 min 5 s after the shot.
  - `ComplicationTimelines` over it gives the logged steps and ends at all three requests.
- **The closing `getprop`.** `adb -s emulator-5558 shell getprop log.tag.TaqwaComplications` printed
  an empty line. This build predates Ruling 72's log gate, so it wrote its lines without the tag, and
  the empty line means nothing (Ruling 76 item 4). `uptime` said 1 day 9:52 at 08:59: no reboot.
- **No crash of Taqwa.** Its process was PID 24930 in every line from 20:40:31 to the end, and still
  at 08:59. The crash buffer holds one process, read at 08:59: the emulator's own sensors HAL
  (`android.hardware.sensors-service.multihal`, SIGABRT at 6 November 23:41:01,
  `activationOnChangeSensorEvent:231: unexpected sensor type: 26`). That is the system image's, not
  Taqwa's, whose compass runs only on the Qibla page, which nothing opened.
- **Untouched.** From 20:45 to 08:53 the watch was sent nothing but one `screencap` at 08:53:55. No
  `TaqwaWearFeed` or `TaqwaFace` line was written after 20:40:31 (`observation-sysout.log`'s last).
- **What this does not show.** The emulator stayed awake on a screen that never times out
  (`screen_off_timeout` 2147483647, `deviceidle` ACTIVE at the start and at 08:59). A real wrist, with
  the watch asleep and dozing, is Task 13's.

### How it started

The capture began at 20:44:26 SAST on 6 November 2026 and ran on alone until the Lead ended it.

This run was on `watch-wear` at `824b0393`: `git merge watch-companion` (`6f8e2cb7`, R71's
landings) over Step 3's WIP `56b1256f`, with no `wearApp` change. The device step took `wear-5558`,
then `phone-5562`, at 20:39:10. Both locks stayed taken, with a note in each, until Step 4. The lines
cited are in `2026-10-24-watch-gates/step-2/`: `logs.txt` for the start, and
`observation-since-2044.txt` for every line after it. The whole captures stay in the session
scratchpad (`p512s2/`).

### The watch going in

- **Staging, this tree's.** `./gradlew :wearApp:assembleStaging -Ptaqwa.unsignedRelease=true`
  built `wearApp-staging.apk`: 4,979,221 bytes, SHA-256 `62e88a0ac18327be…`, on the debug key
  (`e5d76ef6…`). Its dex holds Step 0's two lines (`, last until `, `asked again (`).
  - The bundled face is SHA-1 `73a84951`, on Task 2's test key (`9939135e…`).
- **No uninstall was needed.** Step 3's candidate was on the watch: Wear app `53bc9078` on the debug
  key, face `5b0586ce` on the test key, active. Staging has the same two keys, so it went on with
  `install -r` at 20:40:31. The app's data, the stored feed included, stayed.
- **Ruling 71 item 5: the same-code path ran.**
  - Watch Face Push replaced the face in its slot (`948eb69c…`) at the same code, 41:
    `5b0586ce` became `73a84951`, and its `lastUpdateTime` moved to 20:40:31.
  - The system showed its own default face for 0.21 s (20:40:31.301), then Taqwa's again
    (`favId=45`, 20:40:31.510).
  - The app's `FaceUpdateReceiver` start logged `installed - [948eb69c… 41]`, `bundled 41 copied`
    and `isActive … true` at once. The capability was unchanged.
  - It sent no handshake (`ASK STARTED skipped`) and logged `NUDGE kept`.
- **A feed stored.** Staging is not debuggable, so `run-as` cannot read the file. Three things show
  the stored feed instead:
  - every source's timeline was read from it at the install (below);
  - the face reads London's next prayer, FAJR 5:23, with its countdown;
  - the phone answers that the watch holds its latest feed (next list).

### The phone's feed

- The phone app was opened warm (20:41:28), and nothing was sent.
- It was then opened cold, after a force stop (20:42:35). Its reschedule asked twice:
  `TaqwaWatch RESCHEDULED UNCHANGED 266ms` and `… 17ms`.
- So no new delivery went out. The watch had already echoed the phone's latest: delivery 7,
  fingerprint `5cb4f7c2…`, accepted at 20:31:02 in Step 3. That is the feed it holds, built today
  from the phone's own plan. A send of it would carry the same timeline. Its delivery number is the
  only thing a fresh send would have changed.
- The phone's node table still lists Step 1's ghost, `Taqwa_Wear_OS_4` (`5a3042ca`: 2 hops,
  not nearby).
- `emulator-5562` was stopped by serial at 20:44:05 (`adb -s emulator-5562 emu kill`), so nothing
  refreshes the feed.

### The timeline's end, recorded

The first `last until` line of this staging install (`logcat -v time -s TaqwaComplications`):

```
11-06 20:40:31.090 D/TaqwaComplications(24930): day dial: 13 steps, last until 2026-11-08T17:57:00Z
```

- Every source's line ends at the same instant: `next prayer` RANGED_VALUE and LONG_TEXT, `day` and
  `day dial` each 13 steps, and `dates` LONG_TEXT and SHORT_TEXT each 4 steps.
- That is **2026-11-08T17:57:00Z, 8 November 19:57 SAST**: the start of London's Isha on the 8th,
  where the timeline stops saying anything.
- The buffer's earlier `last until 2026-11-08T14:34:00Z` lines are Step 0's, from another feed.

### The capture

- **Started 20:44:26 SAST**, as a detached background process. It outlived the shell that started
  it: its parent is PID 1.
  - `observation.log`: `adb -s emulator-5558 logcat -v time -s TaqwaComplications TaqwaWearFeed`,
    PID in `observation.pid`.
  - Beside it, `observation-sysout.log`: `-s System.out TaqwaFace`, PID in
    `observation-sysout.pid`. The Wear app's feed and nudge lines (`TaqwaWearFeed NUDGE …`,
    `ASK …`) go to `System.out`, so `-s TaqwaWearFeed` misses them (Step 0's review minor).
- **At 30 s both lived.** `observation.log` held 316 lines, the buffer's first lines: from
  `--------- beginning of main`, through Step 0's lines, to the install's at 20:40:31. Its own lines
  start after 20:44:26.
- **The watch at the start,** read once and changed in nothing:
  - deviceidle `ACTIVE`/`ACTIVE`, awake, battery 100 and not powered;
  - no Tile in the carousel;
  - the `DailyNudgeReceiver` alarm due at about 7 November 20:31 SAST (`whenElapsed 163435684`).
- **The face at 20:45:08** (`step-2/face-at-start-2045.png`), shot with `adb exec-out screencap -p`
  and nothing else: 25 Jumada al-Ula, FRI 6 NOV, 20:45, FAJR 5:23, in 10 h 37 min.
- From then on the watch is left untouched: no Tile view, no app open, no install.

### What the Lead was to read, and when

- **12 h:** 7 November 08:44 SAST.
- **Pass:** a `steps, last until` line after 20:44:26 whose `last until` is later than
  2026-11-08T17:57:00Z. It may come from the system, the update period or the safety net (an
  `asked again (<why>)` line).
- **If none by then:** the observation runs on to the daily nudge, due at about 7 November
  20:31 SAST (`asked again (daily)` and the request after it). It fails if 8 November 19:57 SAST
  passes first.
- **Either way,** at the request's time the face's next prayer and countdown are checked against
  the feed's times: a screencap against `WatchRules` over the stored feed.
- **To end it:**
  - `kill $(cat observation.pid) $(cat observation-sysout.pid)`, the Lead's at 08:54;
  - put the watch back as Step 3 left it, and boot `emulator-5562` again, in Step 4;
  - release `phone-5562`, then `wear-5558`, in Step 4.

### For the Lead, at the start

1. **No new delivery was sent** (see "The phone's feed"). The phone's `UNCHANGED` is its own
   confirmation that the watch holds its current feed. Making it send again needs a change the
   step does not ask for: a new city, a phrase, a changed watch, or the watch's delivery file
   removed. Staging cannot remove it, since it is not debuggable.
2. **The erratum's Tile refresh is not timed in this observation.** The erratum asks for one
   real system Tile refresh, timed against `TilePlan.freshnessMillis`. But there is no Tile in the
   carousel, and the step allows no Tile view.
   - A Tile added now would also ask the sources itself (`asked again (tile)`). That is a fourth
     path, and the Pass does not count it.
   - So it needs its own short run with the Tile added, or Task 13 on the Galaxy. Your call. The
     Lead's call was Ruling 72 item 6, and the run is in Step 4.
3. **Your cron 50999131 (7 November 07:41) runs before the 12 h** end at 08:44. The Lead's second,
   e117b412, ran at 08:53.

## Step 3: the dry-run release candidate on the paired emulators

6 November 2026, on `watch-wear` at `3d4cc672`: `git merge watch-companion` (`f5fbeae1`, which
holds P5.7, Step 0 and the M1 pin) over Step 1's WIP `dcbf6262`, with nothing new to commit first.
The device step held `wear-5558` and `phone-5562` from 20:17:38 to 20:34. The shots and the lines
cited are in `2026-10-24-watch-gates/step-3/` (`logs.txt`); the full captures stay in the session
scratchpad (`p512s3/`).

### The candidate

- **The face, signed with Task 2's test key.** `scripts/release-face.sh` ran with `TAQWA_FACE_KS`
  (`~/.android/taqwa-face-debug.keystore`), `TAQWA_FACE_ALIAS`, `TAQWA_FACE_KS_PASS_ENV` and
  `TAQWA_FACE_CERT_FILE`. The pin was a scratch file holding the test key's certificate SHA-256,
  `9939135e…`, as `keytool` gives it.
- **The dry run.** `TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE=<that pin> scripts/release.sh`, as
  P5.7 documents it (`docs/STORE-CHECKLIST.md` step 4, the ledger's Lane A lines). It ran from
  20:14:06 to 20:16:52 and printed `DRY RUN PASS`, with 29 `ok` lines and no `FAIL`.
  - No `keystore.properties` was in the worktree before, during or after the run. The real run was
    not made. No key in `~/keys` was read.
  - The tree was left alone while the face gate ran. `build/face-release/` was removed afterwards.

| Output in `build/release-dry-run/` | Bytes | SHA-256 |
|---|---|---|
| `androidApp-release-DRY-RUN.apk` | 27,427,656 | `a54ff7347591dcd8…` |
| `androidApp-release-DRY-RUN.aab` | 32,960,829 | `0e66ebae633812ee…` |
| `wearApp-release-DRY-RUN.apk` | 5,010,024 | `1103cf65d81a10d4…` |
| `wearApp-release-DRY-RUN.aab` | 5,748,333 | `b234e7f9479e50dc…` |

- All four are signed by the debug key alone (`e5d76ef6…`). The phone is 1.5.0 (41) and the Wear
  app 1.5.0 (100041). No version was bumped.
- The face inside (`assets/default_watchface.apk`, 472,806 bytes, SHA-1 `5b0586ce`) is
  `world.taqwa.app.watchfacepush.taqwa` 1.5.0 (41) on the test key. It is the signed face byte for
  byte.
- The phone and Wear APKs differ from Lane A's ledger hashes. This tree has Step 0's log lines and
  Step 1's script, and a worktree records no commit in `version-control-info`.
- The dry run's own checks found no sample feed or test vector in any output (Ruling 55 item 6).

### Installed first, and the swap

**What was installed at 20:18** (`logs.txt` section 2):

- **The phone:** the debug build, 1.5.0 (41), `6cbda5a6`, debuggable.
- **The watch:**
  - the debug Wear app, 100041, `109521c0`;
  - the face, 41, `f13548e6`, installed by `com.google.android.wearable.dwf.receiver` and active
    (`watch-as-found.png`);
  - the phone's London feed with its delivery, and `face-activation-spent` in the app's files;
  - no Tile, and `auto_time_zone` 0.

**The swap, in order:**

1. 20:20:50, the face uninstalled. The watch fell back to the system's digital face.
2. 20:20:53, the debug Wear app uninstalled.
3. 20:20:53, the phone's debug build uninstalled.
4. 20:21:07, the dry-run phone APK installed, with `install -r`.
   - Onboarding ran as a new user's would. Location and notifications were not allowed, and London
     (England) was chosen by hand, the found phone's city.
   - Today then read "Fajr in 11 hours, at 5:23", with Qibla "118° · 4,793 km to Makkah".
5. 20:25:26, the dry-run Wear APK installed, with `install -r` (Task 6 Step 6's checks 1 and 2 sit
   between steps 4 and 5).

### What the candidate did

| Expected | Seen | Evidence |
|---|---|---|
| **The default face appears** | The face arrived with the app's install: package `installation completed` at 20:25:26.401, 0.17 s after the app's. Its installer is `com.google.android.wearable.dwf.receiver`, and its bytes are the bundled face's (`5b0586ce`). The picker's Add list shows "Taqwa" with its preview. | `watch-default-face-in-picker.png`, `logs.txt` 5 |
| **Phone, check 1:** the Wear app uninstalled, then a tap | "WATCH › Install on your watch", valued "Taqwa_Wear_OS_6". The first tap (20:22:47) was made with the companion cached. It answered "Couldn’t reach your watch", and its `REMOTE_INTENT` was dispatched 45 s late (enqueued 20:22:48.166, dispatched 20:23:33.234). The watch then started `com.android.vending` with `market://details/…` at 20:23:32. A second tap, with the companion still cached, was the same. A third tap, right after the companion's own screen had been opened, answered "Continue on your watch" and started the Play Store within 0.5 s. | `phone-1-install-row.png`, `phone-1-tap-companion-cached.png`, `phone-1-tap-continue.png`, `watch-1-play-store.png`, `logs.txt` 4 |
| **Phone, check 2:** freshly installed, face not active, activation unspent | "WATCH › Add the Taqwa face". A tap at 20:28:05, with the companion awake, started `taqwa://face` → `FaceActivity` 0.3 s later. The phone said "Continue on your watch". Its button was not pressed from this check. | `phone-2-face-row.png`, `watch-2-face-screen.png`, `logs.txt` 6 |
| **The watch's row** | **The face was made active.** The emulator's activation was not spent: `FaceActivity`'s button asked for `SET_PUSHED_WATCH_FACE_AS_ACTIVE`, then Allow, then `TaqwaFace setActive 948eb69c… ok` (20:28:32.228). The screen said "Taqwa is your watch face.", and the capability was `removed ACTIVE`. So the debug install's spent activation did not carry over to this uninstall and fresh install. | `watch-row-permission.png`, `watch-row-active-line.png`, `watch-face-active.png` |
| **Phone, check 4:** face active, after the watch's publish | No WATCH section: App › Widgets is followed directly by About. | `phone-4-no-section.png` |
| **Phone, check 3:** Today's chip, then Deny (on a second fresh install, below) | The face screen said "Touch and hold your watch face, then choose Taqwa.", with no button, and the capability was `removed CHOOSE_IT_YOURSELF`. The permission reads `granted=false, flags=[ USER_SET]`. The phone then showed no WATCH section. | `watch-3-deny-guidance.png`, `phone-3-no-section-after-deny.png`, `logs.txt` 8 |
| **The feed** | The app's first start (20:25:28) sent `ASK STARTED`. The phone's London feed came back as `ACCEPTED 1/5cb4f7c2…` and then `3/…`, both ACKed. The fingerprint is the found feed's. Today and the Tile both read London's times, the phone's own: Fajr 5:22, Jumuʿah 11:49, Asr 14:37, Maghrib 16:28, Isha 17:59. | `watch-today-top.png`, `watch-today-end.png`, `logs.txt` 5 |
| **The complications** | Every slot of the active face is bound to Taqwa's sources: `DayDialSource` (11), `NextPrayerSource` RANGED_VALUE "FAJR"/"5:23" (12) and LONG_TEXT "in 10 h 54 min", then "53" a minute later (16), `DaySource` (13), and `DatesSource` long "25 Jumada al-Ula · FRI 6 NOV" and short "FRI 6" (14, 15). Every source logged `13 steps` or `4 steps, last until 2026-11-08T17:57:00Z`. The face read FAJR 5:23 with the right countdown straight after the feed (Ruling 70 item 2). | `watch-face-active.png`, `logs.txt` 7 |
| **The Tile** | Added with `DEBUG_SURFACE add-tile`, and found on the first swipe. It reads "FAJR in 10 h 53 min", the six rows with Isha current, and "London". `asked again (tile)` was logged. | `watch-tile.png` |
| **Today** | The ring with "FAJR IN 10:55:44 / 5:23", the day's list, "London" and the "Add the Taqwa face" chip. | `watch-today-top.png`, `watch-today-end.png` |
| **Tasbeeh** | SubhanAllah, ROUND 1, 0 of 100, "Turn the bezel or tap". A tap counted 1. The emulator's `input rotaryencoder` threw, so the bezel was not tried. | `watch-tasbeeh.png` |
| **Qibla** | The dial with the line at London's 118°, "4,800 km", and "Hold your wrist level", from the console's resting sensors (tilt 85°, as in Step 0). | `watch-qibla.png` |

- The crash buffers on both emulators hold no line naming `world.taqwa`.
- The release build keeps the `TaqwaFace`, `TaqwaComplications` (`Log.d` included) and
  `TaqwaWearFeed` (`System.out`) lines, so Task 13 and Step 2 can read them on a store build.

### Check 3's second install

Check 3 needs an unspent activation and a face that is not active, and check 2's tap had already
used both. So the Wear app was uninstalled at 20:30:37 and installed again at 20:30:48.

- The face went with the app's uninstall: it was gone 4 s later.
- At the new install, Push put the same bytes (`5b0586ce`) into the same slot id (`948eb69c…`).
- The first start sent `ASK STARTED` and `ASK OPENED`, and accepted deliveries 5 and 7.
- After the Deny, the guidance line was followed as written: touch and hold the face, then Add,
  then "Taqwa", then Activate. The face became active with FAJR 5:23 and its countdown
  (`watch-face-chosen-by-hand.png`), and every source logged its timeline again.

### Ruling 71 item 5: which Watch Face Push path ran

The **first-install path**, both times. The step uninstalls the face and the app first, so the
system's `dwf.receiver` installed the bundled face from the app's own install (20:25:26.401 and
20:30:48).

- The app's start then found it in the slot (`TaqwaFace installed - [948eb69c… 41]`) and copied
  its bundled copy (`bundled 41 copied`). It added and updated nothing.
- The same-code replacement that Step 0 saw on `install -r` did not come into play: no face was in
  the slot when the app went on.
- Uninstalling the Wear app removes its pushed face.

### Not run in this step

- **Task 6 Step 6 check 5** is for `emulator-5564`, which this step does not use.
- **Check 6 (TalkBack on the phone)** was not heard again on the release build. P5.6 heard it on
  the debug build. Here the dump shows the same nodes: the "WATCH" heading, the row with its value,
  and the answer as its own node under the row.
- **The bezel** in Tasbeeh (see the table).
- **The Play Store install itself:** nothing on the watch's Play Store was pressed. It opened as
  `WearUnauthenticatedMainActivity`, showing "Update in progress… / Continue to Play Store". No
  sign-in was asked of this step, and none was made.
- **Ruling 70 item 3:** `scripts/test-pairing-scripts.sh` ran (all passed; it needs no device).
  This step paired nothing, and the pairing held throughout. So P2.7's carried README minors were
  left, and Step 4 closes them.

### For the Lead

1. **P5.6's finding, seen again on the release candidate.** With the phone's Wear OS companion
   cached, a row's tap answers "Couldn’t reach your watch" after the 10 s bound. The watch then
   opens the target anyway, when the deferred `REMOTE_INTENT` is delivered (45 s here).
   - It is the same emulator pairing, made by adb with no companion-device association. Task 13
     checks it on his S23.
   - It is counted as a finding below, because a check-1 tap gave a false answer.
2. **Step 1's ghost watch is still listed.** The phone's node table lists `Taqwa_Wear_OS_4`
   (`5a3042ca`) as reachable: 2 hops through the cloud, not nearby, and isWatch. `emulator-5566`
   is stopped.
   - The rows chose 5558 (only a nearby watch counts).
   - The watch accepted only odd deliveries (1, 3, 5, 7). If the phone's send numbers are shared
     across nodes, the even ones went to that node. That is a guess, not measured. It bears on
     Step 2, which reads the phone's sends.
3. **The phone's Taqwa is a fresh install.** Its city is London, set by hand, with no location or
   notification permission. Its delivery numbers start again from 1, and so does the watch's
   record, so no Ruling 50 heal is pending.
4. **The activation limit on this emulator** reset with the app's uninstall and fresh install.
   `setActive` worked, after the debug install had recorded `face-activation-spent`.

### Left

- `emulator-5562` and `emulator-5558` are running and paired: the phone lists 5558 nearby.
- The dry-run candidate is installed on both:
  - the phone, 1.5.0 (41), `91d0a6f1`;
  - the Wear app, 100041, `53bc9078`;
  - the face, 41, `5b0586ce` on the test key, active.
- The watch holds the phone's London feed. There is no Tile, `auto_time_zone` is 0, and the
  Tasbeeh count is 0. Step 2 installs staging on the watch next.
- `build/release-dry-run/` holds the four outputs. `build/face-release/` is gone, and there is no
  `keystore.properties`.
- Both locks were released. `KB2604110095`, `emulator-5572`, the iPhone 17 Pro Max simulator and
  the work iPhone 12 were not touched.

## Step 4: the Tile's refresh timed, the devices back, and this report's close

7 November 2026, on `watch-wear`: `git merge watch-companion` (`c5469c9e`, Rulings 72–77) over
Step 2's WIP `d705e838`, as merge `65351227`, with no conflict. The device step took over `wear-5558`
and `phone-5562` from the observation at 08:56, on the Lead's hand-over, and released them at 09:37.
The Tile's lines, the face's and the shots are in `2026-10-24-watch-gates/step-4/` (`logs.txt`, `tile-natural.log`).
The crash buffers, the phone's node table and the settings were read at the time and not kept. The whole
capture stayed in that session's scratchpad.

### The Tile's refresh, timed (Ruling 72 item 6)

**A real system refresh came within 0.1 s of `TilePlan.freshnessMillis`, with the Tile not visible and
with it visible. The system's timer runs on real time, so a moved clock neither brings it forward nor
holds it back.** The usual 45 minutes was not waited out, because the bound was 40. Item 6 is closed
by the shorter runs below, and one line goes to Task 13 for what an emulator cannot show.

- **What the code gives.** On the watch's own feed (`step-2/stored-feed.json`) at the first request,
  7 November 09:00:20: 32 entries, 18 counting down, as the log says, and **2,700 s, 45 minutes**. Four
  sevenths of the 80-minute moving part is more than `MAX_FRESH_MILLIS`, so the cap holds
  (`step-2/stored-feed-check.txt`).
- **The shorter way.** On this feed a shorter freshness exists only at its end, since a Tile is fresh
  until its notice begins. The feed's last start is Isha on 5 December, 19:33:00 SAST (`last until
  2026-12-05T17:33:00Z`, read from a first request at 19:00:00, which `freshnessMillis` gives 1,980 s).
  So the clock was set with `tools/watchface/clock.sh <epoch>` first, and `DEBUG_SURFACE add-tile` then
  made the request, with the staging build `62e88a0a` and nothing else touched until the next request.
- **Waiting:** 33 minutes 26 seconds in all, 09:00:20 to 09:33:46, inside the bound.

| Run | The Tile | The clock | Asked for (the watch's clock) | `freshnessMillis` | Asked again | After |
|---|---|---|---|---|---|---|
| 1 | Not visible, then viewed at 09:53:53 | True at the request, then 43 min 34 s forward, past the freshness's end on its own face (09:45:20.6) | 7 Nov 09:00:20.641 | 2,700 s | Not in the 10 min 22 s of real time before it was removed | Not reached |
| 2 | Not visible: the face in front | Set first, then left alone | 5 Dec 19:21:00.088 | 720 s | 19:33:00.169 | **720.08 s** |
| 3 | Visible from 19:28:02 to the end | Set first, then left alone | 5 Dec 19:28:00.165 | 300 s | 19:33:00.219 | **300.05 s** |
| 4 | Not visible | Set first, then 60 s forward at 19:29:00 | 5 Dec 19:28:00.079 | 300 s | 19:33:59.813 | **About 300 s of real time**, 359.7 s on the watch's clock |

- **Not visible or visible, the same.** Run 2 is the one Ruling 72 item 6 asks for: nothing looked at the
  Tile, and the system asked by itself (`not-visible-face.png`).
- **The clock does not move the timer.**
  - Run 4's request came 59.8 s after the notice's own start on the watch's clock, the 60 s the clock
    moved. So it answers the freshness, not the timeline's end, with which runs 2 and 3 coincide by
    construction.
  - In run 1 the watch's clock passed the freshness's end at 09:01:46 of real time. No request came
    then, or at 09:53:53 on the moved clock, when the Tile was viewed. A view of a Tile that is
    fresh in real time asks nothing.
  - The renderer's own entry changes do follow the clock: their alarm is RTC
    (`ProtoTilesPlTileContentEmitter`, in the 09:01:00 dump in `logs.txt`; its later settings were not saved).
    At 09:53:53 the Tile drew "DHUHR in 3 h 55 min", right for the moved clock
    (`tile-moved-clock-0953.png`).
- **What each request got.** The request at the last start is the notice alone: `Tile: 1 entries, 0
  counting down, 319 bytes`, "Open Taqwa on your phone" (`tile-visible-run-end-notice.png`). The code
  gives it 0, never refreshing by itself. These Tiles stayed on for only 16 to 58 s after it, too short
  to show that by watching; `TilePlanTest.freshnessIsAtMostThreeQuartersOfAnHour` holds it.
- **`asked again (tile)`** was logged at 09:00:20 and at 19:00:00, and not at the other requests, inside
  the six hours of `Throttle`, as expected once a Tile exists. Its stamp from the moved clock was
  removed afterwards (below).

**What this does not show:**
- **The 45 minutes, in this step.** It timed 300 s and 720 s. The review after it timed the 45 minutes on the
  true clock, still on this awake emulator, on today's R8 dry-run candidate, the Tile not viewed: `Tile: 31 entries` at 09:50:42.366 and again at 10:35:42.461,
  2,700.095 s (`tile-natural.log`). The same log shows the Ruling 72 gate on an R8 build: tag unset, `asked
  again (tile)` with no source line; tag DEBUG, `asked again (app start)` and all six `steps, last until` lines.
- **A sleeping watch.** This emulator never sleeps (`screen_off_timeout` 2147483647, `deviceidle` ACTIVE,
  Awake). 20 s after the first request, `dumpsys alarm` held no SysUI alarm at about +45 minutes (Android's
  own `*job.delay*` and three others have windows that span it), and `dumpsys jobscheduler` no job. The Tile's one pending alarm was the renderer's next
  entry. So the timer is not on either list, and from here I cannot say whether it runs while a watch
  sleeps. The cost of a late request is known: past the moving 80 minutes, the line says the start's
  time (call 6), which is true however late; and a late change can keep a start's time and the rows before it
  on the Tile after that start has come, for up to three quarters of that entry again (`TilePlan`'s KDoc).
- **Samsung's system.** So Task 13's check 7 gains one line: the time between `TaqwaTile` lines through
  its hour away, with a Tile added and not viewed, as a record and not a gate.

### The devices, put back

- **The Tile came off** (`remove-tile`, 09:34:44), and `clock.sh auto` put the clock back in the same
  second. `auto_time` is 1.
- **The debug build went over staging for a moment** (`install -r`, the same key, 09:34:57), only for
  `run-as` to read the stored feed. While it was on, `files/complications.asked` was removed: it held 5
  December's stamp. `Throttle` would have treated one ahead of now as due anyway.
- **The watch is on today's dry-run candidate, so a current build was valid.**
  - `release-face.sh` with Task 2's test key and a scratch pin (`9939135e…`), then
    `TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE=<that pin> scripts/release.sh`, from 09:04:52 to
    09:06:42 on `04a82424` (the merged tree): `face-gate.sh: PASS`, `check-bundle: PASS`, `wear-release-check
    release: PASS`, `DRY RUN PASS`, with no `FAIL`. No `keystore.properties` was in the worktree, the real
    run was not made, no key in `~/keys` was read, and `build/face-release/` was removed afterwards.
    The version stays 1.5.0 (41) and (100041).
  - `wearApp-release-DRY-RUN.apk`: 5,010,024 bytes, SHA-256 `099293f2b9ce61f1…`, SHA-1 `614540da`.
    `androidApp-release-DRY-RUN.apk`: 27,427,656 bytes, SHA-256 `613b11a92ddabb0b…`, SHA-1 `7f95f290`.
    The AABs: `9506c0d0309b838e…` and `2a35a63453c8aff6…`.
  - On the watch, Ruling 71 item 5's **same-code path** ran again (09:35:47): Push put the candidate's
    face, 41, `5b0586ce` on the test key, into the same slot (`948eb69c…`). The system showed its
    default face for 0.17 s, then Taqwa's again (`favId=45`). `isActive … true` was logged at once, and
    the capability was unchanged. The face's bytes are Step 3's: nothing in it changed in Rulings 72–77.
  - The rest is as Step 3 left it: no Tile (a swipe from the face lands on the system's Timer), English,
    font 1.0, TalkBack off, Tasbeeh not opened. `auto_time_zone` read 1 at 08:59, before I touched
    anything, where Step 3 recorded 0, and the zone is still Africa/Johannesburg. I left it.
- **`emulator-5562` was booted by its serial** with `scripts/phone-emulator.sh` (09:36:10, API 36), and
  the pairing's two halves were made by hand, as `scripts/wear-pair.sh`'s header gives them. That
  script's poll needs the debug harness, which a candidate does not carry, and would have said "Pair
  Wearable" falsely. Then the dry-run phone went on with `install -r`.
- **A fresh feed was accepted.** Today's feed is a new one, since the phone's days moved on by one: at the
  cold open (09:36:21) the phone logged `RESCHEDULED SENT 1251ms`, `112ms` and `17ms`, `WATCH_APPEARED SENT`
  and `WATCH_ASKED SENT`, and the watch `ACCEPTED 8` to `12/60e7dff197bb2606`, each ACKed. The face reads
  DHUHR 11:49, in 4 h 12 min at 9:36 (`watch-as-left.png`), and the phone's Prayer screen had London's
  day before it was sent home (`phone-as-left.png`). The phone's table lists `Taqwa_Wear_OS_6` nearby. It
  also still lists Step 1's ghost, `5a3042ca`, not nearby.
- `auto_time_zone` read 1, set by the system (`dumpsys settings`: `pkg:android value:1 default:1`). The face is left
  on Arch and Amber with empty slots.
- Neither crash buffer names Taqwa. Both emulators are left running and paired. The locks were released,
  `phone-5562` and then `wear-5558`. `KB2604110095`, `emulator-5572`, `emulator-5580`, the iPhone 17 Pro
  Max simulator and the work iPhone 12 were not touched.

### P2.7's carried minors (Ruling 70 item 3, erratum p5)

- **Closed:** the pass report's untouched list now names the Lead's `emulator-5556` and Dev 2's
  `emulator-5560`, and its harness section gives the remote shell's quoting for a phrase with a space.
- **Closed, with a test proven on its mutant:** `wear-pair.sh`'s `2>&1` had no test. `test-pairing-scripts.sh` now has
  an adb that fails until the deadline, named by its own words in the last answer. Run against the
  mutant without `2>&1`, it fails (`1 failed`); on the script as it is, `all passed`.
- **Run:** `scripts/test-pairing-scripts.sh` in Step 3 and again here. It stays a hand-run script, not
  part of `scripts/test.sh`: the erratum asks for it in this task's run, and the Lead's 05:50 line
  stopped minor waves before the candidate. Adding it is one line, if the Lead wants it.
- **Left, with the reason:** the commit body's "749 ms" is `45c6fc16`'s and is not rewritten; the report
  itself says what was slower. The "coordinates in debug evidence" are the city centres the harness
  was told to store for London and Tripoli, nobody's location, and the feeds beside them carry none.

### What the emulator gates proved

| Gate | Proved | Evidence |
|---|---|---|
| **Step 0** | R8 keeps the lines the gates read. The compass logs only on request. The Tile's preview is the final look. | `step-0/` |
| **Step 1, Wear OS 4 and 3** | On images below the Push library's floor, the app starts and reinstalls with no crash. No face row, no `taqwa_face_addable`. Tile and complications work from a feed. The Tile takes the clock form on renderer 1.0. The splash holds. On API 30, QiblaInk's path is within 0.5 units of its table. The frozen countdown found there became Ruling 72's clock form, proven on the same watches (`2026-11-06-wear-os-3-clock-form.md`). | `step-1/` |
| **Step 2, a day alone** | With the phone stopped and the watch untouched, the system's six-hour period kept every source's timeline moving ahead, and the face stayed right against `WatchRules` over the stored feed (Ruling 78, with its 66 s disclosed). | `step-2/` |
| **Step 3, the candidate** | The dry-run release, R8 and all, on the paired emulators: the default face from the app's install, the phone's rows, the one activation, Deny's guidance, the feed, complications, Tile and three screens. Keys, permissions and bundle checks by `release.sh` itself. | `step-3/` |
| **Step 4, the Tile** | A real system refresh on `freshnessMillis` to 0.1 s, visible or not, on real time. | `step-4/` |

### What they did not prove, and where it goes

All of it is Task 13's or later, none of it a gate these steps could have closed:

- **Samsung's system:** its Tile renderer and picker, its complications' rate, its face editor, its reader, and how far its timers keep to the period when asleep. Checks 3, 4 and 6; the Tile's refresh gap is the one line this step adds to check 7.
- **Real sensors:** the compass on a wrist (check 6's log). The bezel, which the emulator's `input rotaryencoder` could not drive in Step 3.
- **Far north:** erratum p5 #23's late renderer, the Sunrise minute after Fajr at a Tromsø-like feed. Check 6.
- **A real wrist's sleep.** Steps 2 and 4 ran on a watch that never slept or dozed, with the system's period and the Tile's timer both unstressed. Check 7's hour with Bluetooth off is the first look. The observation's mechanism itself is not re-run for 12 hours there.
- **The store's builds:** Mohamed's face key and the upload key (M1's pin holds the real run only), the certificate Play re-signs with, split APKs and Play's delivery of the face. Task 13 on the signed candidate and Task 17.
- **A real companion:** the phone's rows' `REMOTE_INTENT` on an association the Wear OS app really made. Task 6's and check 1's.
- **TalkBack on the release phone,** heard on the debug build in P5.6 only (Step 3's "Not run").
- **Candidate C itself.** Step 3's checks ran on the 6 November candidate, before Rulings 72–77. Today's candidate was installed, took a feed and drew the face, and no more: its rows, activation and screens were not run again. The cut gate and C are the Lead's.

### Checks

- `./gradlew :wearApp:testDebugUnitTest`: 612 tests, 0 failed.
- `scripts/check-versions.sh`: "Versions agree: 1.5.0 (41) on the phone, the face and iOS; 1.5.0 (100041) on the Galaxy watch app."
- `bash scripts/test-pairing-scripts.sh`: all passed, the new case with them.
- The whole gate runs at integration. `scripts/wear-old-emulator.sh` lands with this lane: `git diff watch-companion` shows it as new here, and the docs that name it are already on `watch-companion` (`tools/watchface/clock.sh`, plan 5, the clock-form report) or land here (this report, and a row in `tools/watchface/README.md`).
- No visible word, no string resource, no version bump, no face file, no trailer. No native-review marker came back: outside `docs/`, a `git grep` for it prints nothing (in `docs/`, the plans and reports still name it). Rulings 39, 41, 42 and 43 are untouched: the stored feed in `step-2/` carries the city and the whole-degree bearing, and no coordinates.
