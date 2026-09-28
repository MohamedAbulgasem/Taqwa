package world.taqwa.timetables.gate.formats

import world.taqwa.timetables.gate.OfficialFormat

/**
 * The live month in Morocco's Habous tables (`round/data-maghreb-libya/off-habous-*.txt`): the
 * [DailyFormat] lines whose dates the file's first comment line gives as habous.gov.ma's current
 * Hijri month, printed in UTC+0, so a row using it names `clock` UTC. The same files' Wayback
 * captures are read by [HabousArchiveFormat].
 */
object HabousLiveFormat : OfficialFormat {
    override fun read(lines: List<String>, columns: Int): OfficialFormat.Read {
        val file = HabousFile.of(lines)
        val read = DailyFormat.read(lines, columns)
        return OfficialFormat.Read(read.days.filter { file.isLive(it.date) }, read.unreadable)
    }
}
