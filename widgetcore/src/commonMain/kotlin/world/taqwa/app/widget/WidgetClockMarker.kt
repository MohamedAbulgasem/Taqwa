package world.taqwa.app.widget

/**
 * Whether any of [times] carries a day-period marker, i.e. was written by the 12-hour option
 * ("5:10 PM", "ÖS 5:10", "٦:٣٢ م"). A 24-hour time is digits and a colon and nothing else, in
 * whichever digit set the app draws, so any other character, the space or the marker itself,
 * means a 12-hour time.
 *
 * A widget reads the strings the app wrote and has no other way to know which format wrote them.
 * It matters because the marker makes the longest prayer row wider, and a layout sized for it
 * would otherwise change how the widget looks at the default 24-hour setting. Pure and
 * platform-free so the iOS extension, which links `widgetcore` alone, can ask the same question.
 */
fun clockTimesCarryMarker(times: List<String>): Boolean =
    times.any { time -> time.any { !it.isDigit() && it != ':' } }

/** [clockTimesCarryMarker] over everything this content shows: the next prayer and the five rows. */
fun WidgetContent.carriesDayPeriodMarker(): Boolean =
    clockTimesCarryMarker(listOf(nextClockTime) + rows.map { it.clockTime })
