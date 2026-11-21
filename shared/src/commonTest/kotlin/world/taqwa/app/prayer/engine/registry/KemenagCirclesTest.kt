package world.taqwa.app.prayer.engine.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Kemenag's kab/kota circles against each other (ruling R212, the audit of 12 Nov 2026, finding F11). A user follows
 * their own kab/kota's table, and where two circles hold the same ground the nearer point's table is shown: so no two
 * circles may overlap, unless the pair is listed here with what shows that the shared ground is neither's land.
 */
class KemenagCirclesTest {

    /**
     * Two circles that share open sea. [nearestOtherKm]: from each unit's point, the distance to the nearest boundary
     * of another kab/kota Kemenag prints for, as the round read it on OpenStreetMap (R103; [QUERY_KM] where none lay
     * within the boundary query's reach). While each radius stays the buffer short of that, neither circle holds any
     * land of the other's, so the ground they share is no kab/kota's.
     */
    private class SharedSea(val a: String, val b: String, val nearestOtherKm: Map<String, Double>, val why: String)

    private val sharedSea = listOf(
        SharedSea(
            "3203", "3207", mapOf("3203" to QUERY_KM, "3207" to 28.868),
            "Kab. Halmahera Utara's (Tobelo) and Kab. Pulau Morotai's (Daruba) meet over the strait between Halmahera and " +
                "Morotai: no other kab/kota lies within 30 km of Tobelo, and Halmahera Utara's nearest boundary is 28.868 km " +
                "from Daruba, beyond Morotai's 26.0 km (7c-sea.md)",
        ),
    )

    @Test
    fun `no two circles overlap but over open sea that is neither's land`() {
        for (entryId in listOf("id.kemenag", "id.muhammadiyah")) {
            val units = assertNotNull(Units.of(entryId)).units
            assertTrue(units.size > 200, "$entryId: ${units.size} units")
            val overlapping = mutableSetOf<Pair<String, String>>()
            for (i in units.indices) {
                for (j in i + 1 until units.size) {
                    val a = units[i]
                    val b = units[j]
                    val km = distanceKm(a.point, b.point)
                    if (km > a.radiusKm + b.radiusKm) continue
                    val listed = sharedSea.firstOrNull { setOf(it.a, it.b) == setOf(a.id, b.id) }
                    assertNotNull(
                        listed,
                        "$entryId: ${a.name}'s ${a.radiusKm} km circle and ${b.name}'s ${b.radiusKm} km circle overlap ($km km apart): " +
                            "a place in both follows the nearer point's table, which may not be its own",
                    )
                    overlapping += listed.a to listed.b
                }
            }
            // The list holds nothing that no longer applies, and each pair's reason still stands.
            assertEquals(sharedSea.map { it.a to it.b }.toSet(), overlapping, entryId)
            for (pair in sharedSea) {
                for (id in listOf(pair.a, pair.b)) {
                    val unit = units.single { it.id == id }
                    val nearest = pair.nearestOtherKm.getValue(id)
                    assertTrue(
                        unit.radiusKm <= nearest - BUFFER_KM + 1e-9,
                        "${unit.name}: ${unit.radiusKm} km reaches past the nearest other kab/kota's boundary, $nearest km less the buffer (${pair.why})",
                    )
                }
            }
        }
    }

    private companion object {
        /** The boundary query's reach around a point (R103's rounds): none nearer was found within it. */
        const val QUERY_KM = 30.0

        /** R103's buffer for the boundaries' accuracy. */
        const val BUFFER_KM = 0.5
    }
}
