package world.taqwa.app.i18n

/**
 * A run of one direction kept whole inside a sentence of the other (spec §2.4): [text] between a
 * first-strong isolate and its pop (U+2068 … U+2069). "Umm al-Qura" inside an Arabic sentence,
 * «أم القرى» inside an English one.
 */
fun isolated(text: String): String = "\u2068$text\u2069"

/**
 * A clock time spliced into a sentence. A 12-hour time ("5:10 PM", "٥:١٠ م", "ÖS 5:10") is digits,
 * a space and a marker, often in another script from the sentence around it. Left bare, the bidi
 * algorithm lets the words beside it reorder those pieces: in Urdu, whose marker is Latin, "6:32 PM"
 * comes out "PM 6:32". Wrapped by [isolated], it stays one unit whichever way the sentence runs.
 *
 * Only a time that is more than digits and a colon is wrapped. A 24-hour time has always been
 * spliced bare and reads correctly in every language the app speaks, so it comes back exactly as
 * it was given: under the default setting every sentence that splices a time is byte for byte what
 * it was before this helper. The notifications, the adhkar caption, the timetable and Match my
 * mosque lines and About these times splice through here; the Prayer screen's sub-lines, which
 * isolated every time from the start, keep [isolated] itself.
 */
fun isolatedClock(clockTime: String): String =
    if (clockTime.all { it.isDigit() || it == ':' }) clockTime else isolated(clockTime)
