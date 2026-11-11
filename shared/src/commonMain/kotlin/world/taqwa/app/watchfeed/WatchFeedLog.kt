package world.taqwa.app.watchfeed

import world.taqwa.app.watch.WatchLinkContract

/**
 * How a release log line names the delivery [text] holds (`WatchDelivery.text`, or a watch's echo):
 * by its number and fingerprint alone, `<seq>/<fingerprint>` (Ruling 52 item 4). An echo of nothing
 * is `none`, and anything `WatchLinkContract.deliveryOf` refuses is `unreadable`, never the text as
 * it came, since it may hold anything.
 *
 * Never the origin: it is the install's one random number, the same in every feed the phone sends,
 * so a log that carried it would tie every line of one phone together. No delivery holds the place
 * or the bearing. A debug harness may log the origin; nothing in a release build does.
 */
internal fun loggedDelivery(text: String): String {
    if (text.isEmpty()) return "none"
    val delivery = WatchLinkContract.deliveryOf(text) ?: return "unreadable"
    return "${delivery.seq}/${delivery.fingerprint}"
}

/**
 * The steps of one ask of [WatchFeedSync] (Ruling 70 item 1), as its step line names them, so a
 * slow step can be named (P2.7's round 2 could not say where a cut handshake's 5 s went):
 *
 * - [FORGET], [ECHO]: step 0, the record's read and write for `WATCH_CHANGED`'s forget and for the
 *   echo `WATCH_ASKED` takes in;
 * - [WATCHES]: step 1, the capability query (Android's one reading of its watches, the iPhone's
 *   property read);
 * - [BUILD]: step 2, the feed's build;
 * - [ENCODE]: step 3, its JSON and fingerprint;
 * - [RECORD]: step 4, the watches' keys and the record's read;
 * - [SEND]: steps 5 and 6, the number written down, then the link's send. A feed too large for the
 *   link is encoded and sent once more without its phrases: [ENCODE] and [SEND] again.
 */
internal enum class WatchAskStep { FORGET, ECHO, WATCHES, BUILD, ENCODE, RECORD, SEND }

/**
 * One step an ask reached, and its milliseconds on the sync's clock. [done] is false for the step
 * the ask ended in, the budget's cut or a throw, timed up to that end.
 */
internal data class AskStep(val step: WatchAskStep, val millis: Long, val done: Boolean = true)

/**
 * One ask's steps, in the order taken: its [reason], whether it is the ask asked again after a cut
 * ([again]), its [result], and whether the budget cut it ([cut]).
 */
internal data class AskTimes(
    val reason: WatchSyncReason,
    val again: Boolean,
    val result: WatchSendResult,
    val cut: Boolean,
    val steps: List<AskStep>,
)

/**
 * The step line of one ask: `TaqwaWatch STEPS <reason>[ again] <result>[ cut]: <step>
 * <ms>ms[ unfinished], …`, for example `TaqwaWatch STEPS WATCH_ASKED FAILED cut: echo 2ms, watches
 * 812ms, build 4186ms unfinished`. A debug build prints it, and a signed one once its tag is set
 * to DEBUG ([stepsLogged]). It names no place, person or delivery, as a release line does not.
 */
internal fun askStepsLine(times: AskTimes): String = buildString {
    append(WATCH_LOG_TAG).append(" STEPS ").append(times.reason.name)
    if (times.again) append(" again")
    append(' ').append(times.result.name)
    if (times.cut) append(" cut")
    append(": ")
    append(times.steps.joinToString(", ") { "${it.step.name.lowercase()} ${it.millis}ms" + if (it.done) "" else " unfinished" })
}

/**
 * Whether this is a debug build, which always prints [askStepsLine]: on Android the app's
 * `FLAG_DEBUGGABLE`, on the iPhone a debug Kotlin binary. `:shared` has no build config of its own.
 */
internal expect fun isDebugBuild(): Boolean

/**
 * The word every watch-feed line begins with, the release line's and [askStepsLine]'s, and the tag
 * `adb shell setprop log.tag.TaqwaWatch DEBUG` names to turn the step line on in a signed build
 * ([stepsTagEnabled]). The lines themselves reach logcat as `System.out`.
 */
internal const val WATCH_LOG_TAG = "TaqwaWatch"

/**
 * Whether [askStepsLine] is printed: in a debug build, or once [WATCH_LOG_TAG] is set to DEBUG on
 * the device, as plan 5's compass log is (`qiblaLogOn`), so P5.13's signed candidate can name a
 * slow step too (Ruling 70 item 1). Safe on a store build: the line names no place, person or
 * delivery (Rulings 39 and 52), and it is off until someone with the phone on adb asks for it.
 */
internal fun stepsLogOn(debugBuild: Boolean, tagEnabled: Boolean): Boolean = debugBuild || tagEnabled

/**
 * Whether [WATCH_LOG_TAG] is set to DEBUG on this device: on Android `Log.isLoggable`, which reads
 * `log.tag.TaqwaWatch` at each call, so a `setprop` reaches the next ask without a restart and
 * lasts until `setprop log.tag.TaqwaWatch INFO` or a reboot. The iPhone has no such switch: false,
 * so its line stays a debug binary's.
 */
internal expect fun stepsTagEnabled(): Boolean

/** [stepsLogOn] for this build and device, read at each ask. */
internal fun stepsLogged(): Boolean = stepsLogOn(isDebugBuild(), stepsTagEnabled())
