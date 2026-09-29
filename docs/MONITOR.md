# The weekly timetable monitor

The prayer-time engine promises never to show a start before the authority's (spec §1). The proof
is the gate over the tables held in the restricted archive (spec §5, `tools/timetables/official/README.md`).
Authorities publish new tables every year, sometimes change a method, and the engine carries dates
that run out; the monitor is the weekly, unattended check that keeps the promise from quietly
going stale (spec §5, "the monitor"). It runs in the cloud, from a private repository that holds
the archive (ruling R94), stays silent when all is well, and keeps one issue open while something
needs attention, so that a Claude session can act on it.

## What it does

`scripts/monitor.sh` runs four steps, from any checkout of the repository, on macOS or Linux:

1. **Fetch** (`tools/timetables/monitor/fetch.py`): each source's newest published table, politely
   (one User-Agent naming the monitor, at least 1.5 s between requests whatever the host, one
   retry; a host that fails three times in a row is not tried again that run; the run has a time
   budget of 90 minutes), normalised into the gate's layout under `<official>/archive/tables/monitor/<source>/`,
   with a content hash so an unchanged table is recognised. The raw responses of new or changed
   tables are kept gzipped under `archive/raw/monitor/<source>/<date>/`. A source's cadence is
   weekly, monthly or month-start; one with cadence `manual` runs only when named; a source whose
   fetcher is `manual` is read by hand. A month-start source is a page that shows the current month
   alone and drops it when the next begins (the MJC's): it is due as soon as a new month has begun
   in Africa/Johannesburg since its last complete fetch, so it stays due until that month is
   captured whole, and each month is kept as its own capture. A fetcher that fails is a finding in
   the report, never a crash, and a driver that breaks is one too (what is held is still checked). A
   source that failed or came back in part is fetched once more on the next run, then waits for its
   cadence (one mosque gone from Mawaqit for good does not refetch all 125 calendars weekly; a
   month-start source whose month is not captured yet is due on every run anyway); its finding names
   the missing part and says which of the two it is.
2. **Backup** (`tools/timetables/monitor/backup.py`): only where `TAQWA_OFFICIAL_BACKUP` names a
   folder (the owner's Mac): every new or changed file of the archive is mirrored there, never
   deleting anything, and the report reminds him to upload it to his Drive when files were added.
   In the cloud the private repository is the backup and this step is skipped. A configured mirror
   that does not run is attention.
3. **Check** (`./gradlew -p tools/timetables monitor`, JVM only): every table whose signature
   changed since it was last checked (its content, its metadata, or the engine itself, so an engine
   or registry change re-checks every held table), and every table red last run, goes through the
   gate's machinery at its own point: as the entry Automatic follows there (a cautious entry's
   member row where the table is a member's, and beside it the member's own entry; the table's own
   entry where Automatic follows something else, Diyanet's London table inside the M25; a table
   naming a unit at the unit's point, plus the member row of the cautious entry Automatic follows
   there). A mosque calendar goes through its survey, with the faults and outliers recorded for it.
   Then the whole gate and every survey, the built-in data horizons, and the manual sources' due
   dates.
4. **Report**: `<official>/monitor/reports/<date>.md` and `latest.md`. One summary line is printed
   with the report's path; exit 0 all green, 1 attention needed, 2 the monitor itself failed.
   `last-run.json` says whether the attention set changed since the last notification: only then
   does the Mac post a notification, or the cloud run open or update its issue.

Nothing fetched is ever committed to the public repository: the archive is restricted (ruling R69),
and the reports quote dates, counts and minutes, never a printed time. The committed parts are the
code, the catalogue `tools/timetables/official/monitor/sources.tsv` (ids, cadences, points), the
workflow template and the tests.

## How the report classifies (ruling R93)

Each finding line is classified on its own, not each table:

| Tier | Kind | Meaning | Usual fix |
|---|---|---|---|
| 1 | **Never early** | An early start, an end (sunrise, end of eating) after the authority's, a day out of order, or a survey finding not recorded or past its record — in a fetched table, the gate or a survey. Attention on every run until fixed or recorded; the section opens with a one-line index of them | Never excused. If the authority changed its method or its point, rebuild it in the registry and refit the margins (`./gradlew -p tools/timetables gate -Pfit=<entry>` with the table as a `split=fit` row); if one stray day is the authority's own slip, record it with its reason (a `LateLimit` covers lateness only). For a mosque calendar, decide as the surveys did (rulings R75, R87): a family of mosques becomes a member, a stray day an outlier in `outliers.tsv`, a wrong cell a fault in `faults.tsv` — a cell the monitor recognises as the calendar's own slip (20 min or more off both its neighbours, or exactly an hour or twelve hours off the survey's held capture) comes with its `faults.tsv` row ready to paste. Then add the ready-to-paste gate rows (after copying the live table to the dated path they name), regenerate the stamps and `ProofStamps.kt`, commit |
| 2 | **Fetch broken** | An endpoint moved, changed shape or refused the request; the run's budget was spent; or a table could not be read or checked | Read the message and the raw response; fix the fetcher in `tools/timetables/monitor/fetchers/`; if the site stays closed, make the source `manual` in `sources.tsv` with the month its next edition is expected |
| 2 | **Backup did not run** | The mirror was configured (`TAQWA_OFFICIAL_BACKUP`) and did not run | Point it at the folder, or unset it where the private repository is the backup |
| 3 | **Horizon** | A built-in date runs out: Umm al-Qura's dates (from six months before 31 Dec 2030), a clock-change entry's expiry (eight weeks before), London Unified's or IRN's current year not held (and, from 1 December and 1 November, the next), a proof older than twelve months, or from eight weeks before Ramadan an A/B entry whose proof does not cover it | Fetch the calendar named and add its gate rows; regenerate `UmmAlQuraDates` from the API's raw JSON; review `ClockChanges.table`. A proof older than twelve months stays quiet while a manual source in `sources.tsv` names the entry with a `next_expected` date still to come |
| 3 | **Over the late limit in the gate** | Lateness over the recorded limit, or a capped Maghrib left unchecked, in the full gate: the stamp is red | Refit the margins or record the exception in the registry (rulings R37, R41); hold the most-followed member's table at that point |
| 4 | **Over the late limit** (low) | Lateness over the recorded limit on a fetched table's own entry: raised the first time and whenever it worsens — a higher worst in minutes, or an event over the limit for the first time; a table that merely grew (more cells at the same worst) is never worse (ruling N2) | Refit, or record the exception. Afterwards it is carried under "Still open since <date>" without raising the exit code, and the table is checked again every run until it is green |
| 5 | **Manual source due** | A source read by hand whose next edition was expected by now | Fetch it by hand, normalise it into the archive, add its gate rows, re-run the gate, move `next_expected` on |
| — | **Informational** | "Still open" (above); "For the record": lateness measured on a member row (a cautious start waits for the latest member: the spread between members, not an engine error; the member's own entry is checked beside it), and a capped Maghrib left unchecked because the most-followed member's table is not held at that point (a coverage gap); "Note": the archive repository's workflow file differs from the public template | Nothing, or hold the missing member's table; copy the workflow template over |
| — | **Green** | New table, fine (a gate row is ready to paste); Changed table, fine; Green again (red last run); Checked again, fine (one line for all the held tables an engine change or `--check-all` re-checked) | Optional: add the row to the gate file (or the calendar to its survey) so the proof grows |

The summary leads with the never-early count ("2 never-early failures (…), 3 other items (…)").
The exit code is 1 on any attention. The notification, and the cloud issue, follow only when the
attention set differs from the one last notified: a new item, an item gone, or a never-early item
whose failing days or worst minutes grew. Every table is checked again when its content, its
metadata, the engine, the checker's own code or (for a calendar) its survey's fault and outlier
rows changed, and every run while it is red.

**Partial runs (ruling N1).** `--only <source>` checks one source alone, so it cannot see the whole
attention set: its report is written beside the day's as `reports/<date>-partial-<source>.md`,
`latest.md` and the notified attention set are left as they were, no notification is posted, and
in the cloud the issue is neither closed nor rewritten by it (a comment at most). The same holds for
`-PskipFull=true` (the gate and the surveys left out).

## Running in the cloud (ruling R94)

The monitor runs weekly as a GitHub Actions job in a **private** repository that holds the
restricted archive and the monitor's state. Setting it up, once:

1. Create the private repository `MohamedAbulgasem/Taqwa-official` (empty, no README).
2. In `~/Desktop/Workspace/apps/Taqwa-official` (the folder that holds `archive/` and `monitor/`):
   `git init`, add a `.gitignore` with `monitor/lock/` (the run lock), `.*.tmp` (the atomic writers'
   temp files), `monitor/keys.env` and `.DS_Store`, commit `archive/` and `monitor/` (the state
   files hold paths relative to this folder, so the same state serves the Mac and the runner), and
   push to the new repository. Never push it anywhere public, and never add it as a remote of the
   public checkout.
3. Copy `tools/timetables/monitor/ci/monitor-weekly.yml` from the public repository to
   `.github/workflows/monitor-weekly.yml` there and push.
4. Optional: add the repository secret `LPT_API_KEY` (Settings › Secrets and variables › Actions)
   once London Prayer Times has issued a key; the fetcher reads it from the environment. Nothing
   else is secret.
5. Run it once by hand: Actions › Taqwa monitor › Run workflow (the `options` field takes only
   `--no-fetch`, `--check-all`, `--verbose` and `--only <a source id of sources.tsv>`; anything
   else stops the job). The first run exercises every fetcher from GitHub's addresses; a site that
   refuses them stays `manual` in `sources.tsv`. Remember that `--only` is a partial run.

Each run (Mondays 03:00 UTC, the 1st of each month 03:17 UTC, and on request) checks out the
private repository at its branch's head and the public one at `main` into `taqwa/` (neither
checkout keeps a token; the commit step alone is given one), installs Temurin 21
(with Gradle caching keyed on `gradle/libs.versions.toml` too) and Python, runs
`taqwa/scripts/monitor.sh --verbose` with `TAQWA_OFFICIAL` set to the workspace, then commits what
the run added or changed under `archive/` and `monitor/` (new captures, the state, the report) and
nothing else, rebases it onto whatever arrived meanwhile (a run by hand, a queued run) and pushes,
retrying once; when the push still fails the changes are kept as the artifact
`monitor-run-changes-<run id>` and the job fails, so nothing captured is lost. The job fails
whenever the monitor itself failed: an exit other than 0 or 1, or a result record
(`monitor/last-run.json`) missing or older than the run's start. Every action is pinned by commit.

**The run on the 1st.** The MJC publishes only the current month and its page drops it when the
next begins, so the workflow also runs at 03:17 UTC on the 1st of each month (two hours later in
Cape Town, after the page has turned), when the MJC's source is due for its new month. It is a normal full
run, not a partial one: whatever else is due is fetched too (a source whose retry is pending, which
then counts its cadence from the 1st; on a Sunday the 1st, the weekly sources), and the report and
the issue follow the same rules as on a Monday. So a fetch failure, which is a finding of the run
that fetched, drops out on the 1st when its source is not due that day (the issue is updated, or
closed if that was all, and the next Monday raises it again); and when the 1st is a Monday the two
runs queue, the second finds nothing new due and rewrites that day's report (the first's stays in
the repository's history).

**The issue.** One issue titled "Taqwa monitor", labelled `monitor-attention`, is open while
something needs attention. Its body (at most 60,000 characters, always ending with the pointer to
the full report) is the report's summary, the never-early index and each attention item's title
(dates, counts and minutes, never a printed time); the full report is `monitor/latest.md` in the
private repository. It is created or updated only when the attention set changed (always when the
monitor itself failed, whose body names the run's own log and report); on the first sound full run
after a failure the body is rewritten from the report and a "Recovered" comment added; a green full
run closes it with a one-line comment and never opens one; a partial run (`--only`), sound or
failed, adds a comment at most; refused dispatch options stop the job before anything runs and
touch nothing. The same body is the job's step summary. When the private repository's
`.github/workflows/monitor-weekly.yml` differs from the public template, the report says so under
Informational. A Claude routine fired by that issue prepares fixes as pull requests (not set up
yet).

## Running by hand on the Mac

    scripts/monitor.sh                          fetch, back up, check, report
    scripts/monitor.sh --no-fetch               check what is held (the gate, the surveys, the horizons)
    scripts/monitor.sh --only sa-ummalqura      one source: a partial run (its own report, nothing notified)
    scripts/monitor.sh --no-fetch --check-all   every held table again
    scripts/monitor.sh --verbose                with each step's progress

    python3 tools/timetables/monitor/fetch.py --official <root> [--only <source>] [--force] [--budget-minutes N]
                                              [--now <ISO 8601 moment: a month-start source's clock, for a test>]
    ./gradlew -p tools/timetables monitor -Pofficial=<root> [-PcheckAll=true] [-PskipFull=true] [-Ptoday=yyyy-mm-dd]
    python3 -m unittest tools/timetables/monitor/tests.py
    ./gradlew -p tools/timetables jvmTest --tests 'world.taqwa.timetables.monitor.*'

`TAQWA_OFFICIAL` is the root (default `~/Desktop/Workspace/apps/Taqwa-official`, the folder that
holds `archive/` and `monitor/`; the `monitor` Gradle task refuses to run without it or
`-Pofficial`, so that no state lands in the checkout); `TAQWA_OFFICIAL_BACKUP` an optional mirror
folder; `TAQWA_PYTHON` the interpreter (else Homebrew's `python3`, else the first on `PATH`;
3.9 or later, standard library only); `JAVA_HOME` the JDK (else macOS's own lookup for 21). Two
runs never overlap (`monitor/lock`). The Python code and its tests run under both the Mac's
`/usr/bin/python3` (3.9, LibreSSL) and Homebrew's; a site the Mac's TLS stack cannot reach goes
through `curl`. A notification is posted on macOS when the attention set changed, and always when
the monitor itself failed; every early exit leaves `monitor/last-run.json` saying why (exit 2),
a closed terminal or a `kill` included (the fetch or check still running is stopped with it),
except a run refused by the lock. The Mac and the cloud share one state through the private
repository: **pull it before a run by hand** (`git pull` in `~/Desktop/Workspace/apps/Taqwa-official`),
and **commit and push `archive/` and `monitor/` after**, so that neither side overwrites the
other's `state.json` and `hashes.json`. A London key in `keys.env` (or the cloud's secret) is
stripped, URL-quoted, and masked wherever the run writes.

The state lives in `<official>/monitor/`: `hashes.json` (content hashes, last-fetch dates, each
table's metadata), `state.json` (each table's check record and open lateness, the attention set
last notified, the last backup reminder), `fetch/<date>.json` and `.log` (each run; logs older than
90 days are pruned), `backup.json`, `notes.json` (the shell's notes about the run's setup),
`last-run.json`, `diyanet-ids.json` (resolved district ids; a corrupt cache is discarded and
rebuilt), and on the Mac `keys.env` for a key the owner holds (`LPT_API_KEY`), never in any
repository.

## Where things live

- Code: `scripts/monitor.sh`; `tools/timetables/monitor/` (`fetch.py`, `backup.py`, `common.py`,
  `fetchers/*.py`, `tests.py`, `ci/monitor-weekly.yml`); `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/monitor/`
  (`MonitorMain`, `TableCheck`, `Horizons`, `Report`, `MonitorIndex`) and the `monitor` task in
  `tools/timetables/build.gradle.kts`.
- Catalogue: `tools/timetables/official/monitor/sources.tsv` (committed, metadata only).
- Archive: `<official>/archive/tables/monitor/` and `archive/raw/monitor/` (restricted, never in the
  public repository); the private repository holds it all.
- State and reports: `<official>/monitor/`.

## Sources

`sources.tsv` is the list. As of 30 September 2026 the fetchers cover Umm al-Qura, Diyanet
(Türkiye and nineteen European cities, Antwerpen, Gent, Lyon, Lille, Copenhagen, Helsinki and
Trondheim among them), Kemenag (eighteen kab/kota, Surabaya, Medan, Semarang, Palembang and
Yogyakarta among them), JAKIM, MUIS, Egypt (ESA via Dar al-Ifta, and ESA's daily page), Qatar (the
ministry API and the Calendar House header), Libya (the Awqaf widget and api.ifta.ly), Tunisia (INM),
Morocco (Habous), Jamiatul Ulama, the Muslim Judicial Council (mjc.org.za's current month for Cape
Town, month-start, each month kept as `za-mjc/cape-town-<yyyy>-<mm>` and checked as za.mjc at its
unit and as za.cape's member there), IRN (bonnetid.info's own month tables, no token, ruling R95) and
the surveyed Mawaqit calendars. IACAD Dubai (Cloudflare refuses scripted access), the Calendar
House's printed calendar and Ramadan imsakiya (PDFs), and Sudan's, Gaza's and Mauritania's tables
are read by hand. London Unified waits for the owner's London Prayer Times key: with it,
`--only gb-london-lupt` reads the coming year; without it the horizon reminds from 1 December.
