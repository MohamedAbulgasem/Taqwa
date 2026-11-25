package world.taqwa.app.widget

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android widget store's two files (`KeyValueStore.android.kt`): the ayah pool's keys in a file
 * of their own, so the prayer snapshot's writes, once to a few times a minute while the Prayer
 * screen is open, no longer rewrite the pool, and an install updated from 1.6.0, whose pool and
 * seed are still in the mirror file, keeps finding both, also while the pool moves.
 */
class AndroidKeyValueStoreTest {

    /** Both files' edits, in the order they were applied: "pool: put ayah_pool", and so on. */
    private val log = mutableListOf<String>()
    private val mirrorFile = MapPreferences("mirror", log)
    private val poolFile = MapPreferences("pool", log)
    private val store = AndroidKeyValueStore({ mirrorFile }, { poolFile })

    // AYAH_POOL_KEYS is not restated: the ayah pool mirror's own writes go through the store, so a
    // key it starts writing without its being added to AYAH_POOL_KEYS lands in the mirror file and
    // fails this, as does a key in AYAH_POOL_KEYS it no longer writes.
    @Test
    fun everythingTheAyahPoolMirrorWritesGoesToThePoolsOwnFile() {
        val mirror = AyahPoolMirror(
            languageTag = "en",
            translationId = "en.sahih",
            translationRtl = false,
            arabicIndicDigits = false,
            entries = listOf(AyahPoolEntry(13, 28, "Ar-Ra'd", "ar-rad", "arabic", "translation", "followed by")),
        )

        AyahPoolMirror.writeSeed(store, 42L)
        AyahPoolMirror.write(store, mirror)

        assertEquals(AYAH_POOL_KEYS, poolFile.values.keys)
        assertEquals(0, mirrorFile.edits, "no edit of the mirror file")
        assertEquals(mirror, AyahPoolMirror.read(store))
        assertEquals(42L, AyahPoolMirror.seed(store))
    }

    @Test
    fun thePoolAndTheSeedAreWrittenToTheirOwnFile() {
        store.putString(AyahPoolMirror.SEED_KEY, "42")
        store.putString(AyahPoolMirror.KEY, "pool")

        assertEquals(mapOf(AyahPoolMirror.SEED_KEY to "42", AyahPoolMirror.KEY to "pool"), poolFile.values)
        assertTrue(mirrorFile.values.isEmpty())
        assertEquals("pool", store.getString(AyahPoolMirror.KEY))
        assertEquals("42", store.getString(AyahPoolMirror.SEED_KEY))
    }

    @Test
    fun everyOtherKeyStaysInTheMirrorFile() {
        store.putString(WidgetInputsMirror.KEY, "snapshot")
        store.putString("widget_background", "DARK")

        assertEquals(mapOf(WidgetInputsMirror.KEY to "snapshot", "widget_background" to "DARK"), mirrorFile.values)
        assertTrue(poolFile.values.isEmpty())
        assertEquals("snapshot", store.getString(WidgetInputsMirror.KEY))
    }

    // An install updated from 1.6.0: the pool and the seed are in the mirror file, and the widget may
    // draw before the app runs and rewrites the pool.
    @Test
    fun aPoolAndSeedLeftInTheMirrorFileAreStillRead() {
        mirrorFile.values[AyahPoolMirror.KEY] = "old pool"
        mirrorFile.values[AyahPoolMirror.SEED_KEY] = "7"

        assertEquals("old pool", store.getString(AyahPoolMirror.KEY))
        assertEquals("7", store.getString(AyahPoolMirror.SEED_KEY))
        assertTrue(poolFile.values.isEmpty(), "reading moves nothing")
    }

    @Test
    fun writingThePoolMovesItOutOfTheMirrorFileAndLeavesTheRest() {
        mirrorFile.values[AyahPoolMirror.KEY] = "old pool"
        mirrorFile.values[AyahPoolMirror.SEED_KEY] = "7"
        mirrorFile.values[WidgetInputsMirror.KEY] = "snapshot"

        store.putString(AyahPoolMirror.KEY, "new pool")

        assertEquals(mapOf(AyahPoolMirror.KEY to "new pool"), poolFile.values)
        assertEquals(mapOf(AyahPoolMirror.SEED_KEY to "7", WidgetInputsMirror.KEY to "snapshot"), mirrorFile.values)
        assertEquals("new pool", store.getString(AyahPoolMirror.KEY))
        assertEquals("7", store.getString(AyahPoolMirror.SEED_KEY), "the old seed is still the install's")
    }

    // The order the reads rely on (next test): a pool key reaches its own file before it leaves the
    // mirror file, so it is in one or the other at every moment.
    @Test
    fun aPoolKeyIsPutInItsOwnFileBeforeItLeavesTheMirrorFile() {
        for (key in AYAH_POOL_KEYS) {
            mirrorFile.values[key] = "old"
            log.clear()

            store.putString(key, "new")

            assertEquals(listOf("pool: put $key", "mirror: remove $key"), log, key)
        }
    }

    // The move at the first pool write after the update from 1.6.0, run between a reader's two reads,
    // whichever file it reads first: the reader gets the old value or the new one, never nothing.
    @Test
    fun aReadStraddlingTheMoveNeverComesBackEmpty() {
        for (key in AYAH_POOL_KEYS) {
            mirrorFile.values[key] = "old"
            var moved = false
            val moveOnce = {
                if (!moved) {
                    moved = true
                    store.putString(key, "new")
                }
            }
            mirrorFile.afterRead = moveOnce
            poolFile.afterRead = moveOnce

            val read = store.getString(key)

            assertTrue(moved, "the move ran during the read of $key")
            assertTrue(read == "old" || read == "new", "$key read as $read while it moved")
        }
    }

    // A copy left in the mirror file by a process that died between the two writes.
    @Test
    fun theOwnFileWinsOverACopyStillInTheMirrorFile() {
        for (key in AYAH_POOL_KEYS) {
            poolFile.values[key] = "new"
            mirrorFile.values[key] = "old"

            assertEquals("new", store.getString(key), key)
        }
    }

    @Test
    fun theMirrorFileIsNotEditedWhenItHoldsNoPool() {
        store.putString(AyahPoolMirror.KEY, "pool")
        store.putString(AyahPoolMirror.KEY, "pool again")

        assertEquals(0, mirrorFile.edits, "no edit of the mirror file for a key it never had")
        assertEquals(2, poolFile.edits)
    }

    // Outside an app process (a plain JVM test of shared code), there is no context and so no file.
    @Test
    fun withoutFilesNothingIsStoredAndNothingThrows() {
        val none = AndroidKeyValueStore({ null }, { null })
        none.putString(AyahPoolMirror.KEY, "pool")
        none.putString(WidgetInputsMirror.KEY, "snapshot")

        assertNull(none.getString(AyahPoolMirror.KEY))
        assertNull(none.getString(WidgetInputsMirror.KEY))
    }

    @Test
    fun aPoolWithNowhereToGoLeavesTheMirrorFileAlone() {
        mirrorFile.values[AyahPoolMirror.KEY] = "old pool"
        val noPoolFile = AndroidKeyValueStore({ mirrorFile }, { null })

        noPoolFile.putString(AyahPoolMirror.KEY, "new pool")

        assertEquals("old pool", noPoolFile.getString(AyahPoolMirror.KEY))
        assertFalse(mirrorFile.edits > 0)
    }
}

/**
 * SharedPreferences over a map: what the store calls, applied at once, and how many edits. Each
 * applied change is added to [log] as "<name>: put <key>" or "<name>: remove <key>", so two files
 * given one log record the order between them, and [afterRead] runs inside each getString, after
 * the value is read and before it is returned.
 */
private class MapPreferences(
    private val name: String,
    private val log: MutableList<String>,
) : SharedPreferences {
    val values = mutableMapOf<String, String>()
    var edits = 0
    var afterRead: () -> Unit = {}

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? {
        val value = values[key] ?: defValue
        afterRead()
        return value
    }
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun contains(key: String): Boolean = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor {
        edits++
        return object : SharedPreferences.Editor {
            private val puts = mutableMapOf<String, String>()
            private val removes = mutableSetOf<String>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value == null) removes += key else puts[key] = value
                return this
            }
            override fun remove(key: String): SharedPreferences.Editor {
                removes += key
                return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = unsupported()
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = unsupported()
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = unsupported()
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = unsupported()
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = unsupported()
            override fun clear(): SharedPreferences.Editor = unsupported()
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                removes.forEach {
                    values.remove(it)
                    log += "$name: remove $it"
                }
                puts.forEach { (key, value) ->
                    values[key] = value
                    log += "$name: put $key"
                }
            }
        }
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("the widget store writes strings only")
}
