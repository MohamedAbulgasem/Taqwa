# Ruling 72 items 5 and 7 (iOS), and Ruling 74 items 16-22: the release path's carried minors

Lane A, on `watch-apple`, 6 November 2026. This is no plan task. Ruling 72 (about 20:55) carried
P5.7's release minors into Wave 8 as items 5 and 7 (iOS). Their two reviews were clean, and each
left minors, which Ruling 74 (23:20) carried into Wave 9 as items 16-22 (release, Lane A3). This
report covers both waves. Wave 8 wrote no report of its own (its result went to the Lead in the
workflow's structured result), so Ruling 74 item 22 asks for this one. Nothing is pushed; Taqwa
identity; no trailer. The version is not bumped: the watch ships as 1.6.0 (42), and the cut is the
Lead's.

| Wave | Commit | Items | Subject |
|---|---|---|---|
| 8 | `a1d4cb06` | R72 5 | Release: every build of the dry run, the face gate's too, reads no keystore.properties, a dry run refused before its build keeps the last one's outputs, and the bare ios-release.sh checks the export before it uploads (Ruling 72) |
| 8 | `336735c8` | R72 7 (iOS) | iOS: the watch probe's usage names the launch URL that works on iOS 27, beside openurl, which waits behind "Open in Taqwa?" (Ruling 72) |
| 9 | `66d2169a` | R74 19 | iOS: the recitation harness's usage names the launch URL that works on iOS 27, beside openurl, as the watch probe's does (Ruling 74) |
| 9 | the commit that carries this report | R74 16-18, 20-22 | Release: release-face.sh builds the face with -Ptaqwa.unsignedRelease=true, which leaves the face as it is, the dry run's 'as it found it' checks are seeded, and the iOS steps check the export first (Ruling 74) |

Wave 8 landed on `watch-companion` as `3b0a7a3d` ('Merge int-a: R72A', 22:42). Wave 9's base is
`ce3f9acd` (`git merge --ff-only watch-companion` from `3b0a7a3d`; no WIP from an earlier run).
Its WIP `6d4c4156` (green) is folded into the two commits above.

Keys and devices, in both waves:
- No `keystore.properties` was read, linked or made, except `test-release.sh`'s decoy
  (`storeFile=/nonexistent/decoy.jks`), which its trap removes.
- Faces were signed with the throwaway test key `~/.android/taqwa-face-debug.keystore` and a key
  made in the test's scratch directory. The dry run's outputs were signed with
  `~/.android/debug.keystore`. Mohamed's face key and its pin do not exist yet (M1).
- No real `scripts/release.sh` ran, and no archive, export-check or upload. Xcode has no Apple
  account signed in (ledger, 20:12), and those steps are the Lead's, with Mohamed's OK.
- No device, emulator or `adb` command was used. `emulator-5558` and `emulator-5562` stayed with the
  12-hour observation. iOS work ran only under the `ios-sim-tests` lock (both names). `KB2604110095`,
  Dev 1's 5572, the booted iPhone 17 Pro Max simulator and the work iPhone 12 were not touched.
- `test-release.sh`'s last dry run replaces `build/release-dry-run/`. After each of this wave's
  runs the worktree's four 18:40 outputs (P5.7 Step 4's, `24c346ab…`, `740e58f6…`, `56cb9900…`,
  `081b179c…`) were put back, as Wave 8 did.

## Ruling 72 item 5: P5.7's three carried minors (Wave 8, `a1d4cb06`)

1. **The dry run reads no `keystore.properties`.**
   - The defect: P5.7's dry run passed `-Ptaqwa.unsignedRelease=true` to step 4's four builds
     only. Step 3's `:watchface:assembleRelease` and `tools/watchface/validate.sh` (the face
     gate's) built without it. A build of the root project configures `:androidApp` and
     `:wearApp` whatever its tasks, so those builds read a `keystore.properties` beside them.
   - The change: one `UNSIGNED` array carries the flag to step 3's build and step 4's, and is
     empty in the store run. `validate.sh` always passes it, since it builds `:watchface` alone.
     The store run's own builds never pass it, so the upload key's signing is unchanged.
   - The test: the scratch tree's fake `gradlew` notes a read wherever Gradle would make one,
     beside a decoy, and the store run is seen reading it, so the dry run's case can fail.
2. **A dry run refused before step 4 keeps the last dry run.** Refused at the versions, the face
   gate or a stale face, it leaves `build/release-dry-run/` as it was. Only a run that reached
   step 4's build removes or replaces it.
3. **The bare `scripts/ios-release.sh` checks the export before it uploads.** With no argument it
   runs archive, then export-check, then upload, and `set -e` stops it at the first failure. The
   fake `xcodebuild` records each archive and export by its destination.

Red, then green: `test-release.sh` gave 54 ok and 9 FAIL on the base scripts (`6f8e2cb7`):
- the three `all` cases took `archive,export upload`;
- the three scratch dry-run refusals emptied the directory;
- step 3's face build and `validate.sh` each read the decoy;
- the repository's pinned-key refusal emptied the directory.

After the change: 63 ok, 0 FAIL, in 8 min 42 s.

## Ruling 72 item 7 (iOS): the watch probe's launch URL (Wave 8, `336735c8`)

`WatchHarness.swift`'s usage now leads with the route a script can use on iOS 27:
`SIMCTL_CHILD_TAQWA_HARNESS_URLS='taqwa://watch/probe?…' xcrun simctl launch
--terminate-running-process <phone> world.taqwa.ios`. The `openurl` lines stay beside it. The
change is a `///` comment inside `#if DEBUG`.

Wave 8's other checks:
- `scripts/ios-build.sh`: BUILD SUCCEEDED in 88 s. The bundle rules hold (4412 of 5120 KB, and
  12032 of 15360 KB), and `test-watch-bundle-signing.sh` gives 19 ok.
- `scripts/test.sh`, whole, under `ios-sim-tests`: exit 0 in 395 s, with 0 failures.
- Both review lenses were CLEAN, with 0 critical, 0 important and 6 minors each. Their minors are
  this wave's items:
  - "every build it makes" overclaims (step 1's `tools/timetables` `checkStamps`), and "the store
    build never" is untrue of the store run's face gate (item 16);
  - `release-face.sh:78` builds the face with no flag (item 17);
  - the repository's "as it found it" checks pass vacuously on an empty `build/release-dry-run/`
    (0 files in the reviewer's clone) (item 18);
  - `RecitationHarness` still says "the only channel" (item 19);
  - STORE-CHECKLIST §6 says archive, then upload, and the export-check fallback is unwritten
    (item 20);
  - `ios-release.sh:22-23` says "Both refuse" (item 21);
  - the P5.7 report's :38 and :42 are stale (item 22).

## Ruling 74 item 17: the face's own build passes the flag

**The question first (erratum 3).** Does `-Ptaqwa.unsignedRelease=true` change
`:watchface:assembleRelease`'s output path, or the APK `release-face.sh` then signs? I read
`watchface/build.gradle.kts`, `androidApp/build.gradle.kts:20-36`,
`wearApp/build.gradle.kts:13-27` and `release-face.sh` whole. Only `:androidApp` and `:wearApp`
read the property. `:watchface` never reads it, and its release has no `signingConfig`. I measured
it in this worktree, with no `keystore.properties` in the root:
- the task graph (`--dry-run`) is the same 50 `:watchface` tasks with the flag and without it;
- built without it, then with it after the APK was removed, it is the same
  `watchface/build/outputs/apk/release/watchface-release-unsigned.apk`, byte for byte (SHA-256
  `902f2153…`), with the same `output-metadata.json`;
- `--rerun-tasks` with the flag gives the same bytes again;
- both copies, signed with the test key as `release-face.sh` signs, give the same signed APK
  (`3c3c254e…`). The test key is RSA, so the same bytes sign the same.

The two SHA-256s hold at that revision alone: AGP writes the git revision into the face APK
(`META-INF/version-control-info.textproto`). The proof compares within one run, and `release.sh`'s
step 3 skips `META-INF`, so neither depends on them.

So nothing changes, and the ruling's first branch applies:
- `release-face.sh:82` (was :78) passes the flag. Its header says why: the face never reads it,
  and it keeps `:androidApp` and `:wearApp` from reading a `keystore.properties` beside the build.
  Mohamed runs the script at STORE-CHECKLIST's step 3, before the Lead's link, and it would read
  none even with one there.
- `.github/workflows/tests.yml:67` (was :65) passes it, and its comment says so.
- The plan's Global Constraint (plan 5 :95) and the `androidApp` and `wearApp` KDocs ("every
  release-type build but scripts/release.sh's passes it") are now true of every real build. They
  are left alone. The named exception is the tests that prove the flag, which build a release
  without it on purpose: `scripts/test-unsigned-release.sh` and `scripts/test-release.sh`'s
  `theFlagLeavesTheFaceAsItIs`, each beside no `keystore.properties` but its own decoy, and
  `test-release.sh` refuses to start beside a real one.
- `tools/watchface/README.md`'s `release-face.sh` row, and STORE-CHECKLIST §4 step 3 and §8, name
  the flag.

**The tests** (`scripts/test-release.sh`):
- `theFaceBuildReadsNoKeystoreProperties` runs in the scratch tree, beside its decoy:
  - it runs `release-face.sh` with the test key, its pin and `TAQWA_FACE_KS_PASS_ENV`;
  - the fake `gradlew` records the one build, and copies this repository's unsigned face in as
    its output;
  - it passes when the script signs, its one build is `:watchface:assembleRelease` with
    `-Ptaqwa.unsignedRelease=true`, and the fake noted no read.
- `theFlagLeavesTheFaceAsItIs` runs on this repository, in two halves:
  - before the decoy is written, the face is built without the flag, then with it after the APK
    is removed: the same bytes at the same path;
  - then `release-face.sh` builds it again (its APK was removed) and signs it. Its unsigned APK is
    the build without the flag, and its signed face is that build signed by the test key.
  - A face that read the flag would fail it. A mutant `watchface/build.gradle.kts`, whose
    `versionName` follows the flag, gave two different APKs; it was then checked out again.

**Red, then green.**
- Red, against the base `release-face.sh`, is one FAIL: `theFaceBuildReadsNoKeystoreProperties:
  release-face.sh exits 0, built '-q :watchface:assembleRelease', read '/nonexistent/decoy.jks:
  -q :watchface:assembleRelease'`.
- `theFlagLeavesTheFaceAsItIs` passed on both halves. It is the proof, so it holds before the
  change and after it.
- Green: all three ok in the whole run (below).

## Ruling 74 item 18: the "as it found it" checks are seeded

**The defect.** `aFaceSignedByAnUnpinnedKeyIsRefused` and `aStaleSignedFaceIsRefused` compare
`build/release-dry-run/` before and after a run refused at step 3. On a tree with no last dry run,
both sides are empty, and a `release.sh` that emptied the directory would pass.

**The change.**
- Before each of the two, `seed_dry` sets aside what the directory held (the first time only) and
  writes one file, `test-release-marker.txt`.
- Each check also requires that the seeded list is not empty.
- After the two, `put_back_dry` removes the seeded directory and moves back what was there, or
  nothing. The test's EXIT trap calls it too, so a run stopped between the two still puts it
  back.

**Red, then green.**
- Red: a scratch copy of the new test, stopped after the two checks, with its `dry` pointed at
  `6f8e2cb7`'s `release.sh`. That is the base before Ruling 72's keep, whose cleanup removed the
  directory on any failed dry run. Both checks are red, each for its reason: `exit 1,
  build/release-dry-run/ holds '', seeded with 'build/release-dry-run/test-release-marker.txt'`.
- In the same run, every scratch-tree case but item 17's was ok, and the test put back the
  worktree's four 18:40 outputs (the same SHA-256s).
- Green, against today's `release.sh`: both ok, "is as it found it (seeded, 1 file)".

## Ruling 74 item 16: what the release script says is true

- `release.sh:43-46`: the dry run passes the flag "to every build of the root project it makes,
  step 3's face and step 4's four". It adds that step 1's `tools/timetables` `checkStamps` is a
  build of its own, which reads none.
- `release.sh:52-54`: a dry run refused before step 4 "built none of the four outputs, only the
  face (steps 2 and 3)". It no longer says "built nothing".
- `release.sh:80-84`: the flag goes to the dry run's own builds of the root project, and the store
  run's own builds never pass it, so the upload key signs the four store outputs. Step 2's face
  gate passes it in both runs (`validate.sh`), as `release-face.sh` does. This replaces "the store
  build never", which the store run's face gate contradicted.
- `release.sh:133-135` (the cleanup): a run refused before step 4 "built none of them (steps 2 and
  3 build only the face)".
- STORE-CHECKLIST §4 step 4 (:319-320): "Every build of the root project it makes passes … so none
  reads a `keystore.properties`". §8 (:500-504): "every build of the root project it makes", with
  `validate.sh` and `release-face.sh` named beside it.

## Ruling 74 item 19: the recitation harness's launch URL (iOS)

`RecitationHarness`'s doc (`iOSApp.swift:466-472`) said the URL scheme "is the only channel
`simctl` has into a running app", and it showed `openurl` alone. It now names both ways, as
`WatchHarness.swift` does:
- On iOS 27 only the launch URLs serve a script. `simctl openurl` waits behind "Open in Taqwa?",
  which no script answers, and a launch hands each `TAQWA_HARNESS_URLS` entry to `runLaunchURLs`
  as `onOpenURL` does.
- The example leads with `SIMCTL_CHILD_TAQWA_HARNESS_URLS='taqwa://recite/load?…
  taqwa://recite/next' xcrun simctl launch --terminate-running-process <udid> world.taqwa.ios`.
  `--terminate-running-process` is needed because a process already running has read its launch
  URLs, or had none.
- The `openurl` lines follow, "where nothing asks first".

The change is a `///` comment inside `#if DEBUG`. `swiftc -parse` passes, and `scripts/ios-build.sh`
under `ios-sim-tests` reports BUILD SUCCEEDED, with no warning from a Swift file.

## Ruling 76 items 13 and 14: the release wording (7 November)

Lane `watch-phone-fix`, from the Lead's ruling of 03:20 SAST. No release key, no archive, no upload.

- **Item 13.** STORE-CHECKLIST §6 step 2 no longer says Xcode's cloud-managed distribution may not sign a local export. The real `export-check` of 7 November (1.5.0 (41), ledger 01:05) signed. The fallback stays.
- **Item 14.** This report's merge time and its "true as written" (above), `release-face.sh`'s header (STORE-CHECKLIST's step 3, not `release.sh`'s own), `release.sh`'s header ("at most the face"), `tests.yml`'s comment width, and a correction beside the release-path report's "has built nothing".
- **Item 14's marker, red then green.** The seeded checks' marker is now named `androidApp-release-DRY-RUN.apk`, as an output is. A scratch copy, with a mutant `release.sh` whose refused run removes only `*-DRY-RUN.*`, stopped after the seeded checks:
  - on the base's `test-release-marker.txt`, `aFaceSignedByAnUnpinnedKeyIsRefused` and `aStaleSignedFaceIsRefused` both pass, so the mutant survives;
  - on the new marker, both fail: `build/release-dry-run/ holds '', seeded with 'build/release-dry-run/androidApp-release-DRY-RUN.apk'`.

## Ruling 74 item 20: STORE-CHECKLIST's iOS steps check the export

- §6 step 2 (:381-396) is now archive, the app record, `export-check`, then `upload`, each with
  Mohamed's OK.
- The fallback is written there. If `export-check` cannot sign on the Mac (Xcode's cloud-managed
  distribution may not sign a local export), upload alone, `scripts/ios-release.sh upload` with
  Mohamed's OK. Then read each bundle's entitlements in App Store Connect › TestFlight › the
  build › Build Metadata, and the Lead records them against `check-watch-bundle.sh`'s
  distribution keys. (Ruling 76 item 13 took the parenthetical out of the checklist: the real
  `export-check` of 7 November signed. The fallback stays.)
- The cancel-release step (§6 step 9.2, :463-465) is now archive, `export-check`, then upload,
  "its fallback included".
- §4 step 5 (:332-333) points to the fallback.

## Ruling 74 item 21: `ios-release.sh`'s header

Lines 22-25 now say that archive and export-check refuse a set `TAQWA_CODESIGN` or
`TAQWA_WATCH_BUNDLE_CHECK`, "and so does the bare script (all), before anything is built". The
behaviour was already held by `test-release.sh`'s six refusal cases (three commands × two
variables), so the wording catches up with the code.

## Ruling 74 item 22: the P5.7 report

[`2026-11-06-release-path.md`](2026-11-06-release-path.md) :38 and :42 each get "Superseded in
part by Ruling 72":
- :38's note says the dry run now passes the flag to every build of the root project it makes,
  step 3's face included, and `validate.sh` to the face gate's builds;
- :42's note says only a run that reached step 4's build deletes `build/release-dry-run/`.

The rest of that report stays as the record of P5.7.

## Checks run in Wave 9

All on `watch-apple`. The iOS ones ran under `ios-sim-tests` (both names), held 23:52:21-00:00:52
after a wait behind Lane P3 and the M8f reviewer.
- `scripts/test-release.sh`, whole: PASS, 66 ok and 0 FAIL in 605 s (Wave 8's 63, plus
  `theFlagLeavesTheFaceAsItIs` twice and `theFaceBuildReadsNoKeystoreProperties`). Its real dry
  run's four outputs, all signed by the debug key, were phone AAB `ae16fba6…` (32,964,552 bytes),
  phone APK `c8d62e09…`, Wear AAB `6b986568…` and Wear APK `75b53ae6…` (5,010,024 bytes). Then
  the 18:40 set was put back.
- The red run (above): a scratch copy of the new test, stopped after the seeded checks, with `dry`
  on `6f8e2cb7`'s `release.sh` and the base `release-face.sh`. It took 194 s: 44 ok and 3 FAIL,
  each for its reason, and the tree as found.
- `tools/watchface/validate.sh`: both validators pass (4 s, its builds up to date).
- `scripts/check-versions.sh`: 1.5.0 (41) on the phone, the face and iOS, and 1.5.0 (100041) on the
  Galaxy watch app.
- `scripts/test-face-release.sh` (`release-face.sh`'s own tests, since that script changed): PASS,
  50 ok in 134 s, the pseudo-terminal cases included.
- `scripts/ios-build.sh`: BUILD SUCCEEDED in 75 s. The bundle rules hold (`TaqwaWatchWidgets.appex`
  4412 of 5120 KB, `TaqwaWatch.app` 12032 of 15360 KB), and `test-watch-bundle-signing.sh` PASS
  with 19 ok. No warning comes from a Swift file; the build's other warnings are the Kotlin and
  linker ones it already gave.
- `scripts/test.sh`, whole: exit 0 in 396 s, with 0 failures and no `EOFException`:
  - versions, `test-bump-version.sh`, `test_privacy_check.py`, privacy (263 of 263 rows, 244
    sweep lines, clean) and the listing all pass;
  - Swift: `WatchCore` 286, `WatchViews` 6, `PhoneHarness` 19;
  - Kotlin/Native: shared 2412, watchcore 200, widgetcore 109;
  - host: shared 2436, watchcore 233, widgetcore 111;
  - wearApp: 601.
- `tools/i18n-check.py` is clean. No string resource changed, so no visible word changed.
- `bash -n` passes on the four changed scripts, under bash 3.2 too. `git diff --check` is clean,
  and the CI workflow parses (Ruby's YAML).

## Not done here, by rule

- The real `scripts/release.sh`, the archive, `export-check` and the upload are the Lead's, with
  Mohamed's OK. Xcode has no Apple account signed in, so none could run here anyway.
- The version is not bumped, and nothing is merged or pushed.

## Concerns and notes

1. **`theFlagLeavesTheFaceAsItIs` is a proof, not a regression test of today's scripts.** It holds
   whether or not `release-face.sh` passes the flag. `theFaceBuildReadsNoKeystoreProperties` is
   the case that holds the argument (red on the base script). Together they show that the script
   passes the flag, and that the flag leaves the face as it is.
2. **The signed half relies on RSA signing being deterministic.** That is true of the test key
   (`scripts/face-debug-key.sh`, `-keyalg RSA`). A test key of another algorithm would fail that
   half, with a message naming it, rather than pass it.
3. **The seeded checks move the directory aside with `mv`.** `$WORK` is under `TMPDIR`, on the
   same volume here. The trap puts the directory back after an early exit too.
