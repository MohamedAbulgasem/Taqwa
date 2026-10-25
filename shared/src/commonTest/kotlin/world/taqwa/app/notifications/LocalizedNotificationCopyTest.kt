package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSound
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.PrayerNaming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"

// First-strong isolate and pop-directional isolate: the pair that makes a run its own direction.
private const val FSI = "\u2068"
private const val PDI = "\u2069"

private class StubFormat(private val tag: String, private val arabicIndic: Boolean) : PlatformFormat {
    override fun languageTag() = tag
    override fun localizedDigits(number: Int): String =
        if (arabicIndic) number.toString().map { ARABIC_INDIC[it - '0'] }.joinToString("") else number.toString()
    override fun clockTime(hour: Int, minute: Int) =
        "${localizedDigits(hour)}:${localizedDigits(minute)}"
}

/**
 * The spec's naming rule governs notification text as much as the timeline, and a notification
 * is written once at schedule time — days before it fires, with no chance to re-localise it.
 */
class LocalizedNotificationCopyTest {

    /** Every kind but the iqama, whose body counts the minutes since the adhan and quotes no clock time. */
    private val kindsQuotingATime = NotificationKind.entries - NotificationKind.IQAMA

    @Test
    fun channelNamesAreLocalisedAndDistinctPerSound() {
        val copy = LocalizedNotificationCopy(StubFormat("ar-LY", arabicIndic = false))
        val names = PrayerSound.entries.map { copy.channelName(Prayer.FAJR, it) }
        // Four channels can coexist for one prayer, one per sound the user has tried; the whole
        // point of naming them is that Android's settings list is not four identical rows.
        assertEquals(names.size, names.distinct().size, names.toString())
        names.forEach { assertTrue(it.startsWith("الفجر"), it) }
        assertFalse(names.any { it.contains("Prayer:") })
        assertTrue(names.any { it.contains("أذان") }, names.toString())
    }

    @Test
    fun channelNamesFollowTheDeviceLanguage() {
        val copy = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        val name = copy.channelName(Prayer.MAGHRIB, PrayerSound.ADHAN)
        // Non-Arabic locales get PrayerNaming's paired display, same as the notification title.
        assertTrue(name.startsWith("Maghrib"), name)
        assertTrue(name.endsWith("Adhan"), name)
    }

    @Test
    fun anArabicDeviceGetsTheArabicNameAloneInTheTitle() {
        val copy = LocalizedNotificationCopy(StubFormat("ar-LY", arabicIndic = false))
        val title = copy.title(Prayer.FAJR, NotificationKind.PRAYER)
        assertEquals("الفجر", title)
        assertFalse(title.contains("Fajr"))
    }

    @Test
    fun anArabicDeviceGetsAnArabicBody() {
        val copy = LocalizedNotificationCopy(StubFormat("ar-EG", arabicIndic = true))
        val body = copy.body(Prayer.ISHA, NotificationKind.PRAYER, "٢٠:١٥", 0)
        assertTrue(body.contains("حان الآن وقت صلاة"))
        assertTrue(body.contains("العشاء"))
    }

    @Test
    fun anArabicReminderCountsItsMinutesInTheLocalesOwnDigits() {
        val copy = LocalizedNotificationCopy(StubFormat("ar-EG", arabicIndic = true))
        val body = copy.body(Prayer.ASR, NotificationKind.REMINDER, "١٥:٤٠", 10)
        assertTrue(body.contains("١٠"), body)
        assertTrue(body.contains("دقائق"), body)
    }

    // A reminder clamped to the prayer before it says the minutes really left, which can be one or
    // two: the noun then takes the singular (English, French) or Arabic's singular and dual.
    @Test
    fun aReminderWithOneOrTwoMinutesLeftTakesTheLanguagesOwnNumber() {
        val c = "05:35" // a 24-hour time, set bare (the 12-hour work isolates anything else)
        val en = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Fajr · الفجر in 1 minute · 05:35", en.body(Prayer.FAJR, NotificationKind.REMINDER, c, 1))
        assertEquals("Fajr · الفجر in 2 minutes · 05:35", en.body(Prayer.FAJR, NotificationKind.REMINDER, c, 2))
        val fr = LocalizedNotificationCopy(StubFormat("fr-FR", arabicIndic = false))
        assertTrue(fr.body(Prayer.ISHA, NotificationKind.REMINDER, c, 1).contains("dans 1 minute ·"))
        assertTrue(fr.body(Prayer.ISHA, NotificationKind.REMINDER, c, 2).contains("dans 2 minutes ·"))
        val ar = LocalizedNotificationCopy(StubFormat("ar-EG", arabicIndic = true))
        assertEquals("العشاء بعد دقيقة · 05:35", ar.body(Prayer.ISHA, NotificationKind.REMINDER, c, 1))
        assertEquals("العشاء بعد دقيقتين · 05:35", ar.body(Prayer.ISHA, NotificationKind.REMINDER, c, 2))
        assertEquals("العشاء بعد ٣ دقائق · 05:35", ar.body(Prayer.ISHA, NotificationKind.REMINDER, c, 3))
        assertEquals("العشاء بعد ١٢ دقيقة · 05:35", ar.body(Prayer.ISHA, NotificationKind.REMINDER, c, 12))
        assertEquals("Isha in 1 minute · 05:35", EnglishNotificationCopy.body(Prayer.ISHA, NotificationKind.REMINDER, c, 1))
        for (tag in listOf("tr-TR", "id-ID", "ur-PK", "bn-BD")) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            val one = copy.body(Prayer.ISHA, NotificationKind.REMINDER, c, 1)
            assertEquals(copy.body(Prayer.ISHA, NotificationKind.REMINDER, c, 7).replace("7", "1"), one, tag)
        }
    }

    @Test
    fun anEnglishDeviceKeepsThePairedNameAndTheEnglishSentence() {
        val copy = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Fajr · الفجر", copy.title(Prayer.FAJR, NotificationKind.PRAYER))
        assertEquals(
            "Fajr · الفجر in 15 minutes · 5:42",
            copy.body(Prayer.FAJR, NotificationKind.REMINDER, "5:42", 15),
        )
    }

    // "5:10 PM" is Latin digits, a space and a marker, in a sentence that may run the other way;
    // unisolated, the neighbouring "·" and words reorder its pieces. Wrapped in FSI…PDI, the same
    // isolate the Prayer screen's sub-lines use, it reads as one unit whatever the sentence's
    // direction.
    @Test
    fun theTimeIsIsolatedInsideTheBody() {
        val body = LocalizedNotificationCopy(StubFormat("ur", arabicIndic = false))
            .body(Prayer.FAJR, NotificationKind.PRAYER, "5:10 PM", 0)
        assertTrue(body.contains(FSI + "5:10 PM" + PDI), body)
    }

    @Test
    fun everyKindAndLanguageIsolatesATwelveHourTime() {
        for (tag in listOf("en-GB", "ar-LY", "fr-FR", "tr-TR", "id-ID", "ur-PK", "bn-BD")) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            for (time in listOf("5:10\u00A0PM", "٥:١٠\u00A0م", "ÖS\u00A05:10")) {
                for (kind in kindsQuotingATime) {
                    val body = copy.body(Prayer.FAJR, kind, time, 10)
                    assertTrue(body.contains(FSI + time + PDI), "$tag $kind: $body")
                    assertFalse(body.contains("{time}"), "$tag $kind: $body")
                }
            }
        }
    }

    // What shipped before the 12-hour option: a 24-hour time is digits and a colon, which the
    // sentence already carries correctly, so it is set bare and the armed notifications read byte
    // for byte as they always did.
    @Test
    fun aTwentyFourHourTimeIsSetBareExactlyAsBefore() {
        for (tag in listOf("en-GB", "ar-LY", "fr-FR", "tr-TR", "id-ID", "ur-PK", "bn-BD")) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            for (time in listOf("05:35", "٢٠:١٥", "০৫:৩৫")) {
                for (kind in kindsQuotingATime) {
                    val body = copy.body(Prayer.FAJR, kind, time, 10)
                    assertTrue(body.contains(time), "$tag $kind: $body")
                    assertFalse(body.contains(FSI) || body.contains(PDI), "$tag $kind: $body")
                }
            }
        }
    }

    @Test
    fun everyLanguageRendersBothBodiesWithNoPlaceholderLeftBehind() {
        for (tag in listOf("en-GB", "ar-LY", "fr-FR", "tr-TR", "id-ID", "ur-PK", "bn-BD")) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            for (prayer in Prayer.entries) {
                val prayerBody = copy.body(prayer, NotificationKind.PRAYER, "12:34", 0)
                val reminder = copy.body(prayer, NotificationKind.REMINDER, "12:34", 10)
                val title = copy.title(prayer, NotificationKind.PRAYER)
                for (text in listOf(prayerBody, reminder, title)) {
                    assertFalse(text.contains("{") || text.contains("}"), "$tag $prayer: $text")
                    assertTrue(text.contains(title), "$tag $prayer: body should name the prayer: $text")
                }
                assertTrue(prayerBody.contains("12:34"), "$tag: $prayerBody")
                assertTrue(reminder.contains("10") && reminder.contains("12:34"), "$tag: $reminder")
            }
        }
    }

    @Test
    fun everyLanguageNamesTahajjudAndQuotesFajrInItsBody() {
        for (tag in listOf("en-GB", "ar-LY", "fr-FR", "tr-TR", "id-ID", "ur-PK", "bn-BD")) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            val title = copy.title(Prayer.FAJR, NotificationKind.TAHAJJUD)
            val body = copy.body(Prayer.FAJR, NotificationKind.TAHAJJUD, "05:41", 0)
            val fajr = PrayerNaming.name(Prayer.FAJR, tag)
            assertTrue(title.isNotBlank() && title != copy.title(Prayer.FAJR, NotificationKind.PRAYER), "$tag: $title")
            assertFalse(body.contains("{") || body.contains("}"), "$tag: $body")
            assertTrue(body.contains("05:41") && body.contains(fajr), "$tag: $body")
            // One separator: the paired «Fajr · الفجر» of a title would make it two.
            assertEquals(1, body.count { it == '·' }, "$tag: $body")
        }
    }

    @Test
    fun tahajjudsChannelIsNamedAfterItAndNotAfterFajr() {
        val copy = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Tahajjud · Notification", copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION, NotificationKind.TAHAJJUD))
        assertEquals(
            copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION),
            copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION, NotificationKind.REMINDER),
        )
    }

    // The adhkar reminder's titles are each language's `adhkar_morning` / `adhkar_evening`, the
    // names the Adhkar screen itself wears, written out here so a drift in either is caught.
    private val adhkarTitles = mapOf(
        "en-GB" to ("Morning adhkar" to "Evening adhkar"),
        "ar-LY" to ("أذكار الصباح" to "أذكار المساء"),
        "fr-FR" to ("Adhkar du matin" to "Adhkar du soir"),
        "tr-TR" to ("Sabah zikirleri" to "Akşam zikirleri"),
        "id-ID" to ("Zikir pagi" to "Zikir petang"),
        "ur-PK" to ("صبح کے اذکار" to "شام کے اذکار"),
        "bn-BD" to ("সকালের আযকার" to "সন্ধ্যার আযকার"),
    )

    @Test
    fun `every language titles the adhkar reminder as its adhkar screen does`() {
        for ((tag, titles) in adhkarTitles) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            assertEquals(titles.first, copy.title(Prayer.FAJR, NotificationKind.ADHKAR_MORNING), tag)
            assertEquals(titles.second, copy.title(Prayer.ASR, NotificationKind.ADHKAR_EVENING), tag)
        }
    }

    @Test
    fun `every language quotes the end of the adhkar window with its time isolated`() {
        for (tag in adhkarTitles.keys) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            val morning = copy.body(Prayer.FAJR, NotificationKind.ADHKAR_MORNING, "6:41\u00A0AM", 0)
            val evening = copy.body(Prayer.ASR, NotificationKind.ADHKAR_EVENING, "6:58\u00A0PM", 0)
            assertTrue(morning.endsWith(" · " + FSI + "6:41\u00A0AM" + PDI), "$tag: $morning")
            assertTrue(evening.endsWith(" · " + FSI + "6:58\u00A0PM" + PDI), "$tag: $evening")
            assertTrue(morning != evening.replace("6:58\u00A0PM", "6:41\u00A0AM"), "$tag: the two bodies read the same")
            for (body in listOf(morning, evening)) {
                assertFalse(body.contains("{") || body.contains("}"), "$tag: $body")
                assertEquals(1, body.count { it == '·' }, "$tag: $body")
            }
            // A 24-hour time stays bare, as every other body's does.
            assertEquals(morning.replace(FSI + "6:41\u00A0AM" + PDI, "06:41"), copy.body(Prayer.FAJR, NotificationKind.ADHKAR_MORNING, "06:41", 0))
        }
        val english = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Until sunrise · 06:41", english.body(Prayer.FAJR, NotificationKind.ADHKAR_MORNING, "06:41", 0))
        assertEquals("Until Maghrib · 18:58", english.body(Prayer.ASR, NotificationKind.ADHKAR_EVENING, "18:58", 0))
    }

    @Test
    fun `both adhkar kinds share one channel named for both`() {
        for (tag in adhkarTitles.keys) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            val morning = copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION, NotificationKind.ADHKAR_MORNING)
            assertEquals(morning, copy.channelName(Prayer.ASR, PrayerSound.NOTIFICATION, NotificationKind.ADHKAR_EVENING), tag)
            assertTrue(morning != copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION), "$tag: $morning")
            assertTrue(morning != copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION, NotificationKind.TAHAJJUD), "$tag: $morning")
        }
        val english = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Morning and evening adhkar · Takbir", english.channelName(Prayer.FAJR, PrayerSound.TAKBIR, NotificationKind.ADHKAR_MORNING))
    }

    /**
     * The channel the system lists is named as the switch that turns it on: each language's
     * `notifications_adhkar`, the Settings › Notifications row. A commonTest cannot read the
     * resources, so the seven texts are written out here as the strings files have them.
     */
    private val adhkarSwitch = mapOf(
        "en-GB" to "Morning and evening adhkar",
        "ar-LY" to "أذكار الصباح والمساء",
        "fr-FR" to "Adhkar du matin et du soir",
        "tr-TR" to "Sabah ve akşam zikirleri",
        "id-ID" to "Zikir pagi dan petang",
        "ur-PK" to "صبح و شام کے اذکار",
        "bn-BD" to "সকাল ও সন্ধ্যার আযকার",
    )

    @Test
    fun `every language names the adhkar channel as its notifications switch`() {
        assertEquals(adhkarTitles.keys, adhkarSwitch.keys)
        for ((tag, name) in adhkarSwitch) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            for (kind in listOf(NotificationKind.ADHKAR_MORNING, NotificationKind.ADHKAR_EVENING)) {
                val channel = copy.channelName(Prayer.FAJR, PrayerSound.SILENT, kind)
                assertTrue(channel.startsWith("$name · "), "$tag: $channel")
            }
        }
    }

    // ── Duha and the iqama (Duha and Iqama spec §3.2) ──────────────────────────────────────

    private class DuhaWords(val title: String, val best: String, val start: String)

    /** The spec's table, with `{time}` where END goes. */
    private val duhaCopy = mapOf(
        "en-GB" to DuhaWords("Duha", "The best time for Duha · until {time}", "Duha time is in · until {time}"),
        "ar-LY" to DuhaWords("الضحى", "أفضل وقت لصلاة الضحى · حتى {time}", "دخل وقت الضحى · حتى {time}"),
        "fr-FR" to DuhaWords("Douha", "Le meilleur moment pour la Douha · jusqu’à {time}", "Le temps de la Douha est arrivé · jusqu’à {time}"),
        "tr-TR" to DuhaWords("Kuşluk", "Kuşluk namazı için en faziletli vakit · bitiş {time}", "Kuşluk vakti girdi · bitiş {time}"),
        "id-ID" to DuhaWords("Dhuha", "Waktu terbaik untuk salat Dhuha · hingga pukul {time}", "Telah masuk waktu Dhuha · hingga pukul {time}"),
        "ur-PK" to DuhaWords("چاشت", "نمازِ چاشت کا افضل وقت · {time} تک", "چاشت کا وقت شروع ہو گیا · {time} تک"),
        "bn-BD" to DuhaWords("চাশত", "চাশতের নামাজের উত্তম সময় · {time} পর্যন্ত", "চাশতের সময় শুরু হয়েছে · {time} পর্যন্ত"),
    )

    /** The iqama's body with a gap of 10, and its channel's name. */
    private val iqamaCopy = mapOf(
        "en-GB" to ("Iqama reminder · 10 minutes after the adhan" to "Iqama reminder"),
        "ar-LY" to ("تذكير الإقامة · مضت 10 دقائق على الأذان" to "تذكير الإقامة"),
        "fr-FR" to ("Rappel de l’iqama · 10 minutes après l’adhan" to "Rappel de l’iqama"),
        "tr-TR" to ("Kamet hatırlatması · ezandan 10 dakika sonra" to "Kamet hatırlatması"),
        "id-ID" to ("Pengingat iqamah · 10 menit setelah azan" to "Pengingat iqamah"),
        "ur-PK" to ("اقامت کی یاد دہانی · اذان کے 10 منٹ بعد" to "اقامت کی یاد دہانی"),
        "bn-BD" to ("ইকামতের রিমাইন্ডার · আজানের 10 মিনিট পর" to "ইকামতের রিমাইন্ডার"),
    )

    @Test
    fun `every language titles Duha by its own name and quotes its end with the clock isolated`() {
        for ((tag, words) in duhaCopy) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            for (kind in listOf(NotificationKind.DUHA_BEST, NotificationKind.DUHA_START)) {
                assertEquals(words.title, copy.title(Prayer.SUNRISE, kind), "$tag $kind")
                val template = if (kind == NotificationKind.DUHA_BEST) words.best else words.start
                assertEquals(template.replace("{time}", FSI + "12:41 PM" + PDI), copy.body(Prayer.SUNRISE, kind, "12:41 PM", 0), "$tag $kind")
                // A 24-hour time stays bare, as every other body's does.
                assertEquals(template.replace("{time}", "12:41"), copy.body(Prayer.SUNRISE, kind, "12:41", 0), "$tag $kind")
            }
        }
    }

    @Test
    fun `Urdu and Bengali call Duha by its own name and never Ishraq`() {
        // In subcontinental usage Ishraq is some 15 to 20 minutes after sunrise, so naming it at
        // 50 minutes or more would mislead: both reminders say چاشت / চাশত.
        val urdu = LocalizedNotificationCopy(StubFormat("ur-PK", arabicIndic = false))
        val bengali = LocalizedNotificationCopy(StubFormat("bn-BD", arabicIndic = false))
        for (kind in listOf(NotificationKind.DUHA_BEST, NotificationKind.DUHA_START)) {
            val u = urdu.body(Prayer.SUNRISE, kind, "08:10", 0)
            val b = bengali.body(Prayer.SUNRISE, kind, "08:10", 0)
            assertTrue("چاشت" in u && "اشراق" !in u, "$kind $u")
            assertTrue("চাশত" in b && "ইশরাক" !in b, "$kind $b")
        }
    }

    @Test
    fun `every language says how long after the adhan the iqama reminder rings under the prayer's own title`() {
        for ((tag, words) in iqamaCopy) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            for (prayer in listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
                assertEquals(words.first, copy.body(prayer, NotificationKind.IQAMA, "12:34", 10), "$tag $prayer")
                assertEquals(copy.title(prayer, NotificationKind.PRAYER), copy.title(prayer, NotificationKind.IQAMA), "$tag $prayer")
            }
        }
        val english = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Isha · العشاء", english.title(Prayer.ISHA, NotificationKind.IQAMA))
        assertEquals("Iqama reminder · 1 minute after the adhan", english.body(Prayer.ISHA, NotificationKind.IQAMA, "", 1))
        val french = LocalizedNotificationCopy(StubFormat("fr-FR", arabicIndic = false))
        assertEquals("Rappel de l’iqama · 1 minute après l’adhan", french.body(Prayer.ISHA, NotificationKind.IQAMA, "", 1))
    }

    @Test
    fun `Arabic counts the minutes since the adhan in its own grammar`() {
        val latin = LocalizedNotificationCopy(StubFormat("ar-LY", arabicIndic = false))
        val expected = mapOf(
            1 to "تذكير الإقامة · مضت دقيقة على الأذان",
            2 to "تذكير الإقامة · مضت دقيقتان على الأذان",
            5 to "تذكير الإقامة · مضت 5 دقائق على الأذان",
            10 to "تذكير الإقامة · مضت 10 دقائق على الأذان",
            15 to "تذكير الإقامة · مضت 15 دقيقة على الأذان",
            30 to "تذكير الإقامة · مضت 30 دقيقة على الأذان",
        )
        for ((minutes, body) in expected) {
            assertEquals(body, latin.body(Prayer.ASR, NotificationKind.IQAMA, "", minutes), "$minutes")
        }
        // And in the locale's own digits where it has them.
        val indic = LocalizedNotificationCopy(StubFormat("ar-EG", arabicIndic = true))
        assertEquals("تذكير الإقامة · مضت ١٥ دقيقة على الأذان", indic.body(Prayer.FAJR, NotificationKind.IQAMA, "", 15))
    }

    @Test
    fun `Duha and the iqama post into channels named for them`() {
        for (tag in duhaCopy.keys) {
            val copy = LocalizedNotificationCopy(StubFormat(tag, arabicIndic = false))
            val duha = copy.channelName(Prayer.SUNRISE, PrayerSound.NOTIFICATION, NotificationKind.DUHA_BEST)
            assertEquals(duha, copy.channelName(Prayer.SUNRISE, PrayerSound.NOTIFICATION, NotificationKind.DUHA_START), tag)
            assertTrue(duha.startsWith(duhaCopy.getValue(tag).title + " · "), "$tag: $duha")
            val iqama = copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION, NotificationKind.IQAMA)
            assertTrue(iqama.startsWith(iqamaCopy.getValue(tag).second + " · "), "$tag: $iqama")
            for (prayer in listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)) {
                assertEquals(iqama, copy.channelName(prayer, PrayerSound.NOTIFICATION, NotificationKind.IQAMA), "$tag $prayer")
            }
            assertTrue(iqama != copy.channelName(Prayer.FAJR, PrayerSound.NOTIFICATION), "$tag: $iqama")
        }
        val english = LocalizedNotificationCopy(StubFormat("en-GB", arabicIndic = false))
        assertEquals("Duha · Notification", english.channelName(Prayer.SUNRISE, PrayerSound.NOTIFICATION, NotificationKind.DUHA_BEST))
        assertEquals("Iqama reminder · Takbir", english.channelName(Prayer.MAGHRIB, PrayerSound.TAKBIR, NotificationKind.IQAMA))
        // The planner's default copy names them the same way.
        assertEquals("Duha · Silent", EnglishNotificationCopy.channelName(Prayer.SUNRISE, PrayerSound.SILENT, NotificationKind.DUHA_START))
        assertEquals("Iqama reminder · Notification", EnglishNotificationCopy.channelName(Prayer.ASR, PrayerSound.NOTIFICATION, NotificationKind.IQAMA))
    }
}
