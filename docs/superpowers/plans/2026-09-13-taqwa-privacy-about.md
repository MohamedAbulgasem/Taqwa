# Privacy policy, About screen and network honesty — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "the app makes no network request until you use recitation" true in code, then say it truthfully in a privacy policy, the store forms, an About screen and the README, and ship as 0.12.0 (14).

**Architecture:** A small `RecitationEngagement` (DataStore flag, with the download registry as the fallback for pre-flag installs) gates the daily `ManifestRefresher` fetch; the `RecitationController` marks engagement at its four entry points and asks for an opportunistic refresh when the picker or the Recitation settings open. The policy and store answers are Markdown at the repo root and under `docs/`. The About screen is one new settings-family screen reached from the ABOUT card, with three external links behind one `AboutLinks` object opened through Compose's own `LocalUriHandler`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, DataStore Preferences, kotlinx.coroutines test (`runTest`, `backgroundScope`), okio `FakeFileSystem`; Android `adb`, iOS `xcodebuild`.

**Spec:** `docs/superpowers/specs/2026-09-13-taqwa-privacy-about-design.md` — read §1 (the network inventory) before writing any user-facing sentence; the words may state only what §1 lists.

## Global Constraints

- Ships as **0.12.0 (14)**, on branch **`privacy-about`**; the version moves only in the release task (Task 11), via `scripts/bump-version.sh 0.12.0 14`.
- **Stop after Task 6** (the policy and store documents) and show Mohamed the policy text before anything else lands. He reviews words, not code.
- Network inventory is spec §1: two hosts (`raw.githubusercontent.com` for `manifest.json`; `github.com/MohamedAbulgasem/Taqwa-data/releases/download/…` with a redirect to GitHub's release-asset host for `.taqa` files). No cookies, no identifiers, no headers beyond `Range`. If a request not in §1 is found, the inventory is wrong and the policy changes, not the code.
- **Facts verified 13 Sep 2026 that correct the spec:** bookmarks (`SettingsKeys.QURAN_BOOKMARKS`) and Tasbeeh counts (`tasbeeh_state_<id>`) live in **DataStore**, not SQLDelight (SQLDelight holds only the bundled Quran). On iOS the DataStore file is in `Application Support/Taqwa` **excluded from iCloud backup** since 0.11.0's trust round (`DataStoreFactory.ios.kt:37`), and downloads are excluded (`RecitationPaths.ios.kt:36`). The widget App Group mirror holds prayer times, the ayah pool and the language tag, never coordinates or a city name. Word §4 "Backups" from these facts.
- Keep the §2 edits to the **first line** of each controller method and to the start effect: another branch may touch `RecitationController.kt` and `App.kt`, and the merge must stay additive.
- Tests: `./scripts/test.sh` runs shared and widgetcore on JVM and iOS simulator; trust its final `BUILD` line, not the XML counts. **No comma inside a backtick test name** (Kotlin/Native rejects it).
- Strings: every new key in both `shared/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml`. "Taqwa" stays in Latin script in both locales on the About screen (it is the product name, as on the icon).
- Header family for the About screen: the settings family (large `screenTitle` under the chevron via `SettingsScaffold`), not the inline reader style.
- Never `installDebug` (it installs on every attached device); `adb -s <serial> install -r`.
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

---

### Task 1: `RecitationEngagement` and `RecitationLibrary.hasAnyDownloads()`

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationEngagement.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt:98` (add the flag beside `RECITATION_MANIFEST_CHECKED`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationLibrary.kt` (add `hasAnyDownloads()` next to `downloaded()`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/recitation/RecitationEngagementTest.kt`

**Interfaces:**
- Consumes: `RecitationLibrary(paths: RecitationPaths, store: DataStore<Preferences>, fs: FileSystem)`; `SettingsKeys.RECITATION_DOWNLOADED_PREFIX = "recitation_downloaded_"`; `SettingsKeys.recitationDownloadedKey(reciterId)`.
- Produces: `SettingsKeys.RECITATION_ENGAGED: Preferences.Key<Boolean>` (`"recitation_engaged"`); `suspend fun RecitationLibrary.hasAnyDownloads(): Boolean`; `class RecitationEngagement(store, library) { suspend fun isEngaged(): Boolean; suspend fun mark() }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import world.taqwa.app.settings.SettingsKeys
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Counts writes, so "mark() twice writes once" is a number rather than a hope. */
private class CountingDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    var writes = 0
    override val data: Flow<Preferences> get() = delegate.data
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        writes++
        return delegate.updateData(transform)
    }
}

/**
 * "Engaged" is the one fact the catalogue refresh is gated on: a person who installed Taqwa for
 * prayer times and never opened recitation causes no network request, ever (spec §2.1).
 */
class RecitationEngagementTest {

    private val fs = FakeFileSystem()
    private val paths = RecitationPaths("/files".toPath())

    private fun store() = PreferenceDataStoreFactory.createWithPath {
        "/tmp/taqwa-engagement-${Random.nextULong()}.preferences_pb".toPath()
    }

    private fun engagement(store: DataStore<Preferences>) =
        RecitationEngagement(store, RecitationLibrary(paths, store, fs))

    @Test
    fun aFreshInstallIsNotEngaged() = runTest {
        assertFalse(engagement(store()).isEngaged())
    }

    @Test
    fun aDownloadMadeBeforeTheFlagExistedCountsAsEngaged() = runTest {
        val store = store()
        // 0.11.0 wrote the registry but never a flag; reconcile() rebuilds the registry from disk
        // on a reinstall, so the registry is the right thing to fall back on.
        store.edit { it[SettingsKeys.recitationDownloadedKey("ar.alafasy")] = setOf("1", "112") }
        assertTrue(engagement(store).isEngaged())
    }

    @Test
    fun anEmptyRegistryEntryIsNotADownload() = runTest {
        val store = store()
        store.edit { it[SettingsKeys.recitationDownloadedKey("ar.alafasy")] = emptySet() }
        assertFalse(engagement(store).isEngaged())
    }

    @Test
    fun markThenIsEngaged() = runTest {
        val store = store()
        val engagement = engagement(store)
        engagement.mark()
        assertTrue(engagement.isEngaged())
        assertEquals(true, store.data.first()[SettingsKeys.RECITATION_ENGAGED])
    }

    @Test
    fun markTwiceWritesOnce() = runTest {
        val counting = CountingDataStore(store())
        val engagement = RecitationEngagement(counting, RecitationLibrary(paths, counting, fs))
        engagement.mark()
        engagement.mark()
        assertEquals(1, counting.writes)
    }

    @Test
    fun hasAnyDownloadsReadsTheRegistryOnly() = runTest {
        val store = store()
        val library = RecitationLibrary(paths, store, fs)
        assertFalse(library.hasAnyDownloads())
        store.edit { it[SettingsKeys.recitationDownloadedKey("ar.husary")] = setOf("36") }
        assertTrue(library.hasAnyDownloads())
    }
}
```

Add `import kotlinx.coroutines.flow.first` to the imports (used in `markThenIsEngaged`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.recitation.RecitationEngagementTest -q 2>&1 | grep -E '^e: ' | head`
Expected: compile errors `Unresolved reference 'RecitationEngagement'`, `'RECITATION_ENGAGED'`, `'hasAnyDownloads'`.

- [ ] **Step 3: Write the minimal implementation**

`SettingsKeys.kt`, directly after `RECITATION_MANIFEST_CHECKED` (line 98):

```kotlin
    /**
     * True once the reader has touched recitation in any way — the speaker button, Play on an
     * ayah, the picker, the Recitation settings screen, "Download the whole Quran". The daily
     * catalogue fetch is gated on this (privacy spec §2), so an install that never opens
     * recitation never opens a socket.
     */
    val RECITATION_ENGAGED = booleanPreferencesKey("recitation_engaged")
```

`RecitationLibrary.kt`, after `isDownloaded(...)`:

```kotlin
    /**
     * Whether any reciter has any surah on the phone, from the registry alone — no disk I/O.
     * [RecitationEngagement] falls back on this for installs from before the engaged flag
     * existed: a download is engagement whoever made it, and [reconcile] rebuilds the registry
     * from disk at start, so a reinstall over left-behind files counts too.
     */
    suspend fun hasAnyDownloads(): Boolean = store.data.first().asMap().any { (key, value) ->
        key.name.startsWith(SettingsKeys.RECITATION_DOWNLOADED_PREFIX) && (value as? Set<*>)?.isNotEmpty() == true
    }
```

`RecitationEngagement.kt`:

```kotlin
package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import world.taqwa.app.settings.SettingsKeys

/**
 * The one fact the catalogue refresh is gated on (privacy spec §2.1): has this person ever used
 * recitation. Until they have, the app makes no network request at all, and the privacy policy
 * says so in those words.
 *
 * Engaged is the stored flag, or — for an install from 0.11.0, which wrote no flag, and for a
 * reinstall over files left behind — any surah in the download registry. The flag is read first
 * (one DataStore read) and the registry only when the flag is unset.
 */
class RecitationEngagement(
    private val store: DataStore<Preferences>,
    private val library: RecitationLibrary,
) {
    suspend fun isEngaged(): Boolean {
        if (store.data.first()[SettingsKeys.RECITATION_ENGAGED] == true) return true
        return library.hasAnyDownloads()
    }

    /** Idempotent: the flag is written once and never cleared. */
    suspend fun mark() {
        if (store.data.first()[SettingsKeys.RECITATION_ENGAGED] == true) return
        store.edit { it[SettingsKeys.RECITATION_ENGAGED] = true }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.recitation.RecitationEngagementTest --tests world.taqwa.app.recitation.RecitationLibraryTest -q 2>&1 | grep -E '^e: |FAILED|tests completed'`
Expected: no output (quiet success).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationEngagement.kt shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationLibrary.kt shared/src/commonTest/kotlin/world/taqwa/app/recitation/RecitationEngagementTest.kt
git commit -m "feat(recitation): RecitationEngagement, the one fact the catalogue refresh is gated on"
```

---

### Task 2: Gate `ManifestRefresher` on engagement

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/ManifestRefresher.kt` (constructor + first lines of `refreshIfStale()`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt:64-75` (construct `RecitationEngagement`, pass the lambda)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/recitation/ManifestRefresherTest.kt`

**Interfaces:**
- Consumes: `RecitationEngagement.isEngaged()` (Task 1).
- Produces: `ManifestRefresher(provider, store, fetch, now, engaged: suspend () -> Boolean = { true })`; `AppContainer.recitationEngagement: RecitationEngagement`.

- [ ] **Step 1: Write the failing tests** (append inside `ManifestRefresherTest`)

```kotlin
    @Test
    fun aReaderWhoNeverTouchedRecitationCausesNoFetchAndNoTimestamp() = runTest {
        var asked = 0
        val store = store()
        val refresher = ManifestRefresher(provider, store, { asked++; fresh.encodeToByteArray() }, { clock }, engaged = { false })

        refresher.refreshIfStale()

        assertEquals(0, asked)
        // Not even the attempt is recorded: nothing about this launch should say "checked".
        assertNull(store.data.first()[SettingsKeys.RECITATION_MANIFEST_CHECKED])
    }

    @Test
    fun aStaleTimestampStillDoesNotFetchWhileNotEngaged() = runTest {
        var asked = 0
        val store = store()
        store.edit { it[SettingsKeys.RECITATION_MANIFEST_CHECKED] = clock - 3L * ManifestRefresher.INTERVAL_MILLIS }
        val refresher = ManifestRefresher(provider, store, { asked++; fresh.encodeToByteArray() }, { clock }, engaged = { false })

        refresher.refreshIfStale()
        assertEquals(0, asked)
    }

    @Test
    fun theFirstEngagedLaunchFetchesAtOnce() = runTest {
        var asked = 0
        var engaged = false
        val refresher = ManifestRefresher(provider, store(), { asked++; fresh.encodeToByteArray() }, { clock }, engaged = { engaged })

        refresher.refreshIfStale()
        assertEquals(0, asked)
        engaged = true
        refresher.refreshIfStale()
        assertEquals(1, asked, "no stale window to wait out: the gate never wrote a timestamp")
    }
```

Add `import androidx.datastore.preferences.core.edit` to the test's imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.recitation.ManifestRefresherTest -q 2>&1 | grep -E '^e: ' | head -3`
Expected: `Cannot find a parameter with this name: engaged`.

- [ ] **Step 3: Write the minimal implementation**

`ManifestRefresher.kt` constructor gains, after `now`:

```kotlin
    /**
     * Whether the reader has ever used recitation (privacy spec §2.3). False means return before
     * reading or writing anything: an install that never opens recitation never opens a socket,
     * and never records an "attempt" either, so the first engaged launch fetches at once rather
     * than a day later.
     */
    private val engaged: suspend () -> Boolean = { true },
```

and `refreshIfStale()` opens with:

```kotlin
        if (!engaged()) return
```

`AppContainer.kt`, in the recitation block:

```kotlin
    val recitationEngagement by lazy { RecitationEngagement(dataStore, recitationLibrary) }
    val manifestRefresher by lazy {
        ManifestRefresher(manifestProvider, dataStore, engaged = { recitationEngagement.isEngaged() })
    }
```

with `import world.taqwa.app.recitation.RecitationEngagement`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.recitation.ManifestRefresherTest -q 2>&1 | grep -E '^e: |FAILED|tests completed'`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/recitation/ManifestRefresher.kt shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt shared/src/commonTest/kotlin/world/taqwa/app/recitation/ManifestRefresherTest.kt
git commit -m "feat(recitation): the catalogue refresh waits until the reader has used recitation"
```

---

### Task 3: The controller marks engagement and asks for an opportunistic refresh

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/RecitationController.kt` (constructor; first line of `requestPlay` :299, `onHeaderTap` :318, `downloadWholeQuran` :359, `openPicker` :418; one new method `onSettingsOpened()`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/RecitationSettingsScreen.kt:68` (new `onOpened: () -> Unit` parameter, `LaunchedEffect(Unit) { onOpened() }`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt:657` (`onOpened = recitation::onSettingsOpened`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt:95` (two new lambdas)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/recitation/RecitationControllerTest.kt`

**Interfaces:**
- Consumes: `RecitationEngagement.mark()` (Task 1), `ManifestRefresher.refreshIfStale()` (Task 2).
- Produces: `RecitationController(..., markEngaged: suspend () -> Unit = {}, refreshCatalogue: suspend () -> Unit = {})`; `fun RecitationController.onSettingsOpened()`; `RecitationSettingsScreen(..., onOpened: () -> Unit)`.

- [ ] **Step 1: Write the failing tests** (append inside `RecitationControllerTest`; the `controller(...)` helper gains two counters)

Change the helper to:

```kotlin
    private class Engagement {
        var marks = 0
        var refreshes = 0
    }

    private fun controller(
        harness: Harness,
        scope: kotlinx.coroutines.CoroutineScope,
        previews: Set<String> = setOf("ar.alafasy"),
        engagement: Engagement = Engagement(),
    ) = RecitationController(
        manifests = { catalogue },
        library = harness.library,
        downloader = harness.downloader,
        player = harness.player,
        settings = harness.settings,
        quran = FakeQuranSource(),
        clips = harness.clips,
        previewBytes = { id -> if (id in previews) ByteArray(8) else null },
        scope = scope,
        markEngaged = { engagement.marks++ },
        refreshCatalogue = { engagement.refreshes++ },
    )
```

and add:

```kotlin
    @Test
    fun `opening the picker marks engagement and asks for a catalogue refresh`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val controller = controller(Harness(), backgroundScope, engagement = engagement)

        controller.openPicker()

        assertEquals(1, engagement.marks)
        assertEquals(1, engagement.refreshes)
    }

    @Test
    fun `the header button marks engagement`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        val controller = controller(harness, backgroundScope, engagement = engagement)

        controller.onHeaderTap(1, 1)

        assertEquals(1, engagement.marks)
        assertEquals(0, engagement.refreshes, "a tap to play is not a reason to talk to GitHub")
    }

    @Test
    fun `the recitation settings screen marks engagement and refreshes`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val controller = controller(Harness(), backgroundScope, engagement = engagement)

        controller.onSettingsOpened()

        assertEquals(1, engagement.marks)
        assertEquals(1, engagement.refreshes)
    }

    @Test
    fun `download the whole Quran marks engagement`() = runTest(UnconfinedTestDispatcher()) {
        val engagement = Engagement()
        val harness = Harness()
        val controller = controller(harness, backgroundScope, engagement = engagement)
        controller.selectReciter("ar.alafasy")

        controller.downloadWholeQuran()

        assertEquals(1, engagement.marks)
    }
```

(`selectReciter` is whatever the existing tests call to choose a voice; if the existing file uses a different name, use that one — the assertion is on `marks`, not on the selection.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.feature.recitation.RecitationControllerTest -q 2>&1 | grep -E '^e: ' | head -3`
Expected: `Cannot find a parameter with this name: markEngaged`, `Unresolved reference 'onSettingsOpened'`.

- [ ] **Step 3: Write the minimal implementation**

Constructor, after `scope`:

```kotlin
    /**
     * Privacy spec §2.2: recorded, fire-and-forget, at the first line of every entry point.
     * Until it has been called once, the app makes no network request at all.
     */
    private val markEngaged: suspend () -> Unit = {},
    /** Spec §2.4: the daily catalogue check, run opportunistically when the picker or the
     * Recitation settings open. The refresher's own 24-hour window keeps this to one a day. */
    private val refreshCatalogue: suspend () -> Unit = {},
```

One private helper:

```kotlin
    /** The first line of every entry point. [refresh] only where a fresh catalogue is what the
     * reader is about to look at. */
    private fun engage(refresh: Boolean = false) {
        scope.launch {
            markEngaged()
            if (refresh) refreshCatalogue()
        }
    }
```

First lines: `requestPlay` → `engage()`; `onHeaderTap` → `engage()`; `downloadWholeQuran` → `engage()`; `openPicker` → `engage(refresh = true)`. New method beside `openPicker`:

```kotlin
    /** Settings › Quran › Recitation opened (spec §2.2): engagement on purpose — the screen shows
     * the reciter list and "Download the whole Quran", and whoever went there wants the current
     * catalogue. */
    fun onSettingsOpened() = engage(refresh = true)
```

`RecitationSettingsScreen.kt`: add `onOpened: () -> Unit,` after `onBack`, and as the first statement of the body `LaunchedEffect(Unit) { onOpened() }` (import `androidx.compose.runtime.LaunchedEffect`).

`App.kt:657`: add `onOpened = recitation::onSettingsOpened,` after `onBack`.

`AppContainer.kt:95`: add to the constructor call

```kotlin
            markEngaged = { recitationEngagement.mark() },
            refreshCatalogue = {
                withContext(Dispatchers.Default) { runCatching { manifestRefresher.refreshIfStale() } }
            },
```

(imports `kotlinx.coroutines.withContext`; `Dispatchers` is already imported there).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.feature.recitation.RecitationControllerTest -q 2>&1 | grep -E '^e: |FAILED|tests completed'`
Expected: no output.

- [ ] **Step 5: Run the whole suite**

Run: `./scripts/test.sh > /tmp/privacy-tests.log 2>&1; tail -3 /tmp/privacy-tests.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/RecitationController.kt shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/RecitationSettingsScreen.kt shared/src/commonMain/kotlin/world/taqwa/app/App.kt shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt shared/src/commonTest/kotlin/world/taqwa/app/feature/recitation/RecitationControllerTest.kt
git commit -m "feat(recitation): every entry point marks engagement; the picker and settings refresh the catalogue"
```

---

### Task 4: Device proof that a fresh install makes no request

**Files:**
- Create (scratchpad, not committed): `connect-log.py` — a 40-line proxy that logs every `CONNECT host:port` it is asked for and tunnels it.
- Evidence: log lines recorded in the final report and in `docs/BUILD-LOG.md` (Task 10).

**Interfaces:** none.

- [ ] **Step 1: Write the logging proxy** (scratchpad `connect-log.py`)

```python
import socket, threading, sys, datetime
LISTEN = ("0.0.0.0", 8888)
def log(msg): print(datetime.datetime.now().strftime("%H:%M:%S"), msg, flush=True)
def pipe(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d: break
            b.sendall(d)
    except OSError: pass
    finally:
        for s in (a, b):
            try: s.close()
            except OSError: pass
def handle(c):
    head = b""
    while b"\r\n\r\n" not in head:
        chunk = c.recv(4096)
        if not chunk: return
        head += chunk
    line = head.split(b"\r\n", 1)[0].decode(errors="replace")
    log(line)
    if line.startswith("CONNECT "):
        host, port = line.split()[1].rsplit(":", 1)
        up = socket.create_connection((host, int(port)))
        c.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        threading.Thread(target=pipe, args=(c, up), daemon=True).start()
        pipe(up, c)
    else:
        c.close()
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind(LISTEN); s.listen(16)
log("listening on %s:%d" % LISTEN)
while True:
    conn, _ = s.accept()
    threading.Thread(target=handle, args=(conn,), daemon=True).start()
```

- [ ] **Step 2: Point the emulator at it, fresh-install, launch, wait**

```bash
python3 connect-log.py > connect.log 2>&1 &
adb -s emulator-5556 shell settings put global http_proxy 10.0.2.2:8888
adb -s emulator-5556 uninstall world.taqwa.app
./gradlew :androidApp:assembleDebug -q
adb -s emulator-5556 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5556 shell am start -n world.taqwa.app/.MainActivity
sleep 60
```

Expected in `connect.log`: only the "listening" line and whatever Google/system processes do (`play.googleapis.com` etc.); **no** `raw.githubusercontent.com`. Use a fresh emulator user data if the system's own traffic makes the log noisy; what matters is the absence of GitHub hosts.

- [ ] **Step 3: Engage and watch the fetch appear**

Open the Quran tab, open a surah, tap the speaker. Expected within seconds: `CONNECT raw.githubusercontent.com:443` in `connect.log`. Then `adb shell settings delete global http_proxy` on the emulator.

- [ ] **Step 4: Record the evidence**

Copy the relevant lines (timestamps, the launch with no GitHub host, the fetch after the tap) into the final report; Task 10 puts one sentence in the build log.

---

### Task 5: iOS export compliance

**Files:**
- Modify: `iosApp/iosApp/Info.plist` (add the key beside `NSLocationDefaultAccuracyReduced`)

- [ ] **Step 1: Add the key**

```xml
	<key>ITSAppUsesNonExemptEncryption</key>
	<false/>
```

- [ ] **Step 2: Verify the plist is still valid and there is no ATS exception**

Run: `plutil -lint iosApp/iosApp/Info.plist && grep -c NSAppTransportSecurity iosApp/iosApp/Info.plist`
Expected: `OK` and `0`.

- [ ] **Step 3: Commit**

```bash
git add iosApp/iosApp/Info.plist
git commit -m "ios: declare exempt encryption so every upload skips the export-compliance question"
```

---

### Task 6: `PRIVACY.md` and `docs/STORE-PRIVACY.md` — then STOP for Mohamed's review

**Files:**
- Create: `PRIVACY.md` (repo root)
- Create: `docs/STORE-PRIVACY.md`

**Interfaces:** the policy URL is `https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md` (Task 7's `AboutLinks.PRIVACY_POLICY`).

- [ ] **Step 1: Write `PRIVACY.md`** from spec §4 verbatim, with the Backups section resolved to what the code does:

> Taqwa opts out of Android's app backup, so your location and settings are not copied to Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud backup; the only thing of Taqwa's that is backed up is the widget's small cache of upcoming prayer times, and that backup is encrypted by Apple and never visible to us.

Keep the Contact section with both GitHub issues and the email (the spec's recommendation), and flag it as the one open question in the review message.

- [ ] **Step 2: Write `docs/STORE-PRIVACY.md`** from spec §5, checked against the manifest: permissions are `INTERNET`, `ACCESS_COARSE_LOCATION` (coarse only — the spec's "COARSE/FINE" is wrong, fine is not declared), `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

- [ ] **Step 3: Commit and stop**

```bash
git add PRIVACY.md docs/STORE-PRIVACY.md
git commit -m "docs: the privacy policy and the store privacy answers"
```

Then show Mohamed the policy text in full and wait. **Do not start Task 7 until he has answered.**

---

### Task 7: `AboutLinks`, the About strings, and the string-parity test

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/about/AboutLinks.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml` and `values-ar/strings.xml`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/about/AboutLinksTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/i18n/StringParityTest.kt` (new; there is no existing parity test)

**Interfaces:**
- Produces: `object AboutLinks { PRIVACY_POLICY; SOURCE; LICENCE }`; string keys `settings_about` (changed), `about_tagline`, `about_privacy_label`, `about_stays_title`, `about_stays_body`, `about_online_title`, `about_online_body`, `about_nothing_title`, `about_nothing_body`, `about_links_label`, `about_privacy_policy`, `about_source`, `about_source_value`, `about_licence`, `about_licence_value`.

- [ ] **Step 1: Write the failing tests**

`AboutLinksTest.kt`:

```kotlin
package world.taqwa.app.about

import kotlin.test.Test
import kotlin.test.assertTrue

/** Three links, one place; a typo here is a dead row on the About screen on both platforms. */
class AboutLinksTest {
    private val all = listOf(AboutLinks.PRIVACY_POLICY, AboutLinks.SOURCE, AboutLinks.LICENCE)

    @Test fun everyLinkIsHttpsToTheTaqwaOwner() {
        for (link in all) assertTrue(link.startsWith("https://github.com/MohamedAbulgasem/"), link)
    }

    @Test fun thePolicyLinkPointsAtThePolicyFile() {
        assertTrue(AboutLinks.PRIVACY_POLICY.endsWith("/PRIVACY.md"))
    }
}
```

`StringParityTest.kt` — the Compose resource accessors are generated per key, so parity is checked at the source level: the test reads nothing at runtime and instead the check is a script. Put it in `scripts/check-strings.sh`:

```bash
#!/bin/bash
# Every <string name="…"> in values/strings.xml must exist in values-ar/strings.xml and vice versa.
set -e
cd "$(dirname "$0")/.."
en=$(grep -o 'name="[^"]*"' shared/src/commonMain/composeResources/values/strings.xml | sort)
ar=$(grep -o 'name="[^"]*"' shared/src/commonMain/composeResources/values-ar/strings.xml | sort)
diff <(echo "$en") <(echo "$ar") && echo "strings: en and ar carry the same keys"
```

Run it from `scripts/test.sh`'s callers by hand (Task 9 and the release task); no Kotlin test for it.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.about.AboutLinksTest -q 2>&1 | grep -E '^e: ' | head -2`
Expected: `Unresolved reference 'AboutLinks'`.

- [ ] **Step 3: Write the implementation**

`AboutLinks.kt`:

```kotlin
package world.taqwa.app.about

/**
 * Where the About screen's three links go (privacy spec §6.3). One place to change when the
 * repository goes public under another name or taqwa.world exists. They resolve only once the
 * Taqwa repository is public — a release-day step, not a code one.
 */
object AboutLinks {
    const val PRIVACY_POLICY = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/PRIVACY.md"
    const val SOURCE = "https://github.com/MohamedAbulgasem/Taqwa"
    const val LICENCE = "https://github.com/MohamedAbulgasem/Taqwa/blob/main/LICENSE"
}
```

`values/strings.xml`: change `settings_about` to `About Taqwa` and add, after `settings_version_value`:

```xml
    <!-- About screen (privacy spec §6). "Taqwa" is the product name and stays in Latin script. -->
    <string name="about_tagline">Free, for everyone, for good.</string>
    <string name="about_privacy_label">PRIVACY</string>
    <string name="about_stays_title">Stays on your phone</string>
    <string name="about_stays_body">Location, settings, bookmarks and counts never leave it.</string>
    <string name="about_online_title">Online only when you ask</string>
    <string name="about_online_body">The internet is used for one thing: recitations you choose to download, from Taqwa\'s public repository on GitHub.</string>
    <string name="about_nothing_title">No account, no ads, no analytics</string>
    <string name="about_nothing_body">Nothing to sign into, nothing to sell.</string>
    <string name="about_links_label">MORE</string>
    <string name="about_privacy_policy">Privacy policy</string>
    <string name="about_source">Source code</string>
    <string name="about_source_value">GitHub</string>
    <string name="about_licence">Licence</string>
    <string name="about_licence_value">GPL-3.0</string>
```

`values-ar/strings.xml`: `settings_about` → `عن تقوى`, and:

```xml
    <string name="about_tagline">مجاني، للجميع، دائمًا.</string>
    <string name="about_privacy_label">الخصوصية</string>
    <string name="about_stays_title">يبقى على هاتفك</string>
    <string name="about_stays_body">الموقع والإعدادات والعلامات والعدّاد لا تغادره.</string>
    <string name="about_online_title">متصل فقط عندما تطلب</string>
    <string name="about_online_body">يُستخدم الإنترنت لشيء واحد: التلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub.</string>
    <string name="about_nothing_title">بلا حساب، بلا إعلانات، بلا تحليلات</string>
    <string name="about_nothing_body">لا شيء لتسجيل الدخول إليه، ولا شيء للبيع.</string>
    <string name="about_links_label">المزيد</string>
    <string name="about_privacy_policy">سياسة الخصوصية</string>
    <string name="about_source">الشفرة المصدرية</string>
    <string name="about_source_value">GitHub</string>
    <string name="about_licence">الرخصة</string>
    <string name="about_licence_value">GPL-3.0</string>
```

- [ ] **Step 4: Run the tests and the parity script**

Run: `./gradlew :shared:testDebugUnitTest --tests world.taqwa.app.about.AboutLinksTest -q 2>&1 | grep -E '^e: |FAILED'; bash scripts/check-strings.sh`
Expected: no gradle output; `strings: en and ar carry the same keys`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/about/AboutLinks.kt shared/src/commonTest/kotlin/world/taqwa/app/about/AboutLinksTest.kt shared/src/commonMain/composeResources/values/strings.xml shared/src/commonMain/composeResources/values-ar/strings.xml scripts/check-strings.sh
git commit -m "feat(about): the three links and the About strings in both languages"
```

---

### Task 8: The About screen

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AboutScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/nav/Screen.kt` (add `data object About : Screen` beside `Attribution`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt:236,306-313` (`onOpenAbout` parameter; the Version row becomes the About row)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt:643,866` (`onOpenAbout`, `Screen.About` branch)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt` (add `drawExternalLink`)

**Interfaces:**
- Consumes: `AboutLinks`, the strings from Task 7, `SettingsScaffold`, `SettingsCard`, `SectionLabel`, `TaqwaRow(label, value, subtitle, onClick, trailing)`, `CardDivider`, `TaqwaText.screenTitle/rowLabel/caption`, `LocalTaqwaColors`.
- Produces: `@Composable fun AboutScreen(onBack: () -> Unit)`; `Screen.About`.

- [ ] **Step 1: The glyph** (in `Glyphs.kt`, after `drawChevron`)

```kotlin
/**
 * An external link: a box with its top-trailing corner open and an arrow leaving through it.
 * [pointsForward] is the caller's reading of `LocalLayoutDirection`, as for [drawChevron]: the
 * arrow leaves toward the trailing edge in both directions.
 */
internal fun DrawScope.drawExternalLink(tint: Color, pointsForward: Boolean) {
    val u = size.width / 16f
    fun x(value: Float) = (if (pointsForward) value else 16f - value) * u
    val box = Path().apply {
        moveTo(x(8.5f), 3.2f * u); lineTo(x(3.2f), 3.2f * u); lineTo(x(3.2f), 12.8f * u)
        lineTo(x(12.8f), 12.8f * u); lineTo(x(12.8f), 7.5f * u)
    }
    drawPath(box, tint, style = glyphStroke())
    drawLine(tint, Offset(x(7.2f), 8.8f * u), Offset(x(13.2f), 2.8f * u), strokeWidth = size.width * 0.0875f, cap = StrokeCap.Round)
    val head = Path().apply { moveTo(x(9.6f), 2.8f * u); lineTo(x(13.2f), 2.8f * u); lineTo(x(13.2f), 6.4f * u) }
    drawPath(head, tint, style = glyphStroke())
}
```

- [ ] **Step 2: The screen**

```kotlin
package world.taqwa.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.about.AboutLinks
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.design.components.CardDivider
import world.taqwa.app.design.components.TaqwaRow
import world.taqwa.app.design.components.drawExternalLink
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.about_licence
import world.taqwa.app.resources.about_licence_value
import world.taqwa.app.resources.about_links_label
import world.taqwa.app.resources.about_nothing_body
import world.taqwa.app.resources.about_nothing_title
import world.taqwa.app.resources.about_online_body
import world.taqwa.app.resources.about_online_title
import world.taqwa.app.resources.about_privacy_label
import world.taqwa.app.resources.about_privacy_policy
import world.taqwa.app.resources.about_source
import world.taqwa.app.resources.about_source_value
import world.taqwa.app.resources.about_stays_body
import world.taqwa.app.resources.about_stays_title
import world.taqwa.app.resources.about_tagline
import world.taqwa.app.resources.settings_about
import world.taqwa.app.resources.settings_version_value
import androidx.compose.material3.Text

/**
 * Settings › About Taqwa (privacy spec §6): who made this, what it promises, and where the
 * promise can be checked. Three cards in the settings idiom; the credits stay on their own
 * screen and are not repeated here.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val uriHandler = LocalUriHandler.current
    val forward = LocalLayoutDirection.current == LayoutDirection.Ltr
    // A device with no browser fails silently rather than crashing (spec §6.3).
    fun open(url: String) { runCatching { uriHandler.openUri(url) } }

    SettingsScaffold(stringResource(Res.string.settings_about), onBack) {
        SettingsCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                // The product name, in Latin script in both locales, as on the icon.
                Text("Taqwa", style = TaqwaText.screenTitle, color = colors.textPrimary)
                Text(stringResource(Res.string.settings_version_value), style = TaqwaText.caption, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.about_tagline), style = TaqwaText.rowLabel, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.about_privacy_label))
        SettingsCard {
            TaqwaRow(stringResource(Res.string.about_stays_title), subtitle = stringResource(Res.string.about_stays_body))
            CardDivider()
            TaqwaRow(stringResource(Res.string.about_online_title), subtitle = stringResource(Res.string.about_online_body))
            CardDivider()
            TaqwaRow(stringResource(Res.string.about_nothing_title), subtitle = stringResource(Res.string.about_nothing_body))
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel(stringResource(Res.string.about_links_label))
        SettingsCard {
            LinkRow(stringResource(Res.string.about_privacy_policy), null, forward) { open(AboutLinks.PRIVACY_POLICY) }
            CardDivider()
            LinkRow(stringResource(Res.string.about_source), stringResource(Res.string.about_source_value), forward) { open(AboutLinks.SOURCE) }
            CardDivider()
            LinkRow(stringResource(Res.string.about_licence), stringResource(Res.string.about_licence_value), forward) { open(AboutLinks.LICENCE) }
        }
    }
}

/** A row that leaves the app: the external-link glyph in the trailing slot says so before the tap. */
@Composable
private fun LinkRow(label: String, value: String?, forward: Boolean, onClick: () -> Unit) {
    val tint = LocalTaqwaColors.current.textTertiary
    TaqwaRow(
        label,
        value = value,
        onClick = onClick,
        trailing = { Canvas(Modifier.size(16.dp)) { drawExternalLink(tint, forward) } },
    )
}
```

If `TaqwaRow`'s `subtitle` rows look cramped with two-line captions, the row's own padding is the fix, not a new component.

- [ ] **Step 3: Navigation and the root row**

`Screen.kt`: `data object About : Screen` directly above `Attribution`.
`SettingsRootScreen.kt`: parameter `onOpenAbout: () -> Unit,` above `onOpenAttribution`; the ABOUT card's first row becomes
`TaqwaRow(stringResource(Res.string.settings_about), value = stringResource(Res.string.settings_version_value), onClick = onOpenAbout)`.
`App.kt:643`: `onOpenAbout = { navigator.push(Screen.About) },`; and beside the `Screen.Attribution` branch:

```kotlin
                        Screen.About -> AboutScreen(onBack = { navigator.pop() })
```

- [ ] **Step 4: Compile and run the suite**

Run: `./scripts/test.sh > /tmp/privacy-tests.log 2>&1; tail -3 /tmp/privacy-tests.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AboutScreen.kt shared/src/commonMain/kotlin/world/taqwa/app/nav/Screen.kt shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt shared/src/commonMain/kotlin/world/taqwa/app/App.kt shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt
git commit -m "feat(about): the About screen, reached from the ABOUT card"
```

---

### Task 9: Screenshots and link taps

**Files:** none committed; PNGs in the scratchpad, sent to Mohamed.

- [ ] **Step 1: Emulator, English light**: `./gradlew :androidApp:assembleDebug -q`, `adb -s emulator-5556 install -r …`, open Settings › About Taqwa, screenshot. Tap each of the three rows once and screenshot Chrome opening (a GitHub 404 is fine while the repo is private; what is being proved is that the row opens the browser at the right URL).
- [ ] **Step 2: Emulator, Arabic dark**: `adb -s emulator-5556 shell cmd locale set-app-locales world.taqwa.app --locales ar-EG`, `adb -s emulator-5556 shell cmd uimode night yes`, reopen About, screenshot; then reset both.
- [ ] **Step 3: Simulator**: `./scripts/ios-build.sh`, `xcrun simctl install booted build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app`, `xcrun simctl launch booted world.taqwa.app`, navigate, `xcrun simctl io booted screenshot about-ios.png`.
- [ ] **Step 4: Send the PNGs** with `SendUserFile`.

---

### Task 10: Copy fixes and the build log

**Files:**
- Modify: `README.md:9` (tagline), `README.md:146` (Privacy section), features list (new "Privacy" line)
- Modify: `docs/BUILD-LOG.md` (one entry: "Saying what the app does with the network")

- [ ] **Step 1: README**
  - Line 9: `Free, offline by design, no ads, no accounts, no tracking.`
  - Features list, after the Tasbeeh bullet: `- **Privacy you can check.** Settings › About Taqwa says what stays on the phone and what the internet is used for, and links the policy, the source and the licence.`
  - Privacy section: keep the location sentences; replace "Location never leaves the phone. There are no analytics, no crash reporters and no network access of any kind." with: `Location never leaves the phone. The app makes no network request until you use Quran recitation. Recitations are downloaded one surah at a time from Taqwa's public data repository on GitHub, only when you ask; that request shows GitHub your IP address and the file you asked for, and nothing else. There are no analytics, no crash reporters and no third-party SDKs that talk to the internet. Full policy in [PRIVACY.md](PRIVACY.md).`
  - Line 53 "Offline first." stays. The Principles bullet "Nothing in the app contacts a server." (line 50) becomes `Nothing in the app contacts a server of ours; there is none. The only network use is recitation downloads from a public GitHub repository, on request.`

- [ ] **Step 2: Grep both locales and the README** for `internet|offline|network|never` and check every hit against spec §1. Expected hits that stay: `onboarding_welcome_body` ("works offline" — true), `city_search_hint`, `recitation_fail_no_network`, `onboarding_location_body`.

- [ ] **Step 3: BUILD-LOG entry** "Saying what the app does with the network (13 September, 0.12.0)": the gate and why it came first, the proxy evidence from Task 4, the policy, the About screen.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/BUILD-LOG.md
git commit -m "docs: the README and build log say what the app does with the network"
```

---

### Task 11: Release 0.12.0 (14)

- [ ] **Step 1:** `scripts/bump-version.sh 0.12.0 14` (a feature since 0.11.0 → minor; code +1).
- [ ] **Step 2:** `./scripts/test.sh` and `bash scripts/check-strings.sh` — both green.
- [ ] **Step 3:** Commit `chore: version 0.12.0 (14)`.
- [ ] **Step 4:** Release APK: `./gradlew :androidApp:assembleRelease -q`; copy to the scratchpad; `adb -s <serial> install -r` on whichever phones are attached (never `installDebug`). iOS device build per `scripts/ios-build.sh`'s destination notes and `xcrun devicectl device install app --device <uuid>` if the iPhone is attached.
- [ ] **Step 5:** Memory: the gate, the policy URL, the About screen, the release.

---

## Self-review

- **Spec coverage.** §2.1 → Task 1; §2.2 and §2.4 → Task 3; §2.3 → Task 2; §2.5 → Tasks 1–3 tests; §2.6 → Task 4; §3 → Task 5; §4–§5 → Task 6 with the stop; §6.1–6.3 → Tasks 7–8; §6.4 → Tasks 7 (links, parity) and 9 (screenshots); §7 → Task 10; §8 order → task order; §9 out of scope respected.
- **Corrections to the spec, applied here:** bookmarks and counts are DataStore, not SQLDelight; iOS settings are already excluded from backup; `ACCESS_FINE_LOCATION` is not declared; there is no existing string-parity test, so Task 7 adds a script.
- **Types.** `RecitationEngagement(store, library)`, `ManifestRefresher(..., engaged)`, `RecitationController(..., markEngaged, refreshCatalogue)`, `onSettingsOpened()`, `RecitationSettingsScreen(..., onOpened)`, `AboutScreen(onBack)`, `Screen.About`, `AboutLinks.{PRIVACY_POLICY, SOURCE, LICENCE}`, `drawExternalLink(tint, pointsForward)` — the same names throughout.
