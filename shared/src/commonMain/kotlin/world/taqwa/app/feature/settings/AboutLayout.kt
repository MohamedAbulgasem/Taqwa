package world.taqwa.app.feature.settings

import org.jetbrains.compose.resources.StringResource
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_help_label
import world.taqwa.app.resources.about_open_source_label
import world.taqwa.app.resources.about_privacy_label

/**
 * The link rows About Taqwa can show. Every one the old About screen and the Settings root's About
 * section had is here (Rate and Share became the two buttons under the hero instead), so the test
 * that each sits in exactly one group is the test that the redesign lost none of them.
 */
internal enum class AboutRow { PRIVACY_POLICY, SOURCE, LICENCE, ATTRIBUTION, REPORT, WEBSITE }

/** One card of [AboutRow]s under its section label. */
internal data class AboutGroup(val label: StringResource, val rows: List<AboutRow>)

/**
 * About Taqwa's cards, top to bottom (settings redesign spec, approved 30 Oct 2026). The privacy
 * policy closes the privacy card, under the promises it spells out; Attribution & licences sits
 * with the source and the licence it belongs beside; Help is how to reach a person, then the site.
 */
internal val AboutGroups: List<AboutGroup> = listOf(
    AboutGroup(Res.string.about_privacy_label, listOf(AboutRow.PRIVACY_POLICY)),
    AboutGroup(Res.string.about_open_source_label, listOf(AboutRow.SOURCE, AboutRow.LICENCE, AboutRow.ATTRIBUTION)),
    AboutGroup(Res.string.about_help_label, listOf(AboutRow.REPORT, AboutRow.WEBSITE)),
)
