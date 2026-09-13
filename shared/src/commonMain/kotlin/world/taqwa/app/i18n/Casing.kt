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
