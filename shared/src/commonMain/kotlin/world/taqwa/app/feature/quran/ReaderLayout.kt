package world.taqwa.app.feature.quran

/**
 * Maps [ReaderScreen]'s `LazyColumn.firstVisibleItemIndex` to the index into
 * [ReaderUiState.Ready.ayahs] it corresponds to. Item 0 is the basmala when [hasBasmala], so every
 * other item shifts down by one — but the basmala item itself must still resolve to ayah 1 (index
 * 0), not -1: without the floor, scrolling to the very top of a surah with a basmala reported an
 * out-of-range index, [ReaderUiState.Ready.ayahs.getOrNull] returned null, and
 * [ReaderViewModel.onFirstVisibleAyah] was never called — so opening a surah and leaving right away
 * never wrote ayah 1 as the last-read position.
 */
internal fun firstVisibleAyahIndex(firstVisibleItemIndex: Int, hasBasmala: Boolean): Int =
    (if (hasBasmala) firstVisibleItemIndex - 1 else firstVisibleItemIndex).coerceAtLeast(0)
