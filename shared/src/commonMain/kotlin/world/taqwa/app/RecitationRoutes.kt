package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.taqwa.app.di.AppContainer
import world.taqwa.app.feature.recitation.RecitationController
import world.taqwa.app.feature.recitation.RecitationState
import world.taqwa.app.feature.recitation.RecitationStorage
import world.taqwa.app.feature.recitation.reciterName
import world.taqwa.app.feature.settings.DownloadedSurah
import world.taqwa.app.feature.settings.RecitationDownloadsScreen
import world.taqwa.app.feature.settings.RecitationSettingsScreen
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen
import world.taqwa.app.quran.displayName

@Composable
internal fun RecitationSettingsRoute(
    recitationStateState: State<RecitationState>,
    recitation: RecitationController,
    navigator: Navigator,
) {
    val recitationState by recitationStateState
    // Off the disk, so it is asked for rather than observed: the scan
    // stats every downloaded file. Re-read whenever the registry moves —
    // a download committing, a delete — which is exactly when the number
    // on screen would otherwise be wrong.
    val byReciter = recitationState.downloadedByReciter
    val storage by produceState(RecitationStorage(), byReciter) {
        value = withContext(Dispatchers.Default) { recitation.storage() }
    }
    RecitationSettingsScreen(
        state = recitationState,
        storage = storage,
        onBack = { navigator.pop() },
        onOpened = recitation::onSettingsOpened,
        onOpenPicker = recitation::openPicker,
        onSetMobileData = recitation::setDownloadOnMobileData,
        onSetAutoDownload = recitation::setAutoDownload,
        onOpenDownloads = { navigator.push(Screen.RecitationDownloads(it)) },
        onDownloadWholeQuran = { recitation.downloadWholeQuran() },
        onCancelWholeQuran = recitation::cancelWholeQuran,
    )
}

@Composable
internal fun RecitationDownloadsRoute(
    screen: Screen.RecitationDownloads,
    recitationStateState: State<RecitationState>,
    arabicUi: Boolean,
    container: AppContainer,
    recitation: RecitationController,
    navigator: Navigator,
    scope: CoroutineScope,
) {
    val recitationState by recitationStateState
    val reciter = recitationState.reciters.firstOrNull { it.id == screen.reciterId }
    val owned = recitationState.downloadedByReciter[screen.reciterId].orEmpty()
    // The rows are the intersection of the registry and the catalogue: a
    // size can only come from the manifest, and a surah on the phone that
    // this manifest no longer publishes has no size to print.
    val rows by produceState(emptyList<DownloadedSurah>(), reciter, owned, arabicUi) {
        val voice = reciter
        value = if (voice == null) {
            emptyList()
        } else {
            runCatching {
                val names = container.quranRepository.surahs()
                    .associate { it.number to it.displayName(arabicUi) }
                voice.surahs
                    .filter { it.n in owned }
                    .sortedBy { it.n }
                    .map { DownloadedSurah(it.n, names[it.n].orEmpty(), it.bytes) }
            }.getOrDefault(emptyList())
        }
    }
    val bytes by produceState(0L, screen.reciterId, owned) {
        value = withContext(Dispatchers.Default) {
            recitation.storage().of(screen.reciterId)
        }
    }
    // Popped when the last surah goes, rather than left on an empty
    // screen whose title names a reciter with nothing under it.
    LaunchedEffect(reciter, owned) {
        if (reciter != null && owned.isEmpty()) navigator.pop()
    }
    RecitationDownloadsScreen(
        reciterName = reciter?.let { reciterName(it) }.orEmpty(),
        surahs = rows,
        totalBytes = bytes,
        onBack = { navigator.pop() },
        onDelete = { surah ->
            scope.launch { recitation.deleteSurah(screen.reciterId, surah) }
        },
        onDeleteAll = {
            scope.launch { recitation.deleteReciter(screen.reciterId) }
        },
    )
}
