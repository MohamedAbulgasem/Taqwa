package world.taqwa.timetables

import world.taqwa.app.domain.Prayer
import world.taqwa.app.prayer.engine.method.AsrSchool
import world.taqwa.app.prayer.engine.registry.Registry
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
    fun timetableNamesComeFromTheApp() {
        assertEquals("Muslim World League", strings.timetable("en", Registry.byId("other.mwl")!!))
        assertEquals("رابطة العالم الإسلامي", strings.timetable("ar", Registry.byId("other.mwl")!!))
        assertEquals("Diyanet (Türkiye)", strings.timetable("en", Registry.byId("other.turkey")!!))
    }

    @Test
    fun everyOtherMethodAndSchoolAndPrayerHasANameInEveryLanguage() {
        val others = Registry.otherMethods.filter { it.id.startsWith("other.") }
        for (language in listOf("en", "ar", "fr", "tr", "id", "ur", "bn")) {
            others.forEach { assertTrue(strings.timetable(language, it)?.isNotBlank() == true, "${it.id} in $language") }
            AsrSchool.entries.forEach { assertTrue(strings.school(language, it).isNotBlank()) }
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
