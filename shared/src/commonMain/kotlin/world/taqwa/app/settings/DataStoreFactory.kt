package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.Path.Companion.toPath

internal const val SETTINGS_FILE = "taqwa.preferences_pb"

expect fun dataStoreDirectory(): String

/**
 * DataStore forbids two live instances over one file; every consumer must share this one.
 * `AppContainer`, `SystemEventReceiver` and `BackgroundRefreshBridge` can all be alive in the
 * same process at once, so each must observe the exact same `DataStore` over
 * `taqwa.preferences_pb` rather than opening its own — `by lazy` (SYNCHRONIZED by default)
 * guarantees a single, thread-safe instance no matter which caller touches it first.
 */
private val sharedDataStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath(
        // Without a handler a damaged preferences file throws CorruptionException out of every
        // read for ever — including the first one the app makes, inside a LaunchedEffect, which
        // takes the Recomposer down with it. That is an unrecoverable launch-crash loop whose
        // only exit is clearing app data. Starting again from defaults loses the reader's
        // settings, which is a far smaller loss than an app that cannot open.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    ) { "${dataStoreDirectory()}/$SETTINGS_FILE".toPath() }
}

fun createDataStore(): DataStore<Preferences> = sharedDataStore
