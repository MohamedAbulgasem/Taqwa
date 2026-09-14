package world.taqwa.app.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Four links, one place; a typo here is a dead row on the About screen on both platforms. */
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
}
