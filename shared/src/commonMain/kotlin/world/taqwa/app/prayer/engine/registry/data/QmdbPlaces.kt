package world.taqwa.app.prayer.engine.registry.data

/**
 * The QMDB places kz.qmdb's units name (ruling R44, `CentralAsia.kazakhstanUnits`; every place of QMDB's list is a
 * unit, [QmdbPlaceList]), at the coordinates of QMDB's city list, api.muftyat.kz/cities/ (5,694 places, captured
 * 6 Oct 2026), the only points its year API answers at: the places whose tables are checked ([all]), the five it
 * serves none for ([unanswered]), and the app's own cities' points ([anchors]). Derived facts, never a time:
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

    /** An app city's own point ([lat], [lon], the app's city list) and the [key] of its QMDB place's unit. */
    class Anchor(val key: String, val lat: Double, val lon: Double)

    /**
     * Each Kazakh city of the app's own list (`cities.csv`, GeoNames) at the point the app gives it, with its QMDB
     * place: a user there takes that place's unit, where its reach holds the point, even where a village of QMDB's
     * list lies nearer (Oral's own point is 3 km from the village of Asan and 6 from QMDB's Oral). Shalkar in the
     * Atyrau region has no QMDB place of its name, so no anchor.
     */
    val anchors: List<Anchor> = listOf(
        Anchor("almaty", 43.25249, 76.9115), // Almaty (GeoNames 1526384)
        Anchor("astana", 51.1801, 71.44598), // Astana (GeoNames 1526273)
        Anchor("shymkent", 42.30988, 69.60042), // Shymkent (GeoNames 1518980)
        Anchor("aktobe", 50.27969, 57.20718), // Aktobe (GeoNames 610611)
        Anchor("karaganda", 49.80187, 73.10211), // Karagandy (GeoNames 609655)
        Anchor("taraz", 42.89799, 71.37334), // Taraz (GeoNames 1516905)
        Anchor("kyzylorda", 44.85278, 65.50917), // Kyzylorda (GeoNames 1519922)
        Anchor("oral", 51.24601, 51.42558), // Oral (GeoNames 608668)
        Anchor("pavlodar", 52.27601, 76.96881), // Pavlodar (GeoNames 1520240)
        Anchor("oskemen", 49.97143, 82.60586), // Ust-Kamenogorsk (GeoNames 1520316)
        Anchor("semey", 50.42064, 80.25025), // Semey (GeoNames 1519422)
        Anchor("atyrau", 47.1048, 51.88427), // Atyrau (GeoNames 610529)
        Anchor("turkistan", 43.29458, 68.25685), // Turkistan (GeoNames 1517945)
        Anchor("kostanay", 53.21435, 63.62463), // Kostanay (GeoNames 1519928)
        Anchor("petropavl", 54.87343, 69.15065), // Petropavl (GeoNames 1520172)
        Anchor("temirtau", 50.05197, 72.95497), // Temirtau (GeoNames 1518262)
        Anchor("kokshetau", 53.28414, 69.39364), // Kokshetau (GeoNames 1522203)
        Anchor("aktau", 43.66105, 51.17392), // Aktau (GeoNames 610612)
        Anchor("rudny", 52.97244, 63.11055), // Rudnyy (GeoNames 1519843)
        Anchor("ekibastuz", 51.72371, 75.32287), // Ekibastuz (GeoNames 1524325)
        Anchor("taldykorgan", 45.01556, 78.37389), // Taldykorgan (GeoNames 1518542)
        Anchor("zhezkazgan", 47.79411, 67.70628), // Zhezqazghan (GeoNames 1516589)
        Anchor("zhanaozen", 43.34116, 52.86192), // Zhanaozen (GeoNames 607610)
        Anchor("balkhash", 46.84546, 74.98213), // Balqash (GeoNames 1525798)
        Anchor("sarkand", 45.41322, 79.91713), // Sarqant (GeoNames 1519691)
        Anchor("baikonur", 45.61667, 63.31667), // Baikonur (GeoNames 1521368)
        Anchor("satpayev", 47.90409, 67.54112), // Satpayev (GeoNames 1520692)
        Anchor("kentau", 43.51672, 68.50463), // Kentau (GeoNames 1522751)
        Anchor("ridder", 50.34524, 83.51562), // Ridder (GeoNames 1521370)
        Anchor("kulsary", 46.95307, 54.01978), // Qulsary (GeoNames 609123)
        Anchor("shchuchinsk", 52.93592, 70.18895), // Shchuchinsk (GeoNames 1519244)
        Anchor("stepnogorsk", 52.35062, 71.88161), // Stepnogorsk (GeoNames 1537939)
        Anchor("altay", 49.73626, 84.25416), // Altay (GeoNames 1516438)
        Anchor("aksu-pavlodar", 52.04023, 76.92748), // Aksu (GeoNames 1524298)
        Anchor("zhitikara", 52.19019, 61.19894), // Zhitikara (GeoNames 1516601)
        Anchor("saran", 49.80245, 72.83186), // Saran (GeoNames 1519725)
        Anchor("talgar", 43.30235, 77.23811), // Talghar (GeoNames 1518518)
        Anchor("konaev", 43.86681, 77.06304), // Konayev (GeoNames 1519948)
        Anchor("arkalyk", 50.25031, 66.90384), // Arkalyk (GeoNames 1526193)
        Anchor("shakhtinsk", 49.70713, 72.59321), // Shakhtinsk (GeoNames 1519327)
        Anchor("lisakovsk", 52.54488, 62.49893), // Lisakovsk (GeoNames 1521315)
        Anchor("shu", 43.60507, 73.76221), // Shu (GeoNames 1519030)
        Anchor("karatau", 43.17959, 70.4592), // Karatau (GeoNames 1519938)
        Anchor("zhetysay", 40.77631, 68.32774), // Zhetysay (GeoNames 1524385)
        Anchor("arys", 42.43015, 68.8087), // Arys (GeoNames 1526168)
        Anchor("aiteke-bi", 45.84806, 62.15254), // Novokazalinsk (GeoNames 1516789)
        Anchor("abay-karaganda", 49.63539, 72.86523), // Abay (GeoNames 1526970)
        Anchor("aksay", 51.1681, 52.99782), // Aqsay (GeoNames 610613)
        Anchor("atbasar", 51.80854, 68.35823), // Atbasar (GeoNames 1526038)
        Anchor("zharkent", 44.16619, 80.00736), // Zharkent (GeoNames 1520253)
        Anchor("zhanatas", 43.56274, 69.73209), // Zhangatas (GeoNames 1516788)
        Anchor("ayagoz", 47.96447, 80.43437), // Ayagoz (GeoNames 1525988)
        Anchor("aral", 46.80174, 61.66312), // Aral (GeoNames 1526265)
        Anchor("esik", 43.3552, 77.45245), // Esik (GeoNames 1523741)
        Anchor("mangystau", 43.69088, 51.32237), // Mangistau (GeoNames 608880)
        Anchor("shiyeli", 44.17057, 66.73376), // Shiyeli (GeoNames 1524801)
        Anchor("shelek", 43.59623, 78.25745), // Shelek (GeoNames 1519230)
        Anchor("kandyagash", 49.46917, 57.41865), // Kandyagash (GeoNames 608679)
        Anchor("shalqar", 47.83154, 59.61926), // Shalqar (GeoNames 608359)
        Anchor("tekeli", 44.8678, 78.72807), // Tekeli (GeoNames 1518296)
        Anchor("aksu-turkistan", 42.42193, 69.82709), // Aksu (GeoNames 1525462)
        Anchor("shardara", 41.25832, 67.96991), // Shardara (GeoNames 1524889)
        Anchor("saryagash", 41.46042, 69.16791), // Saryaghash (GeoNames 1519673)
        Anchor("abay-turkistan", 41.34682, 68.9504), // Abay (GeoNames 1526973)
        Anchor("khromtau", 50.25161, 58.43574), // Khromtau (GeoNames 609404)
        Anchor("kalbatau", 49.33009, 81.57275), // Kalbatau (GeoNames 1524245)
        Anchor("zhanakorgan", 43.90652, 67.24637), // Zhangaqorghan (GeoNames 1517323)
        Anchor("lenger", 42.18152, 69.88582), // Lenger (GeoNames 1521379)
        Anchor("boralday", 43.35567, 76.85477), // Burunday (GeoNames 1524958)
        Anchor("ushtobe", 45.25258, 77.98284), // Ushtobe (GeoNames 1517637)
        Anchor("shemonaikha", 50.62899, 81.91092), // Shemonaikha (GeoNames 1519226)
        Anchor("zhosaly", 45.48778, 64.07806), // Zhosaly (GeoNames 1516519)
        Anchor("atyrau", 47.06667, 51.86667), // Balykshi (GeoNames 610445)
        Anchor("otegen-batyr", 43.41845, 77.02187), // Otegen Batyra (GeoNames 1524308)
        Anchor("embi", 48.82981, 58.15042), // Embi (GeoNames 609924)
        Anchor("makanshy", 46.78166, 82.02258), // Maqanshy (GeoNames 1521126)
        Anchor("turar-ryskulov", 42.5334, 70.3496), // Turar Ryskulov (GeoNames 1517501)
        Anchor("makinsk", 52.6329, 70.41911), // Makinsk (GeoNames 1521230)
        Anchor("zaysan", 47.46657, 84.87144), // Zaysan (GeoNames 1517060)
        Anchor("akkol", 53.29617, 69.59997), // Akkol (GeoNames 1526797)
        Anchor("kyzylorda", 44.77018, 65.55461), // Tasbuget (GeoNames 1518431)
        Anchor("merke", 42.87183, 73.19648), // Merke (GeoNames 1520969)
        Anchor("sarykemer", 43.00929, 71.5101), // Sarykemer (GeoNames 1520947)
    )
}
