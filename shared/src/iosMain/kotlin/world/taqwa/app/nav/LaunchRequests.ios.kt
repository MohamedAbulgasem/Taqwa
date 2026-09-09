package world.taqwa.app.nav

/**
 * The entry point `iOSApp.swift`'s `.onOpenURL` calls when it parses a `taqwa://ayah/<s>/<a>`
 * link (design spec §7-8) — Swift sees this as `LaunchRequests_iosKt.openAyahFromWidget`. A
 * top-level function rather than a member of [LaunchRequests] itself so Swift's call site reads
 * as a plain function rather than reaching through a Kotlin `object` singleton accessor.
 */
fun openAyahFromWidget(surah: Int, ayah: Int) = LaunchRequests.openAyah(surah, ayah)
