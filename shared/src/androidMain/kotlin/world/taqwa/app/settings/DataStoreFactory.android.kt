package world.taqwa.app.settings

import android.content.Context

lateinit var appContext: Context

actual fun dataStoreDirectory(): String = appContext.filesDir.path
