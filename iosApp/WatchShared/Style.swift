import SwiftUI

/// The watch's colours (plan 4's Global Constraints): the phone's own, and amber the one accent
/// (plan 4's question 4). Accent shapes are `.widgetAccentable()`, so a tinted face reads them in
/// its one colour.
enum TaqwaColor {
    static let black = Color(rgb: 0x000000)
    static let ink = Color(rgb: 0xF1F3F1)
    static let mist = Color(rgb: 0x8C948F)
    static let slate = Color(rgb: 0x5A625D)
    static let hairline = Color(rgb: 0x232825)
    static let surface = Color(rgb: 0x131614)
    /// The Smart Stack's card.
    static let card = Color(rgb: 0x1A1D1C)
    static let amber = Color(rgb: 0xF0B429)
}

private extension Color {
    init(rgb: UInt32) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}

/// The phone's type rule (`TaqwaTypography`): Manrope Light, SemiBold and ExtraBold under a
/// Latin-script interface (`ui.latinScript`), bundled from `shared/src/commonMain/composeResources/font/`
/// and never copied; otherwise (ar, ur, bn) the system font, with no tracking. The sizes are
/// `faces.js`'s, drawn in its 172-wide space, as points.
enum TaqwaFont {
    enum Weight {
        case light, semiBold, extraBold

        var manrope: String {
            switch self {
            case .light: return "Manrope-Light"
            case .semiBold: return "Manrope-SemiBold"
            case .extraBold: return "Manrope-ExtraBold"
            }
        }

        var system: Font.Weight {
            switch self {
            case .light: return .light
            case .semiBold: return .semibold
            case .extraBold: return .heavy
            }
        }
    }

    /// The face for `latin` (`ui.latinScript`) at `size` points, a fixed size: a complication's box
    /// is the system's, and its text never grows out of it.
    static func of(_ weight: Weight, _ size: CGFloat, latin: Bool) -> Font {
        latin ? .custom(weight.manrope, fixedSize: size) : .system(size: size, weight: weight.system)
    }

    /// Letter spacing, under a Latin-script interface only.
    static func tracking(_ value: CGFloat, latin: Bool) -> CGFloat {
        latin ? value : 0
    }

    /// The prayer's label is drawn larger outside Latin letters, by the ratio `faces.js` gives the
    /// Galaxy's label (13 to 11.5), so a name in Arabic letters is as legible as one in capitals.
    static func labelSize(_ size: CGFloat, latin: Bool) -> CGFloat {
        latin ? size : size * 13 / 11.5
    }
}

/// The feed's direction: every view root and widget view sets it (plan 4's Global Constraints).
func direction(_ feed: WatchFeed) -> LayoutDirection {
    direction(rtl: feed.ui.rtl)
}

func direction(rtl: Bool) -> LayoutDirection {
    rtl ? .rightToLeft : .leftToRight
}

extension View {
    /// The feed's direction when there is a feed (`rtl` not nil); the watch's own otherwise, for
    /// the watch's own words.
    @ViewBuilder func feedDirection(rtl: Bool?) -> some View {
        if let rtl {
            environment(\.layoutDirection, direction(rtl: rtl))
        } else {
            self
        }
    }
}
