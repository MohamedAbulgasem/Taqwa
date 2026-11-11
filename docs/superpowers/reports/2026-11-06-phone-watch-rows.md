# Plan 5 Task 6: the phone's watch rows

Lane P (`watch-feed`), 6 November 2026. Plan 5 Task 6 ("The phone's watch rows"), built test-first.

- Base: `eab5c103` (watch-companion after R68.4's merge; `git merge --ff-only watch-companion` was already up to date). No WIP from an earlier run.
- Commits: WIP `f5145425` (the failing tests over skeletons, the library and the strings) and WIP `493c9d2f` (the code, green), folded with this report into the plan's one commit (Step 8), `0a7f83a5`. Review round 1's fix is its own commit on top ("Settings: a tap's answer on the watch rows is a whole line under its row, after review"; [Review round 1](#review-round-1-the-taps-answer-was-cut)). Taqwa identity, no trailer, nothing pushed, version not bumped (1.5.0 (41) / (100041) until the 1.6.0 (42) cut).
- Devices: `emulator-5562` and `emulator-5558` under `phone-5562` and `wear-5558` (12:50:48 to 13:05, and for the fix round 14:07:39 to 14:29); the `ios-pair` simulators under `ios-pair` (13:07:06 to 13:13); `ios-sim-tests` for every Kotlin/Native run. All put back as found (ledger lines of 13:05, 13:13 and the fix round's). The plugged-in phone `KB2604110095`, the work iPhone 12, Dev 1's 5572 and the booted iPhone 17 Pro Max `EDCDAF37` were not touched. No key was read or used: the release was only processed and shrunk, with `-Ptaqwa.unsignedRelease=true`.

## What was built

**Common rules** (`shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/`), all `internal`, as the plan's Interfaces give them:
- `WatchTargets.kt`: `WatchPeer`, `WatchTarget`, `WatchTargets` (with `NONE`), `WatchAction`, `watchTargets(connected, withApp, wantsFace)` and `watchSectionShown(targets, appleFaceOffered)`.
  - `install` is the first nearby watch not in `withApp`; `addFace` the first nearby watch in both `withApp` and `wantsFace`.
  - No Wear API (`connected == null`) or no nearby watch is `NONE`.
- `RemoteIntentSpec.kt`: VIEW + BROWSABLE, with `market://details?id=world.taqwa.app` or `WatchLinkContract.FACE_URI` (`taqwa://face`).
- `WatchTargetsSource.kt`: the interface, the `expect`, and `loadWatchTargets(source, bound = 3.seconds)`, which is `NONE` on a timeout or a throw and lets the caller's own cancellation through.
  - **Added beyond the Interfaces block:** `openOnWatch(source, target, action, bound = 10.seconds)`. `RemoteActivityHelper`'s future completes only when the watch's companion app answers. Without a bound, a companion that never answers would leave a row asking forever. Built first as false on a timeout or a throw, it answers in three ways since Ruling 72 item 3, and the bound running out is "Continue on your watch" ([below](#item-3-a-tap-still-on-its-way-says-continue-on-your-watch)). Since Ruling 74 the bound counts from the request's sending, and a request expires 2 minutes after it ([below](#ruling-74-items-915-6-november-lane-p3); [Known limits](#known-limits)).

**Android** (`androidMain/.../watchfeed/PlayWatchTargets.kt`), over four calls a test can hand it:
- It asks `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable` first, as the feed's sender does. Without Play services it answers `NONE` and makes no Wearable client.
- Then `NodeClient.getConnectedNodes()` (`displayName`, `isNearby`). An `ApiException` for which `noWearApi` holds (17) is `NONE`; any other throw propagates, and `loadWatchTargets` turns it into no row.
- Only with a nearby watch does it ask the two capabilities, each `FILTER_REACHABLE`.
- `open` is `RemoteActivityHelper(context, directly).startRemoteActivity(intent, nodeId).await()`, using `androidx.concurrent.futures.await` from the library's own `concurrent-futures-ktx`.
  - The helper is made at the tap, so no Wearable client exists before Play services is known.
  - It runs its listeners on the completing thread, so no thread of its own is made per tap.
- `WearCodesTest.playServicesIsAskedThroughItsPublicApiAndNothingCanShow` scans the whole `watchfeed` package's compiled calls, this class included. It still passes: nothing in it can show UI or a notification.

**iOS** (`iosMain/.../WatchTargetsSource.ios.kt`): `NONE`, and a tap that came anyway is false.

**Settings** (`feature/settings/WatchRows.kt`, `SettingsRootScreen.kt`):
- `WatchSection()` sits between App and About and brings its own `GroupGap`. It shows when `watchSectionShown(targets, offer.available())` holds, and draws nothing otherwise.
  - It uses the screen's own `SectionLabel`, `SettingsCard`, `CardDivider` and `TaqwaRow` (through `LinkRow`, with the external-link glyph, as "Add to Apple Watch" has).
  - The rows are read on every `RESUMED`, off the main thread (`Dispatchers.Default`), through `loadWatchTargets`. Since 9 November also in place while Settings stays in front, at each change on the watch's side and for 3 minutes after a tap ([Live rows](#live-rows-9-november-lane-w44-rows)): built first on resume alone, the install row stayed after Taqwa was installed on his watch.
- **The install row** takes the watch's name as its value.
- **A tap** shows "Continue on your watch" or "Couldn’t reach your watch" in a line of its own under the row (`RowAnswer`), level with the row's label, wrapped rather than cut. The row keeps its value, the watch's name. The line is one merged node in a polite live region, which exists only once a tap has answered, so the watch's name is never announced. A second tap while one is being asked starts nothing.
  - Since review round 1. Built first as the row's value, as Step 4 words it, the answer was cut at default settings ([Review round 1](#review-round-1-the-taps-answer-was-cut)).
  - The answer belongs to the reading it was made in. When Settings comes back, the rows are read again, and the line goes. A reading in place (since 9 November) is part of the same reading, and keeps the line while its row stays.
- `WatchFaceRow` moved out of the App group into the section. Its `offered` and `divider` are now parameters, and both default to the row's old behaviour.
  - `offered` defaults to asking the offer itself on `STARTED`, so `WatchFaceRowTest`'s seven tests are unchanged.
  - `WatchSection` passes the answer it already has, so the section and its row never disagree for a frame.
  - The failure line's state now sits after the early return, so a row that goes away forgets its line by leaving composition. That replaces the old `if (!offered) failed = false`, and `aRowThatGoesAndComesBackBringsNoOldLine` still holds it.
- **One small enabling change:** `GroupGap` became `internal`.
  - `TaqwaRow` and `LinkRow` had gained a trailing `modifier` for the row's live region. Review round 1's fix moved the live region to the answer's own line, and both are back as they were on `eab5c103`.
- `WatchFaceRow`'s failure line and the Android rows' answers are drawn by one `RowAnswer`, the node plan 4 built for CMP's iOS mediator.
- **`SettingsRoutes.kt` is unchanged**, though the plan's file list names it. The section loads its own rows, as plan 4's row loads its offer. `SettingsRootScreen` is public, and `WatchTargetsSource` is internal by the Interfaces block, so the route could not pass a source through the screen's signature anyway.

**The strings** (Compose `values*/strings.xml`, all seven, with the `values-in` twin):
- `settings_group_watch`, `settings_watch_install`, `settings_watch_face`, `settings_watch_continue` and `settings_watch_unreachable`.
- The six other languages are drafts, each marked `<!-- draft, to native review (plan 5 Task 11) -->`.
- `settings_watch_face` takes each language's form from the watch's own `face_add`, so the phone and the watch say the same words.
- "your watch" follows the phone's existing words: ساعتك, votre montre, saatiniz, jam tangan Anda, اپنی گھڑی and আপনার ঘড়ি.
- **One deviation:** the group label is **"WATCH"**, not the plan's "Watch". Every other section label in the resource is upper case (PRAYER, QURAN, APP, ABOUT).

**The library:** `androidx.wear:wear-remote-interactions` **1.2.0**, the newest stable (25 Feb 2026; 1.3.0 is an RC). It is in `gradle/libs.versions.toml` in alphabetical order, and in `shared/build.gradle.kts` androidMain.

## Step 1: the library's three checks (Mohamed: no tracker, no unnecessary permission)

| Check | Result |
|---|---|
| The phone's merged release manifest: `uses-permission` | **12 before, 12 after, the same lines** ([`permissions-before.txt`](2026-11-06-phone-watch-rows/permissions-before.txt), [`permissions-after.txt`](2026-11-06-phone-watch-rows/permissions-after.txt)). |
| The manifest's other additions | **One element, and no component:** `<uses-library android:name="wear-sdk" android:required="false"/>` ([`release-manifest.diff`](2026-11-06-phone-watch-rows/release-manifest.diff)). It is declared by both `wear-remote-interactions` 1.2.0 and `wear-core` 1.0.0 in their own manifests. **A Lead's call, below.** |
| New artifacts on the release classpath | **Two, both `androidx`:** `androidx.wear:wear-remote-interactions` 1.2.0 and `androidx.wear:wear-core` 1.0.0 ([`deps.diff`](2026-11-06-phone-watch-rows/deps.diff)). One version changed: `play-services-basement` 18.9.0 to **18.10.0**, which the library asks for. Everything else it asks for was already there at the same or a newer version: annotation, `concurrent-futures-ktx`, `listenablefuture`'s empty stand-in, coroutines, `play-services-wearable` 20.0.1, `org.jspecify`. |
| New tracker-pattern packages (`comm -13 trackers-before trackers-after`, the debug APK) | **None.** The baseline's 12 lines are unchanged: the ten D8 `android.adservices` stubs, `androidx.media3.exoplayer.analytics` and `com.google.firebase` (basement's three exception classes, Ruling 53 item 1). |
| The R8 release dex, read through its mapping | Its tracker scan holds `androidx.media3.exoplayer.analytics` alone, as before. `grep -ci firebase` over the mapping gives 0. |

- **Packages the library added:**
  - In the debug APK ([`debug-packages-added.txt`](2026-11-06-phone-watch-rows/debug-packages-added.txt)): `androidx.wear`, `androidx.wear.core`, `androidx.wear.remote`, `androidx.wear.remote.interactions` and `androidx.wear.utils` (`wear-core`'s `WearApiVersionHelper`).
  - In the R8 release ([`release-packages-added.txt`](2026-11-06-phone-watch-rows/release-packages-added.txt)): those, except `androidx.wear.core`, plus `android.os`.
  - `android.os` holds one class, `android.os.OutcomeReceiver`, which R8 adds as its own stub of that API-31 framework interface. The helper's `RemoteInteractionsManagerCompat` names it, and the phone's minSdk is 26.
  - R8 keeps `RemoteActivityHelper`, its result receiver, `RemoteInteractionsManagerCompat` and `WearApiVersionHelper`.
- **The commands:** `:androidApp:minifyReleaseWithR8` succeeds, with dex SHA-256 `eab04e9ca72435f9…`.
  - The baseline and the after-state used the same commands as plan 2 Task 5 Steps 1 and 5: `dependencies --configuration releaseRuntimeClasspath`, `processReleaseMainManifest`, and `apkanalyzer dex packages` with the same grep.
  - Every release-type task ran with `-Ptaqwa.unsignedRelease=true`. No `keystore.properties` exists in this worktree.
- `:androidApp:lintDebug`: 0 errors, 62 warnings. None is on a file this task touched, and none names the library.

**The `uses-library` line (for the Lead).**
- **Why it is harmless:**
  - `required="false"` filters no device on Play and refuses no install.
  - A phone has no `wear-sdk`, so the element is a no-op on every device the phone app installs on. The phone APK declares no watch feature, so it never runs on a watch.
  - It adds no permission, no component and no network.
  - The Wear app already declares the same library the same way (erratum p5 #7).
- **The question:** it is a manifest addition that is not a component, which plan 2's check 2 does not list. I judged it to pass Mohamed's rule ("no trackers or any unnecessary permissions") and kept the library's own manifest as it is.
- **If the Lead rules otherwise:** one line in `androidApp/src/main/AndroidManifest.xml`, `<uses-library android:name="wear-sdk" tools:node="remove"/>`, makes the phone's merged manifest byte-identical to the baseline.

## Steps 2–5: the tests

**`commonTest` `WatchTargetsTest`** (15 tests, on the host and the simulator):
- The plan's tests: `noWearApiShowsNoRows`, `aWatchWithoutTaqwaOffersTheInstall`, `aWatchWithTaqwaAndNoFaceOffersTheFace`, `aWatchWithTaqwaAndItsFaceShowsNothing`, `aWatchOutOfReachShowsNothing`, `twoWatchesEachGetTheirRow`, `theSectionFollowsTheTargetsOrTheAppleFace`, `theInstallIntentIsThePlayStorePage`, `theFaceIntentIsTheWatchsFaceScreen`, `aSlowNodeQueryNeverHoldsSettings` and `aThrowingSourceIsNone`.
  - `aSlowNodeQueryNeverHoldsSettings`: a fake suspends 10 s, and the answer is `NONE` at exactly 3,000 ms of virtual time. An answer at 2.9 s is taken. Both run on `runTest`'s one scheduler.
  - `aThrowingSourceIsNone` also covers a stray `CancellationException` from inside the source.
- Added: `leavingSettingsCancelsTheLoad`, `aTapIsOpenedOnItsWatchWithItsAction`, `aTapTheWatchNeverAnswersIsUnreachable` (false at exactly 10,000 ms) and `aThrowingOpenIsUnreachable`.

**`androidHostTest` `PlayWatchTargetsTest`** (9 tests), through the real `await()`, with `FakeTask`, `nodeOf` and `capabilityOf`:
- Without Play services, for five `ConnectionResult` codes, there is no Wearable call.
- A 17 is `NONE`, and no capability is asked.
- Any other status is thrown as itself and is `NONE` through `loadWatchTargets`. A cancelled capability task is `TaskCancelled`.
- The rows come from the nodes and the capabilities, which are asked in order: `connectedNodes`, `taqwa_watch`, `taqwa_face_addable`.
- A watch whose face is active is `NONE`. A cloud-only watch asks no capability.
- A tap starts its own intent on its own node. A refused or failed start is false. Without Play services a tap is false and asks nothing.

**`iosTest` `WatchSectionTest`** (8 tests, 10 since review round 1), the composable itself on the simulator:
- No section without a row.
- The install row with the watch's name, under a heading.
- The face row.
- The Apple face alone as the section.
- A tap that reaches the watch: "Continue on your watch" on its own line, in a polite live region, with the row still named after its watch. There is no live region before the tap, and a double tap opens once.
- A tap that cannot reach the watch: "Couldn’t reach your watch".
- Since review round 1: `onA360DpPhoneEachAnswerIsWhole` (both rows tapped, the install row out of reach and the face row reached, on a 360 dp phone at the default text size) and `atTwiceTheTextSizeEachAnswerWrapsAndIsWhole` (300 dp, twice the text). Each label and each answer is read from its own text layout: no line ends in an ellipsis and none overflows its height. At 2× the answer wraps.
- `aTapsAnswerLastsUntilTheRowsAreReadAgain`. A mutant that kept the answer across readings failed this test alone (1 of 8).
- `theRowsAreAskedAgainEachTimeSettingsComesBack`.

**Red first (Step 3):**
- On the host, 17 of 24 failed on their assertions over the skeletons.
- On iOS, 17 of 22 failed.
- The ones that passed are guards the skeleton already met: no Wear API, out of reach, a watch whose face is active, cancellation, and a tap passed through.

**Green (Step 5):**
- The new and the touched tests: `WatchTargetsTest` 15, `PlayWatchTargetsTest` 9 and `WearCodesTest` 7 on the host; `WatchSectionTest` 8, `WatchTargetsTest` 15, `WatchFaceRowTest` 7, **`IosWatchFaceOfferTest` 4** and `LanguageInAppearanceTest` 2 on iOS.
- `scripts/check-strings.sh`: i18n clean.
- **`scripts/test.sh` whole:** exit 0 in 470 s, under `ios-sim-tests`.
  - It ran versions 1.5.0 (41)/(100041), test-bump, the privacy gate (263 rows, 0 awaiting) and the listing.
  - Shared host 2425, shared iOS 2400, watchcore iOS 200, widgetcore 220 and wearApp 586, with 0 failures and no EOFException.
- **`scripts/ios-build.sh`:** BUILD SUCCEEDED three times: plain, with the placeholder, and plain again. `check-watch-bundle` holds every rule (TaqwaWatchWidgets.appex 4412 KB of 5120, TaqwaWatch.app 12012 KB of 15360).

## Step 6: on the paired emulators

**Found:** both running and paired (node `c76e057a` "Taqwa_Wear_OS_6" nearby, `taqwa_watch=true`).
- The watch ran the found app `109521c0` (100041), its Push face 41 `f13548e6` active (Arch, Amber, slots empty), the London feed `183d1d92`, and `face-activation-spent` = "seen 41 active".
- The phone ran Taqwa 1.5.0 (41) debug `6cbda5a6`, London.
- I recorded the watch with `pullfound.sh` and pulled the phone's APK. This tree's `androidApp` debug build (`5f1e6e40`) went on the phone with `install -r`.

| | Expected | Seen | Shot |
|---|---|---|---|
| Found state: the face active, the activation spent | no section | no WATCH section; the phone's Wearable dumpsys lists only `taqwa_watch` for the watch | — |
| **1.** The Wear app uninstalled | "Watch" › "Install on your watch", with the watch's name | **WATCH › Install on your watch, valued "Taqwa_Wear_OS_6"** | [`c1-install-row.png`](2026-11-06-phone-watch-rows/c1-install-row.png) |
| 1. Tap it | the Play Store on the watch with `market://details?id=world.taqwa.app` | the watch's log shows `START u0 {act=android.intent.action.VIEW dat=market://details/... cmp=com.android.vending/com.google.android.finsky.wearmainactivity.WearMainActivity}`, and the phone's companion shows `WearRemoteIntent … startRemoteActivity: Intent { act=VIEW cat=[BROWSABLE] dat=market://details/... } on [c76e057a]`. **Both logs redact the query** (Android's `Intent.toString`), so the query was not seen. `theInstallIntentIsThePlayStorePage` pins it. **Shot again in review round 1's fix:** "Continue on your watch" whole, on its own line under the row's label, and the row still valued "Taqwa_Wear_OS_6". The first build put it in the row's value, where it read "Continue on your w…" | [`c1-after-tap.png`](2026-11-06-phone-watch-rows/c1-after-tap.png), [`c1-watch-play.png`](2026-11-06-phone-watch-rows/c1-watch-play.png) |
| **2.** The found Wear app freshly installed: face 41 from dwf.receiver, not active, the activation unspent | "Add the Taqwa face" | at its start: `TaqwaFace isActive … false`, `capability added INSTALLED`. **WATCH › Add the Taqwa face**, and no install row | [`c2-face-row.png`](2026-11-06-phone-watch-rows/c2-face-row.png) |
| 2. Tap it | `FaceActivity` opens on the watch | **first two taps: "Couldn’t reach your watch"** after the 10 s bound (the finding below); **with the companion awake: `START u0 … dat=taqwa://face/... cmp=world.taqwa.app/.wear.face.FaceActivity`, `Displayed … +44ms`, and "Continue on your watch"**. Its button was not pressed. **Both shot again in review round 1's fix** (`Displayed … +46ms`), each answer whole on its own line. The first `c2-after-tap.png` had been taken while the tap was still being asked, and was the same picture as `c2-face-row.png`. The new one is a tap with the companion cached again (its `REMOTE_INTENT` `DEFERRED for registered`) | [`c2-after-tap.png`](2026-11-06-phone-watch-rows/c2-after-tap.png), [`c2-after-tap-b.png`](2026-11-06-phone-watch-rows/c2-after-tap-b.png), [`c2-watch-face-screen.png`](2026-11-06-phone-watch-rows/c2-watch-face-screen.png) |
| **3.** On the watch: Today's chip, its button, Don’t allow | after the publish, no section | `TaqwaFace capability removed CHOOSE_IT_YOURSELF`; no WATCH section | [`c3-permission.png`](2026-11-06-phone-watch-rows/c3-permission.png), [`c3-denied.png`](2026-11-06-phone-watch-rows/c3-denied.png), [`c3-phone-no-section.png`](2026-11-06-phone-watch-rows/c3-phone-no-section.png) |
| **4.** A fresh install made active by hand (`activate.sh`), the activation unspent | after the publish, no section | `isActive … true`, no capability added (`unchanged 4007`); no WATCH section | [`c4-phone-no-section.png`](2026-11-06-phone-watch-rows/c4-phone-no-section.png) |
| **5.** `emulator-5564`, no Play services | no section, Settings as fast | **not run:** plan 2 never made `Taqwa_Phone_NoPlay` (`emulator -list-avds` has no such AVD). Making it is a download that needs the Lead's OK, and a third emulator would break the map's two-emulator rule. `PlayWatchTargetsTest.withoutPlayServicesNothingIsAsked` holds the rule: no Wearable call at all | — |
| **6.** TalkBack on the phone | the heading, each row with its value, the live result | **Heard in review round 1's fix** (TalkBack's Diagnosis mode on for the turn, then off again): "WATCH. Heading", "Install on your watch. Taqwa_Wear_OS_6", then the answer line as it came, "Couldn’t reach your watch" (the companion cached) and, after the companion woke, "Continue on your watch". A swipe past the row lands on the line, "Couldn’t reach your watch", as a stop of its own ([`tb-2-answer.png`](2026-11-06-phone-watch-rows/tb-2-answer.png), with Diagnosis mode's event overlay). Review round 1's spec lens had heard the first build's row-value answers too. **At the first build: partly seen.** TalkBack (on by `settings`, no consent answered) moved its focus by the console's swipes, and the row was one item, "Location, London" (the merged row). But TalkBack writes no speech log without its Diagnosis mode, and after the first swipe the Accessibility Suite's own "Allow … to send you notifications?" dialog took the screen. It was left unanswered and dismissed, `POST_NOTIFICATIONS` not user-set, and TalkBack turned off again. The words, the heading and the polite live region are held by `WatchSectionTest` on the simulator; the device's speech is left to Task 13 | [`tb-1.png`](2026-11-06-phone-watch-rows/tb-1.png) |

**Finding: a tap can wait on the companion app (for the Lead and Task 13).**
- **What happened:** the first two face taps answered "Couldn’t reach your watch" after the 10 s bound, and the watch opened `FaceActivity` only later.
  - The phone's Wear OS companion, `com.google.android.apps.wear.companion:persistent`, was `CACHED_EMPTY` (procState 19) and frozen. That happened once Settings had been left for the home screen and brought back.
  - Android deferred `RemoteActivityHelper`'s broadcast to it. `dumpsys activity broadcasts` showed `com.google.android.wearable.intent.action.REMOTE_INTENT … DEFERRED for registered` for both taps.
  - When I opened the companion's own screen, the process came out of the cached state (procState 3). Both deferred intents were then delivered at once, and the watch opened `FaceActivity` twice (12:56:32), one to two minutes after the taps.
  - Taps made while the companion was awake reached the watch within a second.
- **Why it may be the emulator:** this pair was made by `adb forward`/`reverse`, with no companion-device association, so nothing keeps the companion alive. On a real phone the pairing normally holds it.
- **What Task 13 should check:** on his S23 with the Galaxy Wearable app, tap both rows after the phone has been idle a while. If a tap there shows "Couldn’t reach your watch" and the watch still opens later, the bound (10 s), or the wording of a late answer, is the Lead's to rule.

**Put back:**
- **The watch:**
  - The found APK went back as a first install. `activate.sh` made Push's face 41 active (favourite 40; Arch, Amber, slots empty).
  - `restore.sh` then ran. Its one difference from found was `auto_time_zone`, because the script sets it to 1 and the found watch had 0, so I set it back to 0 by hand.
  - After that, `state.sh` equals found's, and every `files/` file matches found's sha1 (face-activation-spent `d4f8c9f1`, feed `183d1d92`, delivery `fd0ea56f`). There is no `shared_prefs`.
- **The phone:** its found APK went back by `install -r -d`, with its data kept. TalkBack is off and font scale 1.0. The reinstall sent the watch no feed.
- Both are left running and paired.
- **Left behind:** the companion's and the Accessibility Suite's own notification-permission dialogs, in their background tasks, unanswered.

## Step 7: the iPhone 17 Pro simulator, paired with the Series 12

**Found:** no pair, both Shutdown. The iPhone held the store's Taqwa 1.2.0 (38) (`prev-Taqwa.app`, Doha). Before any change I cloned its bundle, data, group and widget containers (`cp -c`).

**What ran:**
- I made pair `8D5CCFB5` and booted both simulators.
- `scripts/ios-build.sh`'s build of this tree went on: `Taqwa.app` on the iPhone and `Watch/TaqwaWatch.app` on the Series 12.
- Settings was opened by the debug route `taqwa://recite/screen?name=settings`.

| | Log | Shot |
|---|---|---|
| No `.watchface` bundled | `TaqwaWatch face offered=false activated=true paired=true bundled=false` | [`s7-no-face.png`](2026-11-06-phone-watch-rows/s7-no-face.png): no WATCH section |
| A placeholder `Taqwa.watchface` (a text file, this step only, then rebuilt) | `offered=true activated=true paired=true bundled=true` | [`s7-placeholder-row.png`](2026-11-06-phone-watch-rows/s7-placeholder-row.png): **WATCH holding "Add to Apple Watch" alone**, no divider above it |
| A tap (the simulator control tool) | `TaqwaWatch face refused: invalidFile code=2 domain=CLKWatchFaceLibraryErrorDomain` | [`s7-placeholder-refused.png`](2026-11-06-phone-watch-rows/s7-placeholder-refused.png): plan 4's line under the row |
| The placeholder, the watch unpaired | `offered=false activated=true paired=false bundled=true` | [`s7-placeholder-unpaired.png`](2026-11-06-phone-watch-rows/s7-placeholder-unpaired.png): no section |

**After the checks:**
- I deleted the placeholder and ran `ios-build.sh` again. The built app holds no `Taqwa.watchface`, and `git status` shows `iosApp/` clean (the folder holds only its README).
- **Put back:**
  - The pair is unpaired, and both simulators are Shutdown.
  - The store build went back on the iPhone from the clone (app tree `cd3c676c`). Its data and group containers were restored from the clone and equal found's (data `0d22e2cc`, group `afad5ff4`). Its widget container equals found's file for file.
  - The containers took new ids at the install, and the count is 151 data containers, as found.
  - The Series 12 holds no Taqwa app or container.

## Review round 1: the tap's answer was cut

**The finding (Important).** The first build put the answer in `TaqwaRow`'s value slot. That slot is one line with an ellipsis (`TaqwaCard.kt`), and when the label and the value cannot both fit, the label keeps at least 60 % of the row (`LABEL_MIN_SHARE`). So the tap's only feedback read "Continue on your w…" and "Continue on your wat…" in this report's own shots, in English, at font scale 1.0 on a 411 dp phone. A 360 dp phone, a larger text size or the longer drafts (fr "Impossible de joindre votre montre", id "Tidak dapat menjangkau jam tangan Anda", bn, ur) cut it further. The report had said the row showed the whole words.

**The fix: the reviewer's option (a), a line of its own under the row, as plan 4's `WatchFaceRow` already does.**
- `WatchActionRow` draws its `LinkRow` and, once a tap has answered, `RowAnswer(text)` under it.
  - `RowAnswer` is plan 4's failure line lifted out as one function: `SettingsNote` at the row label's 16 dp, 12 dp below, inside one `mergeDescendants` node with a polite live region.
  - `WatchFaceRow` now uses it too, and draws the same as before.
- The row keeps its value, the watch's name, so nothing beside the label moves when the answer comes.
- The live region left the row for the line. That is why `TaqwaRow`'s and `LinkRow`'s `modifier` went back to `eab5c103`.
- **A Lead's call:** Task 6 Step 4 says "as the row's value". Option (b), a value that wraps, would have changed `TaqwaRow` for every row. Option (c), shorter words, cannot fit six languages in what a 360 dp row's label leaves.

**Tests, red first.**
- The two new tests above failed on `0a7f83a5`'s code with `"Couldn’t reach your watch" is cut at the bottom`.
- `aTapThatReachesTheWatchSaysContinueOnItAsItComes` failed too, because the row lost its watch's name once answered.
- WIP `7bc17535`. Green after the fix: `WatchSectionTest` 10, `WatchFaceRowTest` 7, `IosWatchFaceOfferTest` 4, `LanguageInAppearanceTest` 2.
- **`scripts/test.sh` whole: exit 0 in 324 s.** Shared host 2425, shared iOS 2402, watchcore iOS 200, wearApp 586, with 0 failures. Versions 1.5.0 (41)/(100041). Privacy gate 263 approved, 0 awaiting.

**On the paired emulators, with this tree's debug build `db7348d3`:**

| | Seen | Shot |
|---|---|---|
| Check 1's tap, the Wear app uninstalled, the companion awake | the watch started `WearMainActivity` with `market://details/…`; "Continue on your watch" under the row, whole, and the row still valued "Taqwa_Wear_OS_6" | [`c1-after-tap.png`](2026-11-06-phone-watch-rows/c1-after-tap.png) |
| Check 2's tap, the found Wear app as a first install (face 41 not active), the companion awake | `FaceActivity` opened on the watch (`Displayed … +46ms`), its button not pressed; "Continue on your watch" whole | [`c2-after-tap-b.png`](2026-11-06-phone-watch-rows/c2-after-tap-b.png) |
| Check 2's tap with the companion cached (procState 19) | "Couldn’t reach your watch" at 10 s, whole; the tap's `REMOTE_INTENT` `DEFERRED for registered`, and `FaceActivity` opened when the companion woke, then closed with Back | [`c2-after-tap.png`](2026-11-06-phone-watch-rows/c2-after-tap.png) |
| The same answer on a 360 dp phone (`wm size 945x2100` at 420 dpi) at font scale 1.3 | whole, on one line under "Add the Taqwa face"; Recitation's own value is cut as it always is | [`c2-360dp-font-1.3.png`](2026-11-06-phone-watch-rows/c2-360dp-font-1.3.png) |
| TalkBack | check 6 above: the line is said as it comes, from its own node | [`tb-2-answer.png`](2026-11-06-phone-watch-rows/tb-2-answer.png) |

**Put back:** both devices as the turn found them, and left running and paired (the ledger line of the fix round has the detail).
- **The watch:** `activate.sh`, `restore.sh`, then `auto_time_zone` back to 0 by hand. `state.sh` equals found's, and every `files/` file matches found's sha1.
- **The phone:**
  - Its found app is back by `install -r -d`, and `wm size`, font scale and TalkBack (its Diagnosis mode included) are as found.
  - A TalkBack double tap activated the Accessibility Suite's re-shown notification dialog and granted its `POST_NOTIFICATIONS`. It was revoked again, flags cleared.
  - The font-scale change wrote `system_locales`, and it was deleted again.
  - As in the earlier turns, the phone app's watch-feed counters moved (78 to 80, from the fresh watch install's handshakes), and the reinstall rewrote its alarms and widget mirror.

## Errata and rulings

- **Erratum 1:** `git merge --ff-only watch-companion` was already up to date at `eab5c103`.
- **Erratum 2:** the rows sit in 1.4.0's redesigned root, between App (Appearance with the language, Widgets) and About (one row). The section uses that screen's own label, card, divider and row.
- **Erratum 3 (`p5` entries):** none names Task 6. Entry 7's `wear-sdk` (`required="false"`) has a phone-side echo here: the library brings the same line into the phone's merged manifest (Step 1).
- **Erratum 4:**
  - Ruling 39: the rows carry no place. The install row shows the watch's own name, from the system, and no feed field is read.
  - Ruling 55 item 6: no test vector is in any APK; this task added none.
  - No face file changed, so the face tools' `--check` and the always-on lit share are untouched.
  - The style is the phone app's quiet rows: plain values, the link glyph, no badge.
- **Erratum 5:** the version is not bumped. `check-versions` reads 1.5.0 (41)/(100041), and the watch ships as 1.6.0 (42).
- **Erratum 6:** WIP commits were made at red and at green. They are folded into the plan's one commit.
- **Erratum 7:** both emulators were found running and paired, with the Google sign-in in place. Nothing asked for a sign-in. They are left running and paired, and were stopped by nothing.
- **Erratum 8:** no Gradle flake was seen.
- **Question 10** (the library under his rule): built as its default (yes), with the three checks above and the `uses-library` call left to the Lead.
- **Question 11 and the Lead's call 5:** built as their default. The face row can be removed in one commit: `addFace` in `watchTargets`, and the face row in `WatchSection`.

## Left for others

- **The Lead:** the `uses-library` line (Step 1), and the deferred-broadcast finding (Step 6).
- **Task 11:** the native review of the 30 draft lines (five strings in six languages, `values-in` as `values-id`'s twin), and "WATCH" against the plan's "Watch".
- **Task 13:** TalkBack's speech for the section on a real phone (heard on the emulator since review round 1), both taps on his S23 after an idle spell, and check 5 if a phone without Play services is at hand.
- **The Lead:** review round 1's fix places the answer under the row, not in the row's value as Step 4 words it.

## Ruling 72 items 3 and 4 (6 November, Lane P2)

Lane P2 (`watch-phone-fix`, cut from `watch-companion` `6f8e2cb7`; `git merge --ff-only watch-companion` was already up to date), 6 November 2026. Ruling 72 items 3 and 4, test-first, with host and simulator tests only: no device this wave (5558 and 5562 are the observation's).

### Item 3: a tap still on its way says "Continue on your watch"

**The finding it answers.** [Step 6's finding](#step-6-on-the-paired-emulators) above, seen again on the dry-run candidate (P5.12 Step 3): with the phone's Wear OS companion cached, Android held `RemoteActivityHelper`'s `REMOTE_INTENT` (`DEFERRED for registered`) and delivered it 45 s after the tap. The watch then opened the target, but the row had already said "Couldn’t reach your watch" at the 10 s bound. And a second tap queued a second intent, so the watch opened `FaceActivity` twice when the companion woke (12:56:32 above).

**What changed** (`shared/.../watchfeed/WatchTargetsSource.kt`, `feature/settings/WatchRows.kt`):
- `openOnWatch(source, target, action, bound = 10.seconds, requests)` now answers in three ways, as a `Flow<WatchOpen>`:
  - `OPENED`: the companion took it within the bound. "Continue on your watch".
  - `QUEUED`: the bound ran out with the request still on its way. "Continue on your watch" too, at the bound.
  - `UNREACHABLE`: a refused or failed start (`RemoteIntentException`), a throw, or no watch to start it on (the iPhone's source, a phone without Play services). "Couldn’t reach your watch".
  - After `QUEUED` it follows the request to its end, so a companion that takes it late and then refuses still says so.
- **The bound no longer cancels the request.** It only decides when the row answers. `RemoteActivityHelper` has no bound of its own, and cancelling its future never recalled the broadcast anyway: concurrent-futures' `await()` cancels the future, while the system keeps the intent.
- **One request per row on its way.** `WatchRequests` holds, for each watch and action, the request still on its way. A tap while one is on its way joins it and sends nothing more. `WatchRequests.process` runs each request on a scope of its own (a `SupervisorJob`, `Dispatchers.Unconfined`, so it starts on the tap's thread as before). So a request outlives the bound, the row, a re-reading of the rows and the screen that sent it, as the system's copy of it does. Leaving Settings cancels a tap's wait, never its request.
- **The row** keeps a tap "asking" until its request ends, `QUEUED` included, and a tap meanwhile starts nothing. After a re-reading (Settings brought back), a tap joins the request still on its way.
- No new words: the two existing strings, no string file or draft marker touched.

**The iPhone's "Add to Apple Watch".** Checked, and it has no such semantic. `WatchFaceRow` hands the file to `offer.add`, and Swift's `WatchFaceShare.add` calls `CLKWatchFaceLibrary.addWatchFace(at:)`, whose completion comes when the system's own sheet is answered. No timer, no bound, and nothing is called a failure while the sheet is up, which covers the row. So nothing changed there, and no Swift file was touched.

**Tests, red first.**
- `commonTest` `WatchTargetsTest` (run on the host by `:shared:testAndroidHostTest`; 21 tests, 9 of them new or rewritten for the tap):
  - `aTapTheWatchTakesContinuesOnIt`, `aTapTheWatchRefusesIsUnreachable`, `aThrowingOpenIsUnreachable` (a stray `CancellationException` from inside the source included);
  - `aTapStillWithTheCompanionAtTheBoundContinuesOnTheWatch`: `QUEUED` at exactly 10,000 ms, then `OPENED` at 45,000 ms, the request delivered;
  - `aTapTheCompanionNeverAnswersStaysOnItsWay`: `QUEUED` at exactly 10 s;
  - `aQueuedTapTheCompanionThenRefusesSaysSo`: `QUEUED`, then `UNREACHABLE` at 45 s, for a refusal and for a throw;
  - `aSecondTapWhileTheFirstIsPendingSendsNothingMore`: a second tap at 12 s sends nothing, waits its own 10 s and takes the first's answer; once the first has ended, a tap is sent;
  - `aTapForAnotherActionOrWatchIsSentBesideOneOnItsWay`;
  - `leavingSettingsLeavesTheRequestOnItsWay`: the tap's wait cancelled at 2 s, the request delivered at 45 s, and a tap after coming back sends nothing.
  - These replace `aTapIsOpenedOnItsWatchWithItsAction`, `aTapTheWatchNeverAnswersIsUnreachable` and the old `aThrowingOpenIsUnreachable`.
- `iosTest` `WatchSectionTest` (the composable on the simulator; 12 tests, 2 new):
  - `aTapStillOnItsWayAtTheBoundSaysContinueAndSendsNothingMore`: "Continue on your watch" at the 10 s bound, in its polite live region, and a tap then sends nothing;
  - `aTapAfterSettingsComesBackJoinsTheOneStillOnItsWay`.
- **Red first,** over a skeleton that gave the new types the old two-way answer:
  - on the host, 5 of `WatchTargetsTest`'s 21 failed on their assertions: the bound answered `UNREACHABLE` (four tests), and a second tap sent again (`expected:<1> but was:<2>`). The ones that passed are the rules the old code already met: a taken tap, a refusal, a throw, another row sent beside one on its way;
  - on the simulator, 2 of `WatchSectionTest`'s 12 failed: no "Continue on your watch" at the bound, and a tap after Settings came back sent a second request.
- **Green:** `WatchTargetsTest` 21 on the host and on the simulator; the host's `watchfeed` and `feature.settings` tests 461 with 0 failures; the simulator's `feature.settings` tests and `WatchTargetsTest`, 272.
  - One simulator test then still failed on its clock, not its rule: CMP 1.12.1's `FlushCoroutineDispatcher` runs a composition's `delay` on the test clock but has no `invokeOnTimeout`, so `withTimeoutOrNull`'s bound runs in real time there (and in the app). The test now waits for the real 10 s (`WatchSectionTest` 12 of 12, that test about 9.4 s). It is the only test of the row that waits that long.

**On his S23 (Task 13).** No device this wave. Task 13 checks it with the companion idle: after the phone has been left a while, tap each row. Expected: "Continue on your watch" at most 10 s after the tap, the watch opening the Play Store or the face's screen when the companion wakes, and a second tap meanwhile opening nothing more. Only a failed start says "Couldn’t reach your watch".

### Item 4: why `emulator-5558` took only odd deliveries

P5.12 Step 3 (Lane W, `56b1256f`) saw the watch accept deliveries 1, 3, 5 and 7 only. Lane W guessed that the even ones went to Step 1's ghost watch `5a3042ca`. **They went to no watch: each even number was set aside by a forget, and never sent. It is by design** (Ruling 50 item 6, amendment A2, Ruling 52 item 2). No code changed for it.

**Why not the ghost.** Two things rule it out.
- The phone sends only to directly connected watches. `AndroidWatchLink.send` takes `nearby()` (`WatchLink.android.kt:144`), which is `directlyConnected(…)` (`:173`, `:27`). The ghost was `isNearby false`, 2 hops through the cloud, in the phone's own node table.
- A send is never split between watches. One send writes one number down (`WatchFeedSync.deliver`), and that number's path goes to every nearby watch (`:146`). Two watches get the same numbers, never alternate ones.

**What the logs show.** The phone's lines ([`r72-step-3-phone-log.txt`](2026-11-06-phone-watch-rows/r72-step-3-phone-log.txt), excerpted from Lane W's phone logcat; the phone's clock is about 0.6 s ahead of the watch's) beside the watch's (`2026-10-24-watch-gates/step-3/logs.txt`, sections 5 and 8, in `56b1256f`):

| Phone (`TaqwaWatch …`) | Watch (`TaqwaWearFeed …`) | The record (`WatchFeedRecord`) |
|---|---|---|
| 20:25:29.319 `WATCH_APPEARED SENT` (the capability change at the install) | 20:25:28.742 `ACCEPTED 1` | a fresh phone: the send takes 1 |
| 20:25:29.463 `WATCH_ASKED SENT` | 20:25:28.729 `ASK STARTED sent`, before 1 landed; 28.757 `ACCEPTED 3` | the handshake's echo is empty (`PhoneAsk.payload`: no feed held), so `echoed` forgets: **2 set aside**; the send takes 3 |
| 20:25:29.463 `ACK 1`, 29.521 `ACK 3` | `ACK 1 sent`, `ACK 3 sent` | 1 confirmed (below the latest), then 3: the latest |
| 20:30:53.072 `WATCH_APPEARED UNCHANGED` (the reinstall's capability change) | the app uninstalled 20:30:37 and installed 20:30:48: its files, the feed among them, gone | 3 is confirmed for that node, and only the watch can say it was wiped |
| 20:31:03.396 `WATCH_ASKED SENT`, 03.407 `WATCH_ASKED SENT` | 20:31:02.662 `ASK STARTED sent` and `ASK OPENED sent`, in the same millisecond, before anything landed; `ACCEPTED 5`, `ACCEPTED 7` | two empty echoes: **4 set aside**, 5 sent; **6 set aside**, 7 sent |
| 20:31:03.461 `ACK 5`, 03.464 `ACK 7` | `ACK 5 sent`, `ACK 7 sent` | 7 confirmed |

Four asks sent and three empty handshakes forgot: 1 to 7, with every even number a forget's. No `TOO_LARGE` second send was involved: the Android link never answers it.

**Why a forget sets a number aside.** An empty echo means the watch holds nothing now, so whatever the phone had confirmed is void. If the forget only cleared the confirmation, an acknowledgement the watch sent before it was wiped could still arrive and name the latest number, and the phone would answer `UNCHANGED` to an empty watch. Setting the next number aside means no echo sent before the forget can name the latest number again (`WatchFeedRecord.forgotten`'s KDoc, `WatchFeedNamedCasesTest.aLateAckFromBeforeAWipeConfirmsNothingOnceTheWatchSaidItHoldsNothing`). Numbers are cheap, and never given back.

**The replay test.** `AndroidWatchLinkTest.stepThreesOddDeliveriesAreTheNumbersAnEmptyWatchsHandshakesSetAside` plays Step 3 through the real `AndroidWatchLink`, `WatchFeedSync` and record, with its node table: 5558 nearby and the ghost cloud-only. It ends with 1, 3, 5 and 7 sent to 5558 alone, nothing to the ghost, and `lastSeq` 7.
- It passed when first run, as a test of designed behaviour should.
- Its teeth: with a `forgotten()` that sets no number aside, it fails with 1, 2, 3 and 4 sent, and so does the two-watch test (1 then 2). The file was put back.
- `twoWatchesOnOnePhoneTakeEveryNumberTogether` holds the two-watch case below.

**What a real user with two watches on one phone sees.** Nothing of the numbers.
- Both watches, while directly connected, get every send under the same number, so each holds the phone's feed.
- When one of them is new, reinstalled or cleared, its first handshake says it holds nothing. The phone sets a number aside and sends the feed to both under the next one. The other watch takes the same feed one number on, acknowledges it, and its complications are asked again with the same times, so nothing on its face changes.
- The skipped numbers show only in the watch's `ACCEPTED n/…` log lines.
- The cost is one more message to each nearby watch per empty handshake: two at a fresh install that starts and opens at once, as Step 3's second round did. That is within Ruling 50's "one more send, never a wrong `UNCHANGED`".
- A watch reached only through the cloud, like the ghost, gets nothing until it is directly connected again. Then its capability change or its handshake sends it the feed (`aWatchReachedOnlyThroughTheCloudIsNotSent`, `aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake`).
- Plan 2's known limit for two watches stays as it was: a send lost to one watch while the other acknowledges is healed at the first one's next handshake (`aWatchThatMissedTheFeedTheOtherTookIsSentItAtItsHandshake`).

**For Step 2's reading.** The observation reads the phone's sends and the watch's acceptances. A gap of one between two accepted numbers is a number a forget set aside, not a lost send, when the phone served a forget between them: a handshake from a watch that held nothing (the watch's `ASK … sent` with no feed stored), a `WATCH_CHANGED`, or an echo of another install's.

### Errata and rules for this part

- **Plan errata (`p5`):** none of the 30 entries names Task 6, the phone's rows or the delivery numbers (entry 26 is the App Store notes' Settings path). None applied.
- **No new words.** No string resource and no draft marker was touched, nothing in `watch-feed`.
- **Ruling 39:** nothing here reads or logs a place. The rows show the watch's own name, as before.
- **Ruling 55 item 6:** no test vector in any APK; the new tests are host and simulator tests.
- **No face file changed,** so the face tools' `--check` and the always-on lit share are untouched.
- **The version** stays 1.5.0 (41)/(100041); the watch ships as 1.6.0 (42) at the Lead's cut.
- **Devices:** none. Simulator runs only under `ios-sim-tests` (both names), each in the ledger. `KB2604110095`, 5558, 5562, 5566, 5568, Dev 1's 5572, the iPhone 17 Pro Max simulator and the work iPhone 12 were not touched, and adb was not run.
- **The checks,** at WIP `43209a05` under `ios-sim-tests` (21:20:16 to 21:28:53):
  - `scripts/test.sh` whole: exit 0 in 448 s. Versions 1.5.0 (41)/(100041), test-bump, the privacy gate (263 rows, 263 approved, 0 awaiting), the listing, `swift test`, the timetables compile, then shared host 2435, shared iOS 2411, watchcore 233/200, widgetcore 111/109 and wearApp 591, with 0 failures and no EOFException.
  - `scripts/ios-build.sh`: BUILD SUCCEEDED in 69 s; `check-watch-bundle` holds every rule (TaqwaWatchWidgets.appex 4416 KB of 5120, TaqwaWatch.app 12036 KB of 15360).
  - `scripts/check-strings.sh`: i18n clean. `:androidApp:lintDebug`: 0 errors, 62 warnings (as at Task 6), none on a file this touched.

### Left for others

- **Task 13, on his S23 with the companion idle** (item 3): after the phone has sat a while, tap each row. Expected: "Continue on your watch" by the 10 s bound, the watch opening the Play Store or the face's screen when the companion wakes, and a second tap meanwhile opening nothing more. "Couldn’t reach your watch" only for a start that failed.
- **P5.12 Step 2 and its report** (item 4): read gaps of one in the accepted numbers as forgets, as above.

## Ruling 74 items 9–15 (6 November, Lane P3)

Lane P3 (`watch-phone-fix`; `git merge --ff-only watch-companion` moved it from `148ac12b` to `ce3f9acd`, Wave 8's head), 6 November 2026. Ruling 74's phone items, Wave 8's carried minors, test-first, with host and simulator tests only: no device this wave (5558 and 5562 are the observation's). The WIPs `af3a6aa6`, `a45e8e70` and `c3912727` (the tested tree, which differs from the commit only in this report) are folded by a soft reset into one commit, "Phone: … (Ruling 74)". Taqwa identity, no trailer, nothing pushed, version not bumped.

### Items 9–11: a request on its way expires, and an ended one is let go

**What changed** (`shared/.../watchfeed/WatchTargetsSource.kt`):
- **The expiry (item 9).** `WatchRequests.EXPIRY` is 2 minutes, a named constant whose KDoc says why: it covers what was seen of a companion that holds a request (Android delivered its DEFERRED `REMOTE_INTENT` 45 s after the tap at P5.12 Step 3, and in wave 7 a cached companion answered within seconds of the 10 s bound), and it ends a request the companion never answers, so the row is never dead for the rest of the process.
  - Until then a tap joins the request and sends nothing. From the expiry on, a tap sends anew. A second `FaceActivity` is better than a dead row.
  - A tap that follows a request stops following it at the expiry. It says nothing more, so the row keeps "Continue on your watch", and the row takes a tap again.
  - An expired request is let go, not cancelled. Cancelling recalls nothing the system holds, and an answer it still gets reaches no row.
- **The bound counts from the request's sending (item 10).** Each request carries its sending's `TimeMark` (`WatchRequests.Request`, `age` and `left`). A tap waits `bound - age` for the answer.
  - So a tap that joins a request already past the bound says "Continue on your watch" (`QUEUED`) at once.
  - **One choice the ruling left open:** a tap that joins a request before its bound says it with the first, at the first's bound, not a fresh 10 s after its own tap. One request, one bound. It removes the step a fresh bound made: a join at 9.9 s waited until 19.9 s, and one at 10.1 s answered at once.
- **Ended requests leave `onTheirWay` (item 11).** Each request has one watcher in the requests' own scope: it waits for the request to end, however it ends, or for its expiry, whichever comes first, and then takes the request out of the map. It takes out only that request, never a newer one a tap put in its place after the expiry.
  - A join also checks the request is still running and not expired, so a watcher that has not run yet never makes a tap join a request that is over.
  - `rowsOnTheirWay()` reads the rows (watch and action) held, under the lock. The tests read it.
- `WatchRequests` takes the clock the bound and the expiry count on (`TimeSource.Monotonic` in the app, the test scheduler's `timeSource` in `WatchTargetsTest`) and the expiry (`EXPIRY` in the app).

**Tests, red first** (`commonTest` `WatchTargetsTest`, on the host's virtual time; 24 tests, 3 new, 2 rewritten):
- `aRequestOnItsWayExpiresTwoMinutesAfterItWasSent` (new): a companion that never answers. The first tap says `QUEUED` at 10 s and still follows at 119,999 ms. A tap then joins, sends nothing, and says `QUEUED` at once. At 120,000 ms both taps have ended, and a tap is sent anew.
- `aRequestLeavesTheRequestsOnTheirWayWhenItEndsOrExpires` (new): the row is held while the request is on its way, and let go once it is taken, refused or thrown on, and at exactly 2 minutes for one never answered.
- `aTapJoiningBeforeTheBoundAnswersAtTheRequestsBound` (new): a tap 4 s after the first says `QUEUED` 6 s after its own tap, with the first.
- `aSecondTapWhileTheFirstIsPendingSendsNothingMore` (rewritten): the second tap, at 12 s, now says `QUEUED` at 0 ms, then `OPENED` with the first.
- `leavingSettingsLeavesTheRequestOnItsWay` (rewritten): the tap after coming back, at 2 s, says `QUEUED` at 8 s, the request's bound.
- **Red,** over a skeleton that only added the constructor's parameters, `EXPIRY` and `rowsOnTheirWay()`: 5 of 24 failed, each on its assertion:
  - the first tap still following at the expiry;
  - joins at 12 s, 2 s and 4 s waiting a fresh 10 s (`expected:<[(QUEUED, 0), (OPENED, 15000)]> but was:<[(QUEUED, 10000), (OPENED, 15000)]>`);
  - an ended request still held (`taken: an ended request is let go expected:<[]> but was:<[(4a1f, ADD_FACE)]>`).
- **Green:** 24 of 24 (WIP `af3a6aa6`).

### Item 12: each row test has its own requests and a short bound

- `WatchSection(source, offer, requests = WatchRequests.process, bound = 10.seconds)` now passes both to each `WatchActionRow`, which hands them to `openOnWatch`. `SettingsRootScreen`'s `WatchSection()` is unchanged, and so is what it does.
  - `WatchActionRow` is private and always given both, so the defaults are `WatchSection`'s alone.
- `WatchSectionTest` (`iosTest`, the composable on the simulator; 13 tests, 1 new):
  - every test's section gets requests of its own (`Dispatchers.Unconfined` and a `SupervisorJob`, as the app's) and a 2 s bound;
  - the two tests that wait for the bound give it 300 ms. No test shares `WatchRequests.process` or waits the real 10 s;
  - the two tests that had a watch of their own (`r72-bound`, `r72-back`) to keep clear of the process-wide requests use the class's `galaxy` again;
  - `aTapAfterTheRequestExpiredSendsAnew` (new, item 9 at the row): requests that expire after 1.5 s, a companion that never answers. "Continue on your watch" at the 300 ms bound; a tap before the expiry sends nothing; past it the line stays, and a tap sends a second request.
- **Red,** over a `WatchSection` that took both parameters and dropped them: 3 of 13 failed:
  - the bound test, at the real 10 s bound (`Condition (the answer at the bound) still not satisfied after 5000 ms`);
  - `aTapAfterSettingsComesBackJoinsTheOneStillOnItsWay` and `aTapAfterTheRequestExpiredSendsAnew`, whose taps sent nothing: they joined a request the failed bound test had left in `WatchRequests.process`. That is the sharing item 12 ends.
- **Green:** 13 of 13, with `WatchTargetsTest` 24 of 24 on the simulator (exit 0 in 116 s; the suite took 7.5 s in all, and its per-test times are the simulator's batched output, not each test's).
  - The mutant "a tap follows past the expiry" fails `aTapAfterTheRequestExpiredSendsAnew` alone (1 of 13): the row was still asking. The file was put back.
  - WIP `c3912727`.

### Item 13: a source resumes where its answer comes

`WatchTargetsSource.open`'s KDoc now says it runs as `WatchRequests`' request, on no thread of its own: it starts on the tap's thread, and after its first suspension it may resume on any thread, where its answer came. For `PlayWatchTargets` that is a binder thread, where `RemoteActivityHelper`'s result receiver answers. So past that point a source must not touch UI state; the row takes the answer on its own thread. `WatchRequests.process`'s and `PlayWatchTargets`' KDocs point to it. No code changed for it: `PlayWatchTargets.open` only returns true or false after its `await()`.

### Item 14: the report's line 17, its known limits, and the garbage-collected path

- **Line 17** ("What was built") said `openOnWatch` "is false on a timeout or a throw", which Ruling 72 item 3 had made untrue. It now says the bound was first built that way, and points to Ruling 72's three answers, to this part and to the known limits.
- **The known limits** close this report ([Known limits](#known-limits)): a process killed while a broadcast is deferred can send twice, the 2-minute expiry, and how a companion that never answers ends.
- Writing the last of those found that it was not yet true:

**The finding.** Ruling 74 item 14's known limit says a companion that never answers can end "earlier through `CallbackToFutureAdapter`'s garbage-collected path as UNREACHABLE". On the base it did not.
- `RemoteActivityHelper`'s future is a `CallbackToFutureAdapter` future. Its completer is held only by the helper's result receiver, which the companion's side holds.
- Let go unanswered, the completer is finalised, and the future fails with `FutureGarbageCollectedException` (concurrent-futures 1.2.0, `CallbackToFutureAdapter.java:332-344`).
- That class extends `Throwable`, not `Exception` (`:356`). `await()` throws it as itself.
- `PlayWatchTargets.open` caught `Exception`, and so does the common `answerOf`. So the throw went through the request and out of the row's tap, uncaught, into Settings' coroutine scope: a throw where the row should have said "Couldn’t reach your watch".

**The fix** (`androidMain/.../watchfeed/PlayWatchTargets.kt`): `open` catches `Throwable`, rethrows the VM's own `Error`s, lets the caller's own cancellation pass as before, and answers false for the rest. The KDoc names the path.

**The test,** with the real adapter and a real collection (`androidHostTest` `PlayWatchTargetsTest.aTapWhoseAnswerIsCollectedUnansweredIsFalse`): the source's `remote` awaits a `CallbackToFutureAdapter.getFuture` whose completer nothing keeps, and the test calls `System.gc()` until the tap ends (within 30 s).
- **Red** on the base `open`: `expected:<Success(false)> but was:<Failure(androidx.concurrent.futures.CallbackToFutureAdapter$FutureGarbageCollectedException: The completer object was garbage collected …)>`, after 63 ms.
- **Green** with the fix: `PlayWatchTargetsTest` 10 of 10, `WearCodesTest` 7 of 7 (the package's calls still show nothing). WIP `a45e8e70`.

### Item 15: the S23 check in plan 5

Plan 5 Task 13 Step 2's section 2 (Settings › Watch) gains the check, and only that line of the plan changed: with the S23's Wear OS companion idle, each row says "Continue on your watch" within 10 s of the tap; a second tap while the first is still on its way (before the watch opens it, and within 2 minutes of the first) opens nothing more; the watch opens the target once, when the companion wakes. Once the watch has opened it, or the line says "Couldn’t reach your watch", or 2 minutes after the first tap, a tap sends anew, so the watch opening the screen again for that tap is right. It is where a late answer (`QUEUED`, then `OPENED`, or `UNREACHABLE`) is first seen on a device, and he records which.

**Since Ruling 76 item 11 (7 November).** The check above is as this lane left it. Plan 5 now words it so he can run it: the screen kept on and Settings not left, one row at a time, the second tap made at once and read against the companion's answer rather than the watch's screen, and what he records is "the line stayed and the watch opened" or "the line turned to Couldn’t reach your watch", since `QUEUED` and `OPENED` draw the same line.

**Since that item's review round 1 (7 November).** That wording had two faults, both found by reasoning from the code, with no device run, and both are fixed in the plan alone.
- **A line before the bound is the companion's answer.** An answer inside the 10 s emits and ends the flow (`WatchTargetsSource.kt:188-190`), so the row takes a tap again (`WatchRows.kt:171`), and the next tap is sent anew (`WatchTargetsTest.kt:331`). "Tap it again at once" therefore failed a correct build whenever the companion was awake, which this report's Step 6 finding calls the normal case on a real phone. The check now goes by when the line shows:
  - Well before 10 s, he does not tap again. He goes idle for longer and runs the row once more, and if the line comes early again he records "no late answer seen: the companion stayed awake". That record fails no build, and it is not a pass of the late answer.
  - At about 10 s is the run the check is for, with the second tap and the two records Ruling 76 item 11 names.
  - The two records stay as the ruling words them. The new records cover runs that are not late answers: an early line, a first line of "Couldn’t reach your watch", and neither result in 2 minutes.
- **The check never named each row's state.** The rows exclude each other on one watch, and check 1's activation ends the face row. So the face row now runs inside check 1, before Today's chip. The install row runs at check 9's reinstall, between the uninstall and the install. He presses Back on the watch each time.
- **Later candidates:** those two runs are C's alone. A later candidate's S23 reads no section with the face active.
- **Not measured:** the 30 minutes and the 2 hours of idle are first guesses, since nobody has seen when an S23's companion goes cached.

**Since review round 1.** The check first said "a second tap within 2 minutes of the first opens nothing more", as item 15 puts it for short. That holds only while the first is still on its way. The watch opening it ends the request: the row takes a tap again (`WatchRows.kt:172`), the request leaves `onTheirWay` (`WatchTargetsSource.kt:126`) and no longer passes the join's test (`:112`), so the next tap is sent, as `aSecondTapWhileTheFirstIsPendingSendsNothingMore` asserts (`WatchTargetsTest.kt:321`). Done in its written order, the opening noted and then a second tap, the check would have failed a correct build. It now follows item 9, whose request is a pending one, and item 15 is read with it: no ruling changes, and no code.

### Errata and rules for this part

- **Plan errata (`p5`):** none of the 30 entries names Task 6, the phone's rows or Task 13 Step 2 (entry 6 is Task 13 check 4, the watch's screen readers). None applied.
- **No new words.** No string resource and no draft marker was touched, and nothing in `watch-feed` or `watch-wear`.
- **Ruling 39:** nothing here reads or logs a place. The rows show the watch's own name, as before.
- **Ruling 55 item 6:** no test vector in any APK; the new tests are host and simulator tests.
- **No face file changed,** so the face tools' `--check` and the always-on lit share are untouched.
- **The version** stays 1.5.0 (41)/(100041); the watch ships as 1.6.0 (42) at the Lead's cut.
- **Devices:** none. Simulator runs only under `ios-sim-tests` (both names), each in the ledger. `KB2604110095`, 5558, 5562, 5566, 5568, Dev 1's 5572, the iPhone 17 Pro Max simulator and the work iPhone 12 were not touched, and adb was not run.
- **The checks:** at WIP `c3912727`, `ios-sim-tests` held 23:34:41 to 23:43:51:
  - `:shared:testAndroidHostTest --tests 'world.taqwa.app.watchfeed.*' --tests 'world.taqwa.app.feature.settings.*' --rerun-tasks`: 465 tests in 48 classes, 0 failures (Lane P2's 461, and the 4 new).
  - `scripts/test.sh` whole: exit 0 in 457 s. Versions 1.5.0 (41)/(100041), test-bump, the privacy gate (263 rows, 263 approved, 0 awaiting), the listing, `swift test` (WatchCore 286, PhoneHarness 19, WatchViews 6), the timetables compile, then shared host 2440, shared iOS 2416, watchcore 233/200, widgetcore 111/109 and wearApp 601, with 0 failures and no EOFException. No flake re-run was needed.
  - `scripts/ios-build.sh`: BUILD SUCCEEDED in 57 s; `check-watch-bundle` holds every rule (TaqwaWatchWidgets.appex 4416 KB of 5120, TaqwaWatch.app 12036 KB of 15360). Only the usual framework warnings.
  - `scripts/check-strings.sh`: i18n clean. `:androidApp:lintDebug`: 0 errors, 62 warnings (as at Task 6), none on a file this touched. `git diff --check` clean.

### Left for others

- **Task 13, on his S23** (item 15): the idle-companion check in plan 5 Task 13 Step 2, the late answer's first device evidence.
- **The Lead:** the joins before the bound (item 10's open case, above) are this lane's choice; the garbage-collected path's fix is beyond the letter of items 9–15, made because item 14's known limit was not yet true.

## Ruling 76 items 8-12 (7 November, lane `watch-phone-fix`)

The Lead's ruling of 03:20 SAST closes Wave 9's phone minors. No device, no visible word. Every test here passes on the real code, and each was first seen to fail as named.

- **Item 8: one named constant.** `WatchRequests.BOUND` holds the 10 s, beside `EXPIRY`. `openOnWatch` and `WatchSection` both default to it, and `WatchRows.kt`'s KDoc names it. `WatchTargetsTest.theBoundIsTenSeconds` pins it. **Red:** first as a compile failure, the constant not there yet; then with `BOUND = 60.seconds`, that test and seven others. **Not held:** a mutant of `WatchSection`'s own default (a literal in place of the constant). No test can reach the composable's default without waiting the real 10 s, so that line is read, and the S23 check is what would see it.
- **Item 9: the surviving mutants.** One mutant at a time, on the host:

| Mutant | Fails |
|---|---|
| The join without `left.isPositive()` | `aTapAtTheExpiryIsSentAnewAndTheFirstsWatcherLeavesItAlone` |
| The watcher without its identity check | the same test, at its `rowsOnTheirWay()` |
| The join without `answer.isActive` | `aRequestOverAndNotYetLetGoIsNotJoined` |
| `open` without `if (e is Error) throw e` | `anErrorOfTheVmIsNoAnswer` |
| `open` without its `ensureActive()` | `aCancelledTapHasNoAnswerAndAStrayCancelIsFalse` |

  In the app, the gap `aRequestOverAndNotYetLetGoIsNotJoined` holds is a tap on the main thread while the answer's thread is between the answer ending and its watcher running. On the test's scheduler a finished answer and its watcher run in one `runCurrent`, so the test ends its request with a cancel, on a scope that dispatches.
- **Item 10: the expiry in `WatchSectionTest` is 3 s.** The tap before it now has about 2.7 s past the bound, in real time, where it had 1.2 s. Three runs of `WatchSectionTest` on the simulator, each with a temporary edit put back after it: a 2 s stall before that tap, on the base's 1.5 s expiry, fails it ("a tap before the expiry sent nothing. Expected <1>, actual <2>"), so the fault was real; the same stall on 3 s passes, 13 of 13; and on 3 s, with a mutant that ignores the expiry, the test still fails ("the tap after the expiry sent anew"), so it still holds what it is for. It costs the test 1.5 s more of real time.
- **Item 11: the S23 check is runnable.** Plan 5 Task 13 Step 2's section 2 ([Item 15](#item-15-the-s23-check-in-plan-5), above).
- **Item 12: wording.** Known limits gain the uptime clock and the finalizer thread, below. `PlayWatchTargets.kt`'s KDoc names the finalizer's thread and no longer stops short mid-sentence. `WatchSectionTest`'s `requests()` says the scope is the test's own. `rowsOnTheirWay()` says only the tests read it. `PlayWatchTargetsTest` names its dependence on JVM finalization (JEP 421). The new comment lines that ran past 100 columns are wrapped.

## Live rows (9 November, lane `w44-rows`)

Lane `w44-rows` (`watch-44-rows`, cut from `watch-companion` `75fd197f`, the 1.6.0 (43) candidate), 9 November 2026, for 1.6.0 (44). Test-first, with host and simulator tests only: no device, no adb, no emulator. Commits `c18be60d` (common and Android) and `c2c5721c` (iPhone), then this report and the plan's sentences. Taqwa identity, no trailer, nothing pushed, version not bumped.

### The report and its cause

**Mohamed, 9 November:** on his S23 he tapped Settings › Watch's "Install on your watch". Once Taqwa was installed on his Galaxy Watch the row still showed the same thing, and only after he left Settings and came back did it offer the face. He asked for the rows to follow by themselves: the install row going and "Add the Taqwa face" coming when the watch app is installed, that row going when the face is added, and the section hiding once nothing is left to offer, on the iPhone with the Apple Watch too.

**The cause, confirmed in the code.** `WatchSection` read the rows only inside `repeatOnLifecycle(RESUMED)` (`WatchRows.kt:86-91` on `75fd197f`). A row's tap opens the watch's Play Store or face screen through `RemoteActivityHelper`, and the phone's Settings stays resumed throughout, so nothing read the rows again until it was left and brought back. The iPhone's offer was asked at the same moment only.

### What changed

**While Settings is resumed, the rows are also read in place** (`WatchRows.kt:117-138` since review round 1). On every `RESUMED`, as before, the rows are read, `reading` moves on, and each row's tap answer starts afresh. Then, until Settings leaves `RESUMED`, the rows are read again at each word from `watchRowsChanged` (`:419`), which merges three things, and since review round 1 a fourth, the end of a hold on the install row (below):
- **Android's watch side** (`WatchTargetsSource.changes()`, `PlayWatchTargets.kt:127-163`): one `CapabilityClient.OnCapabilityChangedListener`, added with `addListener(listener, name)` for `taqwa_watch` and for `taqwa_face_addable`, removed from both when Settings stops collecting, however that ends. Once both are added it says one change of its own, for a change that came while they were being added ([Review round 1](#review-round-1-the-moment-with-no-section-and-the-listeners-first-word-lane-w44-rows-fix)). `taqwa_watch` is the watch app's static capability (`wearApp/src/main/res/values/wear.xml`), so it comes with the install itself. `taqwa_face_addable` is published by the app once it runs, and removed when the face is taken (below).
- **The iPhone's session** (`WatchFaceOffer.changes()`), below.
- **A tap's follow** (`TapFollow`, `:310`): after each row's tap, a reading every 5 s for 3 minutes, a tap meanwhile starting the 3 minutes again. It is the bounded fallback the task asked for "if a listener cannot be relied on", and it runs only while Settings stays in front.

**Readings in place:**
- They are part of the resume's reading: `reading` does not move, so a row's "Continue on your watch" or "Couldn’t reach your watch" stays while that row stays (`WatchActionRow`'s `remember(source, target, reading)`). A row that goes takes its line with it, and a row that comes is fresh.
- One that fails or passes its 3 s bound keeps the rows as they were (`loadWatchTargets`'s new `otherwise`, the rows shown). Only an answer changes them, and no row is an answer: the watch took its face, or went out of reach. The resume's reading still turns a failure into no row, as before.
- One that takes rows with an install row to no row at all is held for up to 20 s (`InstallHold`, [Review round 1](#review-round-1-the-moment-with-no-section-and-the-listeners-first-word-lane-w44-rows-fix)): a watch that has just got Taqwa answers no row until its app has run and published its face, and the section would otherwise go and come back.
- Several words before a reading is done are one reading after it (conflated), so readings never queue behind a slow one.
- A source or an offer whose listening throws says nothing more and ends nothing (`catch`), so no throw from Play services reaches Settings' composition.

**Android, what `WatchSection` promised, kept:**
- **Off the main thread:** each reading runs on `Dispatchers.Default`, as the resume's does, and the listening is collected on `Dispatchers.Default` too (`listenOn`), where Play services' availability is asked and the listener added.
- **The 3 s bound:** each reading in place goes through `loadWatchTargets`, bounded at 3 s.
- **The taps' answers tied to their reading:** above. Taps, requests, the 10 s bound and the 2-minute expiry are untouched (Ruling 72 item 3, Ruling 74 items 9–12, Ruling 76 item 8).
- **No Wearable client before Play services is known to be there:** `changes()` asks `isGooglePlayServicesAvailable` first, as `targets()` does, and without it ends with no call at all (`withoutPlayServicesNothingIsListenedTo`).
- **No prompt or dialog:** the only new Play services calls are `CapabilityClient.addListener` and `removeListener`, made only with Play services usable. `WearCodesTest.playServicesIsAskedThroughItsPublicApiAndNothingCanShow`, which scans the package's compiled calls, still passes (7 of 7).
- **No permission:** a capability listener needs none, and no manifest line changed.

**`NodeClient` has no listener.** play-services-wearable 20.0.1's `NodeClient` offers `getLocalNode`, `getConnectedNodes`, `getCompanionPackageForNode` and `getNodeId` only (read with `javap`), and the old `NodeApi` has no listener left either. So a watch **without** Taqwa that connects or leaves while Settings is open is read at the next resume, or within a tap's follow. A watch with Taqwa coming or going changes `taqwa_watch`'s reachable nodes, which the listener hears, as the phone's `PhoneWatchListenerService` already relies on.

**The follow's numbers** (`TapFollow.APP`, pinned by `theAppFollowsATapEveryFiveSecondsForThreeMinutes`):
- **Every 5 s:** the rows trail the watch by at most 5 s plus one reading (3 s at most). Each reading is the one a resume makes, so the cost is at most 36 readings in the 3 minutes after a tap, while Settings stays in front, and none otherwise.
- **For 3 minutes:** it covers what follows a tap on the watch. A request a cached companion holds is joined for 2 minutes (`WatchRequests.EXPIRY`; P5.12 Step 3 saw one delivered 45 s late). The wearer then still acts on the watch: the Play Store's install, or the face screen's button with its permission and the push, which `FaceActivity` bounds at 20 s.
- **Why a fallback at all:** the listener is Play services' own and should reach a Settings in front. But no device has shown yet how promptly a capability change reaches Settings on his S23 with the Galaxy Wearable stack, and P5.6 saw the phone's Wear OS companion cached and holding a tap's broadcast. A tap is the moment the watch is about to change, so the follow is placed there.
- Past the 3 minutes, the listening and the next resume remain.

**The iPhone.** What `offer.available()` reads is `WatchFaceShare.isAvailable()`: the session `activated`, `isPaired`, and the face bundled. The bundle never changes while the app runs. So the row can change only when the session's activation state or `isPaired` does, and `TaqwaWatchSession`'s delegate hears each:
- `session(_:activationDidCompleteWith:error:)`, which also covers a Settings shown before activation finished, which would have shown no row until the next resume;
- `sessionDidBecomeInactive(_:)` and `sessionDidDeactivate(_:)`, another watch being chosen;
- `sessionWatchStateDidChange(_:)`, `isPaired` among the properties it reports.

Each now also calls `WatchFaceShareBridge.sessionChanged()` (`iOSApp.swift:398`, `:403`, `:409`, `:418`). That emits into a `SharedFlow` with no replay (`WatchFaceOffer.ios.kt:41`), which the iOS offer returns as its `changes()`, and Settings asks `available()` again on the main thread. It is called on WatchConnectivity's queue and never waits.
- **No fallback after a tap on the iPhone:** "Add to Apple Watch" hands the face to the system's own sheet, and nothing the tap does changes what the offer reads. Every change it reads has its callback.
- **Adding a face does not hide the row,** as plan 4 built it: the offer reads a paired watch and a bundled file, not whether a face was added. [For Mohamed](#for-mohamed-and-the-lead) has the question.
- The standalone `WatchFaceRow(offer)`, which only `WatchFaceRowTest` uses, still asks on each coming to the front alone, as plan 4's Task 10 line says. In the app the row is always inside `WatchSection`.

**One library finding.** `merge(...).conflate()` does not conflate in kotlinx.coroutines 1.10.2. `ChannelLimitedFlowMerge.produceImpl` passes `produce(context, capacity, …)` without the overflow (`Merge.kt:85-87` in the sources jar), so `conflate()`'s `DROP_OLDEST` is lost, and the channel is a rendezvous one that queues every change. A probe test showed it: `merge(watch, empty).conflate()` read 4 times for 4 changes during a slow reading, where a plain `conflate()` read twice. `watchRowsChanged` merges its three by hand in a `channelFlow`, which keeps it, and says why in a comment. `changesDuringAReadingAreOneReadingAfterIt` holds it.

### Item 3: the watch's side

- **The watch app installed:** `taqwa_watch` is the app's static capability (`wear.xml`), added by Play services with the install. The phone's `PhoneWatchListenerService` hears it too and sends the feed (`WATCH_APPEARED`). That message starts the watch app, and its start publishes the face's state (`TaqwaWearApp.refreshFace`, `:82`), adding `taqwa_face_addable` for a face not yet active (P5.6 Step 6 check 2: `capability added INSTALLED` at its start). So with Settings open, the install row stays while the app starts and pushes its face, and "Add the Taqwa face" takes its place once it has, which P5.6 Step 6 check 2 saw at the app's start (nobody has timed it from the install). Between the two the rows answer no row at all, and the install row is held for up to 20 s so that the section stays ([Review round 1](#review-round-1-the-moment-with-no-section-and-the-listeners-first-word-lane-w44-rows-fix)). A watch the phone has fed before is sent no feed when Taqwa is installed on it again (`WATCH_APPEARED` damped), so nothing starts its app: there the section goes when the hold ends and comes back with the face row once the app is opened.
- **The face taken through the row:** the face screen's button publishes its answer as soon as `addAndActivate` returns (`FaceActivity.kt:54`, bounded 20 s, then the publish 5 s). `ACTIVE`, and the spent activation's `CHOOSE_IT_YOURSELF`, both remove `taqwa_face_addable` (`FaceCapability.wanted`). The listener hears it, and the section goes. **Quick enough to be seen,** as the task asks.
- **The face chosen another way** (the watch's own picker, or the Galaxy Wearable app): **not promptly.** Nothing on the watch hears that moment, and `taqwa_face_addable` is published again only at the app's start, Today coming to the front, the daily nudge, or a face update. That is "at most a day stale" by `FaceCapability`'s KDoc.
  - **Not changed**, as the task says. One more gap for the Lead: opening the face screen from a stale row reads the state (`FaceActivity.kt:44-46`) but does not publish it, so the stale row survives its own tap unless that tap started the app's process. The fix would be one line in `FaceActivity`, publishing what it read. That is watch code outside this lane, and the Lead's call.
  - **Closed at the merge into watch-44 (the Lead's call, 9 November):** the face screen publishes the state it reads as it opens (`readAndPublish` in `FacePush.kt`, on the app's scope, the publish after the state is shown), so the stale row's own tap corrects it. A state not read within the screen's 20 s is shown as failed and not published.
- **The feed format and the face** are untouched.

### Tests, red first

- **`commonTest` `WatchTargetsTest`** (28; on the host and the simulator), 1 new: `aReadingThatFailsCanKeepWhatIsShown` (a throw, a stray cancel and the 3 s bound keep the rows shown; no row, or another row, is an answer).
- **`commonTest` `WatchRowsChangedTest`** (7, new), on virtual time:
  - `theAppFollowsATapEveryFiveSecondsForThreeMinutes`;
  - `aTapIsReadAfterEveryFiveSecondsForThreeMinutesAndThenNoMore`: 36 readings, at 5 s to 180 s, and none in the 27 minutes after;
  - `aTapMeanwhileStartsTheThreeMinutesAgain`: a second tap at 62 s, then 36 more to 242 s, never two follows at once;
  - `withoutATapNothingIsRead`;
  - `theWatchSideTheAppleSessionAndATapEachAskForAReading`;
  - `aSourceThatCannotListenEndsNothing`;
  - `changesDuringAReadingAreOneReadingAfterIt`.
- **`androidHostTest` `PlayWatchTargetsTest`** (15), 3 new:
  - `aListenerHearsBothCapabilitiesUntilSettingsStopsListening`: one listener added for `taqwa_watch` then `taqwa_face_addable`; each change heard; on cancelling, removed from both; nothing heard after; no node or capability read by the listening itself;
  - `withoutPlayServicesNothingIsListenedTo`: for the five `ConnectionResult` codes, the flow ends with no Wearable call;
  - `aListenerThatCannotBeAddedOrRemovedLeavesNothingBehind`: a refused second listener ends the flow with its throw after the first is removed; a throwing removal ends the collection as cancelled, both removals tried.
- **`iosTest` `WatchSectionTest`** (19, the composable on the simulator), 6 new:
  - `theRowsFollowTheWatchWhileSettingsStaysInFront`: the install row, then the face row on the watch side's word, then no section, Settings never left (3 readings);
  - `theWatchSideIsListenedToOnlyWhileSettingsIsInFront`: the source's and the offer's listening, 1 while resumed, 0 once left, 1 again on coming back;
  - `aTapsAnswerStaysWhileTheRowsAreReadAgainInPlace`;
  - `aReadingInPlaceThatFailsKeepsTheRows`;
  - `aTapIsFollowedByReadingsForAWhileAndThenNoMore`: a follow of every 100 ms for 500 ms brings the face row with no word from the watch, then the readings stop;
  - `theAppleRowFollowsTheSessionWhileSettingsStaysInFront`.
  - `WatchSection` takes `follow` (default `TapFollow.APP`), as it takes `requests` and `bound` (Ruling 74 item 12). Every other test gives `NO_FOLLOW`, so their reading counts are their own, and no test waits the app's 3 minutes.
- **`iosTest` `IosWatchFaceOfferTest`** (5), 1 new: `aSessionChangeIsSaidToWhoeverListensThen` (none kept for a later listener).
- **Red**, over skeletons that gave the new members empty bodies (`changes()` saying nothing, `otherwise` ignored, `TapFollow.after` empty, `sessionChanged()` a no-op, `listen` unused):
  - on the host, 9 of the 57 tests in the four classes failed, each on its assertion. The two new ones that passed are guards a skeleton meets: nothing listened to without Play services, and nothing read without a tap;
  - on the simulator, 14 of 66, each on its assertion or its `waitUntil` condition.
- **Green:**
  - host: the four classes 57 of 57;
  - simulator: `WatchSectionTest` 19, `WatchFaceRowTest` 7, `IosWatchFaceOfferTest` 5, `WatchRowsChangedTest` 7 and `WatchTargetsTest` 28, with 0 failures.
- **Mutants**, one at a time, each put back. Every one fails the test named:

| Mutant | Fails |
|---|---|
| `TapFollow.after` with `collect` for `collectLatest` | `aTapMeanwhileStartsTheThreeMinutesAgain` |
| `watchRowsChanged` without `conflate()` | `changesDuringAReadingAreOneReadingAfterIt` |
| the source's throw not caught | `aSourceThatCannotListenEndsNothing` |
| listening without the availability check | `withoutPlayServicesNothingIsListenedTo` |
| no removal | `aListenerHearsBothCapabilitiesUntilSettingsStopsListening`, `aListenerThatCannotBeAddedOrRemovedLeavesNothingBehind` |
| a removal's throw not caught | `aListenerThatCannotBeAddedOrRemovedLeavesNothingBehind` |
| `loadWatchTargets`'s bound answering no row, not `otherwise` | `aReadingThatFailsCanKeepWhatIsShown` |
| a reading in place moving `reading` on (simulator) | `aTapsAnswerStaysWhileTheRowsAreReadAgainInPlace`, `aReadingInPlaceThatFailsKeepsTheRows` |
| a reading in place without `otherwise` (simulator) | `aReadingInPlaceThatFailsKeepsTheRows` |
| a tap not followed (simulator) | `aTapIsFollowedByReadingsForAWhileAndThenNoMore` (no face row) |
| the follow ten times longer (simulator) | the same test ("asked 51") |

### Errata and rules for this part

- **No new words.** No string resource and no draft marker was touched. `scripts/check-strings.sh`: i18n clean.
- **Ruling 39:** nothing here reads or logs a place. A capability word carries none.
- **Ruling 55 item 6:** no test vector in any APK; the new tests are host and simulator tests.
- **No face file and no feed format changed:** `watchface/`, `build/face-release/` and `wearApp/` untouched.
- **The version** stays 1.6.0 (43); the lane ships in 44 at the Lead's cut.
- **Devices:** none. Simulator runs only under `ios-sim-tests`, each in the ledger. adb was not run, and no emulator or simulator was started or touched by hand. The Kotlin/Native tests ran on the simulator their Gradle task uses.
- **The checks:**
  - `:shared:testAndroidHostTest` whole: 2495 tests, 0 failures. `:androidApp:testDebugUnitTest`: no tests in that module, exit 0.
  - `:shared:iosSimulatorArm64Test` for the touched classes: green, above.
  - `scripts/ios-build.sh`: BUILD SUCCEEDED, `check-watch-bundle` holds every rule (TaqwaWatchWidgets.appex 4416 KB of 5120, TaqwaWatch.app 12036 KB of 15360).
  - `scripts/test.sh` whole, on `c2c5721c` under `ios-sim-tests` (02:25:45 to 02:35:05 SAST): exit 0 in 560 s. Versions 1.6.0 (43)/(100043), test-bump, the privacy gate (263 rows, 263 approved, 0 awaiting), the listing, `swift test`, the timetables compile, then shared host 2495, shared iOS 2461, widgetcore 144/142, watchcore 233/200 and wearApp 612, with 0 failures and no EOFException.
  - No new compiler warning in a touched file.

### Review round 1: the moment with no section, and the listener's first word (lane `w44-rows-fix`)

Two findings from the review of `75fd197f..10930aa5`. Each was checked against the code first; both hold, and both are fixed. Test-first, with host and simulator tests only: no device, no adb, no emulator. Commits `9d2d08cb` (the listener's first word) and `4da1af6a` (the hold), then these docs; Taqwa identity, no trailer, nothing pushed, version not bumped.

**The moment with no section (important).** In the owner's Android flow the rows went from the install row to no section at all, and then to the face row:
- `taqwa_watch` is the watch app's only static capability (`wear.xml`), so Play services adds it with the install, and the listener reads the rows at once.
- `taqwa_face_addable` comes only from `FaceCapability.publish`, once the watch app's process has started (`TaqwaWearApp.onCreate`, then `refreshFace`). The feed the phone sends at `WATCH_APPEARED` is what starts it.
- In between, `watchTargets` answers `install = null` (the watch has Taqwa) and `addFace = null` (no face word yet), which is `WatchTargets.NONE`. `WatchSection` drew nothing: the heading went, About moved up, and TalkBack's focus on the row or on its "Continue on your watch" line was dropped. Seconds later it all came back.
- On resume alone this was almost never seen. The listener and the follow made it part of every install, and `theRowsFollowTheWatchWhileSettingsStaysInFront` jumped straight from the install row to the face row, so it never covered it.

**Fixed with a hold** (`InstallHold`, `WatchRows.kt`). A reading in place that takes rows with an install row to no row at all keeps the rows shown. A watch that has just got Taqwa cannot have taken its face yet, so this no row means the face is not published yet, not that it was taken.
- **For 20 s** (`InstallHold.APP`), counted from the first reading of no row: the feed's send that starts the watch app, within `WatchFeedSync.CALLER_BOUND` (10 s), then `refreshFace`, within `FACE_BOUND_MS` (9 s, the publish's own 5 s inside it), rounded up.
- **At its end, one more reading**, the hold's end being merged into `watchRowsChanged`. Its answer is taken as it comes.
- **The face row, or any other answer, during the hold** is shown at once and ends it. A reading that answers the rows shown (a failure, through `otherwise`) changes nothing, the hold included. Rows without an install row are never held, so a face taken still makes the section go at once.
- **Each resume makes a new hold.** The resume's own reading takes no row as it comes, so a failure there is still no row.
- **What it costs:** at most one more reading per hold. A watch without Taqwa that leaves while its install row is shown keeps the row for up to 20 s more, since the rows cannot tell the two cases apart.
- **What it does not cover: a watch the phone has fed before.** When Taqwa is installed on it again, it is sent no feed. `WATCH_APPEARED` is `UNCHANGED`, since the phone's record says the watch holds the feed until the watch's own handshake says otherwise (`AndroidWatchLinkTest.stepThreesOddDeliveriesAreTheNumbersAnEmptyWatchsHandshakesSetAside`). Nothing then starts the watch app, so the section goes at the hold's end and comes back with the face row once the app is opened (the Play Store's Open, or the launcher). A first install is sent the feed. Which of the two Mohamed's S23 saw is not known.

**The listener's first word (minor).** Settings starts listening while its first reading asks the capabilities: the producer starts at the main coroutine's first suspension, asks availability on `Dispatchers.Default` and calls `addListener`, which Play services completes asynchronously, while the reading's `getCapability` calls run. A change that landed after the reading asked and before the listener was registered was neither read nor heard, until a tap's follow or the next resume.
- **Now** `capabilityChanges` says one change of its own once both listeners are added (`trySend(Unit)` after the loop, before `awaitClose`), so the rows are read once more after them. That is one more reading each time Settings comes to the front, conflated behind the first if that is still asking.
- **What is left:** the word follows the adds as made, not as Play services confirms them (`addListener`'s task is not awaited, as before). A change in the moment between that reading's ask and the add's completion is still unheard. That moment is far shorter than a reading, and a tap's follow or the next resume reads it.

**Tests, red first:**
- **`commonTest` `InstallHoldTest`** (7, new), on virtual time:
  - `theAppHoldsTheInstallRowFor20Seconds`;
  - `noRowAfterAnInstallRowIsHeldUntilTheHoldEndsThenTaken`: held until 20 s, its end asks once, then no row is taken;
  - `anotherRowDuringTheHoldIsShownAtOnceAndEndsIt`: the face row at 6 s, and no end asked after;
  - `noRowAgainDuringTheHoldKeepsItsEnd`: no row at 5 s and 15 s, one end at 20 s;
  - `aReadingThatAnswersTheRowsShownChangesNothing`;
  - `onlyRowsWithAnInstallRowAreHeld`;
  - `aHoldOverIsSpentByTheNextAnswer`: the end's reading finds the install row, and a later no row is held another 20 s.
- **`commonTest` `WatchRowsChangedTest`** (8), 1 new: `theEndOfAHoldAsksForAReading`. Its other calls now name `listenOn`, since `holdEnds` comes before it.
- **`androidHostTest` `PlayWatchTargetsTest`** (16), 1 new: `onceBothListenersAreAddedItSaysOneChange` (both adds, then the word, before any change). `aListenerHearsBothCapabilitiesUntilSettingsStopsListening` counts that word first, and `aListenerThatCannotBeAddedOrRemovedLeavesNothingBehind` now checks that a refused listening says nothing.
- **`iosTest` `WatchSectionTest`** (22), 3 new:
  - `theSectionStaysWhileAWatchThatHasJustGotTaqwaPublishesItsFace`: the install row, no row twice, then the face row. The heading is checked after the second no row, whose reading starts only once the first's rows are in place, and at every poll of the wait for the face row (4 readings);
  - `noRowIsTakenOnceTheHoldIsOver`: with a 300 ms hold the section goes, after 3 readings: the resume's, the one held, and the hold's end;
  - `noHoldOutlivesSettings`: Settings left and brought back inside the 20 s, and no section at once.
  - `WatchSection` takes `hold` (default `InstallHold.APP`), as it takes `follow`.
- **Red**, over a skeleton `InstallHold` whose `rows` answered the reading, and with no word after the adds:
  - host: `InstallHoldTest` 4 of 7 and `PlayWatchTargetsTest` 2 of 16 failed, each on its assertion;
  - simulator: 6 of 37 (`WatchSectionTest` 2 of 22, `InstallHoldTest` 4 of 7).
  - The new ones that passed are guards a skeleton meets: the 20 s pin, a face row during the hold, rows without an install row, and no hold past a resume. `theEndOfAHoldAsksForAReading` went in with the section's wiring before the red run; its mutant below fails it.
- **Green:**
  - host: `:shared:testAndroidHostTest` whole, 2504 tests (2495 and the 9 new), 0 failures;
  - simulator, under `ios-sim-tests` (03:07:31 to 03:09:13 SAST): `WatchSectionTest` 22, `WatchFaceRowTest` 7, `IosWatchFaceOfferTest` 5, `WatchRowsChangedTest` 8, `WatchTargetsTest` 28 and `InstallHoldTest` 7, 0 failures;
  - `scripts/check-strings.sh`: i18n clean. `git diff --check` clean, and no new compiler warning in a touched file.
  - Not re-run: `scripts/test.sh` whole and `scripts/ios-build.sh`. No Swift, resource or build file changed since the lane's own whole run on `c2c5721c`.
- **Mutants** (host), one at a time, each put back:

| Mutant | Fails |
|---|---|
| no hold's end in `watchRowsChanged` | `theEndOfAHoldAsksForAReading` |
| the 20 s started again at each no row | `noRowAgainDuringTheHoldKeepsItsEnd` |
| a hold over not spent by the next answer | `aHoldOverIsSpentByTheNextAnswer` |
| any rows held, not only an install row's | `onlyRowsWithAnInstallRowAreHeld` |
| a reading that answers the rows shown ends the hold | `aReadingThatAnswersTheRowsShownChangesNothing` |
| the word said before the adds | **survives.** On one test thread the collector cannot run inside the producer's body, so a word sent before the adds still arrives after them, and a refused add cancels the channel, which drops it. The code's order and its KDoc hold it. |

- **No new words**, no string or draft marker touched, and no Swift, `wearApp/`, `watchface/` or `build/face-release/` file changed.

### For Mohamed and the Lead

1. **The iPhone's row after a face is added (Mohamed).** He asked for the row to go once the face is added. "Add to Apple Watch" stays, as plan 4 built it. ClockKit's `addWatchFace(at:)` answers only whether the sheet took the face, and nothing tells the app when a face is removed again. The options:
   - (a) keep it as it is: a second face can be added, and the row stays true;
   - (b) hide the row once a face was added, remembered on the phone: it never comes back if he deletes that face;
   - (c) hide it while `WCSession.isComplicationEnabled` holds, which Apple documents as a complication of the app on the active face. `sessionWatchStateDidChange` reports its changes, so the row would follow. But whether it holds for WidgetKit complications on current watchOS is unverified, and it needs a real Apple Watch.

   Built as (a); (c) is a small change once a watch shows it holds.
2. **A stale face row (the Lead).** On Wear OS, a face chosen without the row's screen leaves `taqwa_face_addable` up until the watch's next publish, at most a day. The face screen does not publish on opening (item 3 above). A one-line watch change would close it, and it is outside this lane. Closed at the merge into watch-44: the face screen now publishes what it reads as it opens, so the row's tap corrects it.
3. **The moment between the install row and the face row (the Lead).** While the watch app starts and pushes its face, the rows answer no row at all. Since review round 1 the install row is held meanwhile, for up to 20 s, so the section stays and the face row takes its place ([Review round 1](#review-round-1-the-moment-with-no-section-and-the-listeners-first-word-lane-w44-rows-fix)). Past the 20 s, as on a reinstall the phone sends no feed to, the section goes and comes back by itself once the app runs.
4. **On a device (Task 6 Step 6 on the emulators, since this changes the rows' own files):** with Settings left open on the phone, the install row stays while the Wear app starts and gives way to "Add the Taqwa face" by itself, the heading never leaving, and the section goes by itself after Today's chip and Deny. Plan 5's Step 6 now says so, and that an app nothing starts within 20 s makes the section go and come back.
5. **On his S23 (Task 13, the Lead's to place):** in check 1, after the face row's idle run is written down and with no tap on the phone's rows in the 3 minutes before (so no follow is running), keep Settings open on the phone while the face is made active from Today's chip, as check 1 does. Expected: the section goes within seconds of the watch's publish, Settings never left. It is where the listener is first seen on a real phone. If the section stays until Settings is left and brought back, that is a finding, and the follow alone covers a tap's 3 minutes.

## Known limits

The rows' taps as they stand after Ruling 74, with Ruling 76's two additions:
- **A process killed while a broadcast is deferred can send twice.** `WatchRequests.process` lives as long as Taqwa's process. If Android kills the process while the system still holds a DEFERRED `REMOTE_INTENT`, the system still delivers it, but the joins are gone. A tap after the restart sends a second request, and the watch can open the screen twice when the companion wakes.
- **The 2-minute expiry.** A request the companion holds longer than 2 minutes and then delivers, with a tap after the expiry, opens the watch's screen twice. Accepted by the ruling, since a second `FaceActivity` is better than a dead row.
- **A companion that never answers** ends at the expiry. The tap stops following it, and the row keeps "Continue on your watch" and takes a tap again.
  - It can end earlier through `CallbackToFutureAdapter`'s garbage-collected path, as `UNREACHABLE` ("Couldn’t reach your watch"): the result receiver let go unanswered, its completer collected ([Ruling 74 item 14](#item-14-the-reports-line-17-its-known-limits-and-the-garbage-collected-path)).
  - Before the expiry the row says so, within the same reading of the rows; after it, or after the rows are read again, no row hears it.
  - The receiver travels in the intent's extras, so while the system holds a deferred broadcast it should hold the receiver too, and this path come only after the companion has had the intent and let it go unanswered. That is read from the library, not seen on a device.
  - **This answer comes on the finalizer thread,** not a binder thread: the collected completer fails the future from its `finalize` (a probe printed `Finalizer @coroutine`). What runs there is short and never blocks: the request's `Unconfined` resume and the requests' `Mutex`, which suspends. `PlayWatchTargetsTest` holds the path through JVM finalization, which JEP 421 deprecates for removal: on a JDK without it that test fails at its 30 s bound, so it would be seen.
- **The 2 minutes count uptime, not wall time.** `TimeSource.Monotonic` is `System.nanoTime` on Android, and it stops in deep sleep, as the waits of `withTimeoutOrNull` do. A phone put down asleep right after a tap holds the row for the rest of its 2 awake minutes: a tap on coming back can join a request sent long ago, send nothing, and say "Continue on your watch" at once. It lets go of itself within those minutes, so this is harmless.
- **The late answer has no device evidence yet.** `QUEUED`, then `OPENED` or `UNREACHABLE`, is first seen on his S23 (plan 5 Task 13 Step 2), if its companion goes idle there. If it never does, the check records that, and the evidence stays missing.
