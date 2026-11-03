@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package world.taqwa.app.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

/**
 * iOS keeps two copies of the chosen recording: the whole file, which the sheet's play button
 * plays, and a notification sound in Library/Sounds — where `UNNotificationSound(named:)` looks
 * — cut to 29.5 seconds with a half-second fade, because iOS stops a notification sound at 30
 * and plays nothing at all from a longer file. The sound is linear PCM, one of the formats a
 * notification sound may use; a compressed file the user picks is decoded into it.
 */
internal object IosCustomAdhanFiles {
    const val SOUND_NAME = "custom-adhan.caf"
    private const val NAME_KEY = "customAdhanName"
    private const val DURATION_KEY = "customAdhanDurationMs"
    private const val EXT_KEY = "customAdhanExtension"
    private const val CAP_SECONDS = 29.5
    private const val FADE_SECONDS = 0.5

    private val defaults get() = NSUserDefaults.standardUserDefaults
    private val files get() = NSFileManager.defaultManager

    private fun library(): String = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true).first() as String

    private fun soundPath(): String = "${library()}/Sounds/$SOUND_NAME"

    fun fullPath(): String? = defaults.stringForKey(EXT_KEY)?.let { "${library()}/CustomAdhan/adhan.$it" }

    fun soundPresent(): Boolean = files.fileExistsAtPath(soundPath())

    fun info(): CustomAdhanInfo? {
        val full = fullPath() ?: return null
        if (!files.fileExistsAtPath(full) || !soundPresent()) return null
        return CustomAdhanInfo(defaults.stringForKey(NAME_KEY) ?: "adhan", defaults.integerForKey(DURATION_KEY))
    }

    fun remove() {
        fullPath()?.let { files.removeItemAtPath(it, null) }
        files.removeItemAtPath(soundPath(), null)
        listOf(NAME_KEY, DURATION_KEY, EXT_KEY).forEach { defaults.removeObjectForKey(it) }
    }

    /** Copies [source] in and writes the notification sound, or returns null if it is not audio. */
    @OptIn(NativeRuntimeApi::class)
    fun copyIn(source: NSURL): CustomAdhanInfo? {
        val ext = (source.pathExtension ?: "").ifEmpty { "m4a" }.lowercase()
        val name = source.lastPathComponent ?: "adhan.$ext"
        val fullDir = "${library()}/CustomAdhan"
        val soundsDir = "${library()}/Sounds"
        files.createDirectoryAtPath(fullDir, true, null, null)
        files.createDirectoryAtPath(soundsDir, true, null, null)
        val full = "$fullDir/adhan.$ext"
        val staging = "$fullDir/staging.$ext"
        files.removeItemAtPath(staging, null)
        if (!files.copyItemAtURL(source, NSURL.fileURLWithPath(staging), null)) return null

        val durationMs = writeNotificationSound(staging, "$soundsDir/staging-$SOUND_NAME")
        // AVAudioFile closes only when it is released; collecting here finishes both files before
        // they are moved into place.
        GC.collect()
        if (durationMs == null || durationMs <= 0) {
            files.removeItemAtPath(staging, null)
            files.removeItemAtPath("$soundsDir/staging-$SOUND_NAME", null)
            return null
        }
        fullPath()?.let { files.removeItemAtPath(it, null) }
        files.removeItemAtPath(soundPath(), null)
        files.moveItemAtPath(staging, full, null)
        files.moveItemAtPath("$soundsDir/staging-$SOUND_NAME", soundPath(), null)
        defaults.setObject(name, NAME_KEY)
        defaults.setInteger(durationMs, DURATION_KEY)
        defaults.setObject(ext, EXT_KEY)
        return CustomAdhanInfo(name, durationMs)
    }

    /** Decodes [from] into a 29.5 s PCM `.caf` at [to] with a fade; returns the source's length. */
    private fun writeNotificationSound(from: String, to: String): Long? {
        val reader = AVAudioFile(forReading = NSURL.fileURLWithPath(from), error = null) ?: return null
        val format = reader.processingFormat
        val rate = format.sampleRate
        if (rate <= 0.0 || reader.length <= 0) return null
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatLinearPCM,
            AVSampleRateKey to rate,
            AVNumberOfChannelsKey to format.channelCount,
            AVLinearPCMBitDepthKey to 16,
            AVLinearPCMIsFloatKey to false,
            AVLinearPCMIsBigEndianKey to false,
        )
        files.removeItemAtPath(to, null)
        val writer = AVAudioFile(
            forWriting = NSURL.fileURLWithPath(to),
            settings = settings,
            commonFormat = AVAudioPCMFormatFloat32,
            interleaved = false,
            error = null,
        ) ?: return null
        val total = minOf(reader.length, (CAP_SECONDS * rate).toLong())
        val fadeFrom = total - (FADE_SECONDS * rate).toLong()
        val buffer = AVAudioPCMBuffer(format, 8192u) ?: return null
        var written = 0L
        while (written < total) {
            if (!reader.readIntoBuffer(buffer, error = null)) break
            var frames = buffer.frameLength.toLong()
            if (frames == 0L) break
            if (written + frames > total) {
                frames = total - written
                buffer.frameLength = frames.toUInt()
            }
            val channels = buffer.floatChannelData
            if (channels != null && written + frames > fadeFrom) {
                for (c in 0 until format.channelCount.toInt()) {
                    val samples = channels[c] ?: continue
                    for (i in 0 until frames.toInt()) {
                        val at = written + i
                        if (at >= fadeFrom) samples[i] = samples[i] * ((total - at).toFloat() / (total - fadeFrom).toFloat())
                    }
                }
            }
            if (!writer.writeFromBuffer(buffer, error = null)) return null
            written += frames
        }
        // iOS 18 added an explicit close; earlier releases close on release (the GC above).
        // (performSelector would read close's void return as an object and crash.)
        if (writer.respondsToSelector(NSSelectorFromString("close"))) writer.close()
        return (reader.length.toDouble() / rate * 1000).toLong()
    }
}

actual val customAdhanSupported: Boolean = true

actual fun currentCustomAdhan(): CustomAdhanInfo? = IosCustomAdhanFiles.info()

actual fun removeCustomAdhan() = IosCustomAdhanFiles.remove()

private class PickerDelegate(val onPicked: (CustomAdhanInfo?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        onPicked(IosCustomAdhanFiles.copyIn(url))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

@Composable
actual fun rememberCustomAdhanPicker(onPicked: (CustomAdhanInfo?) -> Unit): () -> Unit {
    // The picker holds its delegate weakly, so the composition keeps it alive.
    val delegate = remember { PickerDelegate { onPicked(it) } }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio), asCopy = true)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIWindowScene }
    val window = scenes.flatMap { scene -> scene.windows.mapNotNull { it as? UIWindow } }.firstOrNull { it.isKeyWindow() }
        ?: scenes.firstOrNull()?.windows?.firstOrNull() as? UIWindow
    var top = window?.rootViewController ?: return null
    while (top.presentedViewController != null) top = top.presentedViewController!!
    return top
}
