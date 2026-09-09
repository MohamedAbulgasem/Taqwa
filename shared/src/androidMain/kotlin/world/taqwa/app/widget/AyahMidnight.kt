package world.taqwa.app.widget

import java.time.Instant
import java.time.ZoneId

/**
 * The ayah widget's day boundary: the first instant of tomorrow, in a given zone.
 *
 * The ayah rotates on the local *date* (design spec §5), so the widget's daily redraw has to be
 * armed for local midnight rather than "now plus 24 hours" — otherwise it drifts an hour every
 * daylight-saving change and a whole day's worth after a flight.
 *
 * It lives here, in `shared`, rather than beside `AyahWidgetScheduler` in `androidApp`, purely so
 * it can be tested: `androidApp` is an application module with no unit-test source set, and this
 * is the one part of the scheduler that can be wrong in a way a device test would not catch
 * quickly. `AyahWidgetScheduler.nextMidnight` is a one-line call through to it. Nothing here
 * touches an Android class — `java.time` alone — so the test loads no framework stub.
 */
object AyahMidnight {

    /**
     * The first instant of the day after [nowMillis]'s local date in [zone], as epoch millis.
     *
     * `atStartOfDay(zone)` rather than `atTime(MIDNIGHT).atZone(zone)`: in zones that begin their
     * summer time at midnight (America/Santiago, Asia/Beirut) the local time 00:00 does not exist
     * on the transition date, and `atStartOfDay` returns the first instant that *does* — 01:00 —
     * where the naive form would silently push the alarm an hour into the previous day.
     *
     * Always strictly ahead of [nowMillis], including on a 23-hour spring-forward day and on an
     * instant that is itself exactly midnight.
     */
    fun nextMidnight(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
}
