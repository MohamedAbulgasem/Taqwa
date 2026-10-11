package world.taqwa.app.nav

/**
 * The entry point `iOSApp.swift`'s `.onOpenURL` calls when it parses a `taqwa://ayah/<s>/<a>`
 * link (design spec §7-8) — Swift sees this as `LaunchRequests_iosKt.openAyahFromWidget`. A
 * top-level function rather than a member of [LaunchRequests] itself so Swift's call site reads
 * as a plain function rather than reaching through a Kotlin `object` singleton accessor.
 */
fun openAyahFromWidget(surah: Int, ayah: Int) = LaunchRequests.openAyah(surah, ayah)

/**
 * The entry `iOSApp.swift`'s notification-centre delegate calls when a notification is tapped,
 * with the request's identifier — Swift sees it as `LaunchRequests_iosKt.openAdhkarFromNotification`.
 * An adhkar reminder's id opens its set (adhkar spec §5); any other id is ignored here, and the
 * tap simply brings the app forward as it always has.
 */
fun openAdhkarFromNotification(identifier: String) = LaunchRequests.openAdhkarForNotification(identifier)
