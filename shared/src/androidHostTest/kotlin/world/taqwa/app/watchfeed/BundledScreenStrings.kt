package world.taqwa.app.watchfeed

import kotlinx.coroutines.runBlocking
import org.w3c.dom.Element
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchScreenWords
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The host's stand-in for `ResourceScreenStrings`, which reads the phone's strings through
 * Compose's resource environment, and a plain JVM test has none, nor any way to ask it for another
 * language. It reads each key from [language]'s own `strings.xml` in the Compose resources, and
 * from `values/` for a key that folder lacks, as Compose falls back; a plural's item is picked by
 * [cldrItem], and a missing item falls back to `other`. The simulator's
 * `WatchScreenWordsResourceTest` holds the real resource path to the same English
 * (`bundledEnglishIsTheEnglishDefault` ties the two together).
 */
internal object BundledScreenStrings {

    fun of(language: UiLanguage): ScreenStrings = Strings(language)

    /** [language]'s words as the phone sends them, read through [of]. */
    fun words(language: UiLanguage): WatchScreenWords = runBlocking { watchScreenWords(language, of(language)) }

    private class Strings(private val language: UiLanguage) : ScreenStrings {
        private val own: Element by lazy { read(if (language == UiLanguage.ENGLISH) "values" else "values-${language.code}") }
        private val fallback: Element by lazy { read("values") }

        override suspend fun text(key: String): String =
            own.string(key) ?: fallback.string(key) ?: error("no string $key")

        override suspend fun counted(key: String, n: Int): String {
            val items = own.plural(key) ?: fallback.plural(key) ?: error("no plurals $key")
            return items[cldrItem(language, n)] ?: items.getValue("other")
        }
    }

    private fun read(directory: String): Element {
        val moduleRelative = File("src/commonMain/composeResources")
        val resources = if (moduleRelative.isDirectory) moduleRelative else File("shared/src/commonMain/composeResources")
        val file = File(resources, "$directory/strings.xml")
        check(file.exists()) { "no $file" }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.string(name: String): String? {
        val nodes = getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") == name) return e.textContent
        }
        return null
    }

    private fun Element.plural(name: String): Map<String, String>? {
        val nodes = getElementsByTagName("plurals")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") != name) continue
            val items = e.getElementsByTagName("item")
            return (0 until items.length).associate {
                val item = items.item(it) as Element
                item.getAttribute("quantity") to item.textContent
            }
        }
        return null
    }
}
