# The face inside the watch app (plan 5 Task 4)

31 October 2026, Lane W, branch `watch-wear`, base `2f7cedc8` (`Merge watch-wear: P5.3`). Test key only: no release key, no `keystore.properties`, nothing pushed.

## What was built

- **`FaceBundleTask`** (`wearApp/build.gradle.kts`), one per variant through `androidComponents.onVariants`, writes two generated directories: `assets/default_watchface.apk` and `res/values/face_token.xml` (`default_wf_token`, `translatable="false"`). The token comes from `tools/watchface/push-token.sh`, run through Gradle's `ExecOperations` on the very file bundled. Its output is kept, and shown only when it fails.
  - `-Ptaqwa.faceApk=<path>` is the bundled face for every variant, when given.
  - Otherwise debug and staging bundle `:watchface:assembleDebug`'s APK, signed with the test key at `-Ptaqwa.faceDebugKeystore` (default `~/.android/taqwa-face-debug.keystore`). `watchface/build.gradle.kts` reads the same property.
  - When that key is missing, as on CI, nothing depends on a face build (`-m` lists no `:watchface:` task). The build writes no APK and an empty token, and warns `the face is not bundled`.
  - Release without `-Ptaqwa.faceApk` bundles nothing and warns.
  - `-Ptaqwa.storeRelease=true` (true or false, anything else stops the build) adds `checkReleaseFace` to `assembleRelease`, `bundleRelease` and the release face task. It fails with "…: run scripts/release-face.sh, then scripts/release.sh".
- **Manifest:** `com.google.wear.permission.PUSH_WATCH_FACES`, and the `com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN` meta-data on the application, set to `@string/default_wf_token`.
- **`permissions.txt`:** the PUSH_WATCH_FACES line was written before the manifest line, and lands in the same commit.
- **Tests:**
  - `FaceManifestTest` (3): the permission, the one token meta-data, and no network or location permission.
  - `WearManifestTest.theWatchAsksOnlyToVibrate` became `theWatchAsksOnlyForThePermissionsListed`. The manifest's set must equal the file's `manifest` lines. Spec 2.1's NEVER may appear in neither the manifest nor the file. The launcher is `.app.WearMainActivity`.
  - A new `everyListedPermissionSaysWhereItComesFromAndWhy` checks the file's format.
  - `permissions.txt` is a declared test input (`taqwa.wearPermissions`).
- **`tools/watchface/check-bundle.sh <wear.apk> <face-cert-sha256>`** checks:
  - the asset is there;
  - the face's package, and its version code (the app's minus 100000);
  - the face's signer is the given certificate, never the app's;
  - the token is not empty, and is never printed;
  - `validator-push-cli` passes on the face for `world.taqwa.app`.

  Listings are read whole before `grep -q`: under `pipefail`, a `grep -q` that leaves the pipe early made `unzip` exit 141, and a face that was there read as missing once (seen on the staging APK).
- **`tools/watchface/push-face.sh [--inactive] [face.apk]`**, the one way the face reaches `emulator-5558`, built as the plan says. Its `wadb install` has no `-r` because the app has just been removed. `install.sh` now runs `push-face.sh` (`exec`, passing its arguments through), and no Gradle `install*` task runs.

## Plan errata resolved here

- **Errata p5 #3 (IMPORTANT).** The plan had `arabic.sh`, `bengali.sh`, `hijri.sh` and `weekday.sh` call `push-face.sh`. That would wipe the feed they had just written, and reinstall a face that Push cannot update at the same code. So they rebind instead:
  - The debug build's `DebugFeedReceiver` takes a second action, `world.taqwa.app.wear.DEBUG_REBIND`. It calls `ComplicationSources.requestUpdates` and `TileUpdates.request`, the two calls `clear` already made.
  - `lib.sh` `rebind_face` sends it by name with `--include-stopped-packages` after the force-stop, so the new process reads `files/feed.json` again.
  - `store.sh` (P2.3b) reinstalled the face the same way, and the plan's grep forbids that, so it was rewired too. `store-test.sh` now expects remove → write → stop → `DEBUG_REBIND`, and no install or uninstall at all. It was seen red on the old `store.sh` (4 FAIL), then green.
  - `store_feed` also passes `--include-stopped-packages`, so the sample reaches an app that has just been installed (`push-face.sh --inactive`, where nothing starts the app).
  - On the emulator, `hijri.sh '29 Jumada al-Akhirah'` rebound the pushed face (`rebind-hijri-1458-awake.png`). The face's `lastUpdateTime` and installer did not change.
- **Ruling 55 item 6:** already true before this task. The debug APK's assets are the four `sample-feed-capetown*.json` files alone (`wearApp/build.gradle.kts`'s `include`), and `wear-release-check.sh staging` still finds no test data.
- **Errata p5 #7 (`wear-sdk` uses-library):** belongs to Task 5, which adds the Push library. Task 4 adds no library, so nothing changes here.
- **Errata p3 #8:** plan 3's own. `kotlinx-coroutines-test` is already in `wearApp`.
- **The plan's K:** 38 on 24 October. It is 39 now (1.3.0 (39) / (100039)).

## Checks (Step 4)

- `./gradlew :wearApp:testDebugUnitTest`: 518 tests, 0 failed. The red step had 3 of the new ones failing for the expected reasons: no `PUSH_WATCH_FACES`, no token meta-data, and the manifest set differing from the file.
- `check-bundle.sh` on the debug and staging APKs, with the test certificate `9939135e…3b70` (CN=Taqwa face debug): PASS. Before the face was bundled: FAIL ("holds no assets/default_watchface.apk", "no string/default_wf_token").
- A scratch debug APK whose face was re-signed with `~/.android/debug.keystore` and re-packed: FAIL, "the face is signed with the app's key (e5d76ef6…)".
- `tools/wear-release-check.sh staging`: PASS with the new line (VIBRATE, PUSH_WATCH_FACES, DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION). The APK is 4.7 MB.
- `./gradlew :wearApp:assembleDebug -Ptaqwa.faceDebugKeystore=/nonexistent` builds and warns. `check-bundle.sh` on that APK fails: no face, empty token.
- `./gradlew :wearApp:assembleRelease -Ptaqwa.unsignedRelease=true` builds and warns "the face is not bundled". With `-Ptaqwa.storeRelease=true` added, it fails in `checkReleaseFace` with the plan's message.
- `git grep -nE 'installDebug|install -r "\$APK"' -- 'tools/watchface/*.sh'`: two comment lines only (`lib.sh:5`, `store-test.sh:23`).
- `:wearApp:lintDebug`: "No issues found". `:wearApp:minifyReleaseWithR8` builds.
- `tools/watchface/validate.sh`: both validators pass. All four `face-*.py --check`: up to date. No face file changed.

## The default install on emulator-5558 (Step 5)

The run held the `wear-5558` lock from 18:01 to 18:06 SAST.

- **Found:**
  - 5558 and 5562 running, paired through Studio's ports. The phone has no `world.taqwa.app`, so the harness answered no nodes. It was left untouched.
  - The watch app was 1.2.0 (100038) debug, with face 38 side-loaded (installer `null`), Arch and Amber active.
  - On AC, automatic time, TalkBack off, font 1.0, en-US.
- **`push-face.sh --inactive`:**
  - The face appeared within 4 s, at version code 39, installed by `com.google.android.wearable.dwf.receiver`.
  - It is listed in the picker's Add list as "Taqwa" (`picker-taqwa-inactive.png`), and it was not made active: the system fell back to "Simple".
  - The emulator's image has the Push service, so the check stays here and does not move to Task 13.
- **`activate.sh`, then `shoot.sh … 14:58 awake`:** the watch was taken off the charger first, because the charging screen covers the face. Against `pass-arch-en-1458-awake.png`, the largest difference is 2 levels, on 545 pixels (`pushed-arch-en-1458-awake.png`).
- **Left:**
  - The app 1.3.0 (100039) debug, with the Push-installed face 39 active. This is the new baseline: from this task on, the face reaches a device only through the app.
  - Arch and Amber, the slots empty, automatic time, the sample for today, TalkBack off, font 1.0, en-US, back on AC, the pairing ports as found (`left-as-found.png`).
  - 5562 and KB2604110095 untouched.
