package world.taqwa.app.watchface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * "Add to Apple Watch" is the iPhone's alone (plan 4 Task 10): an Android phone never offers an
 * Apple face, so Settings never shows the row there, and an add that somehow came is a failure,
 * never a silence. Plan 5's Android rows live beside it in `WatchRows.kt`, through their own source.
 */
class AndroidWatchFaceOfferTest {

    @Test
    fun anAndroidPhoneOffersNoAppleFace() {
        val offer = createWatchFaceOffer()
        assertFalse(offer.available())
        val answers = mutableListOf<WatchFaceResult>()
        offer.add { answers += it }
        assertEquals(listOf(WatchFaceResult.FAILED), answers)
    }
}
