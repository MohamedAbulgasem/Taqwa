package world.taqwa.app.feature.prayer

import org.jetbrains.compose.resources.StringResource
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_checked_so_far_with_data
import world.taqwa.app.resources.about_checked_so_far_with_data_copy
import world.taqwa.app.resources.about_checked_so_far_with_data_part
import world.taqwa.app.resources.about_checked_through
import world.taqwa.app.resources.about_checked_through_copy
import world.taqwa.app.resources.about_checked_through_part
import world.taqwa.app.resources.about_stat_never_before
import world.taqwa.app.resources.about_stat_never_before_copy
import world.taqwa.app.resources.about_stat_never_before_part
import world.taqwa.app.resources.prayer_whose_checked_body
import world.taqwa.app.resources.prayer_whose_checked_copy_body
import world.taqwa.app.resources.prayer_whose_checked_part_body

/**
 * The sentences that say Taqwa's times were compared with an authority's timetable, each with the
 * twin that says whose copy of it was read (the audit of 12 Nov 2026, finding F26). Where the
 * tables did not come from the authority's own pages
 * ([world.taqwa.app.prayer.engine.registry.RegistryEntry.republisher]) "checked against its
 * published timetable" and "never before Kemenag's" claim more than was done, so every such
 * sentence is chosen here and nowhere else: a screen cannot name the comparison without the copy.
 * The twin takes the republisher's name as its last argument.
 *
 * The third, [part], says that only some of the tables were a copy, the rest the authority's own (the second audit's
 * G7, ruling R276: JAKIM's mirrored years, the Islamic Foundation's Dhaka table); it takes the name where the copy
 * twin does. [copyName] is the name a sentence passes as that last argument.
 */
enum class CheckedClaim(val own: StringResource, val copy: StringResource, val part: StringResource) {
    /** The Prayer screen's card: "…reproduced by Taqwa and checked against its published timetable." */
    WHOSE_BODY(Res.string.prayer_whose_checked_body, Res.string.prayer_whose_checked_copy_body, Res.string.prayer_whose_checked_part_body),

    /** About these times: "Checked against …'s published timetable through …". */
    THROUGH(Res.string.about_checked_through, Res.string.about_checked_through_copy, Res.string.about_checked_through_part),

    /** About these times, the tile under the nought: "starts before …'s". */
    NEVER_BEFORE(Res.string.about_stat_never_before, Res.string.about_stat_never_before_copy, Res.string.about_stat_never_before_part),

    /** About these times beyond the checked places: "Checked on … days so far: never before …'s". */
    SO_FAR(
        Res.string.about_checked_so_far_with_data,
        Res.string.about_checked_so_far_with_data_copy,
        Res.string.about_checked_so_far_with_data_part,
    );

    /** The whole copy's twin where [republisher] is named, else the part twin where [partly] is, else the own sentence. */
    fun sentence(republisher: String?, partly: String? = null): StringResource = when {
        republisher != null -> copy
        partly != null -> part
        else -> own
    }

    companion object {
        /** The name the chosen twin takes as its last argument: the whole copy's, else the part's; null for the own sentence. */
        fun copyName(republisher: String?, partly: String?): String? = republisher ?: partly
    }
}
