package world.taqwa.app.watch

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import world.taqwa.app.watch.Fixtures.at
import world.taqwa.app.watch.Fixtures.atTromso
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Keeps the two files under `watchcore/vectors/` in step with the rules and the fixtures:
 *
 * - `watch-rules-vectors.json` is what [WatchRules] answers for a set of feeds and times. The
 *   watchOS app ports the rules to Swift (spec 1.4), and its tests run this file: the same feeds,
 *   the same times, the same answers. A change to a rule changes the file, so the port cannot
 *   drift from the Kotlin without a test failing there.
 * - `sample-feed-capetown.json` is `Fixtures.capeTownSample()` as the phone would send it, from
 *   yesterday. The Wear app's debug build starts from it. `sample-feed-capetown-<name>.json` are
 *   `Fixtures.capeTownSamples()`, the same days in Arabic and on a twelve-hour clock, which the
 *   debug build stores when it is asked for them by name.
 *
 * The test fails when a file is missing or differs. After a change that is meant, rewrite the
 * files with `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors` (the build file
 * passes the flag on), read the diff, and commit it. Neither file holds anything that varies from
 * run to run, so the same rules write the same bytes.
 */
class VectorsTest {

    @Test
    fun vectorsAreUpToDate() {
        val directory = if (File("src/androidHostTest").isDirectory) File("vectors") else File("watchcore/vectors")
        val files = linkedMapOf(
            "watch-rules-vectors.json" to rulesVectors(),
            "sample-feed-capetown.json" to WatchFeedJson.encode(Fixtures.capeTownSample()) + "\n",
        )
        Fixtures.capeTownSamples().forEach { (name, feed) ->
            files["sample-feed-capetown-$name.json"] = WatchFeedJson.encode(feed) + "\n"
        }

        if (System.getProperty("taqwa.updateVectors") != null) {
            directory.mkdirs()
            files.forEach { (name, text) -> File(directory, name).writeText(text) }
            return
        }

        val problems = files.mapNotNull { (name, text) -> problemWith(File(directory, name), text) }
        if (problems.isNotEmpty()) {
            fail(
                problems.joinToString("\n") + "\nTo write them again: " +
                    "./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors. Read the diff before you commit it.",
            )
        }
    }

    private fun problemWith(file: File, expected: String): String? {
        if (!file.exists()) return "${file.path} is missing."
        val actual = file.readText()
        if (actual == expected) return null
        val actualLines = actual.lines()
        val expectedLines = expected.lines()
        val line = (0 until maxOf(actualLines.size, expectedLines.size))
            .firstOrNull { actualLines.getOrNull(it) != expectedLines.getOrNull(it) }
            // The same lines, so they differ in how they end (CRLF for LF, say).
            ?: return "${file.path} has the lines the rules and fixtures produce, but ends them differently " +
                "(CRLF for LF, say): ${actual.length} characters where ${expected.length} are wanted."
        val found = actualLines.getOrNull(line)
        val wanted = expectedLines.getOrNull(line)
        val column = if (found == null || wanted == null) 0 else found.commonPrefixWith(wanted).length
        return "${file.path} is not what the rules and fixtures produce. First difference at line ${line + 1}:\n" +
            "    file:  ${excerpt(found, column)}\n" +
            "    rules: ${excerpt(wanted, column)}"
    }

    /** A line is a whole feed, so only the stretch around the first difference is shown. */
    private fun excerpt(line: String?, column: Int): String {
        if (line == null) return "(no such line)"
        val from = (column - 40).coerceAtLeast(0)
        return (if (from > 0) "..." else "") + line.substring(from, minOf(line.length, column + 80))
    }

    // ---- The rules' vectors

    /** One case: the feed it runs on by name, and a time. */
    private class Case(val name: String, val feed: String, val now: Long)

    /**
     * The cases are the ones that tell the rules apart: both sides of each boundary, each form of
     * the countdown, the nights, the turn of the day, the ends of the feed and the feeds the
     * engine makes awkward.
     */
    private fun rulesVectors(): String {
        val polar = Fixtures.capeTown(days = 3).let { feed ->
            feed.copy(days = feed.days.map { it.copy(times = it.times.copy(fajrEnd = null, asrEnd = null)) })
        }
        // Day 0 without Fajr's end and day 1 without Asr's: each day missing only one.
        val oneEndMissing = Fixtures.capeTown(days = 3).let { feed ->
            feed.copy(
                days = feed.days.mapIndexed { index, day ->
                    when (index) {
                        0 -> day.copy(times = day.times.copy(fajrEnd = null))
                        1 -> day.copy(times = day.times.copy(asrEnd = null))
                        else -> day
                    }
                },
            )
        }
        // Day 1's times ten minutes later, as Dhuhr drifts, and no midnight to say where the date begins.
        val unknownMidnights = Fixtures.withoutMidnights(
            Fixtures.capeTown(days = 3).let { feed ->
                feed.copy(days = feed.days.mapIndexed { index, day -> if (index == 1) Fixtures.shifted(day, 600) else day })
            },
        )
        // The clocks went back at the end of day 0: the next date begins 25 hours after it did, and
        // everything on the dates after it sits an hour later.
        val fallBack = Fixtures.capeTown(days = 3).let { feed ->
            feed.copy(days = feed.days.mapIndexed { index, day -> if (index == 0) day else Fixtures.shifted(day, 3_600) })
        }
        // Ruling 11, on a shape engine data never has: day 1's times moved two days back, so at
        // 23:00 on day 0 no start of either day is ahead. The phone would answer day 1's Fajr, a
        // time already gone; the watch answers no moment, and its surfaces their empty form.
        val tomorrowPassed = Fixtures.capeTown(days = 2).let { feed ->
            val earlier = Fixtures.shifted(feed.days[1], -2 * 86_400L).copy(midnight = feed.days[1].midnight)
            feed.copy(days = listOf(feed.days[0], earlier))
        }
        val feeds = linkedMapOf(
            "capetown-en-24" to Fixtures.capeTown(),
            "capetown-en-24-5d" to Fixtures.capeTown(days = 5),
            "capetown-en-24-from-yesterday" to Fixtures.capeTown(days = 4, firstDay = -1),
            "capetown-ar-ly" to Fixtures.capeTown(days = 3, ui = Fixtures.arabicLibya()),
            "capetown-ar-sa" to Fixtures.capeTown(days = 3, ui = Fixtures.arabicSaudi()),
            "capetown-bn" to Fixtures.capeTown(days = 1, ui = Fixtures.bengali()),
            "capetown-ur" to Fixtures.capeTown(days = 1, ui = Fixtures.urdu()),
            "capetown-no-ends" to polar,
            "capetown-one-end-missing" to oneEndMissing,
            "capetown-combined-maghrib-isha" to Fixtures.combinedMaghribIsha(),
            "capetown-odd-night" to Fixtures.oddNight(),
            "capetown-no-midnights" to unknownMidnights,
            "capetown-fall-back" to fallBack,
            "tromso-may" to Fixtures.tromsoMay(),
            "tromso-late-maghrib" to Fixtures.lateMaghribNight(),
            "capetown-tomorrow-passed" to tomorrowPassed,
        )

        val cases = listOf(
            Case("14:58: Asr is next in 1 h 12 min", "capetown-en-24", at(0, 14, 58)),
            Case("a second later the minute floors down", "capetown-en-24", at(0, 14, 58, 1)),
            Case("an hour left to the minute", "capetown-en-24", at(0, 15, 10)),
            Case("twelve minutes left", "capetown-en-24", at(0, 15, 58)),
            Case("59 seconds left", "capetown-en-24", at(0, 16, 9, 1)),
            Case("Asr has just begun", "capetown-en-24", at(0, 16, 10)),
            Case("before the feed's first start there is no moment", "capetown-en-24", at(0, 4, 10)),
            Case("Fajr has just begun, the feed's first start", "capetown-en-24", at(0, 4, 33)),
            Case("a second before sunrise, Fajr is lit on the list", "capetown-en-24", at(0, 6, 0, 59)),
            Case("sunrise ends Fajr on the list and not on the glance", "capetown-en-24", at(0, 6, 1)),
            Case("06:40, after sunrise", "capetown-en-24", at(0, 6, 40)),
            Case("a minute before sunset, Asr is lit", "capetown-en-24", at(0, 19, 1)),
            Case("sunset has ended Asr on the list, a minute before Maghrib", "capetown-en-24", at(0, 19, 3)),
            Case("Maghrib has just begun", "capetown-en-24", at(0, 19, 4)),
            Case("after Isha the period runs to tomorrow's Fajr", "capetown-en-24", at(0, 22, 30)),
            Case("the next morning, before Fajr, the period starts at yesterday's Isha", "capetown-en-24", at(1, 4, 10)),
            Case("a second before local midnight, Isha is lit", "capetown-en-24", at(0, 23, 59, 59)),
            Case("the instant of local midnight begins the next day, and nothing is lit", "capetown-en-24", at(1, 0, 0)),
            Case("00:10: the day has turned, and yesterday's Isha lights no row", "capetown-en-24", at(1, 0, 10)),
            Case("a Friday afternoon", "capetown-en-24", at(5, 14, 58)),
            Case("a second before the feed's last start", "capetown-en-24", at(29, 20, 23, 59)),
            Case("the feed's last start has arrived, and nothing is left to count to", "capetown-en-24", at(29, 20, 24)),
            Case("after the feed's last start", "capetown-en-24", at(29, 22, 0)),
            Case("Libya at 14:58: Arabic words, Western digits", "capetown-ar-ly", at(0, 14, 58)),
            Case("Libya, twelve minutes left", "capetown-ar-ly", at(0, 15, 58)),
            Case("Saudi Arabia at 14:58: Arabic words, Arabic-Indic digits", "capetown-ar-sa", at(0, 14, 58)),
            Case("Saudi Arabia, under a minute", "capetown-ar-sa", at(0, 16, 9, 1)),
            Case("Bengali digits at 14:58, with the English phrases", "capetown-bn", at(0, 14, 58)),
            Case("Urdu digits at 14:58, with the English phrases", "capetown-ur", at(0, 14, 58)),
            Case("the phone's feed begins at yesterday: at 04:10 the period starts at yesterday's Isha", "capetown-en-24-from-yesterday", at(0, 4, 10)),
            Case("the same feed just after midnight", "capetown-en-24-from-yesterday", at(0, 0, 10)),
            Case("without midnights the day turns over at solar midnight: 00:20 is still the evening's", "capetown-no-midnights", at(1, 0, 20)),
            Case("without midnights, halfway between the Dhuhrs: day 1's is ten minutes late, so 00:36 is still the evening's", "capetown-no-midnights", at(1, 0, 36)),
            Case("without midnights, 00:40 is the new day's", "capetown-no-midnights", at(1, 0, 40)),
            Case("a date of 25 hours: half past twenty-four hours on, it is still the first date", "capetown-fall-back", at(1, 0, 30)),
            Case("a date of 25 hours: the next date begins at its own midnight", "capetown-fall-back", at(1, 1, 0)),
            Case("a polar day without ends keeps Fajr lit after sunrise", "capetown-no-ends", at(0, 6, 40)),
            Case("a polar day without ends keeps Asr lit after sunset", "capetown-no-ends", at(0, 19, 3)),
            Case("Tromso 23:50: Maghrib at 00:13 is next", "tromso-may", atTromso(0, 23, 50)),
            Case("Tromso 00:20: Fajr is next, and the Isha at 00:41 is not a start", "tromso-may", atTromso(1, 0, 20)),
            Case("Tromso 00:40: after Fajr, the dropped Isha is not next, Dhuhr is", "tromso-may", atTromso(1, 0, 40)),
            Case("Tromso 00:50: the dropped Isha at 00:41 has begun, and is not the previous start", "tromso-may", atTromso(1, 0, 50)),
            Case("Tromso 01:30: after sunrise has ended Fajr", "tromso-may", atTromso(1, 1, 30)),
            Case("a late Maghrib (00:45 on the 19th, after the 19th's Fajr at 00:38): from Asr the phone counts to it", "tromso-late-maghrib", atTromso(1, 20, 0)),
            Case("the same, a second before midnight", "tromso-late-maghrib", atTromso(1, 23, 59, 59)),
            Case("the same, at midnight: the 19th has begun, the Maghrib is dropped and Fajr is next", "tromso-late-maghrib", atTromso(2, 0, 0)),
            Case("the same, at 00:50: the dropped Maghrib has begun, and the previous start is Fajr", "tromso-late-maghrib", atTromso(2, 0, 50)),
            Case("a day missing only Fajr's end keeps Fajr lit after sunrise", "capetown-one-end-missing", at(0, 6, 40)),
            Case("the same day still ends Asr at sunset", "capetown-one-end-missing", at(0, 19, 3)),
            Case("a day missing only Asr's end still ends Fajr at sunrise", "capetown-one-end-missing", at(1, 6, 40)),
            Case("the same day keeps Asr lit after sunset", "capetown-one-end-missing", at(1, 19, 3)),
            Case("a combined Maghrib and Isha (one instant): before it the next start is the Maghrib", "capetown-combined-maghrib-isha", at(1, 19, 0)),
            Case("the same, as it arrives: the list lights the Maghrib, the glance the Isha, and the previous start is the Isha", "capetown-combined-maghrib-isha", at(1, 19, 4)),
            Case("the same, later that evening", "capetown-combined-maghrib-isha", at(1, 22, 0)),
            Case("the guard: an Asr at 00:10 has begun, and yesterday's Isha at 00:20 is still ahead", "capetown-odd-night", at(1, 0, 15)),
            Case("the guard: yesterday's Isha began after today's latest prayer, so the list lights nothing and the glance the Asr", "capetown-odd-night", at(1, 0, 40)),
            Case("Ruling 11: no start of today or tomorrow is ahead, and the phone's answer would be a time already gone, so there is no moment", "capetown-tomorrow-passed", at(0, 23, 0)),
        )

        val countdowns = listOf("capetown-en-24", "capetown-ar-ly", "capetown-ar-sa", "capetown-bn", "capetown-ur").flatMap { feed ->
            listOf(0L, 59L, 60L, 119L, 120L, 719L, 720L, 3599L, 3600L, 3660L, 4319L, 4320L, 86_399L, 90_060L)
                .map { seconds ->
                    buildJsonObject {
                        put("feed", feed)
                        put("secondsLeft", seconds)
                        put("countdown", WatchRules.countdown(feeds.getValue(feed), seconds))
                    }.toString()
                }
        }

        val refreshes = listOf(
            Triple("written a moment ago", "capetown-en-24", at(0, 8, 0)),
            Triple("a minute before it was written", "capetown-en-24", at(0, 7, 59)),
            Triple("exactly a week old", "capetown-en-24", at(7, 8, 0)),
            Triple("a second over a week old", "capetown-en-24", at(7, 8, 0, 1)),
            Triple("the last start is a second over three days away", "capetown-en-24-5d", at(1, 20, 23, 59)),
            Triple("the last start is exactly three days away", "capetown-en-24-5d", at(1, 20, 24)),
            Triple("the last start is a second under three days away", "capetown-en-24-5d", at(1, 20, 24, 1)),
            Triple("the feed has run out", "capetown-en-24-5d", at(4, 21, 0)),
        ).map { (name, feed, now) ->
            buildJsonObject {
                put("name", name)
                put("feed", feed)
                put("now", now)
                put("needsRefresh", WatchRules.needsRefresh(feeds.getValue(feed), now))
            }.toString()
        }

        return buildString {
            appendLine("{")
            appendLine("  \"about\": ${JsonPrimitive(ABOUT)},")
            appendLine("  \"tolerance\": {\"progress\": 0.0001},")
            append(section("feeds", '{', '}', feeds.map { (name, feed) -> "${JsonPrimitive(name)}: ${WatchFeedJson.encode(feed)}" }))
            append(section("cases", '[', ']', cases.map { caseJson(it, feeds) }))
            append(section("countdowns", '[', ']', countdowns))
            append(section("refreshes", '[', ']', refreshes, last = true))
            appendLine("}")
        }
    }

    private fun caseJson(case: Case, feeds: Map<String, WatchFeed>): String {
        val feed = feeds.getValue(case.feed)
        val moment = WatchRules.moment(feed, case.now)
        return buildJsonObject {
            put("name", case.name)
            put("feed", case.feed)
            put("now", case.now)
            put("next", moment?.let { startJson(it.next) } ?: JsonNull)
            put("previous", moment?.let { startJson(it.previous) } ?: JsonNull)
            put("secondsLeft", moment?.secondsLeft)
            put("progress", moment?.progress)
            put("countdown", moment?.let { WatchRules.countdown(feed, it.secondsLeft) })
            put("litOnList", WatchRules.litOnList(feed, case.now)?.name)
            put("litOnGlance", WatchRules.litOnGlance(feed, case.now)?.name)
        }.toString()
    }

    private fun startJson(start: WatchStart): JsonObject = buildJsonObject {
        put("prayer", start.prayer.name)
        put("at", start.at)
        put("dayIndex", start.dayIndex)
    }

    /** `"key": [`, a line for each item, `],`: the file reads one case to a line. */
    private fun section(key: String, open: Char, close: Char, items: List<String>, last: Boolean = false): String =
        buildString {
            appendLine("  ${JsonPrimitive(key)}: $open")
            items.forEachIndexed { index, item -> appendLine("    $item" + if (index < items.lastIndex) "," else "") }
            appendLine("  $close" + if (last) "" else ",")
        }

    private companion object {
        val ABOUT = "Test vectors for the watch rules in WatchRules.kt, run by the Kotlin tests and by the Swift port. " +
            "A case runs on a feed (named in feeds) at a time now, in epoch seconds, and gives what each rule answers there: " +
            "next and previous as prayer, at and dayIndex; secondsLeft; progress, held to within tolerance.progress; " +
            "countdown; and the row lit on the Prayer screen's rule (litOnList) and on the widgets' (litOnGlance). " +
            "Where the rules have no moment, next, previous, secondsLeft, progress and countdown are null: " +
            "before the feed's first start, and at and after its last. " +
            "next and previous are the phone's own: for the day now is on, yesterday's starts that come before today's Fajr " +
            "and all five of today's, so that a start falling after the next day's Fajr is counted to until midnight and dropped once " +
            "the date has turned (tromso-late-maghrib); of two starts on one instant the earlier prayer is next and the later previous " +
            "(capetown-combined-maghrib-isha). " +
            "The row lit is today's. A day runs from its own midnight (the midnight field of the day, in epoch seconds) to the next day's, " +
            "and a day whose midnight is 0 begins at solar midnight instead, halfway between its Dhuhr and the day before's " +
            "(todayIndex in WatchRules.kt; capetown-no-midnights). " +
            "Numbers the watch writes use the nine digits that follow ui.digitZero (capetown-ar-sa, capetown-bn, capetown-ur). " +
            "countdowns are countdown on its own, and refreshes are needsRefresh. " +
            "VectorsTest.kt writes this file and checks it, and nobody edits it: change the rules, then rewrite it with " +
            "./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors and commit the result."
    }
}
