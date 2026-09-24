package world.taqwa.timetables

import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The site says what the app says: these read the app's own translations, not copies. */
class AppStringsTest {

    private val strings = AppStrings(TestPaths.appResources)

    @Test
    fun prayerNamesComeFromTheAppInEachLanguage() {
        assertEquals("Dhuhr", strings.prayer("en", Prayer.DHUHR))
        assertEquals("الظهر", strings.prayer("ar", Prayer.DHUHR))
        assertEquals("ظہر", strings.prayer("ur", Prayer.DHUHR))
    }

    @Test
    fun methodNamesComeFromTheApp() {
        assertEquals("Muslim World League", strings.method("en", CalculationMethodId.MUSLIM_WORLD_LEAGUE))
        assertEquals("رابطة العالم الإسلامي", strings.method("ar", CalculationMethodId.MUSLIM_WORLD_LEAGUE))
        assertEquals("Diyanet (Türkiye)", strings.method("en", CalculationMethodId.TURKEY))
    }

    @Test
    fun everyMethodAndMadhabAndRuleHasANameInEveryLanguage() {
        for (language in listOf("en", "ar", "fr", "tr", "id", "ur", "bn")) {
            CalculationMethodId.entries.forEach { assertTrue(strings.method(language, it).isNotBlank()) }
            AsrMadhab.entries.forEach { assertTrue(strings.madhab(language, it).isNotBlank()) }
            HighLatitudePreference.entries.forEach { assertTrue(strings.highLatitude(language, it).isNotBlank()) }
            Prayer.entries.forEach { assertTrue(strings.prayer(language, it).isNotBlank()) }
        }
    }

    @Test
    fun placeholdersAreFilledInOrder() {
        assertEquals("109° · 2,916 km to Makkah", strings.format("en", "today_qibla_detail", "109", "2,916"))
    }

    @Test
    fun entitiesAndAndroidEscapesAreDecoded() {
        val dir = createTempDirectory("strings").toFile()
        File(dir, "values").mkdirs()
        File(dir, "values/strings.xml").writeText(
            """<resources><string name="a">Alarms &amp; reminders</string><string name="b">It\'s \"here\"</string></resources>""",
        )
        val fixture = AppStrings(dir)
        assertEquals("Alarms & reminders", fixture.get("en", "a"))
        assertEquals("It's \"here\"", fixture.get("en", "b"))
    }

    @Test
    fun aMissingKeyNamesTheKeyAndTheLanguage() {
        val error = assertFailsWith<IllegalStateException> { strings.get("fr", "no_such_key") }
        assertTrue(error.message!!.contains("no_such_key") && error.message!!.contains("fr"), error.message)
    }
}
