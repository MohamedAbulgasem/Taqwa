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
   (one User-Agent naming the monitor, at least 2 s between requests whatever the host (1.5 s until 13 November 2026), one
   retry; a host that fails three times in a row is not tried again that run; the run has a time
   budget of 90 minutes), normalised into the gate's layout under `<official>/archive/tables/monitor/<source>/`,
   with a content hash so an unchanged table is recognised. The raw responses of new or changed
   tables are kept gzipped under `archive/raw/monitor/<source>/<date>/`. A source's cadence is
   weekly, monthly, month-start or hijri-month; one with cadence `manual` runs only when named; a
   source whose fetcher is `manual` is read by hand. A month-start source is a page that shows the
   current month alone and drops it when the next begins (the MJC's): it is due as soon as a new month
   has begun in Africa/Johannesburg since its last complete fetch, so it stays due until that month is
   captured whole, and each month is kept as its own capture. A hijri-month source is a page that shows
   the current Hijri month alone, from its first day, and turns on the day the new month begins, which
   the moon's sighting decides (Habous's): it is weekly, and due besides on every run while any of its
   tables' last day is today or one of the three days before (`MONTH_TURN_DAYS` in `fetch.py`): one
   city missing from a turn keeps it open, and a city a month behind never hides the next turn. A
   fetch whose newest table still ends more than three days ago (the page has not turned) is recorded
   as fetched in part, so it is a fetch finding in the report: retried on the next run, then weekly.
   Its tables keep every month fetched. A table's file keeps every day ever fetched (the store merges
   each capture into it), except for a source that re-issues days it already published (a table whose
   `editions` is set; Diyanet's, whose year table on 9 November 2026 was its 2026 edition again, whose
   takdir differs from the 2027 edition it served until 31 October, so the merged files mixed the two):
   each of its captures is kept whole as `<source>/editions/<key>/<capture date>.txt`, the live file holds
   the latest capture alone, and the source's message says what changed, in days (a re-issue: days changed,
   days still ahead withdrawn, or days long past put back; else the same edition moved on). The first such
   capture keeps the live file as it stood beside the editions as `<its fetched date>-merged.txt`.
   `tools/timetables/monitor/editions.py` rebuilds a source's editions from its raw captures (`--out` to
   look, `--in-place` to write them and the live files, refused unless the latest raw capture is the one
   the index records and agrees with the live file on every day both hold): the migration for Diyanet's
   files written before. Diyanet's tables do not set `editions` yet (`KEEP_EDITIONS` in
   `fetchers/diyanet.py`): the live file would then drop the 2027 days only an earlier edition holds,
   which the Diyanet Europe curves (`DiyanetEuropeCurveGenerator`, `DiyanetEuropeCrossYearTest`) still
   read; switch it on, and run the migration, once those read pinned copies. An editions capture that
   withdraws days still ahead without changing a held day and covers under half the held span (a page
   that came back with its month table alone) is an error, kept as its edition, the live file left alone.
   A fetcher that fails is a finding in
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
| 3 | **Horizon** | A built-in date runs out: Umm al-Qura's dates (from six months before 31 Dec 2030), a clock-change entry's expiry (eight weeks before), London Unified's or IRN's current year not held (and, from 1 December and 1 November, the next), a day that needs work of its own before it comes (Kyrgyzstan's 29 February 2028, from 1 December 2027 until the gate has read it: "Work by hand, by date" below), a proof older than twelve months (the entry's, or one unit's while the entry's is fresh), or from eight weeks before Ramadan an A/B entry with a unit whose Fajr, end of eating and Maghrib were not each checked on every day of it and the day either side — the stamp's runs, unit by unit and event by event, never its `first`..`last` envelope (ruling R145) nor the unit's days as a whole (ruling R148: a table that prints no Suhoor column proves no end of eating) | Fetch the calendar named and add its gate rows; regenerate `UmmAlQuraDates` from the API's raw JSON; review `ClockChanges.table`. A proof older than twelve months stays quiet while a manual source in `sources.tsv` names the entry with a `next_expected` date still to come. The Ramadan build refuses the same shortfall: `checkStamps -Pramadan=<Hijri year>`, which `TAQWA_RAMADAN=<year>` passes through `release.sh` and `ios-release.sh` (ruling R146) |
| 3 | **Over the late limit in the gate** | Lateness over the recorded limit in the full gate: the stamp is red | Refit the margins or record the exception in the registry (rulings R37, R41) |
| 4 | **Over the late limit** (low) | Lateness over the recorded limit on a fetched table's own entry: raised the first time and whenever it worsens — a higher worst in minutes, or an event over the limit for the first time; a table that merely grew (more cells at the same worst) is never worse (ruling N2) | Refit, or record the exception. Afterwards it is carried under "Still open since <date>" without raising the exit code, and the table is checked again every run until it is green |
| 5 | **Manual source due** | A source read by hand whose next edition was expected by now | Fetch it by hand, normalise it into the archive, add its gate rows, re-run the gate, move `next_expected` on |
| — | **Informational** | "Still open" (above); "For the record": lateness measured on a member row (a cautious start waits for the latest member: the spread between members, not an engine error; the member's own entry is checked beside it), and a member's Maghrib under the far-north guard (ruling R156: on a day its own far-north or winter rule sets it, or the real sun does not set there, a member that is not the most followed is checked at its Maghrib from the sun, or not at all, and the days and the gap to its printed time are recorded, as in the stamp's `maghribGuard`); "Note": the archive repository's workflow file differs from the public template | Nothing; copy the workflow template over |
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
4. Add the repository secret `LPT_API_KEY` (Settings › Secrets and variables › Actions) with the
   key London Prayer Times issues by hand (done 10 October 2026); the fetcher reads it from the
   environment. Nothing else is secret.
5. Run it once by hand: Actions › Taqwa monitor › Run workflow (the `options` field takes only
   `--no-fetch`, `--check-all`, `--verbose` and `--only <a source id of sources.tsv>`; anything
   else stops the job). The first run exercises every fetcher from GitHub's addresses; a site that
   refuses them stays `manual` in `sources.tsv`. Remember that `--only` is a partial run.

Each run (Mondays 03:00 UTC, the 1st of each month 03:17 UTC, the days a new Hijri month is due, and
on request) checks out the
private repository at its branch's head and the public one at `main` into `taqwa/` (neither
checkout keeps a token, and this job holds none that can write), installs Temurin 21
(with Gradle caching keyed on `gradle/libs.versions.toml` too) and Python, runs
`taqwa/scripts/monitor.sh --verbose` with `TAQWA_OFFICIAL` set to the workspace, then hands what
the run added or changed under `archive/` and `monitor/` (new captures, the state, the report) to the
clean `publish` job ("Publishing", below), which commits it and nothing else, rebases it onto whatever
arrived meanwhile (a run by hand, a queued run) and pushes, retrying once; the handoff is kept as the
artifact `monitor-handoff-<run id>` for 90 days, so nothing captured is lost when the push fails, and
the run fails then. The run fails
whenever the monitor itself failed: an exit other than 0 or 1, or a result record
(`monitor/last-run.json`) missing or older than the run's start. Every action is pinned by commit.

**Memory (ruling R280, the second audit's G17).** The monitor's check (`MonitorMain`'s whole gate), `prove` and the
tools' tests each run the whole gate in a forked JVM of 4 GB (`tools/timetables/build.gradle.kts`; on the merged line
of 14 November 2026 two gigabytes ran out in `Gate.evaluate`), beside Gradle's own daemon (2 GB,
`tools/timetables/gradle.properties`) and Kotlin's. Prove keeps one whole result at a time: what it needs of the base
gate's (the entries red, the checked timetables, the place-days) is kept and the result let go before the gate runs
again. A standard runner for a private repository has about 7 GB and some swap; the step "The runner's memory" writes
the runner's figures into the log and annotates one under 6 GB, and a check or a prove whose JVM ran out of heap says so
in its own failure (`monitor.sh`'s "the check step wrote no result ... the JVM ran out of heap", `prove.sh`'s "prove could
not run ... its JVM ran out of heap", ProveMain's report). Not yet seen on GitHub's runner at the line's full size: the
first run after the merge is the test, and a rehearsal dispatch before it is the cheap way to make it sooner.

**Time (ruling R280).** The monitor job has 350 minutes (GitHub stops a hosted job at 360). The memory step records the
job's start, and prove runs under `timeout` with what is left less 25 minutes, so a prove that would overrun is stopped,
put back in the step (its pinned copies under `archive/tables/pinned/` and every change in Taqwa's checkout), reported
as "prove ran out of the job's time" with code 2, and the handoff of what the monitor captured is still packed. prove's
errors stream to the log as they come. The job's length at the line's size is not yet measured on the runner; if prove
does not fit, it moves to a job of its own.

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

**The daily check.** Habous (`ma-habous`) published one Hijri month at a time, on the day it began,
and a city page shows only days the gate has checked, so the new month is worth fetching the day it
appears, not the Monday after. (Since November 2026 it posts whole Gregorian months a few days ahead,
which the weekly run reads with the current one; the daily check still catches a month posted only the
day before it starts, as Ramadan's may be.) The workflow therefore also fires every day at 01:23 UTC (after midnight
in Morocco), where a small first job, `due`, checks out only the archive repository's `monitor/` and
the public repository's monitor code and runs `fetch.py --month-turn`: it reads `monitor/hashes.json`
alone and prints each hijri-month source whose month is turning today. Only when it prints one does the
full run follow, a normal one like the 1st's (whatever else is due is fetched too, then the check,
prove and the push); on every other day the run stops there, touching nothing. Mondays, the 1st and a
run by hand never ask. With the sighting deciding between two days, the turn is due from a table's last
day: on that day itself a 29-day month has already turned (the page's 30th row is the new month's
first day), otherwise the next day's run finds it. Any table inside its turn keeps the source due, so a
city whose page failed keeps the turn open to its third day, and a city a month behind does not hide
the others' next turn. A page still on the old month after those three days is a fetch finding in the
issue, not silence. GitHub may start a scheduled run hours late; the new month is still captured
within about a day of its publication. The check costs about one billed runner-minute a day (each job
is rounded up to a minute), and a full run follows on two to four days of each Hijri month.

**The issue.** One issue titled "Taqwa monitor", labelled `monitor-attention`, is open while
something needs attention. Its body (at most 60,000 characters, always ending with the pointer to
the full report) is the report's summary, the never-early index and each attention item's title
(dates, counts and minutes, never a printed time); the full report is `monitor/latest.md` in the
private repository. It is created or updated only when the attention set changed (always when the
monitor itself failed, whose body names the run's own log and report); on the first sound full run
after a failure the body is rewritten from the report and a "Recovered" comment added; a green full
run closes it with a one-line comment and never opens one; a partial run (`--only`), sound or
failed, adds a comment at most; refused dispatch options stop the job before anything runs and
touch nothing. The same body is the job's step summary. The report job names its repository to `gh`
(`GH_REPO`), since its only checkout is Taqwa's monitor code under `taqwa/` and `gh` cannot infer one
(the first live run's issue step failed on that). When the private repository's
`.github/workflows/monitor-weekly.yml` differs from the public template, the report says so under
Informational. A Claude routine fired by that issue prepares fixes as pull requests (not set up
yet).

## Prove: new captures into proof, with no human

After a **full** run whose record is sound and green or attention (never a partial run, never when
the monitor itself failed), the workflow runs `taqwa/scripts/prove.sh --date <run date>`, which runs
the `prove` task (`tools/timetables/.../monitor/Prove.kt`) and then every check a release of the
proof needs. The owner chose to push the result **directly to Taqwa's `main`** when everything is
green.

**Recipes.** `tools/timetables/official/monitor/recipes.tsv` (metadata only: source ids, capture
keys, gate files, entries, members, points; never a printed time) says, per monitor source, how a
capture becomes gate rows: which gate file, which entry or unit (`index`: the capture's own entry
from the index; `same`: clone every row of that gate file that read an earlier capture of the same
source and key, its entry, member and point; or a named entry), which member (`index`: the capture's
entry as a cautious entry's member), which point (`index`, `unit`, `all` — every point the file reads
that entry and member at — or `lat,lon`), and which days (`all`; `month`, the month the capture's key
names, for a cautious entry's members of the month a member published; `excused`, without the days
the file's `@excuse` lines excuse for that member, ruling R117). `from` reads another capture for the
row (za.cape's Jamiat member from `za-jamiat/cpt-{yyyy}` for the MJC's month). Every row is
split=test. What a row reads — columns, zone, format, school, clock — is never in the recipe: it is
the family's, the latest row of the same file, entry, member and point that read an earlier capture
of the same source and key; else such a row whose table prints the same tokens as the capture on
every day both hold (the files prove the layout is the same); else such a row that reads another
capture of the same source (a new year's key, `cpt-2027` after `cpt-2026`: one fetcher, one layout,
and the index agrees when it still lists the old key); else, for a cautious member's row, the
member's latest row of the same width (the gate file's own convention for that member, such as a
Shafi'i reading of a two-school table, which the fetcher's metadata does not know); else the
fetcher's own metadata in the monitor's index. So a convention changed in a gate file (a column no
longer checked, a clock) is inherited by the next capture. A copied note keeps its words only when,
its capture date replaced, it names no other month or date (a range would describe the family's
days); otherwise the row's note is the capture's name and date. A capture that no recipe target
picks up (a city new to a fetcher, or an index line naming no entry) is reported as left out, and is
added by hand once. Not in the recipes: the hand-read sources, the Mawaqit surveys, and
members no fetcher reads (Cape Town's community calendar: masjids.co.za's relay is read by hand each
month; until it is, za.cape's members for a new MJC month are left out by the gate, as they should
be, and za.mjc's own row is added). Diyanet's Australian city tables (Sydney's and Melbourne's, read in
`au-cautious.tsv` and `au-diyanet.tsv`) are research captures of 25 September to 25 October 2026 that no
fetcher reads either, so those rows hold those 31 days until another season's capture is added by hand;
a fetcher for the two pages is a proposal (the Q4 review of 17 October 2026).

Umm al-Qura's and QMDB's units (since 9 October 2026, the review's "prove it across the unit") have a
line each, one per measured unit (259 in all): each new year of a unit's own table is added at every
point its reach file reads the unit at (`sa-ummalqura-reach.tsv`, `kz-qmdb-reach.tsv`; point `all`),
as well as at its own point (the source's `index` line). The pattern ends `-2*` so that one place's key
never names another's. A recipe grows the rows at every point the reach file reads, the fixed grid
and each event's worst point of the generator's sweep alike. The reach rows grow the whole gate to
some 11,000 rows and 4.0 million place-days, about 110 seconds on the Mac. The gate keeps only the
last 64 points' computed days at once, so it stays within its 2 GB. `generateReachRows` (archive
needed, about 5 minutes) is re-run when a unit, its reach or its tables change, or when an engine change can move
where a unit's worst lies (ruling R321: once a false sun crossing was mended, DUM RT's sweep-worst points, chosen on
those nights, no longer held the worst, and the offset grids of `CityUnitsTest` read past the limits they gave), and
`generateOwnPointWorst` (seconds) when a change is meant to move an own point's lateness, or to hold the
own-point rows prove added to their own worst rather than to the documented limits alone.

Since the city-point audit (10–11 October 2026), ISNA's mosques in the United States and Canada and BIK's
base table are units too, gated across their reach the same way (`us-isna-reach.tsv`, `ca-isna-reach.tsv`,
`xk-bik-reach.tsv`, written by the same `generateReachRows`; about 12 minutes for all five now). Their tables
are the research's hand-read captures, which no fetcher reads, so no recipe grows them: a new year of one of
those tables is added by hand at its own point, and `generateReachRows` re-run, then `generateEnvelopeRows`
(seconds, no archive needed). That one writes the envelope's promise-only rows (round 2's fix, 11 October 2026,
`official/gate/<group>-envelope.tsv`): every table printed for its own point at every app city within its bound
reach, whichever table the city follows, and the round's named claims (the Takvim in Kosovo, Kénitra's table at
Sidi Taibi, Lakemba's at Sydney's west, LUPT and IACAD chosen outside their areas, and since 17 October 2026
Diyanet's Sydney table where its preset is chosen around Sydney, `au-diyanet-envelope.tsv`). The gate checks those rows for
the promise alone, so a recipe never targets an envelope file; it is regenerated from the group files instead. A run is a pure
function of the gate's group files, the registry and the app's cities: a second run on the same tree writes nothing. An envelope
file falls behind only when a group file gains rows and nobody runs the task after, as `us-chicago-envelope.tsv` did when the prove
of 1 November 2026 added the mosques' November rows to `us-chicago.tsv` (517 rows behind by 13 November). `./gradlew -p
tools/timetables checkEnvelopeRows` (13 November 2026) writes nothing, names every envelope file a run would change with the rows it
would add and drop, and fails if there is one; it is one of the checks of a release, and of any change to a group file with units.
Since the second trust audit (G24, 14 November 2026) `scripts/release.sh` and `scripts/ios-release.sh` run it after
`checkStamps` and refuse to build while it names a file, and `scripts/prove.sh` runs it right after `prove`: where the proof
changed what a run would write, it writes the files with `generateEnvelopeRows`, gates them (`-PgateGroup=<the envelope files>
-Pstamps=false`; promise-only rows are in no stamp) and checks again, all before the stamps are read into `ProofStamps.kt`, and
puts everything back if any of it fails. The envelope files it writes are committed with the proof.
A file ending `-promise.tsv` (ruling R247, `safe-default-americas-promise.tsv`) is held to the promise alone the same way but kept
by hand: a table no place is known to follow, at its own point; the task never writes or removes it. Since 14 November
2026 `safe-default-europe-promise.tsv` (ruling R261) holds Habous's Sebta (ville 24) and Melilla (ville 40) months at
the app's Ceuta and Melilla and at Habous's points: October and November 2026 alone, since the monitor does not read
those two villes; a later month is added there by hand (the archive's `habous_units.py spain-rows` checks the fit),
and until one is, December onward, Ramadan 1448 included, is checked against no Habous print. The app's own cities inside
those units, and inside Canada's families', Chicago's 18° block's and the city tables' (Delhi, Tashkent,
Karachi, Bishkek, Manchester Central, Gaza, Beirut, Damascus, Baghdad), are rows of the group files
themselves, held to their own worst by `own-points-worst.tsv`; each Damascus city is read against both of the
Ministry's samples, since both are its one table. `own-points-worst.tsv` also holds the three cautious groups
whose limits that audit raised (`au-cautious`, `ie-cautious`, `tr-diyanet-europe`), so their own tables keep
their own worst. The whole gate is now some 13,000 rows and 4.4 million place-days, about two and a half
minutes on the Mac.

Since 13 November 2026 (ruling R181) ESA's 78 Egyptian towns are gated across their reaches too
(`eg-esa-reach.tsv`, 14,061 rows at 1,570 points, the same `generateReachRows`; `--args="--only eg.esa"` takes
under a minute), so the figure About shows for an Egyptian town is its whole reach's. The 30 towns Dar al-Ifta
republishes each have two recipe lines, by the fetcher's key (the town's unit id): the town's own row, and its
reach's, point `all`, so `prove` grows a town's reach rows beside its own point's a month at a time. The other 48
are printed by ESA's daily page alone, and since 13 November 2026 (audit finding F25; until then the fetcher read
their rows and threw them away) `egypt.py` keeps them too (`ESA_CITIES`): that page, asked for plainly, shows
today's row and no other, so their tables grow by one day a run, one day a week. Those days are a sample, six in
seven never compared, and they have no recipe line on purpose: the monitor's own check reads each at the town's
point every run (an early start or a late end is attention on every run, tier 1), but none becomes a gate row. A
stamp therefore never holds an isolated day, About's "checked through" never names a day between days nothing
compared, the gate does not grow by some 900 rows a week, and the Ramadan guard never takes the sample for the
month being watched: `MonthByMonth` (ruling R185 (b)) refuses as cover any table that holds fewer than 10 of the 30
days up to the last it prints, whatever the recipes say. What the 48 give up is prove's own demotion on a failing
sampled day; the weekly issue's tier-1 item is the defence there. ESA's other days stand behind the page's own
date form (a postback), which the monitor never drives: ESA's 2027 pages cannot be read ahead by the monitor, and
the manual line `eg-esa-2027` in `sources.tsv` goes due for them from 1 December 2026 ("Work by hand, by date"
below). When a hand-read row of a town changes, `generateReachRows` is re-run, as for ISNA's and BIK's. The towns'
own-point rows are held to their own worst by `own-points-worst.tsv` (group `eg-esa`). The whole gate is 79,731
rows and 12,658,856 place-days, with 2,490,406 more held to the promise alone, about seven minutes on a busy Mac.

Since the same day (ruling R213, audit finding F13) each Egyptian town's tables are also replayed past its reach,
out to twice the reach, where the town's table rides beside the edge: `eg-esa-envelope.tsv`, held to the promise
alone (never early, no end late), written by `generateEnvelopeRows` from `eg-esa.tsv`'s rows and never by a
recipe. It is a sample: eight bearings at three distances for each town, and the app's Egyptian cities in the
belt, on the days the towns' tables held when it was written. Since 14 November 2026 (rulings R260 and R262, the
second audit's G1 to G3) the same three files of the Kyrgyz, Egyptian and Omani units (`kg-default-envelope.tsv`,
`eg-esa-envelope.tsv`, `om-mara-envelope.tsv`) also hold rings past every reach, belt and ride, out to five minutes
of longitude from each unit's point, wherever the engine reads the nearest unit's table beside the edge (inside a
farther unit's bigger belt or ride too), and Egypt's app cities past every belt (Kom Ombo, Mallawi, Dayrut,
al-Qusiyah, Dayr Mawas, Zaafarana, an-Najaylah). A month `prove` adds to a town or locality is not replayed there
until `generateEnvelopeRows` is run again, so it is run at every release cut and at the yearly turn (it takes
seconds and needs no archive), and the diffs of all three files committed with the gate green. And `generateReachRows` now
writes Oman's reach rows too (`om-mara-reach.tsv`, `--only om.mara`; the localities' 20 km, rulings R166 and
R169), Bahla's among them since 14 November 2026 (ruling R254: Al Hamra's table rides in the north of Bahla's
circle, so Bahla's limit is 3 minutes for Asr, Isha and the end of eating, measured on 150 place-days of May to
July at two of its 13 points), and Türkiye's (`tr-diyanet-reach.tsv`, `--only tr.diyanet`, the districts' 15 km;
ruling R255 of the same day: 5,029 rows at 1,760 points of the 84 il centres, under a minute to write). The whole
gate is 96,887 rows and 13,796,075 place-days, with 4,312,374 more held to the promise alone, about eight and a
half minutes on a busy Mac.

**Türkiye's limits are the reach's (ruling R255).** A Turkish unit's limits are read from the gate's own stamp, each
event's worst over every row of the unit, the reach rows among them (`code/manual/tr-diyanet/limits.py` in the
archive writes `beside-limits.tsv`, `beside.py` writes `DiyanetBesideDistricts.kt` from it), so the figure About
shows for an il centre is its whole reach's: 2 minutes at 44 units, 3 at 39 and 4 at Ankara, where it was 1 at 18,
2 at 38, 3 at 27 and 4 at one. No unit says 1 minute any more: toward the edge of a 15 km reach the place's own sun
alone runs a minute past the centre's table (Şile, with no district beside it, reads 2), and beside a district
whose own table rides, up to 3 (Ankara's Fajr and end of eating 4). Never early: no start before the table and no
end after it at any of the 1,760 points.

What that leaves of class A's one minute, said plainly: it holds at the il centre's own point (each of the 238
own-point rows of the 84 units reads at most 1), and across the reach on 36 of the 588 unit-events; the other 552
carry a limit of their own, the reach's measured worst. Three things keep such a limit from becoming its own
excuse. (1) The own points keep their own worst in `own-points-worst.tsv` (groups `tr-diyanet` and `om-mara` since
that day), so a wider reach limit never hides a minute lost at the place a table is printed for; and a row `prove`
adds after that file was written, which has no line in it yet, is held to the worst of the rows already measured at
the same place (`CityUnitsTest.overItsPlace`, for `om.mara` and `tr.diyanet`), not to the reach's limit. (2) The
tools' test `a turkish limit is its reach's measured worst and none is over the ruling's ceiling` reads the stamp:
a limit must equal the measured worst (or the class's minute), so one widened by hand fails, and **none may pass
4 minutes**, the highest any unit reads (Ankara's Fajr and end of eating); a fifth minute anywhere is a new ruling,
never a run of `limits.py`. `an omani locality's own limit is its reach's measured worst` holds Oman's exceptions
(Bahla's among them) the same way. (3) `limits.py` refuses a stamp that was checked at fewer points than
`tr-diyanet.tsv` and `tr-diyanet-reach.tsv` name, so a gate run of one file (`-PgateGroup=tr-diyanet`) cannot
lower the limits.

**How Türkiye's reach rows grow.** Two recipe lines, read together (`official/monitor/recipes.tsv`, its last two):
`tr-diyanet * tr-diyanet-reach.tsv same * same all -` clones, for a new capture, every reach row that reads an
earlier capture of the same key, whatever the monitor's index says of it; `tr-diyanet * tr-diyanet-reach.tsv index
- all all -` adds the capture at every point the reach file reads its unit at, and needs the index to name the unit
(`tr.diyanet/<id>`). A row both lines call for is planned once. The second line is what the 62 il centres of
12–13 November need: their reach rows read tables kept by hand (`archive/tables/held/tr-diyanet/`), so no earlier
capture exists to clone, and the fetcher names each by its unit. **The index dependency:** nine keys of the
committed `archive/tables/monitor/index.tsv` (antalya, bursa, catalca, diyarbakir, gaziantep, izmir, konya, sile,
silivri) still name bare `tr.diyanet` with a point, from before they were units, and the 62 have no line until
`fetchers/diyanet.py` has run once; the workflow fetches before it proves, which rewrites all 84 by unit, and a
`prove` run by hand does the same (`fetch.py` first). Shown on a scratch copy of the archive and of the two gate
files on 14 November 2026 (real printed tables only: Ankara's and İzmir's newest rows taken out of the scratch,
Kayseri's hand-read table cut to January–June with the whole 2026 table as its capture), `prove --only tr-diyanet
--dry-run` wrote: "71 rows added, 0 left out; gate 6400 -> 6471 rows, 1252257 -> 1270381 place-days, green" —
Ankara (index by unit) its own row and 23 reach rows, İzmir (index still bare) its own row and 20 reach rows,
Kayseri (one of the 62) its own row and 23 reach rows. Without the `same` line the same run adds 51: İzmir's 20
reach rows are the ones missed. The tools' test `every measured unit's own tables are replayed across its reach`
asks for both lines and fails when a new own row has no reach rows.

**How fast they grow.** A reach row is added per point only for a capture that brings days the unit's rows lack,
and older pinned rows are never superseded (a pinned copy is the proof of what was read that day). The 22 older
units hold every day to 31 December 2027, so they add nothing until Diyanet prints further. The 62 will each add
one own row and their reach rows (1,300 or so together) on a weekly run that brings them new days, and with all 84
bringing new days a run adds about 1,760 reach rows; today the file holds 5,029, of which 3,083 read pinned
captures of the 22 (seven captures between 28 September and 11 November, while the page's month table moved forward). That is the worst
case: a capture a week for as long as each week's page prints days not yet held. The gate reads the rows in
seconds, and the file stays generated plus appended; folding a unit's older pinned rows into one when a later
capture holds all their days is a follow-up nobody has needed yet.

**A red to expect from Türkiye's reach rows.** The limits are what the replay of 14 November 2026 found, with
nothing to spare, and the 62 il centres of the round of 12–13 November were measured on their 2026 tables alone
(Diyanet's page prints no 2027 for now). Of the 22 older units, which hold 2026 and 2027, four have an event whose
2027 worst is a minute over its 2026 worst (five of their 154 unit-events, Ankara's end of eating 4 against 3
among them; the tools' test `a turkish reach read one year at a time is never early and the second year's move is
measured` reads each year by itself across the reaches and prints the figure, so it is measured again on every
run), so when the 62 units' 2027 days arrive expect about one unit in five to read a minute over a limit
somewhere in its reach: later, never early. `prove` then leaves that row out and demotes the unit (ruling R185,
below), and the tools' test `every measured unit's own tables are replayed across its reach` fails on the point
whose row was left out. The hand step is the limits' own procedure ("Türkiye's neighbouring districts" below):
`generateReachRows --args="--only tr.diyanet"`, the gate with `-Pentry=tr.diyanet` (it may stop over a limit; the
stamp is written), `limits.py`, `beside.py`, then take the unit's `@demoted` line out of `tr-diyanet.tsv`, the whole gate,
`generateProofStamps`, `generateOwnPointWorst`, `checkStamps` and the tests. An early start is never this case:
stop and fix the rule. A limit is never widened beforehand, because About prints it and a figure there is one
the gate has measured.

**A reach proof owed holds a release.** A unit left out of its reach file (`leftOut`) and a group still
`PENDING` are reaches measured later than the figure About shows for them (never early). `checkStamps` names each
on every run and passes; `checkStamps -Prelease=true`, which `release.sh` and `ios-release.sh` pass, refuses the
build until the list is empty: settle the unit's limit (said in About) or cut its reach, then `generateReachRows`
and the gate, and take the unit off the list. Bahla and Türkiye were settled on 14 November 2026 (rulings R254 and
R255). The second trust audit of the same day (G26) found the hold had read only its own list: Morocco, JAKIM,
Bangladesh, Algeria, DUM RT, Tunisia and Jamiat claimed figures no reach row had measured. Since ruling R270 the list
also names (1) every entry the gate checks whose measured units reach past their own points and that has no reach
spec, and (2) every reach limit over 4 minutes (R255's ceiling: "a fifth minute needs a ruling") that the owner has not
ruled on (`ReachRows.RULED`). Every gated entry with measured units now has its reach rows (Muhammadiyah has measured
units but no gate row and claims no figure, so it is not named), its units' limits raised to the worst measured where
it was over (`ReachLimits.kt`, written from the stamps by `generateReachLimits`).
Diyanet's European cities were `PENDING` from 14 to 16 November 2026: their reach rows met days north of Luleå the engine had to repair into order, an engine matter, not a
limit, until ruling R293 put them in order at the source and the rows joined the gate. Norway's IRN was `PENDING` from
14 to 17 November 2026: ruling R292 puts in order every night of Tromsø's reach that can hold Maghrib and Isha before the
next dawn, but at some points of the reach, the week before and after the midnight sun, the sun is down for a minute
or three and no day in order fits. Since the owner's ruling R302 such a night declares Isha's end not shown: the gate
recomputes it from the real sun at each row's point (`DayPipeline.nightTooShortForIsha`, never a flag on the day) and
counts it declared (the stamp's `shortNights`), and its Maghrib is the minute after the sun has set wherever that is
before the next Fajr. Since ruling R314 (17 November 2026) Norway's Cautious times, IRN a member, is declared on the
same nights too (`EntryStats.SHORT_NIGHT_ENTRIES`), and its About says them in a sentence of its own within Tromsø's reach (beyond it no sentence
says them; no gate row meets such a night today). A day without
its Isha end on any other night, or of any other entry, is still out of order. To settle a figure over the ceiling: the owner rules (add the unit to `RULED`) or the reach
is cut; `generateReachRows --only <entry>`, the gate with `-Pentry=<entry>`, `generateReachLimits` (it prints every
figure it moves: report each raise, never absorb it), the gate again, and commit. `checkStamps` fails on every run
while `ReachLimits.kt` is not what the committed stamps give. Until the owner rules, the site holds the city page of
every unit with a figure over the ceiling (`Proven.verdict`, ruling R270: Kazan, Rabat, Kuala Lumpur, George Town,
Johor Bahru and Kota Bharu on 15 November 2026), as the release is held. On 16 November 2026 the owner accepted all
246 figures then over the ceiling (ruling R291, "Accept all 246"): `RULED` holds each of those figures by unit and
event, as measured, and the six pages are back. Any raise past a ruled figure, any other event of a ruled unit rising
over 4, and any new unit over 4 is named again and holds its pages until he rules.

**A figure claimed across an area (ruling R297).** A radius sweep reads a unit's circle; a claim made over a region's
shape or a whole country has none. London Unified's table is followed across the whole M25 (a unit of radius 0 that
the M25's polygon chooses), MUIS's across Singapore and the Supreme Council's book across Bahrain (entries with no
units, one table at a fixed point). `ReachAreas.AREAS` reads each on a grid over the whole claim (the M25's polygon,
or, since the owner's ruling R307 of 17 November 2026, the app's whole nearest-city cell of the country, sea included:
wherever a GPS fix is placed in the country alone, which is everywhere the app says the figure; until then the
country's ground as drawn by hand, and `ReachRows.BEYOND_AREA` named Bahrain, whose cell read figures over 4; it is now
empty) and four times finer around each event's worst; `generateReachRows` (`--args="--only gb.london.lupt"`, `sg.muis` or
`bh.council`, under a minute to two each; `--only sa.ummalqura` writes both Saudi reach files, the radius file and
Tayma's town, and takes about 4 min) writes the claim's four extremes and each event's worst point to
`gb-london-lupt-reach.tsv`, `sg-muis-reach.tsv` and `bh-council-reach.tsv`, and `recipes.tsv` grows the first two
with every capture, as their own rows grow (Bahrain's book is read by hand: a new book's row goes on
`bh-council-reach.tsv`'s points too, by `generateReachRows --only bh.council`). An entry with no units keys its
area's limits by its own id in `ReachLimits.kt` (ruling R298). The release hold names an entry that claims a figure
across an area of the four kinds `ReachAreas.areaClaims` knows and has no line in `AREAS` (`ReachRows.unswept`): a
measured entry with no units, a measured unit of radius 0, a measured set kept measured beyond every unit, and (ruling
R300) a measured set whose `choose` hands a measured unit to places beyond its circle, as `ReachAreas.CHOSEN_BEYOND`
lists them: KACST's Tayma table within a minute's reach of the town (`sa-ummalqura-tayma-reach.tsv`, grown with each
Tayma capture), and JAKIM's zones within 10 km of each town their lists name (ruling R310: every measured zone read
across all the ground the app hands it, `ReachAreas.Handed`, its circle and its towns' and named places' discs, on a
1 km grid, into one file, `my-jakim-zones-reach.tsv`; `--only my.jakim` writes it and the radius file together, and
`recipes.tsv` grows it with every capture as the radius file grows). None is owed now. Brunei-Muara's district was
chosen out past its 60 km over the sea north of Tutong until ruling R309 (17 November 2026): a Brunei district is now
given only within its own circle, else the nearest district whose circle holds the place. `CityUnitsTest` looks for the fourth kind
among the app's cities, JAKIM's named places and a 0.05° grid over each such set's countries, and holds `CHOSEN_BEYOND` to what it finds: a claim of a shape it does not sample (a
sliver under the grid's 5.5 km) is not proven absent. `CityUnitsTest` also reads each claim again on a grid offset
from the sweep's.

**A year printed again is one table (ruling R299).** Where a table prints, day for day, what another of the same
entry printed at the same place on the same month and day of an earlier year (the Kyrgyz Muftiate's 2027 calendars,
every perpetual table mapped onto a later year), it is that table again (`gate/Repeats.kt`): it is no published table
of its own in the checks page's count, and where any year of it is a fit cell every year is, so it is never held out
nor a held-out Ramadan. Its days still count: each is a date compared against what the authority prints for it. A
`@fitted` line for such a year is no longer needed, though the old ones stay.

**A red to expect from the reach rows of R270.** Their limits are the worst measured on 14 November 2026, with
nothing to spare, and `recipes.tsv` grows these reach files with every capture (Habous, Amman and Cape Town's MJC
monthly; JAKIM, Toronto and Doha as they publish). A capture that reads a minute past a unit's figure at a reach
point turns `prove` red, and for a checked timetable's own capture demotes the entry (R185), which holds its pages
and a release. Read the row left out: if it is the reach's edge, the figure rises by `generateReachLimits` and is
reported to the owner; if it is the unit's own point, `own-points-worst.tsv` has already failed `CityUnitsTest`.

**A red to expect from those rows.** The towns' reach limits are what the replay of 13 November 2026 found, with
nothing to spare, and for the 30 fetched towns on some 137 days each (Cairo 494): 28 towns carry class B's 2 raised
to 3 for the events that reached it (`Egypt.reachLate`), the other 50 keep 2, and 22 of the fetched towns already
stand at 2 of 2. A new month can therefore bring a first 3-minute day at a town or event still at 2 (a later
start or an earlier end across the reach, never an early one; the engine has not changed), and the gate then
fails on that reach row with "over the limit" until the exception is recorded. The limit is not widened
beforehand for every town because About and the timetable sheet print it ("at most N minutes"), and a figure
there is one the gate has measured. The hand step: check the row is late and not early, and that it is a point
of the town's reach and not its own point (a town's own-point row over its worst is `own-points-worst.tsv`'s
failure and a different matter); add the event to that town's set in `Egypt.reachLate`
(shared/…/registry/authorities/Egypt.kt); then `gate`, `generateProofStamps`, `generateOwnPointWorst`,
`checkStamps` and the app's tests, and commit with the row. A 4 at a town other than Taba is not this case:
stop and look. ESA's 2027 tables are not captured yet (it served none by 13 November 2026); the first capture
of a new year is the likeliest moment for this red, so do it before a release cut, not after.

Since 13 and 14 November 2026 (rulings R205 and R249–R253) a Kyrgyz or Kazakh place inside several localities' or
places' reaches waits for each of them, and two more files hold that: `kg-default-envelope.tsv` and
`kz-qmdb-envelope.tsv` (4,160 promise-only rows at 1,998 places: every held table replayed wherever its reach holds
a place that follows another unit), both written by `generateEnvelopeRows`, never by a recipe.
`kz-qmdb-reach.tsv` is generated with the ride (`generateReachRows -Pthreads=6 --args="--only kz.qmdb"`, about 15
minutes: the sweep reads every riding neighbour). `prove` still grows the Kazakh reach rows and own points by
recipe with each new year of a place's table, but a new QMDB year is read on the neighbours' ground only after
`generateEnvelopeRows` is re-run, so re-run it (and `generateReachRows` for `kz.qmdb`) once the year's own rows
are in, before a release cut. The measured places' limits (`QmdbPlaces.reachLimits`, 70 of the 86) are the gate's
worsts with nothing to spare, as ESA's above: a new year can bring a first day a minute later across a reach
(late, never early), the gate then fails on that reach row with "over the limit", and the hand step is the same as
Egypt's, with `reachLimits` raised to the stamp's new worst for that place and event. The Kazakh rows alone take
about 6 minutes of the gate on a quiet Mac and the whole gate (67,593 rows, 12.0 million place-days and 3.5
million more held to the promise alone, on the lane's base) about 15 and a half, 35 under load; the tools' tests
about 44 minutes under load, `CityUnitsTest`'s Kazakh grid most of it. The weekly prove feels both.

**What `prove` does.** For each capture a recipe names whose days are not all held already by rows
of that file, entry, member and point: the capture is copied as it is to
`archive/tables/pinned/<source>/<date>/<key>.txt` (a row with `month` or `excused` days reads its own
cut copy beside it, `<key>.month-<yyyy-mm>.txt` or `<key>.excused-<member>.txt`, with a comment
saying so), never over a pinned file with other content; the rows are appended to their gate files
under a `# prove, <date>: …` comment; and the **whole** gate runs. A new row that breaks a promise
(an early start, a late end, lateness over its limit, a day out of order)
or that the gate refuses (an `@excuse` contradicted) is found — each new row of a failing entry is
checked with that entry's committed rows alone — removed again and reported with the gate's own
words; nothing is ever loosened, and the gate runs again until it is green. An entry already red
before prove (a hand-read member row committed ahead of the capture it leans on) passes only if its
new rows together make it green; otherwise prove stops and writes nothing (exit 2). That stops every
source for the week, on purpose: the stamps are written only from a whole gate that is green. A row
the gate file's reader refuses on its own (an empty zone, a bad point) is left out before anything
is written, and whatever stops prove midway removes the pinned copies it wrote and the folders they
leave empty. When green, the gate files and the stamps are written; pinned copies no kept row reads
are removed (a dry run keeps nothing, not even an empty folder). Run it twice and the second adds
nothing.

**Demotion, by itself (ruling R185, the owner's O4 of 12 November 2026).** When the row left out is
a checked timetable's own (class A or B, never a cautious entry's member row), it reads a capture
of the authority's own site (never a republisher's mirror, a table whose name ends `(mirror)`), and
what it broke is the promise itself (an early start, a late end, a day out of order, lateness over
its limit; not an entry that was red before prove), prove writes one line into that gate file on
the first run that finds it. The capture is whatever the monitor holds and the gate does not: most
often days the authority newly published, but equally a table re-fetched or one fetched long ago
and only now given a recipe, which is why the line names the capture and never says "new". A row
on a mirror's capture is left out with a reason that says it was not allowed to demote, and a row
the reader refuses (an empty zone, a bad point) is left out without a demotion — a table our reader
cannot take is not a promise broken — but both are tier 1 items of the prove issue, because a
checked timetable's own row that did not land is a day not checked:

    @demoted  <entry>[/<unit>]  <yyyy-mm-dd>  <reason: dates, counts and minutes, never a printed time>

The gate carries the line into the entry's stamp (`demoted`), and from there: the website holds the
city pages of that unit (of the whole entry, where no unit is named); a Ramadan build no longer
counts the entry as covered (below); and `checkStamps` refuses every release while the registry
still calls the entry a checked timetable — only prove's own run passes `-PallowDemoted=true`, so
the line is published with the green rows beside it. The issue names it as a tier 1 item. A person
removes the line only with the failing days proven (the method refitted and their rows green, or
the authority's own misprint recorded and its corrected table's rows green), or leaves it standing
and lowers the entry's class in the registry. What it cannot do: the label in an app already
installed is the registry's class on the days the stamps it shipped with hold, and the published
method on the days after them (ruling R275), so it changes only with a new build: a demotion blocks
the next release until it is settled, and a month prove adds after the cut shows as checked in the
app only from the next build (checkStamps names the checked units whose runs end within 45 days, ruling R278).

**A mirror's rows and the authority's own capture (ruling R282, the second audit's G18).** A gate row reading a
republisher's mirror (its note names `(mirror)`, or its file is a `-mirror` copy) holds no day of the authority's own
capture: when e-solat answers its own 2027 table for a zone whose 2027 is gated on api.waktusolat.app's copy (nine zones
and their towns on 14 November 2026), prove plans the capture as new, and the gate then holds both; the new row's note
drops the family's `(mirror)` (and a mirror's row carries it). A mirror's capture is held by any row, as before.
`jakim.py` stores e-solat's whole year ahead unmerged, so the live file is the authority's alone, day for day.

**A held day re-read otherwise (ruling R283, the second audit's G20).** A capture whose every day the target already
holds is compared cell by cell with the held tables (each event, by date, against every holding row on the same clock
that prints it). The days it prints otherwise than every one of them become a row of those days alone, pinned as
`<key>.changed-<first day>-<count>.txt`, whose note says how many held days were re-read otherwise. The gate judges it
like any row, so R185 demotes on an authority's correction exactly as on a new day. A cell the capture leaves empty, or
one no held row prints (a day the hand reading left out), is no change, so the weekly re-read of an unchanged table
still adds nothing.

**The printed imsak (ruling R284, the second audit's G25).** `jakim.py` and `kemenag.py` keep each table's imsak as its
own capture, `<key>-imsak`: two columns, the Subuh and the imsak as printed, the layout of the hand read-backs under
`archive/tables/imsak/<source>/` (Perlis's read as its end of eating, `- E`, as my-jakim.tsv reads it; every other `- Im`).
An imsak is kept as printed whatever its distance from the Subuh (an earlier end is never left out); one more than 15
min before it, or after it (then `-`), is an error while its day is ahead and a note once past; one at its Subuh is
kept (Perlis prints no precaution). e-solat's year ahead replaces a held year only when it is whole, counted on the days
taken; a part year merged over held days it does not print keeps the `(mirror)` mark (ruling R282), imsak and all. Only a
recipe line whose pattern ends `-imsak` reads such a capture, so `*` and `jakarta-2*` never do; recipes.tsv reads them
at every JAKIM zone, its 42 zones' towns and the eighteen Kemenag kab/kota whose imsak id-kemenag-imsak.tsv holds. An
imsak capture holds, and is held by, rows that read ends alone, and finds its family in the hand read-back of the same
key (`archive/tables/imsak/<source>/<capture>/<key>.txt` is that source's `<key>-imsak`). The monitor's own check reads
its Subuh column as the day's start, since the gate checks ends alone only on days a start holds.

**A source that read nothing it was given (ruling R281, the second audit's G19).** JAKIM's days with a cell that is
not a time are an error (how many, the first), and an answer none of whose days could be read is one too; the Kyrgyz
Muftiate's year with such a cell is an error and is not filed (the year replaces what is held, so the held year
stands); and `fetch.run_source` fails any fetcher that made requests, filed no table, recorded no error and noted cells
that are not a time. A format change is never "ok" with no table.

**A timetable whose Ramadan days are not in the gate yet, and the Ramadan build (ruling R185).**
`checkStamps -Pramadan=<Hijri year>` (rulings R145–R149) names every checked timetable whose own
rows do not hold every day of that Ramadan. Since ruling R246 (13 Nov 2026) a day counts only where
a **held-out** cell holds it (ruling R214: a `test` row on a table no margin saw), for Fajr, the end
of eating and Maghrib, and for the imsak too where the unit shows one and its tables print one; a
unit whose guarded days are all in the gate, some only in tables its method was fitted to, is named
apart ("checked and not held out") and refused all the same, with no O4 cover (its days are
published already; what it lacks is a table the fit did not see). The same run names, in notices
that refuse nothing, the "Published method" entries (class D (authority)) not checked against the
authority's own table on every day of the month, and the units that show an imsak no table of theirs
prints. A run **without** `-Pramadan` reads the coming Ramadan the same way and prints one line,
`checkStamps: NOT YET PROVEN for Ramadan <year> …`, naming every such entry; it never fails for it,
so an ordinary build stays green while the line stands above the green one. An entry short of days is instead reported as
`covered under O4 (ruling R185)` when all three of these are read from the files (`MonthByMonth.kt`;
the run needs the archive's checkout, `-Pofficial` or `TAQWA_OFFICIAL`, and without it nothing is
covered). The rule does not ask how the authority publishes (a month at a time, or a year at a time
later than the build); it asks only for this evidence:

- (a) the method is proven on the months held, a Ramadan among them: a green stamp, nothing demoted,
  and at each unit short of the month at least 28 days of each of Fajr, the end of eating and
  Maghrib **inside** the twelve months before the guarded days begin (old days do not count, and one
  recent day does not make them count), and at least 10 days of each in one of the five Ramadans
  before (a third of a fasting month; two stray days of a Ramadan are not a Ramadan checked); and,
  of the entry as a whole, at least 10 Ramadan place-days in rows held out of the fit (the stamp's
  `heldOut.ramadanDays`, which is not kept by unit) — a method never tested on a Ramadan it was not
  fitted to is not proven for one;
- (b) the monitor fetches the authority's own publication, and every day it fetched is in the gate:
  an automatic source of `sources.tsv` (never a hand-read one) holds a table of the unit **from the
  authority's own site** in the monitor's index (a republisher's mirror, a table whose name ends
  `(mirror)`, is not the authority publishing and does not count), a line of `recipes.tsv` makes
  that capture the entry's own gate row, every day such a capture prints outside the guarded days is
  in the unit's checked runs (a day fetched and not in the gate is a row prove left out, or prove
  has not run since the fetch), none of the unchecked days is already printed in any capture held,
  and the newest fetch on record that ran the source (`monitor/fetch/<date>.json`, a skipped run
  apart) ended `ok` no more than 8 days ago for a source fetched weekly (`weekly`, `hijri-month`),
  32 for one fetched monthly, and its captures print through that fetch's date — a fetcher that is
  broken, or that brings back only old days, checks no day, whatever the catalogue says;
- (c) prove demotes the entry on the first failing day of the authority's own capture (above), and
  a demoted entry is not covered.

What (b) is and is not: the monitor runs on its cadence, weekly at its most frequent, so a day is
compared when the next run fetches it, not on the day the authority publishes it, and a missed run
leaves its days unchecked until the next; the guard's own words say so beside each covered entry.
A demotion holds the city pages and blocks the next release; it cannot recall a build already
installed.

Every other entry is still named, with the conditions it fails. The guard is a check at the cut: a
fetch that stops afterwards is the monitor's own overdue and failed-fetch findings, week by week.

    ./gradlew -p tools/timetables prove -Pofficial=<root> [-Pdate=yyyy-mm-dd] [-Ponly=a,b] [-Preport=<file>] [-PdryRun=true]

**What `scripts/prove.sh` checks after it** (when prove changed anything): `generateProofStamps`;
`generateGoldenVector` to a scratch file, required **byte-identical** to the committed one (prove
never changes the engine: if the vector moved, it stops); `checkStamps`; the tools' `jvmTest` with
the archive; `CI=true generate`; `python3 site/build.py --check`; `python3 site/test_timetables.py`;
and last the app's own `:shared:testAndroidHostTest` on the new `ProofStamps.kt`, which is app source
(the common tests the iOS ones run too, with `CI=true` as main's Tests runs them; just before prove the
workflow installs the Android SDK platform `tests.yml` does, never failing the job, and
`prove_tests.py` holds the two to `compileSdk`). A test anywhere that reads the proof holds its rule,
never the live proof's figures or dates, which move every time prove adds rows (`StampsAsOf` in the
tools' tests holds a case that needs fixed dates; a figure is read from the stamp, and one prove grows
is pinned as a floor). On 12 and 13 October 2026 that broke twice: two tools tests failed prove itself
on the captures of the 12th, and `AboutTimesUiStateTest`, which prove did not run then, failed main's
tests on the commit that published them on the 13th.
Any failure puts back everything prove changed in the checkout and the pinned copies it wrote, and
records which check failed in `proof.json` (exit 1; the issue's Proof section then says nothing was
published and why). Its outputs go to `monitor/proof/` in the private repository: `proof.json` (rows added and left
out with the reason: dates, counts and minutes), the generator's notices before and after (cities
newly proven), and `changed`.

The stamps the runner writes should carry the same `engineHash` a Mac run writes for the same engine
and methods. Their fingerprint prints no double: every one is whole millionths (`Canonical`, since 11
October 2026). Until then, `sin` and `cos` gave different last bits on the runner's CPU and the
Mac's, so each cloud run left twelve stamps stale for the Mac's `release.sh`. That the two now agree
is argued and tested on the Mac only (BUILD-LOG, 10–11 October). The first run to measure it is the
first weekly run after the fix reached `main` that has new captures to prove: a run with nothing new
to prove, or one that puts its rows back, commits no stamps and measures nothing. In that run's
"Gate: the weekly monitor's captures … proven" commit, no entry's `engineHash` may change unless that
entry's own rows changed (watch the twelve curve entries: `be.emb`, `fr.gmp`, `ie.icci`, `no.irn`,
`kz.qmdb` and the cautious entries of Belgium, Canada, Germany, France, Ireland, Norway and Sweden).

**Publishing.** Since 27 October 2026 (security review D1) the run is four jobs, so the token that
pushes to Taqwa never sits on a runner that ran a fetcher, pip or Gradle (anything those run could plant
a git hook or a background process and read it). `due` and `monitor` are read-only (D10): `monitor`
fetches, checks and proves, commits nothing, and ends with `tools/timetables/monitor/ci/handoff.py pack`,
which copies what the run changed under `archive/` and `monitor/`, and the gate files, stamps and
`ProofStamps.kt` prove changed, each with its sha256 in a manifest, into the artifact
`monitor-handoff-<run id>` (kept 90 days, so a run whose push fails loses nothing). `publish` runs on
a clean runner and runs nothing but git and `handoff.py` from its own checkout of Taqwa (the Python
standard library alone: no pip, no Gradle, no fetcher, no other script). `handoff.py check` refuses a
path that is not plain, a file the manifest does not list or whose hash differs, a symlink; on the
archive side any path outside `archive/` and `monitor/`; on the public side any path that is not a
line of `tools/timetables/monitor/ci/bot-paths.txt` (the gate files, the stamps, `ProofStamps.kt`), a
deletion, a stamp that is not JSON, an added line with a clock time's shape (`h:mm`, ruling R69; a
signed offset such as `UTC+02:00` is not one), and a `ProofStamps.kt` that is not exactly what
`ci/proof_stamps.py` writes from the resulting stamps: the publish job rebuilds the generated Kotlin
itself, so the proving job can never ship code through it (`ProofStampsPortTest` holds the Python port
byte-identical to `GenerateProofStamps`, whose string literals escape the backslash, the quote, the
dollar and every control character, finding D9, and both refuse an entry id that is not plain, since
it becomes a function name).

The job then commits as `taqwa-monitor` with hooks off (`git -c core.hooksPath=/dev/null`): first the
archive's changes to the branch the run started on (rebased onto whatever arrived meanwhile, one
retry); only when that push landed, its rebase brought in no change under `archive/tables/` from
elsewhere (tables the proof may read; otherwise the proof is not published and the next run proves
again), and prove's checks were green, the proof to `MohamedAbulgasem/Taqwa` `main`, with the message
`tools/timetables/monitor/proof.py message` wrote in the proving job (counts, gate files, entries and
city slugs only; no time, no table date, no AI attribution; refused, exit 3, if it ever had a time's
shape). Before that commit, **main's commits since the proving job's checkout must touch nothing the
proof depends on** (`tools/`, `shared/`, `widgetcore/`, `scripts/`, `site/`, `gradle/`, the build
files): a change there (a stricter gate limit, an engine fix, a hand-added row) means the rows were
judged by rules `main` no longer has, so nothing is pushed and the next run proves again on the new
`main`. The push is fast-forward only (main's ruleset refuses force pushes and deletions); a rejected
one is retried once on `main` as it is then, under the same rule. Each token (the job's own
`GITHUB_TOKEN`, Contents: write on this repository alone; the secret `TAQWA_PUSH_TOKEN`) is turned
into a masked HTTP header and unset at once, and reaches git through `GIT_CONFIG_*` on the commands
that push alone (never on a command line, in a URL, or in any other program's environment; never
echoed). A push GitHub refuses for the token (401 or 403: revoked, expired, without Contents write)
is not retried. `report` (Issues: write) writes the issue and the run's verdict.

**Bot commits at a release cut.** `scripts/check-bot-commits.sh <the last cut's tag>` lists every
commit by `taqwa-monitor` since it and fails when one touched a path outside `bot-paths.txt` (the
publish job's own list) or is a merge.

**A rehearsal.** After any change to the publish path, prove it end to end before it carries a real
push: push the private workflow to a branch of `Taqwa-official` (never `main`) and the Taqwa code it
runs to a branch of Taqwa, then `gh workflow run monitor-weekly.yml -R MohamedAbulgasem/Taqwa-official
--ref <that branch> -f rehearsal=true -f taqwa_ref=<the Taqwa branch>`. A rehearsal fetches nothing and
does not prove: it adds one comment line to one gate file in prove's place, commits the archive's
changes to its own branch, and pushes the proof to a new Taqwa branch `monitor-rehearsal-<run id>`
(never `main`); it never touches the issue. Check the run, then delete both branches.

**The issue.** Its body gains a `## Proof` section (rows added, rows left out with the reason,
cities newly proven or newly held, and what happened to the push), also written to the step summary;
a week where prove did not run says so (last week's `monitor/proof/` is removed when the job starts).
What prove found counts as attention even on a green week: a row left out (above all an early start
or a late end against the table: the comment says so in capitals), prove stopped or put back, a city
newly held, or a push that did not land (the secret missing included). The body carries a fingerprint
of those findings (`<!-- proof: … -->`, blind to dates and numbers); the issue is opened or updated
when the fingerprint changes, and a green week closes it only when prove found nothing. The run
fails when prove could not run, a check failed after rows were added, or the public push failed (a
push skipped because `main` or the archive moved meanwhile does not fail it: the next run proves
again).

**The token (the owner, once).** On GitHub: Settings › Developer settings › Personal access tokens ›
Fine-grained tokens › Generate new token. Name `Taqwa monitor push`; expiration 1 year (a calendar
reminder to renew it); resource owner MohamedAbulgasem; Repository access › Only select repositories
› `MohamedAbulgasem/Taqwa` (the public repository alone); Permissions › Repository permissions ›
Contents: **Read and write** (Metadata: read-only is added by itself; nothing else). Generate, copy
it once. Then in `MohamedAbulgasem/Taqwa-official`: Settings › Secrets and variables › Actions › New
repository secret, name `TAQWA_PUSH_TOKEN`, paste, save. Copy this template to the private
repository's `.github/workflows/monitor-weekly.yml` (as after any change to it). If `main` is
protected, the token's pushes must be allowed by the rule (or the rule must allow this token's
account to push). Deleting the secret makes the runs skip the push with the notice (and the issue says
the proof was not pushed). A revoked or expired token whose secret is still set makes the push step
fail every run ("GitHub refused the token"), with no retry, until the secret is replaced with a new
token or deleted.

## Running by hand on the Mac

    scripts/monitor.sh                          fetch, back up, check, report
    scripts/monitor.sh --no-fetch               check what is held (the gate, the surveys, the horizons)
    scripts/monitor.sh --only sa-ummalqura      one source: a partial run (its own report, nothing notified)
    scripts/monitor.sh --no-fetch --check-all   every held table again
    scripts/monitor.sh --verbose                with each step's progress

    python3 tools/timetables/monitor/fetch.py --official <root> [--only <source>] [--force] [--budget-minutes N]
                                              [--now <ISO 8601 moment: a month-start source's clock, for a test>]
    python3 tools/timetables/monitor/fetch.py --official <root> --month-turn [--today yyyy-mm-dd]
                                              (fetches nothing: the hijri-month sources whose new month is due)
    ./gradlew -p tools/timetables monitor -Pofficial=<root> [-PcheckAll=true] [-PskipFull=true] [-Ptoday=yyyy-mm-dd]
    python3 -m unittest tools/timetables/monitor/tests.py
    ./gradlew -p tools/timetables jvmTest --tests 'world.taqwa.timetables.monitor.*'

`TAQWA_OFFICIAL` is the root (default `~/Desktop/Workspace/apps/Taqwa-official`, the folder that
holds `archive/` and `monitor/`; the `monitor` Gradle task refuses to run without it or
`-Pofficial`, so that no state lands in the checkout); `TAQWA_OFFICIAL_BACKUP` an optional mirror
folder; `TAQWA_PYTHON` the interpreter (else Homebrew's `python3`, else the first on `PATH`;
3.9 or later, standard library only); `JAVA_HOME` the JDK (else macOS's own lookup for 21), whose tz
database must be 2026e or later: every printed time is read on its clock rules, and the cloud runner's
Temurin once moved from 21.0.12 to 21.0.13 (tzdata 2026e) under the monitor between the 26 Oct and 2 Nov
2026 runs and changed what it found, so `monitor.sh` refuses an older JDK before it fetches
(`tools/timetables/monitor/tzdb.py`), and the gate, monitor and prove tasks refuse one before they read
anything (`gate/Tzdb.kt`; raise both minimums together). `tzdb.py` reads `JAVA_HOME`'s JDK, while the Kotlin
tasks run on Gradle's `jvmToolchain(21)`, so `JAVA_HOME` must be that JDK (on the Mac,
`/opt/homebrew/opt/openjdk@21`; on the runner, setup-java's); where they differ, the Kotlin check is the one
that counts, and `tzdb.py`'s refusal says so. Two
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

### Türkiye's neighbouring districts (rulings R201, R202, R204)

The weekly fetch reads Türkiye's 84 unit districts. The 190 districts that ride beside those units, each on its own
Diyanet table, are in no weekly fetch (847 pages a week is more than the route should carry), so nothing notices by
itself when Diyanet prints a new year for them: their gate rows stand on the reading of 12–13 November 2026, calendar
2026 alone, and a rider's 2027 table that starts a minute later than its 2026 one would not be seen. **When Diyanet's
page prints 2027 again** (the weekly report says so: the 84 units' tables grow past 31 December 2026), and in any
case before a build claims Ramadan 1448 for Türkiye, read them again by hand, in the archive's
`code/manual/tr-diyanet/`:

    python3 -I all_districts.py <official> lists
    python3 -I all_districts.py <official> again <file of the riders' IlceIDs>    (plain GETs, 2.5 s apart, about 10 min)
    set READING in beside.py to the new fetch date
    python3 -I beside.py <app checkout>                       (the rows, no limits yet)
    ./gradlew -p tools/timetables generateReachRows --args="--only tr.diyanet" -Pofficial=<official>    (the units' tables across their reaches, ruling R255)
    ./gradlew -p tools/timetables gate -Pentry=tr.diyanet -Pofficial=<official>    (both files' rows, never -PgateGroup=tr-diyanet, whose stamp would leave the reach out and which limits.py refuses; may stop over a limit; the stamp is written)
    python3 -I limits.py <app checkout> > beside-limits.tsv
    python3 -I beside.py <app checkout> beside-limits.tsv
    the whole gate, generateProofStamps, generateOwnPointWorst, checkStamps -Pramadan=1448

`limits.py` reads every row of a unit, so a unit's limits are its reach's worst too (ruling R255); a `@demoted`
line `prove` wrote for a unit whose new year ran a minute over a reach limit comes out of `tr-diyanet.tsv` once
the new limits hold it ("A red to expect from Türkiye's reach rows" above). A limit the measure would put over
4 minutes stops at the tools' test of the ceiling and waits for a ruling; it is never written by the procedure alone. The riders' ids are the districts of `DiyanetBesideDistricts.kt`. An early start in that gate run is a defect to fix
in the riding rule (`Diyanet.rides`), never a row to drop; a later one is the unit's new recorded limit. Until that
reading is in, `checkStamps -Pramadan=1448` stays red for tr.diyanet (the 62 units of R200 hold no 2027 day), which
is the guard doing its work. The 22 older units' 2027 days are those Diyanet's page last printed on 31 October 2026
(the monitor's files keep them; the page fetched on 9 November prints 2026 alone), so their Ramadan 1448 rests on
that printing until the page prints 2027 again and the weekly fetch reads it.

## Where things live

- Code: `scripts/monitor.sh`; `tools/timetables/monitor/` (`fetch.py`, `backup.py`, `common.py`, `editions.py`,
  `fetchers/*.py`, `certs/` (public certificates that complete a server's incomplete chain), `tests.py`,
  `ci/monitor-weekly.yml`); `tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/monitor/`
  (`MonitorMain`, `TableCheck`, `Horizons`, `Report`, `MonitorIndex`) and the `monitor` task in
  `tools/timetables/build.gradle.kts`.
- Catalogue: `tools/timetables/official/monitor/sources.tsv` (committed, metadata only).
- Archive: `<official>/archive/tables/monitor/` and `archive/raw/monitor/` (restricted, never in the
  public repository); the private repository holds it all.
- State and reports: `<official>/monitor/`.

## Sources

`sources.tsv` is the list. As of 2 October 2026 the fetchers cover Umm al-Qura (its API weekly at twelve places of
KACST's own city list and, since 9 October 2026, monthly at the other 161, `sa-ummalqura-cities`: 322 requests,
about 11 minutes at the 2 s interval, on the run it falls due), Diyanet
(Türkiye's 84 unit districts since 13 November 2026, every il's centre among them (rulings R200, R202: 84 pages a
week, about two minutes more; a district new to the fetcher is read at the unit `recipes.tsv`'s `index` line names),
and nineteen European cities, Antwerpen, Gent, Lyon, Lille, Copenhagen, Helsinki and
Trondheim among them; the 190 districts that ride beside Türkiye's units (R201, R204) are not fetched weekly, see
"Türkiye's neighbouring districts" below), Kemenag (eighteen kab/kota, Surabaya, Medan, Semarang, Palembang and
Yogyakarta among them, and since 12 November 2026 109 more and then 119 more: 246 places, read from api.myquran.com,
a republisher's copy of Kemenag's figures and not Kemenag's own page. A run asks the current month and the next for
each place, 492 requests, about 16.5 minutes at this fetcher's own 2 s gap (`KEMENAG_GAP`: the host is a
republisher's free API, so never faster than one request in two seconds); one of the months held beyond the next, read
again for every place that holds it and compared with what is held (a cycle of 36 weeks, so each held month comes
round once in it: at most 246 requests, about 8 minutes, and a held day that changed is a note in the source's
message, in days, never a printed time); and from October the next year for the places that lack a month of it, 20
places a run (240 requests, about 8 minutes), a month held under `archive/tables/round/kemenag-kabkota/` counting as
held, so the round's 228 places' 2027 and 2028 are not fetched again: at most 978 requests, about 33 minutes, a run.
Whether a weekly run of that size at a third party's API is welcome is the owner's question (the build log's
Indonesia entry of 13 November 2026); the safe fall-back is the 492 alone.
An answer that does not hold every day of the month asked, or holds another month's, is an error, and so is a place
no row was read for: audit findings F23, F24 and F28, 13 November 2026), JAKIM (thirteen zones, and since 13 November 2026
sixty: every zone of e-solat's list, the current year by GET and from October the year ahead by POST (the date-range
form e-solat's own site posts, no key, no login; kept by the owner on 14 November 2026, whose rule is that a POST is
as welcome as a GET where the form is the authority's own, public and read-only, and nothing sensitive is sent; a new
host still needs his yes), 120 requests a run then; a zone whose year ahead e-solat has not
loaded is a note and is asked again each week; a day out of line with the days either side in two or more columns is
never left out: it is kept as printed and raised as an error for a human to rule, with the direction and the minutes,
unless the engine holds its starts to the print (ruling R228 as its review round left it: SBH09's 28 to 31 March 2026, printed
later, a note); `prove` grows each
capture at the zone's unit and at every town of its list, `my-jakim-cities.tsv`), MUIS, Egypt (ESA via Dar al-Ifta, and ESA's daily page), Qatar (the
ministry API and the Calendar House header), Libya (the Awqaf widget and api.ifta.ly), Tunisia (INM),
Morocco (Habous: since 13 November 2026 every place the Ministry prints a table for that is a unit of
`ma.habous`, 187 units each read at its own unit and Agoudal's second table against Agoudal's unit (rulings
R195–R199; the ten units of before and the 29 cities read at the app's points since 6 October 2026 keep their
keys, so their held files go on; Sebta and Melilla are not read), still two requests a run; since
November 2026 from the Ministry's month files, `data/horaires-<yyyy>-<mm>.js`, a whole Gregorian month for 190
places by the old pages' ville ids, the current month and the next on each run, the next one's 404 a note
("not posted yet"), a file whose `fuseau` is not GMT refused; the old city pages answer 404 since the site
became a maintenance notice on 26 October 2026; `tools/timetables/monitor/certs/habous.gov.ma.pem` once
completed its server's chain for that host alone, `EXTRA_CA` in `common.py`, harmless now that Cloudflare
serves it), Jamiatul Ulama, the Kyrgyz Muftiate (muftiyat.kg's own calendar page, a whole year a request at each of
its 115 localities, monthly; like JAKIM's and Jordan's, its request carries more than a bare GET, and here
it is one header, `Cookie: point=<locality id>`: the visitor's choice of locality that the site's own picker
writes, no login, session or token, and nothing the server sets is kept or sent back; asked for a year without
it, the server answered 302, set `point=1` (Bishkek) itself and redirected to the same page from that day to
the month's end (the redirect was not followed and one query was tried, so no more than that is claimed), so
the header stays: audit finding F29, 13 November 2026, the two replies held under
`archive/raw/manual/kg-muftiyat/2026-11-13/`; a locality-in-the-path route the site's script builds for its
Ramadan table, `/{lang}/calendar/<id>/orozo/`, was seen and not tried; each of the 230 pages is asked at
least 2 s after the request before it; the reply's selected locality is checked against the one asked for),
Kazakhstan's QMDB (its year API at 87 of its own
places, monthly, from its southernmost to its northernmost, since 3 and 6 October 2026, and since 9 October 2026
QMDB's place for every other city of the app's list: 174 requests, about 4.5 minutes; a reply is taken
only when it keeps QMDB's band rule, Maghrib at sunset + 3 min below 48° N and + 5 at or above, and says what
the held copy says or a second reply says the same, else the place is asked again up to three times and the
held copy stays with a fetch error, since the API answered Zhezkazgan with the northern band's minutes on
9 November 2026. A change that breaks the band rule itself, a new Maghrib offset or a moved 48° N line, is
never taken: once three places in a run have each answered the same way every time against the rule, the
rest are asked once each, and every error says to look by hand and, if QMDB has changed its method, to
change `BAND_LAT` and `band_minutes` in `tools/timetables/monitor/fetchers/qmdb.py`), the Muslim
Judicial Council (mjc.org.za's current month for Cape Town, month-start, each month kept as
`za-mjc/cape-town-<yyyy>-<mm>` and checked as za.mjc at its
unit and as za.cape's member there), and since 2 October 2026 Jordan's Ministry of
Awqaf (awqaf.gov.jo's region table for Amman, Balqa, Zarqa and Madaba, walked by its pager within one
session, checked at Amman's unit and at Zarqa), Toronto's three mosques (IFT's year CSV, IIT's month
tables, MAC's Mawaqit calendar, each at its unit and as ca.toronto's member; a day printed on the
wrong clock is the table's own fault, left out) and Chicago's three Masjidal tables (Makki, DarusSalam
Lombard, the Mosque Foundation, as us.chicago's members; a convention member such as the 18° block is
checked as its member row), and the surveyed Mawaqit calendars. IACAD Dubai (Cloudflare refuses
scripted access), the Calendar House's printed calendar and Ramadan imsakiya (PDFs), EMB's yearly
PDF, the PA Dar al-Iftaa's perpetual table and imsakiya, Chicago's other four mosques (PDFs and
images), Sudan's, Gaza's and Mauritania's tables, and the Islamic Foundation Bangladesh's Ramadan calendar
for its 64 districts (PDFs, due each January, thirty days before Ramadan) are read by hand. So are IRN's calendars since 13 November
2026 (ruling R189): bonnetid.info read by itself from ruling R95 until 31 October 2026, when the site became
an app whose data API needs the key its script carries; the monitor sends no key it was not given, the
owner chose (12 November 2026) a download by hand once a year, and the source is `manual`, due each December (the step is
under "Work by hand, by date" below; `fetchers/irn.py` is kept unwired). London Unified runs on its own
since the owner added the London Prayer Times key as the secret `LPT_API_KEY` (10 October 2026): monthly,
it reads the coming year from October (the year itself before then). Until London Prayer Times adds that
year (late October: "2025 times added (24th Oct 2024)" on its API page) the API answers its months
without days, so a probe of the current month tells "not out yet" (a note) from a refused key or a changed
format (an error with the answer's shape); prove then gates the new year at every point the gate file reads
LUPT at, the unit's own and the twelve M25 edges (one table for the whole M25, ruling R44). The first run
with the key (10 Oct 2026) confirmed the key and the parser on October 2026.

## Work by hand, by date

The hand-read editions the 1.7.0 line rests on are listed in `sources.tsv` since the second audit's G22, so each is a
"Manual source due" item before Ramadan 1448: the Council of Ulama's Ramadan calendar for `tj.default` (tj-council,
2027-01-15), COSIM's Sha'ban and Ramadan calendars for `ci.default` (ci-cosim, 2027-01), Dawat-e-Islami's February 2027
cards for `pk.karachi` and `in.karachi` (pk-dawateislami, 2027-02-01: one plain GET per city in that month, ruling
R235), DUM RD's Makhachkala pages for January and Ramadan 1448 for `ru.dumrd` (ru-dumrd, 2027-01: its method rests on
one month held), and the Moonsighting Committee's 2028 calculator for the Americas' far south (mcw-far-south, 2027-11).

What no fetcher does and nothing else would bring up in time. Each line is also something the monitor
says by itself when its date comes, so this list is the how, not the reminder.

### Before the Ramadan 1448 build is cut (end of December 2026): ESA's 2027 pages (eg.esa)

Every Egyptian town is checked through 31 December 2026 and no further, and `checkStamps -Pramadan=1448` refuses
the build for all 78 until the 32 days it holds (7 February to 10 March 2027) are read. ESA's page
(esa.gov.eg/praytimes.aspx) shows a day other than today only behind its own date form, a postback, which neither
the monitor nor a script of ours drives (the owner's web rule of 13 November 2026). Each day's page prints all 78
towns, so the reading is one page a day: how it is done is the owner's to say (`sources.tsv`, the manual line
`eg-esa-2027`, due from 1 December 2026; the choices are in `docs/BUILD-LOG.md`, the audit's Egypt entry). Once the
pages are in `archive/tables/manual/eg-esa/`, as 2026's are: the towns' rows in `eg-esa.tsv`, `generateReachRows
--only eg.esa`, `generateEnvelopeRows`, the whole gate, `generateProofStamps`, `generateOwnPointWorst` and
`checkStamps -Pramadan=1448`. January's pages matter beside Ramadan's: the Egyptian city pages on taqwa.world
need the whole current month checked. Without them the three pages on cities of the 48 (Mansoura, Tanta, Assiut)
go dark on 1 January 2027 and stay dark, no month of theirs ever being read; the seven on towns of the 30 (Cairo,
Alexandria, Giza, Port Said, Suez, Luxor, Aswan) go dark at each month's turn until the monitor's first run of the
month has brought Dar al-Ifta's table.

### Each December: IRN's calendars for the coming year (no.irn, ruling R189)

Islamsk Råd Norge publishes the coming year on bonnetid.info, usually in December. Its data API needs a
key, so nothing fetches it. The monitor says it twice: a Horizon item from 1 November while the coming
year is not held (Tromsø's rule, ruling R82), and "Manual source due" from 1 December
(`sources.tsv`, no-irn, `next_expected`). The step, about half an hour by hand and an hour of the Tromsø probe:

1. In a browser, open bonnetid.info and save the coming year for Oslo, Trondheim and Tromsø, as the site
   itself offers it (its timetable view's export or print; the saved page if neither). A person reads the
   site's own page, as any visitor does (the page itself calls its data API with its own key, in the
   person's browser); nothing of ours calls that API, holds a key or runs a script against the site.
2. File what was saved, untouched, under `archive/raw/manual/no-irn/<date>/` in Taqwa-official, and one
   table a city under `archive/tables/manual/no-irn/<date>/` (`off-irn-<city>-<year>.txt`): a first comment
   line with the address, the date and how it was read, then a line a day, the date and the seven printed
   columns in this year's order (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha, the later Asr). Restricted:
   the archive only, never this repository (ruling R69).
3. Add the three rows to `official/gate/no-irn.tsv` and Oslo's and Trondheim's member rows to
   `official/gate/no-cautious.tsv`, as this year's rows stand there (same columns, same points). Since ruling R302
   IRN's reach is in the gate: `generateReachRows --args="--only no.irn"` writes the new year's rows across the three
   reaches (`no-irn-reach.tsv`), at the points Tromsø's spec watches too (ruling R321: the Tromsø probe's worst on
   the app's own grid, `ReachRows.TROMSO_WATCHED`), and `generateReachLimits` after the gate reports every figure that
   rises (a raise past a ruled figure is the owner's again). The watched points are a year's own, so before
   `generateReachRows` re-run the Tromsø probe on the new year (`tools/timetables` task `tromsoProbe`, about an hour
   for all four of its grids; `build.gradle.kts` says how): `-PprobeYear=<year> -PprobeGrids=dense` first, then
   `rows:<out>/points-dense.tsv` (that year's `refine`), `edges` and `micro`. Update `ReachRows.TROMSO_WATCHED` to the
   worst points it reports on the app's grid (`refine`, `dense` and `edges`): Isha, Maghrib in May and in July,
   sunrise and the end of eating, each with why. A figure over its ruled number (`ReachRows.RULED`) is the owner's,
   never written into `RULED`.
4. Run the whole gate. An early start is mended in the engine, never listed away; check Tromsø's rule
   dates against `IrnArctic` and narrow the widened window where the new year allows (ruling R82).
5. `generateProofStamps`, `checkStamps`, commit both repositories, and move no-irn's `next_expected` in
   `sources.tsv` on to the next December.

### Each January: MUIS's Imsakiah for the coming Ramadan (sg.muis)

MUIS prints an imsak in one table only, its Imsakiah, a PDF a Ramadan on its Islamic Calendar page;
data.gov.sg's set and the year's prayer timetable do not print one, and no fetcher reads the Imsakiah.
So the year's Fajr, Maghrib and the rest can be checked long before (2027's were, from the year's own
PDF, on 13 November 2026) while the imsak of that Ramadan still rests on the Imsakiahs held: on
13 November 2026 that is one, Ramadan 1447's 30 days (Subuh − 10 on each). The monitor says
"Manual source due" from the month in `sources.tsv` (sg-muis-imsakiah, `next_expected`). The step:

1. One plain GET of https://www.muis.gov.sg/Media/Islamic-Calendar, then one of the English Imsakiah's
   PDF it links. File the PDF untouched under `archive/raw/manual/sg-muis/<date>/` and the table read
   from it under `archive/tables/manual/sg-muis/<date>/imsakiah-<hijri year>.txt`, its first comment
   lines naming the PDF's own address, the page, the date and how it was read (as `imsakiah-1447.txt`).
2. Add its row to `official/gate/sg-muis.tsv` beside 1447's (`Im`, held out), run the gate,
   `generateProofStamps`, `checkStamps`, commit both repositories, and move `next_expected` on a year.
3. A computed imsak after the printed one is a failure (an end is never after the print's): it is
   mended in `Muis`, never listed away.

### By 15 December 2026, then each October: Oman's 86 localities (om.mara, the audit's F27)

The ministry's all-localities day page (`mara.gov.om/arabic/calendar_page4.asp`) computes one page for the
day asked, for any day of 2021 to 2031: there is no edition, no file to compare and no notice when the
ministry corrects a locality. It was read once, on 12 November 2026: every day from 1 November 2026 to
31 December 2027, and the 1st and 15th of each month of 2029. No fetcher reads it: every day but today's
is had only through the page's day picker (a form sent by GET, `?year=&month=&day=`), which the engine
line's web rule, as the Lead tightened it on 13 November 2026 (no pickers by script), keeps out of the
weekly monitor, and the server sends no intermediate certificate. The read is `mara_days.py`, a script run
by hand, never by the weekly monitor; the first read asked one second apart, the day before that rule's
two seconds. Whether the two reads below may go through the picker that way is the owner's to say, and it
is asked in the build log (13 November 2026, ruling R210's entry): until he says, they are not run.
So nothing watches the ministry between reads, and nothing but `sources.tsv`'s date (om-mara,
`next_expected`, "Manual source due") says when the next read is due: the stale-proof horizon stays quiet
for om.mara until December 2030, because the 2029 days and Muscat's 2031 months carry the proof's last
day past the held year. Two reads:

1. **By 15 December 2026**, before the Ramadan 1448 build is cut: Ramadan 1448's days again (about
   8 February to 9 March 2027, 30 pages, about four minutes), compared with the tables held. With the
   owner's say for the picker: `MARA_FETCHED=<today> python3 -I mara_days.py fetch <archive root> 2027-02-08 2027-03-09`
   (the archive's `code/manual/om-mara/`; one page at a time, 2 s apart; the read's own day names its
   folders and the script refuses a read with no day or no days named, so the first read stays as it
   was), `parse --write` the same way, then a diff of each
   locality's 30 days against the same days of its held `<id>-2027.txt`. No difference: note the date read in the
   archive's MANIFEST and move `next_expected` to 2027-10. A difference: the new pages are the table
   (new files, new gate rows in `official/gate/om-mara.tsv`, the whole gate; an early start is mended in
   `Gulf`, never listed away), and the whole of 2027 is read again.
2. **Each October** (first in 2027, for 2028): every day of the coming year for all 86 localities (366
   pages, about 45 to 50 minutes: the first read's 426 pages took about 50 at one second apart, the
   server answering in about six), filed and parsed the same way, one `<id>-<year>.txt` a locality; its
   rows in `official/gate/om-mara.tsv`, then `generateEnvelopeRows` (the rows on the edge inside each
   locality's ride, ruling R210, replay every row of a locality's own; restore any other entry's file
   the task rewrites), the whole gate, `generateProofStamps`, `checkStamps`, both
   repositories committed, and `next_expected` moved on a year. Without it the held year ends on
   31 December 2027 and no Omani day of 2028 has been checked against the ministry's own page.

### TODO before 29 February 2028: Kyrgyzstan's leap day (kg.default)

The Muftiate prints the same times every year, and in a leap year the same rows by day of the year: its
29 February is every other year's 1 March, and every later day of 2028 the day after its month-day's.
The engine reads kg.default's fixed year by month and day (`fixedTableYear`, `DayComputer.fixedDay`), so
from 29 February 2028 to the year's end the app is a day behind the Muftiate's row. Where a time moves
later day by day it would start before the Muftiate's, by a minute or two (the daily change): Maghrib
and Isha first, until June, and Fajr later in the year; and where Fajr moves earlier, until June, the
end of eating would come after the Muftiate's. Which days and by how much has not been computed (the
Ramadan of 2028 ends before 29 February). The owner left the engine as it is (12 November 2026) and
asked that the gap cannot be forgotten:

- From 1 December 2027 every monitor run raises a Horizon item, "kg.default needs its 29 Feb 2028
  handling" (`Horizons.DATED`; a notice, not a failing test: the engine is right until the day itself).
  It goes only when kg.default's stamp holds 29 February and 1 March 2028 with nothing broken at every
  one of its units, for each of Fajr, the end of eating, Maghrib and Isha — that is when the gate has
  read the Muftiate's own rows for those days at every locality. One locality's row, or another
  event's, does not end it.
- The work: a day-of-year mode for kg.default's leap years in the shared engine's fixed-year mapping,
  with a test of the rule; the Muftiate's 2028 tables in the gate (the kg-muftiyat fetcher reads the
  current year and the next, so the monitor holds 2028 from its first run of January 2027 and prove adds
  the rows to `official/gate/kg-default.tsv`); the full gate; a release before 29 February 2028, since
  an installed app changes only with a new build.
- Before the engine is changed, prove reports 2028's early cells from 29 February as never-early items
  on the entry's own rows: the same reminder, sooner. Since ruling R185 such an item on a class A/B
  entry's own row also demotes the entry until it is mended: kg.default's city pages are then held and
  no release is cut, from the first monitor run that holds a 2028 table (January 2027), fourteen months
  before the day. That is a release stop the owner did not ask for in so many words; the way to never
  meet it is to build the day-of-year mode before that run (the December 2026 build).
