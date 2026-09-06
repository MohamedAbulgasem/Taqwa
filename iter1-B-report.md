# Iteration 1 — B: the Android widget's cadence, and its empty space

> Written in the worktree: writing to `…/Taqwa/.superpowers/sdd/iter1-B-report.md` is refused by
> this agent's worktree isolation. Copy it across when merging.

**Status:** done.
**Branch:** `worktree-agent-afdc15c2ed61b868e`
**Base:** `3f3a16e` — 254 JVM / 255 iOS.
**Commits:** `870d10b`, `142c16c`, `a7f22b2`.
**Tests:** `./scripts/test.sh` green — **263 JVM / 255 iOS** (+9 JVM, all new; none weakened or
deleted). `./gradlew :androidApp:assembleDebug` BUILD SUCCESSFUL.

**Maximum staleness observed over 20 minutes: 5 minutes** (upper bound 5 m 37 s — see below).

## 8a — the cadence

Four layers, in descending precision:

| Layer | Where | Cost |
|---|---|---|
| Exact, at each prayer boundary | `PrayerAlarmReceiver` rides its existing exact alarm, via `goAsync` | one redraw per prayer |
| Rolling, on the 5-minute grid | `WidgetRefreshScheduler` + `TaqwaWidgetRefreshReceiver` | one inexact `RTC` window alarm per 5 min, only while a widget is placed |
| On unlock | runtime `ACTION_USER_PRESENT` receiver from `TaqwaApplication` | one redraw per unlock |
| Backstops | mirror write; `updatePeriodMillis` (already at the 30-minute floor) | unchanged |

The rolling alarm is `setWindow(RTC, …)`, never `setExact*`: it batches, defers wholesale under
Doze, needs no `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` grant — that budget stays with the adhan —
and `RTC` rather than `RTC_WAKEUP` means it never wakes a sleeping device. The chain re-arms from
its own receiver, is started from the Glance providers' `onEnabled`/`onUpdate` (self-healing after
a reboot or force-stop) and cancelled when the last widget leaves the launcher.

`WidgetRefreshScheduler.nextBoundaryAfter` is pure and covered by 9 new tests: the strictly-after
rule the re-arm depends on, the wall-clock grid, and a pre-epoch clock, where a bare `%` would arm
the alarm permanently in the past.

### The window had to shrink (`142c16c`)

The design called for a 5-minute window on a 5-minute grid. Measured, that does not give a
5-minute cadence. `setWindow` is a promise of "any time in this range", and with nothing to batch
against the platform spends the whole range: a `[15:05, 15:10]` alarm was still undelivered at
15:08 (`whenElapsed` 2 m 20 s overdue, `maxWhenElapsed` 2 m 39 s away). Since the receiver re-arms
from the boundary after *its own* delivery, a delivery at 15:10 arms 15:15, and redraws land ten
minutes apart — twice the staleness the grid was chosen for. One minute is latitude enough to
batch while bounding what the user sees, is still fully inexact, and clears the platform's own
floor for inexact windows (a tenth of the alarm's futurity, 30 s here).

## 8b — the empty space

Two causes, both fixed:

- `resizeMode="none"` meant a card the launcher had placed too tall could not be dragged back.
  Both providers are now `horizontal|vertical`, with `minResize`/`maxResize` bounds and the
  existing 2×2 / 4×2 `targetCell` declarations. Verified live: `dumpsys appwidget` reports
  `resizeMode=3`, and the medium widget was resized on the device from ~2 cells to 4.
- The type was sized for a cell the launcher never gives. `SizeMode.Responsive` now declares four
  shapes and scales into whichever it gets. The fourth (`a7f22b2`) came from measuring the owner's
  phone: what that launcher calls a two-by-two is **190 × 247 dp**, so the largest of the three
  original buckets that fitted was 110 × 180 — a card typeset for two-thirds of its own height.
  At 170 × 200 the countdown goes to 52 sp, capped by width (Glance clips rather than shrinking,
  and "12:34" is 2.51 em), with the label, clock time and gaps growing to match.

## Evidence

- `/tmp/iter1-B-small.png` — the small widget on the owner's phone, new layout (52 sp countdown).
- `/tmp/iter1-B-medium.png` — the medium widget at a true 4×2; all five rows one line each, names
  untruncated, current prayer in accent.
- `/tmp/iter1-B-sheet.png` — the 20-minute run as one contact sheet, each frame labelled with the
  device clock and the countdown that was true at that instant.
- `/tmp/iter1-B-emu-t1.png` … `t11.png` — the raw frames.

### The staleness measurement

| t | clock | shown | true | staleness (s) |
|---|---|---|---|---|
| 1 | 15:10:31 | 1:57 | 1:56 | 31..91 |
| 2 | 15:12:32 | 1:56 | 1:54 | 92..152 |
| 3 | 15:14:32 | 1:56 | 1:52 | 212..272 |
| 4 | 15:16:33 | 1:51 | 1:50 | 33..93 |
| 5 | 15:18:34 | 1:51 | 1:48 | 154..214 |
| 6 | 15:20:34 | 1:46 | 1:46 | 0..34 |
| 7 | 15:22:34 | 1:46 | 1:44 | 94..154 |
| 8 | 15:24:35 | 1:46 | 1:42 | 215..275 |
| 9 | 15:26:36 | 1:41 | 1:40 | 36..96 |
| 10 | 15:28:36 | 1:41 | 1:38 | 156..216 |
| 11 | 15:30:37 | 1:41 | 1:36 | 277..337 |

A whole-minute countdown `D` was the right answer over exactly one minute of wall clock, which is
what bounds the two columns. **Never more than 5 minutes out; upper bound 5 m 37 s.** The redraws
themselves, from `AppWidgetServiceImpl`, were 15:11:01, 15:15:11, 15:20:30, 15:25:07 — gaps of
4 m 10 s, 5 m 19 s, 4 m 38 s, with no drift, over an app whose process held no Activity.

`adb shell dumpsys alarm` during the run, on both devices:

```
tag=*alarm*:world.taqwa.app.WIDGET_REFRESH
type=RTC origWhen=2026-09-06 15:25:00.000 window=+1m0s0ms
```

`adb logcat -d | grep -ci 'multiple DataStores'` → **0** on both devices. The receivers read the
SharedPreferences mirror through `WidgetMirrorWriter`; none of them builds a store.

## Where the measurement was taken, and why

The 20-minute run is from a **Pixel 8 Pro emulator (API 36)**, not the owner's phone. Another agent
was reinstalling `world.taqwa.app` on the phone throughout the window — `lastUpdateTime` 14:51:24,
14:55:11, 14:56:28, 15:04:28, 15:10:38, 15:12:21 — and at least one of those installs cleared app
data, which `dumpsys alarm` records as `Reason=data_cleared` against every Taqwa alarm and which
also **removed the Taqwa widget from the launcher**. Two attempts to measure on the phone were
destroyed mid-run by that (their build carries no `WIDGET_REFRESH` receiver, so the chain simply
stops).

What *was* verified on the phone, in the gaps:

- The small widget re-placed from the launcher's own picker, which offers it as "2 × 2" — and it
  lands as one. `/tmp/iter1-B-small.png` (15:52:26, `ASR IN 0:10 / 16:03`, correct).
- The countdown correct against the wall clock at 15:21:24 (`0:42`, drawn 15:20:48), 15:23:42
  (`0:39`), 15:34:49 (`0:28`) and 15:52:26 (`0:10`).
- The unlock receiver **live in the process**, which is the thing that could silently not be:

  ```
  * ReceiverList{ae0678b 15684 world.taqwa.app/10236/u0 remote:380195a}
      Action: "android.intent.action.USER_PRESENT"
  ```

  The same `dumpsys` shows why it has to be a runtime receiver: every manifest-declared
  `USER_PRESENT` receiver in that broadcast was `skipped by policy at enqueue: Background
  execution not allowed`.
- The window alarm armed and re-armed, **with one difference worth knowing**: this device's OS
  clamps the window up. A 60 s request came back as `window=+3m9s687ms`. The floor is the
  platform's to choose and there is no API to read it beforehand, so the worst case on this
  handset is a redraw every ~5 min with up to ~3 min of slack rather than ~1. The unlock refresh
  and the prayer-boundary alarm are what cover that, which is why the design is layered.

The unlock refresh's *effect* could not be timed on this phone, because nothing there ever goes
stale enough to show it: with the app process resident, the mirror is rewritten — and the widget
redrawn — every single minute, screen off, Activity stopped, for as long as the process lives
(15:47:01, 15:48:00, 15:49:01, 15:50:00, 15:51:01 through a five-minute screen-off window). That
is `TodayViewModel`'s own ticker running in the background; it is not this iteration's code and it
belongs to the app-UI owner. Flagged separately.

## Known limits

- `ACTION_USER_PRESENT` cannot be manifest-declared, so its receiver lives only as long as the
  process. In practice the rolling alarm keeps a process around; after a force-stop nothing
  refreshes until the next `APPWIDGET_UPDATE` re-arms the chain.
- The widget instance the owner had on the phone before this iteration is gone — removed by the
  other agent's data-clear, not by this work. A fresh 2×2 has been placed back; the medium has
  not, as its home page has no free 4×2 block and rearranging the owner's icons was out of scope.
