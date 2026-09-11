package world.taqwa.app.tasbeeh

/**
 * The seven built-in presets of spec §2, in the order the chip row shows them, plus the lookup
 * the screen and the store share. Ids are stable: they are the keys the counts are stored under.
 *
 * The transliterations and meanings are plain Kotlin rather than resources because they are data
 * belonging to the phrase, not interface copy — they are shown only under a Latin interface, and
 * an Arabic interface hides them entirely rather than translating them.
 */
object TasbeehPresets {
    const val DEFAULT_ID = "after_prayer"

    private val SUBHANALLAH = Dhikr(
        id = "subhanallah",
        arabic = "سُبْحَانَ ٱللَّٰهِ",
        transliteration = "SubhanAllah",
        meaning = "Glory be to Allah",
    )

    private val ALHAMDULILLAH = Dhikr(
        id = "alhamdulillah",
        arabic = "ٱلْحَمْدُ لِلَّٰهِ",
        transliteration = "Alhamdulillah",
        meaning = "All praise is due to Allah",
    )

    private val ALLAHU_AKBAR = Dhikr(
        id = "allahu_akbar",
        arabic = "ٱللَّٰهُ أَكْبَرُ",
        transliteration = "Allahu Akbar",
        meaning = "Allah is the Greatest",
    )

    private val ASTAGHFIRULLAH = Dhikr(
        id = "astaghfirullah",
        arabic = "أَسْتَغْفِرُ ٱللَّٰهَ",
        transliteration = "Astaghfirullah",
        meaning = "I seek Allah's forgiveness",
    )

    private val LA_ILAHA_ILLALLAH = Dhikr(
        id = "la_ilaha_illallah",
        arabic = "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ",
        transliteration = "La ilaha illallah",
        meaning = "There is no god but Allah",
    )

    private val SUBHANALLAHI_WA_BIHAMDIHI = Dhikr(
        id = "subhanallahi_wa_bihamdihi",
        arabic = "سُبْحَانَ ٱللَّٰهِ وَبِحَمْدِهِ",
        transliteration = "SubhanAllahi wa bihamdihi",
        meaning = "Glory and praise be to Allah",
    )

    val builtIn: List<TasbeehPreset> = listOf(
        TasbeehPreset("after_prayer", listOf(DhikrPart(SUBHANALLAH, 33), DhikrPart(ALHAMDULILLAH, 33), DhikrPart(ALLAHU_AKBAR, 34))),
        TasbeehPreset("subhanallah", listOf(DhikrPart(SUBHANALLAH, 33))),
        TasbeehPreset("alhamdulillah", listOf(DhikrPart(ALHAMDULILLAH, 33))),
        TasbeehPreset("allahu_akbar", listOf(DhikrPart(ALLAHU_AKBAR, 34))),
        TasbeehPreset("astaghfirullah", listOf(DhikrPart(ASTAGHFIRULLAH, 100))),
        TasbeehPreset("la_ilaha_illallah", listOf(DhikrPart(LA_ILAHA_ILLALLAH, 100))),
        TasbeehPreset("subhanallahi_wa_bihamdihi", listOf(DhikrPart(SUBHANALLAHI_WA_BIHAMDIHI, 100))),
    )

    /** The default when nothing has been selected yet, and the fallback after a delete. */
    val default: TasbeehPreset = builtIn.first { it.id == DEFAULT_ID }

    /** A user's own phrase: one part, no transliteration, no meaning, shown as typed. */
    fun custom(id: String, phrase: String, target: Int): TasbeehPreset =
        TasbeehPreset(id, listOf(DhikrPart(Dhikr(id, phrase), target)), custom = true)

    /** Built-ins first, then [custom] in creation order — null for an id from neither. */
    fun byId(id: String, custom: List<TasbeehPreset>): TasbeehPreset? =
        builtIn.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }
}
