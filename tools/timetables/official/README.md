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

- `MANIFEST.tsv` (committed) lists all 794 tables, 210,410 days: where each came from, the dates
  it covers, and its licence class.
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
| `columns` | each time column's meaning: `F` Fajr, `S` sunrise, `D` Dhuhr, `A` Asr in the row's school, `As`/`Ah` the Standard/Hanafi Asr, `M` Maghrib, `I` Isha, `E` the end of eating, `Im` imsak, `-` not checked; `F+E` for a column that is both |
| `format` | the reader: `daily` for the layout above; a new layout is one `object <Name>Format : OfficialFormat` in `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/gate/formats/`, named here in kebab case (`twelve-hour` → `TwelveHourFormat`) |
| `school` | the school of the `A` column: `standard`, `hanafi`, or `-` |
| `split` | `fit` (margins are fitted on it) or `test` (held out) |
| `note` | free text |
| `member` | optional; for a cautious (class C) entry, the member id whose printed table the row is. Each start is checked against every member, Maghrib against the most-followed member's alone where the members' printed Maghribs spread beyond 2 minutes (ruling R38) |

Each event of a row is held to a late limit: the `LateLimit` its unit records for that event, else
the one its entry records, else its class's (A 1, B 2, C 1 after the latest member, D 3). An
exception lives in the registry with its reason and the events it covers (rulings R37, R41), never
in a gate file, so that the gate proves and About states the same number. Nothing excuses an early
start or a late end.

    ./gradlew -p tools/timetables jvmTest gate [-PgateGroup=sg-muis] [-Pentry=sg.muis] [-Pfit=sg.muis]

runs NeverEarlyGateTest and the report, and writes `stamps/<entry>.json` (statistics only, never a
time). `archive/…` tables are read from the archive root: this folder in the checkout being tested
(its `archive/` restored from the local-only branch above), or `-Pofficial=<dir>` /
`TAQWA_OFFICIAL`. `open/…` tables are read from the checkout being tested, so a newly committed open
table is the one checked. Where no root is given and this folder holds no `archive/`, the gate and
the tests stop at once with a message saying how to restore it, except on CI (`CI` set), where every
row is skipped and the gate checks "0 rows"; an explicit root that does not exist is skipped the same
way. Where the root holds its archive, a table it does not hold, or one that yields no day, fails.

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
