package world.taqwa.app.domain

enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/** The five that are prayed. Sunrise marks the end of the Fajr window and is never notified. */
val ObligatoryPrayers = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
