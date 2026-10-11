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
    // isolate Today's sub-lines use, it reads as one unit whatever the sentence's direction.
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
                for (kind in NotificationKind.entries) {
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
                for (kind in NotificationKind.entries) {
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
}
