package world.taqwa.app.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The links, one place; a typo here is a dead row on the About screen or the Settings root on both
 * platforms. A store link that names the wrong id opens someone else's listing, or none.
 */
class AboutLinksTest {

    private val all = listOf(AboutLinks.PRIVACY_POLICY, AboutLinks.SOURCE, AboutLinks.LICENCE)

    @Test
    fun everyLinkIsHttpsToTheTaqwaOwner() {
        for (link in all) assertTrue(link.startsWith("https://github.com/MohamedAbulgasem/"), link)
    }

    @Test
    fun theWebsiteOpensInTheLanguageTheAppIsShowing() {
        assertEquals("https://taqwa.world/", AboutLinks.website("en"))
        assertEquals("https://taqwa.world/ar/", AboutLinks.website("ar"))
    }

    @Test
    fun thePolicyLinkPointsAtThePolicyFile() {
        assertTrue(AboutLinks.PRIVACY_POLICY.endsWith("/PRIVACY.md"))
    }

    @Test
    fun theLicenceLinkPointsAtTheLicenceFile() {
        assertTrue(AboutLinks.LICENCE.endsWith("/LICENSE"))
    }

    @Test
    fun everyOtherLanguageHasItsOwnHalfOfTheSite() {
        assertEquals("https://taqwa.world/fr/", AboutLinks.website("fr"))
        assertEquals("https://taqwa.world/ur/", AboutLinks.website("ur"))
        assertEquals("https://taqwa.world/", AboutLinks.website("en"))
    }


    @Test
    fun thePolicyOpensInTheInterfaceLanguage() {
        assertEquals("https://taqwa.world/privacy/", AboutLinks.privacyPolicy("en"))
        assertEquals("https://taqwa.world/ur/privacy/", AboutLinks.privacyPolicy("ur"))
        assertEquals("https://taqwa.world/tr/privacy/", AboutLinks.privacyPolicy("tr"))
    }

    @Test
    fun rateOnIosOpensTheAppStoreListingAtItsReviewPage() {
        assertEquals(
            listOf("https://apps.apple.com/app/id6814975544?action=write-review"),
            AboutLinks.rateLinks(isIos = true),
        )
    }

    @Test
    fun rateOnAndroidTriesThePlayStoreAppBeforeTheWebListing() {
        assertEquals(
            listOf(
                "market://details?id=world.taqwa.app",
                "https://play.google.com/store/apps/details?id=world.taqwa.app",
            ),
            AboutLinks.rateLinks(isIos = false),
        )
    }

    @Test
    fun theRateRowNamesEachPlatformsOwnStore() {
        assertEquals("App Store", AboutLinks.storeName(isIos = true))
        assertEquals("Google Play", AboutLinks.storeName(isIos = false))
    }

    @Test
    fun theShareMessageIsTheLineThenTheSiteInTheAppsLanguage() {
        val line = "Taqwa: prayer times, the Quran and the Qibla."
        assertEquals("$line\nhttps://taqwa.world/", AboutLinks.shareMessage(line, "en"))
        assertEquals("$line\nhttps://taqwa.world/ar/", AboutLinks.shareMessage(line, "ar"))
        assertEquals(2, AboutLinks.shareMessage(line, "bn").lines().size)
    }
}
