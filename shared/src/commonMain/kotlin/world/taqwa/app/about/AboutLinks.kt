package world.taqwa.app.about

/**
 * Where the About screen's links go (privacy spec §6.3). One place to change when the
 * repository moves or taqwa.world hosts the policy. The repository has been public since
 * 13 September 2026, so all of them resolve; the store forms carry the policy one.
 *
 * Also where the Settings root's "Rate Taqwa" and "Share Taqwa" rows go (privacy spec §6.5):
 * the store pages and the line the share sheet carries.
 */
object AboutLinks {
    /** Where "Report a problem" and the crash sheet address their email; the site and the policy say the same. */
    const val SUPPORT_EMAIL = "support@taqwa.world"
    const val WEBSITE = "https://taqwa.world/"
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"

    /** Taqwa's Apple ID in App Store Connect; the README, the site's badge and `site/stores.json` link the same page. */
    const val APP_STORE_ID = "6814975544"

    /** The Android application id, which is also what Google Play files the listing under. */
    const val PLAY_PACKAGE = "world.taqwa.app"

    /**
     * The stores' own names, shown as the Rate row's value. Proper names, the same in every
     * language and in Latin script under Arabic and Urdu too, as the stores print them on their
     * own badges, so they are constants here rather than strings for translators to vary.
     */
    const val APP_STORE_NAME = "App Store"
    const val GOOGLE_PLAY_NAME = "Google Play"

    /** The platform's own store: Taqwa is listed on one per platform, and the row names that one. */
    fun storeName(isIos: Boolean): String = if (isIos) APP_STORE_NAME else GOOGLE_PLAY_NAME

    /**
     * Where "Rate Taqwa" goes, in the order to try; the caller opens the first that opens.
     *
     * On iOS one link: the listing with `action=write-review`, Apple's documented way in, which the
     * App Store app opens on its write-a-review page rather than at the top of the listing. On
     * Android the `market://` scheme first, which goes straight to the Play Store app. A phone
     * with no store app at all (an emulator image without Play) has nothing that answers that
     * scheme, so the listing's web address follows, which any browser opens. Other stores
     * (F-Droid, Aurora, AppGallery) also answer `market://`, so on a phone with one of those
     * instead of Play the row opens that store's page for Taqwa.
     */
    fun rateLinks(isIos: Boolean): List<String> = if (isIos) {
        listOf("https://apps.apple.com/app/id$APP_STORE_ID?action=write-review")
    } else {
        listOf(
            "market://details?id=$PLAY_PACKAGE",
            "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE",
        )
    }

    /**
     * What "Share Taqwa" hands the share sheet: the translated one-line description, then the
     * site in the app's language on its own line, so the reader on the other end lands on the
     * half of the site written in the language the sender uses. The site, not a store: it links
     * both stores and the sender cannot know which phone the reader has.
     */
    fun shareMessage(line: String, languageCode: String): String = line + "\n" + website(languageCode)

    /** The site speaks the app's seven languages, English at the root; open the one the app is in. */
    fun website(languageCode: String): String = if (languageCode == "en") WEBSITE else "$WEBSITE$languageCode/"

    /** The same policy as [PRIVACY_POLICY], rendered by the site in the app's language. */
    fun privacyPolicy(languageCode: String): String = website(languageCode) + "privacy/"
}
