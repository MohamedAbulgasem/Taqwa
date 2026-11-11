package world.taqwa.app.feature.settings

import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_online_body_android
import world.taqwa.app.resources.about_online_body_ios
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * About's "Online only for what you choose" row names the company that may carry the watch's
 * data on this platform alone: Apple on the iPhone, Google on Android (App Store Review
 * Guideline 2.3.10, Ruling 63). [aboutOnlineBody] is a pure function of the platform flag, so
 * both branches are checked here; `isIosPlatform` itself is a one-line `expect`/`actual` fact.
 * What each text says, in seven languages, is the privacy gate's rows (`tools/privacy-check.py`).
 */
class AboutOnlineBodyTest {

    @Test
    fun theIphoneReadsTheTextThatNamesApple() {
        assertEquals(Res.string.about_online_body_ios, aboutOnlineBody(isIos = true))
    }

    @Test
    fun androidReadsTheTextThatNamesGoogle() {
        assertEquals(Res.string.about_online_body_android, aboutOnlineBody(isIos = false))
    }

    @Test
    fun theTwoPlatformsNeverReadTheSameText() {
        assertNotEquals(aboutOnlineBody(isIos = true), aboutOnlineBody(isIos = false))
    }
}
