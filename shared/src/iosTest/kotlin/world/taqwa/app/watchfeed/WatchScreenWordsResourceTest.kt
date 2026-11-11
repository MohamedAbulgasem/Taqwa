package world.taqwa.app.watchfeed

import kotlinx.coroutines.test.runTest
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.watch.WatchScreenWords
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `watchScreenWords()`'s default reads the phone's own strings through Compose Multiplatform's
 * resource environment, off any composition, as the sync will. The simulator runs these tests in
 * English, as the other iOS tests that read resources do, so the answer is the English words, which
 * the host's stand-in gives too (`WatchScreenWordsReaderTest.bundledEnglishIsTheEnglishDefault`).
 */
class WatchScreenWordsResourceTest {

    @Test
    fun theDefaultReadsThePhonesOwnStrings() = runTest {
        assertEquals(WatchScreenWords.ENGLISH, watchScreenWords(UiLanguage.ENGLISH))
    }
}
