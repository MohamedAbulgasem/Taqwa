package world.taqwa.timetables

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.Prayer
import world.taqwa.app.i18n.CountdownDigits
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.engine.EngineDay
import world.taqwa.app.prayer.engine.registry.EntryClass
import world.taqwa.app.prayer.engine.registry.Resolution
import world.taqwa.app.qibla.QiblaMath
import java.io.File
import kotlin.time.Instant

/**
 * The one document the site is rendered from (spec §9.1). Per city: the facts (slug, country, zone,
 * the timetable — its registry id, class and unit — and Asr school the app's engine follows there,
 * the Qibla, the stamp's proof figures, and for every day of the months the page shows the instants
 * as epoch seconds for the page's live countdown, a cautious place's members' own days among them);
 * and per page language, every string the page shows about that city, already written the way the
 * app writes it (the About screen's sentences filled for the city). The site build adds only its
 * own sentences around these values, so it never formats a number or a date itself.
 *
 * [build] applies the proven rule (spec §2, [Proven]): a city the stamps do not prove every day of
 * its current month of is written under `held` with its reason, never as a page; a page carries its
 * current month, and the next only where every day of that is proven too (ruling R116) — else
 * `nextUnchecked` says which timetable leaves which day of it unchecked, and the next month is in
 * no part of the city's document. A page names its timetable in the app's own words, so [build]
 * also refuses a city whose timetable the app has no name for yet. No page names a high-latitude
 * rule: the engine's own rule has no name in the app (its days are still marked in the facts).
 */
class Document(
    private val strings: AppStrings,
    private val stamps: Map<String, Stamp>,
    private val official: File,
    private val timetable: Timetable = Timetable(),
) {

    fun build(cities: List<City>, now: Instant): Map<String, Any?> {
        val prepared = cities.map { prepare(it, now) }
        val published = prepared.filter { it.verdict is Verdict.Published }
        val built = published.map { city(it) }
        val unnamed = built.filter { city ->
            (city["pages"] as Map<*, *>).values.any { page -> (page as Map<*, *>)["method"] == null }
        }.map { it["slug"] }
        check(unnamed.isEmpty()) {
            "The app has no name yet for the timetable of ${unnamed.joinToString()}, so a page could not say " +
                "whose times it shows; hold these cities until it does"
        }
        return linkedMapOf(
            "generated" to now.toString(),
            "prayersArabic" to Prayer.entries.map { strings.prayer("ar", it) },
            "regions" to Regions.ORDER,
            "cities" to built,
            "held" to prepared.mapNotNull { p ->
                (p.verdict as? Verdict.Held)?.let { linkedMapOf("slug" to p.city.slug, "reason" to it.reason) }
            },
            "proof" to proof(published),
        )
    }

    /**
     * One city as [build] writes it, whatever its verdict: a held city carries no proof, and both
     * of its months (nothing of it is published; the tests read its pages).
     */
    fun city(city: City, now: Instant): Map<String, Any?> = city(prepare(city, now))

    /** What every part of a city's document reads: computed once, the verdict with it. */
    private class Prepared(
        val city: City,
        /** The months the page shows: the verdict's (ruling R116), or both for a held city. */
        val months: List<TimetableMonth>,
        val today: LocalDate,
        val source: EngineDay,
        val verdict: Verdict,
    ) {
        val effective: Resolution get() = source.effective
        val cautious: Boolean get() = effective.entryClass == EntryClass.C
        val published: Verdict.Published? get() = verdict as? Verdict.Published
    }

    private fun prepare(city: City, now: Instant): Prepared {
        val months = timetable.months(city, now)
        val today = timetable.localToday(city, now)
        val source = timetable.source(city, today)
        val place = PrayerTimesEngine.placeOf(timetable.location(city))
        val verdict = Proven.verdict(source.effective, place, stamps, months.map { it.days.first().date..it.days.last().date })
        // Only the proven months go any further: the months list, the days, the pages' texts and
        // everything the site builds from them, the live data included.
        val shown = (verdict as? Verdict.Published)?.let { months.take(it.months.size) } ?: months
        return Prepared(city, shown, today, source, verdict)
    }

    private fun city(p: Prepared): Map<String, Any?> {
        val city = p.city
        val days = p.months.flatMap { it.days }
        val location = timetable.location(city)
        val qibla = Qibla(QiblaMath.bearing(location), QiblaMath.distanceKm(location))
        val effective = p.effective
        return linkedMapOf(
            "slug" to city.slug,
            "id" to city.id,
            "country" to city.countryCode,
            "region" to city.region,
            "timeZone" to city.timeZone,
            "latitude" to city.latitude,
            "longitude" to city.longitude,
            "languages" to city.languages,
            "featured" to city.languages.filter { it in city.featured },
            "method" to p.source.effectiveEntry.id,
            "madhab" to p.source.school.name,
            "entryClass" to effective.entryClass.name,
            "measured" to effective.measured,
            "unitId" to effective.unitId,
            "unitName" to effective.unitName,
            "proof" to p.published?.let { v ->
                linkedMapOf(
                    "placeDays" to v.stamp.placeDays,
                    "places" to v.stamp.places,
                    "ramadanDays" to v.stamp.ramadanDays,
                    "first" to v.stamp.first.toString(),
                    "through" to v.through.toString(),
                    "atMost" to v.atMost,
                    "fajrShares" to v.stamp.shares(v.events, "fajr"),
                    "cautious" to p.cautious,
                )
            },
            "qibla" to linkedMapOf("bearing" to qibla.bearing, "km" to qibla.km),
            "today" to p.today.toString(),
            "months" to p.months.map { linkedMapOf("year" to it.year, "month" to it.month, "days" to it.days.size) },
            // Why a page shows its current month alone (ruling R116): the first timetable that
            // leaves a day of the next month unchecked, and that day. Null where both are shown.
            "nextUnchecked" to p.published?.nextUnchecked?.let { linkedMapOf("timetable" to it.timetable, "day" to it.day.toString()) },
            "days" to days.map { day ->
                linkedMapOf<String, Any?>(
                    "date" to day.date.toString(),
                    "friday" to day.friday,
                    "offset" to day.utcOffsetSeconds,
                    "highLatitude" to (day.setByRule.isNotEmpty() || day.polar),
                    "ramadanIsha" to day.ramadanIsha,
                    "epochs" to Prayer.entries.map { day.times.getValue(it).epochSeconds },
                    "asrOther" to day.asrOther.epochSeconds,
                    "endOfEating" to day.endOfEating.epochSeconds,
                    "sunset" to day.sunset.epochSeconds,
                    "imsak" to day.imsak?.epochSeconds,
                    "setByRule" to day.setByRule.map { it.ordinal }.sorted(),
                    "polar" to day.polar,
                ).also { facts ->
                    if (p.cautious) {
                        facts["members"] = day.members.map { member -> member.map { it.epochSeconds } }
                        facts["capped"] = day.capped
                    }
                }
            },
            "pages" to linkedMapOf(
                *city.languages.map { it to page(p, it, qibla) }.toTypedArray(),
            ),
        )
    }

    private data class Qibla(val bearing: Double, val km: Double)

    private fun page(p: Prepared, language: String, qibla: Qibla): Map<String, Any?> {
        val city = p.city
        val f = Formats(language, city.countryCode)
        val zone = TimeZone.of(city.timeZone)
        val days = p.months.flatMap { it.days }
        val today = p.today
        val todayRow = days.first { it.date == today }
        val prayers = Prayer.entries.map { strings.prayer(language, it) }
        fun clock(instant: Instant) = instant.toLocalDateTime(zone).let { f.clock(it.hour, it.minute) }

        // A change on the first day shown (a page built the morning the clocks went back) has no
        // day before it to compare with, so the clock at that day's midnight stands in.
        fun offsetBefore(i: Int): Int =
            if (i > 0) days[i - 1].utcOffsetSeconds else zone.offsetAt(days[0].date.atStartOfDayIn(zone)).totalSeconds
        val clockChanges = days.indices
            .filter { days[it].utcOffsetSeconds != offsetBefore(it) }
            .map { i ->
                linkedMapOf(
                    "index" to i,
                    "date" to f.longDate(days[i].date),
                    "offset" to f.utcOffset(days[i].utcOffsetSeconds),
                )
            }

        return linkedMapOf(
            "locale" to f.locale.toLanguageTag(),
            "digits" to f.digitSet(),
            "countdownDigits" to if (CountdownDigits.westernFallback(f.locale.toLanguageTag())) WESTERN else f.digitSet(),
            "city" to city.name(language),
            "country" to f.countryName(city.countryCode),
            "method" to strings.timetable(language, p.source.effectiveEntry),
            "madhab" to strings.school(language, p.source.school),
            "otherSchool" to strings.school(language, p.source.school.other),
            "members" to p.effective.members.map { strings.get(language, it.nameKey) },
            "prayers" to prayers,
            "nextIn" to prayers.map { strings.format(language, "today_next_in", it) },
            "jumuah" to strings.get(language, "today_jumuah"),
            "qibla" to strings.get(language, "qibla_title"),
            "qiblaDetail" to strings.format(
                language, "today_qibla_detail", f.digits(qibla.bearing.toInt()), f.distance(qibla.km),
            ),
            "bearing" to f.bearing(qibla.bearing),
            "distance" to f.distance(qibla.km),
            "offset" to f.utcOffset(todayRow.utcOffsetSeconds),
            "today" to linkedMapOf(
                "weekday" to f.weekday(today),
                "full" to f.fullDate(today),
                "date" to f.longDate(today),
                "hijri" to f.hijri(todayRow.hijri.year, todayRow.hijri.month, todayRow.hijri.day),
            ),
            "highLatitude" to emptyList<String>(),
            "clockChanges" to clockChanges,
            "strings" to sentences(p, language, f, todayRow),
            "months" to p.months.map { month ->
                linkedMapOf(
                    "title" to f.monthYear(month.year, month.month),
                    "hijri" to f.hijriSpan(month.days.map { it.hijri.year to it.hijri.month }.distinct()),
                )
            },
            "days" to days.map { day ->
                linkedMapOf<String, Any?>(
                    "day" to f.digits(day.date.day),
                    "weekday" to f.weekdayShort(day.date),
                    // The Today card's calendar leaf for this day, as `today.weekday` is for the
                    // day the page was built: the card shows it on the page's last evening, when
                    // the next prayer is on no day the page carries.
                    "weekdayLong" to f.weekday(day.date),
                    "date" to f.longDate(day.date),
                    "full" to f.fullDate(day.date),
                    "hijri" to f.hijriDayMonth(day.hijri.month, day.hijri.day),
                    "hijriLong" to f.hijri(day.hijri.year, day.hijri.month, day.hijri.day),
                    "times" to Prayer.entries.map { clock(day.times.getValue(it)) },
                    "asrOther" to clock(day.asrOther),
                    "endOfEating" to clock(day.endOfEating),
                    "imsak" to day.imsak?.let(::clock),
                    "setByRule" to day.setByRule.sortedBy { it.ordinal }.map { strings.prayer(language, it) },
                ).also { texts ->
                    if (p.cautious) texts["members"] = day.members.map { member -> member.map(::clock) }
                }
            },
        )
    }

    /**
     * The app's About sentences filled for this city (spec §3.6), exactly as `AboutTimesScreen`
     * fills them: the checked template's for class A and B, the cautious template's for class C.
     * A sentence a template does not say is "". A cautious place's proof sentence carries no date
     * (ruling R112: a cautious stamp's last date is one member's, not every member's) and its
     * second tile names the timetable that decides each start (ruling R111), as the app's do.
     */
    private fun sentences(p: Prepared, language: String, f: Formats, todayRow: TimetableDay): Map<String, Any?> {
        val effective = p.effective
        val cautious = p.cautious
        val stamp = p.published?.stamp
        val atMost = p.published?.atMost
        val authority = strings.timetable(language, effective.entry).orEmpty()
        val unitLabel = effective.unitName ?: p.city.name(language)
        val comma = MethodWords.listComma(language)
        val members = effective.members.map { strings.get(language, it.nameKey) }
        val through = p.published?.let { f.longDate(it.through) }
        fun checked(key: String, vararg args: String) = if (cautious) "" else strings.format(language, key, *args)
        return linkedMapOf(
            "whoseTitle" to if (cautious) strings.get(language, "timetable_cautious") else strings.format(language, "today_whose_checked_title", authority),
            "notAffiliated" to checked("about_not_affiliated", authority),
            "checkedThrough" to when {
                through == null -> ""
                cautious -> strings.get(language, "about_cautious_checked")
                else -> strings.format(language, "about_checked_through", authority, through)
            },
            "whoPublishes" to strings.get(language, "about_who_publishes"),
            "whoPublishesBody" to checked("about_who_publishes_body", authority, unitLabel),
            "howReproduces" to strings.get(language, "about_how_reproduces"),
            "methodIntro" to (effective.method?.let { method ->
                strings.format(language, "about_method_intro", authority, MethodWords.describe(strings, language, method, f))
            } ?: ""),
            "howChecked" to strings.get(language, "about_how_checked"),
            "statDaysValue" to (stamp?.let { f.digits(it.placeDays) } ?: ""),
            "statDays" to (stamp?.let { strings.format(language, "about_stat_days_at_places", f.digits(it.places)) } ?: ""),
            "statNever" to (if (stamp == null) "" else checked("about_stat_never_before", authority)),
            "statNeverAny" to (if (cautious && stamp != null) strings.get(language, "about_stat_never_before_decider") else ""),
            "statMinutes" to (atMost?.let { strings.format(language, "about_stat_minutes_value", f.digits(it)) } ?: ""),
            "statAtMost" to (if (atMost == null) "" else strings.get(language, "about_stat_at_most_after")),
            "cautiousBody" to (if (cautious) strings.format(language, "about_cautious_body", p.city.name(language), members.joinToString(comma)) else ""),
            "maghribCap" to (if (cautious) maghribCap(language, effective, todayRow, comma) else ""),
            "maghribCapTemplate" to (if (cautious) strings.get(language, "about_cautious_maghrib_cap") else ""),
            "whichDecides" to strings.get(language, "about_which_decides"),
            "matchMosque" to strings.get(language, "timetable_match_mosque"),
            "setByRule" to strings.get(language, "today_set_by_rule"),
            "polarLine" to strings.get(language, "today_polar_line"),
            "stopEating" to strings.get(language, "about_stop_eating").replace("%1\$s", "{time}"),
        )
    }

    /**
     * Ruling R91 (the app's `maghribCapToday`): where the Maghrib cap decided the day's Maghrib —
     * a member's own is later than the one shown — the sentence naming the member followed (the
     * first whose Maghrib is the one shown, else the most-followed) and the later ones; else "".
     */
    private fun maghribCap(language: String, effective: Resolution, day: TimetableDay, comma: String): String {
        val shown = day.times.getValue(Prayer.MAGHRIB)
        val members = effective.members
        fun name(i: Int) = strings.get(language, members[i].nameKey)
        val later = members.indices.filter { day.members[it][MEMBER_MAGHRIB] > shown }
        if (later.isEmpty()) return ""
        val followed = members.indices.firstOrNull { day.members[it][MEMBER_MAGHRIB] == shown }
            ?: members.indices.minBy { members[it].shareRank }
        return strings.format(language, "about_cautious_maghrib_cap", name(followed), later.joinToString(comma) { name(it) })
    }

    /**
     * The "How Taqwa checks" page's figures (spec §5): the totals over every stamp, the gate's rows
     * and the surveys' calendars read from the files, and one row per published timetable, with
     * the figures its published cities' own pages show: checked through the earliest of their
     * dates (ruling R115), at most the worst over their units — nothing for a cautious entry
     * (ruling R105), never the entry-wide worst of a place no page shows.
     */
    private fun proof(published: List<Prepared>): Map<String, Any?> {
        val all = stamps.values
        val rows = published.groupBy { it.effective.entry.id }.toSortedMap().map { (id, cities) ->
            val first = cities.first()
            val stamp = stamps.getValue(id)
            val entry = first.effective.entry
            val verdicts = cities.map { it.published!! }
            val through = verdicts.minOf { it.through }
            linkedMapOf(
                "entry" to id,
                "class" to first.effective.entryClass.name,
                "placeDays" to stamp.placeDays,
                "places" to stamp.places,
                "first" to stamp.first.toString(),
                "through" to through.toString(),
                "atMost" to if (first.cautious) null else verdicts.mapNotNull { it.atMost }.maxOrNull(),
                "names" to SITE_LANGUAGES.associateWith { lang ->
                    if (first.cautious) strings.get(lang, "timetable_cautious") else strings.timetable(lang, entry).orEmpty()
                },
                // The checks page has no country: one date form per language (ruling R114).
                "throughText" to SITE_LANGUAGES.associateWith { lang -> Formats.forLanguage(lang).longDate(through) },
            )
        }
        return linkedMapOf(
            "entries" to all.size,
            "placeDays" to all.sumOf { it.placeDays },
            "heldOutDays" to all.sumOf { it.heldOutDays },
            "ramadanDays" to all.sumOf { it.ramadanDays },
            "earlyStarts" to all.sumOf { it.early() },
            "lateEnds" to all.sumOf { it.lateEnds() },
            "brokenStamps" to all.count { it.broken != 0 },
            "tables" to ProofTotals.gateRows(official),
            "surveyCalendars" to ProofTotals.surveyCalendars(official),
            "published" to rows,
        )
    }

    private companion object {
        const val WESTERN = "0123456789"
    }
}
