package world.taqwa.app.watch

/**
 * The feeds the tests run the watch rules on. [capeTown] is the same data as the mockups
 * (`docs/superpowers/specs/2026-10-19-watch-companion/`): Cape Town from Sunday 18 October 2026,
 * 6 Jumada al-Ula 1448, with the times the mockups draw. [capeTownSample] is that data as the phone
 * is to send it, from yesterday, and its JSON is the file committed as
 * `vectors/sample-feed-capetown.json`, the feed the Wear app's debug build starts with
 * (`VectorsTest` keeps the two in step).
 *
 * The times are the mockups' own, not the engine's, and every day repeats them on its own date.
 * [tromsoMay] and [lateMaghribNight] are the feeds where the engine's shape matters more than its
 * figures.
 */
object Fixtures {

    /** Sunday 18 October 2026, 00:00 in Cape Town (UTC+2 all year), in epoch seconds: day 0 of [capeTown]. */
    const val DAY_ZERO_MIDNIGHT: Long = 1_792_274_400L

    /** Sunday 17 May 2026, 00:00 in Tromsø (UTC+2 in summer), in epoch seconds: day 0 of [tromsoMay]. */
    const val TROMSO_DAY_ZERO_MIDNIGHT: Long = 1_778_968_800L

    private const val DAY = 86_400L

    /** U+00A0 NO-BREAK SPACE: the app writes one between a 12-hour time and its day period. */
    private const val NBSP = '\u00A0'

    /**
     * The words of the watch's screens as an Arabic phone sends them. Each is the phone's own
     * string in `shared/src/commonMain/composeResources/values-ar/strings.xml`, under the key plan 3
     * Task 10's table reads it from. The tasbeeh's spoken phrases and the turns are accessibility
     * round 2's Arabic (its string table), the turns as the runs the phone makes of a plural asked
     * at 15, 30, … 180 degrees: درجة throughout but at 105, CLDR's "few", which takes درجات. The
     * new phrases, `openOnPhone`, `updateOnWatch`, `bezelHint`, `crownHint`, `undoAction`,
     * `phrasesAction` and `holdLevel`, and the dial's short forms `distanceOnDial` and
     * `qiblaDetailOnDial` (Ruling 66 item 1), are as Mohamed read and approved them at M8.
     * `appName` is `values-ar`'s `app_name` (plan 4 Task 3).
     */
    /** The face screen's English (plan 5 Task 5), as the phone's `WatchWords.face` writes it. */
    val ENGLISH_FACE: WatchFaceWords = WatchFaceWords(
        add = "Add the Taqwa face",
        about = "The next prayer and its countdown on your watch face.",
        active = "Taqwa is your watch face.",
        chooseIt = "Touch and hold your watch face, then choose Taqwa.",
        failed = "Couldn’t add the face. Try again later.",
    )

    /** And its Arabic, as the phone writes it. */
    val ARABIC_FACE: WatchFaceWords = WatchFaceWords(
        add = "أضِف واجهة تقوى",
        about = "الصلاة القادمة وعدّها التنازلي على واجهة ساعتك.",
        active = "تقوى هي واجهة ساعتك.",
        chooseIt = "المس واجهة ساعتك مطوّلًا، ثم اختر تقوى.",
        failed = "تعذّرت إضافة الواجهة. حاول مجددًا لاحقًا.",
    )

    val ARABIC_SCREENS: WatchScreenWords = WatchScreenWords(
        today = "اليوم",
        tasbeeh = "التسبيح",
        qibla = "القبلة",
        appName = "تقوى",
        openOnPhone = "افتح تقوى على هاتفك",
        updateOnWatch = "حدّث تقوى على ساعتك",
        rowCurrent = "الصلاة الحالية",
        rowPassed = "انقضت",
        rowNext = "الصلاة التالية",
        round = "الجولة %1\$s",
        of = "من %1\$s",
        bezelHint = "أدِر الإطار أو اضغط",
        crownHint = "أدِر التاج أو اضغط",
        reset = "إعادة العدّ",
        resetConfirm = "إعادة العدّ لهذا الذكر؟",
        presets = linkedMapOf(
            "after_prayer" to "بعد الصلاة",
            "subhanallah" to "سبحان الله",
            "alhamdulillah" to "الحمد لله",
            "allahu_akbar" to "الله أكبر",
            "astaghfirullah" to "أستغفر الله",
            "la_ilaha_illallah" to "لا إله إلا الله",
            "subhanallahi_wa_bihamdihi" to "سبحان الله وبحمده",
        ),
        countSpoken = "%1\$s من %2\$s",
        nextSpoken = "%1\$s. التالي: %2\$s",
        setCompleteSpoken = "%1\$s. اكتملت الجولة",
        roundSpoken = "اكتملت الجولة. %1\$s، الجولة %2\$s",
        nameRoundSpoken = "%1\$s، الجولة %2\$s",
        countAction = "عُدّ",
        undoAction = "أنقِص واحدة",
        phrasesAction = "اختر ذكرًا",
        facingQibla = "أنت باتجاه القبلة",
        distance = "مكة · على بُعد %1\$s كم",
        bearing = "%1\$s°",
        qiblaDetail = "%1\$s° · %2\$s كم إلى مكة",
        distanceOnDial = "%1\$s كم",
        qiblaDetailOnDial = "%1\$s° · %2\$s كم",
        cardinals = listOf("ش", "ق", "ج", "غ"),
        holdLevel = "أبقِ معصمك مستويًا",
        needsCalibrating = "البوصلة تحتاج إلى معايرة",
        interference = "تداخل مغناطيسي",
        bestEffort = "البوصلة غير متاحة",
        noSensor = "لا توجد بوصلة في هذا الجهاز",
        turnRight = listOf(
            WatchCount(90, "استدر يمينًا حوالي %1\$s درجة"),
            WatchCount(105, "استدر يمينًا حوالي %1\$s درجات"),
            WatchCount(180, "استدر يمينًا حوالي %1\$s درجة"),
        ),
        turnLeft = listOf(
            WatchCount(90, "استدر يسارًا حوالي %1\$s درجة"),
            WatchCount(105, "استدر يسارًا حوالي %1\$s درجات"),
            WatchCount(180, "استدر يسارًا حوالي %1\$s درجة"),
        ),
    )

    /**
     * [hour]:[minute]:[second] on [day] of [capeTown], Cape Town time, as the epoch seconds a watch
     * would read it as. The tests give the rules their `now` this way.
     */
    fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        DAY_ZERO_MIDNIGHT + day * DAY + hour * 3_600L + minute * 60L + second

    /** The same, on [day] of [tromsoMay]. A time past 24:00 is the next date's. */
    fun atTromso(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        TROMSO_DAY_ZERO_MIDNIGHT + day * DAY + hour * 3_600L + minute * 60L + second

    /** English, a 24-hour clock, Western digits, with the English words of the screens: the phone as
     * the mockups show it by default. */
    fun english24(): WatchUi = WatchUi(
        language = "en",
        rtl = false,
        digitZero = '0',
        twelveHour = false,
        am = "AM",
        pm = "PM",
        countdown = WatchCountdownPhrases(
            hoursMinutes = "in %1\$s h %2\$s min",
            hours = "in %1\$s h",
            minutes = "in %1\$s min",
            underAMinute = "in under a minute",
            compactHoursMinutes = "in %1\$sh %2\$sm",
            compactHours = "in %1\$sh",
            compactMinutes = "in %1\$sm",
        ),
        nextIn = "%1\$s in",
        spoken = WatchSpoken(
            countdown = "%1\$s in %2\$s, at %3\$s",
            countdownSoon = "%1\$s in under a minute, at %2\$s",
            hoursMinutes = "%1\$s %2\$s",
            hours = listOf(WatchCount(0, "%1\$s hours"), WatchCount(1, "%1\$s hour"), WatchCount(99, "%1\$s hours")),
            minutes = listOf(WatchCount(0, "%1\$s minutes"), WatchCount(1, "%1\$s minute"), WatchCount(99, "%1\$s minutes")),
        ),
        screens = WatchScreenWords.ENGLISH,
        face = ENGLISH_FACE,
    )

    /**
     * English on a 12-hour clock: 16:10 is written 4:10 in the clock strings, with no marker, and
     * `am` and `pm` hold the markers. Each day of a feed in it carries the app's own 12-hour times
     * beside them, "4:10 PM" ([WatchDay.clockTime]), as `TwelveHourClock.render` writes them.
     */
    fun english12(): WatchUi = english24().copy(twelveHour = true)

    /**
     * Arabic with Western digits, as CLDR defaults `ar-LY` and as the mockups' plain `ar` has it:
     * right to left, in a script that is not Latin, with the phone's Arabic words for the screens
     * ([ARABIC_SCREENS]).
     */
    fun arabicLibya(): WatchUi = WatchUi(
        language = "ar-LY",
        rtl = true,
        digitZero = '0',
        twelveHour = false,
        am = "ص",
        pm = "م",
        countdown = WatchCountdownPhrases(
            hoursMinutes = "بعد %1\$s س %2\$s د",
            hours = "بعد %1\$s س",
            minutes = "بعد %1\$s د",
            underAMinute = "بعد أقل من دقيقة",
        ),
        nextIn = "متبقٍّ على %1\$s",
        // The phone's a11y_hours and a11y_minutes asked for 0 to 99 (values-ar): zero and one
        // share a phrase, then two, few (3 to 10) and many (11 to 99).
        spoken = WatchSpoken(
            countdown = "%1\$s بعد %2\$s، الساعة %3\$s",
            countdownSoon = "%1\$s بعد أقل من دقيقة، الساعة %2\$s",
            hoursMinutes = "%1\$s و%2\$s",
            hours = listOf(
                WatchCount(1, "%1\$s ساعة"),
                WatchCount(2, "%1\$s ساعتين"),
                WatchCount(10, "%1\$s ساعات"),
                WatchCount(99, "%1\$s ساعة"),
            ),
            minutes = listOf(
                WatchCount(1, "%1\$s دقيقة"),
                WatchCount(2, "%1\$s دقيقتين"),
                WatchCount(10, "%1\$s دقائق"),
                WatchCount(99, "%1\$s دقيقة"),
            ),
        ),
        screens = ARABIC_SCREENS,
        latinScript = false,
        face = ARABIC_FACE,
    )

    /**
     * Arabic as a Saudi phone has it: the same words, written in Arabic-Indic digits, but for its
     * ring's H:MM:SS, which the phone keeps in Western digits on ar-SA (`CountdownDigits.westernFallback`).
     */
    fun arabicSaudi(): WatchUi = arabicLibya().copy(language = "ar-SA", digitZero = '٠', ringDigitZero = '0')

    /**
     * A phone in Bengali, for its digits alone: they are written ০ to ৯, so 12 reads ১২. The
     * words stay English, the countdown's phrases included, until the Bengali ones are written
     * (spec 1.3). As the phone writes it (Ruling 47), its script is not Latin, and its ring counts
     * down in Western digits (`CountdownDigits.westernFallback`).
     */
    fun bengali(): WatchUi = english24().copy(language = "bn", digitZero = '০', ringDigitZero = '0', latinScript = false)

    /**
     * A phone in Urdu, for its digits alone: the extended Arabic-Indic set, ۰ to ۹, which
     * Persian writes too and which is a block of its own beside Arabic's. The words stay English, as
     * Bengali's do; its script, as the phone writes it, is not Latin (Ruling 47).
     */
    fun urdu(): WatchUi = english24().copy(language = "ur", rtl = true, digitZero = '۰', latinScript = false)

    /**
     * [days] days of Cape Town from [firstDay], counted from day 0, Sunday 18 October 2026, written
     * in [ui]'s language and digits. Day 0 is Fajr 04:33, Sunrise 06:01, Dhuhr 12:33, Asr 16:10,
     * Maghrib 19:04 and Isha 20:24; Fajr ends at sunrise and Asr at 19:02, a little before
     * Maghrib. The Hijri day advances from 6 Jumada al-Ula, and every Friday (day 5 is the first)
     * names Dhuhr Jumuʿah. Each day carries its local midnight, UTC+2. The feed was written at
     * 08:00 on day 0, whatever its first day.
     */
    fun capeTown(days: Int = 30, ui: WatchUi = english24(), firstDay: Int = 0): WatchFeed {
        val arabic = ui.language.startsWith("ar")
        return WatchFeed(
            version = WatchFeedJson.VERSION,
            writtenAt = at(0, 8, 0),
            days = List(days) { position ->
                val index = firstDay + position
                day(
                    midnight = DAY_ZERO_MIDNIGHT + index * DAY,
                    daysSinceEpoch = CAPE_TOWN_DAY_ZERO + index,
                    hijri = Hijri(year = 1448, month = 5, day = 6).plusDays(index),
                    plan = CAPE_TOWN,
                    ui = ui,
                    words = if (arabic) ARABIC else ENGLISH,
                )
            },
            // The place as the phone writes it under Ruling 39 (WatchFeedBuilder, held to these by
            // WatchFeedSampleTest): the bundle's name for the city, the bearing to the Qibla card's
            // whole degree (23.35), the distance to two figures (6,558 km) in the phone's digits and
            // grouping, and WMM2025's declination on 18 October 2026 (-26.81) to a whole degree.
            place = WatchPlace(
                city = if (arabic) "كيب تاون" else "Cape Town",
                qiblaBearing = 23.0,
                qiblaKm = grouped("6600", ui.digitZero),
                declination = -27.0,
                southern = true,
            ),
            ui = ui,
        )
    }

    /**
     * [feed] with every day's midnight unknown (0), as the phone says it when it cannot say: the
     * rules then turn each day over at solar midnight.
     */
    fun withoutMidnights(feed: WatchFeed): WatchFeed = feed.copy(days = feed.days.map { it.copy(midnight = 0L) })

    /**
     * [day] moved [seconds] later, its midnight and every time with it. A date after a clock change
     * sits against the one before it this way: an hour later when the clocks went back, which
     * makes the date before 25 hours long, an hour earlier when they went forward (23).
     */
    fun shifted(day: WatchDay, seconds: Long): WatchDay = day.copy(
        midnight = day.midnight + seconds,
        times = day.times.copy(
            fajr = day.times.fajr + seconds,
            sunrise = day.times.sunrise + seconds,
            dhuhr = day.times.dhuhr + seconds,
            asr = day.times.asr + seconds,
            maghrib = day.times.maghrib + seconds,
            isha = day.times.isha + seconds,
            fajrEnd = day.times.fajrEnd?.plus(seconds),
            asrEnd = day.times.asrEnd?.plus(seconds),
        ),
    )

    /**
     * The feed as the phone is to send it, and as the Wear app's debug build starts with: yesterday
     * and thirty days, 31 in all, from Saturday 17 October 2026, so that the mockups' Sunday has its
     * yesterday. From the second day on it is [capeTown]'s thirty days. It carries two phrases of his
     * own ([SAMPLE_PHRASES]), so that the watch's phrase list has some to show.
     */
    fun capeTownSample(): WatchFeed = capeTown(days = 31, firstDay = -1).copy(tasbeeh = SAMPLE_PHRASES)

    /**
     * Two phrases of his own, with ids in the phone's form (`custom_` and the time it was made): an
     * Arabic phrase of 36 characters, which takes two lines on the watch's counter, and one in Latin
     * letters with a target of its own.
     */
    val SAMPLE_PHRASES: List<WatchPhrase> = listOf(
        WatchPhrase("custom_1730000000000", "سبحان الله وبحمده، سبحان الله العظيم", 100),
        WatchPhrase("custom_1730000000001", "Astaghfirullah wa atubu ilayh", 33),
    )

    /**
     * [capeTownSample] as a phone in another language or clock sends it: the debug build's other
     * samples, committed beside it as `vectors/sample-feed-capetown-<name>.json` (`VectorsTest`), so
     * that the face is looked at in Arabic and on a twelve-hour clock (plan 1, Task 10):
     *
     * - `en12`: English on a twelve-hour clock, with the app's own 12-hour times ("4:10 PM");
     * - `ar-ly12`: Arabic with Western digits (Libya) on a twelve-hour clock ("4:10 م");
     * - `ar-sa`: Arabic in Arabic-Indic digits (Saudi Arabia), on a 24-hour clock, its ring in
     *   Western digits.
     *
     * Each carries the screens' words in its language. Only the first sample carries his phrases.
     */
    fun capeTownSamples(): Map<String, WatchFeed> = linkedMapOf(
        "en12" to capeTown(days = 31, firstDay = -1, ui = english12()),
        "ar-ly12" to capeTown(days = 31, firstDay = -1, ui = arabicLibya().copy(twelveHour = true)),
        "ar-sa" to capeTown(days = 31, firstDay = -1, ui = arabicSaudi()),
    )

    /**
     * Three days of Tromsø in the middle of May, in English: a high-latitude summer, where the
     * engine's Maghrib and Isha fall after midnight. Day 0 is Sunday 17 May. Its Maghrib falls at
     * 00:13 on the 18th, and its Isha at 00:41 on the 18th, which is after day 1's Fajr at 00:38:
     * so the night belongs to day 1, and day 0's Isha is not a start once the 18th has begun
     * (`TimelineBuilder.yesterdaysStarts`). Day 1's Isha is later than day 2's Fajr in the same
     * way. The figures are invented; their shape is the engine's.
     */
    fun tromsoMay(): WatchFeed = tromso(TROMSO)

    /**
     * [tromsoMay]'s three days with day 1's Maghrib after day 2's Fajr: Maghrib at 00:45 on the
     * 19th, Fajr at 00:38 that morning, Isha at 00:52. It is the shape a polar night gives the
     * engine (the combined Maghrib/Isha of R72 is the extreme of it). The phone's day holds all five
     * of its starts, so from Asr to midnight on the 18th it counts to that Maghrib; once the 19th
     * has begun it is one of yesterday's starts after today's Fajr, and is dropped.
     */
    fun lateMaghribNight(): WatchFeed = tromso(TROMSO_LATE_MAGHRIB)

    /**
     * Three days of Cape Town where day 1's Maghrib and Isha are one instant, 19:04, as a combined
     * polar Maghrib/Isha is (R72). On the Prayer screen's rule Maghrib is the prayer that is lit
     * (the first of two at the latest instant), on the widgets' Isha (the last that has begun).
     */
    fun combinedMaghribIsha(): WatchFeed = capeTown(days = 3).let { feed ->
        feed.copy(
            days = feed.days.mapIndexed { index, day ->
                if (index != 1) day else day.copy(
                    times = day.times.copy(isha = day.times.maghrib),
                    clock = day.clock.copy(isha = day.clock.maghrib),
                )
            },
        )
    }

    /**
     * Two days of Cape Town in a night the engine never produces, for the one guard
     * `TimelineBuilder.build` keeps against it: day 1 has an Asr at 00:10 and day 0 an Isha at
     * 00:20, both before day 1's Fajr, so a prayer of yesterday's has begun after the latest of
     * today's. The Prayer screen lights nothing then, and the widgets light the Asr.
     */
    fun oddNight(): WatchFeed = capeTown(days = 2).let { two ->
        two.copy(
            days = listOf(
                two.days[0].copy(times = two.days[0].times.copy(isha = at(1, 0, 20))),
                two.days[1].copy(times = two.days[1].times.copy(asr = at(1, 0, 10))),
            ),
        )
    }

    private fun tromso(plans: List<Plan>): WatchFeed {
        val ui = english24()
        return WatchFeed(
            version = WatchFeedJson.VERSION,
            writtenAt = atTromso(0, 8, 0),
            days = plans.mapIndexed { index, plan ->
                day(
                    midnight = TROMSO_DAY_ZERO_MIDNIGHT + index * DAY,
                    daysSinceEpoch = TROMSO_DAY_ZERO + index,
                    hijri = Hijri(year = 1447, month = 11, day = 30).plusDays(index),
                    plan = plan,
                    ui = ui,
                    words = ENGLISH,
                )
            },
            // As the phone writes it: the bearing to the whole degree (154.28), the distance to two
            // figures (5,542 km), and WMM2025's declination on 17 May 2026 (11.16) to a whole degree.
            place = WatchPlace(
                city = "Tromsø",
                qiblaBearing = 154.0,
                qiblaKm = "5,500",
                declination = 11.0,
                southern = false,
            ),
            ui = ui,
        )
    }

    /** Days from 1 January 1970 to 18 October 2026, and to 17 May 2026. */
    private const val CAPE_TOWN_DAY_ZERO = 20_744L
    private const val TROMSO_DAY_ZERO = 20_590L

    /** A day's six times as minutes after the local midnight that opens its date, and the two ends. */
    private class Plan(
        val fajr: Int,
        val sunrise: Int,
        val dhuhr: Int,
        val asr: Int,
        val maghrib: Int,
        val isha: Int,
        val fajrEnd: Int?,
        val asrEnd: Int?,
    )

    private val CAPE_TOWN = Plan(
        fajr = 4 * 60 + 33,
        sunrise = 6 * 60 + 1,
        dhuhr = 12 * 60 + 33,
        asr = 16 * 60 + 10,
        maghrib = 19 * 60 + 4,
        isha = 20 * 60 + 24,
        fajrEnd = 6 * 60 + 1,
        asrEnd = 19 * 60 + 2,
    )

    /** Maghrib and Isha past 24 * 60 fall after midnight, on the next date. */
    private val TROMSO = listOf(
        Plan(
            fajr = 42, sunrise = 71, dhuhr = 12 * 60 + 46, asr = 17 * 60 + 9,
            maghrib = 24 * 60 + 13, isha = 24 * 60 + 41, fajrEnd = 71, asrEnd = 24 * 60 + 11,
        ),
        Plan(
            fajr = 38, sunrise = 63, dhuhr = 12 * 60 + 46, asr = 17 * 60 + 9,
            maghrib = 24 * 60 + 21, isha = 24 * 60 + 44, fajrEnd = 63, asrEnd = 24 * 60 + 19,
        ),
        Plan(
            fajr = 34, sunrise = 56, dhuhr = 12 * 60 + 46, asr = 17 * 60 + 8,
            maghrib = 24 * 60 + 29, isha = 24 * 60 + 47, fajrEnd = 56, asrEnd = 24 * 60 + 27,
        ),
    )

    /** Day 1's Maghrib (00:45) is after day 2's Fajr (00:38), both on the 19th. */
    private val TROMSO_LATE_MAGHRIB = listOf(
        TROMSO[0],
        Plan(
            fajr = 38, sunrise = 63, dhuhr = 12 * 60 + 46, asr = 17 * 60 + 9,
            maghrib = 24 * 60 + 45, isha = 24 * 60 + 52, fajrEnd = 63, asrEnd = 24 * 60 + 43,
        ),
        Plan(
            fajr = 38, sunrise = 56, dhuhr = 12 * 60 + 46, asr = 17 * 60 + 8,
            maghrib = 24 * 60 + 49, isha = 24 * 60 + 55, fajrEnd = 56, asrEnd = 24 * 60 + 47,
        ),
    )

    /**
     * What a language calls the things a day's strings are made of: the face's weekdays and months,
     * and the ones a screen reader is told ([spokenWeekdays], [spokenMonths]), which are the face's
     * where the language has no capitals or short forms.
     */
    private class Words(
        val prayers: WatchSix<String>,
        val friday: String,
        val weekdays: List<String>,
        val months: List<String>,
        val hijriMonths: List<String>,
        val spokenWeekdays: List<String> = weekdays,
        val spokenMonths: List<String> = months,
    )

    private val ENGLISH = Words(
        prayers = WatchSix("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
        friday = "Jumuʿah",
        weekdays = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"),
        months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"),
        hijriMonths = listOf(
            "Muharram", "Safar", "Rabi’ al-Awwal", "Rabi’ al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha’ban",
            "Ramadan", "Shawwal", "Dhu al-Qi’dah", "Dhu al-Hijjah",
        ),
        spokenWeekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
        spokenMonths = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        ),
    )

    private val ARABIC = Words(
        prayers = WatchSix("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"),
        friday = "الجمعة",
        weekdays = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"),
        months = listOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
        ),
        hijriMonths = listOf(
            "محرّم", "صفر", "ربيع الأوّل", "ربيع الآخر",
            "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
            "رمضان", "شوّال", "ذو القعدة", "ذو الحجّة",
        ),
    )

    /** A Hijri date, moved by the tabular calendar's month lengths: 30 days, then 29, from Muharram. */
    private data class Hijri(val year: Int, val month: Int, val day: Int) {
        fun plusDays(n: Int): Hijri {
            var date = this
            repeat(n.coerceAtLeast(0)) { date = date.tomorrow() }
            repeat((-n).coerceAtLeast(0)) { date = date.yesterday() }
            return date
        }

        private fun tomorrow(): Hijri = when {
            day < lengthOf(month) -> copy(day = day + 1)
            month < 12 -> Hijri(year, month + 1, 1)
            else -> Hijri(year + 1, 1, 1)
        }

        private fun yesterday(): Hijri = when {
            day > 1 -> copy(day = day - 1)
            month > 1 -> Hijri(year, month - 1, lengthOf(month - 1))
            else -> Hijri(year - 1, 12, lengthOf(12))
        }

        private fun lengthOf(month: Int): Int = if (month % 2 == 1) 30 else 29
    }

    private fun day(
        midnight: Long,
        daysSinceEpoch: Long,
        hijri: Hijri,
        plan: Plan,
        ui: WatchUi,
        words: Words,
    ): WatchDay {
        val (year, month, dayOfMonth) = civilFromDays(daysSinceEpoch)
        // 1 January 1970 was a Thursday; Sunday is 0.
        val weekday = ((daysSinceEpoch + 4) % 7).toInt()
        val friday = weekday == 5

        fun instant(minutes: Int): Long = midnight + minutes * 60L
        fun written(text: String): String = inDigits(text, ui.digitZero)
        fun clock(minutes: Int): String {
            val minuteOfDay = minutes % (24 * 60)
            val hour = minuteOfDay / 60
            val shown = if (ui.twelveHour) (if (hour % 12 == 0) 12 else hour % 12) else hour
            return written("$shown:${(minuteOfDay % 60).toString().padStart(2, '0')}")
        }
        // The app's own 12-hour time, as TwelveHourClock.render joins it: the day period after a
        // no-break space, and before the time in Turkish, the one interface language whose CLDR
        // pattern puts it first.
        val markerFirst = ui.language.substringBefore('-').substringBefore('_').lowercase() == "tr"
        fun clockTime(minutes: Int): String {
            val marker = if (minutes % (24 * 60) < 12 * 60) ui.am else ui.pm
            return if (markerFirst) "$marker$NBSP${clock(minutes)}" else "${clock(minutes)}$NBSP$marker"
        }

        return WatchDay(
            date = "$year-${month.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}",
            times = WatchTimes(
                fajr = instant(plan.fajr),
                sunrise = instant(plan.sunrise),
                dhuhr = instant(plan.dhuhr),
                asr = instant(plan.asr),
                maghrib = instant(plan.maghrib),
                isha = instant(plan.isha),
                fajrEnd = plan.fajrEnd?.let(::instant),
                asrEnd = plan.asrEnd?.let(::instant),
            ),
            clock = WatchSix(
                fajr = clock(plan.fajr),
                sunrise = clock(plan.sunrise),
                dhuhr = clock(plan.dhuhr),
                asr = clock(plan.asr),
                maghrib = clock(plan.maghrib),
                isha = clock(plan.isha),
            ),
            names = words.prayers.copy(dhuhr = if (friday) words.friday else words.prayers.dhuhr),
            hijri = written("${hijri.day} ${words.hijriMonths[hijri.month - 1]} ${hijri.year}"),
            gregorian = written("${words.weekdays[weekday]} $dayOfMonth ${words.months[month - 1]}"),
            weekday = words.weekdays[weekday],
            dayNumber = written("$dayOfMonth"),
            midnight = midnight,
            hijriDayMonth = written("${hijri.day} ${words.hijriMonths[hijri.month - 1]}"),
            gregorianSpoken = written("${words.spokenWeekdays[weekday]} $dayOfMonth ${words.spokenMonths[month - 1]}"),
            // Only a 12-hour phone sends them.
            clockTime = if (!ui.twelveHour) null else WatchSix(
                fajr = clockTime(plan.fajr),
                sunrise = clockTime(plan.sunrise),
                dhuhr = clockTime(plan.dhuhr),
                asr = clockTime(plan.asr),
                maghrib = clockTime(plan.maghrib),
                isha = clockTime(plan.isha),
            ),
        )
    }

    /**
     * [number], Western digits alone, grouped in threes and written from [zero], as the phone's
     * `DigitGrouping` writes a distance: the Arabic thousands separator (U+066C) beside
     * Arabic-Indic and extended Arabic-Indic digits, a comma beside Western and Bengali ones (the
     * fixtures have no French, Turkish or Indonesian phone).
     */
    private fun grouped(number: String, zero: Char): String {
        val mark = if (zero == Char(0x0660) || zero == Char(0x06F0)) Char(0x066C) else ','
        val withMarks = buildString {
            number.forEachIndexed { i, c ->
                if (i > 0 && (number.length - i) % 3 == 0) append(mark)
                append(c)
            }
        }
        return inDigits(withMarks, zero)
    }

    /** [text] with its digits written from [zero]: '0' leaves it as it is. */
    private fun inDigits(text: String, zero: Char): String =
        if (zero == '0') text else text.map { if (it in '0'..'9') zero + (it - '0') else it }.joinToString("")

    /** The proleptic Gregorian date [days] days after 1 January 1970 (Howard Hinnant's algorithm). */
    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val dayOfEra = z - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val shiftedMonth = (5 * dayOfYear + 2) / 153
        val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
        val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
        val year = (yearOfEra + era * 400 + (if (month <= 2) 1 else 0)).toInt()
        return Triple(year, month, day)
    }
}
