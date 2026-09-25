package world.taqwa.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import world.taqwa.app.di.appContainer
import world.taqwa.app.recitation.DownloadKey
import world.taqwa.app.recitation.RecitationManifest

/**
 * A hand for the downloader while slice 3a has no UI (debug builds only).
 *
 * Slice 3a task 2 builds downloads and no screen to start one from, so this is what a device test
 * drives them with. It is kept rather than deleted because the same commands are how a download
 * bug gets reproduced later, once there *is* a UI and the UI is the suspect.
 *
 * ```
 * adb shell am broadcast -a world.taqwa.app.DEBUG_RECITATION -n world.taqwa.app/world.taqwa.app.debug.RecitationDebugReceiver \
 *   --es op download --ei surah 112 [--es reciter ar.alafasy] [--ez mobile true]
 *   --es op cancel   --ei surah 112
 *   --es op retry    --ei surah 112
 *   --es op reciter                                   (the whole-Quran batch)
 *   --es op manifest --es url http://10.0.2.2:8765/manifest.json
 *   --es op states
 *   --es op library
 * ```
 *
 * Everything it prints goes to logcat under [TAG].
 */
class RecitationDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val op = intent.getStringExtra("op") ?: "states"
        val reciter = intent.getStringExtra("reciter") ?: RecitationManifest.DEFAULT_RECITER
        val surah = intent.getIntExtra("surah", 1)
        val mobile = intent.getBooleanExtra("mobile", false)
        val key = DownloadKey(reciter, surah)
        val downloader = appContainer.surahDownloader
        Log.i(TAG, "op=$op reciter=$reciter surah=$surah mobile=$mobile")

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (op) {
                    "download" -> downloader.enqueue(key, allowMobileOnce = mobile)
                    "cancel" -> downloader.cancel(key)
                    "retry" -> downloader.retry(key)
                    "reciter" -> downloader.enqueueReciter(reciter, allowMobileOnce = mobile)
                    "cancelReciter" -> downloader.cancelReciter(reciter)
                    "manifest" -> {
                        val url = intent.getStringExtra("url") ?: return@launch
                        val bytes = world.taqwa.app.recitation.httpGet(url)
                        val stored = bytes != null && appContainer.manifestProvider.store(bytes)
                        Log.i(TAG, "manifest fetched=${bytes?.size} stored=$stored")
                        Log.i(TAG, "manifest base=${appContainer.manifestProvider.current().base}")
                    }
                    "states" -> Unit
                    "library" -> Unit
                    else -> Log.w(TAG, "unknown op $op")
                }
                Log.i(TAG, "states=${downloader.states.value}")
                Log.i(TAG, "library ${reciter}=${appContainer.recitationLibrary.downloaded(reciter).first()}")
                Log.i(TAG, "bytesUsed=${appContainer.recitationLibrary.bytesUsed(reciter)}")
            } catch (e: Throwable) {
                Log.e(TAG, "debug op $op failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TaqwaDebug"
    }
}
