package world.taqwa.app.widget

import android.content.Context
import world.taqwa.app.settings.appContext

private const val PREFS_NAME = "taqwa_widget_mirror"

// `appContext` is only assigned from `TaqwaApplication.onCreate`, which never runs inside a
// plain JVM unit test (there is no Robolectric here). `TodayViewModel.refresh()` — exercised by
// `TodayViewModelTest` with a fake `SettingsRepository` and no real Android process — reaches
// this store through `WidgetMirrorWriter`. `appContext`'s backing field lives in another file, so
// `::appContext.isInitialized` cannot be checked from here; catching the one exception `lateinit`
// throws is the tolerant equivalent, in the same spirit as `toEnumOr`.
private class AndroidKeyValueStore : KeyValueStore {
    private val prefs: android.content.SharedPreferences? by lazy {
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: UninitializedPropertyAccessException) {
            null
        }
    }
    override fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }
    override fun getString(key: String): String? = prefs?.getString(key, null)
}

actual fun createWidgetKeyValueStore(): KeyValueStore = AndroidKeyValueStore()
