package world.taqwa.app.notifications

/**
 * Whether the app coming back to the front re-plans the notifications, as a launch does.
 *
 * iOS says yes. A request added while notifications are not allowed is refused, and nothing
 * retries it: someone who turns notifications on in iOS Settings and comes back to the app had
 * nothing armed until the next launch or background refresh, and an app that is only ever
 * resumed from the app switcher ran its plan out after a few days. Android says no: it arms its
 * alarms whether or not notifications are allowed, its prayer alarm and the WorkManager top-up
 * keep the plan full while the app is closed, and its system receivers cover a changed clock or
 * zone.
 */
internal expect val returnToFrontReschedules: Boolean
