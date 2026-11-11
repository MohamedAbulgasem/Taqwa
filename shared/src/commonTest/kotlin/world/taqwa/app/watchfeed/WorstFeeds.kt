package world.taqwa.app.watchfeed

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.i18n.ClockFormat
import world.taqwa.app.i18n.PlatformFormat
import world.taqwa.app.i18n.UiLanguage
import world.taqwa.app.tasbeeh.TasbeehPreset
import world.taqwa.app.tasbeeh.TasbeehPresets
import world.taqwa.app.watch.WatchFeed
import world.taqwa.app.watch.WatchScreenWords
import world.taqwa.app.watch.WatchSpoken
import kotlin.time.Instant

/** What the worst phrases are made of: the three costliest kinds of character a phrase can hold. */
internal enum class PhraseKind {
    /** Arabic letters, two UTF-8 bytes each: the phrases a reader is most likely to write. */
    ARABIC,

    /** U+1F4FF, four UTF-8 bytes (and two UTF-16 units) each. */
    EMOJI,

    /** U+0001, which JSON writes as the six-byte escape `\u0001`: the most any character costs. */
    CONTROL,
}

/**
 * The largest feed the phone can be asked to build, for the size tests: a city name longer than any
 * the bundle holds, and phrases at the counter's limit. In commonTest, so that the Android host
 * tests and the iPhone's simulator tests measure the same worst case.
 */
internal object WorstFeeds {

    /** The longest phrase the counter's sheet takes (`TasbeehStore.MAX_PHRASE`), in code points. */
    private const val MAX_PHRASE = 60

    /** The largest target the counter's sheet takes (`TasbeehStore.MAX_TARGET`). */
    private const val MAX_TARGET = 1000

    /**
     * 100 Bengali letters, three UTF-8 bytes each: 300 bytes. The longest name the bundle holds on
     * 23 October is 91 bytes, and `theWorstCityIsLongerThanAnyTheBundleNames` keeps this ahead of
     * the bundle when the list is refreshed. The letters are ক to ন, U+0995 to U+09A8, all assigned.
     */
    val CITY: String = buildString { repeat(100) { append(Char(0x0995 + it % 20)) } }

    /**
     * The screens' words a worst-case feed carries where no language's real words can be read: on
     * the iPhone's simulator, whose resources are English (plan 3 Task 10). It is
     * [WatchScreenWords.ENGLISH] with every word, each string field, preset name, cardinal and run's
     * phrase, made of ঔ (U+0994, three UTF-8 bytes) twice as many times as the English has
     * characters; the keys, the preset ids and the runs' numbers stay as they are.
     * `theWorstWordsOutweighEveryLanguage` keeps it heavier than every language's real words when
     * a translation grows, as `theWorstCityIsLongerThanAnyTheBundleNames` keeps [CITY] ahead.
     */
    val SCREENS: WatchScreenWords by lazy {
        val json = Json { encodeDefaults = true }
        fun worse(element: JsonElement): JsonElement = when (element) {
            is JsonPrimitive -> if (element.isString) JsonPrimitive(WORST_LETTER.repeat(2 * element.content.length)) else element
            is JsonArray -> JsonArray(element.map(::worse))
            is JsonObject -> JsonObject(element.mapValues { (_, value) -> worse(value) })
        }
        val english = json.encodeToJsonElement(WatchScreenWords.serializer(), WatchScreenWords.ENGLISH)
        json.decodeFromJsonElement(WatchScreenWords.serializer(), worse(english))
    }

    /** ঔ, U+0994, an assigned Bengali letter: three UTF-8 bytes. */
    private val WORST_LETTER: String = Char(0x0994).toString()

    /**
     * [count] of the reader's own phrases of [kind], each [MAX_PHRASE] code points long, with the
     * largest target and the id `TasbeehStore` gives a phrase (`custom_` and the moment it was made).
     */
    fun presets(kind: PhraseKind, count: Int = 100): List<TasbeehPreset> = List(count) { index ->
        TasbeehPresets.custom("custom_${FIRST_ID + index}", phrase(kind, index), MAX_TARGET)
    }

    /**
     * The largest feed a phone builds at [location]: [language]'s words in [format]'s locale (a
     * format made for 12-hour time), 12-hour time, the worst [CITY] and a hundred phrases of [kind]
     * ([presets]), within the feed's budget; null where the builder gives none. One fixture
     * (Ruling 54 item 5), so that plan 2 Task 3's `theLargestFeedStaysWithinItsBudget`, Task 5's
     * `aFeedAtTheBudgetIsWithinTheMessageLimit` and Task 8's `theLargestContextFitsTheSmallestLimit`
     * build the same worst case. Each platform hands it its own format, spoken words, screens' words
     * (the language's own on the host, [SCREENS] on the simulator) and day.
     */
    fun largestFeed(
        location: GeoLocation,
        format: PlatformFormat,
        language: UiLanguage,
        spoken: WatchSpoken?,
        screens: WatchScreenWords,
        kind: PhraseKind,
        now: Instant,
        timesFor: (LocalDate) -> DayPrayerTimes,
    ): WatchFeed? = WatchFeedBuilder.build(
        WatchFeedInputs(
            location = location,
            city = CITY,
            language = language,
            spoken = spoken,
            screens = screens,
            prayerSettings = PrayerSettings(),
            format = format,
            clockFormat = ClockFormat.TWELVE_HOUR,
            phrases = presets(kind),
            now = now,
            timesFor = timesFor,
        ),
    )

    private fun phrase(kind: PhraseKind, index: Int): String = buildString {
        repeat(MAX_PHRASE) { position ->
            when (kind) {
                // ا to غ, U+0627 to U+063A, all assigned letters.
                PhraseKind.ARABIC -> append(Char(0x0627 + (index + position) % 20))
                // U+1F4FF as its surrogate pair, written from the code units so that no escape is
                // pasted raw.
                PhraseKind.EMOJI -> append(Char(0xD83D)).append(Char(0xDCFF))
                PhraseKind.CONTROL -> append(Char(0x0001))
            }
        }
    }

    /** A moment in 2026, in milliseconds, as `TasbeehStore` stamps a new phrase's id. */
    private const val FIRST_ID = 1_790_000_000_000L
}
