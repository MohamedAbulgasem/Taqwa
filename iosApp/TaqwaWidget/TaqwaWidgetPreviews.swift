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

/// Every widget family at its real point size, over a neutral swatch.
///
/// This file is a member of **both** the extension and the app. In the extension it is only the
/// Xcode canvas previews below; in the app it is the `-taqwaWidgetPreview 1` debug route wired up
/// in `iOSApp.swift`, which renders these exact views against the real App Group mirror. There is
/// no `simctl` command that places a widget on a home screen, so this is how a widget render is
/// captured on a simulator without a human long-pressing the wallpaper.
struct TaqwaWidgetPreviewScreen: View {
    /// Which widgets to draw. `-taqwaWidgetPreviewSection ayah` narrows the route to the ayah
    /// card alone, so both of its families fit one screenshot with nothing to scroll — a
    /// simulator screenshot is a still of whatever is on screen and there is no way to scroll it
    /// from `simctl`.
    var section: String = "all"

    private var showsPrayer: Bool { section != "ayah" }
    private var showsAyah: Bool { section == "all" || section == "ayah" }

    /// Nominal WidgetKit sizes on a modern iPhone.
    private static let small = CGSize(width: 170, height: 170)
    private static let medium = CGSize(width: 364, height: 170)
    private static let large = CGSize(width: 364, height: 382)
    private static let circular = CGSize(width: 76, height: 76)
    private static let rectangular = CGSize(width: 172, height: 76)

    private var entry: TaqwaEntry { TaqwaTimelineProvider.entries(from: Date()).first! }
    private var ayahEntry: AyahEntry { AyahTimelineProvider.entries(from: Date()).first! }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("Taqwa widget preview")
                    .font(.headline)
                Text(entry.content == nil
                     ? "No mirror data. Open Taqwa normally first."
                     : "Live App Group mirror data")
                    .font(.caption)
                    .foregroundColor(.secondary)

                if showsAyah {
                    labelled("ayah systemMedium") {
                        // No `.padding(14)` here, unlike the prayer widgets above: the ayah card
                        // applies spec §2's padding itself, inside its own background, because a
                        // padded-then-backgrounded card leaves its fill short of the cell edges.
                        TaqwaAyahWidgetView(entry: ayahEntry, familyOverride: .systemMedium, inWidgetContainer: false)
                            .frame(width: Self.medium.width, height: Self.medium.height)
                            .clipShape(RoundedRectangle(cornerRadius: 19, style: .continuous))
                    }

                    labelled("ayah systemLarge") {
                        TaqwaAyahWidgetView(entry: ayahEntry, familyOverride: .systemLarge, inWidgetContainer: false)
                            .frame(width: Self.large.width, height: Self.large.height)
                            .clipShape(RoundedRectangle(cornerRadius: 19, style: .continuous))
                    }
                }

                if showsPrayer {
                    labelled("systemSmall") {
                        TaqwaHomeWidgetView(entry: entry, familyOverride: .systemSmall, inWidgetContainer: false)
                            .padding(14)
                            .frame(width: Self.small.width, height: Self.small.height)
                            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    }

                    labelled("systemMedium") {
                        TaqwaHomeWidgetView(entry: entry, familyOverride: .systemMedium, inWidgetContainer: false)
                            .padding(14)
                            .frame(width: Self.medium.width, height: Self.medium.height)
                            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    }

                    labelled("accessoryCircular") {
                        TaqwaLockScreenWidgetView(entry: entry, familyOverride: .accessoryCircular)
                            .frame(width: Self.circular.width, height: Self.circular.height)
                            .environment(\.colorScheme, .dark)
                    }

                    labelled("accessoryRectangular") {
                        TaqwaLockScreenWidgetView(entry: entry, familyOverride: .accessoryRectangular)
                            .frame(width: Self.rectangular.width, height: Self.rectangular.height)
                            .environment(\.colorScheme, .dark)
                            .foregroundColor(.white)
                    }
                }
            }
            .padding(.vertical, 28)
            .frame(maxWidth: .infinity)
        }
        .background(Color(white: 0.35))
    }

    @ViewBuilder
    private func labelled<Content: View>(_ title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 6) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundColor(.white.opacity(0.8))
            content()
        }
    }
}

// MARK: - Xcode canvas previews

struct TaqwaWidget_Previews: PreviewProvider {
    private static let sample = TaqwaEntry(
        date: Date(),
        content: WidgetContent(
            nextPrayerDisplayName: "Dhuhr · الظهر",
            countdownMinutes: 209,
            nextClockTime: "12:46",
            rows: [
                WidgetPrayerRow(prayer: Prayer.fajr, displayName: "Fajr · الفجر", clockTime: "05:12", isCurrent: false),
                WidgetPrayerRow(prayer: Prayer.dhuhr, displayName: "Dhuhr · الظهر", clockTime: "12:46", isCurrent: false),
                WidgetPrayerRow(prayer: Prayer.asr, displayName: "Asr · العصر", clockTime: "16:02", isCurrent: false),
                WidgetPrayerRow(prayer: Prayer.maghrib, displayName: "Maghrib · المغرب", clockTime: "18:31", isCurrent: true),
                WidgetPrayerRow(prayer: Prayer.isha, displayName: "Isha · العشاء", clockTime: "19:55", isCurrent: false),
            ],
            ringProgress: 0.42,
            countdownLabel: "Dhuhr in"
        ),
        countdownMinutes: 209,
        background: WidgetBackground.followTheme,
        languageTag: "en"
    )

    static var previews: some View {
        Group {
            TaqwaHomeWidgetView(entry: sample)
                .previewContext(WidgetPreviewContext(family: .systemSmall))
            TaqwaHomeWidgetView(entry: sample)
                .previewContext(WidgetPreviewContext(family: .systemMedium))
            TaqwaLockScreenWidgetView(entry: sample)
                .previewContext(WidgetPreviewContext(family: .accessoryCircular))
            TaqwaLockScreenWidgetView(entry: sample)
                .previewContext(WidgetPreviewContext(family: .accessoryRectangular))
            TaqwaWidgetPreviewScreen()
                .previewDisplayName("In-app debug route")
            TaqwaWidgetPreviewScreen(section: "ayah")
                .previewDisplayName("In-app debug route (ayah)")
        }
    }
}
