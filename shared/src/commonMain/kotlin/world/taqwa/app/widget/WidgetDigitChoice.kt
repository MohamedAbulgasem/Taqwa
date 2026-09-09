package world.taqwa.app.widget

import world.taqwa.app.i18n.PlatformFormat

/**
 * Whether the app draws numbers in Arabic-Indic digits, asked of the platform rather than derived
 * from a language tag.
 *
 * Both mirrors carry this answer ([AyahPoolMirror.arabicIndicDigits],
 * [WidgetSnapshot.arabicIndicDigits]) so a widget never has to guess, and both writers ask it the
 * same way — one line, one definition, no chance of the two mirrors disagreeing about the same
 * device. The probe is a literal `1`, because that is the smallest question with an unambiguous
 * answer: a locale whose formatter renders it as `١` renders every other digit in that set too.
 *
 * The tag rule it replaces is CLDR's default numbering system, and the device is allowed to
 * disagree with it: under an `ar-LY` per-app locale the S23's ICU data renders the app's numbers
 * in Arabic-Indic digits although CLDR's default for that tag is `latn`, so the widget footer and
 * the app showed the same reference in two different scripts (D2, S23 round).
 */
fun PlatformFormat.usesArabicIndicDigits(): Boolean = localizedDigits(1) == "\u0661"
