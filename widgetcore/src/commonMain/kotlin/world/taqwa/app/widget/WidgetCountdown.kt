package world.taqwa.app.widget

/**
 * Derives "how long until the next prayer" from the widget's **own** clock at the moment it draws,
 * rather than from the number that happened to be true when the mirror was last written.
 *
 * The distinction is the whole of finding I8. The mirror is only ever written while the Prayer
 * screen is open; the Android widget is otherwise re-rendered by a half-hourly `APPWIDGET_UPDATE`
 * that hands it the *same* mirror again. Rendering [WidgetSnapshot.countdownMinutes] verbatim
 * therefore meant: open the app at 14:00 with Asr forty minutes away, close it, and at 17:00 the
 * home screen still reads "ASR IN 0:40" — confidently, precisely wrong, for most of the day. A
 * widget whose one job is to be right about how long is left cannot do that.
 *
 * Kept here in `:widgetcore` rather than in the Glance code so it is covered by the shared test
 * suite on both targets: iOS works out every timeline entry with the same rule, and both widgets
 * take what to show when there is no honest answer ([headline], [ringProgress]) from here too.
 */
object WidgetCountdown {

    /**
     * Whole minutes remaining at [nowEpochSeconds], truncated the same way
     * `TodayState.countdown.inWholeMinutes` truncates, or `null` when no honest answer exists:
     *
     *  - the mirror predates [WidgetSnapshot.nextPrayerEpochSeconds] (the `0` sentinel), so there
     *    is no absolute instant to count towards; or
     *  - the next prayer is already in the past, meaning the mirror is stale enough that the app
     *    has not been opened since that prayer came in.
     *
     * A caller must show the prayer name without a countdown in both cases. Showing a negative
     * number, clamping to zero, or falling back to the frozen `countdownMinutes` would each be a
     * different way of stating something the widget does not actually know.
     */
    fun remainingMinutesAt(snapshot: WidgetSnapshot, nowEpochSeconds: Long): Long? {
        // With a schedule the answer survives prayer boundaries: the next instant is whichever
        // entry is first after now. Only a mirror older than its whole two-day horizon has no
        // honest answer left.
        if (snapshot.schedule.isNotEmpty()) {
            val upcoming = snapshot.schedule.filter { it.epochSeconds > nowEpochSeconds }.minOfOrNull { it.epochSeconds }
                ?: return null
            return (upcoming - nowEpochSeconds) / 60L
        }
        val next = snapshot.nextPrayerEpochSeconds
        if (next <= 0L) return null
        val remainingSeconds = next - nowEpochSeconds
        if (remainingSeconds < 0L) return null
        return remainingSeconds / 60L
    }

    /**
     * The line over the countdown: the "next prayer in" phrase, or, when [remainingMinutes] is
     * `null`, the prayer's name alone. With no honest countdown there is no number for "in" to
     * lead into, and "Asr in" over nothing (or over "0:00", which is what iOS drew once its
     * schedule ran out, B16) reads as a prayer about to begin.
     */
    fun headline(content: WidgetContent, remainingMinutes: Long?): String =
        if (remainingMinutes == null) content.nextPrayerDisplayName else content.countdownLabel

    /**
     * The ring's fill, or an empty ring when [remainingMinutes] is `null`. That is exactly when
     * [content] comes from `WidgetContentBuilder.build(snapshot)`, whose ring is the one frozen at
     * the moment of writing: a ring that stands still at two-thirds for days says the same false
     * thing a stale number would.
     */
    fun ringProgress(content: WidgetContent, remainingMinutes: Long?): Float =
        if (remainingMinutes == null) 0f else content.ringProgress
}
