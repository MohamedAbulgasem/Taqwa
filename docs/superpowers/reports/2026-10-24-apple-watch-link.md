# Plan 4 Task 5B: the link end to end on the simulators

Lane A, in `watch-apple` (plan 4's own worktree), 31 October 2026. Base `9d747537` ("Merge watch-apple: P4.9"), which is `watch-companion`'s head (`git merge --ff-only watch-companion`: already up to date). The device part ran on the iPhone 17 Pro `2D01CE58` paired with the Apple Watch Series 12 (46mm) `C803D94B`, both on their 27.0 runtimes, under the `ios-pair` lock (11:17:16 to 11:36:08 SAST), and `watch-build.sh test` on the SE 3 `FCA316A7` under `sim-se3` (11:08 to 11:16). The trimmed `TaqwaWatch` lines of both devices, WatchConnectivity's own size checks and the step times are in [`2026-10-24-apple-watch-link/`](2026-10-24-apple-watch-link/).

**In one line:** on the simulators the iPhone's feed reaches the watch, a handshake that holds the latest delivery is answered UNCHANGED with no send, a city changed on the phone reached the closed watch app in under a second, and a handshake woke the closed iPhone app, which answered in 0.34 s. The iPhone's copy of the context lies inside Taqwa's own container, excluded only through `Library/Application Support`. **The Backups sentence stays** (Ruling 39). WatchConnectivity compresses a payload before it checks its size. With bytes no compressor can shrink, `transferUserInfo` takes 65,536 serialized bytes and `updateApplicationContext` about 256 KiB, both above `FEED_BUDGET_BYTES + ENVELOPE_BYTES` (61,024). userInfo is never handed to the watch app on this simulator, and `simctl unpair` erases a simulated watch.

## What was made

- **`iosApp/iosApp/WatchHarness.swift`** (`#if DEBUG`): `WatchHarness`, with `shared`, `handle(_:) -> Bool` (discardable) and `transferFinished(_:error:)`. It reads `taqwa://watch/probe?via=context|userInfo&from=&to=&step=&fill=random|zero` and sends `[probe: <n bytes>]` on `WCSession.default` for each size the search gives, one at a time. It logs each answer, then `TaqwaWatch probe <via> <fill> <largest accepted> <first refused>[ incomplete][ unmeasured]` (since review round 1; the lines recorded below are the task's own build, which wrote `<via>[ random]`). For `userInfo` it then cancels every outstanding probe transfer. Last, it calls `WatchFeedBridge.shared.watchStateChanged()` and logs `TaqwaWatch probe restore`. A userInfo transfer unanswered in 30 s logs `no-answer <n>` and stops the probe, and so does any error but `payloadTooLarge`.
- **`iosApp/iosApp/WatchProbe.swift`** (`#if DEBUG`, pure): the probe's rules, which `swift test` holds on the Mac:
  - `WatchProbeVia`;
  - `WatchProbeRequest(url)`, with the plan's bounds as defaults, and `random` as the default fill since review round 1;
  - `WatchProbeSearch`: `from`, then `to`, then the middle of the gap until it is at most `step`; every size sent once, eight at most for the plan's bounds; its `line`;
  - `WatchProbeFill`: a fixed SplitMix64 stream that does not compress, or zeros by name.
- **`iosApp/iosApp/iOSApp.swift`:**
  - in DEBUG, `taqwa://watch/…` goes to `WatchHarness.shared.handle(_:)`, beside `RecitationHarness`;
  - `TaqwaWatchSession` gains the DEBUG `session(_:didFinish:error:)`, which hands the answer to `transferFinished`.
  - A release build is unchanged.
- **`iosApp/TaqwaWatch/WatchSession.swift`:** the DEBUG `session(_:didReceiveUserInfo:)` logs `TaqwaWatch userInfo <bytes>` (the size of its `probe` value, `none` without one) and drops it. A release build does not implement it. The selector `session:didReceiveUserInfo:` is in the installed watch binary (`TaqwaWatch.debug.dylib`, byte-equal to the build).
- **`iosApp/WatchCore/Package.swift`:**
  - a test-support target `PhoneHarness`, whose one source is a link to `iosApp/iosApp/WatchProbe.swift` (`Tests/PhoneHarness/WatchProbe.swift`), as `WatchViews` links the watch's views;
  - its tests, `PhoneHarnessTests/WatchProbeTests.swift` (14).
- **`tools/add-watch-targets.rb`:** `WatchHarness.swift` and `WatchProbe.swift` join `iosApp`'s Sources (iosApp lists its files by hand), found in iosApp's own group and checked on disk. The re-run added two file references and two build files to the iPhone target and nothing else; a second run left the project byte-identical.

## The errata and rulings

1. **Erratum 1 (`git merge --ff-only watch-companion`):** already up to date, base `9d747537`.
2. **Erratum 2 (`ios-pair` for the whole task; the backup check answers Ruling 63 item 4(f) for the watch side):** `ios-pair` was held through all of Step 3, never beside P4.10 (Lane A's next task, not started). What an iPhone backup could hold of the watch's copy is under "The backup check" below. Lane P was told by a ledger line, 31 Oct 11:38.
3. **Plan 4 errata (p4):**
   - **#8** (intake off for a debug sample): every launch here was plain, so intake and handshakes were on, as this task needs. No `-taqwa…` argument was used on the Series 12.
   - **#16** (install the debug iPhone Taqwa with the watch app on a paired simulator, and put the store build back): the debug `Taqwa.app` went on the phone before the watch app went on the watch. The store's 1.2.0 (38) was saved first (as `prev-Taqwa.app`, the name the Lead's put-back had left, tree md5 `b0ea6765`) and reinstalled after, with its data and group containers restored byte for byte.
   - The other p4 errata touch Tasks 3, 6 to 11, not this one.
4. **Rulings 55 to 64:**
   - Ruling 56 (the Swift port's ENCODER / ONE_CHANGE / FOREIGN contract) is untouched: no decoder or vector changed, and the feed the watch read is the phone's own encoder text (ENCODER).
   - **Ruling 64 item 8:** both simulators' states were recorded before use and put back. The Series 12 was found Booted since 00:06:16, the Lead's P4.6 put-back boot, with no lock and nothing attached, so it was left Booted (see "Put back").
   - Ruling 39 below.
5. **WIP commits:** `2ca9c13b` (the harness, the delegate, the watch's userInfo method and the probe's rules) and `9b697a8e` (the fill), folded into the task's commit.
6. **Host state:** Xcode 27.2 with its components; every simulator step ran, each under its lock. The iOS Simulator control tool was not needed: every step is `simctl` (installs, launches, terminations, `openurl`, `io screenshot`, `log`).
7. **Devices:** only the SE 3 (`sim-se3`) and the pair (`ios-pair`). No real device, no work iPhone 12, and `KB2604110095` was untouched.
8. **The Gradle flake** did not arise: no Gradle task was run (nothing here is Kotlin; `swift test` covers the package change).
- **Ruling 39:** the watch sent the phone only the handshake's echo, and no line logged by either app names the origin, the city or a coordinate (checked over the saved evidence). The verdict is below. "Only your own devices can see your location." and the relay sentence are plan 5's to apply, and nothing here touches them. **Rulings 41, 42 and 43** concern the face and Task 11: untouched.

## The steps and their evidence

### Step 1: the harness, test-first where it has rules

The plan calls this step DEBUG plumbing whose evidence is Step 3's logs. Its rules (which size to send next, when to stop, what the line says, what the URL means) decide the ship gate's numbers, so they were split into `WatchProbe.swift` and written test-first:

- `WatchProbeTests` was written against a stub that compiled and did nothing. Seven of eight failed on assertions; `testOnlyAWellFormedProbeIsARequest` passed against an always-nil stub, as a rejection test must, and was then held by mutants.
- After the rules: 10/10. Twelve mutants were each killed:
  - no host check, `to >= from`, no step check, a case-blind via, any path;
  - the gap compared with `>= step` or a step and a thousand;
  - `to` sent first, the middle a third of the way;
  - the largest accepted or the smallest refused overwritten, the default `from` changed.
  - The first run left two alive (the gap rule and the overwrite were equivalent for the plan's bounds), so `testAGapOfExactlyAStepIsDone`, `testTheBoundsKeepTheLargestAcceptedAndTheSmallestRefused` and a second set of bounds were added.
- The fill (Step 3's finding below): four more tests (`testTheFillIsZerosUnlessRandomIsAskedFor`, `testZerosAreZeros`, `testRandomBytesAreTheSameEachTimeAndDoNotCompress` (zlib, LZFSE, LZ4 and LZMA each leave at least 99 %), `testTheLineNamesARandomFill`). They were red first (not building: no `fill`, no `WatchProbeFill`), then green 14/14.

### Step 2: the builds and the watch's UI tests

- `swift test --package-path iosApp/WatchCore`: WatchCoreTests 285/0, WatchViewsTests 6/0, PhoneHarnessTests 14/0, no warning.
- `scripts/ios-build.sh` (iPhone 17 Pro destination, build only): BUILD SUCCEEDED at both WIPs and at the head. `TaqwaWidget.appex` 3 MB of 15, `TaqwaWatchWidgets.appex` 4 MB of 5. `check-watch-bundle.sh`: every rule holds (4,380 of 5,120 KB and 12,000 of 15,360 KB; rule 4b skipped ad hoc, rule 8 skipped for a Debug bundle). No warning from the new files; the one Swift warning is the existing unused `KotlinUnit` at `iOSApp.swift:212`.
- `scripts/watch-build.sh test` on the SE 3: TEST SUCCEEDED, 23/0 (LaunchTests 3, QiblaTests 5, TasbeehCrownTests 3, TasbeehTests 8, TodayReadingOrderTests 4), at `2ca9c13b`. No watch target's source changed after it: the fill touched only the phone's two files and the package's tests.

### Step 3: end to end on the simulators

**Setting up.** No pair was listed, so the two were paired (`098E9B67`) and the phone booted (the watch was already booted). The phone's Taqwa was the store's 1.2.0 (38) with its location set by hand to Doha (`location_source` MANUAL). A MANUAL location ignores the simulator's own, so `simctl location` could not move it. London (`cities.csv` id 2643743, 51.509, -0.126, Europe/London, MANUAL) was therefore written into the stopped app's settings file (`taqwa.preferences_pb`), as plan 2's Task 8 Step 5 did. The write used a scratch DataStore reader and writer whose untouched round trip was byte-identical. Then the debug `Taqwa.app` went on the phone and `Watch/TaqwaWatch.app` on the watch.

**The first feed.** The watch app was launched (11:20:17), then the phone app (11:20:25). Times are SAST, from the logs:

| time | device | line |
|---|---|---|
| 11:20:18.188 | watch | `activated=true`, `nothing to store via activation`, `drain drained 108 ms` |
| 11:20:18.382 | watch | `reachable=true`, `handshake reachable have=none` |
| 11:20:20.597 | phone | `activate() asked`: a new process 2.2 s after the handshake and before the launch at 11:20:25, so WatchConnectivity started the app |
| 11:20:20.611 | phone | `isReady()=true activated=true paired=true installed=true` |
| 11:20:20.981 | phone | `send queued: bytes=21034 delivery=1/6efab8e0696fdd95` (the launch's ask) |
| 11:20:21.017 | phone | `send queued: bytes=21034 delivery=3/6efab8e0696fdd95` (the handshake's: the watch held nothing) |
| 11:20:21.018 | phone | `handshake answered queued=true` |
| 11:20:35.207 | watch | `accepted 1/6efab8e0696fdd95 31 days via context` |
| 11:20:35.211 | watch | `unchanged 3/6efab8e0696fdd95 31 days via context` (the newer number for the same feed: stored, nothing reloaded) |

The watch shook hands before the phone app had sent anything, holding nothing, so its handshake rightly drew a send. To see the plan's case, the watch app was relaunched at 11:21:53, now holding `3/6efab8e0696fdd95`:

- watch: `stale 3/6efab8e0696fdd95 via activation`, then `handshake reachable have=3/6efab8e0696fdd95` at 11:21:56.725;
- phone: `handshake have=3/6efab8e0696fdd95` at 11:21:58.425, then `handshake answered queued=true` at 11:21:58.472, with **no `send` line: UNCHANGED, no second send**.

**The backup check** (plan 2 Task 8's "For plan 4"; Ruling 39), right after the first `accepted`. `DATA` is the phone's `data` folder, `APP` its Taqwa data container.

- `grep -rlF 6efab8e0696fdd95` over `$DATA/Library/WatchConnectivity` and `$APP` finds two files, and the same two over all of `$DATA`:
  - `$APP/Library/Application Support/com.apple.watchconnectivity/C803D94B-…/ApplicationContext/context`: **the iPhone's copy of the application context, inside `$APP`.** It holds 6,499 bytes for the 21,034-byte feed (it is compressed), and the city can be read in it. Beside it, in the same folder, are the watch directory (`…/WatchContent/<hash>/taqwa-watch-key`) and `UserInfoTransfers/`. `$DATA/Library/WatchConnectivity` holds only an empty `Clients` folder, as found.
  - `$APP/Library/Application Support/Taqwa/taqwa.preferences_pb`: Taqwa's own settings, where the sync's memory keeps the fingerprint. It is not the context.
- **The key.** `xcrun swift build/backup-key.swift <paths>` (three lines, untracked) prints `isExcludedFromBackup` = **true** for every path from both files up to `$DATA`, `$DATA` itself included. That is the Mac's reading, not iOS's. CoreSimulator puts the exclude attribute on each device's own folder (`Devices/2D01CE58-…`, which `tmutil isexcluded` calls `[Excluded]`), and the Mac's key reads true for everything under a marked folder. `Devices` above it, `~/Library/Developer/CoreSimulator`, `~/Library/Developer`, `~/Library` and a file in the scratchpad each read **false**. So on a simulator the key is no evidence of an app's own marking.
- **The attribute behind it**, `xattr -l` (`com.apple.metadata:com_apple_backup_excludeItem`), is iOS's marking:

| path (from `$APP`) | exclude attribute |
|---|---|
| `Library/Application Support/com.apple.watchconnectivity/C803D94B-…/ApplicationContext/context` | none |
| `…/ApplicationContext`, `…/C803D94B-…`, `…/com.apple.watchconnectivity` | none |
| `Library/Application Support` | **yes**: `recitationFilesDirectory()` marks it at every launch (`RecitationPaths.ios.kt`) |
| `Library/Application Support/Taqwa` (the settings) | **yes**: `dataStoreDirectory()` |
| `Library`, `$APP`, and up to `$DATA` | none |

  So on this simulator, Taqwa's copy of the context is kept out of an iOS backup only because it lies under `Application Support`, which another module marks as a side effect. `QuranDb.ios.kt` itself says not to rely on that side effect.
- **The watch's copies of the same delivery** (Ruling 63 item 4(f), the watch side):
  - `FeedStore`'s folder in the group container (`Library/Application Support/TaqwaWatch`, holding `feed.json`, `feed.delivery` and `refresh.asked`) carries the attribute, as built.
  - WatchConnectivity's own received copy, `Library/Application Support/com.apple.watchconnectivity/2D01CE58-…/ReceivedApplicationContext/context` in the watch app's data container (6,499 bytes, the city readable), carries it **at no level**. Its staging folder `Documents/Inbox/com.apple.watchconnectivity/2D01CE58-…/ApplicationContext` was empty, and `Documents` is unmarked.
  - A simulated iPhone holds no backup of its watch, so nothing of the watch's copies is on the phone.
  - What keeps the received copy out of an iPhone's backup of its watch is Apple's statement that a watch's backup holds third-party apps' settings, not their data.
- **The verdict: the Backups sentence stays.** Each of these leaves it in place by Ruling 39:
  - a copy inside `$APP`;
  - the file itself not excluded;
  - its exclusion resting on a parent folder that another module marks;
  - the simulator's WatchConnectivity not being a device's.
  This is evidence for plan 5.
- Two facts for the privacy text, told to Lane P:
  - Apple documents neither where WatchConnectivity keeps the copy nor whether a backup holds it, and that stays true of Apple's documents. But the copy lies in each app's own container, so "WCSession has no way to keep it out" is not quite so: an app could mark its `com.apple.watchconnectivity` folder excluded. That is a Lead's call, and nothing is built.
  - "The watch's own copy is kept out of a backup" holds for `FeedStore`'s copy only.

**A city change with the watch app closed** (the Lead's call 4):

| time | what |
|---|---|
| 11:24:55 | watch app terminated |
| 11:24:56 | phone app terminated; Cape Town (id 3369157, -33.926, 18.423, Africa/Johannesburg, MANUAL) written into its settings, as London was, because the location is MANUAL |
| 11:24:58 | phone app launched |
| 11:24:59.677 | phone: `send queued: bytes=21037 delivery=4/22fe6eecbe87a32f`, a greater number and a fingerprint other than `6efab8e0696fdd95`, 1.7 s after the launch |
| 11:25:00.347 | the watch's `runningboardd` starts `world.taqwa.ios.watchkitapp`, unasked |
| 11:25:00.615 | watch: `stale 3/6efab8e0696fdd95 via activation` |
| 11:25:00.621 | watch: **`accepted 4/22fe6eecbe87a32f 31 days via context`**, 0.94 s after the send |
| 11:25:00.689 | watch: `drain drained 72 ms` (the activation's), then `drain drained 0 ms` at 11:25:00.695 (the WatchConnectivity background task's) |
| 11:25:00.691 to .711 | watch: `handshake reachable have=4/22fe6eecbe87a32f`, then `reachable=false` and `handshake reachable failed: WCErrorDomain 7007` (not reachable in the background; its stamp taken back). The phone still logged `handshake have=4/22fe6eecbe87a32f` at 11:25:02.415 and answered `queued=true` with no send. |

The watch's screen showed its own face throughout (shot at 11:25), so the app ran in the background only. **On the simulator, the new context reached the closed watch app in under a second, and the drain followed.** That is the simulator's answer only. The Lead's call 4 is decided on her watch in plan 5, and plan 2's sender is unchanged. The thirty minutes' watch was not needed.

**A handshake that cold-wakes the iPhone app** (Ruling 53 item 4b). The phone app was terminated at 11:26:07 (the watch app too, which the city change had left in the background), and the watch app opened at 11:26:11:

| time | device | line |
|---|---|---|
| 11:26:11.392 | watch | `handshake reachable have=4/22fe6eecbe87a32f` |
| 11:26:13.663 | phone | `activate() asked`: a new process 2.27 s later, with the app not opened by hand (the phone's screen showed the home screen) |
| 11:26:13.668 | phone | `handshake have=4/22fe6eecbe87a32f, calling refreshRequested(have:reply:)` |
| 11:26:14.012 | phone | `handshake answered queued=true`, 0.34 s after the handshake reached it, well inside `WatchFeedSync.CALLER_BOUND` (10 s); no send (UNCHANGED) |
| 11:26:15.321 | watch | `handshake reachable answered queued=true` |

**WatchConnectivity woke the iPhone app in the background** both times a handshake came while it was not running: here, and at 11:20:20 in the first feed. The device check is plan 5's.

**The probe**, with the watch app opened before each run, and the restore after each:

| run | phone's answer | watch | after the restore |
|---|---|---|---|
| `via=context` (the plan's zeros) | `probe context 300000 none`: 60,000 and 300,000 both accepted | `nothing to store via context` twice | `send queued … delivery=6/22fe6eecbe87a32f`; watch `unchanged 6/22fe6eecbe87a32f 31 days via context` |
| `via=userInfo` (zeros) | `probe userInfo 300000 none`: both accepted, `cancelled 0 outstanding` | **no `TaqwaWatch userInfo` line** | `delivery=8/…`; watch `unchanged 8/22fe6eecbe87a32f` |
| `via=context&fill=random` | `probe context random 258750 262500` (8 sizes) | `nothing to store via context` for each of the five accepted | `delivery=10/…` and `12/…` (see below); watch `unchanged 10/…`, `unchanged 12/…` |
| `via=userInfo&fill=random` | `probe userInfo random 63750 67500` (8 sizes) | no `userInfo` line | `delivery=14/…`; watch `unchanged 14/…` |
| `via=userInfo&fill=random`, `from=63750&to=67500&step=8` | `probe userInfo random 65521 65528` (11 sizes) | no `userInfo` line | `delivery=16/…`; watch `unchanged 16/…` |

After every restore the watch logged `unchanged` under a newer number with the fingerprint it held before the probe (`22fe6eecbe87a32f`): the real context was back, and nothing was reloaded. A probe context stored nothing.

**Why the plan's zeros measured nothing.** WatchConnectivity logs its own check, `WCIsSizeAcceptableForType(type, size)`, on the payload it has serialized and compressed:
- the 21,034-byte feed was checked as 6,507 bytes;
- 60,000 zeros as 258 bytes, and 300,000 zeros as 1,200.

[`wc-size-checks.txt`](2026-10-24-apple-watch-link/wc-size-checks.txt) keeps only the random runs' checks and their restores' (the 21,037-byte Cape Town feed checked as 6,240 and 6,244 bytes). The three figures above were read from the phone's unified log during the run and not saved with it. Review round 1 (spec lens) read the same 258 and 1,200 on `ios-pair` at 12:07:33 with its own build of `ffa03a88`.

So `Data(count: n)` never meets the limit, and its "300000 none" is no threshold. The harness gained `fill=random` (Step 1), bytes no compressor shrinks, which check as the payload plus 15 bytes. That measures the limit a feed would meet at its very worst; a real feed compresses to about a third.

**The thresholds, against `FEED_BUDGET_BYTES + ENVELOPE_BYTES` = 60,000 + 1,024 = 61,024:**

| call | largest accepted | first refused | WatchConnectivity's checked size | against 61,024 |
|---|---|---|---|---|
| `updateApplicationContext` (type 1) | 258,750 random bytes | 262,500 | 258,765 accepted, 262,515 refused: a limit of about 256 KiB, either side of `WatchLinkContract.CONTEXT_LIMIT_BYTES` (262,144) | above, by more than four times |
| `transferUserInfo` (type 2) | 65,521 random bytes | 65,528 | **65,536 accepted, 65,543 refused: 64 KiB to within 7 bytes**, `WatchLinkContract.USER_INFO_LIMIT_BYTES` (65,536) | above, by 4,497 bytes of incompressible payload |

Neither is below the budget, so nothing stops for the Lead.
- The userInfo figure is the iPhone's own check. Every transfer it accepted was answered `didFinish` with no error, within 25 ms to 1 s.
- **None was handed to the watch app.** The watch's `wcd` logs each message received from the simulator's relay (`Service recieved message`), but never the `WCDPayloadSendType` line it logs for each context. The app's `session(_:didReceiveUserInfo:)`, present in its binary, was never called, and the app's containers hold no queued user info.
- So on these simulators `transferUserInfo`'s acceptance is measured and its delivery is not. That gate stays open for plan 5's device.
- `didFinish` with no error came before the watch's daemon had even received the message (11:27:21.519 against 11:27:22.178), so "accepted" means WatchConnectivity on the phone took the payload, not that the watch app had it.

**Seen beside the steps, for the Lead:**
- After the phone app was reinstalled with the fill (11:30:14), its first launch sent the unchanged feed twice (`delivery=10` at 11:30:24.475 and `12` at 11:30:24.563): the launch's ask and the restore's WATCH_CHANGED. Without a reinstall (11:26:49) the launch's ask sent nothing.
- The delivery numbers rose by two between most sends (1, 3, 4, 6, 8, …, 16).
- Review round 1 explained the double send. At 11:29:02, before the reinstall, the phone logged `watch state changed: paired=true installed=false, calling watchStateChanged()` ([`phone.log`](2026-10-24-apple-watch-link/phone.log) line 74), which forgets the phone's memory, so the next launch's ask sent the feed again. What made the watch app read as not installed at 11:29:02 is not in the saved logs.
- After the background accept at 11:25:00.621 the watch logged `stale 4/22fe6eecbe87a32f via context` 18 ms later ([`watch.log`](2026-10-24-apple-watch-link/watch.log) line 25): WatchConnectivity called `session(_:didReceiveApplicationContext:)` twice with the same delivery, and the second was dropped unstored, as `WatchLinkContract.stores` rules for the same number.
- Both are plan 2's sync, not this task's, and neither is a fault the watch saw.

### Put back

- **iPhone 17 Pro**: the watch app and the debug app were uninstalled.
  - The store's 1.2.0 (38) was reinstalled from the saved copy (tree md5 `b0ea6765`, equal).
  - Its data and group containers' contents were restored byte-equal: Doha, no watch keys. The container UUIDs differ, as after any reinstall.
  - The phone was shut down and its Preferences restored from the snapshot (md5 list identical; two files the boot made were removed).
  - Groups, data containers and bundles are equal to the found ones by identifier, 40 apps, and `Library/WatchConnectivity` is as found.
- **The pair** `098E9B67` was unpaired (`xcrun simctl list pairs` empty).
- **Apple Watch Series 12**, a finding: **`simctl unpair` erases a simulated watch.** It shut the Series 12 down and re-made its data folder (11:33:53: no containers, two preference files). That answers the plan's open question (Global Constraints, "whether an unpair erases a simulator watch is not known"). The Lead's 00:06 unpair did the same, so the state found at 11:17 was itself a re-made watch after eleven hours of running.
  - Booted again, as found: unpaired, 15 apps, no Taqwa app or group, the same 38 data containers by identifier, `("en-US")`, `en_US@rg=zazzzz`, the Numerals face.
  - Five system groups (`amsondevicestoraged`, `feedbacklogger`, `iBooks`, `private.translation`, `servicesanalytics`) and nine system preference files, which daemons make as they run, were not back yet at 11:36.
  - Any later `ios-pair` task that pairs and unpairs should expect the same erase.
- **SE 3**:
  - put back after `watch-build.sh test`: the app and its UI-test runner uninstalled;
  - the empty `group.com.apple.calendar` container its boot made removed;
  - every Preferences file restored from the snapshot (md5 list identical);
  - shut down, unpaired, 39 groups and 38 data containers by identifier, as found.

## Where it differs from the plan

Written into plan 4 Task 5B as an as-built note:

1. The probe's rules are in their own file, `WatchProbe.swift`, held by 14 package tests through a linked `PhoneHarness` target. The plan names only `WatchHarness.swift` and calls the step plumbing without tests. `tools/add-watch-targets.rb` adds both files to `iosApp`.
2. `fill=random` (Step 3's finding): WatchConnectivity checks a payload after compressing it, so the plan's zeros cannot measure the limit. The plan's zero runs were made and are recorded; the thresholds are the random runs'. One more userInfo run with a step of 8 pinned 65,536. Since review round 1 `random` is the default, so the plan's URLs, which name no fill, measure the limit, and a zero run's line ends `unmeasured`.
3. `handle(_:)` returns a discardable `Bool`, as `RecitationHarness.handle` does, so the route can fall through.
4. London and Cape Town were written into the stopped phone app's settings, because its location is set by hand (MANUAL), and `simctl location` moves only the simulator's own.
5. The first feed came before the plan's order could: the watch, launched first, shook hands holding nothing and woke the phone app, which sent twice. The plan's "answered UNCHANGED, with no second send" was then seen on the watch's next handshake.
6. The city change needed no thirty-minute watch: `accepted` came 0.94 s after the send.
7. The watch's `userInfo <bytes>` lines never appear on these simulators, so they cannot "agree with the phone's accepted sizes". No `no-answer` came either: the phone's own check answered each size.

## Review round 1

Two reviewers read `9d747537..ffa03a88`: the spec, behaviour and evidence lens (NEEDS FIXES, 1 Important, 6 minors; it re-ran Step 3 on `ios-pair`) and the code quality, platform APIs, tests, privacy, accessibility and hygiene lens (CLEAN, 5 minors).

**The Important, fixed: the ship gate's probe sent zeros.** This task showed that zeros measure nothing, yet `WatchProbeRequest` still defaulted to `fill=zero`. Plan 5's check 10 and plan 4's "For plan 5" both run the plan's URLs, which name no fill, on her pair. A zero run's `300000 none` is above 61,024, so plan 2's size gate would have passed without measuring anything. Now:
- `random` is the default fill. Zeros go only by name (`fill=zero`), and an empty or unknown fill is no request (`testTheFillIsRandomUnlessZerosAreAskedForByName`, which holds plan 5's own URL).
- The line always names its fill, and a zero run's ends `unmeasured`: `TaqwaWatch probe context zero 300000 none unmeasured` (`testTheLineNamesItsFillAndAZeroLineIsNoThreshold`). A random line reads as the task's own did (`TaqwaWatch probe context random 258750 262500`). `line(_:fill:)` and the request's initialiser take the fill with no default, so no caller gets zeros by leaving it out.
- Plan 5's check 10 names `fill=random` in its URLs as well. It records the `random` lines, and it counts only a line with nothing after its two figures (neither `incomplete` nor `unmeasured`). Plan 4's "For plan 5" and its as-built note say the same.
- Test-first: the four changed tests were red on `ffa03a88`'s code (the default read `zero`, and the zero line had no `zero` and no `unmeasured`), then green.

**Re-verified** at the fix: `swift test --package-path iosApp/WatchCore` WatchCoreTests 285/0, WatchViewsTests 6/0, PhoneHarnessTests 14/0; `scripts/ios-build.sh` BUILD SUCCEEDED, with `check-watch-bundle.sh` holding every rule (4,380 of 5,120 KB, 12,000 of 15,360 KB) and only the existing `KotlinUnit` warning. The built `Taqwa.debug.dylib` holds the new `unmeasured`. No watch source changed, so `watch-build.sh test` was not re-run, and no device was used: the harness only passes the request's fill to `WatchProbeFill.data`, and Step 3 already exercised the random path end to end.

**Minors fixed in the documents:**
- The zero runs' and the London feed's checked sizes are cited as read from the log during the run, since `wc-size-checks.txt` does not hold them, and the review's 12:07:33 reading is named (both lenses).
- "exactly 64 KiB" is now a 7-byte bracket, and the context row names `CONTEXT_LIMIT_BYTES` (code lens).
- The double send after the reinstall is explained by `phone.log` line 74 (both lenses), and the second `stale 4/… via context` is reported (spec lens).
- Plan 4's Global Constraints no longer call the unpair's erase unknown, and its "For plan 5" no longer makes userInfo's open gate depend on `no-answer` alone (spec lens).

**Minors left**, for the Lead, in the ledger:
- userInfo answers are matched by size, not by the transfer `transferUserInfo` returned (code lens).
- `advance()` sends without checking the session's activation again, and `to` has no upper bound (code lens).
- The simulator's unified log under `$DATA` also holds the fingerprint, beside the two files the backup check names (spec lens).
- Apple's `transferUserInfo(_:)` reference, as the spec lens reads it, says the Simulator does not support it. This report does not cite it, though it is the likely reason no `TaqwaWatch userInfo` line came (spec lens).
- Plan 4's "For plan 5" still says the watch's own files are excluded from backup, so the policy's paragraph needs nothing for the watch. That holds for `FeedStore`'s copy only (Step 3), not for WatchConnectivity's received copy. It is left as written because the paragraph is Ruling 39's and plan 5's to word, and the Lead decides (spec lens; Lane P was told on 31 Oct at 11:38).
