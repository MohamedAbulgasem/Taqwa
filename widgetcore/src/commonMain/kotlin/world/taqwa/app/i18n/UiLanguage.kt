package world.taqwa.app.i18n

/**
 * The seven languages the interface is written in, and the two facts that follow from the
 * language alone: which way the layout runs, and whether the script is Latin — which decides the
 * typeface (Manrope has Latin glyphs only) and whether letter-spacing is applied at all.
 *
 * Everything else that differs by language — prayer names, Hijri months, the words baked into a
 * notification — lives in a table keyed by this enum. Nothing should ask `if (arabic)` any more:
 * Urdu is right-to-left but not Arabic, Bengali is neither Latin nor right-to-left, and the next
 * language will be different again.
 */
enum class UiLanguage(val code: String, val rtl: Boolean, val latinScript: Boolean) {
    ENGLISH("en", rtl = false, latinScript = true),
    ARABIC("ar", rtl = true, latinScript = false),
    FRENCH("fr", rtl = false, latinScript = true),
    TURKISH("tr", rtl = false, latinScript = true),
    INDONESIAN("id", rtl = false, latinScript = true),
    URDU("ur", rtl = true, latinScript = false),
    BENGALI("bn", rtl = false, latinScript = false),
    ;

    /**
     * Written in the Arabic script. A prayer's name in such an interface *is* the Arabic-script
     * name, so it stands alone where a Latin or Bengali interface pairs its own name with the
     * Arabic one; the same rule picks Arabic-script names for reciters and surahs.
     */
    val arabicScript: Boolean get() = this == ARABIC || this == URDU

    companion object {
        /**
         * From a BCP-47 tag ("ur-PK", "id-ID"), a bare code, or the value of the `ui_language`
         * resource; English for anything the app does not speak. Java still spells Indonesian
         * "in" and Hebrew "iw" in `Locale.getLanguage()`, so those are folded in.
         */
        fun of(tag: String?): UiLanguage {
            val code = tag.orEmpty().substringBefore('-').substringBefore('_').lowercase()
            val iso = when (code) {
                "in" -> "id"
                else -> code
            }
            return entries.firstOrNull { it.code == iso } ?: ENGLISH
        }

        val codes: List<String> get() = entries.map { it.code }
    }
}
