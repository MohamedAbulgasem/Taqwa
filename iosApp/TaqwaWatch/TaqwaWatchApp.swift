import SwiftUI

/// The Taqwa watch app (plan 4). It starts the link at every launch, a background one included
/// (`WatchSession`, Task 5), and holds a WatchConnectivity background task open until the feed it
/// brought is stored and the timelines are reloaded, or until watchOS cancels the task for running
/// out of time, when `drain()` returns at once. It draws its three pages from the feed it holds
/// (`Pages`, Task 7).
@main
struct TaqwaWatchApp: App {
    @StateObject private var model: AppModel
    @StateObject private var tasbeeh: TasbeehSession
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let store = FeedStore.appGroup()
        let counts = TasbeehStore.app()
        #if DEBUG
        DebugLaunch.apply(to: store)
        DebugLaunch.apply(toTasbeeh: counts)
        #endif
        WatchSession.shared.start()
        _model = StateObject(wrappedValue: AppModel(store: store, clock: AppModel.launchClock()))
        _tasbeeh = StateObject(wrappedValue: TasbeehSession(store: counts, feed: store.read()))
    }

    var body: some Scene {
        WindowGroup {
            RootView(model: model, tasbeeh: tasbeeh)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.opened() }
            // A wrist lowered or the app left mid-burst loses nothing: the phone's flush on leave.
            if phase != .active { tasbeeh.settle() }
        }
        .backgroundTask(.watchConnectivity) {
            await WatchSession.shared.drain()
        }
    }
}

/// The app's root: its three pages, or a DEBUG `-taqwaPage gallery`'s complication gallery (Task 6).
struct RootView: View {
    @ObservedObject var model: AppModel
    let tasbeeh: TasbeehSession

    var body: some View {
        #if DEBUG
        if DebugLaunch.page == .gallery {
            ComplicationGallery(model: model, part: DebugLaunch.arguments.galleryPart)
        } else {
            Pages(model: model, tasbeeh: tasbeeh, start: DebugLaunch.page.flatMap(Page.init(debug:)) ?? .today)
        }
        #else
        Pages(model: model, tasbeeh: tasbeeh, start: .today)
        #endif
    }
}

/// The app's three pages, in the order of spec 2.5 and 4.4.
enum Page: String, CaseIterable {
    case today, tasbeeh, qibla

    #if DEBUG
    /// The page a DEBUG `-taqwaPage` opens on; nil for the gallery, which is no page.
    init?(debug: DebugPage) {
        self.init(rawValue: debug.rawValue)
    }
    #endif
}

/// Today, Tasbeeh and Qibla (plan 4 Task 7), a swipe apart (`TabView`'s page style), so the crown
/// is free to count on Tasbeeh. The pager runs left to right in every language, as the Galaxy's
/// does (plan 3's call 4); each page's content takes the feed's direction, or with no feed the
/// watch's own.
///
/// - With no feed, every page shows the watch's own line, in plan 3's call 5 order: "Update Taqwa
///   on your watch" once a newer format has been seen, else "Open Taqwa on your phone".
/// - With a feed, Today draws it (`TodayPage`: a spent feed says so in the feed's words, and a
///   newer format seen puts the update in the time's place); Tasbeeh (`TasbeehPage`, Task 8) and
///   Qibla (`QiblaPage`, Task 9) keep working from the feed's words and place, which hold no time.
///   The Qibla page's compass runs only while it is the page shown and the app is active.
/// - The tasbeeh counter lives above the pager (`TasbeehSession`): it takes the feed's phrases each
///   time the feed changes, and is settled when its page is swiped away.
///
/// Each page reads the feed's words once for each of its drawings (plan 4 erratum p4 #12).
struct Pages: View {
    @ObservedObject var model: AppModel
    let tasbeeh: TasbeehSession
    @State private var selection: Page
    @Environment(\.layoutDirection) private var watchDirection

    init(model: AppModel, tasbeeh: TasbeehSession, start: Page) {
        self.model = model
        self.tasbeeh = tasbeeh
        _selection = State(initialValue: start)
    }

    var body: some View {
        let feed = model.feed
        let contentDirection = feed.map(direction) ?? watchDirection
        TabView(selection: $selection) {
            ForEach(Page.allCases, id: \.self) { page in
                content(page, feed: feed)
                    .environment(\.layoutDirection, contentDirection)
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("page.\(page.rawValue)")
                    .tag(page)
            }
        }
        .tabViewStyle(.page)
        .environment(\.layoutDirection, .leftToRight)
        .onChange(of: model.feed) { _, feed in tasbeeh.use(feed) }
        .onChange(of: selection) { old, _ in
            if old == .tasbeeh { tasbeeh.settle() }
        }
    }

    @ViewBuilder private func content(_ page: Page, feed: WatchFeed?) -> some View {
        if let feed {
            switch page {
            case .today:
                TodayPage(feed: feed, newerVersionSeen: model.newerVersionSeen, clock: model.clock)
            case .tasbeeh:
                TasbeehPage(feed: feed, session: tasbeeh)
            case .qibla:
                QiblaPage(feed: feed, visible: selection == .qibla)
            }
        } else {
            MessageView(own: model.newerVersionSeen ? .updateOnWatch : .openOnPhone)
        }
    }
}
