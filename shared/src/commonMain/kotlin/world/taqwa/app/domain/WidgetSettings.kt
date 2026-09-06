package world.taqwa.app.domain

/** The spec's four widget backgrounds. The fourth is labelled per platform at the UI layer —
 * "Translucent" on Android, "Frosted" on iOS — but is the same stored value either way. */
enum class WidgetBackground { FOLLOW_THEME, LIGHT, DARK, TRANSLUCENT_OR_FROSTED }
