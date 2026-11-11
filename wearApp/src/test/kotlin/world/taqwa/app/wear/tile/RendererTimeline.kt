package world.taqwa.app.wear.tile

import androidx.wear.protolayout.proto.TimelineProto

/**
 * Which entry of [timeline] the Tiles renderer shows at [millis], by its own rule
 * (`TilesTimelineCacheInternal.findTimelineEntryForTime`, androidx.wear.tiles:tiles-renderer): of
 * the entries whose validity holds the moment, start ≤ millis < end, the shortest; an entry with no
 * validity is the default, shown only while none holds it. When neither holds it, the renderer
 * falls back to the entry closest in time (`findClosestTimelineEntry`), which shows whatever was
 * last true and is never what the timeline meant: that is null here, so that a test sees it.
 */
internal fun shownAt(timeline: TimelineProto.Timeline, millis: Long): Int? {
    var shown: Int? = null
    var shownLength = Long.MAX_VALUE
    for ((i, entry) in timeline.timelineEntriesList.withIndex()) {
        if (!entry.hasValidity()) {
            if (shownLength == Long.MAX_VALUE) shown = i
            continue
        }
        val validity = entry.validity
        val length = validity.endMillis - validity.startMillis
        if (length > shownLength) continue
        if (validity.startMillis <= millis && millis < validity.endMillis) {
            shown = i
            shownLength = length
        }
    }
    return shown
}
