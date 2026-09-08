package world.taqwa.app.quran

import app.cash.sqldelight.db.SqlDriver

object QuranDb {
    /** Must equal the pipeline's USER_VERSION; a mismatch on disk triggers a fresh copy. */
    const val VERSION = 2
    const val FILE = "quran.db"
    const val RESOURCE = "files/quran.db"
}

/**
 * Opens the bundled database, copying it out of the app's resources into the platform's database
 * directory first if it is missing or carries a different `user_version`. Blocking; call it off
 * the main thread once, from [QuranRepository].
 */
expect fun createQuranDriver(): SqlDriver
