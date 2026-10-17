package world.taqwa.app.settings

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone

/**
 * A stored zone id as one this phone can parse. `TimeZone.of` throws for a zone the phone's time
 * zone data does not have, and every reader of the location parses its zone, the prayer engine
 * included, so a city in such a zone crashed the app on every launch.
 *
 * The phones that lack zones are the old ones. Android 9 ships tzdata 2018e and Android 8 an older
 * release still. Before Android 10 the data changes only when the phone's maker updates it: by a
 * system update, or on 8.1 and 9 by a time zone data app the maker can publish through an app
 * store. From Android 10 it comes as a Google Play system update. The city list has five zones
 * that 2018e does not: Kyiv (renamed from Kiev
 * in 2022b), Qostanay (split off in 2018h), Nuuk (renamed from Godthab in 2020a), Ciudad Juárez
 * (split off in 2022g) and Coyhaique (split off in 2025b, so it is missing from newer phones too
 * until their data is updated).
 *
 * [legacyNames] gives each of those a zone the 2018 data has whose clocks agree with the place's
 * own from today on: Kiev is Kyiv under its old name, Aqtobe has been on Qostanay's +05 since
 * 2024, Ojinaga is the 2018 data's zone for the Chihuahua border, and Punta Arenas has been on
 * Coyhaique's −03 since 2025. Nuuk has no such zone: Greenland's clocks moved an hour in 2023,
 * and the 2018 data has no zone on −02 with Europe's summer time, so Godthab would be an hour off
 * all year. A zone with no old name, an old name this phone lacks too (Android 8's data is older
 * than 2018e), and any other id this phone cannot parse read as the phone's own zone, the clock the
 * phone itself is showing.
 *
 * The stored id is left as picked ([world.taqwa.app.domain.GeoLocation.pickedTimeZoneId]), so a
 * phone whose data is updated later reads the real zone. The Location screen names the picked zone
 * while its old name stands in for it, on the same clock, and the zone in use when the times have
 * fallen back to the phone's own ([named]).
 */
internal object ZoneIds {

    val legacyNames: Map<String, String> = mapOf(
        "Europe/Kyiv" to "Europe/Kiev",
        "Asia/Qostanay" to "Asia/Aqtobe",
        "America/Ciudad_Juarez" to "America/Ojinaga",
        "America/Coyhaique" to "America/Punta_Arenas",
    )

    /** [id] if [known], else its old name if that is [known], else [fallback]. */
    fun resolvable(
        id: String,
        known: (String) -> Boolean = ::parses,
        fallback: () -> String = { TimeZone.currentSystemDefault().id },
    ): String {
        if (known(id)) return id
        val old = legacyNames[id]
        return if (old != null && known(old)) old else fallback()
    }

    /**
     * The zone the Location screen names for a location drawn in [inUse] and stored as [picked]:
     * [picked] while [inUse] is it or its old name, which keep the same clock, and [inUse]
     * otherwise. A zone read as the phone's own is on that zone's clock, and the row is the clue
     * the screen offers when the times look hours wrong, so it names the clock they are on.
     */
    fun named(inUse: String, picked: String): String =
        if (inUse == picked || inUse == legacyNames[picked]) picked else inUse

    private fun parses(id: String): Boolean = try {
        TimeZone.of(id)
        true
    } catch (_: IllegalTimeZoneException) {
        false
    }
}
