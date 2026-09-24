package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AttributionSourceUrlTest {

    // A credit shows its source bare ("tanzil.net"), but the URI handler needs the scheme: Android
    // resolves no activity for a bare domain, and the guarded tap would silently do nothing.
    @Test fun aCreditOpensItsSourceOverHttpsWithThePathKept() {
        assertEquals("https://tanzil.net", sourceUrl("tanzil.net"))
        assertEquals("https://tanzil.net/trans", sourceUrl("tanzil.net/trans"))
        assertEquals("https://github.com/zonetecde/mushaf-layout", sourceUrl("github.com/zonetecde/mushaf-layout"))
    }
}
