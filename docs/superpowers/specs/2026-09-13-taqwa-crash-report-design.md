# Taqwa crash report — design

**Date:** 2026-09-13 · **Branch:** `crash-report` · **Approved in chat by Mohamed, 13 September 2026**

## 1. Why

Taqwa ships with no crash-reporting SDK, by design (privacy spec §2: no analytics, no third-party
code that phones home). The stores' own crash pages only show what the phone chose to send under
its own settings, which for most Android phones and every iPhone with "Share with developers" off
is nothing. So today a crash in the field is invisible unless the person emails about it, and even
then they cannot say what happened.

This feature lets the app itself keep the evidence, on the phone, and lets the person send it in
one tap if they want to. Nothing leaves the phone otherwise.

## 2. What is captured

**When:** an uncaught exception in Kotlin code, on either platform.

- Android: `Thread.setDefaultUncaughtExceptionHandler` installed in `TaqwaApplication.onCreate`
  before anything else runs. The handler writes the report and then calls the handler that was
  there before it, so the process still dies the way it does today (the system dialog, and the
  phone's own reporting, are untouched).
- iOS: Kotlin/Native's unhandled-exception hook (`setUnhandledExceptionHook`), installed from
  shared code when the root view controller is created. Kotlin/Native terminates the process after
  the hook returns, which is the behaviour we want. Objective-C exceptions and native signals
  (memory faults) are out of scope: they cannot be caught without a crash-reporter SDK, and the
  phone's own crash log in Settings still covers them.

**What:** one plain-text file, `crash-report.txt`, in the app's private files directory (the
same directory as the settings DataStore, so it is backed up or not exactly as the settings are).
Each crash overwrites it: only the latest crash is kept. Its contents, in order:

```
Taqwa crash report
saved: 2026-09-13T21:05:12Z            (UTC, ISO-8601)
app: 1.0.0 (20)
platform: Android 16 (SDK 36)          (or: iOS 18.6.2)
device: samsung SM-S918B               (or: iPhone14,5)
language: en                           (the phone's language tag)
thread: main

java.lang.IllegalStateException: …
    at …                               (the full stack trace, with causes)
```

Nothing else. No location, no settings, no bookmarks, no counts, no identifiers.

The file is capped at 16 000 characters (a trace longer than that is cut with a `… (trimmed)`
line) so a runaway recursive exception cannot fill the disk.

## 3. What the person sees

### 3.1 The sheet, on the next launch

When the app starts and a report exists that has not yet been offered, the root composable shows
a bottom sheet (the app's `TaqwaBottomSheet`, same chrome as every other sheet):

- Title: **Taqwa closed unexpectedly** (`crash_sheet_title`)
- Body: *A technical report was saved on your phone. Sending it to support@taqwa.world helps fix
  the problem. It contains the app version, your phone model and the error, nothing else.*
  (`crash_sheet_body`)
- Primary pill: **Send report** (`crash_send`) — opens the mail app (§4) and marks the report as
  offered.
- Text link: **Not now** (`crash_not_now`) — marks the report as offered and dismisses. Swiping
  the sheet away does the same.

"Offered" is a sidecar file, `crash-report.offered`, holding the `saved:` timestamp of the report
it refers to. The sheet appears once per crash: a new crash has a new timestamp, so it is offered
again; the same report is never offered twice. The report itself stays on the phone until the
next crash overwrites it, so it can still be sent later from About.

### 3.2 The About screen

The MORE list gains a last row, **Report a problem** (`about_report_problem`), with the same
external-link glyph as the rows above it. It opens the mail app with the device lines from §2
and, if a report exists, the report itself. When no report exists the body says so
(`crash_mail_no_report`: *No crash report is saved on this phone.*) followed by the device lines,
so a general complaint still carries what support needs.

Arabic: every string above has an Arabic value; the sheet and the row are laid out by the
existing RTL machinery.

## 4. The email

A `mailto:` URL opened through `LocalUriHandler`, which hands it to the phone's mail app with the
recipient, subject and body filled in. The person reads what is about to be sent and taps Send
there; the app never sends anything itself.

- To: `support@taqwa.world` (`AboutLinks.SUPPORT_EMAIL`, the same address as the site and the
  policy).
- Subject: `Taqwa report · 1.0.0 (20) · Android` (not localised; it is read by support).
- Body: the report, compacted to fit what mail apps accept from a link. The header lines are
  kept whole; the stack trace is cut to the first lines that fit within 1 800 characters of
  body, ending with `… (trimmed)`. Android's mail intents and iOS Mail both take bodies of this
  size; the full file stays on the phone in case the trimmed version is not enough and support
  asks for it.

Subject and body are percent-encoded per RFC 3986 (space → `%20`, newline → `%0A`, `+` is not
used, since some mail apps read it literally).

## 5. Code

New package `world.taqwa.app.crash` in `shared`:

| File | Responsibility |
|---|---|
| `DeviceInfo.kt` | `data class DeviceInfo(appVersion, build, platform, device, language)` and `expect fun deviceInfo(): DeviceInfo`. Android actual reads `PackageInfo`, `Build`, `Locale`; iOS actual reads `NSBundle`, `UIDevice`, `utsname`, `NSLocale`. |
| `CrashReport.kt` | `CrashReportFormat.render(throwable, threadName, info, savedAt): String` — the text in §2, capped. `CrashReport(savedAt: Instant, text: String)` and `CrashReport.parse(text)` (reads `saved:` back). Pure Kotlin. |
| `CrashLogStore.kt` | `class CrashLogStore(dir: Path, fs: FileSystem = FileSystem.SYSTEM)` — `write(text)`, `read(): CrashReport?`, `markOffered(report)`, `pendingOffer(): CrashReport?` (a report not yet offered), `clear()`. Every method swallows I/O failures: a store that throws inside a crash handler would hide the crash it is trying to record. |
| `CrashHandler.kt` | `fun recordCrash(store, throwable, threadName, info, now)` (common, testable) and `expect fun installCrashHandler(store: CrashLogStore)` with the two actuals from §2. `val crashLogStore: CrashLogStore by lazy` next to `appContainer`, so the entry points and the container share one instance. |
| `ReportMail.kt` | `ReportMail.subject(info)`, `ReportMail.body(info, report, noReportLine)`, `ReportMail.mailto(to, subject, body)`; `percentEncode(String)`. Pure Kotlin. |

UI:

| File | Change |
|---|---|
| `feature/crash/CrashReportSheet.kt` | The sheet in §3.1: `CrashReportSheet(onSend, onDismiss)`. |
| `App.kt` | Reads `crashLogStore.pendingOffer()` once at start; hosts the sheet beside the recitation sheets; on Send builds the mail from `deviceInfo()` and the report, marks offered, opens the URL. |
| `feature/settings/AboutScreen.kt` | The **Report a problem** row (§3.2). |
| `about/AboutLinks.kt` | `SUPPORT_EMAIL`. |
| `TaqwaApplication.kt` / `MainViewController.kt` | Call `installCrashHandler(crashLogStore)` first thing. |
| `strings.xml` (en, ar) | The seven strings named above. |
| `PRIVACY.md`, `PRIVACY.ar.md` | One sentence under "What stays on your phone": *If Taqwa crashes, it saves a technical report on your phone (the app version, your phone model and the error, nothing else). It never leaves the phone unless you choose to email it to us.* |

## 6. Tests

- `CrashReportFormatTest`: header lines in order; the trace present; causes present; the cap
  applied with the `(trimmed)` marker; parse round-trips `saved:`.
- `CrashLogStoreTest` (okio `FakeFileSystem`): write then read; a second write replaces the
  first; `pendingOffer` is the report until `markOffered`, then null; a new report with a newer
  timestamp is pending again; a corrupt file reads as null; the store never throws when the
  directory is unwritable.
- `ReportMailTest`: encoding of space, newline, `&`, `?`, `#`, non-ASCII; the body keeps every
  header line and cuts the trace at the cap; the no-report body; the subject.
- `recordCrash` never throws (a store over a read-only file system).
- Devices: a temporary **Crash now** row in About (a commit that is reverted before merge)
  proves the whole path on the S23 and on iOS: the crash, the relaunch, the sheet, Send opening
  the mail app with the right recipient, subject and body, Not now dismissing for good, and the
  About row carrying the report. Arabic checked on Android.

## 7. Out of scope

Native crashes, ANRs, multiple stored reports, automatic sending, a crash counter, and any
in-app viewer for the report.
