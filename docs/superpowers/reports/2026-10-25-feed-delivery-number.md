# Ruling 50: the feed's delivery number

Lane P, on `watch-feed`, 25 October 2026. Base `6ddfa1ce` (Ruling 48's last round). Ruling 50 is the ledger's (the Lead, 25 October, about 04:00 SAST), read with its two design checks (the Android and the iPhone lenses, 03:47 and 03:53) and the ten errata in the task.

**In one line:** every feed now travels with the install's random origin and a delivery number written down before the link is called. The watch stores only what moves it forward and echoes what it holds. The phone answers `UNCHANGED` only once the watch has echoed the latest delivery (on the iPhone, also once the latest context was handed over to that very watch). Ruling 48's lists of fingerprints are gone, and `WatchFeedSync` is simpler:
- `WatchFeedSync.kt` is 256 lines, against 348 at the base (124 lines of code against 154);
- its decisions (steps 0 to 6, the send and the intake) are 25 lines of code, against 62;
- the record's pure rules live in `WatchFeedRecord`, in `WatchFeedMemory.kt`, with their own tests.

## What was built

| Item | Where | How |
|---|---|---|
| 1. origin and `seq` beside every feed, `seq` written before the link is called | `WatchFeedRecord.sending`, `WatchFeedSync.deliver`, `DataStoreWatchFeedMemory` | The next number, with what it stands for and the fingerprint it carries, is written and awaited before `link.send`. The origin is 16 hex characters of `Uuid.random()`, made by the first read in one edit with an empty record. Origin and numbers share every edit and the one file, so `ReplaceFileCorruptionHandler` resets them together, and a store whose origin is gone starts a whole new record. |
| 2. the watch stores only another origin or a greater number, and echoes `(origin, seq, fingerprint)` or nothing | `WatchLinkContract.stores`, `echoOf`, `STORE_STEPS` (`:watchcore`), and `TestWatch` in the tests | A greater number is stored even for the same feed, so the echo names it; only the redraw may be skipped. The store order (remove, place, write) is part of the contract, with its test. |
| 3. one record; `UNCHANGED` exactly when `confirmedSeq == lastSeq` and `lastStandsFor` is the feed | `WatchFeedRecord.holds` | As amended: `confirmedSeq` is the last own-origin echo's number, lower or higher (A1/A1b). An echo of nothing or of another origin clears it. The record also keeps `lastSent` (A4) and `confirmedFor` (A3). |
| 4. Android confirms only by echo; the iPhone also on `SENT` | `WatchFeedSync.deliver` (`handedOver(key)`) | The key is read before the call. A throw, a refusal or a cut-off leaves the confirmation where it was. |
| 5. an echo above `lastSeq` lifts it | `WatchFeedRecord.echoed` | So does an echo of `lastSeq` with another fingerprint than `lastSent` (A4). Either way nothing it stood for is known, and the next ask sends above the watch's number. |
| 6. `WATCH_CHANGED` forgets the confirmation | step 0, `WatchFeedRecord.forgotten` | Since review fix round 1, a forget also sets the next number aside (A2, below). `PHRASES_CHANGED`, `RESCHEDULED` and `WATCH_APPEARED` follow item 3. |
| 7. the proof | `WatchFeedDeliveryModelTest`, `WatchFeedNamedCasesTest`, `WatchFeedRecordTest`, the rewritten `WatchFeedSyncTest` | Below. |
| 8. the contract in `:watchcore` | `WatchLinkContract`: `WatchDelivery` (its `text` is `origin/seq/fingerprint`), `deliveryOf`, `feedPath(delivery)`, `deliveryIn`, `isOrigin`, `CONTEXT_DELIVERY_KEY = "delivery"` (in place of `"fingerprint"`), `stores`, `echoOf`, `STORE_STEPS` | 11 tests in `WatchLinkContractTest`, 7 of them new. One encoding everywhere: the Android path's tail, the acknowledgement's payload, the handshake's payload on both links, and the iPhone's context value. |

**Kept from Ruling 48:**
- Item 2: MB is `aChangedWatchForgetsFirst`, MC is `aPhraseChangeTheWatchAlreadyHoldsIsNotSentAgain`, and MD is the coordinator's test, untouched.
- Item 4: the closed channel, plus the new `aWorkerThatEndsWhileServingAnswersAtOnceAfterwards` (a worker that ends while it serves a gated send).
- Item 5: the `println`.
- Items 6 and 7, unchanged.

The link's interface gained `watchKey()`, and `send` takes the `WatchDelivery`. `sync` and `request` take `from`, the key the bridge read when the handshake arrived, and `acknowledge` takes the echo.

## The errata, and how each was met

1. **Replace Ruling 48's machinery, keep its true tests.** `onTheirWay`, `standIn`, `Delivery`, `nothingElseMayLand`, the iPhone's seed from the memory and `WatchFeedMemory`'s one fingerprint are gone. The tests that only pinned them are deleted:
   - `aChangedWatchForgetsThePendingFeedToo`;
   - `aChangedWatchForgetsTheFeedHandedOverToo`;
   - `aRefusedSendLeavesTheFeedSentBeforeItTheLastOnItsWay` (its true rule is now `aHandshakeAfterARefusedSendIsSentTheCurrentFeed`);
   - `anAcknowledgementLandsEveryFeedSentBeforeIt` (A1b contradicts it on purpose);
   - `aLinkThatDoesNotConfirmTakesNoAcknowledgement` (now A3's: a keyless acknowledgement is not the session's watch);
   - `aHandshakeNamingThePendingFeedIsRemembered` (folded into `theWatchsHandshakeWithItsCurrentFeedIsUnchangedAndRemembered`);
   - `theTimesAloneAreTheFeedOnceThePhrasesAreGone` (item 3 compares what the send stood for, so that case costs one send; its counterexample is `aLateStandInAckAfterThePhrasesAreDeletedConfirmsNothingWrong`).

   Renamed to say Ruling 50's rule: `aLateAckForAnOlderFeedIsIgnored` became `aLateAckForAnOlderFeedCostsOneMoreSendNeverAWrongUnchanged` (erratum 4), and four others moved from the fingerprint to the delivery:
   - `aHandshakeWithoutAFingerprintIsSent` → `aHandshakeWithoutADeliveryIsSent`;
   - `theFingerprintTravelsWithTheFeed` → `theDeliveryTravelsWithTheFeed`;
   - `twoFeedsDeliveredOutOfOrderStillHealAtTheHandshake` → `twoFeedsDeliveredOutOfOrderLeaveTheWatchOnTheLater` (call 1's "one case left" is closed);
   - watchcore's `theFeedPathCarriesTheFingerprint` → `theFeedPathCarriesTheDelivery`.

   Ruling 48's named undo cases moved to `WatchFeedNamedCasesTest`, rewritten to Ruling 50 and checked against a watch that stores by the contract's rule. `aHandshakeAfterARelaunchDuringAnUndoEndsWithTheWatchOnTheUndo` is merged into `aHandshakeDuringAnUndoEndsWithTheWatchOnTheUndo` (with a restart, on both links).
2. **The model test** is in `:shared`'s commonTest. It is seeded, deterministic, covers both links and a second watch, and drives the real `WatchFeedSync`. Ruling 48's 20 named counterexamples and the design checks' new ones are each a plain example (below).
3. **Item 8's receivers' side** is pure rules in `:watchcore`, with tests. Plan 2's call 1, Tasks 3 to 8, its Review Focus 2 and its Ruling 39 table, plan 4's Global Constraints, Task 1's `contract` vector, Task 5 and Task 5B, the execution map's P2.4, P2.6 and P4.1 rows and its shared-files line, and spec 1.2 are rewritten to it.
4. **A1:** the "stale and ignored" clause is deleted: the confirmation is the last own-origin echo's number, lower or higher.
5. **A3:** `confirmedFor`, the iPhone's watch key. It is set on `SENT` (the key read before the call) and on an echo from that watch. An ask with another key reads nothing as confirmed, and an echo from a watch that is not the session's is ignored. Android passes null. The hook (`WatchSessionHook.watchKey()`, a UUID file in `watchDirectoryURL`) is plan 2 Task 8's to build, as its text now says.
6. **A1b** is the same rule as A1. `aWatchHoldingAnOlderFeedIsSentTheFeedTooLargeForTheLink` keeps its `SENT` on both links.
7. **A4:** `lastSent`. An echo of `lastSeq` whose fingerprint is not `lastSent` is item 5's case. Origin and record share one edit.
8. **Item 7's S** is written as erratum 9 reads it, with this amendment. On the iPhone, "holds" also counts the latest context handed over for that watch with a number above what it holds. A reschedule's `UNCHANGED` is judged on the session's watch, and a handshake's on the watch that asked (when its echo was taken in).
9. **The minors, weighed:**
   - **The clone guard** is A4, adopted.
   - **The stragglers:** no protocol change, as advised. The rider is in S, in plan 2's call 1, in Review Focus 2 and in spec 1.2. `stores`' KDoc says it too.
   - **S with two watches:** the model has a second watch on both links. Its S uses the asking watch, or the watch the phone heard from last (Android), or the session's watch (the iPhone).
   - **The constraints:**
     - (1) The store order is `STORE_STEPS`. Its test stops a receiver after each step of the right order and of the three wrong ones.
     - (2) Origin and record live in one edit, and a store without an origin starts afresh (`aStoreWithoutAnOriginStartsAWholeNewRecord`).
     - (3) `eachSendIsWrittenDownBeforeTheLinkIsCalled`, and the `seq_after_call` mutant.
     - (4) Origin and number are added to Ruling 39's row, in plan 2 and in the privacy-wording report.
   - **Plan 2 Task 8's re-check before `updateApplicationContext`:** written into Task 8, with its test `aSessionThatWentInactiveIsRefusedNotSent`. The model refuses a send whose session went inactive while it was in flight.
   - **Plan 4 Task 5's activation handshake** waits for `ConnectivityDrain.isDrained`: written in.
   - **Android (a), A2** (every reset of `confirmedSeq` also raises `lastSeq`): not built in this commit, left for the Lead; **built in review fix round 1** (below), after both reviewers reproduced D1.
     - It is ruled "for the Android lens", and the task says to build exactly items 1 to 8 and the amendments.
     - My reading: in every flow I traced it costs no send, because every reset already makes the next ask send. It would close D1's case: an acknowledgement sent before a wipe arrives after the wiped watch's empty handshake and confirms again (4 per 200,000 in the iPhone lens's model).
     - Under S as erratum 9 writes it ("or was wiped since"), D1 is accepted, so this model's S does not count it.
     - I recommend it as a one-line follow-up: `forgotten()` also returns `lastSeq + 1`, with `lastStandsFor` and `lastSent` null.
   - **Android (b), the Wear node id as `confirmedFor`:** not built. Two Wear watches connected at once is not a case the plans name; the model's two-watch configuration passes S as written, and each watch's own handshake heals it.
10. **The Gradle flake:** no `EOFException` and no `EngineBudgetBenchmarkTest` failure in any run.

## Test first

- **`:watchcore`:** the new `WatchLinkContractTest` ran red first, as compile errors: `WatchStoreStep`, `isOrigin`, `WatchDelivery`, `deliveryOf` and `deliveryIn` unresolved. Then 11 of 11 passed.
- **`WatchFeedRecordTest` and `WatchFeedMemoryTest`** ran red first: `WatchFeedRecord`, `read` and `newWatchOrigin` unresolved. Then 11 and 5 passed.
- **`WatchFeedSyncTest`** (and its fakes) was written before the new sync, against the new API. It was not run red on its own: once the record existed, the old sync no longer compiled. The mutants below are its behavioural red.
- **`WatchFeedNamedCasesTest` and `WatchFeedDeliveryModelTest`** were written after the sync and passed at their first run. Their teeth are the mutants too.

**The mutants**: each is applied to the code, then the four watchfeed test classes are run (scratchpad `r50-mutants/`). The last column is the model alone at 20,000 schedules a configuration (140,000 in all).

| Mutant | Failing tests | The committed model (960) | The model at 140,000 |
|---|---|---|---|
| Ruling 50 as written: an echo below `confirmedSeq` ignored | 10 | 1 | 321 |
| no A3 (keys ignored on both sides) | 6 | 12 | 1,994 |
| no A4 | 5 | 1 | 146 |
| no item 5 lift | 4 | 5 | 412 |
| `seq` written after the call | 10 | 3 | 197 |
| Android confirms on `SENT` too | 9 | 238 | 29,971 |
| the iPhone never confirms on `SENT` | 21 | 4 | 759 |
| the watch stores everything | 5 | 1 | 21 |
| the watch stores an equal number | 2 | passes | passes |
| the times alone stand for themselves | 6 | 378 | (the failure list overran the worker's heap) |
| `WATCH_CHANGED` forgets nothing | 4 | passes | passes |
| an echo of nothing keeps the confirmation | 4 | 48 | 6,146 |
| `UNCHANGED` ignores `lastStandsFor` | 43 | 109 | 12,421 |

Both columns of the model are from its final form, after the three fixes below; the failing tests are from the final commit's four watchfeed classes.

Every mutant is killed by at least one plain test. The model alone sees eleven of the thirteen. Two are equivalent within it:
- **An equal number stored:** only a reused number tells it apart, and A4 then costs one send.
- **`WATCH_CHANGED` forgetting nothing:** A3's key already covers a changed watch, and a reinstall that keeps the key is the accepted "wiped since". So under A3, item 6 is a belt and braces, and the plain tests hold it.

## The model

`WatchFeedDeliveryModelTest` drives the real `WatchFeedSync`, over a record in memory, through `WorldLink`. A world decides every send's fate: sent, refused, no watch, cut off before or after the hand-over, thrown before or after. The watches store by `WatchLinkContract.stores`.
- **Android:** messages lost, duplicated and delivered in any order both ways; Play services dropping half of what is in flight; acknowledgements and handshakes; a second watch nearby at once or switched to; new installs leaving stragglers.
- **The iPhone:** the latest context per watch, delivered only to the worn watch and re-read at activation; handshakes in order, lost or duplicated, reaching the phone only from the session's watch and waking the app; reinstalls and erases that may lose the context and get a new key; two watches with automatic switching, the change told only to a running app and sometimes lost; a send refused when its session went inactive in flight.
- **Both:** phone processes killed between asks, wipes with or without the start's handshake, and feeds over the link's limit.
- **Outside the documented platform** (S not asserted): records restored from older snapshots, and on the iPhone a context the system loses.

It asserts S, L and D as the class's KDoc and erratum 9 state them. The committed size is 960 schedules: 160 for each of the five documented-platform configurations and 80 for each of the two outside it. That takes **0.41 to 0.44 s on the JVM and 1.8 to 2.0 s on the simulator** (both `scripts/test.sh` runs). A first draft at 2,400 schedules took 8.5 s on the simulator, mostly the real sync's encodes, about 220 µs an ask there.

**The large run** used the same code with `RUNS` raised in the worktree for the run alone, then put back: **1,200,000 schedules** (200,000 for each documented configuration, 100,000 for each of the others), **0 failures**, 175 s. On the way, three model faults were found and fixed. None was a design fault:
- the same tick for a straggler's landing and the handshake before it;
- an echo the sync ignored (it arrived while the iPhone's session was between watches) counted as heard;
- a duplicated handshake judged by identity.

What the first and second of these show about the design:
- A **straggler** stored after the watch's handshake left is exactly the rider, and the next handshake heals it.
- A **wiped watch's empty echo that arrives mid-switch** is ignored by A3 by design, so its wipe counts only at its next handshake. That is erratum 9's "or was wiped since", and L holds.

## Checks

- `scripts/test.sh`, first run: exit 0.
  - Versions: 1.2.0 (38), and (100038) on the Galaxy watch app; `test-bump-version.sh` PASS.
  - Shared: host **2,133** and iOS simulator **2,077**, against 2,104 and 2,048 at the base. `WatchFeedSyncTest` has 40, `WatchFeedNamedCasesTest` 26, `WatchFeedRecordTest` 11, `WatchFeedDeliveryModelTest` 1 and `WatchFeedMemoryTest` 5. Of Ruling 48's 50 sync tests, 33 stay in `WatchFeedSyncTest` (three renamed), 10 moved to the named cases (one merged there), and 7 are deleted. `WatchFeedSyncTest` gains 7 new ones.
  - watchcore 119/117, widgetcore 111/109, wearApp 143. 0 failures, no `EOFException`.
- `scripts/test.sh`, second run, with each of the seven test tasks given `--rerun` so nothing is answered from up-to-date checks: exit 0, `BUILD SUCCESSFUL in 5m 31s`, all seven executed, the same counts, 0 failures, no `EOFException`. The model took 2.01 s on the simulator and 0.41 s on the JVM.
- `scripts/ios-build.sh` with `IOS_DEST` set to the unlocked iPhone 17 (`D9C6CFA7`, which stayed shut down): `BUILD SUCCEEDED`, `TaqwaWidget.appex: 3 MB (limit 15 MB)`.
- `git diff --check` is clean. The commit carries no trailer, under the repository's Taqwa identity.
- `:androidApp:compileDebugKotlin` and `:shared:linkDebugFrameworkIosSimulatorArm64` are green, and `shared.h` names no watch-feed type (`WatchDelivery`, `WatchFeedRecord` and the rest are `internal` or in the unexported `:watchcore`).
- No device, emulator, simulator by hand, lock or KB2604110095 was used. The K/N tests booted their own default simulator.

## Rulings 39, 41, 42 and 43

- **39:** the promise "Only your own devices can see your location." is untouched. No copy, string or privacy line changed. The two new values that leave the phone (a random per-install number and a counter, beside the feed, never a place or a person) are added to Ruling 39's table in plan 2 and in the privacy-wording report.
- **41, 42, 43:** no face, `wearApp`, complication or screen-reader file was touched.

## For the next tasks

- **P2.6 (Wear):** `FeedStore.write(json, delivery)` asks `WatchLinkContract.stores` first (else `STALE`, nothing touched), follows `STORE_STEPS`, and keeps `feed.json.delivery`. `FeedReceiver` reads `deliveryIn(path)`: a feed without one is not stored. The acknowledgement and the handshake are `echoOf(store.delivery())`. `RefreshPolicy`'s `fromPhone` is `delivery() != null`. P2.6 starts once a "[Lead/int R50]" line is in the ledger.
- **P2.5 (Android sender):** `AndroidWatchLink.send(json, delivery)` sends on `feedPath(delivery)`, with `watchKey()` null. `PhoneWatchListenerService` passes the acknowledgement's and the handshake's payloads unchanged.
- **P2.8 (iPhone sender):** `WatchSessionHook { isReady(); watchKey(); send(feed, delivery) }`. The UUID file goes in `watchDirectoryURL`. `refreshRequested` reads the key as the message arrives. The send re-checks the session right before `updateApplicationContext`. The context keys are `feed` and `delivery`.
- **Plan 4:** Task 1's `contract` vector gains `deliveries`, `stores`, `echoes` and `storeSteps`. Task 5's `FeedStore` and `FeedIntake` port the three rules against them (`.stale`; `.unchanged` writes the newer delivery). Its activation handshake waits for the drain.
- **The Lead:** A2 is built (review fix round 1). D1's remaining limit, below, is the Lead's to accept or to close with a protocol change.

## Review fix round 1 (25 October)

Both reviewers (the code-quality lens and the spec lens) found one Important, the same: **D1, a wrong `UNCHANGED` outside call 1's limits.** They reproduced it on Android:
- X is sent as number 1 and stored, and its acknowledgement is still on its way.
- The watch is wiped. Its empty handshake is taken in by an ask that ends before it takes a number: no watch, no feed, or the budget runs out in the build.
- The late acknowledgement of number 1 then confirms number 1, which is `lastSeq`. The next reschedule and the next reconnect answer `UNCHANGED` while the watch holds nothing. Only the watch's next handshake heals it.

This contradicted four texts: `WatchFeedRecord.echoed`'s KDoc, `WatchFeedSync`'s, spec 1.2 and Review Focus 2. The committed model could not see it, because its S exempted any wipe after the last echo heard was sent, and the late acknowledgement was sent before the wipe.

**The fix is A2**, the report's own recommendation. `forgotten()` returns `lastSeq + 1`, with `lastStandsFor` and `lastSent` null, and nothing confirmed. That number is set aside and never sent, so the next send takes the one after it. Every forget (`WATCH_CHANGED`, an echo of nothing, an echo of another origin) now puts the latest number above every number an echo sent before it can name. It costs no send, since every forget already made the next ask send. No ruling is contradicted:
- Ruling 50 item 6 promises "one more send, never a wrong `UNCHANGED`", and A2 makes it so;
- item 1 holds, since a number is still never used for another feed.

**Tests, red first:**
- `WatchFeedNamedCasesTest.aLateAckFromBeforeAWipeConfirmsNothingOnceTheWatchSaidItHoldsNothing` is D1. It covers each way of ending before a number (no watch, no feed, cut off in the build), then a reschedule or a reconnect. On 18a93a78 it fails: "a wrong UNCHANGED: watch 0 holds null".
- `WatchFeedRecordTest.anEchoSentBeforeAForgetConfirmsNothing` (new) failed, and `aChangedWatchForgetsTheConfirmation` (rewritten: number 2 set aside, nothing it stands for, never sent, the next send is 3) failed too.
- `anEchoOfNothingOrOfAnotherOriginClearsIt` now checks that those echoes are exactly a forget.
- `WatchFeedSyncTest.theDeliveryTravelsWithTheFeed` asks with `RESCHEDULED`, not `WATCH_CHANGED`, so its numbers stay 1 to 4. It never acknowledges, so each feed is still sent.

**The model's S is now item 7's "wiped after its last echo".** A wipe, or a straggler stored, excuses an `UNCHANGED` only while it is unseen: after the echo the phone took in from that watch last, and after every echo of nothing (or of another install's) it took in from it. So once the phone has heard a wiped watch say it holds nothing, no echo sent before that excuses an `UNCHANGED`, whatever order they arrive in.
- On the iPhone this is the rule it had, since its handshakes arrive in order.
- Without A2, the new S fails 1 schedule in 140,000 and 5 in 1,200,000. Four are D1. The fifth is its straggler form: an echo of another install's feed, then a stale echo of the phone's own.
- With A2, the 960 committed schedules pass (0.44 s on the JVM), and so do 1,200,000 (`RUNS` raised for the run alone, and the test's one-minute timeout lifted, then both put back): 0 failures in 178 s.

**The mutants this round:**
- A2 undone (`forgotten()` keeping the numbers): 3 plain tests fail (the D1 case, `anEchoSentBeforeAForgetConfirmsNothing`, `aChangedWatchForgetsTheConfirmation`), and so does the model at 140,000 schedules.
- The spec reviewer's M6, a handshake's echo taken in only once a watch is reachable, survived every test at 18a93a78. The D1 case's no-watch half now kills it.

**What A2 cannot close.** A wiped watch whose handshake was lost, and which takes a late older feed (Y, number 1), can have its acknowledgement of Y overtaken by a stale one of X (number 2, sent before the wipe). That stale acknowledgement then confirms number 2. Echoes carry no time, so no rule on their values can tell this from X's acknowledgement arriving in order after Y's: refusing that would break the plain flow. This is the lost-handshake limit call 1 already names. Its text, spec 1.2 and Review Focus 2 now say so ("whose echoes from before the wipe may still arrive"). `aStaleAckOvertakingAWipedWatchsNewerOneIsHealedByItsHandshake` pins it as the accepted transient, healed by the watch's handshake. Closing it would need the watch to send a per-install number of its own in every echo, a protocol change for both watch apps and the Lead's to rule on.

**Checks this round:**
- `scripts/test.sh`: exit 0, with versions 1.2.0 (38) and (100038) and `test-bump-version.sh` PASS.
  - Shared: host **2,136** and iOS simulator **2,080**, three more each. `WatchFeedNamedCasesTest` has 28 and `WatchFeedRecordTest` 12.
  - The model took 0.32 s on the JVM and 3.0 s on the simulator.
  - watchcore 119/117, widgetcore 111/109, wearApp 143. 0 failures, no `EOFException`.
- `scripts/ios-build.sh` on the unlocked iPhone 17 (`D9C6CFA7`, which stayed shut down): `BUILD SUCCEEDED`, `TaqwaWidget.appex: 3 MB`.
- `tools/i18n-check.py` is clean, and so is `git diff --check`.
- No device, emulator, simulator by hand, lock or KB2604110095 was used.

**The minors**, recorded in the ledger and left as the reviews list them:
- the A3 key read before the call has no test of its own (the mutant survives);
- the origin-only-write mutant survives `WatchFeedMemoryTest`;
- a null watch key counts as a watch on the iPhone link;
- an echo at `Long.MAX_VALUE` wedges the sync, since `lastSeq` overflows and every ask is `FAILED`. A2 changes nothing there: `sending` already overflowed;
- the model's timing text differs from the runs;
- `restart()` leaves the old sync alive;
- the `STORE_STEPS` test covers 3 of the 5 wrong orders;
- the Script's `wiped` flag is cleared by any acknowledgement, even one sent before the wipe, which is stricter than the model.
