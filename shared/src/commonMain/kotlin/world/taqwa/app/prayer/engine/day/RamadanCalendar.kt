package world.taqwa.app.prayer.engine.day

import kotlinx.datetime.LocalDate

/** Whether a civil date is in Ramadan by the authority's own calendar (Umm al-Qura's for Saudi Arabia). */
fun interface RamadanCalendar {
    fun isRamadan(date: LocalDate): Boolean
}
