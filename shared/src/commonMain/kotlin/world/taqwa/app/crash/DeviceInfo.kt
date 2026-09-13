package world.taqwa.app.crash

/**
 * The five facts a crash report carries about where it happened (crash spec §2). Nothing in
 * here identifies a person: the language is the phone's language tag, the device its model name.
 */
data class DeviceInfo(
    val appVersion: String,
    val build: String,
    val platform: String,
    val device: String,
    val language: String,
)

/** Read from the platform: the bundle or package for the version, the OS for the rest. */
expect fun deviceInfo(): DeviceInfo

/** What a report says when even reading the version fails; the trace is still worth having. */
fun unknownDevice(): DeviceInfo = DeviceInfo("?", "?", "?", "?", "?")
