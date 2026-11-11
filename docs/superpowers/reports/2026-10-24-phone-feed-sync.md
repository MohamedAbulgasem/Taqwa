# Plan 2, Task 4: when the phone sends, never at the reminders' cost

Lane P (`watch-feed`), 24 October 2026. Plan 2 Task 4 (P2.4), with Ruling 45's Compose stability file, its review fix round 1, and Ruling 48's follow-ups.

- Base: `4c59eef2` (watch-companion after P3.3's merge).
- Commits, in order:
  - `d5fc197d`, the plan's Step 5 message. The WIP `bd54f573` (the green code and tests) was folded into it by a soft reset; the first mutant table below ran on it, and its code equals the commit's but for one test KDoc and this report.
  - `a5b3fb4e`, review fix round 1 ("the iPhone reloads its widgets before it gives the watch its turn, and a handshake with the current feed leaves a later one pending, after review"; WIP `2df37d38` folded in). The Lead merged it into `watch-companion` as `79b1ea6f`, and `watch-feed` fast-forwarded to it.
  - Ruling 48's follow-ups on `79b1ea6f`: `605d4594`, then its review fix round 1 (`15b050bd`), round 2 (`dba75dc7`) and round 3 (the commit after it). See the sections of those names below.
- No device, no emulator, no lock: the JVM and Kotlin/Native's simulator tests only. The plugged-in phone was not touched.

## What was built

| File | What |
|---|---|
| `shared/.../watchfeed/WatchLink.kt` | `WatchSendResult`, the `WatchLink` interface (`confirmsDelivery`, `reachable`, `send(json, fingerprint)`), `NoWatchLink` and `expect fun createWatchLink()`. |
| `shared/.../watchfeed/WatchLink.{android,ios}.kt` | The interim `actual`s, both `NoWatchLink`; Tasks 5 and 8 replace them. |
| `shared/.../watchfeed/WatchFeedFingerprint.kt` | `watchFeedFingerprint` (FNV-1a 64 over the UTF-8 of `WatchFeedJson.encode(feed.copy(writtenAt = 0))`, 16 lowercase hex) and `fnv1a64Hex`. In `:shared` alone: the watch only echoes. |
| `shared/.../watchfeed/WatchFeedMemory.kt` | `WatchFeedMemory` and `DataStoreWatchFeedMemory` over the settings store, `SettingsKeys.WATCH_FEED_FINGERPRINT`; `forget` removes the key. |
| `shared/.../watchfeed/WatchFeedSync.kt` | `WatchSyncReason`; `WatchFeedSync` (`request`, `sync`, `acknowledge`, `settle`, `CALLER_BOUND` = 10 s); `settleTheWatch`, the bounded settle every background entry point runs last. |
| `NotificationCoordinator.kt` | `afterPlan: (GeoLocation) -> Unit = {}`, called inside the lock right after `scheduleAll`, wrapped in `try`/`catch (Exception)`, never without a location. |
| `TasbeehViewModel.kt` | `onPhrasesChanged: () -> Unit = {}`, called by `addCustom`, `updateCustom` and `removeCustom` after their `_state.update`, guarded the same way. |
| `AppContainer.kt` | `watchFeedScope`, `watchFeedMemory`, `watchLink`, `watchFeedFor` and `watchFeedSync`, all `internal` and declared before the coordinator; the coordinator's `afterPlan` only `request`s. `report` prints `TaqwaWatch <reason> <result> <ms>ms`. |
| `PrayerRoutes.kt` | The tasbeeh route's `onPhrasesChanged = { container.watchFeedSync.request(PHRASES_CHANGED) }`. |
| `SystemEventReceiver.kt` | The body moved into `afterSystemEvent(reschedule, refreshMirror, redrawWidgets, redrawAyah, settleWatch)`, each step in its own `runCatching`, the watch last, inside the receiver's 8 s. |
| `NotificationTopUpWorker.kt` | `topUp` takes a fourth step, `settleWatch = {}`, run last in its own `runCatching`. |
| `PrayerAlarmReceiver.kt` | `runCatching { settleTheWatch { appContainer.watchFeedSync } }` after the top-up. |
| `BackgroundRefreshBridge.kt` (iosMain) | The body moved into `afterBackgroundRefresh(reschedule, refreshMirror, redrawWidgets, settleWatch)` in fix round 1, each step in its own `runCatching`: the reschedule, the mirror, `refreshWidgets()` (Swift's `WidgetRefreshBridge` hook, so the widgets are reloaded before the watch's turn), then `settleTheWatch { appContainer.watchFeedSync }` last. The answer is whether the reschedule ran. `iOSApp.swift` is untouched; its own later `reloadWidgets()` is de-duplicated by `lastMirrorKey`. |
| `shared/compose-stability.conf`, `shared/build.gradle.kts` | Ruling 45 (below). |

## How the plan's design was built, and the few choices made inside it

- **The worker** is one coroutine on `scope`, launched on `work`, reading a `Channel(UNLIMITED)` of three kinds of entry: an ask (with an optional answer for `sync`), an acknowledgement and a settle marker. A settle is answered when the worker reaches it, so it is true exactly when every entry made before it has been served. `request` is a `trySend`, which neither suspends nor throws, even with the worker gone.
- **Step 0 runs inside the ask's budget** with steps 1–6. The plan puts steps 1–6 under the budget and says nothing of step 0's `memory.forget()`; inside, a store that never answers cannot stall the worker. Observable behaviour is the same: `aChangedWatchForgetsFirst` holds either way.
- **The memory is forgotten once per send,** before the first `link.send`; the `TOO_LARGE` resend needs no second forget. The pending fingerprint becomes each send's own, so the phrase-less resend's is the pending one after it.
- **The pending fingerprint after a send that did not answer `SENT`** stays as set: a send cut off by the budget may have been queued, and `aSendCutOffByItsBudgetClaimsNothing` requires its late acknowledgement to count.
- **An acknowledgement is served inside one budget too,** and never reported: `report` is once per ask, and an acknowledgement is not one.
- **Exceptions:** `attempt { }` catches every `Exception` and rethrows only through `currentCoroutineContext().ensureActive()`, so a stray `CancellationException` from a library is `FAILED` while the worker's own cancellation still ends it. `report` is called through it too, so a throwing callback cannot end the worker.
- **`settleTheWatch { sync }`** is the plan's `withTimeoutOrNull(CALLER_BOUND) { appContainer.watchFeedSync.settle() }`, written once and used by all four entry points and by the worker and receiver tests ("the wiring's own lambda"). The container is read inside it, so a container that cannot be built fails that step alone.
- **`watchFeedFor` builds a fresh `createPlatformFormat(clockFormat)` for every ask** (erratum 3), as `PlatformFormat.android.kt`'s comment from P2.1 says it does: an Android format's `NumberFormat` is never shared across threads. The language and the spoken words are read one after the other in the same call.
- **Test helpers:** `FakeWatchLink` (programmable reach, answers, per-send delays, a gate, throws; it records each send before any wait), `FakeWatchFeedMemory`, and `syncFeed`/`syncPhrase`, one small day whose Fajr moves by a minute as the Manual adjustments stepper's + does.

## Test first

1. **Step 2, red (compile).** With every test written and no production code, `./gradlew :watchcore:testAndroidHostTest :shared:testAndroidHostTest` failed in `:shared:compileAndroidHostTest`, 310 errors, all of the kind `Unresolved reference 'WatchFeedSync'`, `'WatchLink'`, `'WatchSendResult'`, `'DataStoreWatchFeedMemory'`, `'afterSystemEvent'`, `'settleTheWatch'`, and `No parameter with name 'afterPlan'` / `'onPhrasesChanged'` / `'settleWatch'`.
2. **Red (behaviour).** With the signatures in and every body `TODO()`, the touched classes ran (after two fixes to the tests' own compile: `fp` moved out of the test class for the harness, and the dispatcher read through `ContinuationInterceptor`, whose key needs no opt-in): 196 run, **44 failed**, every one on the missing behaviour: 39 on `NotImplementedError`, and 5 assertions on a call that never came (the coordinator's `[armed]` against `[armed, watch London]`, the two tasbeeh callbacks, the worker's settle, the slow watch's two reports). Two passed at once, as guards: `aWatchRequestThatThrowsNeverCostsTheReminders` and `withNoLocationTheWatchIsNotAsked`, which the stub's uncalled `afterPlan` satisfies. Mutants M1 and M2 below show they catch the faults they guard against.
3. **Green.** One test then failed for a test-harness reason, `requestNeverWaitsForTheWorker`: kotlinx-coroutines-test refuses a dispatcher of a second scheduler inside runTest's own scope ("Detected use of different schedulers"), so that test gives the sync a scope of its own on the second scheduler, as the coordinator's test does. Then all green.

## The tests

46 new tests in `d5fc197d`, every one named in the plan's Step 1 (plus a padding vector in `itIsTheFormTheContractExpects`). Fix round 1 added 3 (`aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives` and the two of `BackgroundRefreshBridgeTest`), and Ruling 48 adds 9 (its section below). The table is the first commit's, with the later rows marked:

| Where | Tests |
|---|---|
| `WatchFeedSyncTest` (commonTest, virtual time, fakes only) | 29: the plan's 29 names, `anUnchangedFeedIsNotSentAgain` to `settleWaitsForWhatWasAsked`. Each runs on both kinds of link where the plan says "on each kind" or "either kind". `aHandshakeWithoutAFingerprintIsSent` also sends the right fingerprint in capitals, which `isFingerprint` refuses. Fix round 1 made it 30, Ruling 48 makes it 38. |
| `WatchFeedMemoryTest` (commonTest, a real DataStore) | 4, order and outcome only; `itSurvivesANewStoreOnTheSameFile` cancels and joins the first store's `Dispatchers.IO + Job()` scope before the second opens the file. |
| `WatchFeedFingerprintTest` (commonTest) | 4; the published FNV-1a vectors ("", "a", "foobar") and a hash with two leading zeros ("13900" is `00f5898e9456454c`). |
| `NotificationCoordinatorTest` | 4 added: `theWatchIsAskedAfterThePlanIsArmed`, `theLockIsFreeWhileTheWatchIsSlow` (the sync on a second scheduler advanced by hand: `FAILED` at 5 s and 10 s), `aWatchRequestThatThrowsNeverCostsTheReminders`, `withNoLocationTheWatchIsNotAsked`. |
| `TasbeehViewModelTest` | 2 added: `addingEditingOrRemovingAPhraseTellsTheWatch` (three calls; a tap, a chip switch, a reset and a flush make none), `theWatchIsToldAfterTheChipIsShown`. |
| `SystemEventReceiverTest` (host) | `theWidgetsAreRedrawnWhileTheWatchLinkHangs`: armed, mirror, widgets, ayah all at 0 ms; settled true at 5,000 ms; returned before 8 s. |
| `NotificationTopUpWorkerTest` (host) | `theWatchIsSettledLastAndNeverCostsTheWidget`, `aStoppedWorkerCostsTheTopUpOnlyItsBound` (success within 10 s over a cancelled sync). |
| `BackgroundRefreshBridgeTest` (iosTest, fix round 1) | 2: `theWidgetsAreReloadedWhileTheWatchLinkHangs` (the widgets at 0 while the link hangs, the settle at 5 s) and `eachStepHasItsTurnAndTheAnswerIsTheReschedules` (every step runs whatever the others throw; the answer is the reschedule's). |

No existing test body changed: the only removed lines in the touched test files are two class KDocs (`SystemEventReceiverTest`'s "and nothing else", `NotificationTopUpWorkerTest`'s "three steps"), rewritten for the new tests.

### Mutants

To show the tests catch what they guard, each mutant below was applied alone to the WIP `bd54f573`, the named class run, and the file restored (`git checkout`), by a script in the lane's scratchpad:

| Mutant | Killed by |
|---|---|
| M1 `afterPlan` without its `try` | `aWatchRequestThatThrowsNeverCostsTheReminders` |
| M2 `afterPlan` called with no location | `withNoLocationTheWatchIsNotAsked` |
| M3 a confirming link remembers on `SENT` | `aConfirmingLinkRemembersOnlyWhatTheWatchAcknowledged` and 4 more |
| M4 no forget before the send | `aChangeUndoneBeforeItsAckIsSentAgain`, `aSendCutOffByItsBudgetClaimsNothing`, `anAcknowledgementIsServedInTurn` |
| M5 any acknowledgement counts, pending or not | `aLateAckForAnOlderFeedIsIgnored` |
| M6 the worker swallows its own cancellation | `cancellingTheScopeStillStopsTheWorker` |
| M7 `WATCH_CHANGED` does not forget | `aChangedWatchForgetsFirst` |
| M8 the feed built before the watch is asked for | `noWatchBuildsNothing`, `theWholeSyncHasOneBudget` |
| M9 the phrase-less resend keeps the first fingerprint | `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases` |
| M10b a budget for each send instead of one for the ask | `theWholeSyncHasOneBudget`, `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases` |
| M11 the matching handshake not remembered | `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered` |
| M12 the fingerprint keeps `writtenAt` | `theFingerprintIgnoresWrittenAt` |
| M13 the watch told before the chip row shows the phrase | both tasbeeh tests |
| M14 the watch settled first after a system event | `theWidgetsAreRedrawnWhileTheWatchLinkHangs` |
| M15 the top-up settles before the redraw | `theWatchIsSettledLastAndNeverCostsTheWidget` |

M10 itself (a per-send budget *added inside* the ask's budget) survived, as it should: the ask's own budget still governs, so it changes nothing. M10b removes the ask's budget, and is killed.

Fix round 1 and its reviews added:

| Mutant | Killed by |
|---|---|
| MA a matching handshake clears the pending feed | `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives` |
| MG the iPhone's settle before the widget reload | both `BackgroundRefreshBridgeTest` tests |

The reviews' MB (step 0 keeps the pending feed), MC (a phrase change never damped) and MD (`afterPlan` outside the lock) survived there, on rules with no test. Ruling 48 kills them: its table below.

## Step 4: `scripts/test.sh` twice (the first commit)

- **Run 1,** no arguments: exit 0, `BUILD SUCCESSFUL in 5m 37s`. Versions agree 1.2.0 (38) / (100038); `test-bump-version.sh: PASS`.
- **Run 2,** the same suites with every test task forced to run again (`scripts/test.sh :shared:allTests :widgetcore:allTests :watchcore:allTests` and each of the seven test tasks with `--rerun`), so that the second run is not answered from up-to-date checks: exit 0, `BUILD SUCCESSFUL in 4m 37s`, every test task executed.
- Both runs, from the JUnit results: shared host **2,074** (2,028 + 46), shared iOS simulator **2,014** (1,971 + 43: the commonTest ones), widgetcore 99 / 97, watchcore 112 / 110, wearApp 142; 0 failures, 0 errors. Every existing coordinator, receiver, worker and tasbeeh test passed unchanged.
- The known flake (erratum 4: an `EOFException` in `:shared:testAndroidHostTest`'s worker, `EngineBudgetBenchmarkTest` under load) did not occur in any run.

Also run:
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :androidApp:compileDebugKotlin`: green. Task 1 Step 4's header grep, widened to this task's names (`WatchLink`, `WatchSendResult`, `WatchSyncReason`, `NoWatchLink`, `WatchFeedMemory`, `watchFeed`, `settleTheWatch`, `afterSystemEvent`), prints nothing: every new declaration is `internal`. The public constructors gain only `afterPlan: (GeoLocation) -> Unit` and `onPhrasesChanged: () -> Unit`, and no Swift calls either constructor.
- `scripts/ios-build.sh` on the unlocked iPhone 17 simulator `D9C6CFA7` (built, never booted; the `ios-pair` iPhone 17 Pro untouched): `** BUILD SUCCEEDED **`, `TaqwaWidget.appex: 3 MB`.
- `git diff --check` clean.

## Ruling 45: the tasbeeh types stable again for Compose

`shared/compose-stability.conf` lists `world.taqwa.app.tasbeeh.*`, wired through `composeCompiler { stabilityConfigurationFiles.add(...) }` in `shared/build.gradle.kts`. Every class in the package is immutable (data classes of `val`s, sealed data objects, stateless objects), which the file says, with the rule that a class added there must be too.

Proved with the Compose compiler's own reports for `:shared:compileAndroidMain` (`--rerun`), written by an init script in the scratchpad that sets `composeCompiler.reportsDestination`, so no build file was edited for it. Three trees: 6e72eabf (before P3.1 moved the package, a git-archive copy), the base 4c59eef2, and this task's tree.

| | Before P3.1 (6e72eabf) | Base (4c59eef2) | With the file |
|---|---|---|---|
| `TasbeehUiState` | `runtime class`: `runtime` preset, state | **`unstable class`**: `unstable` preset, state | `runtime class`: **`stable`** preset, state; `Uncertain(List)` for `custom` |
| `TasbeehSheet.EditCustom` | `runtime` | **`unstable`** | **`stable`** |
| `DhikrBlock`'s `dhikr` (×3) | runtime | **`unstable`** | **`stable`** |
| `PresetChip`'s `preset` (×2) | runtime | **`unstable`** | **`stable`** |
| `CustomDhikrSheet`'s `existing` | runtime | **`unstable`** | **`stable`** |

So `TasbeehUiState` infers as it did before P3.1 (a runtime class, its `List` field the only uncertainty, as then), and its parameters are stable. The only other differences between the base's and this tree's reports are the new members of this task (the container's five watch fields, `afterPlan`, `onPhrasesChanged`, the key, the sync's own classes).

## The errata and rulings, and how each was met

1. **Ruling 45:** built as above.
2. **Ruling 47:** none of its items is this task's to build. (1), (3), (4) and (6) are plan 3 and plan 4 tasks. (2) applies to "the next task that touches Fixtures": this task does not touch `Fixtures.kt` or any vector. (5) bears on the fingerprint: `watchFeedFingerprint` relies on "one feed encodes one text", and a `Map` in the feed (`WatchScreenWords`' presets) encodes in iteration order, so P3.10 building it in `TasbeehPresets.builtIn` order is what keeps the fingerprint the same across asks. Until then the builder leaves `screens` at its default, so nothing in today's feed depends on map order; a different order would only cost one more send, never a wrong `UNCHANGED`.
3. **Erratum 3:** `watchFeedFor` builds `createPlatformFormat(clockFormat)` afresh inside every ask, on the sync's worker, exactly as P2.1's comment in `PlatformFormat.android.kt` says.
4. **Erratum 4:** watched for; not seen.
- **Ruling 39:** no copy, string or privacy line changed; the feed's content is Task 3's builder, unchanged. The fingerprint key names no place and no person. **Rulings 41, 42 and 43:** no face, `watchface/`, `wearApp/` or `complication/` file touched.

## Fix round 1, in brief

`a5b3fb4e` (from round 1's two Importants): the iPhone's background refresh reloads the widgets before the watch's turn (`afterBackgroundRefresh`, above), and `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives` holds the rule that a matching handshake leaves the pending feed. `scripts/test.sh` twice, exit 0 both: shared host 2,075 / iOS 2,017, widgetcore 99 / 97, watchcore 112 / 110, wearApp 142, 0 failures; `ios-build.sh` BUILD SUCCEEDED. Round 2 of both lenses found it clean.

## Ruling 48: P2.4's follow-ups

The Lead's ruling of 24 October, items 1 to 7, built test-first on `79b1ea6f` (`watch-companion` after P2.4's merge, which brought in Dev 1's widgets and Qibla through the main merge `3c3683a0`, hence the higher base counts below).

### What changed, item by item

1. **A handshake during an undo** (`WatchFeedSync.answer`, step 4). A handshake that names the current feed while a *different* feed is pending answers `UNCHANGED` and remembers nothing: that feed may land after it, so the next ask sends the current feed once more. The reviewer's probe is now `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` (X acknowledged, Y sent, the − back to X, the handshake naming X served before the undo's reschedule): the reschedule is `SENT`, the link received [X, Y, X], and after Y's and then X's acknowledgements the memory names X. `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives` now expects the memory null after that handshake, and Y's late acknowledgement still counts.
   - "Different" is judged by `Delivery.isFor`: the pending feed is the current one when its own bytes are the current feed's, or the feed it stands for is (item 3). So a watch naming the very feed still pending is remembered (`aHandshakeNamingThePendingFeedIsRemembered`), and so are times alone pending as a stand-in once every phrase is deleted, when they are the feed itself (`theTimesAloneAreTheFeedOnceThePhrasesAreGone`, its third case).
2. **The three untested rules,** now each with a test that its mutant fails: `aChangedWatchForgetsThePendingFeedToo` (MB: with Y pending, `WATCH_CHANGED` with no watch is `NO_WATCH`, Y's late acknowledgement is not remembered, and Y goes again), `aPhraseChangeTheWatchAlreadyHoldsIsNotSentAgain` (MC, on both links: a `PHRASES_CHANGED` of the feed the memory names is `UNCHANGED` with nothing sent) and `NotificationCoordinatorTest.theWatchIsAskedBeforeTheNextRescheduleTakesTheLock` (MD: a reschedule started undispatched from inside `afterPlan` waits for the lock, so it is located only after the first has told the watch; outside the lock it is located before).
   - Ruling 48 writes MC's rule as "PHRASES_CHANGED is never damped". Read with the plan's step 4 and spec 1.2 (both bind), that names the mutant, the reviewers' "MC (PHRASES_CHANGED undamped)", and the rule the test holds is that a phrase change *is* damped.
3. **The over-limit feed damped** (spec 1.2). Each send is a `Delivery(sent, standsFor)`: the fingerprint the feed travels with, and the feed it was sent for. They differ only for the times alone after a `TOO_LARGE`, which stand for the full feed. What is remembered is `standsFor`, on both links: on the iPhone's at `SENT`, on Android's at the acknowledgement of the pending delivery's `sent`. So an unchanged over-limit feed answers `UNCHANGED` to a reschedule, a phrase change and a watch appearing (`anUnchangedFeedTooLargeForTheLinkIsNotSentAgain`, both links; `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases` now expects the full feed's fingerprint in the memory). The wire is unchanged: the second send still carries the times-alone feed's own fingerprint (`theFingerprintTravelsWithTheFeed`, the Global Constraint).
   - **The handshake.** The watch echoes the fingerprint it was sent, the times alone's. The sync keeps, in the process alone, the last times it sent as a stand-in (`standIn`), and a handshake naming them counts as naming the full feed, when that is the current feed. Only times this sync sent as a stand-in count: a watch holding the same times for another reason is sent the feed (`twoFeedsDeliveredOutOfOrderStillHealAtTheHandshake`: times alone, then the feed with a phrase, stored out of order, still heal at the handshake, the Lead's call 1). After a restart such a handshake costs one more send (the feed, its `TOO_LARGE`, the times), which records the stand-in again; never a wrong `UNCHANGED`.
   - **A persisted alternative, rejected.** Accepting at any handshake the times alone of the feed the memory names (no in-process record) would survive a restart, but it breaks the out-of-order healing above: mutant R3f, killed by that test.
4. **The worker's channel closed** when its job completes, by `invokeOnCompletion { asks.close() }` on the worker's `launch`. A `sync`, `acknowledge` or `settle` made after that is refused by `trySend` and answered at once (`FAILED`, a return, false), and `request` still returns. A completion handler, not the `finally` the ruling names: a scope cancelled before the worker started never runs the body, so a `finally` would never close the queue. `aSyncWhoseWorkerHasEndedAnswersAtOnce` tests both (the scope cancelled after the sync is built, and before); mutant R4b (the `finally`) fails its first case (corrected in review fix round 2: in both cases the scope is cancelled before the worker's first dispatch, so a `finally` never runs). An ask queued before the worker ended still waits its `callerBound`, as before (`aStoppedWorkerNeverHoldsACaller` unchanged).
5. **The log line.** `:shared` has no logger: the `TaqwaWatch` line in `AppContainer` is the only log call in its main source sets (grep for `println`, `Log.`, `NSLog`, `Napier` and `Logger`), and the plan's Tasks 5 and 7 read `TaqwaWatch …` from logcat's `System.out`. So `println` is kept, as the ruling allows, with a KDoc that says why; it prints the reason, the result and the milliseconds, never the place or the person.
6. **Hygiene:** this report (the commits, the counts, `BackgroundRefreshBridgeTest`, the new tests, the mutant tables); `BackgroundRefreshBridge`'s object KDoc (it does the task's whole work, in `afterBackgroundRefresh`'s order, not "only the rescheduling"); `WatchLink.kt`'s `NO_WATCH` (a link's answer is only "no watch"; "nothing was built" is the sync's, for an ask); the two second-scheduler scopes cancelled at the end of `NotificationCoordinatorTest.theLockIsFreeWhileTheWatchIsSlow` and `WatchFeedSyncTest.requestNeverWaitsForTheWorker`; `NotificationTopUpWorker.kt`'s imports in order. `WatchFeedSync`'s class KDoc says steps 4 and 5 and the closed queue as now built.
7. **Plan text:**
   - plan 2's Review Focus 2 names `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives` and `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo`;
   - Task 4's Files name `afterBackgroundRefresh` and `shared/src/iosTest/.../notifications/BackgroundRefreshBridgeTest.kt`; its Interfaces read "after the mirror and the widget reload" and describe `afterBackgroundRefresh`;
   - the execution map's P2.4 row lists `shared/build.gradle.kts` and `shared/compose-stability.conf` (Ruling 45), and its shared-files line for `shared/build.gradle.kts` names P2.4;
   - plan 2's two `qiblaKm` formula lines (the Global Constraints' place and Task 3's `place`) point at `WatchFeedBuilder.distance()`, which supersedes them (the phone's own string where the feed keeps its digits, Western throughout otherwise);
   - beyond the ruling's list, so that the plan does not contradict the code: Task 4's step 4 handshake bullet, step 5's `TOO_LARGE` bullet and the Exceptions bullet each say Ruling 48's rule, and Step 1's test list names the tests Ruling 48 adds. The Lead may drop these four lines.

### Test first

1. **Red.** With the tests written and no production change, `:shared:testAndroidHostTest` over `WatchFeedSyncTest` and `NotificationCoordinatorTest`: 48 run, **5 failed**, each on the missing behaviour: `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` (UNCHANGED for the undo's reschedule), `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives` (X remembered at the handshake), `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` and `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases` (the times alone's fingerprint remembered, not the full feed's), `aSyncWhoseWorkerHasEndedAnswersAtOnce` (an answer after 30,000 ms of virtual time, three bounds, against 0).
2. **Item 3 in two steps.** With the memory side in, `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` failed at the next line, the handshake naming the times alone (`SENT` against `UNCHANGED`); the stand-in record turned it green.
3. **Guards that pass on today's code,** written for rules already built (item 2) or for an alternative design (item 3): `aChangedWatchForgetsThePendingFeedToo`, `aPhraseChangeTheWatchAlreadyHoldsIsNotSentAgain`, `theWatchIsAskedBeforeTheNextRescheduleTakesTheLock`, `twoFeedsDeliveredOutOfOrderStillHealAtTheHandshake` and `aHandshakeNamingThePendingFeedIsRemembered`. Each was shown to bite by its mutant (table below). `theTimesAloneAreTheFeedOnceThePhrasesAreGone` was written after the stand-in's own feed check, and is shown to bite by R3e. Its third case (times alone still pending) was written first and failed (memory null against the times' fingerprint), and `Delivery.isFor` turned it green.

### The mutants

Each applied alone to the final code (WIP `bc42f384`), the `watchfeed`, `notifications` and `feature.tasbeeh` host tests run (261 tests), and the file put back with `git checkout`, by a script in the session scratchpad:

| Mutant | Killed by |
|---|---|
| MB step 0 keeps the pending feed | `aChangedWatchForgetsThePendingFeedToo` |
| MC PHRASES_CHANGED undamped | `aPhraseChangeTheWatchAlreadyHoldsIsNotSentAgain`, `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain`, `theTimesAloneAreTheFeedOnceThePhrasesAreGone` |
| MD afterPlan outside the lock | `theWatchIsAskedBeforeTheNextRescheduleTakesTheLock` (NotificationCoordinatorTest) |
| R1 a matching handshake remembers whatever is pending | `aFeedStillInFlightAtAHandshakeCountsWhenItsAckArrives`, `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` |
| R1b a matching handshake remembers nothing while anything is pending | `aHandshakeNamingThePendingFeedIsRemembered`, `theTimesAloneAreTheFeedOnceThePhrasesAreGone` |
| R1c the pending feed judged by the feed it stands for alone | `theTimesAloneAreTheFeedOnceThePhrasesAreGone` |
| R3a the wire fingerprint remembered on both links | `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases`, `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` |
| R3b the wire fingerprint remembered on the non-confirming link | `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases`, `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` |
| R3c the wire fingerprint remembered at an acknowledgement | `aFeedTooLargeForTheLinkGoesAgainWithoutItsPhrases`, `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` |
| R3d no stand-in at the handshake | `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` |
| R3e the stand-in without its feed check | `theTimesAloneAreTheFeedOnceThePhrasesAreGone` |
| R3f the times alone of the remembered feed accepted at any handshake (persisted inference) | `twoFeedsDeliveredOutOfOrderStillHealAtTheHandshake` |
| R4a the queue never closed | `aSyncWhoseWorkerHasEndedAnswersAtOnce` |
| R4b the queue closed in a finally | `aSyncWhoseWorkerHasEndedAnswersAtOnce` |

All 14 killed, none survived. MB, MC and MD are the reviews' names; the R ones are this round's own, one or more for each new rule. R1b first survived a run on WIP `e592795d`, which is why `aHandshakeNamingThePendingFeedIsRemembered` exists; R3f is the persisted alternative to the stand-in record (item 3). The code review then found R3g, which survived every one of these tests: see "Review fix round 1" below.

### `scripts/test.sh`

- **Run 1,** no arguments: exit 0, `BUILD SUCCESSFUL in 5m 40s`. Versions agree 1.2.0 (38) / (100038); `test-bump-version.sh: PASS`. (`widgetcore`'s and `watchcore`'s host tests came from the build cache: neither module changed.)
- **Run 2,** the same suites with each of the seven test tasks forced to run (`scripts/test.sh :shared:allTests :widgetcore:allTests :watchcore:allTests`, then each test task with `--rerun`): exit 0, `BUILD SUCCESSFUL in 4m 54s`, all seven executed.
- Both runs, from the JUnit results: shared host **2,092** and iOS simulator **2,036** (the base `79b1ea6f`'s 2,083 and 2,027, as the Lead's merge counted them, plus Ruling 48's 9: 8 in `WatchFeedSyncTest`, now 38, and 1 in `NotificationCoordinatorTest`, now 12); widgetcore 111 / 109, watchcore 112 / 110, wearApp 143; 0 failures, 0 errors. `BackgroundRefreshBridgeTest` 2, on the iOS simulator alone (iosTest).
- The known flake (an `EOFException` in `:shared:testAndroidHostTest`'s worker, `EngineBudgetBenchmarkTest` under load) did not occur in either run.
- Also: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :androidApp:compileDebugKotlin` green; `shared.h` names no new symbol (`Delivery` and `standIn` are private to `WatchFeedSync`, itself internal); `git diff --check` clean. `scripts/ios-build.sh` was not run: the only iosMain change is `BackgroundRefreshBridge`'s KDoc, and the Lead runs it after the merge.

### Rulings 39, 41, 42 and 43

Ruling 39 untouched: no copy, string or privacy line changed, nothing of the place in the log line or the memory (the memory holds a fingerprint; the stand-in record two, in the process alone). Rulings 41, 42 and 43: no face, `watchface/`, `wearApp/` or `complication/` file touched.

### Review fix round 1

The two-lens review of `79b1ea6f..605d4594`: the spec lens clean, the code lens one Important, a test gap with the code right.

- **The Important.** At a handshake the stand-in counts only when the watch names those very times (`it.sent == named` in `answer`'s step 4), and no test held that check. Mutant R3g, which drops it, passed all 237 `watchfeed` and `notifications` host tests: `anUnchangedFeedTooLargeForTheLinkIsNotSentAgain` tries only the good path, and `twoFeedsDeliveredOutOfOrderStillHealAtTheHandshake` has no `TOO_LARGE`, so it records no stand-in. Under R3g a watch that holds an older feed, and says so, is told `UNCHANGED` while an over-limit feed's stand-in is on record, and the memory names the full feed, so later reschedules are damped against a feed the watch does not hold: the wrong `UNCHANGED` that Review Focus 2 rules out.
- **The test,** the reviewer's probe: `aWatchHoldingAnOlderFeedIsSentTheFeedTooLargeForTheLink`, on both links. Y is remembered, and the link's limit is set to the size of W's times alone. W (X with three phrases), asked by `PHRASES_CHANGED`, is `SENT`: refused, then sent as its times alone, which are never acknowledged. The handshake naming Y is then `SENT`, and the link received [Y, W, times, W, times]. The memory is null on Android's link (nothing until the times' acknowledgement) and names W on the iPhone's (what it sent).
- **Shown to bite.** It passes on `605d4594`'s code, on the JVM and on the iOS simulator. With R3g applied alone and the `watchfeed` and `notifications` host tests run (238), it fails, and nothing else does: "confirms false: the watch holds Y, not the times expected:<SENT> but was:<UNCHANGED>". Run twice more with the test's loop cut to one link, it fails on each ("confirms true: …" on Android's link), so each link kills R3g by itself. The code and the test were put back with `git checkout`.
- **Plan text:** plan 2's Review Focus 2 and Task 4 Step 1's test list name the new test.
- **`scripts/test.sh`,** run twice as above (the second with each of the seven test tasks `--rerun`): exit 0 both times; shared host **2,093** and iOS simulator **2,037**, one more each, with `WatchFeedSyncTest` at 39 on both; widgetcore 111 / 109, watchcore 112 / 110, wearApp 143; 0 failures. Only a test and two documents changed, so no link, `ios-build.sh` or i18n run was needed.
- **Minors left for the Lead,** from both lenses, none fixed here; they are in the ledger.

### Review fix round 2

The two-lens review of `79b1ea6f..15b050bd` found one Important, the same from both lenses: Ruling 48's undo rule held on Android's link alone.

- **The Important.** On the iPhone's link (`confirmsDelivery` false) nothing is ever pending, so a handshake naming the current feed always remembered it. The undo's order there: X handed over, then Y; the − back to X; a handshake naming X, sent by the watch before it took Y, served before the undo's reschedule. The handshake remembered X, the reschedule answered `UNCHANGED`, and the system then delivered Y, the context it was handed last. So the watch stayed on the undone Y while the memory named X, until its next handshake: a wrong `UNCHANGED` against Review Focus 2. The spec lens's random model (1,500 runs a link, 25 events each, FIFO queues both ways with losses and stale handshakes) found it 4 times on the iPhone's link and never on Android's.
- **The fix,** the same rule on both links (`WatchFeedSync.nothingElseMayLand`). A matching handshake remembers only when no other feed may still land after it. On Android's link that is the pending rule as before. On the iPhone's, the feed handed over last (`handed`, set before each send as `pending` is, so a send cut off by its budget counts, and cleared at step 0 with the memory) must be the current one, by `Delivery.isFor`. Before the process has handed any feed over, the memory stands in: on that link it names the feed that got through last, so a handshake is remembered only when the memory names the current feed or nothing. The memory clause matters because the iPhone's app is often started by the watch's handshake itself. The two reviewers' candidates were each half of this: the code lens's (the last delivery alone) misses a relaunch, and the spec lens's (the memory alone, with the stand-in clause) misses a send cut off by its budget, whose memory is already forgotten. Ruling 48 is not contradicted: it names `pending`, Android's notion, and its "must end with the watch on X" now holds on both links.
- **The tests.**
  - `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` now runs on both links, each with Y sent and with Y's send cut off by its budget. Every case ends with [X, Y, X] sent, X handed over or acknowledged last, and the memory naming X.
  - `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered` now runs on both links: with nothing known, the watch's word is taken.
  - New: `aHandshakeAfterARelaunchDuringAnUndoEndsWithTheWatchOnTheUndo` (iPhone's link: a new sync over the same memory and link; the handshake naming X leaves the memory on Y, the reschedule sends X, and a third sync's handshake naming X costs no send).
  - New: `aChangedWatchForgetsTheFeedHandedOverToo` (iPhone's link: step 0 clears `handed` as it clears `pending`, so the changed watch's handshake is taken at its word).
  - New: `aHandshakeNamingTheTimesStillOnTheirWayIsRemembered` (both links: the times alone, still pending or with their send cut off by its budget, named by the watch, count for the full feed). It also closes round 1's minor (a), the untested `standsFor` arm of `Delivery.isFor`.
- **Red, then green.** With the first three written and `15b050bd`'s code, `WatchFeedSyncTest` ran 40 and 2 failed: the undo's ("confirms false, cut off false: never UNCHANGED, Y may still land expected:<SENT> but was:<UNCHANGED>") and the relaunch's ("Y was handed over last …": the memory named X). The handshake test on both links passed there, a guard for H3. The fix turned the two green, with 263 `watchfeed`, `notifications` and `feature.tasbeeh` host tests passing. The last two were written with the fix in, and are shown to bite by their mutants.
- **Mutants,** each applied alone to the final code, those host tests run (264 or 265), and the file put back:

| Mutant | Killed by |
|---|---|
| H1 the memory alone (the spec lens's candidate without its stand-in clause) | `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` (cut off), `theTimesAloneAreTheFeedOnceThePhrasesAreGone` |
| H2 the feed handed over alone, no memory fallback (the code lens's candidate) | `aHandshakeAfterARelaunchDuringAnUndoEndsWithTheWatchOnTheUndo` |
| H3 the memory fallback without its "nothing" arm | `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered`, `aChangedWatchForgetsTheFeedHandedOverToo` |
| H4 `handed` never set | `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo`, `theTimesAloneAreTheFeedOnceThePhrasesAreGone` |
| H5 step 0 keeps `handed` | `aChangedWatchForgetsTheFeedHandedOverToo` |
| H6 `handed` matched by its bytes alone | `aHandshakeNamingTheTimesStillOnTheirWayIsRemembered` |
| H7 `handed` set only on `SENT` | `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` (cut off) |
| H8 the iPhone's link back to the pending rule (`15b050bd`) | `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo`, `aHandshakeAfterARelaunchDuringAnUndoEndsWithTheWatchOnTheUndo` |
| X2 `Delivery.isFor` by its bytes alone (round 1's survivor) | `aHandshakeNamingTheTimesStillOnTheirWayIsRemembered`, on each link alone |

  All nine killed.
- **The reviewers' model, re-run** on the final code (the spec lens's probe file from the session scratchpad, copied in for the run and removed): 0 wrong `UNCHANGED` and 0 unhealed runs without restarts on either link; with restarts, 0 on the iPhone's link and 3 on Android's, all the known limit below. Its p4 (the iPhone's undo): the handshake `UNCHANGED`, the reschedule `SENT`, [X, Y, X] sent, X the last context.
- **The known limit, left.** On Android's link a process restarted while a feed is in flight forgets it (the pending fingerprint lives in the process, and the memory was forgotten at the send), so a stale handshake served then is remembered. The plan's call 1 said "never a wrong `UNCHANGED`" there. It now says this is the known limit, and so do `pending`'s KDoc and Review Focus 2. Closing it needs the pending feed persisted, which changes `WatchFeedMemory`; that is the Lead's call. The iPhone's link has a gap of the same kind after any send that did not answer `SENT`, then a restart (corrected in review fix round 3, which also found the gap in a single process that this sentence first left out).
- **Two minors fixed in passing,** both text: `WatchLink`'s `NO_WATCH` KDoc no longer says an ask's `NO_WATCH` always built nothing (a send's own `NO_WATCH` comes after the build), and the R4b line above names the case it fails. The other minors are left, in the ledger.
- **Plan text:** the Lead's call 1 (the restart limit, and the iPhone's handshake rule); Task 4's Interfaces (the feed handed over last, steps 0, 4 and 5); Step 1's list and Review Focus 2 name the new tests.
- **Checks.** `scripts/test.sh` twice (the second with each of the seven test tasks `--rerun`): exit 0 both times; shared host **2,096** and iOS simulator **2,040**, three more each, with `WatchFeedSyncTest` at 42 on both and `NotificationCoordinatorTest` 12; `BackgroundRefreshBridgeTest` 2; widgetcore 111 / 109, watchcore 112 / 110, wearApp 143; 0 failures; no `EOFException`. `:shared:linkDebugFrameworkIosSimulatorArm64` and `:androidApp:compileDebugKotlin` green, `shared.h` names no new symbol; `scripts/ios-build.sh` BUILD SUCCEEDED on the unlocked iPhone 17 `D9C6CFA7` (never booted); `git diff --check` clean.

### Review fix round 3

The two-lens review of `79b1ea6f..dba75dc7`: the spec lens clean with six minors, the code lens one Important.

- **The Important.** Ruling 48's undo rule still failed, on both links, when the undo's own send did not get through. `deliver` made the new feed the pending or handed one before `link.send` and never put it back, so `nothingElseMayLand` took that feed as the only one that could still land, even when the link refused it (`FAILED`, `NO_WATCH`) or the budget cut it off. An `updateApplicationContext` that throws does not replace the context (Task 8 maps the throw to `FAILED`), so the system goes on delivering the earlier context Y; on Android a send answered `FAILED` or `NO_WATCH` queued nothing, while Y may still be on its way. The reviewer's probe: X sent (and acknowledged on Android), then Y; the undo's send of X `FAILED`, `NO_WATCH` or cut off; a stale handshake naming X answered `UNCHANGED` and remembered X; Y landed (on Android its acknowledgement was ignored, the pending feed being the failed X); the next reschedule of X answered `UNCHANGED` with the watch on Y. All six cases failed so (two links, three outcomes): a wrong `UNCHANGED` against Review Focus 2, in the very order Ruling 48 item 1 rules on. The round 2 section above, plan 2 and the KDocs claimed a narrower gap.
- **The fix,** the reviewer's own. `pending` and `handed` become one list, `onTheirWay`: the feeds that may still land after any handshake, oldest first, in the process alone and cleared at step 0.
  - A send puts its feed last before the link is called (a feed already there with the same bytes moves up to it), since a send cut off by its budget, or one that throws, may have been handed over: the old and the new may then both land.
  - `SENT` leaves that feed the only one: Android delivers in order (call 1's one case aside), and the iPhone's latest context replaces the ones before it.
  - Any other answer puts the list back as it was, since the link handed nothing over. `WatchLink.send`'s KDoc now states that contract, which Tasks 5 and 8 keep as their plan writes them (no node queued it; `updateApplicationContext` not called, or thrown).
  - A matching handshake is remembered only when every feed on the list is the current one (`Delivery.isFor`).
  - On Android an acknowledgement takes out the feed it names and every one before it, and is remembered only when none sent after it is left. So after a refused undo Y's acknowledgement counts (nothing after it), and after a cut-off it does not (X may still follow).
  - On the iPhone, before this process has sent a feed, the memory stands in as before; the process's first send now starts the list from the memory's word, read before step 5 forgets it, so a relaunched app whose undo does not get through still knows of Y.
  - Ruling 48 is not contradicted: its "must end with the watch on X" now holds also when the undo's own send fails, on both links.
- **The tests,** eight new in `WatchFeedSyncTest` (50 now):
  - `aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo`, the probe: both links, the undo's send `FAILED`, `NO_WATCH` or cut off. Every case ends with [X, Y, X, X] sent, the memory naming X and the next reschedule `UNCHANGED`;
  - `aRelaunchedPhoneWhoseUndoDidNotGetThroughEndsWithTheWatchOnTheUndo`: the same on the iPhone's link, the undo served by a new sync over the same memory (the seed);
  - `aRefusedSendLeavesTheFeedSentBeforeItTheLastOnItsWay`: Y and X sent, Z refused; the handshake naming X is remembered and costs no send (a refused send is taken off, and `SENT` replaces what came before it);
  - `aSendThatMayHaveGotThroughStaysOnItsWayBesideTheOneBeforeIt`: Y sent, X cut off or thrown; a handshake naming Y, and on Android Y's acknowledgement, remembers nothing;
  - `aRefusedResendLeavesTheFeedOnItsWay`: Y on its way, sent once more and refused, still counts;
  - `anAcknowledgementLandsEveryFeedSentBeforeIt` (Android): Y sent, X cut off, X acknowledged and then Y late; after a refused Z the handshake naming X is remembered;
  - `aRelaunchedAndroidPhoneTakesTheWatchsWordForAFeedCutOff`: the seed is the iPhone's alone;
  - `aLinkThatDoesNotConfirmTakesNoAcknowledgement`.
- **Red, then green.** The first four were written against `dba75dc7`'s code: `WatchFeedSyncTest` ran 46 and 3 failed, the probe test, the relaunch and the refused send ("confirms false, the undo's send FAILED: Y may still land after the handshake expected null, but was:<X>"; "confirms false, FAILED: nothing but X may land expected:<X> but was:<null>"). The cut-off and throw test passed there, a guard. The fix turned the three green, with 269 `watchfeed`, `notifications` and `feature.tasbeeh` host tests passing. The last four were written after the first mutant run, to kill its survivors. Over `dba75dc7`'s code `anAcknowledgementLandsEveryFeedSentBeforeIt` fails as well (4 of 50), and the other three pass, guards.
- **Mutants,** each applied alone to the final code, those host tests run (273), and the file put back with `git checkout`, by a script in the session scratchpad:

| Mutant | Killed by |
|---|---|
| N1 a refused send stays on its way (`dba75dc7`'s rule) | `aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo`, `aRefusedSendLeavesTheFeedSentBeforeItTheLastOnItsWay`, `anAcknowledgementLandsEveryFeedSentBeforeIt` |
| N2 `SENT` leaves the earlier ones on the list | `aRefusedSendLeavesTheFeedSentBeforeItTheLastOnItsWay` |
| N3 a send puts its feed alone on the list (the cut-off keeps only the new) | `aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo`, `aRelaunchedPhoneWhoseUndoDidNotGetThroughEndsWithTheWatchOnTheUndo` |
| N4 no seed from the memory | `aRelaunchedPhoneWhoseUndoDidNotGetThroughEndsWithTheWatchOnTheUndo` |
| N5 the seed on both links | `aRelaunchedAndroidPhoneTakesTheWatchsWordForAFeedCutOff` |
| N6 any acknowledgement of a feed on the list remembered | `aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo`, `aSendThatMayHaveGotThroughStaysOnItsWayBesideTheOneBeforeIt` |
| N7 an acknowledgement takes out only its own feed | `anAcknowledgementLandsEveryFeedSentBeforeIt` |
| N8 an acknowledgement takes out nothing | `anAcknowledgementLandsEveryFeedSentBeforeIt` |
| N9 acknowledgements counted on a link that does not confirm | `aLinkThatDoesNotConfirmTakesNoAcknowledgement` |
| N10 any feed on the list for it, not every one | four tests, both undo tests among them |
| N11 the last on the list alone (round 2's rule) | `aHandshakeAfterAnUndoThatDidNotGetThroughEndsWithTheWatchOnTheUndo`, `aRelaunchedPhoneWhoseUndoDidNotGetThroughEndsWithTheWatchOnTheUndo` |
| N12 no move-up of a feed already on the list | survives, equivalent in effect (below) |
| N13 a refused resend takes the earlier copy off too | `aRefusedResendLeavesTheFeedOnItsWay` |
| N14 step 0 keeps the list | `aChangedWatchForgetsThePendingFeedToo`, `aChangedWatchForgetsTheFeedHandedOverToo` |
| N15 a new process on the iPhone's link does not ask the memory | `aHandshakeAfterARelaunchDuringAnUndoEndsWithTheWatchOnTheUndo` |

  14 of 15 killed. N12 keeps two copies of the same bytes on the list where the fix moves one up. The handshake and acknowledgement rules answer the same over both, but in one order (times alone on their way as their own feed, then again as a stand-in) the copy costs one more send; the move-up keeps the list as short as the distinct feeds cut off since the last `SENT`.
- **A model with sends that do not get through.** The round 3 spec lens's FIFO model (1,500 runs a configuration, 30 events each, both directions in order, losses on Android), with a link that may refuse the next send (`FAILED` or `NO_WATCH`, nothing handed over) or be cut off by its budget (handed over or not, at random), copied into the worktree for the run and removed, never committed. Runs with a wrong `UNCHANGED` during the run and at its end:
  - on `dba75dc7`: the iPhone's link 31 and 26 without restarts, 8 and 19 with; Android's 22 and 25 without, 11 and 22 with;
  - on the fix: the iPhone's 0 and 0 without restarts, 1 and 3 with; Android's 1 and 4 without, 5 and 16 with (3 and 5, 5 and 18 with losses off).
  - Every Android run left without restarts (5 of 5, losses on or off) sends one feed, then another, then the first again (A, B, A): a stale handshake or a late acknowledgement from the first A's time is matched to the last A, which was lost, or cut off without being handed over. That is the round 3 spec lens's minor (2), Android's ABA, which `dba75dc7`'s single pending feed shares, and it heals at the next handshake. The iPhone's runs left all need a restart: the known limit below.
- **The known limits, as now written.** Android: a process restarted while a feed is in flight, as before. The iPhone: an app relaunched after any send that did not answer `SENT`, which forgot the memory, so the new process cannot know of the context handed over before it (the round 2 section said "only after a send cut off by its budget", corrected there). Both heal at the next handshake. Plan 2's call 1 and Review Focus 2, `onTheirWay`'s KDoc and this report say so.
- **Plan text:** call 1 (the acknowledgement rule, the iPhone's rule and its known limit); Task 4's Interfaces (`WatchLink.send`'s contract, `acknowledge`, the feeds on their way in place of the pending fingerprint and the feed handed over last, steps 0, 4, 5 and 6); Step 1's list and Review Focus 2 (the limit and the tests). This report's hand-off lines for Tasks 5 and 8 name the send contract.
- **Checks.** `scripts/test.sh` twice (the second with each of the seven test tasks `--rerun`): exit 0 both times; shared host **2,104** and iOS simulator **2,048**, eight more each, with `WatchFeedSyncTest` at 50 on both and `NotificationCoordinatorTest` 12; `BackgroundRefreshBridgeTest` 2; widgetcore 111 / 109, watchcore 112 / 110, wearApp 143; 0 failures; no `EOFException`. Versions agree, 1.2.0 (38) / (100038), and `test-bump-version.sh` passes. `:shared:linkDebugFrameworkIosSimulatorArm64` and `:androidApp:compileDebugKotlin` green, and `shared.h` names no watch feed symbol (only the allowed `SharedWatchcore` tasbeeh types); `scripts/ios-build.sh` BUILD SUCCEEDED for the unlocked iPhone 17 `D9C6CFA7`, which stayed shut down; `tools/i18n-check.py` clean; `git diff --check` clean.
- **Minors left for the Lead,** in the ledger: N12; Android's ABA family, with the model's figures and a possible fix (keep Android's unacknowledged feeds from before the last `SENT`, match an acknowledgement to the earliest copy, and remember a handshake only when every unacknowledged feed is the current one, at the cost of one more send when acknowledgements are lost); and the round 3 spec lens's minors, carried.

## Ruling 50: the feed's delivery number (25 October)

Ruling 48 stopped after three fix rounds: a fingerprint names what a feed is, never when it was sent. Ruling 50 replaced its machinery (`onTheirWay`, `standIn`, `Delivery`, the memory of one fingerprint) with a numbered delivery, the watch only moving forward, and one record on the phone. The report is `docs/superpowers/reports/2026-10-25-feed-delivery-number.md`. What follows in this section is superseded where it names the old machinery.

## For the next tasks

Rewritten on 25 October (Ruling 52 item 8): the section first named Ruling 48's on-their-way contract, which Ruling 50 replaced. As the code stands after Rulings 50 and 52:

- **Task 5** replaces `WatchLink.android.kt` with `AndroidWatchLink` (`confirmsDelivery = true`). Its `send` carries the delivery on the path (`WatchLinkContract.feedPath`) and answers only `SENT`, `NO_WATCH` or `FAILED` (`WatchLink.send`'s contract): the number is on record before the call, and only the watch's echo confirms it. Its listener calls `watchFeedSync.sync(WATCH_ASKED, have = …)`, `acknowledge(…)` and `sync(WATCH_APPEARED)`, each under `withTimeoutOrNull(CALLER_BOUND)`, and logs an echo as `loggedDelivery(echo)`, never its origin (Ruling 52 item 4). Item 9's watch key (the Wear node id) is that task's.
- **Task 8** (built, P2.8) is `IosWatchLink` (`confirmsDelivery = false`): a `SENT` confirms its number for the watch whose key was read before the call (amendment A3), and `watchActivated` and `watchStateChanged` are `request(WATCH_APPEARED)` and `request(WATCH_CHANGED)`. Since Ruling 52 the bridge's handshake answers its reply handler from a coroutine and never blocks WatchConnectivity's queue.
- **Task 7's** debug seam reaches `appContainer.watchFeedFor` and `appContainer.watchLink`, both `internal` and the very two the sync was built from. It may log an origin; release lines never do.
- The phone logs `TaqwaWatch <reason> <result> <ms>ms` once per ask (System.out on Android): no delivery, place or bearing.
- A sync whose worker has ended answers at once.
