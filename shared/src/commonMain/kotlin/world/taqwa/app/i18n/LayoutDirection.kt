package world.taqwa.app.i18n

private val RTL_LANGUAGES = setOf("ar", "he", "fa", "ur")

/**
 * Slice 1 only ships Arabic and English UI copy, but the other RTL tags cost nothing to
 * recognise now and save a bug report the day a device's system language is Urdu or Farsi.
 */
object LayoutDirection {
    fun isRtl(languageTag: String): Boolean =
        languageTag.substringBefore('-').lowercase() in RTL_LANGUAGES
}
