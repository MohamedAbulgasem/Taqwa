# Watch companion 5: ship, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** the watch companion reaches both stores in the watch release. That covers:
- the Taqwa face installed, updated and made active by the Galaxy watch app through Watch Face Push, signed with Mohamed's own face key;
- the phone's watch rows;
- every new word in seven languages, after native review;
- Ruling 39's privacy text everywhere it lives, and the stores' privacy forms answered again;
- the README, the site, the store copy and the release notes;
- one version scheme for all four builds, and R8 on the watch app;
- his Galaxy Watch 8 Classic and his wife's Apple Watch passed by hand;
- the watch screenshots, and the store steps only Mohamed can do.

**Architecture:**
- **Watch Face Push, in `:wearApp`:**
  - The release face is signed by Mohamed alone (`scripts/release-face.sh`) and given its validation token by Google's validator in the release path (`tools/watchface/push-token.sh`).
  - It is bundled as Google's *default watch face*: `assets/default_watchface.apk`, with the token in `@string/default_wf_token`. The system installs it into Taqwa's one slot when the watch app installs, and never makes it active.
  - A small `FacePush` layer over `WatchFacePushManager`, one per process and one call at a time, updates the face after each app update (`MY_PACKAGE_REPLACED`). It makes the face active once, on the wearer's tap, behind the runtime permission. It publishes a Data Layer capability, `taqwa_face_addable`, while the face is not active and its one activation is unspent (the Lead's call 5, question 11).
- **The phone (`:shared`):** a "Watch" section in Settings, in plan 4's `feature/settings/WatchRows.kt`.
  - Android: "Install on your watch" opens Taqwa's Play page on the watch, and "Add the Taqwa face" opens the watch's face screen (`taqwa://face`), both through `RemoteActivityHelper`.
  - iPhone: the section holds plan 4's "Add to Apple Watch" row, shown while plan 4's offer is available.
- **Release:** `scripts/release.sh` builds and checks the phone and the watch bundles together, and `scripts/ios-release.sh` checks the archived watchOS bundles with plan 4's `check-watch-bundle.sh`, at the iPhone's version. A dry-run mode (`-Ptaqwa.unsignedRelease=true`, honoured by both app modules) lets agents run the whole path with test keys only.
- **Candidates:** each release candidate takes the next version code (C, C+1, …), and is cut only past the cut gate: every privacy line applied as ruled or approved, and every new word reviewed. Each later candidate carries the fixes since the one before it, with the checks each fix asks for run again on it. Production is the last candidate that passes, delivered by Play over the one before it (the Lead's call 7).
- **Words:** the watch apps' own resources (the face editor, the pickers, the first-run lines) get the six other languages. `tools/i18n-check.py` learns the watch roots. One native review round covers every new phrase of plans 2–5.

**Tech stack:**
- Kotlin 2.4.10 and AGP 9.4.0 (the variant API's `sources.assets` and `sources.res` generated directories);
- `androidx.wear.watchfacepush:watchfacepush`: 1.0.0 (stable, 8 April 2026, AAR `minSdk` 33, `isSupported()` false below API 36), or a newer stable on the day, recorded in Task 5;
- `androidx.wear:wear-remote-interactions`, the newest stable, on the phone (spec 5's `RemoteActivityHelper`);
- `androidx.core:core-splashscreen`, the newest stable, on the watch (Task 3);
- Google's `validator-push-cli` and `wff-validator`, pinned in `tools/watchface/validate.sh`;
- `apksigner` and `aapt2` from the newest build-tools; Xcode 27.1;
- the Wear OS 6 emulator `Taqwa_Wear_OS_6`, plan 2's paired phone emulator `Taqwa_Phone_Wear`, and two new emulators: Wear OS 4 (API 33) and Wear OS 3 (API 30).

**Spec:** `docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`:
- 2.1: listing, version codes, permissions;
- 3.1: Watch Face Push, keys, the "Add the Taqwa face" row;
- 1.3: the watch system's own words in seven languages;
- 4.1: one upload;
- 5: the phone rows and the docs;
- 6: the devices.

Binding rulings are in `.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`:
- 22: the 12-hour observation is a ship gate;
- 31: Samsung's picker hides the day dial;
- 33–35: the Hijri steps, the weekday sizes, the Moon's sweep;
- 36: the Moon's option (d);
- 38: screen readers are a ship gate;
- 39, as amended: the privacy wording and what the feed may carry;
- 42 (which replaced 40): the face's reading order.

Plan 2's section "Ruling 39: what leaves the phone, and what the promises say" and its Task 3 Step 7 report (`docs/superpowers/reports/2026-10-23-watch-privacy-wording.md`) are this plan's list of privacy lines.

**The series:** this is plan 5 of 5. It ships what plans 1–4 built. `docs/superpowers/plans/2026-10-24-watch-companion-execution-map.md` sets the order and the worktrees, from these facts:
- **Tasks 1 and 2 run beside plans 2–4:** Task 1 after plan 1's Task 12 closes, and Task 2 after plan 1's Task 13 (the Moon's filled always-on hands, Ruling 41) closes too, because that task redraws the face's pictures and runs its validators:
  - Task 2 touches only the face's release scripts and tools. It edits `tools/watchface/validate.sh` and `tools/face-art.py`, and its font pull (Step 3) takes a turn on `emulator-5558`, never while plan 2's Task 7 holds it.
  - Task 1 edits `watchface/build.gradle.kts` and the version lines of `wearApp/build.gradle.kts`, which plan 3 gives to its Tasks 4 and 8, so the map names those lines as shared. It does not wait for plan 4: whichever of Task 1 and plan 4's Task 4 lands second puts plan 4's two watch plists in `IOS_PLISTS`, and `check-versions.sh` fails until it does.
- **Tasks 3, 4 and 5 run after plans 2 and 3 are built, in that order, and Task 4 only after Tasks 1 and 2** (Task 2's `push-token.sh` makes the bundled face's token). Task 4's `check-bundle.sh` needs the face's version code to be the Wear app's minus 100000, and Task 5 Step 7 needs `bump-version.sh` to write the face. Both are Task 1's. Today the face is `versionCode = 1` (`watchface/build.gradle.kts:29`) and the Wear app 100038 (`wearApp/build.gradle.kts:46`).
  - Task 3's emulator check needs the Tile, plan 3's three screens and plan 2's debug phone app sending a real feed.
  - Tasks 3–5 edit `wearApp/src/main/AndroidManifest.xml`, `wearApp/build.gradle.kts` and plan 3's `WearManifestTest`, which plan 3 gives to its own Task 4 and Task 8 alone. The map must say these files are shared.
  - After Task 3, plan 3's merged-manifest checks are `tools/wear-release-check.sh release`, and its `WearManifestTest` reads `wearApp/permissions.txt` (Task 4). A later re-run of a plan 3 step uses them.
- **Every other task needs plans 2–4 built.**
- **Emulators:** `emulator-5558` and `emulator-5562` are plan 2's Task 7's alone while it runs. Otherwise plan 3's emulator steps and this plan's take turns on 5558.
- Where this plan names a file of plan 3 or 4 that has not been written yet, the name that plan's Interfaces block gives wins, and the map aligns it.

## Global Constraints

- **Where:**
  - The worktree is `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site`, branch `watch-companion`, or the worktree the map assigns.
  - Start every shell with `source ~/.zprofile; source ~/.zshrc`, and run Gradle from the worktree root.
- **Commits:**
  - Commit with `git commit -F <file>`, then check `git log -1`.
  - Commits carry no `Co-Authored-By` and no AI attribution line, ever.
  - Make WIP commits as you go.
  - Nothing is pushed until Mohamed says so in chat.
- **Ledger:** append short dated lines to the ledger under a "Plan 5" heading, as each task starts and ends, and for every flag this plan asks for.
- **The release:**
  - The watch release is version **V**. V is the next minor version after the last store build, because the watch is a feature (the rule in `scripts/bump-version.sh`). The expected value is 1.4.0 (build 39 shipped as 1.3.0 (39)).
  - **Candidates:** each release candidate takes the next version code, C, C+1, C+2 and so on, set by `scripts/bump-version.sh V <code>`. C is the code after the last store build's, expected 40. For every candidate the Wear app is **100000 + its code**, the face its code, and the watchOS app V (its code).
  - **A code is never reused.** Play refuses a code it has seen on any track, and Watch Face Push updates only a face older than the bundled one (Task 5's `theSameOrANewerFaceIsLeftAlone`). So a change made after a candidate reaches a device only in a later candidate.
  - **The cut gate.** No candidate is cut until:
    - Task 9 has applied plan 2's items 1–3, each as ruled or as Mohamed approved it (M3), and `python3 tools/privacy-check.py` exits 0, with no row awaiting his approval;
    - Task 11 has removed every native-review marker, his English and Arabic reading included: `git grep "draft, to native review" -- ':!docs'` prints nothing (the plans and reports under `docs/` name the marker);
    - Tasks 1–8 and 10 are closed, and Task 12's Steps 0, 1 and 3, with the Lead's call 6 and its follow-up.
  - **Production** is the last candidate that passed every check Task 13 Step 6 asks of it, and that Play delivered on his watch over the candidate before it (Task 17 Step 3). The expected value is 1.4.0 (41).
- **Packages:**
  - The phone and the Wear app are `world.taqwa.app`, and the face is `world.taqwa.app.watchfacepush.taqwa`.
  - The Wear app is signed with the phone's upload key, and Play re-signs both with the app-signing key.
  - The face is signed with **Mohamed's face key**, which is never the app's key.
- **Keys:**
  - Agents never open, print, copy or create `keystore.properties`, anything in `~/keys`, Mohamed's face key or its password. Mohamed types the face key's password himself, every time.
  - **The real `scripts/release.sh` is run by the Lead alone,** never by a task's subagent. Mohamed keeps `keystore.properties` in the main checkout (`~/Desktop/Workspace/apps/Taqwa/keystore.properties`). For that one build the Lead symlinks it into the worktree's root, then removes the link straight after, as for build 38 (`.superpowers/sdd/2026-10-17-build-38/progress.md`, line 97). Gradle reads it, and no agent does.
  - The one other `keystore.properties` an agent writes is the **decoy** in `scripts/test-unsigned-release.sh` and `scripts/test-release.sh`. It holds no secret (`storeFile=/nonexistent/decoy.jks`) and is removed by a `trap`. Both scripts refuse to start while any `keystore.properties`, or a link to one, is in the root.
  - **`-Ptaqwa.unsignedRelease=true`** (Task 3) makes both `:androidApp` and `:wearApp` set no release `signingConfig`, whatever `keystore.properties` says. Outside the real `release.sh`, every release-type build (`assembleRelease`, `bundleRelease`, `minifyReleaseWithR8`, the staging build) passes it once Task 3 has landed, and the dry run passes it always. Before Task 3, plan 3's release builds run only where no `keystore.properties` is, as in this worktree.
  - Only the real `release.sh` and its dry run pass `-Ptaqwa.storeRelease=true` (Task 4), and Task 4 Step 4's check that it fails without a face, unsigned.
  - Agents prove the release path only with the throwaway test key (`~/.android/taqwa-face-debug.keystore`), keys made inside the session scratchpad, and the dry-run mode (Task 7).
- **Uploads:** an upload to App Store Connect (`scripts/ios-release.sh`) needs Mohamed's OK in chat, each time. Every Play Console action is his.
- **Devices:**
  - The plugged-in phone `KB2604110095`: never install on it or touch it.
  - His Galaxy Watch 8 Classic is reached only over wireless debugging, after he pairs it (Task 13), by its `<ip>:<port>` serial.
  - His S23 and every iPhone are his hands only.
  - **The work iPhone 12 never takes TestFlight.** Mohamed runs the release candidate on it from Xcode (Run), as in the 1.2.0 smoke test (Task 13's phone smoke test).
  - Her iPhone and Apple Watch: TestFlight on her iPhone, or a Debug build from Xcode once her devices are registered under team 3K93P4PA5H, with Mohamed's OK and his hands at the Mac (Task 15).
  - **The once-only activation:** a reinstall is not a reliable reset of the system's one activation per app. On a watch or emulator that has spent it, a tap that expected ACTIVE may show the guidance line. The check then records which it saw, and whether the ledger shows an earlier activation there. His Galaxy's first activation (Task 13 check 1) must be ACTIVE.
  - Every `adb` command names `-s <serial>`. Install with `adb -s <serial> install -r <apk>`, never with Gradle's `install*` tasks.
  - **From Task 4 on, the face reaches a device only through the Wear app.** Nothing installs `world.taqwa.app.watchfacepush.taqwa` directly. On `emulator-5558` that is `tools/watchface/push-face.sh` (Task 4). On a watch it is a candidate with a higher code, or the face removed in the watch's face list and added again by the app, which loses its style, colour and slots. A rebuild at the same code, installed with `-r`, leaves the old face in the slot.
- **Emulators:**
  - The Lead's phone is 5556, the Wear OS 6 emulator 5558, Dev 2's 5560, plan 2's paired phone `Taqwa_Phone_Wear` 5562, and plan 2's phone without Play services 5564.
  - This plan adds `Taqwa_Wear_OS_4` (API 33) on **5566** and `Taqwa_Wear_OS_3` (API 30) on **5568**.
  - `emulator-5558` and `emulator-5562` belong to plan 2's Task 7 alone while it runs.
  - Stop an emulator only with `adb -s emulator-55xx emu kill`, never with `pkill`.
- **Permissions:**
  - The phone gains none. Its merged release manifest keeps exactly the set in `androidApp/permissions.txt` (Task 7).
  - `:wearApp` declares **no `INTERNET` and no location permission**. Its merged release manifest holds exactly `wearApp/permissions.txt` (Task 3): androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `com.google.wear.permission.PUSH_WATCH_FACES`, `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE` (a runtime permission), and plan 3's `VIBRATE`.
  - `wearApp/permissions.txt` is the one source of truth. Plan 3's `WearManifestTest` (Task 4) and every merged-manifest check, plan 3's included (`tools/wear-release-check.sh`), read it. A permission is added to the file in the same commit as the manifest line.
  - The watchOS app asks for no location and no HealthKit authorisation (spec 4.1).
- **Libraries:** any new library passes plan 2's three checks before it is kept, and the result is recorded in the ledger:
  - the merged release manifest's permissions are unchanged, beyond those named here;
  - no manifest additions beyond the library's own non-exported components;
  - no Firebase, measurement, ads or crash-reporting code.

  Mohamed's rule: "as long as it doesn't add any trackers or any unnecessary permissions".
- **No time is computed on the watch.** Every instant is the phone's (app = site = watch). A device pass compares the watch with the phone's Prayer screen and the site's city page.
- **Languages:**
  - Seven: en, ar, fr, tr, id, ur, bn.
  - Android resources spell Indonesian `values-in`. The Compose resources keep `values-id` and its `values-in` twin. Write apostrophes as ’ (the house style), which neither aapt nor Compose needs escaped.
  - Each watch speaks its phone's interface language, which the app's `ui_language` resource gives, never the device locale. What the watch system itself shows (spec 1.3's list) is in the watch's own language.
- **Copy:**
  - The countdown reads "in 1 h 12 min", and in Arabic بعد 1 س 12 د, in the feed's digits.
  - The location promise is exactly **"Only your own devices can see your location."** (Ruling 39, amended).
- **Accessibility:** nothing at default settings changes what is drawn. Every screen and the face give real screen-reader text, and screen readers are a ship gate (Ruling 38).
- **The face's reading order is Ruling 42's** (which replaced Ruling 40), built by plan 1's Task 12 close (from WIP `07e511be`). The slots are declared 7, 0, 1, 2, 3, 6, 4, 5, so a swipe reads the time, the prayer with its countdown and time, the dates, then the wearer's slots. `SourceManifestTest.aScreenReaderIsGivenTheTimeThePrayerTheDatesAndTheWearersSlotsEachOnce` holds it. Only a new numbered ruling changes it (Task 14).

## Review Focus

1. **An app update over an installed, active, customised face.** After the Wear app updates from the store, the face in Taqwa's slot is the new one. It is still active, and it keeps the wearer's style, colour and slots. The face is never removed and added again, which would lose all three. The update starts in two places at once (the app's start and `MY_PACKAGE_REPLACED`), and only one of them updates. Tests: Task 5 `anOlderFaceIsUpdatedInItsSlot`, `twoConcurrentUpdatesUpdateOnce`, `anActiveFaceStaysActiveThroughItsUpdate`, `theSlotIsReadAgainBeforeEveryCall` and `noActivityCheckRightAfterAnUpdate`; Task 5 Step 7 check 4 on the emulator; on his watch, Task 17 Step 3's Play-delivered update of each candidate over the one before it, production's included, with the release signing.
2. **The face made active only once, and the permission refused only once.** Google allows one activation per app, and one refusal of `SET_PUSHED_WATCH_FACE_AS_ACTIVE`. After either, the wearer is told once how to choose the face himself, and both rows go quiet. There is never a crash, a loop of prompts, a row that stays forever, or a button that does nothing. Tests: Task 5 `anInstalledFaceIsOnlyMadeActive`, `aRefusedPermissionLeadsToChoosingItYourself`, `aSecondActivationLeadsToChoosingItYourself`, `aRemovedFaceIsAddedEvenWhenTheActivationIsSpent`, `theSpentActivationOutlivesTheProcess` and `theRowsGoQuietOnceTheActivationIsSpent`.
3. **A watch without Watch Face Push, or a Push service that answers an error** (Wear OS 3 to 5, or a Samsung build that throws). There is no face row and no capability, and the app, the Tile and the complications work. Below API 33 (Wear OS 3) the complications show no countdown, as the Lead's call 6 ruled ((b), Ruling 72 item 1): NextPrayer's long text is the prayer's time, every spoken line is the prayer and its time ("Asr, 16:10"), and the ring keeps its entry's start. Tests: Task 5 `belowWearOsSixThereIsNoRow` and `aPushThatThrowsShowsNoRow`; `ClockFormTest`, and `NextPrayerSourceTest` on Robolectric at API 30 and 33; Task 12's Wear OS 4 and Wear OS 3 emulators; Task 6 `aWatchWithTaqwaAndItsFaceShowsNothing`.
4. **A release with a stale face, a face signed with the wrong key, a token for another APK, or a dry run that signs with the upload key.** `release.sh` refuses to bundle the face, and the dry run never signs with the upload key. The token is always made from the very APK bundled. Tests: Task 3 `scripts/test-unsigned-release.sh`; Task 7 `aFaceSignedWithTheAppKeyIsRefused`, `aStaleSignedFaceIsRefused`, `aFaceSignedByAnUnpinnedKeyIsRefused`, `theBundledTokenIsTheBundledApks` and `theDryRunNeverUsesTheUploadKey`.
5. **R8, or Play's delivery, removing something only a store build needs:** the feed's serializers, a complication service's dependency, Push's classes, the bundled face in Play's split APKs, the log lines the 12-hour gate reads. That shows as a blank face or a crash on a store build alone. Tests: Task 3's staging build on the paired emulators, Task 12's dry-run release candidate, Task 13 on the release-signed build itself, and Task 17's Play-delivered build on his watch.

## The Lead's calls in this plan

Each default is what the tasks build. The Lead rules on each in the ledger before the task named.
1. **The face is Google's default watch face** (before Task 4). It installs with the watch app and is never made active by itself. The "Add the Taqwa face" row then only makes it active. If the wearer has removed the face, the row adds it again first. This is Mohamed's question 3, below.
2. **The face's version code is the phone's C** (Task 1). So every watch-app update updates the face in its slot, and Mohamed signs the face once per release (question 4).
3. **The face's words come from the feed** (Task 5), as plan 2's call 6 sets for every screen word. The watch's own resources hold the same five phrases for a watch that has no feed yet.
4. **The fit tools are a Mac-side release gate** (Task 2), with the emulator's own fonts pinned by hash, rather than CI with fonts fetched from notofonts. The tools were calibrated on the emulator's fonts, and a CI copy could differ.
5. **The two "Add the Taqwa face" rows** (Task 5, Task 6). This is question 11: the phone's row is not in spec 5, and Today's row goes beyond spec 3.1's "when the face isn't installed", because the default face is installed from the start.
   - Today's chip, and the phone's row while the watch publishes `taqwa_face_addable`, show only while the face is not active and the one activation is unspent: `FaceCapability.wanted(state, spent)`.
   - After one tap that activates, is refused, or finds the activation spent, both go quiet for good. A wearer who keeps Taqwa's complications on another face is never asked again.
   - The watch republishes at app start, on its face screen and Today, after an update and at plan 2's daily nudge, so the phone's row is at most a day stale. A stale row opens a watch screen that says the face is already on, or how to choose it.
6. **The Wear OS 3 floor** (Task 12 Step 1, before Task 17). `wearApp`'s `minSdk` is 30 (spec 2.1). Below API 33 the system cannot evaluate dynamic values (`NextPrayerSource`'s KDoc, `evaluatesDynamicValues`), so on any face the ring and the countdown keep the words of their entry for the whole prayer period: "in 3 h 10 min" for three hours. The Lead rules, as a numbered ruling, after the API 30 emulator run:
   - **(a), the default:** accept it, and the support answer (Task 10) says the countdown on Wear OS 3 moves only at each prayer;
   - **(b):** below API 33 the sources send a countdown-free text, the next prayer and its time ("Asr 16:10"), as plan 3's Tile does without dynamic values;
   - **(c):** raise `wearApp`'s `minSdk` to 33, which also drops the `tools:overrideLibrary` line. That changes spec 2.1, so it goes to Mohamed first (spec 8).
   - **Ruled: (b)** (Ruling 72 item 1, from Step 1's API 30 run), as built: below API 33 NextPrayer's long text is the prayer's time ("ASR" over "16:10"), and what the ring, the long text and the short text say is the prayer and its time joined by the phone's comma ("Asr, 16:10", Ruling 74 item 1), true however long an entry is shown. The ring keeps its value at the entry's start: stepping it would pass the timeline's budget of 2 to 16 entries (`ComplicationTimelinesTest.aTimelineIsAFewEntriesAndNotAMonthsWorth`). From API 33 nothing changes. The support answer's Wear OS 3 line stays (Ruling 74 item 6).
7. **The release candidates** (before Task 13), as the Global Constraints set them: each the next code, each cut past the cut gate, and production the last that passes.
   - C is the first: his Galaxy pass, the phones' smoke test, her first TestFlight pass and the closed tests.
   - C+1 is expected to be the last. It carries her watch's `.watchface` file (Task 15), the Moon's sweep as he chose it (Task 13 Step 5), Task 14 if it ran, and every fix found on C. It is not a version-only bump: its ledger line lists each change with the checks Task 13 Step 6 asks for, and those checks run on C+1 itself. It gets its own release-notes files (Task 10).
   - It goes to Play's internal tracks over C first, to prove a Play-delivered update, then to the closed tracks and TestFlight.
   - A failure on C+1 makes C+2 the same way, and so on. Production waits for a candidate that passes.
   - If her watch cannot make the file, C+1 is still made, for the fixes and the Play update check, and the Apple row ships hidden, reported to Mohamed (spec 4.5).

## Open questions for Mohamed

None blocks a task's start; each task builds the default.
1. **Her Apple Watch** (Task 15): the model, its watchOS version, and her iPhone's iOS version. They must meet plan 4's minimums. Default: SE 3 (spec), tested through TestFlight on her iPhone, in an external group "Family" with her Apple ID's email.
   - Plan 2's size gate needs one Debug run of plan 4's `WatchHarness` on her real pair. Default: he registers her iPhone and watch under team 3K93P4PA5H for that run, at the Mac, then TestFlight goes back on.
2. **His Galaxy Watch** (Task 13): which phone is it paired with? Default: his S23. The release candidate's phone APK goes on it the way the 1.2.0 smoke test installed build 38, and the watch app goes on the watch over wireless debugging.
3. **The default face** (the Lead's call 1): the Taqwa face appears in the watch's face list as soon as the watch app installs, never chosen for him. Default: yes. The alternative is to install it only when he taps "Add the Taqwa face".
4. **Signing the face each release:** he runs `scripts/release-face.sh` and types the face key's password, about a minute per release. Default: yes. The alternative is a git-ignored properties file like the upload key's, which would let the Lead's `release.sh` sign the face. That is against "only he handles" the key.
5. **The three face polish items from plan 1:** the Moon's arc on a clock-change night, a far-north twilight past the dial's midnight, and a spent Ring's missing track. Default: they go to the Ramadan build.
   - No clock change falls between mid-November and 14 March 2027.
   - The twilight case is a hairline drawn to the Isha mark in far-south summers.
   - A spent Ring needs two days without the phone.
   - The alternative is one more face task now, about a day.
6. **The Moon's seconds sweep** (Ruling 35), after his two battery days (Task 13). Default: keep 15 Hz if the Moon day costs under 3 points of battery more than the Arch day; otherwise 5 Hz.
7. **Store screenshots** (Task 16). Default: the Galaxy shots from his own watch, in all seven languages, with him switching the phone app's language. The Apple Watch shots come from the simulator at Apple's largest watch size, since her watch's own shots need two buttons a shot and AirDrop.
8. **The Wear OS release:** a closed test on Play's Wear OS track first. Default: the existing `taqwa-testers` group for three days, then production with the phone release.
9. **Exodus:** before release, a local check of both APKs against Exodus's public tracker signatures. After Play serves the build, the public report as before. Default: both.
10. **`androidx.wear:wear-remote-interactions`** on the phone, for the two rows: is it acceptable under his library rule once it passes the three checks? Default: yes. Spec 5 names `RemoteActivityHelper`.
11. **The "Add the Taqwa face" rows** (the Lead's call 5). Spec 5 names only "Install on your watch" for the Android phone, and spec 3.1 puts "Add the Taqwa face" on the watch's Today, when the face isn't installed.
    - Default: a phone row in Settings › Watch, and Today's chip, while the face is not active and its one activation is unspent. Both go quiet after the one tap, so a wearer who uses Taqwa's complications on another face is not asked again.
    - Alternative (b): no phone row. Today's chip as the default says.
    - Alternative (c): spec 3.1 as written. Today's chip only when the face is not installed, which with the default face means only after he removes it.
    - Until he answers, the default is built, and the phone row can be removed in one commit (Task 6's `addFace`).
12. **Plan 3's six defaults,** which plan 3 leaves for him to judge on his Galaxy (Task 13 check 6). Default: each stays as built. A change is a fix in the next candidate (Task 13 Step 6).
    - The phrase list is reached by a swipe up from the counter. Alternative: a long press.
    - A "Reset" row ends the phrase list, with the phone's question. Alternative: no reset on the watch.
    - The watch's own screen timeout runs during Tasbeeh. Alternative: the screen kept on while counting.
    - "Hold your wrist level" shows past 40° of tilt, until it is back under 30°. Alternative: a fixed line before the first good heading.
    - The compass's bad states show the phone's titles alone. Alternative: one new line, "Move your wrist in a figure of eight".
    - The three screens show no time and no page dots. Alternative: Wear's time and page dots.

---

### Task 1: One version for the phone, the watch, the face and the Apple Watch

**When:** after plan 1's Task 12 closes, because it edits `watchface/build.gradle.kts`, and before Task 4. It does not wait for plan 4.
- `IOS_PLISTS` holds every plist `bump-version.sh` writes on the day: today's `iosApp/iosApp/Info.plist` and `iosApp/TaqwaWidget/Info.plist`, and plan 4's `iosApp/TaqwaWatch/Info.plist` and `iosApp/TaqwaWatchWidgets/Info.plist` once its Task 4 has created them.
- If plan 4's Task 4 has landed, this task moves its two plists from the loop into `IOS_PLISTS` and keeps its report lines. If this task lands first, plan 4's Task 4 adds its two plists to `IOS_PLISTS` instead of the loop (the map tells it so), and `check-versions.sh`'s unlisted-plist rule fails until it does.

**Files:**
- Create: `scripts/version-files.sh` (the list both scripts read), `scripts/check-versions.sh`, `scripts/test-bump-version.sh`
- Modify:
  - `scripts/bump-version.sh`: the Wear and face lines, and its plist loop reads `IOS_PLISTS`;
  - `scripts/test.sh`, which runs both new scripts first;
  - `.github/workflows/tests.yml`, a step that runs both;
  - `wearApp/build.gradle.kts:43-47`: the "Provisional" comment becomes "written by scripts/bump-version.sh";
  - `watchface/build.gradle.kts`: `versionCode`, `versionName`.

**Interfaces:**
- Produces `scripts/version-files.sh`, sourced by both scripts:
  - `PHONE_GRADLE=androidApp/build.gradle.kts`, `WEAR_GRADLE=wearApp/build.gradle.kts`, `FACE_GRADLE=watchface/build.gradle.kts` and `WEAR_OFFSET=100000`;
  - `IOS_PLISTS=(…)`: the plists above, two before plan 4's Task 4 and four after it.
- Produces `scripts/check-versions.sh`. It exits 0 when everything agrees, and otherwise prints one line per disagreement, naming the file, and exits 1. Agreement means:
  - the phone is (N, C), the Wear app (N, 100000 + C) and the face (N, C);
  - every listed plist is (N, C);
  - every `settings_version_value` is N;
  - no `Info.plist` under `iosApp/` (outside `build/` and `DerivedData/`) carries `CFBundleVersion` without being listed.
- The scheme the release relies on (Tasks 4, 5, 7): face version code = C; Wear version code = 100000 + C.

- [ ] **Step 1: Write the failing test** `scripts/test-bump-version.sh`. It copies every file `version-files.sh` names, the Compose `values*/strings.xml` and the two scripts into a scratch tree, then asserts:
  - `bump-version.sh 1.4.0 40` makes `check-versions.sh` exit 0;
  - `grep 'versionCode = 100040'` in the Wear file, and `versionCode = 40` in the face file;
  - every plist reads 1.4.0 and 40;
  - a hand-edited Wear code (100039) makes `check-versions.sh` exit 1, with the Wear file named;
  - an extra `iosApp/Extra/Info.plist` with a `CFBundleVersion` makes it exit 1, naming that file;
  - `bump-version.sh 1.4 40` and `bump-version.sh 1.4.0 forty` exit 1 and change nothing.

  It passes with two plists listed or four.
- [ ] **Step 2:** Run `bash scripts/test-bump-version.sh`. Expected: FAIL, because the Wear code is not written.
- [ ] **Step 3: Implement.**
  - `bump-version.sh` writes `versionCode = $((WEAR_OFFSET + CODE))` and the name into `WEAR_GRADLE`, and `CODE` and the name into `FACE_GRADLE`. Its plist loop iterates `IOS_PLISTS`, the same files it already writes. It ends by running `check-versions.sh`.
  - Keep the header's policy text, and plan 4's lines if they have landed, and add a paragraph: the Wear app is phone + 100000, so Play never sees two equal codes on one listing (spec 2.1); the face is the phone's code, so every watch-app update updates the face (the Lead's call 2); the watchOS app equals the iPhone app (spec 4.1).
- [ ] **Step 4:** Run `bash scripts/test-bump-version.sh`. Expected: PASS. Then run `scripts/bump-version.sh N K` on the worktree, with the current name and code read from `androidApp/build.gradle.kts` (1.2.0 and 38 on 24 October, until build 39 reaches the branch). Expected: `git diff --stat` shows only the face's two lines and the Wear file's comment. The Wear app already reads 100000 + K, and every listed plist already reads N and K. `scripts/check-versions.sh` exits 0, and the face now reads `versionCode = K`, which Task 4's `check-bundle.sh` needs.
- [ ] **Step 5: Commit** with the message "Release: one version for the phone, the Galaxy watch app, the face and the Apple Watch, and a check that they agree".

### Task 2: The face's release key, its signing, its token, and the face gate

**When:** it runs beside plans 2–4, as Task 1 does. It starts after plan 1's Task 13 closes, because it edits `tools/watchface/validate.sh` and `tools/face-art.py`, which plan 1's last tasks use. Step 3's font pull takes a turn on `emulator-5558`, never while plan 2's Task 7 holds it.

**Files:**
- Create:
  - `scripts/make-face-key.sh`: Mohamed runs it once;
  - `scripts/release-face.sh`: Mohamed runs it each release;
  - `tools/watchface/push-token.sh`, `tools/watchface/lib-validators.sh` (the pinned fetch, moved out of `validate.sh`) and `tools/watchface/get-system-fonts.sh`;
  - `scripts/face-gate.sh` and `scripts/test-face-release.sh`.
- Modify:
  - `tools/watchface/validate.sh`, to source `lib-validators.sh`, behaving the same;
  - `tools/face-art.py` (`--check`);
  - `tools/watchface/README.md` (the release section);
  - `docs/STORE-CHECKLIST.md` §3, the face key beside the upload key.
- The pin, `watchface/face-release-cert.sha256`, is written by the Lead in Task 17 step M1, from what Mohamed's run prints. It is the certificate's public SHA-256, not a secret.

**Interfaces:**
- `scripts/make-face-key.sh [dir]`:
  - It writes `${dir:-~/keys}/taqwa-face.jks`, alias `taqwa-face`, RSA 4096, validity 10000, `CN=Taqwa watch face, O=Mohamed Abulgasem`.
  - It asks for the password twice, never echoed, and writes it nowhere. `TAQWA_FACE_PASSWORD` is read only for `test-face-release.sh`'s scratch keys.
  - It refuses to overwrite.
  - It prints the certificate's SHA-256 and two warnings:
    - back up the `.jks` and the password in two places;
    - unlike the upload key, nobody can reset a lost face key. Every watch's face would have to be removed and added again, losing its wearer's style, colour and slots.
- `scripts/release-face.sh`:
  - It runs `./gradlew :watchface:assembleRelease -Ptaqwa.unsignedRelease=true` (the face never reads the flag, and it keeps `:androidApp` and `:wearApp` from reading a `keystore.properties` beside the build: Ruling 74 item 17), then signs `watchface-release-unsigned.apk` into `build/face-release/watchface-release-signed.apk` with apksigner (`--ks-pass stdin`, so Mohamed types the password).
  - Signing details: `TAQWA_FACE_KS` (default `~/keys/taqwa-face.jks`), `TAQWA_FACE_ALIAS` (default `taqwa-face`).
  - It verifies the signature, and that the certificate's SHA-256 equals `${TAQWA_FACE_CERT_FILE:-watchface/face-release-cert.sha256}`. Otherwise it deletes the output and exits 1.
  - `TAQWA_FACE_KS_PASS_ENV=<var>` (apksigner `--ks-pass env:<var>`) exists for the test key alone.
- `tools/watchface/push-token.sh <signed.apk> <token-file>`:
  - It runs the pinned `validator-push-cli` with `--package_name=world.taqwa.app`.
  - It exits 1 unless the output says "Validation is successful".
  - It writes only the token into `<token-file>`, and never prints it.
- `scripts/face-gate.sh` runs, and exits 1 if any fails:
  - the four generators' `--check`;
  - `tools/face-art.py --check`;
  - `hijri-fit.py`, `window-fit.py`, `slot-fit.py` and `moon-fit.py`, with `fonts=` the cache that `get-system-fonts.sh` fills and pins;
  - `tools/watchface/validate.sh`.
- `tools/face-art.py --check` draws every picture into a temporary directory and compares decoded pixels, mode and size with `watchface/src/main/res/drawable/`. It exits 1 on any difference and writes nothing. An unknown argument prints the usage and exits 2, with no file rewritten (the plan-1 close's item 3).

- [ ] **Step 1: Write the failing test** `scripts/test-face-release.sh`. It uses scratch keys and the test key only:
  - `aScratchFaceKeyIsMadeAndNeverOverwritten`: `TAQWA_FACE_PASSWORD=… make-face-key.sh <scratch>` makes `taqwa-face.jks`, prints a 64-hex SHA-256, and a second run exits 1.
  - `theTestKeySignsTheReleaseFace`: run with `TAQWA_FACE_KS=~/.android/taqwa-face-debug.keystore`, `TAQWA_FACE_ALIAS=taqwaface`, the password from an env var, and `TAQWA_FACE_CERT_FILE` holding the test certificate's SHA-256. The signed APK verifies, and push-token.sh writes a non-empty token. Nothing printed contains the token.
  - `aFaceSignedByAnotherKeyThanThePinIsDeleted`: with a pin of all zeros it exits 1, and `build/face-release/` holds no APK.
  - `theUnsignedFaceHasNoToken`: push-token.sh on `watchface-release-unsigned.apk` exits 1, with an empty token file.
  - `faceArtCheckSeesAChangedPicture`: a scratch copy of the repository with one pixel of `arch_mark.png` changed makes `face-art.py --check` exit 1. `face-art.py --help` exits 2, and `git status` stays clean.
- [ ] **Step 2:** Run `bash scripts/test-face-release.sh`. Expected: FAIL, because the scripts are missing.
- [ ] **Step 3: Implement the scripts.**
  - Check Google's wff-validator releases for a versioned tag. If one exists, pin `validate.sh` to it (the plan-1 close's row L299).
  - `get-system-fonts.sh` pulls, with `adb -s emulator-5558`, the font files the fit tools' `fonts=` reads (their README names them) into `~/.cache/taqwa-watchface-tools/fonts/`. It checks each against a SHA-256 written in the script. `face-gate.sh` refuses a missing or changed font.
  - `make-face-key.sh` follows `make-upload-key.sh`'s shape.
- [ ] **Step 4:** Run `bash scripts/test-face-release.sh` and `scripts/face-gate.sh`. Expected: both PASS.
- [ ] **Step 5: Commit** with the message "Release: the face's own key, signed by Mohamed alone, its validation token, and the face gate before every release".

### Task 3: R8 for the watch app, a staging build, and release builds that cannot sign by accident

**When:** after plans 2 and 3 are built (the series). This task edits `wearApp/build.gradle.kts` and the manifest, which plan 3 gives to its Tasks 4 and 8.

**Files:**
- Modify:
  - `wearApp/build.gradle.kts` (signing, build types) and `wearApp/src/main/AndroidManifest.xml` (`android:icon`, `android:roundIcon`);
  - `androidApp/build.gradle.kts:25-27` and `:77`: the same `taqwa.unsignedRelease` rule;
  - `gradle/libs.versions.toml` and `wearApp/build.gradle.kts`: `androidx.core:core-splashscreen`;
  - `wearApp/src/main/AndroidManifest.xml`: `WearMainActivity`'s `android:theme`;
  - plan 3's `wearApp/src/main/kotlin/world/taqwa/app/wear/app/WearMainActivity.kt`: `installSplashScreen()` before `super.onCreate`;
  - plan 3's `WearManifestTest.kt`: `theLauncherStartsOnTheSplashTheme`.
- Create:
  - `wearApp/proguard-rules.pro`, `wearApp/permissions.txt`, `tools/wear-release-check.sh` and `scripts/test-unsigned-release.sh`;
  - the watch app's launcher icon, `wearApp/src/main/res/mipmap-*/ic_launcher*`, made from the phone's own (`androidApp/src/main/res/mipmap-*`) for a round screen. That is the plan-1 hand-off "the watch app's launcher icon", wearApp lint's one warning;
  - the splash: `wearApp/src/main/res/values/themes.xml` (`Theme.Taqwa.Starting`) and `wearApp/src/main/res/drawable/splash_icon.xml`, the launcher icon itself at 48 dp, a layer-list of its mipmap (as built in Task 3's fix round 1).

**Interfaces:**
- Produces the build types:
  - `release`: `isMinifyEnabled = true`, `isShrinkResources = false` (the phone's lesson, `androidApp/build.gradle.kts:70-76`), with `proguard-android-optimize.txt` and `proguard-rules.pro`. The signing config is `release` from `keystore.properties`, read as `androidApp` reads it, or none.
  - `staging`: `initWith(release)`, signed with the debug key, `matchingFallbacks += "release"`. It is R8'd like a store build, and it reaches the debug phone app over the Data Layer (same key). It has no `src/debug` code.
- Produces the Gradle property **`taqwa.unsignedRelease`**, read in both `androidApp/build.gradle.kts` and `wearApp/build.gradle.kts`:
  - when it is `true`, `keystore.properties` is not read and no `release` signing config is made, so `release.signingConfig` is null;
  - `staging` keeps the debug key either way.
- Produces `wearApp/permissions.txt`, one permission a line: `<name> <origin> # <reason>`. `<origin>` is `manifest` (declared in `src/main/AndroidManifest.xml`) or `library:<group:artifact>` (added by the merge). Today it holds `android.permission.VIBRATE manifest` and androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` as `library:androidx.core:core`. Tasks 4 and 5 add their lines.
- Produces `tools/wear-release-check.sh <variant>`, which exits 1 on any failure:
  - the variant's `mapping.txt` exists;
  - the APK's dex holds no `DEBUG_FEED`, `SampleFeed` or `sample-feed` (through `apkanalyzer dex packages` or a dex string scan);
  - the merged manifest's `uses-permission` set equals every name in `wearApp/permissions.txt`;
  - it prints the APK's size.

  From this task on it is also plan 3's merged-manifest check, in place of plan 3's hand-written one (`tools/wear-release-check.sh release`).
- Produces the splash. Google's Wear app quality item WO-V15 asks for a 48 dp icon on black that matches the launcher icon. Wear OS 3 (API 30, the `minSdk`) has no system splash screen, and plan 3 builds none.
  - `core-splashscreen` draws it on API 30, and hands to the system's from API 31.
  - `Theme.Taqwa.Starting`: `windowSplashScreenBackground` `#000000`, `windowSplashScreenAnimatedIcon` `@drawable/splash_icon`, and `postSplashScreenTheme` the theme `WearMainActivity` has today.
  - It passes plan 2's three library checks, recorded in the ledger.
- Produces `scripts/test-unsigned-release.sh`:
  - It refuses to start (exit 2) while a `keystore.properties` file or link is in the root.
  - It writes the decoy `keystore.properties` (`storeFile=/nonexistent/decoy.jks`, with `trap` removal).
  - With `-Ptaqwa.unsignedRelease=true`, `./gradlew :androidApp:assembleRelease :wearApp:assembleRelease` passes, and `apksigner verify` reports no signature on either output.
  - Without the property, the same build fails, naming the decoy's store file. That proves the decoy would have been used, so the property is what kept it out.

- [ ] **Step 1: Write the failing checks.**
  - Write `wearApp/permissions.txt`, `tools/wear-release-check.sh` and `scripts/test-unsigned-release.sh`.
  - Run `tools/wear-release-check.sh staging`. Expected: FAIL, because there is no staging variant.
  - Run `bash scripts/test-unsigned-release.sh`. Expected: FAIL, because the property is not read.
  - Write `WearManifestTest.theLauncherStartsOnTheSplashTheme`: the launcher's `android:theme` is `@style/Theme.Taqwa.Starting`, and `themes.xml` gives that style `#000000`, `@drawable/splash_icon` and a `postSplashScreenTheme`. Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement the build types, the property and the splash.** `proguard-rules.pro` starts with a comment and no rules. Any rule added later names the failure it fixes.
- [ ] **Step 3:** Run `./gradlew :wearApp:testDebugUnitTest :wearApp:assembleStaging :wearApp:minifyReleaseWithR8 :wearApp:lintDebug -Ptaqwa.unsignedRelease=true`, then `tools/wear-release-check.sh staging` and `bash scripts/test-unsigned-release.sh`. Expected: PASS, the size recorded (plan 1 measured 27 MB unminified), and lint reports "No issues found", the launcher icon included.
- [ ] **Step 4: Run it on the paired emulators,** not while plan 2's Task 7 holds them.
  - Install the staging APK on `emulator-5558` over the debug one (same key), with plan 2's debug phone app on `emulator-5562`.
  - Expected, from the phone's real feed:
    - the face draws (the pass shot at the same moment, within 3 levels, as plan 1's pass);
    - the complications on a system face;
    - the Tile and plan 3's three screens open;
    - the log shows the feed `ACCEPTED`;
    - a cold start (`am force-stop world.taqwa.app`, then `am start -W`) under `adb -s emulator-5558 shell screenrecord --time-limit 3` shows the icon on black, then Today.
  - If anything is blank or crashes, find the stripped class in `mapping.txt`, add the narrowest keep rule with its reason, and run again.
- [ ] **Step 5: Flag it in the ledger.** Under "Plan 5": "R8 on :wearApp since <sha>. Checked: staging on the paired emulators, the face, complications, Tile and three screens. Rules: <none, or each with its failure>. A blank face or a crash on a store build alone points here first."
- [ ] **Step 6: Commit** with the message "Wear: R8 for the store build, a staging build that is minified like it, a check of its permissions and debug code, release builds left unsigned on request, and the launch icon on black".

### Task 4: The face inside the watch app

**When:** after Tasks 1, 2 and 3 (the series), and after plan 1's Task 13 closes, because it changes plan 1's tools.

**Files:**
- Modify:
  - `wearApp/build.gradle.kts`: a `FaceBundleTask` per variant, through `androidComponents.onVariants`;
  - `watchface/build.gradle.kts`: the debug key's path from `-Ptaqwa.faceDebugKeystore`;
  - `wearApp/src/main/AndroidManifest.xml`: `PUSH_WATCH_FACES`, and the default-face meta-data;
  - `wearApp/permissions.txt`;
  - plan 3's `wearApp/src/test/kotlin/world/taqwa/app/wear/app/WearManifestTest.kt`: `theWatchAsksOnlyToVibrate` becomes `theWatchAsksOnlyForThePermissionsListed`, which reads `wearApp/permissions.txt`;
  - plan 1's `tools/watchface/install.sh`, `arabic.sh`, `bengali.sh`, `hijri.sh`, `weekday.sh` and `README.md`: no direct face install;
  - the root `README.md`'s build steps, whose `./tools/watchface/install.sh` line says it installs the face too (P5.10 review round 1's code lens, Ruling 67).
- Create:
  - `tools/watchface/check-bundle.sh` and `tools/watchface/push-face.sh`;
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/face/FaceManifestTest.kt`.

**Interfaces:**
- `FaceBundleTask` writes two generated directories, added with `variant.sources.assets?.addGeneratedSourceDirectory` and `variant.sources.res?.addGeneratedSourceDirectory`:
  - `assets/default_watchface.apk`;
  - `res/values/face_token.xml`, holding `<string name="default_wf_token" translatable="false">…</string>`.
- Its source by variant:
  - **debug and staging:** `:watchface:assembleDebug`'s APK, signed with the test key, and its token from `push-token.sh`.
    - The key's path is `-Ptaqwa.faceDebugKeystore`, default `~/.android/taqwa-face-debug.keystore`. `watchface/build.gradle.kts` reads the same property in place of its fixed path.
    - Without the key, as on CI, the task depends on no face build. It writes no APK and an empty token, and warns "the face is not bundled".
  - **release:** the APK named by `-Ptaqwa.faceApk=<path>`, with its token made by `push-token.sh` from that very file.
    - Without the property, the task writes nothing and an empty token, and warns "the face is not bundled". So `minifyReleaseWithR8`, lint, CI and plan 3's `assembleRelease` runs still build.
    - With **`-Ptaqwa.storeRelease=true`**, which only `scripts/release.sh` and its dry run pass, a `checkReleaseFace` task joins `bundleRelease` and `assembleRelease`. It fails when `taqwa.faceApk` or its file is missing: "run scripts/release-face.sh, then scripts/release.sh". Release.sh's `check-bundle.sh` then catches a face that is missing anyway.
  - **`-Ptaqwa.faceApk`, when given, is the bundled face for every variant,** debug and staging included, with its token made from that file. `push-face.sh` uses it for a face APK that plan 1's tools built themselves.
- Manifest:
  - `<uses-permission android:name="com.google.wear.permission.PUSH_WATCH_FACES"/>`;
  - `<meta-data android:name="com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN" android:value="@string/default_wf_token"/>`.

  These are Google's names (developer.android.com, "Watch Face Push: Wear OS app configuration", read 24 October).
- `tools/watchface/check-bundle.sh <wear.apk> <face-cert-sha256>` exits 1 unless:
  - the APK holds `assets/default_watchface.apk`;
  - that face's package is `world.taqwa.app.watchfacepush.taqwa`, and its version code equals the Wear app's minus 100000;
  - its certificate equals `<face-cert-sha256>` and differs from the Wear APK's own;
  - `default_wf_token` is not empty (`aapt2 dump resources`);
  - `validator-push-cli` passes on the extracted face for `world.taqwa.app`.
- **`tools/watchface/push-face.sh [--inactive] [face.apk]`, the one way a face reaches `emulator-5558` from this task on.** It sources `lib.sh` and calls `need_emulator`, then:
  - builds `:wearApp:assembleDebug`, with `-Ptaqwa.faceApk=<face.apk>` when one is given;
  - removes the face and the Wear app (`wadb uninstall world.taqwa.app.watchfacepush.taqwa`, then `wadb uninstall world.taqwa.app`), so that the install is a first install, and installs the Wear APK with `wadb install`;
  - waits up to 120 s for the system to install the default face (`pm list packages`), sends the sample feed again (`tools/watchface/feed.sh`), and runs `activate.sh`, unless `--inactive` asks for the face as the system left it;
  - prints the face's version code and installer (`dumpsys package world.taqwa.app.watchfacepush.taqwa`), and exits 1 if the face never appears.

  A first install drops the face's style, colour and slots, and the Wear app's own memory. `pass.sh` chooses them again.
- **Plan 1's tools stop installing the face.** `install.sh` builds `:wearApp:assembleDebug` and runs `push-face.sh`, with no Gradle `install*` task and no task argument. `arabic.sh`, `bengali.sh`, `hijri.sh` and `weekday.sh` call `"$HERE/push-face.sh" "$APK"` where they ran `wadb install -r "$APK"`. The README says why: a face installed beside Push's slot is not the face the slot holds.
- If Step 5 finds the emulator's image has no Push service, there is no slot to bypass. The tools keep side-loading on that image, and the ledger says so.

- [ ] **Step 1: Write the failing tests.**
  - `FaceManifestTest`: the source manifest declares `PUSH_WATCH_FACES` and the meta-data with `@string/default_wf_token`, and no `INTERNET` and no `ACCESS_*_LOCATION`.
  - `WearManifestTest.theWatchAsksOnlyForThePermissionsListed`:
    - the source manifest's `uses-permission` set equals the `manifest` lines of `wearApp/permissions.txt`;
    - no `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` or `BODY_SENSORS`, in the manifest or the file;
    - the launcher is `.app.WearMainActivity`, as plan 3's test had it.
  - Add `com.google.wear.permission.PUSH_WATCH_FACES manifest # installs and updates the Taqwa face (Watch Face Push)` to `permissions.txt`, before the manifest line exists.
  - Then `check-bundle.sh` on the current debug APK, with the test certificate.
- [ ] **Step 2:** Run `./gradlew :wearApp:testDebugUnitTest` and `tools/watchface/check-bundle.sh wearApp/build/outputs/apk/debug/wearApp-debug.apk <test cert>`. Expected: FAIL, the manifest lacking `PUSH_WATCH_FACES` and the APK its face.
- [ ] **Step 3: Implement** `FaceBundleTask`, the manifest lines and `check-bundle.sh`. The task runs `push-token.sh` through Gradle's `ExecOperations`.
- [ ] **Step 4: Run the tests and the checks.**
  - The tests and `check-bundle.sh` pass on debug and staging.
  - `tools/wear-release-check.sh staging` passes with the new line.
  - A scratch copy of the debug APK whose face is re-signed with `~/.android/debug.keystore`, the app's own key, fails "the face is signed with the app's key".
  - `./gradlew :wearApp:assembleDebug -Ptaqwa.faceDebugKeystore=/nonexistent` builds, as CI's does, and warns.
  - `./gradlew :wearApp:assembleRelease -Ptaqwa.unsignedRelease=true` builds and warns "the face is not bundled", as plan 3's runs need. With `-Ptaqwa.storeRelease=true` added, it fails with the `checkReleaseFace` message.
  - `git grep -nE 'installDebug|install -r "\$APK"' -- 'tools/watchface/*.sh'` prints comment lines only: no script runs a Gradle `install*` task or installs a face APK itself.
- [ ] **Step 5: Check the default install on `emulator-5558`,** not while plan 2's Task 7 holds it.
  - Run `tools/watchface/push-face.sh --inactive`. It removes plan 1's side-loaded face, and the Wear app's install is a first install.
  - Expected: within two minutes the Taqwa face is in the face picker without adb having installed it, and it is not active. The script prints the current version code K (38 on 24 October) and an installer that is neither `null` nor `com.android.shell`.
  - Run `tools/watchface/activate.sh`. Expected: plan 1's pass shot at the same moment, within 3 levels.
  - If the emulator's image has no Push service, record it, and move this check to Task 13 (the risks below).
- [ ] **Step 6: Commit** with the message "Wear: the Taqwa face rides inside the watch app as its default face, with its validation token, and the face tools put it on through the app".

### Task 5: Watch Face Push: add, update and make active

**When:** after plans 2 and 3 are built, and after Tasks 1 and 4 (the series). Step 7's check 4 needs `bump-version.sh` to write the face (Task 1). It edits the manifest, `wearApp/build.gradle.kts`, the catalog and `TaqwaWearApp.kt`, which plan 3 gives to its Task 8.

**Files:**
- Modify:
  - `gradle/libs.versions.toml` and `wearApp/build.gradle.kts`: `watchfacepush`;
  - `wearApp/src/main/AndroidManifest.xml`: `SET_PUSHED_WATCH_FACE_AS_ACTIVE`, `FaceActivity` and `FaceUpdateReceiver`;
  - `wearApp/permissions.txt`;
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/TaqwaWearApp.kt`;
  - plan 3's Today screen, with one `FaceRow`;
  - plan 2's daily nudge (`DailyNudge.nudge`), which publishes `FacePushes.of(context).state()` through `FaceCapability.publish`;
  - `watchcore/src/commonMain/kotlin/world/taqwa/app/watch/WatchFeed.kt` (`WatchUi.face`) and `WatchLinkContract.kt` (`FACE_CAPABILITY`, `FACE_URI`);
  - `:shared`'s `WatchWords` and `WatchFeedBuilder` (plan 2 Task 3) and their tests;
  - `wearApp/src/main/res/values/strings.xml`.
- Create, in `wearApp/src/main/kotlin/world/taqwa/app/wear/face/`:
  - `FacePushPort.kt`, `AndroidFacePushPort.kt`, `BundledFace.kt`, `FaceMemory.kt` and `FacePush.kt`;
  - `FaceCapability.kt` and `FaceUpdateReceiver.kt`;
  - `FaceActivity.kt`, `FaceScreen.kt` and `FaceRow.kt`.
- Test: `wearApp/src/test/kotlin/world/taqwa/app/wear/face/FacePushTest.kt`, with `FakeFacePushPort`.

**Interfaces:**
- Consumes Task 4's bundled face and token, Task 1's face version code, plan 2's `WatchLinkContract` and daily nudge, and plan 3's Today screen.
- Produces, all `internal`:
  ```kotlin
  const val FACE_PACKAGE = "world.taqwa.app.watchfacepush.taqwa"
  data class InstalledFace(val slotId: String, val packageName: String, val versionCode: Long)
  interface FacePushPort {
      suspend fun installed(): List<InstalledFace>?   // null: no Watch Face Push here
      suspend fun remainingSlots(): Int
      suspend fun add(apk: File, token: String): InstalledFace
      suspend fun update(slotId: String, apk: File, token: String): InstalledFace
      suspend fun isActive(packageName: String): Boolean
      suspend fun setActive(slotId: String)            // throws FaceRefused once spent
  }
  class FaceRefused(cause: Throwable?) : Exception(cause)
  class BundledFace(val apk: File, val token: String, val versionCode: Long)
  enum class FaceState { UNSUPPORTED, NOT_BUNDLED, NOT_INSTALLED, INSTALLED, ACTIVE, CHOOSE_IT_YOURSELF, FAILED }
  class FacePush(
      port: FacePushPort,
      bundled: suspend () -> BundledFace?,
      memory: FaceMemory,
      elapsed: () -> Duration,                                    // SystemClock.elapsedRealtime in the app, a fake in tests
  ) {
      val spent: Boolean                                          // memory.spent
      suspend fun state(): FaceState
      suspend fun ensureCurrent(): FaceState                      // updates an older face; never adds or activates
      suspend fun addAndActivate(permissionGranted: Boolean): FaceState
  }
  const val UPDATE_SETTLE: Duration = 30.seconds
  object FacePushes { fun of(context: Context): FacePush }       // the process's one FacePush
  class FaceMemory(file: File) { var spent: Boolean }             // the one activation, or the one refusal, used
  object FaceCapability {
      fun wanted(state: FaceState, spent: Boolean): Boolean
      suspend fun publish(context: Context, state: FaceState, spent: Boolean)
  }
  suspend fun refreshAfterUpdate(push: FacePush, publish: suspend (FaceState, Boolean) -> Unit): FaceState
  ```
- The rules `FacePush` keeps:
  - **One call at a time.** Every public call holds one `Mutex`, and each reads `installed()` afresh inside it. So when the app's start and `MY_PACKAGE_REPLACED` both run `ensureCurrent`, the second finds the face already current and calls no `update`. `FacePushes.of` makes one `FacePush` per process, and every caller uses it.
  - **No activity check right after a change** (Google, "Watch Face Push: Wear OS app configuration", read 24 October). `ensureCurrent` reads `isActive` before an `update`, never after. Google: if the update completes, the app still holds the active face. So it answers ACTIVE when the face was active, and INSTALLED otherwise. `addAndActivate` answers ACTIVE after `setActive` without asking. For `UPDATE_SETTLE` after this process's last `update` or `setActive`, `state()` answers that call's result and does not call `isActive`.
  - **`state()`:** INSTALLED with `spent` true is CHOOSE_IT_YOURSELF. NOT_INSTALLED stays NOT_INSTALLED whatever `spent` says, so the face screen can still add it.
  - **`addAndActivate(granted)`:**
    - if the face is missing, `add` it (FAILED when `remainingSlots()` is 0 or `add` throws);
    - if it is older, `update` it;
    - an INSTALLED, current face is neither added nor updated;
    - then, if `granted` and not `spent`, read `installed()` again and `setActive` that slot. The answer is ACTIVE, and `spent` becomes true. A `setActive` that throws `FaceRefused` answers CHOOSE_IT_YOURSELF, and `spent` becomes true;
    - otherwise the answer is CHOOSE_IT_YOURSELF, and `spent` becomes true.
- `refreshAfterUpdate` is the whole sequence `FaceUpdateReceiver` and `TaqwaWearApp.onCreate` run: `push.ensureCurrent()`, then `publish(that state, push.spent)`. It never calls `state()`.
- `FaceCapability.wanted(state, spent)` is true only when `spent` is false and the state is NOT_INSTALLED, INSTALLED or FAILED. `publish` adds or removes the local capability from that, never from a state it reads itself.
- In `watchcore`:
  - `WatchUi.face: WatchFaceWords = WatchFaceWords()`, with `data class WatchFaceWords(val add: String = "", val about: String = "", val active: String = "", val chooseIt: String = "", val failed: String = "")`. `WatchFeedJson.VERSION` stays 1.
  - `WatchLinkContract.FACE_CAPABILITY = "taqwa_face_addable"` and `FACE_URI = "taqwa://face"`.
- The English, from the feed, else from the watch's own `face_*` strings:
  - `add`: "Add the Taqwa face";
  - `about`: "The next prayer and its countdown on your watch face.";
  - `active`: "Taqwa is your watch face.";
  - `chooseIt`: "Touch and hold your watch face, then choose Taqwa.";
  - `failed`: "Couldn’t add the face. Try again later."

- [ ] **Step 1: Read Google's current Watch Face Push pages and the library's release notes.** Record in the task report:
  - the version chosen, and the AAR's `minSdk`;
  - each method's exact signature and its exceptions;
  - both permission names;
  - the once-only rule;
  - the default-face mechanism (Task 4).

  - The review read 1.0.0 (stable, 8 April 2026): AAR `minSdk` 33, and `isSupported()` false below API 36. So the manifest takes `tools:overrideLibrary="androidx.wear.watchfacepush"` (the package the AAR's manifest names), unless the Lead's call 6 rules (c).
  - `AndroidFacePushPort` touches the library only where `Build.VERSION.SDK_INT >= 36` (Wear OS 6) and the library's own support check says yes. Below that, `installed()` is null.
  - Task 12 runs the override on API 30 and API 33.
  - Run plan 2's three library checks and record them.
- [ ] **Step 2: Write the failing tests** in `FacePushTest`. Version codes 39 and 40; bundled 40:
  - `belowWearOsSixThereIsNoRow`: `installed()` is null, so `UNSUPPORTED`, and `ensureCurrent` makes no other call.
  - `withoutABundledFaceThereIsNoRow`: `bundled()` is null, so `NOT_BUNDLED`.
  - `aWatchWithoutTheFaceOffersToAddIt`: none installed and one slot, so `NOT_INSTALLED`.
  - `theDefaultFaceNotChosenOffersToUseIt`: Taqwa 40 installed and `isActive` false, so `INSTALLED`.
  - `theActiveFaceShowsNoRow`: so `ACTIVE`.
  - `anOlderFaceIsUpdatedInItsSlot`: installed 39, so `update(slotId of 39, …, token)` exactly once, never `add` or remove.
  - `twoConcurrentUpdatesUpdateOnce`: installed 39, and the fake's `update` suspends until released. Two `ensureCurrent` calls launched together on one `FacePush` make exactly one `update`, and both answer the same state.
  - `anActiveFaceStaysActiveThroughItsUpdate`: installed 39 and active. `isActive` is called before the `update` and never after, and `ensureCurrent` answers `ACTIVE`.
  - `theSameOrANewerFaceIsLeftAlone`: 40 or 41 installed, so no `update`.
  - `theSlotIsReadAgainBeforeEveryCall`: `installed()` is called before each `update`, `add` and `setActive` (Google: never store a slot id).
  - `noActivityCheckRightAfterAnUpdate`, over the receiver's whole sequence, `refreshAfterUpdate(push, publish)`, with installed 39:
    - the port's call log holds no `isActive` after the `update`;
    - `publish` receives the state `ensureCurrent` answered, and `spent`;
    - a `state()` 29 s later answers the same and calls no `isActive`;
    - a `state()` at 31 s calls `isActive`.
  - `noActivityCheckRightAfterSetActive`: `addAndActivate(true)` ends its call log with `setActive` and answers `ACTIVE`. A `state()` within `UPDATE_SETTLE` answers `ACTIVE` with no `isActive`.
  - `addAndActivateAddsThenActivates`: `NOT_INSTALLED`, granted, so `add` then `setActive(new slot)`, then `ACTIVE`.
  - `anInstalledFaceIsOnlyMadeActive`: Taqwa 40 `INSTALLED` (the default face, the commonest case), granted. No `add` and no `update`, then `setActive(the listed slot)`, then `ACTIVE`, and `spent`.
  - `anInstalledOlderFaceIsUpdatedThenMadeActive`: Taqwa 39 installed, granted. `update`, then `setActive`, never `add`.
  - `aRefusedPermissionLeadsToChoosingItYourself`: `INSTALLED`, not granted, so no `setActive`, `CHOOSE_IT_YOURSELF`, and `memory.spent` is true.
  - `aSecondActivationLeadsToChoosingItYourself`: `setActive` throws `FaceRefused`, so `CHOOSE_IT_YOURSELF`, and spent.
  - `aRemovedFaceIsAddedEvenWhenTheActivationIsSpent`: `NOT_INSTALLED` and one slot free, with the permission refused, then again with `memory.spent` already true. Each time: `add`, no `setActive`, then `CHOOSE_IT_YOURSELF`, so the face the guidance names is in the picker.
  - `aFullSlotIsFailedNotThrown`: `NOT_INSTALLED` and `remainingSlots()` 0, so no `add`, and `FAILED`.
  - `theSpentActivationOutlivesTheProcess`: a new `FacePush` over the same `FaceMemory` file, with the face `INSTALLED`, answers `CHOOSE_IT_YOURSELF` and calls no `setActive`.
  - `aPushThatThrowsShowsNoRow`: `installed()` throws `IllegalStateException`, so `UNSUPPORTED`. An `add` that throws anything but `FaceRefused` gives `FAILED`. A `CancellationException` is rethrown.
  - `anotherPackageInTheListIsNeverTouched`: a listed face whose package is not `FACE_PACKAGE` is never updated.
  - `theCapabilityFollowsTheState`: with `spent` false, `wanted` is true for `NOT_INSTALLED`, `INSTALLED` and `FAILED`, and false for `ACTIVE`, `CHOOSE_IT_YOURSELF`, `UNSUPPORTED` and `NOT_BUNDLED`.
  - `theRowsGoQuietOnceTheActivationIsSpent`: with `spent` true, `wanted` is false for every state. So after one refusal, `refreshAfterUpdate`'s `publish` removes the capability, and Today's chip is gone.
  - In `:shared`, `theFaceWordsAreTheInterfaceLanguages`: an Arabic interface on an English device gives the Arabic `WatchFaceWords`. In `:wearApp`, `theFeedsFaceWordsComeFirstAndTheWatchsOwnBeforeAnyFeed`.
- [ ] **Step 3:** Run `./gradlew :wearApp:testDebugUnitTest :shared:testAndroidHostTest :watchcore:allTests`. Expected: FAIL.
- [ ] **Step 4: Implement.**
  - `AndroidFacePushPort` wraps `WatchFacePushManagerFactory.createWatchFacePushManager(context)`. It opens the APK with `ParcelFileDescriptor.open(file, MODE_READ_ONLY)`.
  - `FacePush` logs each port call and its result as `TaqwaFace <call> <version code or slot> <result>`, never the token. Step 7 and Tasks 12, 13 and 17 read these lines.
  - `BundledFace` copies `assets/default_watchface.apk` to `cacheDir/face-<versionCode>.apk` once, reads `versionCode` with `PackageManager.getPackageArchiveInfo`, and reads the token from `R.string.default_wf_token`. It is null when the asset is missing or the token is empty.
  - `FaceUpdateReceiver` (`MY_PACKAGE_REPLACED`) and `TaqwaWearApp.onCreate` run `refreshAfterUpdate(FacePushes.of(context), FaceCapability::publish)` inside `goAsync`/the app scope, bounded to 10 s. Google: a bundled face is not updated with the app. The update starts the process, so both run, and the `Mutex` makes the second a no-op.
  - `FaceCapability.publish` calls `CapabilityClient.addLocalCapability(FACE_CAPABILITY)` when `wanted(state, spent)`, else `removeLocalCapability`, and catches every `ApiException`.
  - Every other caller (Today, `FaceActivity`, plan 2's daily nudge) publishes the state its own call answered: `state()` on Today and the nudge, `addAndActivate`'s answer on the face screen.
  - `FaceActivity`:
    - It is exported, with a filter for `VIEW`, `BROWSABLE` and `DEFAULT` on `taqwa://face`.
    - It shows `FaceScreen`: a heading, the `about` line, and a button reading `add`. `ACTIVE` shows the `active` line; `CHOOSE_IT_YOURSELF` the `chooseIt` line; `FAILED` the `failed` line, with the button to try again.
    - The button asks for `SET_PUSHED_WATCH_FACE_AS_ACTIVE` with `RequestPermission`, unless it is already granted or `memory.spent`, then calls `addAndActivate(granted)`.
  - `FaceActivity` opened after the activation is spent (a stale phone row, or `taqwa://face` by hand) shows the line for its state: `active`, or `chooseIt` with no button. A NOT_INSTALLED face still shows the button, which adds the face and then shows `chooseIt`.
  - `FaceRow` is one chip at the end of Today's list, visible when `FaceCapability.wanted(state, push.spent)`, which opens `FaceActivity`.
  - Screen readers: the heading is a heading; the chip and button say their words; state changes are a polite live region.
  - Re-run plan 2's size tests and regenerate the goldens and the sample vectors, as plan 2's call 6 requires, and add the face words' row to plan 2's table under Ruling 39. Then run `swift test --package-path iosApp/WatchCore`: plan 4's port reads the regenerated samples and ignores the new key.
- [ ] **Step 5:** Run the tests. Expected: PASS.
- [ ] **Step 6: Run** `tools/wear-release-check.sh staging` after adding `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE manifest # makes the Taqwa face active once, on the wearer's tap` to `permissions.txt`. Expected: PASS, and `WearManifestTest` passes.
- [ ] **Step 7: Check on `emulator-5558`** (Wear OS 6), not while plan 2's Task 7 holds it. Each check starts from a fresh install of the debug Wear app, `tools/watchface/push-face.sh --inactive`, with TalkBack on for check 1. The emulator's first activation is check 1's, and the Global Constraints' once-only rule applies to the rest.
  1. Today shows "Add the Taqwa face". A tap asks the permission; Allow. Expected:
     - the Taqwa face is active (screencap);
     - the phone's capability query (`emulator-5562`) no longer lists the watch;
     - Today shows no chip;
     - TalkBack reads the chip and the result.
  2. On a fresh install, Deny. Expected:
     - the guidance line;
     - after the screen closes, Today shows no chip, and the phone's capability query no longer lists the watch;
     - `taqwa://face` (check 5's command) shows the guidance line, with no dialog and no button.
  3. Choose another face in the picker. Expected: Today shows no chip, the phone no row, and `taqwa://face` shows the guidance line, and no crash (`logcat -b crash` empty).
  4. Make the face active again by hand, and customise it (Moon, Sage, the battery in slot 4). Then build an update one code higher, without losing work:
     - First make a WIP commit of this task's files (`git commit -F`), so that nothing of the task is uncommitted.
     - Read the name N and code K from `androidApp/build.gradle.kts` (1.3.0 and 39 since MM2). Run `scripts/bump-version.sh N K+1`, build `:wearApp:assembleDebug`, and install it with `adb -s emulator-5558 install -r`.
     - Put the version back with `scripts/bump-version.sh N K`. Never `git checkout` or `git restore`: `bump-version.sh` rewrites the phone, Wear and face build files, every Compose `values*/strings.xml` and the iOS plists, and a checkout would also discard other work in the shared worktree. Then `scripts/check-versions.sh` exits 0, and `git diff -U0 | grep -E 'versionCode|versionName|CFBundle(Short)?Version|settings_version_value'` prints nothing.

     Expected:
     - `dumpsys package world.taqwa.app.watchfacepush.taqwa` shows version code K+1;
     - the face is still active, still Moon, Sage, and battery (screencap against before);
     - the log shows one `update`, from whichever of the app's start and the receiver ran first, and the other finding the face current.
  5. `adb -s emulator-5558 shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d taqwa://face`. Expected: `FaceActivity` opens.

  If the emulator has no Push service (Task 4 Step 5), checks 1–4 move to Task 13, and the ledger says so.
- [ ] **Step 8: Commit** with the message "Wear: Watch Face Push adds, updates and makes the Taqwa face active once, and tells the phone while it is not the face".

### Task 6: The phone's watch rows

**When:** after this plan's Task 5 (its `FACE_CAPABILITY` and `FACE_URI`) and plan 4's Task 10, which creates `feature/settings/WatchRows.kt` with `WatchFaceRow()` (plan 4's call 6: "Plan 5 adds Android's Install on your watch row to the same file").

**Files:**
- Create:
  - `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchTargets.kt`, `WatchTargetsSource.kt` (the `expect`) and `RemoteIntentSpec.kt`;
  - `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/PlayWatchTargets.kt`;
  - `shared/src/iosMain/kotlin/world/taqwa/app/watchfeed/WatchTargetsSource.ios.kt`;
  - `shared/src/commonTest/kotlin/world/taqwa/app/watchfeed/WatchTargetsTest.kt`.
- Modify:
  - plan 4's `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/WatchRows.kt`: it gains `WatchSection()`, holding Android's two rows and plan 4's `WatchFaceRow()`;
  - `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt`: plan 4's `WatchFaceRow()` call leaves the App group, and `WatchSection()` goes between "App" and "About";
  - `SettingsRoutes.kt`;
  - `shared/src/commonMain/composeResources/values*/strings.xml`: English, with drafts in the six others marked `<!-- draft, to native review (plan 5 Task 11) -->`;
  - `gradle/libs.versions.toml` and `shared/build.gradle.kts`: androidMain `androidx.wear:wear-remote-interactions`.

**Interfaces:**
- Consumes:
  - plan 2's `WatchLinkContract` (`WATCH_CAPABILITY`) and the Play services checks in `PlayWatchNodes` (`noPlayServices`, `noWearApi`, `Task.await`);
  - Task 5's `FACE_CAPABILITY` and `FACE_URI`;
  - plan 4's `createWatchFaceOffer(): WatchFaceOffer` (`available()`) and `WatchFaceRow()`.
- Produces, `internal`:
  ```kotlin
  data class WatchPeer(val id: String, val name: String, val nearby: Boolean)
  data class WatchTarget(val nodeId: String, val name: String)
  data class WatchTargets(val install: WatchTarget?, val addFace: WatchTarget?) { companion object { val NONE: WatchTargets } }
  fun watchTargets(connected: List<WatchPeer>?, withApp: Set<String>, wantsFace: Set<String>): WatchTargets
  fun watchSectionShown(targets: WatchTargets, appleFaceOffered: Boolean): Boolean
  enum class WatchAction { INSTALL, ADD_FACE }
  data class RemoteIntentSpec(val action: String, val category: String, val uri: String)
  fun remoteIntentSpec(action: WatchAction): RemoteIntentSpec
  interface WatchTargetsSource { suspend fun targets(): WatchTargets; suspend fun open(target: WatchTarget, action: WatchAction): Boolean }
  expect fun watchTargetsSource(): WatchTargetsSource
  suspend fun loadWatchTargets(source: WatchTargetsSource, bound: Duration = 3.seconds): WatchTargets   // NONE on timeout or throw
  ```
- The rules:
  - `install` is the first nearby peer not in `withApp`.
  - `addFace` is the first nearby peer in both `withApp` and `wantsFace`. The watch publishes `wantsFace` only while `FaceCapability.wanted` holds (Task 5), so the row goes quiet with the watch's chip (question 11).
  - With no Wear API (`connected == null`) or no nearby peer, the targets are `NONE`.
  - **The section shows when `watchSectionShown(targets, createWatchFaceOffer().available())`:** `targets != NONE || appleFaceOffered`. On Android plan 4's offer is never available. On the iPhone the targets are always `NONE`, so the section is exactly plan 4's row: shown while a watch is paired and a `.watchface` file is bundled.
- The intents:
  - INSTALL: `android.intent.action.VIEW`, `android.intent.category.BROWSABLE`, `market://details?id=world.taqwa.app`.
  - ADD_FACE: the same action and category, with `taqwa://face`.
- The Android actual:
  - peers from `NodeClient.getConnectedNodes()` (`displayName`, `isNearby`);
  - `withApp` and `wantsFace` from `CapabilityClient.getCapability(…, FILTER_REACHABLE)`;
  - `open` through `RemoteActivityHelper(context).startRemoteActivity(intent, nodeId)`, awaited.
  - It calls nothing that shows UI or a notification.
- The iOS actual answers `NONE`.
- The strings:
  - `settings_group_watch`: "Watch";
  - `settings_watch_install`: "Install on your watch", with the watch's name as its value;
  - `settings_watch_face`: "Add the Taqwa face";
  - `settings_watch_continue`: "Continue on your watch";
  - `settings_watch_unreachable`: "Couldn’t reach your watch".

- [ ] **Step 1: Record plan 2's baseline,** the phone's release dependencies and its 12 permissions, then add the library. Run the three checks and record them.
- [ ] **Step 2: Write the failing tests:**
  - `noWearApiShowsNoRows`;
  - `aWatchWithoutTaqwaOffersTheInstall`;
  - `aWatchWithTaqwaAndNoFaceOffersTheFace`;
  - `aWatchWithTaqwaAndItsFaceShowsNothing`;
  - `aWatchOutOfReachShowsNothing`;
  - `twoWatchesEachGetTheirRow`;
  - `theSectionFollowsTheTargetsOrTheAppleFace`: `watchSectionShown` is false for `NONE` without the Apple face, and true for `NONE` with it, and for any target;
  - `theInstallIntentIsThePlayStorePage` and `theFaceIntentIsTheWatchsFaceScreen` (`remoteIntentSpec`);
  - `aSlowNodeQueryNeverHoldsSettings`: a fake whose `targets()` suspends 10 s, with `loadWatchTargets` answering `NONE` at 3 s, on one test scheduler (plan 2's virtual-time rule);
  - `aThrowingSourceIsNone`.
- [ ] **Step 3:** Run `./gradlew :shared:allTests`. Expected: FAIL.
- [ ] **Step 4: Implement** the rules, the actuals and `WatchSection`.
  - `WatchSection` uses the Settings screen's own `SectionLabel`, `SettingsCard`, `CardDivider` and `TaqwaRow`. It draws the install row, the face row, and plan 4's `WatchFaceRow()`, each only when it applies.
  - It is loaded off the main thread on every resume, through `loadWatchTargets`. Since 9 November (the owner's report, lane `w44-rows`), also in place while Settings stays in front: at each change of either capability (one `CapabilityClient` listener, only once Play services is known to be there), at each change of the iPhone's session, and every 5 s for 3 minutes after a row's tap. A reading in place keeps a tap's answer while its row stays, and one that fails keeps the rows. One that takes the install row to no row at all, as a watch that has just got Taqwa answers until its app has run and published its face, keeps the rows for up to 20 s (`InstallHold.APP`), then reads once more and takes what comes, so the section does not go and come back in between (the review of 9 November). The listener says one change of its own once it is added, for a change that came while it was being added.
  - A tap shows `settings_watch_continue` or `settings_watch_unreachable` as the row's value, in a polite live region.
- [ ] **Step 5:** Run the tests, `scripts/check-strings.sh` and `./gradlew :shared:iosSimulatorArm64Test` (plan 4's `IosWatchFaceOfferTest` still passes). Expected: PASS.
- [ ] **Step 6: Check on the paired emulators** (`emulator-5562` and `emulator-5558`), not while plan 2's Task 7 holds them:
  1. With the Wear app uninstalled, Settings shows "Watch" › "Install on your watch", with the watch's name. Tap it. Expected: the watch's log shows the Play Store started with `market://details?id=world.taqwa.app`.
  2. With the Wear app freshly installed, the face not active and its activation unspent: "Add the Taqwa face". Since 9 November, with Settings left open on the phone from check 1 while the Wear app installs: the install row stays while the watch app starts and publishes its face, held for up to 20 s, then gives way to "Add the Taqwa face" by itself, and the "Watch" heading never leaves in between. If the app has not started within those 20 s, the section goes when they end and comes back with the face row once the app starts and publishes. That is expected, not a failure: a watch the phone has fed before is sent no feed when the app is installed again (`WATCH_APPEARED` is damped, since the phone's record says the watch holds it), so nothing starts the app; start it from the watch's launcher. Tap it. Expected: `FaceActivity` opens on the watch. Do not press its button.
  3. On the watch, tap Today's chip and Deny. Expected: after the watch publishes, the phone shows no section; since 9 November, with Settings open on the phone, it goes by itself.
  4. With the face active, after the watch's publish: no section.
  5. On `emulator-5564` (no Play services), if plan 2 made it: no section, and Settings opens in the same time as before (`am start -W`, three runs).
  6. TalkBack on the phone reads the section heading, each row with its value, and the live result.
- [ ] **Step 7: Check on the iPhone 17 Pro simulator, paired with a watch simulator.**
  - With no `.watchface` file bundled: no section.
  - With plan 4's placeholder copied into `iosApp/iosApp/WatchFace/` for this step only (plan 4 Task 10 Step 5's way): the section shows, holding plan 4's row alone, and a tap answers with plan 4's failure line.
  - With the placeholder and no paired watch: no section.
  - Delete the placeholder before the commit (`git status` clean of it).
- [ ] **Step 8: Commit** with the message "Phone: a Watch section in Settings, to install Taqwa on the watch and to add the Taqwa face, with no new permission".

### Task 7: The release path, end to end

**Files:**
- Modify: `scripts/release.sh`, `scripts/ios-release.sh`, plan 4's `scripts/check-watch-bundle.sh` (the Release signal and rule 4b's keys) and `scripts/ios-build.sh`, `docs/STORE-CHECKLIST.md` (a new section "Each watch release", and §8)
- Create: `androidApp/permissions.txt` (today's 12, each with its reason), `scripts/test-release.sh`, `scripts/test-watch-bundle-signing.sh`

**Interfaces:**
- Consumes Tasks 1–5.
- `scripts/release.sh`, in order, stops at the first failure:
  1. `check-versions.sh`, and the stamps check (as today).
  2. `scripts/face-gate.sh`.
  3. The signed face, `build/face-release/watchface-release-signed.apk`:
     - It must exist, and its certificate must equal `watchface/face-release-cert.sha256`.
     - It must be the current face. Build `:watchface:assembleRelease` fresh, then compare every zip entry's name and CRC-32 outside `META-INF/` with the signed APK's. Otherwise: "the signed face is not the current face: run scripts/release-face.sh again".
  4. Build `:androidApp:bundleRelease :androidApp:assembleRelease :wearApp:bundleRelease :wearApp:assembleRelease -Ptaqwa.storeRelease=true -Ptaqwa.faceApk=<the signed face>`.
  5. Check:
     - `check-bundle.sh` on the Wear APK;
     - `tools/wear-release-check.sh release`;
     - the phone's merged release permissions equal `androidApp/permissions.txt`;
     - the Wear APK's certificate equals the phone APK's;
     - `aapt2 dump badging`: the phone C, the Wear app 100000 + C, the face C.
  6. Print both AABs and APKs with their sizes and certificate SHA-256s.
- The real run (no `TAQWA_RELEASE_DRY_RUN`) keeps today's refusal when `keystore.properties` is missing. It is the Lead's alone (the Global Constraints' keys).
- `TAQWA_RELEASE_DRY_RUN=1` is its own branch:
  - It skips the `keystore.properties` check, and passes **`-Ptaqwa.unsignedRelease=true`** to step 4's build, so neither module reads `keystore.properties`, present or not.
  - Before signing anything it runs `apksigner verify` on both release APKs, and checks that both AABs have no `META-INF/*.RSA`, `*.EC` or `*.DSA` entry. If any output is signed, it stops: "the release build was signed: taqwa.unsignedRelease is not honoured".
  - It then signs the unsigned outputs with `~/.android/debug.keystore` into `build/release-dry-run/*-DRY-RUN.{aab,apk}`.
  - The face comes from `TAQWA_FACE_*` (Task 2's test key) and its pin from `TAQWA_FACE_CERT_FILE`.
  - Play refuses such a bundle, because the key is wrong, and its names say what it is.
- `scripts/ios-release.sh check <xcarchive>`, also run inside `archive`'s report. It exits 1 unless:
  - the archive's iPhone app holds plan 4's watch app under `Watch/`, with its widget extension;
  - the `Info.plist` of every app and app-extension bundle reads the iPhone app's V and C. That is `Taqwa.app`, `Taqwa.app/PlugIns/*.appex`, `Taqwa.app/Watch/*.app` and `Taqwa.app/Watch/*.app/PlugIns/*.appex`. Frameworks keep their own versions and are not read;
  - **`scripts/check-watch-bundle.sh --release` passes on `<xcarchive>/Products/Applications/Taqwa.app`.**

  It prints the watch app's and the widget extension's sizes. The bundle check runs as `${TAQWA_WATCH_BUNDLE_CHECK:-scripts/check-watch-bundle.sh}`, so the fake-archive test can stub it.
- **What the archive's signature is.** `ios-release.sh:46-48` archives with automatic signing and `-allowProvisioningUpdates`, so the archive is signed with an **Apple Development** identity. The App Store signature is applied only at export (`upload`, lines 88-104), where his cloud-managed distribution certificate signs it. So the archive check proves rule 8 on the Release bundle, and rule 4b's capabilities on the development signature. The distributed signature is `export-check`'s.
- `scripts/ios-release.sh export-check`, run by the Lead with Mohamed's OK, as `archive` is:
  - It exports the last archive locally: `method` `app-store-connect`, `destination` `export`, into `build/ios-archive/export-check-<V>-<C>/`, as the 22 October check on the Mac mini exported with cloud-managed distribution.
  - It unzips the IPA into a fresh directory there, runs `check-watch-bundle.sh --release` on `Payload/Taqwa.app`, and deletes both. It uploads nothing.
  - If the export cannot sign on the Mac, the Lead reads each bundle's entitlements in App Store Connect once the upload has processed (TestFlight › the build › Build Metadata), and records them in the ledger instead.
- **The Release signal and rule 4b's keys,** in plan 4's `scripts/check-watch-bundle.sh <Taqwa.app>`:
  - **`--release`**, given before `<Taqwa.app>`. With it, rule 8 always runs: no `sample-feed*` file anywhere under `Taqwa.app/Watch/`. Rule 4b is then required: an unsigned or ad hoc bundle fails "rule 4b: a release bundle must be signed". Without the flag the script is as plan 4 left it.
  - **Rule 4b's keys, by the signature's first `Authority` line:**
    - `Apple Development: …`, the archive: the app group, `application-identifier` (equal to `<TEAM_ID>.<the bundle's id>`), `com.apple.developer.team-identifier` (equal to `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`), `get-task-allow`, and `keychain-access-groups` only as the one value `<TEAM_ID>.<the bundle's id>`;
    - `Apple Distribution: …` or `iPhone Distribution: …`, the export: the same keys and `beta-reports-active`, with `get-task-allow` absent or false;
    - any other key, or any other `Authority`, fails, naming it. A key Xcode adds by itself that is not listed goes to the Lead with the run's output. It joins the list only by his ruling in the ledger.
  - **`TAQWA_CODESIGN`**, default `codesign`: the command that rule 4b's two reads run (`-dv` for the `Authority`, `-d --entitlements - --xml`), so a test can give a fake signature.
  - `ios-release.sh archive`, `export-check` and the bare script (`all`) refuse (exit 2) a set `TAQWA_CODESIGN` or `TAQWA_WATCH_BUNDLE_CHECK`.

- [ ] **Step 1: Write the failing test** `scripts/test-release.sh` (dry runs and the test key only):
  - `theDryRunBuildsBothBundlesWithTheFaceInside`: every output exists, and step 5's checks pass.
  - `aFaceSignedWithTheAppKeyIsRefused`: the signed face re-signed with the debug key, which is the dry run's app key.
  - `aStaleSignedFaceIsRefused`: a scratch signed face with one extra `res/` entry.
  - `aFaceSignedByAnUnpinnedKeyIsRefused`.
  - `theBundledTokenIsTheBundledApks`: the token in the Wear APK equals `push-token.sh`'s for the face inside it, run again.
  - `disagreeingVersionsAreRefused`: a scratch tree with the Wear code changed by hand.
  - `theDryRunNeverUsesTheUploadKey`: the test refuses to start (exit 2) while any `keystore.properties` file or link is in the root. It writes the decoy (`storeFile=/nonexistent/decoy.jks`, `trap` removal) and runs the dry run. Expected: it passes; every output's certificate is the debug key's; nothing names the decoy's store file. A run with `-Ptaqwa.unsignedRelease` dropped from the script, in a scratch copy of it, fails on the decoy.
  - `ios-release.sh check` on a fake `.xcarchive` tree, with `TAQWA_WATCH_BUNDLE_CHECK` a stub that records its arguments and exits 0:
    - equal plists pass;
    - a watch plist one code behind fails;
    - no `Watch/` fails;
    - a framework's `Info.plist` at another version still passes;
    - `theArchiveIsCheckedAsARelease`: the stub was called with `--release` and the archive's `Taqwa.app`.
  - `archive`, `export-check` and the bare script (`all`) exit 2 with `TAQWA_CODESIGN` or `TAQWA_WATCH_BUNDLE_CHECK` set.
- [ ] **Step 2: Write the failing test** `scripts/test-watch-bundle-signing.sh`. It runs the real `check-watch-bundle.sh` on a scratch copy of plan 4's Debug simulator build (`build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app`, which holds the sample feeds), with `TAQWA_CODESIGN` a stub that prints a chosen `Authority` line and entitlements plist for each bundle:
  - `theReleaseFlagFindsTheSampleFeed`: `--release`, with a development `Authority` and the allowed keys, fails rule 8, naming a `sample-feed-capetown` file. With the sample files deleted from the copy, it passes;
  - `aReleaseBundleMustBeSigned`: `--release` with no `Authority` (ad hoc) fails rule 4b. Without `--release`, it prints "rule 4b skipped", as plan 4's does;
  - `theDevelopmentSigningKeysAreAllowed`: the archive's keys, with `get-task-allow` true, pass;
  - `anExtraCapabilityIsRefused`: the same plus `com.apple.developer.healthkit` fails, naming it;
  - `aDistributionSignatureMayNotBeDebuggable`: a distribution `Authority` with `get-task-allow` true fails. With `beta-reports-active` true and no `get-task-allow`, it passes;
  - `anotherTeamIsRefused`: a `com.apple.developer.team-identifier` other than `TEAM_ID` fails.

  Run `bash scripts/test-release.sh` and `bash scripts/test-watch-bundle-signing.sh`. Expected: FAIL.
- [ ] **Step 3: Implement the scripts.** `scripts/ios-build.sh` runs `test-watch-bundle-signing.sh` after plan 4's bundle check, on the Debug build it has just made.
- [ ] **Step 4: Run them.**
  - `bash scripts/test-release.sh` and `bash scripts/test-watch-bundle-signing.sh`. Expected: PASS.
  - `TAQWA_RELEASE_DRY_RUN=1 scripts/release.sh` with the test key. Expected: PASS, and the dry-run outputs are recorded for Task 12.
  - **The Lead, with Mohamed's OK in chat:** `scripts/ios-release.sh archive`, then `scripts/ios-release.sh export-check`, with no upload. The first archive registers `world.taqwa.ios.watchkitapp` and `.widgets` under his team (plan 4's hand-off), which is why it needs his OK. Expected: both checks pass, with `check-watch-bundle.sh --release` on the archive's development signature and on the export's distribution signature, and the sizes printed. The ledger records the keys each signature carried.
- [ ] **Step 5: Write "Each watch release" in `docs/STORE-CHECKLIST.md`:**
  - bump;
  - `scripts/face-gate.sh`;
  - **Mohamed:** `scripts/release-face.sh`;
  - **the Lead alone:** symlink `keystore.properties` from the main checkout, run `scripts/release.sh`, and remove the link (`rm keystore.properties`, then `ls` shows it gone);
  - with Mohamed's OK: `scripts/ios-release.sh archive`, `scripts/ios-release.sh export-check`, then `scripts/ios-release.sh upload`;
  - the uploads.

  Add to §8: the watch builds' permissions, and where each check lives.
- [ ] **Step 6: Commit** with the message "Release: the phone and watch bundles built and checked together, the face's signature and freshness held, and the watch app checked as a release inside the iPhone archive and its export".

### Task 8: The watch apps' own words, in seven languages

**Files:**
- Modify: plan 3's `wearApp/src/main/res/values-{ar,fr,tr,in,ur,bn}/strings.xml`. Plan 3's Task 4 creates them, drafting every key the watch app had then (plan 1's picker names among them); this task adds the keys added since, Task 5's `face_*` among them.
- Create:
  - `watchface/src/main/res/values-{ar,fr,tr,in,ur,bn}/strings.xml`;
  - plan 4's watch app and watch widget `.lproj/Localizable.strings` (and `InfoPlist.strings`) in the six others, where plan 4 left English alone.
- Modify:
  - `tools/i18n-check.py`: the Android roots `wearApp/src/main/res` and `watchface/src/main/res`, with `ANDROID_LANGS` and no `ui_language` rule there;
  - a `.strings` parity check over every `iosApp/**/<lang>.lproj` set: equal keys, equal placeholders, seven folders;
  - `SAME_OK` gains the keys that are the same in every language: `watch_face_name`, `complication_mark`, `complication_mark_description` and the Western-digit previews (`preview_prayer_time`, `preview_day_number`).
- The words: every watch-system word spec 1.3 lists:
  - the face's name, and its style, colour and slot names;
  - the app's name;
  - the complication and Tile picker entries, and their previews;
  - the watchOS gallery name and description;
  - "Open Taqwa on your phone" and "Update Taqwa on your watch";
  - Task 5's `face_*` fallbacks.

  The style and colour names in Arabic are spec 1.3's table (المحراب, الحلقة, الشمس, القمر; كهرماني, لؤلؤي, ميرمية, ليلي), for Mohamed to read.

- [ ] **Step 1: Extend `i18n-check.py`.** Run `scripts/check-strings.sh`. Expected: FAIL, naming every missing watch translation.
- [ ] **Step 2: Draft each language from the app's own terms.** Draft from the phone's existing translations of the prayer names, "Next prayer", "Dates" and "Taqwa", following the house rules. Mark each `<!-- draft, to native review (plan 5 Task 11) -->`.
- [ ] **Step 3:** Run `scripts/check-strings.sh`. Expected: PASS.
- [ ] **Step 4: Check on `emulator-5558` with the watch's language set to Arabic, then Bengali,** after `tools/watchface/push-face.sh` has put this commit's face on. Expected (screencaps): the face editor's style, colour and slot names, the complication picker's entries and the app's name are in that language. Nothing is clipped. The face itself still speaks the phone's language.
- [ ] **Step 5: Commit** with the message "Strings: the watch apps' own words in seven languages, and the string check reads them".

### Task 9: The privacy text, as Ruling 39 rules, everywhere it lives

**Precondition, and why it gates the cut:** plan 2's gate binds this plan: "No store build carries a sender until every line below reads as ruled or as he approved". Testers get candidate C on Play's closed track and TestFlight, so every line of items 1–3 is applied before C is cut (the cut gate), and the public pages are live before any store track takes it (Task 17's M10a). The ledger must record M3, his approval of plan 2's report, question 7's lines with it.
- The task may start before M3. Items 1 and 2 go in as ruled, and each item 3 line as its draft. Its row in `privacy-check.py` is marked `awaiting`.
- `privacy-check.py` exits 1 while any row is `awaiting`, naming each, so no candidate can be cut.
- His answer turns each `awaiting` row into the text he approved, applied in every language, and the row's mark is removed.
- **M3 was given on 31 October 2026** (the ledger's "[Lead] 31 Oct ~13:30 SAST: M3 GIVEN"). Mohamed, in chat: "M3 approved, with your two suggested tweaks (the opening and the backups sentence)."
  - Every awaiting row is approved as drafted, but for his two English changes, carried into the six other languages with the same meaning (drafts to native review, Task 11):
    - the policy's opening ends "If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below." in place of the drafted "A watch you add Taqwa to may be reached over the internet too, encrypted, as below.";
    - Backups' earlier sentence reads "the only files of Taqwa's own that are backed up are the widgets' small caches, …, neither of which contains your location.", without "and that backup is encrypted by Apple and never visible to us" (the sentences after it say how a backup is encrypted). The gate holds it as the rows `3-4d-policy-backups-caches`, whose old text is that dropped clause in each language.
  - The stores' recommended answers (Play: no data collected; App Store: Data Not Collected) were approved with it, which is M9 (Task 17 enters them).
  - So `privacy-check.py` has no `awaiting` row, and `scripts/test.sh` runs it strict, without `--allow-awaiting`. The policies' "Last updated" stays 30 October 2026, which the date check accepts.

**Files:** every line under plan 2's "The lines that change", items 1–3, in each of its languages, from plan 2's report:
- `iosApp/iosApp/{en,ar,fr,tr,id,ur,bn}.lproj/InfoPlist.strings` and `Info.plist`'s fallback;
- the Compose `values*/strings.xml`: `onboarding_location_body`, `location_privacy_note`, `about_stays_*` and `about_online_*`;
- `PRIVACY.md` and `PRIVACY.{ar,bn,fr,id,tr,ur}.md`: the lines, the "Your watch" paragraph and the "Last updated" date;
- `site/pages/*/home.html`, `support.html` and `meta.json`;
- `docs/store/listing/*.md`: lines 49–50, and `en.md:115`'s review notes;
- `README.md`, `CONTRIBUTING.md` and `SECURITY.md`;
- a new `docs/store/app-review/<date>-notes-<V>-<C>.txt` (at most 4,000 UTF-8 bytes), with `en.md:117` pointed at it.

Also:
- Modify: `docs/STORE-PRIVACY.md`: Data safety and App Privacy answered again; a Wear app section (its permissions, `permissions.txt`); a watchOS section (its `PrivacyInfo.xcprivacy`, plan 4's).
- Create: `tools/privacy-check.py`, run from `scripts/test.sh`.

**Interfaces:**
- `tools/privacy-check.py [--pages] [--root <dir>]` holds one row per line in plan 2's report: (file, language, the old text that must be gone, the new text that must be there, `approved` or `awaiting`). It checks:
  - every row's old text is gone and its new text is there;
  - no row is `awaiting`;
  - every `PRIVACY*.md` "Last updated" is on or after the watch release's branch date;
  - plan 2's sweep (its Task 3 Step 7 `grep`) prints no line outside the report's items 1–5. Since Ruling 69 item 1 (31 October 2026), only a row's own words are the row's: on a line that holds a row's new text, the words beside it are swept as any other line is, and those the report keeps there are `KNOWN` whole (`^…$`). There a `KNOWN` entry excuses the words only if it matches all of them (`re.fullmatch`), so a promise left beside an approved line is seen, even next to words that `KNOWN` keeps elsewhere in the file (review round 1).

  It exits 1 with each failing row.
  - `--pages` checks only the public pages' rows, and sweeps only those files (`PRIVACY*.md`, `site/pages/`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`). `--root` reads another tree. Both are for Step 5's check against `origin/main`.
  - `--allow-awaiting` reports `awaiting` rows as warnings and does not fail on them. `scripts/test.sh` passes it, so that the suite stays green for other work until M3. The cut gate runs the script without it. Since M3 (31 October 2026) `scripts/test.sh` runs it without the flag too.

- [ ] **Step 1: Write `tools/privacy-check.py`.** Run it. Expected: FAIL on every row.
- [ ] **Step 2: Apply the lines** as ruled and as approved, in every language, from the report's drafts.
  - Mark each non-English line "draft, to native review (plan 5 Task 11)" where the file allows a comment. Otherwise list it in Task 11's table.
  - The site's privacy pages follow `PRIVACY*.md` through `site/build.py`.
- [ ] **Step 3: Answer the stores' forms again, against their own definitions.** Quote each in `STORE-PRIVACY.md`, with the answer and why.
  - **Play Data safety:**
    - Google, read 24 October: "'Collect' means transmitting data from your app off a user's device". Data "unreadable by you or anyone other than the sender and recipient as a result of end-to-end encryption" does not need to be disclosed.
    - The feed goes only to the user's own watch, over Bluetooth, or through Google's relay end-to-end encrypted. Taqwa and every intermediary cannot read it.
    - **Recommended:** "No" stays.
    - If Google's text on the day no longer exempts it, the fallback is "Approximate location: collected, not shared, optional, for app functionality, encrypted in transit, deleted by uninstalling Taqwa from the phone and the watch".
  - **App Store App Privacy:**
    - Apple: data is collected when it is transmitted off the device so that the developer or its partners can access it beyond servicing the request in real time.
    - WatchConnectivity moves it between the user's own devices, encrypted with the pair's keys, and neither Taqwa nor a partner can access it.
    - **Recommended:** "Data Not Collected" stays.
  - Mohamed approves both answers in chat before Task 17 submits them.
- [ ] **Step 4:** Run `python3 tools/privacy-check.py`, `scripts/check-strings.sh`, `python3 tools/store-listing-check.py` and `python3 site/build.py --check`. Expected: PASS once M3 is recorded; before it, `privacy-check.py` fails on the `awaiting` rows alone. Then check that the notes file is at most 4,000 bytes (`wc -c`).
- [ ] **Step 5: Commit in two parts,** so that the public pages can go live alone (M10a):
  1. **The public pages:** `PRIVACY*.md`, `site/pages/*/` (the home page's privacy rows, `support.html:37`, `meta.json`), `README.md`, `CONTRIBUTING.md`, `SECURITY.md` and `tools/privacy-check.py`. Message: "Privacy: only your own devices can see your location, and the watch's relay in the policy, in seven languages".
     - It must apply to `origin/main` alone. In a scratch worktree at `origin/main`, `git cherry-pick <sha>` applies with no conflict, and there `python3 site/build.py --check` and `python3 tools/privacy-check.py --pages` pass. Remove the scratch worktree after.
     - If it conflicts, the Lead makes the main-side commit from the same lines in the scratch worktree, with the same message, and the ledger records both shas.
  2. **The rest:** the app's strings and plists, the store listing lines and the new notes file, `STORE-PRIVACY.md` and `scripts/test.sh`. Message: "Privacy: the app's promise for the watch, and the stores' forms answered again".
  - Once M3 is recorded, each `awaiting` row's change is a further commit in the same two parts.

### Task 10: The README, the site, the store copy and the release notes

**Files:**
- Modify:
  - `README.md`: a "On your wrist" part under "What it does", the Roadmap, the Project layout's watch modules and targets, and the build steps for the face key and `face-gate.sh`;
  - `site/pages/*/home.html`: a features row "On your wrist";
  - `site/pages/*/support.html`: a new answer, `<h2 id="watch">`;
  - `docs/store/listing/*.md`: each store's own watch paragraph, in the Play full description and in the App Store description, which becomes its own text (below); and the App Store "What's new";
  - `tools/store-listing-check.py`: the release-notes files, at 500 characters per language, and the platform rule.
- Create:
  - `docs/store/release-notes/<V>-<C>.txt` (the phone, in Play's `<en-GB>…</en-GB>` tag template, seven languages);
  - `docs/store/release-notes/<V>-<100000+C>-wear.txt` (the Wear OS track);
  - for each later candidate, its own two files at its code (Task 13 Step 6). Their text is C's when no change since C alters what a user sees. Otherwise the changed bullet is rewritten, and its translations go through Task 11's review before that candidate is cut.

**Each store names only its own watch.** App Store Review Guideline 2.3.10 forbids naming other mobile platforms in App Store metadata, and build 34 was rejected once already. On Play, an Android phone cannot use an Apple Watch. So the App Store "Description (4000)" in each `docs/store/listing/<lang>.md` stops being "(The same text as the Play full description above.)" (`en.md:93`). It becomes its own text: the Play full description with the Apple paragraph in place of the Wear one. Google's Wear app quality item WO-G2 asks a listing to name the Tile and complications when the app has them.

**The English, fixed here:**
- **The Play full description's paragraph:** "On your wrist: on Wear OS watches such as Galaxy Watch, Taqwa shows the next prayer and its countdown on its own watch face, in complications for any face and in a Tile, counts tasbeeh with the bezel or the crown, and points to the Qibla. The times come from your phone, so your watch and your phone always agree."
- **The App Store description's paragraph:** "On your wrist: on Apple Watch, Taqwa shows the next prayer and its countdown in your watch face's complications, counts tasbeeh with the Digital Crown, and points to the Qibla. The times come from your iPhone, so your watch and your iPhone always agree."
- **The Play phone notes' bullet:** "• Taqwa on your watch: a watch face, complications and a Tile, tasbeeh and Qibla, on Wear OS watches such as Galaxy Watch". It joins the build's other bullets in `<V>-<code>.txt`.
- **The App Store "What's new" bullet,** in each listing file: "• Taqwa on your Apple Watch: the next prayer in your complications, tasbeeh with the Digital Crown, and Qibla".
- The Wear track's notes: "The Taqwa face in four styles, complications for any face, a Tile, and Today, Tasbeeh and Qibla. Needs Taqwa on your phone, which sends the prayer times."
- The support answer covers:
  - installing, from the phone's Settings › Watch or the watch's Play Store, and on the Apple Watch with the iPhone app;
  - adding the face;
  - that the watch speaks the phone app's language;
  - "Open Taqwa on your phone";
  - calibrating the watch's compass;
  - that the watch keeps working for weeks away from the phone;
  - on Wear OS 3, the default (a)'s line: the countdown moves only at each prayer. Task 12's ruling on the Lead's call 6 was (b) (Ruling 72 item 1), and the line stays as written (Ruling 72 kept it; Ruling 74 item 6): it is Mohamed's text, approved at M8 in seven languages, and it is true of the ring, whose countdown still moves only at each prayer, while the text forms show the prayer's time.

  It is at most five short paragraphs.

- [ ] **Step 1: Extend `store-listing-check.py`.**
  - The release-notes files at 500 characters per language, and both files present for the code in `androidApp/build.gradle.kts`.
  - The platform rule: no field under `## App Store` holds `Android`, `Wear OS`, `Galaxy`, `Google Play` or `Play Store`. No field under `## Google Play`, and neither of the newest code's notes files, holds `Apple Watch`, `watchOS` or `Digital Crown`. An App Store description that is only a note in parentheses fails as empty. The names stay in Latin script in every language (Task 11's reviewers are told so), so the rule reads all seven.
  - Its self-test, on scratch copies: an App Store description naming Galaxy Watch fails, and a Play description naming Apple Watch fails.

  Run it. Expected: FAIL, because the new files are missing and every App Store description is a note.
- [ ] **Step 2: Write the English. Then draft the six other languages,** each marked for Task 11. Each language's App Store description is its Play full description with the Apple paragraph in place of the Wear one, and with any line the iPhone app does not have left out.
- [ ] **Step 3:** Run `python3 tools/store-listing-check.py` and `python3 site/build.py --check`. Expected: PASS. Then open `_site/support/` and `_site/ar/support/#watch` in the browser pane. Expected: the answer is there, right to left in Arabic, with no horizontal scroll at 375 px.
- [ ] **Step 4: Commit** with the message "Docs: Taqwa on your wrist in the README, the site, the store copy and the release notes, in seven languages".

### Task 11: Native review of every new phrase

**Files:**
- Modify: every file holding a "draft, to native review" marker from plans 2–5, and `docs/BUILD-LOG.md` (a section "Watch words, native review (<date>)")
- Create: `docs/superpowers/reports/2026-10-24-watch-native-review.md`

- [ ] **Step 1: The inventory.**
  - `git grep -n "draft, to native review"`, plus every key added or changed in any `strings.xml`, `.strings`, `WatchWords.kt`, `PRIVACY*.md`, `site/pages/` or `docs/store/` since plan 2's base.
  - Write it as one table per language in the report: key or line, English, draft.
  - Expected: the table holds every marker the grep finds, and nothing is unlisted.
- [ ] **Step 2: Five native reviewers,** one subagent each for fr, tr, id, ur and bn, as in the 14 September launch sweep (`docs/BUILD-LOG.md`).
  - Each reads its whole table against the English and the app's existing words in that language.
  - Each keeps product and platform names in Latin script: Taqwa, Wear OS, Galaxy Watch, Apple Watch, Digital Crown, TalkBack and VoiceOver.
  - Each returns must, should and nit corrections, each with its reason.
  - Apply every must and should. Leave the nits as notes in the report.
- [ ] **Step 3: Mohamed's reading list:** the English and the Arabic of the report, with the spec's eight Arabic style and colour names first. Post it to him in chat as one list. His corrections are applied when he answers. Until then the Arabic keeps its markers, and no candidate is cut (the cut gate).
- [ ] **Step 4: Remove each marker** that is reviewed and applied. Run:
  - `scripts/check-strings.sh`;
  - `python3 tools/privacy-check.py`;
  - `python3 tools/store-listing-check.py`;
  - `python3 site/build.py --check`;
  - plan 2's size tests (`./gradlew :shared:testAndroidHostTest --tests '*WatchFeedBuilder*'`);
  - for a changed short form of the Qibla dial (`distanceOnDial`, `qiblaDetailOnDial`): the files plan 3's Ruling 67 note names for its language, the vectors regenerated as it says, then `scripts/test.sh` (`QiblaFitTest` among it).

  Expected: PASS. `git grep "draft, to native review"` prints only lines Mohamed has not yet answered, and nothing once he has, which the cut gate needs.
- [ ] **Step 5: Commit** with the message "Strings: the watch's words read by native speakers in five languages, with the corrections applied".

### Task 12: The emulator gates: Wear OS 3 and 4, the 12-hour observation, and the release candidate

**Files:**
- Create:
  - `scripts/wear-old-emulator.sh <30|33>`: the AVD `Taqwa_Wear_OS_3` (`system-images;android-30;android-wear;arm64-v8a`, port 5568) or `Taqwa_Wear_OS_4` (`system-images;android-33;android-wear;arm64-v8a`, port 5566), round, waiting for boot;
  - `docs/superpowers/reports/2026-10-24-watch-gates.md`.
- Modify, for the observation's log lines (Step 0):
  - `wearApp/src/main/kotlin/world/taqwa/app/wear/complication/ComplicationSources.kt`;
  - `NextPrayerSource.kt`, `DatesSource.kt`, `DaySource.kt` and `DayDialSource.kt` in the same package;
  - the callers of `ComplicationSources.requestUpdates`: plan 3's `onAppOpened` and Tile, plan 2's `DailyNudge.nudge` and the feed store's `onStored`.
- Modify, for the Galaxy's compass pass (plan 3's hand-off): plan 3's `wearApp/src/main/kotlin/world/taqwa/app/wear/qibla/WristCompass.kt`.
- Modify, for plan 3's hand-off "the Tile's preview image, refreshed with the final look": `wearApp/src/main/res/drawable/tile_preview.png`.
- Test: `wearApp/src/test/kotlin/world/taqwa/app/wear/complication/TimelineLogTest.kt` and `wearApp/src/test/kotlin/world/taqwa/app/wear/qibla/QiblaLogTest.kt`.

**Interfaces:**
- `private fun timelineLogLine(source: String, type: ComplicationType?, steps: Int, lastUntil: Long?): String`, for example `next prayer, RANGED_VALUE: 10 steps, last until 2026-11-02T03:58:00Z`, reached only through `internal fun timelineLog(tagEnabled: Boolean, source: String, type: ComplicationType?, steps: Int, lastUntil: Long?): String?`, null while the tag is off (Ruling 72 item 2). Each source logs it through `internal fun logTimeline(source: String, type: ComplicationType?, plan: List<Step<*>>, tagEnabled: () -> Boolean = { Log.isLoggable(COMPLICATION_TAG, Log.DEBUG) }, write: (String) -> Unit = { Log.d(COMPLICATION_TAG, it) })` in place of today's line; a host test passes its own reader and writer (Ruling 74 item 3).
- `ComplicationSources.requestUpdates(context: Context, why: String = "unknown")` logs `asked again (<why>)` at `Log.i` on success. The callers pass `app start`, `tile`, `daily` and `feed`.
- **The compass log on a store build.** Plan 3 logs `TaqwaWearQibla` in a debug build only, and Task 13 runs the signed candidate.
  - `internal const val QIBLA_TAG = "TaqwaWearQibla"`, and `internal fun qiblaLogOn(debugBuild: Boolean, tagEnabled: Boolean): Boolean`, true when either is. `WristCompass` reads it once each time `readings` starts being collected, with `tagEnabled = Log.isLoggable(QIBLA_TAG, Log.DEBUG)`.
  - So the candidate logs only after `adb -s <watch> shell setprop log.tag.TaqwaWearQibla DEBUG` and a fresh open of Qibla, and stops after `setprop log.tag.TaqwaWearQibla INFO` or a reboot.
  - `internal fun qiblaLogLine(rvAccuracy: Int?, magAccuracy: Int?, fieldMicroTesla: Double?, trusted: Boolean, rawPairActive: Boolean, tiltDegrees: Double, displayRotation: Int, headingDegrees: Double?): String`, for example `rv 3, mag 2, field 46.1 µT, trusted, raw pair off, tilt 12°, rotation 0, heading 231°`. It takes no Qibla bearing, declination or place, so the log says nothing of where the wearer is (Ruling 39).

Both system images are downloads. They need the Lead's OK, recorded in the ledger.

- [ ] **Step 0: Make the observation and the compass readable, and refresh the Tile's preview.**
  - Write `TimelineLogTest.theLogLineNamesTheTimelinesEnd`: the example above, and `last until none` for a null.
  - Write `QiblaLogTest.theQiblaLogIsOnInADebugBuildOrWhenItsTagIsEnabled`: `qiblaLogOn` over the four cases, false only for (false, false). And `theQiblaLogLineIsTheExample`: the example above, and `field none, heading none` for nulls.
  - Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL.
  - Implement the lines, and `WristCompass`'s gate in place of plan 3's debug-only one.
  - Run the tests, then `./gradlew :wearApp:assembleStaging -Ptaqwa.unsignedRelease=true`. Install it on `emulator-5558`. Expected:
    - after `adb -s emulator-5558 shell setprop log.tag.TaqwaComplications DEBUG` (the `last until` line is written only while its tag is enabled, Ruling 72 item 2), `logcat -s TaqwaComplications` shows both lines after a feed is stored, so R8 kept them; `adb -s emulator-5558 shell 'setprop log.tag.TaqwaComplications ""'` then puts the tag back unset, and `adb -s emulator-5558 shell getprop log.tag.TaqwaComplications` prints an empty line. The quotes go round the whole command: `adb shell` joins its arguments with spaces, so a bare `""` is lost, `setprop` prints its usage and the tag stays `DEBUG`;
    - with Qibla open, `logcat -s TaqwaWearQibla` prints nothing for 5 s. After the `setprop … DEBUG` and a fresh open of Qibla, one line a second. After `setprop … INFO` and a fresh open, nothing again.
  - **The Tile's preview, final.** Shoot the English 14:58 Tile on the staging build as plan 3's Task 8 Step 5 shot it, cropped the same way. If it differs from `tile_preview.png` (`tools/watchface/compare.py`, 0 levels), replace the file. Expected: the Tile picker on `emulator-5558` shows it.
  - Commit with the message "Wear: the sources log how far their timeline reaches and why they were asked again, the compass logs on request, and the Tile's preview is the final look".
- [ ] **Step 1: Wear OS 4 (API 33) on `emulator-5566`, then Wear OS 3 (API 30) on `emulator-5568`,** one at a time. Install the debug Wear app, which holds the sample feed. R8 is proven in Step 3 and in Task 3. On each:
  - Expected:
    - the app starts with `logcat -b crash` empty. That proves `tools:overrideLibrary` and the API 36 guard on an OS below the library's floor;
    - the Tile and the complications on a system face work from a feed;
    - Today shows no face row;
    - the watch publishes no `taqwa_face_addable`;
    - `MY_PACKAGE_REPLACED`, by reinstalling with `-r`, leaves `logcat -b crash` empty and logs no `TaqwaFace update`;
    - a cold start under `screenrecord --time-limit 3` shows Task 3's icon on black, then Today, which on API 30 is `core-splashscreen`'s own;
    - the Tile's renderer schema, recorded from the Tile's request: at 1.2 or above the line counts down, and below it reads the label and the clock ("ASR 16:10"), never a stale countdown (plan 3's call 6).
  - On API 30 only, record the countdown below API 33: a NextPrayer ranged complication on a system face, shot at an entry's start and 5 minutes later. Expected: the same words in both, the known limit (`NextPrayerSource`'s KDoc, its "Below Wear OS 4" paragraph).
  - Stop each with `adb -s emulator-5566 emu kill` or `adb -s emulator-5568 emu kill`.
  - **The Lead's call 6:** the Lead writes the ruling in the ledger, under the next free number, from what API 30 showed. Its follow-up is a fix task with its own test, for (b) or (c), or a line in Task 10's support answer, for (a). For (c) the ledger records Mohamed's answer first.
- [ ] **Step 2: The 12-hour observation (Ruling 22, a ship gate).**
  - Staging on `emulator-5558`, not while plan 2's Task 7 holds it. First `adb -s emulator-5558 shell setprop log.tag.TaqwaComplications DEBUG`, since the sources write their `last until` lines only while that tag is enabled, in every build (Ruling 72 item 2); then a fresh feed from the phone. Record the timeline's end from the first `last until` line.
  - Then stop `emulator-5562`, so that nothing refreshes the feed.
  - Capture `adb -s emulator-5558 logcat -v time -s TaqwaComplications TaqwaWearFeed` to the scratchpad, as a background command. Leave the watch untouched: no Tile view, no app open.
  - **Pass:** within the 12 h, at least one source request is logged (a `steps, last until` line), whether the system's, the update period's or the safety net's (an `asked again (<why>)` line), and its `last until` is later than the first one's.
  - **If none arrives in 12 h,** the observation runs on until plan 2's daily nudge logs `asked again (daily)` and the source request that follows, about 24–30 h after the start. It fails if the timeline's recorded end passes first.
  - In either case, at the request's time the face's next prayer and countdown match the feed's times (a screencap against `WatchRules` over the stored feed).
  - The report names which path fired, with its log lines. A fail goes back to the Lead as a finding.
  - **Only once the capture is over and read,** the tag: `adb -s emulator-5558 shell getprop log.tag.TaqwaComplications` still prints `DEBUG`: a reboot clears the setprop, and then no line is written, so a missing line would not mean no request. Then `adb -s emulator-5558 shell 'setprop log.tag.TaqwaComplications ""'`, quoted as Step 0 says, puts the tag back unset, and the same `getprop` prints an empty line. Not before: with the tag unset no `last until` line is written, and the ship gate would fail falsely. **The observation that started on 6 November at 20:44 (staging `62e88a0a`) predates Ruling 72's gate:** its start records no setprop, and its build wrote its `last until` line without one, so its closing `getprop` may print an empty line, and that means nothing, not a reboot (Ruling 76 item 4).
- [ ] **Step 3: The dry-run release candidate on the paired emulators.** Install Task 7's dry-run APKs on `emulator-5562` and `emulator-5558`, after uninstalling the debug builds and the face (`adb -s emulator-5558 uninstall world.taqwa.app.watchfacepush.taqwa`), because the key differs.
  - Expected:
    - the default face appears;
    - the phone's rows act as in Task 6 Step 6;
    - the watch's row makes the face active, or shows the guidance line if the emulator's one activation was spent in Task 5 (the Global Constraints' once-only rule), recorded as which;
    - the feed, the complications, the Tile and the three screens work.
- [ ] **Step 4: Write the report.** Commit it with the message "Docs: the watch's emulator gates: Wear OS 3 and 4, a day alone, and the release candidate".

### Task 13: His Galaxy Watch 8 Classic, and both phones' smoke test

**Files:** Create, in the 1.2.0 smoke test's voice (`docs/store/smoke-tests/1.2.0-38.md`: checkboxes, what to do, what he should see):
- `docs/store/smoke-tests/<V>-<C>-galaxy-watch.md`;
- `docs/store/smoke-tests/<V>-<C>.md`, the phones.

**Builds:** release candidate C, cut only past the cut gate (the Global Constraints): `scripts/bump-version.sh V C`, committed. Mohamed signs the face first (`scripts/release-face.sh`), then the Lead runs the real `scripts/release.sh` (the Global Constraints' keys).
- The phone APK goes on his S23 as the 1.2.0 smoke test installed build 38: by him, over the store build.
- The iPhone build goes on the work iPhone 12 by Xcode Run, by him, never TestFlight.
- The Wear APK goes on the watch with `adb -s <watch> install -r`.
- A later candidate goes on the same way, over the one before, until his watch moves to Play's builds in Task 17 Step 3. From then on it comes from Play's internal track.
- First check that the phone and watch builds carry one certificate (`adb -s <watch> shell dumpsys package world.taqwa.app | grep -A3 signatures` on the watch, and the APK's own on the phone). The Data Layer joins only equal keys.

- [ ] **Step 1: Write the checklist** from plan 1's "Plan 1 as built" section (its plan 5 device-pass list), the ledger's close (L489–L500), Rulings 31, 33–35, 38, 39 and 42, plan 3's "Open questions for Mohamed" (question 12 here) and the "Plan 5 (ship)" list in plan 3's "What plans 4 and 5 take from this plan". Group it as below. Each check says who does it (Mohamed by eye, or the agent over adb) and what passes.
  1. **Install and link:**
     - the signatures match;
     - the face is in the face list, not active (`TaqwaFace` lines show no `update` and no `setActive`);
     - the phone's Settings › Watch shows "Add the Taqwa face". Its tap opens the face screen on the watch. Press Back there, not the button;
     - this row's idle run (the phones' section 2, Step 2) comes here. The check waits for it to be written down, since Today's chip ends the row;
     - Today's "Add the Taqwa face" chip, then the button and Allow, make the face active. This is the watch's one activation, so it must be ACTIVE (Google allows one activation per app; a reinstall does not reliably reset it);
     - afterwards neither the phone's row nor Today's chip shows. `taqwa://face` (adb) shows "Taqwa is your watch face.";
     - the city shows on the Tile.
  2. **App = site = watch:** after Fajr, mid-afternoon and after Isha, the face's next prayer, its time and the Tile's rows equal the phone's Prayer screen and taqwa.world's page for the city.
  3. **The face:**
     - all four styles in all four colours, awake and always-on;
     - English and Arabic, on 12 and 24 hours (the phone app's language and clock setting);
     - always-on: the look and brightness, and the clock's weight;
     - the PM marker, the proportional Arabic-Indic clock and the Bold weight;
     - the Hijri steps (Ruling 33) and the weekday sizes (Ruling 34);
     - the wearer's slots with Samsung's and Google's sources: ranged-first, icon-only, a short text's title, a missing icon;
     - the wearer's values under an Arabic watch language;
     - the emulator-only constructs: cross-slot reads, multi-run text, the clip to the box, the font cache, `COUNTER_CLOCKWISE` and the editor outlines;
     - the box rounding on 438 px.
  4. **Screen readers (Ruling 38, a ship gate):**
     - TalkBack, and Samsung's own reader if One UI Watch offers one beside it;
     - on every style, swiping, Ruling 42's order: the time, the prayer with its countdown and time, the dates, then the wearer's slots, once each, and nothing said in 10 s untouched. The face declares 7, 0, 1, 2, 3, 6, 4, 5, so any other order is a finding;
     - by touch, a held finger on the prayer line, the time, the dates and two points elsewhere on the face: what is said, per style, written down word for word. If a touch where the prayer's and the dates' boxes overlap (most of the face) says the dates, Task 14 runs: Ruling 42's reopening of option (b). If it says the prayer, or finds no face item (as on the emulator), Ruling 42 holds;
     - plan 3's Today, Tasbeeh, Qibla and Tile, and the face screen.

     Use `tools/watchface/talkback.sh`, taught the watch's TalkBack package (`pm list packages | grep -i talkback`), with Mohamed listening where the reader logs no speech.
  5. **Samsung's pickers:**
     - the day dial is offered in no picker (Ruling 31);
     - NextPrayer, Dates and the mark work on two of Samsung's own faces.
  6. **Plan 3's screens and Tile on the real bezel, compass and renderer** (plan 3's "Plan 5 (ship)" list). Mohamed judges plan 3's six defaults (question 12) by eye, and the ledger records each answer.
     - Tasbeeh: after prayer counts 1 to 100 with ticks at 33 and 66; one bezel detent per count, in the bezel's right sense, and back takes one away; the haptics, and a fast spin across 33 gives the part's buzz whole; his own phrases; the bezel hint on one line.
       - By eye: the phrase list by a swipe up, the Reset row, and the screen timing out mid-count, with a raise of the wrist bringing the count back (question 12's first three).
     - Qibla: the full dial with its rim on the screen's edge; the calibration prompt; the buzz when facing it; the bearing equal to the phone's Qibla card in the same spot.
       - By eye: "Hold your wrist level", and the compass's titles (question 12's fourth and fifth).
     - **The compass's log,** on the candidate itself (Task 12 Step 0). The agent runs `adb -s <watch> shell setprop log.tag.TaqwaWearQibla DEBUG`, then captures `logcat -s TaqwaWearQibla` while he:
       - holds Qibla level on the left wrist, then on the right wrist with the display turned;
       - tilts past 40° and back under 30°;
       - holds it near his laptop;
       - draws a figure of eight.

       Expected, from the lines: near the laptop the field is heard beside a trusted rotation vector, and the dial dims (interference); the accuracies Samsung's sensors report, written down (B6); the right wrist's needle on the left wrist's bearing; the caption switching at 40° and back at 30°; which source drove the heading at each step (`raw pair`). Then `setprop log.tag.TaqwaWearQibla INFO`.
       - A dead rotation vector cannot be made on a working watch. Its fallback is plan 3's `aDeadRotationVectorLeavesTheRawPairInCharge`, and the log only records which source the Galaxy used.
     - **The Tile on Samsung's renderer:** the countdown moves each minute with the app's process dead (`pidof world.taqwa.app` empty), in English and in Arabic, right to left, in the system font. Samsung's Tile picker shows Task 12's refreshed preview. Far north, with the phone's place set to Tromsø or like it, watch the Tile through Fajr: a late renderer once showed the Sunrise minute after Fajr (erratum p5 #23).
     - Today, Tasbeeh, Qibla and the Tile at the Galaxy's largest font size: nothing clipped.
     - By eye: no time and no page dots on the three screens (question 12's sixth).
  7. **Away and back:**
     - the phone's Bluetooth off for an hour: the face keeps going;
     - a capture of the sources' lines on the candidate (`logcat -s TaqwaComplications`, the `steps, last until` lines) starts with `adb -s <watch> shell setprop log.tag.TaqwaComplications DEBUG` and ends with `adb -s <watch> shell getprop log.tag.TaqwaComplications`, which still prints `DEBUG` (a reboot clears it, and then no line is written, so a missing line would not mean no request), then `adb -s <watch> shell 'setprop log.tag.TaqwaComplications ""'`, quoted as Task 12 Step 0 says, back to unset, and the same `getprop` prints an empty line. Those lines are written only while their tag is enabled (Ruling 72 item 2), and a prayer's start with its date says roughly where the wearer is (Ruling 39), so the check is not over while it still prints `DEBUG`;
     - **The Tile's own refresh, in the same hour:** with a Tile added and not viewed, `logcat -s TaqwaTile` beside that capture, the watch asleep as it falls. Write down the time between the `Tile:` lines, not counting one straight after the Tile is added, which can ask the sources itself (`asked again (tile)`) and is not the system's period. On the awake emulator the system asked within 0.1 s of `TilePlan.freshnessMillis` at 5, at 12 and at 45 minutes, on real time (Task 12 Step 4). This is a record, not a gate: a longer gap costs only what the code already allows. Past the moving 80 minutes the line says the start's time, and a late change can keep a start's time and the rows before it on the Tile after that start has come, for up to three quarters of that entry again (`TilePlan`'s KDoc). Write down if a lit row goes stale;
     - Bluetooth back: the handshake sends any change;
     - a change of the phone app's language or city reaches the watch within a minute.
  8. **The battery (Ruling 35):** a day on the Moon beside a day on Arch, alike in use. `dumpsys batterystats --reset` at each day's start, then his battery percentage and the face runtime's share at its end. He chooses the sweep (question 6).
  9. **The feed's first sync and handshake (Ruling 70 item 1),** on the candidate itself. The phone's sync prints `TaqwaWatch <reason> <result> <ms>ms` for each ask, and a signed build adds each ask's step times (`TaqwaWatch STEPS …`) only once its tag is set to DEBUG, as the compass's log does (`stepsLogOn` in `shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchFeedLog.kt`, Ruling 70 item 1's review round 2). The lines are on the S23, so its `adb` commands are typed by him (the Global Constraints), and the capture file is handed to the agent:
     - **Before the Wear app's install in Step 4:** `adb -s <S23> shell setprop log.tag.TaqwaWatch DEBUG`, then `adb -s <S23> logcat -v time -s System.out | grep --line-buffered TaqwaWatch > ~/Desktop/taqwa-watch-steps.txt` left running. The setprop lasts until a reboot or `setprop log.tag.TaqwaWatch INFO`, and the next ask reads it, with no restart. If the S23 refuses the setprop, that is recorded, and only the totals are read.
     - **A fresh install's first sync:** the install's first asks (the watch's handshake, `WATCH_ASKED`, and its capability arriving, `WATCH_APPEARED`), each with a `STEPS` line beside it. Write down the total and each step's milliseconds (`echo`, `watches`, `build`, `encode`, `record`, `send`). A `FAILED … cut` names the step that held it (marked `unfinished`), and its ask asked again follows about 30 s later as `STEPS <reason> again …`.
     - **A reinstalled watch's first handshake,** with the uninstall and reinstall Ruling 70 item 2 asks of this pass (one reinstall serves both), the phone app in the background and the S23's screen off (the report's For the Lead 2): the same lines. If the handshake is cut, a `STEPS WATCH_ASKED again` line within a minute shows that the ask asked again ran in a cached process, and none shows that it did not, which is For the Lead 2's remedy for the Lead to rule on. The install row's idle run (the phones' section 2, Step 2) comes between the uninstall and the install, and Settings is then left and the screen put off before the install.
     - **Then** `adb -s <S23> shell setprop log.tag.TaqwaWatch INFO`, and the next ask (a change of city, say) prints its total alone. So the step line was on only on request, on the signed build itself.
- [ ] **Step 2: Write the phones' checklist,** `docs/store/smoke-tests/<V>-<C>.md`, in the 1.2.0 file's shape: Install, then numbered sections, each check a checkbox with what to do and what he should see. He runs it on the S23 and the work iPhone 12 (Xcode Run), before the release goes to either store:
  1. **Basics (both):** it opens on Prayer, Cape Town's times match build 38's, the countdown runs; the widgets show the next prayer; Settings › About Taqwa reads V.
  2. **Settings › Watch (both):**
     - S23 with the Galaxy connected: the section and its rows as Task 6 says, and TalkBack reads them, each row in its set-up below, and no section with the face active;
     - S23 with its Wear OS companion idle, on candidate C, one row at a time, the next only when the last is written down (Ruling 72 item 3, Ruling 74 items 9 and 10, Ruling 76 item 11). One watch shows only one of these rows (`WatchTargetsTest`), so each has its own set-up and its place in the pass (Step 4):
       - **"Add the Taqwa face":** the Wear app installed and the face not active. Inside check 1, before Today's chip: the chip spends the watch's one activation, and the row goes with it.
       - **"Install on your watch":** the Wear app uninstalled. At check 9's reinstall, after the uninstall and before the install.
       - **On the watch, press Back each time.** Never the face screen's button, which spends the activation out of order, and nothing installed from the Play Store's page, since a candidate goes on by `adb`.
     - Each run of either row, in this order:
       - **Go idle, before every run:** Settings left, and the phone with its screen off and unused for at least 30 minutes, so the companion can go cached. A companion that has just answered a tap is awake, so no run follows straight on another. The 30 minutes is a first guess: how long an S23 takes has not been measured.
       - Wake the phone, open Settings › Watch, and **keep the screen on and stay in Settings until the row is written down** (set Settings › Display › Screen timeout to 10 minutes first, and touch nothing on the row to keep it awake): a screen off and on, or leaving Settings, reads the rows again and takes the line with it, so a late answer would never be seen. (Since 9 November the rows are also read in place while Settings stays in front, at the watch's word and for 3 minutes after the tap; those readings keep the line while the row stays.)
       - Tap the row, timing it from the tap: within 10 s the row says "Continue on your watch". The row waits at most 10 s for the companion, so when the line shows tells whether the companion has answered:
         - **Well before 10 s (under about 8 s): the companion was awake and has answered.** Do not tap again: that request is over, so a tap would be sent anew, rightly, and prove nothing. This run saw no late answer and does not count. Write the time down, go idle for at least 2 hours (a night will do), and run the row again. If the line comes that early again, write down **"no late answer seen: the companion stayed awake"**, with both times, and go on. It is a likely result, since a real phone's pairing normally keeps the companion alive (`docs/superpowers/reports/2026-11-06-phone-watch-rows.md`, its Step 6's finding). It fails no build, and it is not a pass of the late answer: that stays unseen on a device, and the Lead reads it so.
         - **"Couldn’t reach your watch" as the first line:** the companion refused or failed, which an idle companion does not explain. Write it down with the time: it is a finding.
         - **At about 10 s: the companion still holds the request, and this is the run the check is for:**
           - Tap again at once, as soon as the line shows: a second tap while the first is still with the watch's companion, within 2 minutes of the first, sends nothing more. It is the companion's answer that ends a request on the phone, not the watch drawing the screen, and past the 10 s the phone shows no sign of that answer. So the second tap is made early. One made as the watch is opening, or after, may come just after the companion answered, and is then sent anew, which is right. The late answer still counts: only "sends nothing more" went unshown, so run the row again after going idle to see that half.
           - The watch opens the Play Store or the face's screen once, when the companion wakes: write down how long after the first tap. If it opens twice, write down both times, for the Lead to read.
           - Write down what the phone showed, one of two: **"the line stayed and the watch opened"**, or **"the line turned to Couldn’t reach your watch"**. This is where a late answer is first seen on a device. The two can't be told further: `QUEUED` and `OPENED` draw the same line and neither writes a log line. If 2 minutes pass with neither, write that down instead, and when the watch opens, if it does. "The line turned", "neither" and a watch that never opens are findings for Step 6, and don't by themselves fail the candidate until the Lead has read them.
           - After that, or 2 minutes after the first tap, a tap sends anew, so the watch opening the screen again for that tap is right, not a failure;
     - S23 with the watch's Bluetooth off: no section, and Settings opens as fast as before;
     - iPhone 12, with no Apple Watch paired: no section, and no "Add to Apple Watch" row anywhere.
  3. **The privacy words (both, in English and in Arabic):** "Only your own devices can see your location." where the location note and About › What stays on your phone show it, and the policy's watch paragraph on taqwa.world's privacy page once it is live. Optional, his choice: the location prompt's text on the iPhone, which needs iOS's Reset Location & Privacy.
  4. **No new permission (S23):** Settings › Apps › Taqwa › Permissions lists what build 38 lists. On the iPhone, iOS Settings › Taqwa shows the same items as before.
  5. **The feed (S23):** a change of city or of the app's language reaches the watch within a minute (shared with the watch's check 7).
  6. **Nothing else moved (both):** a prayer notification fires on time; a recitation plays and its notification opens the ayah; the 1.2.0 checklist's TalkBack and VoiceOver lines in section 6 still hold on Prayer and Settings.
  7. **The Quran as 1.5.1 (42) draws it (both):** the merged build's drawing is pinned byte for byte to 1.5.1's in the tests, but no one has looked at it on a device in the watch build. From `docs/store/smoke-tests/1.5.1-42.md`: section 2's page 411 line 15 (31:11's small alef), section 3's basmala above Luqman, and section 6's 2:4 lam-alef on page 2 line 6, each as that file says he should see it.
- [ ] **Step 3: Mohamed pairs the watch.** Settings › About watch › Software: tap the build number to open Developer options; turn on ADB debugging and Wireless debugging; Pair new device; then `adb pair <ip>:<port>` with the code the watch shows, typed by him. The agent then uses `adb connect` and the watch's serial.
- [ ] **Step 4: Install, then run checks 1–7** in order, writing each result into the checklist file as it is seen: shots, logs and his words. Check 9 runs beside them: its setprop and capture start before the install, its fresh-install lines come with check 1, and its reinstall comes where Ruling 70 item 2's does. He runs the phones' checklist on the same candidate, and his answers go into its file. Two of its runs have a place in this order, and the agent tells him when each comes: section 2's face row inside check 1, before Today's chip, and its install row at check 9's reinstall, between the uninstall and the install. Section 2's first line needs the same two set-ups. The rest of that list can come at any time on the candidate.
- [ ] **Step 5: Run check 8** over his next two ordinary days, and record his choice in the ledger as a ruling under the next free number. Any change to the sweep is a one-line `face-moon.py` edit, with `face-moon.py --check`, `tools/watchface/validate.sh` and the Moon's pass, in its own commit: "Face: the Moon's seconds at <n> Hz, as Mohamed chose after two days (Ruling <n>)". It reaches his watch only in the next candidate (Step 6).
- [ ] **Step 6: Findings, fixes, and the next candidate.**
  - Every failure becomes a finding in the ledger, under "Plan 5 device findings", with its evidence. The Lead writes a fix task for each, with its own test and the two-lens review. Check 4's touch result goes to Task 14 instead, which is a ruling first, not a fix.
  - Fixes land as commits after the candidate, and none reaches a device until the next candidate (the Global Constraints' codes).
  - The next candidate is cut, past the cut gate, once these have landed: the fixes found on the current candidate, the sweep (Step 5), Task 14 if it runs, and, for C+1, her `.watchface` file or her word that her watch cannot make one (Task 15).
  - Its ledger line lists every commit since the candidate before, each with the checks the table below asks for. The checks run on that candidate's own signed build, and their results go into the checklist files under its code.
  - **On every candidate after C:** check 1's signature line; an install over the candidate before, with the face updated in its slot (one `TaqwaFace update`, still active, still customised); check 2 at one moment; the phones' sections 1 and 2 on both phones; and her checks 1 and 2 (Task 15). On the S23, section 2's rows now read no section, with the face active. Its two idle runs are C's alone: the face row has gone, and the install row's uninstall would take his customised face with it (Ruling 70 item 2). A candidate that changes the rows' own code (`WatchRows.kt`, `WatchTargetsSource.kt`, `PlayWatchTargets.kt`) runs Task 6 Step 6 on the emulators again, and the install row's idle run waits for the uninstall he makes anyway, Task 17 Step 3's.
  - **By what changed:**

    | A change to | Run again on the next candidate |
    |---|---|
    | the face (`watchface/`, `tools/face-*.py`) | `scripts/face-gate.sh`, plan 1's pass on `emulator-5558` through `push-face.sh`, check 3 on the styles it touches, and check 4 on them |
    | the Moon's sweep | the face's row for the Moon, and check 8's Moon day again only if the rate went up |
    | the Wear app (`wearApp/`, `watchcore/`) | its tests, `tools/wear-release-check.sh release`, the check of the screen or source it touches with TalkBack on it, and check 7 |
    | the phone (`shared/`, `androidApp/`, `iosApp/` outside `WatchFace/`) | its tests, the phones' sections it touches and section 6 on both phones, and checks 2 and 7 |
    | the watchOS app | its tests, `ios-release.sh check` and `export-check`, and her checks of the screen it touches, with check 6 (VoiceOver) on it |
    | `iosApp/iosApp/WatchFace/Taqwa.watchface` | her check 8 |
    | words or privacy lines | Task 11's review of the changed lines, `privacy-check.py` and `check-strings.sh`; for a public page, M10a again before a track takes the candidate |

  - It gets its own release-notes files (Task 10).
  - A candidate that fails any of these is not production. The next one is made the same way.
- [ ] **Step 7: Commit** both checklists with their results, with the message "Docs: the watch release on his Galaxy Watch 8 Classic and on both phones, check by check".

### Task 14: Only if the Galaxy's touch finds the dates over the prayer: option (b), as Ruling 42 allows

Ruling 42 (which replaced Ruling 40, option (b)) is built: plan 1's Task 12 close, from WIP `07e511be`, declares the slots 7, 0, 1, 2, 3, 6, 4, 5. `SourceManifestTest.aScreenReaderIsGivenTheTimeThePrayerTheDatesAndTheWearersSlotsEachOnce` expects `listOf("0", "2", "4", "5")`. A swipe reads the prayer before the dates, which is what a TalkBack user gets for certain. Touch could not be judged on the emulator, where a held touch reached no face item at all. Ruling 42 leaves one door open: "If touch there finds the dates over the prayer, (b) may be reopened with that evidence."

This task runs only if Task 13's check 4 finds exactly that: a held touch where the prayer's and the dates' boxes overlap says the dates. If it says the prayer, finds no face item, or check 4 has not run yet, nothing here is done, and the ledger says "Ruling 42 holds on the Galaxy" with check 4's words.

**Files** (only when it runs):
- Modify:
  - `watchface/src/main/res/raw/watchface.xml`, and any generator in `tools/face-centre.py`, `tools/face-moon.py` or `tools/face-sun.py` that writes the slots' order (Ruling 40's build, `fef65dc7` and `a75edce9`, and its revert, `07e511be`, name them);
  - `wearApp/src/test/kotlin/world/taqwa/app/wear/complication/SourceManifestTest.kt` and `SpokenTest.kt`'s KDoc;
  - spec 3.3 and 3.6, plan 1's Task 12 as-built lines, and `task-12/touch.txt`.

- [ ] **Step 1: The Lead rules, in the ledger, under the next free number,** replacing Ruling 42. It quotes check 4's words per style, and reopens option (b): slot 2 declared before slot 0, 7, 2, 0, 1, 3, 6, 4, 5. The swipe order becomes the time, the dates, the prayer with its countdown and time, then the wearer's slots, and a touch over most of the face finds the prayer, as the Galaxy showed.
- [ ] **Step 2: Write the failing test.** Rename `aScreenReaderIsGivenTheTimeThePrayerTheDatesAndTheWearersSlotsEachOnce` to `aScreenReaderIsGivenTheTimeTheDatesThePrayerAndTheWearersSlotsEachOnce`. Expect `listOf("2", "0", "4", "5")`, and the comment says slot 2 is declared first, citing the new ruling. Run `./gradlew :wearApp:testDebugUnitTest`. Expected: FAIL, the face still declaring 0 before 2.
- [ ] **Step 3: Move slot 2's `ComplicationSlot` block before slot 0's,** in whatever writes it, as `fef65dc7` did. Put `SpokenTest`'s KDoc to "the time, then slot 2, then slot 0". Bring spec 3.3 and 3.6, plan 1's lines and `touch.txt` to the new ruling, with the Galaxy's words. Then run:
  - every generator's `--check`;
  - `tools/watchface/validate.sh`;
  - `./gradlew :wearApp:testDebugUnitTest`;
  - `tools/watchface/push-face.sh`, so the reordered face is the one in the slot, then plan 1's full pass (`tools/watchface/pass.sh`).

  Expected: PASS, and the pass within 3 levels, since nothing drawn changes.
- [ ] **Step 4: Check it again on the Galaxy, on the next candidate** (Task 13 Step 6), the only way the reordered face reaches his watch: swiping and touch, on all four styles, written into Task 13's checklist under that candidate's code.
- [ ] **Step 5: Commit** with the message "Face: the dates declared before the prayer, as his Galaxy reads a touch (Ruling <n>)".

### Task 15: Her Apple Watch

**Files:** Create `docs/store/smoke-tests/<V>-<C>-apple-watch.md`, in the same voice, written for her and Mohamed.

**Builds:**
- **Candidate C:** the iPhone archive with the watch app, uploaded by `scripts/ios-release.sh` with Mohamed's OK. TestFlight then gives it to her iPhone, never to the work iPhone 12.
- **One Debug run, for check 10 only:** plan 4's DEBUG `WatchHarness` on her real pair, by Xcode Run on her iPhone. Her iPhone and watch are first registered under team 3K93P4PA5H, with Mohamed's OK, his hands at the Mac (question 1). TestFlight's build goes back on afterwards.
- **Candidate C+1** (the Lead's call 7): the next code, with the `.watchface` file check 8 makes bundled at `iosApp/iosApp/WatchFace/Taqwa.watchface`, and every fix since C. It is uploaded with Mohamed's OK. Check 8 runs on it, with the checks Task 13 Step 6 asks for its other changes.

- [ ] **Step 1: Write the checklist.** Gates are marked; every gate must pass for the ship gate (Task 17).
  1. **Install:**
     - TestFlight on her iPhone, then accept the invitation and install;
     - the watch app installs with it (Watch app › My Watch › Available Apps, if automatic install is off);
     - the feed arrives (the city in Today).
  2. **App = watch:** at three moments, the complications' next prayer and time equal her phone's Prayer screen.
  3. **The complications,** on her face, in each family (circular, corner, rectangular, inline), tinted and full colour. Then the Smart Stack card, and its relevance before a prayer.
  4. **The screens:**
     - Today;
     - Tasbeeh with the crown: detents, down takes one away, haptics, 33 · 33 · 34 to 100. Her own phrases, added in her iPhone app, appear in the watch's list and count;
     - Qibla with the compass, which SE 3 and Series 5 and later have (an older watch shows the bearing with no needle).
  5. **Always-on, wrist down:** the app's dimmed view and the complications.
     - **Qibla, wrist down** (plan 4 Task 9, review round 1): face the Qibla until the rim lights and "Facing qibla" shows, lower the wrist, and turn about 90°. Expected: the dimmed page is the resting dial (no needle, no Kaaba marker, no lit rim, the bearing over the distance), never the old needle or "Facing qibla", and with VoiceOver on nothing old is said. Raised, the needle comes back within about a second, from the heading of that moment.
  6. **VoiceOver (gate, Ruling 38)** on the watch: every screen and every complication, in order, in her phone app's language, with no screen abbreviation read as letters.
  7. **Her phone app's language** (iOS per-app language) set to Arabic: the watch follows. Her 12- or 24-hour setting: the watch follows.
  8. **"Add to Apple Watch":**
     - **The file, made on C,** if plan 4 left it to a real watch. The repository `MohamedAbulgasem/Taqwa` is public, and plan 4's "Watch face" phase copies the file into every copy of the iPhone app, where its snapshot is the preview every user sees in the add-face sheet. A shared `.watchface` carries a snapshot of the face as her watch drew it, and each slot's data. So, before she makes it:
       - her iPhone app's city is set to Cape Town, the house staging city (Task 16), and set back after;
       - the face is a Modular-style face, amber, with Taqwa's rectangular and circular complications, and every other slot Off. A slot that cannot be Off holds a data-free option, such as the date: no activity, heart rate, weather, calendar, contact or place;
       - she shares it from the iPhone's Watch app and AirDrops it to the Mac.
     - **Inspect it before the commit.** Unzip a copy into a new, empty directory in the scratchpad (`ditto -x -k`). List every entry, look at each snapshot image, and print each plist and JSON file (`plutil -p`, or as text). Expected: the snapshot shows Cape Town's prayer and a time; the other slots are off or data-free; no entry names her, her devices, her account, or a place other than Cape Town.
       - The ledger records the entry list and what each snapshot shows. Mohamed approves the snapshot in chat, as the preview every user will see.
       - If anything personal is there, she makes the file again.
     - Then commit it at `iosApp/iosApp/WatchFace/Taqwa.watchface` (plan 4's path), for C+1.
     - On C+1: from her iPhone's Settings › Watch, the row adds the shared face with Taqwa in its slots.
     - If her watch cannot make the file, or the add fails on C+1, the row ships hidden (plan 4's offer is not available without the file), and the Lead reports it to Mohamed (spec 4.5).
  9. **Away from her iPhone** for an hour: the watch keeps going. Back: it catches up.
  10. **The payload thresholds on the real pair (gate, plan 2's size gate).** On the Debug run, open plan 4's probe for each channel. Use `xcrun devicectl device process launch --device <her iPhone> --payload-url "taqwa://watch/probe?via=context&from=60000&to=300000&step=4096&fill=random" world.taqwa.ios`, then the same with `via=userInfo`, or tap each link in Notes on her iPhone.
      - **Random bytes, never zeros** (plan 4 Task 5B's finding): WatchConnectivity compresses a payload before it checks its size, so 300,000 zeros check as 1,200 bytes and measure nothing. `fill=random` is the harness's default since Task 5B's review round 1, and the URLs above name it anyway.
      - Record the `TaqwaWatch probe <via> random <largest accepted> <first refused>` lines.
      - Only a `random` line with nothing after its two figures counts. A line ending `incomplete` is a search that stopped early, and a line ending `unmeasured` is a zero run: neither is a threshold, and the channel is probed again.
      - Passes when both largest-accepted sizes are at least `FEED_BUDGET_BYTES + ENVELOPE_BYTES` (60,000 + 1,024).
      - Otherwise plan 2's rule applies: the Lead lowers the budget and `WatchLinkContract`'s figures take the measured values, as a fix task.
  11. **A city change reaches her complications without opening the watch app (plan 4's Lead's call 4).** With the watch app closed, change the city on her iPhone. Expected: within an hour, her complications show the new city's next prayer.
      - If they do not, the finding goes to Mohamed as a change to plan 2's sender: `transferCurrentComplicationUserInfo` while `isComplicationEnabled`, as plan 4's call 4 says.
  12. **Qibla heading with no authorization prompt (gate, spec 4.4).** Open Qibla on her watch. Expected: no location prompt on the watch or the iPhone, the first time or later, and the needle moves.
      - If heading needs location authorization on her watchOS, the gate fails. The watch app declares no location, so this goes back to Mohamed before anything ships (spec 4.4's to-verify).
  13. **The needle against a real compass (gate), with the crown on each side.** In one spot, the watch's Qibla needle against a hand compass's bearing for the feed's Qibla, or against her iPhone's Qibla card side by side. Expected: within 5°, the watch's aligned threshold.
      - Then the same with the crown on the other side (the Watch app on her iPhone: General › Watch Orientation › Digital Crown; watchOS turns the screen half round), and back as she wears it. Expected: within 5° again, both ways.
      - Core Location's heading is the watch's own top, not the screen's (`CLHeading.h`), so the watch turns it by 180° for a crown on the left (`HeadingSample.onScreen`, plan 4 Task 9's review round 1). A needle about 180° out with the crown on the left, and right with it on the right, means watchOS turns the heading itself: that half turn then goes, as a fix task. A needle out with the crown on the right too is another fault (the declination or the compass), which the first half of this check finds.
- [ ] **Step 2: Mohamed's TestFlight steps** (Task 17, M6): the external group "Family", her email, the build, and the "What to Test" text: "The Apple Watch app: complications, Today, Tasbeeh with the crown, Qibla, and Add to Apple Watch in the iPhone app's Settings."
- [ ] **Step 3: Run checks 1–13 on C,** and check 10 on the Debug run. Then, on C+1 and every later candidate: check 8 while the file is in it, checks 1 and 2 (the update installed, the complications still there), and the checks Task 13 Step 6 asks for its changes.
- [ ] **Step 4: Record each result** as she and Mohamed report it. Failures become findings, as Task 13 Step 6.
- [ ] **Step 5: Commit** with the message "Docs: the watch release on her Apple Watch, check by check".

### Task 16: Store screenshots for the watches

**Files:**
- Create:
  - `docs/store/screenshots/play-wear/<lang>/<lang>-0{1..5}.png`;
  - `docs/store/screenshots/appstore-watch/<lang>/<lang>-0{1..4}.png`;
  - `tools/store/capture-wear.sh`;
  - the README's and the site's watch images (WebP for the site, PNG for the README, at the sizes those already use).
- Modify: `README.md` and `site/pages/*/home.html` (the images). `capture-wear.sh`'s own header says how to run it.

**The sets:**
- **Galaxy, five,** each the app's own interface only, as Google's Wear app quality item WO-G5 asks, which also recommends a Tile:
  1. the face, Arch, Amber, with the wearer's slots empty;
  2. the Tile;
  3. Today;
  4. Tasbeeh;
  5. Qibla.
- **Apple Watch, four:**
  1. Today;
  2. Tasbeeh;
  3. Qibla;
  4. Taqwa's complications on a watch face.

Galaxy:
- from his watch, over adb (`exec-out screencap -p`), at its own 438 × 438;
- square and frameless, as Play's Wear OS screenshot rules ask (check their wording in Play Console Help on the day: 1:1, at least 384 px);
- his phone app's city set to Cape Town for the shoot, as the phone shots are staged, and set back after, so that no shot shows where he is;
- in seven languages, Mohamed switching the phone app's language between sets.

Apple Watch:
- from plan 4's simulator harness, on the simulator of Apple's largest listed Apple Watch size, staged on the same Cape Town afternoon as the phone shots, seven languages.

- [ ] **Step 1: Write `capture-wear.sh <serial> <lang>`.** It shoots the five in order after Mohamed opens each, and checks each PNG is square and at least 384 px.
- [ ] **Step 2: Shoot both sets.** Expected: 35 Galaxy shots and 28 Apple Watch shots, none showing a debug line, a notification, a city other than Cape Town or a wrong language. In each language's Tasbeeh shot the bezel hint is on one line (plan 3's hand-off).
- [ ] **Step 3: Make the README and site images** from the English Galaxy face and the English Apple Watch complications. Run `python3 site/build.py --check`. Expected: PASS.
- [ ] **Step 4: Commit** with the message "Docs: the watch screenshots for both stores, the README and the site".

### Task 17: The stores: what Mohamed does, and when

**Files:** Modify `docs/STORE-CHECKLIST.md` (the watch release's own run, in console order), and the ledger.

The ship gate is all of these:
- Tasks 1–16 closed;
- Ruling 39's lines and question 7's as he approved them, in every language (`privacy-check.py` green);
- the native review applied, and his English and Arabic reading done;
- both store forms approved by him;
- the 12-hour observation passed (Task 12 Step 2, a logged request before the timeline's end);
- the Lead's call 6 ruled, and its follow-up done;
- both device passes passed, the screen readers included: his Galaxy and her Apple Watch on C, and on each later candidate the checks Task 13 Step 6 asks of it, the production candidate's included; every gate in Task 15;
- the phones' smoke test passed on the S23 and the iPhone 12 (Task 13 Step 2), and its sections that Task 13 Step 6 names on each later candidate;
- the production candidate delivered by Play over the candidate before it, on his watch (Step 3 below);
- Ruling 42 holding on the Galaxy, or Task 14 done;
- Exodus's local check clean;
- the site's privacy pages live since before any store track took a candidate (M10a, `PRIVACY.md:87`'s own promise).
- 1.5.1 (42) released on the App Store and at 100 % on Play before 1.6.0 is submitted to either. The 1.6.0 notes leave out 1.5.1's Quran change on that footing; if either store has not got it to everyone, its 1.6.0 notes (the release-notes files and the listings' What's new) take the Quran bullet of `docs/store/release-notes/1.5.1-42-appstore.txt` or `1.5.1-42.txt` first.

**Before any store track takes a candidate** (M6, M11 and M11a): the cut gate held for it, and M10a has taken the public privacy pages live. Plan 2's gate binds every store build that carries a sender, testers' builds included, not production alone.

| When | Mohamed does | Exactly |
|---|---|---|
| M1, before Task 7's real run (any time now) | Makes the face key | `scripts/make-face-key.sh`. Then he backs up `~/keys/taqwa-face.jks` and its password in two places, and pastes the SHA-256 it prints into chat. The Lead writes it to `watchface/face-release-cert.sha256` and commits it. |
| M2, now | Answers the open questions | Above, 1–12, in chat. |
| M3, before Task 9 closes, and before any candidate is cut | Approves the privacy wording | Plan 2's report, question 7's lines, in chat. **Given 31 October 2026**, with two tweaks (Task 9). |
| M4, at Task 13's start | Pairs his Galaxy Watch | Task 13 Step 3. He keeps it on the Mac's Wi-Fi during the pass. During it he runs the phones' checklist (Task 13 Step 2) on the S23 and, by Xcode Run, the iPhone 12, with section 2's two idle runs in their places in checks 1 and 9 (Task 13 Step 4). |
| M5, each candidate, past the cut gate | Signs the face | `scripts/release-face.sh`, typing the face key's password. Then the Lead alone runs the real `scripts/release.sh`, with `keystore.properties` symlinked from the main checkout for that build and removed after. |
| M6a, before Task 15's check 10 | Registers her devices | Her iPhone and Apple Watch under team 3K93P4PA5H (Xcode › Window › Devices and Simulators, or the developer site), for the one Debug run (question 1). |
| M6, after M10a, and after the Lead's `ios-release.sh` upload of each candidate (his OK first, each time) | TestFlight for her | App Store Connect › Taqwa › TestFlight › External Testing › "+" group "Family" › add her Apple ID's email › add the build › the "What to Test" text (Task 15), and the review notes from Task 9's new notes file › Submit for Beta App Review (about a day). |
| M7, Task 13 | Judges the face and plan 3's defaults, and wears the two battery days | The checklist's "Mohamed by eye" lines, question 12's six, then question 6. |
| M8, Task 11, before any candidate is cut | Reads the English and the Arabic | The list the Lead posts. |
| M9, before upload | Approves the store forms | Task 9 Step 3's answers, in chat. **Given 31 October 2026**, with M3. |
| M10, Play, once | Adds Wear OS | Play Console › Taqwa › Test and release › Advanced settings › Form factors › Add form factor › Wear OS. He opts in and accepts Wear OS's terms. Then Main store listing: the Wear OS screenshots per language (`docs/store/screenshots/play-wear/`), and the full description with its watch paragraph (Task 10), which names the Tile and complications. |
| M10a, once C is cut, before M11 and M6 | OKs the privacy push | The Lead cherry-picks Task 9's public-pages commit onto `main` under his Taqwa identity, with no `Co-Authored-By`, and pushes `main` alone, with his OK in chat. Since the final 1.5.1 (42) was merged into the watch release (aa3709ed), `PRIVACY*.md` are taken as they stand on the watch branch, which hold 1.5.1's Report a problem sentence, not from the older privacy commits (516253d5 and those before it, dated 30 October), and every one's "Last updated" is set to the day of the push, in all seven (Bengali digits in `bn`, Latin in `ur`), so the page's date moves past the 7 November of the policy live before it. The site goes live with it. The Lead checks that each of the seven privacy pages on taqwa.world holds the relay sentence, records it in the ledger, and ff-pulls the main checkout; the branch and its worktree stay, since the branch is not merged. He pastes Play's listing lines 49–50 (Task 9), which are true of both builds, into the main store listing that closed testers see. A later change to a public privacy line repeats this row before its candidate goes to a track. |
| M11, Play, C, after M10a | Uploads both bundles to testing | The phone AAB to the phone's closed track, and the Wear AAB (`wearApp-release.aab`, version 100000 + C) to the Wear OS closed track. Choose Wear OS in the Releases page's form-factor selector (question 8). The release notes (the Lead pastes both files' text in chat with the AABs). Data safety: as approved (M9). Then send for review. |
| M11a, Play, each later candidate | The update over the one before, then production | He adds his own account to the internal testers, once. Each later candidate's pair goes to the internal tracks, phone and Wear OS, for Step 3's update check, then to the closed tracks. Once the ship gate is met, he promotes the last passing candidate's releases to production on both form factors. |
| M12, App Store, the production candidate | Submits the version | App Store Connect › the version, with the production candidate's build: the Apple Watch screenshots per localization (`docs/store/screenshots/appstore-watch/`), the Description and "What's New" from the App Store section of `docs/store/listing/<lang>.md` (their own text, naming only Apple Watch, guideline 2.3.10), App Privacy as approved (M9), and the Notes field from the new notes file (Task 9). Submit. |
| M13, before both releases go live | OKs the push | The squash to main under his Taqwa identity, the push, which takes the site live, and his after-push cleanup (worktree, branch, ff-pull). In the squash, the README's Roadmap slice 5 turns from "Coming in 1.4.0" to "Done" (Ruling 67). |
| M14, after Play serves the build | Exodus | He opens Exodus's public report for the new build, or the Lead submits it with his OK. Its line in the README and the site stays only if it finds no tracker. |

- [ ] **Step 1: Exodus, locally.** Take Exodus's public tracker signatures (their API's tracker list, a data download) and check them against the class names of both release APKs (`apkanalyzer dex packages`). Expected: no match. Record it in `STORE-PRIVACY.md`.
- [ ] **Step 2: Write the watch release's run into `docs/STORE-CHECKLIST.md`,** as the table above, in console order: the cut gate, C, M10a, then each later candidate.
- [ ] **Step 3: The Play-delivered Wear build on his watch,** during question 8's closed test, after M10a and M11. Every Watch Face Push proof before this used AGP APKs installed with adb. Play serves bundletool split APKs, re-signed with the app-signing key. Write the results into Task 13's Galaxy checklist, under "From Play".
  1. First compare Play's app-signing certificate (Play Console › Test and release › App integrity) with the upload key's. If they differ, the S23 also takes the closed-track phone build from Play (the Risks' "One key on both ends"), so that the link can join.
  2. Mohamed uninstalls the sideloaded candidate from his watch (Settings › Apps › Taqwa › Uninstall, his hands), then installs Taqwa from Play on the watch, from the Wear OS closed track (C).
  3. Expected:
     - the default face appears in the face list, not active (`TaqwaFace` lines over adb);
     - Today's chip makes it active, or, if the system still counts the sideloaded candidate's one activation, shows the guidance line, and choosing it by hand works. Record which;
     - the link works with the phone build he has from Play: the city on the Tile, and a city change reaches the watch.
  4. He customises the face: Moon, Sage, the battery in slot 4.
  5. The next candidate goes to the internal tracks (M11a). Play updates the watch app.
  6. Expected:
     - `dumpsys package world.taqwa.app.watchfacepush.taqwa` shows that candidate's code;
     - one `TaqwaFace update` line;
     - the face still active and still Moon, Sage, battery;
     - `logcat -b crash` empty.

  Steps 5 and 6 run for every later candidate, so the production candidate is always one that Play delivered over the candidate before it. A failure is a finding (Task 13 Step 6), and production waits.
- [ ] **Step 4: Hand over.** Put each candidate's AABs on the Desktop when it is cut, as for 37 and 38. Paste its two release-notes files, all seven languages in Play's tag template, in the chat itself, with each candidate.
- [ ] **Step 5: After each store goes live:** a ledger line. Then the site's store badges, already in `site/stores.json`, need no change.
- [ ] **Step 6: Commit** the checklist and the ledger lines, with the message "Docs: the watch release's store run, in Mohamed's order".

---

## Risks

- **Watch Face Push on the emulator.** If the Wear OS 6 image installs no default face or has no Push service, Task 4 Step 5 and Task 5 Step 7 move to his watch. That costs device time, and the update check (Review Focus 1) would first be seen on hardware.
- **The library's own floor.** `watchfacepush` 1.0.0's `minSdk` is 33, above the Wear app's 30, and it is supported only from API 36. Task 5 Step 1 takes `tools:overrideLibrary` and a runtime guard, run on Wear OS 3 and 4 (Task 12). Below API 33 the complications' countdown also stands still for each prayer period, which the Lead's call 6 rules on.
- **Samsung's differences.** One UI Watch may ignore `METADATA_KEY_HIDDEN` (Ruling 31), read the face differently (Rulings 38 and 42), or draw a construct the emulator drew. Each finding is a new fix task, and the budget has to leave room for some.
- **The once-only activation and reinstalls.** Google allows one activation per app, and a reinstall is not a reliable reset. On the emulator it is spent in Task 5, and on his watch in Task 13 check 1. Later checks that tap the row accept the guidance line and record which they saw (the Global Constraints).
- **What only Play's delivery shows.** Split APKs, the app-signing key, and Play's installer and update meet the bundled face first on Task 17 Step 3. That step runs before production, and a failure there holds production.
- **One key on both ends.** If his phone holds a build signed with another key than the watch app, the link stays silent. Task 13 checks the certificates first. A pass from Play's tracks needs both apps from Play, which may mean reinstalling the phone app (bookmarks and counts are lost; backup is off). That is his decision on the day.
- **The face key cannot be reset.** Unlike the upload key, a lost face key means every watch's face is removed and added again, losing its wearer's style, colour and slots. `make-face-key.sh` says so, and M1 backs it up twice.
- **R8 on the Wear app is new.** A class stripped from a store build shows as a blank face or a crash only there. The staging build, the dry-run candidate and the release candidate on his watch are the three nets (Review Focus 5).
- **Reviews take time.** Play reviews Wear OS separately, and the first Wear release can take days, or come back on Wear quality items. TestFlight's Beta App Review takes about a day, and each later candidate needs another. Both depend on his and her availability. The spec's mid-November target holds only if M3 and his Arabic reading come early (the cut gate waits on both), Tasks 13 and 15 start soon after candidate C, and C+1 is cut as soon as her `.watchface` file and C's fixes exist. Each extra candidate costs a signing, a TestFlight review and a Play update check.
- **The order of the privacy push.** The policy promises to say so before a version that uses the network anew ships, and testers' builds carry the sender from C. So every in-app line is approved and applied before C is cut (the cut gate), and the public privacy pages go live (M10a) before any store track takes it. M13's full push still comes before the production release buttons.
- **Her `.watchface` file is public.** It ships in every copy of the iPhone app and sits in a public repository. Task 15 check 8 stages it on Cape Town with the other slots off, and inspects it before the commit.
