import SwiftUI
import WidgetKit

/// The next-prayer widget's kind, named in both targets, and what the watch app does once it has
/// stored a feed (or seen a newer format): every timeline is reloaded, and on watchOS 11 and later
/// the Smart Stack's relevance is marked stale too. watchOS asks `relevance()` only now and then,
/// so without it the first feed, or a new city's, would not lift Taqwa before the coming prayers
/// until that next ask (P4.6 review round 1; `WidgetCenter.invalidateRelevance(ofKind:)`).
enum NextPrayerWidgets {
    /// The kind Task 4's placeholder kept, so a complication placed then stays placed.
    static let kind = "TaqwaNextPrayer"

    static func reload() {
        WidgetCenter.shared.reloadAllTimelines()
        if #available(watchOS 11.0, *) {
            WidgetCenter.shared.invalidateRelevance(ofKind: kind)
        }
    }
}

/// The next-prayer widget in one family (plan 4 Task 6, `faces.js`'s `aCircular`, `aAnalog`'s
/// corner, `aRect`, `aUtility` and `aStack`). It draws in the families' own boxes; [card] is the
/// Smart Stack's (`showsWidgetContainerBackground`), where the rectangular gains a header. Every
/// family is one accessibility element, its decorative shapes hidden, and nothing drawn changes for
/// VoiceOver.
struct NextPrayerView: View {
    let family: WidgetFamily
    let content: NextPrayerContent
    var card = false

    var body: some View {
        drawn
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(content.spoken(inline: family == .accessoryInline)))
            .feedDirection(rtl: content.rtl)
    }

    @ViewBuilder private var drawn: some View {
        if let glance = content.glance {
            switch family {
            case .accessoryCircular:
                CircularGlance(glance: glance, shown: content.countdownShown)
            case .accessoryCorner:
                CornerGlance(glance: glance, shown: content.countdownShown, line: content.cornerLine ?? "")
            case .accessoryInline:
                Text(content.inlineLine)
            default:
                if card {
                    StackCard(appName: content.appName, latin: content.latin) {
                        RectangularGlance(glance: glance, shown: content.countdownShown)
                    }
                } else {
                    RectangularGlance(glance: glance, shown: content.countdownShown)
                }
            }
        } else {
            EmptyForm(family: family, appName: content.appName, notice: content.notice, latin: content.latin, fromFeed: content.script != nil)
        }
    }
}

/// `aCircular`: the ring's track and accent arc as a gauge, with the label over the time.
private struct CircularGlance: View {
    let glance: Glance
    let shown: Bool

    var body: some View {
        Gauge(value: Double(shown ? glance.progress : 0)) {
            EmptyView()
        } currentValueLabel: {
            VStack(spacing: 0) {
                Text(glance.label)
                    .font(TaqwaFont.of(.extraBold, TaqwaFont.labelSize(7.6, latin: glance.latinScript), latin: glance.latinScript))
                    .tracking(TaqwaFont.tracking(0.5, latin: glance.latinScript))
                    .foregroundStyle(TaqwaColor.amber)
                    .widgetAccentable()
                Text(glance.time)
                    .font(TaqwaFont.of(.semiBold, 10, latin: glance.latinScript))
                    .foregroundStyle(TaqwaColor.ink)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.5)
        }
        .gaugeStyle(.accessoryCircularCapacity)
        .tint(TaqwaColor.amber)
    }
}

/// `aAnalog`'s corner: a gauge holding the Taqwa arch, with the label and the time along the bezel.
/// The gauge is `appRing` drawn to the corner's own small box (`RingGauge`): the system's circular
/// gauge keeps the circular family's size, and would spill out of a corner.
private struct CornerGlance: View {
    let glance: Glance
    let shown: Bool
    let line: String

    var body: some View {
        RingGauge(progress: shown ? glance.progress : 0, rtl: glance.rtl) { side in
            ArchMark(height: side * 0.45)
                .foregroundStyle(TaqwaColor.mist)
        }
        .widgetLabel {
            Text(line)
                .foregroundStyle(TaqwaColor.amber)
        }
    }
}

/// `aRect`: the label in the accent above the countdown, the time beside it in ink, and the period
/// bar, the ring's model laid flat. Without the countdown, the label and the time alone above the
/// bar's track.
struct RectangularGlance: View {
    let glance: Glance
    let shown: Bool

    var body: some View {
        let latin = glance.latinScript
        VStack(alignment: .leading, spacing: 1) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(glance.label)
                    .font(TaqwaFont.of(.extraBold, TaqwaFont.labelSize(11.5, latin: latin), latin: latin))
                    .tracking(TaqwaFont.tracking(1.1, latin: latin))
                    .foregroundStyle(TaqwaColor.amber)
                    .widgetAccentable()
                Text(glance.time)
                    .font(TaqwaFont.of(.semiBold, 15, latin: latin))
                    .foregroundStyle(TaqwaColor.ink)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            if shown {
                Text(glance.countdown)
                    .font(TaqwaFont.of(.semiBold, 12.5, latin: latin))
                    .foregroundStyle(TaqwaColor.mist)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
            PeriodBar(progress: shown ? glance.progress : 0)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The period bar, 4 pt high: a hairline track, filled in reading order to the share of the period
/// gone (from the right in a right-to-left language). With no share, the track alone.
struct PeriodBar: View {
    let progress: Float

    var body: some View {
        GeometryReader { box in
            ZStack(alignment: .leading) {
                Capsule().fill(TaqwaColor.hairline)
                if progress > 0 {
                    Capsule()
                        .fill(TaqwaColor.amber)
                        .frame(width: max(4, box.size.width * CGFloat(min(progress, 1))))
                        .widgetAccentable()
                }
            }
        }
        .frame(height: 4)
        .accessibilityHidden(true)
    }
}

/// `aStack`: the rectangular below a header of the Taqwa arch and the app's name in mist, on the
/// card the widget's container background draws.
private struct StackCard<Content: View>: View {
    let appName: String
    let latin: Bool
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 5) {
                ArchMark(height: 11)
                    .foregroundStyle(TaqwaColor.mist)
                Text(appName)
                    .font(TaqwaFont.of(.semiBold, 10.5, latin: latin))
                    .foregroundStyle(TaqwaColor.mist)
                    .lineLimit(1)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The Taqwa arch (`faces.js`'s `archMark`) drawn [height] tall, in the box of the arch itself. The
/// asset is the launcher icon's drawing, whose arch, stroke and all, stands about half its height
/// (508 of 1,024) and 0.4 of its width, centred: so the image is drawn twice the arch's height and
/// laid out in the arch's own box, its empty margin left to overflow.
struct ArchMark: View {
    let height: CGFloat

    var body: some View {
        Image("TaqwaArch")
            .resizable()
            .scaledToFit()
            .frame(width: height * 2, height: height * 2)
            .frame(width: height * 0.81, height: height)
            .accessibilityHidden(true)
    }
}

/// The empty form: the Taqwa arch on its own (circular, corner), the app's name above the notice
/// (rectangular), or the app's name (inline). From a spent feed it is in the feed's words and type;
/// with no feed, in the extension's own, in the watch's language.
private struct EmptyForm: View {
    let family: WidgetFamily
    let appName: String
    let notice: String
    let latin: Bool
    let fromFeed: Bool

    var body: some View {
        switch family {
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Text(appName)
                    .font(fromFeed ? TaqwaFont.of(.extraBold, 15, latin: latin) : .headline)
                    .foregroundStyle(TaqwaColor.amber)
                    .widgetAccentable()
                Text(notice)
                    .font(fromFeed ? TaqwaFont.of(.semiBold, 12.5, latin: latin) : .footnote)
                    .foregroundStyle(TaqwaColor.mist)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .accessoryInline:
            Text(appName)
        default:
            Image("TaqwaArch")
                .resizable()
                .scaledToFit()
                .foregroundStyle(TaqwaColor.amber)
                .widgetAccentable()
                .padding(6)
        }
    }
}
