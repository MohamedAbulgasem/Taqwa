# Taqwa crash report — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When Taqwa crashes, keep a technical report on the phone and let the person email it to support in one tap, on Android and iOS, without any SDK.

**Architecture:** A pure-Kotlin core in `shared/…/crash` (format, file store, mailto builder) with one `expect` for device facts and one for installing the platform hook. The root composable shows a sheet once per new report; the About screen gets a permanent row. Everything I/O-facing swallows failures, because it runs inside a crash.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, okio (`FileSystem`, `FakeFileSystem` in tests), `kotlin.time.Instant`, Android `Thread.setDefaultUncaughtExceptionHandler`, Kotlin/Native `setUnhandledExceptionHook`.

**Spec:** `docs/superpowers/specs/2026-09-13-taqwa-crash-report-design.md`

## Global Constraints

- No new dependencies. No network. The report holds only: app version and build, platform, device, language tag, thread name, the exception and its trace.
- Support address is `support@taqwa.world`, one constant (`AboutLinks.SUPPORT_EMAIL`).
- File cap 16 000 characters; mail body cap 1 800 characters; the marker is `… (trimmed)`.
- Every new string exists in `values/strings.xml` and `values-ar/strings.xml` (`scripts/check-strings.sh` must pass).
- Run `scripts/test.sh` (both targets) before merging. The temporary crash trigger is reverted before merge.

---

### Task 1: Report format and parse

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/crash/DeviceInfo.kt` (the data class only; the `expect` comes in Task 4)
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/crash/CrashReport.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/crash/CrashReportFormatTest.kt`

**Interfaces:**
- Produces: `data class DeviceInfo(val appVersion: String, val build: String, val platform: String, val device: String, val language: String)`; `data class CrashReport(val savedAt: Instant, val text: String)`; `object CrashReportFormat { const val MAX_CHARS = 16_000; const val TRIMMED = "… (trimmed)"; fun render(throwable: Throwable, threadName: String, info: DeviceInfo, savedAt: Instant): String; fun parse(text: String): CrashReport? }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package world.taqwa.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrashReportFormatTest {
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en-GB")
    private val at = Instant.parse("2026-09-13T21:05:12Z")

    @Test
    fun theHeaderNamesTheAppThePhoneAndTheMoment() {
        val text = CrashReportFormat.render(IllegalStateException("boom"), "main", info, at)
        val lines = text.lines()
        assertEquals("Taqwa crash report", lines[0])
        assertEquals("saved: 2026-09-13T21:05:12Z", lines[1])
        assertEquals("app: 1.0.0 (20)", lines[2])
        assertEquals("platform: Android 16 (SDK 36)", lines[3])
        assertEquals("device: samsung SM-S918B", lines[4])
        assertEquals("language: en-GB", lines[5])
        assertEquals("thread: main", lines[6])
        assertEquals("", lines[7])
    }

    @Test
    fun theTraceAndItsCauseFollowTheHeader() {
        val cause = IllegalArgumentException("root")
        val text = CrashReportFormat.render(IllegalStateException("boom", cause), "main", info, at)
        assertTrue("IllegalStateException: boom" in text, text)
        assertTrue("IllegalArgumentException: root" in text, text)
        assertTrue("theTraceAndItsCauseFollowTheHeader" in text, text)
    }

    @Test
    fun aRunawayTraceIsCutAtTheCap() {
        val huge = RuntimeException("x".repeat(40_000))
        val text = CrashReportFormat.render(huge, "main", info, at)
        assertTrue(text.length <= CrashReportFormat.MAX_CHARS + CrashReportFormat.TRIMMED.length + 1, "${text.length}")
        assertTrue(text.endsWith(CrashReportFormat.TRIMMED))
    }

    @Test
    fun parseReadsTheSavedMomentBack() {
        val text = CrashReportFormat.render(RuntimeException("boom"), "main", info, at)
        val report = CrashReportFormat.parse(text)
        assertEquals(at, report?.savedAt)
        assertEquals(text, report?.text)
    }

    @Test
    fun parseRefusesTextThatIsNotAReport() {
        assertNull(CrashReportFormat.parse(""))
        assertNull(CrashReportFormat.parse("saved: yesterday\nsomething"))
        assertNull(CrashReportFormat.parse("Taqwa crash report\nnot a saved line"))
    }
}
```

- [ ] **Step 2: Run the tests, expect a compile failure on the missing types**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.crash.CrashReportFormatTest -q`
Expected: `Unresolved reference 'DeviceInfo'` / `'CrashReportFormat'`

- [ ] **Step 3: Implement**

`DeviceInfo.kt`:

```kotlin
package world.taqwa.app.crash

/** The five facts a report carries about where it happened. Nothing in here identifies a person. */
data class DeviceInfo(
    val appVersion: String,
    val build: String,
    val platform: String,
    val device: String,
    val language: String,
)
```

`CrashReport.kt`:

```kotlin
package world.taqwa.app.crash

import kotlin.time.Instant

/** A report read back from disk: when it was saved, and the whole text as saved. */
data class CrashReport(val savedAt: Instant, val text: String)

/**
 * The text of a crash report (crash spec §2). Header lines first, a blank line, then the trace.
 * The header is fixed in order so support can read it at a glance and so [parse] can find the
 * moment it was saved without a real parser.
 */
object CrashReportFormat {
    const val TITLE = "Taqwa crash report"
    const val SAVED_PREFIX = "saved: "
    const val MAX_CHARS = 16_000
    const val TRIMMED = "… (trimmed)"

    fun render(throwable: Throwable, threadName: String, info: DeviceInfo, savedAt: Instant): String {
        val header = buildString {
            appendLine(TITLE)
            appendLine("$SAVED_PREFIX$savedAt")
            appendLine("app: ${info.appVersion} (${info.build})")
            appendLine("platform: ${info.platform}")
            appendLine("device: ${info.device}")
            appendLine("language: ${info.language}")
            appendLine("thread: $threadName")
            appendLine()
        }
        val trace = throwable.stackTraceToString()
        val full = header + trace
        if (full.length <= MAX_CHARS) return full
        return full.take(MAX_CHARS) + "\n" + TRIMMED
    }

    fun parse(text: String): CrashReport? {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != TITLE) return null
        if (!lines.hasNext()) return null
        val saved = lines.next()
        if (!saved.startsWith(SAVED_PREFIX)) return null
        val at = runCatching { Instant.parse(saved.removePrefix(SAVED_PREFIX)) }.getOrNull() ?: return null
        return CrashReport(at, text)
    }
}
```

- [ ] **Step 4: Run the tests, expect them to pass**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.crash.CrashReportFormatTest -q`
Expected: no output (pass). Check `shared/build/test-results/testDebugUnitTest/TEST-world.taqwa.app.crash.CrashReportFormatTest.xml` says `failures="0"`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/crash shared/src/commonTest/kotlin/world/taqwa/app/crash
git commit -m "crash: the report text, its cap and parse"
```

---

### Task 2: The file store

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/crash/CrashLogStore.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/crash/CrashLogStoreTest.kt`

**Interfaces:**
- Consumes: `CrashReportFormat.parse`, `CrashReport`
- Produces: `class CrashLogStore(dir: Path, fs: FileSystem = FileSystem.SYSTEM) { fun write(text: String); fun read(): CrashReport?; fun pendingOffer(): CrashReport?; fun markOffered(report: CrashReport); fun clear() }`; file names `crash-report.txt` and `crash-report.offered`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package world.taqwa.app.crash

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CrashLogStoreTest {
    private val fs = FakeFileSystem()
    private val dir = "/data/taqwa".toPath()
    private val store = CrashLogStore(dir, fs)
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en")

    private fun report(at: String) =
        CrashReportFormat.render(RuntimeException("boom at $at"), "main", info, Instant.parse(at))

    @Test
    fun aWrittenReportReadsBack() {
        store.write(report("2026-09-13T21:05:12Z"))
        val read = store.read()
        assertEquals(Instant.parse("2026-09-13T21:05:12Z"), read?.savedAt)
    }

    @Test
    fun theSecondCrashReplacesTheFirst() {
        store.write(report("2026-09-13T21:05:12Z"))
        store.write(report("2026-09-14T08:00:00Z"))
        assertEquals(Instant.parse("2026-09-14T08:00:00Z"), store.read()?.savedAt)
    }

    @Test
    fun aReportIsPendingUntilOfferedAndANewerOneIsPendingAgain() {
        store.write(report("2026-09-13T21:05:12Z"))
        val first = store.pendingOffer()
        assertEquals(Instant.parse("2026-09-13T21:05:12Z"), first?.savedAt)
        store.markOffered(first!!)
        assertNull(store.pendingOffer())
        assertEquals(first.savedAt, store.read()?.savedAt) // still on the phone for About
        store.write(report("2026-09-14T08:00:00Z"))
        assertEquals(Instant.parse("2026-09-14T08:00:00Z"), store.pendingOffer()?.savedAt)
    }

    @Test
    fun nothingWrittenMeansNothingToRead() {
        assertNull(store.read())
        assertNull(store.pendingOffer())
    }

    @Test
    fun aCorruptFileReadsAsNothing() {
        fs.createDirectories(dir)
        fs.write(dir / "crash-report.txt") { writeUtf8("garbage") }
        assertNull(store.read())
    }

    @Test
    fun clearRemovesBothFiles() {
        store.write(report("2026-09-13T21:05:12Z"))
        store.markOffered(store.read()!!)
        store.clear()
        assertNull(store.read())
        assertEquals(false, fs.exists(dir / "crash-report.offered"))
    }

    @Test
    fun anUnwritableDirectoryNeverThrows() {
        val broken = CrashLogStore("/nope".toPath(), ReadOnlyFileSystem(fs))
        broken.write("Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\n")
        assertNull(broken.read())
        broken.clear()
    }
}

/** okio has no read-only wrapper; this one throws on every write, which is what a full or locked disk does. */
private class ReadOnlyFileSystem(private val inner: okio.FileSystem) : okio.ForwardingFileSystem(inner) {
    override fun sink(file: okio.Path, mustCreate: Boolean): okio.Sink = throw okio.IOException("read only")
    override fun createDirectory(dir: okio.Path, mustCreate: Boolean) = throw okio.IOException("read only")
    override fun atomicMove(source: okio.Path, target: okio.Path) = throw okio.IOException("read only")
    override fun delete(path: okio.Path, mustExist: Boolean) = throw okio.IOException("read only")
}
```

- [ ] **Step 2: Run, expect `Unresolved reference 'CrashLogStore'`**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.crash.CrashLogStoreTest -q`

- [ ] **Step 3: Implement**

```kotlin
package world.taqwa.app.crash

import okio.FileSystem
import okio.Path

/**
 * The one crash report kept on the phone (crash spec §2) and whether it has been offered yet
 * (§3.1). Two files beside the settings: `crash-report.txt` is the report, `crash-report.offered`
 * holds the `saved:` moment of the report the sheet already showed. Every method swallows I/O
 * failure — this runs inside a crash handler, where a second exception would only hide the first,
 * and at start-up, where a bad disk must not stop the app opening.
 */
class CrashLogStore(private val dir: Path, private val fs: FileSystem = FileSystem.SYSTEM) {
    private val reportFile = dir / REPORT_FILE
    private val offeredFile = dir / OFFERED_FILE

    fun write(text: String) {
        runCatching {
            fs.createDirectories(dir)
            fs.write(reportFile) { writeUtf8(text) }
        }
    }

    fun read(): CrashReport? = runCatching {
        if (!fs.exists(reportFile)) return null
        CrashReportFormat.parse(fs.read(reportFile) { readUtf8() })
    }.getOrNull()

    /** The report, if there is one the sheet has not shown yet. */
    fun pendingOffer(): CrashReport? {
        val report = read() ?: return null
        val offered = runCatching {
            if (fs.exists(offeredFile)) fs.read(offeredFile) { readUtf8() }.trim() else null
        }.getOrNull()
        return if (offered == report.savedAt.toString()) null else report
    }

    fun markOffered(report: CrashReport) {
        runCatching {
            fs.createDirectories(dir)
            fs.write(offeredFile) { writeUtf8(report.savedAt.toString()) }
        }
    }

    fun clear() {
        runCatching { fs.delete(reportFile, mustExist = false) }
        runCatching { fs.delete(offeredFile, mustExist = false) }
    }

    companion object {
        const val REPORT_FILE = "crash-report.txt"
        const val OFFERED_FILE = "crash-report.offered"
    }
}
```

- [ ] **Step 4: Run, expect pass**
- [ ] **Step 5: Commit** — `git commit -m "crash: the on-phone store and the offered marker"`

---

### Task 3: The email

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/crash/ReportMail.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/about/AboutLinks.kt` (add `SUPPORT_EMAIL`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/crash/ReportMailTest.kt`

**Interfaces:**
- Produces: `object ReportMail { const val BODY_MAX = 1_800; fun subject(info: DeviceInfo): String; fun body(info: DeviceInfo, report: CrashReport?, noReportLine: String): String; fun mailto(to: String, subject: String, body: String): String; fun percentEncode(s: String): String }`; `AboutLinks.SUPPORT_EMAIL = "support@taqwa.world"`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package world.taqwa.app.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReportMailTest {
    private val info = DeviceInfo("1.0.0", "20", "Android 16 (SDK 36)", "samsung SM-S918B", "en")
    private val at = Instant.parse("2026-09-13T21:05:12Z")

    @Test
    fun theSubjectNamesTheVersionAndThePlatform() {
        assertEquals("Taqwa report · 1.0.0 (20) · Android 16 (SDK 36)", ReportMail.subject(info))
    }

    @Test
    fun encodingKeepsUnreservedCharactersAndEscapesTheRest() {
        assertEquals("a-b_c.d~e", ReportMail.percentEncode("a-b_c.d~e"))
        assertEquals("a%20b%0Ac", ReportMail.percentEncode("a b\nc"))
        assertEquals("%26%3F%23%2B%25", ReportMail.percentEncode("&?#+%"))
        assertEquals("%D8%AA%D9%82%D9%88%D9%89", ReportMail.percentEncode("تقوى"))
    }

    @Test
    fun mailtoCarriesRecipientSubjectAndBody() {
        val url = ReportMail.mailto("support@taqwa.world", "Hi there", "line 1\nline 2")
        assertEquals("mailto:support@taqwa.world?subject=Hi%20there&body=line%201%0Aline%202", url)
    }

    @Test
    fun theBodyIsTheReportWhenThereIsOne() {
        val text = CrashReportFormat.render(RuntimeException("boom"), "main", info, at)
        val body = ReportMail.body(info, CrashReport(at, text), "No crash report is saved on this phone.")
        assertEquals(text, body)
    }

    @Test
    fun aLongReportKeepsItsHeaderAndCutsTheTrace() {
        val text = CrashReportFormat.render(RuntimeException("x".repeat(5_000)), "main", info, at)
        val body = ReportMail.body(info, CrashReport(at, text), "none")
        assertTrue(body.length <= ReportMail.BODY_MAX + CrashReportFormat.TRIMMED.length + 1, "${body.length}")
        assertTrue(body.startsWith("Taqwa crash report\nsaved: 2026-09-13T21:05:12Z\napp: 1.0.0 (20)\n"))
        assertTrue(body.endsWith(CrashReportFormat.TRIMMED))
    }

    @Test
    fun withoutAReportTheBodySaysSoAndStillCarriesTheDevice() {
        val body = ReportMail.body(info, null, "No crash report is saved on this phone.")
        assertEquals(
            "No crash report is saved on this phone.\n\napp: 1.0.0 (20)\nplatform: Android 16 (SDK 36)\ndevice: samsung SM-S918B\nlanguage: en\n",
            body,
        )
    }
}
```

- [ ] **Step 2: Run, expect `Unresolved reference 'ReportMail'`**
- [ ] **Step 3: Implement**

```kotlin
package world.taqwa.app.crash

/**
 * The support email (crash spec §4): a `mailto:` URL the phone's mail app opens with everything
 * filled in. The person still reads and sends it; the app sends nothing.
 */
object ReportMail {
    /** What mail apps reliably accept from a link. The full report stays on the phone. */
    const val BODY_MAX = 1_800

    fun subject(info: DeviceInfo): String = "Taqwa report · ${info.appVersion} (${info.build}) · ${info.platform}"

    fun body(info: DeviceInfo, report: CrashReport?, noReportLine: String): String {
        if (report == null) {
            return buildString {
                appendLine(noReportLine)
                appendLine()
                appendLine("app: ${info.appVersion} (${info.build})")
                appendLine("platform: ${info.platform}")
                appendLine("device: ${info.device}")
                appendLine("language: ${info.language}")
            }
        }
        val text = report.text
        if (text.length <= BODY_MAX) return text
        // Cut on a line boundary so the last frame is whole, never mid-path.
        val cut = text.lastIndexOf('\n', BODY_MAX).takeIf { it > 0 } ?: BODY_MAX
        return text.take(cut) + "\n" + CrashReportFormat.TRIMMED
    }

    fun mailto(to: String, subject: String, body: String): String =
        "mailto:$to?subject=${percentEncode(subject)}&body=${percentEncode(body)}"

    /** RFC 3986: unreserved characters as they are, every other byte of UTF-8 as %XX. */
    fun percentEncode(s: String): String = buildString {
        for (byte in s.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                append(ch)
            } else {
                append('%')
                append(HEX[c shr 4])
                append(HEX[c and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
```

And in `AboutLinks`: `const val SUPPORT_EMAIL = "support@taqwa.world"`.

- [ ] **Step 4: Run, expect pass**
- [ ] **Step 5: Commit** — `git commit -m "crash: the support email, encoded and trimmed"`

---

### Task 4: Device facts and the platform hooks

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/crash/DeviceInfo.kt` (add `expect fun deviceInfo(): DeviceInfo`)
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/crash/DeviceInfo.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/crash/DeviceInfo.ios.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/crash/CrashHandler.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/crash/CrashHandler.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/crash/CrashHandler.ios.kt`
- Modify: `androidApp/src/androidMain/kotlin/world/taqwa/app/TaqwaApplication.kt`, `shared/src/iosMain/kotlin/world/taqwa/app/MainViewController.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/crash/RecordCrashTest.kt`

**Interfaces:**
- Produces: `expect fun deviceInfo(): DeviceInfo`; `fun recordCrash(store: CrashLogStore, throwable: Throwable, threadName: String, info: DeviceInfo, now: Instant = Clock.System.now())`; `expect fun installCrashHandler(store: CrashLogStore)`; `val crashLogStore: CrashLogStore by lazy { CrashLogStore(dataStoreDirectory().toPath()) }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package world.taqwa.app.crash

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordCrashTest {
    private val info = DeviceInfo("1.0.0", "20", "iOS 18.6", "iPhone14,5", "ar")

    @Test
    fun recordingWritesAReportTheStoreReadsBack() {
        val store = CrashLogStore("/data".toPath(), FakeFileSystem())
        recordCrash(store, IllegalStateException("boom"), "main", info, Instant.parse("2026-09-13T21:05:12Z"))
        val report = store.read()
        assertEquals(Instant.parse("2026-09-13T21:05:12Z"), report?.savedAt)
        assertTrue("IllegalStateException: boom" in report!!.text)
    }

    @Test
    fun recordingNeverThrowsEvenWhenTheStoreCannotWrite() {
        val fs = FakeFileSystem()
        val store = CrashLogStore("/data".toPath(), fs)
        fs.createDirectories("/data".toPath())
        fs.write("/data/crash-report.txt".toPath()) { writeUtf8("old") }
        fs.setReadOnly = true // FakeFileSystem has no such flag; see the implementation note below
        recordCrash(store, RuntimeException("boom"), "main", info)
    }
}
```

Implementation note: `FakeFileSystem` has no read-only switch; reuse the `ReadOnlyFileSystem` wrapper from Task 2 (move it to a shared test helper `shared/src/commonTest/kotlin/world/taqwa/app/crash/ReadOnlyFileSystem.kt`) and drop the `setReadOnly` line.

- [ ] **Step 2: Run, expect `Unresolved reference 'recordCrash'`**
- [ ] **Step 3: Implement**

`CrashHandler.kt` (common):

```kotlin
package world.taqwa.app.crash

import okio.Path.Companion.toPath
import world.taqwa.app.settings.dataStoreDirectory
import kotlin.time.Clock
import kotlin.time.Instant

/** One store for the process: the entry points install the hook on it, App reads from it. */
val crashLogStore: CrashLogStore by lazy { CrashLogStore(dataStoreDirectory().toPath()) }

/** Installs the platform's uncaught-exception hook so a crash writes [store] first (spec §2). */
expect fun installCrashHandler(store: CrashLogStore)

/**
 * What both hooks do. Wrapped whole in runCatching: this runs inside a crash, and a second
 * exception here would replace the one being recorded with a much less useful one.
 */
fun recordCrash(
    store: CrashLogStore,
    throwable: Throwable,
    threadName: String,
    info: DeviceInfo,
    now: Instant = Clock.System.now(),
) {
    runCatching { store.write(CrashReportFormat.render(throwable, threadName, info, now)) }
}
```

Android actual:

```kotlin
package world.taqwa.app.crash

actual fun installCrashHandler(store: CrashLogStore) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        recordCrash(store, throwable, thread.name, runCatching { deviceInfo() }.getOrElse { unknownDevice() })
        // The handler that was there before — the one that shows the system dialog and lets the
        // phone's own reporting see the crash — still runs. Without this the process would hang.
        previous?.uncaughtException(thread, throwable)
    }
}
```

iOS actual:

```kotlin
package world.taqwa.app.crash

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual fun installCrashHandler(store: CrashLogStore) {
    setUnhandledExceptionHook { throwable ->
        recordCrash(store, throwable, "main", runCatching { deviceInfo() }.getOrElse { unknownDevice() })
        // Kotlin/Native terminates the process once the hook returns; nothing else to do.
    }
}
```

`unknownDevice()` in common: `fun unknownDevice() = DeviceInfo("?", "?", "?", "?", "?")`.

`DeviceInfo.android.kt`:

```kotlin
package world.taqwa.app.crash

import android.os.Build
import world.taqwa.app.settings.appContext
import java.util.Locale

actual fun deviceInfo(): DeviceInfo {
    val pm = appContext.packageManager
    val pkg = pm.getPackageInfo(appContext.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()
    return DeviceInfo(
        appVersion = pkg.versionName ?: "?",
        build = code.toString(),
        platform = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        language = Locale.getDefault().toLanguageTag(),
    )
}
```

`DeviceInfo.ios.kt`:

```kotlin
package world.taqwa.app.crash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

@OptIn(ExperimentalForeignApi::class)
actual fun deviceInfo(): DeviceInfo {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    val model = memScoped {
        val u = alloc<utsname>()
        uname(u.ptr)
        u.machine.toKString()
    }
    return DeviceInfo(
        appVersion = version,
        build = build,
        platform = "iOS ${UIDevice.currentDevice.systemVersion}",
        device = model,
        language = NSLocale.currentLocale.localeIdentifier,
    )
}
```

Entry points: in `TaqwaApplication.onCreate`, right after `appContext = applicationContext`: `installCrashHandler(crashLogStore)`. In `MainViewController()`: `installCrashHandler(crashLogStore)` before `ComposeUIViewController` (a `by lazy` guard or a top-level `private val installed = run { … }` keeps it once).

- [ ] **Step 4: Run the unit test, then compile both targets**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.crash.RecordCrashTest -q` then `scripts/test.sh :shared:compileKotlinIosSimulatorArm64 :androidApp:compileDebugKotlin`
Expected: pass; both compile. If `setUnhandledExceptionHook` needs a different opt-in or import in this Kotlin, follow the compiler's message.

- [ ] **Step 5: Commit** — `git commit -m "crash: device facts and the uncaught-exception hooks on both platforms"`

---

### Task 5: Strings, the sheet, the About row, the policy

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`, `values-ar/strings.xml`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/crash/CrashReportSheet.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`, `feature/settings/AboutScreen.kt`
- Modify: `PRIVACY.md`, `PRIVACY.ar.md`

- [ ] **Step 1: Strings**

English:

```xml
    <!-- Crash report (crash spec §3). -->
    <string name="crash_sheet_title">Taqwa closed unexpectedly</string>
    <string name="crash_sheet_body">A technical report was saved on your phone. Sending it to support@taqwa.world helps fix the problem. It contains the app version, your phone model and the error, nothing else.</string>
    <string name="crash_send">Send report</string>
    <string name="crash_not_now">Not now</string>
    <string name="about_report_problem">Report a problem</string>
    <string name="crash_mail_no_report">No crash report is saved on this phone.</string>
```

Arabic:

```xml
    <!-- تقرير الأعطال -->
    <string name="crash_sheet_title">أُغلق تقوى بشكل غير متوقع</string>
    <string name="crash_sheet_body">حُفظ تقرير تقني على هاتفك. إرساله إلى support@taqwa.world يساعد على إصلاح المشكلة. يحتوي على إصدار التطبيق وطراز هاتفك والخطأ، لا غير.</string>
    <string name="crash_send">إرسال التقرير</string>
    <string name="crash_not_now">ليس الآن</string>
    <string name="about_report_problem">الإبلاغ عن مشكلة</string>
    <string name="crash_mail_no_report">لا يوجد تقرير أعطال محفوظ على هذا الهاتف.</string>
```

Run `scripts/check-strings.sh`.

- [ ] **Step 2: The sheet**

```kotlin
package world.taqwa.app.feature.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.TaqwaBottomSheet
import world.taqwa.app.design.components.TaqwaPrimaryButton
import world.taqwa.app.design.components.TaqwaTextLink
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.crash_not_now
import world.taqwa.app.resources.crash_send
import world.taqwa.app.resources.crash_sheet_body
import world.taqwa.app.resources.crash_sheet_title

/** Shown once per new crash report, on the launch after the crash (crash spec §3.1). */
@Composable
fun CrashReportSheet(onSend: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalTaqwaColors.current
    TaqwaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(Res.string.crash_sheet_title), style = TaqwaText.screenTitle.copy(fontSize = 20.sp), color = colors.textPrimary)
            Text(stringResource(Res.string.crash_sheet_body), style = TaqwaText.caption.copy(fontSize = 15.sp, lineHeight = 22.sp), color = colors.textSecondary)
            TaqwaPrimaryButton(stringResource(Res.string.crash_send), onClick = onSend, modifier = Modifier.padding(top = 6.dp))
            TaqwaTextLink(stringResource(Res.string.crash_not_now), onClick = onDismiss)
        }
    }
}
```

- [ ] **Step 3: Host it in App.kt**

Next to the recitation sheets:

```kotlin
    // The crash sheet (crash spec §3.1): read once per process, shown until answered.
    var crashOffer by remember { mutableStateOf(crashLogStore.pendingOffer()) }
    val uriHandler = LocalUriHandler.current
    val noReportLine = stringResource(Res.string.crash_mail_no_report)
    crashOffer?.let { report ->
        CrashReportSheet(
            onSend = {
                crashLogStore.markOffered(report)
                crashOffer = null
                val info = deviceInfo()
                runCatching { uriHandler.openUri(ReportMail.mailto(AboutLinks.SUPPORT_EMAIL, ReportMail.subject(info), ReportMail.body(info, report, noReportLine))) }
            },
            onDismiss = {
                crashLogStore.markOffered(report)
                crashOffer = null
            },
        )
    }
```

- [ ] **Step 4: The About row**

After the Licence row:

```kotlin
            CardDivider()
            LinkRow(stringResource(Res.string.about_report_problem), null, forward) {
                val info = deviceInfo()
                open(ReportMail.mailto(AboutLinks.SUPPORT_EMAIL, ReportMail.subject(info), ReportMail.body(info, crashLogStore.read(), noReportLine)))
            }
```

with `val noReportLine = stringResource(Res.string.crash_mail_no_report)` read in the composable.

- [ ] **Step 5: The policy sentence** (both files, under "What stays on your phone" after the list's closing paragraph):

EN: *If Taqwa crashes, it saves a technical report on your phone (the app version, your phone model and the error, nothing else). It never leaves the phone unless you choose to email it to us.*

AR: *إذا تعطّل تقوى، يحفظ تقريرًا تقنيًا على هاتفك (إصدار التطبيق وطراز هاتفك والخطأ، لا غير). ولا يغادر الهاتف إلا إذا اخترت إرساله إلينا بالبريد.*

- [ ] **Step 6: Build both targets, run `scripts/test.sh`, commit** — `git commit -m "crash: the sheet on the next launch, the About row and the policy sentence"`

---

### Task 6: Device proof on both platforms, then merge

- [ ] **Step 1:** A temporary commit adds a **Crash now** row to About that throws `IllegalStateException("Deliberate test crash")`.
- [ ] **Step 2 (Android, S23):** signed release build; open About › Crash now; the app dies; relaunch; the sheet appears; Send opens Gmail with To support@taqwa.world, the subject, and the body starting with the header; Back; relaunch: no sheet (offered). Kill and relaunch once more: still no sheet. About › Report a problem: body carries the report. Then Arabic: crash again, sheet in Arabic, Not now, no sheet after relaunch. Screenshots of the sheet in both languages.
- [ ] **Step 3 (iOS):** `scripts/ios-build.sh` for the simulator (and the iPhone 12 if attached); Crash now; relaunch; the sheet; Send (on a device with Mail the composer opens; on the simulator `openURL` is a no-op and that is noted); Not now. Confirm `crash-report.txt` exists in the app container.
- [ ] **Step 4:** Revert the temporary commit (`git revert`), `scripts/test.sh`, merge to main, push.
