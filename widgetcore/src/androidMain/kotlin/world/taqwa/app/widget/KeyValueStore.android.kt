package world.taqwa.app.widget

import android.content.Context
import android.content.SharedPreferences
import world.taqwa.app.settings.appContext

/**
 * The widgets' mirror: the prayer snapshot and the widget background. Builds before 1.7.0 kept the
 * ayah pool and its seed here too. On an install updated from one, the pool leaves at its first
 * write after the update, and the seed stays here for good, since it is never rewritten.
 */
private const val PREFS_NAME = "taqwa_widget_mirror"

/**
 * The ayah pool's own file, since 1.7.0. The pool mirror is 180-310 KB since the pool became 365
 * (spec §4), and SharedPreferences rewrites a whole file whenever anything in it changes: in
 * [PREFS_NAME], beside the prayer snapshot, every snapshot write rewrote the pool with it. While
 * the Prayer screen is open the snapshot is written each time its countdown passes a whole minute
 * or its ring one of the 120 steps it takes from one prayer to the next (`WidgetMirrorWriter`),
 * mostly at different moments: once to twice a minute with the prayers two hours apart or more,
 * and more often when they are closer (about 2.7 times a minute at 70 minutes, as Maghrib to Isha
 * can be). On its own the pool's file is written only when the pool itself changes; the app's
 * write of the same pool at every start changes nothing, and SharedPreferences then skips the
 * disk (AOSP's `SharedPreferencesImpl`). Backed up exactly as [PREFS_NAME] is, which is not at
 * all: `allowBackup` is false and `data_extraction_rules.xml` excludes the whole `sharedpref`
 * domain, so no rule names either file.
 */
private const val AYAH_POOL_PREFS_NAME = "taqwa_widget_ayah_pool"

/** Every key [AyahPoolMirror] and `AyahPoolMirrorWriter` use: the pool and the install's seed. */
internal val AYAH_POOL_KEYS: Set<String> = setOf(AyahPoolMirror.KEY, AyahPoolMirror.SEED_KEY)

/**
 * The widget [KeyValueStore] over two SharedPreferences files: the ayah pool's keys
 * ([AYAH_POOL_KEYS]) in their own, everything else in the mirror file.
 *
 * A pool key is written to its own file and then removed from the mirror file if a build before
 * 1.7.0 left it there. It is read from both, the mirror file first, and the own file's copy is the
 * one returned whenever there is one. So an install updated from 1.6.0 still finds the pool, and
 * the seed, before the app has run again (the Android widget draws in the app's process and may
 * draw after the update and before the app is opened); a stale copy left in the mirror file never
 * wins over the own file's; and a read that runs while the pool moves never comes back empty
 * ([getString] says why). The seed is never rewritten once it exists (`AyahPoolMirrorWriter`), so
 * an updated install keeps reading its seed from the mirror file, where 1.6.0 put it.
 *
 * Every widget reader and writer on Android comes through here ([createWidgetKeyValueStore]), so
 * none of them names a file: the Glance widgets (the ayah widget writes the pool itself when it
 * finds none), the Settings › Widgets previews, the pool's writer in `AppEffects`, the snapshot's
 * in `PrayerViewModel`, the widget background in `SettingsRoutes`, and `WidgetMirrorRefresher`,
 * which the alarm receiver, the system-event receiver, the top-up worker, a language change
 * (`AppEffects`) and `refreshForNewTimes` (`SettingsRoutes`) call.
 */
internal class AndroidKeyValueStore(
    mirrorFile: () -> SharedPreferences?,
    ayahPoolFile: () -> SharedPreferences?,
) : KeyValueStore {
    private val mirror: SharedPreferences? by lazy(mirrorFile)
    private val ayahPool: SharedPreferences? by lazy(ayahPoolFile)

    override fun putString(key: String, value: String) {
        if (key !in AYAH_POOL_KEYS) {
            mirror?.edit()?.putString(key, value)?.apply()
            return
        }
        val pool = ayahPool ?: return
        pool.edit().putString(key, value).apply()
        // Removed only after the put above, never before, so the key is in one file or the other
        // at every moment; `getString` reads the two files in the order that relies on this.
        mirror?.takeIf { it.contains(key) }?.edit()?.remove(key)?.apply()
    }

    /**
     * A pool key is read from the mirror file first and its own file second, and the own file's
     * copy wins when there is one.
     *
     * [putString] puts into the own file before it removes from the mirror file, and
     * SharedPreferences changes a file's map in memory inside `apply()`, under that file's lock,
     * which `getString` takes too (AOSP's `SharedPreferencesImpl`, android-36: `apply` calls
     * `commitToMemory`, which edits the map under `mLock`; the disk write is only queued). Each
     * file is one object in the process (`ContextImpl` caches it), so every store shares that
     * lock, and what one thread did under it is seen by the next thread to take it. So a read of
     * the mirror file that finds the key removed comes after the removal, and therefore after the
     * put, and the read of the own file that follows finds the put, or a later value. A read of
     * the mirror file that still finds the key keeps that copy, for the case where the own file
     * has nothing yet.
     *
     * Read the other way round, a read could straddle the move: find the own file still empty,
     * then, the writer having put and removed in between, find the mirror file empty as well, and
     * return null for a pool that was in one file or the other all along. That could happen once
     * per install, at the first pool write after the update from 1.6.0, and draw one blank ayah
     * card until the redraw that follows the write, or have the widget write the pool once more.
     */
    override fun getString(key: String): String? {
        if (key !in AYAH_POOL_KEYS) return mirror?.getString(key, null)
        val legacy = mirror?.getString(key, null)
        return ayahPool?.getString(key, null) ?: legacy
    }
}

// `appContext` is only assigned from `TaqwaApplication.onCreate`, which never runs inside a
// plain JVM unit test (there is no Robolectric here). `PrayerViewModel.refresh()` — exercised by
// `PrayerViewModelTest` with a fake `SettingsRepository` and no real Android process — reaches
// this store through `WidgetMirrorWriter`. `appContext`'s backing field lives in another file, so
// `::appContext.isInitialized` cannot be checked from here; catching the one exception `lateinit`
// throws is the tolerant equivalent, in the same spirit as `toEnumOr`.
private fun preferences(name: String): SharedPreferences? =
    try {
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    } catch (e: UninitializedPropertyAccessException) {
        null
    }

actual fun createWidgetKeyValueStore(): KeyValueStore =
    AndroidKeyValueStore({ preferences(PREFS_NAME) }, { preferences(AYAH_POOL_PREFS_NAME) })
