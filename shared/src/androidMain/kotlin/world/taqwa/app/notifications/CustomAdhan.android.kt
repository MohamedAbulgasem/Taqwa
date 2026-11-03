package world.taqwa.app.notifications

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.taqwa.app.settings.appContext
import java.io.File

/**
 * The chosen recording lives at one fixed path in the app's own files, so the Adhan channel's
 * sound URI never changes: choosing another recording replaces the file, and the channel plays
 * the new one without a new channel id. Its name, length and type sit in a small preference file
 * beside it.
 */
internal object CustomAdhanFiles {
    const val PATH = "/adhan"
    private const val PREFS = "custom_adhan"

    /** Large enough for any adhan, small enough that a wrongly picked album is refused. */
    private const val MAX_BYTES = 40L * 1024 * 1024

    fun file(context: Context): File = File(context.filesDir, "custom_adhan/adhan")

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.adhan$PATH")

    fun present(context: Context): Boolean = file(context).length() > 0

    fun mime(context: Context): String = prefs(context).getString("mime", null) ?: "audio/*"

    fun info(context: Context): CustomAdhanInfo? {
        if (!present(context)) return null
        val p = prefs(context)
        return CustomAdhanInfo(p.getString("name", null) ?: "adhan", p.getLong("duration", 0))
    }

    fun remove(context: Context) {
        file(context).delete()
        prefs(context).edit().clear().apply()
    }

    /** Copies [source] in, or returns null if it is too large or does not read as audio. */
    fun copyIn(context: Context, source: Uri): CustomAdhanInfo? = runCatching {
        val resolver = context.contentResolver
        val name = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "adhan"
        val target = file(context).apply { parentFile?.mkdirs() }
        val staging = File(target.parentFile, "adhan.part")
        resolver.openInputStream(source)?.use { input ->
            staging.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) error("too large")
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("unreadable")
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(staging.path)
            check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } finally {
            retriever.release()
        }
        check(durationMs > 0)
        check(staging.renameTo(target) || (staging.copyTo(target, overwrite = true).exists() && staging.delete()))
        prefs(context).edit()
            .putString("name", name)
            .putLong("duration", durationMs)
            .putString("mime", resolver.getType(source) ?: "audio/*")
            .apply()
        CustomAdhanInfo(name, durationMs)
    }.getOrElse {
        File(file(context).parentFile, "adhan.part").delete()
        null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

actual val customAdhanSupported: Boolean = true

actual fun currentCustomAdhan(): CustomAdhanInfo? = CustomAdhanFiles.info(appContext)

actual fun removeCustomAdhan() = CustomAdhanFiles.remove(appContext)

@Composable
actual fun rememberCustomAdhanPicker(onPicked: (CustomAdhanInfo?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            onPicked(withContext(Dispatchers.IO) { CustomAdhanFiles.copyIn(appContext, uri) })
        }
    }
    return { launcher.launch(arrayOf("audio/*")) }
}
