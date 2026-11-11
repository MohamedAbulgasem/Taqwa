#if DEBUG
import SwiftUI
import WidgetKit

/// The DEBUG complication gallery (plan 4 Task 6 Step 5): the next-prayer widget's own views, from
/// `WatchShared/NextPrayerViews.swift`, laid out on the app's screen at the SE 3 (40mm)'s family
/// sizes, for shots with `-taqwaPage gallery -taqwaFrozen 1` and a sample and a clock. The SE 3's
/// screen holds a third of it at a time, so `-taqwaGalleryPart` picks the part, in the plan's order:
/// 1, each family; 2, the Smart Stack card; 3, each family without its countdown. It draws the
/// timeline's first entry at the scripted instant: the glance that holds for its minute, so at
/// 14:58:00 "in 1 h 11 min", the 72 minutes holding at that instant alone (P4.6 review round 1).
///
/// A face draws the corner's line along its bezel and the inline line in the system's own type;
/// here the corner's line sits under its gauge, and the inline line is in the system font at
/// `faces.js`'s size. Debug builds only.
struct ComplicationGallery: View {
    @ObservedObject var model: AppModel
    let part: Int

    /// The SE 3 (40mm)'s accessory boxes, in points.
    static let circular = CGSize(width: 42, height: 42)
    static let corner = CGSize(width: 32, height: 32)
    static let rectangular = CGSize(width: 150, height: 47)

    var body: some View {
        let timeline = Glances.timeline(model.feed, newerVersionSeen: model.newerVersionSeen, now: model.clock.now())
        let content = NextPrayerContent(timeline.entries[0], of: timeline, own: OwnWords.lines)
        VStack(spacing: 8) {
            switch part {
            case 2:
                card(content)
            case 3:
                families(content.withoutCountdown)
            default:
                families(content)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .feedDirection(rtl: content.rtl)
    }

    @ViewBuilder private func families(_ content: NextPrayerContent) -> some View {
        HStack(alignment: .top, spacing: 14) {
            NextPrayerView(family: .accessoryCircular, content: content)
                .frame(width: Self.circular.width, height: Self.circular.height)
            VStack(spacing: 3) {
                NextPrayerView(family: .accessoryCorner, content: content)
                    .frame(width: Self.corner.width, height: Self.corner.height)
                if let line = content.cornerLine {
                    Text(line)
                        .font(TaqwaFont.of(.extraBold, 7.8, latin: content.latin))
                        .tracking(TaqwaFont.tracking(0.5, latin: content.latin))
                        .foregroundStyle(TaqwaColor.amber)
                        .lineLimit(1)
                        .accessibilityHidden(true)
                }
            }
        }
        NextPrayerView(family: .accessoryInline, content: content)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(TaqwaColor.ink)
            .lineLimit(1)
            .frame(width: Self.rectangular.width)
        NextPrayerView(family: .accessoryRectangular, content: content)
            .frame(width: Self.rectangular.width, height: Self.rectangular.height)
    }

    /// The Smart Stack's card, as `aStack` draws it: 156 wide, its corners 20 round, `#1A1D1C`.
    private func card(_ content: NextPrayerContent) -> some View {
        NextPrayerView(family: .accessoryRectangular, content: content, card: true)
            .frame(width: Self.rectangular.width - 12)
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(TaqwaColor.card))
    }
}
#endif
