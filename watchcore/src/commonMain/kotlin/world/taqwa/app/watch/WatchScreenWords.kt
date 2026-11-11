package world.taqwa.app.watch

import kotlinx.serialization.Serializable

/**
 * The words of the watch app's screens and its Tile, in the phone's language (spec 1.3): the
 * titles, the notices a held feed may need, the Today rows' spoken states, the tasbeeh counter's
 * words and the Qibla dial's. Each is the phone's own string where the phone has one, read as its
 * pattern, `%1$s` and `%2$s` and all (plan 3 Task 10's table), and the watch fills the places with
 * [WatchRules.filled]. The rest are new phrases, written in seven languages for the watch.
 *
 * The property names are the JSON keys, under the feed's `ui.screens`, and every field is empty by
 * default: a feed written before the words, which only a development feed ever is, reads with none,
 * and a watch then speaks [ENGLISH] ([orEnglish], [WatchFeed.words]). So the words joined the feed
 * with no change to [WatchFeedJson.VERSION].
 *
 * The English given for each field is [ENGLISH]'s, which is the phone's English
 * (`values/strings.xml`) and, for the two notices, the Wear app's own (`open_taqwa_on_phone`,
 * `update_taqwa_on_watch`).
 *
 * Both watches read every field. Two are the Apple Watch's alone (plan 4 Task 3, added beside plan
 * 3's through the same reader): [appName] and [crownHint]. The Galaxy draws neither, and a Galaxy
 * build that predates them ignores them as any later key.
 */
@Serializable
data class WatchScreenWords(
    /** The Today page's title: "Today" (`qibla_back`, the phone's word for the Today tab). */
    val today: String = "",
    /** "Tasbeeh" (`tasbeeh_title`). */
    val tasbeeh: String = "",
    /** "Qibla" (`qibla_title`). */
    val qibla: String = "",
    /** "Taqwa", the app's name in the interface language (`app_name`): تقوى in Arabic, তাকওয়া in
     * Bengali. The Apple Watch's own words name the app where the watch draws it (plan 4 Task 3). */
    val appName: String = "",

    /** "Open Taqwa on your phone": Today and the Tile once the feed held is spent (spec 2.6). The
     * watch's own resources say it before any feed exists. */
    val openOnPhone: String = "",
    /** "Update Taqwa on your watch": a newer format has been seen, and the last feed is drawn on. */
    val updateOnWatch: String = "",

    /** What a screen reader is told of Today's lit row: "Current prayer" (`prayer_a11y_prayer_current`). */
    val rowCurrent: String = "",
    /** Of a row whose start has passed: "Passed" (`prayer_a11y_prayer_passed`). */
    val rowPassed: String = "",
    /** Of the next start's row: "Next prayer" (`prayer_a11y_prayer_next`). */
    val rowNext: String = "",

    /** "ROUND %1$s", the round's number (`tasbeeh_round`). */
    val round: String = "",
    /** "of %1$s", under the count, the target (`tasbeeh_of`). */
    val of: String = "",
    /** "Turn the bezel or tap", the counter's hint on the Galaxy (a new phrase). */
    val bezelHint: String = "",
    /** "Turn the crown or tap", the counter's hint on the Apple Watch (a new phrase, plan 4 Task 3). */
    val crownHint: String = "",
    /** "Reset" (`tasbeeh_reset`). */
    val reset: String = "",
    /** "Reset the count for this dhikr?" (`tasbeeh_reset_confirm`). */
    val resetConfirm: String = "",
    /**
     * Each built-in phrase's name, by its id (`TasbeehPresets.builtIn`, in its order): "after_prayer"
     * to "After prayer", "subhanallah" to "SubhanAllah" and so on (`tasbeeh_preset_<id>`).
     */
    val presets: Map<String, String> = emptyMap(),

    /** What a screen reader is told of the count: "%1$s of %2$s", the count and the target
     * (`tasbeeh_a11y_count`, accessibility round 2). */
    val countSpoken: String = "",
    /** At a part's end: "%1$s. Next, %2$s", the count and the next part's name (`tasbeeh_a11y_next`). */
    val nextSpoken: String = "",
    /** At the set's end: "%1$s. Round complete", the count (`tasbeeh_a11y_set_complete`). */
    val setCompleteSpoken: String = "",
    /** As the next round begins: "Round complete. %1$s, round %2$s", the phrase and the round
     * (`tasbeeh_a11y_round`). */
    val roundSpoken: String = "",
    /** The counter's name: "%1$s, round %2$s", the phrase and the round (`tasbeeh_a11y_name_round`). */
    val nameRoundSpoken: String = "",
    /** The counter's action: "Count" (`tasbeeh_a11y_count_action`). */
    val countAction: String = "",
    /** The bezel's turn back, as an action: "Take one away" (a new phrase). */
    val undoAction: String = "",
    /** The phrase list, as an action: "Choose a dhikr" (a new phrase). */
    val phrasesAction: String = "",

    /** "Facing qibla" (`qibla_facing_qibla`). */
    val facingQibla: String = "",
    /** "Makkah · %1$s km away", the feed's distance (`qibla_distance`). */
    val distance: String = "",
    /** "%1$s°", the feed's bearing (`qibla_bearing`). */
    val bearing: String = "",
    /** "%1$s° · %2$s km to Makkah", the bearing and the distance (`prayer_qibla_detail`). */
    val qiblaDetail: String = "",
    /**
     * "%1$s km", the distance as the turning dial draws it: the figure and the unit alone, the
     * watch's short form (Ruling 66 item 1, a new phrase). A screen reader still hears [distance].
     */
    val distanceOnDial: String = "",
    /**
     * "%1$s° · %2$s km", the bearing and the distance as the dimmed dial draws them, the watch's
     * short form (Ruling 66 item 1, a new phrase). A screen reader still hears [qiblaDetail].
     */
    val qiblaDetailOnDial: String = "",
    /** The dial's four letters, north, east, south and west: N, E, S, W (`qibla_cardinal_*`). */
    val cardinals: List<String> = emptyList(),
    /** "Hold your wrist level" (a new phrase, spec 1.3). */
    val holdLevel: String = "",
    /** "Compass needs calibrating" (`qibla_needs_calibrating`). */
    val needsCalibrating: String = "",
    /** "Magnetic interference" (`qibla_interference_title`). */
    val interference: String = "",
    /** "Compass unavailable" (`qibla_best_effort_title`). */
    val bestEffort: String = "",
    /** "No compass sensor" (`qibla_no_sensor_title`). */
    val noSensor: String = "",

    /**
     * What a screen reader is told while the Qibla is to the right: "Turn right about %1$s degrees",
     * the degrees in the phrase each number takes, picked by the number alone as [WatchSpoken.hours]
     * are (`qibla_a11y_turn_right`, a plural). The phone asks it at 15, 30, … 180 degrees.
     */
    val turnRight: List<WatchCount> = emptyList(),
    /** The same to the left: "Turn left about %1$s degrees" (`qibla_a11y_turn_left`). */
    val turnLeft: List<WatchCount> = emptyList(),
) {
    /**
     * These words with [ENGLISH]'s in place of every one the feed left blank: a string that is
     * empty or only spaces, a list or a map that is empty. [presets] is [ENGLISH]'s map overlaid by
     * this one's names, so every built-in has a name, in the built-ins' order, and a blank name is
     * the English one.
     */
    fun orEnglish(): WatchScreenWords {
        val english = ENGLISH
        fun String.or(fallback: String): String = ifBlank { fallback }
        fun <T> List<T>.or(fallback: List<T>): List<T> = ifEmpty { fallback }
        return WatchScreenWords(
            today = today.or(english.today),
            tasbeeh = tasbeeh.or(english.tasbeeh),
            qibla = qibla.or(english.qibla),
            appName = appName.or(english.appName),
            openOnPhone = openOnPhone.or(english.openOnPhone),
            updateOnWatch = updateOnWatch.or(english.updateOnWatch),
            rowCurrent = rowCurrent.or(english.rowCurrent),
            rowPassed = rowPassed.or(english.rowPassed),
            rowNext = rowNext.or(english.rowNext),
            round = round.or(english.round),
            of = of.or(english.of),
            bezelHint = bezelHint.or(english.bezelHint),
            crownHint = crownHint.or(english.crownHint),
            reset = reset.or(english.reset),
            resetConfirm = resetConfirm.or(english.resetConfirm),
            presets = english.presets + presets.filterValues { it.isNotBlank() },
            countSpoken = countSpoken.or(english.countSpoken),
            nextSpoken = nextSpoken.or(english.nextSpoken),
            setCompleteSpoken = setCompleteSpoken.or(english.setCompleteSpoken),
            roundSpoken = roundSpoken.or(english.roundSpoken),
            nameRoundSpoken = nameRoundSpoken.or(english.nameRoundSpoken),
            countAction = countAction.or(english.countAction),
            undoAction = undoAction.or(english.undoAction),
            phrasesAction = phrasesAction.or(english.phrasesAction),
            facingQibla = facingQibla.or(english.facingQibla),
            distance = distance.or(english.distance),
            bearing = bearing.or(english.bearing),
            qiblaDetail = qiblaDetail.or(english.qiblaDetail),
            distanceOnDial = distanceOnDial.or(english.distanceOnDial),
            qiblaDetailOnDial = qiblaDetailOnDial.or(english.qiblaDetailOnDial),
            cardinals = cardinals.or(english.cardinals),
            holdLevel = holdLevel.or(english.holdLevel),
            needsCalibrating = needsCalibrating.or(english.needsCalibrating),
            interference = interference.or(english.interference),
            bestEffort = bestEffort.or(english.bestEffort),
            noSensor = noSensor.or(english.noSensor),
            turnRight = turnRight.or(english.turnRight),
            turnLeft = turnLeft.or(english.turnLeft),
        )
    }

    companion object {
        /**
         * The English words: the phone's own English for its strings, and the English of the new
         * phrases. A feed without words speaks these ([orEnglish]).
         */
        val ENGLISH: WatchScreenWords = WatchScreenWords(
            today = "Today",
            tasbeeh = "Tasbeeh",
            qibla = "Qibla",
            appName = "Taqwa",
            openOnPhone = "Open Taqwa on your phone",
            updateOnWatch = "Update Taqwa on your watch",
            rowCurrent = "Current prayer",
            rowPassed = "Passed",
            rowNext = "Next prayer",
            round = "ROUND %1\$s",
            of = "of %1\$s",
            bezelHint = "Turn the bezel or tap",
            crownHint = "Turn the crown or tap",
            reset = "Reset",
            resetConfirm = "Reset the count for this dhikr?",
            presets = linkedMapOf(
                "after_prayer" to "After prayer",
                "subhanallah" to "SubhanAllah",
                "alhamdulillah" to "Alhamdulillah",
                "allahu_akbar" to "Allahu Akbar",
                "astaghfirullah" to "Astaghfirullah",
                "la_ilaha_illallah" to "La ilaha illallah",
                "subhanallahi_wa_bihamdihi" to "SubhanAllahi wa bihamdihi",
            ),
            countSpoken = "%1\$s of %2\$s",
            nextSpoken = "%1\$s. Next, %2\$s",
            setCompleteSpoken = "%1\$s. Round complete",
            roundSpoken = "Round complete. %1\$s, round %2\$s",
            nameRoundSpoken = "%1\$s, round %2\$s",
            countAction = "Count",
            undoAction = "Take one away",
            phrasesAction = "Choose a dhikr",
            facingQibla = "Facing qibla",
            distance = "Makkah · %1\$s km away",
            bearing = "%1\$s°",
            qiblaDetail = "%1\$s° · %2\$s km to Makkah",
            distanceOnDial = "%1\$s km",
            qiblaDetailOnDial = "%1\$s° · %2\$s km",
            cardinals = listOf("N", "E", "S", "W"),
            holdLevel = "Hold your wrist level",
            needsCalibrating = "Compass needs calibrating",
            interference = "Magnetic interference",
            bestEffort = "Compass unavailable",
            noSensor = "No compass sensor",
            turnRight = listOf(WatchCount(180, "Turn right about %1\$s degrees")),
            turnLeft = listOf(WatchCount(180, "Turn left about %1\$s degrees")),
        )
    }
}

/** The words of the watch's screens in the phone's language, with the English for any it lacks. */
fun WatchFeed.words(): WatchScreenWords = ui.screens.orEnglish()
