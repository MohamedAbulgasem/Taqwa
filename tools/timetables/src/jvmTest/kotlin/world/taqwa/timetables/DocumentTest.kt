package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DocumentTest {

    private fun city(
        slug: String,
        country: String,
        lat: Double,
        lon: Double,
        zone: String,
        names: Map<String, String>,
        featured: Set<String> = emptySet(),
        admin1: String? = null,
        id: Int = 1,
    ) = City(
        slug = slug, id = id, countryCode = country, region = "north-africa", latitude = lat, longitude = lon,
        timeZone = zone, languages = names.keys.toList(), featured = featured,
        names = names, admin1 = admin1,
    )

    private val tripoli = city(
        "tripoli-libya", "LY", 32.88743, 13.18733, "Africa/Tripoli",
        linkedMapOf("en" to "Tripoli", "ar" to "طرابلس"), featured = setOf("ar", "en"),
    )
    private val cairo = city("cairo-egypt", "EG", 30.06263, 31.24967, "Africa/Cairo", linkedMapOf("en" to "Cairo", "ar" to "القاهرة"))
    private val london = city("london-uk", "GB", 51.50853, -0.12574, "Europe/London", linkedMapOf("en" to "London", "ar" to "لندن"), admin1 = "England")
    private val toronto = city("toronto-canada", "CA", 43.70643, -79.39864, "America/Toronto", linkedMapOf("en" to "Toronto", "ur" to "ٹورانٹو"), admin1 = "Ontario")
    private val istanbul = city("istanbul-turkiye", "TR", 41.01384, 28.94966, "Europe/Istanbul", linkedMapOf("en" to "Istanbul", "tr" to "İstanbul"), admin1 = "Istanbul")
    private val newYork = city("new-york-usa", "US", 40.71427, -74.00597, "America/New_York", linkedMapOf("en" to "New York"))

    private val official = TestPaths.repoRoot.resolve("tools/timetables/official")
    private val stamps = Stamp.load(official.resolve("stamps"))
    private val strings = AppStrings(TestPaths.appResources)
    private val document = Document(strings, stamps, official)
    private val friday = Instant.parse("2026-09-25T00:07:00Z")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String) = getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = getValue(key) as List<Map<String, Any?>>

    private fun page(city: City, language: String, now: Instant = friday) =
        document.city(city, now).map("pages").map(language)

    private fun dayIndex(date: LocalDate) = date.day - 1

    private val clock = Regex("[0-9]{1,2}:[0-9]{2}")

    @Test
    fun aLibyanArabicPageReadsTheAppsTimesInLatinDigits() {
        val page = page(tripoli, "ar")
        assertEquals("0123456789", page["digits"])
        val times = page.list("days")[dayIndex(LocalDate(2026, 9, 13))]["times"] as List<*>
        assertEquals(listOf("5:25", "13:08", "16:36", "19:21", "20:42"), listOf(0, 2, 3, 4, 5).map { times[it] })
    }

    @Test
    fun anEgyptianArabicPageReadsArabicIndicDigits() {
        val page = page(cairo, "ar")
        assertEquals("٠١٢٣٤٥٦٧٨٩", page["digits"])
        val dhuhr = (page.list("days")[0]["times"] as List<*>)[2] as String
        assertTrue(dhuhr.matches(Regex("[٠-٩]{1,2}:[٠-٩]{2}")), dhuhr)
    }

    @Test
    fun theEpochsAreTheInstantsTheTimesWereFormattedFrom() {
        val day = document.city(tripoli, friday).list("days")[dayIndex(LocalDate(2026, 9, 13))]
        val expected = Timetable().day(tripoli, LocalDate(2026, 9, 13))
        assertEquals(Prayer.entries.map { expected.times.getValue(it).epochSeconds }, day["epochs"])
        assertEquals("2026-09-13", day["date"])
    }

    @Test
    fun namesAndSentencesComeFromTheApp() {
        val page = page(tripoli, "en")
        assertEquals(listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"), page["prayers"])
        assertEquals("Asr in", (page["nextIn"] as List<*>)[3])
        // Libya's Awqaf, by the short name the app shows beside its ⓘ (Task 10).
        assertEquals("Libyan Awqaf", page["method"])
        assertEquals("Standard", page["madhab"])
        assertEquals("ly.awqaf", document.city(tripoli, friday)["method"])
        assertEquals("STANDARD", document.city(tripoli, friday)["madhab"])
        assertEquals("109° · 2,916 km to Makkah", page["qiblaDetail"])
        assertEquals("Tripoli", page["city"])
        assertEquals("Libya", page["country"])
        assertEquals("طرابلس", page(tripoli, "ar")["city"])
        assertEquals("ليبيا", page(tripoli, "ar")["country"])
    }

    @Test
    fun theTodayBlockIsTheCitysOwnDay() {
        val today = page(tripoli, "en").map("today")
        assertEquals("Friday", today["weekday"])
        assertEquals("25 September 2026", today["date"])
        assertEquals("12 Rabi’ al-Thani 1448", today["hijri"])
        val full = today["full"] as String
        assertTrue(full.startsWith("Friday") && full.endsWith("25 September 2026"), full)
        assertEquals("25 Eylül 2026 Cuma", page(city("istanbul", "TR", 41.01384, 28.94966, "Europe/Istanbul", linkedMapOf("en" to "Istanbul", "tr" to "İstanbul")), "tr").map("today")["full"])

        val november = Instant.parse("2026-11-01T00:07:00Z")
        assertEquals("2026-10-31", document.city(newYork, november)["today"])
        assertEquals("October 31, 2026", page(newYork, "en", november).map("today")["date"])
    }

    @Test
    fun theMonthsCarryTheirGregorianAndHijriTitles() {
        val months = page(tripoli, "en").list("months")
        assertEquals("September 2026", months[0]["title"])
        assertEquals("Rabi’ al-Awwal – Rabi’ al-Thani 1448", months[0]["hijri"])
        assertEquals("October 2026", months[1]["title"])
        val facts = document.city(tripoli, friday).list("months")
        assertEquals(listOf(30, 31), facts.map { it["days"] })
    }

    @Test
    fun aClockChangeIsReportedOnTheFirstDayOnTheNewClock() {
        val changes = page(london, "en", Instant.parse("2026-10-02T00:07:00Z")).list("clockChanges")
        assertEquals(1, changes.size)
        assertEquals(24, changes[0]["index"])
        assertEquals("25 October 2026", changes[0]["date"])
        assertEquals("UTC", changes[0]["offset"])
    }

    @Test
    fun aClockChangeOnTheFirstDayShownIsReportedToo() {
        // Built just after New York's clocks went back on 1 November, the page starts on that day.
        val changes = page(newYork, "en", Instant.parse("2026-11-01T06:07:00Z")).list("clockChanges")
        assertEquals(0, changes[0]["index"])
        assertEquals("UTC−5", changes[0]["offset"])
    }

    @Test
    fun theCountdownIsInTheDigitsTheAppCountsDownIn() {
        // The app falls back to Western digits for the countdown in Egyptian and Saudi Arabic and
        // in Bengali (CountdownDigits); everywhere else it keeps the page's own.
        assertEquals("0123456789", page(cairo, "ar")["countdownDigits"])
        val newYorkArabic = city("new-york-usa", "US", 40.71427, -74.00597, "America/New_York", linkedMapOf("en" to "New York", "ar" to "نيويورك"))
        assertEquals("٠١٢٣٤٥٦٧٨٩", page(newYorkArabic, "ar")["countdownDigits"])
        assertEquals(page(newYorkArabic, "ar")["digits"], page(newYorkArabic, "ar")["countdownDigits"])
    }

    @Test
    fun theDocumentCarriesTheRegionOrderAndEachDaysLongDate() {
        assertEquals(Regions.ORDER, document.build(emptyList(), friday)["regions"])
        val city = document.city(tripoli, friday)
        assertEquals("25 September 2026", city.map("pages").map("en").list("days")[dayIndex(LocalDate(2026, 9, 25))]["date"])
        assertEquals(false, city.list("days")[0]["ramadanIsha"])
        assertEquals(false, city.list("days")[0]["highLatitude"])
        assertEquals(emptyList<String>(), city.map("pages").map("en")["highLatitude"])
    }

    @Test
    fun aCityWhoseTimetableTheAppCanNameIsBuilt() {
        // Every registry entry has a short name since Task 10, so no city is refused for want of one.
        val built = document.build(listOf(london), friday)
        assertTrue((built["cities"] as List<*>).isNotEmpty(), built.toString())
    }

    @Test
    fun theFeaturedLanguagesFollowThePageOrder() {
        assertEquals(listOf("en", "ar"), document.city(tripoli, friday)["featured"])
    }

    @Test
    fun fridaysAreMarkedInTheFacts() {
        val days = document.city(tripoli, friday).list("days")
        assertEquals(true, days[dayIndex(LocalDate(2026, 9, 25))]["friday"])
        assertEquals(false, days[dayIndex(LocalDate(2026, 9, 24))]["friday"])
    }

    // ── Spec §9.1: the stamps' figures, the detail fields, the cautious members, the proven rule ──

    @Test
    fun aPublishedCityCarriesItsClassItsUnitAndItsStampsFigures() {
        val city = document.city(london, friday)
        assertEquals("B", city["entryClass"])
        assertEquals(true, city["measured"])
        assertEquals("gb.london.lupt", city["unitId"])
        assertEquals("London (M25)", city["unitName"])
        val proof = city.map("proof")
        assertEquals(4015, proof["placeDays"])
        assertEquals(11, proof["places"])
        assertEquals(330, proof["ramadanDays"])
        assertEquals("2026-01-01", proof["first"])
        assertEquals("2026-12-31", proof["through"])
        assertEquals(5, proof["atMost"])
        assertEquals(false, proof["cautious"])
        val shares = proof.map("fajrShares")
        assertEquals(listOf("0", "1", "2", "3+"), shares.keys.toList())
        assertEquals(1.0, shares.values.sumOf { it as Double }, 0.001)
    }

    @Test
    fun theChecksTablesAtMostIsThePublishedCitiesOwnFigure() {
        // Kazan's own unit runs at most 2 minutes after DUM RT's starts; the entry-wide worst is
        // Yelabuga's, which no page shows (final review I1).
        val kazan = city("kazan-russia", "RU", 55.78874, 49.12214, "Europe/Moscow", linkedMapOf("en" to "Kazan"), admin1 = "Tatarstan Republic")
        val built = document.build(listOf(kazan), friday)
        val row = built.map("proof").list("published").single()
        assertEquals("ru.dumrt", row["entry"])
        assertEquals(2, row["atMost"])
        assertEquals(2, built.list("cities").single().map("proof")["atMost"])
        assertTrue(stamps.getValue("ru.dumrt").worstStarts(stamps.getValue("ru.dumrt").events)!! > 2)
    }

    @Test
    fun theProofSaysThroughWhenTheCheckedRunHoldingTheDaysShownEnds() {
        // Ruling R115: "through" is the end of the checked run the shown days sit in, not the stamp's
        // last date beyond a hole.
        @Suppress("UNCHECKED_CAST")
        val root = LinkedHashMap(Json.parse(official.resolve("stamps/gb.london.lupt.json").readText()) as Map<String, Any?>)
        @Suppress("UNCHECKED_CAST")
        val units = LinkedHashMap(root["units"] as Map<String, Any?>)
        @Suppress("UNCHECKED_CAST")
        val unit = LinkedHashMap(units["gb.london.lupt"] as Map<String, Any?>)
        unit["checked"] = listOf("2026-01-01..2026-10-31", "2027-01-01..2027-12-31")
        units["gb.london.lupt"] = unit
        root["units"] = units
        val holed = Document(strings, mapOf("gb.london.lupt" to Stamp(root)), official)
        val city = holed.city(london, friday)
        assertEquals("2026-10-31", city.map("proof")["through"])
        assertEquals(
            "Checked against London Unified’s published timetable through 31 October 2026.",
            city.map("pages").map("en").map("strings")["checkedThrough"],
        )
        val row = holed.build(listOf(london), friday).map("proof").list("published").single()
        assertEquals("2026-10-31", row["through"])
        assertEquals("31 October 2026", row.map("throughText")["en"])
    }

    // ── Ruling R116: the document carries only the months the page shows ──

    @Test
    fun aCityWhoseNextMonthIsUncheckedCarriesItsCurrentMonthAlone() {
        // Built at the first run of 1 October: Diyanet's captures reach 1 November, so İstanbul's page
        // is October alone — the months, the facts' days and every page's days — and the document
        // says why. Türkiye keeps one clock all year, so no page mentions a change.
        val october = Instant.parse("2026-10-01T06:07:00Z")
        val built = document.build(listOf(istanbul, london), october)
        assertEquals(emptyList<Map<String, Any?>>(), built.list("held"))
        val city = built.list("cities").first { it["slug"] == "istanbul-turkiye" }
        assertEquals(listOf(linkedMapOf<String, Any?>("year" to 2026, "month" to 10, "days" to 31)), city.list("months"))
        assertEquals((1..31).map { LocalDate(2026, 10, it).toString() }, city.list("days").map { it["date"] })
        assertEquals(mapOf("timetable" to "tr.diyanet", "day" to "2026-11-02"), city["nextUnchecked"])
        assertEquals("2026-11-01", city.map("proof")["through"])
        assertNull(city["excused"])
        for (language in listOf("en", "tr")) {
            val page = city.map("pages").map(language)
            assertEquals(1, page.list("months").size, language)
            assertEquals(31, page.list("days").size, language)
            assertEquals(emptyList<Map<String, Any?>>(), page.list("clockChanges"), language)
        }
        assertEquals("October 2026", city.map("pages").map("en").list("months").single()["title"])
        // London is checked through December: October and November, and nothing to say.
        val london = built.list("cities").first { it["slug"] == "london-uk" }
        assertEquals(listOf(10, 11), london.list("months").map { it["month"] })
        assertEquals(61, london.list("days").size)
        assertNull(london["nextUnchecked"])
    }

    @Test
    fun aCautiousCityShowsAMonthWithAMembersFaultDaysExcusedAndSaysSo() {
        // Ruling R117: IFT's table is wrong on nine November days (recorded in ca-toronto.tsv); on them
        // IIT's and MAC's tables are checked, so from 1 October Toronto shows October and November, and
        // the document names the excused days for the build's notice.
        val city = document.city(toronto, Instant.parse("2026-10-01T06:07:00Z"))
        assertEquals(listOf(10, 11), city.list("months").map { it["month"] })
        assertNull(city["nextUnchecked"])
        assertEquals(
            listOf(mapOf("timetable" to "ca.ift (a member of ca.toronto)", "why" to "recorded faults", "days" to 9)),
            city["excused"],
        )
    }

    @Test
    fun aCityShownWithBothMonthsSaysNothingIsUnchecked() {
        val city = document.city(toronto, friday) // September and October, both checked
        assertEquals(listOf(9, 10), city.list("months").map { it["month"] })
        assertNull(city["nextUnchecked"])
    }

    @Test
    fun everyDayCarriesItsLongWeekdayForTheCalendarLeaf() {
        // The Today card shows the page's last day as a calendar leaf on its last evening (the next
        // prayer is on no day the page carries), so each day has the leaf's weekday as `today` does.
        val page = page(london, "en")
        assertEquals("Tuesday", page.list("days")[0]["weekdayLong"])
        assertEquals("Tue", page.list("days")[0]["weekday"])
        assertEquals(page.map("today")["weekday"], page.list("days")[dayIndex(LocalDate(2026, 9, 25))]["weekdayLong"])
        assertEquals("الجمعة", page(london, "ar").list("days")[dayIndex(LocalDate(2026, 9, 25))]["weekdayLong"])
    }

    @Test
    fun aHeldCityIsListedWithItsReasonAndNotBuilt() {
        val built = document.build(listOf(tripoli, london), friday)
        assertEquals(listOf("london-uk"), built.list("cities").map { it["slug"] })
        assertEquals(listOf(mapOf("slug" to "tripoli-libya", "reason" to "class D_AUTHORITY is not proven")), built.list("held"))
        // A held city still builds on its own (for these tests), with no proof to show.
        assertNull(document.city(tripoli, friday)["proof"])
        assertEquals("D_AUTHORITY", document.city(tripoli, friday)["entryClass"])
    }

    @Test
    fun aPageInsideAnotherCitysUnitNamesTheCityWhoseTableItFollowsAsTheAppDoes() {
        // The app's Balykshi takes QMDB's Atyrau (its unit's city, GeoNames 610529): the app's About names Atyrau, in
        // the reader's language, and so does the page; Atyrau's own page names itself.
        val balykshi = city("balykshi-kazakhstan", "KZ", 47.06667, 51.86667, "Asia/Atyrau", linkedMapOf("en" to "Balykshi", "ar" to "بالىقشى"), id = 610445)
        assertEquals("Kazakh Muftiate publishes the prayer times used in Atyrau.", page(balykshi, "en").map("strings")["whoPublishesBody"])
        val arabic = page(balykshi, "ar").map("strings")["whoPublishesBody"] as String
        assertTrue("أتيراو" in arabic && "بالىقشى" !in arabic, arabic)
        val atyrau = city("atyrau-kazakhstan", "KZ", 47.1048, 51.88427, "Asia/Atyrau", linkedMapOf("en" to "Atyrau City"), id = 610529)
        assertEquals("Kazakh Muftiate publishes the prayer times used in Atyrau City.", page(atyrau, "en").map("strings")["whoPublishesBody"])
    }

    @Test
    fun thePageStringsAreTheAppsSentencesFilledForTheCity() {
        val page = page(london, "en")
        assertEquals("Standard", page["otherSchool"])
        assertEquals(emptyList<String>(), page["members"])
        val s = page.map("strings")
        assertEquals("London Unified timetable", s["whoseTitle"])
        assertEquals("Taqwa is not affiliated with London Unified.", s["notAffiliated"])
        assertEquals("Checked against London Unified’s published timetable through 31 December 2026.", s["checkedThrough"])
        assertEquals("Who publishes them", s["whoPublishes"])
        assertEquals("London Unified publishes the prayer times used in London (M25).", s["whoPublishesBody"])
        assertEquals("How Taqwa reproduces them", s["howReproduces"])
        val intro = s["methodIntro"] as String
        assertTrue(intro.startsWith("The way London Unified calculates: dawn (Fajr) at a twilight angle") && intro.endsWith("down)."), intro)
        assertEquals("How it was checked", s["howChecked"])
        assertEquals("4015", s["statDaysValue"]) // the app's localizedDigits: no thousands grouping
        assertEquals("days at 11 places", s["statDays"])
        assertEquals("starts before London Unified’s", s["statNever"])
        assertEquals("5 min", s["statMinutes"])
        assertEquals("at most after", s["statAtMost"])
        assertEquals("", s["cautiousBody"])
        assertEquals("", s["maghribCap"])
        assertEquals("Which timetable decides each time today", s["whichDecides"])
        assertEquals("Match my mosque", s["matchMosque"])
        assertEquals("Set by rule", s["setByRule"])
        assertTrue((s["polarLine"] as String).startsWith("The sun does not rise or set here today"))
        assertEquals("Stop eating by {time} today.", s["stopEating"])
        // The Arabic page says the same in the app's Arabic.
        val ar = page(london, "ar").map("strings")
        assertEquals(strings.format("ar", "today_whose_checked_title", strings.get("ar", "authority_london_unified")), ar["whoseTitle"])
    }

    @Test
    fun aCautiousPageNamesItsMembersAndClaimsNoAtMostFigure() {
        val city = document.city(toronto, friday)
        assertEquals("C", city["entryClass"])
        assertNull(city["unitId"])
        assertNull(city["unitName"])
        val proof = city.map("proof")
        assertEquals(true, proof["cautious"])
        assertNull(proof["atMost"]) // ruling R105
        assertEquals(1400, proof["placeDays"])
        val page = city.map("pages").map("en")
        val members = listOf("Islamic Foundation Toronto", "Islamic Institute", "MAC Masjid")
        assertEquals(members, page["members"])
        assertEquals("Standard", page["otherSchool"]) // Toronto's school is not known: the later (Hanafi) Asr leads
        val s = page.map("strings")
        assertEquals("Cautious times", s["whoseTitle"])
        assertEquals("", s["methodIntro"])
        assertEquals("", s["statMinutes"])
        assertEquals("", s["statAtMost"])
        assertEquals("", s["statNever"])
        assertEquals("days at 6 places", s["statDays"])
        assertEquals("1400", s["statDaysValue"])
        assertEquals(
            "Mosques in Toronto follow different timetables in good faith, and none is followed by most. " +
                "Taqwa combines Islamic Foundation Toronto, Islamic Institute, MAC Masjid: each prayer once all have begun it.",
            s["cautiousBody"],
        )
        // The app's cautious About screen's proof sentence and second tile (rulings R111 and R112):
        // no date, and the tile names the timetable that decides each start.
        assertEquals("Checked against the timetables followed here.", s["checkedThrough"])
        assertEquals("starts before the timetable that decides it", s["statNeverAny"])
        assertEquals(strings.get("ur", "about_cautious_checked"), page(toronto, "ur").map("strings")["checkedThrough"])
        assertEquals(strings.get("ur", "about_stat_never_before_decider"), page(toronto, "ur").map("strings")["statNeverAny"])
        // The built day's members, seven epochs and seven clocks each, and the Maghrib cap's sentence only on a capped day.
        val todayIndex = dayIndex(LocalDate(2026, 9, 25))
        val dayMembers = city.list("days")[todayIndex]["members"] as List<*>
        assertEquals(3, dayMembers.size)
        assertTrue(dayMembers.all { (it as List<*>).size == 7 && it.all { e -> e is Long } }, dayMembers.toString())
        val pageMembers = page.list("days")[todayIndex]["members"] as List<*>
        assertTrue(pageMembers.all { (it as List<*>).size == 7 && it.all { c -> clock.matches(c as String) } }, pageMembers.toString())
        val capped = city.list("days")[todayIndex]["capped"] as Boolean
        val cap = s["maghribCap"] as String
        assertEquals(capped, cap.isNotEmpty(), cap)
        if (capped) assertTrue(cap.startsWith("Maghrib is the exception today"), cap)
    }

    @Test
    fun everyDayCarriesTheDetailFieldsAsEpochsAndAsClocks() {
        val city = document.city(london, friday)
        val day = city.list("days")[0]
        val epochs = day["epochs"] as List<*>
        assertTrue((day["asrOther"] as Long) < (epochs[3] as Long))
        assertTrue((day["endOfEating"] as Long) <= (epochs[0] as Long))
        assertTrue((day["sunset"] as Long) <= (epochs[4] as Long))
        assertTrue(day["imsak"] == null || day["imsak"] is Long)
        assertEquals(emptyList<Int>(), day["setByRule"])
        assertEquals(false, day["polar"])
        assertEquals(false, day.containsKey("members"))
        val pageDay = city.map("pages").map("en").list("days")[0]
        assertTrue(clock.matches(pageDay["asrOther"] as String))
        assertTrue(clock.matches(pageDay["endOfEating"] as String))
        assertTrue(pageDay["imsak"] == null || clock.matches(pageDay["imsak"] as String))
        assertEquals(emptyList<String>(), pageDay["setByRule"])
    }

    @Test
    fun theDocumentsProofComesFromTheStampsAndTheGateFiles() {
        val built = document.build(listOf(toronto, tripoli, london), friday)
        val proof = built.map("proof")
        assertEquals(stamps.size, proof["entries"])
        assertEquals(stamps.values.sumOf { it.placeDays }, proof["placeDays"])
        assertEquals(stamps.values.sumOf { it.heldOutDays }, proof["heldOutDays"])
        assertEquals(stamps.values.sumOf { it.ramadanDays }, proof["ramadanDays"])
        assertEquals(stamps.values.sumOf { it.early() }, proof["earlyStarts"])
        assertEquals(stamps.values.sumOf { it.lateEnds() }, proof["lateEnds"])
        assertEquals(stamps.values.count { it.broken != 0 }, proof["brokenStamps"])
        assertEquals(ProofTotals.gateRows(official), proof["tables"])
        assertEquals(ProofTotals.surveyCalendars(official), proof["surveyCalendars"])
        val published = proof.list("published")
        assertEquals(listOf("ca.toronto", "gb.london.lupt"), published.map { it["entry"] })
        val london = published[1]
        assertEquals("B", london["class"])
        assertEquals(4015, london["placeDays"])
        assertEquals(11, london["places"])
        assertEquals("2026-01-01", london["first"])
        assertEquals("2026-12-31", london["through"])
        assertEquals(5, london["atMost"])
        assertEquals("London Unified", london.map("names")["en"])
        assertEquals(SITE_LANGUAGES.toSet(), london.map("names").keys)
        assertEquals("31 December 2026", london.map("throughText")["en"])
        val toronto = published[0]
        assertEquals("C", toronto["class"])
        assertNull(toronto["atMost"]) // a cautious entry claims no "at most" (ruling R105); the page renders "—"
        assertEquals("Cautious times", toronto.map("names")["en"])
        // Ruling R115: the row says through the end of the days covered at Toronto. Ruling R117: IFT's
        // fault days in November are covered (IIT's and MAC's tables are checked on them), so through
        // the members' December. Ruling R114: the checks page has no country, so the date reads in the
        // language's own form — British order, not en-CA's "December 31, 2026".
        assertEquals("2026-12-31", toronto["through"])
        assertEquals("31 December 2026", toronto.map("throughText")["en"])
        assertEquals(Formats.forLanguage("ar").longDate(LocalDate(2026, 12, 31)), toronto.map("throughText")["ar"])
        assertEquals(1, built.list("held").size)
    }
}
