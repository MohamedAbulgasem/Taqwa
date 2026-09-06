package world.taqwa.app.widget

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

private const val APP_GROUP_ID = "group.world.taqwa.app"

@OptIn(ExperimentalForeignApi::class)
private class IosKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults(suiteName = APP_GROUP_ID)
    override fun putString(key: String, value: String) { defaults.setObject(value, key) }
    override fun getString(key: String): String? = defaults.stringForKey(key)
}

actual fun createWidgetKeyValueStore(): KeyValueStore = IosKeyValueStore()
