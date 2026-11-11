# Plan 5 Task 7: the release path, end to end

Lane A (`watch-apple`), 6 November 2026. Plan 5 Task 7 ("The release path, end to end"), built test-first, as Ruling 71 item 6 sets Wave 7: Steps 1 to 6, except Step 4's archive and export-check, which wait for Mohamed's OK and are the Lead's.

- Base: `f601d14d` (`git merge --ff-only watch-companion` from `2d6e343a`; no WIP from an earlier run). WIP `d37dff95` (the bundle check, green, with the release test still red) and WIP `7b4e736c` (the release path, green) are folded with this report into the plan's one commit. Taqwa identity, no trailer, nothing pushed, version not bumped (1.5.0 (41), (100041) on the watch, until the 1.6.0 (42) cut).
- Keys: no `keystore.properties` was read, linked or made, except `test-release.sh`'s decoy (`storeFile=/nonexistent/decoy.jks`), written and removed by its trap. Faces were signed with the throwaway test key `~/.android/taqwa-face-debug.keystore` and a key made in the test's scratch directory, and the dry run's outputs with `~/.android/debug.keystore`. Mohamed's face key and its pin do not exist yet (M1). No archive, export or upload was made, no distribution identity was used and no bundle id was registered. The real `scripts/release.sh` never ran.
- Devices: none but the build machine. `ios-sim-tests` was held from 18:28:25 for `scripts/ios-build.sh` and `scripts/test.sh` (ledger). No emulator, simulator by hand, phone or watch was touched.

## What was built

**`scripts/release.sh`**, rewritten. It builds and checks the phone's and the Galaxy watch app's bundles together, stopping at the first failure:
1. `check-versions.sh` and the stamps, as before.
2. `scripts/face-gate.sh`.
3. The signed face at `build/face-release/watchface-release-signed.apk`. It must verify, its one certificate must be the pin, and it must be the current face: `:watchface:assembleRelease` is built again and every zip entry outside `META-INF/` must have the same name and CRC-32 in both. Otherwise it says "the signed face is not the current face: run scripts/release-face.sh again" and lists the entries. (`META-INF/version-control-info.textproto` holds the commit, so it is rightly outside the comparison.)
4. The four builds with `-Ptaqwa.storeRelease=true -Ptaqwa.faceApk=<the signed face>`, after removing the four release output folders, so only this run's files are judged.
5. The checks, all run whatever the earlier ones found:
   - `check-bundle.sh` on the Wear APK, with the pin;
   - `tools/wear-release-check.sh release`;
   - the phone's merged permissions are exactly `androidApp/permissions.txt`;
   - the Wear APK's certificate is the phone APK's, and each AAB's is its APK's;
   - badging: the phone N (C), the Wear app N (100000 + C), the bundled face N (C).
   Beyond the plan's list, three more checks, cheap, on the build that ships:
   - the Wear APK and AAB carry the signed face byte for byte;
   - the APK's `default_wf_token` equals `push-token.sh`'s for that face, compared and never printed;
   - no output holds a sample feed or a test vector (Ruling 55 item 6: every APK, the phone's included).
6. The four outputs with their sizes and certificate SHA-256s, and the three badging lines.

**The store build** (no `TAQWA_RELEASE_DRY_RUN`) names everything it lacks at once, before anything is built:
- `keystore.properties`;
- the pin `watchface/face-release-cert.sha256`. The message names M1 and `scripts/make-face-key.sh` (erratum 5);
- a set `TAQWA_FACE_CERT_FILE`, which it refuses: a store build holds the face to the committed pin alone, so a dry run's test pin left in the shell cannot bundle a test-signed face into a store build.

It checks that its outputs are signed. It never reads `keystore.properties`; only Gradle does.

A run that fails from step 4 on removes the four outputs it built, and says "the outputs it built are removed" (review round 1). Step 5 is where a store run catches a face signed with the app's key, a permission a library added, or a token that does not match. Before this, those bundles were left signed at the paths the header says to upload, so a mistaken upload would have burnt the candidate's code. A run refused before step 4 has built nothing, and leaves what was there. *Corrected by Ruling 76 item 14:* it has built none of the four outputs and at most the face (steps 2 and 3), as `release.sh`'s header says. The checklist's uploads step now says to upload only after `release.sh: PASS`.

**The dry run** (`TAQWA_RELEASE_DRY_RUN=1`; any other value is refused with exit 2):
- It skips the `keystore.properties` check, and passes `-Ptaqwa.unsignedRelease=true` to step 4. *Superseded in part by Ruling 72:* it now passes it to every build of the root project it makes, step 3's face build too, and `tools/watchface/validate.sh` passes it to the face gate's builds, so none reads a `keystore.properties` beside it ([`2026-11-06-ruling-72-release-minors.md`](2026-11-06-ruling-72-release-minors.md)).
- It refuses to sign if any APK verifies or any AAB has a `META-INF/*.RSA|EC|DSA` entry: "the release build was signed: taqwa.unsignedRelease is not honoured".
- It signs with the debug key into `build/release-dry-run/{androidApp,wearApp}-release-DRY-RUN.{aab,apk}` (apksigner for the APKs, v4 off; jarsigner for the AABs), and step 5 checks those files.
- Its pin is `TAQWA_FACE_CERT_FILE`, by default the committed pin. The face comes from `release-face.sh` run with Task 2's `TAQWA_FACE_*` and the test key, exactly as Mohamed's run makes his. The release script itself never signs a face, so it can never overwrite one he signed.
- A failed dry run deletes `build/release-dry-run/`, so no unchecked set is left for Task 12 Step 3 to install. *Superseded in part by Ruling 72:* only a run that reached step 4's build deletes it; one refused before (the versions, the face gate, the signed face) leaves the last dry run's outputs as they were ([`2026-11-06-ruling-72-release-minors.md`](2026-11-06-ruling-72-release-minors.md)).

**`androidApp/permissions.txt`**: today's 12, in `wearApp/permissions.txt`'s format (`<name> <origin> # <reason>`). The origins come from the merger report (`media3-exoplayer` for `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, `androidx.core` for `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`). `tools/privacy-check.py`'s sweep caught its `INTERNET` and `ACCESS_NETWORK_STATE` lines, as it catches the manifest's. The tool's rule is that such a line joins a row or `KNOWN` in the same commit, so one exact `KNOWN` entry, "build tools and configuration", joins them. The gate is clean again (263 rows, 242 sweep lines).

**`scripts/check-watch-bundle.sh`**:
- `[--release] <Taqwa.app>`. With `--release`, rule 8 always runs, and an unsigned or ad hoc watch bundle breaks rule 4b with "a release bundle must be signed".
- `TAQWA_CODESIGN` (default `codesign`) runs rule 4b's two reads.
- Rule 4b's keys go by the first `Authority`:
  - Development allows the app group, `application-identifier` (= `<TEAM_ID>.<id>`), `com.apple.developer.team-identifier` (= `TEAM_ID`), `get-task-allow`, and `keychain-access-groups` only as `[<TEAM_ID>.<id>]`;
  - Apple or iPhone Distribution allows the same, plus `beta-reports-active`, with `get-task-allow` absent or false;
  - any other key or `Authority` is broken, named.
- On every real signature it prints the `Authority` and the keys it read, so the Lead can record them from the archive and export runs.
- Plan 4's `com.apple.application-identifier` is no longer allowed. Plan 5's list does not name it, and a watchOS signature carries `application-identifier`. If Xcode adds it anyway, the archive check names it and the run goes to the Lead (the plan's rule for an unlisted key).
- Errata 13 and 14: `-dvv`, its output read whole, never piped into `grep -q`. The first `Authority` is read with one `sed` that stops at it. `beta-reports-active` is allowed for distribution alone.

**`scripts/ios-release.sh`**:
- `check <xcarchive>`: the watch app is under `Watch/` with its widget extension; every app and appex `Info.plist` reads the archive's iPhone app's V and C (`Taqwa.app`, `PlugIns/*.appex`, `Watch/*.app`, `Watch/*.app/PlugIns/*.appex`; frameworks are not read); and `${TAQWA_WATCH_BUNDLE_CHECK:-scripts/check-watch-bundle.sh} --release <archive>/Products/Applications/Taqwa.app` passes. It prints the watch app's and its widgets' sizes. `archive`'s report runs it last.
- `export-check`: a local export of the last archive (`method` `app-store-connect`, `destination` `export`) into `build/ios-archive/export-check-<V>-<C>/`. It unzips the IPA into a fresh directory there, runs `check-watch-bundle.sh --release` on `Payload/Taqwa.app`, and an EXIT trap deletes the IPA and its copy whatever happens. It keeps the export's logs and `DistributionSummary.plist`, which also lists each bundle's entitlements.
- `archive`, `export-check` and `all` exit 2, before `xcode.sh` and before anything is built, while `TAQWA_CODESIGN` or `TAQWA_WATCH_BUNDLE_CHECK` is set at all.

**`scripts/ios-build.sh`** runs `scripts/test-watch-bundle-signing.sh` on the Debug build it has just made, after the bundle check. CI's `ios.yml` lists the new script among its paths. Its comment says where the signed checks now live.

**`docs/STORE-CHECKLIST.md`**:
- "Each watch release", a new section at the end of §4. It lists the bump, the face gate, Mohamed's `release-face.sh`, the Lead's linked `release.sh` with the `rm` and `ls` after it, then with Mohamed's OK archive, export-check and upload, then the uploads. It also covers the dry run that comes first, and leaving the tree alone while the face gate runs.
- §8: the release-signing line now covers both bundles. New entries give the watch builds' permissions, and a table of where each check lives.
- Ruling 71 item 4a: the two notes pointers (now at lines 403 and 429, after the new section) are untouched. Lane P edits them.

## The tests

**`scripts/test-watch-bundle-signing.sh [<Taqwa.app>]`** works on a clone (`cp -Rc`) of plan 4's Debug simulator build, with a stub `codesign`:
- The stub's `-dv` prints no `Authority`, and its `-dvv` prints them, each line one write to stderr, then codesign's later lines. Its lines are modelled on captured real output: the Debug build's ad hoc `-dvv`, and a signed bundle's `-dvv`.
- `-d --entitlements - --xml` gives a chosen plist.
- Cases: the plan's six, `beta-reports-active` under development, iPhone Distribution, `get-task-allow` false, an unknown `Authority`, another team's `application-identifier`, a wider keychain group, another app group, HealthKit without `--release`, and that `-dvv` was asked for.
- Red before (18 of 19 failed: `--release` was read as the app's path and `TAQWA_CODESIGN` was ignored). Green after: 19 ok in about 20 s, and again inside `scripts/ios-build.sh` on its fresh build.

**`scripts/test-release.sh`** takes 8 min 40 s (8 min 36 s and 52 ok after review round 1). It refuses to start (exit 2) while a `keystore.properties` file or link is in the root, or while `build/face-release/` holds an APK.
- `ios-release.sh`, in a scratch tree where `xcodebuild` and `gradlew` are fakes, so no case can reach Apple whatever the script does: `check` passes equal plists, a framework at 9.9.9 (1) included. It fails a watch plist one code behind, the watch's widgets at 0.0.1, no `Watch/`, no watch widgets, and a failing bundle check. `theArchiveIsCheckedAsARelease`: the stub got `--release` and the archive's `Taqwa.app`. No argument exits 2. The refusals: six cases (3 commands × 2 variables) exit 2 with nothing built. `archive`'s report checks its own archive as a release. `export-check` exports with `app-store-connect`/`export`, checks `Payload/Taqwa.app` as a release, and leaves no IPA or copy, when the check passes and when it fails. It uploads nothing.
- `release.sh`'s refusals, in a scratch tree whose `gradlew` records each call:
  - `disagreeingVersionsAreRefused`: the tree as it is gets past `check-versions.sh`, and with the Wear code changed by hand it stops there, nothing built.
  - The store run refuses with no pin (naming M1 and `scripts/make-face-key.sh`) and no `keystore.properties`, and refuses `TAQWA_FACE_CERT_FILE`.
  - `TAQWA_RELEASE_DRY_RUN=yes` exits 2.
  - The dry run refuses with no pin.
  - `aFailedStoreRunLeavesNoBundleToUpload` (review round 1). It runs the store path in the scratch tree, beside a decoy pin (the test key's) and a decoy `keystore.properties` inside that tree. The face gate is a stub, the face is the test-signed one, and `gradlew` copies fake outputs in. The store run builds with `-Ptaqwa.storeRelease=true` and the signed face, and never with `-Ptaqwa.unsignedRelease`. Refused at step 4 (unsigned outputs) or at step 5 (signed outputs that fail its checks), it leaves none of the four. Refused at step 3, it leaves the four that were there before it. Red first: on 0662aaab's release.sh, both failed runs left all four.
- The dry runs on this repository, beside the decoy. They run only once the scratch tree has shown that release.sh knows its dry run, because one that does not would take the store path:
  - `aFaceSignedByAnUnpinnedKeyIsRefused` (a scratch key);
  - `aStaleSignedFaceIsRefused` (one extra `res/raw/stale_extra`);
  - `aFaceSignedWithTheAppKeyIsRefused`. Pinned to the debug key, the face passes step 3 and `check-bundle.sh` catches it in step 5, with no output left;
  - `theDryRunNeverUsesTheUploadKey`. A copy of release.sh without `-Ptaqwa.unsignedRelease=true` fails on `/nonexistent/decoy.jks`; the real dry run names it nowhere, and all four outputs are the debug key's;
  - `theDryRunBuildsBothBundlesWithTheFaceInside`: the four outputs; step 5's lines; the face byte for byte in the Wear APK and AAB;
  - `theBundledTokenIsTheBundledApks`: `push-token.sh` run again on the face inside gives the APK's token. The validator's token was first checked to be the same for the same APK twice. The dry run prints it nowhere;
  - and the tree as found.
- Red before: the iOS cases failed on the old script (`check` was usage, the refusals ran the fake archive). With a release.sh that had only its preflight and step 1, the refusal cases passed and every dry-run case on the repository failed (20 failed): no refusals, no outputs. Green after: every case ok.

**The rest:**
- `scripts/ios-build.sh` passes: BUILD SUCCEEDED, the bundle check holds, `test-watch-bundle-signing.sh` passes on the fresh build.
- The first `scripts/test.sh` failed on the privacy sweep (above). It passed again after the `KNOWN` entry (ledger). `tools/privacy-check.py` is clean and `tools/test_privacy_check.py` is OK.
- A hand dry run's first attempt failed the face gate's "the tree as it was", because I edited tracked files while it ran. That is the gate working. The checklist now says to leave the tree alone. Run again untouched, it passed.

## Step 4: the dry run for Task 12

`TAQWA_RELEASE_DRY_RUN=1 TAQWA_FACE_CERT_FILE=<the test key's pin> scripts/release.sh`, after `release-face.sh` with the test key. It runs on this commit and leaves its outputs in `build/release-dry-run/` of the `watch-apple` worktree. The ledger line "[Lane A] … P5.7 Step 4 dry run" records each output's path, size, file SHA-256 and certificate. They stay the same across runs:
- the app's certificate (all four outputs) is the debug key's, `e5d76ef683b35977e7c4127144cabdcb1279afb59349b5f954c5dee77c2a7dc7`;
- the face inside is signed by the test key, `9939135e65000a3915c722d45463569863d191152bd5418c1fab569185743b70`, at `world.taqwa.app.watchfacepush.taqwa` 1.5.0 (41);
- the sizes on this tree: phone AAB 31.4 MB, phone APK 26.2 MB, Wear AAB 5.5 MB, Wear APK 4.8 MB. The R8'd Wear APK is 4.8 MB, the face inside included (472,806 bytes).

For Task 12 Step 3: the four outputs and the debug builds share one key, the debug key. The face differs from the debug build's, though both are signed with the test key: it is the release face, the debug build bundles the debug one. So the step's uninstall of the face first still applies.

## Step 4: the real archive and export check (7 November 2026)

The Lead ran both on `dd9d2786`, under `trunk-merge`, with Mohamed's OK in chat ("Yes, go ahead with archiving the iPhone app with the watch inside", 6 November), after he signed back in to Xcode. No upload. The version is still 1.5.0 (41): this proves the path, and candidate C archives again at 1.6.0 (42).

- `scripts/ios-release.sh archive`: ARCHIVE SUCCEEDED. `check` passes: `Taqwa.app`, `TaqwaWidget.appex`, `Watch/TaqwaWatch.app` and its `TaqwaWatchWidgets.appex` all read 1.5.0 (41), and every bundle rule holds as a release. `TaqwaWatch.app` is 3260 KB (limit 15360), its widgets 1292 KB (limit 5120), with no sample feed inside. This first archive registered `world.taqwa.ios.watchkitapp` and `.widgets` under team 3K93P4PA5H.
- The archive's signature: Apple Development (R2WATM9ZUH), with `application-identifier`, `com.apple.developer.team-identifier`, `com.apple.security.application-groups` (`group.world.taqwa.ios`) and `get-task-allow`.
- `scripts/ios-release.sh export-check`: EXPORT SUCCEEDED (a 37337 KB IPA, deleted after the check). The App Store signature passes the same bundle check.
- The App Store signature, on all four bundles: Apple Distribution, team 3K93P4PA5H (cloud-managed), each with its own App Store profile, and the keys `application-identifier`, `beta-reports-active` (true), `com.apple.developer.team-identifier`, `com.apple.security.application-groups` (`group.world.taqwa.ios`) and `get-task-allow` (false).

So concerns 2 and 3 below are closed: the Mac's cloud-managed distribution signs a local export, and rule 4b's key lists hold on real signatures.

## Not done here, by rule

- Step 4's archive and export-check are the Lead's, with Mohamed's OK in chat (Ruling 71 item 6). They ran on 7 November (the section above).
- The real `scripts/release.sh` is the Lead's alone, after M1 (the pin) and the cut.

## Concerns and notes

1. **Two paths no test here can reach.** Only the Lead's real run proves them:
   - the dry run's refusal of a signed output, which needs a release build signed by a real key;
   - the store run signing its outputs with the upload key. Its refusal of unsigned outputs, and its step 5 on signed ones, are proven since review round 1, but only on fake outputs in the scratch tree.
   Writing any `keystore.properties` other than the decoy is forbidden. `scripts/test-unsigned-release.sh` proves the property they depend on.
2. **`export-check` is proven against a fake `xcodebuild`.** That shows the options, the check, the cleanup and that nothing is uploaded. Whether the Mac's cloud-managed distribution signs a local `export` is the Lead's first run. If it cannot, the plan's fallback stands: read the entitlements in App Store Connect.
3. **Rule 4b's key lists could break the first real archive check.** They are written from the plan, not from a real signature. A key Xcode adds by itself (for example `com.apple.application-identifier`, which plan 4 allowed) would fail it, naming the key. Per the plan, that goes to the Lead.
4. **`tools/privacy-check.py` is Lane P's file.** One `KNOWN` line was added there, as its own header requires in the same commit as the swept lines. It does not touch any row.
