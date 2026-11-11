package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchLinkContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The name the phone gives each feed (the Lead's call 1): FNV-1a over the feed's own encoding with
 * `writtenAt` at 0, so a feed written again unchanged keeps its name and any change gives another.
 */
class WatchFeedFingerprintTest {

    @Test
    fun theHashIsFnv1a64() {
        // The published 64-bit FNV-1a vectors.
        assertEquals("cbf29ce484222325", fnv1a64Hex(ByteArray(0)))
        assertEquals("af63dc4c8601ec8c", fnv1a64Hex("a".encodeToByteArray()))
        assertEquals("85944171f73967e8", fnv1a64Hex("foobar".encodeToByteArray()))
    }

    @Test
    fun theFingerprintIgnoresWrittenAt() {
        assertEquals(
            watchFeedFingerprint(syncFeed(writtenAt = 1_792_303_200L)),
            watchFeedFingerprint(syncFeed(writtenAt = 1_792_306_800L)),
        )
    }

    @Test
    fun aChangedFeedHasAnotherFingerprint() {
        val feed = syncFeed()
        assertNotEquals(watchFeedFingerprint(feed), watchFeedFingerprint(syncFeed(phrases = listOf(syncPhrase(1)))))
        assertNotEquals(watchFeedFingerprint(feed), watchFeedFingerprint(syncFeed(fajrShiftMinutes = 1)))
    }

    @Test
    fun itIsTheFormTheContractExpects() {
        val feeds = listOf(
            syncFeed(),
            syncFeed(writtenAt = 0L),
            syncFeed(fajrShiftMinutes = 1),
            syncFeed(fajrShiftMinutes = -1),
            syncFeed(phrases = listOf(syncPhrase(1))),
            syncFeed(phrases = (1..100).map(::syncPhrase)),
        )
        for (feed in feeds) {
            val fingerprint = watchFeedFingerprint(feed)
            assertTrue(WatchLinkContract.isFingerprint(fingerprint), fingerprint)
        }
        // A hash whose top bits are zero is still 16 characters: the padding is part of the form.
        assertEquals("00f5898e9456454c", fnv1a64Hex("13900".encodeToByteArray()))
    }
}
