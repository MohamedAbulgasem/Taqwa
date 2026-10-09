package world.taqwa.app.prayer.engine.registry.data

/**
 * The QMDB places kz.qmdb's units name (ruling R44, `CentralAsia.kazakhstanUnits`; every place of QMDB's list is a
 * unit, [QmdbPlaceList]), at the coordinates of QMDB's city list, api.muftyat.kz/cities/ (5,694 places, captured
 * 6 Oct 2026), the only points its year API answers at: the places whose tables are checked ([all]), the five it
 * serves none for ([unanswered]), and the app's own cities ([appCities]). Derived facts, never a time:
 * [QmdbPlace.qmdbId] is the list's own id, [QmdbPlace.key] the unit's id (the monitor's capture key),
 * [QmdbPlace.name] an English name for it.
 */
object QmdbPlaces {
    class QmdbPlace(val qmdbId: Int, val key: String, val name: String, val lat: Double, val lon: Double)

    /**
     * The places the monitor fetches (`tools/timetables/monitor/fetchers/qmdb.py`) and the gate checks
     * (kz-qmdb.tsv): the 29 held since the rounds of 3 and 6 Oct 2026, and QMDB's place for each other city of the
     * app's own list (its "<name> қаласы" city entry where it lists one, else the locality of that name).
     */
    val all: List<QmdbPlace> = listOf(
        // The gate's 29 places of 3 and 6 Oct 2026, in the monitor's order.
        QmdbPlace(72, "almaty", "Almaty", 43.238293, 76.945465),
        QmdbPlace(3, "astana", "Astana", 51.133333, 71.433333),
        QmdbPlace(17, "kokshetau", "Kokshetau", 53.291667, 69.391667),
        QmdbPlace(42, "kostanay", "Kostanay", 53.219333, 63.634194),
        QmdbPlace(53, "pavlodar", "Pavlodar", 52.315556, 76.956389),
        QmdbPlace(51, "petropavl", "Petropavl", 54.862222, 69.140833),
        QmdbPlace(9954, "isakovka", "Isakovka", 55.405802, 68.935933),
        QmdbPlace(9951, "krasny-yar", "Krasny Yar", 55.369280, 69.367089),
        QmdbPlace(10006, "kulomzino", "Kulomzino", 55.250541, 70.473193),
        QmdbPlace(7, "oral", "Oral", 51.204019, 51.370537),
        QmdbPlace(59, "aksay", "Aksay", 51.164945, 53.020868),
        QmdbPlace(20, "aktobe", "Aktobe", 50.300377, 57.154555),
        QmdbPlace(26, "shalqar", "Shalqar", 47.831392, 59.619290),
        QmdbPlace(18, "atyrau", "Atyrau", 47.116667, 51.883333),
        QmdbPlace(47, "aktau", "Aktau", 43.635379, 51.169135),
        QmdbPlace(46, "kyzylorda", "Kyzylorda", 44.842544, 65.502563),
        QmdbPlace(57, "shymkent", "Shymkent", 42.368009, 69.612769),
        QmdbPlace(10554, "zhenis", "Zhenis", 40.599543, 68.504760),
        QmdbPlace(7373, "oteshqali", "Oteshqali Atambayev", 47.994187, 51.622514),
        QmdbPlace(29, "ayagoz", "Ayagoz", 47.966667, 80.433333),
        QmdbPlace(9948, "vagulino", "Vagulino", 55.151518, 69.262462),
        QmdbPlace(9944, "bugrovoe", "Bugrovoe", 55.049091, 69.724025),
        QmdbPlace(10019, "pulemetovka", "Pulemetovka", 54.551120, 70.421316),
        QmdbPlace(9890, "spasovka", "Spasovka", 54.349933, 67.721896),
        QmdbPlace(25, "khromtau", "Khromtau", 50.250278, 58.434722),
        QmdbPlace(41, "arkalyk", "Arkalyk", 50.248611, 66.911389),
        QmdbPlace(30, "oskemen", "Oskemen", 49.948325, 82.627848),
        QmdbPlace(5789, "mamyrsu", "Mamyrsu", 47.951147, 80.382042),
        QmdbPlace(7368, "makat", "Makat", 47.647520, 53.349220),
        // The app's other Kazakh cities, at QMDB's own place for each, north to south (9 Oct 2026).
        QmdbPlace(6358, "akkol", "Akkol", 53.296079, 69.597040),
        QmdbPlace(43, "rudny", "Rudny", 52.966667, 63.116667),
        QmdbPlace(154, "makinsk", "Makinsk", 52.632810, 70.417677),
        QmdbPlace(69, "lisakovsk", "Lisakovsk", 52.544079, 62.492641),
        QmdbPlace(21, "stepnogorsk", "Stepnogorsk", 52.346944, 71.881667),
        QmdbPlace(113, "aksu-pavlodar", "Aksu (Pavlodar)", 52.037890, 76.920582),
        QmdbPlace(23, "atbasar", "Atbasar", 51.815761, 68.358335),
        QmdbPlace(52, "ekibastuz", "Ekibastuz", 51.729778, 75.326583),
        QmdbPlace(176, "shemonaikha", "Shemonaikha", 50.627624, 81.916697),
        QmdbPlace(32, "semey", "Semey", 50.404976, 80.249235),
        QmdbPlace(31, "ridder", "Ridder", 50.338860, 83.506329),
        QmdbPlace(109, "temirtau", "Temirtau", 50.058756, 72.953424),
        QmdbPlace(39, "karaganda", "Karaganda", 49.806406, 73.085485),
        QmdbPlace(166, "saran", "Saran", 49.801993, 72.828387),
        QmdbPlace(174, "altay", "Altay", 49.725218, 84.273562),
        QmdbPlace(167, "shakhtinsk", "Shakhtinsk", 49.705868, 72.594637),
        QmdbPlace(24, "kandyagash", "Kandyagash", 49.474444, 57.423333),
        QmdbPlace(5936, "kalbatau", "Kalbatau", 49.328084, 81.573693),
        QmdbPlace(93, "embi", "Embi", 48.823344, 58.148397),
        QmdbPlace(40, "satpayev", "Satpayev", 47.907455, 67.528112),
        QmdbPlace(38, "zhezkazgan", "Zhezkazgan", 47.799711, 67.714090),
        QmdbPlace(95, "zaysan", "Zaysan", 47.453008, 84.969846),
        QmdbPlace(27, "kulsary", "Kulsary", 46.983333, 54.016667),
        QmdbPlace(36, "balkhash", "Balkhash", 46.843721, 74.977301),
        QmdbPlace(71, "aral", "Aral", 46.797738, 61.660792),
        QmdbPlace(5979, "makanshy", "Makanshy", 46.781629, 82.023857),
        QmdbPlace(44, "baikonur", "Baikonur", 45.966111, 63.307778),
        QmdbPlace(9319, "aiteke-bi", "Aiteke Bi (Novokazalinsk)", 45.835934, 62.148317),
        QmdbPlace(9358, "zhosaly", "Zhosaly", 45.488495, 64.086675),
        QmdbPlace(161, "sarkand", "Sarkand", 45.413666, 79.916894),
        QmdbPlace(163, "ushtobe", "Ushtobe", 45.251221, 77.980827),
        QmdbPlace(162, "taldykorgan", "Taldykorgan", 45.017837, 78.382123),
        QmdbPlace(143, "tekeli", "Tekeli", 44.863094, 78.764266),
        QmdbPlace(70, "zharkent", "Zharkent", 44.169365, 80.003842),
        QmdbPlace(11338, "shiyeli", "Shiyeli", 44.167573, 66.736893),
        QmdbPlace(9295, "zhanakorgan", "Zhanakorgan", 43.900451, 67.243723),
        QmdbPlace(2, "konaev", "Konaev", 43.883333, 77.083333),
        QmdbPlace(9449, "mangystau", "Mangystau", 43.691561, 51.305571),
        QmdbPlace(77, "shu", "Shu", 43.611782, 73.760237),
        QmdbPlace(6980, "shelek", "Shelek", 43.597525, 78.250618),
        QmdbPlace(33, "zhanatas", "Zhanatas", 43.554650, 69.722525),
        QmdbPlace(100, "kentau", "Kentau", 43.518131, 68.504652),
        QmdbPlace(7084, "otegen-batyr", "Otegen Batyr", 43.423782, 77.028020),
        QmdbPlace(7074, "boralday", "Boralday (Burunday)", 43.358094, 76.861006),
        QmdbPlace(50, "zhanaozen", "Zhanaozen", 43.343266, 52.865792),
        QmdbPlace(73, "talgar", "Talgar", 43.302813, 77.239690),
        QmdbPlace(58, "turkistan", "Turkistan", 43.302025, 68.268979),
        QmdbPlace(34, "karatau", "Karatau", 43.166667, 70.466667),
        QmdbPlace(7785, "sarykemer", "Sarykemer", 43.007610, 71.515131),
        QmdbPlace(35, "taraz", "Taraz", 42.883333, 71.366667),
        QmdbPlace(7979, "merke", "Merke", 42.872481, 73.190139),
        QmdbPlace(10901, "turar-ryskulov", "Turar Ryskulov", 42.534675, 70.350564),
        QmdbPlace(134, "arys", "Arys", 42.432744, 68.813798),
        QmdbPlace(10669, "aksu-turkistan", "Aksu (Turkistan)", 42.420712, 69.828661),
        QmdbPlace(82, "lenger", "Lenger", 42.182051, 69.882120),
        QmdbPlace(55, "saryagash", "Saryagash", 41.466667, 69.166667),
        QmdbPlace(56, "shardara", "Shardara", 41.254722, 67.969167),
        QmdbPlace(54, "zhetysay", "Zhetysay", 40.775278, 68.327222),
    )

    /**
     * Five more of the app's cities, whose point QMDB's list gives to two of its places at once (Shchuchinsk,
     * Zhitikara and Esik as a city and as a locality of the same name, Abay in the Karaganda region and its Duanshy,
     * Abay in the Turkistan region twice): there the year API answers HTTP 500 (9 Oct 2026), so QMDB publishes no
     * table to check, and these units claim no figure.
     */
    val unanswered: List<QmdbPlace> = listOf(
        QmdbPlace(156, "shchuchinsk", "Shchuchinsk", 52.942096, 70.210140),
        QmdbPlace(168, "zhitikara", "Zhitikara", 52.183928, 61.189833),
        QmdbPlace(160, "esik", "Esik", 43.365179, 77.450893),
        QmdbPlace(164, "abay-karaganda", "Abay (Karaganda)", 49.631899, 72.859245),
        QmdbPlace(10460, "abay-turkistan", "Abay (Turkistan)", 41.346802, 68.950432),
    )

    /** An app city: the [key] of its QMDB place's unit, its id in the app's city list, and the point the app gives it. */
    class AppCity(val key: String, val geonamesId: Int, val lat: Double, val lon: Double)

    /**
     * Each Kazakh city of the app's own list (`cities.csv`, GeoNames), with its QMDB place: these places are the
     * cities (`CentralAsia.kazakhstanUnits`, the owner's decision of 9 Oct 2026): inside a city's reach a user takes
     * the city's place even where a village or suburb of QMDB's list lies nearer (Oral's own point is 3 km from the
     * village of Asan and 6 from QMDB's Oral), the nearer city where two reach. Atyrau and Balykshi share Atyrau's
     * place, and Kyzylorda and Tasbuget Kyzylorda's; the first named is the unit's city. Shalkar in the Atyrau region
     * has no QMDB place of its name, so it is not here.
     */
    val appCities: List<AppCity> = listOf(
        AppCity("almaty", 1526384, 43.25249, 76.9115), // Almaty
        AppCity("astana", 1526273, 51.1801, 71.44598), // Astana
        AppCity("shymkent", 1518980, 42.30988, 69.60042), // Shymkent
        AppCity("aktobe", 610611, 50.27969, 57.20718), // Aktobe
        AppCity("karaganda", 609655, 49.80187, 73.10211), // Karagandy
        AppCity("taraz", 1516905, 42.89799, 71.37334), // Taraz
        AppCity("kyzylorda", 1519922, 44.85278, 65.50917), // Kyzylorda
        AppCity("oral", 608668, 51.24601, 51.42558), // Oral
        AppCity("pavlodar", 1520240, 52.27601, 76.96881), // Pavlodar
        AppCity("oskemen", 1520316, 49.97143, 82.60586), // Ust-Kamenogorsk
        AppCity("semey", 1519422, 50.42064, 80.25025), // Semey
        AppCity("atyrau", 610529, 47.1048, 51.88427), // Atyrau
        AppCity("turkistan", 1517945, 43.29458, 68.25685), // Turkistan
        AppCity("kostanay", 1519928, 53.21435, 63.62463), // Kostanay
        AppCity("petropavl", 1520172, 54.87343, 69.15065), // Petropavl
        AppCity("temirtau", 1518262, 50.05197, 72.95497), // Temirtau
        AppCity("kokshetau", 1522203, 53.28414, 69.39364), // Kokshetau
        AppCity("aktau", 610612, 43.66105, 51.17392), // Aktau
        AppCity("rudny", 1519843, 52.97244, 63.11055), // Rudnyy
        AppCity("ekibastuz", 1524325, 51.72371, 75.32287), // Ekibastuz
        AppCity("taldykorgan", 1518542, 45.01556, 78.37389), // Taldykorgan
        AppCity("zhezkazgan", 1516589, 47.79411, 67.70628), // Zhezqazghan
        AppCity("zhanaozen", 607610, 43.34116, 52.86192), // Zhanaozen
        AppCity("balkhash", 1525798, 46.84546, 74.98213), // Balqash
        AppCity("sarkand", 1519691, 45.41322, 79.91713), // Sarqant
        AppCity("baikonur", 1521368, 45.61667, 63.31667), // Baikonur
        AppCity("satpayev", 1520692, 47.90409, 67.54112), // Satpayev
        AppCity("kentau", 1522751, 43.51672, 68.50463), // Kentau
        AppCity("ridder", 1521370, 50.34524, 83.51562), // Ridder
        AppCity("kulsary", 609123, 46.95307, 54.01978), // Qulsary
        AppCity("shchuchinsk", 1519244, 52.93592, 70.18895), // Shchuchinsk
        AppCity("stepnogorsk", 1537939, 52.35062, 71.88161), // Stepnogorsk
        AppCity("altay", 1516438, 49.73626, 84.25416), // Altay
        AppCity("aksu-pavlodar", 1524298, 52.04023, 76.92748), // Aksu
        AppCity("zhitikara", 1516601, 52.19019, 61.19894), // Zhitikara
        AppCity("saran", 1519725, 49.80245, 72.83186), // Saran
        AppCity("talgar", 1518518, 43.30235, 77.23811), // Talghar
        AppCity("konaev", 1519948, 43.86681, 77.06304), // Konayev
        AppCity("arkalyk", 1526193, 50.25031, 66.90384), // Arkalyk
        AppCity("shakhtinsk", 1519327, 49.70713, 72.59321), // Shakhtinsk
        AppCity("lisakovsk", 1521315, 52.54488, 62.49893), // Lisakovsk
        AppCity("shu", 1519030, 43.60507, 73.76221), // Shu
        AppCity("karatau", 1519938, 43.17959, 70.4592), // Karatau
        AppCity("zhetysay", 1524385, 40.77631, 68.32774), // Zhetysay
        AppCity("arys", 1526168, 42.43015, 68.8087), // Arys
        AppCity("aiteke-bi", 1516789, 45.84806, 62.15254), // Novokazalinsk
        AppCity("abay-karaganda", 1526970, 49.63539, 72.86523), // Abay
        AppCity("aksay", 610613, 51.1681, 52.99782), // Aqsay
        AppCity("atbasar", 1526038, 51.80854, 68.35823), // Atbasar
        AppCity("zharkent", 1520253, 44.16619, 80.00736), // Zharkent
        AppCity("zhanatas", 1516788, 43.56274, 69.73209), // Zhangatas
        AppCity("ayagoz", 1525988, 47.96447, 80.43437), // Ayagoz
        AppCity("aral", 1526265, 46.80174, 61.66312), // Aral
        AppCity("esik", 1523741, 43.3552, 77.45245), // Esik
        AppCity("mangystau", 608880, 43.69088, 51.32237), // Mangistau
        AppCity("shiyeli", 1524801, 44.17057, 66.73376), // Shiyeli
        AppCity("shelek", 1519230, 43.59623, 78.25745), // Shelek
        AppCity("kandyagash", 608679, 49.46917, 57.41865), // Kandyagash
        AppCity("shalqar", 608359, 47.83154, 59.61926), // Shalqar
        AppCity("tekeli", 1518296, 44.8678, 78.72807), // Tekeli
        AppCity("aksu-turkistan", 1525462, 42.42193, 69.82709), // Aksu
        AppCity("shardara", 1524889, 41.25832, 67.96991), // Shardara
        AppCity("saryagash", 1519673, 41.46042, 69.16791), // Saryaghash
        AppCity("abay-turkistan", 1526973, 41.34682, 68.9504), // Abay
        AppCity("khromtau", 609404, 50.25161, 58.43574), // Khromtau
        AppCity("kalbatau", 1524245, 49.33009, 81.57275), // Kalbatau
        AppCity("zhanakorgan", 1517323, 43.90652, 67.24637), // Zhangaqorghan
        AppCity("lenger", 1521379, 42.18152, 69.88582), // Lenger
        AppCity("boralday", 1524958, 43.35567, 76.85477), // Burunday
        AppCity("ushtobe", 1517637, 45.25258, 77.98284), // Ushtobe
        AppCity("shemonaikha", 1519226, 50.62899, 81.91092), // Shemonaikha
        AppCity("zhosaly", 1516519, 45.48778, 64.07806), // Zhosaly
        AppCity("atyrau", 610445, 47.06667, 51.86667), // Balykshi
        AppCity("otegen-batyr", 1524308, 43.41845, 77.02187), // Otegen Batyra
        AppCity("embi", 609924, 48.82981, 58.15042), // Embi
        AppCity("makanshy", 1521126, 46.78166, 82.02258), // Maqanshy
        AppCity("turar-ryskulov", 1517501, 42.5334, 70.3496), // Turar Ryskulov
        AppCity("makinsk", 1521230, 52.6329, 70.41911), // Makinsk
        AppCity("zaysan", 1517060, 47.46657, 84.87144), // Zaysan
        AppCity("akkol", 1526797, 53.29617, 69.59997), // Akkol
        AppCity("kyzylorda", 1518431, 44.77018, 65.55461), // Tasbuget
        AppCity("merke", 1520969, 42.87183, 73.19648), // Merke
        AppCity("sarykemer", 1520947, 43.00929, 71.5101), // Sarykemer
    )
}
