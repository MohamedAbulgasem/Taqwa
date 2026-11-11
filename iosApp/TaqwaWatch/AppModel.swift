import Combine
import Foundation

/// What the app's screens draw from (plan 4 Task 5): the feed the store holds, whether a newer
/// format has been seen, and the clock they are drawn for. It reads the store when it is made,
/// each time `WatchSession` stores a feed or sees a newer format, and each time the app opens,
/// when it also lets the session shake hands if the feed needs it.
@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var feed: WatchFeed?
    @Published private(set) var newerVersionSeen: Bool
    let clock: AppClock

    private let store: FeedStore
    private var changes: AnyCancellable?

    init(store: FeedStore, clock: AppClock) {
        self.store = store
        self.clock = clock
        feed = store.read()
        newerVersionSeen = store.newerVersionSeen
        changes = NotificationCenter.default.publisher(for: WatchSession.feedChanged)
            .sink { [weak self] _ in Task { @MainActor in self?.reload() } }
    }

    /// Reads the store again.
    func reload() {
        let read = store.read()
        if read != feed { feed = read }
        let newer = store.newerVersionSeen
        if newer != newerVersionSeen { newerVersionSeen = newer }
    }

    /// The app came to the screen.
    func opened() {
        reload()
        WatchSession.shared.opened()
    }

    /// The clock the app is drawn for: the DEBUG `-taqwaAt` clock when one is asked for, the
    /// watch's own otherwise.
    static func launchClock() -> AppClock {
        #if DEBUG
        if let scripted = DebugLaunch.clock() { return scripted }
        #endif
        return SystemClock()
    }
}
