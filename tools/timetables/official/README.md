# Official prayer timetables

The ground truth the prayer-time engine is checked against: timetables published by the
authorities and mosques whose times people actually pray and fast by, gathered in the research
round of 25–26 September 2026 (docs/research/2026-09-prayer-times/, and the design in
docs/superpowers/specs/2026-09-26-taqwa-prayer-times-engine-design.md).

Every table is one line per day, in the authority's local clock:

    # source URL, fetch date, anything unusual (one or more comment lines)
    2026-09-25 04:40 06:10 12:20 15:45 18:30 19:50        (an invented row)
    yyyy-mm-dd fajr  sunrise dhuhr asr  maghrib isha      ("-" where the table has no value)

## What is here, and what is not

The repository is public, and most publishers either reserve all rights (Diyanet, JAKIM, IZ BiH,
the Muslim Board of Uzbekistan), forbid copying without permission (Egypt's Survey Authority,
Algeria's Ministry of Religious Affairs, Tunisia's INM, Jordan's Ministry of Awqaf, Bahrain's
Supreme Council) or state no terms at all. So:

- `MANIFEST.tsv` (committed) lists 1,911 tables, 516,994 days: where each came from, the dates
  it covers, and its licence class. It is not the whole archive (6,346 table files on 13 Nov 2026,
  and three `.tsv` lists beside them): the monitor's own tables (`archive/tables/monitor`, `pinned`, `held`) are described in
  `monitor/sources.tsv`, and some manual and round captures are not listed yet.
- `open/` (committed) holds the tables whose licence allows republishing, with attribution:
  MUIS Singapore (Singapore Open Data Licence v1.0) and DUM RT Tatarstan (CC BY 4.0).
  `open/LY-AWQAF-OWNER/` is not a publisher's table: it holds the owner's own adhan observations
  at Benghazi and Sabha (27 Sep 2026, Fajr and Maghrib; ruling R73), committed as the owner's data.
- `archive/` (git-ignored) holds everything, plus the research code that fitted each authority's
  method and the raw reports. It is also committed on the local-only branch
  `local/official-data-never-push`, so it survives this checkout; that branch must never be pushed.

The engine never ships any of these tables. It reproduces each authority's published method,
which copies nothing, and the tables are only used to prove the reproduction is never early.
The one exception the design allows is a table whose licence permits it (MUIS, and London Prayer
Times' "completely free for all use").

## What the manifest does not say yet

`MANIFEST.tsv` is a first pass: the `authority` column is assigned from file and folder names and
is provisional where it names a research region (`SOUTHEAST-ASIA`, `CENTRAL-ASIA`, `EUROPE`,
`UK-FRANCE`). Before the gate can read it, phase 1 of the design turns it into a typed manifest:
per table the registry entry it tests, its reference point and zone, the clock it is printed in,
the method version in force, what each column means (prayer start, end of eating, imsak as a
precaution, mosque or congregation time), and a status (valid, duplicate, known error with its
reason). Known errors already found: JAKIM SBH06's 2026 breaks, a Brunei row that would put Fajr
about 9 min early, Lakemba's daylight-saving day, Libya Wayback captures one or two days stale,
Egypt's captures before its method change of Dec 2024–Jan 2025, ICC London printed in GMT all year,
Morocco's archived legal-clock tables beside the live UTC+0 month, duplicates of the same data in
several rounds. `archive/raw/` holds the Arab-world captures that were never normalised (Jordan,
Palestine, Lebanon, Syria, Iraq, the Masjidi feeds).

### A republisher's copy: Kemenag

Every `ID-KEMENAG` table (698 manifest lines, 254,694 days, and the monitor's weekly reads) was read
from `api.myquran.com`, the open myQuran API, which republishes the schedule Kemenag's Bimas Islam
publishes for each kabupaten and kota. None was captured from Kemenag's own pages
(bimasislam.kemenag.go.id): no address of its table that a plain GET can read is held, and Taqwa
reads nothing through a form or a page's own script. So what the gate proves for Indonesia is "never
before myQuran's copy of Kemenag's table", which is as good as the copy is: a figure myQuran changed
or dropped would pass unseen (the audit of 12 Nov 2026, finding F26). What holds the copy to account
today: the gate reproduces each of the 246 units' tables from Kemenag's documented angles (20° and
18°) with the unit's own allowance, the monitor reads every unit's current and next month weekly and
one already-held month again each run, and a held day that changes is noted. The app and the site say it in so many words
(`about_checked_through_copy`, `prayer_whose_checked_copy_body`: "checked against myQuran's copy of
Kemenag's published timetable"), from `RegistryEntry.republisher`. Whether to check a sample against
Kemenag's own publication by hand is the owner's question.

## The gate

`gate/<group>.tsv` (committed) is the typed manifest, one file per authority group: each line a
table and how to check the engine against it. The header names these columns, in any order:

| column | meaning |
|---|---|
| `path` | the table, relative to this folder (`open/…` or `archive/…`) |
| `entry` | the registry id it is checked against, never resolved by place; `<id>/<unit>` names a unit |
| `lat`, `lon` | where the table applies; both empty for the unit's reference point or the method's fixed point |
| `zone` | the place's civil IANA zone, the one the engine computes in |
| `clock` | optional; the zone the table is printed in where it is not `zone` (ICC London in GMT all year): an IANA zone or a fixed offset such as `UTC+01:00` |
| `columns` | each time column's meaning: `F` Fajr, `S` sunrise, `D` Dhuhr, `A` Asr in the row's school, `As`/`Ah` the Standard/Hanafi Asr, `M` Maghrib, `I` Isha, `E` the end of eating, `Im` imsak, `-` not checked; `F+E` for a column that is both, which the gate refuses on a table whose `#` lines say its times were rounded up to the minute (the printed Fajr could then stand up to 59 s after the authority's moment: read the end from a floored column of its own, ruling R149) |
| `format` | the reader: `daily` for the layout above; a new layout is one `object <Name>Format : OfficialFormat` in `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate/formats/`, named here in kebab case (`twelve-hour` → `TwelveHourFormat`) |
| `school` | the school of the `A` column: `standard`, `hanafi`, or `-` |
| `split` | `fit` (margins are fitted on it) or `test` (held out). What a figure shown to a reader counts is ruling R214's (below): a table's days once, and a cell as held out only where no margin saw it, whatever this column says of a row replaying a fitted table |
| `note` | free text |
| `member` | optional; for a cautious (class C) entry, the member id whose printed table the row is. Each start, Maghrib too, is checked against the latest member's printed time (rulings R38, R155); on a day a member that is not the most followed has its Maghrib set by its own far-north or winter rule, or the real sun does not set there, that member is checked at its Maghrib from the sun, or not at all, and the gap is recorded in the stamp's `maghribGuard` (ruling R156); a cautious place-day that shows no Maghrib end, as no end in play is after the Maghrib shown, is recorded in the stamp's `maghribNoEnd` with its dates (ruling R158), never broken |

Each event of a row is held to a late limit: the `LateLimit` its unit records for that event, else
the one its entry records, else its class's (A 1, B 2, C 1 after the latest member, D 3). An
exception lives in the registry with its reason and the events it covers (rulings R37, R41), never
in a gate file, so that the gate proves and About states the same number. Nothing excuses an early
start or a late end.

**What a stamp counts (ruling R214, 13 Nov 2026).** The gate checks every row; the figures a reader is
shown (a stamp's `places`, `placeDays`, `ramadanDays` and its held-out days, and from them About's
"days at places" and the website's checks page) count a published table once:

- A table's days are counted at the first row of the entry that reads its starts, a row of a
  `*-reach.tsv` file only where no other file reads the table. Every other row reading the same table
  (at a point of the unit's reach, in another town the one national table serves) is a **replay**:
  checked like any row, an early start or a late end there breaks the gate, and it adds no place, no
  place-day and no Ramadan day to a figure shown. The gate's first line says both numbers (the days
  checked, and of them the published tables' own).
- A cell (a table's column of one event) is held out only where its row is `test` and no margin saw
  it: no `fit` row of the entry reads that event from the same table, and no `@fitted` line names it.

      @fitted  <entry>[/<unit>]  <path | prefix*>  <events: F S D A As Ah M I E Im, or *>  <reason>

  A `@fitted` line says that a margin, a limit's floor or a rule of the entry was set on those
  cells of a row that stays `test` for its other events (a minute widened after the row was found
  early, an end read off the same year's print). It is written beside the row, in the same file, and
  the gate refuses one that names no test row of the file reading those events at that path. The rule
  for a new row: **a row a margin saw is a fit row**; mark it `fit`, or where only some of its events
  were fitted on, leave it `test` and add the line.

**Promise-only files.** A row of a `<group>-envelope.tsv` file (written by `generateEnvelopeRows`,
never by hand; `checkEnvelopeRows` says when one is behind) or of a `<group>-promise.tsv` file (kept
by hand, ruling R247) is held to the promise alone: an early start or a late end breaks the gate, no
late limit applies, and its days are in no stamp and its table in no count of published tables. A
`-promise.tsv` file is for a table no place is known to follow, a calculator's own table for a
point, say, which must still never find a start early there; its header says what the table is and
why it is not the entry's own row. A held table is never left out of the gate without a line saying
why.

    ./gradlew -p tools/timetables jvmTest gate [-PgateGroup=sg-muis] [-Pentry=sg.muis] [-Pfit=sg.muis]

runs NeverEarlyGateTest and the report, and writes `stamps/<entry>.json` (statistics only, never a
time). `archive/…` tables are read from the archive root: this folder in the checkout being tested
(its `archive/` restored from the local-only branch above), or `-Pofficial=<dir>` /
`TAQWA_OFFICIAL`. `open/…` tables are read from the checkout being tested, so a newly committed open
table is the one checked. Where no root is given and this folder holds no `archive/`, the gate and
the tests stop at once with a message saying how to restore it, except on CI (`CI` set), where every
row is skipped and the gate checks "0 rows"; an explicit root that does not exist is skipped the same
way. Where the root holds its archive, a table it does not hold, or one that yields no day, fails.

## The sun-sanity pass

What needs no table: every app city (`cities.csv`) under Automatic, every registry unit's point
and every region rule's own point, and each timetable Settings offers (every Other method `other.*`
included) at a spread sample of the app cities where it applies and Automatic follows another entry
(40 cities an entry, 400 an Other method, at the default high-latitude setting), every day of a
window (by default today through 13 months on) and both Asr schools, held to the real sun at the
point (the exact sun, sea level, standard refraction, as the engine's own real sunset): no Dhuhr
before transit, no Maghrib before sunset, no Fajr or end of eating after sunrise, no Isha at or
before Maghrib. The first two are the engine's own rule since ruling R180 (the real sun at the
user's point is the floor of Dhuhr and of Maghrib, `DayPipeline.onRealSun`): the pass checks them
outside the pipeline with the same sun model as the floor (so it proves that nothing leaves the
pipeline around the floor, not the astronomy: the model itself is held against a separately
written sun by the app's `RealSunModelTest`), either one found or listed fails it outright, and
the report counts instead what the floor moved (place-days and worst minutes later, per entry,
and the days its raise moved another time along with it). A breach of the
others that the day itself accounts for (an authority time it declares it does not show, ruling
R82; a time a high-latitude or clock rule set; a polar day) is excused; every other is a finding.
Both are pinned, each with a ceiling on its worst seconds and on its points:
`sun-sanity/open-findings.tsv` (committed, none open since R180) lists the known findings, each
with its fix, and `sun-sanity/excused.tsv` the excused groups. A group not listed, or one past a
ceiling, fails.

    ./gradlew -p tools/timetables sunSanity [-Pfrom=<today>] [-Pto=<13 months on>] [-PeveryCity=1]
        [-PchosenPerEntry=40] [-PotherPerEntry=400] [-Pout=<file>]

It needs no archive (`SunSanityTest` runs a sample of it in `jvmTest`).

## Surveys

A cautious entry whose members are families of mosque timetables rather than tables printed for one
place cannot be gated under ruling R38, which needs every member's printed table at the same place and
date. It is held to the promise by a survey instead: `survey/<entry>/` (committed) lists mosque
calendars kept in the archive (`calendars.tsv`: path, the mosque's own point, the columns), the cells
each gets wrong and why (`faults.tsv`: dates and columns only), and the cases no member absorbs at a
fair cost for everyone (`outliers.tsv`: the days and the worst minutes each may reach, and why). Each
calendar is checked on its own at its own point: no start before its time, no sunrise or end of eating
after it, no day out of order, no Isha without an end, except what `outliers.tsv` records.

- `survey/gb-cautious/` (`UkMawaqitSurveyTest`): the UK outside London against the 71 Mawaqit
  calendars research-uk surveyed (ruling R75), kept in `archive/tables/uk-mawaqit/` (their MANIFEST
  rows are `GB-MOSQUE`). Without the archive it is skipped and says so, like the gate.
- `survey/fr-cautious/`, `be-cautious/`, `nl-cautious/`, `de-cautious/` and `ca-cautious/`
  (`ContinentalMawaqitSurveyTest`): France (Lyon and Marseille), Belgium, the Netherlands, Germany and
  Canada outside its gated cities against the 54 Mawaqit calendars research-mawaqit spot-checked
  (ruling R87), kept in `archive/tables/<cc>-mawaqit/` (MANIFEST rows `FR-MOSQUE`, `BE-MOSQUE`,
  `NL-MOSQUE`, `DE-MOSQUE`, `CA-MOSQUE`). Canada's `calendars.tsv` carries a `zone` column, each
  mosque's own (three across the country).
