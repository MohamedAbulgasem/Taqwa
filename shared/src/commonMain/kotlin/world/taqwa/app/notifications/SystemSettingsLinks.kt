package world.taqwa.app.notifications

/**
 * The two system screens the Notifications settings can send someone to when the app itself
 * cannot put things right: the OS notification switch for Taqwa, and, on Android 12 and later,
 * the "Alarms & reminders" special access that an on-time adhan needs. Both are best effort: a
 * build with no such screen leaves the note in place and nothing else happens.
 */
expect fun openAppNotificationSettings()

/** A no-op where the concept does not exist (iOS fires local notifications at the stated time). */
expect fun requestExactAlarmAccess()

/**
 * Whether the platform has a per-app language page for Taqwa: Android 13 and later (the "App
 * languages" page `locales_config.xml` feeds) and iOS (the app's own Settings page lists a
 * Preferred Language once an app ships more than one localisation). Older Android has nothing
 * to open, so the Settings row only states the language there.
 */
expect fun canOpenAppLanguageSettings(): Boolean

/** Opens that page; a no-op where [canOpenAppLanguageSettings] is false. */
expect fun openAppLanguageSettings()
