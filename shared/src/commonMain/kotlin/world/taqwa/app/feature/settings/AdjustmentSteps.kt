package world.taqwa.app.feature.settings

/** What one tap on a Manual adjustments stepper does (spec §2.2). */
sealed interface AdjustStep {
    /** Store [minutes] as it is. */
    data class Apply(val minutes: Int) : AdjustStep

    /**
     * Ask first, with the one template ("Show Maghrib before Diyanet’s time?"); [strong] when
     * [minutes] passes the timetable's own margin, where it asks again in stronger words.
     */
    data class Ask(val minutes: Int, val strong: Boolean) : AdjustStep
}

/**
 * The step from [current] to [next] minutes for a prayer whose earlier adjustment was last
 * confirmed under [confirmedUnder] (null for never), while the day follows [effectiveId], whose
 * margin for this prayer is [margin] minutes (null where none is claimed).
 *
 * A later time, or a step back towards the timetable's, never asks. The first step below it asks
 * once per prayer and timetable; once confirmed, the one step that passes the margin asks again,
 * more strongly, and every step beyond it is the user's own.
 */
fun adjustStep(current: Int, next: Int, confirmedUnder: String?, effectiveId: String, margin: Int?): AdjustStep {
    if (next >= 0 || next > current) return AdjustStep.Apply(next)
    val pastMargin = margin != null && next < -margin
    if (confirmedUnder != effectiveId) return AdjustStep.Ask(next, strong = pastMargin)
    val crossing = margin != null && current >= -margin && pastMargin
    return if (crossing) AdjustStep.Ask(next, strong = true) else AdjustStep.Apply(next)
}

/**
 * Re-confirming a paused earlier adjustment of [minutes] under the timetable now followed: the
 * same question, in its stronger words when the value is past that timetable's [margin].
 */
fun reconfirmStep(minutes: Int, margin: Int?): AdjustStep.Ask = AdjustStep.Ask(minutes, strong = margin != null && minutes < -margin)
