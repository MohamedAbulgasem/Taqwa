# Plan 2, Task 8: the iPhone sender over WatchConnectivity

Lane P (`watch-feed`), 25 October 2026. Plan 2 Task 8 (P2.8), built to Ruling 50 (the feed's delivery number) with Rulings 47 and 48 as Ruling 50 keeps them, and amendment A3 (the watch key).

- Base: `5d2718f3` (watch-companion with Ruling 50 merged).
- Commit: the plan's Step 6 message, "Watch feed: the iPhone hands it to WatchConnectivity as UTF-8 data, damped at each launch, ready for the Apple Watch to read". The WIP `5f965f47` (the green code and tests) was folded into it by a soft reset.
- Devices: the iPhone 17 Pro (iOS 27.0) and the Apple Watch Series 12 (46mm, watchOS 27.0) simulators, under the `ios-pair` lock, for Step 5 alone. The plugged-in phone `KB2604110095`, the work iPhone 12 and every other device were not touched. No release key was read or used.

## What was built

| File | What |
|---|---|
| `shared/src/iosMain/.../watchfeed/WatchLink.ios.kt` (replaced) | `WatchSessionHook` (public), `WatchFeedBridge` (public), `WatchBridgeCalls`, `feedData`, `IosWatchLink` and `actual fun createWatchLink() = IosWatchLink()`. |
| `iosApp/iosApp/iOSApp.swift` | `import WatchConnectivity`; `Self.installWatchSession()` in `init()`, beside `installWidgetRefreshHook()`; `static func installWatchSession()`; `final class TaqwaWatchSession: NSObject, WCSessionDelegate, WatchSessionHook`. Every other line, Dev 1's widget changes from MM1 among them, is as it was. The project file is untouched: the class lives in a file the target already lists. |
| `shared/src/iosMain/.../settings/DataStoreFactory.ios.kt` | The comment only (Ruling 39 item 5): it quotes "Only your own devices can see your location." in place of "your location never leaves your phone"; its reasoning for the backup exclusion stands. No code changed. |
| `shared/src/iosTest/.../watchfeed/IosWatchLinkTest.kt` | 17 tests (below). |

### The Kotlin side

- **`WatchSessionHook`**: `isReady(): Boolean`, `watchKey(): String?`, `send(feed: NSData, delivery: String): Int`. In the framework header it is the protocol `WatchSessionHook` with `isReady() -> Bool`, `watchKey() -> String?` and `send(feed: Data, delivery: String) -> Int32` (checked in `shared.h`).
- **`WatchFeedBridge`**: `hook` (`@Volatile`, since Swift sets it on the main thread and the sync's worker reads it on `Dispatchers.Default`); `contextFeedKey`, `contextDeliveryKey`, `refreshMessageKey` and `refreshReplyKey`, each `WatchLinkContract`'s constant; `sendQueued = 0`, `sendFailed = 1`, `sendTooLarge = 2`; `refreshRequested(have)`, `watchActivated()` and `watchStateChanged()`, which hand over to a `WatchBridgeCalls` over `appContainer.watchFeedSync` and the hook.
- **`WatchBridgeCalls`** (internal) holds the three calls apart from the app's container, so that the tests run them over a sync of their own:
  - `refreshRequested(have)` reads `hook()?.watchKey()` first, as the message arrives (A3), then `runBlocking { withTimeoutOrNull(bound) { sync().sync(WATCH_ASKED, have = have, from = key) } }`, and answers true for `SENT` or `UNCHANGED`. `bound` is `WatchFeedSync.CALLER_BOUND` (10 s) unless a test passes a shorter one. A timeout, any other answer or any throw is false.
  - `watchActivated()` is `request(WATCH_APPEARED)`, `watchStateChanged()` is `request(WATCH_CHANGED)`; both return at once.
  - None of the three throws: a Kotlin exception that reached Swift through a non-`@Throws` call would end the app, so each catches `Exception` (a container that cannot be built included).
- **`feedData(json)`**: `json.encodeToByteArray()` as `NSData` (`NSData.create(bytes:length:)` over the pinned bytes; an empty `NSData` for an empty string, which no feed is).
- **`IosWatchLink(hook = { WatchFeedBridge.hook })`**: `confirmsDelivery = false`; `reachable()` is `hook()?.isReady() == true`; `watchKey()` is `hook()?.watchKey()`; `send` is `NO_WATCH` with no hook or a session not ready, else the hook's answer: `sendQueued` is `SENT`, `sendTooLarge` is `TOO_LARGE`, anything else or a throw is `FAILED` (a throw rethrown only when the caller's own coroutine is cancelled, `ensureActive()`, as the Global Constraints say).

### The Swift side, `TaqwaWatchSession`

- `start()`: when `WCSession.isSupported()`, `WatchFeedBridge.shared.hook = self`, then the delegate, then `activate()`.
- `isReady()`: `activationState == .activated && isPaired && isWatchAppInstalled`, logged with its three facts.
- `send(feed:delivery:)`: checks the three again right before the call and answers `sendFailed` with nothing handed over when any is false (a watch switch can make the session inactive in between, and `updateApplicationContext` on an inactive session is a programmer error). Then `try updateApplicationContext([contextFeedKey: feed, contextDeliveryKey: delivery])`: `sendQueued`; `sendTooLarge` for `WCError` `.payloadTooLarge`; `sendFailed`, logged, for any other error. Each answer is logged with `feed.count` and the delivery.
- `watchKey()`: nil when `watchDirectoryURL` is nil (the SDK header, `WCSession.h`: "If the watch app is not installed for the selected Watch the value will be nil", and the directory "will be deleted upon next launch if the watch app is uninstalled for the selected Watch, or that Watch is unpaired"; added by Ruling 53 item 4); otherwise the `taqwa-watch-key` file's text, or a new `UUID().uuidString` written there, logged as `TaqwaWatch key kept` or `TaqwaWatch key new` (never the key). Under an `NSLock`, so the worker's first read and a handshake's on WatchConnectivity's queue cannot each write a key of their own.
- The delegate: `activationDidCompleteWith` calls `watchActivated()` and `sessionWatchStateDidChange` calls `watchStateChanged()`, never the other way round; `sessionDidBecomeInactive` only logs; `sessionDidDeactivate` calls `WCSession.default.activate()`; `didReceiveMessage` with a reply handler and a `refresh` key replies `[queued: refreshRequested(have: message["refresh"] as? String ?? "")]`; the form without a reply handler calls it and drops the answer.
- Every line starts `TaqwaWatch` and goes through `NSLog`: Kotlin's `println` does not reach the unified log on iOS. Nothing logged names the place or the person: a delivery is a random install number, a counter and a fingerprint, and the key itself is never logged.

## How the known errata were resolved

1. **Ruling 50.** The context carries the delivery beside the feed: `[contextFeedKey: <the JSON's UTF-8 Data>, contextDeliveryKey: WatchDelivery.text]`, the text holding the origin, the number and the fingerprint (`aContextTooLargeIsTooLarge` checks the second send's `origin/2/<times-alone fingerprint>`). The handshake's key, its reply's key and the echo it carries are `:watchcore`'s (`WatchFeedBridge`'s four keys are the contract's constants, `theContractKeysAreTheWatchcoreOnes`; the echo is parsed by the sync through `WatchLinkContract.deliveryOf`). `SENT` moves `confirmedSeq` on this link because `confirmsDelivery` is false and `WatchFeedSync.deliver` then calls `handedOver(key)`: `theLaunchIsDampedAndTheWatchStateChangeIsNot` and `aHandshakeWithTheCurrentFeedIsAnsweredWithoutASend` show a `SENT` followed by `UNCHANGED` with no echo taken in between. Rulings 47 and 48 as Ruling 50 keeps them bind through the sync this task only drives; nothing in them is iPhone-only beyond the times-alone resend (`aContextTooLargeIsTooLarge`) and the bounded caller (`aHandshakeTheSyncCannotServeIsFalse`).
2. **MM1.** `iOSApp.swift` holds Dev 1's widget changes (`e5cafbca`). This task added only `import WatchConnectivity`, one line in `init()`, `installWatchSession()` and the new class; the diff has no other line. The project file is untouched.
3. **The `ios-pair` lock** covered Step 5 alone (07:03 to 07:10 SAST), and every simulator is back as found (Step 5 below).
4. **Ruling 39.** The link sends only to the paired watch's own Taqwa app through `WCSession` (`isPaired && isWatchAppInstalled`, checked twice), and what it sends is the builder's feed, which carries the city and the bearing to a whole degree and never a coordinate (Task 3's rules; this task adds nothing to the feed). The `DataStoreFactory.ios.kt` comment quotes the ruled line.
5. **The Gradle flake.** Not seen: `scripts/test.sh` passed on its first run, with no `EOFException` in the log.

## The choices made inside the plan

- **The hook is set before `activate()`,** where the plan's text sets it after. Activation completes on WatchConnectivity's own queue, so its `watchActivated()` ask could reach the sync's worker before `start()` had set the hook, and that launch's ask would answer `NO_WATCH`. Set first, the hook cannot be missing; until activation completes `isReady()` is false anyway, and every send checks again. Nothing else changes.
- **`WatchBridgeCalls`** is the one seam the tests need: the bridge's public calls are one line each over it, with the app's container and the bridge's hook.
- **A key that cannot be written** is still returned, and differs at the next read: the phone sends once more and never takes a confirmation for another watch's. Returning nil instead would let two such watches share the key nil.
- **A message without a `refresh` key** is answered `[:]`, so a sender with a reply handler is never left to time out.

## The tests

`shared/src/iosTest/kotlin/world/taqwa/app/watchfeed/IosWatchLinkTest.kt`, 17 tests. The plan's 14, then three more for the bridge's own answers (`aHandshakeWithNothingIsSentTheFeed`, `aHandshakeTheSyncCannotServeIsFalse`, `aBridgeCallNeverThrows`). The tests that hold a sync run it on `Dispatchers.Default` in a scope of their own and wait with `runBlocking`, because `refreshRequested` itself blocks and a virtual-time bound would pass at once; they assert order and outcome only, and the fake hook answers at once, so no budget is reached.

| Test | What it holds |
|---|---|
| `withoutASessionThereIsNoWatch` | no hook: not reachable, no key, `NO_WATCH` |
| `aSessionNotReadyIsNoWatch` | not ready: not reachable, `NO_WATCH`, nothing handed over |
| `aReadySessionIsReachable` | reachable, and the key is the hook's |
| `theFeedTravelsAsUtf8Data` | an Arabic feed reaches the hook as exactly `json.encodeToByteArray()`, with the delivery's text beside it |
| `aRefusedContextIsFailed` | `sendFailed`, and an answer the bridge never gives, are `FAILED` |
| `aContextTooLargeIsTooLarge` | `sendTooLarge` is `TOO_LARGE`; through a sync, the second payload decodes to the same days and no phrases, under number 2, and the ask is `SENT` |
| `aThrowingSessionIsAFailure` | a throwing `send`, and a throwing `isReady` at the send, are `FAILED` |
| `theIphoneRemembersWhatItSent` | `confirmsDelivery` is false, and `createWatchLink()` is an `IosWatchLink` |
| `aHandshakeWithTheCurrentFeedIsAnsweredWithoutASend` | after one send, `refreshRequested(<that send's delivery>)` is true and `UNCHANGED`, one hook send in all |
| `aHandshakeWithNothingIsSentTheFeed` | an empty echo is true and `SENT`, a second hook send |
| `theLaunchIsDampedAndTheWatchStateChangeIsNot` | `watchActivated()` twice is `WATCH_APPEARED` `SENT` then `UNCHANGED`, one send; `watchStateChanged()` is `WATCH_CHANGED` `SENT`, a second |
| `aConfirmationCountsOnlyForTheWatchItWentTo` | under key k0 `SENT` then `UNCHANGED`; under k1 `SENT`; a handshake read under k0 and served under k1 is ignored (`UNCHANGED`, no third send) |
| `aSessionThatWentInactiveIsRefusedNotSent` | the hook's `sendFailed` is `FAILED`, the next ask sends again, and the one after is `UNCHANGED` |
| `aHandshakeTheSyncCannotServeIsFalse` | a sync whose worker is gone, and one that does not answer within the bridge's bound (200 ms here), answer false |
| `aBridgeCallNeverThrows` | a container that cannot be built and a throwing key: false, and the two asks return |
| `theContractKeysAreTheWatchcoreOnes` | the four keys are the contract's, and the codes are 0, 1 and 2 |
| `theLargestContextFitsTheSmallestLimit` | the sizes below |

**Red first.** Step 2 failed to compile (no `IosWatchLink`, `WatchFeedBridge`, `WatchBridgeCalls`, `feedData`). With stubs that only declare them (every answer `NO_WATCH`, the keys empty, the codes all 0, `feedData` empty), 13 of the 17 failed on their own assertions; the four that passed hold the no-watch, bound and no-throw answers, which a link that is never there gives too. With the implementation, 17 of 17 pass.

### The context's size (the Lead's call 4)

Feeds built by `WatchFeedBuilder` through `IosPlatformFormat` on 12 hours, with `city = WorstFeeds.CITY`, `spoken = watchSpoken()` (the simulator's own resources), frozen engine days, and the longest delivery there can be (`ffffffffffffffff/9223372036854775807/<fingerprint>`). The dictionary `[contextFeedKey: feedData(json), contextDeliveryKey: delivery.text]`:

| Feed | Phrases | Feed, UTF-8 bytes | Binary property list | `NSKeyedArchiver` (no secure coding) |
|---|---|---|---|---|
| ar_SA 12 h, control characters | 86 | 59,587 | 59,716 | 60,004 |
| ar_SA 12 h, emoji | 100 | 53,411 | 53,540 | 53,828 |
| bn_BD 12 h, control characters | 83 | 59,740 | 59,869 | 60,157 |
| bn_BD 12 h, emoji | 100 | 54,812 | 54,941 | 55,229 |

Every one is within `USER_INFO_LIMIT_BYTES` (65,536), the smallest WatchConnectivity figure, with at least 5,379 bytes to spare; the property list adds 129 bytes to the feed and the keyed archive 417. Plan 4's ship gate measures the real thresholds on the paired watch app.

## The checks

- `./gradlew :shared:iosSimulatorArm64Test`: Step 2 red as above, Step 3 green.
- `scripts/test.sh`, exit 0 on its first run: shared iOS 2,097 (2,080 + 17) and host 2,136, widgetcore 109/111, watchcore 117/119, wearApp 143, 0 failures, 0 skipped. `check-versions.sh` and `test-bump-version.sh` passed first, as always.
- `scripts/ios-build.sh`: `BUILD SUCCEEDED`, `TaqwaWidget.appex: 3 MB (limit 15 MB)`. The build's one Swift warning, at `iOSApp.swift:210`, is the existing widget-placement hook's, not this task's.
- The Task 1 grep over `shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework/Headers/shared.h` (after `linkDebugFrameworkIosSimulatorArm64`) prints nothing. Its first run caught two words in the bridge's KDoc, which the header copies (`WatchLinkContract.echoOf`, `WatchFeedSync.CALLER_BOUND`); the comment was reworded, and no type had leaked.

## Step 5: the simulator check

Under the `ios-pair` lock (`~/.cache/taqwa-locks/ios-pair.lock`, 07:03 to 07:10 SAST).

**Found:** no pair (`xcrun simctl list pairs -j` empty), the iPhone 17 Pro `2D01CE58-…` and the Series 12 `C803D94B-…` both shut down, an older Taqwa build (38) installed on the phone, its settings without a location. Its app bundle, its data container, its app-group container and the phone's `Library/WatchConnectivity` were copied aside first.

**Run:** `xcrun simctl pair C803D94B-… 2D01CE58-…` (pair `B89A6219-…`, "active, connected"), both booted, `build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app` installed, `xcrun simctl launch … world.taqwa.ios`. The log (`log show --predicate 'eventMessage CONTAINS "TaqwaWatch"'`), whole:

```
07:04:31.312 TaqwaWatch activate() asked
07:04:31.845 TaqwaWatch activated=true state=2 error=none, calling watchActivated()
07:04:31.847 TaqwaWatch isReady()=false activated=true paired=true installed=false
07:07:04.791 TaqwaWatch activate() asked
07:07:04.795 TaqwaWatch activated=true state=2 error=none, calling watchActivated()
07:07:04.826 TaqwaWatch isReady()=false activated=true paired=true installed=false
07:07:05.527 TaqwaWatch isReady()=false activated=true paired=true installed=false
07:07:05.737 TaqwaWatch isReady()=false activated=true paired=true installed=false
```

- The session activated, and the line for it names `watchActivated()`; `watchStateChanged()` appears nowhere.
- At 07:04 the app had no location: the launch's damped ask read `isReady()` once (`paired=true installed=false`) and stopped there, as `NO_WATCH` does, with nothing built.
- **With a location** (07:07): the location was set (`xcrun simctl location … set 51.5074,-0.1278`), but the onboarding's taps could not be made: the simulator tool asks Mohamed for access to the device, and no answer came. So the app was stopped and London (51.5074, -0.1278, Europe/London, source MANUAL, onboarding complete) was written into its settings file, the keys the onboarding writes, and the app launched again; the Prayer screen showed London. The launch's ask and the reschedules that followed read `isReady()` three times, each `paired=true installed=false`, and no `send` line appears: an iPhone without the watch app builds and sends nothing. The watch app is plan 4's.

**Put back:** the found build reinstalled (its bundle `diff -rq`-equal to the copy), its data and app-group containers restored from the copies (`diff -rq`-equal; installing a build gives the data container a new folder name, so the restored one sits under a new name with the same contents), the location cleared, both simulators shut down, the pair removed (`list pairs -j` empty again). The lock was then released.

## For plan 4, and what this task leaves

- The backup check (Ruling 39 item 3) is plan 4's, since only a paired watch app lets the iPhone store a context: here `isWatchAppInstalled` was false, so `updateApplicationContext` was never called, and the phone's `Library/WatchConnectivity` held only an empty `Clients` folder before and after.
- The size ship gate (`payloadTooLarge`'s real thresholds) is plan 4's too; the table above is the figure it compares against.
- `refreshRequested` blocks WatchConnectivity's delegate queue for at most 10 s while the sync serves the handshake, as the plan designs; other delegate calls wait behind it meanwhile. **Superseded by Ruling 52 item 6 (25 October):** it now returns at once and answers the reply handler from a coroutine; see `2026-10-25-link-follow-ups.md`.
- A3's known minor stands: a nil watch key counts as a watch on this link's record. The key is nil when the session is not activated (since Ruling 52 item 5 the hook does not read the directory then: Apple calls its URL undefined) or when there is no paired watch (Apple: nil), which is when `isReady()` is false and nothing is sent.
- **Since Ruling 52 item 4,** every `TaqwaWatch` line is `NSLog("%@", line)` and names a delivery by its number and fingerprint alone (`WatchFeedBridge.logged`), never its origin.
- The end-to-end iPhone check, with the watch app installed, is plan 4's.
