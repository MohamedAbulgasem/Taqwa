package world.taqwa.app.about

import kotlin.test.Test
import kotlin.test.assertTrue

/** Three links, one place; a typo here is a dead row on the About screen on both platforms. */
class AboutLinksTest {

    private val all = listOf(AboutLinks.PRIVACY_POLICY, AboutLinks.SOURCE, AboutLinks.LICENCE)

    @Test
    fun everyLinkIsHttpsToTheTaqwaOwner() {
        for (link in all) assertTrue(link.startsWith("https://github.com/MohamedAbulgasem/"), link)
    }

    @Test
    fun thePolicyLinkPointsAtThePolicyFile() {
        assertTrue(AboutLinks.PRIVACY_POLICY.endsWith("/PRIVACY.md"))
    }

    @Test
    fun theLicenceLinkPointsAtTheLicenceFile() {
        assertTrue(AboutLinks.LICENCE.endsWith("/LICENSE"))
    }
}
