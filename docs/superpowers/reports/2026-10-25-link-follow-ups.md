# Ruling 52: the link's follow-ups

Lane P (`watch-feed`), 25 October 2026. Ruling 52's items 1 to 8 (item 9 is P2.5's), built test-first on watch-feed's head after P2.8.

- Base: `1c84c6b3` (watch-companion with P2.8 merged).
- Commits (Taqwa identity, no trailer, nothing pushed):
  - `45a5243d` "Watch feed: plan 2 Task 6 reads the contract as Ruling 50 left it …": item 8's plan 2 Task 6 Interfaces line, first and on its own, for Lane W's P2.6, with Task 6's own log lines (item 4).
  - `c88f71f8` "Watch feed: no ask fails for good on a bad stored origin or a huge echo, …": the code and tests (items 3 to 7), with item 8's code comments.
  - The commit after it: the rest of the plan text, the spec and the reports (items 1, 2, 4 and 8), and this report.
  - The WIP `1e5f6d5c` (green on the watchfeed tests) was folded into the two by a soft reset.
- Devices: none. No lock taken, no emulator or simulator by hand (the Kotlin/Native tests boot their own simulator). The plugged-in phone `KB2604110095` and the work iPhone 12 were not touched. No release key was read or used.

## What was built, item by item

### 1. The stale acknowledgement overtaking a newer one: a known limit

Docs only. Plan 2's call 1 (the known limits) and Review Focus 2 name `aStaleAckOvertakingAWipedWatchsNewerOneIsHealedByItsHandshake` as the limit Ruling 52 accepted, healed by the watch's next handshake; spec 1.2's known limits say a pre-wipe echo may confirm "even one that overtakes an echo it sent since". The named case was already in `WatchFeedNamedCasesTest` and is unchanged.

### 2. A2 adopted

Plan 2's call 1 now reads `lastSeq` (the highest number used, sent or set aside), A2's wording. The code already had it (`WatchFeedRecord.forgotten`, Ruling 50's fix round).

### 3. Robustness

- `WatchFeedRecord.echoed`: an echo numbered above `MAX_ECHOED_SEQ` (`Long.MAX_VALUE / 2`) counts as another origin's and is forgotten. So neither `sending` nor `forgotten` after it can overflow. Before, an echo at `Long.MAX_VALUE` lifted `lastSeq` there, the next send's number overflowed, `lastDelivery` was null, and every ask was `FAILED` for good.
- `DataStoreWatchFeedMemory.recordOf` takes a stored origin only when `WatchLinkContract.isOrigin` accepts it. Otherwise `read()` starts a whole new record under a new origin, in one edit, as it does for a store with no origin. Before, a malformed stored origin made every send's `WatchDelivery` throw, and every ask was `FAILED` for good.
- Tests: `WatchFeedRecordTest.anEchoNumberedAboveHalfALongIsAnotherInstalls` (half a `Long` + 1 and `Long.MAX_VALUE` are forgets, half a `Long` still lifts, with room above), `WatchFeedSyncTest.anEchoAtTheLargestNumberNeverWedgesTheSync` (both links: the handshake at `Long.MAX_VALUE` is `SENT` under number 3, then `UNCHANGED`), `WatchFeedMemoryTest.aStoredOriginOfAnotherFormStartsAWholeNewRecord` (empty, upper case, 15 and 17 characters, punctuation).

### 4. Logs that carry no origin

- `loggedDelivery(text)` in `:shared` commonMain (`watchfeed/WatchFeedLog.kt`): `<seq>/<fingerprint>` for a delivery's text, `none` for an echo of nothing, `unreadable` for anything `deliveryOf` refuses, never the text as it came. The iPhone's Swift reads it as `WatchFeedBridge.shared.logged(delivery:)`. P2.5's listener uses it for its `TaqwaWatch ACK` line (plan 2 Task 5's text).
- `TaqwaWatchSession` (Swift): every `NSLog` is `NSLog("%@", line)`, so no value is interpolated into the format string; each send's line names `bytes=` and `delivery=<seq>/<fingerprint>`, and each handshake's `have=<seq>/<fingerprint>` (or `none`). Nothing logs the origin, the place or the bearing. The DEBUG-only `TaqwaHarness` lines (the recitation harness, not the link's) are not the link's and were left as they are.
- The plans' log lines: plan 2 Task 6 (`TaqwaWearFeed ACK <seq>/<fingerprint> <sent|failed>`, `STALE <seq>/<fingerprint>`, and a rule for every `TaqwaWearFeed` line of the release build), Task 5 (`TaqwaWatch ACK <loggedDelivery(echo)>`), Task 7 check 1, Task 8 and a Global Constraints rule "Logs (Ruling 52 item 4), on every platform"; plan 4 Task 5 (`NSLog("%@", …)` with `<seq>/<fingerprint>`) and Task 5B's two expectations (`<n>/<fp>`, `unchanged <n>/<fp2>`). Only a debug harness may log an origin.
- The phone's own per-ask line (`TaqwaWatch <reason> <result> <ms>ms`) never carried a delivery and is unchanged.
- Tests: `WatchFeedLogTest` (3) and `IosWatchLinkTest.theBridgeLogsADeliveryWithoutItsOrigin`.

### 5. The watch key reads the directory only once activated

`TaqwaWatchSession.watchKey()` now guards `session.activationState == .activated, let directory = session.watchDirectoryURL`. Its doc comment, the `keyFile` comment, `WatchSessionHook.watchKey`'s KDoc and plan 2 Task 8 now say only what Apple's `watchDirectoryURL` page says (read 25 October): the URL is "undefined and should not be used" while `notActivated`; it is "specific to the currently paired and active Apple Watch" and "changes when the active Apple Watch changes"; iOS deletes the directory "If the user uninstalls your app or unpairs their Apple Watch"; and it is nil "If there is no paired watch". The old "(not activated, not paired, no watch app)" claim is gone. (Added by Ruling 53 item 4: the SDK header, `WCSession.h`, also says "If the watch app is not installed for the selected Watch the value will be nil", and that the directory "will be deleted upon next launch if the watch app is uninstalled for the selected Watch, or that Watch is unpaired"; both comments now quote it.) Swift has no unit test here (no XCTest target exists until plan 4); the guard is two lines, read in review, and `scripts/ios-build.sh` compiles it.

### 6. A handshake that never blocks WatchConnectivity's queue

- `WatchFeedBridge.refreshRequested(have: String, reply: (Boolean) -> Unit)` replaces `refreshRequested(have): Boolean`. In the header it is `- (void)refreshRequestedHave:(NSString *)have reply:(void (^)(SharedBoolean *))reply`, so Swift passes a closure returning `Void`.
- `WatchBridgeCalls.refreshRequested` reads the key as the message arrives (A3, unchanged), then launches on its `scope` `withTimeoutOrNull(bound) { sync().sync(WATCH_ASKED, have, from) }` and returns at once. No `runBlocking` anywhere in the file. The reply is called exactly once (`ReplyOnce`, an `AtomicBoolean`): true for `SENT` or `UNCHANGED`; false for anything else, a bound passed, a throw, a key that throws, or a scope gone before the coroutine ran (`invokeOnCompletion`).
- `WatchFeedBridge` owns the scope: `SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler`, never cancelled, independent of the app's container, so a handshake never builds the container on WatchConnectivity's queue. (Superseded by Ruling 53 item 4a: the coroutine now starts `UNDISPATCHED`, so at a cold start the handshake's first `sync()` builds the container on that queue, as `watchActivated()` already did.)
- Swift: `didReceiveMessage(_:replyHandler:)` calls `refreshRequested(have:) { queued in replyHandler([refreshReplyKey: queued.boolValue]) }` and returns; the form without a reply handler passes a closure that only logs.
- Tests (iosTest): `aHandshakeNeverBlocksWatchConnectivitysQueue` (the call returns while the sync is still building the feed; the reply comes after), `aHandshakeIsAnsweredOnceWhateverBecomesOfIt` (a throwing key, no container, a gone scope: one reply each), and the existing handshake tests through a new `handshake()` helper that awaits the reply.

### 7. Sharper tests

- `aHandshakeTheSyncCannotServeIsFalse`'s first half now ends the worker first (`cancelAndJoin` of its scope's job), and every bound is a minute, so only the closed queue's refusal can answer within its 5 s guard. It took 10 s before (the bound); it takes 0.0 s now.
- `aHandshakeWithNoWatchOrNoFeedIsFalse` pins `refreshRequested` false for `NO_WATCH` (the hook not ready) and `NO_FEED`.
- `onTheIphoneAHandOverCountsForTheWatchKeyReadBeforeTheCall` (commonTest) kills mutant M9: the session switches watches during the call, and the confirmation stays with the key read before it.
- The delivery model (`WatchFeedDeliveryModelTest`) gains two features and two configurations:
  - `NO_FEED`: the phone sometimes has no feed (a setting change to none, one time in five), so an ask, a handshake's among them, ends `NO_FEED`; the quiet phase gives the feed back with its reschedule first.
  - `AWAY`: a lone Android watch that goes away and comes back.
  - "android, a lone watch that goes away, wipes, no feed at times" (`WIPE`, `AWAY`, `NO_FEED`, three times the runs) seeks out D1: after three stores in four the watch is reset behind its acknowledgement (`resetBehindItsAck`: wiped, its start's handshake reaching the phone at once, then the watch out of reach or the phone without a feed), and `up` delivers newest first. "iphone, one watch, reinstalls, no feed at times" runs `NO_FEED` on the iPhone's link.
  - **With A2 reverted** (`forgotten()` as before Ruling 50's fix, `copy(confirmedSeq = null, confirmedFor = null)`), the committed model fails: 11 of the D1 configuration's 480 schedules break S on the committed seed, for example seed 53000005 "(S) UNCHANGED for RESCHEDULED at 72 while watch 0 holds null". With A2 it passes. Getting there took five tunings: without the directed reset the two features alone gave 0 failures in 45,000 schedules, because D1 needs the empty handshake served without a number, the pre-wipe acknowledgement after it, and the same feed asked again, in that order.
  - A larger run of the same code (`RUNS` 40,000 and the test's timeout lifted for that run alone, then put back): 400,000 schedules, 0 failures, 55 s on the JVM.
- `TestWatch.names` is per test: `FeedNames`, made by each test (the model makes one per run of its test), passed to each `TestWatch`; no process-wide cache.

**Mutants** (each applied, its suite run, then put back; the tree compared with the commit after):

| Mutant | Killed by |
|---|---|
| A2 reverted (`forgotten()` keeps `lastSeq`, `lastStandsFor` and `lastSent`) | the model (11 S failures), as well as Ruling 50's three plain tests |
| M9: the key read after the call | `onTheIphoneAHandOverCountsForTheWatchKeyReadBeforeTheCall` alone |
| the bridge blocking again (`runBlocking`, reply after) | `aHandshakeNeverBlocksWatchConnectivitysQueue` (60 s, "the call returned before the sync answered") |
| true for any answer but `FAILED` | `aHandshakeWithNoWatchOrNoFeedIsFalse` ("no watch ready") |
| no once-guard | `aHandshakeIsAnsweredOnceWhateverBecomesOfIt` ("no container: one reply", 2) |
| a closed queue not refused (`sync`'s `trySend` failure ignored) | `aHandshakeTheSyncCannotServeIsFalse` (its 5 s guard) |

**Red first.** The host tests failed on their own assertions with a wrong stub of `loggedDelivery` (6 failures: the record's bound, the sync's wedge, the stored origin, the three log-form tests); the iOS tests failed to compile against the old bridge (`refreshRequested(have, reply)`, `scope`, `logged`). M9's test passed on the code as it was, as a mutant-killing test must, and failed on the mutant.

### 8. The plan text and the comments

- First, on its own: plan 2 Task 6's Interfaces line consumes `WatchDelivery`, `isFeedPath`, `deliveryIn`, `deliveryOf`, `stores`, `echoOf` and `STORE_STEPS`, never `fingerprintIn`, which Ruling 50 removed from the contract.
- Plan 2 Task 8 as built: `start()` sets the hook before `activate()` (and why); Step 5's location was written into the settings file with the app stopped (and why), and the put-back's unpair (worded since by Ruling 53 item 4 as observed: after it, no pair and a Series 12 with no Taqwa app, which it never had, so an erase is not shown); `refreshRequested(have, reply)`, `logged`, the `watchKey()` guard and the asynchronous reply.
- Spec 4.2: the handshake is `["refresh": <its echo>]` with the `["queued": Bool]` reply, sent at activation, on reachability and on open; the context is stored only when its delivery moves the watch forward. Spec 1.2: the overflow rule and the log rule.
- `AppContainer.kt`'s `watchLink` KDoc (the iPhone's link is built) and `WatchFeedFingerprint.kt`'s (the fingerprint travels inside the delivery, on the path and under the `delivery` key).
- `2026-10-24-phone-feed-sync.md`'s "For the next tasks", rewritten for Rulings 50 and 52 (it named Ruling 48's on-their-way contract); `2026-10-23-iphone-watch-link.md`'s blocking bullet marked superseded.
- The named-case `Script`'s KDocs say what its `wiped` mark does: cleared by any acknowledgement, even one sent before the wipe (stricter than S), and not by a handshake served after it (looser than S).
- The model's timing text: 1,600 schedules (this line first said 1,920, which Ruling 53 item 4 corrected: the configurations summed to 1,600 then, and to 1,760 since P2.5), about 0.56 s on the JVM and 4.0 s on the simulator (it said half a second and three).
- Plan 4: Global Constraints and Tasks 5B, 6 (Step 6), 10 (Step 5) and 11 (Step 1) expect a fresh Series 12 with no Taqwa app after every unpair, pair again and install the watch app. (Ruling 53 item 4 rewrote this: each `ios-pair` task pairs and installs whenever it finds no pair, and leaves the two as it found them, unpairing only a pair it made.)

## The errata, and how each was resolved

1. **Items 1 to 8 only, the Interfaces line first on its own.** `45a5243d` holds plan 2 Task 6's text alone. It also carries Task 6's log lines (item 4), since Lane W's P2.6 builds its log lines from the same text; the Interfaces line itself is exactly item 8's. Item 9 is not touched.
2. **`iOSApp.swift`.** Only `TaqwaWatchSession`'s lines that items 4 to 6 name changed: its doc comment and `keyFile` comment, every `NSLog` in the class, `watchKey()`'s guard, `send`'s log lines, and the two `didReceiveMessage` methods. Dev 1's Qibla and widget lines are untouched (`git diff` shows hunks inside the class alone). `project.pbxproj` is untouched: no file was added to the Xcode project (`WatchFeedLog.kt` is Kotlin, in the framework).
3. **The Gradle flake.** Not seen: `scripts/test.sh` passed on its first run, with no `EOFException` in its log, and `EngineBudgetBenchmarkTest` passed with it.

## The checks

- `scripts/test.sh`, exit 0 on its first run (5 m 15 s): versions agree, 1.2.0 (38) and (100038); `test-bump-version.sh` PASS; shared host 2,143 (2,136 + 7) and iOS 2,108 (2,097 + 7 common + 4 iPhone), widgetcore 111/109, watchcore 119/117, wearApp 143; 0 failures, 0 skipped.
- `IosWatchLinkTest` 21/21. `aHandshakeTheSyncCannotServeIsFalse` 0.0 s (it was about 10 s), `aHandshakeIsAnsweredOnceWhateverBecomesOfIt` 1.0 s (three 300 ms waits for a second reply that must not come), `theLargestContextFitsTheSmallestLimit` 5.2 s.
- The model: 0.56 s on the JVM, 4.0 s on the simulator.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, then Task 1's grep over `shared.h`: prints nothing. The header has `refreshRequestedHave:reply:` with `void (^)(SharedBoolean *)` and `loggedDelivery:` (Swift `logged(delivery:)`).
- `scripts/ios-build.sh`: BUILD SUCCEEDED, `TaqwaWidget.appex: 3 MB (limit 15 MB)`. Its default destination is `name=iPhone 17 Pro`, which resolved to the `ios-pair` phone `2D01CE58` (compile only: nothing booted or installed, and no lock was held then); it was built again on the unlocked iPhone 17 Pro `ECD602E9` (`IOS_DEST`), BUILD SUCCEEDED. The one Swift warning is `iOSApp.swift:210`, the widget-placement hook's, as before; none in `TaqwaWatchSession`.
- `python3 tools/i18n-check.py`: clean. `git diff --check`: clean.

## Left as it was, for the record

- `watchKey()` still never creates the watch directory; Apple does not say when it is created. A key that cannot be written differs at the next read, so the phone sends once more and never takes another watch's confirmation (P2.8's choice).
- An echo above half a `Long` from this origin is forgotten, so a watch that somehow held such a number would refuse the phone's smaller numbers as stale until reset. No phone of ours numbers that high; Ruling 52 chose the forget.
- The `TaqwaHarness` lines in `iOSApp.swift` (DEBUG, the recitation harness) still interpolate into `NSLog`'s format; they are not the link's lines, and erratum 2 keeps the change to the lines items 4 to 6 name.

## Ruling 53 item 4: Ruling 52's review notes

Lane P (`watch-feed`), 25 October 2026, after P2.5's commit `588eca7a` (plan 2 Task 5), in a commit of its own, test-first. Devices: none. No lock, no emulator, no simulator by hand (the Kotlin/Native tests boot their own; `ios-build.sh` compiled for the unlocked iPhone 17 `D9C6CFA7`, which stayed shut down). `KB2604110095` and the work iPhone 12 untouched.

### (a) The handshake keeps WatchConnectivity's arrival order

`WatchBridgeCalls.refreshRequested` now launches its coroutine with `CoroutineStart.UNDISPATCHED`. The body runs on the caller's thread until its first suspension, so `sync().sync(WATCH_ASKED, have, from)` puts its ask on the sync's queue (`Channel.trySend`, which never suspends or blocks) before the call returns, and only the wait for the answer and the reply are off WatchConnectivity's queue. A handshake, a `watchStateChanged()` and another handshake are served in that order. With the dispatched launch of Ruling 52, both handshakes reached the queue after the change.

- Test: `aHandshakeReachesTheSyncInWatchConnectivitysOrder`, over a bridge scope on a `StandardTestDispatcher` that runs only when the test calls `runCurrent()`, and a real sync on `Dispatchers.Default`. Red over the old launch: `Expected <[WATCH_ASKED, WATCH_CHANGED, WATCH_ASKED]>, actual <[WATCH_CHANGED, WATCH_ASKED, WATCH_ASKED]>`.
- What now runs on WatchConnectivity's queue, and the KDocs say so: at the first call after a cold start, the lazy app container (as `watchActivated()` already built it there); UIKit's `beginBackgroundTask`; the key file's read under the session's `keyLock`; the `trySend`. None waits for the sync, the feed or the link. Item 6's earlier note that a handshake never builds the container on that queue is superseded (marked above).
- A reply can now come on the caller's thread too, when the ask is answered before it suspends (a key or a container that throws, a sync whose worker has ended). WatchConnectivity's reply handler may be called from any thread.

### (b) Background time until the reply

A handshake can wake the iPhone app in the background. The bridge now asks UIKit for background time first and ends it right after the reply, once:
- `internal interface HandshakeTime { begin(expired): ULong?; end(task) }`. `UIKitHandshakeTime` is `UIApplication.sharedApplication.beginBackgroundTaskWithName("TaqwaWatch handshake", expirationHandler)`, `UIBackgroundTaskInvalid` read as none, and `endBackgroundTask`. Apple's pages (read 25 October): both "can be safely called on a non-main thread"; the expiration handler is called "synchronously on the main thread", "shortly before the app's remaining background time reaches 0", and also "If the system is unable to grant the task assertion"; "Failure to end the task explicitly will result in the termination of the app".
- `HandshakeAnswer` replaces `ReplyOnce`: one reply, whichever path comes first (the sync's answer, a coroutine that ended without one, the expiration handler's false), then one `end`. An answer given before `begin` returned (the handler may run that early) ends the time as soon as `begin` stores it.
- Tests: `aHandshakeHoldsBackgroundTimeUntilItIsAnswered` (`begin 1` before the call returns, nothing more while the sync works, then `reply true`, `end 1`); `theBackgroundTimeEndsRightAfterTheReplyWhateverBecomesOfTheHandshake` (its bound passed, a key that throws, no container, the bridge's scope gone, the sync's worker gone: `begin 1`, `reply false`, `end 1` each; no time given: still answered, nothing ended); `theSystemTakingTheTimeBackAnswersAndEndsItThere` (the handler while the sync works, and inside `begin`: `reply false`, `end 1` at once, the ask still served, nothing more after). All three red over the skeleton.
- Plan 4's Task 5B Step 3 now records a handshake that cold-wakes the iPhone app (the phone app terminated, the watch app opened): the phone's `handshake have=` and `handshake answered` lines, whether WatchConnectivity woke the app and how long it took to answer.

### (c) The KDocs and the docs

- `WatchLink.ios.kt`: the bridge's "none of its calls blocks" now says what runs on the queue (above); `refreshRequested`'s "called exactly once, from that coroutine" now names every thread a reply can come from.
- The delivery model's KDoc: 1,760 schedules (1,600 until P2.5 added the switched Android watches; the 1,920 above was wrong), measured over all 1,760, two runs each: 0.581 s and 0.583 s on the JVM, 3.819 s and 3.977 s on the simulator. Its iPhone premise "handshakes in order" holds again, by (a).
- `WatchFeedFingerprint.kt`'s KDoc reflowed (the 171-column line).
- Plan 2 Task 8: the interface line as built (a) and (b); Step 1 lists Ruling 52's tests and Ruling 53's. Review Focus 2 names Ruling 52's tests (`anEchoNumberedAboveHalfALongIsAnotherInstalls`, `aStoredOriginOfAnotherFormStartsAWholeNewRecord`, `anEchoAtTheLargestNumberNeverWedgesTheSync`, `onTheIphoneAHandOverCountsForTheWatchKeyReadBeforeTheCall`, item 9's, the handshake's) and Ruling 53's.
- The NSLog rule (plan 2 Global Constraints, "Logs") is scoped to release lines, `TaqwaWatch` and plan 4's watch lines; the `#if DEBUG` `TaqwaHarness` lines are outside it.
- `watchDirectoryURL`: the SDK header's "If the watch app is not installed for the selected Watch the value will be nil" and "will be deleted upon next launch if the watch app is uninstalled for the selected Watch, or that Watch is unpaired" are in `2026-10-23-iphone-watch-link.md`, item 5 above, `WatchSessionHook.watchKey`'s KDoc and the Swift `watchKey()` comment.
- Plan 4 (Global Constraints, Tasks 5B, 6, 10, 11) and plan 2 Task 8 Step 5: an `ios-pair` task leaves the two as it found them, pairing and installing when it finds no pair, and unpairing only a pair it made, the one exception to "never unpair". The erase-on-unpair claim is worded as observed: after plan 2's unpair, no pair and a Series 12 with no Taqwa app, which it never had, so an erase is not shown.
- Spec 4.2: the reply never makes WatchConnectivity's queue wait for the sync; the phone queues each handshake in its order and holds background time until it replies.

### Checks

- Red first: `a3bbce54` (WIP), 4 of 25 `IosWatchLinkTest` failed, each for its missing behaviour (the order above; `Expected <[begin 1]>, actual <[]>`; a 30 s guard waiting for an `end` that never came; the handler's `actual <[]>`). Green: `40c612d4` (WIP), 25 of 25.
- Mutants, each alone, run against `IosWatchLinkTest` and put back (status clean after each): the dispatched launch (killed by the order test); no late `end` after `begin` (by the handler test, inside `begin`); `end` before the reply (3 tests); no `begin` (3); the expiration handler ignored (1). 5 of 5 killed.
- `scripts/test.sh`: exit 0 (versions 1.2.0 (38)/(100038), bump PASS; shared host 2,173 / iOS 2,115, the 4 new tests; widgetcore 111/109, watchcore 119/117, wearApp 143; 0 failures, no `EOFException`).
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, then plan 2 Task 1's grep over `shared.h`: prints nothing. `HandshakeTime` is internal and not in the header; `refreshRequestedHave:reply:` is as before.
- `scripts/ios-build.sh` with `IOS_DEST` on the iPhone 17 `D9C6CFA7`: BUILD SUCCEEDED, `TaqwaWidget.appex: 3 MB (limit 15 MB)`; the one Swift warning is `iOSApp.swift:210`'s, as before.
- `python3 tools/i18n-check.py`: clean. `git diff --check`: clean.

## Ruling 54: P2.5's review notes

Lane P (`watch-feed`), 25 October 2026, on watch-feed's head after P2.5 was merged (`6507b452`), test-first, items 1 to 5. Devices: none. No lock, no emulator, no simulator by hand (the Kotlin/Native tests boot their own; `ios-build.sh` compiles for an unlocked iPhone that stays shut down). `KB2604110095` and the work iPhone 12 untouched. No release key read or used: the release is checked through `processReleaseMainManifest` and `minifyReleaseWithR8`, which do not sign.

- Base: `6507b452` (watch-companion with P2.5 merged).
- Commits (Taqwa identity, no trailer, nothing pushed):
  - `481a2352` "Watch feed: the Android sender asks Play services only through its public API, …": the code, the manifest, `wear.xml`, the Gradle inputs and the tests (items 1 to 5);
  - the commit after it: plan 2's text (item 2's "update plan 2's text to match", and items 1 and 4), the library check report's notes, and this section.
  - The WIPs `19033775` (red), `8d17ed57` (green), `24513c5f` (item 5's fixture) and `342dca57` (the docs) were folded into the two by a soft reset to the base. The two commits' tree is the last WIP's, but for this list.

### 1. Public API only

`PlayWatchNodes(context)` asks `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)`, in place of `GoogleApiAvailabilityLight`'s (`@ShowFirstParty`, `@KeepForSdk`, no public reference). In play-services-base 18.5.0 (`javap -c`) that method is `invokespecial GoogleApiAvailabilityLight.isGooglePlayServicesAvailable`, the same answer, and it shows nothing. No update prompt is shown, as before: nothing calls `getErrorDialog`, `showErrorDialogFragment`, `showErrorNotification`, `makeGooglePlayServicesAvailable`, `getErrorResolutionIntent`, `getErrorResolutionPendingIntent`, `setDefaultNotificationChannelId` or `startResolutionForResult` (the last added in review round 1, below).

The test, `WearCodesTest.playServicesIsAskedThroughItsPublicApiAndNothingCanShow`, reads what the compiled classes of `world.taqwa.app.watchfeed` refer to: every `Methodref` and `InterfaceMethodref` in their constant pools (`ClassReferences`, a test helper of 96 lines over JVMS 4.4). They must refer to `GoogleApiAvailability.getInstance` and `isGooglePlayServicesAvailable`, to nothing whose owner is `GoogleApiAvailabilityLight`, and to none of those eight methods of any Play services class (Ruling 55 item 2 corrected these two numbers, which said about 60 lines and seven). A host test cannot watch Play services keep from showing something, but it can hold what the code links against, whatever the source looks like and whatever its comments say. The availability lambda's body compiles into `PlayWatchNodes._init_$lambda$0`, a static method of `PlayWatchNodes` itself (Kotlin's `invokedynamic` lambdas). The helper reads every class file beside it in the directory or jar it was loaded from.

### 2. The manifest tied to the contract

- The two `MESSAGE_RECEIVED` filters are literal: `android:path="/taqwa/refresh"` and `android:path="/taqwa/ack"`, never `pathPrefix`. Play services now wakes the service for those two paths alone. `takeWatchMessage` still ignores anything else, whatever reaches it.
- `PhoneWatchListenerServiceTest.theManifestWakesTheListenerForTheContractsPathsAlone` parses `androidApp/src/main/AndroidManifest.xml`. It finds the service by `PhoneWatchListenerService::class.java.name` and checks that it is exported. Each of its three filters must have exactly the `android:` attributes `scheme`, `host` and `path` (so no prefix, pattern, suffix or advanced pattern), `wear`, `*`. In order, they must be `MessageClient.ACTION_MESSAGE_RECEIVED` on `REFRESH_PATH` and on `ACK_PATH`, then `CapabilityClient.ACTION_CAPABILITY_CHANGED` on `"/" + WATCH_CAPABILITY`.
- `thePhonesCapabilityIsTheContractsAndKept` parses `res/values/wear.xml`: `android_wear_capabilities` is `[PHONE_CAPABILITY]`, and `tools:keep` names it.
- Both files are Gradle inputs of `:shared`'s tests (`phoneWearDeclarations` in `shared/build.gradle.kts`), so an edit to either re-runs the test instead of leaving it green from the cache (as Ruling 44 did for the strings).
- Plan 2 Task 5's text, the manifest comment and `wear.xml`'s comment say so.

### 3. Two mutants killed

- **G1** (any `getCapability` `ApiException` read as no Wear API). `AndroidWatchLinkTest.aCapabilityQueryThatFailsOtherwiseIsAFailure` covers `CANCELED` (16, `ConnectionResult.API_UNAVAILABLE`'s number), `INTERNAL_ERROR`, `TIMEOUT`, `NETWORK_ERROR` and `ERROR`, through the real `PlayWatchNodes` and `await()`. Each is thrown as itself (`assertSame`), the ask is `FAILED` with nothing built, and the next ask is `SENT`.
- **G2** (no `ensureActive` in the send's `catch`). `aSendCutByTheBudgetCallsNoFurtherNode` has two nearby nodes, and the first's message never answers. The ask is `FAILED` at 5 s, only A was handed a message, and the next ask sends to A and B.

### 4. One capability reading per ask

- `WatchLink` gains `suspend fun <T> oneAsk(block: suspend () -> T): T = block()`. `WatchFeedSync` serves each ask as `withTimeoutOrNull(budget) { link.oneAsk { answer(ask) } }`, so the reading is inside the ask's budget.
- `AndroidWatchLink.oneAsk` runs the ask under a coroutine context element of its own (`AskReading`). `reachable()` and `watchKeys()` share the one reading kept there, taken at its first need. It never outlives the ask, and no other ask sees it. Outside an ask, each reads for itself.
- `send()` still asks again at the send, as plan 2 requires.
- The KDoc gives the count. An ask makes one query when it sends nothing (no watch, no feed, a feed that cannot be encoded, or one the watch holds) and two when it sends. Never three, since this link never answers `TOO_LARGE`. An acknowledgement makes none.
- The ruling's "reachable(), watchKeys() and send() share it … send() still re-asks nearness at the send" is read this way: the reading is the ask's, and the send's own re-ask is the second query. The iPhone's link keeps the default, which reads nothing.
- Tests:
  - `AndroidWatchLinkTest.anAskReadsTheWatchesOnceAndAgainAtTheSend`, over the real `PlayWatchNodes`, counting `getCapability` calls. `NO_WATCH` makes 1, `SENT` 2, an acknowledgement 0, and `UNCHANGED` makes 1 for each of `RESCHEDULED`, `WATCH_APPEARED` and `WATCH_ASKED`. `NO_FEED` makes 1. A watch switched to after that is read afresh at the next ask: `SENT` to B, 2.
  - `aWatchGoneByTheSendOfAnAskIsNotSent`: the ask's reading has A nearby, the send's has it reached only through the cloud. The ask is `NO_WATCH` with 2 queries, and no message goes.

### 5. Tests and docs

- **`bounded()`.** The listener's wrapper is now `internal fun bounded(bound: Duration = WatchFeedSync.CALLER_BOUND, body)`. The service calls it with the default. `PhoneWatchListenerServiceTest` runs each of the three bodies on a thread of its own, as Play services runs them, waiting at most 20 s:
  - `aContainerThatThrowsCostsTheListenerThatMessageAlone`: a container getter that throws. Each body returns and throws nothing, and the next one is served.
  - `aSyncWhoseWorkerIsGoneHoldsTheListenerNoLongerThanItsBound` uses two syncs. In one, the scope was cancelled, so the queue is closed and each body returns at once. In the other, the worker never serves: its dispatcher is a `StandardTestDispatcher` that nothing drains, and its own caller bound is an hour. There the wrapper's 300 ms cuts each body.
- **`largestFeed`.** It moved into fixtures, so no test class builds another (`WatchFeedSampleTest().largestFeed(…)` is gone):
  - `WorstFeeds.largestFeed(location, format, language, spoken, kind, now, timesFor)` in commonTest, beside `WorstFeeds.CITY` and `presets`, is the one worst-case build;
  - `CapeTownSample` (androidHostTest) holds the committed samples' place, moment and days (from `WatchFeedSampleTest`'s companion and members, moved) and `largestFeed(tag, kind)`, Android's.
  - `WatchFeedSampleTest.theLargestFeedStaysWithinItsBudget`, `AndroidWatchLinkTest.aFeedAtTheBudgetIsWithinTheMessageLimit` and `IosWatchLinkTest.theLargestContextFitsTheSmallestLimit` all build there.
  - The nine largest feeds measure the same through the old helper and the new one: the old `WatchFeedSampleTest` checked out for one run, then put back. For example, bn-BD/12 CONTROL is 79 phrases and 59,968 UTF-8 bytes, and its message path 65 bytes.
- **`WatchFeedSync.acknowledge`'s KDoc** now says what really happens on the iPhone. Nothing calls it there, because the watch shakes hands and never acknowledges. One made there is read as a handshake's echo from the key it names. So one with no key is ignored while the session's watch has a key, and taken in for the session's watch while it has none (`watchKey()` null). The old "one with no key is ignored" held only in the first case. `WatchFeedSyncTest.onTheIphoneAnAcknowledgementWithNoKeyIsAKeylessWatchsWord` holds both halves.

### The errata, and how each was resolved

1. **Ruling 54 items 1 to 5, test-first on `6507b452`.** Built from there, with WIPs as I went, folded into the commits below. Where a test was written for behaviour that already held (items 3, 5 and the `wear.xml` half of item 2), its red is shown by a mutant instead (below).
2. **Rulings 50, 52 and 53.** The feed still goes only to nearby nodes with `taqwa_watch`, over `MessageClient` (no `DataClient` anywhere). The send still re-asks nearness. No log line was added or changed, so release logs still carry no origin, place or bearing (`TaqwaWatch ACK <seq>/<fingerprint>`, `TaqwaWatch no Wear API <code>`).
3. **The Gradle flake.** See the checks.

### Checks

- **Red first.**
  - The listener test would not compile: `bounded` was a private member of the service.
  - With `bounded` lifted out unchanged, the WIP `19033775` failed 4 of 77, each for its missing behaviour:
    - `playServicesIsAskedThroughItsPublicApiAndNothingCanShow`: no `GoogleApiAvailability.getInstance()`;
    - `anAskReadsTheWatchesOnceAndAgainAtTheSend`: a send made 3 queries, not 2;
    - `aWatchGoneByTheSendOfAnAskIsNotSent`: 3, not 2;
    - `theManifestWakesTheListenerForTheContractsPathsAlone`: `[host, pathPrefix, scheme]`.
  - Green: the WIP `8d17ed57`.
- **Mutants**, each applied alone to the green tree, run against the watch feed host tests (`AndroidWatchLinkTest`, `WearCodesTest`, `PhoneWatchListenerServiceTest`, `WatchFeedSyncTest`, `WatchFeedNamedCasesTest`, `WatchFeedRecordTest`, `WatchFeedMemoryTest`, `WatchFeedDeliveryModelTest`), then put back with `git checkout`. Status was clean after each. 12 of 12 killed:

  | Mutant | Killed by |
  |---|---|
  | G1, any capability `ApiException` read as no Wear API | `aCapabilityQueryThatFailsOtherwiseIsAFailure` alone |
  | G2, no `ensureActive` in the send's `catch` | `aSendCutByTheBudgetCallsNoFurtherNode` alone |
  | `bounded()` without its bound | `aSyncWhoseWorkerIsGoneHoldsTheListenerNoLongerThanItsBound` |
  | `bounded()` without its `catch` | `aContainerThatThrowsCostsTheListenerThatMessageAlone` |
  | `wear.xml` naming `taqwa_phones` | `thePhonesCapabilityIsTheContractsAndKept` |
  | `wear.xml` without `tools:keep` | `thePhonesCapabilityIsTheContractsAndKept` |
  | the ack path drifting to `/taqwa/acks` | `theManifestWakesTheListenerForTheContractsPathsAlone` |
  | the refresh filter back to `pathPrefix` | `theManifestWakesTheListenerForTheContractsPathsAlone` |
  | a keyless iPhone acknowledgement always ignored | `onTheIphoneAnAcknowledgementWithNoKeyIsAKeylessWatchsWord` and four older `WatchFeedSyncTest` cases |
  | the sync's ask outside `oneAsk` | both item 4 tests |
  | `send()` reusing the ask's reading | both item 4 tests |
  | the availability check through `GoogleApiAvailabilityLight` again | `playServicesIsAskedThroughItsPublicApiAndNothingCanShow` |

  G1 and G2 survived every older test, as the review found. Each is killed by its new test alone.
- **`scripts/test.sh`:** exit 0 on its first run (5 m 38 s), with no `EOFException` and `EngineBudgetBenchmarkTest` green.
  - Versions agree, 1.2.0 (38) and (100038); `test-bump-version.sh` PASS.
  - Shared host 2,183 (2,173 + 10 new) and iOS 2,116 (2,115 + the 1 common test), 0 failures, 0 skipped.
  - widgetcore 111/109, watchcore 119/117 and wearApp 143 were up to date, since no input of theirs changed.
- **The release manifest:** `:androidApp:processReleaseMainManifest`. The merged release manifest keeps the same 12 `uses-permission` entries, and `PhoneWatchListenerService` has its three filters, each `scheme="wear"`, `host="*"` and one literal `path` (`/taqwa/refresh`, `/taqwa/ack`, `/taqwa_watch`). No dependency changed, so the dependency and debug-APK diffs stand as recorded above.
- **The release dex:** `:androidApp:minifyReleaseWithR8` succeeds (never assembled or signed). Its `classes.dex` is 7,072,636 bytes (7,071,572 on `015830a6`).
  - `apkanalyzer dex packages --defined-only --proguard-mappings` (mapping loaded) gives 437 packages, as before. The tracker scan finds `androidx.media3.exoplayer.analytics` alone, and `com.google.firebase` appears 0 times in the mapping.
  - `GoogleApiAvailability` and its superclass `GoogleApiAvailabilityLight` are both kept, as R8 must keep a superclass.
- **Lint:** `:androidApp:lintDebug` gives 0 errors, 61 warnings and 2 hints, as before. The only manifest warnings are line 58's (`localeConfig`), none on a line changed here.
- **The iOS header:** `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, then plan 2 Task 1's grep over `shared.h`, prints nothing. Neither `oneAsk` nor `AskReading` is in the header.
- **`scripts/ios-build.sh`** with `IOS_DEST` on the unlocked iPhone 17 `D9C6CFA7`: BUILD SUCCEEDED, `TaqwaWidget.appex: 3 MB (limit 15 MB)`. The one Swift warning is `iOSApp.swift:210`'s, as before. The simulator stayed shut down.
- **Merges:** `git merge-tree` against `watch-wear` (`61d37e6a`) is clean, and `watch-companion` is still the base, `6507b452`.
- `python3 tools/i18n-check.py`: clean. `git diff --check`: clean.

### Left as it was, for the record

- `takeWatchMessage` keeps its "anything else is no message of ours" rule, though the literal filters now let only the two paths through. `theListenerServesEachMessageForTheWatchItCameFrom` still holds it.
- `bounded`'s production bound is `CALLER_BOUND` by default. The tests pass a shorter one, so they run in real time without waiting 10 s per body.
- The iPhone's `IosWatchLink` keeps `oneAsk`'s default. Its `reachable()` is a property read, and its `watchKeys()` the one session key.

### Review round 1: the reading before the build, a known limit

Lane P, 25 October 2026, on `303d2501`. The spec lens was clean. The code lens found one Important: the ask's one reading is taken at step 1, before the build (31 engine days, the DataStore reads, the resource read), and step 4's `watchKeys()` answers from it. Before Ruling 54, `watchKeys()` read afresh after the build, so the window was only that query and `memory.read()`. The reviewer's probe, in a scratch copy: watch A confirmed, and the nearby set switched to {B} inside `feedFor`. At `6507b452` that ask was `SENT` to B. At `303d2501` it was `UNCHANGED`, B was sent nothing, and only the next ask (`WATCH_APPEARED`) sent to B. No known limit listed it, no test held it and no document said it.

**Built: option (a), a known limit.** The finding left two fixes to the Lead's ruling: (a) accept it as a known limit, documented and tested, or (b) read the watches again at step 4. (b) contradicts Ruling 54 item 4, under which `reachable()` and `watchKeys()` share one reading. It would also cost a third capability query on every ask that sends, and a second on every `UNCHANGED`. A finding that contradicts a ruling keeps the ruling, so (a) is built. If the Lead rules (b) instead, the new test flips at its first assertion, as the mutant below shows.

**Why it heals.** Whatever tells the phone of the switch comes after the switch, and so after the reading: B's `CAPABILITY_CHANGED` (`WATCH_APPEARED`) and its handshake on finding the phone nearby (`PHONE_NEARBY`). So it is queued behind the ask and served on a reading of its own. Once A has left, A is not nearby there and B is not confirmed, so B's capability change sends. If A is still nearby when it is served, A's confirmation still counts, that ask is damped too, and B's handshake heals it, since B's own echo is taken in for B. (Ruling 55 item 1 corrected this paragraph, which said either heals.) The limit is of a kind with call 1's second watch, and narrower, since the switch must land inside one ask's build.

- **The test:** `AndroidWatchLinkTest.aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk`.
  - The test's `AndroidSync` gains a `duringBuild` hook that runs inside the build.
  - For each heal, the listener's own body (`takeCapabilityChange`, or `takeWatchMessage` on `REFRESH_PATH` with B's echo of nothing) starts undispatched inside the build, so its ask is queued while the feed is being built.
  - Asserted: the switched ask is `UNCHANGED`; the reports are `SENT, UNCHANGED, SENT`; the messages went to A, then to B; and three capability queries were made (the switched ask's one reading, then B's ask's reading and its send's).
- **Its red, by mutant.** Each mutant was applied alone to `AndroidWatchLink`, run against `AndroidWatchLinkTest`, then put back with `git checkout`.
  - Option (b) as a mutant (`watchKeys()` reading afresh, the base's behaviour, as in the probe): the test fails at its first assertion (`SENT` for `UNCHANGED`), and so do item 4's two counting tests (3 queries, not 2).
  - The reading kept across asks (one `AskReading` for the link): the test fails at the heal (`SENT, UNCHANGED, UNCHANGED`), and so do four older tests.
- **The docs:**
  - KDocs: `WatchFeedSync`'s step 4, `AndroidWatchLink`'s (a known-limit bullet, and its "never inherits" now says "between asks"), and `WatchLink.oneAsk` and `watchKeys`;
  - the test KDocs: `aSwitchedWatchNeverInheritsAnotherWatchsConfirmation` covers a switch between asks, which is what its name stands for, and `WatchFeedDeliveryModelTest`'s world moves only between asks, so the limit lies outside the model;
  - plan 2: call 1's and Review Focus 2's lists of known limits, Task 5's interfaces, and Ruling 54's section;
  - the spec's known limits, and its "never inherits", now "between asks".
- **Minors fixed:**
  - Plan 2 Task 5's As built said `watchKeys()` is one capability query. It now says that was as first built, and that since Ruling 54 item 4 it is the ask's one reading.
  - `playServicesIsAskedThroughItsPublicApiAndNothingCanShow`'s show list gains `startResolutionForResult`.
- **Minors left** (also in the ledger):
  - `ClassReferences` reads only `Methodref`s and `InterfaceMethodref`s, and only in one package.
  - `bounded()` is a generic package-level name.
  - The service's `appContainer` read inside `bounded` is held by no test (closed by Ruling 55 item 2, below).
  - The spec lens's two surviving mutants: the service's `onMessageReceived` calling its body unbounded, and `bounded` at ten times its bound.
- **Checks:**
  - `scripts/test.sh`: exit 0 on its first run (5 m 40 s), with no `EOFException`. Versions agree, 1.2.0 (38) and (100038), and `test-bump-version.sh` PASS. Shared host 2,184 (2,183 + the new test) and iOS 2,116, widgetcore 111/109, watchcore 119/117 and wearApp 143, with 0 failures and 0 skipped.
  - After the show list's one line: `WearCodesTest` 7, `AndroidWatchLinkTest` 23 and `PhoneWatchListenerServiceTest` 4, re-run with `--rerun`, 0 failures.
  - No declaration changed (KDocs, test code and docs only), so the release manifest, the dex, the iOS header and `ios-build.sh` stand as above. `python3 tools/i18n-check.py` and `git diff --check` are clean, and `git merge-tree` against `watch-companion` (`431d4e99`) and `watch-wear` (`95a29dc8`) is clean.
  - Devices: none. No lock, no emulator, no simulator by hand. `KB2604110095` and the work iPhone 12 were untouched.

## Ruling 55 items 1 and 2: Ruling 54's review notes

Lane P (`watch-feed`), 29 October 2026, on watch-feed's head after P4.1 was merged (`0c6f7e9e`), test-first. There is no behaviour change: two test classes gain cases, and the rest is KDocs and docs. Devices: none. No lock, no emulator, no simulator by hand (the Kotlin/Native tests boot their own). `KB2604110095` and the work iPhone 12 untouched. No release key read or used.

- Base: `0c6f7e9e` (watch-companion with P4.1 merged).
- Commits (Taqwa identity, no trailer, nothing pushed):
  - `89040c19` "Watch feed: a watch switched to during the build is healed by its capability change once the old watch has left, otherwise by its handshake, …": the KDocs and the tests (items 1 and 2);
  - the commit after it: plan 2's text, spec 1.2, the library check report, and in this report the corrections above and this section.
  - The WIPs `2a5731e5` (item 2's tests), `730722b1` (item 1's case) and `e0a7b238` (the wording) were folded into the two by a soft reset to the base.

### Item 1: the known limit ratified, its wording, and the stronger handshake case

**The wording.** Round 2's spec lens probed A still nearby when B's capability change is served. There A's confirmation still counts (it is the two-watches limit of Ruling 53 item 3), so that ask is damped too, and only B's handshake heals it. The docs said either heals. They now say the capability change heals it once the old watch has left, and otherwise the handshake does, in:
- `WatchFeedSync`'s step 4 (and "ratified by Ruling 55 item 1");
- `AndroidWatchLink`'s known-limit bullet;
- `WatchLink.oneAsk` ("only at its own next ask" is now "only at an ask of its own: its capability change once the old watch has left, otherwise its handshake");
- the KDoc of `AndroidWatchLinkTest.aWatchSwitchedToDuringTheBuildIsSentTheFeedAtItsOwnNextAsk` ("Either sends it the feed"), which now points at `aSecondWatchBesideTheFirstIsSentTheFeedAtItsOwnHandshake` for the damped half;
- plan 2: call 1, Review Focus 2, Task 5's interfaces and its Ruling 54 block, and a new Ruling 55 block. Call 1 also records the ruling: option (a) ratified, option (b) not taken, and the middle option (a second reading after the build, shared by `watchKeys()` and `send()`) not taken because it moves the send's nearness reading away from the send;
- spec 1.2's known limits;
- this report's "Why it heals" (round 1, above), corrected in place with a note.

The ledger does not list the reviewer's five places by name. The list above is every place that said either heals, so it covers them.

**"Between asks."** `WatchFeedRecord.holds`'s KDoc (`WatchFeedMemory.kt:54`) now says a watch switched to between asks never inherits another's confirmation, and that one switched to while an Android ask builds its feed is not in that ask's keys (step 4's known limit). The library check report's line 102 says the same.

**The stronger handshake case:** `AndroidWatchLinkTest.aWatchSwitchedToDuringTheBuildHoldingAnOlderDeliveryIsSentTheFeedAtItsHandshake`.
- B is worn first with an older feed, delivery 1, which it acknowledges. Then A is worn and the feed changes: delivery 2 goes to A, A acknowledges it, and a reschedule is `UNCHANGED`.
- During the next reschedule's build the user goes back to B, and B's handshake (the listener's own `takeWatchMessage` on `REFRESH_PATH`, started undispatched inside the build) echoes its delivery 1.
- Asserted: the switched ask is `UNCHANGED`; the reports are `SENT, SENT, UNCHANGED, UNCHANGED, SENT`; the messages went to B, A, then B; 3 capability queries were made after the setup; the record names B at number 1, below `lastSeq` 3, which stands for the latest feed. B's acknowledgement of number 3 then makes a reschedule `UNCHANGED`, with no further send.
- Why it is stronger: an echo of nothing forgets the confirmation and sets a number aside, so any rule sends after it. An echo of an older delivery of this install's must be confirmed at its own number, below the latest, for B to be sent the feed.

**Its red, by mutant.** Each mutant was applied alone, run, then put back with `git checkout` (status clean after each):

| Mutant | `AndroidWatchLinkTest` (24) | `world.taqwa.app.watchfeed.*` host (195) |
|---|---|---|
| M1: `echoed`'s last branch confirms `lastSeq`, not the number echoed | 1 fails: the new case alone (`… UNCHANGED, UNCHANGED, UNCHANGED`) | 16 fail |
| M2: `holds` without `confirmedSeq == lastSeq` | 1 fails: the new case alone | 20 fail |
| The reading kept across asks (one `AskReading` for the link) | 6 fail, the new case at its setup ("A holds the latest": `SENT`) | not run |

- Both M1 and M2 were already killed elsewhere (`WatchFeedRecordTest`, `WatchFeedSyncTest`, `WatchFeedNamedCasesTest`). Inside the switch's own window, the switch test's echo-of-nothing iteration passed under both, and only the new case catches them.
- The kept-reading mutant, which round 2 found the handshake iteration passes: no handshake can catch it. B's echo is taken in for B, and B is never among a stale reading's keys, so B's handshake sends under either reading. The capability-change iteration is the one that holds the fresh reading (`SENT, UNCHANGED, UNCHANGED` under the mutant), as round 1 showed.

### Item 2: the test minors

- **`bounded()` and any `Exception`.** `PhoneWatchListenerServiceTest.aContainerThatThrowsCostsTheListenerThatMessageAlone` now runs each of the three bodies with a container that throws an `IllegalStateException`, an `UninitializedPropertyAccessException` (what the lateinit `appContext` throws before it is set) and an `IOException` (checked, so not even a `RuntimeException`).
  - `bounded()` narrowed to `catch (e: IllegalStateException)`: the `IllegalStateException` iteration passes, and the test fails at the `UninitializedPropertyAccessException` one. The service test below fails too.
  - Narrowed to `catch (e: RuntimeException)`: the test fails at the `IOException` alone.
- **The service reads its container inside `bounded`.** `theServiceReadsTheContainerInsideTheWrapper` builds the real `PhoneWatchListenerService` and calls its `onMessageReceived` (on `REFRESH_PATH`, then `ACK_PATH`) and `onCapabilityChanged` (`taqwa_watch`, node A nearby), each on a thread of its own as Play services runs them, over test fakes of `MessageEvent`, `CapabilityInfo` and `Node`. Each must return and throw nothing on the service's thread.
  - A host test has no `appContext`, so `appContainer` cannot be built there: the first build throws `UninitializedPropertyAccessException` at `createWatchLink()` (`PlayWatchNodes(appContext)`; a probe's stack trace, not committed: `getAppContext`, `createWatchLink`, `AppContainer.<init>` at `AppContainer.kt:193`). That comes before `watchFeedSync` is made, so no worker starts. The test asserts that first, so a test run that can one day build the container fails there instead of passing for another reason.
  - `WearableListenerService`'s constructor runs over the host test's `android.jar`, whose constructors keep only their `super` call.
  - The mutant (each override reading `appContainer.watchFeedSync` into a `val` before `bounded`) fails at the handshake; the same mutant on `onCapabilityChanged` alone fails at the capability change. `aContainerThatThrowsCostsTheListenerThatMessageAlone` passes under both, as the round-2 code lens found.
  - Round 1's "the service's `appContainer` read inside `bounded` is held by no test" is marked closed above.
- **Section 1's numbers:** `ClassReferences` is 96 lines, and the show list has eight names, `startResolutionForResult` added in round 1. Ruling 54's section 1 now says so and names the eighth.

### The errata, and how each was resolved

1. **Not a plan task: Ruling 55 items 1 and 2, on watch-feed after P4.1 landed.** Built exactly those on `0c6f7e9e`: the wording, one stronger test case and two tests that kill the surviving mutants (the non-`IllegalStateException` throw and the container read outside `bounded`). No behaviour change: `git diff 0c6f7e9e -- '*/src/*Main/*'` is comments only. Two commits, each "Watch feed: <what> (Ruling 55)".
2. **The Gradle flake** (`EOFException` in `:shared:testAndroidHostTest`'s worker, `EngineBudgetBenchmarkTest` under load). Neither appeared: `scripts/test.sh` passed on its first run, with no `EOFException` and `EngineBudgetBenchmarkTest` green, so nothing needed a re-run.

### Checks

- **Red first,** by mutant, since the behaviour already held: the tables and lists above. Green: the WIPs `2a5731e5` (`PhoneWatchListenerServiceTest` 5 of 5) and `730722b1` (`AndroidWatchLinkTest` 24 and `PhoneWatchListenerServiceTest` 5, 29 of 29).
- **`scripts/test.sh`:** exit 0 on its first run (8 m 14 s; 103 tasks, 22 executed), with no `EOFException` and `EngineBudgetBenchmarkTest` green.
  - Versions agree, 1.3.0 (39) and (100039); `test-bump-version.sh` PASS.
  - Shared host 2,305 (2,303 + the 2 new tests: `AndroidWatchLinkTest` 24, `PhoneWatchListenerServiceTest` 5) and iOS 2,258, with 0 failures and 0 skipped.
  - widgetcore 111/109, watchcore 130/117 and wearApp 143 were up to date or from the cache, since no input of theirs changed.
- **No declaration changed** (KDocs, test code and docs only), so the release manifest, the dex, the iOS header and `ios-build.sh` stand as recorded for Ruling 54.
- `python3 tools/i18n-check.py`: clean. `git diff --check`: clean. `git merge-tree` against `watch-wear` (`d396be38`) and `watch-apple` (`0c6f7e9e`) is clean, and `watch-companion` is still the base, `0c6f7e9e`.

### Left as it was, for the record

- The kept-reading mutant at the handshake, by construction (above).
- Round 1's other minors stand: `ClassReferences` reads only `Methodref`s and `InterfaceMethodref`s, in one package; `bounded()` is a generic package-level name; `bounded` at ten times its bound survives. Of the spec lens's "`onMessageReceived` calling its body unbounded", the form with no wrapper at all (`runBlocking { … }`, no `catch`) is now killed by `theServiceReadsTheContainerInsideTheWrapper`. The form that keeps a `catch (e: Exception)` but drops the time bound still survives, since the host test's container throws at once.
