package world.taqwa.app.watchfeed

import world.taqwa.app.city.CityRepository
import java.io.File

/**
 * The bundled city list as the app reads it, for the host tests: `cities.csv` and its
 * `city-names-<language>.csv`, read straight from the module's Compose resources (as
 * `WatchWordsResourcesTest` reads `strings.xml`), since a plain JVM test has no Compose resource
 * environment. The files are a test input of `:shared` (`bundledFiles` in its build file).
 */
internal object BundledCities {

    private fun files(): File {
        val moduleRelative = File("src/commonMain/composeResources/files")
        return if (moduleRelative.isDirectory) moduleRelative else File("shared/src/commonMain/composeResources/files")
    }

    /** A repository over the bundle, as `AppContainer` builds the app's. */
    fun repository(): CityRepository = CityRepository(
        loadCsv = { File(files(), "cities.csv").readText() },
        loadNames = { language -> File(files(), "city-names-$language.csv").takeIf { it.exists() }?.readText() },
    )

    /** Every name the bundle holds: the list's own, then each language's. */
    fun allNames(): List<String> {
        val listNames = File(files(), "cities.csv").readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",")[1] }
        val translated = files().listFiles { file -> file.name.startsWith("city-names-") && file.name.endsWith(".csv") }
            .orEmpty()
            .sortedBy { it.name }
            .flatMap { file ->
                file.readLines().drop(1).filter { it.isNotBlank() }.map { it.substring(it.indexOf(',') + 1).trim() }
            }
        return listNames + translated
    }
}
