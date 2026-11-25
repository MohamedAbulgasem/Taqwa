package world.taqwa.app.feature.common

import world.taqwa.app.prayer.engine.registry.Registry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthorityNamesTest {

    /**
     * Every key the screens can be handed: each entry's short name, each cautious member's, and the body a calculated
     * entry is on the safe side of.
     */
    private val registryKeys: Set<String> = Registry.entries.flatMap { entry ->
        listOfNotNull(entry.shortNameKey, entry.safeSideOfKey) + entry.members.map { it.nameKey }
    }.toSet()

    @Test
    fun everyNameTheRegistryUsesHasAString() {
        val missing = registryKeys.filter { authorityNameRes(it) == null }
        assertEquals(emptyList(), missing, "keys with no string")
    }

    @Test
    fun everyOtherMethodHasAString() {
        assertTrue(Registry.otherMethods.all { authorityNameRes(it.shortNameKey) != null })
    }

    @Test
    fun anUnknownKeyHasNoString() {
        assertNull(authorityNameRes("authority_nobody"))
    }
}
