# Taqwa city prayer-time pages — design

**Date:** 2026-09-25 · **Branch:** `city-pages` · **Approved in chat by Mohamed, 25 September 2026**
(direction, languages, home section, the Jumuʿah pill; the rest decided overnight under his
instruction to "go with your recommendation" and build it end to end.)

Mockups: https://claude.ai/artifact/KzyAT5kziDDudXRUWcLaag

## 1. What and why

A page per city with that city's prayer times for this month and next, in English and in the
languages actually spoken there, generated from the app's own prayer-time engine and rebuilt every
night by a scheduled GitHub Action. "Prayer times" plus a city is the most common prayer search in
every language; the pages give the site something people search for, and every page offers the app.

The pages must never disagree with the app. That is the whole promise: the numbers on
taqwa.world/prayer-times/tripoli-libya/ are the numbers Taqwa shows a user in Tripoli with the app's
default settings.

## 2. Decisions

| Question | Decision |
|---|---|
| Languages per city | English, plus the languages spoken there. Diaspora cities also get the languages of their large Muslim communities (London: Urdu, Bengali, Arabic). Makkah and Madinah get all seven. |
| How many cities | About 150 to start, hand-picked. Curation is the product: every city is checked. |
| Method | The app's `CalculationMethodDefaults.forCountry`, the same default a user there gets. A country is left out of the first list if its national timetable disagrees with that default by more than about 2 minutes (see §8). |
| Asr | Hanafi in countries where the Hanafi school is the norm (Pakistan, India, Bangladesh, Afghanistan); Standard elsewhere. Always stated on the page. |
| High latitudes | The app's AUTOMATIC rule, and the page says so when the rule changed a time that month. |
| Months | The current month and the next, both on the page. |
| Digits, dates, month names | CLDR for the page language in the city's country, exactly as the app's Android formatter does it: a Libyan page reads 5:35 and 24 صفر 1448, an Egyptian one ٥:٣٥ and ٢٤ صفر ١٤٤٨. |
| Names | Prayer, method and madhab names from the app's `strings.xml`; Hijri month names from the app's own table; city names from the app's city files, with checked overrides where those are wrong. |
| Home page | A section at the bottom of every language's home page. |
| Navigation | "Prayer times" first in the header, every language. |
| The Jumuʿah pill | On Fridays, next to Dhuhr: on the city pages' Today card and in the month table, and in the app's Prayer screen. |

## 3. Architecture

```
app sources ──► tools/timetables (JVM build) ──► _data/timetables.json ──► site/build.py ──► _site/
 engine, Qibla,      compiles them unchanged,         every city, every day,       pages, index,
 Hijri, names        adds CLDR formatting             every page language          sitemap, 404
                                     ▲
                       nightly GitHub Action, and on every push that touches any of it
```

### 3.1 The generator: `tools/timetables`

A standalone Gradle build (its own `settings.gradle.kts`, run with the root wrapper as
`./gradlew -p tools/timetables generate`) so the app's build is untouched. It reads the app's
version catalog, so Kotlin, adhan2 and kotlinx-datetime are always the app's versions.

It is a Kotlin Multiplatform module with a single `jvm()` target. Its `commonMain` is **the app's
own source files**, included by path from `shared/` and `widgetcore/`:

- `prayer/PrayerTimesEngine.kt`, `prayer/HighLatitudeSelector.kt`, `prayer/CalculationMethodDefaults.kt`
- `domain/Prayer.kt` (both halves), `domain/PrayerSettings.kt`, `domain/GeoLocation.kt`
- `qibla/QiblaMath.kt`, `hijri/TabularHijriCalendar.kt`, `hijri/HijriMonthNames.kt`
- `i18n/UiLanguage.kt`

`commonTest` includes the app's own tests for those files (`PrayerTimesEngineTest`,
`HighLatitudeSelectorTest`, `CalculationMethodDefaultsTest`, `QiblaMathTest`,
`TabularHijriCalendarTest`), so the JVM build proves it computes what the app computes. A change
to one of those files that breaks JVM compilation fails the site build loudly, which is the
intended coupling: the site can never quietly drift from the app.

One small app refactor makes this possible: the Hijri month names move from a private table in
`HijriFormatter.kt` (which imports Compose through `PlatformFormat`) into `HijriMonthNames.kt`,
which imports only `UiLanguage`. `HijriFormatter.monthName` delegates to it; behaviour is unchanged.

`jvmMain` holds the generator's own code:

- **`Catalog`** reads `site/cities.tsv` (the curated list), the app's `cities.csv` and
  `city-names-<lang>.csv`, and validates everything: every id exists, every slug is unique and
  URL-safe, every page language is one of the seven, every page language has a name.
- **`Formats`** mirrors `AndroidPlatformFormat` for a `Locale(lang, country)`: integer digits,
  `H:mm` clock times, CLDR LONG dates with the day unpadded, plus month-year titles, weekday names
  and grouped distances. English pages use en-GB, as the site and `EnglishPlatformFormat` do.
- **`AppStrings`** reads the app's `strings.xml` per language (prayer names, method names, madhab
  names, the Qibla detail format).
- **`Timetable`** runs `PrayerTimesEngine.timesFor` for every day of both months in the city's
  time zone, and `TabularHijriCalendar` and `QiblaMath` beside it.
- **`Main`** writes one JSON document: for every city, its facts and, for each of its languages,
  every string the page shows, already formatted, plus epoch seconds for the live countdown.

### 3.2 The curated list: `site/cities.tsv`

One row per city: `slug`, GeoNames `id` (the app's), `languages`, optional `madhab`, optional
`featured` (the languages whose home page lists it), optional per-language name overrides. The
country, coordinates and time zone always come from the app's `cities.csv`.

### 3.3 Rendering: `site/build.py` + `site/timetables.py`

`build.py` gains the city pages, one index per language and the home section, all from the JSON.
Sentences live in each language's `meta.json` under `timetable`; the JSON supplies only values.

- `/{lang}/prayer-times/` lists the cities in that language by region and country, with a filter.
- `/{lang}/prayer-times/{slug}/` is a city page.
- The language picker on a city page links to the city's twin where it exists and to that
  language's index where it does not; hreflang lists only real twins, x-default is English.
- The sitemap gains every city page and index with their alternates.
- A city page carries `BreadcrumbList` structured data and the language's share card.

### 3.4 The page (see the mockups)

Header and language row as today, with "Prayer times" first in the nav. Breadcrumb, H1
"Prayer times in {city}", a line with country, weekday, Gregorian and Hijri date. Two fact cards:
the method (name, Asr school, time zone) linking to the support page's "A prayer time looks wrong"
section, and the Qibla (bearing and distance, with the app's mini dial). The Today card mirrors the
app's Prayer screen: the ring and countdown to the next prayer, then the five prayers with the
current one lit and the Jumuʿah pill on Fridays. Then this month's table (date, Hijri, six times;
today lit, past days faded, Fridays marked), next month's table, a note on how the times are
made, the app card, and links to other cities in the country and nearby.

### 3.5 Behaviour in the browser

Everything is readable without script. A small script, no network and no storage, makes it live:
it resolves "today" in the city's own time zone (so a reader in London looking at Jakarta sees
Jakarta's today), lights that row, fills the Today card and counts down to the next prayer. On a
phone it folds the days already gone behind "Earlier this month". On the index it filters cities
as you type. The static page shows the generation day, so a missed night leaves it correct except
for which row is lit.

## 4. Automation

`.github/workflows/pages.yml` runs on every push that touches the site, the generator, the list,
or any app file the generator compiles or reads; every night at 00:07 UTC; and by hand. It sets up
JDK 21 and Gradle (cached), runs the generator's tests and the generator, then `build.py --check`,
then deploys. Scheduled runs also call the workflow `enable` endpoint, which resets GitHub's
60-day inactivity timer so the schedule is never switched off on a quiet repository. A failed
scheduled run emails the repository owner. Nothing generated is committed.

## 5. The app change

On the Prayer screen, the Dhuhr row carries a Jumuʿah pill on Fridays, in the location's own time
zone, in all seven languages: Jumuʿah, الجمعة, Joumouʿa, Cuma, Jumat, جمعہ, জুমা.

## 6. Testing

- The generator's `commonTest` is the app's own engine, Qibla and Hijri tests, run on the JVM.
- Known answers from the app itself: Tripoli on 13 September 2026 reads Fajr 5:26, Dhuhr 13:04,
  Asr 16:34, Maghrib 19:16, Isha 20:35 on the app's Prayer screen.
- `Formats` tests pin the CLDR behaviour the app relies on (ar-LY Latin digits, ar-EG Arabic-Indic,
  bn-BD Bengali, ur-PK Latin, fr-MA month names, the unpadded day).
- `Catalog` tests: bad ids, duplicate slugs, unknown languages and missing names all fail.
- `build.py --check` additionally fails on a city page missing either month, a hreflang that is
  not reciprocal, or an index that lists a page that does not exist.
- An independent cross-check against the Aladhan API for a sample of cities and dates, and a
  comparison with official timetables where countries publish one.

## 7. Performance and privacy

No images on a city page besides the app screenshot in the app card; the data for the countdown is
inline. The script makes no requests and stores nothing. Pages are static HTML, gzip-served by
GitHub Pages.

## 8. Risks

- **National timetables.** Turkey, Indonesia, Malaysia, Morocco and others publish official times.
  Where the app's default method disagrees by more than about 2 minutes the country waits until the
  app supports its method, so the site never publishes numbers the app would not show and a
  mosque would not recognise. The research note that decided this is in the build log.
- **Bad names in the app's data.** Found already: Al Khums is "المرقب" in the Arabic city file and
  Zawiya appears twice. The list carries overrides and the report flags these for the app.
- **Scale.** A few hundred pages of real, distinct data. No thin country pages, no month archives.

## 9. Out of scope

Per-city share images, country pages, month archives, cities whose communities follow the Jaʿfari
method (the app has no exact Jaʿfari method), anything using the reader's location.
