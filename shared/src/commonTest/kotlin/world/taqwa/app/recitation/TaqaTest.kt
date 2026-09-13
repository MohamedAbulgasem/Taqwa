package world.taqwa.app.recitation

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Builds a `.taqa` exactly as the pipeline does (spec 3a §4), so the reader is tested against the
 * layout and not against itself. Deliberately hand-rolled — the index JSON is written as text
 * rather than serialised — because a builder that shared code with the parser would agree with it
 * even when both were wrong.
 */
internal fun buildTaqa(
    reciter: String = "ar.alafasy",
    surah: Int = 1,
    kbps: Int = 64,
    ayahs: List<ByteArray>,
    version: Int = 1,
    magic: String = "TAQA",
    /** Ayah number to its own bit-rate, for the one entry the pipeline writes one on. */
    kbpsOverrides: Map<Int, Int> = emptyMap(),
): ByteArray {
    val entries = StringBuilder()
    var off = 0L
    ayahs.forEachIndexed { i, bytes ->
        if (i > 0) entries.append(",")
        val own = kbpsOverrides[i + 1]?.let { ",\"kbps\":$it" }.orEmpty()
        entries.append("{\"n\":${i + 1},\"off\":$off,\"len\":${bytes.size}$own}")
        off += bytes.size
    }
    val index = "{\"reciter\":\"$reciter\",\"surah\":$surah,\"kbps\":$kbps,\"ayahs\":[$entries]}"
        .encodeToByteArray()
    val out = ArrayList<Byte>()
    out += magic.encodeToByteArray().toList()
    out += version.toByte()
    out += listOf<Byte>(0, 0, 0)
    out += byteArrayOf(
        (index.size ushr 24).toByte(),
        (index.size ushr 16).toByte(),
        (index.size ushr 8).toByte(),
        index.size.toByte(),
    ).toList()
    out += index.toList()
    ayahs.forEach { out += it.toList() }
    return out.toByteArray()
}

private fun ayah(size: Int, fill: Byte) = ByteArray(size) { fill }

class TaqaIndexTest {

    private val ayahs = listOf(ayah(64, 1), ayah(96, 2), ayah(32, 3))

    @Test
    fun parsesTheHeaderAndPlacesTheAudioAfterIt() {
        val file = buildTaqa(surah = 112, ayahs = ayahs)
        val index = TaqaIndex.parse(file)
        assertEquals("ar.alafasy", index.reciter)
        assertEquals(112, index.surah)
        assertEquals(64, index.kbps)
        assertEquals(listOf(1, 2, 3), index.ayahs.map { it.n })
        assertEquals(TaqaAyah(2, 64, 96), index.ayah(2))
        // Every offset in the index is relative; dataStart is what makes them file offsets.
        assertEquals(index.dataStart, index.rangeOf(1).first)
        assertEquals(index.dataStart + 64, index.rangeOf(2).first)
        assertEquals(index.dataStart + 64 + 95, index.rangeOf(2).last)
        assertEquals(file.size.toLong(), index.expectedFileBytes)
    }

    @Test
    fun theFixedHeaderAloneSaysHowMuchMoreToRead() {
        val file = buildTaqa(ayahs = ayahs)
        val length = TaqaIndex.indexLength(file.copyOfRange(0, TaqaIndex.FIXED_HEADER))
        // Exactly the header plus the index is enough to parse; the audio is never needed.
        val header = file.copyOfRange(0, TaqaIndex.FIXED_HEADER + length)
        assertEquals(3, TaqaIndex.parse(header).ayahs.size)
    }

    @Test
    fun rejectsAFileThatIsNotATaqa() {
        val wrong = buildTaqa(ayahs = ayahs, magic = "ZIP!")
        assertFailsWith<MalformedTaqa> { TaqaIndex.parse(wrong) }
    }

    @Test
    fun rejectsAVersionItDoesNotKnow() {
        val newer = buildTaqa(ayahs = ayahs, version = 2)
        assertFailsWith<MalformedTaqa> { TaqaIndex.parse(newer) }
    }

    @Test
    fun rejectsATruncatedIndex() {
        val file = buildTaqa(ayahs = ayahs)
        assertFailsWith<MalformedTaqa> { TaqaIndex.parse(file.copyOfRange(0, 8)) }
        assertFailsWith<MalformedTaqa> { TaqaIndex.parse(file.copyOfRange(0, TaqaIndex.FIXED_HEADER)) }
        assertFailsWith<MalformedTaqa> { TaqaIndex.parse(file.copyOfRange(0, TaqaIndex.FIXED_HEADER + 4)) }
    }

    @Test
    fun rejectsAnIndexThatIsNotTheJsonThisVersionWrites() {
        val file = buildTaqa(ayahs = ayahs)
        // Corrupt one byte inside the index rather than the header.
        file[TaqaIndex.FIXED_HEADER + 2] = '#'.code.toByte()
        assertFailsWith<MalformedTaqa> { TaqaIndex.parse(file) }
    }

    @Test
    fun anAyahThatIsNotInTheIndexIsAnError() {
        val index = TaqaIndex.parse(buildTaqa(ayahs = ayahs))
        assertFailsWith<MalformedTaqa> { index.rangeOf(9) }
    }
}

class TaqaFileTest {

    private val fs = FakeFileSystem()
    private val path = "/audio/ar.alafasy/112.taqa".toPath()
    private val ayahs = listOf(ayah(64, 1), ayah(96, 2), ayah(32, 3))
    private val bytes = buildTaqa(surah = 112, ayahs = ayahs)

    private fun writeFile(content: ByteArray = bytes): TaqaFile {
        fs.createDirectories(path.parent!!)
        fs.write(path) { write(content) }
        return TaqaFile(path, fs)
    }

    @Test
    fun readsTheIndexOffDisk() {
        val file = writeFile()
        assertEquals(112, file.index().surah)
        assertEquals(3, file.index().ayahs.size)
        fs.checkNoOpenFiles()
    }

    @Test
    fun rangesAreAbsoluteFileOffsets() {
        val file = writeFile()
        val start = file.index().dataStart
        assertEquals(start until start + 64, file.ayahRange(1))
        assertEquals(start + 64 until start + 160, file.ayahRange(2))
        assertEquals(start + 160 until start + 192, file.ayahRange(3))
        assertEquals(bytes.size.toLong(), file.ayahRange(3).last + 1)
    }

    @Test
    fun readsOneAyahsBytesUntouched() {
        val file = writeFile()
        assertTrue(file.readAyah(1).all { it == 1.toByte() })
        assertEquals(96, file.readAyah(2).size)
        assertTrue(file.readAyah(2).all { it == 2.toByte() })
        assertEquals(32, file.readAyah(3).size)
        fs.checkNoOpenFiles()
    }

    @Test
    fun sha256IsTheWholeFileInLowerCaseHex() {
        val file = writeFile()
        val hash = file.sha256()
        assertEquals(64, hash.length)
        assertEquals(hash.lowercase(), hash)
        // The same content hashes the same wherever it sits.
        val other = "/audio/ar.husary/112.taqa".toPath()
        fs.createDirectories(other.parent!!)
        fs.write(other) { write(bytes) }
        assertEquals(hash, TaqaFile(other, fs).sha256())
        // A single changed byte does not.
        val changed = bytes.copyOf().also { it[it.size - 1] = 9 }
        val third = "/audio/ar.husary/113.taqa".toPath()
        fs.write(third) { write(changed) }
        assertTrue(hash != TaqaFile(third, fs).sha256())
        fs.checkNoOpenFiles()
    }

    @Test
    fun isCompleteComparesWithWhatTheManifestPublishes() {
        val file = writeFile()
        assertTrue(file.isComplete(bytes.size.toLong()))
        assertFalse(file.isComplete(bytes.size.toLong() + 1))
        assertFalse(TaqaFile("/audio/ar.alafasy/113.taqa".toPath(), fs).isComplete(1))
    }

    @Test
    fun isWholeRejectsHalfAFileAndAcceptsAFinishedOne() {
        assertTrue(writeFile().isWhole())
        val half = "/audio/ar.alafasy/002.taqa".toPath()
        fs.createDirectories(half.parent!!)
        fs.write(half) { write(bytes.copyOfRange(0, bytes.size - 10)) }
        assertFalse(TaqaFile(half, fs).isWhole())
        // Not a container at all.
        val junk = "/audio/ar.alafasy/003.taqa".toPath()
        fs.write(junk) { write("not audio".encodeToByteArray()) }
        assertFalse(TaqaFile(junk, fs).isWhole())
        // Not there at all.
        assertFalse(TaqaFile("/audio/ar.alafasy/004.taqa".toPath(), fs).isWhole())
        fs.checkNoOpenFiles()
    }
}

/** The length estimate read off a container (spec §14.1), and the ID3 tag it must not count. */
class TaqaDurationsTest {

    private fun id3(size: Int, footer: Boolean = false): ByteArray {
        val flags = if (footer) 0x10 else 0
        val head = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, flags.toByte(),
            ((size ushr 21) and 0x7F).toByte(),
            ((size ushr 14) and 0x7F).toByte(),
            ((size ushr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte(),
        )
        return head + ByteArray(size) { 9 } + (if (footer) ByteArray(10) else ByteArray(0))
    }

    @Test
    fun anId3TagIsItsHeaderPlusItsSyncsafeSizePlusAnyFooter() {
        assertEquals(407L, id3v2TagBytes(id3(397)))
        assertEquals(10L + 397L + 10L, id3v2TagBytes(id3(397, footer = true)))
        // 128 is 0x80: a syncsafe field carries seven bits a byte, so it is written 01 00.
        assertEquals(10L + 128L, id3v2TagBytes(id3(128)))
        assertEquals(0L, id3v2TagBytes(ByteArray(10) { 0xFF.toByte() }))
        assertEquals(0L, id3v2TagBytes(ByteArray(4)))
    }

    @Test
    fun everyAyahIsTimedFromItsAudioBytesWithTheTagLeftOut() {
        val fs = FakeFileSystem()
        val path = "/quran/ar.alafasy/112.taqa".toPath()
        fs.createDirectories(path.parent!!)
        // 64 kbps: 8 bytes a millisecond. 800 bytes of audio is 100 ms, 407 of tag is nothing.
        val ayahs = listOf(
            id3(397) + ByteArray(800) { 1 },
            ByteArray(1_600) { 2 },
            id3(0) + ByteArray(80) { 3 },
        )
        fs.write(path) { write(buildTaqa(surah = 112, kbps = 64, ayahs = ayahs)) }

        val durations = TaqaFile(path, fs).ayahDurationsMs()

        assertEquals(mapOf(1 to 100L, 2 to 200L, 3 to 10L), durations)
    }

    @Test
    fun anAyahAtTheOtherPublishedBitRateIsTimedAtItsOwn() {
        val fs = FakeFileSystem()
        val path = "/quran/ar.ahmedajamy/9.taqa".toPath()
        fs.createDirectories(path.parent!!)
        // 1,600 bytes is 100 ms at 128 kbps and 200 ms at 64; the second ayah says it is 64.
        val ayahs = listOf(ByteArray(1_600) { 1 }, ByteArray(1_600) { 2 })
        fs.write(path) { write(buildTaqa(surah = 9, kbps = 128, ayahs = ayahs, kbpsOverrides = mapOf(2 to 64))) }

        val taqa = TaqaFile(path, fs)

        assertEquals(64, taqa.index().ayah(2)?.kbps)
        assertEquals(mapOf(1 to 100L, 2 to 200L), taqa.ayahDurationsMs())
    }

    @Test
    fun anAyahShorterThanATagHeaderIsStillTimed() {
        val fs = FakeFileSystem()
        val path = "/quran/ar.alafasy/1.taqa".toPath()
        fs.createDirectories(path.parent!!)
        fs.write(path) { write(buildTaqa(kbps = 64, ayahs = listOf(ByteArray(4) { 1 }))) }

        assertEquals(mapOf(1 to 0L), TaqaFile(path, fs).ayahDurationsMs())
    }
}
