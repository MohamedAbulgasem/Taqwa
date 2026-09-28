# The weekly timetable monitor

The prayer-time engine promises never to show a start before the authority's (spec §1). The proof
is the gate over the tables held in the restricted local archive (spec §5, `tools/timetables/official/README.md`).
Authorities publish new tables every year, sometimes change a method, and the engine carries dates
that run out; the monitor is the weekly, unattended check that keeps the promise from quietly
going stale (spec §5, "the monitor"). It stays silent when all is well and writes a report a Claude
session can act on when something needs attention.

## What it does

`scripts/monitor.sh` runs four steps, from any checkout of the repository:

1. **Fetch** (`tools/timetables/monitor/fetch.py`): each source's newest published table, politely
   (one User-Agent naming the monitor, at least 1.5 s between requests, one retry), normalised into
   the gate's layout under `<official>/archive/tables/monitor/<source>/`, with a content hash so an
   unchanged table is recognised. The raw responses of new or changed tables are kept gzipped under
   `archive/raw/monitor/<source>/<date>/`. A source's cadence is weekly or monthly; a `manual` source
   is never fetched. A fetcher that fails is a finding in the report, never a crash.
2. **Backup** (`tools/timetables/monitor/backup.py`): every new or changed file of the archive is
   mirrored into the backup folder, never deleting anything there. The owner uploads that folder to
   his Drive by hand: the report reminds him when files were added since the last reminder.
3. **Check** (`./gradlew -p tools/timetables monitor`, JVM only): each new or changed table goes
   through the gate's machinery at its own point, against the entry Automatic follows there (a
   cautious entry's member where the table is a member's; the table's own entry where Automatic
   follows something else, Diyanet's London table inside the M25; a mosque calendar through its
   survey, with the faults and outliers recorded for it). Then the whole gate and every survey, the
   built-in data horizons, and the manual sources' due dates.
4. **Report**: `<official>/monitor/reports/<date>.md` and `latest.md`. One summary line is printed
   with the report's path; exit 0 all green, 1 attention needed, 2 the monitor itself failed, and a
   macOS notification on 1 or 2.

Nothing fetched is ever committed: the archive is git-ignored and restricted (ruling R69), and the
reports quote dates, counts and minutes, never a printed time. The committed parts are the code,
the catalogue `tools/timetables/official/monitor/sources.tsv` (ids, cadences, points) and the tests.

## Running it by hand

    scripts/monitor.sh                       fetch, back up, check, report
    scripts/monitor.sh --no-fetch            check what is held (the gate, the surveys, the horizons)
    scripts/monitor.sh --only sa-ummalqura   one source (a manual-cadence one runs only when named)
    scripts/monitor.sh --no-fetch --check-all   every held table again (a run checks the new, the
                                             changed, and the ones red last run until they are green)

    python3 tools/timetables/monitor/fetch.py --official <root> [--only <source>] [--force]
    ./gradlew -p tools/timetables monitor -Pofficial=<root> [-PcheckAll=true] [-PskipFull=true] [-Ptoday=yyyy-mm-dd]
    python3 -m unittest tools/timetables/monitor/tests.py
    ./gradlew -p tools/timetables jvmTest --tests 'world.taqwa.timetables.monitor.*'

`TAQWA_OFFICIAL` is the archive root (default `~/Desktop/Workspace/apps/Taqwa-official`, the folder
that holds `archive/`); `TAQWA_OFFICIAL_BACKUP` the backup folder (default the 27 Sep 2026 backup
beside it). The state lives in `<official>/monitor/`: `hashes.json` (content hashes, last-fetch
dates, each table's metadata), `state.json` (the last backup reminder), `fetch/<date>.json` and
`.log` (each run), `backup.json`, `last-run.json`, `diyanet-ids.json` (resolved district ids), and
`keys.env` for a key the owner holds (`LPT_API_KEY` for London Prayer Times), never in the repository.

## What each finding means, and its usual fix

| Finding | Meaning | Usual fix |
|---|---|---|
| **Early or late** — a fetched table | The engine shows a start before the authority's printed minute, an end after it, or lateness over the recorded limit, on a table fetched this week | Never excused. If the authority changed its method or its point, rebuild it in the registry and refit the margins (`./gradlew -p tools/timetables gate -Pfit=<entry>` with the table as a `split=fit` row); if one stray day is the authority's own slip, record it with its reason (a `LateLimit` covers lateness only). Then add the ready-to-paste gate row, regenerate the stamps and `ProofStamps.kt`, commit. |
| **Early or late** — the gate or a survey | The engine or the registry changed under a held table | `./gradlew -p tools/timetables gate -Pentry=<entry>` shows the days; fix the method, never the table. |
| **Early or late** — a mosque calendar | A surveyed Mawaqit calendar changed and the cautious entry is early or late against it | Decide as the surveys did (rulings R75, R87): a family of mosques becomes a member, a stray day an outlier in `outliers.tsv`, a wrong cell a fault in `faults.tsv`. |
| **Fetch broken** | An endpoint moved, changed shape, or refused the request; or a table could not be read | Read the message and the raw response; fix the fetcher in `tools/timetables/monitor/fetchers/`; if the site stays closed, make the source `manual` in `sources.tsv` with the month its next edition is expected. |
| **Horizon** | A built-in date runs out: Umm al-Qura's dates (from six months before 31 Dec 2030), a clock-change entry's expiry (eight weeks before), London Unified's next year not held by 1 December, IRN's by 1 November, a proof older than twelve months, or from eight weeks before Ramadan an A/B entry whose proof does not cover it | Fetch the calendar named and add its gate rows; regenerate `UmmAlQuraDates` from the API's raw JSON; review `ClockChanges.table`. A proof older than twelve months stays quiet while a manual source in `sources.tsv` names the entry with a `next_expected` date still to come. |
| **Manual source due** (low) | A source read by hand whose next edition was expected by now | Fetch it by hand, normalise it into the archive, add its gate rows, re-run the gate, move `next_expected` on. |
| **New table, fine** (green) | A table not held before, checked and fine; a gate row is ready to paste | Optional: add the row to the gate file (or the calendar to its survey) so the proof grows. |
| **Changed table, fine** (green) | A held table's content changed (a day added, a reprint), checked and fine | Nothing. |

## Where things live

- Code: `scripts/monitor.sh`; `tools/timetables/monitor/` (`fetch.py`, `backup.py`, `common.py`,
  `fetchers/*.py`, `tests.py`); `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/monitor/`
  (`MonitorMain`, `TableCheck`, `Horizons`, `Report`, `MonitorIndex`) and the `monitor` task in
  `tools/timetables/build.gradle.kts`.
- Catalogue: `tools/timetables/official/monitor/sources.tsv` (committed, metadata only).
- Archive: `<official>/archive/tables/monitor/` and `archive/raw/monitor/` (restricted, never committed);
  backup: `<backup>/archive/`.
- State and reports: `<official>/monitor/`.

## Sources

`sources.tsv` is the list. As of 28 September 2026 the fetchers cover Umm al-Qura, Diyanet
(Türkiye and Europe), Kemenag, JAKIM, MUIS, Egypt (ESA via Dar al-Ifta, and ESA's daily page),
Qatar (the ministry API and the Calendar House header), Libya (the Awqaf widget and api.ifta.ly),
Tunisia (INM), Morocco (Habous), Jamiatul Ulama, IRN and the surveyed Mawaqit calendars. IACAD
Dubai (Cloudflare refuses scripted access), the Calendar House's printed calendar and Ramadan
imsakiya (PDFs) and London Unified (an API key issued by hand) are manual. Diyanet's tables for
Antwerpen, Gent, Lyon, Lille, Copenhagen, Helsinki and Trondheim, and Kemenag's for Surabaya,
Medan, Semarang, Palembang and Yogyakarta, are fetched as new data for a later fit: checked and
reported, not gated.
