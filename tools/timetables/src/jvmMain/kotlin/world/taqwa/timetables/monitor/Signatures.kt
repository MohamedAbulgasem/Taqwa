package world.taqwa.timetables.monitor

import java.io.File
import java.security.MessageDigest

/**
 * What a table's check depends on, hashed so that a table is checked again exactly when any of it
 * changed (reviews I8, N9): the table's content and metadata, the whole engine
 * ([world.taqwa.timetables.gate.Stamps.wholeEngineHash]), the checker's own code
 * ([checkerHash]) and, for a mosque calendar, the survey rows that cover it ([surveyRows]).
 */
internal object Signatures {

    /** The checker's own sources (the gate and monitor Kotlin, the fetch Python), hashed with their paths. */
    fun checkerHash(repo: File): String {
        val roots = listOf(
            repo.resolve("tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate") to "kt",
            repo.resolve("tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/monitor") to "kt",
            repo.resolve("tools/timetables/monitor") to "py",
        )
        val files = roots.flatMap { (dir, ext) -> dir.walkTopDown().filter { it.isFile && it.extension == ext }.toList() }
        val digest = MessageDigest.getInstance("SHA-256")
        for (file in files.sortedBy { it.relativeTo(repo).invariantSeparatorsPath }) {
            digest.update(file.relativeTo(repo).invariantSeparatorsPath.toByteArray())
            digest.update(0)
            digest.update(file.readBytes())
            digest.update(0)
        }
        return hex(digest.digest()).take(16)
    }

    /**
     * The faults and outliers [table]'s survey records for it (its own rows and the `*` rows), as
     * text; "" for a table that goes through no survey. [cache] keeps each folder's rows for a run.
     */
    fun surveyRows(officialDir: File, table: MonitorTable, cache: MutableMap<String, List<String>> = HashMap()): String {
        val folder = table.survey ?: return ""
        val lines = cache.getOrPut(folder) {
            listOf("faults.tsv", "outliers.tsv").flatMap { name ->
                val file = officialDir.resolve("survey/$folder/$name")
                if (file.isFile) file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { "$name\t$it" } else emptyList()
            }
        }
        return lines.filter { line -> line.split('\t').getOrNull(1).let { it == table.key || it == "*" } }.joinToString("\n")
    }

    /** The signature itself: 24 hex characters over the four parts. */
    fun of(table: MonitorTable, engineHash: String, checkerHash: String, surveyRows: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in listOf(table.metadata, engineHash, checkerHash, surveyRows)) {
            digest.update(part.toByteArray())
            digest.update(0)
        }
        return hex(digest.digest()).take(24)
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
