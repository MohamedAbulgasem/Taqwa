package world.taqwa.app.audio

/**
 * True when a prayer notification would make no sound right now: the ringer is on silent or
 * vibrate, the notification volume is at zero, or Do Not Disturb is filtering. The sound sheet's
 * previews play on the notification stream on purpose — a preview is only honest at the loudness
 * the real notification will have — so on a silenced phone the play button does nothing audible,
 * which a tester read as a bug. The sheet says so, and only then (spec §17.1).
 *
 * iOS gives an app no way to read the mute switch, so it answers false and shows no hint.
 */
expect fun notificationSoundsMuted(): Boolean
