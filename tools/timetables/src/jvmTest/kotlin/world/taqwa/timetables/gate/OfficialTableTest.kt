package world.taqwa.timetables.gate

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import world.taqwa.timetables.gate.formats.DailyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class OfficialTableTest {

    @Test
    fun `the daily reader takes dates and times and counts what it cannot read and what repeats`() {
        // Invented rows (ruling R69: no printed time from a restricted table).
        val lines = """
            # source, fetch date
            2026-09-25 04:40 06:10 12:20 15:45 18:30 19:50  # file1

            2026-09-26 04:41 - 12:20 15:44 18:29 24:10 p2r1
            2026-09-27 04:41 06:11 12:20 15:43 18:28? 19:48
            2026-09-28 04:42 06:12 112:28 15:42 18:27 19:47
            2026-09-29 04:42 06:12 12:19
            2026-09-25 04:50 06:15 12:25 15:50 18:35 19:55
        """.trimIndent().lines()
        val table = OfficialTable.from(DailyFormat.read(lines, 6))
        assertEquals(listOf(LocalDate(2026, 9, 25), LocalDate(2026, 9, 26), LocalDate(2026, 9, 25)), table.days.map { it.date })
        assertEquals(listOf(4 * 60 + 40, 6 * 60 + 10, 12 * 60 + 20, 15 * 60 + 45, 18 * 60 + 30, 19 * 60 + 50), table.days[0].minutes)
        assertEquals(listOf(4 * 60 + 41, null, 12 * 60 + 20, 15 * 60 + 44, 18 * 60 + 29, 24 * 60 + 10), table.days[1].minutes)
        assertEquals(3, table.unreadable)
        assertEquals(1, table.duplicates)
    }

    @Test
    fun `a reader is found by its name`() {
        assertSame(DailyFormat, OfficialFormats.named("daily"))
        assertEquals("world.taqwa.timetables.gate.formats.TwelveHourFormat", OfficialFormats.className("twelve-hour"))
        val error = assertFailsWith<IllegalArgumentException> { OfficialFormats.named("twelve-hour") }
        assertTrue("object TwelveHourFormat : OfficialFormat" in error.message!!, error.message)
    }

    @Test
    fun `printed times become instants on the right day`() {
        val kazan = TimeZone.of("Europe/Moscow")
        val day = LocalDate(2026, 5, 5)
        fun at(text: String) = Instant.parse(text)
        assertEquals(at("2026-05-05T08:58:00Z"), Gate.officialInstant(day, 11 * 60 + 58, Event.DHUHR, kazan))
        // DUM RT's end of sahur on its 5 May 2026 row: the evening before.
        assertEquals(at("2026-05-04T20:54:00Z"), Gate.officialInstant(day, 23 * 60 + 54, Event.END_OF_EATING, kazan))
        assertEquals(at("2026-05-04T20:54:00Z"), Gate.officialInstant(day, 23 * 60 + 54, Event.FAJR, kazan))
        // An Isha after midnight, printed as 00:19 or as 24:19.
        assertEquals(at("2026-05-05T21:19:00Z"), Gate.officialInstant(day, 19, Event.ISHA, kazan))
        assertEquals(at("2026-05-05T21:19:00Z"), Gate.officialInstant(day, 24 * 60 + 19, Event.ISHA, kazan))
        assertEquals(at("2026-05-04T21:19:00Z"), Gate.officialInstant(day, 19, Event.FAJR, kazan))
    }

    @Test
    fun `printed times follow a zone's clock changes`() {
        val london = TimeZone.of("Europe/London")
        fun at(text: String) = Instant.parse(text)
        fun hm(h: Int, m: Int) = h * 60 + m
        // British Summer Time begins at 01:00 UTC on 29 March 2026 and ends at 01:00 UTC on 25 October.
        // Invented times, only the clock arithmetic matters.
        assertEquals(at("2026-03-28T04:50:00Z"), Gate.officialInstant(LocalDate(2026, 3, 28), hm(4, 50), Event.FAJR, london))
        assertEquals(at("2026-03-29T03:47:00Z"), Gate.officialInstant(LocalDate(2026, 3, 29), hm(4, 47), Event.FAJR, london))
        assertEquals(at("2026-03-29T05:45:00Z"), Gate.officialInstant(LocalDate(2026, 3, 29), hm(6, 45), Event.SUNRISE, london))
        assertEquals(at("2026-10-24T05:20:00Z"), Gate.officialInstant(LocalDate(2026, 10, 24), hm(6, 20), Event.FAJR, london))
        assertEquals(at("2026-10-25T05:25:00Z"), Gate.officialInstant(LocalDate(2026, 10, 25), hm(5, 25), Event.FAJR, london))
        // A summer Isha after midnight, on the local clock and on a table printed in GMT all year.
        val june = LocalDate(2026, 6, 20)
        assertEquals(at("2026-06-20T23:35:00Z"), Gate.officialInstant(june, hm(0, 35), Event.ISHA, london))
        val gmt = TimeZone.of("UTC")
        assertEquals(at("2026-06-20T23:50:00Z"), Gate.officialInstant(june, hm(23, 50), Event.ISHA, gmt, london))
        assertEquals(at("2026-06-20T01:05:00Z"), Gate.officialInstant(june, hm(1, 5), Event.FAJR, gmt, london))
        assertEquals(at("2026-06-20T20:15:00Z"), Gate.officialInstant(june, hm(20, 15), Event.MAGHRIB, gmt, london))
    }
}
