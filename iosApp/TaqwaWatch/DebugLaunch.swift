#if DEBUG
import Foundation

/// The DEBUG launch arguments (`DebugArguments`, plan 4 Task 5), applied once at launch, before
/// the session starts, in this order: `-taqwaNoFeed 1` empties the store, `-taqwaSampleFeed`
/// stores its sample moved to today (with `-taqwaPhrase`'s phrase after its own), and
/// `-taqwaNewerSeen 1` then leaves the newer-version notice by offering the store a version-2
/// feed, as a newer phone would. `-taqwaStartIn <minutes>` (plan 4 Task 6) stores the `capetown`
/// sample in `-taqwaSampleFeed`'s place, moved so that its next start is that many minutes away.
/// `-taqwaAt` and `-taqwaFrozen` make the app's clock (`AppModel.launchClock`). A run whose
/// arguments set the store takes in no context and shakes no hands (`DebugArguments.holdsState`,
/// plan 4 erratum p4 #8), and reloads the complications' timelines once the state is set.
///
/// A sample is stored with no delivery, and the delivery held is removed first, so it never
/// carries a phone's. Debug builds only: a Release build compiles none of this and bundles no
/// sample (`scripts/check-watch-bundle.sh`, rule 8).
enum DebugLaunch {
    static let arguments = DebugArguments(ProcessInfo.processInfo.arguments)

    /// The instant the launch is read at: the sample's day and the scripted clock's both come from it.
    static let launchedAt = SystemClock().now()

    static func apply(to store: FeedStore) {
        if arguments.noFeed {
            do {
                try store.clear()
                log("TaqwaWatch debug no feed: the store is empty")
            } catch {
                log("TaqwaWatch debug no feed failed: \(error)")
            }
        }
        if let minutes = arguments.startIn {
            if let name = arguments.sample { log("TaqwaWatch debug sample \(name) ignored: -taqwaStartIn stores capetown") }
            storeStartingIn(minutes, into: store)
        } else if let name = arguments.sample {
            storeSample(name, into: store)
        } else if arguments.phrase != nil {
            log("TaqwaWatch debug phrase ignored: no -taqwaSampleFeed")
        }
        if arguments.newerSeen {
            do {
                log("TaqwaWatch debug newer notice: \(try store.write(DebugSample.newerFeed, delivery: nil))")
            } catch {
                log("TaqwaWatch debug newer notice failed: \(error)")
            }
        }
        // The complications draw what was just set, as they would after a feed from the phone.
        if arguments.holdsState {
            NextPrayerWidgets.reload()
            log("TaqwaWatch debug timelines reloaded")
        }
    }

    /// `-taqwaFresh 1` forgets every tasbeeh count and the phrase chosen, `-taqwaTasbeehPhrase <id>`
    /// chooses that phrase, and `-taqwaTasbeehCount <n>` then puts the phrase chosen at n of round 1
    /// (plan 4 Task 8), before the counter reads them. A phrase the feed does not hold falls back to
    /// After prayer when the counter opens, as any phrase gone from the phone does.
    static func apply(toTasbeeh store: TasbeehStore) {
        if arguments.fresh {
            store.clear()
            log("TaqwaWatch debug tasbeeh fresh: no count, After prayer chosen")
        }
        if let id = arguments.tasbeehPhrase {
            store.selectedId = id
            log("TaqwaWatch debug tasbeeh phrase \(id) chosen")
        }
        if let count = arguments.tasbeehCount {
            let id = store.selectedId
            store.save(TasbeehState(presetId: id, count: count, round: 1))
            log("TaqwaWatch debug tasbeeh \(id) at \(count) of round 1")
        }
    }

    /// `-taqwaStartIn <minutes>` (plan 4 Task 6 Step 7): the `capetown` sample moved so that its
    /// next start is that many minutes from the launch, for watching the timeline with the app
    /// gone. Its clock strings stay the sample's, so the face reads "ASR 16:10" at another hour.
    private static func storeStartingIn(_ minutes: Int, into store: FeedStore) {
        guard let resource = DebugSample.resourceName("capetown"),
              let url = Bundle.main.url(forResource: resource, withExtension: "json")
        else {
            log("TaqwaWatch debug start in \(minutes) min: the capetown sample is not in this build")
            return
        }
        do {
            let text = try DebugSample.startingIn(Data(contentsOf: url), minutes: minutes, now: launchedAt, phrase: arguments.phrase)
            let result = try store.write(text, delivery: nil)
            let next = store.read().flatMap { Glances.make($0, now: launchedAt)?.nextAt }.map { String($0) } ?? "none"
            log("TaqwaWatch debug start in \(minutes) min: \(result), next start at \(next), launched at \(launchedAt)")
        } catch {
            log("TaqwaWatch debug start in \(minutes) min failed: \(error)")
        }
    }

    /// The `-taqwaAt` clock, or nil when none is asked for.
    static func clock() -> AppClock? {
        guard let at = arguments.at else { return nil }
        guard let start = DebugSample.clockStart(at, now: launchedAt) else {
            log("TaqwaWatch debug clock '\(at)' is not HH:MM: the watch's own clock")
            return nil
        }
        return ScriptedClock(start: start, frozen: arguments.frozen, launchedAt: launchedAt)
    }

    /// The `-taqwaPage` page: Task 6's complication gallery, and the pages Task 7 builds.
    static var page: DebugPage? { arguments.page }

    private static func storeSample(_ name: String, into store: FeedStore) {
        guard let resource = DebugSample.resourceName(name),
              let url = Bundle.main.url(forResource: resource, withExtension: "json")
        else {
            log("TaqwaWatch debug sample '\(name)' is not in this build: the names are \(DebugSample.names.joined(separator: ", "))")
            return
        }
        do {
            let text = try DebugSample.redated(Data(contentsOf: url), now: launchedAt, phrase: arguments.phrase)
            let result = try store.write(text, delivery: nil)
            log("TaqwaWatch debug sample \(name) \(result) \(store.read()?.days.count ?? 0) days")
        } catch {
            log("TaqwaWatch debug sample \(name) failed: \(error)")
        }
    }

    private static func log(_ line: String) {
        NSLog("%@", line)
    }
}
#endif
