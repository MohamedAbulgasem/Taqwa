import WidgetKit
import SwiftUI
import CoreText
import UIKit
// Same two-module arrangement as `TaqwaWidgetViews.swift`: this file is compiled into the
// extension (which links the Compose-free `widgetcore.framework`) *and* into the app, for the
// `-taqwaWidgetPreview 1` debug route. `shared` re-exports `:widgetcore`, so every Kotlin type
// below is spelled identically either way.
#if TAQWA_WIDGET_EXTENSION
import widgetcore
#else
import shared
#endif

// MARK: - The Hafs face

/// The Uthmani Hafs face, by PostScript name, with a `CTFontManager` fallback.
///
/// `UIAppFonts` in `TaqwaWidget/Info.plist` registers the face for the *extension*, which is the
/// shipping path. It cannot cover the app: this file is also compiled into the app for the
/// `-taqwaWidgetPreview 1` route, and the app carries the same TTF only inside Compose's own
/// resource bundle (`compose-resources/…/font/uthmanic_hafs.ttf`), which `UIAppFonts` cannot name.
/// So the lookup is: ask for the face; if it is not there, find the TTF wherever this bundle keeps
/// it and register it with the process. Both callers then get real Hafs rather than a silent
/// fallback to the system Arabic face, whose ayah digits are plain numerals instead of the
/// Mushaf roundel.
enum TaqwaHafs {
    /// From the TTF's `name` table — verified with `CGFont(...).postScriptName`, not guessed. The
    /// truncated "Regula" is the font's own spelling.
    static let postScriptName = "KFGQPCHAFSUthmanicScript-Regula"

    private static let ready: Bool = {
        if UIFont(name: postScriptName, size: 12) != nil { return true }
        for url in candidateURLs() {
            // `.process` (not `.persistent`): the registration lives as long as this process and
            // leaves nothing behind on the device.
            if CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) {
                return UIFont(name: postScriptName, size: 12) != nil
            }
        }
        return false
    }()

    /// Where the TTF sits in each of the two bundles this file can find itself in.
    private static func candidateURLs() -> [URL] {
        let bundle = Bundle.main
        return [
            // The extension: copied in by `tools/add-widget-font.rb`.
            bundle.url(forResource: "uthmanic_hafs", withExtension: "ttf"),
            // The app: the Compose resources plugin's own copy, one per framework it aggregates.
            bundle.url(
                forResource: "uthmanic_hafs",
                withExtension: "ttf",
                subdirectory: "compose-resources/composeResources/world.taqwa.app.resources/font"
            ),
        ].compactMap { $0 }
    }

    /// The Hafs face at `size`, or the system face when it could not be loaded at all. A missing
    /// font must never take the widget down — the wrong face still shows today's ayah.
    static func font(size: CGFloat) -> Font {
        ready ? .custom(postScriptName, size: size) : .system(size: size)
    }
}

// MARK: - Arabic-Indic digits

/// `QuranText.arabicIndic` lives in `:shared`, which the extension deliberately does not link, so
/// the one rule it needs — the ayah roundel is a bare Arabic-Indic digit run, drawn as a roundel by
/// the Hafs face itself (spec §5.2) — is repeated here in five lines rather than dragging Compose
/// into a 30 MB extension.
private func arabicIndicDigits(_ n: Int32) -> String {
    let digits = Array("٠١٢٣٤٥٦٧٨٩")
    return String(String(n).map { c in
        // `wholeNumberValue` rather than `asciiValue! - 48`: it never force-unwraps, and it still
        // maps only the plain ASCII digits `String(Int32)` can ever produce (a minus sign or any
        // other character passes through unchanged).
        guard let value = c.wholeNumberValue, digits.indices.contains(value) else { return c }
        return digits[value]
    })
}

/// U+00A0. `QuranText.MARKER_SEPARATOR`'s value: a non-breaking space, so wrapping never splits an
/// ayah from its number.
private let markerSeparator = "\u{00A0}"

// MARK: - Timeline

struct AyahEntry: TimelineEntry {
    let date: Date
    /// Today's ayah, or nil before the app has ever written the pool mirror — the placeholder card.
    let entry: AyahPoolEntry?
    let showTranslation: Bool
    let translationRtl: Bool
    /// The *UI's* direction, from the mirror's own `languageTag` (spec §2's "Arabic UI" rule).
    /// Independent of `translationRtl`: an Arabic UI reading an English translation still sets
    /// that paragraph left to right.
    let arabicUi: Bool
    /// The mirror's tag, not the device's — it is the tag the surah names in it were read under,
    /// so the footer's script follows the script those names are already in.
    let languageTag: String
    /// `AyahPoolMirror.arabicIndicDigits`: the digit set the *app* itself draws, recorded when it
    /// wrote the mirror. Not derived from `languageTag`, because CLDR's default for a tag and the
    /// device's own ICU data are allowed to disagree — under an `ar-LY` per-app locale the S23
    /// draws Arabic-Indic digits everywhere in the app while the tag rule says Western, and the
    /// widget footer contradicted the app (D2, S23 round).
    let arabicIndicDigits: Bool
    let background: WidgetBackground
}

struct AyahTimelineProvider: TimelineProvider {
    /// Today plus the next six local midnights (spec §7). Seven entries is a week of correct
    /// cards without a single extension wake-up; `.atEnd` picks it up from there, and any change
    /// to the pool (translation, language) ends in `reloadAllTimelines()` anyway.
    private static let futureDays = 6

    func placeholder(in context: Context) -> AyahEntry {
        AyahEntry(
            date: Date(), entry: nil, showTranslation: false, translationRtl: false,
            arabicUi: false, languageTag: "", arabicIndicDigits: false, background: .followTheme
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (AyahEntry) -> Void) {
        let entry = Self.entries(from: Date()).first!
        // The gallery asks for a snapshot before the app has necessarily run once, and a gallery
        // card reading "open Taqwa to load today's ayah" sells nothing. A real ayah goes there
        // instead; a home-screen render never takes this branch, because it is not a preview.
        if context.isPreview && entry.entry == nil {
            completion(Self.sampleEntry(background: entry.background))
        } else {
            completion(entry)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AyahEntry>) -> Void) {
        completion(Timeline(entries: Self.entries(from: Date()), policy: .atEnd))
    }

    /// One entry for `start` and one for each of the next six local midnights, each carrying the
    /// ayah `AyahRotation` picks for *that entry's own calendar date* (spec §5).
    static func entries(from start: Date) -> [AyahEntry] {
        let store = KeyValueStore_iosKt.createWidgetKeyValueStore()
        let mirror = AyahPoolMirror.companion.read(store: store)
        // A mirror written before its seed (an interrupted first write) rotates from 0 rather than
        // failing; the next successful write puts the install's real seed in place.
        let seed = AyahPoolMirror.companion.seed(store: store)?.int64Value ?? 0
        let background = TaqwaMirror.background()
        let tag = mirror?.languageTag ?? ""

        return dates(from: start).map { date in
            AyahEntry(
                date: date,
                entry: mirror?.entryFor(epochDay: epochDay(of: date), seed: seed),
                showTranslation: mirror?.showsTranslation ?? false,
                translationRtl: mirror?.translationRtl ?? false,
                // Arabic and Urdu: the surah name stands alone in the footer, as in the app.
                arabicUi: UiLanguage.companion.of(tag: tag).arabicScript,
                languageTag: tag,
                arabicIndicDigits: mirror?.arabicIndicDigits ?? false,
                background: background
            )
        }
    }

    /// The device's time zone on a *Gregorian* calendar — not `Calendar.current`, which is the
    /// user's chosen calendar and returns Hijri year/month/day components when Settings ▸ General ▸
    /// Language & Region ▸ Calendar is Islamic. `AyahRotation.epochDay` is proleptic Gregorian
    /// (spec §5), so feeding it 1447/3/17 instead of 2026/9/9 would rotate to a different ayah than
    /// Android shows and re-rotate the moment the user changed calendar. The time zone still comes
    /// from `Calendar.current`, because "today" is genuinely the device's local date; only the
    /// calendar *system* is pinned. `dates(from:)` uses the same one so the entry boundaries and
    /// the epoch days computed from them cannot disagree.
    private static var rotationCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Calendar.current.timeZone
        return cal
    }

    /// `start`, then each following local midnight. Built by adding a day to a *start of day* and
    /// re-normalising, so a DST transition still lands on the day boundary the phone shows.
    private static func dates(from start: Date) -> [Date] {
        let calendar = rotationCalendar
        var dates = [start]
        var day = calendar.startOfDay(for: start)
        for _ in 0..<futureDays {
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = calendar.startOfDay(for: next)
            dates.append(day)
        }
        return dates
    }

    /// The device's *local* calendar date for `date`, as the days-since-epoch number `AyahRotation`
    /// rotates on — never `timeIntervalSince1970 / 86400`, which is a UTC day and would turn the
    /// ayah over at the wrong hour everywhere but Greenwich.
    private static func epochDay(of date: Date) -> Int64 {
        let c = rotationCalendar.dateComponents([.year, .month, .day], from: date)
        return AyahRotation.shared.epochDay(
            year: Int32(c.year ?? 1970), month: Int32(c.month ?? 1), day: Int32(c.day ?? 1)
        )
    }

    /// Ar-Ra'd 13:28 — for the widget gallery alone, in the gallery's own language.
    static func sampleEntry(background: WidgetBackground) -> AyahEntry {
        let tag = Locale.current.language.languageCode?.identifier ?? "en"
        // The sample translation is the Arabic tafsir only for Arabic itself; the footer's
        // "name alone" rule covers every Arabic-script interface (Arabic, Urdu).
        let arabic = tag.hasPrefix("ar")
        let arabicScript = UiLanguage.companion.of(tag: tag).arabicScript
        let entry = AyahPoolEntry(
            surah: 13,
            ayah: 28,
            surahLatin: "Ar-Ra'd",
            surahArabic: "الرعد",
            arabic: "ٱلَّذِينَ ءَامَنُواْ وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ ٱللَّهِ ۗ أَلَا بِذِكْرِ ٱللَّهِ تَطْمَئِنُّ ٱلْقُلُوبُ",
            // The bundled default for each UI language (`ReadingSettings.defaultsFor`): Saheeh
            // International in English, al-Muyassar in Arabic.
            translation: arabic
                ? "ويهدي الذين تسكن قلوبهم بتوحيد الله وذكره فتطمئن، ألا بطاعة الله وذكره وثوابه تسكن القلوب وتستأنس."
                : "Those who have believed and whose hearts are assured by the remembrance of Allah. Unquestionably, by the remembrance of Allah hearts are assured."
        )
        return AyahEntry(
            date: Date(), entry: entry, showTranslation: true, translationRtl: arabic,
            arabicUi: arabicScript, languageTag: tag,
            // The gallery sample has no mirror to read, so this is the one place the tag rule
            // still answers the question (see `WidgetDigits.defaultsToArabicIndic`).
            arabicIndicDigits: WidgetDigits.shared.defaultsToArabicIndic(languageTag: tag),
            background: background
        )
    }
}

// MARK: - The card

/// The reader's ayah card at widget scale (spec §2): the Uthmani Arabic with its roundel, the
/// translation beneath it, and a footer naming the surah.
///
/// The Android widget draws the same card by auto-fitting the Arabic from 28 sp down to 17 sp
/// (spec §2) because it renders to a bitmap and must measure everything itself. SwiftUI does the
/// same job with `minimumScaleFactor`, so the sizes here are the per-family starting points and
/// SwiftUI shrinks from there — the range works out the same and the layout stays declarative.
struct TaqwaAyahWidgetView: View {
    @Environment(\.widgetFamily) private var environmentFamily
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale
    let entry: AyahEntry
    /// Set only by the in-app debug preview, where there is no widget environment to read.
    var familyOverride: WidgetFamily? = nil
    var inWidgetContainer: Bool = true

    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    /// Spec §2's "compact footer under 170 dp drawn height": Medium is 158 pt tall, Large is not.
    private var compact: Bool { family != .systemLarge }

    private var colors: WidgetPaletteColors {
        WidgetPalette.shared.colorsFor(background: entry.background, systemIsDark: colorScheme == .dark)
    }

    private var textColor: Color { Color(argb: colors.textArgb) }
    private var accentColor: Color { Color(argb: colors.accentArgb) }

    /// True when this card actually draws a translation — the mirror can say translations are on
    /// while this particular entry has none.
    private var showsTranslation: Bool {
        entry.showTranslation && !(entry.entry?.translation.isEmpty ?? true)
    }

    // Spec §2 metrics.
    /// Large with the translation off goes to 32 pt, the same move Android's renderer makes when
    /// it lifts its auto-fit ceiling for a translation-less card (N1/N2, S23 round). Android's
    /// other half of that rule — a ceiling that grows with the drawn height, up to 40 sp — has no
    /// counterpart here: a WidgetKit family is a fixed cell, so Large *is* the tall case, and
    /// there is no 4x6 for the size to grow into.
    private var arabicSize: CGFloat {
        if compact { return 19 }
        return showsTranslation ? 26 : 32
    }
    private var translationSize: CGFloat { compact ? 13 : 14 }
    private var translationLineLimit: Int { compact ? 3 : 8 }
    /// The Arabic name's size: 16 pt normally, 15 pt under an Arabic UI, 14 pt in a compact footer.
    private var footerArabicSize: CGFloat { compact ? 14 : (entry.arabicUi ? 15 : 16) }

    private static let secondaryAlpha = 0.62
    private static let tertiaryAlpha = 0.42
    private static let ruleAlpha = 0.12

    var body: some View {
        Group {
            if let ayah = entry.entry {
                card(ayah)
            } else {
                placeholder
            }
        }
        // Spec §2's 14 pt, but only off a home screen and only from iOS 17: inside a widget
        // container, WidgetKit applies its own content margins through `containerBackground`, and
        // adding these on top of them would inset a Medium card twice — but that API (and the
        // margins it brings) is iOS 17+ only. On iOS 16 `taqwaSurface` falls back to a plain
        // `.background`, which insets nothing, so the card still needs its own 14 pt there.
        // `TaqwaWidgetSurface.cardPadding` is the single place that gate lives.
        // It has to sit *inside* `taqwaSurface` either way — padding applied outside the background
        // leaves the card's fill 14 pt short of the cell on every edge.
        .padding(TaqwaWidgetSurface.cardPadding(inWidgetContainer: inWidgetContainer))
        .taqwaSurface(colors, frosted: entry.background == .translucentOrFrosted, inWidgetContainer: inWidgetContainer)
    }

    /// The footer is pinned to the bottom and the Arabic + translation are centred in what is left
    /// above it — the same arrangement `AyahCardRenderer.draw` settled on for Android, so the two
    /// platforms read alike. Top-aligning instead collects every unused point into one dead band
    /// between the translation and the rule.
    private func card(_ ayah: AyahPoolEntry) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 8) {
                arabicText(ayah)
                if showsTranslation {
                    translationText(ayah)
                }
            }
            Spacer(minLength: 0)
            footer(ayah)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The ayah, its non-breaking separator and its number in Arabic-Indic digits, which the Hafs
    /// face draws as the Mushaf roundel. The digit run alone takes the accent, exactly as the
    /// reader's own `AyahCard` styles it — built by concatenating two runs rather than searching
    /// for a range, because the same digits can occur inside the ayah text.
    private func arabicText(_ ayah: AyahPoolEntry) -> some View {
        var text = AttributedString(ayah.arabic + markerSeparator)
        var marker = AttributedString(arabicIndicDigits(ayah.ayah))
        marker.foregroundColor = accentColor
        text.append(marker)
        return Text(text)
            .font(TaqwaHafs.font(size: arabicSize))
            .foregroundColor(textColor)
            .multilineTextAlignment(.leading)
            // Spec's 1.75x line height; the Hafs face's own metrics give about 1.68x, so this adds
            // the small remaining balance rather than replacing it (SwiftUI has no absolute
            // line-height knob) — the same approach `translationText` takes below.
            .lineSpacing(arabicSize * 0.1)
            // The Arabic is never cut (spec §2), so no `lineLimit`: it shrinks instead.
            .minimumScaleFactor(0.6)
            .frame(maxWidth: .infinity, alignment: .leading)
            // Right-to-left *and* leading-aligned is what sets the ayah hard against the right
            // edge and wraps it the right way; `.trailing` under an LTR environment would only
            // right-align an otherwise left-to-right paragraph.
            .environment(\.layoutDirection, .rightToLeft)
    }

    /// The translation reads in its own language's direction, whatever the UI's is (spec §2).
    private func translationText(_ ayah: AyahPoolEntry) -> some View {
        Text(ayah.translation)
            .font(.system(size: translationSize))
            .foregroundColor(textColor.opacity(Self.secondaryAlpha))
            // Spec §2's 1.45x line height; the system face's own is about 1.2x, so the balance is
            // added here rather than replacing it (SwiftUI has no absolute line-height knob).
            .lineSpacing(translationSize * 0.25)
            .multilineTextAlignment(.leading)
            .lineLimit(translationLineLimit)
            .minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .environment(\.layoutDirection, entry.translationRtl ? .rightToLeft : .leftToRight)
    }

    /// Spec §2's footer: a hairline rule, then the surah at one end and the reference at the other.
    /// Compact (Medium) drops the rule and tightens the gap.
    @ViewBuilder
    private func footer(_ ayah: AyahPoolEntry) -> some View {
        if compact {
            footerRow(ayah).padding(.top, 6)
        } else {
            VStack(spacing: 0) {
                Rectangle()
                    .fill(textColor.opacity(Self.ruleAlpha))
                    // One *pixel*, not one point: a hairline that stays a hairline however dense
                    // the screen, as the in-app card dividers are.
                    .frame(height: 1 / displayScale)
                    .padding(.top, 9)
                footerRow(ayah).padding(.top, 8)
            }
        }
    }

    private func footerRow(_ ayah: AyahPoolEntry) -> some View {
        // Composed here rather than pre-formatted upstream, so it takes the same pass through
        // `WidgetDigits` every number a widget builds itself gets — against the choice the app
        // recorded in the mirror, so the reference is in the digits the app is drawing whatever
        // CLDR's default for the tag happens to be (D2).
        let reference = WidgetDigits.shared.localize(
            text: "\(ayah.surah):\(ayah.ayah)", nativeDigits: entry.arabicIndicDigits, languageTag: entry.languageTag
        )
        let arabicName = Text(ayah.surahArabic)
            .font(TaqwaHafs.font(size: footerArabicSize))
            .foregroundColor(textColor)
        let referenceText = Text(reference)
            .font(.system(size: 12))
            .foregroundColor(textColor.opacity(Self.tertiaryAlpha))

        return HStack(spacing: 0) {
            if entry.arabicUi {
                // No Latin at all under an Arabic UI: the surah name is already in the reader's
                // own script and a transliteration beside it would be noise.
                arabicName
                Spacer(minLength: 8)
                referenceText
            } else {
                (
                    Text(ayah.surahLatin)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(textColor)
                        + Text(" \u{00B7} ")
                        .font(.system(size: 12))
                        .foregroundColor(textColor.opacity(Self.tertiaryAlpha))
                        + referenceText
                )
                Spacer(minLength: 8)
                arabicName
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        // The *start* of the row follows the UI's direction, not the text's: under an Arabic UI
        // the surah name sits on the right and the reference on the left.
        .environment(\.layoutDirection, entry.arabicUi ? .rightToLeft : .leftToRight)
    }

    /// Spec §2's iOS-only placeholder: the mirror is written by the app, and until it has run once
    /// there is no pool to draw from.
    ///
    /// Looked up by hand rather than handed to SwiftUI as a `LocalizedStringKey`, because this file
    /// is compiled into the app too and the strings table ships only in the *extension's* bundle:
    /// the `-taqwaWidgetPreview` route would otherwise draw the raw key. The fallback is the same
    /// copy `Localizable.strings` carries.
    private var placeholderText: String {
        let key = "ayah_widget_placeholder"
        let localized = Bundle.main.localizedString(forKey: key, value: key, table: nil)
        guard localized == key else { return localized }
        let arabic = Locale.current.language.languageCode?.identifier.hasPrefix("ar") ?? false
        return arabic ? "افتح تقوى مرة لتحميل آية اليوم" : "Open Taqwa once to load today's ayah"
    }

    private var placeholder: some View {
        Text(placeholderText)
            .font(.system(size: 14))
            .multilineTextAlignment(.center)
            .foregroundColor(textColor.opacity(Self.secondaryAlpha))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Widget declaration

struct TaqwaAyahWidget: Widget {
    let kind = "TaqwaAyahWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AyahTimelineProvider()) { entry in
            TaqwaAyahWidgetView(entry: entry)
                // Spec §8: the tap opens that ayah in the reader. Nil before the mirror exists, so
                // the tap is a plain "open Taqwa" rather than a link to nowhere.
                .widgetURL(entry.entry.flatMap { URL(string: "taqwa://ayah/\($0.surah)/\($0.ayah)") })
        }
        .configurationDisplayName(LocalizedStringKey("ayah_widget_name"))
        .description(LocalizedStringKey("ayah_widget_description"))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}
