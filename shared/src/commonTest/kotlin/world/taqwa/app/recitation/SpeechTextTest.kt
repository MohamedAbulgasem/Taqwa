package world.taqwa.app.recitation

import world.taqwa.app.quran.TextKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Read-aloud spec §3, rule by rule, on the bundled database's own sentences. */
class SpeechTextTest {

    private fun translation(language: String, text: String, surah: Int = 1, ayah: Int = 1) =
        SpeechText.speakable(TextKind.TRANSLATION, language, surah, ayah, text)

    private fun tafsir(surah: Int, ayah: Int, text: String) =
        SpeechText.speakable(TextKind.TAFSIR, "ar", surah, ayah, text)

    @Test
    fun `saheeh's brackets are read as words and its trailing dash is dropped`() {
        assertEquals(
            "All praise is due to Allah, Lord of the worlds",
            translation("en", "[All] praise is [due] to Allah, Lord of the worlds -"),
        )
    }

    @Test
    fun `urdu honorifics keep their words without the ornate brackets`() {
        assertEquals(
            "(حضرت) آدم علیہ السلام سے فرمایا",
            translation("ur", "(حضرت) آدم ﴿علیہ السلام﴾ سے فرمایا"),
        )
    }

    @Test
    fun `a verse quoted in braces is dropped with its reference`() {
        assertEquals(
            "وهي قوله تعالى: فتاب الله عليه.",
            tafsir(2, 37, "وهي قوله تعالى: {رَبَّنَا ظَلَمْنَا أَنْفُسَنَا (7:23)}، فتاب الله عليه."),
        )
    }

    @Test
    fun `a fully voweled quotation of the ayah is dropped`() {
        assertEquals(
            "الثناء على الله بصفاته.",
            tafsir(1, 2, "(الحَمْدُ للهِ رَبِّ العَالَمِينَ) الثناء على الله بصفاته."),
        )
    }

    @Test
    fun `a lightly voweled name stays because it is the sentence's subject`() {
        assertEquals("(اللهِ) علم على الرب", tafsir(1, 1, "(اللهِ) علم على الرب"))
    }

    @Test
    fun `an explanation in parentheses is read`() {
        assertEquals("(وهم المسلمون) والذين هادوا", tafsir(5, 69, "(وهم المسلمون) والذين هادوا"))
    }

    @Test
    fun `a surah's opening letters are dropped at its opening`() {
        assertEquals(
            "سبق الكلام على الحروف المقطَّعة في أول سورة البقرة.",
            tafsir(10, 1, "(الر) سبق الكلام على الحروف المقطَّعة في أول سورة البقرة."),
        )
        assertEquals("سبق الكلام.", tafsir(42, 1, "(حم * عسق) سبق الكلام."))
        assertEquals("سبق الكلام.", tafsir(29, 1, "الم: سبق الكلام."))
    }

    @Test
    fun `the same letters elsewhere are read`() {
        assertEquals("(الم) كلمة", tafsir(5, 1, "(الم) كلمة"))
        assertEquals("(الله) ربنا", tafsir(2, 1, "(الله) ربنا"))
    }

    @Test
    fun `bengali's mangled character references are repaired`() {
        assertEquals("চিহিত ঘোড়ার", translation("bn", "চিহিߦ#2468; ঘোড়ার"))
        assertEquals("কতৃক নির্ধারিত", translation("bn", "কতৃꦣ2453; নির্ধারিত"))
        assertEquals("প্রজ্জিত করে", translation("bn", "প্রজ্জ?482;িত করে").replace("িত", "িত"))
    }

    @Test
    fun `other languages keep their digits`() {
        assertEquals("Kami angkat 12 orang pemimpin", translation("id", "Kami angkat 12 orang pemimpin"))
    }

    @Test
    fun `transliteration is never read`() {
        assertEquals("", SpeechText.speakable(TextKind.TRANSLITERATION, "en", 1, 1, "Bismi Allahi"))
        assertEquals(emptyMap(), SpeechText.prepare(TextKind.TRANSLITERATION, "en", 1, mapOf(1 to "Bismi")))
    }

    @Test
    fun `a run of ayahs sharing one text is read once after its last ayah`() {
        assertEquals(
            mapOf(124 to "Aynı metin.", 125 to "Başka."),
            SpeechText.prepare(TextKind.TRANSLATION, "tr", 26, mapOf(123 to "Aynı metin.", 124 to "Aynı metin.", 125 to "Başka.")),
        )
    }

    @Test
    fun `the same text on ayahs that are not neighbours is read twice`() {
        assertEquals(
            mapOf(1 to "Same.", 3 to "Same."),
            SpeechText.prepare(TextKind.TRANSLATION, "en", 1, mapOf(1 to "Same.", 3 to "Same.")),
        )
    }

    @Test
    fun `a text that is all quotation leaves nothing to read`() {
        assertEquals(emptyMap(), SpeechText.prepare(TextKind.TAFSIR, "ar", 1, mapOf(1 to "(الرَّحْمَنِ)")))
    }

    @Test
    fun `an overlong text is cut at a sentence end`() {
        val long = "Sentence one is here. ".repeat(300)
        val spoken = translation("en", long)
        assertTrue(spoken.length <= SpeechText.MAX_CHARS, "length ${spoken.length}")
        assertTrue(spoken.endsWith("."))
    }

    @Test
    fun `the estimate is characters over the language's rate and at least a second`() {
        assertEquals(10_000L, SpeechText.estimateMs("a".repeat(160), "en"))
        assertEquals(10_000L, SpeechText.estimateMs("a".repeat(130), "ar"))
        assertEquals(1_000L, SpeechText.estimateMs("short", "en"))
    }
}
