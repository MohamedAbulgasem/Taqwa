package world.taqwa.app.watch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The feed as JSON: what the phone encodes and sends, and what a watch decodes and stores.
 *
 * One feed always encodes to the same text, since the keys come in declaration order with no
 * whitespace between them. The phone relies on that: it decides whether a feed has changed, and
 * so whether to send it again, by comparing encodings without `writtenAt` (spec 1.2).
 */
object WatchFeedJson {
    /**
     * The newest format this build reads, and the one it writes. It rises only for a change that
     * a build still on the old number could not read correctly; a field added with a default is
     * not one. A watch that meets a higher number keeps the last feed it understood and asks for
     * an update (spec 1.1).
     */
    const val VERSION = 1

    /**
     * `ignoreUnknownKeys` on purpose: a field a later phone adds must not stop a watch that has
     * not been updated yet from reading the rest. `encodeDefaults` so that every key is on the
     * wire whatever the model's defaults are, `tasbeeh` and the two ends included, and the Swift
     * reader never has to guess which keys a given phone leaves out. The one exception is the
     * Tile's three compact countdown forms, which are left out when empty
     * ([WatchCountdownPhrases.compactHoursMinutes]) and read as empty when absent.
     */
    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(feed: WatchFeed): String = format.encodeToString(WatchFeed.serializer(), feed)

    /**
     * The feed in [json], or null when this build cannot use it: the text is not JSON, is not a
     * feed, is a feed of a newer [VERSION] than this build reads, or declares a version below 1,
     * which no phone has ever written. The caller then keeps whatever feed it already had.
     */
    fun decode(json: String): WatchFeed? {
        val feed = try {
            format.decodeFromString(WatchFeed.serializer(), json)
        } catch (_: IllegalArgumentException) {
            // A SerializationException is one: malformed JSON, a key missing, a value of the
            // wrong type.
            return null
        }
        return feed.takeIf { it.version in 1..VERSION }
    }

    /**
     * The `version` that the document in [json] declares, or null when it declares none that can
     * be read: the text is not JSON, is not an object, or has no `version` that is a whole number.
     *
     * [decode] answers null both for a feed of a newer [VERSION] and for one that is simply
     * broken, and the watch must tell them apart: a newer feed means "Update Taqwa on your
     * watch", a broken one means nothing is said, and either way the last feed it understood is
     * kept (spec 1.1 and 2.6). A caller that has got null from [decode] asks this: a version
     * above [VERSION] is the first case, and anything else the second. Only this one key is read,
     * so it answers for a newer feed whatever shape the rest of that feed has.
     */
    fun versionOf(json: String): Int? {
        val root = try {
            format.parseToJsonElement(json)
        } catch (_: IllegalArgumentException) {
            // A SerializationException again: the text is not JSON, or is cut short.
            return null
        }
        val version = (root as? JsonObject)?.get("version") as? JsonPrimitive ?: return null
        // A version written as text, "2", is not a version.
        return if (version.isString) null else version.intOrNull
    }
}
