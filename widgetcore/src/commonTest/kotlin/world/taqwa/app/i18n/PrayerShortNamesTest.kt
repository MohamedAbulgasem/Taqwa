package world.taqwa.app.i18n

import world.taqwa.app.domain.Prayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B37. The lock-screen ring clipped the next prayer's name to its first four letters, which cut
 * all five Arabic names (الفج, العش) and left stems like "Akşa" and "Yats" in Turkish and "Asha"
 * in Indonesian. The ring's slot holds every language's whole name, so the short name is the
 * whole name everywhere, the Arabic one with its article as on every other screen (the owner's
 * choice, 24 Oct 2026).
 */
class PrayerShortNamesTest {

    private val prayers = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    /** Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
    private val table: Map<String, List<String>> = mapOf(
        "en" to listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
        "ar" to listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"),
        "fr" to listOf("Fajr", "Lever du soleil", "Dhuhr", "Asr", "Maghrib", "Isha"),
        "tr" to listOf("Sabah", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"),
        "id" to listOf("Subuh", "Terbit", "Dzuhur", "Ashar", "Maghrib", "Isya"),
        "ur" to listOf("فجر", "طلوعِ آفتاب", "ظہر", "عصر", "مغرب", "عشاء"),
        "bn" to listOf("ফজর", "সূর্যোদয়", "যোহর", "আসর", "মাগরিব", "এশা"),
    )

    @Test
    fun theRingsShortNamesAreTheseInEveryLanguage() {
        assertEquals(UiLanguage.codes.toSet(), table.keys)
        for ((code, names) in table) {
            prayers.zip(names).forEach { (prayer, short) ->
                assertEquals(short, PrayerNaming.shortName(prayer, code), "$code $prayer")
            }
        }
    }

    @Test
    fun theRingsNameIsTheWholeNameInEveryLanguage() {
        for (code in UiLanguage.codes) {
            prayers.forEach { prayer ->
                assertEquals(PrayerNaming.name(prayer, code), PrayerNaming.shortName(prayer, code), "$code $prayer")
            }
        }
    }

    @Test
    fun regionalAndUnknownTagsResolveLikeTheFullNames() {
        assertEquals("الفجر", PrayerNaming.shortName(Prayer.FAJR, "ar-LY"))
        assertEquals("Subuh", PrayerNaming.shortName(Prayer.FAJR, "in-ID"))
        assertEquals("Fajr", PrayerNaming.shortName(Prayer.FAJR, "de"))
    }
}
