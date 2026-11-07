package world.taqwa.app.quran

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import world.taqwa.app.feature.quran.followingLines
import world.taqwa.app.feature.quran.followingWord
import world.taqwa.app.feature.quran.placeOf
import world.taqwa.app.widget.HafsSpelling
import world.taqwa.app.widget.QuranPlace

/**
 * The whole bundled Quran as the app draws it, built through the app's own code: [QuranRepository]
 * reads the database and gives each ayah and page what is read after it, the Mushaf's
 * [followingLines] and [followingWord] give each line and word theirs, [placeOf] each word its place,
 * and [HafsSpelling.forDrawing] draws them. Loaded once per test JVM.
 */
internal object DrawnQuran {

    private val repo = QuranRepository(
        driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
        io = Dispatchers.Unconfined,
    )

    /** Every ayah in reading order, with [Ayah.followedBy] as the repository gives it. */
    val ayahs: List<Ayah> by lazy { runBlocking { (1..114).flatMap { repo.ayahs(it) } } }

    /** Each ayah drawn, as the Reader, search, the saved list, Adhkar and the widgets draw it. */
    val drawnAyahs: List<String> by lazy { ayahs.map { HafsSpelling.forDrawing(it.text, it.followedBy, QuranPlace(it.surah, it.number)) } }

    /** The basmala every surah but 1 and 9 opens with: 1:1, as the Mushaf and the Reader take it. */
    val basmala: String by lazy { ayahs.first().text }

    val pages: List<MushafPage> by lazy { runBlocking { (1..604).map { repo.page(it) } } }

    /** One Mushaf word as the page draws it. */
    data class DrawnWord(val page: Int, val line: Int, val word: LineWord, val drawn: String)

    /** One Mushaf text line drawn whole, as the page measures it. */
    data class DrawnLine(val page: Int, val line: MushafLine, val drawn: String, val followedBy: String?)

    val drawnLines: List<DrawnLine> by lazy {
        pages.flatMap { page ->
            val following = followingLines(page, basmala)
            page.lines.mapIndexedNotNull { index, line ->
                if (line.type != LineType.TEXT) null else DrawnLine(page.number, line, HafsSpelling.forDrawing(line.text.orEmpty(), following[index], placeOf(line.words.first())), following[index])
            }
        }
    }

    val drawnWords: List<DrawnWord> by lazy {
        pages.flatMap { page ->
            val following = followingLines(page, basmala)
            page.lines.flatMapIndexed { index, line ->
                if (line.type != LineType.TEXT) {
                    emptyList()
                } else {
                    line.words.mapIndexed { i, word ->
                        DrawnWord(page.number, line.line, word, HafsSpelling.forDrawing(word.text, followingWord(line, i, following[index]), placeOf(word)))
                    }
                }
            }
        }
    }
}
