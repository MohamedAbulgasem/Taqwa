package world.taqwa.app.wear.tile

import androidx.compose.ui.unit.sp
import androidx.wear.protolayout.proto.AlignmentProto
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.wear.LongestCity
import world.taqwa.app.wear.TestFeeds
import world.taqwa.app.wear.app.EXTRA_PAGE
import world.taqwa.app.wear.app.WatchUnits
import world.taqwa.app.wear.complication.DynamicEvaluator
import world.taqwa.app.wear.today.RowStatus
import world.taqwa.app.wear.today.TodayLayout

/**
 * The Tile as ProtoLayout lays it out (`gTile`): the countdown line, the day's six rows (five where
 * the phone hides Sunrise) and the city, read back as the renderer is sent them ([LayoutWalk]).
 */
class TileLayoutTest {

    private val sample = TestFeeds.sample
    private val at1458 = TestFeeds.at(0, 14, 58)
    private val screen = TileScreen(widthDp = 227, heightDp = 227, density = 2f, fontScale = 1f, round = true, dynamic = true)

    private fun firstEntry(feed: WatchFeed = sample, now: Long = at1458, newer: Boolean = false) = TilePlan.entries(feed, newer, now).first()

    private fun walk(entry: TileEntry, rtl: Boolean = false, hostRtl: Boolean = false, on: TileScreen = screen, measure: TileMeasure = MEASURE) =
        LayoutWalk(TileLayout.entry(entry, on, rtl, hostRtl, PACKAGE, measure).toLayoutElementProto())

    /** The Texts' static words, as each is seen drawn ([visible]). */
    private fun words(walk: LayoutWalk) = walk.texts.map { visible(it.text!!.value) }

    /** A Text's size in the drawing's units, back through the screen's own density, as `WatchUnits.sp` made it. */
    private fun unitsOf(text: androidx.wear.protolayout.proto.LayoutElementProto.Text, on: TileScreen = screen): Float {
        val sp = text.fontStyle.getSize(0).value
        return with(androidx.compose.ui.unit.Density(on.density, on.fontScale)) { sp.sp.toDp().value } / on.units.dp
    }

    /**
     * The room between the countdown line's name and [word], in units, as the renderer draws the
     * line (ProtoLayoutInflater 1.4, `inflateText`): it lays the row's children side by side in its
     * host's direction, the first on the left unless [hostRtl]; a dynamic Text with a layout
     * constraint is a frame as wide as the constraint's words measure, and the words sit in it at
     * the constraint's alignment, its start and end resolved in the host's direction and centred
     * when it names none. So the room is the spacer between the two, and whatever of the frame lies
     * between the spacer and the words.
     */
    private fun gapAfterTheName(walk: LayoutWalk, word: String, hostRtl: Boolean, measure: TileMeasure, on: TileScreen = screen): Float {
        val row = walk.nodes.first { node -> node.kind == "row" && node.children.count { it.hasText() } == 2 && node.children.any { it.hasSpacer() } }
        val seen = if (hostRtl) row.children.reversed() else row.children
        val nameAt = seen.indexOfFirst { it.hasText() && !it.text.text.hasDynamicValue() }
        val wordsAt = seen.indexOfFirst { it.hasText() && it.text.text.hasDynamicValue() }
        assertEquals(2, abs(nameAt - wordsAt), "a spacer stands between the name and the countdown")
        val spacer = seen[(nameAt + wordsAt) / 2].also { assertTrue(it.hasSpacer()) }.spacer.width.linearDimension.value / on.units.dp
        val text = seen[wordsAt].text
        val size = unitsOf(text, on)
        val room = measure.width(text.text.valueForLayout, size, 700, tabular = true)
        val drawn = measure.width(word, size, 700, tabular = true)
        val alignment = text.text.textAlignmentForLayout
        val left = when (alignment) {
            AlignmentProto.TextAlignment.TEXT_ALIGN_START -> if (hostRtl) room - drawn else 0f
            AlignmentProto.TextAlignment.TEXT_ALIGN_END -> if (hostRtl) 0f else room - drawn
            else -> (room - drawn) / 2
        }
        // The words' distance from the frame's side that faces the name.
        val inside = if (nameAt < wordsAt) left else room - drawn - left
        return spacer + inside
    }

    @Test
    fun everyEntryIsWithinTheRenderersQuota() {
        // The spike (P3.8 Step 0): the emulator's Tile renderer gives a layout 200 dynamic nodes,
        // and over them it drops the dynamic value and keeps the static one, a countdown that never
        // moves. Each entry is held to it counting every node, its fixed values too, as Google
        // documents and a renderer may count them, and the binding itself as well (review round 1).
        val tromso = TestFeeds.phone("tromso-may-en")
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"), tromso)
        for (feed in feeds) {
            val now = if (feed === tromso) tromso.days[1].midnight + 3_600L else at1458
            for (entry in TilePlan.entries(feed, true, now)) {
                val cost = walk(entry, rtl = feed.ui.rtl).quotaCost
                assertTrue(cost <= TileLayout.QUOTA, "${feed.ui.language} from ${entry.from}: $cost dynamic nodes")
            }
        }
    }

    @Test
    fun everyEntryIsWithinTheRenderersQuotaWhenTheTileIsAskedFor() {
        // Review round 4: an entry's chains depend on where in the minute, and where between the
        // cuts, the Tile is asked for, which one request time samples once. Every moving entry of
        // the Tiles asked for every 47 s through a day, in six feeds, with the update, counted as
        // above; each entry the requests share is laid out once. The heaviest is 172 nodes (every
        // 53 s through two days, review round 4); six-minute entries were 204 with the start's
        // time past their chains.
        val tromso = TestFeeds.phone("tromso-may-en")
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"), tromso)
        for (feed in feeds) {
            val start = feed.days[1].midnight
            val seen = HashSet<TileEntry>()
            for (now in start until start + 86_400L step 47L) {
                for (entry in TilePlan.entries(feed, true, now)) {
                    if (entry.next?.minutes.isNullOrEmpty() || !seen.add(entry)) continue
                    val cost = walk(entry, rtl = feed.ui.rtl).quotaCost
                    assertTrue(cost <= TileLayout.QUOTA, "${feed.ui.language} asked at $now, the entry from ${entry.from}: $cost dynamic nodes")
                }
            }
        }
    }

    @Test
    fun theLineIsTheLabelAndTheMovingCountdown() {
        val walk = walk(firstEntry())
        val label = walk.texts[0]
        val countdown = walk.texts[1]

        assertEquals("ASR", assertNotNull(label.text).value)
        assertFalse(assertNotNull(label.text).hasDynamicValue())
        assertTrue(assertNotNull(countdown.text).hasDynamicValue())
        assertEquals("in 1h 12m", DynamicEvaluator(at1458).string(countdown.text.dynamicValue))
        val line = walk.nodes.first { it.description?.hasDynamicValue() == true }
        assertEquals("Asr in 1 hour 12 minutes, at 16:10", DynamicEvaluator(at1458).string(line.description!!.dynamicValue))
        // Ruling 65 item 2: what a renderer that drops them keeps is call 6's clock form, held as
        // wide as the widest word (aRendererThatLaysTheCountdownOutAsWideAsItsStaticValue...).
        assertEquals("16:10", visible(countdown.text.value), "its static value, the start's time")
        assertEquals("Asr, 16:10", line.description!!.value)
    }

    @Test
    fun aRendererThatDropsTheMovingValuesSaysTheStartsTime() {
        // Ruling 65 item 2: a renderer that ignores a dynamic value (over its quota, on an
        // evaluation error, or in the frame before it binds it) keeps its static value, so that is
        // call 6's clock form, true however late the renderer moves on: "ASR 16:10", said "Asr,
        // 16:10", never a countdown, which would stand still. Review round 4 found every static
        // value the entry's first countdown ("ASR in 2 min" with Dhuhr lit after Asr had begun).
        // Every entry of six feeds, asked for at three moments, read as such a renderer shows it at
        // its first second and ten minutes past its end, and each side's static values as sent.
        val tromso = TestFeeds.phone("tromso-may-en")
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"), tromso)
        for (feed in feeds) {
            val nows = if (feed === tromso) listOf(tromso.days[1].midnight + 3_600L, tromso.days[2].midnight + 20 * 3_600L) else listOf(at1458, TestFeeds.at(0, 16, 8, 30), TestFeeds.at(0, 18, 50))
            for (now in nows) {
                for (entry in TilePlan.entries(feed, false, now)) {
                    val root = TileLayout.entry(entry, screen, feed.ui.rtl, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
                    val next = entry.next ?: continue
                    for (s in listOf(entry.from, (entry.until ?: entry.from) + 600L)) {
                        val seen = Seen(root, s * 1_000L, binds = false)
                        assertEquals(setOf(next.label, next.clock), seen.texts.take(2).toSet(), "${feed.ui.language} asked at $now, the entry from ${entry.from}, at $s")
                        assertEquals(next.clockSentence, seen.said.first(), "${feed.ui.language} asked at $now, the entry from ${entry.from}, at $s")
                    }
                    val walk = LayoutWalk(root)
                    val sides = listOfNotNull(next, entry.after?.next).filter { it.minutes.isNotEmpty() }
                    assertEquals(sides.map { it.clock }, walk.texts.filter { it.text!!.hasDynamicValue() }.map { visible(it.text!!.value) }, "${feed.ui.language} from ${entry.from}")
                    assertEquals(sides.map { it.clockSentence }, walk.nodes.filter { it.description?.hasDynamicValue() == true }.map { it.description!!.value }, "${feed.ui.language} from ${entry.from}")
                }
            }
        }
    }

    @Test
    fun belowSchemaOneTwoTheLineIsTheLabelAndItsTime() {
        // Call 6: a renderer without dynamic values never shows a countdown it cannot move.
        val walk = walk(firstEntry(), on = screen.copy(dynamic = false))

        assertTrue(walk.dynamicStrings.isEmpty())
        assertEquals(listOf("ASR", "16:10"), words(walk).take(2))
        assertTrue(walk.nodes.any { it.description?.value == "Asr, 16:10" }, "the line says the prayer and its time")
    }

    @Test
    fun pastTheMovingHoursTheLineSaysTheStartsTime() {
        val later = TilePlan.entries(sample, false, at1458).first { it.next?.minutes?.isEmpty() == true }
        val next = assertNotNull(later.next)
        val walk = walk(later)

        assertTrue(walk.dynamicStrings.isEmpty())
        assertEquals(listOf(next.label, next.clock), words(walk).take(2))
        assertTrue(walk.nodes.any { it.description?.value == next.clockSentence })
    }

    @Test
    fun theRowsAreTheDaysSixTimesAndTheCityEndsTheTile() {
        val entry = firstEntry()
        val texts = words(walk(entry))

        assertEquals(
            listOf("ASR", "16:10", "Fajr", "4:33", "Sunrise", "6:01", "Dhuhr", "12:33", "Asr", "16:10", "Maghrib", "19:04", "Isha", "20:24", "Cape Town"),
            texts,
        )
    }

    @Test
    fun eachRowSaysItsTimeAndItsState() {
        val walk = walk(firstEntry())
        val rows = walk.nodes.filter { it.kind == "row" && it.description != null }

        assertEquals(
            listOf("Fajr, 4:33", "Sunrise, 6:01", "Dhuhr, 12:33", "Asr, 16:10", "Maghrib, 19:04", "Isha, 20:24"),
            rows.map { it.description!!.value },
        )
        assertEquals(listOf("Passed", "Passed", "Current prayer", "Next prayer", "", ""), rows.map { it.state?.value.orEmpty() })
    }

    @Test
    fun withoutSunriseTheTileDrawsAndSaysTheFivePrayers() {
        // The phone's Show sunrise off (the owner's decision of 9 November 2026): the five rows
        // share the six rows' room, the line and the city where they were, and a screen reader is
        // told the five rows the Tile shows.
        val entry = firstEntry(TestFeeds.withoutSunrise(sample))
        val walk = walk(entry)
        assertEquals(
            listOf("ASR", "16:10", "Fajr", "4:33", "Dhuhr", "12:33", "Asr", "16:10", "Maghrib", "19:04", "Isha", "20:24", "Cape Town"),
            words(walk),
        )
        val rows = walk.nodes.filter { it.kind == "row" && it.description != null }
        assertEquals(listOf("Fajr, 4:33", "Dhuhr, 12:33", "Asr, 16:10", "Maghrib, 19:04", "Isha, 20:24"), rows.map { it.description!!.value })
        assertEquals(listOf("Passed", "Current prayer", "Next prayer", "", ""), rows.map { it.state?.value.orEmpty() })

        val root = TileLayout.entry(entry, screen, rtl = false, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
        val baselines = LayoutBoxes(root, screen, MEASURE).texts.map { it.baseline }
        val expected = listOf(40f, 40f) + listOf(64f, 88.4f, 112.8f, 137.2f, 161.6f).flatMap { listOf(it, it) } + 184f
        assertEquals(expected.size, baselines.size)
        expected.zip(baselines).forEach { (want, got) -> assertEquals(want, got, 0.01f) }
    }

    @Test
    fun theRowsFollowThePhonesOrderOverTheHosts() {
        val entry = firstEntry(TestFeeds.other("ar-sa"))
        val fajr = entry.rows.first()
        fun firstRowKinds(rtl: Boolean, hostRtl: Boolean): List<String> {
            val walk = walk(entry, rtl, hostRtl)
            val row = walk.nodes.first { it.kind == "row" && it.description != null }
            return row.children.map { child ->
                val inside = LayoutWalk(child).texts.map { it.text!!.value }
                when {
                    child.hasSpacer() -> "space"
                    fajr.name in inside -> "name"
                    fajr.time in inside -> "time"
                    else -> "pip"
                }
            }
        }
        assertEquals(listOf("pip", "name", "time"), firstRowKinds(rtl = false, hostRtl = false).filter { it != "space" })
        assertEquals(listOf("time", "name", "pip"), firstRowKinds(rtl = true, hostRtl = false).filter { it != "space" })
        assertEquals(listOf("pip", "name", "time"), firstRowKinds(rtl = true, hostRtl = true).filter { it != "space" })
        assertEquals(listOf("time", "name", "pip"), firstRowKinds(rtl = false, hostRtl = true).filter { it != "space" })
    }

    @Test
    fun aTapOpensToday() {
        val root = TileLayout.entry(firstEntry(), screen, rtl = false, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
        val tap = LayoutWalk(root).nodes.first().modifiers!!.clickable.onClick.launchAction.androidActivity

        assertEquals(PACKAGE, tap.packageName)
        assertEquals("world.taqwa.app.wear.app.WearMainActivity", tap.className)
        assertEquals("TODAY", tap.keyToExtraMap.getValue(EXTRA_PAGE).stringVal.value)

        val empty = LayoutWalk(TileLayout.noFeed("Open Taqwa on your phone", screen, PACKAGE).toLayoutElementProto())
        assertEquals("TODAY", empty.nodes.first().modifiers!!.clickable.onClick.launchAction.androidActivity.keyToExtraMap.getValue(EXTRA_PAGE).stringVal.value)
    }

    @Test
    fun theUpdateTakesTheCitysPlaceAfterTheRows() {
        val texts = words(walk(firstEntry(newer = true)))

        // At the city's 10.5 units it is too wide for the chord at 184, so it takes two lines.
        assertEquals("Update Taqwa on your watch", texts.takeLast(2).joinToString(" "))
        assertFalse("Cape Town" in texts)
        assertEquals(16, texts.size, "the line and the rows are still drawn, the update in the city's place")
    }

    @Test
    fun theCityAndTheUpdateAreReadAfterTheRows() {
        // TalkBack reads what has its own speech, in order: the line, the six rows, then the city,
        // or the update whole in its place (Review Focus 2), never its two lines one by one.
        val said = walk(firstEntry()).nodes.mapNotNull { it.description?.value }
        assertEquals(8, said.size)
        assertEquals("Cape Town", said.last())

        val newer = walk(firstEntry(newer = true)).nodes.mapNotNull { it.description?.value }
        assertEquals("Update Taqwa on your watch", newer.last())
        assertEquals(said.take(7), newer.take(7))
    }

    @Test
    fun aTwelveHourFridayMovesTheTimesOutForJumuah() {
        // Today's rule (TodayLayout.timeEnd): the times end at 150 unless a row's name and time need
        // more room, when the whole column moves towards the round edge, as far as the narrowest row
        // allows. On twelve hours, "Jumuʿah" beside "12:33 PM" needs it; on 24 hours nothing does.
        val friday = firstEntry(TestFeeds.other("en12"), now = TestFeeds.at(5, 11, 0))
        val units = WatchUnits.of(227, 227)
        fun timeEnds(entry: TileEntry): List<Float> = walk(entry).nodes.filter { it.kind == "row" && it.description != null }
            .map { WatchUnits.ACROSS - it.modifiers!!.padding.end.value / units.dp }

        val moved = timeEnds(friday)
        assertTrue(moved.all { abs(it - moved.first()) < 1e-3f }, "one column: $moved")
        assertTrue(moved.first() > TodayLayout.TIME_END, "${moved.first()}")
        val rows = friday.rows
        val expected = TodayLayout.timeEnd(
            // Each name keeps MEASURE_SLACK to spare: on emulator-5558 "Jumuʿah", measured to fill
            // its box exactly, was drawn cut, "Jumuʿ…".
            nameWidths = rows.map { MEASURE.width(it.name, if (it.small) 10f else 12f, if (it.status == RowStatus.CURRENT) 800 else 600, tabular = false) + MEASURE_SLACK },
            timeWidths = rows.map { MEASURE.width(it.time, if (it.small) 10f else 12f, 600, tabular = true) },
            maxEnds = rows.indices.map { i -> TodayLayout.maxEnd(TodayLayout.list(rows.map { it.small }).baselines[i], rows[i].small, round = true) },
        )
        assertEquals(expected, moved.first(), 1e-3f)

        assertTrue(timeEnds(firstEntry()).all { abs(it - TodayLayout.TIME_END) < 1e-3f }, "24 hours keep 150")
    }

    @Test
    fun theTileIsTheDrawingsTwoHundredUnitsTallWithTheUpdateToo() {
        // The Tile is a stack the drawing's height, centred on the screen: the update in the city's
        // place moves no row.
        val units = WatchUnits.of(227, 227)
        for (feed in listOf(sample, TestFeeds.other("ar-sa"))) {
            val baselines = listOf(false, true).map { newer ->
                val root = TileLayout.entry(firstEntry(feed, newer = newer), screen, feed.ui.rtl, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
                val stack = root.box.contentsList.single().box
                assertEquals(units.x(WatchUnits.ACROSS).value, stack.height.linearDimension.value, 0.01f, "with the update: $newer")
                LayoutBoxes(root, screen, MEASURE).texts.take(14).map { it.baseline }
            }
            assertEquals(baselines[0], baselines[1], feed.ui.language)
        }
    }

    @Test
    fun noLineIsCutAndEachStandsOnTheDrawingsBaseline() {
        // Review round 1: a box sized for Roboto cut a line in Arabic script, whose Naskh line is
        // 1.70 of its size tall (Sunrise's, the update's, and Isha's above the update). Each line
        // now has a box its fonts' height, set so its baseline is the drawing's, and the boxes may
        // reach into each other's room. Over the samples in four languages and digit sets, the
        // phone's Urdu, at 14:58 and at 06:40, with and without the update, and at the Tile's
        // largest text: every line's whole height is inside every box it is drawn in, on its row's
        // baseline (the line's at 40, the city's at 184, the update's where it was fitted).
        // And without Sunrise, the five rows sharing its room, in English and in Arabic script.
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24")) +
            listOf(sample, TestFeeds.phone("capetown-ur24")).map(TestFeeds::withoutSunrise)
        for (feed in feeds) {
            for (now in listOf(at1458, TestFeeds.at(0, 6, 40))) {
                for (newer in listOf(false, true)) {
                    for (scale in listOf(1f, 1.1f)) {
                        val on = screen.copy(fontScale = scale)
                        val entry = firstEntry(feed, now = now, newer = newer)
                        val root = TileLayout.entry(entry, on, feed.ui.rtl, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
                        val placed = LayoutBoxes(root, on, MEASURE).texts
                        val geometry = TodayLayout.list(entry.rows.map { it.small })
                        val last = if (newer) {
                            fitUpdate(
                                entry.update!!,
                                TileType.update.range(scale),
                                round = true,
                                width = { t, u -> MEASURE.width(t, u, 600, tabular = false) },
                                extent = { t, u -> MEASURE.extent(t, u, 600) },
                            ).baselines
                        } else {
                            listOf(geometry.city)
                        }
                        val expected = listOf(40f, 40f) + geometry.baselines.flatMap { listOf(it, it) } + last
                        val where = "${feed.ui.language} at $now, update $newer, font $scale"
                        assertEquals(expected.size, placed.size, where)
                        for ((i, text) in placed.withIndex()) {
                            assertFalse(text.cut, "$where: '${text.text}' runs from ${text.top} to ${text.bottom}, its room ${text.roomTop} to ${text.roomBottom}")
                            assertEquals(expected[i], text.baseline, 0.01f, "$where: '${text.text}'")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aScreenReaderTakesTheLineTheRowsThenTheCity() {
        // Android hands a screen reader a view group's children sorted by place (ViewGroup's
        // ChildListForAccessibility): where they overlap, by their left edges in a left-to-right
        // host, then by their tops, and the taller first on a tie. The stack's boxes that all began
        // at its top were taken tallest first, the city before the line, and a narrower box after
        // the others (TalkBack on emulator-5558, review fix round 1). Each box now runs across the
        // screen from its line to the stack's bottom, so their tops follow the drawing's order: the
        // line, the six rows (five without Sunrise), the city or the update.
        val units = WatchUnits.of(227, 227)
        for (feed in listOf(sample, TestFeeds.other("ar-sa"), TestFeeds.phone("capetown-ur24"), TestFeeds.withoutSunrise(TestFeeds.other("ar-sa")))) {
            for (newer in listOf(false, true)) {
                for (scale in listOf(1f, 1.1f)) {
                    val on = screen.copy(fontScale = scale)
                    val root = TileLayout.entry(firstEntry(feed, newer = newer), on, feed.ui.rtl, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
                    val stack = root.box.contentsList.single().box
                    assertEquals(androidx.wear.protolayout.proto.AlignmentProto.VerticalAlignment.VERTICAL_ALIGN_BOTTOM, stack.verticalAlignment.value)
                    assertTrue(stack.contentsList.all { it.box.width.linearDimension.value == units.x(WatchUnits.ACROSS).value }, "every box across the screen")
                    val tops = stack.contentsList.map { WatchUnits.ACROSS - it.box.height.linearDimension.value / units.dp }
                    val rows = firstEntry(feed, newer = newer).rows
                    assertEquals(if (feed.ui.showSunrise) 6 else 5, rows.size)
                    assertEquals(rows.size + 2, tops.size)
                    assertTrue(tops.zipWithNext().all { (a, b) -> a < b }, "${feed.ui.language}, update $newer, font $scale: $tops")
                    val said = stack.contentsList.map { LayoutWalk(it).nodes.mapNotNull { n -> n.description?.value }.first() }
                    assertEquals(rows.map { it.spoken }, said.subList(1, rows.size + 1))
                }
            }
        }
    }

    @Test
    fun theNoticeIsShownOnceTheLastStartHasCome() {
        // Review round 1: the last entry had a start alone, so an end of 0, and the renderer shows
        // an entry only while start <= now < end: past the last start it kept the entry closest in
        // time, the last real one, which still counted down to a prayer that had begun. Read as the
        // renderer reads it, each entry is what is shown through its own time, and the notice from
        // its start on, a week later too.
        val week = 7 * 86_400_000L
        for ((feed, now) in listOf(sample to at1458, TestFeeds.other("en12") to at1458, sample to TestFeeds.at(29, 21, 0))) {
            val entries = TilePlan.entries(feed, false, now)
            val timeline = TileLayout.timeline(entries, screen, feed.ui.rtl, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toProto()
            for ((i, entry) in entries.withIndex()) {
                val from = entry.from * 1_000L
                assertEquals(i, shownAt(timeline, from), "${feed.ui.language}: entry $i at its start")
                val until = entry.until
                if (until != null) {
                    assertEquals(i, shownAt(timeline, until * 1_000L - 1), "${feed.ui.language}: entry $i at its last millisecond")
                } else {
                    assertEquals(i, shownAt(timeline, from + week), "${feed.ui.language}: the notice a week on")
                }
            }
            assertEquals(entries.lastIndex, shownAt(timeline, entries.last().from * 1_000L + 15_000L))
        }
        // The case seen on emulator-5558: an en12 Tile asked for at 14:58 on Sunday 18 October,
        // whose notice begins at Dhuhr on Tuesday 20 October, at 12:33:15 and on Friday at 11:00.
        val en12 = TilePlan.entries(TestFeeds.other("en12"), false, at1458)
        val timeline = TileLayout.timeline(en12, screen, rtl = false, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toProto()
        assertEquals(TestFeeds.at(2, 12, 33), en12.last().from)
        assertEquals(en12.lastIndex, shownAt(timeline, TestFeeds.at(2, 12, 33, 15) * 1_000L))
        assertEquals(en12.lastIndex, shownAt(timeline, TestFeeds.at(5, 11, 0) * 1_000L))
    }

    @Test
    fun theCountdownLineFitsInEachOfTheSevenLanguages() {
        // Review round 1: the label and the countdown stood unfitted in the chord at 40, 131.6 units
        // at a font scale of 1.0, and "MAGHRIB in under a minute" is about 158 of Roboto's units,
        // French's "JOUMOUʿA dans 1 h 32 min" 156, Indonesian's "... kurang dari semenit" 172. Each
        // language's longest label beside its longest countdown and its under-a-minute, in every
        // digit set, in the full forms (an older phone's) and in the Tile's compact ones, with its
        // room the start's time held as wide as them and a no-break space more (Mohamed's report of
        // 9 November 2026), in the emulator's Roboto, at Wear OS's font sizes and past the line's
        // cap of 1.1, as far as any watch's largest: the line fits the chord at the largest size
        // that does, never below its floor, and never past the round edge.
        for (language in PhoneWords.languages) {
            for (words in listOf(language.countdowns, language.compactCountdowns)) {
                for (scale in SCALES) {
                    val label = language.labels.maxBy { ROBOTO.width(it, 10.5f, 800, tabular = false) }
                    val fit = fitLine(label, words + language.clock, scale, round = true, measure = ROBOTO, clock = language.clock, rtl = language.rtl)
                    assertTrue(fit.fits, "${language.tag} at $scale: '$label' and '${fit.widest}' do not fit at ${fit.countdownSize}")
                    assertTrue(fit.countdownSize >= TileType.countdown.floor && fit.labelSize >= TileType.label.floor, "${language.tag} at $scale: $fit")
                    assertEquals(fit.labelSize / fit.countdownSize, TileType.label.units / TileType.countdown.units, 1e-4f, "the label shrinks with the countdown")
                }
            }
        }
    }

    @Test
    fun aRendererThatLaysTheCountdownOutAsWideAsItsStaticValueDrawsEveryWordWhole() {
        // Mohamed's report of 9 November 2026: his Galaxy Watch8 Classic drew the line "DHUHR in
        // 6 ...". Measured on the photo, its renderer gave the countdown a box as wide as the
        // static value, the start's time "12:35", and drew the moving words into it, where
        // Google's makes the box as wide as the layout constraint (ProtoLayoutInflater's
        // inflateText), which the emulator showed whole. So the static value is the start's time
        // held wider than the widest word the entry can show by MEASURE_SLACK, led by a mark of
        // the feed's direction and followed by spaces alone, and the constraint is it and a
        // no-break space more: in either box every word is whole, and in Google's the static value
        // too. Held only as wide as the widest word, a review's probe on the Wear OS 6 emulator
        // found the held value a pixel or two narrower than the word at the size drawn, where the
        // paint hints each advance to a whole pixel. Every entry of four samples and the phone's
        // Urdu asked for at three moments, and what the one before each change draws after it, at
        // every font size, in Roboto's widths on the test's 454-pixel screen and as hinted on
        // Mohamed's 438.
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"))
        val galaxy = screen.copy(widthDp = 219, heightDp = 219)
        for ((base, measure) in listOf(screen to ROBOTO, galaxy to TestMeasures.hinted(galaxy.pixelsPerUnit))) {
            for (feed in feeds) {
                for (now in listOf(at1458, TestFeeds.at(0, 16, 5), TestFeeds.at(0, 18, 50))) {
                    for (entry in TilePlan.entries(feed, false, now).flatMap { listOfNotNull(it, it.after) }) {
                        val next = entry.next ?: continue
                        if (next.minutes.isEmpty()) continue
                        for (scale in SCALES) {
                            val on = base.copy(fontScale = scale)
                            val countdown = walk(entry.copy(after = null), rtl = feed.ui.rtl, on = on, measure = measure).texts.first { it.text!!.hasDynamicValue() }.element.text
                            val at = "${feed.ui.language} from ${entry.from} at $scale on ${on.widthDp * on.density} px"
                            val static = countdown.text.value
                            assertEquals(directed(next.clock, feed.ui.rtl), static.trimEnd { it == '\u2007' || it == NO_BREAK_SPACE }, "$at: the start's time and spaces alone")
                            assertEquals(static + NO_BREAK_SPACE, countdown.text.valueForLayout, at)
                            assertEquals(AlignmentProto.TextAlignment.TEXT_ALIGN_START, countdown.multilineAlignment.value, "$at: set against the name in its own box too")
                            val size = unitsOf(countdown, on)
                            fun width(text: String) = measure.width(text, size, 700, tabular = true)
                            for (word in next.minutes.map { it.text } + directed(next.clock, feed.ui.rtl)) {
                                assertTrue(width(word) + MEASURE_SLACK <= width(static) + 1e-3f, "$at: '$word' in the box of '$static', with MEASURE_SLACK to spare")
                            }
                            val widest = next.minutes.maxOf { width(it.text) }.coerceAtLeast(width(next.clock))
                            assertTrue(width(static) < widest + MEASURE_SLACK + width(NO_BREAK_SPACE.toString()), "$at: '$static' held no wider than it needs")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theLineIsCentredOnItsNameAndItsWidestWord() {
        // A review of the Tile's compact countdown (9 November 2026): the countdown's frame runs
        // past the widest word by what the held static value keeps beyond it and a no-break space,
        // and with the row centred on the frame every moving line stood 1 to 4 units off centre
        // towards the name, the widest word showing too (emulator-5584's shots). gTile centres the
        // name and the countdown. So a spacer as wide as that overrun stands on the name's outer
        // side, and the name and the widest word are centred in the line's box, which holds the
        // whole row. Every moving entry from 14:58 and from 18:50, in four samples and the phone's
        // Urdu, on both hosts, at font 1.0 and 1.3.
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"))
        for (feed in feeds) {
            for (now in listOf(at1458, TestFeeds.at(0, 18, 50))) {
                for (entry in TilePlan.entries(feed, false, now).flatMap { listOfNotNull(it, it.after) }) {
                    val next = entry.next ?: continue
                    if (next.minutes.isEmpty()) continue
                    for (hostRtl in listOf(false, true)) {
                        for (scale in listOf(1f, 1.3f)) {
                            val on = screen.copy(fontScale = scale)
                            val walk = walk(entry.copy(after = null), rtl = feed.ui.rtl, hostRtl = hostRtl, on = on, measure = ROBOTO)
                            val at = "${feed.ui.language} from ${entry.from}, host ${if (hostRtl) "rtl" else "ltr"}, at $scale"
                            val row = walk.nodes.first { node -> node.kind == "row" && node.children.count { it.hasText() } == 2 && node.children.any { it.hasSpacer() } }
                            val box = walk.nodes.first { node -> node.kind == "box" && row.element in node.children }
                            // The row's children left to right, each as wide as the renderer draws it.
                            val seen = if (hostRtl) row.children.reversed() else row.children
                            val countdown = seen.first { it.hasText() && it.text.text.hasDynamicValue() }.text
                            val size = unitsOf(countdown, on)
                            val widest = (next.minutes.map { it.text } + directed(next.clock, feed.ui.rtl)).maxOf { ROBOTO.width(it, size, 700, tabular = true) }
                            fun drawn(child: androidx.wear.protolayout.proto.LayoutElementProto.LayoutElement): Float = when {
                                child.hasSpacer() -> child.spacer.width.linearDimension.value / on.units.dp
                                child.text.text.hasDynamicValue() -> ROBOTO.width(child.text.text.valueForLayout, size, 700, tabular = true)
                                else -> {
                                    val label = child.text.text.value
                                    val labelSize = unitsOf(child.text, on)
                                    ROBOTO.width(label, labelSize, 800, tabular = false) + label.codePointCount(0, label.length) * child.text.fontStyle.letterSpacing.value * labelSize
                                }
                            }
                            val lefts = seen.runningFold(0f) { left, child -> left + drawn(child) }
                            val total = lefts.last()
                            val nameAt = seen.indexOfFirst { it.hasText() && !it.text.text.hasDynamicValue() }
                            val wordsAt = seen.indexOfFirst { it.hasText() && it.text.text.hasDynamicValue() }
                            // The widest word sits against the name in its frame (towardsTheName).
                            val wordLeft = if (wordsAt > nameAt) lefts[wordsAt] else lefts[wordsAt + 1] - widest
                            val inkLeft = minOf(lefts[nameAt], wordLeft)
                            val inkRight = maxOf(lefts[nameAt + 1], wordLeft + widest)
                            assertEquals(total / 2, (inkLeft + inkRight) / 2, 1e-3f, "$at: the name and '${next.minutes.maxBy { ROBOTO.width(it.text, size, 700, tabular = true) }.text}' centred")
                            assertTrue(total <= box.element.box.width.linearDimension.value / on.units.dp + 1e-3f, "$at: the line's box holds its row")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aLineWhoseWordsCannotFitWholeSaysTheStartsTime() {
        // Never an ellipsis: where even the floor cannot hold the words a line can show, the line
        // says the start's time and the sentence the prayer and its time (call 6), as past the
        // moving hours. Here a phone's compact forms far wider than any language's.
        val long = "in %1\$s hours and %2\$s minutes, if God wills, from where you stand now"
        val wordy = sample.copy(ui = sample.ui.copy(countdown = sample.ui.countdown.copy(compactHoursMinutes = long, compactHours = long, compactMinutes = long)))
        val entry = TilePlan.entries(wordy, false, at1458).first()
        val walk = walk(entry, measure = ROBOTO)

        assertTrue(entry.moves)
        assertTrue(walk.dynamicStrings.isEmpty(), "nothing moves on the line")
        assertEquals(listOf("ASR", "16:10"), words(walk).take(2))
        assertTrue(walk.nodes.any { it.description?.value == "Asr, 16:10" }, "the line says the prayer and its time")
    }

    @Test
    fun theDrawingsLineKeepsItsSize() {
        // At 14:58 the line is gTile's, "ASR in 1 h 12 min" at 10.5 and 12 units.
        val fit = fitLine("ASR", firstEntry().next!!.minutes.map { it.text }, 1f, round = true, measure = ROBOTO)
        assertEquals(12f, fit.countdownSize)
        assertEquals(10.5f, fit.labelSize)
        assertTrue(fit.fits)
    }

    @Test
    fun theMinutesBeforeMaghribsLastKeepTheDrawingsSizeAndGap() {
        // Review round 2: the ten minutes before Maghrib at 19:04 held "in under a minute" through
        // their chain's trailing margin, so the whole line was fitted to it and drawn smaller for
        // all ten, and "in 9 min" stood centred in that room, 21 to 25 units further from the name
        // than the drawing's 7.5. Only the last two minutes' entry says under a minute; since
        // review round 3 the entries before it, which the renderer may still show in the last
        // minute, say the start's time there. Every entry before it, asked for at 18:55 and at
        // 18:50, is gTile's line, 10.5 and 12 units, and every word it can show stands 7.5 units
        // from the name.
        for (now in listOf(TestFeeds.at(0, 18, 55), TestFeeds.at(0, 18, 50))) {
            val before = TilePlan.entries(sample, false, now).takeWhile { it.until!! <= TestFeeds.at(0, 19, 4) - TilePlan.LAST_MINUTE_SECONDS }
            assertTrue(before.size >= 2)
            for (entry in before) {
                val next = assertNotNull(entry.next)
                val walk = walk(entry, on = screen, measure = ROBOTO)
                val (label, countdown) = walk.texts.take(2).map { it.element.text }
                val words = next.minutes.map { it.text }

                assertEquals("MAGHRIB", label.text.value)
                assertTrue("in under a minute" !in words, "the entry from ${entry.from}")
                val widest = (words + next.clock).maxOf { ROBOTO.width(it, 12f, 700, tabular = true) }
                // The room is the start's time held MEASURE_SLACK wider than the widest word, and a no-break space more.
                val held = ROBOTO.width(countdown.text.value, 12f, 700, tabular = true)
                assertTrue(held >= widest + MEASURE_SLACK - 1e-3f && held < widest + MEASURE_SLACK + ROBOTO.width(NO_BREAK_SPACE.toString(), 12f, 700, tabular = true), "the static value of the entry from ${entry.from}")
                assertEquals(countdown.text.value + NO_BREAK_SPACE, countdown.text.valueForLayout, "the room of the entry from ${entry.from}")
                assertEquals(10.5f, unitsOf(label), 1e-3f)
                assertEquals(12f, unitsOf(countdown), 1e-3f)
                for (word in words) {
                    assertEquals(7.5f, gapAfterTheName(walk, word, hostRtl = false, measure = ROBOTO), 1e-3f, "'$word' from ${entry.from}")
                }
            }
        }
    }

    @Test
    fun theLastMinuteIsDrawnSmallerAndWholeInItsOwnEntry() {
        // The two minutes before Maghrib can say "in under a minute", the widest of their words:
        // that entry's room is it, and its line is fitted to it, smaller and whole. Its "in 1m"
        // still stands at the drawing's gap from the name, at the line's size.
        val entry = TilePlan.entries(sample, false, TestFeeds.at(0, 18, 55)).first { it.until == TestFeeds.at(0, 19, 4) }
        assertEquals(TestFeeds.at(0, 19, 2), entry.from)
        val next = assertNotNull(entry.next)
        val walk = walk(entry, on = screen, measure = ROBOTO)
        val (label, countdown) = walk.texts.take(2).map { it.element.text }

        val fit = fitLine("MAGHRIB", next.minutes.map { it.text } + next.clock, 1f, round = true, measure = ROBOTO, clock = next.clock, rtl = false)
        assertEquals("in under a minute", fit.widest)
        assertEquals(fit.staticValue, countdown.text.value)
        assertEquals(fit.room, countdown.text.valueForLayout)
        assertTrue(ROBOTO.width(fit.staticValue, fit.countdownSize, 700, tabular = true) >= ROBOTO.width("in under a minute", fit.countdownSize, 700, tabular = true) + MEASURE_SLACK - 1e-3f, "$fit")
        assertTrue(fit.fits && fit.countdownSize < 12f, "$fit")
        assertEquals(fit.countdownSize, unitsOf(countdown), 1e-3f)
        assertEquals(LINE_GAP * fit.scale, gapAfterTheName(walk, "in 1m", hostRtl = false, measure = ROBOTO), 1e-3f)
        assertEquals(androidx.wear.protolayout.proto.LayoutElementProto.TextOverflow.TEXT_OVERFLOW_ELLIPSIZE, label.overflow.value)
        assertEquals(androidx.wear.protolayout.proto.LayoutElementProto.TextOverflow.TEXT_OVERFLOW_ELLIPSIZE, countdown.overflow.value)
    }

    @Test
    fun everyWordTheCountdownShowsStandsAtTheDrawingsGapFromTheName() {
        // Review round 2: the countdown's room is the widest of its entry's words, and a shorter
        // word stood centred in it, further from the name: "in 59 min" in the room of "in 1 h 10
        // min", about 10 units more. It is aligned towards the name, on the side the feed's
        // direction puts the name, in the host's own direction (the renderer resolves the
        // alignment's start and end in it, as it lays the row). Every moving entry from 14:58 and
        // from 18:50, in four samples and the phone's Urdu, on both hosts, at font 1.0 and 1.1.
        val feeds = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"))
        for (feed in feeds) {
            for (now in listOf(at1458, TestFeeds.at(0, 18, 50))) {
                // Each entry, and what the one before a change draws after it, laid out alone.
                for (entry in TilePlan.entries(feed, false, now).flatMap { listOfNotNull(it, it.after) }) {
                    val next = entry.next ?: continue
                    if (next.minutes.isEmpty()) continue
                    for (hostRtl in listOf(false, true)) {
                        for (scale in listOf(1f, 1.1f)) {
                            val on = screen.copy(fontScale = scale)
                            val walk = walk(entry.copy(after = null), rtl = feed.ui.rtl, hostRtl = hostRtl, on = on)
                            val countdown = walk.texts.first { it.text!!.hasDynamicValue() }.element.text
                            val gap = LINE_GAP * unitsOf(countdown, on) / TileType.countdown.range(scale).endInclusive
                            for (word in (next.minutes.map { it.text } + next.clock).distinct()) {
                                assertEquals(gap, gapAfterTheName(walk, word, hostRtl, MEASURE, on), 1e-3f, "${feed.ui.language} '$word' from ${entry.from}, host rtl $hostRtl, font $scale")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theEntryBeforeAChangeDrawsBothSidesOfItEachOnItsOwnSide() {
        // Review round 3: the renderer changes entries late, so the entry before Asr at 16:10
        // draws Dhuhr's rows and "ASR in under a minute" until 16:10, and Asr's rows and Maghrib's
        // countdown from 16:10, by the platform's clock, each in a box hidden on the other side.
        val entry = TilePlan.entries(sample, false, at1458).first { it.until == TestFeeds.at(0, 16, 10) }
        val root = TileLayout.entry(entry, screen, rtl = false, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
        val walk = LayoutWalk(root)
        assertEquals(2, walk.dynamicBools.size)
        val sides = walk.nodes.filter { it.modifiers?.hasVisible() == true }
        assertEquals(listOf(true, false), sides.map { it.modifiers!!.visible.value }, "a renderer that cannot bind them shows what comes before")

        val before = Seen(root, TestFeeds.at(0, 16, 9, 59) * 1_000L + 999)
        assertEquals(listOf("ASR", "in under a minute"), before.texts.take(2))
        assertEquals(listOf("Passed", "Passed", "Current prayer", "Next prayer"), before.states)
        val after = Seen(root, TestFeeds.at(0, 16, 10) * 1_000L)
        assertEquals(listOf("MAGHRIB", "in 2h 54m"), after.texts.take(2))
        assertEquals("Maghrib in 2 hours 54 minutes, at 19:04", after.said.first())
        assertEquals(listOf("Passed", "Passed", "Passed", "Current prayer", "Next prayer"), after.states)
        // Each side is the whole Tile, read in the drawing's order: the line, the rows, the city.
        assertEquals(listOf("Asr in under a minute, at 16:10") + entry.rows.map { it.spoken } + "Cape Town", before.said)
        assertEquals(entry.after!!.rows.map { it.spoken } + "Cape Town", after.said.drop(1))
        // The conditions are charged to the quota as well: seven nodes each, with the binding.
        assertEquals(walk.dynamicStrings.sumOf { DynamicNodes.of(it).all + 1 } + 14, walk.quotaCost)
        assertTrue(walk.quotaCost <= TileLayout.QUOTA)
    }

    @Test
    fun belowSchemaOneThreeTheEntryBeforeAChangeDrawsWhatComesBefore() {
        // A renderer before schema 1.3 cannot hide an element, and would draw both sides on top
        // of each other: it is sent what comes before the change alone, whose line says the
        // start's time once it has come.
        val entry = TilePlan.entries(sample, false, at1458).first { it.until == TestFeeds.at(0, 16, 10) }
        val root = TileLayout.entry(entry, screen.copy(hides = false), rtl = false, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toLayoutElementProto()
        val walk = LayoutWalk(root)

        assertTrue(walk.dynamicBools.isEmpty() && walk.nodes.none { it.modifiers?.hasVisible() == true })
        assertEquals(listOf("ASR", "16:10"), Seen(root, TestFeeds.at(0, 16, 10, 30) * 1_000L, hides = false).texts.take(2))
        assertEquals(listOf("ASR", "16:10"), words(walk).take(2), "its static words, the clock form (Ruling 65 item 2)")
    }

    @Test
    fun aRendererFromSchemaOneThreeCanHideAnElement() {
        fun on(major: Int, minor: Int) = TileScreen.of(
            androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters.Builder()
                .setScreenWidthDp(227).setScreenHeightDp(227).setScreenDensity(2f).setFontScale(1f)
                .setScreenShape(androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
                .setRendererSchemaVersion(androidx.wear.protolayout.expression.VersionBuilders.VersionInfo.Builder().setMajor(major).setMinor(minor).build())
                .build(),
        )
        assertEquals(false to false, on(1, 100).let { it.dynamic to it.hides })
        assertEquals(true to false, on(1, 200).let { it.dynamic to it.hides })
        assertEquals(true to true, on(1, 300).let { it.dynamic to it.hides })
        assertEquals(true to true, on(1, 500).let { it.dynamic to it.hides })
    }

    @Test
    fun theCountdownsRoomIsTheWidestOfItsWordsNotTheLongest() {
        // The room is the widest of the words as the watch measures them, not the one of the most
        // characters: here a word of seven wide letters beside one of thirteen.
        val words = listOf("in 1 h 11 min", "WWWWWWW")
        val fit = fitLine("ASR", words, 1f, round = true, measure = ROBOTO)
        assertEquals("WWWWWWW", fit.widest)
        assertTrue(ROBOTO.width("WWWWWWW", 12f, 700, tabular = true) > ROBOTO.width("in 1 h 11 min", 12f, 700, tabular = true))
    }

    @Test
    fun theNoticeIsDrawnAlone() {
        val spent = TestFeeds.at(29, 21, 0)
        assertEquals(listOf("Open Taqwa on your phone"), words(walk(TilePlan.entries(sample, false, spent).single())))
        assertEquals(listOf("Open Taqwa on your phone"), words(LayoutWalk(TileLayout.noFeed("Open Taqwa on your phone", screen, PACKAGE).toLayoutElementProto())))
    }

    @Test
    fun theLinesAreDrawnAtTheirUnitsInTheRequestsDensity() {
        val walk = walk(firstEntry(), on = screen.copy(fontScale = 1.1f))
        val units = WatchUnits.of(227, 227)
        val density = androidx.compose.ui.unit.Density(2f, 1.1f)
        val fajr = walk.texts.first { it.text!!.value == "Fajr" }

        assertEquals(units.sp(TileType.rowName.range(1.1f).endInclusive, density).value, fajr.element.text.fontStyle.getSize(0).value, 1e-3f)
    }

    @Test
    fun theTimelineIsSmallEnoughToSend() {
        // Each entry carries its whole layout, and the Tile goes to the renderer in one one-way
        // binder call, into the renderer's async space (half its binder buffer of about a
        // megabyte, shared by every one-way call in flight to it). The Tile as it is sent
        // (TileLayout.fitted), asked for every 17 minutes through a day and at 14:58 in four
        // samples and the phone's Urdu, and through each of Tromsø's nights around midnight in
        // May, where five changes fall within the hour, in each of the phone's seven languages on
        // both its clocks (PhoneFeeds), with the longest city a feed may carry or the update in its
        // place. There the plan's whole moving part came to 225 KB, in Bengali on 12 hours with
        // the update (review round 5), and 242 KB with that city; the Tile that is sent ends it
        // sooner, but never sooner than 45 minutes, so the system is never asked back more often
        // than every 25 (TilePlan.freshnessMillis).
        val samples = listOf(sample, TestFeeds.other("en12"), TestFeeds.other("ar-sa"), TestFeeds.other("ar-ly12"), TestFeeds.phone("capetown-ur24"))
        for (feed in samples) {
            val start = feed.days[1].midnight
            for (now in (start until start + 86_400L step 17 * 60L) + at1458) {
                for (newer in listOf(false, true)) assertSmallEnough(feed, newer, now)
            }
        }
        for (feed in farNorth) {
            for (now in tromsoNights) {
                for (newer in listOf(false, true)) {
                    val sent = assertSmallEnough(feed, newer, now)
                    val moving = sent.entries.first { !it.moves }.from - now
                    assertTrue(moving >= 45 * 60L, "${feed.ui.language} at $now, newer $newer: it counts down for $moving s")
                    assertTrue(TilePlan.freshnessMillis(sent.entries, now) >= 25 * 60_000L)
                }
            }
        }
    }

    @Test
    fun theMovingPartEndsAtTheLatestOfItsEntriesAtWhichTheTimelineFits() {
        // Tromsø's heaviest night, 16 May, in Bengali on 12 hours with the longest city, asked for
        // every 53 s around midnight: where the plan's whole moving part is too heavy to send, it
        // ends where one of its entries began, the latest at which the timeline fits, and the
        // entries from there say the start's time (call 6). Where it fits, it is sent whole.
        val feed = PhoneFeeds.localised(tromso, PhoneFeeds.Phone("bn-BD", twelveHour = true), city = WORST_CITY)
        val night = feed.days.first { it.date == "2026-05-16" }.midnight + 23 * 3_600L
        var ended = 0
        for (now in night until night + 2 * 3_600L step 53L) {
            for (newer in listOf(false, true)) {
                val whole = TilePlan.entries(feed, newer, now)
                val sent = TileLayout.fitted(feed, newer, now, screen, hostRtl = false, packageName = PACKAGE, measure = MEASURE)
                if (bytes(feed, whole) <= TileLayout.MAX_TIMELINE_BYTES) {
                    assertEquals(whole, sent.entries, "at $now, newer $newer")
                    continue
                }
                ended++
                val end = sent.entries.first { !it.moves }.from
                val at = whole.indexOfFirst { it.from == end }
                assertTrue(at >= 0 && whole[at].moves, "at $now, newer $newer: the moving part ends where one of its entries began")
                assertEquals(TilePlan.entries(feed, newer, now, movingUntil = end), sent.entries, "at $now, newer $newer")
                assertTrue(bytes(feed, sent.entries) <= TileLayout.MAX_TIMELINE_BYTES, "at $now, newer $newer")
                val further = TilePlan.entries(feed, newer, now, movingUntil = whole[at + 1].from)
                assertTrue(bytes(feed, further) > TileLayout.MAX_TIMELINE_BYTES, "at $now, newer $newer: an entry more would fit")
            }
        }
        assertTrue(ended > 0, "the night is heavy enough to end the moving part early")
    }

    @Test
    fun theMovingPartIsSentWholeWhereverItFits() {
        // Cape Town in every language on both clocks with the longest city the bundle names,
        // through a day: the moving part is the plan's whole 80 minutes.
        val capeTown = TestFeeds.phone("capetown-en")
        val city = LongestCity.name()
        for (phone in PhoneFeeds.ALL) {
            val feed = PhoneFeeds.localised(capeTown, phone, city = city)
            val start = feed.days[1].midnight
            for (now in start until start + 86_400L step 23 * 60L) {
                for (newer in listOf(false, true)) {
                    assertEquals(TilePlan.entries(feed, newer, now), TileLayout.fitted(feed, newer, now, screen, hostRtl = false, packageName = PACKAGE, measure = MEASURE).entries, "$phone at $now, newer $newer")
                }
            }
        }
    }

    @Test
    fun aCityNoPhoneWritesIsCutBeforeTheTimelinePassesItsBound() {
        // Ruling 68 item 2 (b): a city of 6,000 bytes (2,000 Bengali letters, twenty times the
        // phone's worst) makes the sample's timeline far heavier than the bound even with nothing
        // moving (308,081 B at 14:58, review round 6), and one of 12,000 more so. As a last resort
        // the city is cut, by half its letters at a time and marked with an ellipsis, to the
        // longest of those that fits, and the moving part is fitted again for it; where the update
        // takes the city's place the city weighs nothing and is never cut. The phone's own cities
        // are never cut (assertSmallEnough).
        for (letters in listOf(2_000, 4_000)) {
            val city = buildString { repeat(letters) { append(Char(0x0995 + it % 20)) } }
            val feed = sample.copy(place = sample.place.copy(city = city))
            val halves = generateSequence(city) { it.take(it.length / 2) }.takeWhile { it.isNotEmpty() }.toList()
            val cuts = listOf(city) + halves.drop(1).map { "$it…" }
            val start = feed.days[1].midnight
            for (now in (start until start + 86_400L step 6 * 3_600L) + at1458) {
                for (newer in listOf(false, true)) {
                    val sent = assertSmallEnough(feed, newer, now, cityKept = newer)
                    val sentCity = sent.entries.first().city
                    assertTrue(sent.entries.all { it.city == sentCity }, "$letters letters at $now")
                    if (newer) {
                        assertEquals(city, sentCity, "the update in its place, at $now")
                        continue
                    }
                    val at = cuts.indexOf(sentCity)
                    assertTrue(at > 0, "$letters letters at $now: the city is one of its halvings, with an ellipsis")
                    val longer = feed.copy(place = feed.place.copy(city = cuts[at - 1]))
                    assertTrue(bytes(longer, TilePlan.entries(longer, newer, now, movingUntil = now)) > TileLayout.MAX_TIMELINE_BYTES, "$letters letters at $now: the halving before it does not fit even with nothing moving")
                    val cut = feed.copy(place = feed.place.copy(city = sentCity))
                    assertEquals(TileLayout.fitted(cut, newer, now, screen, hostRtl = false, packageName = PACKAGE, measure = MEASURE).entries, sent.entries, "$letters letters at $now")
                }
            }
        }
    }

    @Test
    fun theWeightIsThatOfTheProtobufSent() {
        // What fitted counts is what the timeline is sent as, byte for byte.
        for (feed in listOf(sample, farNorth.last())) {
            val now = if (feed === sample) at1458 else tromsoNights[tromsoNights.size / 2]
            for (newer in listOf(false, true)) {
                val sent = TileLayout.fitted(feed, newer, now, screen, hostRtl = false, packageName = PACKAGE, measure = MEASURE)
                assertEquals(sent.timeline.toProto().serializedSize, sent.bytes)
                assertEquals(bytes(feed, sent.entries), sent.bytes)
            }
        }
    }

    @Test
    fun theLongestCityIsLongerThanAnyTheBundleNames() {
        assertTrue(WORST_CITY.encodeToByteArray().size > LongestCity.name().encodeToByteArray().size)
    }

    /**
     * The Tile as it is sent for [feed] at [now], which weighs no more than the renderer may be
     * sent, with the feed's own city unless [cityKept] is false (the last resort's city).
     */
    private fun assertSmallEnough(feed: WatchFeed, newer: Boolean, now: Long, cityKept: Boolean = true): TileLayout.Fitted {
        val sent = TileLayout.fitted(feed, newer, now, screen, hostRtl = false, packageName = PACKAGE, measure = MEASURE)
        val bytes = sent.timeline.toProto().serializedSize
        val what = "${feed.ui.language}${if (feed.ui.twelveHour) " 12-hour" else ""} at $now, newer $newer"
        assertTrue(bytes <= TileLayout.MAX_TIMELINE_BYTES, "$what: $bytes bytes")
        if (cityKept) assertTrue(sent.entries.all { it.city == feed.place.city }, "$what: the city is sent whole")
        return sent
    }

    /** What [entries] weigh laid out as one timeline, as the protobuf it is sent as. */
    private fun bytes(feed: WatchFeed, entries: List<TileEntry>): Int =
        TileLayout.timeline(entries, screen, feed.ui.rtl, hostRtl = false, packageName = PACKAGE, measure = MEASURE).toProto().serializedSize

    private val tromso: WatchFeed by lazy { TestFeeds.phone("tromso-may-en") }

    /** Tromsø in May in each of the phone's seven languages on both its clocks, with the longest city. */
    private val farNorth: List<WatchFeed> by lazy { PhoneFeeds.ALL.map { PhoneFeeds.localised(tromso, it, city = WORST_CITY) } }

    /** Each of Tromsø's nights from 23:00 to 01:00, every [NIGHT_STEP] seconds. */
    private val tromsoNights: List<Long> by lazy {
        tromso.days.dropLast(1).flatMap { day -> (day.midnight + 23 * 3_600L until day.midnight + 25 * 3_600L step NIGHT_STEP).toList() }
    }

    private companion object {
        const val PACKAGE = "world.taqwa.app"

        /** The system font's widths, roughly: half an em and a twentieth for every character, with its lines' heights. */
        val MEASURE = TestMeasures.ROUGH

        /** The emulator's Roboto's own widths. */
        val ROBOTO = TestMeasures.ROBOTO

        /**
         * The font sizes the line is held at: a smaller one, Wear OS's own up to 1.24, and past the
         * line's cap of 1.1 as far as a watch's largest (a Galaxy's goes beyond Wear OS's).
         */
        val SCALES = listOf(0.9f, 1f, 1.06f, 1.1f, 1.12f, 1.18f, 1.24f, 1.3f, 2f)

        /** How often the size test asks for the Tile through Tromsø's nights: every 9 minutes 7 seconds. */
        const val NIGHT_STEP = 547L

        /**
         * The phone's size tests' worst city (`WorstFeeds.CITY` in :shared's commonTest): 100
         * Bengali letters, ক to ন, three UTF-8 bytes each, 300 bytes, longer than any the bundle
         * names ([LongestCity], 91 bytes on 24 October).
         */
        val WORST_CITY: String = buildString { repeat(100) { append(Char(0x0995 + it % 20)) } }
    }
}
