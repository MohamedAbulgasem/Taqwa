import SwiftUI
import WidgetKit

/// Taqwa's next prayer on Apple's faces and in the Smart Stack (plan 4 Task 6, spec 4.3): the four
/// accessory families, drawn from the feed the watch app stored in the app group. Its gallery name
/// and description come from the extension's own table, in the watch's language (spec 1.3).
struct TaqwaNextPrayer: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: NextPrayerWidgets.kind, provider: NextPrayerProvider()) { entry in
            NextPrayerEntryView(entry: entry)
        }
        .configurationDisplayName("next_prayer_widget_name")
        .description("next_prayer_widget_description")
        .supportedFamilies([.accessoryCircular, .accessoryCorner, .accessoryRectangular, .accessoryInline])
    }
}

/// One entry: what holds from [date] until the next, and how much the Smart Stack should lift it.
struct NextPrayerEntry: TimelineEntry {
    let date: Date
    let content: NextPrayerContent
    let relevance: TimelineEntryRelevance?
}

/// The timeline of the Lead's call 5 (`Glances.timeline`): an entry for each minute of the coming
/// hour, the hour's end without the countdown, then one at each of the next ten starts (erratum
/// p4 #10), reloaded at the next start or the hour's end, whichever comes first. With no feed, or a
/// spent one, the empty form and no reload: the app reloads the timelines when a feed comes.
struct NextPrayerProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextPrayerEntry {
        Self.entries(Glances.timeline(nil, newerVersionSeen: false, now: Self.now()))[0]
    }

    func getSnapshot(in context: Context, completion: @escaping (NextPrayerEntry) -> Void) {
        completion(Self.entries(Self.timeline())[0])
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextPrayerEntry>) -> Void) {
        let started = DispatchTime.now().uptimeNanoseconds
        let timeline = Self.timeline()
        let entries = Self.entries(timeline)
        let policy: TimelineReloadPolicy = timeline.reloadAt.map { .after(Self.date($0)) } ?? .never
        let ms = (DispatchTime.now().uptimeNanoseconds - started) / 1_000_000
        let size = context.displaySize
        // The plan's line, then the family and its box, which say which complication asked.
        NSLog("%@", "TaqwaWatch timeline \(entries.count) \(ms) reloadAt \(timeline.reloadAt.map { String($0) } ?? "never") \(context.family) \(Int(size.width))x\(Int(size.height))")
        completion(Timeline(entries: entries, policy: policy))
    }

    /// The Smart Stack's windows, watchOS 11 and later: the half hour before each of the next ten
    /// starts (plan 4's question 2). The app marks them stale whenever it stores a feed
    /// (`NextPrayerWidgets.reload`), so watchOS asks again then and not only now and then.
    @available(watchOS 11.0, *)
    func relevance() async -> WidgetRelevance<Void> {
        guard let feed = FeedStore.appGroup().read() else { return WidgetRelevance([]) }
        return WidgetRelevance(Glances.relevanceWindows(feed, now: Self.now()).map { window in
            WidgetRelevanceAttribute(context: .date(from: Self.date(window.lowerBound), to: Self.date(window.upperBound)))
        })
    }

    /// The stored feed's timeline from now. The store is read once, and the feed's words once
    /// (erratum p4 #12).
    private static func timeline() -> GlanceTimeline {
        let store = FeedStore.appGroup()
        return Glances.timeline(store.read(), newerVersionSeen: store.newerVersionSeen, now: now())
    }

    private static func entries(_ timeline: GlanceTimeline) -> [NextPrayerEntry] {
        let own = OwnWords.lines // the watch's own words, read once for the whole timeline
        return timeline.entries.map { entry in
            let relevance = entry.glance.map { glance in
                TimelineEntryRelevance(score: entry.relevant ? 1 : 0, duration: TimeInterval(glance.nextAt - entry.at))
            }
            return NextPrayerEntry(date: date(entry.at), content: NextPrayerContent(entry, of: timeline, own: own), relevance: relevance)
        }
    }

    /// The watch's own clock, as epoch seconds: only the instant is read, never a time to show.
    private static func now() -> Int64 {
        Int64(Date().timeIntervalSince1970.rounded(.down))
    }

    private static func date(_ epoch: Int64) -> Date {
        Date(timeIntervalSince1970: TimeInterval(epoch))
    }
}

/// The entry in the family WidgetKit asks for, on the Smart Stack's card where it shows one.
struct NextPrayerEntryView: View {
    let entry: NextPrayerEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.showsWidgetContainerBackground) private var card

    var body: some View {
        NextPrayerView(family: family, content: entry.content, card: card)
            .containerBackground(for: .widget) { TaqwaColor.card }
    }
}
