package world.taqwa.app.settings

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone

/**
 * A stored zone id as one this phone can parse. `TimeZone.of` throws for a zone the phone's time
 * zone data does not have, and every reader of the location parses its zone, the prayer engine
 * included, so a city in such a zone crashed the app on every launch.
 *
 * The phones that lack zones are the old ones. Android 9 ships tzdata 2018e and Android 8 an older
 * release still, and before Android 10 the data is updated only by the phone's maker, never
 * through Google Play. The city list has five zones that 2018e does not: Kyiv (renamed from Kiev
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
 * The stored id is left as picked, and the Location screen names it
 * ([world.taqwa.app.domain.GeoLocation.pickedTimeZoneId]), so the stand-in is for the arithmetic
 * only and a phone whose data is updated later reads the real zone.
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

    private fun parses(id: String): Boolean = try {
        TimeZone.of(id)
        true
    } catch (_: IllegalTimeZoneException) {
        false
    }
}
