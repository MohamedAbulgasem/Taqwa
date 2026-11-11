# Plan 5 Task 5: Watch Face Push adds, updates and makes the face active

Lane W, branch `watch-wear`, base `2d6e343a` (watch-companion after MM4), 5 November 2026.
The shots referred to below are in `2026-11-05-watch-face-push/`.

## Step 1: Google's pages and the library, read 5 November 2026

- **Version:** `androidx.wear.watchfacepush:watchfacepush` **1.0.0**. Google Maven's group index lists 1.0.0-alpha01, -beta01, -rc01, -rc02 and 1.0.0, so 1.0.0 is still the newest stable.
  - The AAR's manifest has `minSdkVersion 33`, `<queries><package android:name="com.google.android.wearable.dwf.receiver"/></queries>`, and `<uses-library android:name="wear-sdk" android:required="true"/>`.
  - Its R8 rules are empty ("safe to shrink").
  - Its dependencies: guava 31.1-android (with failureaccess, listenablefuture, jsr305, checker-qual, error_prone_annotations and j2objc-annotations), androidx.core 1.1.0, and kotlinx-coroutines-guava 1.9.0 (resolved to 1.10.2).
- **`WatchFacePushManagerFactory`:** `createWatchFacePushManager(Context)` throws `UnsupportedOperationException("Watch Face Push feature is supported starting from API level 36.")` unless `isSupported()` is true. `isSupported()` is `Build.VERSION.SDK_INT >= 36`.
- **`WatchFacePushManager`'s methods** (all suspend; from `javap` on the 1.0.0 classes):
  - `listWatchFaces(): ListWatchFacesResponse` throws `ListWatchFacesException` (`ERROR_UNKNOWN` 1). The response has `installedWatchFaceDetails: List<WatchFaceDetails>` (`slotId`, `versionCode`, `packageName`, `getMetaData(key)`) and `remainingSlotCount: Int`.
  - `addWatchFace(ParcelFileDescriptor, token): WatchFaceDetails` throws `AddWatchFaceException`: UNKNOWN 1, UNEXPECTED_CONTENT 2, INVALID_PACKAGE_NAME 3, MALFORMED_WATCHFACE_APK 4, SLOT_LIMIT_REACHED 5, INVALID_VALIDATION_TOKEN 6.
  - `updateWatchFace(slotId, ParcelFileDescriptor, token): WatchFaceDetails` throws `UpdateWatchFaceException`: UNKNOWN 1, UNEXPECTED_CONTENT 2, INVALID_PACKAGE_NAME 3, MALFORMED_WATCHFACE_APK 4, INVALID_SLOT_ID 5, INVALID_VALIDATION_TOKEN 6.
  - `removeWatchFace(slotId)` throws `RemoveWatchFaceException`: UNKNOWN 1, INVALID_SLOT_ID 2. Taqwa never calls it.
  - `isWatchFaceActive(packageName): Boolean` throws `IsWatchFaceActiveException`: UNKNOWN 1, INVALID_PACKAGE_NAME 2.
  - `setWatchFaceAsActive(slotId)` throws `SetWatchFaceAsActiveException`: UNKNOWN 1, MAXIMUM_ATTEMPTS_REACHED 2, INVALID_SLOT_ID 3, MISSING_PERMISSION 1000.
- **Permissions:** `com.google.wear.permission.PUSH_WATCH_FACES`, and `com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE`, a runtime permission.
- **The once-only rules** (developer.android.com, "Watch Face Push: Wear OS app configuration"):
  - The permission "has a maximum rejection count of 1".
  - The active face "can be set by this means *only once*", after which the phone app "should instead offer guidance on how to manually set the active watch face".
  - Don't store slot ids: "Always obtain the current state immediately before any slot-based operation".
  - Don't call `isWatchFaceActive()` right after `updateWatchFace()`: "If updateWatchFace() completes successfully, you can assume that your app still has control of the active watch face".
  - Wear OS 6 gives one slot.
- **The default face (Task 4):** `assets/default_watchface.apk`, plus `<meta-data android:name="com.google.android.wearable.marketplace.DEFAULT_WATCHFACE_VALIDATION_TOKEN" android:value="@string/default_wf_token"/>`. The system installs it when the app is installed and never makes it active. Google says it "isn't updated should a newer version be bundled with any update", so the app listens for `MY_PACKAGE_REPLACED`. On this emulator the system does update it; see Step 7 check 4.
- **Plan 2's three library checks**, on the staging build:
  1. The merged manifest asks for exactly `wearApp/permissions.txt`. The only new permission is `SET_PUSHED_WATCH_FACE_AS_ACTIVE`, which is named (`tools/wear-release-check.sh staging` and `release`: PASS).
  2. The library adds only its `<queries>` package (`com.google.android.wearable.dwf.receiver`, the system's Push receiver) and its `uses-library wear-sdk`, which the app's manifest makes not required. It adds no component.
  3. The dex holds no `com/google/firebase`, `gms/measurement`, `gms/ads`, `datatransport`, `crashlytics`, `sentry`, `bugsnag`, `appsflyer` or `amplitude`. The new code is guava's, under R8. The staging APK is 4,977,345 bytes (4.7 MB, as before).

## What was built

- **`wearApp/.../face/`**
  - `FacePushPort` (with `FACE_PACKAGE`, `InstalledFace`, `FaceRefused`) and `AndroidFacePushPort`. The port touches the library only where all three hold: API 36, `isSupported()`, and the system's shared libraries containing `wear-sdk`. Only `MAXIMUM_ATTEMPTS_REACHED` and `MISSING_PERMISSION` become `FaceRefused`.
  - `FacePush`, with one `Mutex`, `UPDATE_SETTLE` of 30 s, and `refreshAfterUpdate`.
  - `FaceMemory`, which keeps the one activation or refusal, and the face's last-seen version and activity.
  - `BundledFace` and `FaceStage`, which copy the asset to `cacheDir/face-<code>.apk` and read it once per install of the app (review round 1 below).
  - `FacePushes`, the process's one `FacePush`. It is a file of its own, so that `FacePush.kt` stays free of Android.
  - `FaceCapability`, which owns `wanted` and `publish`.
  - `FaceUpdateReceiver` (`MY_PACKAGE_REPLACED`, `goAsync`, 9 s).
  - `FaceActivity`, `FaceScreen` (with `faceView` and `faceWords`) and `FaceRow` (with `rememberFaceChip`).
- **Wiring:**
  - `TaqwaWearApp.onCreate` runs `refreshAfterUpdate`.
  - Today ends with the chip while `wanted`.
  - The daily nudge publishes `state()`.
  - Every `FacePush` call logs `TaqwaFace <call> <slot or code> <result>`, and never logs the token.
- **Manifest:**
  - `<uses-sdk tools:overrideLibrary="androidx.wear.watchfacepush"/>`;
  - `SET_PUSHED_WATCH_FACE_AS_ACTIVE`;
  - `<uses-library android:name="wear-sdk" android:required="false" tools:replace="android:required"/>`;
  - `FaceActivity`, exported, with VIEW, BROWSABLE and DEFAULT on `taqwa://face`;
  - `FaceUpdateReceiver`, not exported.
- **Feed:**
  - `WatchUi.face: WatchFaceWords` (add, about, active, chooseIt, failed; empty by default, and `WatchFeedJson.VERSION` stays 1);
  - `WatchLinkContract.FACE_CAPABILITY = "taqwa_face_addable"` and `FACE_URI = "taqwa://face"`;
  - `:shared`'s `WatchWords.face(language)` in seven languages: the plan's English, and the other six marked `draft, to native review (plan 5)`, worded as the phone's "Add to Apple Watch" failure line words a watch face;
  - `WatchFeedBuilder` fills it.
- **The watch's own `face_*` strings** are in all seven `values*/strings.xml`. `FaceWordsResourcesTest` holds them to the feeds' `ui.face` in English, Arabic and Urdu, and `SystemStringsTest`'s draft markers cover the other six.
- **Regenerated:**
  - `watchcore/vectors` (`-Ptaqwa.updateVectors`): the four samples, `feed-vectors.json` (its `newerPhone` holds a `future` in `ui.face` too, the twelfth class) and `watch-rules-vectors.json`;
  - `:shared`'s five phone golden feeds.
  - `Fixtures` gains `ENGLISH_FACE` and `ARABIC_FACE`.
  - The Swift mirror `FeedVectorsTests.testALaterPhonesKeyIsIgnoredInEveryObject` names `ui.face` (12 strays). The Swift port does not read the face words, and ignores them.
- **Plan 2's Ruling 39 table** gains the face words' row: the phone's text in the interface language, nothing of the place or the person.

## Tests, red first

- **Red** (stubs that throw `TODO()`, so that each test failed for the missing feature):
  - FacePushTest: 27 tests with NotImplementedError. The plan's 24 names, plus `aTransientSetActiveErrorIsFailedNotSpent` (erratum p5), the face screen's states, and `theFeedsFaceWordsComeFirstAndTheWatchsOwnBeforeAnyFeed`.
  - FaceManifestTest: 4 (`SET_PUSHED`, wear-sdk not required plus `overrideLibrary`, the `taqwa://face` filter, the receiver). Then the manifest merge failed on minSdk 33 until the override was in.
  - `WatchLinkContractTest.theFacesCapabilityAndLinkAreTheContracts`.
  - `:shared`'s `theFaceWordsAreTheInterfaceLanguages` and the five-phrases test.
  - The stale vectors: VectorsTest, SwiftPortVectorsTest twice, WatchFeedGoldenTest, the sample test, and FaceWordsResourcesTest.
- **Later red, from Step 7:**
  - `aFaceTheSystemUpdatedIsNotAskedWhetherItIsActiveRightAway`: expected ACTIVE, got INSTALLED.
  - The `theLauncherInstallsTheSplashBeforeItsFirstFrame` guard caught a mutant that put `installSplashScreen()` after `super.onCreate`.
- **Green:**
  - watchcore + shared + wearApp + widgetcore host tests: 3162/0 at WIP 1. The face's tests are 41/0 after the fix.
  - `swift test --package-path iosApp/WatchCore`: 285 + 5 + 14 + 19 + 19, 0 failures.
  - `:wearApp:lintDebug`: no issues.
  - `tools/wear-release-check.sh staging` and `release`: PASS.
  - `scripts/test-unsigned-release.sh`: PASS, with the new misspelt-flag case.
  - `python3 tools/privacy-check.py`: 263 rows, 236 sweep lines, clean. `--pages` is clean.
  - `tools/i18n-check.py`: clean.
  - face-arch, face-centre, face-moon and face-sun `--check`: all up to date. No face file changed, so the always-on lit share is untouched.
  - **`scripts/test.sh` whole:** exit 0 under ios-sim-tests (held 23:33:50 to 23:42:13). It ran check-versions 1.5.0 (41)/(100041), test-bump, test_privacy_check, the strict privacy gate, test_store_listing and the listing check, and the Swift tests. Gradle's suites came out shared host 2379, shared iOS 2355, watchcore 233 + 200, widgetcore 111 + 109 and wearApp 553, with 0 failures and no EOFException.
  - Not run here, for the Lead's merge: `scripts/ios-build.sh` (this task's only iosApp change is a WatchCore Swift test) and `tools/watchface/validate.sh` (no face file changed).

## Step 7 on emulator-5558, paired with emulator-5562

Both emulators were down. I booted them with `tools/watchface/emulator.sh` and `scripts/phone-emulator.sh`. The phone had no Taqwa app, so I installed this tree's `androidApp` debug build there. `scripts/wear-pair.sh` then answered: node c76e057a nearby, `taqwa_watch=true`. The phone's Google sign-in was in place, and nothing asked for a sign-in.

- **Fresh install** (`push-face.sh --inactive`):
  - The face came at code 41, installed by `com.google.android.wearable.dwf.receiver`, not active.
  - Log: `installed [948eb69c… 41]`, `isActive false`, `capability added INSTALLED`.
  - The phone's Wearable dumpsys lists `taqwa_face_addable` under the watch's node.
- **Check 1, TalkBack on:**
  - Today's list ends with "Add the Taqwa face" (`c1-today-end.png`). TalkBack reads it as "Add the Taqwa face. Button".
  - The chip opens FaceActivity, which TalkBack reads as "Taqwa. Heading", then the about line, then the button.
  - The button asks "Allow Taqwa to change the active watch face?". After Allow, TalkBack reads "Taqwa is your watch face." as it appears.
  - The Taqwa face (Arch) is the watch face (`c1-watch-face.png`).
  - The capability is gone from the phone's dumpsys and from the watch's.
  - Today shows no chip (`c1-today-after.png`).
  - The emulator's Push activation was not used up by this: later activations from the app succeeded, in the spec lens's run and again in the re-issue's check 1 (`setActive … ok`). Wear OS 6 on this emulator does not hold an app to one activation across installs, so the once-only rule is the app's own (`FaceMemory`), and his Galaxy's count is checked in Task 13.
- **Check 2, a fresh install, then Don't allow:**
  - The screen shows "Touch and hold your watch face, then choose Taqwa." (`c2-denied.png`), and the log says `capability removed CHOOSE_IT_YOURSELF`.
  - After Back, Today has no chip, and the phone lists only `taqwa_watch`.
  - `taqwa://face` shows the guidance line, with no dialog and no button (`c2-pair.png`).
- **Check 3:**
  - I made Taqwa active by hand (`activate.sh`), then chose Abstract in the picker (`AbstractWatchFaceService`).
  - Today has no chip, the phone has no capability, and `taqwa://face` shows the guidance line (`c3-pair.png`).
  - `logcat -b crash` is empty.
- **Check 4:**
  - The face was active, then customised: Moon, Sage, the battery in the left slot (`c4-before.png`).
  - I ran `bump-version.sh 1.5.0 42`, built the debug app and installed it with `install -r`.
    - **Finding:** the system's Push service updated the face to 42 itself, at the app's install (PackageManager "Update package world.taqwa.app.watchfacepush.taqwa"). That was before Taqwa's process ran, so neither the start nor the receiver had an update to make. Both found 42 current.
    - Both then asked `isWatchFaceActive` and got **false** for the active face, and true again 48 s later: Google's "right after an update" trap, reached through the system's own update.
  - **The fix, test-first:** `FaceMemory` keeps the face's last-seen version and activity. A version this process did not make counts as a change just made: no activity check, the last-seen activity is used, and the answer holds for `UPDATE_SETTLE` (`aFaceTheSystemUpdatedIsNotAskedWhetherItIsActiveRightAway`, `theFirstFaceSeenIsAskedWhetherItIsActive`).
  - **Re-run on the fix, 42 to 43:**
    - The log says `changed 42->43 active (not asked)`, and the second refresh found the face current inside the settle window, with no `isActive`.
    - The face is 43, still active, still Moon, Sage and battery (`c4-after-43.png` against `c4-before.png`; only the minute differs).
  - The version went back with `bump-version.sh 1.5.0 41`. `check-versions` exited 0, and the version grep on `git diff -U0` prints nothing. There is no version bump in any commit: the watch ships as 1.6.0 (42) at the cut.
  - **The plan's "one update, the other finding it current" does not hold on this image:** Wear OS 6's system updates the bundled default face with the app, against the page's note. The app's update path stays as the net for a system that does not.
- **Check 5:** `am start -a VIEW -c BROWSABLE -d taqwa://face` opens FaceActivity.

## Ruling 70 item 2: the face after an uninstall (my call)

- `adb uninstall world.taqwa.app` removes the pushed face with it. No `world.taqwa` package is left, and the system falls back to its DefaultWatchFace.
- Reinstalling the app brings a fresh default face, 41 from dwf.receiver.
- I set the phone to London (the harness's `place`). The watch then held London's feed. The face, made active, read **FAJR 5:22, in 7 h 54 min**, which is London's 05:22Z from the phone's plan (`r70-face.png`). So the data was not stale.
- With Watch Face Push the face cannot outlive the app, so the stale-face case of a side-loaded face cannot arise. **No re-push or re-activation on first start is added.** P5.13's Galaxy pass still repeats this on his watch.

## Errata resolved

- **p5 CRITICAL (wear-sdk):**
  - The manifest's `uses-library wear-sdk required=false` with `tools:replace`, held by `FaceManifestTest.watchesBeforeWearOsSixStillInstallTheApp`.
  - `wear-release-check.sh` asserts it in the merged APK (`uses-library-not-required:'wear-sdk'`, and no required one).
  - `AndroidFacePushPort` checks `systemSharedLibraryNames` for wear-sdk, and `FacePush` treats a `LinkageError` as UNSUPPORTED (`aPushThatThrowsShowsNoRow`).
- **p5 IMPORTANT (setActive's codes):** only MAXIMUM_ATTEMPTS_REACHED and MISSING_PERMISSION are `FaceRefused`. Any other error answers FAILED and leaves the activation unspent (`aTransientSetActiveErrorIsFailedNotSpent`).
- **p5 IMPORTANT (TalkBack on a physical watch):** this task's TalkBack line ran on emulator-5558 through `talkback.sh`, which works there. Task 13 still needs Mohamed's hands, as the erratum says.
- **p5 Ruling 70:** item 2 above. This task does not touch item 1.
- **p5 wave 7 minors:**
  - `WearManifestTest.theLauncherInstallsTheSplashBeforeItsFirstFrame` holds `installSplashScreen()` before `super.onCreate`, and caught a mutant.
  - `test-unsigned-release.sh` has the misspelt-flag case (`=ture` stops both modules with their message), and warns before it removes the release outputs.
  - `wear-release-check.sh` fails a missing or unreadable dex. It reads the listing whole, since under pipefail a `grep -q` pipe failed falsely. It also asserts the APK is not debuggable, and its usage names `-Ptaqwa.unsignedRelease=true`.
  - The report drivers (`2026-10-23-phone-forms-fit/feedtext.sh` and `restore.sh`, and `2026-10-24-wear-app-pass/drivers/restore.sh`) rebind the face with DEBUG_REBIND instead of installing it.
  - `FaceBundleTask` takes `lib-validators.sh` as an input (P5.4's code-lens minor).
  - Plan line 310's splash wording is now the built form.
- **Errata #4 and #5:**
  - The privacy promise is untouched. The face words carry nothing of the place or the person, and plan 2's table says so.
  - No face file changed, so the face-*.py checks and the lit share stand.
  - Ruling 55 item 6 holds: the release check finds no vector in the APK.
  - The version is not bumped.

## Review round 1

Two lenses on `2d6e343a..3c02993b`: the spec, behaviour and evidence lens CLEAN (0 Critical, 0 Important, 9 minors), and the code lens NEEDS FIXES (0 Critical, 1 Important). The Important is fixed. No ruling is touched: 39, 41, 42 and 43 hold, and no word, permission, face file or vector changed.

- **IMPORTANT (code lens): the bundled face was copied out of the APK and parsed at every process start on Wear OS 6.** `BundledFace.load` copied the whole asset (481,791 bytes) to the cache and ran `getPackageArchiveInfo` on it at each load, and `FacePushes` kept the answer only for the process. On a watch a process starts for every complication, Tile, Data Layer or nudge wake. A copy the system cleared from the cache while the process ran also made every add and update fail until the next restart.
  - **The fix:**
    - `FaceStage` copies and reads the face once per install of the app. An install is the app's version code with its `lastUpdateTime`, so a rebuild at the same code counts as a new install too.
    - The face's version code is recorded in `noBackupFilesDir/face-bundled` as `<install> <versionCode>`. That file is outside the cache, so a start that finds the record copies and reads nothing.
    - `BundledFace` now knows its `versionCode` without its file. The file is reached only through `apk()`, and only an add or an update calls it, inside `FacePush`'s guard. `state()` and a current face never touch the file.
    - `apk()` copies the face again when the cache file is gone, with no second read. If that copy fails, it throws `IOException`, and the add or update answers FAILED.
    - Every copy is written to `face.part` and then renamed, so a copy cut short never takes the face's name. Earlier installs' copies are removed when a new face is copied, the old `face-staged.apk` included.
    - The interface changes from the plan's `BundledFace(val apk: File, …)` to `BundledFace(token, versionCode, staged)` with `apk()`. A constructor with the plan's arguments is kept for the tests.
  - **Not done:** the build does not write the face's version code at build time, and the version is not taken from the app's code minus 100000. The plan says the code is read with `getPackageArchiveInfo`, and a debug build given `-Ptaqwa.faceApk` may bundle a face whose code does not follow the app's.
  - **Tests, red first:**
    - `FaceStageTest` (10 tests) has a skeleton with the old behaviour, which went red for the finding's reasons. Three starts made 3 copies where 1 was expected, and a cache cleared mid-process gave the reviewer's `FileNotFoundException`.
    - `FacePushTest` gains two tests:
      - `onlyAnAddOrAnUpdateAsksForTheFacesFile`: `state()` and a current `ensureCurrent` ask for no file;
      - `aFaceThatCannotBeCopiedFailsItsAddOrUpdate`: FAILED, no `add` and no `update` sent, and the activation kept.
    - Six mutants were all red:
      - no copy again at use;
      - the copy written straight onto the face's name;
      - the earlier copies kept;
      - `state()` asking for the file;
      - the record ignored;
      - the file asked for outside the guard.
  - **Checks:**
    - `:wearApp:testDebugUnitTest --rerun` 565/0 (553 + 12);
    - `lintDebug`: no issues;
    - `assembleDebug`, plus `assembleStaging -Ptaqwa.unsignedRelease=true` and `wear-release-check.sh staging` PASS;
    - `scripts/test.sh` whole exit 0, under `ios-sim-tests`.
  - **On emulator-5558,** with `ad80e70a`'s debug build installed with `-r` over the found 1.5.0 (100041):
    - The install's first start logs `TaqwaFace bundled 41 copied` once.
    - Two more process starts (`force-stop`, then `taqwa://face`) log no `bundled` line, and the copy's mtime stays at 00:19:31.748.
    - `cache/face-41.apk` was removed while the process ran. After that, `state()` in that process and in a new one made no copy and no read, and answered ACTIVE.
    - Then the face was removed from its slot, with the memory set to spent so that no dialog would show. The button logged `remainingSlots - 1`, `bundled 41 copied again` and `add 41 <slot> 41`, then showed the guidance line. The new copy is byte-for-byte the APK's asset.
    - The watch was put back as found (ledger, 00:24).
  - **Seen on the way:** at the same code, `install -r` made Wear OS 6 reinstall the face (its `lastUpdateTime` moved). `isActive` answered false straight after and true 45 s later. A version this process did not make is caught by `FaceMemory.seen` (check 4). A reinstall at the same code is a development case only, and the next start corrects `seen`.
- **Minors left, for the Lead,** as the reviewers gave them. None is fixed in this round.
  - Code lens:
    - on R8 builds the TaqwaFace lines carry obfuscated failure names and no Push error code;
    - `UPDATE_SETTLE` is 30 s, while the system was seen taking 48 s once;
    - `FaceMemory` can race on its first load;
    - a FAILED update publishes the addable capability;
    - an `isActive` error blocks the update;
    - `spent` is still queried after it is spent;
    - `FaceScreen` has no rotary scrolling;
    - the button is about 35 dp (fixed in the re-issue's review round 1, below);
    - test gaps, among them the unused `updateThrows`;
    - the orphaned `Fixtures` KDoc;
    - `c3-pair.png` is the same file as `c2-pair.png`.
  - Spec lens:
    - a stale `seen.active` makes the update answer INSTALLED for 30 s;
    - the claim above that "the emulator's Push activation is spent" is wrong: the spec lens's run made the face active from the app;
    - the app's own update path has not run on a device, so Tasks 13 and 17 should record which path ran;
    - `c3-pair.png` is the same file as `c2-pair.png`;
    - a permission dialog swiped away spends the chance;
    - the `Fixtures.kt` KDoc points at nothing;
    - publish logs on a timeout;
    - the fr, tr, in and bn `face_*` strings are not tested against the feed.
  - The spec lens's "BundledFace copies per process" is the Important above, and it is fixed.

## Review round 1 of the re-issue, 6 November

Two lenses on Task 5 as landed (`464cd610`): the spec, behaviour and evidence lens CLEAN (0 Critical, 0 Important, 5 minors), and the code lens NEEDS FIXES (0 Critical, 1 Important). The Important is fixed. No ruling is touched: 39, 41, 42 and 43 hold, and no word, permission, face file or vector changed.

- **IMPORTANT (code lens): the face button was laid out about 36 dp tall.** Google's Wear app quality item WO-V2 asks for "a minimum of 48x48dp touch targets", and Play holds a watch app to it. The button is Today's chip and the face screen's button, the only control that adds the face or makes it active.
  - **What was already true:** Compose widens a clickable smaller than 48 dp to 48 dp, both for taps (the hit test's minimum touch target) and for screen readers (`touchBoundsInRoot`, the bounds it gives accessibility). On emulator-5558 the landed build's button node was already 299 x 96 px (48 dp), over a pill drawn 299 x 72 px (36 dp). So the target was 48 dp in effect, but only through the framework's widening, which yields to any clickable it overlaps, and which nothing in Taqwa's code or tests held.
  - **The fix:**
    - `FaceButton` lays out the pill as before, and a touch target over it (`FaceButtonMeasure`): the pill grown evenly to at least 48 dp each way.
    - The target takes the tap and carries the button's semantics ("Add the Taqwa face", Button).
    - The pill keeps the outline and shows the press, through one interaction source. Its own words are hidden from screen readers, so they are said once.
    - The button reports the pill's size, so nothing around it moves, at any font size. The target reaches 6 dp past the pill, into the room both screens leave around it: the 6 dp above Today's chip, and the face screen's 14 dp spacer.
  - **Not the review's outer 48 dp box:** that grows the button's layout by 12 dp, which moves the pill, and on the face screen the heading and line too, at the default font size. That is against the rule that accessibility changes nothing drawn at default settings.
  - **Test, red first:** `FaceButtonTest` (5 tests) runs `FaceButtonMeasure` over stand-ins for the pill and the target, at 1 to 3 px a dp:
    - the target is at least 48 dp each way, and measured at its own size;
    - it is centred on the pill to the pixel;
    - the button is the pill's size, the pill is where it was, and it gets the caller's constraints;
    - a pill over 48 dp is its own target;
    - a small pill grows both ways.

    A skeleton with the old behaviour (the target is the pill) failed two of them: "at 1.0 the target is 36 px tall, under 48 dp".
  - **On emulator-5558** (`touch/phase.sh` and `touch/pill.py`), with the Taqwa face installed but not the watch's face (Abstract chosen), first the landed build, then this one installed with `install -r`:
    - In both builds the button node is 299 x 96 px, at [78,290][377,386] on Today and [78,277][377,373] on the face screen. The drawn pill is 299 x 72 px, 12 px inside the target above and below.
    - The shots are the same pixel for pixel in both builds, at font scale 1.0 and 1.3 (`touch/today-end.png`, `touch/face.png`, `touch/face-1.3.png`). So are the accessibility trees: no new node, and the same words.
    - At 1.3 the words wrap, the pill is 130 px tall, and it is its own target.
    - On Today, a tap 6 px above the pill opens the face screen, and a tap 2 px above the target does not.
    - The watch was put back as found (ledger, 10:13).
  - **Checks:** `:wearApp:testDebugUnitTest` 577/0 (572 + 5); `lintDebug` no issues; `scripts/test.sh` exit 0, under `ios-sim-tests`.
- **Fixed in passing:** the two lines above that said the emulator's Push activation was spent, which both rounds' spec lenses found wrong.
- **Minors left, for the Lead:**
  - Spec lens:
    - plan 5's lines 1077 and 1262 expect "one TaqwaFace update", where Wear OS 6 logs `changed … (not asked)`; the Lead amends them before Tasks 13 and 17;
    - the system's face update and the app's first read are not ordered;
    - TalkBack speaks the face screen's line before its heading when the screen opens;
    - plan 5's line 466 still has the plan's `BundledFace` block.
  - Code lens: its 12 minors, as its ledger line counts them.

## Minors left, for the Lead

- The P5.3/P5.4 code-lens minors not done here, from the wave 7 journal:
  - the stale-mapping check;
  - `SampleFeed` is inert on an R8 dex, and there is no merged-manifest check for the debug receivers;
  - `ApiException` logged as `bb` on R8 builds;
  - the decoy write could follow a link (`set -C`);
  - the dex scan rests on two string literals;
  - staging takes only `src/release/kotlin`;
  - Hijri stays English after `arabic.sh`/`bengali.sh`;
  - `faceDebugKeystore` is resolved against the daemon's directory;
  - `bengali.sh`'s `FACE_APK`;
  - `testDebugUnitTest` builds the face;
  - `push-face.sh`'s wait accepts a face left behind by a failed uninstall.
- `FacePushes` is its own file (the plan lists the object with FacePush).
- The emulator's Push activation is not spent: a fresh install of the app can make the face active again (above, check 1). His Galaxy's first activation should still be Task 13 check 1's, since a real watch may keep its count.
- The Today chip makes the list's end differ from the Tile's while it shows. Plan 3's shot comparison holds only with the chip hidden, which is every state but the three wanted ones.
- The emulator-5562 phone now has this tree's debug app, with the place set to London.
