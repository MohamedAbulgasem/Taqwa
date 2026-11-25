package world.taqwa.app.prayer.engine.registry.data

import world.taqwa.app.prayer.engine.method.GeoPoint

/**
 * The places the Ministry of Habous and Islamic Affairs prints a prayer table for (habous.gov.ma, its month files'
 * ville ids), each as Taqwa reads its table (ruling R195): derived facts only, never a printed time.
 *
 * - [City.point]: Habous's own computing point, which it does not publish: fitted from the place's November 2026
 *   table (Fajr, Dhuhr, Asr and Isha, which no elevation moves), rounded to 0.01°.
 * - [City.dip]: how far below the plain horizon (−0.8333°) Habous's sunrise and Maghrib sit there, its correction
 *   for the place's elevation, to 0.1° (ruling R196): the one at which November's sunrise needs nearest −30 s, where
 *   the nine cities with other seasons held keep their narrowest year-round band. One month cannot tell a dip from a
 *   margin, so this is a reading, not a measurement: the safety is there for it.
 * - [City.sunrise], [City.maghrib]: the seconds that then keep every printed November minute (the latest sunrise
 *   offset, the earliest Maghrib one), before any safety. Sunrise's are never above 0 and Maghrib's never below it:
 *   the day's own floor holds a Maghrib to the method's sunset, so a dip too deep would show it late.
 * - [City.seasons]: the nine cities whose held pages cover other seasons and years (2015–2025) besides.
 * - [City.town]: the app's point for the town where Habous's point lies off it: Souk El Arbaa (its table fits as if
 *   computed at Sidi Yahia El Gharb's coordinates, 45 km away: a reading of the fit, nothing Habous says), Ksar El
 *   Kebir and Beni Enzar (each nearer another place's point than its own). The unit sits at the town and reads its
 *   table at Habous's point besides.
 *
 * Agoudal is ville 309; ville 310, the same place printed a second time for a lower horizon, is checked against the
 * same unit. Sebta (24) and Melilla (40), which Habous prints as well, are not units: the app places both in Spain,
 * where this entry does not apply; they are read the same way all the same ([spain]), and ride beside Spain's day at
 * Ceuta and Melilla (ruling R261). Generated from the archive's month files of 12 November 2026
 * (archive/code/manual/ma-habous/).
 *
 * Names are the app's for the towns it lists, else a plain transliteration; Habous's own name for the place is the
 * comment on each line. Two names carry more than Habous prints, so that neither reads as another town of the same
 * name that the app lists, and both are identifications from the fitted point alone: "Ras El Ma (Nador)" (ville 50,
 * on the coast east of Nador; the app's Ras el Ma is by Fes, 281 km away) and "Oulad Ayad Tahla" (ville 302, 16 km
 * from Tahla beside Khemis Sidi Abdeljalil; the app's Oulad Ayad is in Beni Mellal-Khenifra, 298 km away).
 */
object HabousCities {
    class City(
        val ville: Int,
        val id: String,
        val name: String,
        val point: GeoPoint,
        val dip: Double,
        val sunrise: Int,
        val maghrib: Int,
        val seasons: Boolean,
        val town: GeoPoint?,
    )

    private fun city(
        ville: Int,
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        dip: Double,
        sunrise: Int,
        maghrib: Int,
        seasons: Boolean = false,
        town: GeoPoint? = null,
    ) = City(ville, id, name, GeoPoint(lat, lon), dip, sunrise, maghrib, seasons, town)

    val cities: List<City> = listOf(
        city(1, "rabat", "Rabat", 34.04, -6.82, 0.4, -25, 92, seasons = true), // الرباط
        city(2, "khemisset", "Khemisset", 33.84, -6.04, 0.7, -33, 99), // الخميسات
        city(3, "tiflet", "Tiflet", 33.99, -6.3, 0.7, -42, 105), // تيفلت
        city(4, "rommani", "Rommani", 33.53, -6.56, 0.7, -42, 108), // الرماني
        city(5, "oulmes", "Oulmes", 33.45, -5.97, 1.2, -19, 61), // والماس
        city(6, "bouznika", "Bouznika", 33.83, -7.14, 0.4, -18, 89), // بوزنيقة
        city(7, "kenitra", "Kenitra", 34.27, -6.57, 0.5, -25, 97), // القنيطرة
        city(8, "sidi-kacem", "Sidi Kacem", 34.25, -5.69, 0.7, -34, 98), // سيدي قاسم
        city(9, "sidi-yahia-el-gharb", "Sidi Yahia El Gharb", 34.33, -6.29, 0.5, -25, 94), // سيدي يحيى الغرب
        city(10, "sidi-slimane", "Sidi Slimane", 34.29, -5.92, 0.3, -45, 120), // سيدي سليمان
        city(11, "souk-el-arbaa", "Souk El Arbaa", 34.33, -6.29, 0.4, -15, 83, town = GeoPoint(34.68473, -6.00344)), // سوق أربعاء الغرب
        city(12, "arbaoua", "Arbaoua", 34.97, -5.89, 0.5, -24, 102), // عرباوة
        city(13, "moulay-bousselham", "Moulay Bousselham", 34.91, -6.26, 0.4, -17, 87), // مولاي بوسلهام
        city(14, "tangier", "Tangier", 35.78, -5.79, 0.6, -28, 88, seasons = true), // طنجة
        city(15, "tetouan", "Tétouan", 35.59, -5.33, 0.8, -17, 80), // تطوان
        city(16, "larache", "Larache", 35.21, -6.12, 0.3, -45, 121), // العرائش
        city(17, "asilah", "Asilah", 35.49, -6.02, 0.4, -22, 99), // أصيلة
        city(18, "chefchaouen", "Chefchaouen", 35.17, -5.23, 1.1, -33, 73), // شفشاون
        city(19, "martil", "Martil", 35.65, -5.25, 0.4, -16, 90), // مرتيل
        city(20, "mdiq", "Mdiq", 35.72, -5.3, 0.3, -37, 123), // المضيق
        city(21, "ksar-el-kebir", "Ksar El Kebir", 35.04, -5.89, 0.4, -41, 106, town = GeoPoint(35.00044, -5.90378)), // القصر الكبير
        city(22, "ksar-sghir", "Ksar Sghir", 35.86, -5.54, 0.4, -40, 110), // القصر الصغير
        city(23, "al-hoceima", "Al Hoceïma", 35.28, -3.9, 0.5, -25, 98), // الحسيمة
        city(25, "fnidek", "Fnidek", 35.86, -5.33, 0.5, -40, 105), // الفنيدق
        city(26, "el-jebha", "El Jebha", 35.23, -4.64, 0.5, -43, 110), // الجبهة
        city(27, "oued-laou", "Oued Laou", 35.46, -5.06, 0.3, -45, 123), // واد لاو
        city(28, "bab-berred", "Bab Berred", 35.01, -4.85, 1.1, -44, 87), // باب برد
        city(29, "ouezzane", "Ouezzane", 34.81, -5.55, 0.9, -22, 80), // وزان
        city(30, "bouskour", "Bouskour", 34.73, -3.56, 0.7, -20, 90), // بوسكور
        city(31, "oujda", "Oujda", 34.7, -1.9, 0.9, -26, 84, seasons = true), // وجدة
        city(32, "berkane", "Berkane", 34.93, -2.32, 0.5, -25, 93), // بركان
        city(33, "figuig", "Figuig", 32.15, -1.21, 1.0, -32, 77, seasons = true), // فكيك
        city(34, "bouarfa", "Bouarfa", 32.56, -1.94, 1.0, -28, 77), // بوعرفة
        city(35, "guercif", "Guercif", 34.25, -3.33, 0.7, -24, 91), // كرسيف
        city(36, "jerada", "Jerada", 34.33, -2.13, 1.1, -22, 69), // جرادة
        city(37, "ain-chair", "Ain Chair", 32.22, -2.48, 1.0, -22, 78), // عين الشعير
        city(38, "taourirt", "Taourirt", 34.44, -2.85, 1.0, -43, 93), // تاوريرت
        city(39, "nador", "Nador", 35.2, -2.9, 0.5, -24, 91), // الناظور
        city(41, "debdou", "Debdou", 34.02, -2.98, 1.1, -28, 74), // دبدو
        city(42, "selouane", "Selouane", 35.08, -2.9, 0.6, -25, 94), // سلوان
        city(43, "beni-enzar", "Beni Enzar", 35.3, -2.88, 0.5, -26, 97, town = GeoPoint(35.26, -2.93)), // بني أنصار
        city(44, "farkhana", "Farkhana", 35.3, -2.96, 0.5, -27, 90), // فرخانة
        city(45, "talsint", "Talsint", 32.56, -3.4, 1.2, -43, 79), // تالسينت
        city(46, "tendrara", "Tendrara", 33.06, -1.97, 1.3, -15, 49), // تندرارة
        city(47, "el-aioun", "El Aïoun", 34.6, -2.47, 0.8, -37, 96), // العيون الشرقية
        city(48, "beni-drar", "Beni Drar", 34.86, -1.96, 0.8, -24, 78), // بني ادرار
        city(49, "saidia", "Saidia", 35.21, -2.22, 0.4, -15, 89), // السعيدية
        city(50, "ras-el-ma", "Ras El Ma (Nador)", 35.17, -2.39, 0.3, -44, 120), // رأس الماء
        city(51, "tafoughalt", "Tafoughalt", 34.83, -2.37, 1.0, -29, 78), // تافوغالت
        city(52, "fezouane", "Fezouane", 34.95, -2.18, 0.9, -46, 95), // فزوان
        city(53, "ahfir", "Ahfir", 34.98, -2.07, 0.6, -25, 94), // أحفير
        city(54, "zaio", "Zaïo", 34.95, -2.69, 0.8, -36, 92), // زايو
        city(55, "driouch", "Driouch", 34.97, -3.35, 0.8, -30, 91), // دريوش
        city(56, "beni-tadjit", "Beni Tadjit", 32.31, -3.43, 1.2, -27, 70), // بني تجيت
        city(57, "bouanane", "Bouanane", 32.13, -3.02, 1.0, -28, 75), // بوعنان
        city(58, "casablanca", "Casablanca", 33.57, -7.64, 0.5, -25, 94, seasons = true), // الدار البيضاء
        city(59, "mohammedia", "Mohammedia", 33.72, -7.37, 0.3, -44, 120), // المحمدية
        city(60, "benslimane", "Benslimane", 33.64, -7.1, 0.6, -22, 93), // بن سليمان
        city(61, "settat", "Settat", 33.03, -7.6, 0.7, -22, 87), // سطات
        city(62, "el-gara", "El Gara", 33.29, -7.21, 0.7, -28, 86), // الكارة
        city(63, "al-brouj", "Al Brouj", 32.52, -7.17, 0.7, -42, 107), // البروج
        city(64, "ben-ahmed", "Ben Ahmed", 33.09, -7.22, 0.9, -15, 72), // ابن أحمد
        city(65, "berrechid", "Berrechid", 33.29, -7.55, 0.5, -40, 113), // برشيد
        city(66, "el-jadida", "El Jadida", 33.26, -8.48, 0.3, -40, 119), // الجديدة
        city(67, "azemmour", "Azemmour", 33.31, -8.32, 0.4, -37, 109), // أزمور
        city(68, "sidi-bennour", "Sidi Bennour", 32.68, -8.4, 0.5, -21, 91), // سيدي بنور
        city(69, "khemis-zemamra", "Khemis Zemamra", 32.62, -8.68, 0.5, -21, 92), // خميس الزمامرة
        city(70, "khenifra", "Khenifra", 32.96, -5.65, 1.0, -44, 92), // خنيفرة
        city(71, "moulay-bouazza", "Moulay Bouazza", 33.25, -6.17, 1.0, -26, 74), // مولاي بوعزة
        city(72, "zaouiat-ahansal", "Zaouiat Ahansal", 31.85, -6.09, 1.6, -40, 40), // زاوية أحنصال
        city(73, "beni-mellal", "Beni Mellal", 32.35, -6.33, 1.1, -39, 85), // بني ملال
        city(74, "azilal", "Azilal", 31.98, -6.55, 1.3, -44, 74), // أزيلال
        city(75, "al-fqih-ben-calah", "Al Fqih Ben Çalah", 32.52, -6.67, 0.7, -41, 107), // الفقيه بنصالح
        city(76, "demnate", "Demnate", 31.78, -6.99, 1.4, -17, 43), // دمنات
        city(77, "el-ksiba", "El Ksiba", 32.61, -6.02, 1.2, -28, 65), // القصيبة
        city(78, "kasba-tadla", "Kasba Tadla", 32.63, -6.26, 0.8, -25, 84), // قصبة تادلة
        city(79, "khouribga", "Khouribga", 32.91, -6.89, 1.0, -15, 64), // خريبكة
        city(80, "oued-zem", "Oued Zem", 32.89, -6.55, 1.0, -25, 73), // وادي زم
        city(81, "fes", "Fes", 34.08, -4.98, 0.7, -30, 98), // فاس
        city(82, "sefrou", "Sefrou", 33.86, -4.82, 1.0, -41, 88), // صفرو
        city(83, "moulay-yacoub", "Moulay Yacoub", 34.1, -5.15, 0.7, -21, 80), // مولاي يعقوب
        city(84, "boulemane", "Boulemane", 33.39, -4.71, 1.6, -44, 38), // بولمان
        city(85, "missour", "Missour", 33.04, -3.98, 1.0, -20, 76), // ميسور
        city(86, "ribat-al-khayr", "Ribat Al Khayr", 33.85, -4.39, 1.3, -23, 49), // رباط الخير
        city(87, "el-menzel", "El Menzel", 33.89, -4.51, 1.1, -25, 73), // المنزل بني يازغة
        city(88, "imouzzer-kandar", "Imouzzer Kandar", 33.77, -4.99, 1.3, -26, 55), // إموزار كندر
        city(89, "taza", "Taza", 34.23, -3.98, 1.0, -25, 79), // تازة
        city(90, "taounate", "Taounate", 34.54, -4.62, 0.9, -42, 93), // تاونات
        city(91, "aknoul", "Aknoul", 34.67, -3.85, 1.2, -30, 69), // أكنول
        city(92, "tizi-ouasli", "Tizi Ouasli", 34.79, -3.77, 1.3, -17, 48), // تيزي وسلي
        city(93, "boured", "Boured", 34.75, -4.07, 1.0, -25, 77), // بورد
        city(94, "tahla", "Tahla", 34.09, -4.4, 0.8, -18, 80), // تاهلة
        city(95, "tissa", "Tissa", 34.31, -4.65, 0.6, -28, 97), // تيسة
        city(96, "karia-ba-mohamed", "Karia Ba Mohamed", 34.38, -5.19, 0.5, -41, 110), // قرية با محمد
        city(97, "ketama", "Ketama", 34.9, -4.6, 1.4, -19, 47), // كتامة
        city(98, "oued-amlil", "Oued Amlil", 34.2, -4.25, 0.7, -18, 87), // واد أمليل
        city(99, "meknes", "Meknes", 33.97, -5.55, 0.8, -39, 100), // مكناس
        city(100, "ifrane", "Ifrane", 33.56, -5.07, 1.3, -19, 58), // يفرن
        city(101, "el-hajeb", "El Hajeb", 33.72, -5.36, 1.1, -40, 81), // الحاجب
        city(102, "zerhoun", "Zerhoun", 34.09, -5.51, 1.0, -24, 72), // زرهون
        city(103, "azrou", "Azrou", 33.47, -5.21, 1.3, -15, 42), // آزرو
        city(104, "marrakesh", "Marrakesh", 31.68, -8.01, 0.8, -41, 96), // مراكش
        city(105, "el-kelaa-des-srarhna", "El Kelaa des Srarhna", 32.06, -7.39, 0.8, -24, 80), // قلعة السراغنة
        city(106, "essaouira", "Essaouira", 31.54, -9.75, 0.4, -24, 99), // الصويرة
        city(107, "chichaoua", "Chichaoua", 31.56, -8.73, 0.7, -41, 105), // شيشاوة
        city(108, "ben-guerir", "Ben Guerir", 32.25, -7.94, 0.8, -26, 82), // بنجرير
        city(109, "rhamna", "Rhamna", 32.51, -7.89, 0.9, -31, 87), // الرحامنة
        city(110, "tamanar", "Tamanar", 31.03, -9.66, 0.7, -38, 97), // تمنار
        city(111, "safi", "Safi", 32.32, -9.22, 0.4, -39, 109), // آسفي
        city(112, "oualidia", "Oualidia", 32.74, -9.01, 0.4, -19, 88), // الوليدية
        city(113, "youssoufia", "Youssoufia", 32.26, -8.5, 0.7, -31, 98), // اليوسفية
        city(114, "tassoultante", "Tassoultante", 31.57, -7.92, 0.8, -31, 91), // تسلطانت
        city(115, "tameslouht", "Tameslouht", 31.53, -8.07, 0.8, -37, 95), // تامصلوحت
        city(116, "kettara", "Kettara", 31.93, -8.14, 0.8, -43, 98), // قطارة
        city(117, "agadir", "Agadir", 30.42, -9.54, 0.6, -26, 89), // أكادير
        city(118, "taroudant", "Taroudant", 30.51, -8.77, 0.6, -28, 90), // تارودانت
        city(119, "tiznit", "Tiznit", 29.73, -9.7, 0.6, -26, 95), // تزنيت
        city(120, "igherm", "Igherm", 30.12, -8.44, 1.5, -22, 34), // إغرم
        city(121, "taliouine", "Taliouine", 30.53, -7.89, 1.2, -41, 77), // تالوين
        city(122, "tafraout", "Tafraout", 29.74, -8.95, 1.3, -28, 60), // تافراوت
        city(123, "tata", "Tata", 29.8, -7.95, 0.9, -26, 86), // طاطا
        city(124, "akka", "Akka", 29.43, -8.22, 1.3, -20, 53), // أقا
        city(125, "foum-el-hisn", "Foum El Hisn", 29.08, -8.87, 1.1, -24, 69), // فم لحصن
        city(126, "tine-biougra", "Tine Biougra", 30.25, -9.33, 0.4, -36, 108), // بويكرة
        city(127, "oulad-teima", "Oulad Teïma", 30.44, -9.18, 0.6, -22, 94), // أولاد تايمة
        city(128, "errachidia", "Errachidia", 31.98, -4.42, 1.0, -45, 94), // الرشيدية
        city(129, "recani", "Reçani", 31.3, -4.24, 1.1, -17, 61), // الريصاني
        city(130, "arfoud", "Arfoud", 31.43, -4.2, 1.0, -38, 87), // أرفود
        city(131, "tendite", "Tendite", 33.66, -3.59, 0.9, -25, 86), // تنديت
        city(132, "goulmima", "Goulmima", 31.72, -4.91, 1.0, -43, 91), // كلميمة
        city(133, "imilchil", "Imilchil", 32.18, -5.58, 1.5, -39, 54), // إملشيل
        city(134, "tinejdad", "Tinejdad", 31.54, -5.0, 1.0, -45, 92), // تنجداد
        city(135, "rich", "Rich", 32.29, -4.43, 1.2, -33, 71), // الريش
        city(136, "midelt", "Midelt", 32.71, -4.72, 1.3, -31, 63, seasons = true), // ميدلت
        city(137, "zagora", "Zagora", 30.35, -5.82, 0.9, -44, 92), // زاكورة
        city(138, "ouarzazate", "Ouarzazate", 30.94, -6.9, 1.1, -42, 80), // ورزازات
        city(139, "tinghir", "Tinghir", 31.53, -5.5, 1.3, -21, 56), // تنغير
        city(140, "skoura", "Skoura", 31.1, -6.52, 1.2, -20, 60), // هسكورة
        city(141, "kelaat-mgouna", "Kelaat Mgouna", 31.27, -6.12, 1.3, -25, 56), // قلعة مكونة
        city(142, "agdz", "Agdz", 30.72, -6.42, 1.3, -30, 58), // أكدز
        city(143, "boumalne-dades", "Boumalne Dades", 31.41, -5.97, 1.3, -35, 65), // بومالن دادس
        city(144, "alnif", "Alnif", 31.14, -5.14, 1.0, -16, 75), // النيف
        city(145, "assoul", "Assoul", 31.95, -5.15, 1.7, -35, 31), // أسول
        city(146, "msemrir", "Msemrir", 31.7, -5.78, 1.5, -24, 41), // أمسمرير
        city(147, "tazarine", "Tazarine", 30.78, -5.53, 0.7, -41, 106), // تازارين
        city(148, "sidi-ifni", "Sidi Ifni", 29.41, -10.15, 0.6, -39, 107), // سيدي إفني
        city(149, "guelmim", "Guelmim", 28.99, -10.04, 0.8, -40, 99), // كلميم
        city(150, "assa", "Assa", 28.6, -9.42, 0.6, -27, 94), // أسا
        city(151, "zag", "Zag", 28.04, -9.27, 0.7, -22, 84), // الزاك
        city(152, "tan-tan", "Tan-Tan", 28.45, -11.08, 0.6, -24, 95), // طانطان
        city(153, "bou-izakarn", "Bou Izakarn", 29.19, -9.7, 0.8, -29, 89), // بويزكارن
        city(154, "mahbes", "Mahbes", 27.44, -9.02, 0.7, -33, 99), // المحبس
        city(155, "lemseid", "Lemseid", 28.04, -10.75, 0.7, -37, 109), // لمسيد
        city(156, "laayoune", "Laayoune", 27.16, -13.19, 0.3, -34, 118, seasons = true), // العيون
        city(157, "smara", "Smara", 26.78, -11.65, 0.6, -26, 90), // السمارة
        city(158, "boujdour", "Boujdour", 26.18, -14.49, 0.3, -41, 123), // بوجدور
        city(159, "tarfaya", "Tarfaya", 27.99, -12.91, 0.4, -19, 92), // طرفاية
        city(160, "tifariti", "Tifariti", 26.2, -10.53, 1.0, -43, 93), // تفاريتي
        city(161, "boukraa", "Boukraa", 26.37, -12.79, 0.6, -17, 93), // بوكراع
        city(162, "guelta-zemmur", "Guelta Zemmur", 25.18, -12.32, 0.6, -25, 100), // كلتة زمور
        city(163, "amgala", "Amgala", 26.19, -11.37, 0.6, -25, 94), // أمكالة
        city(164, "akhfennir", "Akhfennir", 28.12, -12.01, 0.6, -31, 99), // أخفنير
        city(165, "dakhla", "Dakhla", 23.72, -15.95, 0.4, -34, 107, seasons = true), // الداخلة
        city(166, "lagouira", "Lagouira", 20.84, -17.06, 0.3, -36, 119, seasons = true), // الكويرة
        city(167, "aousserd", "Aousserd", 22.57, -14.27, 0.6, -23, 90), // أوسرد
        city(168, "bir-gandouz", "Bir Gandouz", 21.65, -16.43, 0.6, -23, 95), // بئر كندوز
        city(169, "bir-anzarane", "Bir Anzarane", 23.93, -14.5, 0.7, -20, 86), // بئر أنزاران
        city(301, "khemis-sidi-abdeljalil", "Khemis Sidi Abdeljalil", 34.13, -4.56, 0.8, -38, 90), // خميس سيدي عبد الجليل
        city(302, "oulad-ayad-tahla", "Oulad Ayad Tahla", 34.12, -4.57, 0.6, -28, 94), // أولاد عياد
        city(304, "matmata", "Matmata", 34.11, -4.51, 0.4, -33, 111), // مطماطة
        city(305, "imi-n-tanout", "Imi-n-Tanout", 31.21, -8.82, 1.0, -27, 73), // إيمنتانوت
        city(306, "sidi-ghanem", "Sidi Ghanem", 31.34, -9.42, 1.1, -16, 62), // سيدي غانم
        city(307, "tifentane", "Tifentane", 31.36, -7.48, 0.7, -16, 87), // تفنتان
        city(308, "ait-el-kak", "Ait El Kak", 31.26, -7.75, 1.7, -20, 19), // آيت القاق
        city(309, "agoudal", "Agoudal", 32.1, -5.46, 1.6, -16, 27), // أكدال أملشيل
        city(311, "agaiouar", "Agaiouar", 31.29, -7.79, 1.1, -42, 80), // أكايوار
        city(312, "ain-el-aouda", "Ain El Aouda", 33.81, -6.76, 0.5, -26, 88), // عين العودة
        city(313, "asguine", "Asguine", 31.35, -7.73, 1.1, -24, 72), // أسكين
        city(314, "ait-ourir", "Ait Ourir", 31.58, -7.62, 0.9, -24, 78), // آيت ورير
        city(315, "zaouiat-moulay-brahim", "Zaouiat Moulay Brahim", 31.33, -7.93, 1.1, -43, 89), // زاوية مولاي ابراهيم
        city(316, "toulkoult", "Toulkoult", 31.25, -9.04, 1.1, -44, 82), // تولكولت
        city(317, "iks", "Iks", 30.96, -7.82, 1.6, -36, 39), // إيكس
        city(318, "kers", "Kers", 31.56, -7.6, 1.4, -44, 65), // كرس
        city(319, "tissint", "Tissint", 29.91, -7.27, 1.2, -36, 68), // تيسنت
        city(320, "foum-zguid", "Foum Zguid", 30.14, -6.83, 1.3, -24, 60), // فم زكيد
        city(321, "ksar-ich", "Ksar Ich", 32.54, -0.98, 1.2, -17, 57), // قصر إيش
        city(322, "imi-n-tlat", "Imi-n-Tlat", 31.69, -6.51, 1.1, -22, 63), // إيمين ثلاث
    )

    /**
     * Sebta (ville 24) and Melilla (ville 40), read as every place above (the fit of 14 Nov 2026, habous_units.py
     * `spain-rows` and `spain-build`), and no unit of Morocco's: the app places Ceuta and Melilla in Spain, and Habous's
     * tables for them ride beside Spain's day there (ruling R261, the second audit's G6).
     */
    val spain: List<City> = listOf(
        city(24, "sebta", "Sebta", 35.9, -5.32, 0.7, -43, 104), // سبتة
        city(40, "melilla", "Melilla", 35.32, -2.94, 0.4, -16, 87), // مليلية
    )
}
