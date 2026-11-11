package world.taqwa.app.wear.face

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled face on disk (plan 5 Task 5 Step 4, P5.5 review round 1): copied out of the app's APK
 * and read once for each install of the app, not at every process start, which on a watch is every
 * complication, Tile, Data Layer or nudge wake. A copy the system cleared from the cache is copied
 * again when an add or an update needs it, without reading it again.
 *
 * The fake APK's bytes are "face <versionCode>"; [copies] counts the asset's copies and [reads] the
 * archive reads (PackageManager.getPackageArchiveInfo on the watch). Each [stage] is a process.
 */
class FaceStageTest {

    private val root: File = Files.createTempDirectory("face-stage").toFile().apply { deleteOnExit() }
    private val cache = File(root, "cache").apply { mkdirs() }
    private val record = File(File(root, "no_backup").apply { mkdirs() }, "face-bundled")

    /** The face the build bundled, or null for a build that carries none. */
    private var asset: Long? = 40
    private var copies = 0
    private var reads = 0
    private var cutShort = false

    private fun stage() = FaceStage(
        cache = cache,
        record = record,
        copy = { file ->
            copies++
            val code = asset ?: throw FileNotFoundException("default_watchface.apk")
            if (cutShort) {
                file.writeText("fa")
                throw IOException("no space left on device")
            }
            file.writeText("face $code")
        },
        versionOf = { file -> reads++; file.readText().removePrefix("face ").toLongOrNull() },
    )

    private fun faces(): List<String> = cache.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun theFaceIsCopiedAndReadOnceAnInstallNotAtEveryStart() {
        repeat(3) { start ->
            val face = assertNotNull(stage().load("token", INSTALL_40), "start $start")
            assertEquals(40, face.versionCode)
            assertEquals("token", face.token)
        }
        assertEquals(1, copies, "one copy for the install")
        assertEquals(1, reads, "one read for the install")
        assertEquals(listOf("face-40.apk"), faces())
    }

    @Test
    fun onlyTheFirstStartAfterAnUpdateCopiesAndReadsTheNewFace() {
        File(cache, "face-staged.apk").writeText("face 39") // the earlier build's staging file
        assertEquals(40, stage().load("token", INSTALL_40)?.versionCode)

        asset = 41
        assertEquals(41, stage().load("token", INSTALL_41)?.versionCode)
        assertEquals(41, stage().load("token", INSTALL_41)?.versionCode)

        assertEquals(2, copies)
        assertEquals(2, reads)
        assertEquals(listOf("face-41.apk"), faces(), "the earlier copies are not the bundled face")
    }

    @Test
    fun aStartReadsNoFileOfTheFaceAndOnlyTheApkAsksForIt() {
        stage().load("token", INSTALL_40)
        copies = 0
        reads = 0

        val face = assertNotNull(stage().load("token", INSTALL_40))
        assertEquals(0, copies)
        assertEquals(0, reads)
        assertEquals("face 40", face.apk().readText())
        assertEquals(0, copies, "the copy on disk is handed over as it is")
    }

    @Test
    fun aCopyTheSystemClearedIsCopiedAgainWhenAnAddOrUpdateNeedsIt() {
        val face = assertNotNull(stage().load("token", INSTALL_40))
        cache.deleteRecursively() // the system clears the cache while the process runs

        val apk = face.apk()
        assertEquals(File(cache, "face-40.apk"), apk)
        assertEquals("face 40", apk.readText())
        assertEquals(2, copies)
        assertEquals(1, reads, "copied again, not read again")

        // A later process after another clear: no copy and no read at its start, one copy at use.
        cache.deleteRecursively()
        val later = assertNotNull(stage().load("token", INSTALL_40))
        assertEquals(2, copies)
        assertEquals("face 40", later.apk().readText())
        assertEquals(3, copies)
        assertEquals(1, reads)
    }

    @Test
    fun withoutATokenNothingIsCopied() {
        assertNull(stage().load("", INSTALL_40))
        assertEquals(0, copies)
        assertEquals(emptyList(), faces())
    }

    @Test
    fun aBuildWithoutItsFaceIsNotBundled() {
        asset = null
        assertNull(stage().load("token", INSTALL_40))
        assertEquals(emptyList(), faces())
        assertFalse(record.exists(), "nothing recorded, so a later start tries again")
    }

    @Test
    fun anApkThatCannotBeReadIsNotBundledAndLeavesNothing() {
        val unreadable = FaceStage(cache, record, copy = { it.writeText("not an apk") }, versionOf = { null })
        assertNull(unreadable.load("token", INSTALL_40))
        assertEquals(emptyList(), faces())
        assertFalse(record.exists())
    }

    @Test
    fun aCopyCutShortLeavesNoHalfFaceAndTheNextStartCopiesAgain() {
        cutShort = true
        assertNull(stage().load("token", INSTALL_40))
        assertEquals(emptyList(), faces(), "no half copy under the face's name")
        assertFalse(record.exists())

        cutShort = false
        assertEquals(40, stage().load("token", INSTALL_40)?.versionCode)
        assertEquals("face 40", File(cache, "face-40.apk").readText())
    }

    @Test
    fun aCopyThatCannotBeMadeAgainThrowsForFacePushToFail() {
        val face = assertNotNull(stage().load("token", INSTALL_40))
        cache.deleteRecursively()
        cutShort = true

        assertFailsWith<IOException> { face.apk() }
        assertEquals(emptyList(), faces())
    }

    @Test
    fun aRecordOfAnotherInstallOrUnreadableIsReadAfresh() {
        record.writeText("$INSTALL_41 41\n")
        assertEquals(40, stage().load("token", INSTALL_40)?.versionCode)
        record.writeText("garbage")
        assertEquals(40, stage().load("token", INSTALL_40)?.versionCode)
        assertEquals(2, reads)
        assertTrue(record.readText().startsWith(INSTALL_40))
    }

    private companion object {
        /** An install of the app: its version code and its last update time. */
        const val INSTALL_40 = "100040:1762380000000"
        const val INSTALL_41 = "100041:1762390000000"
    }
}
