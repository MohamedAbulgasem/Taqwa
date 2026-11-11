import Foundation

/// The watch's own words (spec 1.3): what the watch shows before it holds a feed, and what the
/// system shows of the app. They come from the target's own tables, in the *watch's* language.
/// Every other word a Taqwa surface draws or speaks comes from the feed, in the phone's words.
///
/// Both watch targets compile this file, and each reads its own bundle's tables. Both name
/// `open_taqwa_on_phone` in their own code, which is the canary `scripts/check-watch-bundle.sh`
/// (rule 5) looks for: a scan of the built bundles that misses it has missed the code.
enum OwnWords {
    /// "Open Taqwa on your phone": no feed has come from the phone yet.
    static var openTaqwaOnPhone: String {
        NSLocalizedString("open_taqwa_on_phone", comment: "The watch holds no feed yet")
    }

    /// "Update Taqwa on your watch": a feed in a newer format than this build reads was seen
    /// (plan 3's call 5).
    static var updateTaqwaOnWatch: String {
        NSLocalizedString("update_taqwa_on_watch", comment: "The phone writes a newer format")
    }

    /// The three, for the empty form with no feed (`NextPrayerContent`): read once per timeline.
    static var lines: OwnLines {
        OwnLines(appName: appName, openOnPhone: openTaqwaOnPhone, updateOnWatch: updateTaqwaOnWatch)
    }

    /// The app's name as the system shows it, in the watch's language (`InfoPlist.strings`).
    static var appName: String {
        (Bundle.main.localizedInfoDictionary?["CFBundleDisplayName"] as? String)
            ?? (Bundle.main.infoDictionary?["CFBundleDisplayName"] as? String)
            ?? (Bundle.main.infoDictionary?["CFBundleName"] as? String)
            ?? ""
    }
}
