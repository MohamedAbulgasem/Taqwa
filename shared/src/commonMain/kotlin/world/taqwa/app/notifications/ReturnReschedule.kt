package world.taqwa.app.notifications

/**
 * Whether the notifications are planned each time the app is in front, the first time included,
 * in place of the plan made when App first composes. False plans once when App first composes, as
 * Android does, and never again on a return; a scene composed in the background is then planned at
 * composition, not when the person opens it.
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
