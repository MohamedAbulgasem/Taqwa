package world.taqwa.app.watchfeed

import kotlinx.coroutines.runBlocking
import org.w3c.dom.Element
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchSpoken
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The host's stand-in for `watchSpoken()`, which reads the phone's strings through Compose's
 * resource environment, and a plain JVM test has none. It reads the same three templates and two
 * plurals from [language]'s own `strings.xml`, picks each number's plural item by CLDR's rule for
 * the language, and keeps the runs with `countRuns`, through `watchSpoken` itself. A missing item
 * falls back to `other`, as Compose's does. Task 1's simulator test and Task 7's check 4 hold the
 * real resource path to the same words.
 */
internal object BundledSpoken {

    fun of(language: UiLanguage): WatchSpoken = runBlocking { watchSpoken(Strings(language)) }

    private class Strings(private val language: UiLanguage) : SpokenStrings {
        private val xml: Element by lazy { read(language) }

        override suspend fun countdown(): String = xml.string("prayer_a11y_countdown")
        override suspend fun countdownSoon(): String = xml.string("prayer_a11y_countdown_soon")
        override suspend fun hoursMinutes(): String = xml.string("duration_hours_minutes")
        override suspend fun hours(n: Int): String = xml.plural("a11y_hours", cldrItem(language, n))
        override suspend fun minutes(n: Int): String = xml.plural("a11y_minutes", cldrItem(language, n))
    }

    private fun read(language: UiLanguage): Element {
        val directory = if (language == UiLanguage.ENGLISH) "values" else "values-${language.code}"
        val moduleRelative = File("src/commonMain/composeResources")
        val resources = if (moduleRelative.isDirectory) moduleRelative else File("shared/src/commonMain/composeResources")
        val file = File(resources, "$directory/strings.xml")
        check(file.exists()) { "no $file" }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.string(name: String): String {
        val nodes = getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") == name) return e.textContent
        }
        error("no string $name")
    }

    private fun Element.plural(name: String, quantity: String): String {
        val nodes = getElementsByTagName("plurals")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("name") != name) continue
            val items = e.getElementsByTagName("item")
            val byQuantity = (0 until items.length).associate {
                val item = items.item(it) as Element
                item.getAttribute("quantity") to item.textContent
            }
            return byQuantity[quantity] ?: byQuantity.getValue("other")
        }
        error("no plurals $name")
    }
}

/**
 * CLDR's plural category of [n], a whole number, in [language]: the item of a `<plurals>` a host
 * test's stand-in picks, as Compose picks it on the phone. [BundledSpoken] and
 * `BundledScreenStrings` both pick by it.
 */
internal fun cldrItem(language: UiLanguage, n: Int): String = when (language) {
    UiLanguage.ENGLISH, UiLanguage.TURKISH, UiLanguage.URDU -> if (n == 1) "one" else "other"
    UiLanguage.FRENCH, UiLanguage.BENGALI -> if (n == 0 || n == 1) "one" else "other"
    UiLanguage.INDONESIAN -> "other"
    UiLanguage.ARABIC -> when {
        n == 0 -> "zero"
        n == 1 -> "one"
        n == 2 -> "two"
        n % 100 in 3..10 -> "few"
        n % 100 in 11..99 -> "many"
        else -> "other"
    }
}
