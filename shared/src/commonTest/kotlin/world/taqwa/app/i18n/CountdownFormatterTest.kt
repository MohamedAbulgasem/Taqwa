package world.taqwa.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"

private class FakePlatformFormat(private val tag: String, private val useArabicIndic: Boolean) : PlatformFormat {
    override fun languageTag() = tag
    override fun localizedDigits(number: Int): String =
        if (useArabicIndic) number.toString().map { ARABIC_INDIC[it - '0'] }.joinToString("") else number.toString()
    override fun clockTime(hour: Int, minute: Int): String = "${localizedDigits(hour)}:${localizedDigits(minute)}"
}

class CountdownFormatterTest {

    @Test
    fun egyptianArabicUsesArabicIndicDigitsWhenTabularIsVerified() {
        val format = FakePlatformFormat("ar-EG", useArabicIndic = true)
        assertEquals("١:٠٥:٠٠", CountdownFormatter.countdown(65.minutes, format, tabularDigitsVerified = true))
    }

    @Test
    fun egyptianArabicFallsBackToWesternDigitsWhenTabularIsNotVerified() {
        val format = FakePlatformFormat("ar-EG", useArabicIndic = true)
        assertEquals("1:05:00", CountdownFormatter.countdown(65.minutes, format, tabularDigitsVerified = false))
    }

    @Test
    fun libyanArabicNeverFallsBackBecauseItAlreadyDefaultsToWestern() {
        val format = FakePlatformFormat("ar-LY", useArabicIndic = false)
        assertEquals("1:05:00", CountdownFormatter.countdown(65.minutes, format, tabularDigitsVerified = false))
    }

    @Test
    fun englishIsUnaffectedByTheFallbackFlagEitherWay() {
        val format = FakePlatformFormat("en", useArabicIndic = false)
        assertEquals("1:05:00", CountdownFormatter.countdown(65.minutes, format, tabularDigitsVerified = false))
        assertEquals("1:05:00", CountdownFormatter.countdown(65.minutes, format, tabularDigitsVerified = true))
    }

    @Test
    fun minutesAreAlwaysTwoDigitsEvenWhenSingleDigit() {
        val format = FakePlatformFormat("en", useArabicIndic = false)
        assertEquals("0:05:00", CountdownFormatter.countdown(5.minutes, format, tabularDigitsVerified = true))
    }

    @Test
    fun secondsAreShownSoAShortCountdownCannotBeReadAsSeconds() {
        val format = FakePlatformFormat("en", useArabicIndic = false)
        assertEquals("0:20:15", CountdownFormatter.countdown(20.minutes + 15.seconds, format, tabularDigitsVerified = true))
        assertEquals("3:00:59", CountdownFormatter.countdown(3.hours + 59.seconds, format, tabularDigitsVerified = true))
        assertEquals("10:00:00", CountdownFormatter.countdown(10.hours, format, tabularDigitsVerified = true))
    }

    @Test
    fun secondsTruncateRatherThanRoundAndNeverGoNegative() {
        val format = FakePlatformFormat("en", useArabicIndic = false)
        assertEquals("0:00:59", CountdownFormatter.countdown(59_900.milliseconds, format, tabularDigitsVerified = true))
        assertEquals("0:00:00", CountdownFormatter.countdown((-5).seconds, format, tabularDigitsVerified = true))
    }

    @Test
    fun defaultsToArabicIndicDigitsIsExactlyTheSpecsTwoLocales() {
        assertEquals(true, CountdownFormatter.defaultsToArabicIndicDigits("ar-EG"))
        assertEquals(true, CountdownFormatter.defaultsToArabicIndicDigits("ar-SA"))
        listOf("ar-LY", "ar-MA", "ar-TN", "ar-DZ", "ar", "en").forEach {
            assertEquals(false, CountdownFormatter.defaultsToArabicIndicDigits(it), it)
        }
    }
}
