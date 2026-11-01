package world.taqwa.app.recitation

import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSURLSessionConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Requests carry nothing extra (security review A4, B2): no cookie kept or sent, and a User-Agent
 * and an Accept-Language that are the same for everyone, on the manifest GET and on both
 * background download sessions.
 */
class SessionConfigurationTest {

    private fun assertCarriesNothingExtra(configuration: NSURLSessionConfiguration) {
        assertFalse(configuration.HTTPShouldSetCookies, "cookies are sent")
        assertNull(configuration.HTTPCookieStorage, "cookies are kept")
        assertEquals(NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever, configuration.HTTPCookieAcceptPolicy)
        assertEquals("Taqwa", configuration.HTTPAdditionalHeaders?.get("User-Agent"))
        // CFNetwork otherwise sends the reader's own languages, in their order of preference.
        assertEquals("*", configuration.HTTPAdditionalHeaders?.get("Accept-Language"))
    }

    @Test
    fun theManifestGetKeepsNoCookieAndNoCache() {
        val configuration = manifestConfiguration()
        assertCarriesNothingExtra(configuration)
        assertNull(configuration.URLCache, "responses are cached")
    }

    @Test
    fun theBackgroundSessionsKeepTheirIdentifiersAndTheirRules() {
        // The identifier is how iOS hands a relaunched app the downloads still in flight, so it
        // must come out exactly as it went in.
        for ((identifier, cellular) in listOf("world.taqwa.app.recitation" to false, "world.taqwa.app.recitation.mobile" to true)) {
            val configuration = backgroundConfiguration(identifier, cellular)
            assertEquals(identifier, configuration.identifier)
            assertEquals(cellular, configuration.allowsCellularAccess)
            assertTrue(configuration.sessionSendsLaunchEvents)
            assertCarriesNothingExtra(configuration)
        }
    }
}
