package world.taqwa.app.prayer.engine.registry.data

import world.taqwa.app.prayer.engine.method.GeoPoint
import world.taqwa.app.prayer.engine.registry.regionKey

/**
 * The seats of Algeria's 58 wilayas, where each wilaya's times are computed (spec §10.4): derived
 * facts, not official times. Points are the app's cities.csv (GeoNames) where the seat is listed;
 * the nine seats it lacks (Boumerdès, Tipaza, El Tarf, Naâma, Illizi, Djanet, Béni Abbès, Bordj
 * Badji Mokhtar, In Guezzam) are town-centre coordinates to about 0.05°, flagged [approximate].
 * [code] is the wilaya's official number.
 */
object AlgeriaWilayas {
    class Seat(val code: Int, val name: String, val point: GeoPoint, val approximate: Boolean = false)

    /**
     * The seat of the wilaya named [admin1] (the app's city list's region, GeoNames' spelling), or
     * null for a name that is none of the 58. Names match whatever their accents and spacing.
     */
    fun seatFor(admin1: String): Seat? = byKey[regionKey(admin1)]

    private fun s(code: Int, name: String, lat: Double, lon: Double, approximate: Boolean = false) =
        Seat(code, name, GeoPoint(lat, lon), approximate)

    val seats: List<Seat> = listOf(
        s(1, "Adrar", 27.87429, -0.29388),
        s(2, "Chlef", 36.16525, 1.33452),
        s(3, "Laghouat", 33.8, 2.86514),
        s(4, "Oum El Bouaghi", 35.87541, 7.11353),
        s(5, "Batna", 35.55597, 6.17414),
        s(6, "Béjaïa", 36.75587, 5.08433),
        s(7, "Biskra", 34.85038, 5.72805),
        s(8, "Béchar", 31.61667, -2.21667),
        s(9, "Blida", 36.47004, 2.8277),
        s(10, "Bouira", 36.37489, 3.902),
        s(11, "Tamanrasset", 22.785, 5.52278),
        s(12, "Tébessa", 35.40417, 8.12417),
        s(13, "Tlemcen", 34.87833, -1.315),
        s(14, "Tiaret", 35.37103, 1.31699),
        s(15, "Tizi Ouzou", 36.71182, 4.04591),
        s(16, "Algiers", 36.73225, 3.08746),
        s(17, "Djelfa", 34.67279, 3.263),
        s(18, "Jijel", 36.821, 5.76352),
        s(19, "Sétif", 36.19112, 5.41373),
        s(20, "Saïda", 34.83033, 0.15171),
        s(21, "Skikda", 36.87617, 6.90921),
        s(22, "Sidi Bel Abbès", 35.18994, -0.63085),
        s(23, "Annaba", 36.9, 7.76667),
        s(24, "Guelma", 36.46214, 7.42608),
        s(25, "Constantine", 36.365, 6.61472),
        s(26, "Médéa", 36.26417, 2.75393),
        s(27, "Mostaganem", 35.93115, 0.08918),
        s(28, "M'Sila", 35.70889, 4.53722),
        s(29, "Mascara", 35.39664, 0.14027),
        s(30, "Ouargla", 31.94932, 5.32502),
        s(31, "Oran", 35.69906, -0.63588),
        s(32, "El Bayadh", 33.68318, 1.01927),
        s(33, "Illizi", 26.4833, 8.4667, approximate = true),
        s(34, "Bordj Bou Arréridj", 36.07389, 4.76139),
        s(35, "Boumerdès", 36.7664, 3.4772, approximate = true),
        s(36, "El Tarf", 36.7672, 8.3138, approximate = true),
        s(37, "Tindouf", 27.67111, -8.14743),
        s(38, "Tissemsilt", 35.60722, 1.81081),
        s(39, "El Oued", 33.35608, 6.86319),
        s(40, "Khenchela", 35.43583, 7.14333),
        s(41, "Souk Ahras", 36.28639, 7.95111),
        s(42, "Tipaza", 36.5897, 2.4475, approximate = true),
        s(43, "Mila", 36.45028, 6.26444),
        s(44, "Aïn Defla", 36.26405, 1.9679),
        s(45, "Naâma", 33.2667, -0.3167, approximate = true),
        s(46, "Aïn Témouchent", 35.29749, -1.14037),
        s(47, "Ghardaïa", 32.49094, 3.67347),
        s(48, "Relizane", 35.73734, 0.55599),
        s(49, "Timimoun", 29.26417, 0.23583),
        s(50, "Bordj Badji Mokhtar", 21.3281, 0.9489, approximate = true),
        s(51, "Ouled Djellal", 34.43, 5.06139),
        s(52, "Béni Abbès", 30.1333, -2.1667, approximate = true),
        s(53, "In Salah", 27.19678, 2.47913),
        s(54, "In Guezzam", 19.5686, 5.7722, approximate = true),
        s(55, "Touggourt", 33.11083, 6.07),
        s(56, "Djanet", 24.5547, 9.4847, approximate = true),
        s(57, "El M'Ghair", 33.95139, 5.92222),
        s(58, "El Meniaa", 30.57556, 2.88417),
    )

    /** Other spellings of a wilaya's name that the region names use. */
    private val otherNames = mapOf("El Menia" to 58, "Tamanghasset" to 11, "Alger" to 16)

    private val byKey: Map<String, Seat> by lazy {
        seats.associateBy { regionKey(it.name) } + otherNames.map { (name, code) -> regionKey(name) to seats.first { it.code == code } }
    }
}
