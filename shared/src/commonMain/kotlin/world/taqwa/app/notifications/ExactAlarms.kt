package world.taqwa.app.notifications

/**
 * Whether the platform will currently let the app schedule an *exact* alarm.
 *
 * Android 12 (API 31) put exact alarms behind `SCHEDULE_EXACT_ALARM`, which the user can revoke
 * in Settings → Apps → Special access → Alarms & reminders. When it is revoked the scheduler
 * falls back to an inexact window, so the adhan can arrive a few minutes late — a deviation the
 * user deserves to be told about rather than left to discover. iOS has no such concept and
 * always reports true.
 */
expect fun canScheduleExactAlarms(): Boolean
