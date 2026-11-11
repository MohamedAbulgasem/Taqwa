# Ruling 70 item 1: an ask the budget cuts is asked again, once

Lane P, on `watch-feed`, 5 November 2026. Base `2d6e343a` (`git merge --ff-only watch-companion`:
already up to date; `watch-feed` had no commits of its own and no WIP from an earlier run). This is
no plan task: it is Ruling 70 item 1 in the ledger, test-first in `:shared` (`WatchFeedSync`, plan
2's code). The budget stays 5 s. No device, emulator or simulator was used by hand; the one lock
taken was `ios-sim-tests`, for the Kotlin/Native tests. Nothing is pushed; Taqwa identity; no
trailer. The version is not bumped (the watch ships as 1.6.0 (42)).

One commit, the WIPs (`95d999ca`, `9b338dd9`) folded into it by a soft reset: "Watch feed: an ask
the budget cuts is asked again once, 30 s on, and each ask's steps are timed in a debug build
(Ruling 70)".

## Why

P2.7's pass (`docs/superpowers/reports/2026-10-23-phone-feed-pass/README.md`, "For the Lead",
item 1) saw the 5 s budget cut a reinstalled watch's warm handshake, `WATCH_ASKED FAILED 5262ms`.
Nothing asked again, so the watch waited for the phone's next reschedule or its own next
handshake, and the logs could not say which step had held the ask. Ruling 70 item 1 takes both.

## What changed

| File | Change |
|---|---|
| `shared/src/commonMain/.../watchfeed/WatchFeedSync.kt` | An ask the budget cuts is queued once more `AGAIN_AFTER` (30 s) after the cut (`askAgain`), on the sync's scope; for a reschedule's ask (a planned location) alone, a later ask served first takes its place (narrowed in review round 1); each step of an ask is timed (`StepTimes`) and handed to the new `timed` callback; the ask's clock is the new `clock` parameter. The class KDoc says so. |
| `shared/src/commonMain/.../watchfeed/WatchFeedLog.kt` | `WatchAskStep`, `AskStep`, `AskTimes`, `askStepsLine` (the step line), `expect fun isDebugBuild()`; since review round 2 `WATCH_LOG_TAG` (`TaqwaWatch`), `stepsLogOn(debugBuild, tagEnabled)`, `expect fun stepsTagEnabled()` and `stepsLogged()`. |
| `shared/src/androidMain/.../watchfeed/WatchFeedLog.android.kt` (new) | `isDebugBuild()` is the app's `ApplicationInfo.FLAG_DEBUGGABLE` (`debuggable(flags)`); `stepsTagEnabled()` is `Log.isLoggable("TaqwaWatch", Log.DEBUG)` (review round 2). |
| `shared/src/iosMain/.../watchfeed/WatchFeedLog.ios.kt` (new) | `isDebugBuild()` is `Platform.isDebugBinary`: Xcode's Debug configuration builds a debug framework, an archive's Release never does; `stepsTagEnabled()` is false (review round 2). |
| `shared/src/commonMain/.../di/AppContainer.kt` | `timed = { if (stepsLogged()) println(askStepsLine(it)) }` (review round 2; it was `isDebugBuild()`); its KDoc says when that second line prints. The release line prints the same, now from `WATCH_LOG_TAG`. |
| `shared/src/commonTest/.../watchfeed/WatchFeedSyncTest.kt` | Two Ruling 70 sections (17 tests after review round 1); the harness passes the test scheduler's `timeSource` and keeps each ask's `AskTimes`. |
| `shared/src/commonTest/.../watchfeed/WatchFeedLogTest.kt` | `anAsksStepsAreOneDebugLine`; review round 2's `theStepsLineIsOnInADebugBuildOrWhenItsTagIsEnabled` and `theTagTheStepsLineIsAskedByIsItsFirstWord`. |
| `shared/src/commonTest/.../watchfeed/FakeWatchFeedMemory.kt` | `readTakes`, and the `takes` queue (review round 1), so a slow record read is timed on virtual time. |
| `shared/src/androidHostTest/.../watchfeed/DebugBuildTest.kt` (new), `shared/src/iosTest/.../watchfeed/DebugBuildTest.kt` (new) | The two `isDebugBuild` actuals; the iOS `stepsTagEnabled` (review round 2). |
| `docs/superpowers/plans/2026-10-23-watch-companion-2-phone-feed.md` | One "As built, Ruling 70 item 1" bullet under Task 4's `WatchFeedSync`. |
| `docs/superpowers/plans/2026-10-24-watch-companion-5-ship.md` | Task 13 check 9: Ruling 70 item 1's measure on the candidate, with the setprop (review round 2). |

## Ruling 70 item 1, as built

1. **Asked again, once, after 30 s.** When an ask's `withTimeoutOrNull(budget)` gives null (the
   budget's cut), the worker launches, on the sync's own `scope` and `work` dispatcher, a
   coroutine that waits `AGAIN_AFTER` and queues the same ask: its reason, location, echo (`have`)
   and watch (`from`), with no caller waiting. It is served through the same path, `serve`, with a
   budget of its own, and reports as any ask does. Its own cut, or any answer, is final: "a second
   FAILED waits for the usual triggers".
2. **Only the budget's cut.** A `FAILED` the link answered, a throw (the link's, the question's, a
   stray `CancellationException`), a feed that cannot be encoded, `NO_WATCH`, `NO_FEED`, `SENT` and
   `UNCHANGED` are never asked again.
3. **The same path is safe**, as the ruling says: a `WATCH_CHANGED` asked again forgets again (a
   number set aside, one more send at most); a `WATCH_ASKED` asked again takes in the same echo
   again, and an echo older than one taken in since only lowers the confirmation (one more send,
   never a wrong `UNCHANGED`: `holds` needs the latest number).
4. **A later ask takes the place of a reschedule's** (my addition, for the Lead to ratify: For the
   Lead, 1; narrowed in review round 1). Only `RESCHEDULED` carries a planned location of its own
   (`afterPlan`, `AppContainer.kt:265`), and only its ask again carries the count of asks as made
   served at its cut; when it comes due and another ask as made was served since (one queued behind
   the cut one, or one made in the 30 s), it is dropped unserved. Without this, a reschedule cut for
   London and followed by one for Tripoli would send London's feed 30 s later, over Tripoli's: the
   watch would show another city than the phone until the next trigger (app = site = watch). A
   settle, an acknowledgement or another ask asked again is no ask as made and takes no place.
   Every other ask (`WATCH_ASKED`, `WATCH_CHANGED`, `WATCH_APPEARED`, `PHRASES_CHANGED`, and a
   reschedule with no location from the debug seam) is built by `feedFor(null)` from the stored
   location, clock and settings when it is served, which `LocationRefresher` stores before it hands
   a plan its location, so it can never send an older plan: it is asked again whatever is served
   meanwhile, and a cut in its step 0 (the forget, or the echo taken in) is never lost.
5. **Nothing waits for it.** The 30 s are never spent in the worker's turn, so the asks behind the
   cut one are served at the cut, and `settle` (the receivers' and the top-up worker's last step,
   inside their 8 s) does not wait for it. It lives as long as the process and the sync's scope:
   cancelling the scope drops it (the queue is closed), a process that ends inside the 30 s
   loses it, and a cached Android process may run it only when it next thaws (For the Lead, 2).
6. **The budget stays 5 s**, `CALLER_BOUND` 10 s, the receivers' 8 s untouched.
7. **Each ask's steps, timed.** `answer` runs each step inside `StepTimes.time`, on the sync's
   `clock`: step 0 as `forget` (`WATCH_CHANGED`) or `echo` (`WATCH_ASKED`), then `watches` (the
   capability query: Android's one reading, taken at `reachable()`), `build`, `encode` (JSON and
   fingerprint), `record` (the watches' keys and the record's read for step 4) and `send` (the
   number written down, then the link; a `TOO_LARGE` adds `encode` and `send` again). The step an
   ask ended in, the budget's cut or a throw, is kept unfinished, timed up to that end, so a cut
   names the step that held it. `timed` gets an `AskTimes` once per ask (reason, `again`, result,
   `cut`, steps), after `report`.
8. **Logged in a debug build, or on request** (review round 2). `AppContainer` prints `askStepsLine` only when `stepsLogged()`: a debug build, or on Android a signed one once `adb shell setprop log.tag.TaqwaWatch DEBUG` is set. For example
   `TaqwaWatch STEPS WATCH_ASKED FAILED cut: echo 2ms, watches 812ms, build 4186ms unfinished`, or
   `TaqwaWatch STEPS WATCH_ASKED again SENT: …` for the ask asked again. The line names no place,
   person, origin or delivery (Ruling 52 item 4, Ruling 39). A throw from either callback is caught
   (`attempt`), so a context not yet set can never cost an ask. The release line,
   `TaqwaWatch <reason> <result> <ms>ms`, is unchanged.

## Tests, test-first

Each was written before its code and seen red for the reason it exists:

- **Red on the base's code (no ask asked again):** `anAskTheBudgetCutsIsAskedAgainThirtySecondsLater`
  (P2.7 round 2's case on both links: a reinstalled watch's handshake cut in its capability query,
  reports `WATCH_ASKED FAILED` at 5 s and `WATCH_ASKED SENT` at 35 s, X sent), `theAskAskedAgainIsBuiltForTheSameLocation`,
  `anAskCutAgainWaitsForTheUsualTriggers` (cuts at 5 s and 40 s, nothing else for 10 minutes, the
  next trigger still serves). The first try at the others used `advanceUntilIdle`, which does not
  run `backgroundScope`'s tasks, so they read nothing; they wait on virtual time (`delay`) instead.
- **Guards, green on the base and red on the mutant each exists to catch** (in this worktree, each
  mutant taken out again with `git checkout`):
  - every `FAILED` asked again: `onlyAnAskTheBudgetCutIsAskedAgain` red;
  - no place taken: `aLaterAskTakesThePlaceOfTheAskAskedAgain` red;
  - the 30 s spent in the worker's turn: `theAskAskedAgainHoldsNothingBehindIt` red, with 5 others;
  - asked again after its own cut: `anAskCutAgainWaitsForTheUsualTriggers` red (it first waited
    1 minute, which a third ask at 75 s outlived; it waits 10 minutes now);
  - the wait launched on a scope of its own: survived, as it must: the cancelled sync's queue is
    closed, so it can never be served either way. `cancellingTheScopeDropsTheAskAskedAgain` tests
    that behaviour, and its KDoc says so.
- **Red on a skeleton** (the types and parameters, `timed` never called, an empty line):
  `eachAskTimesItsSteps` (`watches 1000, build 2000, encode 0, record 200, send 1200` on virtual
  time, the send's number read included), `stepZeroIsTimedByWhatItDoes` (`forget` and `echo`),
  `anAsksStepsEndWhereItEnds` (no watch, no feed, unchanged, sent, too large), `theStepTheBudgetCutIsNamed`
  (`echo 0, watches 1000, build 4000 unfinished`, cut, and the ask asked again says `again`),
  `theStepAThrowEndedIsUnfinished` (a NaN: `encode` unfinished, not cut), `anAsksStepsAreOneDebugLine`.
  Mutants, each red: the unfinished step dropped (2 tests), the record step timed as the build (3),
  the send untimed (3), `cut` not told (1), `again` not told (1), the real clock for the steps (4),
  step 0 untimed (2).
- **`DebugBuildTest`:** on the host, `debuggable(flags)` red on a stub answering false, green on
  `FLAG_DEBUGGABLE`; on the iOS simulator, red on a stub (1 test, 1 failed, under `ios-sim-tests`),
  green on `Platform.isDebugBinary` in the whole run below.

## Review round 1

Two reviewers (the spec, behaviour and evidence lens; the code quality, platform APIs, tests,
privacy, accessibility and hygiene lens) found 0 Critical and 4 Important, two of them the same
finding. Fixed, test-first, in "Watch feed: a cut ask asked again gives way only to a newer plan,
after review (Ruling 70)":

1. **The place rule was too broad** (both lenses). It dropped the ask again of every reason
   whenever any ask was served after the cut, so a reinstalled watch's handshake (echo `""`) cut in
   its echo step, with the capability change a reinstall also sends queued behind it, answered
   `WATCH_ASKED FAILED, WATCH_APPEARED UNCHANGED`, one send, and was never asked again: the watch
   kept nothing until its next handshake. Narrowed to the ask that carries a planned location
   (item 4 above). New tests, red on `85066068` for that reason, green now:
   `aHandshakeCutInItsEchoIsAskedAgainThoughAnAskWasServedMeanwhile` (the reviewers' case on both
   links: `WATCH_ASKED FAILED @5000, WATCH_APPEARED UNCHANGED @5000, WATCH_ASKED SENT @35000`, X
   sent twice) and `anAskWithNoPlannedLocationIsAskedAgainWhateverIsServedMeanwhile` (all five
   reasons with no location, a reschedule for Tripoli served at 20 s). `FakeWatchFeedMemory` has a
   `takes` queue, so only the first record read is slow.
2. **Two mutants survived all 56 tests.** Guards added, green on the head and red on their mutants:
   `aSettleOrAnAcknowledgementNeverTakesThePlaceOfTheAskAskedAgain` (a settle or an
   acknowledgement counted as served drops London's ask again behind every `settleTheWatch` and
   every ACK) and `theAskAskedAgainCarriesItsEchoAndItsWatch` (the iPhone's session watch `w1`
   echoing nothing, cut in its echo step, is sent X again; Android's handshake echoing X's
   delivery, cut the same way, is confirmed `UNCHANGED` with nothing sent twice). A ninth mutant
   I found while checking, an ask asked again counted as served, survived too and is now held by
   `anAskAskedAgainNeverTakesThePlaceOfAnother`: a handshake asked again and cut again would
   otherwise drop London's ask again, leaving its plan unasked.
3. **The in-process 30 s on Android** (spec lens). For the Lead 2 below now names
   `PhoneWatchListenerService` and Android 14's cached-app rule, and asks P5.13 to measure it. No
   code changes: the ruling asks for an ask again in the sync, and a durable one is new machinery
   for the Lead to rule on after that measure.

**Mutants** (host, `WatchFeedSyncTest` + `WatchFeedNamedCasesTest` + `WatchFeedDeliveryModelTest`,
each taken out again with `git checkout`): a settle counted as served, an ack counted (1 red each);
the ask again without its echo and watch, without its echo alone, without its watch alone (1 red
each); every ask's place taken, as on `85066068` (2 red); no place taken (1 red); asked again after
its own cut (1 red); an ask asked again counted as served (1 red, after the new test).

**Minors left** (the code lens's, recorded in the ledger): the seeded delivery model reaches no ask
again (its cuts are never served again within its runs); an ask again dropped for a newer plan
leaves no line (the release line was not to change, and the debug line is per ask served); a cut can
end with no unfinished step, as `withTimeoutOrNull`'s documented race allows (the block may finish
just as the time runs out); a throwing `timed`, and `DebugBuildTest` beyond the flag's mask, are not
tested; P5.13's release-signed build prints no step line (For the Lead 3).

## Review round 2

The code lens found it clean (0 Critical, 0 Important, 8 minors). The spec lens found 0 Critical,
1 Important and 5 minors. Fixed, test-first, in "Watch feed: a signed build prints each ask's step
times once its tag is set to DEBUG, after review (Ruling 70)":

1. **The step line was gated on a debug build alone**, so the signed candidate that P5.13 runs,
   the pass Ruling 70 item 1 names, could never name a slow step, and For the Lead 3's remedy (the
   debug build of the same commit) cannot work on his devices. The gate is now plan 5 Task 12 Step
   0's for the compass log: `stepsLogged() = stepsLogOn(isDebugBuild(), stepsTagEnabled())`, with
   Android's `stepsTagEnabled()` as `Log.isLoggable("TaqwaWatch", Log.DEBUG)` and the iPhone's
   false. Google's reference for `Log.isLoggable`: "The default level of any tag is set to INFO",
   changed with `setprop log.tag.<YOUR_LOG_TAG> <LEVEL>`. Its tag-length limit applies to API 25
   and below only, and the phone's `minSdk` is 26. It is read at each ask, so a setprop reaches the
   next ask with no restart. `WATCH_LOG_TAG` now spells the word both lines begin with. Plan 5 Task
   13 gains check 9, the ruling's measure (a fresh install's first sync, and a reinstalled watch's
   first handshake with the phone app backgrounded), with the setprop before and `INFO` after. For
   the Lead 2, 3 and 4 say so.
   - `WatchFeedLogTest.theStepsLineIsOnInADebugBuildOrWhenItsTagIsEnabled`, the four cases, false
     only for (false, false): red on a stub keeping today's gate (`= debugBuild`) for its reason, the
     (false, true) case off. Green now.
   - `theTagTheStepsLineIsAskedByIsItsFirstWord` holds the tag check 9's setprop names.
   - The iOS `DebugBuildTest.theIPhoneHasNoTagToTurnTheStepsLineOn`.
   - Mutants, each red: the tag alone, always on, both needed (1 red each), another tag (2 red).
   - No ruling is contradicted: Ruling 70's "in debug" holds, since a debug build always prints the
     line and a signed one only at the DEBUG level. Rulings 39 and 52 hold, since the line names no
     place, person, origin or delivery.

**Minors fixed with it** (doc and test-fake only): the table's 12 Ruling 70 tests (17) and
`readTakes` alone (the `takes` queue); For the Lead 1's cost, which said "fails too" though any
answer of the later ask takes the place, `NO_WATCH` included; the spec lens's alternative (a
reschedule's ask asked again built from the stored location, with no place rule) is now in For the
Lead 1 for the Lead; plan 5 Task 13 had no Ruling 70 measure (check 9); `FakeWatchFeedMemory`'s
local `takes` shadowed the queue (now `wait`). The checks below are this round's.

**Minors left** (recorded in the ledger): an ask asked again dropped for a newer plan leaves no
line; `withTimeoutOrNull`'s race (a cut with no unfinished step); a throwing `timed`, the Android
`isDebugBuild` read and the Android `Log.isLoggable` read have no host test (no Robolectric in
`:shared`; check 9's last step proves the tag read on the signed build); the seeded delivery model
still reaches no ask asked again; the gate is read at each ask (now deliberate, so a setprop needs
no restart).

## Checks

- After review round 2: `./gradlew :shared:testAndroidHostTest --rerun` 2399 tests, 0 failures
  (`WatchFeedLogTest` 6, `DebugBuildTest` 2); **`scripts/test.sh` whole, exit 0 in 440 s** under
  `ios-sim-tests` (taken 00:27:38, released 00:35:29): versions agree, bump PASS,
  `test_privacy_check` PASS, privacy-check 263 rows (263 approved, 0 awaiting) / 7 policies / 233
  sweep lines clean, listing PASS + clean, `swift test` 285 + the rest 0 failures; K/N shared 2376
  (the iOS `DebugBuildTest` 2, `WatchFeedLogTest` 6), widgetcore 109, watchcore 198; host shared
  2399, widgetcore 111, watchcore 231; wearApp 518; 0 failures, no `EOFException`. After it, KDoc
  words alone changed ("debug line" to "step line"): host `--rerun` 2399/0 again and
  `:shared:compileTestKotlinIosSimulatorArm64` exit 0. `tools/i18n-check.py` clean; `git diff
  --check` clean; `git merge-tree` with `watch-companion` clean.
- After review round 1: `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.watchfeed.*' --tests 'world.taqwa.app.notifications.*'`
  416 tests, 0 failures (`WatchFeedSyncTest` 61); `scripts/test.sh` whole as recorded in the
  ledger's fix-round line.
- Before review: the same host run, 411 tests, 0 failures (the named cases, the seeded model, the coordinator, the receivers, the
  top-up worker, the listener service and the debug seam among them, all unchanged).
- **`scripts/test.sh` whole, exit 0 in 517 s**, under `ios-sim-tests` (taken 23:01:42, released
  23:10:19): versions agree, bump PASS, `test_privacy_check` PASS, privacy-check strict 263 rows
  (263 approved, 0 awaiting) / 7 policies / 233 sweep lines clean, listing PASS + clean, `swift
  test` all passed, `tools/timetables` compiled; K/N `iosSimulatorArm64Test` shared 2368 (the iOS
  `DebugBuildTest` 1/0, `WatchFeedSyncTest` 56/0), widgetcore 109, watchcore 198; host shared 2392
  ran, widgetcore 111 and watchcore 231 from cache (no input of theirs changed); wearApp 518; 0
  failures, 0 skipped, no `EOFException`.
- `:androidApp:lintDebug`: 0 errors (62 warnings, 2 hints; it does not lint `:shared`, which has
  no main lint task of its own).
- `tools/i18n-check.py` clean (no string changed); `git diff --check` clean.
- Not run: `scripts/ios-build.sh` (the Lead's merge step for `shared/`; the iOS actual compiled
  and ran in the simulator test binary), and no device pass: the ruling's measures on the Galaxy
  are P5.13's.
- Rulings 39, 41, 42 and 43 hold: no place, bearing, coordinate, origin or delivery in any new
  line; nothing drawn changes; no face file, vector or permission touched; Ruling 55 item 6 holds
  (no test vector moves into an APK).

## For the Lead

1. **Ruling 70 item 1's text has no place-taking rule; I added one, narrowed in review round 1**
   (item 4 above): a reschedule's ask asked again, the one ask with a planned location of its own,
   is dropped when another ask as made was served after its cut. Without it the watch can be sent
   an older plan's city after a newer one. Its cost: the later ask takes the place whatever it
   answers (`NO_WATCH`, a `FAILED` the link gave, or a cut whose own ask again is cut too), and
   London's plan is then not tried again; the stored location is the newer plan's, so the next
   trigger sends the right city. Every other ask is asked again whatever is served meanwhile, at
   the cost of one more send at most. Ratify, or rule otherwise. An alternative from review round
   2's spec lens, not built: a reschedule's ask asked again could drop its planned location and
   build from the stored one (`LocationRefresher` stores it before it hands a plan its location,
   so it is that plan's or a newer one's); it would then need no place rule and lose no ask.
2. **The ask asked again lives in the process, and is not shown to run in a cached one.** It is a
   `delay` on `watchFeedScope`. Ruling 70's own case is a reinstalled watch's handshake while the
   phone app is in the background, served in `PhoneWatchListenerService` (a `WearableListenerService`
   Play services binds for the message), and the same holds for `SystemEventReceiver`,
   `PrayerAlarmReceiver` and the top-up worker. Android 14's behaviour changes for all apps, under
   "System enforces cached-app resource usage", say: "Shortly after an app process enters a cached
   state, background work is disallowed, until a process component re-enters an active state of
   the lifecycle." So once the service is unbound and the process is cached, the 30 s may not run
   at 30 s: it runs at the next thaw (often just ahead of the waking component's own ask, inside its
   8 s), or never if the process is killed; the usual triggers heal it, as before Ruling 70. Not
   measured here (the paired emulators were Lane W's, and the task has no device step). **P5.13
   (the S23, Android 16) should measure it:** the reinstalled watch's first handshake with the
   phone app backgrounded and the phone's screen off, reading for a second `TaqwaWatch WATCH_ASKED`
   line about 30 s after a `FAILED`, on the candidate itself: after `setprop log.tag.TaqwaWatch
   DEBUG` its step line says `again` (For the Lead 3, plan 5 Task 13 check 9). If it does not run
   there, the remedy is a durable ask again: WorkManager one-time work with a 30 s initial delay (a framework-supported lifecycle API, which
   the same page says is not affected), queuing the same ask. On the iPhone the handshake's
   background time ends with its reply, so a suspended app's 30 s run on when it next runs, and the
   ask again is served then (safe: a later plan, if any, took a reschedule's place; any other ask
   builds from the stored state, and an old echo costs one more send at most).
3. **P5.13's signed candidate prints the step line on request** (review round 2). Until then a
   debug build alone printed it, and this item's remedy, the debug build of the same commit, cannot
   work on his devices: Task 13 puts the phone APK on his S23 over the store build, and "The Data
   Layer joins only equal keys", so a debug phone build never talks to the release-signed Wear app
   (and both in debug is no candidate check, and risks the watch's one activation). That remedy is
   withdrawn. The Android gate is now plan 5 Task 12 Step 0's for the compass: `isDebugBuild() ||
   Log.isLoggable("TaqwaWatch", Log.DEBUG)`, so `adb -s <S23> shell setprop log.tag.TaqwaWatch
   DEBUG`, typed by him, turns it on at the next ask, and `INFO` or a reboot turns it off. Task 13's
   new check 9 does that. It is safe on a store build: the line names no place, person or delivery
   (Rulings 39 and 52), and it is off until someone with the phone on `adb` asks. The ruling's "in
   debug" is kept: a debug build always prints it, and a signed one only at the DEBUG level. The
   gate's Android half (`Log.isLoggable`) has no host test (`:shared` has no Robolectric); check 9's
   last step proves it on the signed build itself (a step line after the setprop, none after
   `INFO`). On the iPhone there is no such switch (`stepsTagEnabled()` is false), and Kotlin's
   `println` reaches Xcode's console only, not the unified log (plan 2, Task 8).
4. **The release line does not mark the ask asked again**: it reads as a second line of the same
   reason about 30 s after a `FAILED`. Changing the release line was not asked for. Since review
   round 2 the step line, which says `again`, can be turned on on the candidate (For the Lead 3), so
   For the Lead 2's measure reads it there.
5. **What the step times do not cover:** the time before the serve (P2.7 saw 9.6 s in Play
   services' delivery of the handshake), Android's `oneAsk` wrapper and the gaps between steps.
   The release line's total covers everything after the serve. A cut lands at the next suspension
   point, so a slow CPU-only step (the encoding) is named by its own finished milliseconds and the
   step after it may be the unfinished one.
