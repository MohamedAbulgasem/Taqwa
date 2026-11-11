package world.taqwa.app.wear

import java.io.File

/**
 * The longest city name the phone can send the watch as `place.city`: the name with the most UTF-8
 * bytes in the phone's own bundle, `cities.csv` and its translations `city-names-*.csv`
 * (`shared/src/commonMain/composeResources/files`), which `watchCityName` names a city from. Gradle
 * hands the directory in (`taqwa.phoneCities`) and counts its files as the tests' inputs
 * (`wearApp/build.gradle.kts`, Ruling 68 item 2 (c)), as `FontCopiesTest`'s phone fonts are, so
 * the tests that lay it out (Today's list, the Tile) run again as the bundle changes; a bare JVM
 * run reads it from the module directory. On 24 October it is a Bengali name of 33 characters and
 * 91 bytes.
 *
 * The files hold no quoted field, so a line is split on its commas: the name is the second
 * column of each (`id,name,…` and `id,name`).
 */
internal object LongestCity {

    private val files = File(System.getProperty("taqwa.phoneCities") ?: "../shared/src/commonMain/composeResources/files")

    fun name(): String {
        val sources = files.listFiles { f -> f.name == "cities.csv" || (f.name.startsWith("city-names-") && f.name.endsWith(".csv")) }
            .orEmpty()
        check(sources.any { it.name == "cities.csv" } && sources.size > 1) { "the phone's city files are not in ${files.absolutePath}" }
        return sources.asSequence()
            .flatMap { it.readLines().asSequence().drop(1) }
            .mapNotNull { line -> line.split(',').getOrNull(1) }
            .maxBy { it.encodeToByteArray().size }
    }
}
