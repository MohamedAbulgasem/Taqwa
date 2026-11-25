package world.taqwa.app.prayer.engine.registry.data

import world.taqwa.app.prayer.engine.registry.authorities.Kemenag.Kabkota
import world.taqwa.app.prayer.engine.registry.authorities.Kemenag.m

/**
 * Kemenag's kab/kota of the round of 12 Nov 2026 (Kemenag.kt): each at the app's own GeoNames point for its kota or
 * regency seat, its horizon class read from its own table, its radius by rulings R103/R113 (OpenStreetMap's
 * admin_level-5 boundaries, Overpass, 12 Nov 2026: the distance to the nearest other kab/kota less 0.5 km, floored to
 * 0.1 km), its margins (seconds: Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha) fitted over its 2026 and 2027 tables at
 * safety 5 s, its 2028 held out (gate/id-kemenag.tsv). In the order they were fetched.
 */
internal object KemenagKabkota {
    val round: List<Kabkota> = listOf(
        Kabkota("1221", "Kota Bekasi", -6.2349, 106.9896, highland = false, radiusKm = 3.3, m(6, -11, 65, 16, 6, 5)),
        Kabkota("1225", "Kota Depok", -6.4, 106.81861, highland = false, radiusKm = 3.5, m(21, 2, 79, 29, 18, 19)),
        Kabkota("1107", "Kota Tangerang", -6.17806, 106.63, highland = false, radiusKm = 3.3, m(6, -11, 67, 18, 6, 5)),
        Kabkota("1108", "Kota Tangerang Selatan", -6.28862, 106.71789, highland = false, radiusKm = 3.2, m(10, -8, 69, 21, 8, 8)),
        Kabkota("0506", "Kota Batam", 1.14937, 104.02491, highland = false, radiusKm = 17.3, m(1, -17, 61, 15, 2, 1)),
        Kabkota("0412", "Kota Pekanbaru", 0.51667, 101.44167, highland = false, radiusKm = 6.6, m(6, -11, 67, 20, 5, 4)),
        Kabkota("1014", "Kota Bandar Lampung", -5.42917, 105.26111, highland = false, radiusKm = 4.2, m(9, -9, 67, 20, 8, 9)),
        Kabkota("0314", "Kota Padang", -0.94924, 100.35427, highland = false, radiusKm = 14.0, m(7, -12, 65, 22, 6, 6)),
        Kabkota("2310", "Kota Samarinda", -0.49167, 117.14583, highland = false, radiusKm = 8.5, m(14, -3, 73, 26, 16, 15)),
        Kabkota("1227", "Kota Tasikmalaya", -7.3274, 108.2207, highland = false, radiusKm = 2.1, m(7, -8, 68, 22, 9, 8)),
        Kabkota("1106", "Kota Serang", -6.11528, 106.15417, highland = false, radiusKm = 4.4, m(7, -9, 68, 20, 7, 7)),
        Kabkota("2308", "Kota Balikpapan", -1.26753, 116.82887, highland = false, radiusKm = 5.5, m(8, -9, 68, 18, 9, 8)),
        Kabkota("2013", "Kota Pontianak", -0.03194, 109.325, highland = false, radiusKm = 3.4, m(11, -17, 65, 26, 10, 11)),
        Kabkota("2113", "Kota Banjarmasin", -3.31987, 114.59075, highland = false, radiusKm = 2.6, m(9, -10, 68, 20, 9, 8)),
        Kabkota("0610", "Kota Jambi", -1.6, 103.61667, highland = false, radiusKm = 3.6, m(11, -7, 69, 25, 9, 9)),
        Kabkota("1223", "Kota Cimahi", -6.87222, 107.5425, highland = true, radiusKm = 0.9, m(9, -7, 67, 19, 9, 8)),
        Kabkota("1434", "Kota Surakarta", -7.55611, 110.83167, highland = false, radiusKm = 2.0, m(9, -7, 69, 31, 11, 12)),
        Kabkota("1922", "Kota Kupang", -10.17083, 123.60694, highland = false, radiusKm = 3.3, m(13, -2, 74, 26, 15, 14)),
        Kabkota("1105", "Kota Cilegon", -6.0144, 106.0542, highland = false, radiusKm = 2.4, m(10, -7, 69, 21, 9, 8)),
        Kabkota("2914", "Kota Manado", 1.48218, 124.84892, highland = false, radiusKm = 2.7, m(9, -7, 70, 19, 9, 9)),
        Kabkota("1810", "Kota Mataram", -8.58333, 116.11667, highland = false, radiusKm = 1.7, m(13, -4, 71, 22, 12, 11)),
        Kabkota("0710", "Kota Bengkulu", -3.80044, 102.26554, highland = false, radiusKm = 4.5, m(10, -6, 69, 20, 10, 10)),
        Kabkota("2813", "Kota Palu", -0.90833, 119.87083, highland = false, radiusKm = 1.2, m(13, -5, 72, 24, 12, 11)),
        Kabkota("1226", "Kota Sukabumi", -6.91806, 106.92667, highland = true, radiusKm = 1.0, m(9, -8, 69, 20, 9, 9)),
        Kabkota("2717", "Kota Kendari", -3.9778, 122.51507, highland = false, radiusKm = 3.6, m(9, -10, 67, 17, 7, 8)),
        Kabkota("0411", "Kota Dumai", 1.66711, 101.44316, highland = false, radiusKm = 4.1, m(28, -4, 82, 41, 27, 28)),
        Kabkota("3110", "Kota Ambon", -3.69583, 128.18333, highland = false, radiusKm = 7.0, m(10, -6, 70, 19, 8, 9)),
        Kabkota("1224", "Kota Cirebon", -6.7063, 108.557, highland = false, radiusKm = 0.3, m(8, -9, 69, 24, 8, 9)),
        Kabkota("1431", "Kota Pekalongan", -6.8886, 109.6753, highland = false, radiusKm = 1.3, m(8, -9, 68, 19, 9, 9)),
        Kabkota("2214", "Kota Palangkaraya", -2.20833, 113.91667, highland = false, radiusKm = 4.9, m(14, -4, 75, 27, 16, 16)),
        Kabkota("1632", "Kota Kediri", -7.81667, 112.01667, highland = false, radiusKm = 1.1, m(8, -7, 69, 23, 10, 11)),
        Kabkota("1435", "Kota Tegal", -6.8694, 109.1402, highland = false, radiusKm = 0.4, m(10, -8, 70, 21, 9, 8)),
        Kabkota("2112", "Kota Banjarbaru", -3.4406, 114.8365, highland = false, radiusKm = 0.4, m(11, -7, 70, 22, 13, 13)),
        Kabkota("0226", "Kota Binjai", 3.6001, 98.4854, highland = false, radiusKm = 1.6, m(5, -12, 66, 22, 5, 5)),
        Kabkota("0230", "Kota Pematangsiantar", 2.9595, 99.0687, highland = false, radiusKm = 0.7, m(10, -5, 72, 24, 12, 11)),
        Kabkota("2405", "Kota Tarakan", 3.31332, 117.59152, highland = false, radiusKm = 7.2, m(0, -17, 60, 13, 2, 2)),
        Kabkota("2014", "Kota Singkawang", 0.90925, 108.98463, highland = false, radiusKm = 9.1, m(6, -8, 68, 21, 9, 9)),
        Kabkota("0507", "Kota Tanjung Pinang", 0.91667, 104.45833, highland = false, radiusKm = 6.4, m(13, -5, 72, 27, 11, 11)),
        Kabkota("0907", "Kota Pangkal Pinang", -2.12914, 106.11377, highland = false, radiusKm = 2.0, m(8, -8, 67, 21, 9, 8)),
        Kabkota("2506", "Kota Gorontalo", 0.5375, 123.0625, highland = false, radiusKm = 1.5, m(8, -8, 68, 21, 9, 8)),
        Kabkota("1203", "Kab. Bekasi", -6.26111, 107.15278, highland = false, radiusKm = 8.7, m(11, -24, 64, 28, 11, 13)),
        Kabkota("1204", "Kab. Bogor", -6.48167, 106.85417, highland = false, radiusKm = 2.3, m(16, 0, 74, 29, 16, 14)),
        Kabkota("1201", "Kab. Bandung", -7.03306, 107.51833, highland = true, radiusKm = 3.5, m(6, -11, 65, 20, 4, 5)),
        Kabkota("1614", "Kab. Malang", -8.1303, 112.5727, highland = false, radiusKm = 10.9, m(9, -6, 70, 22, 12, 12)),
        Kabkota("1607", "Kab. Jember", -8.17211, 113.69953, highland = false, radiusKm = 12.5, m(8, -9, 68, 18, 6, 8)),
        Kabkota("1216", "Kab. Sukabumi", -6.9875, 106.55139, highland = false, radiusKm = 16.5, m(7, -9, 68, 21, 9, 9)),
        Kabkota("1206", "Kab. Cianjur", -6.82222, 107.13944, highland = false, radiusKm = 12.6, m(9, -8, 69, 18, 8, 9)),
        Kabkota("1210", "Kab. Karawang", -6.30525, 107.3197, highland = false, radiusKm = 5.7, m(15, -2, 74, 25, 14, 16)),
        Kabkota("1207", "Kab. Cirebon", -6.76028, 108.48306, highland = false, radiusKm = 2.7, m(10, -7, 68, 21, 10, 10)),
        Kabkota("1624", "Kab. Sidoarjo", -7.4478, 112.7183, highland = false, radiusKm = 10.3, m(10, -8, 69, 17, 8, 9)),
        Kabkota("1407", "Kab. Cilacap", -7.72639, 109.00944, highland = false, radiusKm = 17.6, m(7, -7, 67, 20, 8, 9)),
        Kabkota("1218", "Kab. Tasikmalaya", -7.3515, 108.111, highland = false, radiusKm = 4.4, m(8, -8, 67, 20, 9, 9)),
        Kabkota("1209", "Kab. Indramayu", -6.32639, 108.32, highland = false, radiusKm = 24.0, m(8, -10, 66, 19, 7, 8)),
        Kabkota("1402", "Kab. Banyumas", -7.42139, 109.23444, highland = false, radiusKm = 8.0, m(8, -7, 68, 19, 9, 9)),
        Kabkota("1215", "Kab. Subang", -6.57, 107.75667, highland = false, radiusKm = 9.7, m(6, -9, 66, 23, 7, 8)),
        Kabkota("1606", "Kab. Gresik", -7.15389, 112.65611, highland = false, radiusKm = 3.3, m(17, 1, 77, 30, 18, 18)),
        Kabkota("1414", "Kab. Klaten", -7.70583, 110.60639, highland = false, radiusKm = 9.0, m(6, -12, 65, 23, 4, 5)),
        Kabkota("1410", "Kab. Jepara", -6.5924, 110.671, highland = false, radiusKm = 13.1, m(9, -8, 70, 19, 8, 10)),
        Kabkota("1504", "Kab. Sleman", -7.71556, 110.35556, highland = false, radiusKm = 5.1, m(9, -8, 66, 24, 8, 9)),
        Kabkota("1501", "Kab. Bantul", -7.88806, 110.32889, highland = false, radiusKm = 5.6, m(8, -10, 67, 18, 7, 6)),
        Kabkota("1423", "Kab. Semarang", -7.13972, 110.405, highland = false, radiusKm = 2.2, m(8, -9, 68, 22, 7, 8)),
        Kabkota("1415", "Kab. Kudus", -6.8048, 110.8405, highland = false, radiusKm = 3.9, m(11, -7, 69, 24, 10, 9)),
        Kabkota("1408", "Kab. Demak", -6.8909, 110.6396, highland = false, radiusKm = 8.8, m(7, -9, 69, 27, 8, 8)),
        Kabkota("1214", "Kab. Purwakarta", -6.55694, 107.44333, highland = false, radiusKm = 8.1, m(9, -8, 69, 20, 9, 8)),
        Kabkota("1217", "Kab. Sumedang", -6.85861, 107.91639, highland = false, radiusKm = 9.1, m(8, -9, 67, 18, 7, 7)),
        Kabkota("1620", "Kab. Pasuruan", -7.59939, 112.8186, highland = false, radiusKm = 3.2, m(17, 0, 75, 27, 17, 16)),
        Kabkota("1636", "Kota Pasuruan", -7.6453, 112.9075, highland = false, radiusKm = 1.4, m(9, -8, 69, 19, 8, 8)),
        Kabkota("1637", "Kota Probolinggo", -7.7543, 113.2159, highland = false, radiusKm = 1.3, m(10, -8, 68, 20, 9, 7)),
        Kabkota("1633", "Kota Madiun", -7.6298, 111.5239, highland = false, radiusKm = 1.0, m(11, -6, 69, 21, 11, 10)),
        Kabkota("1631", "Kota Blitar", -8.0983, 112.1681, highland = false, radiusKm = 1.1, m(10, -7, 69, 20, 8, 10)),
        Kabkota("1635", "Kota Mojokerto", -7.4664, 112.4338, highland = false, radiusKm = 0.2, m(9, -8, 69, 18, 8, 9)),
        Kabkota("1430", "Kota Magelang", -7.47056, 110.21778, highland = false, radiusKm = 0.3, m(7, -9, 69, 19, 10, 9)),
        Kabkota("1432", "Kota Salatiga", -7.33194, 110.49278, highland = true, radiusKm = 1.7, m(7, -10, 68, 19, 5, 7)),
        Kabkota("2912", "Kota Bitung", 1.44059, 125.12824, highland = false, radiusKm = 3.2, m(7, -10, 68, 18, 7, 8)),
        Kabkota("0121", "Kota Lhokseumawe", 5.1801, 97.1507, highland = false, radiusKm = 5.0, m(9, -7, 71, 26, 9, 10)),
        Kabkota("3209", "Kota Ternate", 0.79065, 127.38424, highland = false, radiusKm = 3.6, m(8, -8, 68, 19, 7, 8)),
        Kabkota("2309", "Kota Bontang", 0.1324, 117.4854, highland = false, radiusKm = 4.4, m(7, -9, 68, 19, 8, 9)),
        Kabkota("2716", "Kota Bau-Bau", -5.4633, 122.6024, highland = false, radiusKm = 2.8, m(6, -9, 67, 18, 6, 7)),
        Kabkota("2624", "Kota Parepare", -4.0135, 119.6255, highland = false, radiusKm = 1.1, m(5, -10, 66, 14, 6, 5)),
        Kabkota("2623", "Kota Palopo", -2.9925, 120.19694, highland = false, radiusKm = 5.2, m(10, -8, 69, 18, 8, 10)),
        Kabkota("1809", "Kota Bima", -8.46006, 118.72667, highland = false, radiusKm = 2.7, m(9, -10, 66, 18, 7, 7)),
        Kabkota("1015", "Kota Metro", -5.11306, 105.30667, highland = false, radiusKm = 1.2, m(9, -8, 69, 19, 9, 9)),
        Kabkota("1426", "Kab. Tegal", -6.9816, 109.1407, highland = false, radiusKm = 6.8, m(10, -8, 70, 22, 9, 7)),
        Kabkota("1625", "Kab. Situbondo", -7.70623, 114.00976, highland = false, radiusKm = 5.1, m(10, -7, 69, 19, 9, 9)),
        Kabkota("1602", "Kab. Banyuwangi", -8.2325, 114.35755, highland = false, radiusKm = 9.4, m(6, -14, 63, 14, 3, 4)),
        Kabkota("1608", "Kab. Jombang", -7.54595, 112.23307, highland = false, radiusKm = 8.7, m(8, -9, 68, 20, 8, 8)),
        Kabkota("1629", "Kab. Tulungagung", -8.0657, 111.9025, highland = false, radiusKm = 6.8, m(7, -8, 70, 20, 7, 9)),
        Kabkota("1417", "Kab. Pati", -6.7559, 111.038, highland = false, radiusKm = 7.5, m(7, -8, 66, 25, 7, 7)),
        Kabkota("1419", "Kab. Pemalang", -6.89193, 109.38263, highland = false, radiusKm = 3.0, m(9, -8, 70, 27, 9, 9)),
        Kabkota("1405", "Kab. Boyolali", -7.53306, 110.59583, highland = false, radiusKm = 3.5, m(7, -10, 66, 27, 8, 8)),
        Kabkota("1604", "Kab. Bojonegoro", -7.1502, 111.8817, highland = false, radiusKm = 1.4, m(7, -8, 68, 20, 9, 8)),
        Kabkota("1610", "Kab. Lamongan", -7.11667, 112.41667, highland = false, radiusKm = 6.0, m(8, -8, 69, 26, 9, 8)),
        Kabkota("1628", "Kab. Tuban", -6.8976, 112.0649, highland = false, radiusKm = 11.1, m(7, -8, 66, 20, 7, 8)),
        Kabkota("1211", "Kab. Kuningan", -6.97583, 108.48306, highland = true, radiusKm = 9.5, m(9, -7, 70, 20, 8, 10)),
        Kabkota("1212", "Kab. Majalengka", -6.83611, 108.22778, highland = false, radiusKm = 4.2, m(10, -7, 70, 21, 10, 11)),
        Kabkota("1205", "Kab. Ciamis", -7.3257, 108.3534, highland = false, radiusKm = 2.2, m(9, -9, 68, 21, 9, 9)),
        Kabkota("1101", "Kab. Lebak", -6.3591, 106.2494, highland = false, radiusKm = 4.9, m(9, -7, 70, 20, 9, 9)),
        Kabkota("1102", "Kab. Pandeglang", -6.3084, 106.1067, highland = false, radiusKm = 2.8, m(9, -7, 69, 19, 9, 8)),
        Kabkota("1626", "Kab. Sumenep", -7.01152, 113.86042, highland = false, radiusKm = 24.9, m(9, -9, 67, 21, 9, 8)),
        Kabkota("1619", "Kab. Pamekasan", -7.1568, 113.4746, highland = false, radiusKm = 9.3, m(7, -11, 66, 16, 4, 8)),
        Kabkota("1601", "Kab. Bangkalan", -7.0455, 112.7351, highland = false, radiusKm = 8.5, m(7, -12, 66, 17, 5, 5)),
        Kabkota("1616", "Kab. Nganjuk", -7.6051, 111.9035, highland = false, radiusKm = 9.4, m(9, -8, 70, 19, 9, 8)),
        Kabkota("1621", "Kab. Ponorogo", -7.8685, 111.462, highland = false, radiusKm = 8.6, m(8, -9, 69, 19, 7, 9)),
        Kabkota("1611", "Kab. Lumajang", -8.1335, 113.2248, highland = false, radiusKm = 13.8, m(8, -8, 69, 20, 9, 7)),
        Kabkota("1605", "Kab. Bondowoso", -7.91346, 113.82145, highland = false, radiusKm = 10.4, m(8, -9, 67, 18, 9, 8)),
        Kabkota("1424", "Kab. Sragen", -7.42639, 111.02222, highland = false, radiusKm = 8.4, m(9, -7, 68, 27, 7, 10)),
        Kabkota("1409", "Kab. Grobogan", -7.0868, 110.9158, highland = false, radiusKm = 10.5, m(9, -9, 69, 20, 7, 8)),
        Kabkota("1622", "Kab. Probolinggo", -7.75845, 113.39624, highland = false, radiusKm = 16.9, m(4, -13, 64, 23, 4, 4)),
        Kabkota("3413", "Kota Sorong", -0.87956, 131.26104, highland = false, radiusKm = 3.9, m(3, -14, 63, 10, 3, 2)),
    )

    /**
     * The second round of 12 Nov 2026 (Kemenag.kt): 119 more, the largest kab/kota whose kota or regency seat is an app
     * city still on the edge, 18 kota and 101 regencies; eight of the regencies by the name the app gives the seat
     * (Bireun for Bireuen, Maros for Turikale, Pangkalanbuun, Tanjung Balai for Tanjung Balai Karimun, Tanjung,
     * Toli-Toli for Baolan, Majene for Banggae, Daruba for Morotai Selatan). Points, horizon classes, radii and fits as
     * the first round's, but where no other kab/kota lies within the Overpass query's 30 km (eighteen, all on islands
     * or coasts: Kab. Sambas, Nias Selatan, Merauke, Fakfak, …) the radius is the query's own reach less 0.5 km,
     * 29.5 km; three circles whose own tables went past class B's 2 min at Asr across them are cut back to where every
     * event holds (Kab. Sambas 20.0, Nias Selatan 21.0, Pulau Morotai 26.0). Kab. Puncak Jaya and Lanny Jaya, high in Papua, print a Subuh about 4 min before and an Isya about
     * 4.5 min after the angles' (the fitted margins take both), so their end of eating comes earlier with it.
     */
    val round2: List<Kabkota> = listOf(
        Kabkota("1805", "Kab. Lombok Timur", -8.6503, 116.5318, highland = false, radiusKm = 11.6, m(6, -9, 65, 17, 7, 7)),
        Kabkota("0201", "Kab. Asahan", 2.9845, 99.6158, highland = false, radiusKm = 6.1, m(4, -11, 64, 19, 6, 5)),
        Kabkota("1804", "Kab. Lombok Tengah", -8.70536, 116.27036, highland = false, radiusKm = 8.3, m(9, -8, 68, 19, 8, 7)),
        Kabkota("0210", "Kab. Langkat", 3.73335, 98.45025, highland = false, radiusKm = 5.5, m(11, -10, 69, 24, 9, 9)),
        Kabkota("1420", "Kab. Purbalingga", -7.38806, 109.36389, highland = false, radiusKm = 4.9, m(9, -8, 68, 20, 8, 9)),
        Kabkota("1002", "Kab. Lampung Utara", -4.82505, 104.8817, highland = false, radiusKm = 11.4, m(9, -8, 68, 21, 9, 8)),
        Kabkota("1404", "Kab. Blora", -6.9698, 111.4186, highland = false, radiusKm = 7.9, m(11, -7, 70, 28, 9, 9)),
        Kabkota("1703", "Kab. Buleleng", -8.112, 115.08818, highland = false, radiusKm = 17.1, m(9, -9, 68, 18, 8, 8)),
        Kabkota("1502", "Kab. Gunungkidul", -7.96556, 110.60361, highland = false, radiusKm = 12.1, m(9, -7, 68, 20, 9, 8)),
        Kabkota("2009", "Kab. Sambas", 1.36259, 109.30093, highland = false, radiusKm = 20.0, m(6, -11, 67, 17, 8, 6)),
        Kabkota("1422", "Kab. Rembang", -6.7036, 111.3416, highland = false, radiusKm = 10.5, m(6, -10, 65, 19, 7, 6)),
        Kabkota("2102", "Kab. Banjar", -3.4109, 114.8642, highland = false, radiusKm = 2.6, m(16, -4, 74, 27, 13, 12)),
        Kabkota("3006", "Kab. Polewali Mandar", -3.4324, 119.3435, highland = false, radiusKm = 13.1, m(16, -3, 73, 25, 14, 14)),
        Kabkota("2621", "Kab. Wajo", -4.1279, 120.0297, highland = false, radiusKm = 8.3, m(7, -9, 66, 19, 8, 9)),
        Kabkota("1708", "Kab. Tabanan", -8.5413, 115.12522, highland = false, radiusKm = 3.5, m(7, -9, 68, 26, 8, 8)),
        Kabkota("0116", "Kab. Pidie", 5.3848, 95.9609, highland = false, radiusKm = 13.7, m(7, -8, 69, 24, 9, 9)),
        Kabkota("0808", "Kab. Ogan Ilir", -3.25249, 104.67555, highland = false, radiusKm = 13.3, m(12, -15, 67, 28, 11, 10)),
        Kabkota("0803", "Kab. Lahat", -3.78514, 103.54279, highland = false, radiusKm = 18.1, m(9, -8, 68, 20, 9, 9)),
        Kabkota("3105", "Kab. Maluku Tengah", -3.29833, 128.96882, highland = false, radiusKm = 14.7, m(11, -5, 71, 23, 10, 10)),
        Kabkota("0207", "Kab. Labuhanbatu", 2.1, 99.83333, highland = false, radiusKm = 7.8, m(7, -8, 67, 22, 8, 9)),
        Kabkota("2205", "Kab. Kapuas", -3.00913, 114.38759, highland = false, radiusKm = 11.9, m(4, -18, 62, 16, 3, 3)),
        Kabkota("2208", "Kab. Kotawaringin Timur", -2.5315, 112.9496, highland = false, radiusKm = 21.5, m(4, -13, 64, 16, 6, 6)),
        Kabkota("2012", "Kab. Sintang", 0.06667, 111.5, highland = false, radiusKm = 23.3, m(21, -20, 69, 36, 21, 21)),
        Kabkota("2611", "Kab. Luwu Utara", -2.5537, 120.3279, highland = false, radiusKm = 24.4, m(9, -9, 67, 17, 9, 8)),
        Kabkota("0607", "Kab. Tanjung Jabung Barat", -0.81623, 103.46111, highland = false, radiusKm = 4.5, m(7, -9, 67, 22, 8, 8)),
        Kabkota("1915", "Kab. Sikka", -8.6199, 122.2111, highland = false, radiusKm = 24.4, m(8, -10, 67, 17, 7, 7)),
        Kabkota("1705", "Kab. Jembrana", -8.35694, 114.61694, highland = false, radiusKm = 14.4, m(20, 3, 78, 37, 19, 19)),
        Kabkota("1917", "Kab. Sumba Barat Daya", -9.4283, 119.238, highland = false, radiusKm = 14.7, m(16, -6, 72, 23, 13, 12)),
        Kabkota("0901", "Kab. Bangka", -1.85442, 106.12215, highland = false, radiusKm = 22.0, m(13, -5, 73, 24, 15, 15)),
        Kabkota("0214", "Kab. Nias Selatan", 0.5729, 97.8034, highland = false, radiusKm = 21.0, m(4, -13, 62, 19, 5, 5)),
        Kabkota("2001", "Kab. Bengkayang", 0.83194, 109.43589, highland = false, radiusKm = 7.1, m(-3, -19, 56, 8, -4, -4)),
        Kabkota("0206", "Kab. Karo", 3.1001, 98.4908, highland = true, radiusKm = 10.1, m(8, -10, 67, 22, 5, 7)),
        Kabkota("0224", "Kab. Tapanuli Utara", 2.01467, 98.96548, highland = true, radiusKm = 18.1, m(9, -9, 68, 23, 6, 8)),
        Kabkota("1903", "Kab. Ende", -8.8432, 121.6623, highland = false, radiusKm = 26.8, m(9, -6, 71, 21, 11, 11)),
        Kabkota("0203", "Kab. Dairi", 2.74899, 98.3127, highland = true, radiusKm = 4.5, m(8, -9, 67, 23, 8, 9)),
        Kabkota("0708", "Kab. Rejang Lebong", -3.4703, 102.5207, highland = true, radiusKm = 2.3, m(6, -9, 68, 19, 8, 7)),
        Kabkota("2616", "Kab. Sinjai", -5.1241, 120.253, highland = false, radiusKm = 0.7, m(7, -8, 67, 21, 7, 7)),
        Kabkota("1909", "Kab. Manggarai Barat", -8.4964, 119.8877, highland = false, radiusKm = 29.5, m(9, -8, 69, 27, 8, 9)),
        Kabkota("1921", "Kab. Timor Tengah Utara", -9.44667, 124.47806, highland = false, radiusKm = 11.4, m(10, -12, 68, 23, 12, 12)),
        Kabkota("2105", "Kab. Hulu Sungai Tengah", -2.58333, 115.38333, highland = false, radiusKm = 8.2, m(9, -9, 69, 20, 8, 8)),
        Kabkota("2809", "Kab. Poso", -1.3959, 120.7524, highland = false, radiusKm = 14.6, m(8, -10, 67, 18, 7, 6)),
        Kabkota("2620", "Kab. Toraja Utara", -2.9701, 119.8978, highland = true, radiusKm = 5.5, m(8, -10, 69, 17, 8, 8)),
        Kabkota("1919", "Kab. Sumba Timur", -9.6567, 120.2641, highland = false, radiusKm = 29.5, m(12, -6, 70, 19, 9, 9)),
        Kabkota("0309", "Kab. Sijunjung", -0.7005, 100.9774, highland = false, radiusKm = 13.0, m(22, 0, 80, 34, 20, 20)),
        Kabkota("3315", "Kab. Merauke", -8.49958, 140.40613, highland = false, radiusKm = 29.5, m(9, -7, 69, 31, 10, 9)),
        Kabkota("2106", "Kab. Hulu Sungai Utara", -2.41773, 115.24941, highland = false, radiusKm = 6.6, m(7, -9, 67, 19, 7, 7)),
        Kabkota("2911", "Kab. Minahasa Utara", 1.42804, 124.97774, highland = false, radiusKm = 6.2, m(10, -8, 68, 20, 8, 8)),
        Kabkota("1902", "Kab. Belu", -9.10611, 124.8925, highland = false, radiusKm = 7.7, m(6, -9, 68, 16, 7, 6)),
        Kabkota("1802", "Kab. Dompu", -8.5365, 118.4634, highland = false, radiusKm = 3.7, m(9, -7, 69, 20, 10, 9)),
        Kabkota("0814", "Kota Lubuklinggau", -3.2945, 102.8614, highland = false, radiusKm = 4.3, m(8, -8, 67, 21, 9, 8)),
        Kabkota("1901", "Kab. Alor", -8.2151, 124.5458, highland = false, radiusKm = 29.5, m(14, -1, 75, 31, 14, 14)),
        Kabkota("3322", "Kab. Puncak Jaya", -3.71472, 137.98528, highland = true, radiusKm = 12.1, m(-230, -4, 73, 29, 14, 276), endOfEating = -215),
        Kabkota("0817", "Kota Prabumulih", -3.43447, 104.23056, highland = false, radiusKm = 7.2, m(7, -12, 65, 17, 5, 5)),
        Kabkota("0902", "Kab. Bangka Barat", -2.06719, 105.16228, highland = false, radiusKm = 28.6, m(4, -13, 64, 20, 3, 3)),
        Kabkota("3203", "Kab. Halmahera Utara", 1.72837, 128.00948, highland = false, radiusKm = 29.5, m(7, -8, 67, 18, 7, 8)),
        Kabkota("0101", "Kab. Aceh Barat", 4.14402, 96.12664, highland = false, radiusKm = 7.9, m(10, -9, 68, 23, 8, 8)),
        Kabkota("0120", "Kota Langsa", 4.4683, 97.9683, highland = false, radiusKm = 3.8, m(6, -10, 68, 21, 8, 7)),
        Kabkota("0233", "Kota Tebing Tinggi", 3.3285, 99.1625, highland = false, radiusKm = 1.1, m(9, -8, 70, 21, 9, 9)),
        Kabkota("2403", "Kab. Nunukan", 4.13841, 117.6507, highland = false, radiusKm = 3.3, m(9, -8, 69, 18, 9, 7)),
        Kabkota("3317", "Kab. Nabire", -3.35989, 135.50074, highland = false, radiusKm = 29.5, m(9, -8, 69, 21, 9, 11)),
        Kabkota("3307", "Kab. Jayapura", -2.5691, 140.5127, highland = false, radiusKm = 11.0, m(24, 3, 81, 35, 23, 21)),
        Kabkota("0905", "Kab. Belitung", -2.73353, 107.63477, highland = false, radiusKm = 20.4, m(7, -10, 68, 20, 7, 8)),
        Kabkota("3302", "Kab. Biak Numfor", -1.1767, 136.082, highland = false, radiusKm = 29.5, m(6, -10, 67, 17, 7, 7)),
        Kabkota("2203", "Kab. Barito Utara", -0.94734, 114.8962, highland = false, radiusKm = 22.9, m(8, -8, 69, 21, 10, 8)),
        Kabkota("0102", "Kab. Aceh Barat Daya", 3.73956, 96.83608, highland = false, radiusKm = 19.3, m(8, -8, 68, 23, 7, 9)),
        Kabkota("0219", "Kab. Samosir", 2.6081, 98.6961, highland = true, radiusKm = 14.4, m(8, -8, 69, 23, 8, 8)),
        Kabkota("2905", "Kab. Kepulauan Sangihe", 3.61099, 125.4858, highland = false, radiusKm = 29.5, m(6, -8, 67, 20, 8, 7)),
        Kabkota("0227", "Kota Gunungsitoli", 1.2888, 97.6143, highland = false, radiusKm = 3.9, m(9, -8, 68, 25, 10, 8)),
        Kabkota("0815", "Kota Pagar Alam", -4.02506, 103.24694, highland = true, radiusKm = 2.0, m(7, -8, 67, 19, 7, 8)),
        Kabkota("1808", "Kab. Sumbawa Barat", -8.7449, 116.8532, highland = false, radiusKm = 18.7, m(9, -9, 68, 20, 9, 9)),
        Kabkota("1906", "Kab. Lembata", -8.3723, 123.418, highland = true, radiusKm = 11.7, m(9, -8, 69, 17, 8, 7)),
        Kabkota("0106", "Kab. Aceh Singkil", 2.2874, 97.7884, highland = false, radiusKm = 15.5, m(9, -9, 67, 23, 9, 9)),
        Kabkota("2401", "Kab. Bulungan", 2.8375, 117.36528, highland = false, radiusKm = 29.5, m(9, -9, 67, 20, 7, 8)),
        Kabkota("0317", "Kota Payakumbuh", -0.2159, 100.6334, highland = false, radiusKm = 1.9, m(8, -8, 68, 20, 7, 7)),
        Kabkota("3205", "Kab. Kepulauan Sula", -2.05853, 125.97786, highland = false, radiusKm = 29.5, m(8, -9, 67, 16, 8, 8)),
        Kabkota("2913", "Kota Kotamobagu", 0.74194, 124.31208, highland = false, radiusKm = 0.8, m(7, -9, 65, 19, 8, 7)),
        Kabkota("3311", "Kab. Lanny Jaya", -3.92361, 138.45472, highland = true, radiusKm = 14.8, m(-237, -11, 66, 19, 6, 270), endOfEating = -220),
        Kabkota("0906", "Kab. Belitung Timur", -2.88333, 108.26667, highland = false, radiusKm = 29.5, m(2, -17, 61, 17, 2, 2)),
        Kabkota("0611", "Kota Sungai Penuh", -2.0561, 101.3913, highland = true, radiusKm = 2.5, m(7, -10, 68, 19, 8, 7)),
        Kabkota("3301", "Kab. Asmat", -5.54505, 138.13522, highland = false, radiusKm = 29.5, m(18, -1, 75, 32, 16, 15)),
        Kabkota("3103", "Kab. Kepulauan Aru", -5.76092, 134.23405, highland = false, radiusKm = 29.5, m(9, -8, 68, 25, 10, 9)),
        Kabkota("2915", "Kota Tomohon", 1.31678, 124.80379, highland = true, radiusKm = 3.0, m(0, -16, 59, 11, 0, -1)),
        Kabkota("0231", "Kota Sibolga", 1.74016, 98.78117, highland = false, radiusKm = 0.1, m(7, -8, 68, 24, 7, 10)),
        Kabkota("0316", "Kota Pariaman", -0.61898, 100.11997, highland = false, radiusKm = 4.0, m(8, -9, 68, 20, 9, 9)),
        Kabkota("3401", "Kab. Fakfak", -2.92481, 132.29813, highland = false, radiusKm = 29.5, m(8, -9, 67, 20, 7, 10)),
        Kabkota("3111", "Kota Tual", -5.62878, 132.75229, highland = false, radiusKm = 1.4, m(11, -6, 71, 21, 14, 13)),
        Kabkota("0123", "Kota Subulussalam", 2.662, 97.8827, highland = false, radiusKm = 6.4, m(-21, -37, 39, -8, -20, -20)),
        Kabkota("3411", "Kab. Teluk Bintuni", -2.10967, 133.52461, highland = false, radiusKm = 29.5, m(8, -9, 68, 17, 9, 8)),
        Kabkota("2402", "Kab. Malinau", 3.58855, 116.62391, highland = false, radiusKm = 7.9, m(12, -4, 73, 25, 11, 13)),
        Kabkota("3102", "Kab. Buru Selatan", -3.84548, 126.72619, highland = false, radiusKm = 25.8, m(-16, -41, 41, -8, -17, -16)),
        Kabkota("0319", "Kota Solok", -0.8006, 100.6571, highland = false, radiusKm = 0.1, m(7, -10, 67, 20, 6, 7)),
        Kabkota("3409", "Kab. Sorong Selatan", -1.4416, 132.01428, highland = false, radiusKm = 16.6, m(7, -8, 69, 17, 7, 9)),
        Kabkota("0315", "Kota Padangpanjang", -0.45602, 100.40521, highland = true, radiusKm = 0.1, m(9, -7, 69, 22, 10, 9)),
        Kabkota("0122", "Kota Sabang", 5.88969, 95.31644, highland = false, radiusKm = 28.3, m(5, -9, 65, 23, 6, 5)),
        Kabkota("3404", "Kab. Manokwari Selatan", -1.5083, 134.1708, highland = false, radiusKm = 12.1, m(2, -13, 63, 13, 3, 2)),
        Kabkota("1617", "Kab. Ngawi", -7.4038, 111.4461, highland = false, radiusKm = 2.8, m(10, -7, 69, 19, 9, 9)),
        Kabkota("1623", "Kab. Sampang", -7.1872, 113.2394, highland = false, radiusKm = 12.9, m(5, -11, 65, 16, 6, 5)),
        Kabkota("1627", "Kab. Trenggalek", -8.0764, 111.70641, highland = false, radiusKm = 6.8, m(10, -11, 67, 20, 7, 8)),
        Kabkota("1807", "Kab. Sumbawa", -8.49317, 117.42024, highland = false, radiusKm = 29.5, m(6, -10, 67, 20, 7, 7)),
        Kabkota("1908", "Kab. Manggarai", -8.61139, 120.46444, highland = true, radiusKm = 4.3, m(9, -9, 69, 19, 10, 10)),
        Kabkota("1920", "Kab. Timor Tengah Selatan", -9.86071, 124.28395, highland = true, radiusKm = 20.5, m(10, -4, 71, 29, 12, 12)),
        Kabkota("2603", "Kab. Bone", -4.5386, 120.3279, highland = false, radiusKm = 29.5, m(7, -9, 67, 18, 8, 6)),
        Kabkota("2713", "Kab. Muna", -4.838, 122.7209, highland = false, radiusKm = 9.4, m(5, -10, 66, 16, 7, 5)),
        Kabkota("2801", "Kab. Banggai", -0.9516, 122.7875, highland = false, radiusKm = 28.2, m(8, -9, 66, 19, 6, 6)),
        Kabkota("2806", "Kab. Morowali", -2.5471, 121.972, highland = false, radiusKm = 22.7, m(8, -9, 68, 21, 7, 9)),
        Kabkota("2908", "Kab. Minahasa", 1.3054, 124.91261, highland = true, radiusKm = 3.3, m(7, -9, 68, 19, 8, 8)),
        Kabkota("3101", "Kab. Buru", -3.26194, 127.09292, highland = false, radiusKm = 24.1, m(4, -10, 65, 16, 6, 6)),
        Kabkota("3403", "Kab. Manokwari", -0.86291, 134.06402, highland = false, radiusKm = 23.6, m(5, -11, 66, 14, 5, 6)),
        Kabkota("0229", "Kota Padangsidempuan", 1.37952, 99.27146, highland = false, radiusKm = 2.5, m(9, -8, 68, 21, 7, 8)),
        Kabkota("0602", "Kab. Bungo", -1.49018, 102.1149, highland = false, radiusKm = 5.4, m(9, -8, 67, 20, 8, 8)),
        Kabkota("0810", "Kab. Ogan Komering Ulu", -4.12891, 104.16695, highland = false, radiusKm = 14.4, m(7, -9, 67, 18, 7, 6)),
        Kabkota("0113", "Kab. Bireuen", 5.203, 96.7009, highland = false, radiusKm = 18.8, m(7, -9, 68, 22, 9, 9)),
        Kabkota("2612", "Kab. Maros", -5.006, 119.5727, highland = false, radiusKm = 7.6, m(8, -9, 67, 18, 7, 9)),
        Kabkota("2207", "Kab. Kotawaringin Barat", -2.6832, 111.6259, highland = false, radiusKm = 24.8, m(5, -11, 66, 19, 4, 4)),
        Kabkota("0502", "Kab. Karimun", 1.00005, 103.42186, highland = false, radiusKm = 22.1, m(17, -2, 77, 30, 15, 15)),
        Kabkota("2108", "Kab. Tabalong", -2.1709, 115.4077, highland = false, radiusKm = 9.8, m(0, -17, 61, 11, 0, 2)),
        Kabkota("2812", "Kab. Toli-Toli", 1.04018, 120.81764, highland = false, radiusKm = 21.4, m(7, -9, 67, 17, 9, 6)),
        Kabkota("3001", "Kab. Majene", -3.5403, 118.9707, highland = false, radiusKm = 3.0, m(10, -6, 71, 21, 12, 13)),
        Kabkota("3207", "Kab. Pulau Morotai", 2.05049, 128.29398, highland = false, radiusKm = 26.0, m(-8, -24, 50, 0, -7, -8)),
    )

    /**
     * The round2 units whose end of eating may run 3 min before Kemenag's Subuh toward the edge of their circles
     * (Kemenag.kt's lateLimitsOf): each one's reach rows of 12 Nov 2026 found it so, and no other event over class B's
     * 2 min. Approved by the owner on 12 Nov 2026, with Kab. Sumenep's the 30 (ruling R186).
     */
    val round2EarlierEnd: Set<String> = setOf(
        "0113", // Kab. Bireuen
        "0122", // Kota Sabang
        "0502", // Kab. Karimun
        "0902", // Kab. Bangka Barat
        "0906", // Kab. Belitung Timur
        "1807", // Kab. Sumbawa
        "1901", // Kab. Alor
        "1903", // Kab. Ende
        "1909", // Kab. Manggarai Barat
        "1919", // Kab. Sumba Timur
        "2012", // Kab. Sintang
        "2203", // Kab. Barito Utara
        "2401", // Kab. Bulungan
        "2603", // Kab. Bone
        "2611", // Kab. Luwu Utara
        "2801", // Kab. Banggai
        "2812", // Kab. Toli-Toli
        "2905", // Kab. Kepulauan Sangihe
        "3101", // Kab. Buru
        "3103", // Kab. Kepulauan Aru
        "3203", // Kab. Halmahera Utara
        "3205", // Kab. Kepulauan Sula
        "3301", // Kab. Asmat
        "3302", // Kab. Biak Numfor
        "3315", // Kab. Merauke
        "3317", // Kab. Nabire
        "3401", // Kab. Fakfak
        "3403", // Kab. Manokwari
        "3411", // Kab. Teluk Bintuni
    )
}
