# Plan 2 Task 5: the Android sender, and the Wear library check

Lane P (`watch-feed`), 25 October 2026. Plan 2 Task 5 ("The Android sender over the Data Layer, to directly connected watches only"), built test-first, with Ruling 52 item 9 (the watch key is the Wear node id).

- Base: `e5c057f7` (watch-companion after Ruling 52's merge).
- Commits: the WIPs `aa6ea961` (the failing tests over skeletons), `fb449718` (the code), `a4281d30` (the delivery model's node ids), `da2f6e77` (every Android echo taken in; plan 2 and the spec as built) and `015830a6` (this report), then Ruling 53's release proof and known limit, folded by `git reset --soft e5c057f7` into the plan's one commit (Step 6). Taqwa identity, no trailer, nothing pushed. Ruling 53 item 4 (Ruling 52's review notes, the iPhone) is its own commit after it, with its own report, `docs/superpowers/reports/2026-10-25-link-follow-ups.md`.
- **Ruling 53 item 1 passed the tracker diff's one line.** Step 5's diff prints `com.google.firebase`. The check, as Ruling 53 reads it: any line the debug diff prints must either be absent from the release dex (`apkanalyzer` on the R8 release) or be explained in the report. It is absent from the release dex, by `apkanalyzer` read through R8's mapping (below), and explained here. Every other check passed as the plan wrote it.
- Devices: none. No lock taken, no emulator, no simulator by hand (the Kotlin/Native tests and `ios-build.sh` use their own). The plugged-in phone `KB2604110095` and the work iPhone 12 were not touched. No release key was read or used: the release was only processed and shrunk (`processReleaseMainManifest`, `minifyReleaseWithR8`), never assembled or signed.

## The verdict on `play-services-wearable` (Mohamed: no tracker, no unnecessary permission)

| Check | Result |
|---|---|
| The phone's merged release manifest: `uses-permission` | **12 before, 12 after, the same lines.** |
| The manifest's other additions | **Only the three the plan allows:** `GoogleApiActivity` (`exported="false"`), the `com.google.android.gms.version` meta-data, and our `PhoneWatchListenerService` with its three filters. Nothing else changed but the allowBackup comment (Ruling 39 item 5). |
| New artifacts on the release classpath | **Eight, all `com.google.android.gms` or `androidx`:** `play-services-wearable` 20.0.1, `-base` 18.5.0, `-basement` 18.9.0, `-tasks` 18.2.0; `androidx.fragment:fragment` 1.1.0, `androidx.viewpager:viewpager` 1.0.0, `androidx.loader:loader` 1.0.0, `androidx.customview:customview` 1.0.0. No artifact's name matches the tracker pattern. No version of an artifact already there changed. |
| New packages in the debug APK matching the tracker pattern (`comm -13 trackers-before trackers-after`) | **One line: `com.google.firebase`**, three exception classes of `play-services-basement`. **Absent from the release dex** (`apkanalyzer` on R8's output, read through its mapping). It passes by Ruling 53 item 1. |
| `:androidApp:minifyReleaseWithR8` | Succeeds. |

**The one line.** `com.google.firebase` in the debug APK is three classes of `play-services-basement` 18.9.0's own jar, 464 bytes of dex in all:
- `FirebaseException extends Exception`, with its three constructors;
- `FirebaseApiNotAvailableException extends FirebaseException`;
- `FirebaseExceptionMapper implements StatusExceptionMapper`, whose one method turns a Play services `Status` into one of the two.

They are exception types that Google's Firebase SDKs share with Play services; basement carries them for every app that uses it. There is no Firebase SDK, no analytics, no measurement, no network code and no initialisation in them, and nothing of ours names them.

**The release proof (Ruling 53 item 1): R8's release dex holds none of them.** `:androidApp:minifyReleaseWithR8` (never assembled or signed) writes one dex, `androidApp/build/intermediates/dex/release/minifyReleaseWithR8/classes.dex` (7,071,572 bytes, SHA-256 `345d7c1744b58d82…`, built 25 October 10:29 on `015830a6`'s code). Zipped as it is (`zip release-dex.zip classes.dex`), `apkanalyzer` reads it through R8's mapping, so every kept class shows its own name:

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" dex packages --defined-only \
  --proguard-mappings androidApp/build/intermediates/mapping/release/minifyReleaseWithR8/mapping.txt release-dex.zip \
  | awk '$1=="P"{print $NF}' | sort -u > release-packages.txt          # 437 packages ("Successfully loaded maps from: mapping.txt")
grep -E 'firebase|crashlytics|analytics|measurement|appmeasurement|gms\.ads|adservices|privacysandbox' \
  release-packages.txt > release-trackers.txt                         # androidx.media3.exoplayer.analytics alone
```

- The release's tracker scan holds one line, `androidx.media3.exoplayer.analytics`, the baseline's media3 line. No `com.google.firebase` and no `android.adservices` (D8's debug stubs are not in the release).
- `grep -ci firebase` over the whole listing, classes and methods included, is 0. The mapping names no `firebase` class either.
- The listing is the real release, read through its mapping: it holds `com.google.android.gms.wearable` (354 methods) and `.wearable.internal`, 97 classes of `com.google.android.gms.wearable.*`, and `world.taqwa.app.watchfeed.PhoneWatchListenerService` under its own name.

Exodus Privacy's signatures could not be fetched from this session (`reports.exodus-privacy.eu.org` and `etip.exodus-privacy.eu.org` gave no answer to `curl`), so the release's own scan stays plan 5's, as Ruling 39, the plan and Ruling 53 item 1 have it: plan 5's Exodus scan of the release build is the final word.

**What the Lead ruled (Ruling 53 item 1):** the line passes. Three exception classes, no SDK and no network, removed by R8 from the release: not a tracker under Mohamed's condition. The 12 permissions are unchanged. Plan 2's Global Constraints ("The library"), Task 5 Step 5 and Task 6 Step 5 now read the check as the ruling does.

### Step 1, the baseline (before the catalog changed)

```bash
./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath > deps-before.txt   # 1,127 lines
./gradlew :androidApp:processReleaseMainManifest                                               # 12 uses-permission
./gradlew :androidApp:assembleDebug
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" dex packages --defined-only \
  androidApp/build/outputs/apk/debug/androidApp-debug.apk | awk '$1=="P"{print $NF}' | sort -u > packages-before.txt   # 769
grep -E 'firebase|crashlytics|analytics|measurement|appmeasurement|gms\.ads|adservices|privacysandbox' \
  packages-before.txt > trackers-before.txt
```

The twelve permissions: `ACCESS_COARSE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

`trackers-before.txt` is not empty, as the plan expects. Each line and where it comes from:
- `androidx.media3.exoplayer.analytics`: media3-exoplayer 1.11.1 (`AnalyticsListener`, the player's own event interface), for `RecitationService`.
- `android.adservices`, `.adid`, `.adselection`, `.appsetid`, `.common`, `.customaudience`, `.exceptions`, `.measurement`, `.ondevicepersonalization`, `.signals` (ten packages, 46 classes): **the debug build's own dexing step, no library.** Every one of the 46 is `SYNTHETIC`, and its only method is a `<clinit>` that throws `NoClassDefFoundError` (read with `dexdump -d`). They sit in one dex, `classes19.dex`, beside the same kind of stub for other framework packages newer than minSdk 26 (`android.net.ipsec`, `android.health.connect`, `android.os.strictmode`, `android.app.appsearch` …): D8's stubs for platform classes above the app's minimum API. No class of the app or of a library references them.

### Step 5, the same commands after the library

- **Dependencies:** `diff deps-before.txt deps-after.txt` adds one 30-line subtree under `:shared`, and nothing else. Who pulled what:
  - `play-services-wearable` 20.0.1, asked by `shared`'s androidMain;
  - `play-services-base` 18.5.0 and `play-services-tasks` 18.2.0, by `play-services-wearable`;
  - `play-services-basement` 18.9.0, by wearable and base (base asks 18.4.0);
  - `androidx.fragment:fragment` 1.1.0, by base and basement (base asks 1.0.0);
  - `androidx.viewpager:viewpager` 1.0.0 and `androidx.loader:loader` 1.0.0, by fragment;
  - `androidx.customview:customview` 1.0.0, by viewpager.
- **The merged release manifest**, diffed whole against the baseline's: the three additions above and the comment, nothing else.
- **The debug APK:** 769 packages before, 827 after; none removed. The 58 added:
  - `androidx.fragment`, `.fragment.app` (fragment 1.1.0); `androidx.viewpager`, `.viewpager.widget` (viewpager); `androidx.loader`, `.loader.app`, `.loader.content` (loader); `androidx.customview.view`, `.customview.widget` (customview);
  - `androidx.lifecycle.lifecycle`, `.lifecycle.viewmodel`, `.lifecycle.viewmodel.anchor`: one generated `R` class (resource ids alone) of a lifecycle library already on the classpath at the same version;
  - `com.google.android.gms.wearable`, `.wearable.internal`, `.internal.wearable` (wearable); `com.google.android.gms.tasks`, `.internal.tasks` (tasks); `com.google.android.gms.base`, `.internal.base`, `.common.api`, `.common.api.internal`, `.signin`, `.signin.internal`, `.auth`, `.auth.api`, `.auth.api.signin`, `.auth.api.signin.internal` (base: the `GoogleApi` client machinery, which every Play services API is built on; the sign-in types come with it and are never called, and no account permission joins the manifest); `com.google.android`, `com.google.android.gms`, `.gms.actions`, `.gms.common` and its 26 subpackages, `.gms.dynamic`, `.gms.dynamite`, `.gms.internal`, `.gms.internal.common`, `.gms.security` (basement);
  - `com.google.firebase` (basement): the line above.
- **Trackers:** `comm -13 trackers-before.txt trackers-after.txt` prints `com.google.firebase` alone. It is absent from the R8 release's dex (the release proof above), so it passes by Ruling 53 item 1.
- **R8:** `./gradlew :androidApp:minifyReleaseWithR8` BUILD SUCCESSFUL. `PhoneWatchListenerService` is kept under its own name (the manifest names it).
- **Lint:** `./gradlew :androidApp:lintDebug`: 0 errors, 61 warnings, 2 hints, none on a file this task touched and none naming Play services. The two `ExportedReceiver` warnings are the debug harness's own manifest, as before. The service carries `tools:ignore="ExportedService"`, as `RecitationService` does, with its reason in the comment.

## What was built

All `internal`, in `shared/src/androidMain/kotlin/world/taqwa/app/watchfeed/`, unless named otherwise.

- **`WearCodes.kt`:** `noPlayServices(availability)` (a `ConnectionResult` code), `noWearApi(statusCode)` (17, `CommonStatusCodes.API_NOT_CONNECTED`, alone), `nodeGone(statusCode)` (4000, `WearableStatusCodes.TARGET_NODE_NOT_CONNECTED`, alone), `TaskCancelled : Exception` (never a `CancellationException`), `outcome(isCanceled, exception, result)` and `Task<T>.await()`, a `suspendCancellableCoroutine` with one listener on a direct executor, so the 17 reaches `noWearApi` as itself and a task that never answers is cut by the ask's budget.
- **`WatchLink.android.kt`** (replacing the interim `NoWatchLink`):
  - `WatchNode`, `directlyConnected`, `NodeSend`, `WatchNodes`;
  - `PlayWatchNodes`, over three functions (`availability`, `capability`, `message`) that `PlayWatchNodes(context)` makes `GoogleApiAvailabilityLight`'s and `Wearable`'s (since Ruling 54 item 1, `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)`, the public reference's call, in place of the first: `2026-10-25-link-follow-ups.md`, "Ruling 54"). Without a usable Play services no Wearable client is made, so nothing can prompt or notify. A 17 from `getCapability` is null, its `ConnectionResult` code logged (`TaqwaWatch no Wear API <code>`); from `sendMessage`, a 17 or a 4000 is `GONE`; anything else throws as itself;
  - `AndroidWatchLink`: `confirmsDelivery` true; `reachable()` one capability query (since Ruling 54 item 4, the ask's one reading, which `watchKeys()` shares); `watchKey()` null; `watchKeys()` the nearby nodes' ids (Ruling 52 item 9); `send` asks again, then one message per nearby node on `WatchLinkContract.feedPath(delivery)`, each in its own `try`;
  - `createWatchLink() = AndroidWatchLink(PlayWatchNodes(appContext))`.
- **`PhoneWatchListenerService.kt`:** the service, whose two callbacks pass the message's path, data and `sourceNodeId`, or the capability's name and nodes, to `takeWatchMessage` and `takeCapabilityChange`, each inside `runBlocking { withTimeoutOrNull(CALLER_BOUND) { … } }` and a `catch (Exception)`. The handshake is `sync(WATCH_ASKED, have, from = node)`, the acknowledgement `acknowledge(echo, from = node)` with the log line `TaqwaWatch ACK <loggedDelivery(echo)>` (number and fingerprint, never the origin: Ruling 52 item 4), and `taqwa_watch` with a nearby node `sync(WATCH_APPEARED)`. Paths the filters' prefixes also match (`/taqwa/refreshed`) are ignored. (Since Ruling 54 item 2 the two message filters are literal `android:path`s, so no other path wakes the service; the rule stays.)
- **`androidApp`:** the service in the manifest (exported, the three filters on `wear` with `host="*"`); `res/values/wear.xml`, `android_wear_capabilities` = `taqwa_phone`, `tools:keep`, `translatable="false"`; the allowBackup comment and `data_extraction_rules.xml`'s comment now quote "Only your own devices can see your location" (Ruling 39 item 5). No attribute or rule changed.
- **Build:** `gradle/libs.versions.toml` and `shared/build.gradle.kts` (below); `AppContainer`'s comment on the link.

### Ruling 52 item 9: the watch key is the Wear node id

- `WatchLink` (commonMain) gains `watchKeys(): Set<String?>`, with the default `setOf(watchKey())`, so the iPhone's link and every fake behave exactly as before; `AndroidWatchLink` answers its nearby nodes' ids.
- `WatchFeedRecord` gains `holds(fingerprint, keys)` (`confirmedFor in keys`) and `echoed(echo, from, keys)` (ignored when `from !in keys`); the one-key forms the iPhone and the existing tests use are those over `setOf(key)`.
- `WatchFeedSync` reads `watchKeys()` once at step 4: a confirmation counts only while its watch is among them, and a link that does not confirm hands over to `keys.singleOrNull()`, read before the call (mutant M9's test still holds). `acknowledge` gains `from`.
- **Every Android echo is taken in, for the node it came from** (step 0 and acknowledgements): the iPhone keeps A3's rule (only the session's watch's), and a link that confirms by echo takes in any watch's, an echo from a watch no longer nearby among them. The ruling asks for the node id and for the confirmation to count only while that node is nearby; it does not ask to drop a far echo, and dropping one lost information: with far echoes dropped, the model's D1 configuration found the A2-undone mutant on 2 schedules, against 11 when they are taken in (a wiped watch's handshake served after it went away said nothing to the phone). Taking a far echo in costs at most one more send to the watch that is nearby, never a wrong `UNCHANGED`.
- So: a watch switched to between asks never inherits another's confirmation (one switched to while an ask builds its feed is the known limit of Ruling 54's one reading per ask, ratified by Ruling 55 item 1); a second watch beside the first stays plan 2 call 1's known limit (an `UNCHANGED` until its own handshake, which it makes as soon as it hears the phone), as `aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake` shows.

## The errata, as resolved

1. **Ruling 50 (A1–A4) and Ruling 52:** each send carries its delivery on `WatchLinkContract.feedPath(delivery)` (`theFeedGoesToEveryNearbyWatchOnTheFeedPath`: `/taqwa/feed/<origin>/<seq>/<fingerprint>`, the number the record wrote down); the watch's acknowledgement and handshake are read as `echoOf` text through the sync's `takeIn` (`deliveryOf`), never parsed here. Item 9 as above. The plan's call 1, Task 4's interface lines and Task 5 (a new "As built" block) and spec 1.2 now say so.
2. **The catalog line,** shared with Lane W's P2.6, is the plan's, bare, with no comment (so the two additions are byte for byte the same), each in its section where the neighbours are in alphabetical order:
   - `[versions]`: `play-services-wearable = "20.0.1"`, right after `media3 = "1.11.1"` and before the Wear OS comment block;
   - `[libraries]`: `play-services-wearable = { module = "com.google.android.gms:play-services-wearable", version.ref = "play-services-wearable" }`, right after `androidx-work-runtime` and before `wear-compose-material3`.

   Google's `maven-metadata.xml` (read 25 October) names 20.0.1 as both `latest` and `release`, so the plan's version stands. The reason for the library sits beside its one use, in `shared/build.gradle.kts`. The manifest's permissions and the dependency tree are verified above.
3. **Ruling 39:** only nodes with `taqwa_watch` that are nearby at the send get the feed (`aWatchReachedOnlyThroughTheCloudIsNotSent`, `nearbyIsAskedAgainAtTheSend`); the feed is Task 3's, with the city and the bearing to 1° and no coordinate; no `DataClient`, `putDataItem`, channel or asset anywhere (`grep` of `shared/src` and `androidApp/src`: only the KDoc that forbids them). The release log lines are `TaqwaWatch no Wear API <code>` and `TaqwaWatch ACK <seq>/<fingerprint>`, with the sync's existing `TaqwaWatch <reason> <result> <ms>ms`: no origin, place or bearing (Ruling 52 item 4).
4. **The Gradle flake** (an `EOFException` in `:shared:testAndroidHostTest`'s worker, `EngineBudgetBenchmarkTest` under load): not seen in any run of this task.

## Tests

- **Red first:** the new tests over skeleton bodies, `aa6ea961`: 27 of 82 failed, each for its missing body (`NotImplementedError`, or the old behaviour: `anAcknowledgementIsTakenInFromTheWatchItCameFrom` expected true). The far-echo rule was turned the same way: `anEchoFromAWatchNotNearbyCountsOnlyWhileItIsNearby` was seen failing (`expected:<SENT> but was:<UNCHANGED>`) before `takeIn` changed.
- **`WearCodesTest`** (6): `eachCallIsReadInItsOwnCodeSpace`, `aCancelledTaskIsAnErrorNotACancellation`, `aFailedTaskAwaitsToItsOwnException` (a real `ApiException(Status(17))` builds on the host JVM, so the fallback the plan allowed was not needed), `aCancelledTaskAwaitsToTaskCancelled`, `aSuccessfulTaskAwaitsToItsResult`, `aTaskThatNeverCompletesIsCutByTheBudget` (null at 5,000 ms of virtual time; the late `complete(1)` throws nothing).
- **`AndroidWatchLinkTest`** (18): Step 2's thirteen (`theLinkConfirmsDelivery`, `withoutPlayServicesNothingIsAskedOrShown`, `theWearApiMissingIsNoWatchNotAFailure`, `aCancelledCapabilityQueryIsAFailure`, `noWatchWithTaqwaIsNoWatch`, `aWatchReachedOnlyThroughTheCloudIsNotSent`, `nearbyIsAskedAgainAtTheSend`, `aNodeThatLeftAtTheSendIsNoWatch`, `theFeedGoesToEveryNearbyWatchOnTheFeedPath`, `aFeedAtTheBudgetIsWithinTheMessageLimit`, `oneWatchThatTookItIsSent`, `aThrowingNodeIsAFailureNotACrash`, `aWatchQueryThatNeverAnswersIsCutAndTheNextAskIsServed`: `FAILED` at 5 s, `SENT` at 5 s, `FAILED` at 10 s, `SENT`), item 9's four and the listener's routing (see plan 2 Task 5's "As built").
- **`aFeedAtTheBudgetIsWithinTheMessageLimit`:** Task 3's largest feed, bn-BD on 12 hours with the worst city and a hundred control-character phrases, built by `WatchFeedSampleTest.largestFeed` (the very builder `theLargestFeedStaysWithinItsBudget` now uses), sent under the longest possible delivery (`seq` = `MAX_ECHOED_SEQ`): the node receives the JSON's UTF-8 bytes, at most 60,000, the path within the 1,024-byte envelope, and both within the 100,000-byte message limit.
- **Common:** `WatchFeedRecordTest` +2 (15), `WatchFeedSyncTest` +1 (43). `WatchFeedDeliveryModelTest`: each Android watch is named by its node id; every echo is taken in for it; (S) also requires the watch heard from last to be nearby at every `UNCHANGED` that is not its own handshake's; and a new configuration, "android, two watches switched, wipes" (last in the list, so every other configuration keeps its seeds), puts on the other watch, often once the links are quiet. 1,760 schedules, about 0.6 s on the JVM. Counted once with a temporary counter (on the earlier seed order): an ask that met a confirmed watch away beside a nearby one came 3 times in the existing two-watch configuration and 25 times in the new one, and was answered `SENT` or `FAILED` every time.
- **Mutants,** each applied alone to the sources, run against `world.taqwa.app.watchfeed.*` on the host, and put back (the working tree equal to the commit after each): 16 of 16 killed.

| Mutant | Killed by |
|---|---|
| M1 `holds` ignores the keys | 11 tests, the model among them |
| M2 `echoed` ignores the keys | the A3 record and sync tests (3) |
| M3 `acknowledge` drops its node | 7, the model among them |
| M4 `watchKeys` counts cloud nodes | 3 |
| M5 `await` reads `task.result` eagerly | 4 (the 17 hidden in `RuntimeExecutionException`) |
| M6 `send` stops at the first throw | `oneWatchThatTookItIsSent` |
| M7 `send` ignores the nearby flag | 5 |
| M8 `noWearApi` also takes 16 | 2 |
| M9 no availability check | `withoutPlayServicesNothingIsAskedOrShown` |
| M10 a capability change asks without a nearby node | the listener's test |
| M11 the handshake drops its node | the listener's test |
| M12 a gone node is a failure | `aNodeThatLeftAtTheSendIsNoWatch` |
| M13 an Android confirmation counts whether or not its watch is nearby | 3, the model among them (17 switched schedules; with the model's new nearby check removed the model lets it pass, so that check is what catches it) |
| M14 a non-cancellable `await` (`suspendCoroutine`) | hangs: the run never ends |
| M15 Android takes an echo in only from a nearby watch | 3, the model among them |
| M16 A2 undone (a forget sets no number aside) | 7; the model on 11 schedules, seed 53000005 first, as Ruling 52 found |

- **`scripts/test.sh`** on the final code: exit 0 (versions 1.2.0 (38)/(100038), bump PASS; shared host 2,173 / iOS 2,111, widgetcore 111/109, watchcore 119/117, wearApp 143; 0 failures, no `EOFException`). Again with every host test task `--rerun`: exit 0, the same counts. The new tests account for the 27 more host tests and 3 more iOS ones than Ruling 52's merge (2,146 / 2,108). The model takes about 0.6 to 0.7 s on the JVM and 4.0 s on the simulator.
- **`scripts/ios-build.sh`** (`shared/` changed), with `IOS_DEST` on the unlocked iPhone 17 `D9C6CFA7`, which stayed shut down: BUILD SUCCEEDED, `TaqwaWidget.appex` 3 MB; only the old `iOSApp.swift:210` warning of ours. The framework header names no feed type (only two old KDoc lines mention `WatchDelivery`).
- **`tools/i18n-check.py`:** clean. **`git diff --check`:** clean.

## For the Lead

- **The ruling on `com.google.firebase`:** Ruling 53 item 1 passed it, with the release proof above. The WIPs were folded by `git reset --soft e5c057f7` into the plan's Step 6 commit, "Watch feed: the Android phone sends it over the Wear Data Layer, to watches directly connected at the send, each answer read in its own code space".
- **Ruling 53 item 2** accepted the common-code changes for Ruling 52 item 9 (`WatchLink.watchKeys()`, `WatchFeedRecord.holds`/`echoed` over a set of keys, `acknowledge(from)`) and Android taking every echo in for its node id, counted only while that node is nearby. Built as above, unchanged.
- **Ruling 53 item 3, the known limit:** with two Wear watches connected at the same time, the one not confirmed can get `UNCHANGED` until its own handshake (`STARTED`, `PHONE_NEARBY` or the daily one) heals it (`aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake`). A phone normally keeps one watch connected. Plan 2 call 1's known limits, Review Focus 2 and spec 1.2 now name it.
- **Lane W's P2.6** adds the same two catalog lines at the same two places (above), and reads the phone's echo as before: nothing in the contract changed. The phone now takes the watch's `sourceNodeId` from every message, which the watch sends anyway.
- **Not done here, as the plan has it:** the paired-emulator pass (Task 7), Exodus on a build with the sender (plan 5).

## The watch: plan 2 Task 6's checks (30 October, Lane W)

Task 6 adds `play-services-wearable` to `:wearApp` (`implementation(libs.play.services.wearable)`, the catalog line P2.5 put in, used as it is) for the Wear receiver, its acknowledgement, the handshake and the daily nudge. The same three checks, on the watch. Worktree `watch-wear`, base `cc32c391`; the scratch files are in the session's scratchpad (`p26/`), never in the repository.

### The verdict

**Passed, as on the phone.** No permission joins: the merged release manifest keeps androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone (plan 3's `VIBRATE` has not landed). The library adds `GoogleApiActivity` (`exported="false"`) and the `com.google.android.gms.version` meta-data, and nothing else. The tracker diff prints one line, `com.google.firebase`: the same three exception classes of `play-services-basement` that Ruling 53 item 1 passed on the phone. No Firebase SDK, analytics, measurement, ads or crash reporting is added.

### Step 1, the baseline (before `wearApp/build.gradle.kts` named the library)

- `./gradlew :wearApp:dependencies --configuration releaseRuntimeClasspath > wear-deps-before.txt`: 788 lines, no `com.google.android.gms` artifact.
- `./gradlew :wearApp:processReleaseMainManifest`: one `uses-permission`, `world.taqwa.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx.core's own), with its `<permission>`.
- `./gradlew :wearApp:assembleDebug`, then Task 5 Step 1's `apkanalyzer dex packages --defined-only … | awk '$1=="P"{print $NF}' | sort -u` and `grep -E 'firebase|crashlytics|analytics|measurement|appmeasurement|gms\.ads|adservices|privacysandbox'` on `wearApp-debug.apk`: 585 packages, and 10 tracker-pattern lines, `android.adservices` and its nine subpackages (`adid`, `adselection`, `appsetid`, `common`, `customaudience`, `exceptions`, `measurement`, `ondevicepersonalization`, `signals`).
  - **Where they come from:** no library. They are defined in `wearApp/build/intermediates/global_synthetics_dex/debug/generateDebugGlobalSynthetics/classes.dex` alone, the global synthetics AGP's D8 writes: stubs of platform classes newer than the app's `minSdk` 30, so that code naming them verifies on older watches. The face workflow's APK of 24 October held the same ten.

### Step 5, the same commands after the library

- **Permissions:** unchanged. `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone; no `INTERNET`, no location.
- **The merged release manifest's new entries:**
  - from the library: `com.google.android.gms.common.api.GoogleApiActivity`, `exported="false"` (`play-services-base` 18.5.0), and the `com.google.android.gms.version` meta-data (`play-services-basement` 18.9.0);
  - our own: `world.taqwa.app.wear.link.FeedListenerService` (exported for Play services, two filters on `wear` and `host="*"`: `MESSAGE_RECEIVED` with `pathPrefix="/taqwa/feed"` and `CAPABILITY_CHANGED` with `path="/taqwa_phone"`), and `world.taqwa.app.wear.link.DailyNudgeReceiver` (`exported="false"`, no filter).
  - Neither `DebugNudgeReceiver` nor `DebugFeedReceiver` is in it. The `com.google.android.wearable` `uses-library` (`required="false"`) was there before the library: the manifest merger's blame names `androidx.wear.watchface:watchface-complications-data:1.3.0`.
- **The dependency diff** (`wear-deps-before.txt` against the same command now): the four Google libraries alone, `play-services-wearable` 20.0.1, `-base` 18.5.0, `-basement` 18.9.0 and `-tasks` 18.2.0. What they ask for besides (`androidx.core:core` 1.0.0 and 1.2.0, `androidx.fragment:fragment` 1.0.0, `androidx.collection:collection` 1.0.0) resolves to the versions already on the classpath (1.18.0, 1.1.0 and 1.5.0), so no other artifact joins.
- **The tracker diff:** `comm -13 wear-trackers-before.txt wear-trackers-after.txt` prints one line, `com.google.firebase`.
  - It holds `FirebaseException`, `FirebaseExceptionMapper` and `FirebaseApiNotAvailableException`, from `play-services-basement-18.9.0`'s `classes.jar`: exception types the Play services code names, with no network, no collection and no Firebase SDK behind them, as on the phone.
  - It is **present in the watch's release APK** (`wearApp-release-unsigned.apk`), since the watch's release has no R8 yet (plan 5 Task 3 adds it, ledger L460). So the line is explained here rather than shown absent; the phone's R8 release proof (above) is what R8 will do on the watch too. P5.3 should repeat that proof on the watch's mapping.
- **The packages the library added** (`comm -13 wear-packages-before.txt wear-packages-after.txt`, 47 lines, 632 packages in all), each with the library whose `classes.jar` defines it (`…gms` is `com.google.android.gms`):
  - `play-services-wearable` 20.0.1: `…gms.wearable`, `…gms.wearable.internal`, `…gms.internal.wearable`;
  - `play-services-tasks` 18.2.0: `…gms.tasks`, `…gms.internal.tasks`;
  - `play-services-base` 18.5.0: `…gms.auth.api.signin`, `…gms.auth.api.signin.internal`, `…gms.common.data`, `…gms.common.images`, `…gms.common.internal.service`, `…gms.common.moduleinstall`, `…gms.common.moduleinstall.internal`, `…gms.common.server`, `…gms.common.server.converter`, `…gms.common.server.response`, `…gms.internal.base`, `…gms.signin`, `…gms.signin.internal`;
  - `play-services-basement` 18.9.0: `…gms.actions`, `…gms.common.annotation`, `…gms.common.config`, `…gms.common.internal.constants`, `…gms.common.internal.safeparcel`, `…gms.common.logging`, `…gms.common.providers`, `…gms.common.signatureverification`, `…gms.common.sqlite`, `…gms.common.stats`, `…gms.common.util`, `…gms.common.util.concurrent`, `…gms.common.wrappers`, `…gms.dynamite`, `…gms.internal.common`, `…gms.security`, `com.google.firebase`;
  - both `-base` and `-basement`: `…gms.common`, `…gms.common.api`, `…gms.common.api.internal`, `…gms.common.internal`, `…gms.dynamic`;
  - parents with no class of a jar's own: `com.google.android`, `…gms`, `…gms.auth`, `…gms.auth.api`, `…gms.internal`, and `…gms.base`, which holds only the R class the build generates for `play-services-base`'s resources;
  - ours: `world.taqwa.app.wear.link`.
- **The debug seams stay out of the release:** `strings` over the release APK's three `classes*.dex` finds none of `drop-next-feed`, `hold-next-ack`, `DEBUG_NUDGE`, `DebugNudgeReceiver`, `DebugFeedReceiver` and `DEBUG_FEED` (0 each). The debug APK's dex holds each of them, as the control.
- **`./gradlew :wearApp:assembleRelease :wearApp:lint`:** BUILD SUCCESSFUL. `assembleRelease` writes `wearApp-release-unsigned.apk`: `:wearApp` has no signing config, so no key is read. Lint: 0 errors and 1 warning, `MissingApplicationIcon` on the `<application>` element, which this task does not touch.
- **`scripts/test.sh`:** exit 0 (versions 1.3.0 (39)/(100039), bump PASS; shared host 2,305 / iOS 2,258, widgetcore 111/109, watchcore 130/117, wearApp 186, 0 failures, no `EOFException`). wearApp was 143: the 43 new tests are below.
- **`./gradlew :wearApp:compileDebugUnitTestKotlin --rerun`:** no warning. It printed three before: ledger L460's two (`ComplicationTimelinesTest.kt:184-185`, a `!!` on a value already smart-cast), and `SampleFeedTest.kt:226` (`System.getProperty`'s `String?` passed as a `String`). All three are fixed. `compileDebugKotlin` and `compileReleaseKotlin` print none.

### What Task 6 built

- **`FeedStore`** (Ruling 50, built on `WatchLinkContract` and never copied): `write(json, delivery: WatchDelivery? = null)`, `delivery()`, `deliveryFile` (`feed.json.delivery`), and a new `FeedWriteResult.STALE`.
  - A write with a delivery is first put to `WatchLinkContract.stores(delivery(), delivery)`. When it says no, the result is `STALE` before the text is even decoded: the feed, its delivery and the update notice stay as they are, and nothing is redrawn or acknowledged.
  - A stored feed follows `WatchLinkContract.STORE_STEPS` as a loop over the steps: the delivery deleted (`Files.deleteIfExists`, whose failure is thrown with the old feed and delivery as they were), the feed renamed into place, then the delivery written the same way (a temporary file, then a rename). That last write is best effort: an `IOException` there leaves no delivery and the feed stored.
  - Plan 1's callers pass none: the sample is stored whatever the watch holds and holds no delivery. `NEWER_VERSION` and `MALFORMED` leave the delivery the stored feed's.
  - A greater number for the very feed held is `ACCEPTED` and rewrites the delivery. Its redraw is not skipped (the plan allows either): plan 1's rule tells the listener of an identical feed too, since the phone being in reach makes what was drawn ahead a little older.
- **`link/FeedReceiver`:** `Received(result, echo)` and `onMessage(path, data, store)`, as the plan's interface says: null for a path `isFeedPath` refuses, for a feed path with no delivery, and for an `IOException` (logged). `echo` is `echoOf(store.delivery())` on `ACCEPTED` alone. `ACK_BOUND` (5 s) lives here.
- **`link/WearCodes`:** the watch's copy of `noPlayServices`, `noWearApi`, `TaskCancelled`, `outcome` and the cancellable `Task.await()`. `nodeGone` is not copied: the watch's sends treat every failure alike.
- **`link/RefreshPolicy`:** `AskWhy` and `shouldAsk`, as written. One rule beyond the plan: a stamp ahead of `now` (the watch's clock set back) holds nothing back, so a clock moved back a day cannot silence the daily handshake for a day.
- **`link/PhoneAsk`:** as written, plus a seam the JVM tests reach. The handshake itself is `PhoneAsk.handshake(why, now, store, stamp, link)` in the companion, over a small `PhoneLink` (`phones()`, `send()`). `PlayPhoneLink(context)` is that link over Play services' public API: `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable`, then `getCapability(taqwa_phone, FILTER_REACHABLE)` (an `ApiException` with `noWearApi` is no phone), then `sendMessage`. `askIfDue` is `handshake` over it. Only nearby phones are asked, each in its own `try`. The stamp is written only when one took it. `BOUND` (10 s) lives here.
  - Log lines: `ASK <why> sent`, `skipped` or `no phone` as the plan has them, and two more words for a failed query or send, `failed <exception class>` and `not queued <exception class>`.
- **`link/FeedListenerService`:** as the plan's interface says (drop, receive, hold, acknowledge inside 5 s; reconnect handshake inside 10 s), every body catching `Exception`. Its lines are `TaqwaWearFeed <result> <seq>/<fingerprint>`, `IGNORED no delivery`, `DROPPED`, `ACK HELD` and `ACK <seq>/<fingerprint> <sent|failed>`. It never logs the path, which holds the origin.
- **Logging:** `feedLog(line)` prints `TaqwaWearFeed <line>` on standard output, as the phone's `TaqwaWatch` lines do, so Task 7's `logcat -s TaqwaWearFeed System.out` reads both, and the JVM tests need no `android.util.Log`. `logged(delivery)` and `loggedEcho(echo)` give `<seq>/<fingerprint>`, `none` or `unreadable`, never an origin (Ruling 52 item 4). No line holds the place or the bearing (Ruling 39): the lines are built from results, reasons and deliveries alone.
- **`link/DailyNudge`** and `DailyNudgeReceiver`: an inexact `ELAPSED_REALTIME` daily alarm, `FLAG_IMMUTABLE`; `goAsync` and `PhoneAsk.BOUND`. `nudge` answers whether the handshake went out, for the debug receiver's line.
  - After review (round 1), `FLAG_NO_CREATE` is no longer the whole arm, because a force stop on Wear OS 3 to 5 removes the alarm and keeps its `PendingIntent`. `arm` runs on the app's scope at every process start. It reads the `PendingIntent` (`FLAG_NO_CREATE`), the nudge's anchor (`nudge.anchor`, which the receiver writes when the alarm goes off) and the last process's exit reason, and `DailyNudge.arming` decides. A live alarm is left alone, so a frequent process start never pushes the nudge out. A stop by the user, or a missing `PendingIntent` (a reboot, a force stop from Android 15 on), sets it again for when it was due. An anchor more than 36 h away sets it due now, which also heals a force stop that left no exit reason. Its lines are `NUDGE kept` and `NUDGE armed in <seconds>s found=<bool> stopped=<bool>`.
  - On `emulator-5558` (API 36), with my debug build, the arming worked as designed:
    - after the install, with no anchor: `armed in 86400s`;
    - after a process killed by a signal: `NUDGE kept`, with one pending alarm;
    - after a force stop, which removed the alarm: `armed in 86322s found=false stopped=true`, on the same schedule within a second;
    - with an anchor two days old: `armed in 0s`. The alarm went off within 7 s, its receiver anchored it again, and `ASK DAILY` ran.
    - The system sorts exit records by the clock. That emulator held two records dated in November, from an earlier clock set forward, so `stopped` read true until the app's own exit history was cleared. That changes nothing, as designed.
    - The case the review names, a `PendingIntent` found after a force stop, needs API 30 to 34 and was not run.
  - The epoch-second stamps are read and written by `readStamp` and `writeStamp` (`link/Stamps.kt`), which `PhoneAsk.lastAskedAt` and the handshake's stamp now use too.
- **`TaqwaWearApp`:** a process scope (`SupervisorJob() + Dispatchers.IO`). `onCreate` arms the nudge (a `RuntimeException` there is logged and never stops the app) and calls `askPhone(AskWhy.STARTED)`. `askPhone(why)` launches the handshake on the scope inside `PhoneAsk.BOUND`, catching `Exception`, and never blocks its caller.
- **`StatusActivity.onResume`:** `ComplicationSources.requestUpdates(this)`, then `askPhone(AskWhy.OPENED)` (erratum p3 #1: built here as P2.6 writes it; P3.4 moves the two lines). Moved by P3.4 on 30 October into `TaqwaWearApp.onAppOpened`, which `WearMainActivity.onResume` calls.
- **Debug only:** `FeedDrop` (`drop-next-feed`, `hold-next-ack`, `ACK_HOLD` 20 s) with its release twin answering false and naming no file. `DebugNudge.forgetLastAsk` and `DebugNudgeReceiver`, exported in `src/debug/AndroidManifest.xml` for `world.taqwa.app.wear.DEBUG_NUDGE` with the comment the plan asks for. It is sent by name (`-n …/world.taqwa.app.wear.link.DebugNudgeReceiver`) and ends with `nudged asked=<true|false>`.
- **Resources and comments:** `res/values/wear.xml` (`taqwa_watch`, `tools:keep`). The manifest's permissions comment is rewritten (the feed comes through Play services' Data Layer, sent only while the two are directly connected, which can still cross Google's servers end to end encrypted, Ruling 39). Its allowBackup comment and `data_extraction_rules.xml`'s now quote "Only your own devices can see your location" (Ruling 39, item 5 of its list). No attribute or rule changed.

### Tests (43 new; wearApp 143 → 186)

- `FeedReceiverTest` (11): the plan's ten, plus `aFeedTheStoreCannotWriteIsNoAnswer`.
- `FeedStoreTest` (+6): the plan's four, plus `aStaleOrDuplicateDeliveryTouchesNothing` (a stale number is not even read for its version, and nothing is redrawn) and `aDeliveryFileThatHoldsNoDeliveryIsNone`.
  - `theStoreFollowsTheContractsSteps` makes the feed's step fail twice. Once the temporary path is taken by a directory, with the old feed in place: the old feed is left, and no delivery. Once the rename's target is a directory, as the plan words it: a planted delivery is gone, and the new one was never written.
- `RefreshPolicyTest` (10): the plan's nine, plus `anAskStampedAheadOfTheClockSilencesNothing`.
- `PhoneAskTest` (4): `theHandshakeCarriesTheStoredDelivery`, `theStampIsTheLastAskOrNothing`, `aHandshakeGoesToNearbyPhonesAloneAndIsStampedOnlyWhenItWentOut` and `aHandshakeThatReachesNoPhoneIsNotStamped`.
- `WearCodesTest` (4): the plan's three, plus `playServicesIsAskedThroughItsPublicApiAndNothingCanShow` (Ruling 54 item 1 on the watch). It reads the link package's constant pools with a copy of the phone's `ClassReferences`, beside a copy of its `FakeTask`.
- `NoEngineOnTheWatchTest` (1), `FeedLogTest` (1) and `LinkManifestTest` (3). `LinkManifestTest` holds the listener's two filters, the nudge receiver and `wear.xml` to `WatchLinkContract` and the library's action constants; `wear.xml` became a Gradle test input.
- testDebug: `FeedDropTest` (2) and `DebugNudgeTest` (1).
- After review (round 1): `DailyNudgeTest` (7, wearApp 186 → 193). It covers the first arming, a live alarm left alone, a force stop set again on its schedule, a reboot keeping it, a stale anchor due now, no anchor, and the anchor's stamp. Seven mutants, each applied alone and restored, were all killed: the stop ignored, the staleness ignored, a stale anchor set a day away, no upper clamp, a strict limit, the schedule not kept, and a future anchor fresh for ever.
- The red step was a compile failure (no `delivery`, `STALE` or link types), seen and not committed.
- **Ten mutants**, each applied alone and restored, all killed:
  - the `stores` check dropped;
  - the delivery written before the feed;
  - the old delivery kept;
  - every result acknowledged;
  - `STARTED` only with no feed;
  - a handshake stamped when nothing went out;
  - cloud-reached phones asked;
  - the origin logged;
  - a stamp ahead of the clock obeyed;
  - `GoogleApiAvailabilityLight` asked.

### The errata, as resolved

1. **Ruling 50:** the store keeps the delivery, takes only what `WatchLinkContract.stores` allows, stores in `STORE_STEPS`' order, and echoes `echoOf` what it holds, in the acknowledgement and in the handshake. No rule is copied out of `:watchcore`, and the watch computes no fingerprint.
2. **Erratum p3 #1:** P2.6's own checks are as written. The merged release manifest holds `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` alone, and the `OPENED` ask is in `StatusActivity.onResume`.
   - Since P3.4 (30 October): the merged release manifest holds `VIBRATE` and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, and nothing else. The `OPENED` ask is in `TaqwaWearApp.onAppOpened`. On `emulator-5558`, opening P3.4's debug build logged `TaqwaWearFeed ASK OPENED skipped` for a fresh sample feed. Plan 2 Task 6's text says the same. The lines P3.4 will move are in the ledger.
3. **The catalog line:** untouched. P2.5's `play-services-wearable = "20.0.1"` and its library alias were merged already; `:wearApp` names `libs.play.services.wearable`.
4. **Ruling 39:** no log line holds the place, the bearing or the origin (`FeedLogTest`, and no line logs a path). The status screen still shows only `place.city`, which the feed holds.
5. **`emulator-5558`:** not used. The task has no device step, and no lock was taken.
6. **The Gradle flake:** not seen.

### For the Lead

- **P3.4's lines** (ledger): `StatusActivity.kt:49-53`, the `onResume` override, whose two lines are `ComplicationSources.requestUpdates(this)` and `(application as TaqwaWearApp).askPhone(AskWhy.OPENED)`. In `TaqwaWearApp.onAppOpened` they become `ComplicationSources.requestUpdates(this)` and `askPhone(AskWhy.OPENED)`. Plan 3's text still names the delivery by Ruling 48's words: `fingerprintFile` should read `deliveryFile` (`feed.json.delivery`, and its temporary `feed.json.delivery.tmp`), and `write(json, fingerprint)` should read `write(json, delivery)`.
- **Ruling 55 item 6** (the debug APK carries `feed-vectors.json`, `watch-rules-vectors.json` and `phone/`) is its own commit after Task 6's, `fa62c182`. The debug APK's assets are now the four sample feeds alone, and the release APK has none.
- **Not done here, as the plan has it:** the paired-emulator pass (Task 7), and Exodus on a build with the sender (plan 5).
