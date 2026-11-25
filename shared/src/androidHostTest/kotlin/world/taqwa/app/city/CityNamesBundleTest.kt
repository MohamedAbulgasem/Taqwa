package world.taqwa.app.city

import kotlinx.coroutines.test.runTest
import java.io.File
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the six bundled `city-names-<lang>.csv` files against `cities.csv` itself (design spec
 * §7): every translated name must join onto a city that is actually in the bundle, no id may
 * appear twice, and the handful of cities the design was argued over must resolve to the names
 * it quotes.
 *
 * Lives in `androidHostTest` — the module's JVM unit test source set, run by
 * `:shared:testAndroidHostTest` — because it reads the real resource files. Compose resources are
 * not on a plain JVM unit test's classpath, so it reads them from the file system relative to the
 * module, exactly as [world.taqwa.app.quran.QuranRepositoryDbTest] reads the bundled `quran.db`.
 */
class CityNamesBundleTest {

    private val languages = listOf("ar", "id", "ur", "bn", "tr", "fr")

    private fun resource(name: String): File {
        val moduleRelative = File("src/commonMain/composeResources/files/$name")
        if (moduleRelative.exists()) return moduleRelative
        val rootRelative = File("shared/src/commonMain/composeResources/files/$name")
        if (rootRelative.exists()) return rootRelative
        error("$name not found at either ${moduleRelative.absolutePath} or ${rootRelative.absolutePath}")
    }

    /** `id,name` — the name may itself contain an Arabic comma (U+060C), so split on the first only. */
    private fun namePairs(language: String): List<Pair<Int, String>> =
        resource("city-names-$language.csv").readText().lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val comma = line.indexOf(',')
                assertTrue(comma > 0, "malformed line in city-names-$language.csv: $line")
                val id = line.substring(0, comma).toIntOrNull()
                assertTrue(id != null, "non-numeric id in city-names-$language.csv: $line")
                id!! to line.substring(comma + 1)
            }
            .toList()

    private val cityIds: Set<Int> by lazy {
        resource("cities.csv").readText().lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.substringBefore(',').toInt() }
            .toSet()
    }

    private val englishNames: List<String> by lazy {
        resource("cities.csv").readText().lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",")[1] }
            .toList()
    }

    private fun repository() = CityRepository(
        loadCsv = { resource("cities.csv").readText() },
        loadNames = { language -> resource("city-names-$language.csv").readText() },
    )

    @Test
    fun citiesCsvLeadsWithNumericIdsAndHasNoDuplicates() {
        val ids = resource("cities.csv").readText().lineSequence()
            .drop(1).filter { it.isNotBlank() }
            .map { it.substringBefore(',').toInt() }
            .toList()
        assertTrue(ids.size > 30_000, "the bundle should still hold the full cities15000 extract, got ${ids.size}")
        assertEquals(ids.size, ids.toSet().size, "cities.csv has duplicate ids")
    }

    @Test
    fun everyTranslatedNameJoinsOntoACityInTheBundle() {
        for (language in languages) {
            val pairs = namePairs(language)
            assertTrue(pairs.isNotEmpty(), "city-names-$language.csv is empty")
            val orphans = pairs.map { it.first }.filterNot { it in cityIds }
            assertTrue(orphans.isEmpty(), "city-names-$language.csv has ids not in cities.csv: ${orphans.take(5)}")
        }
    }

    @Test
    fun noNameFileRepeatsAnId() {
        for (language in languages) {
            val ids = namePairs(language).map { it.first }
            assertEquals(ids.size, ids.toSet().size, "city-names-$language.csv has duplicate ids")
        }
    }

    @Test
    fun noTranslatedNameIsBlank() {
        for (language in languages) {
            val blanks = namePairs(language).filter { it.second.isBlank() }
            assertTrue(blanks.isEmpty(), "city-names-$language.csv has blank names: ${blanks.take(5)}")
        }
    }

    @Test
    fun theCitiesTheDesignArguesOverResolveToTheirExpectedNames() = runTest {
        val expected = mapOf(
            "ar" to mapOf(
                3369157 to "كيب تاون",
                2643743 to "لندن",
                2210247 to "طرابلس",
                745044 to "اسطنبول",
                1642911 to "جاكارتا",
            ),
            "tr" to mapOf(2643743 to "Londra", 2210247 to "Trablus", 745044 to "İstanbul"),
            "fr" to mapOf(3369157 to "Le Cap", 2643743 to "Londres", 2210247 to "Tripoli"),
            "bn" to mapOf(745044 to "ইস্তাম্বুল", 1642911 to "জাকার্তা"),
            "id" to mapOf(1642911 to "Jakarta", 745044 to "Istanbul"),
        )
        for ((language, names) in expected) {
            val file = namePairs(language).toMap()
            for ((id, name) in names) {
                assertEquals(name, file[id], "city-names-$language.csv, id $id")
            }
        }
    }

    /**
     * The names `tools/build-city-db.py` writes over GeoNames' own (its `NAME_FIXES`), each checked on
     * 25 Nov 2026 against the language's Wikidata label and Wikipedia title: GeoNames had an Ottoman form,
     * a misspelling, the English name, a name in Latin letters in an Arabic, Urdu or Bengali file, the
     * country's name for its capital, a district's or province's name (Sharjah's emirate, Yogyakarta's
     * special region, Indonesia's "Kota" for its cities), a name without its article or with a mark on every
     * letter, a state or country after the city's name, or no name at all. The site titles its city pages
     * with these names, city then country, and its indexes name other cities by them, so a rebuild that
     * lost the table would title Algiers' Arabic page with the country's name twice.
     */
    @Test
    fun theNamesFixedOverGeoNamesStayFixed() {
        val expected = mapOf(
            "ar" to mapOf(
                89055 to "البيضاء",
                89570 to "زاخو",
                90026 to "طوز خورماتو",
                95788 to "هيت",
                101554 to "تاروت",
                107304 to "بريدة",
                109223 to "المدينة المنورة",
                110250 to "عفيف",
                163915 to "صوران",
                165060 to "صافيتا",
                172082 to "أعزاز",
                248299 to "ماحص",
                250090 to "الزرقاء",
                267008 to "صور",
                281715 to "صوريف",
                286245 to "صور",
                286282 to "صحار",
                287830 to "عبري",
                292672 to "الشارقة",
                293253 to "يافا",
                314830 to "غازي عنتاب",
                347236 to "طوخ",
                357039 to "فايد",
                359678 to "الطور",
                359796 to "السويس",
                360761 to "المنصورة",
                932035 to "تياتيانينغ",
                1622786 to "ماكاسار",
                1655078 to "موانج إكساي",
                1655123 to "فونسافان",
                1735106 to "جورج تاون",
                2210554 to "سرت",
                2219905 to "الخمس",
                2464470 to "تونس العاصمة",
                2507480 to "الجزائر العاصمة",
                2538475 to "الرباط",
                3353383 to "روندو",
                6167865 to "تورنتو",
            ),
            "bn" to mapOf(
                250090 to "জারকা",
                306571 to "কোনিয়া",
                347497 to "তান্তা",
                358619 to "সাইদ বন্দর",
                359783 to "আসিউত",
                359792 to "আসওয়ান",
                359796 to "সুয়েজ",
                360502 to "লাক্সর",
                360761 to "মনসুরা",
                360995 to "গিজা",
                1007311 to "ডারবান",
                1185092 to "ঠাকুরগাঁও",
                1185095 to "টেকনাফ",
                1185141 to "পঞ্চগড়",
                1185148 to "পটিয়া",
                1185149 to "পার্বতীপুর",
                1185160 to "নাগেশ্বরী",
                1185167 to "মোড়লগঞ্জ",
                1185173 to "মঠবাড়িয়া",
                1185181 to "লালমনিরহাট",
                1185191 to "কুষ্টিয়া",
                1185199 to "কালিগঞ্জ",
                1185204 to "ঝিকরগাছা",
                1185206 to "জয়পুরহাট",
                1185239 to "শরণখোলা",
                1185247 to "চিলমারী",
                1185251 to "লালমোহন",
                1185260 to "অভয়নগর",
                1185262 to "ভেড়ামারা",
                1185270 to "বান্দরবান",
                1185272 to "কালিয়া",
                1189638 to "রাউজান",
                1203878 to "দোহাজারী",
                1210565 to "বোরহানউদ্দিন",
                1214520 to "মেদান",
                1215502 to "বান্দা আচেহ",
                1275004 to "কলকাতা",
                1336133 to "সাতকানিয়া",
                1336134 to "কক্সবাজার",
                1336135 to "খুলনা",
                1336139 to "রাঙ্গামাটি",
                1336140 to "যশোর",
                1337240 to "চাঁপাইনবাবগঞ্জ",
                1462681 to "মানিকছড়ি",
                1477498 to "ভান্ডারিয়া",
                1621177 to "যোগ্যকার্তা",
                1622786 to "মাকাসার",
                1625822 to "সুরাবায়া",
                1627896 to "সেমারাং",
                1633070 to "পালেমবাং",
                1650357 to "বানদুং",
                1880252 to "সিঙ্গাপুর",
                2464470 to "তিউনিস",
                2803138 to "আন্টভের্প",
                3143244 to "অসলো",
                6075357 to "মিসিসগা",
                6167865 to "টরন্টো",
                7483813 to "নাটোর",
            ),
            "fr" to mapOf(
                359783 to "Assiout",
                1736376 to "Kota Bharu",
            ),
            "id" to mapOf(
                359783 to "Asyut",
                1214520 to "Medan",
                1215502 to "Banda Aceh",
                1621177 to "Yogyakarta",
                1622786 to "Makassar",
                1625822 to "Surabaya",
                1627896 to "Semarang",
                1633070 to "Palembang",
                1650357 to "Bandung",
                1735106 to "George Town",
                1880252 to "Singapura",
                2800866 to "Brussel",
            ),
            "tr" to mapOf(
                107304 to "Büreyde",
                250090 to "Zerka",
                359783 to "Asyut",
                360995 to "Gize",
                1642911 to "Cakarta",
                1880252 to "Singapur",
                2467454 to "Sefakus",
                2553604 to "Kazablanka",
                2561668 to "Agadir",
                2803138 to "Anvers",
            ),
            "ur" to mapOf(
                101628 to "تبوک",
                223817 to "جبوتی شہر",
                250090 to "زرقاء",
                286245 to "صور",
                286621 to "صلالہ",
                347497 to "طنطا",
                358619 to "پورٹ سعید",
                359783 to "اسیوط",
                360761 to "منصورہ",
                360995 to "جیزہ",
                932035 to "تئیاتئیانگ",
                1214520 to "مدان",
                1215502 to "باندا آچے",
                1273294 to "دہلی",
                1621177 to "یوگیاکارتا",
                1622786 to "ماکاسار",
                1625822 to "سورابایا",
                1627896 to "سیمارانگ",
                1735106 to "جارج ٹاؤن",
                1880252 to "سنگاپور",
                2210247 to "طرابلس",
                2219905 to "خمس",
                2530335 to "طنجہ",
                2800866 to "برسلز",
                2803138 to "اینٹورپن",
                4180439 to "اٹلانٹا",
                4560349 to "فلاڈیلفیا",
                4990510 to "ڈئربورن",
            ),
        )
        for ((language, names) in expected) {
            val file = namePairs(language).toMap()
            for ((id, name) in names) {
                assertEquals(name, file[id], "city-names-$language.csv, id $id")
            }
        }
    }

    @Test
    fun arabicTripoliCarriesNoHarakat() {
        val tripoli = namePairs("ar").toMap().getValue(2210247)
        assertEquals("طرابلس", tripoli)
        assertEquals(tripoli, CityText.fold(tripoli), "the stored Arabic name should already be bare")
    }

    @Test
    fun theRealBundleSearchesInArabic() = runTest {
        val repo = repository()
        repo.setLanguage("ar-LY")

        val capeTown = repo.search("كيب").first { it.name == "Cape Town" }
        assertEquals("كيب تاون", capeTown.displayName)

        val tripoli = repo.search("طرابلس").first { it.countryCode == "LY" }
        assertEquals("Tripoli", tripoli.name)
        assertEquals("طرابلس", tripoli.displayName)

        // English keeps working under an Arabic interface, and shows the Arabic name.
        assertEquals("لندن", repo.search("london").first().displayName)
    }

    @Test
    fun theRealBundleFoldsTurkishAndFrenchNames() = runTest {
        val repo = repository()
        repo.setLanguage("tr-TR")
        assertEquals("İstanbul", repo.search("istanbul").first().displayName)

        repo.setLanguage("fr-FR")
        assertEquals("Le Cap", repo.search("le cap").first { it.name == "Cape Town" }.displayName)
        assertEquals("Londres", repo.search("londres").first().displayName)
    }

    /**
     * The regression guard for the whole class of bug the curated fold table was: if a bundled
     * English name still carries a letter no rule reaches, that city cannot be found by typing
     * its name on a plain keyboard. The curated map left 83 such letters in 4,559 places —
     * Thāne (1.8 M people), Ōta, Niš. Folding must leave nothing but ASCII and spaces behind.
     */
    @Test
    fun noBundledEnglishNameKeepsANonAsciiLetterAfterFolding() {
        val offenders = mutableMapOf<Char, String>()
        for (name in englishNames) {
            for (ch in CityText.fold(name)) {
                if (ch.code > 0x7F) offenders.putIfAbsent(ch, name)
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "cities.csv names still fold to non-ASCII: " +
                offenders.entries.take(10).joinToString { (ch, name) ->
                    "'" + ch + "' (U+" + ch.code.toString(16).uppercase() + ") in " + name
                },
        )
    }

    /** The five names the amended §4 rules were written for, found by what a searcher types. */
    @Test
    fun theRealBundleFindsTheNamesTheCuratedFoldingMissed() = runTest {
        val repo = repository()
        assertEquals("Th\u0101ne", repo.search("thane").first().name)
        assertEquals("\u014Cta", repo.search("ota").first { it.countryCode == "JP" }.name)
        assertEquals("Ni\u0161", repo.search("nis").first { it.countryCode == "RS" }.name)
        assertEquals("Xi\u2019an", repo.search("xian").first { it.countryCode == "CN" }.name)
        assertTrue(
            repo.search("saint denis", limit = 200).any { it.name.startsWith("Saint-Denis") },
            "a hyphenated name must be reachable by typing a space",
        )
    }

    @Test
    fun anEnglishInterfaceShowsTheEnglishNames() = runTest {
        val repo = repository()
        assertEquals("London", repo.search("london").first().displayName)
        assertEquals("Cape Town", repo.search("cape town").first().displayName)
    }

    @Test
    fun everyNameIsInUnicodesComposedForm() {
        // The city build writes NFC (tools/build-city-db.py): GeoNames had Bengali nukta letters precomposed and Arabic
        // marks out of canonical order, so the same name could be two strings.
        for (language in listOf("ar", "bn", "fr", "id", "tr", "ur")) {
            val bad = namePairs(language).filterNot { (_, name) -> Normalizer.isNormalized(name, Normalizer.Form.NFC) }
            assertTrue(bad.isEmpty(), "city-names-$language.csv: $bad")
        }
    }
}
