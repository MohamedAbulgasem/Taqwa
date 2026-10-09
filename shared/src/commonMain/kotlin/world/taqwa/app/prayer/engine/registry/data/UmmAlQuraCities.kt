package world.taqwa.app.prayer.engine.registry.data

/**
 * Umm al-Qura's own places: the 173 of KACST's city list, each region's capital, its governorates and five
 * centres, at the coordinates the official prayer-times page passes to KACST's API for each
 * (https://www.ummulqura.org.sa/assets/data/cities.json, fetched 9 Oct 2026; the city-points round). Each place's
 * table is the API's answer there, and each is a unit of sa.ummalqura (ruling R44, `UmmAlQura.units`). Derived
 * facts, never a time: [kacstId] is the list's own id, [key] the unit's id (the monitor's capture key, Makkah and
 * Madinah by those names where KACST writes Mecca and Medina), [name] KACST's English name. Grouped by KACST's
 * region (1001 Riyadh to 1013 Al Jawf), each region's places by KACST's id.
 */
object UmmAlQuraCities {
    class UmmAlQuraCity(val kacstId: Int, val key: String, val name: String, val lat: Double, val lon: Double)

    val all: List<UmmAlQuraCity> = listOf(
        // Riyadh region (1001).
        UmmAlQuraCity(4, "ad-diriyah", "Ad Diriyah", 24.74, 46.58),
        UmmAlQuraCity(5, "afif", "Afif", 23.91, 42.92),
        UmmAlQuraCity(9, "al-aflaj", "Al Aflaj", 22.22, 46.656),
        UmmAlQuraCity(19, "al-ghat", "Al Ghat", 26.03, 44.95),
        UmmAlQuraCity(22, "al-quwaiiyah", "Al Quwaiiyah", 24.05, 45.25),
        UmmAlQuraCity(23, "al-hariq", "Al Hariq", 23.62, 46.52),
        UmmAlQuraCity(28, "al-kharj", "Al Kharj", 24.15, 47.32),
        UmmAlQuraCity(48, "as-sulayyil", "As Sulayyil", 20.47, 45.57),
        UmmAlQuraCity(49, "az-zulfi", "Az Zulfi", 26.3, 44.82),
        UmmAlQuraCity(62, "dhurma", "Dhurma", 24.6, 46.13),
        UmmAlQuraCity(64, "al-duwadimi", "Al Duwadimi", 24.5, 44.38),
        UmmAlQuraCity(71, "howtat-bani-tamim", "Howtat Bani Tamim", 23.49, 46.86),
        UmmAlQuraCity(72, "huraymila", "Huraymila", 25.11, 46.1),
        UmmAlQuraCity(85, "al-majmaah", "Al Majmaah", 25.9, 45.34),
        UmmAlQuraCity(87, "al-muzahimiyah", "Al Muzahimiyah", 24.48, 46.29),
        UmmAlQuraCity(95, "rumah", "Rumah", 25.56, 47.17),
        UmmAlQuraCity(105, "shagra", "Shagra", 25.26, 45.26),
        UmmAlQuraCity(112, "thadig", "Thadig", 25.28, 45.86),
        UmmAlQuraCity(116, "wadi-ad-dawasir", "Wadi ad-Dawasir", 20.4722, 44.783),
        UmmAlQuraCity(150, "al-hayr", "Al Hayr", 24.404568, 46.839516),
        UmmAlQuraCity(151, "ad-dilam", "Ad Dilam", 23.99649, 47.138344),
        UmmAlQuraCity(152, "marat", "Marat", 25.0699, 45.4639),
        UmmAlQuraCity(185, "tumair", "Tumair", 25.7134, 45.8707),
        UmmAlQuraCity(186, "jalajil", "Jalajil", 25.6834, 45.4592),
        UmmAlQuraCity(187, "hawtat-sudayr", "Hawtat Sudayr", 25.5947, 45.6184),
        UmmAlQuraCity(188, "rawdat-sudayr", "Rawdat Sudayr", 25.6187, 45.5543),
        UmmAlQuraCity(190, "layla", "Layla", 22.2899, 46.7188),
        UmmAlQuraCity(191, "al-uyaynah", "Al Uyaynah", 24.9018, 46.3865),
        UmmAlQuraCity(192, "malham", "Malham", 25.1625, 46.3337),
        UmmAlQuraCity(193, "sudus", "Sudus", 24.993744, 46.207878),
        UmmAlQuraCity(197, "ar-rayn", "Ar Rayn", 23.542, 45.5159),
        UmmAlQuraCity(1001, "riyadh", "Riyadh", 24.67, 46.69),
        // Makkah region (1002).
        UmmAlQuraCity(26, "al-jumum", "Al Jumum", 21.63, 39.7),
        UmmAlQuraCity(27, "al-kamil", "Al Kamil", 22.26, 39.79),
        UmmAlQuraCity(29, "al-khurma", "Al Khurma", 21.91, 42.02),
        UmmAlQuraCity(30, "al-lith", "Al Lith", 20.15, 40.28),
        UmmAlQuraCity(66, "al-qunfudhah", "Al Qunfudhah", 19.13, 41.08),
        UmmAlQuraCity(74, "jeddah", "Jeddah", 21.5, 39.17),
        UmmAlQuraCity(82, "khulais", "Khulais", 22.11, 39.31),
        UmmAlQuraCity(93, "rabigh", "Rabigh", 22.79, 39.03),
        UmmAlQuraCity(96, "ranyah", "Ranyah", 21.28, 42.82),
        UmmAlQuraCity(109, "taif", "Taif", 21.25, 40.4),
        UmmAlQuraCity(114, "turbah", "Turbah", 21.23, 41.65),
        UmmAlQuraCity(154, "adham", "Adham", 20.423, 40.8624),
        UmmAlQuraCity(155, "maysan", "Maysan", 20.74756002, 40.85031999),
        UmmAlQuraCity(157, "al-ardhiyat", "Al Ardhiyat", 19.41604388, 41.74531637),
        UmmAlQuraCity(189, "thuwal", "Thuwal", 22.2758, 39.1132),
        UmmAlQuraCity(194, "dhahban", "Dhahban", 21.9309, 39.116),
        UmmAlQuraCity(198, "al-muwayh", "Al Muwayh", 22.4341, 41.7615),
        UmmAlQuraCity(199, "bahrah", "Bahrah", 21.4023, 39.4631),
        UmmAlQuraCity(1002, "makkah", "Mecca", 21.426666, 39.831666),
        // Madinah region (1003).
        UmmAlQuraCity(25, "al-henakiyah", "Al Henakiyah", 24.88, 40.52),
        UmmAlQuraCity(31, "mahd-al-thahab", "Mahd Al Thahab", 23.52, 40.89),
        UmmAlQuraCity(38, "al-ula", "Al Ula", 26.55, 37.96),
        UmmAlQuraCity(50, "badr", "Badr", 23.78, 38.8),
        UmmAlQuraCity(77, "khaybar", "Khaybar", 25.71, 39.28),
        UmmAlQuraCity(118, "yanbu", "Yanbu", 24.09, 38.07),
        UmmAlQuraCity(158, "al-is", "Al 'Is", 25.0598, 38.1167),
        UmmAlQuraCity(200, "wadi-al-fara", "Wadi Al Fara", 23.3127, 39.7776),
        UmmAlQuraCity(1003, "madinah", "Medina", 24.54, 39.63),
        // Qassim region (1004).
        UmmAlQuraCity(14, "alasyah", "Alasyah", 26.77, 44.22),
        UmmAlQuraCity(15, "al-badayea", "Al Badayea", 26.0, 43.73),
        UmmAlQuraCity(17, "al-bukayriyah", "Al Bukayriyah", 26.15, 43.67),
        UmmAlQuraCity(33, "al-mithnab", "Al Mithnab", 25.87, 44.21),
        UmmAlQuraCity(42, "ar-rass", "Ar Rass", 25.86, 43.48),
        UmmAlQuraCity(46, "an-nabhaniyah", "An Nabhaniyah", 25.94, 42.95),
        UmmAlQuraCity(89, "unayzah", "Unayzah", 26.09, 43.97),
        UmmAlQuraCity(90, "uyun-al-jawa", "Uyun Al Jawa", 26.51, 43.62),
        UmmAlQuraCity(100, "riyadh-al-khabra", "Riyadh Al Khabra", 26.04, 43.55),
        UmmAlQuraCity(107, "ash-shimasiyah", "Ash Shimasiyah", 26.3189, 44.2574),
        UmmAlQuraCity(159, "uqlat-as-suqur", "Uqlat As Suqur", 25.8311, 42.1879),
        UmmAlQuraCity(160, "dariyah", "Dariyah", 24.7225, 42.9327),
        UmmAlQuraCity(195, "al-busur", "Al Busur", 26.2888, 43.8612),
        UmmAlQuraCity(196, "al-quwarah", "Al Quwarah", 26.7716, 43.4742),
        UmmAlQuraCity(201, "abanat", "Abanat", 25.5057, 42.8464),
        UmmAlQuraCity(1004, "buraydah", "Buraydah", 26.35, 43.96),
        // Eastern Province region (1005).
        UmmAlQuraCity(2, "abqaiq", "Abqaiq", 25.93, 49.66),
        UmmAlQuraCity(8, "qaryat-al-ulya", "Qaryat Al Ulya", 27.56, 47.7),
        UmmAlQuraCity(10, "al-hofuf", "Al Hofuf", 25.408, 49.6132),
        UmmAlQuraCity(37, "nairyah", "Nairyah", 27.47, 48.47),
        UmmAlQuraCity(68, "hafar-al-batin", "Hafar Al Batin", 28.4, 45.97),
        UmmAlQuraCity(75, "al-jubail", "Al Jubail", 27.0, 49.66),
        UmmAlQuraCity(76, "khafji", "Khafji", 28.42, 48.49),
        UmmAlQuraCity(80, "al-khobar", "Al Khobar", 26.26, 50.21),
        UmmAlQuraCity(91, "al-qatif", "Al Qatif", 26.56, 50.0),
        UmmAlQuraCity(97, "ras-tanura", "Ras Tanura", 26.71, 50.07),
        UmmAlQuraCity(161, "dhahran", "Dhahran", 26.239291, 50.040259),
        UmmAlQuraCity(164, "tarut", "Tarut", 26.57110001, 50.07269982),
        UmmAlQuraCity(165, "al-mubarraz", "Al Mubarraz", 25.405807, 49.548469),
        UmmAlQuraCity(202, "al-ahsa", "Al Ahsa", 24.1389, 49.4277),
        UmmAlQuraCity(203, "al-udayd", "Al Udayd", 24.147, 50.919),
        UmmAlQuraCity(204, "al-bayda", "Al Bayda", 26.361417, 49.969591),
        UmmAlQuraCity(209, "ras-tannurah", "Ras Tannurah", 26.7065, 50.0528),
        UmmAlQuraCity(1005, "dammam", "Dammam", 26.44, 50.1),
        // Asir region (1006).
        UmmAlQuraCity(7, "ahad-rafidah", "Ahad Rafidah", 18.17, 42.84),
        UmmAlQuraCity(34, "almajaridah", "Almajaridah", 19.13, 41.93),
        UmmAlQuraCity(36, "al-namas", "Al Namas", 19.14, 42.14),
        UmmAlQuraCity(55, "belqarn", "Belqarn", 19.57, 41.96),
        UmmAlQuraCity(56, "bishah", "Bishah", 20.0, 42.6),
        UmmAlQuraCity(60, "dhahran-al-janub", "Dhahran Al Janub", 17.69, 43.51),
        UmmAlQuraCity(78, "khamis-mushait", "Khamis Mushait", 18.31, 42.73),
        UmmAlQuraCity(84, "muhayil", "Muhayil", 18.54, 42.05),
        UmmAlQuraCity(98, "rojal", "Rojal", 18.23, 42.28),
        UmmAlQuraCity(104, "sarat-abidah", "Sarat Abidah", 18.08, 43.14),
        UmmAlQuraCity(110, "tathleeth", "Tathleeth", 19.55, 43.52),
        UmmAlQuraCity(166, "bariq", "Bariq", 18.9305, 41.9296),
        UmmAlQuraCity(167, "tanumah", "Tanumah", 18.929, 42.177),
        UmmAlQuraCity(168, "tarib", "Tarib", 18.5667607, 43.20690401),
        UmmAlQuraCity(169, "al-harajah", "Al Harajah", 17.9204, 43.3729),
        UmmAlQuraCity(170, "al-amwah", "Al Amwah", 18.7141, 43.6591),
        UmmAlQuraCity(171, "al-birk", "Al Birk", 18.2071, 41.5363),
        UmmAlQuraCity(205, "al-farshah", "Al Farshah", 17.7532, 43.1529),
        UmmAlQuraCity(1006, "abha", "Abha", 18.22, 42.51),
        // Tabuk region (1007).
        UmmAlQuraCity(44, "al-wajh", "Al Wajh", 26.23, 36.47),
        UmmAlQuraCity(45, "umluj", "Umluj", 25.03, 37.26),
        UmmAlQuraCity(59, "duba", "Duba", 27.35, 35.69),
        UmmAlQuraCity(70, "haql", "Haql", 29.3, 34.95),
        UmmAlQuraCity(111, "tayma", "Tayma", 28.63, 38.55),
        UmmAlQuraCity(206, "al-bada", "Al Bada", 28.473, 35.0278),
        UmmAlQuraCity(1007, "tabuk", "Tabuk", 28.4, 36.58),
        // Hail region (1008).
        UmmAlQuraCity(20, "al-ghazalah", "Al Ghazalah", 26.79, 41.32),
        UmmAlQuraCity(43, "ash-shinan", "Ash Shinan", 27.17, 42.44),
        UmmAlQuraCity(54, "baqaa", "Baqaa", 27.9, 42.4),
        UmmAlQuraCity(173, "al-hait", "Al Hait", 25.9933, 40.4669),
        UmmAlQuraCity(174, "as-sulaymi", "As Sulaymi", 26.2896, 41.3661),
        UmmAlQuraCity(175, "mawqaq", "Mawqaq", 27.378545, 41.179762),
        UmmAlQuraCity(176, "simira", "Simira'", 26.49941, 42.12939001),
        UmmAlQuraCity(177, "ash-shamli", "Ash Shamli", 26.858595, 40.328537),
        UmmAlQuraCity(1008, "hail", "Hail", 27.52, 41.7),
        // Northern Borders region (1009).
        UmmAlQuraCity(94, "rafha", "Rafha", 29.63, 43.5),
        UmmAlQuraCity(115, "turaif", "Turaif", 31.68, 38.66),
        UmmAlQuraCity(178, "al-uwayqiliyah", "Al 'Uwayqiliyah", 30.361771, 42.250924),
        UmmAlQuraCity(1009, "arar", "Arar", 30.99, 41.02),
        // Jazan region (1010).
        UmmAlQuraCity(3, "abu-arish", "Abu Arish", 16.97, 42.83),
        UmmAlQuraCity(6, "ahad-al-masarihah", "Ahad Al Masarihah", 16.7, 42.95),
        UmmAlQuraCity(11, "al-edabi", "Al Edabi", 17.24, 42.95),
        UmmAlQuraCity(13, "al-aridhah", "Al Aridhah", 17.04, 43.09),
        UmmAlQuraCity(18, "addayer", "Addayer", 17.34, 43.14),
        UmmAlQuraCity(24, "alharth", "Alharth", 16.78, 43.22),
        UmmAlQuraCity(39, "aldarb", "Aldarb", 17.71, 42.09),
        UmmAlQuraCity(41, "al-reeth", "Al Reeth", 17.6, 42.86),
        UmmAlQuraCity(52, "baish", "Baish", 17.378, 42.538),
        UmmAlQuraCity(61, "damad", "Damad", 17.1, 42.78),
        UmmAlQuraCity(65, "farasan-island", "Farasan Island", 16.71, 42.12),
        UmmAlQuraCity(101, "sabya", "Sabya", 17.15, 42.62),
        UmmAlQuraCity(103, "samtah", "Samtah", 16.59, 42.94),
        UmmAlQuraCity(179, "fayfa", "Fayfa", 17.2473, 43.1069),
        UmmAlQuraCity(180, "harub", "Harub", 17.435, 42.8857),
        UmmAlQuraCity(181, "at-tuwal", "At Tuwal", 16.528, 42.9685),
        UmmAlQuraCity(1010, "jazan", "Jazan", 16.89, 42.54),
        // Najran region (1011).
        UmmAlQuraCity(51, "badr-al-janoub", "Badr Al Janoub", 17.88, 43.72),
        UmmAlQuraCity(67, "hubuna", "Hubuna", 17.85, 43.02),
        UmmAlQuraCity(79, "al-kharkhir", "Al Kharkhir", 18.85, 51.819),
        UmmAlQuraCity(81, "khbash", "Khbash", 17.6, 45.066),
        UmmAlQuraCity(106, "sharorah", "Sharorah", 17.49, 47.11),
        UmmAlQuraCity(113, "thar", "Thar", 17.97, 44.11),
        UmmAlQuraCity(117, "yadamah", "Yadamah", 18.53, 44.21),
        UmmAlQuraCity(1011, "najran", "Najran", 17.52, 44.2),
        // Al Bahah region (1012).
        UmmAlQuraCity(12, "al-aqiq", "Al Aqiq", 20.27, 41.66),
        UmmAlQuraCity(32, "al-makhwah", "Al Makhwah", 19.78, 41.43),
        UmmAlQuraCity(35, "almandaq", "Almandaq", 20.16, 41.29),
        UmmAlQuraCity(40, "alqura", "Alqura", 20.25, 41.36),
        UmmAlQuraCity(53, "baljurashi", "Baljurashi", 19.86, 41.57),
        UmmAlQuraCity(92, "qilwah", "Qilwah", 19.95, 41.24),
        UmmAlQuraCity(182, "al-hajrah", "Al Hajrah", 20.1954, 41.0722),
        UmmAlQuraCity(183, "ghamid-az-zinad", "Ghamid Az Zinad", 19.6033, 41.4813),
        UmmAlQuraCity(207, "bani-hasan", "Bani Hasan", 20.051578, 41.37405),
        UmmAlQuraCity(1012, "al-bahah", "Al Bahah", 20.01, 41.47),
        // Al Jawf region (1013).
        UmmAlQuraCity(21, "al-qurayyat", "Al Qurayyat", 31.33, 37.34),
        UmmAlQuraCity(63, "dumah-al-jandal", "Dumah Al Jandal", 29.81, 39.87),
        UmmAlQuraCity(184, "tubarjal", "Tubarjal", 30.50098005, 38.22183997),
        UmmAlQuraCity(208, "suwayr", "Suwayr", 30.1147, 40.3803),
        UmmAlQuraCity(1013, "sakaka", "Sakaka", 29.97, 40.2),
    )

    /**
     * The app's own city (its GeoNames id in `cities.csv`) whose table each place is: the app's city of the place's
     * name among those whose own point takes the place's unit (Ash Shafa and Taif both take Taif's, which is Taif's),
     * so About names the city whose table a user follows, in the reader's language, as Kazakhstan's do (the review of
     * 9 Oct 2026). Tayma's is the town's own (`UmmAlQura`'s Tayma rule). A place no app city takes has none.
     */
    val appCityOf: Map<String, Int> = mapOf(
        "ad-diriyah" to 110312, // Ad Dir‘īyah
        "afif" to 110250, // Afif
        "al-kharj" to 109353, // Al Kharj
        "as-sulayyil" to 108048, // As Sulayyil
        "az-zulfi" to 107781, // Az Zulfī
        "al-duwadimi" to 110325, // Ad Dawādimī
        "howtat-bani-tamim" to 13631408, // Ḥawṭah Banī Tamīm
        "rumah" to 102744, // Rumāḩ
        "shagra" to 102170, // Shaqra
        "ad-dilam" to 110314, // Ad Dilam
        "layla" to 104716, // Laylá
        "riyadh" to 108410, // Riyadh
        "al-jumum" to 109417, // Al Jumūm
        "al-khurma" to 109306, // Al Khurmah
        "al-lith" to 109253, // Al Līth
        "jeddah" to 105343, // Jeddah
        "khulais" to 104923, // Khulayş
        "rabigh" to 103035, // Rābigh
        "ranyah" to 12546009, // Ranyah
        "taif" to 107968, // Ta’if
        "turbah" to 101322, // Turabah
        "thuwal" to 409682, // Thuwal
        "makkah" to 104515, // Makkah
        "mahd-al-thahab" to 104578, // Mahd adh Dhahab
        "al-ula" to 108841, // Al-`Ula
        "badr" to 107744, // Badr Ḩunayn
        "yanbu" to 100425, // Yanbu
        "madinah" to 109223, // Madinah
        "al-badayea" to 397833, // Al Badā’i‘ al Wusţá
        "al-bukayriyah" to 109878, // Al Bukayrīyah
        "al-mithnab" to 109131, // Al Mithnab
        "ar-rass" to 108435, // Ar Rass
        "unayzah" to 101732, // Unaizah
        "uyun-al-jawa" to 108782, // ‘Uyūn al Jiwā’
        "riyadh-al-khabra" to 11835536, // Riyāḑ al Khabrā’
        "buraydah" to 107304, // Buraydah
        "abqaiq" to 107312, // Abqaiq
        "al-hofuf" to 109571, // Al Hufūf
        "hafar-al-batin" to 106297, // Hafar Al-Batin
        "al-jubail" to 109435, // Al Jubayl
        "khafji" to 109380, // Al Khafjī
        "al-khobar" to 109323, // Khobar
        "al-qatif" to 108927, // Al Qaţīf
        "ras-tanura" to 102891, // Ras Tanura
        "dhahran" to 107797, // Dhahran
        "tarut" to 101554, // Tārūt
        "dammam" to 110336, // Dammam
        "al-namas" to 108617, // An Nimas
        "belqarn" to 11670045, // Sabt Alalayah
        "bishah" to 103369, // Bīshah
        "khamis-mushait" to 105072, // Khamis Mushait
        "sarat-abidah" to 12500245, // Sarāt ‘Abīdah
        "bariq" to 12495725, // Bariq
        "abha" to 110690, // Abha
        "al-wajh" to 108773, // Al Wajh
        "umluj" to 100926, // Umluj
        "duba" to 106909, // Duba
        "haql" to 106102, // Ḩaql
        "tayma" to 101516, // Taymā’
        "tabuk" to 101628, // Tabuk
        "baqaa" to 107588, // Baq‘ā’
        "hail" to 106281, // Ha'il
        "turaif" to 101312, // Turaif
        "arar" to 108512, // Arar
        "abu-arish" to 110619, // Abū ‘Arīsh
        "damad" to 107117, // Ḑamad
        "sabya" to 102651, // Şabyā
        "samtah" to 102451, // Şāmitah
        "fayfa" to 106667, // Fayfā’
        "jazan" to 105299, // Jizan
        "najran" to 103630, // Najrān
        "al-aqiq" to 110060, // Al ‘Aqīq
        "baljurashi" to 107692, // Baljurashi
        "al-bahah" to 109953, // Al Bahah
        "al-qurayyat" to 108648, // Qurayyat
        "tubarjal" to 101631, // Ţubarjal
        "sakaka" to 102527, // Sakakah
    )
}
