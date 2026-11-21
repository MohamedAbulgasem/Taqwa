package world.taqwa.app.prayer.engine.registry.data

/**
 * The app's Malaysian cities by JAKIM zone (the zone round of 13 Nov 2026, ruling R226): each city of the app's list
 * (`cities.csv`, its id and its point there) under the zone whose list of districts names its district, read off
 * the zone list held in the archive (`raw/manual/my-jakim-zones/2026-11-11/zones.json`). A city is here on that
 * list, never on its distance from a zone's point: a city whose district the list does not settle is under
 * no zone ([unattached]) and takes the zone of the nearest town here, with the neighbouring zones beside it
 * (`Jakim.units`). `JakimZoneCitiesTest` holds every row to the city list, and every Malaysian city of the list to
 * a row here or in [unattached].
 *
 * What the evidence is, plainly (the review round of 13 Nov 2026): the zone list names districts and a few places,
 * and it names fewer than a hundred of these 344 cities by their own name (97 by the review's count). The other 247
 * are here on the builder's reading of which district a town lies in (general geography; no district table is held
 * in the archive), and that reading is not checked by any test. Three cities are under a zone of another state than
 * `cities.csv` gives them: Bandar Utama, Ampang and SS2 are "Kuala Lumpur" there and under SGR01 here (they lie in
 * Petaling and Hulu Langat/Gombak, Selangor; WLY01 and SGR01 share their starts, so the times differ by the
 * sunrise's margin alone). The list itself has the shape of the mirror's zone list, not a page of e-solat's own (its
 * `source.txt`); confirming it against e-solat's own zone page is an owner question of the lane, since the lane was
 * held to the fetcher's own requests. A city under the wrong zone is still never early where the zones ride beside
 * each other (`Jakim.withNeighbours`: every zone with a listed town or named place near it), which is the guard
 * that does not rest on this reading.
 */
object JakimZoneCities {
    class City(val id: Int, val lat: Double, val lon: Double)

    private fun c(id: Int, lat: Double, lon: Double) = City(id, lat, lon)

    val byZone: Map<String, List<City>> = mapOf(
        "WLY01" to listOf(
            c(1735161, 3.1412, 101.68653), // Kuala Lumpur
            c(12557764, 3.19916, 101.64983), // Taman Petaling
            c(1735162, 3.207, 101.727), // Setapak
            c(12750606, 3.20056, 101.73972), // Wangsa Maju
            c(13118362, 3.04913, 101.68036), // Bukit Jalil
            c(13118233, 3.17822, 101.70312), // Titiwangsa
            c(11054399, 3.14657, 101.71023), // Bukit Bintang
            c(1744763, 3.18333, 101.68333), // Sentul
            c(1760605, 3.21398, 101.64018), // Kepong
            c(6697380, 2.93527, 101.69112), // Putrajaya
            c(7779082, 3.06881, 101.68971), // Sri Petaling
            c(12680194, 3.22124, 101.72324), // Taman Melati
            c(12750614, 3.08469, 101.69086), // Kuchai Lama
            c(12750575, 3.12945, 101.67004), // Bangsar
            c(13118095, 3.1868, 101.65085), // Taman Sri Sinar
            c(12750574, 3.14506, 101.62617), // Taman Tun Dr Ismail
            c(13118197, 3.06028, 101.7408), // Alam Damai
            c(13118231, 3.10628, 101.66761), // Bukit Kerinchi
            c(12750571, 3.16866, 101.65076), // Mont Kiara
            c(12750572, 3.07292, 101.6763), // Taman OUG
            c(13118381, 3.10591, 101.68683), // Taman Desa
            c(13118230, 3.08385, 101.70895), // Desa Petaling
            c(1744508, 3.11667, 101.68333), // Seputeh
            c(12746672, 3.18611, 101.62875), // Desa Parkcity
        ),
        "SGR01" to listOf(
            c(1771023, 3.15, 101.53333), // Kampung Baru Subang
            c(1735158, 3.10726, 101.60671), // Petaling Jaya
            c(1732903, 3.08507, 101.53281), // Shah Alam
            c(8504423, 3.04384, 101.58062), // Subang Jaya
            c(13118225, 3.21727, 101.5608), // Bukit Rahman Putra
            c(7795834, 3.2549, 101.6668), // Selayang Baru Utara
            c(12514556, 3.15454, 101.58022), // Kota Damansara
            c(1749822, 3.0, 101.61667), // Puchong
            c(1735166, 3.238, 101.682), // Batu Caves
            c(10941913, 2.99424, 101.78875), // Kajang
            c(1771298, 3.05, 101.76667), // Kampong Baharu Cheras Batu Sebelas
            c(1735153, 3.21, 101.561), // Sungai Buloh
            c(1734821, 2.6931, 101.7498), // Sepang
            c(11054411, 3.0693, 101.6074), // Bandar Sunway
            c(13118277, 3.14818, 101.61407), // Bandar Utama
            c(1735150, 3.3213, 101.5767), // Rawang
            c(1732676, 3.5638, 101.6581), // Kuala Kubu Baharu
            c(13118091, 3.10155, 101.45565), // Setia Alam
            c(13118229, 3.28531, 101.4741), // Bandar Tasik Puteri
            c(1744246, 3.03333, 101.71667), // Seri Kembangan
            c(1735168, 3.15, 101.76667), // Ampang
            c(13061022, 3.0542, 101.79844), // Bandar Mahkota Cheras
            c(12750644, 3.21083, 101.74825), // Taman Melawati
            c(13118096, 3.04926, 101.64153), // Bandar Kinrara
            c(1734798, 2.9516, 101.843), // Semenyih
            c(12514620, 3.19893, 101.59184), // Damansara Damai
            c(6930887, 2.92281, 101.65718), // Cyberjaya
            c(1735148, 3.3646, 101.6041), // Serendah
            c(1771304, 3.02938, 101.74714), // Kampung Baru Balakong
            c(10792382, 2.99361, 101.57255), // Putra Heights
            c(12746668, 3.11898, 101.58656), // Ara Damansara
            c(1735172, 3.1137, 101.8142), // Hulu Langat
            c(12535033, 3.1903, 101.61061), // Bandar Sri Damansara
            c(1734797, 2.9, 101.78333), // Bangi
            c(12622018, 3.18523, 101.52461), // Elmina
            c(12648574, 3.23208, 101.70484), // Taman Greenwood
            c(13117827, 3.05469, 101.75939), // Bandar Tun Hussein Onn
            c(13118193, 3.32752, 101.53106), // Bandar Country Homes
            c(12746683, 3.24177, 101.53296), // Kampung Setia
            c(12625525, 3.16027, 101.51501), // Denai Alam
            c(1738412, 3.18, 101.773), // Ulu Kelang
            c(13118198, 3.12036, 101.62246), // SS2
            c(1750974, 3.2856, 101.519), // Ladang Seri Kundang
            c(1732893, 3.31667, 101.46667), // Batu Arang
            c(13118276, 2.81232, 101.73562), // Bandar Baru Salak Tinggi
            c(1759486, 3.2594, 101.5541), // Kuang
            c(1735147, 3.4537, 101.6413), // Hulu Yam Lama
            c(13118055, 3.41558, 101.55709), // Bukit Beruntung
            c(12625526, 3.14165, 101.59089), // Tropicana Indah
            c(12750607, 2.94761, 101.84695), // Seminyih
        ),
        "JHR02" to listOf(
            c(1732752, 1.4655, 103.7578), // Johor Bahru
            c(1732747, 1.5243, 103.824), // Pelentong
            c(10063567, 1.39324, 103.62322), // Iskandar Puteri
            c(1732722, 1.462, 103.9053), // Pasir Gudang
            c(12750654, 1.53333, 103.66667), // Mukim Pulai
            c(1732724, 1.5024, 103.744), // Kampung Larkin Lama
            c(1762110, 1.532, 103.7549), // Kampung Kangkar Teberau
            c(13118272, 1.50434, 103.87525), // Bandar Seri Alam
            c(1732745, 1.53741, 103.65779), // Skudai
            c(1732721, 1.4726, 103.878), // Kampung Pasir Gudang Baru
            c(1732750, 1.4907, 103.8788), // Masai
            c(12750652, 1.48061, 103.68381), // Perling
            c(1738294, 1.6, 103.81667), // Ulu Tiram
            c(1732741, 1.6006, 103.6419), // Taman Senai
            c(7619704, 1.5392, 103.8092), // Johor Jaya
            c(1732755, 1.4484, 103.5873), // Gelang Patah
            c(1732742, 1.6561, 103.6032), // Kulai
            c(1732746, 1.5489, 103.6965), // Kempas
            c(13118228, 1.54631, 103.71829), // Setia Tropika
            c(12750583, 1.47999, 103.65959), // Bukit Indah
            c(1732738, 1.7381, 103.8999), // Kota Tinggi
            c(7619709, 1.511, 103.8198), // Taman Permas Jaya
            c(7858022, 1.5331, 103.6155), // Taman Universiti
            c(13118271, 1.51219, 103.9166), // Taman Scientex
            c(7858028, 1.4714, 103.5675), // Kampung Jelutong Tengah
            c(1732734, 1.7114, 103.5276), // Kampung Sedenak
            c(1732826, 2.4312, 103.8405), // Mersing
            c(1780841, 1.776, 103.8736), // Kampung Batu Empat
            c(13118270, 1.64958, 103.62661), // Bandar Putra Kulai
            c(13118269, 1.45768, 103.64554), // Horizon Hills
            c(1732785, 1.3651, 104.1118), // Pengerang
            c(7614200, 1.87, 103.6158), // Bandar Tenggara
        ),
        "KTN01" to listOf(
            c(1736376, 6.12361, 102.24333), // Kota Bharu
            c(1736372, 6.04934, 102.13987), // Pasir Mas
            c(1735545, 5.83333, 102.4), // Pasir Puteh
            c(1735572, 5.5313, 102.19925), // Kuala Krai
            c(1766249, 5.91667, 102.21667), // Kampong Pangkal Kalong
            c(1735553, 5.8, 102.15), // Tanah Merah
            c(1750582, 6.03333, 102.28333), // Peringat
            c(1768664, 6.0, 102.25), // Kampong Kadok
            c(1736366, 6.11667, 102.2), // Wakaf Baharu
        ),
        "SBH07" to listOf(
            c(1733432, 5.9749, 116.0724), // Kota Kinabalu
            c(1733440, 5.9258, 116.06094), // Putatan
            c(1733438, 5.90702, 116.10146), // Donggongon
            c(13118086, 5.91161, 116.04991), // Petagas
            c(1733502, 5.9538, 116.6641), // Ranau
            c(1733353, 5.73333, 115.93333), // Papar
            c(1733449, 5.8231, 116.0466), // Kinarut
        ),
        "SWK08" to listOf(
            c(1735634, 1.55, 110.33333), // Kuching
            c(1734539, 1.41667, 110.15), // Bau
            c(1737012, 1.67439, 109.85171), // Lundu
        ),
        "SBH01" to listOf(
            c(1734052, 5.8402, 118.1179), // Sandakan
        ),
        "PLS01" to listOf(
            c(1736278, 6.4414, 100.19862), // Kangar
            c(6819209, 6.42942, 100.28174), // Kampung Hutan Arau
        ),
        "PNG01" to listOf(
            c(13100478, 5.37278, 100.27972), // Paya Terubong
            c(1735079, 5.36301, 100.4667), // Bukit Mertajam
            c(1735106, 5.41123, 100.33543), // George Town
            c(1764027, 5.36966, 100.31353), // Kampung Sungai Glugur
            c(1764160, 5.32699, 100.27348), // Kampung Sungai Ara
            c(1735093, 5.48032, 100.49849), // Tasek Glugor
            c(1735102, 5.29992, 100.26052), // Bayan Lepas
            c(1735076, 5.3991, 100.36382), // Butterworth
            c(1733331, 5.25198, 100.49046), // Val Dor
            c(1735077, 5.38333, 100.38333), // Perai
            c(1772808, 5.38998, 100.31885), // Jelutong
            c(1778252, 5.34605, 100.43917), // Bukit Tengah
            c(1735086, 5.16586, 100.47793), // Nibong Tebal
            c(1772983, 5.21525, 100.49211), // Jawi
            c(1742815, 5.44266, 100.42972), // Sungai Dua
            c(1759378, 5.40375, 100.45745), // Kubang Semang
            c(1750500, 5.46339, 100.38144), // Permatang Kuching
            c(1735096, 5.34916, 100.23938), // Balik Pulau
            c(1778262, 5.27179, 100.4601), // Bukit Tambun
            c(1887985, 5.289, 100.247), // Kampung Teluk Kumbar
            c(1735074, 5.4709, 100.24529), // Batu Feringgi
            c(13118223, 5.19779, 100.49244), // Sungai Jawi
            c(1735069, 5.48333, 100.38333), // Kampung Teluk Air Tawar
            c(1748731, 5.33096, 100.28296), // Relau
            c(6833189, 5.37215, 100.30708), // Glugor
            c(12750617, 5.29775, 100.21147), // Bukit Gemuroh
            c(1735506, 5.51707, 100.4265), // Kepala Batas
            c(1782366, 5.40158, 100.27815), // Ayer Itam
        ),
        "WLY02" to listOf(
            c(1733782, 5.28028, 115.2475), // Labuan
            c(7302815, 5.28319, 115.25371), // Bandar Labuan
        ),
        "JHR03" to listOf(
            c(1732811, 2.03046, 103.31689), // Kluang
            c(1732700, 1.825, 103.3107), // Simpang Renggam
            c(1732710, 1.5059, 103.3816), // Pontian Besar
            c(7620438, 1.7541, 103.2798), // Kawasan Penempatan Ulu Sungai Benut
            c(7620439, 1.7497, 103.277), // Kawasan Penempatan Ulu Benut Tiga
            c(7620470, 1.7439, 103.3207), // Kawasan Penempatan Ulu Benut Lima
            c(1732706, 1.51, 103.5141), // Pekan Nenas
            c(1764318, 1.8278, 103.3), // Kampung Simpang Renggam
        ),
        "JHR04" to listOf(
            c(1732869, 2.0442, 102.5689), // Muar
            c(1732687, 1.8548, 102.9325), // Batu Pahat
            c(1732859, 2.1495, 102.7704), // Pagoh
            c(1732680, 1.976, 102.8835), // Parit Sulung
            c(1732846, 2.5148, 102.8158), // Segamat
            c(1778385, 2.211, 102.6546), // Bukit Gambir
            c(1732857, 2.2673, 102.5453), // Tangkak
            c(1732858, 2.2097, 102.7095), // Gerisek
            c(7798288, 2.428, 102.6801), // Taman Pelangi
            c(1732871, 2.0441, 102.6527), // Bakri
            c(1732814, 2.0136, 103.0659), // Yong Peng
            c(1734881, 2.5825, 102.711), // Batu Anam
            c(1732877, 2.385, 103.021), // Labis
            c(1764685, 2.35, 102.876), // Kampung Selang
            c(1766795, 1.7954, 102.8971), // Kampung Minyak Beku
            c(1732698, 1.8681, 103.1124), // Parit Raja
        ),
        "KDH01" to listOf(
            c(1736309, 6.12104, 100.36014), // Alor Setar
            c(1736302, 6.26812, 100.42167), // Jitra
            c(6807244, 6.08838, 100.39167), // Kampung Alor Mengkudu
            c(1736338, 6.17055, 100.51885), // Pokok Sena
            c(1736305, 6.1, 100.3), // Kuala Kedah
            c(1736284, 6.43422, 100.43133), // Changloon
            c(1771606, 6.184, 100.37752), // Anak Bukit
        ),
        "KDH02" to listOf(
            c(6847550, 5.58822, 100.37085), // Kota Kuala Muda
            c(1735498, 5.647, 100.48772), // Sungai Petani
            c(1765769, 5.99516, 100.47885), // Pendang
            c(1735489, 5.7989, 100.37264), // Yan Besar
            c(1734439, 5.72743, 100.50876), // Bedong
            c(1742731, 5.6998, 100.51449), // Sungai Lalang
            c(1736314, 6.02867, 100.40691), // Tokai
            c(1735485, 5.81717, 100.47381), // Gurun
        ),
        "KDH03" to listOf(
            c(6805124, 5.83333, 100.75), // Sik
        ),
        "KDH04" to listOf(
            c(1759564, 5.6, 100.65), // Kuala Ketil
            c(1734445, 5.65489, 100.89541), // Pulai
            c(1734444, 5.63638, 100.84517), // Kampung Kupang
            c(6847278, 5.71681, 100.65819), // Teloi Kanan
        ),
        "KDH05" to listOf(
            c(1734393, 5.36499, 100.56177), // Kulim
            c(1734418, 5.51307, 100.55356), // Padang Serai
            c(1734407, 5.13428, 100.49549), // Bandar Baharu
            c(1734389, 5.43058, 100.53012), // Lunas
        ),
        "KDH06" to listOf(
            c(1222396, 6.32649, 99.8432), // Kuah
            c(6804851, 6.35196, 99.78791), // Kampung Baru Ulu Melaka
        ),
        "KTN02" to listOf(
            c(1734651, 4.88441, 101.96857), // Gua Musang
            c(1734464, 5.7, 101.83333), // Kampung Jeli
        ),
        "MLK01" to listOf(
            c(1734759, 2.196, 102.2405), // Malacca
            c(1770351, 2.2152, 102.2851), // Kampung Bukit Baharu
            c(1734748, 2.3125, 102.2805), // Durian Tunggal
            c(1761242, 2.2186, 102.1995), // Klebang Besar
            c(1734753, 2.2654, 102.2801), // Kampung Ayer Keroh
            c(1768286, 2.2958, 102.2537), // Kampung Kerubong
            c(1767021, 2.35, 102.11667), // Kampong Masjid Tanah
            c(1734757, 2.269, 102.1427), // Sungai Udang
            c(1780890, 2.2487, 102.246), // Batu Berendam
            c(1763378, 2.2666, 102.2004), // Kampung Tanjung Minyak
            c(1734745, 2.3804, 102.2089), // Alor Gajah
            c(1769827, 2.2635, 102.2179), // Kampung Cheng
            c(7797910, 2.2618, 102.2317), // Kawasan Perindustrian Cheng
            c(1734762, 2.1449, 102.4251), // Merlimau
            c(1778290, 2.2594, 102.1838), // Bukit Rambai
            c(1779395, 2.2898, 102.1928), // Kampung Bertam Ulu
            c(1767194, 2.3829, 102.3311), // Kampung Machap Baru
            c(1771485, 2.2139, 102.3278), // Kampung Ayer Molek
        ),
        "NGS01" to listOf(
            c(1734883, 2.7083, 102.5047), // Rompin
            c(1734839, 2.8079, 102.4049), // Bahau
            c(1734885, 2.5832, 102.6117), // Gemas
            c(1734738, 2.4701, 102.2302), // Tampin
        ),
        "NGS02" to listOf(
            c(1734854, 2.5897, 102.0913), // Rembau
            c(1734841, 2.7389, 102.2487), // Kuala Pilah
        ),
        "NGS03" to listOf(
            c(1734810, 2.7297, 101.9381), // Seremban
            c(1734815, 2.52462, 101.79651), // Port Dickson
            c(1734826, 2.691, 101.989), // Taman Senawang Indah
            c(12750582, 2.68982, 101.90766), // Seremban 2
            c(1771287, 2.8033, 101.7972), // Kampung Baharu Nilai
            c(7797211, 2.6831, 101.97), // Taman Seremban Jaya
            c(13118149, 2.67375, 101.8721), // Bandar Sri Sendayan
            c(1734721, 2.46146, 101.85951), // Kampung Teluk Kemang
            c(1756720, 2.5703, 101.833), // Lukut
        ),
        "PHG02" to listOf(
            c(1735227, 3.8077, 103.326), // Kuantan
            c(1734971, 3.4836, 103.3996), // Pekan
            c(13118274, 3.37457, 102.94343), // FELDA Chini
        ),
        "PHG03" to listOf(
            c(1732637, 3.586, 102.773), // Maran
            c(1735022, 3.4506, 102.4176), // Temerluh
            c(7566098, 3.8682, 102.5862), // Kampung Perak
            c(1735018, 3.4854, 102.3484), // Mentakab
            c(1732602, 3.936, 102.3626), // Jerantut
        ),
        "PHG04" to listOf(
            c(1779790, 3.52229, 101.90866), // Bentong Town
            c(1732667, 3.5316, 101.9038), // Bentung
            c(1732663, 3.7899, 101.857), // Raub
            c(1733023, 4.1842, 102.0468), // Kuala Lipis
        ),
        "PRK01" to listOf(
            c(1732672, 3.681, 101.5198), // Tanjong Malim
            c(1732927, 4.0, 101.31667), // Sungkai
            c(1735287, 4.11667, 101.28333), // Bidur
            c(1735282, 4.16667, 101.2), // Tapah Road
        ),
        "PRK02" to listOf(
            c(1734634, 4.5841, 101.0829), // Ipoh
            c(7473418, 4.6411, 101.1393), // Bercham
            c(7473443, 4.6192, 101.0685), // Kampung Manjoi
            c(1735265, 4.3945, 101.1947), // Sungai Siput
            c(1735268, 4.46916, 101.04107), // Batu Gajah
            c(1734599, 4.76667, 100.93333), // Kuala Kangsar
            c(1734594, 4.76667, 100.85), // Padang Rengas
            c(1734637, 4.5388, 101.0365), // Lahat
            c(1734626, 4.7211, 101.1189), // Chemor
            c(1735274, 4.3, 101.15), // Kampar
        ),
        "PRK03" to listOf(
            c(1734372, 5.41667, 101.13333), // Gerik
        ),
        "PRK05" to listOf(
            c(1735459, 4.02219, 101.02083), // Teluk Intan
            c(1735453, 4.2168, 100.6996), // Sitiawan
            c(7792200, 4.1987, 100.67), // Seri Manjung
            c(1735433, 4.48333, 100.91667), // Parit
            c(1735450, 4.2323, 100.6298), // Lumut
            c(1735188, 3.8766, 100.9328), // Hutan Melintang
            c(1732915, 3.9851, 101.1237), // Cangkat Jong
            c(1752256, 4.4557, 100.6288), // Pantai Remis
        ),
        "PRK06" to listOf(
            c(1734586, 4.85, 100.73333), // Taiping
            c(1734576, 4.95, 100.63333), // Simpang Empat
            c(1734577, 4.88333, 100.73333), // Kamunting
            c(1734399, 5.2236, 100.69262), // Selama
            c(1735092, 5.01821, 100.42824), // Kuala Kurau
            c(1735089, 5.12671, 100.49316), // Parit Buntar
            c(1734579, 4.96667, 100.8), // Batu Kurau
            c(1734409, 5.0108, 100.54101), // Bagan Serai
        ),
        "SBH03" to listOf(
            c(1733953, 5.02298, 118.32897), // Lahad Datu
            c(1733697, 4.48178, 118.61119), // Semporna
        ),
        "SBH04" to listOf(
            c(1734199, 4.24482, 117.89115), // Tawau
        ),
        "SBH05" to listOf(
            c(1736458, 6.88732, 116.82364), // Kudat
        ),
        "SBH08" to listOf(
            c(1734098, 5.3378, 116.1602), // Keningau
            c(1734114, 5.0393, 116.4274), // Bandar Nabawan
            c(1733468, 5.71667, 116.4), // Tambunan
        ),
        "SBH09" to listOf(
            c(1733868, 5.07903, 115.54964), // Sipitang
            c(1733376, 5.5714, 115.5984), // Kuala Penyu
            c(1733874, 5.3473, 115.7455), // Beaufort
        ),
        "SGR02" to listOf(
            c(1769014, 3.31667, 101.4), // Ijok
            c(1763999, 3.4161, 101.173), // Kampung Sungai Kajang
            c(1732891, 3.35, 101.25), // Kuala Selangor
            c(1735199, 3.6746, 100.9867), // Sungai Besar
            c(11429145, 3.2389, 101.42793), // Bandar Puncak Alam
            c(1732892, 3.38333, 101.41667), // Batang Berjuntai
            c(1763412, 3.4242, 101.1849), // Kampung Tanjung Karang
        ),
        "SGR03" to listOf(
            c(1732901, 3.13333, 101.38333), // Kapar
            c(1732905, 3.03667, 101.44333), // Klang
            c(13118145, 3.08737, 101.43339), // Bandar Bukit Raja
            c(13118146, 3.01083, 101.42081), // Pandamaran
            c(1734768, 2.90796, 101.46877), // Teluk Panglima Garang
            c(1734781, 2.8136, 101.50185), // Banting
            c(1734793, 2.8724, 101.49484), // Jenjarum
            c(1741562, 2.6579, 101.5629), // Tanjung Sepat
            c(1734769, 2.99959, 101.39287), // Pelabuhan Klang
        ),
        "SWK01" to listOf(
            c(1737714, 4.75477, 115.01011), // Limbang
            c(1737589, 4.8604, 115.409), // Lawas
        ),
        "SWK02" to listOf(
            c(1738050, 4.39928, 113.99163), // Miri
            c(1737919, 4.18333, 114.31667), // Marudi
            c(1737247, 3.86203, 113.71521), // Niah
        ),
        "SWK03" to listOf(
            c(1737486, 3.16667, 113.03333), // Bintulu
            c(1735404, 2.88333, 112.85), // Tatau
            c(1737489, 3.11667, 113.26667), // Sebauh
            c(1737314, 2.7, 113.78333), // Belaga
        ),
        "SWK04" to listOf(
            c(1735902, 2.3, 111.81667), // Sibu
            c(13118085, 2.24026, 111.97135), // Sibu Jaya
            c(1735339, 2.89435, 112.09145), // Mukah
            c(1737185, 1.99546, 112.93254), // Kapit
            c(1736973, 2.1, 112.15), // Kanowit
            c(1736929, 2.66667, 112.08333), // Dalat
        ),
        "SWK05" to listOf(
            c(1735837, 2.11667, 111.51667), // Sarikei
            c(1735851, 2.08333, 111.61667), // Maradong
            c(1736781, 2.51688, 111.43002), // Daro
            c(1735889, 2.01667, 111.91667), // Julau
        ),
        "SWK06" to listOf(
            c(1735799, 1.24722, 111.45278), // Sri Aman
            c(1736902, 1.4, 111.51667), // Betong
            c(1736760, 1.73333, 111.33333), // Saratok
            c(1736870, 1.13864, 111.66665), // Engkilili
        ),
        "SWK07" to listOf(
            c(1744288, 1.16667, 110.56667), // Serian
            c(1735681, 1.38333, 110.75), // Simunjan
        ),
        "TRG01" to listOf(
            c(1734705, 5.3302, 103.1408), // Kuala Terengganu
            c(12534340, 5.37293, 103.02053), // Kuala Nerus
            c(1734715, 5.2056, 103.2059), // Marang
        ),
        "TRG04" to listOf(
            c(1732945, 4.25, 103.41667), // Cukai
            c(1734308, 4.775, 103.4162), // Dungun
            c(1734316, 4.5141, 103.4483), // Kertih
            c(1734313, 4.6374, 103.4368), // Paka
        ),
    )

    /**
     * The Malaysian cities of the app's list under no zone, by id: a district border runs through the town or beside
     * it (Cheras, Bandar Saujana Utama, Kampong Sidam, Siburan), the zone list names its district or sub-district
     * under no zone (Bandar Tun Razak, in Rompin outside PHG07's three mukims; Beluru, Tebedu, Selangau, Pakan), or
     * the city list's own region and point disagree (Kampong Dungun, Padang Besar).
     */
    val unattached: Set<Int> = setOf(1777077, 13118224, 1764375, 1769612, 1737936, 1734506, 1734537, 7697762, 12547009, 1753004, 1735963)
}
