package world.taqwa.app.i18n

/**
 * Upper-cases interface text the way the interface language does. Kotlin's `uppercase()` is
 * locale-free, which is right for identifiers and wrong for Turkish, whose dotted i capitalises
 * to İ and whose dotless ı to I — "İkindi" became "İKINDI" on the countdown ring. Scripts
 * without case (Arabic, Urdu, Bengali) pass through untouched.
 */
fun String.uppercaseIn(language: UiLanguage): String = when {
    !language.latinScript -> this
    language == UiLanguage.TURKISH -> replace('i', 'İ').replace('ı', 'I').uppercase()
    else -> uppercase()
}

/**
 * The lower-casing twin, for comparing text rather than showing it: Turkish İ lowers to i and I
 * to ı, where the locale-free `lowercase()` turns İ into i plus a combining dot and I into i, so
 * a search for "iman" would skip every sentence-initial "İman". Other languages lower-case any
 * Latin letters they contain the ordinary way.
 */
fun String.lowercaseIn(language: UiLanguage): String = when (language) {
    UiLanguage.TURKISH -> replace('İ', 'i').replace('I', 'ı').lowercase()
    else -> lowercase()
}
