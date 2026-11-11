# Plan 5 Task 3: R8 for the watch app, a staging build, and release builds that cannot sign by accident

Lane W, 31 October 2026, on `watch-wear` from base `8aea9361` (`git merge --ff-only watch-companion`
from `630b7565`). The evidence is in `2026-10-31-wear-r8-staging/`, and the device turn's drivers are
in its `drivers/` folder, so the turn can be run again.

## What was built

- **`:wearApp`'s build types** (`wearApp/build.gradle.kts`):
  - `release`: `isMinifyEnabled = true`, `isShrinkResources = false` (the phone's lesson), with
    `proguard-android-optimize.txt` and `wearApp/proguard-rules.pro`. That file holds a comment and no
    rule. None was needed: Step 4 found nothing stripped.
  - Signing: the upload key named by `keystore.properties`, read as `:androidApp` reads it, or no
    signing at all.
  - `staging`: `initWith(release)`, signed with the debug key, `matchingFallbacks += "release"`.
    It takes `src/release/kotlin`, the release build's seams (`FeedDrop`, `QiblaLog`), and none of
    `src/debug`.
  - Lint's `NotShrinkingResources` is disabled with its reason. It was the one warning, and only
    on the chosen `isShrinkResources = false`.
- **`taqwa.unsignedRelease`**, the same lines in `androidApp/build.gradle.kts` and
  `wearApp/build.gradle.kts`:
  - `true`: `keystore.properties` is not read and no `release` signing config is made;
  - absent or `false`: as before;
  - any other value stops the build (`taqwa.unsignedRelease is true or false, not 'yes'`), so a
    misspelt value never signs.
- **`wearApp/permissions.txt`**: VIBRATE (`manifest`) and androidx.core's
  `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (`library:androidx.core:core`).
  Its header avoids the privacy sweep's words: a first wording ("Never INTERNET …") was caught by
  `tools/privacy-check.py`'s sweep, and was reworded rather than added to its list.
- **`tools/wear-release-check.sh <variant>`** checks four things and exits 1 on any failure:
  - the mapping exists;
  - there is no `DEBUG_FEED`, `SampleFeed` or `sample-feed` in the dex. It also checks that no file
    of the APK is a sample feed or a test vector (Ruling 55 item 6);
  - the APK's own merged manifest asks for exactly `permissions.txt`'s names (`aapt2 dump
    permissions`, newest build-tools);
  - it prints the size.
- **`scripts/test-unsigned-release.sh`** runs as the plan writes it:
  - it refuses to start, with exit 2, while a `keystore.properties` file or link is in the root;
  - it writes the decoy and removes it with a `trap`.
- **The launcher icon** is the phone's own, copied byte for byte:
  - `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`, the adaptive icon. With `minSdk`
    30 it needs no `-v26` folder and no bitmaps, and the watch's launcher masks it round;
  - `drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`;
  - `values/ic_launcher_background.xml`;
  - `android:icon` and `android:roundIcon` in the manifest.
- **The splash:**
  - `values/themes.xml` defines `Theme.Taqwa.Starting` (parent `Theme.SplashScreen`): `#000000`,
    `@drawable/splash_icon`, and `postSplashScreenTheme` `@android:style/Theme.DeviceDefault`, the
    theme the activity had before;
  - `drawable/splash_icon.xml` is the launcher icon itself, `@mipmap/ic_launcher` in a layer-list
    at 48 dp, as Google's Wear splash page draws a round icon: the white disc, the dark arch and the
    amber lamp. (Until review round 1 it was the foreground's two paths in the app's ink with no
    disc, which did not match the launcher icon; see "Review round 1" below.)
  - `WearMainActivity` calls `installSplashScreen()` before `super.onCreate`;
  - the activity's `android:theme` is the splash theme;
  - `androidx.core:core-splashscreen` 1.2.0 is the latest stable (Google Maven, 5 November 2025).
- **Tests** (`WearManifestTest`):
  - `theLauncherStartsOnTheSplashTheme`, as the plan writes it;
  - `theLauncherIconIsThePhones`, one more: the icon files equal the phone's, so "made from the
    phone's own" stays true, and (since review round 1) the disc is `#FFFFFF`, the arch `#16160F`
    and the lamp `#E3A21C`;
  - `theSplashIsTheLauncherIconAt48dp` (review round 1): the splash is a layer-list of the
    manifest's `android:icon` at 48 dp, centred, with no paths of its own, and the round icon is the
    same drawing.

## Test first (Step 1)

At WIP `f3fd0539`, everything was red for the reason expected:
- `tools/wear-release-check.sh staging`: "expected one APK in wearApp/build/outputs/apk/staging,
  found 0", exit 1.
- `scripts/test-unsigned-release.sh`, 4 failed:
  - with the property, `:androidApp:validateSigningRelease` failed on `/nonexistent/decoy.jks`, so
    no APK was made;
  - without it, `:wearApp did not try the decoy's signing config`.
- `theLauncherStartsOnTheSplashTheme`: `expected:<@style/Theme.Taqwa.Starting> but was:<>`.
- `theLauncherIconIsThePhones`, red first in the same way before the icon existed:
  `expected:<@mipmap/ic_launcher> but was:<>`.

## The checks (Step 3)

`./gradlew :wearApp:testDebugUnitTest :wearApp:assembleStaging :wearApp:minifyReleaseWithR8
:wearApp:lintDebug -Ptaqwa.unsignedRelease=true`: BUILD SUCCESSFUL.
- wearApp tests: 513 run, 0 failed.
- Lint: "No issues found", after the one `NotShrinkingResources` warning was disabled. The launcher
  icon is in, and `MissingApplicationIcon`, plan 1's parked warning, is gone.

`tools/wear-release-check.sh staging` and `release`: PASS (`check-staging.txt`,
`check-release.txt`).

| Variant | APK | Size |
|---|---|---|
| staging | `wearApp-staging.apk`, signed with the debug key | 4,453,616 B (4.2 MB) |
| release | `wearApp-release-unsigned.apk` | 4,441,328 B (4.2 MB) |
| debug, for comparison | not minified | 37,799,468 B (36.0 MB) |

Plan 1 measured 27 MB unminified.

The check bites:
- On the debug APK it fails three times (`check-debug.txt`): no mapping; `1 DEBUG_FEED 16
  SampleFeed 2 sample-feed` in the dex; the four `assets/sample-feed-capetown*.json`.
- With `permissions.txt` changed to list INTERNET in place of VIBRATE, it names both sides
  (`check-permissions-mutant.txt`).

`scripts/test-unsigned-release.sh`: PASS, 8 ok (`test-unsigned-release.txt`):
- both release builds pass beside the decoy;
- both outputs are named `*-release-unsigned.apk`;
- `apksigner verify` gives "DOES NOT VERIFY" with "Missing META-INF/MANIFEST.MF" on each;
- without the property, the build fails on `/nonexistent/decoy.jks`, in `:androidApp:` and in
  `:wearApp:validateSigningRelease`.

The refusals were tried too (`test-unsigned-refusal.txt`), and the decoy was gone after every run:
- a dangling `keystore.properties` link: exit 2, the link untouched;
- a plain file: exit 2, the file untouched.

`scripts/test.sh` as a whole is given in the ledger line that closes this task.

### The library checks (plan 2's three) for `core-splashscreen` 1.2.0

- **The release runtime classpath** (`library-deps-diff.txt`), all AndroidX:
  - `core-splashscreen` 1.2.0 joins;
  - `appcompat` and `appcompat-resources` move from 1.1.0 to 1.7.0. Its POM names
    `appcompat-resources` 1.7.0, and appcompat 1.1.0 was already on the classpath;
  - `fragment` moves from 1.1.0 to 1.5.4;
  - `emoji2-views-helper` 1.4.0 and `resourceinspection-annotation` 1.0.1 join.
- **The merged release manifest** (`library-manifest-diff.txt`):
  - no addition from the library;
  - only this task's own lines: the icon, the round icon and the activity's theme;
  - Play services' `GoogleApiActivity` and version meta-data merged in another order, unchanged.
- **Permissions:** unchanged. `wear-release-check.sh` holds them to `permissions.txt`.
- **Trackers:** plan 2 Task 5 Step 1's pattern over `apkanalyzer dex packages --defined-only`, on the
  debug APK before (10:51, `wearApp-debug.apk`) and after.
  - `comm -13` of the tracker lines prints nothing (`library-new-trackers.txt`). The 11 lines before
    are the 11 after.
  - The new packages are `androidx.core.splashscreen`, `androidx.emoji2.viewsintegration`,
    `androidx.fragment.app.strictmode` and `androidx.resourceinspection(.annotation)`.
  - No Firebase, measurement, ads or crash-reporting code.

## On the paired emulators (Step 4)

The device turn ran 16:26-16:37 SAST under `wear-5558`, then `phone-5562`. Both emulators were found
running and paired, and both were left running and paired: WearableService "1 connected out of 1" on
each.

**Found:**
- the watch (`state-found.txt`):
  - the debug app `2b2d77c9` (100038) and the face `d4f50b1d`;
  - the sample feed `0b3f92ef`;
  - automatic time, font 1.0, TalkBack off, en-US, 0 Tiles, 0 alarms, Arch and Amber, slots empty;
- the phone: **without Taqwa**, as P2.7 left it.

So plan 2's debug phone app (this tree's `androidApp-debug.apk`) was installed on `emulator-5562` for
the turn, and uninstalled after it.

1. **The staging APK over the debug one, with the same key:**
   - `adb -s emulator-5558 install -r`: Success;
   - installed sha1 `79597cae`, the build's;
   - flags `[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`: not debuggable, as a store build.
2. **The phone's real feed** (`feed-watch.txt`, `feed-phone.txt`). The harness placed London
   (`place … London,2643743`), and the R8 build received the feed and answered:

   ```
   watch 16:28:06.906  TaqwaWearFeed ASK PHONE_NEARBY sent
   watch 16:28:10.456  TaqwaWearFeed ACCEPTED 2/6efab8e0696fdd95
   watch 16:28:10.457  TaqwaWearFeed ACK 2/6efab8e0696fdd95 sent
   watch 16:28:10.605  TaqwaWearFeed ACCEPTED 3/6efab8e0696fdd95
   phone 16:28:10.895  TaqwaWatch WATCH_ASKED SENT 2745ms
   phone 16:28:10.946  TaqwaWatch ACK 2/6efab8e0696fdd95
   phone 16:28:10.992  TaqwaWatch RESCHEDULED SENT 96ms
   phone 16:28:11.079  TaqwaWatch ACK 3/6efab8e0696fdd95
   ```

   - The watch's own handshake was answered (`WATCH_ASKED`), then the reschedule. Both were
     ACCEPTED and acknowledged, with the fingerprint the same on both sides. The feed's
     serializers, the Data Layer listener and the ack all survive R8.
   - The crash buffer was empty after the feed, and again after the pages.
3. **The face draws, against the debug build at the same moment:**
   - `face-staging-awake.png`: 16:30:00, Arch, "19 Jumada al-Ula · SAT 31 OCT", "ASR 14:47 in 17
     min", London's Asr in London's clock (16:47 SAST).
   - The same moment on this tree's own debug build (`3a323d93`, 100039), installed over staging
     with its data kept, so with the same phone feed: `face-debug-awake.png`.
   - **0 pixels differ, worst level 0**, within plan 1's 3 levels.
   - The found debug APK (100038) could not take this shot. It is a downgrade, which a user build
     refuses over a build that is not debuggable. The first try's install failed, and its shot,
     still of staging, was thrown away.
4. **Complications:**
   - The face's fixed slots are all Taqwa's sources: the dates, the next prayer and its countdown.
     They draw from the staging build (above).
   - The wearer's two slots were given Taqwa's pickable sources through the system's own chooser
     (`choose.sh left next right taqwa`, the chooser's "Next prayer, Taqwa" and "Taqwa, Taqwa").
     The face then drew "14:47" and the arch mark in them (`slots-staging.png`).
   - The slots were emptied again after.
   - Not done: a face of Google's own (Utility) carrying them. No driver opens Utility's editor, and
     the sources' code is the same whichever face asks (the Lead's call; see below).
5. **The Tile:**
   - added (`DEBUG_SURFACE add-tile`) and found by its city on the first try (`tile-staging.png`);
   - its tree reads "Asr in 16 minutes, at 14:47", then "Fajr, 5:12", "Sunrise, 6:49", "Dhuhr,
     11:49" and so on.
6. **Plan 3's three screens,** each opened afresh (`app-*-staging.png`, crash buffer empty):
   - Today reads "Asr in 16 minutes, at 14:47";
   - Tasbeeh reads "SubhanAllah";
   - Qibla reads "Makkah · 4,800 km away", with "Hold your wrist level" (the emulator's
     accelerometer as found).
7. **A cold start** (`am force-stop`, then `am start -W` under `screenrecord --time-limit 3`):
   - `LaunchState: COLD`, `TotalTime: 109` ms (`cold-start.txt`);
   - the frames, pulled with AVFoundation (`drivers/frames.swift`, since no ffmpeg is installed):
     `cold-start-sheet.png` every 100 ms, and `cold-start-splash.png` every 20 ms from 300 to
     620 ms;
   - the launch opens over the face. From about 340 ms the arch mark in ink with its amber lamp
     shows, alone on black at 420-440 ms (`cold-start-420ms.png`), then black at 460 ms. These
     shots are c6202bee's splash; review round 1's are in `review1/`;
   - Today then fades in, "ASR IN 0:10:47", full from about 620 ms.
   - Wear OS 6 (API 36) draws this splash itself, from the theme's attributes. Wear OS 3's compat
     path, the one `core-splashscreen` draws, is seen on the API 30 emulator in Task 12.
8. **Put back:**
   - this tree's debug build was over staging, so `restore.sh` could install the found 100038 with
     `-d` (a downgrade is allowed over a debuggable build);
   - restore.sh then put back the files byte for byte, the face, automatic time, and no Tile:
     **STATE AS FOUND** (`state-after.txt`, `left-as-found.png`), with a later shot at 16:37:11;
   - the phone's Taqwa was uninstalled (0 installed), as found;
   - the phone shows Play's "google pixel watch" search page in front. Its front screen was not
     recorded before the turn, and nothing in this run opens Play; it fits Mohamed's sign-in session.
   - KB2604110095 and the work iPhone 12 were untouched. No emulator was stopped.

## Errata and rulings

- **Erratum 1:** `git merge --ff-only watch-companion`, `630b7565..8aea9361`.
- **Erratum 2:** no `keystore.properties` or `~/keys` was read or used. Release builds ran only with
  `-Ptaqwa.unsignedRelease=true` or beside the test's decoy. Nothing was signed but the staging and
  debug builds, with the debug key. There is no version bump: 1.3.0 (39)/(100039) stays.
- **Erratum 3:** 5558 and 5562 were found running and paired, and left so. Neither was stopped, and
  nothing signed in.
- **Erratum 5 (p3):** no p3 entry touches this task.
  - Plan 5's own erratum 8 (p5, `wear-sdk`) belongs to Task 5. Today's merged manifest already holds
    `uses-library wear-sdk required="false"` from the Tiles library.
  - When Watch Face Push joins, Task 5 adds the assertion of that line to `tools/wear-release-check.sh`.
- **Erratum 6:**
  - Rulings 39, 41, 42, 43 and 55-70: nothing here changes a promise, the face or the reading order;
  - no face file changed, so the face tools' `--check` and the always-on lit share are untouched;
  - the splash is the launcher icon on black (review round 1).
- **Erratum 7 (Ruling 55 item 6):** already done (`fa62c182`). The debug APK carries only the four
  sample feeds, and `wear-release-check.sh` now also fails a release-type APK that carries one, or
  any test vector.
- **Erratum 9:** `wear-5558` was held for the turn, then `phone-5562`. Installs used `adb -s … install
  -r`, never Gradle. No hang. The left-as-found shots were taken a few seconds after restoring.
- **Erratum 10:** no flake was seen in this task's runs.

## For the Lead

1. **A system face.** Step 4 asks for "the complications on a system face". They were seen in the
   Taqwa face's fixed slots, and in its wearer's slots through the system's chooser, not on Utility.
   - Default: Task 12's release-candidate pass puts "Next prayer, Taqwa" on Utility by hand once.
2. **The splash on Wear OS 3.** The compat splash on API 30, `core-splashscreen`'s own drawing, is
   only seen when Task 12 boots `Taqwa_Wear_OS_3`. There the library puts the splash's drawable in
   its 90 dp watch box (`values-watch-v20`), and the layer-list's 48 dp item sits in its middle, so
   the disc is expected at 48 dp, inside the library's 60 dp round window. Task 12 shoots it.
3. **The splash and the launcher icon (review round 1).** The splash is now the launcher icon
   itself: the white disc, the dark arch and the amber lamp, at 48 dp on black, as WO-V15 and the
   branded launch guide ask ("The splash screen icon must match the app launcher icon"). This is
   option (a) of the review's three, built as the default because it is the form Google's own Wear
   splash page gives and it changes no design. Its cost is a 48 dp white disc on black for the
   moment of a cold start, the same disc the app list shows. The other two stay open for the Lead:
   - (b) a dark disc for both the launcher icon and the splash: a design change, Mohamed's call,
     and the phone's icon would no longer be the watch's (`theLauncherIconIsThePhones`);
   - (c) a numbered ruling that goes back to c6202bee's light arch with no disc, accepting the
     WO-V15 risk at Play's Wear review.
   Either is a change to `splash_icon.xml` (or the icon's colour) and `WearManifestTest`'s
   expected colours, nothing else.
4. **Wear OS 6 draws the splash's drawable at 1.25 times its size** (review round 1, measured).
   Held still, the 48 dp splash is a 120 px disc on the 320 dpi emulator, 60 dp; a 36 dp mutant is
   90 px, 45 dp. So the system scales whatever drawable it is given, Google's sample included,
   and the 48 dp is the drawable's, as Google's page writes it. Tuning it to 38.4 dp to measure 48
   dp here would leave Google's form and could be wrong on another Wear OS version; not done.

## Review round 1

The code lens found one Important: the splash's icon did not match the launcher icon. c6202bee drew
the launcher foreground's arch in the app's light ink with no disc, while the launcher icon is a
white disc with a dark arch. Google's quality item WO-V15 says "The splash screen icon must match the
app launcher icon", and its branded launch guide asks for "a 48x48dp circular icon"; the plan quotes
the same. The test compared only the paths, so it could not see it.

**What changed** (option (a), the default; For the Lead item 3 keeps (b) and (c) open):
- `drawable/splash_icon.xml` is Google's own form for a round icon: a layer-list whose one item is
  `@mipmap/ic_launcher` at 48 dp, centred. The splash is therefore the launcher icon itself, masked
  round by the watch as in its app list, and it cannot drift from it.
- `Theme.Taqwa.Starting` keeps the parent `Theme.SplashScreen` (a round icon's), with no
  `windowSplashScreenIconBackgroundColor`, which would put a second disc behind it.
- The comments in `themes.xml`, the manifest and `WearMainActivity` say "the launcher icon itself"
  in place of "the launcher icon's mark".

**Tests, first red** (`WearManifestTest`, 5 tests):
- `theSplashIsTheLauncherIconAt48dp`, new, failed on c6202bee's splash: "the launcher icon wrapped
  at a size, not a drawing of its own expected:<[layer-list]> but was:<[vector]>".
- `theLauncherIconIsThePhones` now holds the disc `#FFFFFF`, the arch `#16160F` and the lamp
  `#E3A21C`, and that the icon is an adaptive icon with a colour background, which the mask cuts
  into a full disc.
- `theLauncherStartsOnTheSplashTheme` now holds the parent `Theme.SplashScreen`, no icon background
  colour, and a `postSplashScreenTheme` equal to the application's theme (it held only "not empty":
  the code lens's minor).
- Six mutants, each red (`review1/test-mutants.txt`): c6202bee's inverted vector, the IconBackground
  theme with a background colour, the item at 90 dp, the item as the foreground alone, a dark disc
  on the phone and the watch together, and another `postSplashScreenTheme`.

**Checks** (in the worktree, no keystore.properties, `-Ptaqwa.unsignedRelease=true`):
- `:wearApp:testDebugUnitTest` 514 run, 0 failed (513 before, plus the new test);
  `assembleStaging`, `assembleRelease`, `lintDebug` and `lintRelease`: "No issues found";
- `tools/wear-release-check.sh staging` PASS, 4,453,308 bytes (308 fewer: the vector is gone);
  `release` PASS, 4,441,020 bytes.

**On the watch** (`emulator-5558` under the `wear-5558` lock, 17:10-17:19; the phone not used):
- Staging (7599164c) over the found debug app, cold start under `screenrecord`: `COLD`,
  `TotalTime: 104` (`review1/cold-start.txt`). The white disc with the dark arch and the amber lamp
  grows in over the face from about 330 ms and leaves by 420 ms (`review1/cold-start-sheet.png`).
  The app is ready before the system's entry animation ends, so the splash is never still there.
- Held still (`review1/drivers/hold-splash.sh`: `am start -D` on this tree's debug build, then jdb
  stops `TaqwaWearApp.onCreate`, so the "Waiting For Debugger" dialog goes and the system's splash
  window is alone): `review1/splash-held-48dp.png`, a round white disc, 120 x 120 px, centred at
  (226.5, 226.5) on black, with the dark arch and the amber lamp (`#E3A21C` among its colours). A
  36 dp mutant, built and held the same way and never committed, is 90 x 90 px
  (`review1/splash-held-36dp-mutant.png`): Wear OS 6 draws the drawable 1.25 times its size (For the
  Lead item 4).
- The launcher icon in the watch's app grid (`review1/launcher-icon-app-list.png`, staging
  installed): the same white disc, dark arch and amber lamp. This also answers the spec lens's
  minor that no shot showed the icon in the app list.
- Put back: this tree's debug build over staging (so the found 100038 could go back with `-d`; a
  first try straight over the non-debuggable staging was refused as a downgrade, and changed
  nothing), then `restore.sh`: STATE AS FOUND (`review1/state-found.txt`, `review1/state-after.txt`,
  `review1/left-as-found.png`: app 2b2d77c9 100038, face d4f50b1d, sample feed 0b3f92ef, Arch Amber,
  slots empty). The animation scales, set to 10 for one slow recording (in which the system skipped
  the splash), went back to 1.0 as found; the jdwp forward was removed and the debug app cleared.

**Minors left** (recorded in the ledger): the code lens's stale-mapping check (R8's pg-map-id is in
the dex), `SampleFeed` inert on an R8 dex and no merged-manifest check for the debug receivers,
`ApiException` logged as `bb` on R8 builds, and the decoy write that could follow a link (`set -C`);
the spec lens's system-face complications (For
the Lead 1), the dex scan resting on two string literals, the check's usage omitting
`-Ptaqwa.unsignedRelease=true`, and staging taking only `src/release/kotlin`. The spec lens's API 30
icon size is For the Lead item 2, and its app-list shot is above. The code lens's minor that the
driver's `debug` step differs from what ran is fixed: this round hit it (the downgrade refused over
staging), and `drivers/staging.sh debug` now installs this tree's debug build first, as the turn did.
