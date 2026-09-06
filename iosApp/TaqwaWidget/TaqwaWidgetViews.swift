import WidgetKit
import SwiftUI
// The extension links `widgetcore.framework` alone — a slim Kotlin/Native framework with no
// Compose in it, because a WidgetKit extension runs under a ~30 MB memory ceiling. This file is
// also a member of the *app* target (for the `-taqwaWidgetPreview 1` debug route), which links
// `shared`; `shared` re-exports `:widgetcore`, so the Kotlin types below are spelled identically
// either way.
#if TAQWA_WIDGET_EXTENSION
import widgetcore
#else
import shared
#endif

/// Must match `IosKeyValueStore.APP_GROUP_ID` in `widgetcore/src/iosMain/.../KeyValueStore.ios.kt`
/// and both `.entitlements` files. A mismatch is silent: the widget reads an empty suite and
/// shows the placeholder state forever.
let taqwaAppGroupId = "group.world.taqwa.app"

/// Written by the app (never by Kotlin) each time the mirror actually changes, so the widget can
/// extrapolate the countdown forward instead of showing whatever minute the app last saw.
/// `WidgetSnapshot` carries no timestamp of its own and Task 23 owns its wire format.
let taqwaWrittenAtKey = "snapshot_written_at"
let taqwaSnapshotKey = "snapshot"
let taqwaBackgroundKey = "widget_background"

// MARK: - Reading the mirror

enum TaqwaMirror {
    /// The moment `content.countdownMinutes` reaches zero, derived from the write timestamp.
    /// Nil when the app has never written one, in which case the countdown is shown as-is.
    static func read() -> (content: WidgetContent, deadline: Date?)? {
        let store = KeyValueStore_iosKt.createWidgetKeyValueStore()
        guard let snapshot = WidgetInputsMirror.shared.read(store: store) else { return nil }
        let content = WidgetContentBuilder.shared.build(snapshot: snapshot)
        let defaults = UserDefaults(suiteName: taqwaAppGroupId)
        let writtenAt = defaults?.double(forKey: taqwaWrittenAtKey) ?? 0
        let deadline = writtenAt > 0
            ? Date(timeIntervalSince1970: writtenAt + Double(content.countdownMinutes) * 60)
            : nil
        return (content, deadline)
    }

    static func background() -> WidgetBackground {
        let store = KeyValueStore_iosKt.createWidgetKeyValueStore()
        // Task 25 mirrors the Appearance setting into this key; until then every widget renders
        // the default, exactly as the Android widget does.
        guard let raw = store.getString(key: taqwaBackgroundKey) else { return .followTheme }
        return WidgetBackground.entries.first { $0.name == raw } ?? .followTheme
    }
}

// MARK: - Timeline

struct TaqwaEntry: TimelineEntry {
    let date: Date
    let content: WidgetContent?
    /// Minutes remaining at `date`, already extrapolated forward from the mirror write.
    let countdownMinutes: Int64
    let background: WidgetBackground

    var countdownText: String {
        let m = max(0, countdownMinutes)
        return "\(m / 60):\(String(format: "%02d", m % 60))"
    }
}

struct TaqwaTimelineProvider: TimelineProvider {
    /// Spec §4.5 asks for a countdown, so an entry a minute for the next hour: WidgetKit renders
    /// entries the system already holds without waking the extension, which is the only way the
    /// number on screen is never a minute stale.
    private static let entryCount = 60

    func placeholder(in context: Context) -> TaqwaEntry {
        TaqwaEntry(date: Date(), content: nil, countdownMinutes: 0, background: .followTheme)
    }

    func getSnapshot(in context: Context, completion: @escaping (TaqwaEntry) -> Void) {
        completion(Self.entries(from: Date()).first!)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TaqwaEntry>) -> Void) {
        // The app calls WidgetCenter.shared.reloadAllTimelines() whenever the mirror changes, so
        // `.atEnd` is only the floor for a phone whose owner has not opened the app in an hour.
        completion(Timeline(entries: Self.entries(from: Date()), policy: .atEnd))
    }

    static func entries(from start: Date) -> [TaqwaEntry] {
        let background = TaqwaMirror.background()
        guard let (content, deadline) = TaqwaMirror.read() else {
            return [TaqwaEntry(date: start, content: nil, countdownMinutes: 0, background: background)]
        }
        return (0..<entryCount).map { minute in
            let date = start.addingTimeInterval(Double(minute) * 60)
            return TaqwaEntry(
                date: date,
                content: content,
                countdownMinutes: remainingMinutes(content: content, deadline: deadline, at: date),
                background: background
            )
        }
    }

    static func remainingMinutes(content: WidgetContent, deadline: Date?, at date: Date) -> Int64 {
        guard let deadline else { return content.countdownMinutes }
        let seconds = deadline.timeIntervalSince(date)
        if seconds <= 0 { return 0 }
        return Int64((seconds / 60).rounded(.up))
    }
}

// MARK: - Palette

extension Color {
    /// `WidgetPaletteColors` hands over plain ARGB longs precisely so neither platform's widget
    /// target needs a UI-framework colour type as a shared-code dependency; this is the one place
    /// iOS converts it to something SwiftUI can paint.
    init(argb: Int64) {
        let a = Double((argb >> 24) & 0xFF) / 255.0
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

/// Applies the user's background choice. `TRANSLUCENT_OR_FROSTED` is *Frosted* on iOS: WidgetKit
/// cannot sample the wallpaper for its own blur, so the honest equivalent is the system widget
/// material rather than a fake translucent fill (spec §4.5).
struct TaqwaWidgetSurface: ViewModifier {
    let colors: WidgetPaletteColors
    let frosted: Bool
    /// False for the in-app debug preview route, where `containerBackground(for: .widget)` does
    /// nothing because there is no widget container to paint.
    let inWidgetContainer: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        if inWidgetContainer, #available(iOS 17.0, *) {
            if frosted {
                content.containerBackground(.regularMaterial, for: .widget)
            } else {
                content.containerBackground(Color(argb: colors.backgroundArgb), for: .widget)
            }
        } else if frosted {
            // iOS 16 has no containerBackground; the material still reads as frosted here.
            content.background(.regularMaterial)
        } else {
            content.background(Color(argb: colors.backgroundArgb))
        }
    }
}

extension View {
    func taqwaSurface(_ colors: WidgetPaletteColors, frosted: Bool, inWidgetContainer: Bool) -> some View {
        modifier(TaqwaWidgetSurface(colors: colors, frosted: frosted, inWidgetContainer: inWidgetContainer))
    }
}

// MARK: - Home screen

struct TaqwaHomeWidgetView: View {
    @Environment(\.widgetFamily) private var environmentFamily
    @Environment(\.colorScheme) private var colorScheme
    let entry: TaqwaEntry
    /// Set only by the in-app debug preview, where there is no widget environment to read.
    var familyOverride: WidgetFamily? = nil
    var inWidgetContainer: Bool = true

    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    private var colors: WidgetPaletteColors {
        // FOLLOW_THEME resolves against the widget's own rendering environment, which is what the
        // user actually sees — not the app process's last known appearance.
        WidgetPalette.shared.colorsFor(background: entry.background, systemIsDark: colorScheme == .dark)
    }

    var body: some View {
        Group {
            if let content = entry.content {
                if family == .systemSmall {
                    small(content)
                } else {
                    medium(content)
                }
            } else {
                placeholder
            }
        }
        .taqwaSurface(colors, frosted: entry.background == .translucentOrFrosted, inWidgetContainer: inWidgetContainer)
    }

    /// Spec §4.5 small: next prayer name, countdown, clock time. One question answered.
    private func small(_ content: WidgetContent) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(content.nextPrayerDisplayName)
                .font(.system(size: 15, weight: .semibold))
                .minimumScaleFactor(0.7)
                .lineLimit(2)
                .foregroundColor(Color(argb: colors.accentArgb))
            Text(entry.countdownText)
                .font(.system(size: 34, weight: .medium, design: .rounded))
                .monospacedDigit()
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .foregroundColor(Color(argb: colors.textArgb))
            Text(content.nextClockTime)
                .font(.system(size: 15, weight: .regular))
                .foregroundColor(Color(argb: colors.textArgb).opacity(0.7))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    /// Spec §4.5 medium: countdown on the left, all five times on the right, current in accent.
    private func medium(_ content: WidgetContent) -> some View {
        HStack(alignment: .center, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(content.nextPrayerDisplayName)
                    .font(.system(size: 14, weight: .semibold))
                    .minimumScaleFactor(0.7)
                    .lineLimit(2)
                    .foregroundColor(Color(argb: colors.accentArgb))
                Text(entry.countdownText)
                    .font(.system(size: 36, weight: .medium, design: .rounded))
                    .monospacedDigit()
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                    .foregroundColor(Color(argb: colors.textArgb))
                Text(content.nextClockTime)
                    .font(.system(size: 13))
                    .foregroundColor(Color(argb: colors.textArgb).opacity(0.7))
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .leading, spacing: 2) {
                // Sunrise is absent by construction: `rows` comes from `ObligatoryPrayers`.
                ForEach(content.rows, id: \.prayer) { row in
                    HStack(spacing: 8) {
                        Text(row.displayName)
                            .font(.system(size: 12, weight: row.isCurrent ? .semibold : .regular))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Spacer(minLength: 4)
                        Text(row.clockTime)
                            .font(.system(size: 12, weight: row.isCurrent ? .semibold : .regular))
                            .monospacedDigit()
                    }
                    .foregroundColor(Color(argb: row.isCurrent ? colors.accentArgb : colors.textArgb))
                }
            }
            .frame(width: 130)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var placeholder: some View {
        VStack(spacing: 4) {
            Text("Taqwa")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(Color(argb: colors.accentArgb))
            Text("Open Taqwa to load your prayer times.")
                .font(.system(size: 12))
                .multilineTextAlignment(.center)
                .foregroundColor(Color(argb: colors.textArgb).opacity(0.7))
        }
        .padding(8)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Lock screen

struct TaqwaLockScreenWidgetView: View {
    @Environment(\.widgetFamily) private var environmentFamily
    let entry: TaqwaEntry
    var familyOverride: WidgetFamily? = nil

    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    /// The circular complication has room for about three glyphs. Arabic names have no
    /// conventional Latin abbreviation, so take the first word of the display name (which is the
    /// localised name outside Arabic locales, and the Arabic name inside them) and clip it.
    private func abbreviation(_ name: String) -> String {
        let first = name.split(separator: "·").first.map(String.init)?
            .trimmingCharacters(in: .whitespaces) ?? name
        return String(first.prefix(4))
    }

    /// The one after the next — the "then …" line. `rows` is in prayer order and excludes
    /// Sunrise, so the row following the next prayer is the answer, wrapping past Isha to Fajr.
    static func prayerAfterNext(_ content: WidgetContent) -> WidgetPrayerRow? {
        guard !content.rows.isEmpty,
              let i = content.rows.firstIndex(where: { $0.displayName == content.nextPrayerDisplayName })
        else { return content.rows.first }
        return content.rows[(i + 1) % content.rows.count]
    }

    var body: some View {
        if let content = entry.content {
            switch family {
            case .accessoryCircular:
                // Spec §4.5: ring + abbreviation + countdown — a real progress ring, the same
                // motif CountdownRing uses on Today, fed by the model's `ringProgress`.
                ZStack {
                    AccessoryWidgetBackground()
                    Gauge(value: Double(content.ringProgress).clamped01()) {
                        EmptyView()
                    } currentValueLabel: {
                        VStack(spacing: -1) {
                            Text(abbreviation(content.nextPrayerDisplayName))
                                .font(.system(size: 11, weight: .medium))
                                .minimumScaleFactor(0.6)
                                .lineLimit(1)
                            Text(entry.countdownText)
                                .font(.system(size: 12, weight: .bold))
                                .monospacedDigit()
                                .minimumScaleFactor(0.6)
                                .lineLimit(1)
                        }
                    }
                    .gaugeStyle(.accessoryCircularCapacity)
                }
            default:
                VStack(alignment: .leading, spacing: 1) {
                    HStack {
                        Text(content.nextPrayerDisplayName)
                            .font(.headline)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                        Spacer(minLength: 4)
                        Text(content.nextClockTime)
                            .font(.headline)
                            .monospacedDigit()
                    }
                    if let then = Self.prayerAfterNext(content) {
                        Text("then \(then.displayName)")
                            .font(.caption2)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                            .opacity(0.7)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }
        } else {
            Text("Taqwa")
        }
    }
}

private extension Double {
    func clamped01() -> Double { Swift.min(1, Swift.max(0, self)) }
}

// MARK: - Widget declarations

struct TaqwaHomeWidget: Widget {
    let kind = "TaqwaHomeWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TaqwaTimelineProvider()) { entry in
            TaqwaHomeWidgetView(entry: entry)
        }
        .configurationDisplayName("Taqwa")
        .description("Next prayer, countdown and today's times.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct TaqwaLockScreenWidget: Widget {
    let kind = "TaqwaLockScreenWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TaqwaTimelineProvider()) { entry in
            TaqwaLockScreenWidgetView(entry: entry)
        }
        .configurationDisplayName("Taqwa — Lock Screen")
        .description("Next prayer at a glance.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular])
    }
}
