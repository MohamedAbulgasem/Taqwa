import Foundation

/// The watch's own words for the empty form with no feed: the extension's table, in the watch's
/// language (spec 1.3), as `OwnWords` reads them in each watch target. Every other word is the
/// feed's.
struct OwnLines: Equatable {
    /// The app's name as the system shows it (`CFBundleDisplayName`).
    let appName: String
    /// `open_taqwa_on_phone`: "Open Taqwa on your phone".
    let openOnPhone: String
    /// `update_taqwa_on_watch`: "Update Taqwa on your watch" (plan 3's call 5).
    let updateOnWatch: String
}

/// What one entry of the next-prayer widget draws (plan 4 Task 6): its glance, or the empty form
/// and the words it needs. Made once per entry from the timeline, which read the feed's words once
/// (plan 4 erratum p4 #12), and the watch's own words, which the provider reads once. It lives here,
/// with no family in it, so that `swift test` holds what each family draws and says (Ruling 64
/// item 3); the views pick the family.
struct NextPrayerContent: Equatable {
    let glance: Glance?
    private(set) var countdownShown: Bool
    /// The feed's words; nil with no feed, when the watch's own speak ([own]).
    let words: WatchScreenWords?
    /// A newer format has been seen: the empty form says "Update Taqwa on your watch".
    let update: Bool
    /// How the feed writes; nil with no feed.
    let script: GlanceScript?
    /// The watch's own words, for the empty form with no feed.
    let own: OwnLines

    init(_ entry: GlanceEntry, of timeline: GlanceTimeline, own: OwnLines) {
        glance = entry.glance
        countdownShown = entry.countdownShown && entry.glance != nil
        words = timeline.words
        update = timeline.update
        script = timeline.script
        self.own = own
    }

    /// The same content without its countdown: what the hour's last entry draws.
    var withoutCountdown: NextPrayerContent {
        var plain = self
        plain.countdownShown = false
        return plain
    }

    /// Whether the feed is written right to left; nil with no feed, so the watch's own words take
    /// the watch's own direction.
    var rtl: Bool? { glance?.rtl ?? script?.rtl }

    /// Whether the words are in Latin letters: Manrope, capitals and tracking. The watch's own
    /// words, with no feed, are drawn in the system font as the watch draws its own.
    var latin: Bool { glance?.latinScript ?? script?.latinScript ?? false }

    /// The app's name in the empty form and the Smart Stack's header: the feed's (تقوى in Arabic),
    /// or the watch's own `CFBundleDisplayName` with no feed.
    var appName: String { words?.appName ?? own.appName }

    /// The empty form's notice, in plan 3's call 5 order: a newer format seen says the update,
    /// with a feed or without; otherwise "Open Taqwa on your phone". The feed's words when it has
    /// some, the watch's own otherwise.
    var notice: String {
        if update { return words?.updateOnWatch ?? own.updateOnWatch }
        return words?.openOnPhone ?? own.openOnPhone
    }

    /// The corner's line along the bezel: "DHUHR 12:33". None in the empty form, whose corner is
    /// the arch alone.
    var cornerLine: String? {
        glance.map { "\($0.label) \($0.time)" }
    }

    /// The inline line: "ASR in 1 h 12 min", "ASR 16:10" without the countdown, the app's name in
    /// the empty form.
    var inlineLine: String {
        guard let glance else { return appName }
        return countdownShown ? "\(glance.label) \(glance.countdown)" : "\(glance.label) \(glance.time)"
    }

    /// The one sentence VoiceOver reads for the whole complication (Ruling 38, Ruling 42), in the
    /// feed's language (erratum p4 #5): the prayer, the time left and its time; the row's form
    /// without the countdown; and the empty form's words, the app's name alone where that is all
    /// it draws ([inline], the inline family). The name and the notice are joined as the row's form
    /// is (`Glances.separator`): "تقوى، …" in Arabic and Urdu, as Today joins that notice (Ruling 75
    /// item 1). The watch's own words, with no feed, keep ", ": nothing here reads the watch's
    /// language.
    func spoken(inline: Bool) -> AttributedString {
        if let glance {
            return Speech.line(countdownShown ? glance.spoken : glance.spokenWithoutCountdown, language: glance.language)
        }
        let words = inline ? appName : appName + Glances.separator(script?.rtl == true) + notice
        return Speech.line(words, language: script?.language)
    }
}
