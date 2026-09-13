package world.taqwa.app.recitation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The recitation catalogue (spec 3a §4): who may be listened to, where each surah's audio lives
 * and what it should hash to. It is bundled with the app so the picker works before the first
 * network call, and re-fetched from the data repository at most daily so a reciter can be added
 * — or, if an estate ever asks, withdrawn — without an app update.
 *
 * Nothing here is a UI model. The names are the names the catalogue publishes; how a row is
 * drawn (the monogram, the hue) is [ReciterHue]'s business, from [Reciter.hue].
 */
@Serializable
data class RecitationManifest(
    val schema: Int,
    /** When the pipeline wrote this file, ISO-8601. Carried, not parsed: it is for humans. */
    val generated: String,
    /** The release-download prefix every asset URL is built on. Ends with a slash. */
    val base: String,
    val reciters: List<Reciter>,
) {
    fun reciter(id: String): Reciter? = reciters.firstOrNull { it.id == id }

    /**
     * Where the surah's `.taqa` lives: `<base><release>/<id>-<nnn>.taqa`, the surah number always
     * three digits so the 114 assets of a release sort the way a reader expects them to.
     */
    fun assetUrl(reciter: Reciter, surah: Int): String =
        base + reciter.release + "/" + reciter.id + "-" + surah.toString().padStart(3, '0') + ".taqa"

    fun assetUrl(reciterId: String, surah: Int): String? =
        reciter(reciterId)?.let { assetUrl(it, surah) }

    /**
     * The one input the app takes from the network, checked before it is trusted. Every asset
     * URL has to be HTTPS (both platforms would refuse cleartext anyway, but a manifest asking for
     * it is wrong rather than unlucky), and a reciter id or release is a single plain path
     * segment: each names a directory and a file under the audio root, so `..` or a slash in one
     * would point outside it.
     *
     * @throws IllegalArgumentException, which [ManifestProvider.store] turns into "keep the
     * catalogue already in force".
     */
    fun validate() {
        require(base.startsWith("https://")) { "Recitation manifest base is not https: $base" }
        for (reciter in reciters) {
            require(isPlainSegment(reciter.id)) { "Recitation manifest reciter id is not a plain name: ${reciter.id}" }
            require(isPlainSegment(reciter.release)) { "Recitation manifest release is not a plain name: ${reciter.release}" }
        }
    }

    companion object {
        /** The only schema this build understands; see [UnsupportedManifest]. */
        const val SCHEMA = 1

        private val PLAIN_SEGMENT = Regex("[A-Za-z0-9._-]{1,64}")

        /** Letters, digits, dot, underscore and hyphen only, and not the two names that mean a directory. */
        fun isPlainSegment(segment: String): Boolean =
            PLAIN_SEGMENT.matches(segment) && segment != "." && segment != ".."

        /** Alafasy, as decided on 7 September and confirmed on 12 September (spec §12.1). */
        const val DEFAULT_RECITER = "ar.alafasy"
    }
}

/**
 * One voice. [kbps] is the *true* measured bitrate, not the folder the corpus published it under
 * (spec §2: two of the ten are mislabelled at source). [gapMs] is the silence the player inserts
 * between ayahs, because four of the ten begin at full voice on sample zero and sound rushed
 * played back to back. [photo] is null for all ten by decision (spec §12.5) and is kept only so a
 * portrait can arrive by manifest if a reciter's own foundation ever grants one.
 */
@Serializable
data class Reciter(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    /** `murattal` for all ten; a string rather than an enum so a new style needs no app update. */
    val style: String,
    val kbps: Int,
    val gapMs: Int,
    /** A [ReciterHue] name. A hue this build does not know falls back to amber. */
    val hue: String,
    val photo: String? = null,
    /** The GitHub release holding this reciter's 114 assets, e.g. `audio-ar.alafasy-v1`. */
    val release: String,
    val totalBytes: Long,
    val surahs: List<SurahAsset> = emptyList(),
) {
    fun surah(n: Int): SurahAsset? = surahs.firstOrNull { it.n == n }

    /** The disc-and-glyph pair the picker draws this reciter with. */
    val hueColors: ReciterHue get() = ReciterHue.of(hue)

    /** The single Arabic letter drawn on the disc. See [monogramInitial]. */
    val monogram: String get() = monogramInitial(nameAr)
}

/** One surah of one reciter, as it sits in the release: byte size and SHA-256 of the `.taqa`. */
@Serializable
data class SurahAsset(
    val n: Int,
    val bytes: Long,
    @SerialName("sha256") val sha256: String,
)

/**
 * A manifest written by a newer app than this one. Thrown rather than tolerated: the schema
 * number is only ever raised for a change this build could not read correctly, and quietly
 * showing half a catalogue would be worse than falling back to the bundled copy — which is what
 * [ManifestProvider] does when it sees this.
 */
class UnsupportedManifest(val schema: Int) :
    IllegalArgumentException("Recitation manifest schema $schema is newer than ${RecitationManifest.SCHEMA}")

/** Reading and writing [RecitationManifest] JSON. */
object ManifestJson {
    /**
     * `ignoreUnknownKeys` on purpose: the manifest is data we ship separately from the app, and a
     * field added for a later slice (word timings, a licence string) must not stop this build
     * reading the reciters it does understand. The *schema* number is the deliberate break.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * @throws UnsupportedManifest if the file was written for a newer schema.
     * @throws kotlinx.serialization.SerializationException if it is not a manifest at all.
     * @throws IllegalArgumentException if it names a cleartext base or a reciter id or release
     * that is not a plain path segment; see [RecitationManifest.validate].
     */
    fun parse(text: String): RecitationManifest {
        val manifest = json.decodeFromString(RecitationManifest.serializer(), text)
        if (manifest.schema > RecitationManifest.SCHEMA) throw UnsupportedManifest(manifest.schema)
        manifest.validate()
        return manifest
    }

    fun encode(manifest: RecitationManifest): String =
        json.encodeToString(RecitationManifest.serializer(), manifest)
}
