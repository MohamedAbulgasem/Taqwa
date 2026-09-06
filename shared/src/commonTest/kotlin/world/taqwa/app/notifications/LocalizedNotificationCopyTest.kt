package world.taqwa.app.notifications

import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.PlatformFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"

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
}
