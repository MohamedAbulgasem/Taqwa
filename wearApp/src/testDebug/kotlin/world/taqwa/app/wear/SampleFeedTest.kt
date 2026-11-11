package world.taqwa.app.wear

import java.io.File
import java.util.TimeZone
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchFeedJson
import world.taqwa.app.watch.WatchPrayer
import world.taqwa.app.watch.WatchRules

/**
 * The debug build's sample feed: the committed Cape Town sample, moved by whole days so that its
 * Sunday 18 October 2026 is the date the build is wanted for.
 *
 * The figures are worked out by hand and not by the code under test: Johannesburg is UTC+2 all
 * year, so 00:00 on 5 November 2026 there is 22:00 UTC on the 4th, and the sample's own midnights
 * and times are those of 18 October 2026, 18 days earlier (18 * 86,400 = 1,555,200 seconds).
 */
class SampleFeedTest {

    private val sample = TestFeeds.sample
    private lateinit var directory: File
    private lateinit var storedZone: TimeZone

    @BeforeTest
    fun setUp() {
        directory = createTempDirectory("sample-feed").toFile()
        // The date is Johannesburg's whatever zone the machine runs in: here it is a zone half a
        // world away, so that a date taken from the machine's own zone is caught on any machine.
        storedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
    }

    @AfterTest
    fun tearDown() {
        TimeZone.setDefault(storedZone)
        directory.deleteRecursively()
    }

    @Test
    fun theSampleHoldsTheMockDayAsASecondDayAtItsOwnMidnight() {
        // What SampleFeed counts on: 31 days from yesterday, so that the mockups' Sunday 18 October
        // is the second, and every day begins at midnight in Johannesburg. Regenerating the sample
        // with other dates or another zone must fail here, not move the debug build's days by an
        // hour or a day without a word.
        assertEquals(31, sample.days.size)
        assertEquals("2026-10-17", sample.days[0].date)
        assertEquals("2026-10-18", sample.days[1].date)
        assertEquals(1_792_274_400L, sample.days[1].midnight, "00:00 on 18 October 2026 in Johannesburg")
        assertEquals(86_400L, sample.days[1].midnight - sample.days[0].midnight)
        assertEquals(1_792_303_200L, sample.writtenAt, "08:00 on the mock day")
    }

    @Test
    fun theMockDayLandsOnTheTargetDate() {
        // Thursday 5 November 2026, 14:58 in Johannesburg.
        val redated = SampleFeed.redate(sample, 1_793_883_480L)

        val mock = redated.days[1]
        assertEquals(1_793_829_600L, mock.midnight, "00:00 on 5 November")
        assertEquals(1_793_845_980L, mock.times.fajr, "04:33")
        assertEquals(1_793_887_800L, mock.times.asr, "16:10")
        assertEquals(1_793_898_120L, mock.times.asrEnd, "19:02, the ends move too")
        assertEquals(1_793_743_200L, redated.days[0].midnight, "the day before it begins a day earlier")
        assertEquals(1_796_335_200L, redated.days[30].midnight, "and the last is 29 days after it")
        assertEquals(1_792_303_200L + 1_555_200L, redated.writtenAt, "08:00 on 5 November: the feed is as old as it was")
    }

    @Test
    fun everyInstantOfEveryDayMovesByTheSameWholeDays() {
        val shift = 18 * 86_400L

        val redated = SampleFeed.redate(sample, 1_793_883_480L)

        val before = daysOf(sample)
        val after = daysOf(redated)
        assertEquals(before.size, after.size)
        before.zip(after).forEachIndexed { index, (was, now) ->
            val wasLeaves = leaves(was)
            val nowLeaves = leaves(now)
            assertEquals(wasLeaves.keys, nowLeaves.keys, "day $index has the same fields")
            wasLeaves.forEach { (field, wasValue) ->
                val nowValue = nowLeaves.getValue(field)
                if (wasValue is JsonNull || wasValue.isString) {
                    // The words are display text and stay as they are.
                    assertEquals(wasValue, nowValue, "day $index $field")
                } else {
                    assertEquals(wasValue.long + shift, nowValue.long, "day $index $field")
                }
            }
        }
    }

    @Test
    fun atFourteenFiftyEightTheNextPrayerIsAsrInAnHourAndTwelveMinutes() {
        val now = 1_793_883_480L
        val redated = SampleFeed.redate(sample, now)

        val moment = assertNotNull(WatchRules.moment(redated, now))

        assertEquals(WatchPrayer.ASR, moment.next.prayer)
        assertEquals(WatchPrayer.DHUHR, moment.previous.prayer)
        assertEquals(4_320L, moment.secondsLeft)
        assertEquals("in 1 h 12 min", WatchRules.countdown(redated, moment.secondsLeft))
    }

    @Test
    fun justAfterMidnightYesterdaysIshaIsStillThePreviousStart() {
        // 00:10 on 5 November. The date has turned only if the days' midnights moved with their
        // times; if they had not, the rules would not find the day.
        val now = 1_793_830_200L
        val redated = SampleFeed.redate(sample, now)

        val moment = assertNotNull(WatchRules.moment(redated, now))

        assertEquals(WatchPrayer.ISHA, moment.previous.prayer)
        assertEquals(0, moment.previous.dayIndex)
        assertEquals(WatchPrayer.FAJR, moment.next.prayer)
        assertEquals(1, moment.next.dayIndex)
    }

    @Test
    fun theDateIsJohannesburgsAndNeitherUtcsNorTheMachines() {
        fun midnightOfMockDayFor(epoch: Long) = SampleFeed.redate(sample, epoch).days[1].midnight

        assertEquals(1_793_743_200L, midnightOfMockDayFor(1_793_829_599L), "23:59:59 on 4 November there")
        assertEquals(1_793_829_600L, midnightOfMockDayFor(1_793_829_600L), "00:00 on 5 November there, 22:00 UTC on the 4th")
        assertEquals(1_793_829_600L, midnightOfMockDayFor(1_793_915_999L), "23:59:59 on 5 November there")
        assertEquals(1_793_916_000L, midnightOfMockDayFor(1_793_916_000L), "00:00 on 6 November there, 22:00 UTC on the 5th")
    }

    @Test
    fun aDateBeforeTheSampleMovesItBack() {
        // Saturday 10 October 2026, eight days before the mock day.
        val redated = SampleFeed.redate(sample, 1_791_630_000L)

        assertEquals(1_791_583_200L, redated.days[1].midnight)
        assertEquals(1_791_583_200L - 86_400L, redated.days[0].midnight)
    }

    @Test
    fun theMockDaysOwnDateLeavesTheSampleAsItIs() {
        // 14:58 on 18 October 2026, the sample's own Sunday.
        assertEquals(sample, SampleFeed.redate(sample, 1_792_274_400L + 14 * 3_600L + 58 * 60L))
    }

    @Test
    fun anUnknownMidnightStaysUnknown() {
        val unknown = sample.copy(days = sample.days.map { it.copy(midnight = 0L) })

        val redated = SampleFeed.redate(unknown, 1_793_883_480L)

        assertTrue(redated.days.all { it.midnight == 0L }, "0 says the phone could not tell; it is not a time to move")
        assertEquals(1_793_845_980L, redated.days[1].times.fajr, "the times move all the same")
    }

    @Test
    fun aSampleWithoutTheMockDayIsRefusedAndNotGuessedAt() {
        val shortened = sample.copy(days = sample.days.drop(2))

        assertFailsWith<IllegalStateException> { SampleFeed.redate(shortened, 1_793_883_480L) }
    }

    @Test
    fun theSampleIsInstalledWhereNothingIsStored() {
        val store = FeedStore(File(directory, "feed.json"))

        assertTrue(SampleFeed.installIfEmpty(directory, store, 1_793_883_480L) { TestFeeds.sampleJson })

        val installed = assertNotNull(store.read())
        assertEquals("Cape Town", installed.place.city)
        assertEquals(31, installed.days.size)
        assertEquals(1_793_829_600L, installed.days[1].midnight)
        assertEquals(installed, store.changes.value)
    }

    @Test
    fun aStoredFeedIsNeverReplacedAtStart() {
        val store = FeedStore(File(directory, "feed.json"))
        val real = TestFeeds.another(city = "Johannesburg")
        store.write(TestFeeds.json(real))

        val installed = SampleFeed.installIfEmpty(directory, store, 1_793_883_480L) { error("the sample is not even read") }

        assertFalse(installed)
        assertEquals(real, store.read())
    }

    @Test
    fun theBroadcastReplacesWhateverIsStored() {
        val store = FeedStore(File(directory, "feed.json"))
        store.write(TestFeeds.json(TestFeeds.another(city = "Johannesburg")))

        val result = SampleFeed.install(directory, store, 1_793_883_480L, TestFeeds.sampleJson)

        assertEquals(FeedWriteResult.ACCEPTED, result)
        val installed = assertNotNull(store.read())
        assertEquals("Cape Town", installed.place.city)
        assertEquals(1_793_829_600L, installed.days[1].midnight)
    }

    // ---- The hold: a cleared watch stays empty (DebugFeedReceiver's `--ez clear true`)

    @Test
    fun aHeldSampleIsNotInstalled() {
        val store = FeedStore(File(directory, "feed.json"))
        assertEquals("sample.held", SampleFeed.HOLD)
        assertFalse(SampleFeed.held(directory))

        SampleFeed.hold(directory)

        assertTrue(SampleFeed.held(directory))
        assertFalse(SampleFeed.installIfEmpty(directory, store, 1_793_883_480L) { error("a held sample is not even read") })
        assertNull(store.read(), "the store stays empty, so the process's next start finds it empty too")
        assertTrue(SampleFeed.held(directory), "and the hold stays")
    }

    @Test
    fun anAcceptedInstallLiftsTheHold() {
        val store = FeedStore(File(directory, "feed.json"))
        SampleFeed.hold(directory)

        assertEquals(FeedWriteResult.ACCEPTED, SampleFeed.install(directory, store, 1_793_883_480L, TestFeeds.sampleJson))

        assertFalse(SampleFeed.held(directory))
        assertEquals("Cape Town", assertNotNull(store.read()).place.city)
    }

    @Test
    fun aNewerOfferKeepsTheHold() {
        val store = FeedStore(File(directory, "feed.json"))
        SampleFeed.hold(directory)

        val result = SampleFeed.install(directory, store, 1_793_883_480L, TestFeeds.sampleJson, version = 2)

        assertEquals(FeedWriteResult.NEWER_VERSION, result)
        assertTrue(SampleFeed.held(directory), "the watch stays without a feed")
        assertNull(store.read())
        assertTrue(store.newerVersionSeen.value, "and says it must be updated")
    }

    @Test
    fun aNewerOfferOverAFeedKeepsTheFeed() {
        val store = FeedStore(File(directory, "feed.json"))
        SampleFeed.install(directory, store, 1_793_883_480L, TestFeeds.sampleJson)
        val held = assertNotNull(store.read())

        assertEquals(FeedWriteResult.NEWER_VERSION, SampleFeed.install(directory, store, 1_793_883_480L, TestFeeds.sampleJson, version = 2))

        assertEquals(held, store.read())
        assertTrue(store.newerVersionSeen.value)
    }

    @Test
    fun aCityOfOnesChoosingIsStoredWithTheSampleUnchangedBesideIt() {
        val store = FeedStore(File(directory, "feed.json"))
        val name = "Kota Kinabalu, Sabah, Malaysia and a little more"

        assertEquals(FeedWriteResult.ACCEPTED, SampleFeed.install(directory, store, 1_793_883_480L, TestFeeds.sampleJson, city = name))

        val installed = assertNotNull(store.read())
        assertEquals(name, installed.place.city)
        assertEquals(SampleFeed.redate(sample, 1_793_883_480L).copy(place = sample.place.copy(city = name)), installed)
    }

    @Test
    fun theOtherSamplesAreTheSameDaysAsOtherPhonesWriteThem() {
        // Each named sample is in the debug assets (copied from watchcore/vectors), and is the first
        // sample's days and instants in another language or clock, so that it moves to a date the
        // same way.
        assertEquals("sample-feed-capetown.json", SampleFeed.assetOf(""))
        assertNull(SampleFeed.assetOf("klingon"))
        val vectors = File(checkNotNull(System.getProperty("taqwa.sampleFeed")) { "Gradle hands the sample's path in" }).parentFile
        for (name in SampleFeed.NAMES) {
            val file = File(vectors, assertNotNull(SampleFeed.assetOf(name), name))
            val other = assertNotNull(WatchFeedJson.decode(file.readText()), name)
            assertEquals(sample.days.map { it.times }, other.days.map { it.times }, name)
            assertEquals(sample.days.map { it.midnight }, other.days.map { it.midnight }, name)
            assertEquals(sample.days.map { it.date }, other.days.map { it.date }, name)
        }
        val libya = assertNotNull(WatchFeedJson.decode(File(vectors, "sample-feed-capetown-ar-ly12.json").readText()))
        assertTrue(libya.ui.rtl)
        assertTrue(libya.ui.twelveHour)
        assertEquals("4:10", libya.days[1].clock.asr)
        assertEquals("6 جمادى الأولى", libya.days[1].hijriDayMonth)
        val saudi = assertNotNull(WatchFeedJson.decode(File(vectors, "sample-feed-capetown-ar-sa.json").readText()))
        assertEquals('٠', saudi.ui.digitZero)
        assertFalse(saudi.ui.twelveHour)
        assertEquals("١٦:١٠", saudi.days[1].clock.asr)
    }

    @Test
    fun theEpochExtraMayBeAnyWholeNumberTheShellCanSend() {
        assertEquals(1_793_883_480L, epochOf(1_793_883_480L), "am broadcast --el")
        assertEquals(1_793_883_480L, epochOf(1_793_883_480), "--ei")
        assertEquals(1_793_883_480L, epochOf("1793883480"), "--es")
        assertNull(epochOf("14:58"), "a time of day is not an epoch")
        assertNull(epochOf(1.5f), "nor is a float, which could not hold one")
        assertNull(epochOf(null), "no extra at all")
    }

    private fun daysOf(feed: WatchFeed): List<JsonElement> =
        Json.parseToJsonElement(WatchFeedJson.encode(feed)).jsonObject.getValue("days").jsonArray

    /** Every value in [element], each under the path to it: `/times/fajr`, `/clock/asr`, `/midnight`. */
    private fun leaves(element: JsonElement, path: String = ""): Map<String, JsonPrimitive> = when (element) {
        is JsonPrimitive -> mapOf(path to element)
        is JsonObject -> element.entries.fold(emptyMap()) { all, (key, value) -> all + leaves(value, "$path/$key") }
        is JsonArray -> element.withIndex().fold(emptyMap()) { all, (index, value) -> all + leaves(value, "$path[$index]") }
    }
}
