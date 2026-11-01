package world.taqwa.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_help_label
import world.taqwa.app.resources.about_open_source_label
import world.taqwa.app.resources.about_privacy_label

/**
 * The cards of link rows on About Taqwa, decided as data so the one promise of the redesign that a
 * reader would notice breaking — every row the screen and the Settings root had is still somewhere,
 * and only once — is checked without a composable.
 */
class AboutLayoutTest {

    @Test
    fun `every row appears exactly once across the groups`() {
        val placed = AboutGroups.flatMap { it.rows }
        assertEquals(AboutRow.entries.toSet(), placed.toSet())
        assertEquals(AboutRow.entries.size, placed.size)
    }

    @Test
    fun `the groups are privacy then open source then help`() {
        assertEquals(
            listOf(Res.string.about_privacy_label, Res.string.about_open_source_label, Res.string.about_help_label),
            AboutGroups.map { it.label },
        )
    }

    @Test
    fun `the privacy policy closes the privacy card and attribution sits with the source`() {
        assertEquals(listOf(AboutRow.PRIVACY_POLICY), AboutGroups[0].rows)
        assertEquals(listOf(AboutRow.SOURCE, AboutRow.LICENCE, AboutRow.ATTRIBUTION), AboutGroups[1].rows)
        assertEquals(listOf(AboutRow.REPORT, AboutRow.WEBSITE), AboutGroups[2].rows)
    }
}
