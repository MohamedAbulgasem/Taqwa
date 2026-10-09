package world.taqwa.app.prayer.engine.registry.data

/**
 * QMDB's own places that are kz.qmdb's units (ruling R44, `CentralAsia.kazakhstanUnits`), at the coordinates of
 * QMDB's city list, api.muftyat.kz/cities/ (5,694 places, captured 6 Oct 2026), the only points its year API
 * answers at. Derived facts, never a time: [QmdbPlace.qmdbId] is the list's own id, [QmdbPlace.key] the unit's id
 * (the monitor's capture key), [QmdbPlace.name] an English name for it.
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
}
