# Read the Translation Aloud — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An option, off by default, that reads the translation shown under each ayah aloud with the phone's own offline voice after the reciter recites it, on Android and iOS.

**Architecture:** The recitation queue gains a `Speech(n)` item, preceded by a 400 ms breath, after every ayah that has a translation to read. Both platform players play it as part of the same queue:
- **Android** synthesizes it to a WAV inside the Media3 service, on ExoPlayer's loading thread, and plays it like any other item.
- **iOS** speaks it with `AVSpeechSynthesizer` as a timed phase, like the existing gap.

A pure `SpeechText` prepares what is read. The controller decides when there is something to read, and the reading sheet and Settings › Recitation carry the switch.

**Tech Stack:** Kotlin Multiplatform 2.4.10, Compose Multiplatform 1.12, Media3 1.11.1 (ExoPlayer + MediaSessionService), Android `TextToSpeech`, iOS `AVSpeechSynthesizer`, DataStore, SQLDelight, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-27-translation-read-aloud-design.md`. Read it first: this plan argues from it.

## Global Constraints

- Off by default. DataStore key `recitation_read_aloud`, boolean, default `false`.
- The text read is the reading sheet's translation (`ReadingSettings.translationId`), in every mode. Nothing is read when it is `ReadingSettings.NO_TRANSLATION`. `TextKind.TRANSLITERATION` and the Quran text are never read.
- Tafsir al-Muyassar is read with its quotations dropped: braces, fully voweled parentheses (marks ≥ half the letters), and the opening letters of the 29 surahs, as spec §3.2 lists them.
- Consecutive ayahs with the identical source text are read once, after the last ayah of the run.
- Queue order per ayah: `Ayah(n)`, then `Gap(400)` + `Speech(n)` when n has text, then `Gap(reciter gap)` when there is a gap and n is not the last ayah.
- The surah clock includes the breath and each translation's estimated slot: characters × 1000 / {en 16, fr 15, tr 14, id 14, ar 13, ur 13, bn 12, other 14}, at least 1,000 ms. With read-aloud off, the clock is unchanged.
- Only offline, installed voices. Android status is Ready, Missing(engine) or Unsupported. iOS status is Ready or Unsupported. Unsupported means the switch is hidden everywhere.
- A failed or missing voice never stops the recitation. Android serves a 100 ms silent WAV; iOS skips to the next item.
- No new Android permission. The only manifest change is `<queries>` for `android.intent.action.TTS_SERVICE`.
- Strings go in all 8 `shared/src/commonMain/composeResources/values*` directories, with `values-in` byte-identical to `values-id`. Apostrophes in Compose strings are the typographic ’.
- **Taqwa commits never carry a `Co-Authored-By` trailer.**
- Tests run with `./gradlew :shared:testAndroidHostTest` (JVM, common and androidHostTest) and `./scripts/test.sh` (everything, including iOS). Test names must not contain commas, because the iOS target breaks on them.
- Work in the worktree `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/read-aloud`, branch `translation-read-aloud`. Start every shell command with `cd` into it.

---

## File map

| File | Responsibility |
|---|---|
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationQueue.kt` | queue items incl. `Speech`; list-based navigation |
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/SurahTimeline.kt` | speech slots; snap past non-ayah items |
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/SpeechText.kt` (new) | text preparation, grouping, estimates |
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/SpokenTranslation.kt` (new) | `SpeechVoice`, `VoiceStatus`, `SpeechVoices`, `SpokenTranslation`, `buildSpokenTranslation` |
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/VoicePick.kt` (new) | pure voice choice shared by both platforms |
| `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.kt` | `speaking`, `load(…, speech)`, `setSpeech` |
| `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/*` | ports, controller, state |
| `shared/src/commonMain/kotlin/world/taqwa/app/settings/*` | `readAloud` setting |
| `shared/src/androidMain/kotlin/world/taqwa/app/recitation/SpeechVoices.android.kt` (new) | engine/voice status, installer |
| `shared/src/androidMain/kotlin/world/taqwa/app/recitation/SpeechDataSource.kt` (new) | `Speaker` + `SpeechDataSource` |
| `shared/src/androidMain/kotlin/world/taqwa/app/recitation/{RecitationService,AyahPlayer,RecitationPlayer.android}.kt` | playback integration |
| `shared/src/iosMain/kotlin/world/taqwa/app/recitation/SpeechVoices.ios.kt` (new) | voice status |
| `shared/src/iosMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.ios.kt` | speech phase |
| UI: `ReadingSheet.kt`, `RecitationSettingsScreen.kt`, `PlayerBar.kt`, `AyahCard.kt`, `Glyphs.kt`, `QuranRecitation.kt`, `QuranRoutes.kt`, `RecitationRoutes.kt`, `App.kt`, `ReaderScreen.kt`, `MushafScreen.kt` | switch, caption, mark |
| harnesses: `androidApp/src/debug/.../RecitationHarnessReceiver.kt`, `iosApp/iosApp/iOSApp.swift` | device test hooks |

---

### Task 1: The queue and the clock learn about speech

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationQueue.kt` (whole file)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/SurahTimeline.kt` (`snapToAyah`, `of`)
- Modify: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/AyahPlayer.kt:193` (snap call)
- Modify: `shared/src/iosMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.ios.kt` (lines using `built.gapMs`, the `snapToAyah` call)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/recitation/RecitationQueueTest.kt`, `SurahTimelineTest.kt` (append)

**Interfaces:**
- Produces:
  - `QueueItem.Speech(n: Int)`
  - `const val SPEECH_BREATH_MS = 400L` (package `world.taqwa.app.recitation`)
  - `RecitationQueue(surah: Int, items: List<QueueItem>)` primary constructor
  - `RecitationQueue(surah: Int, ayahs: List<Int>, gapMs: Long, spoken: Set<Int> = emptySet())`
  - members: `isAyah(i)`, `isSpeech(i)`, `isGap(i)` (any silence), `silenceMs(i)`, `ayahIndexAt(i)`, and `RecitationQueue.of(index, gapMs, spoken = emptySet())`
  - `SurahTimeline.of(queue, ayahMs, speechMs)` 3-arg overload
  - `snapToAyah(ms, isGap)`, where `isGap` means "not an ayah" and the function walks forward past such items
  - `RecitationQueue.gapMs` is **removed**; use `silenceMs(index)`.

- [ ] **Step 1: Write the failing queue tests.** Append to `RecitationQueueTest.kt` inside the class:

```kotlin
    private fun spokenFatiha() = RecitationQueue(1, (1..3).toList(), gapMs = 300L, spoken = setOf(1, 3))

    @Test
    fun `a spoken ayah is followed by a breath and its translation before the gap`() {
        assertEquals(
            listOf(
                QueueItem.Ayah(1), QueueItem.Gap(SPEECH_BREATH_MS), QueueItem.Speech(1), QueueItem.Gap(300L),
                QueueItem.Ayah(2), QueueItem.Gap(300L),
                QueueItem.Ayah(3), QueueItem.Gap(SPEECH_BREATH_MS), QueueItem.Speech(3),
            ),
            spokenFatiha().items,
        )
    }

    @Test
    fun `a translation reports the ayah it follows`() {
        val queue = spokenFatiha()
        assertTrue(queue.isSpeech(2))
        assertFalse(queue.isAyah(2))
        assertFalse(queue.isGap(2))
        assertEquals(1, queue.ayahAt(2))
        assertEquals(0, queue.ayahIndexAt(2))
        assertEquals(1, queue.ayahAt(1))
        assertEquals(6, queue.ayahIndexAt(8))
    }

    @Test
    fun `ayahs keep their own indices past the translations`() {
        val queue = spokenFatiha()
        assertEquals(0, queue.indexOfAyah(1))
        assertEquals(4, queue.indexOfAyah(2))
        assertEquals(6, queue.indexOfAyah(3))
        assertEquals(listOf(1, 2, 3), queue.ayahs)
        assertEquals(3, queue.ayahCount)
        assertEquals(9, queue.size)
    }

    @Test
    fun `next from a translation is the next ayah and nothing after the last`() {
        val queue = spokenFatiha()
        assertEquals(4, queue.next(2))
        assertEquals(4, queue.next(1))
        assertEquals(6, queue.next(5))
        assertNull(queue.next(8))
    }

    @Test
    fun `previous during a translation restarts the ayah it follows`() {
        val queue = spokenFatiha()
        assertEquals(0, queue.previous(index = 2, positionMs = 0L))
        assertEquals(6, queue.previous(index = 8, positionMs = 10L))
        // An ayah after a translation still steps back within the first two seconds.
        assertEquals(0, queue.previous(index = 4, positionMs = 100L))
    }

    @Test
    fun `a silence knows its own length`() {
        val queue = spokenFatiha()
        assertEquals(SPEECH_BREATH_MS, queue.silenceMs(1))
        assertEquals(300L, queue.silenceMs(3))
        assertEquals(0L, queue.silenceMs(0))
        assertEquals(0L, queue.silenceMs(2))
    }

    @Test
    fun `a queue rebuilt from its items keeps the same arithmetic`() {
        val queue = RecitationQueue(
            0,
            listOf(QueueItem.Ayah(1), QueueItem.Gap(0L), QueueItem.Speech(1), QueueItem.Ayah(2)),
        )
        assertEquals(1, queue.ayahAt(2))
        assertEquals(3, queue.next(2))
        assertEquals(3, queue.indexOfAyah(2))
        assertEquals(listOf(1, 2), queue.ayahs)
    }

    @Test
    fun `an ayah outside the spoken set has no translation after it`() {
        val queue = RecitationQueue(1, (1..2).toList(), gapMs = 0L, spoken = setOf(2))
        assertEquals(
            listOf(QueueItem.Ayah(1), QueueItem.Ayah(2), QueueItem.Gap(SPEECH_BREATH_MS), QueueItem.Speech(2)),
            queue.items,
        )
    }
```

Append to `SurahTimelineTest.kt` inside the class:

```kotlin
    private val spoken = RecitationQueue(1, (1..2).toList(), gapMs = 300L, spoken = setOf(1))

    @Test
    fun `a translation takes the slot it is given and the breath its length`() {
        val clock = SurahTimeline.of(spoken, { 1_000L }) { 5_000L }
        // [ayah 1000][breath 400][speech 5000][gap 300][ayah 1000]
        assertEquals(7_700L, clock.totalMs)
        assertEquals(1_400L, clock.startOf(2))
        assertEquals(6_400L, clock.startOf(3))
    }

    @Test
    fun `without speech lengths a translation slot is empty`() {
        val clock = SurahTimeline.of(spoken) { 1_000L }
        assertEquals(0L, clock.durationOf(2))
        assertEquals(2_700L, clock.totalMs)
    }

    @Test
    fun `a seek inside a translation lands on the next ayah`() {
        val clock = SurahTimeline.of(spoken, { 1_000L }) { 5_000L }
        assertEquals(4, clock.snapToAyah(3_000L) { !spoken.isAyah(it) })
        assertEquals(4, clock.snapToAyah(1_200L) { !spoken.isAyah(it) })
    }

    @Test
    fun `a seek inside the last translation goes back to its ayah`() {
        val last = RecitationQueue(1, (1..2).toList(), gapMs = 300L, spoken = setOf(2))
        val clock = SurahTimeline.of(last, { 1_000L }) { 5_000L }
        // [ayah1 0-1000][gap 1000-1300][ayah2 1300-2300][breath 2300-2700][speech 2700-7700]
        assertEquals(2, clock.snapToAyah(5_000L) { !last.isAyah(it) })
    }
```

- [ ] **Step 2: Run the tests and see them fail.** Run `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/read-aloud && ./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.recitation.RecitationQueueTest' --tests 'world.taqwa.app.recitation.SurahTimelineTest'`. Expected: compilation FAILS (`Speech`, `spoken`, `isSpeech` unresolved).

- [ ] **Step 3: Replace `RecitationQueue.kt` with this.**

```kotlin
package world.taqwa.app.recitation

/**
 * One entry in a surah's playback queue. The gaps are entries of their own rather than a property
 * of the ayah before them, because that is how both platforms play them — a silent item on
 * Android, a timed wait on iOS — and because it is the only way the queue index the platform
 * reports back can be turned into an ayah without guessing.
 */
sealed interface QueueItem {
    /** An ayah of the surah, played from its byte range inside the `.taqa`. */
    data class Ayah(val n: Int) : QueueItem

    /**
     * A silence: the reciter's own inter-ayah gap (spec §2), or the breath before a spoken
     * translation (read-aloud spec §2). Never at either end of the queue.
     */
    data class Gap(val durationMs: Long) : QueueItem

    /**
     * The translation read aloud after ayah [n] by the phone's own voice (read-aloud spec §2) —
     * for a run of ayahs that share one text, after the last of them.
     */
    data class Speech(val n: Int) : QueueItem
}

/** The breath between an ayah and its spoken translation (read-aloud spec §2). */
const val SPEECH_BREATH_MS = 400L

/**
 * The index arithmetic of a surah's playback, and nothing else: no player, no files, no platform.
 *
 * Four of the ten reciters begin at full voice on sample zero and sound rushed played back to
 * back, so the manifest carries an inter-ayah gap per reciter which the player inserts between
 * items (spec §2); with read-aloud on, a breath and the translation follow an ayah too. That is
 * what makes this class worth having — the platform's "current item" is no longer the ayah
 * number, most items are not ayahs at all, and previous/next have to skip over them. Getting that
 * wrong shows up as an ayah highlight that flickers to nothing between every two ayahs, which is
 * exactly the sort of thing a unit test should catch rather than a device.
 *
 * **While a gap or a translation plays the queue keeps reporting the ayah that just ended.** The
 * alternative — reporting nothing, or the ayah about to start — would either blank the reader's
 * highlight for 300 ms every ayah or move it before the voice does.
 *
 * Built from its [items], which is how the Android session rebuilds it from the ids it holds; the
 * secondary constructor builds the items from the surah's ayahs.
 */
class RecitationQueue(
    val surah: Int,
    val items: List<QueueItem>,
) {

    /**
     * [ayahs] in playing order, each followed — when it is in [spoken] — by a breath and its
     * translation, and then by the reciter's [gapMs] of silence unless it is the last ayah. A
     * zero or negative gap means no gap items at all.
     */
    constructor(surah: Int, ayahs: List<Int>, gapMs: Long, spoken: Set<Int> = emptySet()) :
        this(surah, build(ayahs, gapMs, spoken))

    /** The ayah numbers in playing order, normally 1..n. */
    val ayahs: List<Int> = items.mapNotNull { (it as? QueueItem.Ayah)?.n }

    init {
        require(ayahs.isNotEmpty()) { "A recitation queue needs at least one ayah" }
    }

    val ayahCount: Int get() = ayahs.size

    /** How many items the platform is given. */
    val size: Int get() = items.size

    /** True when any silence — a reciter's gap or a breath — is in the queue. */
    val hasGaps: Boolean = items.any { it is QueueItem.Gap }

    /** For each item, the position in [ayahs] of the ayah it is or follows. */
    private val owner: IntArray = IntArray(items.size).also { owners ->
        var position = -1
        items.forEachIndexed { i, item ->
            if (item is QueueItem.Ayah) position++
            owners[i] = position.coerceAtLeast(0)
        }
    }

    /** For each position in [ayahs], the queue index of that ayah's own item. */
    private val ayahItem: IntArray = IntArray(ayahs.size).also { at ->
        var position = 0
        items.forEachIndexed { i, item -> if (item is QueueItem.Ayah) at[position++] = i }
    }

    fun isAyah(index: Int): Boolean = items.getOrNull(index) is QueueItem.Ayah

    fun isSpeech(index: Int): Boolean = items.getOrNull(index) is QueueItem.Speech

    /** A silent item: the reciter's gap or the breath before a translation. */
    fun isGap(index: Int): Boolean = items.getOrNull(index) is QueueItem.Gap

    /** How long the silence at [index] lasts; zero for anything that is not a silence. */
    fun silenceMs(index: Int): Long = (items.getOrNull(index) as? QueueItem.Gap)?.durationMs ?: 0L

    fun contains(ayah: Int): Boolean = ayah in ayahs

    /** The queue index ayah [n] starts at, or null if this surah has no such ayah. */
    fun indexOfAyah(n: Int): Int? {
        val position = ayahs.indexOf(n)
        return if (position < 0) null else ayahItem[position]
    }

    /**
     * The ayah to report while queue item [index] is playing — the ayah itself, or, for a gap or
     * a translation, the ayah it follows. Out-of-range indices are clamped rather than thrown on:
     * the platform can report an index from a timeline this queue has already been replaced in.
     */
    fun ayahAt(index: Int): Int = ayahs[positionAt(index)]

    /** The queue index of the ayah item [index] belongs to: itself for an ayah. */
    fun ayahIndexAt(index: Int): Int = ayahItem[positionAt(index)]

    /** The queue index of the next ayah, or null when the last ayah is the one playing. */
    fun next(index: Int): Int? {
        val position = positionAt(index)
        return if (position + 1 <= ayahs.lastIndex) ayahItem[position + 1] else null
    }

    /**
     * Where "previous" goes from queue item [index] at [positionMs] into it: the previous ayah
     * within the first [RESTART_WINDOW_MS] of an ayah, otherwise the start of the ayah playing.
     * At the first ayah it always restarts, so this never returns null.
     *
     * A gap or a translation always restarts the ayah it follows: the ayah has by then finished,
     * so "back to the start of what I am hearing" is that ayah, and treating the gap's own
     * position as an ayah's would send the listener a whole ayah backwards for pressing late.
     */
    fun previous(index: Int, positionMs: Long): Int {
        val position = positionAt(index)
        val stepBack = isAyah(index) && positionMs < RESTART_WINDOW_MS && position > 0
        return ayahItem[if (stepBack) position - 1 else position]
    }

    /** The position in [ayahs] a queue index belongs to, clamped into the queue. */
    private fun positionAt(index: Int): Int = owner[index.coerceIn(0, items.size - 1)]

    companion object {
        /** Past this into an ayah, "previous" restarts it instead of going back one (spec §6). */
        const val RESTART_WINDOW_MS = 2_000L

        /** The queue a container's own index describes, with the reciter's gap between ayahs. */
        fun of(index: TaqaIndex, gapMs: Long, spoken: Set<Int> = emptySet()): RecitationQueue = RecitationQueue(
            surah = index.surah,
            ayahs = index.ayahs.map { it.n },
            gapMs = gapMs,
            spoken = spoken,
        )

        private fun build(ayahs: List<Int>, gapMs: Long, spoken: Set<Int>): List<QueueItem> = buildList {
            ayahs.forEachIndexed { position, n ->
                add(QueueItem.Ayah(n))
                if (n in spoken) {
                    add(QueueItem.Gap(SPEECH_BREATH_MS))
                    add(QueueItem.Speech(n))
                }
                if (gapMs > 0L && position < ayahs.lastIndex) add(QueueItem.Gap(gapMs))
            }
        }
    }
}
```

- [ ] **Step 4: Update `SurahTimeline.kt`.** Replace `snapToAyah` with:

```kotlin
    /**
     * Where a seek to surah time [ms] should land: the start of the ayah whose slot holds it, or,
     * when it falls inside a gap or a translation, the start of the next ayah — and when there is
     * no next ayah, the last one. [isGap] is true for every item that is **not** an ayah.
     * Recitation moves by ayah — a scrub on the lock screen that dropped the voice into the
     * middle of a word would be worse than one that snapped — so the snap is the rule and this
     * is where it lives.
     */
    fun snapToAyah(ms: Long, isGap: (Int) -> Boolean): Int {
        val index = indexAt(ms)
        var forward = index
        while (forward <= itemsMs.lastIndex && isGap(forward)) forward++
        if (forward <= itemsMs.lastIndex) return forward
        var back = index
        while (back > 0 && isGap(back)) back--
        return back
    }
```

Replace the companion's `of` with the two overloads:

```kotlin
        /** The clock for [queue], asking [ayahMs] for each ayah's length; gaps carry their own. */
        fun of(queue: RecitationQueue, ayahMs: (Int) -> Long): SurahTimeline = of(queue, ayahMs) { 0L }

        /**
         * The clock with read-aloud on (read-aloud spec §5.2): each translation gets the slot
         * [speechMs] estimates for the ayah it follows, so the lock screen's bar keeps moving
         * while the voice reads rather than standing still and jumping.
         */
        fun of(queue: RecitationQueue, ayahMs: (Int) -> Long, speechMs: (Int) -> Long): SurahTimeline = SurahTimeline(
            queue.items.map { item ->
                when (item) {
                    is QueueItem.Ayah -> ayahMs(item.n).coerceAtLeast(MIN_AYAH_MS)
                    is QueueItem.Gap -> item.durationMs
                    is QueueItem.Speech -> speechMs(item.n).coerceAtLeast(0L)
                }
            },
        )
```

- [ ] **Step 5: Keep the players compiling on the new names.**
  - In `AyahPlayer.kt`'s `seekTo`, replace `real.seekTo(clock.snapToAyah(positionMs, queue::isGap), 0L)` with `real.seekTo(clock.snapToAyah(positionMs) { !queue.isAyah(it) }, 0L)`.
  - In `RecitationPlayer.ios.kt`, make three replacements:
    - `if (built.isGap(at)) waitOutGap(built, built.gapMs - gapElapsedMs) else player?.play()` becomes `if (built.isGap(at)) waitOutGap(built, built.silenceMs(at) - gapElapsedMs) else player?.play()`.
    - `if (wantsPlay) waitOutGap(built, built.gapMs)` becomes `if (wantsPlay) waitOutGap(built, built.silenceMs(at))`.
    - `go(clock.snapToAyah(positionMs, built::isGap))` becomes `go(clock.snapToAyah(positionMs) { !built.isAyah(it) })`.
  - Then run `grep -rn '\.gapMs' shared/src --include='*.kt' | grep -v 'reciter.gapMs\|Reciter\|gapMs:'`. Expected: no line calling `gapMs` on a `RecitationQueue`.

- [ ] **Step 6: Run the tests.** Run `cd /Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/read-aloud && ./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.recitation.*' && ./gradlew :shared:compileKotlinIosSimulatorArm64`. Expected: all recitation tests PASS, the old queue and timeline tests included, and iOS compiles.

- [ ] **Step 7: Commit.**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationQueue.kt shared/src/commonMain/kotlin/world/taqwa/app/recitation/SurahTimeline.kt shared/src/androidMain/kotlin/world/taqwa/app/recitation/AyahPlayer.kt shared/src/iosMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.ios.kt shared/src/commonTest/kotlin/world/taqwa/app/recitation/RecitationQueueTest.kt shared/src/commonTest/kotlin/world/taqwa/app/recitation/SurahTimelineTest.kt
git commit -m "recitation: a queue item for a spoken translation, and its slot on the clock"
```

---

### Task 2: SpeechText — what a voice reads

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/SpeechText.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/recitation/SpeechTextTest.kt`
- Test: `shared/src/androidHostTest/kotlin/world/taqwa/app/recitation/SpeechTextDbTest.kt`

**Interfaces:**
- Produces `object SpeechText` with:
  - `MAX_CHARS = 3_900` and `MIN_SPEECH_MS = 1_000L`
  - `fun prepare(kind: TextKind, language: String, surah: Int, texts: Map<Int, String>): Map<Int, String>`
  - `fun speakable(kind: TextKind, language: String, surah: Int, ayah: Int, text: String): String`
  - `fun estimateMs(text: String, language: String): Long`

- [ ] **Step 1: Write the failing unit tests** in `SpeechTextTest.kt`:

```kotlin
package world.taqwa.app.recitation

import world.taqwa.app.quran.TextKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Read-aloud spec §3, rule by rule, on the bundled database's own sentences. */
class SpeechTextTest {

    private fun translation(language: String, text: String, surah: Int = 1, ayah: Int = 1) =
        SpeechText.speakable(TextKind.TRANSLATION, language, surah, ayah, text)

    private fun tafsir(surah: Int, ayah: Int, text: String) =
        SpeechText.speakable(TextKind.TAFSIR, "ar", surah, ayah, text)

    @Test
    fun `saheeh's brackets are read as words and its trailing dash is dropped`() {
        assertEquals(
            "All praise is due to Allah, Lord of the worlds",
            translation("en", "[All] praise is [due] to Allah, Lord of the worlds -"),
        )
    }

    @Test
    fun `urdu honorifics keep their words without the ornate brackets`() {
        assertEquals(
            "(حضرت) آدم علیہ السلام سے فرمایا",
            translation("ur", "(حضرت) آدم ﴿علیہ السلام﴾ سے فرمایا"),
        )
    }

    @Test
    fun `a verse quoted in braces is dropped with its reference`() {
        assertEquals(
            "وهي قوله تعالى: فتاب الله عليه.",
            tafsir(2, 37, "وهي قوله تعالى: {رَبَّنَا ظَلَمْنَا أَنْفُسَنَا (7:23)}، فتاب الله عليه."),
        )
    }

    @Test
    fun `a fully voweled quotation of the ayah is dropped`() {
        assertEquals(
            "الثناء على الله بصفاته.",
            tafsir(1, 2, "(الحَمْدُ للهِ رَبِّ العَالَمِينَ) الثناء على الله بصفاته."),
        )
    }

    @Test
    fun `a lightly voweled name stays because it is the sentence's subject`() {
        assertEquals("(اللهِ) علم على الرب", tafsir(1, 1, "(اللهِ) علم على الرب"))
    }

    @Test
    fun `an explanation in parentheses is read`() {
        assertEquals("(وهم المسلمون) والذين هادوا", tafsir(5, 69, "(وهم المسلمون) والذين هادوا"))
    }

    @Test
    fun `a surah's opening letters are dropped at its opening`() {
        assertEquals(
            "سبق الكلام على الحروف المقطَّعة في أول سورة البقرة.",
            tafsir(10, 1, "(الر) سبق الكلام على الحروف المقطَّعة في أول سورة البقرة."),
        )
        assertEquals("سبق الكلام.", tafsir(42, 1, "(حم * عسق) سبق الكلام."))
        assertEquals("سبق الكلام.", tafsir(29, 1, "الم: سبق الكلام."))
    }

    @Test
    fun `the same letters elsewhere are read`() {
        assertEquals("(الم) كلمة", tafsir(5, 1, "(الم) كلمة"))
        assertEquals("(الله) ربنا", tafsir(2, 1, "(الله) ربنا"))
    }

    @Test
    fun `bengali's mangled character references are repaired`() {
        assertEquals("চিহিত ঘোড়ার", translation("bn", "চিহিߦ#2468; ঘোড়ার"))
        assertEquals("কতৃক নির্ধারিত", translation("bn", "কতৃꦣ2453; নির্ধারিত"))
        assertEquals("প্রজ্জিত করে", translation("bn", "প্রজ্জ?482;িত করে").replace("িত", "িত"))
    }

    @Test
    fun `other languages keep their digits`() {
        assertEquals("Kami angkat 12 orang pemimpin", translation("id", "Kami angkat 12 orang pemimpin"))
    }

    @Test
    fun `transliteration is never read`() {
        assertEquals("", SpeechText.speakable(TextKind.TRANSLITERATION, "en", 1, 1, "Bismi Allahi"))
        assertEquals(emptyMap(), SpeechText.prepare(TextKind.TRANSLITERATION, "en", 1, mapOf(1 to "Bismi")))
    }

    @Test
    fun `a run of ayahs sharing one text is read once after its last ayah`() {
        assertEquals(
            mapOf(124 to "Aynı metin.", 125 to "Başka."),
            SpeechText.prepare(TextKind.TRANSLATION, "tr", 26, mapOf(123 to "Aynı metin.", 124 to "Aynı metin.", 125 to "Başka.")),
        )
    }

    @Test
    fun `the same text on ayahs that are not neighbours is read twice`() {
        assertEquals(
            mapOf(1 to "Same.", 3 to "Same."),
            SpeechText.prepare(TextKind.TRANSLATION, "en", 1, mapOf(1 to "Same.", 3 to "Same.")),
        )
    }

    @Test
    fun `a text that is all quotation leaves nothing to read`() {
        assertEquals(emptyMap(), SpeechText.prepare(TextKind.TAFSIR, "ar", 1, mapOf(1 to "(الرَّحْمَنِ)")))
    }

    @Test
    fun `an overlong text is cut at a sentence end`() {
        val long = "Sentence one is here. ".repeat(300)
        val spoken = translation("en", long)
        assertTrue(spoken.length <= SpeechText.MAX_CHARS, "length ${spoken.length}")
        assertTrue(spoken.endsWith("."))
    }

    @Test
    fun `the estimate is characters over the language's rate and at least a second`() {
        assertEquals(10_000L, SpeechText.estimateMs("a".repeat(160), "en"))
        assertEquals(10_000L, SpeechText.estimateMs("a".repeat(130), "ar"))
        assertEquals(1_000L, SpeechText.estimateMs("short", "en"))
    }
}
```

(The third Bengali assertion's `.replace` is a no-op kept for clarity. What is checked is that `?482;` is removed and the neighbouring text survives.)

- [ ] **Step 2: Run the tests and see them fail.** Run `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.recitation.SpeechTextTest'`. Expected: compilation FAILS (`SpeechText` unresolved).

- [ ] **Step 3: Create `SpeechText.kt`.**

```kotlin
package world.taqwa.app.recitation

import world.taqwa.app.quran.TextKind

/**
 * What a phone voice reads after an ayah (read-aloud spec §3): the text the reader shows under
 * it, cleaned of what a voice should not say, and — for Tafsir al-Muyassar — without the Quran
 * words it quotes, because a phone voice never recites the Quran. Pure, so every rule is held to
 * the whole bundled database by `SpeechTextDbTest`.
 */
object SpeechText {

    /** Android's input limit is 4,000 characters; the longest bundled text is about 1,700. */
    const val MAX_CHARS = 3_900

    /** The shortest slot a translation is given on the surah's clock. */
    const val MIN_SPEECH_MS = 1_000L

    /**
     * What to read after which ayah of [surah], keyed by that ayah. Consecutive ayahs that carry
     * the same text — Diyanet and Muyassar give one text to a run of up to fourteen — are read
     * once, after the run's last ayah. An ayah whose text prepares to nothing gets no entry.
     */
    fun prepare(kind: TextKind, language: String, surah: Int, texts: Map<Int, String>): Map<Int, String> {
        if (kind == TextKind.TRANSLITERATION) return emptyMap()
        val ordered = texts.entries.sortedBy { it.key }
        val prepared = LinkedHashMap<Int, String>()
        var start = 0
        while (start < ordered.size) {
            var end = start
            while (
                end + 1 < ordered.size &&
                ordered[end + 1].key == ordered[end].key + 1 &&
                ordered[end + 1].value == ordered[start].value
            ) {
                end++
            }
            val spoken = speakable(kind, language, surah, ordered[start].key, ordered[start].value)
            if (spoken.isNotEmpty()) prepared[ordered[end].key] = spoken
            start = end + 1
        }
        return prepared
    }

    /** One ayah's text as a voice should read it; empty when nothing is left to read. */
    fun speakable(kind: TextKind, language: String, surah: Int, ayah: Int, text: String): String {
        if (kind == TextKind.TRANSLITERATION) return ""
        var s = text
        if (kind == TextKind.TAFSIR) s = dropQuotations(surah, ayah, s)
        if (language == "bn") s = repairBengali(s)
        s = STRIPPED.replace(s, "")
        return clip(tidy(s))
    }

    /**
     * How long [text] takes to read at a phone's default rate, for the surah's clock (read-aloud
     * spec §5.2). An estimate: the player scales the real reading into this slot.
     */
    fun estimateMs(text: String, language: String): Long {
        val perSecond = when (language) {
            "en" -> 16
            "fr" -> 15
            "tr", "id" -> 14
            "ar", "ur" -> 13
            "bn" -> 12
            else -> 14
        }
        return (text.length * 1_000L / perSecond).coerceAtLeast(MIN_SPEECH_MS)
    }

    // ── Tafsir al-Muyassar's quotations (spec §3.2) ──────────────────────────────────────

    private fun dropQuotations(surah: Int, ayah: Int, text: String): String {
        val opening = OPENING_LETTERS[surah]?.takeIf { ayah <= 2 }
        var s = QUOTED_VERSES.replace(text, " ")
        s = PARENTHESES.replace(s) { match ->
            val inside = match.groupValues[1]
            if (isVoweled(inside) || (opening != null && isOpening(inside, opening))) " " else match.value
        }
        if (opening != null) {
            s = LEADING_LETTERS.replace(s) { match ->
                if (isOpening(match.groupValues[1], opening)) "" else match.value
            }
        }
        return s
    }

    /** A quotation of the ayah: at least one vowel mark for every two Arabic letters. */
    private fun isVoweled(inside: String): Boolean {
        val letters = inside.count { it in 'ء'..'ي' }
        if (letters == 0) return false
        val marks = inside.count { it in 'ً'..'ْ' || it == 'ٰ' }
        return marks * 2 >= letters
    }

    /** Only the letters a surah opens with (spaces and Muyassar's `*` between them allowed). */
    private fun isOpening(inside: String, accepted: Set<String>): Boolean {
        if (inside.any { !it.isWhitespace() && it != '*' && it !in 'ء'..'ي' }) return false
        return inside.filter { it in 'ء'..'ي' } in accepted
    }

    // ── Bengali's mangled character references (spec §3.1) ──────────────────────────────

    private fun repairBengali(text: String): String = MANGLED_REFERENCE.replace(text) { match ->
        val code = match.groupValues[1].toInt()
        if (code in 0x0980..0x09FF) code.toChar().toString() else ""
    }

    // ── Tidying ──────────────────────────────────────────────────────────────────────────

    private fun tidy(text: String): String {
        var s = WHITESPACE.replace(text, " ")
        s = SPACE_BEFORE_MARK.replace(s, "$1")
        s = STACKED_MARKS.replace(s, "$1")
        s = LEADING_MARKS.replace(s, "")
        s = TRAILING_DASH.replace(s, "")
        return s.trim()
    }

    private fun clip(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val cut = text.substring(0, MAX_CHARS)
        val end = cut.indexOfLast { it in ".!?؟۔।" }
        return if (end > MAX_CHARS / 2) cut.substring(0, end + 1) else cut
    }

    /** The 29 surahs that open with disjoined letters, as Muyassar writes them. */
    private val OPENING_LETTERS: Map<Int, Set<String>> = buildMap {
        listOf(2, 3, 29, 30, 31, 32).forEach { put(it, setOf("الم")) }
        put(7, setOf("المص"))
        listOf(10, 11, 12, 14, 15).forEach { put(it, setOf("الر")) }
        put(13, setOf("المر"))
        put(19, setOf("كهيعص"))
        put(20, setOf("طه"))
        listOf(26, 28).forEach { put(it, setOf("طسم")) }
        put(27, setOf("طس"))
        put(36, setOf("يس"))
        put(38, setOf("ص"))
        listOf(40, 41, 43, 44, 45, 46).forEach { put(it, setOf("حم")) }
        put(42, setOf("حمعسق", "حم", "عسق"))
        put(50, setOf("ق"))
        put(68, setOf("ن"))
    }

    private val QUOTED_VERSES = Regex("\\{[^{}]*\\}|\\uFD3F[^\\uFD3E]*\\uFD3E")
    private val PARENTHESES = Regex("\\(([^()]*)\\)")
    private val LEADING_LETTERS = Regex("^\\s*([\\u0621-\\u064A\\s*]{1,12}?)\\s*:")
    private val MANGLED_REFERENCE = Regex("[^\\s\\u0980-\\u09FF]?#?(\\d{3,4});")
    private val STRIPPED = Regex("[\\[\\]{}\\uFD3E\\uFD3F`*]")
    private val WHITESPACE = Regex("\\s+")
    private val SPACE_BEFORE_MARK = Regex(" ([\\u060C,\\u061B;:.!?\\u061F])")
    private val STACKED_MARKS = Regex("([\\u060C,\\u061B;:.!?\\u061F])(?: ?[\\u060C,\\u061B;])+")
    private val LEADING_MARKS = Regex("^[\\s\\u060C,\\u061B;:.!?\\u061F\\-\\u2013\\u2014]+")
    private val TRAILING_DASH = Regex("\\s*[\\-\\u2013\\u2014]+\\s*$")
}
```

- [ ] **Step 4: Run the unit tests.** Run `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.recitation.SpeechTextTest'`. Expected: PASS. If an expectation differs only in tidying, fix the code rather than the test: the expected strings are the spec's.

- [ ] **Step 5: Write the database test.** Create `shared/src/androidHostTest/kotlin/world/taqwa/app/recitation/SpeechTextDbTest.kt`:

```kotlin
package world.taqwa.app.recitation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import world.taqwa.app.quran.QuranRepository
import world.taqwa.app.quran.QuranRepositoryDbTest
import world.taqwa.app.quran.TextKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Read-aloud spec §3 held to every row of the bundled `quran.db`: what the voice would read in
 * all seven texts, all 6,236 ayahs. JVM-only for the same reason as [QuranRepositoryDbTest].
 */
class SpeechTextDbTest {

    private val repo = QuranRepository(
        driverFactory = { JdbcSqliteDriver("jdbc:sqlite:" + QuranRepositoryDbTest.dbFile().absolutePath) },
        io = Dispatchers.Unconfined,
    )

    private val texts = listOf(
        Triple("en.sahih", "en", TextKind.TRANSLATION),
        Triple("fr.hamidullah", "fr", TextKind.TRANSLATION),
        Triple("tr.diyanet", "tr", TextKind.TRANSLATION),
        Triple("id.indonesian", "id", TextKind.TRANSLATION),
        Triple("bn.bengali", "bn", TextKind.TRANSLATION),
        Triple("ur.junagarhi", "ur", TextKind.TRANSLATION),
        Triple("ar.muyassar", "ar", TextKind.TAFSIR),
    )

    private suspend fun prepared(id: String, language: String, kind: TextKind): Map<Int, Map<Int, String>> =
        (1..114).associateWith { surah -> SpeechText.prepare(kind, language, surah, repo.translationTexts(id, surah)) }

    @Test
    fun muyassarKeepsNoQuotation() = runTest {
        prepared("ar.muyassar", "ar", TextKind.TAFSIR).forEach { (surah, spoken) ->
            spoken.forEach { (ayah, text) ->
                assertFalse('{' in text || '}' in text, "$surah:$ayah still has braces")
                Regex("\\(([^()]*)\\)").findAll(text).forEach { match ->
                    val inside = match.groupValues[1]
                    val letters = inside.count { it in 'ء'..'ي' }
                    val marks = inside.count { it in 'ً'..'ْ' || it == 'ٰ' }
                    assertTrue(letters == 0 || marks * 2 < letters, "$surah:$ayah still quotes: $inside")
                }
            }
        }
    }

    @Test
    fun muyassarDropsEveryOpeningLetterQuotation() = runTest {
        val all = prepared("ar.muyassar", "ar", TextKind.TAFSIR)
        listOf(10, 11, 12, 13, 14, 15, 19, 20, 26, 27, 28, 29, 30, 31, 32, 36, 38, 40, 41, 42, 43, 44, 45, 46, 50, 68)
            .forEach { surah ->
                val first = all.getValue(surah).entries.first().value
                assertTrue(first.startsWith("سبق"), "$surah opens with: ${first.take(30)}")
            }
    }

    @Test
    fun fatihaDropsItsQuotationsAndKeepsTheName() = runTest {
        val fatiha = SpeechText.prepare(TextKind.TAFSIR, "ar", 1, repo.translationTexts("ar.muyassar", 1))
        assertTrue(fatiha.getValue(2).startsWith("الثناء على الله"), fatiha.getValue(2).take(40))
        assertFalse("الرَّحْمَنِ" in fatiha.getValue(1))
        assertTrue("(اللهِ)" in fatiha.getValue(1))
    }

    @Test
    fun bengaliHasNoMangledReference() = runTest {
        prepared("bn.bengali", "bn", TextKind.TRANSLATION).forEach { (surah, spoken) ->
            spoken.forEach { (ayah, text) ->
                assertFalse(Regex("\\d{3,4};").containsMatchIn(text), "$surah:$ayah: $text")
            }
        }
    }

    @Test
    fun everyTextFitsAndNothingIsBlank() = runTest {
        texts.forEach { (id, language, kind) ->
            prepared(id, language, kind).forEach { (surah, spoken) ->
                spoken.forEach { (ayah, text) ->
                    assertTrue(text.isNotBlank(), "$id $surah:$ayah blank")
                    assertTrue(text.length <= SpeechText.MAX_CHARS, "$id $surah:$ayah is ${text.length}")
                }
            }
        }
    }

    @Test
    fun diyanetRunsAreReadOnce() = runTest {
        val raw = repo.translationTexts("tr.diyanet", 26)
        val spoken = SpeechText.prepare(TextKind.TRANSLATION, "tr", 26, raw)
        assertTrue(spoken.size < raw.size, "no run grouped: ${spoken.size} of ${raw.size}")
        // Every run is read after its last ayah: the ayah after a key never has the key's text.
        spoken.keys.forEach { n -> raw[n + 1]?.let { next -> assertFalse(next == raw[n], "26:$n is not a run's end") } }
    }

    @Test
    fun saheehReadsItsBracketsAsWords() = runTest {
        val fatiha = SpeechText.prepare(TextKind.TRANSLATION, "en", 1, repo.translationTexts("en.sahih", 1))
        assertEquals("All praise is due to Allah, Lord of the worlds", fatiha.getValue(2))
    }
}
```

- [ ] **Step 6: Run the database test.** Run `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.recitation.SpeechTextDbTest'`. Expected: PASS.
  - If `muyassarDropsEveryOpeningLetterQuotation` fails for one surah, print that row (`sqlite3 shared/src/commonMain/composeResources/files/quran.db "select text from ayah_translation where translation_id='ar.muyassar' and surah=N and number=1"`). Fix the rule if the data is a quotation; otherwise take the surah out of the list and say why in a comment.
  - Report the failure and the fix.

- [ ] **Step 7: Commit.**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/recitation/SpeechText.kt shared/src/commonTest/kotlin/world/taqwa/app/recitation/SpeechTextTest.kt shared/src/androidHostTest/kotlin/world/taqwa/app/recitation/SpeechTextDbTest.kt
git commit -m "recitation: prepare a translation for the voice, the tafsir without its quotations"
```

---

### Task 3: The contract, the setting and the controller

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/SpokenTranslation.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/VoicePick.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/SpeechVoices.android.kt` (a stub; Task 5 replaces it)
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/recitation/SpeechVoices.ios.kt` (a stub; Task 7 replaces it)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.kt` (`PlaybackState.speaking`, `load`, `setSpeech`)
- Modify: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.android.kt` and `shared/src/iosMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.ios.kt` (signature only)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/recitation/RecitationLibrary.kt` (`RecitationSettings.readAloud`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt`, `SettingsRepository.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/RecitationPorts.kt`, `RecitationState.kt`, `RecitationController.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt`
- Modify: `iosApp/iosApp/iOSApp.swift` (`load` call gains `speech: nil`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/recitation/RecitationControllerTest.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/recitation/VoicePickTest.kt`

**Interfaces:**
- Consumes: `RecitationQueue`, `SpeechText` (Tasks 1 and 2).
- Produces, in package `world.taqwa.app.recitation`:
  - `SpeechVoice(engine: String, id: String)`
  - `sealed interface VoiceStatus { Ready(voice); Missing(engine: String); Unsupported }`
  - `interface SpeechVoices { suspend fun status(language: String): VoiceStatus; fun installVoice(engine: String) }` and `expect fun createSpeechVoices(): SpeechVoices`
  - `SpokenTranslation(translationId, kind: TextKind, language, voice: SpeechVoice, texts: Map<Int, String>)` with `spoken: Set<Int>` and `estimateMs(n: Int): Long`
  - `suspend fun buildSpokenTranslation(quran: QuranSource, voices: SpeechVoices, translationId: String, surah: Int): SpokenTranslation?`
  - `VoiceCandidate(id, language, country, offline, installed, quality)`
  - `object VoicePick { fun best(language, candidates): VoiceCandidate?; fun downloadable(language, candidates): Boolean; fun normalize(language): String }`
  - `PlaybackState.speaking: Boolean`
  - `RecitationPlayer.load(…, speech: SpokenTranslation? = null)` and `RecitationPlayer.setSpeech(speech: SpokenTranslation?)`
  - `RecitationSettings.readAloud`
- Produces, in package `world.taqwa.app.feature.recitation`:
  - `PlayerPort.load(…, speech)` and `PlayerPort.setSpeech`
  - `RecitationSettingsPort.setReadAloud`
  - `interface ReadAloudPort` and `object NoReadAloud`, plus `fun readAloudPort(settings, voices)`
  - `ReadAloudState(enabled, translationName, language, kind, missingEngine: String? = null)` and `RecitationState.readAloud`
  - `BarState.readingAloud: TextKind?`
  - controller: `setLanguageTag(tag: String)`, `refreshVoices()`, `onForeground()`, `setReadAloud(on: Boolean)`, `getVoice()`
- In `AppContainer`: `val speechVoices: SpeechVoices`.

- [ ] **Step 1: Create `SpokenTranslation.kt`.**

```kotlin
package world.taqwa.app.recitation

import world.taqwa.app.quran.QuranSource
import world.taqwa.app.quran.TextKind

/**
 * The phone voice that reads a language (read-aloud spec §4): Android's engine package and
 * `Voice.name`, or iOS's voice identifier with an empty [engine].
 */
data class SpeechVoice(val engine: String, val id: String)

/** What this phone can do for one language (read-aloud spec §4). Asked of it, never guessed. */
sealed interface VoiceStatus {
    /** An offline voice is on the phone. */
    data class Ready(val voice: SpeechVoice) : VoiceStatus

    /** Android only: [engine] offers the language, but its voice is not downloaded yet. */
    data class Missing(val engine: String) : VoiceStatus

    /** Nothing on this phone can read the language offline; the switch is hidden. */
    data object Unsupported : VoiceStatus
}

/** The platform's voices. */
interface SpeechVoices {
    suspend fun status(language: String): VoiceStatus

    /** Opens [engine]'s own voice installer (Android). Nothing on iOS. */
    fun installVoice(engine: String)
}

expect fun createSpeechVoices(): SpeechVoices

/**
 * A surah's translation as the player reads it (read-aloud spec §5.3). [texts] are already
 * prepared by [SpeechText.prepare] and keyed by the ayah they are read after.
 */
data class SpokenTranslation(
    val translationId: String,
    val kind: TextKind,
    val language: String,
    val voice: SpeechVoice,
    val texts: Map<Int, String>,
) {
    /** The ayahs a translation is read after, for [RecitationQueue]. */
    val spoken: Set<Int> get() = texts.keys

    /** The surah clock's slot for the translation read after ayah [n]; zero when there is none. */
    fun estimateMs(n: Int): Long = texts[n]?.let { SpeechText.estimateMs(it, language) } ?: 0L
}

/**
 * [translationId] read aloud over [surah], or null when this phone has no voice for its language
 * or there is nothing to read. For the debug harnesses; the controller builds its own with the
 * setting and a cached status.
 */
suspend fun buildSpokenTranslation(
    quran: QuranSource,
    voices: SpeechVoices,
    translationId: String,
    surah: Int,
): SpokenTranslation? {
    val info = quran.translations().firstOrNull { it.id == translationId } ?: return null
    val voice = (voices.status(info.language) as? VoiceStatus.Ready)?.voice ?: return null
    val texts = SpeechText.prepare(info.kind, info.language, surah, quran.translationTexts(translationId, surah))
    if (texts.isEmpty()) return null
    return SpokenTranslation(info.id, info.kind, info.language, voice, texts)
}
```

- [ ] **Step 2: Write the failing `VoicePickTest.kt`**, then create `VoicePick.kt`.

```kotlin
package world.taqwa.app.recitation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoicePickTest {

    private fun voice(id: String, language: String, country: String = "", offline: Boolean = true, installed: Boolean = true, quality: Int = 300) =
        VoiceCandidate(id, language, country, offline, installed, quality)

    @Test
    fun `only an offline installed voice of the language counts`() {
        val voices = listOf(
            voice("fr-net", "fr", "FR", offline = false),
            voice("fr-missing", "fr", "FR", installed = false),
            voice("en-local", "en", "US"),
        )
        assertNull(VoicePick.best("fr", voices))
        assertTrue(VoicePick.downloadable("fr", voices))
        assertEquals("en-local", VoicePick.best("en", voices)?.id)
        assertFalse(VoicePick.downloadable("ur", voices))
    }

    @Test
    fun `java's legacy indonesian code is indonesian`() {
        assertEquals("id-local", VoicePick.best("id", listOf(voice("id-local", "in", "ID")))?.id)
        assertEquals("id", VoicePick.normalize("ind"))
        assertEquals("ar", VoicePick.normalize("ar-001"))
    }

    @Test
    fun `quality wins and then the preferred country`() {
        val bengali = listOf(voice("bn-in", "bn", "IN"), voice("bn-bd", "bn", "BD"))
        assertEquals("bn-bd", VoicePick.best("bn", bengali)?.id)
        val english = listOf(voice("en-us", "en", "US", quality = 300), voice("en-gb-premium", "en", "GB", quality = 500))
        assertEquals("en-gb-premium", VoicePick.best("en", english)?.id)
    }
}
```

```kotlin
package world.taqwa.app.recitation

/**
 * One voice as a platform describes it, reduced to what the choice needs (read-aloud spec §4).
 * [language] and [country] are as the platform reports them ("in", "ind", "ar-001" all happen).
 */
data class VoiceCandidate(
    val id: String,
    val language: String,
    val country: String,
    val offline: Boolean,
    val installed: Boolean,
    val quality: Int,
)

/** The choice of voice, shared by both platforms so one test holds it. */
object VoicePick {

    /** The best offline, installed voice for [language]: highest quality, then the preferred country. */
    fun best(language: String, candidates: List<VoiceCandidate>): VoiceCandidate? {
        val wanted = normalize(language)
        val countries = PREFERRED[wanted].orEmpty()
        return candidates
            .filter { normalize(it.language) == wanted && it.offline && it.installed }
            .sortedWith(
                compareByDescending<VoiceCandidate> { it.quality }
                    .thenBy { voice -> countries.indexOf(voice.country.uppercase()).let { if (it < 0) countries.size else it } }
                    .thenBy { it.id },
            )
            .firstOrNull()
    }

    /** True when [language] is offered but not yet usable offline: a download away. */
    fun downloadable(language: String, candidates: List<VoiceCandidate>): Boolean {
        val wanted = normalize(language)
        return candidates.any { normalize(it.language) == wanted && !(it.offline && it.installed) }
    }

    /** Legacy and three-letter codes, and region suffixes, to the database's two letters. */
    fun normalize(language: String): String =
        when (val code = language.lowercase().substringBefore('-').substringBefore('_')) {
            "in", "ind" -> "id"
            "eng" -> "en"
            "ara" -> "ar"
            "fra", "fre" -> "fr"
            "tur" -> "tr"
            "urd" -> "ur"
            "ben" -> "bn"
            else -> code
        }

    private val PREFERRED = mapOf(
        "en" to listOf("US", "GB", "AU", "IE", "ZA", "IN"),
        "fr" to listOf("FR", "CA", "BE", "CH"),
        "ar" to listOf("001", "SA", "EG", "XA"),
        "bn" to listOf("BD", "IN"),
        "ur" to listOf("PK", "IN"),
        "tr" to listOf("TR"),
        "id" to listOf("ID"),
    )
}
```

- [ ] **Step 3: Add the platform stubs.** Create `SpeechVoices.android.kt` and `SpeechVoices.ios.kt`, each with:

```kotlin
package world.taqwa.app.recitation

/** Replaced by the real engine query in the platform task; until then no language is readable. */
actual fun createSpeechVoices(): SpeechVoices = object : SpeechVoices {
    override suspend fun status(language: String): VoiceStatus = VoiceStatus.Unsupported
    override fun installVoice(engine: String) = Unit
}
```

- [ ] **Step 4: Extend the player contract.** In `RecitationPlayer.kt`:
  - Add to `PlaybackState`, after `surahDurationMs`: `/** True while the phone's voice reads the translation of [ayah] (read-aloud spec §2). */ val speaking: Boolean = false,`
  - In the `expect class`, replace `load` and add `setSpeech`:

```kotlin
    /**
     * Builds the queue for [surah] from the reciter's downloaded `.taqa` — one item per ayah,
     * [Reciter.gapMs] of silence between them and, when [speech] is given, a breath and the
     * translation after every ayah it has text for — and starts playing at [startAyah].
     *
     * Does nothing if the surah is not on disk or its container cannot be read; the caller offers
     * the download, and a player that threw would make every call site handle a case the UI has
     * already handled.
     */
    suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText, speech: SpokenTranslation? = null)

    /**
     * Read-aloud turned on or off, or another translation chosen, while a surah is loaded
     * (read-aloud spec §2): the queue is rebuilt around the ayah being heard — inside an ayah it
     * carries on where it is; in a translation or a silence it moves to the next ayah.
     */
    fun setSpeech(speech: SpokenTranslation?)
```

  - In `RecitationPlayer.android.kt` and `RecitationPlayer.ios.kt`, change the `actual suspend fun load(...)` signature to end with `, speech: SpokenTranslation?)` (no default on an `actual`). Leave its body as is for now, and add `actual fun setSpeech(speech: SpokenTranslation?) = Unit`.
  - In `iosApp/iosApp/iOSApp.swift`'s harness `case "load":`, add `speech: nil,` after the `text:` argument of `player.load(...)`.

- [ ] **Step 5: The setting.**
  - `RecitationLibrary.kt`: add to `RecitationSettings` `/** Read-aloud spec §1: read the translation after each ayah. Off by default. */ val readAloud: Boolean = false,`.
  - `SettingsKeys.kt`, beside the other recitation keys: `/** Read-aloud spec §1. */ val RECITATION_READ_ALOUD = booleanPreferencesKey("recitation_read_aloud")`.
  - `SettingsRepository.kt`: in `recitationSettings` add `readAloud = p[SettingsKeys.RECITATION_READ_ALOUD] ?: false,`, and add below `setRecitationMobileData`:

```kotlin
    /** Read-aloud's switch (read-aloud spec §6), from the reading sheet or Settings › Recitation. */
    suspend fun setRecitationReadAloud(value: Boolean) {
        store.edit { it[SettingsKeys.RECITATION_READ_ALOUD] = value }
    }
```

- [ ] **Step 6: The ports.** In `RecitationPorts.kt`:
  - Add the imports `world.taqwa.app.quran.ReadingSettings`, `world.taqwa.app.recitation.SpeechVoices`, `world.taqwa.app.recitation.SpokenTranslation`, `world.taqwa.app.recitation.VoiceStatus`, `kotlinx.coroutines.flow.flowOf`, `kotlinx.coroutines.flow.map` and `kotlinx.coroutines.flow.distinctUntilChanged`.
  - In `PlayerPort`, replace `load` with `suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText, speech: SpokenTranslation?)` and add `fun setSpeech(speech: SpokenTranslation?)`.
  - In `RecitationSettingsPort`, add `suspend fun setReadAloud(value: Boolean)`.
  - In `asRecitationPort()`, add `override suspend fun setReadAloud(value: Boolean) = setRecitationReadAloud(value)`.
  - In `RecitationPlayer.asPort()`, replace `load` with `override suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText, speech: SpokenTranslation?) = this@asPort.load(reciter, surah, startAyah, text, speech)` and add `override fun setSpeech(speech: SpokenTranslation?) = this@asPort.setSpeech(speech)`.
  - Append:

```kotlin
/**
 * What read-aloud asks outside the recitation: which translation the reader shows, and what the
 * phone's voices can do (read-aloud spec §4, §5.4).
 */
interface ReadAloudPort {
    /** `ReadingSettings.translationId` under UI language [languageTag]. */
    fun translationId(languageTag: String): Flow<String>
    suspend fun status(language: String): VoiceStatus
    fun installVoice(engine: String)
}

/** No voices at all: the switch never shows. The default, so tests and previews need nothing. */
object NoReadAloud : ReadAloudPort {
    override fun translationId(languageTag: String): Flow<String> = flowOf(ReadingSettings.NO_TRANSLATION)
    override suspend fun status(language: String): VoiceStatus = VoiceStatus.Unsupported
    override fun installVoice(engine: String) = Unit
}

fun readAloudPort(settings: SettingsRepository, voices: SpeechVoices): ReadAloudPort = object : ReadAloudPort {
    override fun translationId(languageTag: String): Flow<String> =
        settings.readingSettings(languageTag).map { it.translationId }.distinctUntilChanged()
    override suspend fun status(language: String): VoiceStatus = voices.status(language)
    override fun installVoice(engine: String) = voices.installVoice(engine)
}
```

- [ ] **Step 7: The state.** In `RecitationState.kt`:
  - Add `import world.taqwa.app.quran.TextKind`.
  - Add to `BarState`, after `incoming`: `/** Set while the phone's voice reads this ayah's translation (read-aloud spec §6): the bar's caption says so. */ val readingAloud: TextKind? = null,`
  - Add to `RecitationState` (its last field): `/** Read-aloud's switch, or null where it must not show (read-aloud spec §4, §6). */ val readAloud: ReadAloudState? = null,`
  - Add the class:

```kotlin
/**
 * Read-aloud's switch as the reading sheet and Settings › Recitation draw it (read-aloud spec §6).
 * [missingEngine] is Android's engine whose free voice is a download away; null when the voice is
 * on the phone.
 */
data class ReadAloudState(
    val enabled: Boolean,
    val translationName: String,
    val language: String,
    val kind: TextKind,
    val missingEngine: String? = null,
)
```

- [ ] **Step 8: Write the failing controller tests.** In `RecitationControllerTest.kt`:
  - Add the imports `world.taqwa.app.quran.TextKind`, `world.taqwa.app.quran.TranslationInfo`, `world.taqwa.app.recitation.SpeechVoice`, `world.taqwa.app.recitation.SpokenTranslation` and `world.taqwa.app.recitation.VoiceStatus`.
  - In `FakePlayer`:
    - Change `load` to take `speech: SpokenTranslation?` and record it: `speeches += speech` beside `loads += …`.
    - Add `val speeches = mutableListOf<SpokenTranslation?>()` and `val speechUpdates = mutableListOf<SpokenTranslation?>()`.
    - Add `override fun setSpeech(speech: SpokenTranslation?) { speechUpdates += speech }`.
  - In `FakeSettings`, add `override suspend fun setReadAloud(value: Boolean) { stored.value = stored.value.copy(readAloud = value) }`.
  - Extend the `controller(...)` helper with the parameters `readAloud: ReadAloudPort = NoReadAloud, quran: world.taqwa.app.quran.QuranSource = FakeQuranSource()`. Pass `quran = quran` and `readAloud = readAloud` to the constructor.
  - Append:

```kotlin
    private class FakeReadAloud(
        val translation: MutableStateFlow<String> = MutableStateFlow("en.sahih"),
        val statuses: MutableMap<String, VoiceStatus> = mutableMapOf(
            "en" to VoiceStatus.Ready(SpeechVoice("com.google.android.tts", "en-us-x-sfg-local")),
        ),
    ) : ReadAloudPort {
        val asked = mutableListOf<String>()
        val installs = mutableListOf<String>()
        override fun translationId(languageTag: String): Flow<String> = translation
        override suspend fun status(language: String): VoiceStatus {
            asked += language
            return statuses[language] ?: VoiceStatus.Unsupported
        }
        override fun installVoice(engine: String) {
            installs += engine
        }
    }

    private val readingQuran = FakeQuranSource(
        translationsList = listOf(
            TranslationInfo("en.sahih", "en", "Saheeh International", "Saheeh International", "licence", "url", TextKind.TRANSLATION),
            TranslationInfo("ar.muyassar", "ar", "التفسير الميسر", "مجمع الملك فهد", "licence", "url", TextKind.TAFSIR),
            TranslationInfo("ur.junagarhi", "ur", "ترجمہ محمد جوناگڑھی", "محمد جوناگڑھی", "licence", "url", TextKind.TRANSLATION),
        ),
        translationTextsById = mapOf(
            "en.sahih" to mapOf(
                1 to mapOf(
                    1 to "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
                    2 to "[All] praise is [due] to Allah, Lord of the worlds -",
                ),
            ),
        ),
    )

    @Test
    fun `read aloud off loads no speech`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        val c = controller(harness, backgroundScope, readAloud = FakeReadAloud(), quran = readingQuran)
        c.setLanguageTag("en")
        c.requestPlay(1, 1)
        assertEquals(listOf<SpokenTranslation?>(null), harness.player.speeches)
    }

    @Test
    fun `read aloud on with a ready voice loads the surah's prepared translation`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        harness.settings.stored.value = harness.settings.stored.value.copy(readAloud = true)
        val c = controller(harness, backgroundScope, readAloud = FakeReadAloud(), quran = readingQuran)
        c.setLanguageTag("en")
        c.requestPlay(1, 1)
        val speech = assertNotNull(harness.player.speeches.single())
        assertEquals("en.sahih", speech.translationId)
        assertEquals(TextKind.TRANSLATION, speech.kind)
        assertEquals("All praise is due to Allah, Lord of the worlds", speech.texts[2])
        assertEquals(SpeechVoice("com.google.android.tts", "en-us-x-sfg-local"), speech.voice)
    }

    @Test
    fun `the switch shows only where the phone can read the language`() = runTest(UnconfinedTestDispatcher()) {
        val readAloud = FakeReadAloud()
        val c = controller(Harness(), backgroundScope, readAloud = readAloud, quran = readingQuran)
        assertNull(c.state.value.readAloud)
        c.setLanguageTag("en")
        c.refreshVoices()
        val shown = assertNotNull(c.state.value.readAloud)
        assertFalse(shown.enabled)
        assertEquals("Saheeh International", shown.translationName)
        assertEquals("en", shown.language)
        readAloud.translation.value = "ur.junagarhi"
        c.refreshVoices()
        assertNull(c.state.value.readAloud)
    }

    @Test
    fun `translation off hides the switch`() = runTest(UnconfinedTestDispatcher()) {
        val readAloud = FakeReadAloud()
        val c = controller(Harness(), backgroundScope, readAloud = readAloud, quran = readingQuran)
        c.setLanguageTag("en")
        c.refreshVoices()
        assertNotNull(c.state.value.readAloud)
        readAloud.translation.value = world.taqwa.app.quran.ReadingSettings.NO_TRANSLATION
        assertNull(c.state.value.readAloud)
    }

    @Test
    fun `a missing voice shows the switch with its installer and loads nothing`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        harness.settings.stored.value = harness.settings.stored.value.copy(readAloud = true)
        val readAloud = FakeReadAloud(statuses = mutableMapOf("en" to VoiceStatus.Missing("com.google.android.tts")))
        val c = controller(harness, backgroundScope, readAloud = readAloud, quran = readingQuran)
        c.setLanguageTag("en")
        c.refreshVoices()
        assertEquals("com.google.android.tts", c.state.value.readAloud?.missingEngine)
        c.getVoice()
        assertEquals(listOf("com.google.android.tts"), readAloud.installs)
        c.requestPlay(1, 1)
        assertEquals(listOf<SpokenTranslation?>(null), harness.player.speeches)
    }

    @Test
    fun `turning read aloud on while a surah plays rebuilds it with speech and off again without`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        val c = controller(harness, backgroundScope, readAloud = FakeReadAloud(), quran = readingQuran)
        c.setLanguageTag("en")
        c.requestPlay(1, 1)
        c.setReadAloud(true)
        assertEquals("en.sahih", harness.player.speechUpdates.single()?.translationId)
        c.setReadAloud(false)
        assertEquals(2, harness.player.speechUpdates.size)
        assertNull(harness.player.speechUpdates.last())
    }

    @Test
    fun `the bar says the translation is being read while the voice speaks`() = runTest(UnconfinedTestDispatcher()) {
        val harness = Harness()
        harness.library.put("ar.alafasy", setOf(1))
        harness.settings.stored.value = harness.settings.stored.value.copy(readAloud = true)
        val c = controller(harness, backgroundScope, readAloud = FakeReadAloud(), quran = readingQuran)
        c.setLanguageTag("en")
        c.requestPlay(1, 1)
        assertNull(c.state.value.bar?.readingAloud)
        harness.player.emit(harness.player.state.value.copy(speaking = true))
        assertEquals(TextKind.TRANSLATION, c.state.value.bar?.readingAloud)
    }
```

  - Check `FakeQuranSource`'s constructor parameter names (`translationsList`, `translationTextsById`) against the file, and adjust the test if they differ.

- [ ] **Step 9: Run the tests and see them fail.** Run `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.feature.recitation.RecitationControllerTest' --tests 'world.taqwa.app.recitation.VoicePickTest'`. Expected: FAIL (the controller lacks the new API).

- [ ] **Step 10: The controller.** In `RecitationController.kt`:
  - Add the imports `kotlinx.coroutines.CancellationException` (from `kotlin.coroutines.cancellation`), `world.taqwa.app.quran.ReadingSettings`, `world.taqwa.app.quran.TextKind`, `world.taqwa.app.quran.TranslationInfo`, `world.taqwa.app.recitation.SpeechText`, `world.taqwa.app.recitation.SpeechVoice`, `world.taqwa.app.recitation.SpokenTranslation` and `world.taqwa.app.recitation.VoiceStatus`.
  - Add the constructor parameter, **last**, after `refreshCatalogue`:

```kotlin
    /** Read-aloud spec §5.4: the reading translation and the phone's voices. None by default. */
    private val readAloud: ReadAloudPort = NoReadAloud,
```

  - Add fields after `pausedForPreview`:

```kotlin
    /** The UI language, for the reading translation's default; set by the composition. */
    private val languageTag = MutableStateFlow<String?>(null)

    /** The bundled translations, read once. */
    private val translationInfos = MutableStateFlow<List<TranslationInfo>>(emptyList())

    /** What the phone said for each language this process has asked about (read-aloud spec §4). */
    private val voices = MutableStateFlow<Map<String, VoiceStatus>>(emptyMap())

    /** The kind the loaded surah's translation is, for the bar's caption. */
    private var speechKind: TextKind? = null

    /** What the loaded surah was built with, so a rebuild only follows a real change. */
    private var appliedSpeech: SpeechKey? = null

    private data class SpeechKey(val surah: Int, val translationId: String?, val voice: SpeechVoice?)

    /**
     * The translation the reader shows, as the reader resolves it: none for Translation off, the
     * bundled default for an id that is not bundled, and nothing for the transliteration.
     */
    private val readingTranslation: StateFlow<TranslationInfo?> =
        combine(
            languageTag.filterNotNull().flatMapLatest { readAloud.translationId(it) },
            translationInfos,
        ) { id, infos ->
            if (id == ReadingSettings.NO_TRANSLATION) {
                null
            } else {
                (infos.firstOrNull { it.id == id } ?: infos.firstOrNull { it.id == FALLBACK_TRANSLATION })
                    ?.takeIf { it.kind != TextKind.TRANSLITERATION }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The switch, or null where it must not show (read-aloud spec §4). */
    private val readAloudState: StateFlow<ReadAloudState?> =
        combine(settings.settings.map { it.readAloud }.distinctUntilChanged(), readingTranslation, voices) { enabled, info, statuses ->
            if (info == null) return@combine null
            when (val status = statuses[info.language]) {
                is VoiceStatus.Ready -> ReadAloudState(enabled, info.name, info.language, info.kind)
                is VoiceStatus.Missing -> ReadAloudState(enabled, info.name, info.language, info.kind, missingEngine = status.engine)
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)
```

    These fields must be declared **before** `val state` (Kotlin initialises in order). Put them right after the `allDownloaded` property.
  - Change `val state` to combine four flows. Wrap the existing three-argument `combine(...) { catalogue, playing, surface -> assemble(catalogue, playing, surface) }` so that the outer call is `combine(<catalogue combine>, <playing combine>, <surface combine>, readAloudState) { catalogue, playing, surface, spoken -> assemble(catalogue, playing, surface, spoken) }`.
  - Change `assemble`'s signature to `private fun assemble(catalogue: Catalogue, playing: Playing, surface: Surface, spoken: ReadAloudState?): RecitationState`. Inside it:
    - Replace `val bar = barOf(playing, catalogue)` with `val bar = barOf(playing, catalogue)?.let { if (playing.playback.speaking) it.copy(readingAloud = speechKind) else it }`.
    - Add `readAloud = spoken,` to the returned `RecitationState`.
  - In `init`, append:

```kotlin
        scope.launch { translationInfos.value = runCatching { quran.translations() }.getOrDefault(emptyList()) }
        // Read-aloud changes while a surah is loaded (read-aloud spec §2): the switch, the
        // translation, or its voice arriving — rebuild around the ayah being heard.
        scope.launch {
            combine(settings.settings.map { it.readAloud }.distinctUntilChanged(), readingTranslation, voices) { on, info, statuses ->
                Triple(on, info?.id, info?.language?.let { statuses[it] })
            }.distinctUntilChanged().collect { applyReadAloud() }
        }
        // A new reading language while the switch is in use: ask the phone about it at once, so
        // the switch does not vanish for a language it can read.
        scope.launch {
            readingTranslation.filterNotNull().map { it.language }.distinctUntilChanged().collect { language ->
                if (voices.value.isNotEmpty() && language !in voices.value) checkVoice(language)
            }
        }
```

  - Replace `start` with:

```kotlin
    private suspend fun start(surah: Int, ayah: Int, voice: Reciter? = null) {
        val playWith = voice ?: reciter.value ?: return
        loading.value = true
        try {
            val speech = spokenFor(surah)
            speechKind = speech?.kind
            appliedSpeech = keyOf(surah, speech)
            player.load(playWith, surah, ayah, nowPlaying(playWith, surah), speech)
            player.play()
        } finally {
            loading.value = false
        }
    }

    /**
     * The surah's translation as the voice will read it (read-aloud spec §5.4), or null: the
     * switch off, Translation off, no voice on the phone, or nothing to read.
     */
    private suspend fun spokenFor(surah: Int): SpokenTranslation? {
        if (!settings.settings.first().readAloud) return null
        val info = readingTranslation.value ?: return null
        val status = voices.value[info.language] ?: checkVoice(info.language)
        val voice = (status as? VoiceStatus.Ready)?.voice ?: return null
        val texts = runCatching { quran.translationTexts(info.id, surah) }.getOrNull() ?: return null
        val prepared = SpeechText.prepare(info.kind, info.language, surah, texts)
        if (prepared.isEmpty()) return null
        return SpokenTranslation(info.id, info.kind, info.language, voice, prepared)
    }

    private fun keyOf(surah: Int, speech: SpokenTranslation?) = SpeechKey(surah, speech?.translationId, speech?.voice)

    /** Rebuilds the loaded surah's speech when what it should be has changed. */
    private suspend fun applyReadAloud() {
        val surah = player.state.value.surah ?: return
        if (loading.value) return
        val speech = spokenFor(surah)
        // The surah may have changed while the texts were read; that load built its own speech.
        if (player.state.value.surah != surah) return
        val key = keyOf(surah, speech)
        if (key == appliedSpeech) return
        appliedSpeech = key
        speechKind = speech?.kind
        player.setSpeech(speech)
    }

    /** Asks the phone about [language] and remembers the answer. A failure is "cannot read it". */
    private suspend fun checkVoice(language: String): VoiceStatus {
        val status = try {
            readAloud.status(language)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            VoiceStatus.Unsupported
        }
        voices.value = voices.value + (language to status)
        return status
    }
```

  - Add the public entry points (near `setArabicUi`):

```kotlin
    /** The UI language, for the reading translation's default (read-aloud spec §5.4). */
    fun setLanguageTag(tag: String) {
        languageTag.value = tag
    }

    /** The reading sheet, Settings › Recitation or the reader opened: ask the phone again. */
    fun refreshVoices() {
        val info = readingTranslation.value ?: return
        scope.launch { checkVoice(info.language) }
    }

    /** The app came back to the front — perhaps from installing a voice. Only once asked before. */
    fun onForeground() {
        if (voices.value.isNotEmpty()) refreshVoices()
    }

    /** Read-aloud's switch (read-aloud spec §6). */
    fun setReadAloud(on: Boolean) {
        scope.launch { settings.setReadAloud(on) }
    }

    /** "Get the voice": the engine's own installer, for the language the reader shows. */
    fun getVoice() {
        val info = readingTranslation.value ?: return
        val engine = (voices.value[info.language] as? VoiceStatus.Missing)?.engine ?: return
        readAloud.installVoice(engine)
    }
```

  - At file level beside `LAST_SURAH`: `/** What the reader loads when the stored translation id is not bundled. */ private const val FALLBACK_TRANSLATION = "en.sahih"`.
  - `refreshVoices()` in the test is followed by an immediate assertion. With the unconfined test dispatcher, `scope.launch` runs eagerly, so this works.

- [ ] **Step 11: Wire the app graph.** In `AppContainer.kt`:
  - Add `import world.taqwa.app.recitation.createSpeechVoices`, `import world.taqwa.app.recitation.SpeechVoices` and `import world.taqwa.app.feature.recitation.readAloudPort`.
  - Add `val speechVoices: SpeechVoices by lazy { createSpeechVoices() }` before `recitationController`.
  - Pass `readAloud = readAloudPort(settingsRepository, speechVoices),` as the last constructor argument.

- [ ] **Step 12: Run everything.** Run `./gradlew :shared:testAndroidHostTest && ./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`. Expected: all tests PASS (the controller suite included) and both platforms compile.

- [ ] **Step 13: Commit.**

```bash
git add -A shared/src iosApp/iosApp/iOSApp.swift
git commit -m "recitation: read-aloud's setting, contract and controller decisions"
```

---

### Task 4: The switch, the caption and the mark (UI and strings)

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReadingSheet.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/ReaderScreen.kt`, `MushafScreen.kt` (pass through)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/QuranRecitation.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/RecitationSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/PlayerBar.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/AyahCard.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/Glyphs.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/QuranRoutes.kt`, `RecitationRoutes.kt`, `App.kt`
- Modify: all 8 `shared/src/commonMain/composeResources/values*/strings.xml`

**Interfaces:**
- Consumes: `ReadAloudState`, `RecitationState.readAloud`, `BarState.readingAloud`, `RecitationController.setLanguageTag / refreshVoices / onForeground / setReadAloud / getVoice` (Task 3).

- [ ] **Step 1: Strings.** Add these to every `strings.xml`, beside the other `recitation_*` and `quran_sheet_*` keys. `values-in` gets exactly the same lines as `values-id`.

| key | en | ar | fr |
|---|---|---|---|
| recitation_read_aloud_heading | After each ayah | بعد كل آية | Après chaque verset |
| recitation_read_aloud_translation | Read the translation aloud | قراءة الترجمة صوتيًا | Lire la traduction à voix haute |
| recitation_read_aloud_tafsir | Read the tafsir aloud | قراءة التفسير صوتيًا | Lire le tafsir à voix haute |
| recitation_read_aloud_detail | %1$s, in %2$s. A voice on this phone reads it, so it works offline. | صوتٌ على هذا الهاتف يقرأ %1$s باللغة %2$s، فيعمل دون إنترنت. | %1$s, en %2$s. Une voix de ce téléphone s’en charge, même hors ligne. |
| recitation_read_aloud_missing | Your phone needs its free %1$s voice, a small one-time download. | يحتاج هاتفك إلى صوت اللغة %1$s المجاني، وهو تنزيل صغير لمرة واحدة. | Votre téléphone a besoin d’une voix gratuite en %1$s, un petit téléchargement unique. |
| recitation_read_aloud_get_voice | Get the voice | تنزيل الصوت | Obtenir la voix |
| quran_sheet_read_aloud | Read it aloud after each ayah | القراءة الصوتية بعد كل آية | Lecture à voix haute après chaque verset |
| quran_sheet_read_aloud_missing | Needs your phone’s free %1$s voice. | يلزم صوت اللغة %1$s المجاني على هاتفك. | Nécessite une voix gratuite en %1$s. |
| recitation_bar_translation | Translation | الترجمة | Traduction |
| recitation_bar_tafsir | Tafsir | التفسير | Tafsir |
| quran_a11y_read_aloud | Being read aloud | تُقرأ صوتيًا الآن | Lecture à voix haute en cours |

| key | tr | id | ur | bn |
|---|---|---|---|---|
| recitation_read_aloud_heading | Her ayetten sonra | Setelah setiap ayat | ہر آیت کے بعد | প্রতিটি আয়াতের পরে |
| recitation_read_aloud_translation | Meali sesli oku | Bacakan terjemahan | ترجمہ سنائیں | অনুবাদ পড়ে শোনান |
| recitation_read_aloud_tafsir | Tefsiri sesli oku | Bacakan tafsir | تفسیر سنائیں | তাফসির পড়ে শোনান |
| recitation_read_aloud_detail | %1$s, %2$s. Bu telefondaki bir ses okur; internet gerekmez. | %1$s, dalam bahasa %2$s. Dibacakan oleh suara di ponsel ini, jadi tetap berfungsi tanpa internet. | %1$s، %2$s میں۔ اس فون کی آواز پڑھتی ہے، اس لیے انٹرنیٹ کے بغیر چلتا ہے۔ | %1$s, %2$s ভাষায়। এই ফোনের একটি কণ্ঠ পড়ে শোনায়, তাই ইন্টারনেট ছাড়াই চলে। |
| recitation_read_aloud_missing | Telefonunuzun ücretsiz %1$s sesine ihtiyacı var; küçük, tek seferlik bir indirme. | Ponsel Anda memerlukan suara %1$s gratis, unduhan kecil sekali saja. | آپ کے فون کو %1$s کی مفت آواز درکار ہے، ایک بار کی چھوٹی سی ڈاؤن لوڈ۔ | আপনার ফোনে বিনামূল্যের %1$s কণ্ঠ দরকার, একবারের ছোট একটি ডাউনলোড। |
| recitation_read_aloud_get_voice | Sesi indir | Unduh suara | آواز حاصل کریں | কণ্ঠ নামান |
| quran_sheet_read_aloud | Her ayetten sonra sesli oku | Bacakan setelah setiap ayat | ہر آیت کے بعد سنائیں | প্রতিটি আয়াতের পরে পড়ে শোনান |
| quran_sheet_read_aloud_missing | Telefonunuzun ücretsiz %1$s sesi gerekir. | Memerlukan suara %1$s gratis di ponsel Anda. | آپ کے فون پر %1$s کی مفت آواز درکار ہے۔ | আপনার ফোনে বিনামূল্যের %1$s কণ্ঠ দরকার। |
| recitation_bar_translation | Meal | Terjemahan | ترجمہ | অনুবাদ |
| recitation_bar_tafsir | Tefsir | Tafsir | تفسیر | তাফসির |
| quran_a11y_read_aloud | Sesli okunuyor | Sedang dibacakan | سنایا جا رہا ہے | পড়ে শোনানো হচ্ছে |

Run `python3 tools/sync-indonesian.py && python3 tools/i18n-check.py && ./scripts/check-strings.sh`. Expected: no errors. If `sync-indonesian.py` does not exist, copy the lines into `values-in` by hand.

- [ ] **Step 2: The speaker glyph.** Append to `Glyphs.kt`:

```kotlin
/** A small loudspeaker with one wave: read-aloud's "being read" mark (read-aloud spec §6). */
internal fun DrawScope.drawSpeaker(tint: Color) {
    val u = size.width / 16f
    val body = Path().apply {
        moveTo(2.5f * u, 6.2f * u)
        lineTo(5.2f * u, 6.2f * u)
        lineTo(8.6f * u, 3.4f * u)
        lineTo(8.6f * u, 12.6f * u)
        lineTo(5.2f * u, 9.8f * u)
        lineTo(2.5f * u, 9.8f * u)
        close()
    }
    drawPath(body, tint, style = Fill)
    drawArc(
        color = tint,
        startAngle = -50f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(7.2f * u, 4.6f * u),
        size = androidx.compose.ui.geometry.Size(6.8f * u, 6.8f * u),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.1f, cap = StrokeCap.Round),
    )
}
```

- [ ] **Step 3: `QuranRecitation`.** Add these fields, defaulted, before `jumpToken`:

```kotlin
    /** Read-aloud's switch for the reading sheet, or null where it must not show (read-aloud spec §6). */
    val readAloud: ReadAloudState? = null,
    /** True while the voice reads the translation of [playing]'s ayah: its card shows the mark. */
    val speaking: Boolean = false,
    val onReadAloud: (Boolean) -> Unit = {},
    val onGetVoice: () -> Unit = {},
    /** The reading sheet opened: the phone is asked about its voices again. */
    val onSheetOpened: () -> Unit = {},
```

- [ ] **Step 4: The reading sheet.** In `ReadingSheet.kt`:
  - Add the parameters, defaulted, after `mushafMode`: `readAloud: ReadAloudState? = null, onReadAloud: (Boolean) -> Unit = {}, onGetVoice: () -> Unit = {},`.
  - Right after the `Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { … translation … }` block and before the reading-mode column, add:

```kotlin
        // Read-aloud (spec §6), under the translation it reads. Hidden with Translation off or
        // where the phone has no voice for the language: the controller hands in null then.
        if (readAloud != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TaqwaRow(
                    label = stringResource(Res.string.quran_sheet_read_aloud),
                    trailing = { TaqwaToggle(checked = readAloud.enabled, onCheckedChange = onReadAloud) },
                )
                val missing = readAloud.missingEngine
                if (missing != null) {
                    Text(
                        stringResource(Res.string.quran_sheet_read_aloud_missing, format.languageName(readAloud.language)),
                        style = TaqwaText.caption,
                        color = colors.textSecondary,
                    )
                    Text(
                        stringResource(Res.string.recitation_read_aloud_get_voice),
                        style = TaqwaText.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onGetVoice,
                            )
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
```

  - Add any missing imports: `Res.string.quran_sheet_read_aloud`, `quran_sheet_read_aloud_missing`, `recitation_read_aloud_get_voice`, `FontWeight`, `clickable`, `MutableInteractionSource`, `ReadAloudState`, `TaqwaToggle` (`world.taqwa.app.feature.settings.TaqwaToggle`, already imported for transliteration if present).

- [ ] **Step 5: The reader and the Mushaf pass it through.**
  - In `ReaderScreen.kt` and `MushafScreen.kt`, add these arguments to the `ReadingSheet(...)` call: `readAloud = recitation.readAloud, onReadAloud = recitation.onReadAloud, onGetVoice = recitation.onGetVoice,`.
  - Where `showSheet` becomes true, add `LaunchedEffect(showSheet) { if (showSheet) recitation.onSheetOpened() }` inside the screen.
  - In `ReaderScreen.kt`'s `AyahCard(...)` call, add `speaking = playingAyah == ayah.number && recitation.speaking,`.

- [ ] **Step 6: The card's mark.** In `AyahCard.kt`:
  - Add the parameter `/** True while the phone's voice reads this card's translation (read-aloud spec §6). */ speaking: Boolean = false,` after `playing`.
  - Replace the translation `Text(...)` inside `if (translation != null)` with:

```kotlin
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            if (speaking) {
                                val readingLabel = stringResource(Res.string.quran_a11y_read_aloud)
                                Canvas(
                                    Modifier
                                        .padding(top = 3.dp, end = 6.dp)
                                        .size(14.dp)
                                        .semantics { contentDescription = readingLabel },
                                ) { drawSpeaker(colors.accent) }
                            }
                            Text(
                                translation,
                                style = TaqwaText.caption,
                                color = colors.textSecondary,
                                lineHeight = 21.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.weight(1f),
                            )
                        }
```

  - Import `Row`, `drawSpeaker` and `Res.string.quran_a11y_read_aloud`.

- [ ] **Step 7: The bar's caption.** In `PlayerBar.kt`, replace the `Text(stringResource(Res.string.quran_ayah_n, …))` caption with:

```kotlin
                    val ayahCaption = stringResource(Res.string.quran_ayah_n, format.localizedDigits(bar.ayah))
                    val reading = bar.readingAloud?.let { kind ->
                        stringResource(if (kind == TextKind.TAFSIR) Res.string.recitation_bar_tafsir else Res.string.recitation_bar_translation)
                    }
                    Text(
                        // "Ayah 2 · Translation" while the voice reads (read-aloud spec §6): the
                        // word in the accent, so the change reads at a glance.
                        buildAnnotatedString {
                            append(ayahCaption)
                            if (reading != null) {
                                append(" · ")
                                withStyle(SpanStyle(color = colors.accent)) { append(reading) }
                            }
                        },
                        style = TaqwaText.caption.copy(fontSize = 12.sp),
                        color = if (bar.buffering) colors.textTertiary else colors.textSecondary,
                        maxLines = 1,
                    )
```

  - Import `world.taqwa.app.quran.TextKind` and the two string resources.

- [ ] **Step 8: Settings › Recitation.** In `RecitationSettingsScreen.kt`:
  - Add the parameters `onSetReadAloud: (Boolean) -> Unit = {}, onGetVoice: () -> Unit = {},` at the end.
  - After the first `SettingsCard { … }` and before the downloads section, insert:

```kotlin
        // Read-aloud (spec §6): its own card, shown only where the phone can read the language.
        val readAloud = state.readAloud
        if (readAloud != null) {
            val format = LocalPlatformFormat.current
            Spacer(Modifier.height(28.dp))
            SectionLabel(stringResource(Res.string.recitation_read_aloud_heading))
            SettingsCard {
                TaqwaRow(
                    stringResource(
                        if (readAloud.kind == TextKind.TAFSIR) Res.string.recitation_read_aloud_tafsir
                        else Res.string.recitation_read_aloud_translation,
                    ),
                    onClick = { onSetReadAloud(!readAloud.enabled) },
                    ripple = false,
                    trailing = { TaqwaToggle(readAloud.enabled, onSetReadAloud) },
                )
                val language = format.languageName(readAloud.language)
                Text(
                    if (readAloud.missingEngine != null) {
                        stringResource(Res.string.recitation_read_aloud_missing, language)
                    } else {
                        stringResource(Res.string.recitation_read_aloud_detail, readAloud.translationName, language)
                    },
                    style = TaqwaText.caption,
                    color = LocalTaqwaColors.current.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
                if (readAloud.missingEngine != null) {
                    CardDivider()
                    QuietRow(stringResource(Res.string.recitation_read_aloud_get_voice), LocalTaqwaColors.current.accent, onGetVoice)
                }
            }
        }
```

  - Import `TextKind` and the new strings.

- [ ] **Step 9: Routes and the app.**
  - `RecitationRoutes.kt`: pass `onSetReadAloud = recitation::setReadAloud, onGetVoice = recitation::getVoice,`.
  - `QuranRoutes.kt`, in both `QuranRecitation(...)` constructions, add `readAloud = recitationState.readAloud, speaking = bar?.readingAloud != null, onReadAloud = recitation::setReadAloud, onGetVoice = recitation::getVoice, onSheetOpened = recitation::refreshVoices,`. Also, at the start of `ReaderRoute` and `MushafRoute`, add `LaunchedEffect(Unit) { recitation.refreshVoices() }`.
  - `App.kt`, beside `LaunchedEffect(arabicUi) { recitation.setArabicUi(arabicUi) }`, add:

```kotlin
    // Read-aloud (spec §5.4) needs the reading translation's language default.
    LaunchedEffect(platformFormat) { recitation.setLanguageTag(platformFormat.languageTag()) }
    // Back from the phone's voice installer, perhaps: ask the phone again.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == androidx.lifecycle.Lifecycle.State.RESUMED) recitation.onForeground()
        }
    }
```

  - Check whether `App.kt` already imports `LocalLifecycleOwner` (see memory: lifecycle-runtime-compose 2.9.6 is on the classpath). Use the existing import path if there is one.

- [ ] **Step 10: Build and test.** Run `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64`. Expected: PASS. Then `./scripts/check-strings.sh` and `python3 tools/i18n-check.py`. Expected: clean.

- [ ] **Step 11: Commit.**

```bash
git add -A shared/src/commonMain
git commit -m "quran: read-aloud's switch in the reading sheet and Settings, the bar's caption, the card's mark"
```

---

### Task 5: Android — asking the engines for a voice

**Files:**
- Modify: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/SpeechVoices.android.kt` (replace the stub)
- Modify: `androidApp/src/main/AndroidManifest.xml` (`<queries>`)
- Modify: `androidApp/src/debug/kotlin/world/taqwa/app/debug/RecitationHarnessReceiver.kt` (`voices` command)

**Interfaces:**
- Consumes: `VoicePick`, `VoiceCandidate`, `VoiceStatus`, `SpeechVoice` (Task 3).
- Produces: `actual fun createSpeechVoices()` returning `AndroidSpeechVoices`. `SpeechVoice.engine` is the engine package; `SpeechVoice.id` is `android.speech.tts.Voice.name`.

- [ ] **Step 1: The manifest.** In `androidApp/src/main/AndroidManifest.xml`, directly inside `<manifest>` after the permissions, add:

```xml
    <!-- Read-aloud (spec §4): from Android 11 an app sees other text-to-speech engines — Google's,
         Samsung's — only when it says it looks for them. No permission; nothing is sent anywhere. -->
    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>
```

- [ ] **Step 2: Replace `SpeechVoices.android.kt`.**

```kotlin
package world.taqwa.app.recitation

import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import world.taqwa.app.settings.appContext
import java.util.Locale
import kotlin.coroutines.resume

actual fun createSpeechVoices(): SpeechVoices = AndroidSpeechVoices()

/**
 * The phone's text-to-speech engines, asked per language (read-aloud spec §4). Each engine is
 * bound only for the question and released at once: holding Google's engine for the life of the
 * process would keep its own process alive for nothing.
 *
 * The user's default engine is asked first, then Google's, then any other. The first with an
 * offline voice installed answers Ready; otherwise the first that offers the language as a
 * download answers Missing; otherwise the language is Unsupported and the switch stays hidden.
 */
internal class AndroidSpeechVoices : SpeechVoices {

    override suspend fun status(language: String): VoiceStatus = withContext(Dispatchers.Main) {
        var missing: String? = null
        for (engine in engines()) {
            when (val answer = ask(engine, language)) {
                is VoiceStatus.Ready -> return@withContext answer
                is VoiceStatus.Missing -> if (missing == null) missing = answer.engine
                VoiceStatus.Unsupported -> Unit
            }
        }
        missing?.let { VoiceStatus.Missing(it) } ?: VoiceStatus.Unsupported
    }

    override fun installVoice(engine: String) {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .setPackage(engine)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    /** Installed engines, the user's default first and Google's second. */
    private fun engines(): List<String> {
        val installed = appContext.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
        val preferred = Settings.Secure.getString(appContext.contentResolver, DEFAULT_ENGINE)
        return (listOfNotNull(preferred, GOOGLE_ENGINE) + installed).distinct().filter { it in installed }
    }

    private suspend fun ask(engine: String, language: String): VoiceStatus {
        val tts = bind(engine) ?: return VoiceStatus.Unsupported
        return try {
            val candidates = runCatching { tts.voices.orEmpty().map { it.candidate() } }.getOrDefault(emptyList())
            val best = VoicePick.best(language, candidates)
            when {
                best != null -> VoiceStatus.Ready(SpeechVoice(engine, best.id))
                VoicePick.downloadable(language, candidates) -> VoiceStatus.Missing(engine)
                runCatching { tts.isLanguageAvailable(Locale(language)) }.getOrNull() == TextToSpeech.LANG_MISSING_DATA ->
                    VoiceStatus.Missing(engine)
                else -> VoiceStatus.Unsupported
            }
        } finally {
            tts.shutdown()
        }
    }

    /** A bound engine, or null when it would not start within [BIND_TIMEOUT_MS]. Main thread. */
    private suspend fun bind(engine: String): TextToSpeech? = withTimeoutOrNull(BIND_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(appContext, { status ->
                val bound = tts
                if (!continuation.isActive) return@TextToSpeech
                if (status == TextToSpeech.SUCCESS && bound != null) {
                    continuation.resume(bound)
                } else {
                    bound?.shutdown()
                    continuation.resume(null)
                }
            }, engine)
            continuation.invokeOnCancellation { tts?.shutdown() }
        }
    }

    private fun Voice.candidate() = VoiceCandidate(
        id = name,
        language = locale.language,
        country = locale.country,
        offline = !isNetworkConnectionRequired,
        installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty(),
        quality = quality,
    )

    private companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val DEFAULT_ENGINE = "tts_default_synth"
        const val BIND_TIMEOUT_MS = 4_000L
    }
}
```

- [ ] **Step 3: The harness `voices` command.** In `RecitationHarnessReceiver.kt`'s `when (command)`, add:

```kotlin
                    // Read-aloud (spec §4): what the phone's engines answer for every bundled language.
                    "voices" -> listOf("en", "ar", "fr", "tr", "id", "ur", "bn").forEach { language ->
                        Log.i(TAG, "voice $language ${appContainer.speechVoices.status(language)}")
                    }
```

  Add `voices` to the KDoc's list of commands.

- [ ] **Step 4: Build and check on the emulator.**
  - Run `./gradlew :androidApp:assembleDebug`. Expected: BUILD SUCCESSFUL.
  - The controller runs this on a device in Task 8, so there is no unit test here: the logic that can be tested lives in `VoicePick`, which Task 3 covers.

- [ ] **Step 5: Commit.**

```bash
git add shared/src/androidMain/kotlin/world/taqwa/app/recitation/SpeechVoices.android.kt androidApp/src/main/AndroidManifest.xml androidApp/src/debug/kotlin/world/taqwa/app/debug/RecitationHarnessReceiver.kt
git commit -m "android: ask the phone's speech engines for an offline voice"
```

---

### Task 6: Android — the translation in the player

**Files:**
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/SpeechDataSource.kt`
- Modify: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/RecitationService.kt`
- Modify: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/AyahPlayer.kt`
- Modify: `shared/src/androidMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.android.kt`
- Modify: `androidApp/src/debug/kotlin/world/taqwa/app/debug/RecitationHarnessReceiver.kt` (`load --es translation`, `speech`)

**Interfaces:**
- Consumes: `RecitationQueue` (`isAyah`, `ayahIndexAt`, `Speech`), `SurahTimeline.of(queue, ayahMs, speechMs)`, `SpokenTranslation`, `buildSpokenTranslation`.
- Produces:
  - `SPEECH_SCHEME = "speech"` and `speechUri(generation: Long, surah: Int, ayah: Int)`
  - `RecitationService.EXTRA_PHASE` (Int in the session extras): `PHASE_AYAH = 0`, `PHASE_SILENCE = 1`, `PHASE_SPEECH = 2`
  - NOW_PLAYING args `ARG_SPEECH_GENERATION` (Long), `ARG_SPEECH_ENGINE`, `ARG_SPEECH_VOICE`, `ARG_SPEECH_LANGUAGE` (String), `ARG_SPEECH_AYAHS` (IntArray), `ARG_SPEECH_TEXTS` (String[])

- [ ] **Step 1: Create `SpeechDataSource.kt`.**

```kotlin
package world.taqwa.app.recitation

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `speech://<generation>/<surah>/<ayah>` — the translation read after an ayah (read-aloud spec §5.5). */
internal const val SPEECH_SCHEME = "speech"

internal fun speechUri(generation: Long, surah: Int, ayah: Int): String = "$SPEECH_SCHEME://$generation/$surah/$ayah"

/** What the service is to read, sent by the app with the queue it belongs to. */
internal data class SpeechScript(
    val generation: Long,
    val engine: String,
    val voiceId: String,
    val language: String,
    val texts: Map<Int, String>,
)

/**
 * The service's voice (read-aloud spec §5.5): one `TextToSpeech` bound to the engine the app
 * chose, turning a translation into a WAV the player plays like any other item.
 *
 * Called on ExoPlayer's loading thread, which is already reading ahead while the ayah before it
 * plays, so the synthesis is done before the voice is due. It never throws: an engine that fails,
 * a voice that has gone, a timeout — each is a tenth of a second of silence, and the recitation
 * carries on, because an error from a data source would stop the whole surah.
 */
@UnstableApi
internal class Speaker(private val context: Context) {

    @Volatile private var script: SpeechScript? = null
    private var tts: TextToSpeech? = null
    private var ttsEngine: String? = null
    private val lock = Any()
    private val dir: File get() = File(context.cacheDir, "speech")

    /** A new queue's script, or none. Files of older queues go; the engine is released with none. */
    fun setScript(value: SpeechScript?) {
        script = value
        synchronized(lock) {
            val keep = value?.generation?.let { "$it-" }
            dir.listFiles()?.forEach { file ->
                if (file.name != SILENCE_FILE && (keep == null || !file.name.startsWith(keep))) file.delete()
            }
            if (value == null) release()
        }
    }

    /** The WAV for the translation after [ayah] of queue [generation]; silence when there is none. */
    fun render(generation: Long, ayah: Int): File = synchronized(lock) {
        val current = script
        val text = current?.takeIf { it.generation == generation }?.texts?.get(ayah) ?: return silence()
        dir.mkdirs()
        val out = File(dir, "$generation-$ayah.wav")
        if (out.length() > WAV_HEADER_BYTES) return out
        val engine = bind(current.engine) ?: return silence()
        val voice = runCatching { engine.voices?.firstOrNull { it.name == current.voiceId } }.getOrNull() ?: return silence()
        if (engine.voice?.name != voice.name) engine.voice = voice
        val id = "speech-$generation-$ayah"
        val done = CountDownLatch(1)
        var ok = false
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) {
                    ok = true
                    done.countDown()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.countDown()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.countDown()
            }
        })
        val partial = File(dir, "$generation-$ayah.part")
        if (engine.synthesizeToFile(text, Bundle(), partial, id) != TextToSpeech.SUCCESS) return silence()
        val finished = done.await(SYNTHESIS_BASE_MS + text.length * SYNTHESIS_PER_CHAR_MS, TimeUnit.MILLISECONDS)
        if (!finished || !ok || partial.length() <= WAV_HEADER_BYTES || !partial.renameTo(out)) {
            partial.delete()
            return silence()
        }
        // A rolling window: the few translations around the one just made, never a whole surah's.
        dir.listFiles()?.forEach { file ->
            val n = file.name.removePrefix("$generation-").removeSuffix(".wav").toIntOrNull()
            if (n != null && file.name.startsWith("$generation-") && n < ayah - KEEP_BEHIND) file.delete()
        }
        out
    }

    fun release() {
        synchronized(lock) {
            tts?.shutdown()
            tts = null
            ttsEngine = null
        }
    }

    /** The engine the script names, bound once and kept for the queue. Loading thread; waits. */
    private fun bind(engine: String): TextToSpeech? {
        if (tts != null && ttsEngine == engine) return tts
        tts?.shutdown()
        tts = null
        ttsEngine = null
        val ready = CountDownLatch(1)
        var success = false
        val made = TextToSpeech(context, { status ->
            success = status == TextToSpeech.SUCCESS
            ready.countDown()
        }, engine)
        if (!ready.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS) || !success) {
            made.shutdown()
            return null
        }
        tts = made
        ttsEngine = engine
        return made
    }

    /** A tenth of a second of 16 kHz mono silence, written once. */
    private fun silence(): File {
        dir.mkdirs()
        val file = File(dir, SILENCE_FILE)
        if (file.length() > WAV_HEADER_BYTES) return file
        val rate = 16_000
        val samples = rate / 10
        val data = samples * 2
        val header = java.nio.ByteBuffer.allocate(WAV_HEADER_BYTES.toInt()).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
        }.array()
        file.writeBytes(header + ByteArray(data))
        return file
    }

    private companion object {
        const val SILENCE_FILE = "silence.wav"
        const val WAV_HEADER_BYTES = 44L
        const val BIND_TIMEOUT_MS = 5_000L
        const val SYNTHESIS_BASE_MS = 15_000L
        const val SYNTHESIS_PER_CHAR_MS = 40L
        const val KEEP_BEHIND = 3
    }
}

/** Serves `speech://` items from the [Speaker]'s WAVs. */
@UnstableApi
internal class SpeechDataSource(private val speaker: Speaker) : DataSource {

    private val delegate = FileDataSource()
    private var requested: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val generation = uri.authority?.toLongOrNull() ?: -1L
        val ayah = uri.pathSegments.getOrNull(1)?.toIntOrNull() ?: -1
        val file = speaker.render(generation, ayah)
        requested = uri
        return delegate.open(dataSpec.buildUpon().setUri(Uri.fromFile(file)).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    override fun getUri(): Uri? = requested ?: delegate.uri

    override fun close() {
        requested = null
        delegate.close()
    }

    @UnstableApi
    class Factory(private val speaker: Speaker) : DataSource.Factory {
        override fun createDataSource(): DataSource = SpeechDataSource(speaker)
    }
}
```

- [ ] **Step 2: The service.** In `RecitationService.kt`:
  - **The speaker.** Add a field `private var speaker: Speaker? = null`. In `onCreate`, before building the player, add `val voice = Speaker(this).also { speaker = it }`. Construct the source factory as `RecitationSourceFactory(TaqaDataSource.Factory(paths), voice)`.
  - **The phase.** After `player.addListener(teardown)`, add:

```kotlin
        // Read-aloud (spec §5.5): what the real current item is, for the app's own bar — the
        // session itself only ever shows ayahs (see [AyahPlayer]).
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publishPhase(player)
        })
```

    and the member:

```kotlin
    private var phase = PHASE_AYAH

    private fun publishPhase(player: Player) {
        val id = player.currentMediaItem?.mediaId.orEmpty()
        val now = when {
            id.startsWith("$SPEECH_SCHEME://") -> PHASE_SPEECH
            id.startsWith("$SILENCE_SCHEME://") -> PHASE_SILENCE
            else -> PHASE_AYAH
        }
        if (now == phase) return
        phase = now
        session?.setSessionExtras(Bundle().apply { putInt(EXTRA_PHASE, now) })
    }
```

  - **Release.** In `releaseSession()`, add `speaker?.release()` and `speaker = null`.
  - **The script.** In `onCustomCommand` for `COMMAND_NOW_PLAYING`, after the timeline line, add:

```kotlin
                // Read-aloud (spec §5.5): what the speech items of the queue about to be set say.
                val ayahs = args.getIntArray(ARG_SPEECH_AYAHS)
                val texts = args.getStringArray(ARG_SPEECH_TEXTS)
                speaker?.setScript(
                    if (ayahs != null && texts != null && ayahs.size == texts.size && ayahs.isNotEmpty()) {
                        SpeechScript(
                            generation = args.getLong(ARG_SPEECH_GENERATION),
                            engine = args.getString(ARG_SPEECH_ENGINE).orEmpty(),
                            voiceId = args.getString(ARG_SPEECH_VOICE).orEmpty(),
                            language = args.getString(ARG_SPEECH_LANGUAGE).orEmpty(),
                            texts = ayahs.toList().zip(texts.toList()).toMap(),
                        )
                    } else {
                        null
                    },
                )
```

  - **Constants.** Add to the companion:

```kotlin
        /** Read-aloud's script (spec §5.5), sent with [COMMAND_NOW_PLAYING]. */
        const val ARG_SPEECH_GENERATION = "speechGeneration"
        const val ARG_SPEECH_ENGINE = "speechEngine"
        const val ARG_SPEECH_VOICE = "speechVoice"
        const val ARG_SPEECH_LANGUAGE = "speechLanguage"
        const val ARG_SPEECH_AYAHS = "speechAyahs"
        const val ARG_SPEECH_TEXTS = "speechTexts"

        /** Session extras: the real current item's kind, for the app's bar. */
        const val EXTRA_PHASE = "phase"
        const val PHASE_AYAH = 0
        const val PHASE_SILENCE = 1
        const val PHASE_SPEECH = 2
```

  - **`RecitationSourceFactory`.** Change it to take `speaker: Speaker`, and add a speech factory that shares the policy setters:

```kotlin
@UnstableApi
private class RecitationSourceFactory(
    dataSourceFactory: TaqaDataSource.Factory,
    speaker: Speaker,
) : MediaSource.Factory {

    private val audio = DefaultMediaSourceFactory(dataSourceFactory)

    /** Read-aloud's WAVs (spec §5.5), made on the loading thread as the queue reads ahead. */
    private val speech = ProgressiveMediaSource.Factory(SpeechDataSource.Factory(speaker))

    override fun getSupportedTypes(): IntArray = audio.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        audio.setDrmSessionManagerProvider(drmSessionManagerProvider)
        speech.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        audio.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        speech.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri?.scheme == SILENCE_SCHEME) {
            val millis = uri.authority?.toLongOrNull() ?: 0L
            // The factory's own createMediaSource() hands back a source whose MediaItem is
            // Media3's placeholder — no title, no artist, no artwork — which would blank the
            // notification for the length of every gap. The source will take ours instead.
            return SilenceMediaSource(millis * 1_000L).apply { updateMediaItem(mediaItem) }
        }
        if (uri?.scheme == SPEECH_SCHEME) return speech.createMediaSource(mediaItem)
        return audio.createMediaSource(mediaItem)
    }
}
```

    Import `androidx.media3.exoplayer.source.ProgressiveMediaSource`.

- [ ] **Step 3: `AyahPlayer`.** Replace the queue reconstruction and the gap tests:
  - Replace `queue()`, `cachedQueue`, `cachedCount` and `cachedGapped` with:

```kotlin
    /**
     * The same arithmetic the app's bar uses, over the items actually loaded — ayahs, silences
     * and, with read-aloud on, translations, each naming itself by its scheme. Ayah *numbers*
     * never reach the session, so they are simply counted.
     *
     * Kept per timeline: Media3 asks the getters above many times per state bundle, the app
     * polls four times a second on top, and an 858-item queue rebuilt on each ask is a lot of
     * garbage to make on the application thread.
     */
    private fun queue(): RecitationQueue? {
        val count = real.mediaItemCount
        if (count == 0) return null
        val timeline = real.currentTimeline
        cachedQueue?.let { if (cachedTimeline === timeline && cachedCount == count) return it }
        var ayah = 0
        val items = (0 until count).map { i ->
            val id = real.getMediaItemAt(i).mediaId
            when {
                id.startsWith("$SILENCE_SCHEME://") -> QueueItem.Gap(0L)
                id.startsWith("$SPEECH_SCHEME://") -> QueueItem.Speech(ayah.coerceAtLeast(1))
                else -> QueueItem.Ayah(++ayah)
            }
        }
        if (ayah == 0) return null
        return RecitationQueue(0, items).also {
            cachedQueue = it
            cachedCount = count
            cachedTimeline = timeline
        }
    }

    private var cachedQueue: RecitationQueue? = null
    private var cachedCount = -1
    private var cachedTimeline: androidx.media3.common.Timeline? = null
```

  - Delete `isGapItem`.
  - Replace `onGap()` with `private fun onGap(): Boolean { val queue = queue() ?: return false; return !queue.isAyah(real.currentMediaItemIndex) }`.
  - Replace `ayahIndex()`'s body with `val at = real.currentMediaItemIndex; val queue = queue() ?: return at; return queue.ayahIndexAt(at)`.
  - In `previousAyah()`, replace `if (queue.isGap(at)) 0L else real.currentPosition` with `if (!queue.isAyah(at)) 0L else real.currentPosition`.
  - Update the class KDoc: "gaps" becomes "gaps and translations" where it describes what is hidden.

- [ ] **Step 4: The app's player.** In `RecitationPlayer.android.kt`:
  - **Fields**, beside `timeline`:

```kotlin
    /** Read-aloud (spec §5.3): what the loaded queue reads, and what a rebuild needs to redo it. */
    private var speech: SpokenTranslation? = null
    private var generation = 0L
    private var nowText: NowPlayingText? = null
    private var gapMs = 0L
    private var ayahDurations: Map<Int, Long> = emptyMap()
```

  - **`load`**, with the signature `actual suspend fun load(reciter: Reciter, surah: Int, startAyah: Int, text: NowPlayingText, speech: SpokenTranslation?)`:
    - Replace the queue/clock lines with `val built = RecitationQueue.of(index, reciter.gapMs.toLong(), speech?.spoken.orEmpty())` and `val clock = SurahTimeline.of(built, { durations[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }`.
    - After `reciterId = reciter.id`, add `this.speech = speech; generation++; nowText = text; gapMs = reciter.gapMs.toLong(); ayahDurations = durations`.
    - Replace the inline `sendCustomCommand(...)` with `sendNowPlaying(bound, text, clock)`.
  - **Add** `sendNowPlaying` and `setSpeech`:

```kotlin
    /** The notification's text, the clock and read-aloud's script, before the queue they describe. */
    private fun sendNowPlaying(bound: MediaController, text: NowPlayingText, clock: SurahTimeline) {
        val spoken = speech
        bound.sendCustomCommand(
            SessionCommand(RecitationService.COMMAND_NOW_PLAYING, Bundle.EMPTY),
            Bundle().apply {
                putString(RecitationService.ARG_TITLE, text.title)
                putString(RecitationService.ARG_SUBTITLE, text.subtitle)
                putString(RecitationService.ARG_PREVIOUS_AYAH, text.previousAyahLabel)
                putString(RecitationService.ARG_NEXT_AYAH, text.nextAyahLabel)
                putLongArray(RecitationService.ARG_TIMELINE, clock.itemsMs.toLongArray())
                if (spoken != null) {
                    val entries = spoken.texts.entries.sortedBy { it.key }
                    putLong(RecitationService.ARG_SPEECH_GENERATION, generation)
                    putString(RecitationService.ARG_SPEECH_ENGINE, spoken.voice.engine)
                    putString(RecitationService.ARG_SPEECH_VOICE, spoken.voice.id)
                    putString(RecitationService.ARG_SPEECH_LANGUAGE, spoken.language)
                    putIntArray(RecitationService.ARG_SPEECH_AYAHS, entries.map { it.key }.toIntArray())
                    putStringArray(RecitationService.ARG_SPEECH_TEXTS, entries.map { it.value }.toTypedArray())
                }
            },
        )
    }

    actual fun setSpeech(speech: SpokenTranslation?) {
        val bound = live ?: return
        val old = queue ?: return
        val reciter = reciterId ?: return
        val text = nowText ?: return
        val built = RecitationQueue(old.surah, old.ayahs, gapMs, speech?.spoken.orEmpty())
        val clock = SurahTimeline.of(built, { ayahDurations[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }
        val ayah = _state.value.ayah ?: old.ayahs.first()
        val ayahIndex = built.indexOfAyah(ayah) ?: 0
        // Inside an ayah, the same ayah from where it is; in a translation or a silence, the next
        // ayah — or, after the last one, the last ayah's end, which ends the surah as it would have.
        val (startIndex, startMs) = when {
            phase() == RecitationService.PHASE_AYAH -> ayahIndex to ayahPositionMs
            else -> built.next(ayahIndex)?.let { it to 0L } ?: (ayahIndex to (ayahDurations[ayah] ?: 0L))
        }
        this.speech = speech
        generation++
        queue = built
        timeline = clock
        sendNowPlaying(bound, text, clock)
        bound.setMediaItems(built.items.map { item(reciter, built.surah, it) }, startIndex, startMs)
        bound.prepare()
        publish()
    }

    /** The real current item's kind, as the service publishes it (read-aloud spec §5.5). */
    private fun phase(): Int =
        live?.sessionExtras?.getInt(RecitationService.EXTRA_PHASE, RecitationService.PHASE_AYAH)
            ?: RecitationService.PHASE_AYAH
```

  - **`item`**: add the branch `is QueueItem.Speech -> speechUri(generation, surah, entry.n)`.
  - **`publish`**: add `speaking = phase() == RecitationService.PHASE_SPEECH,` to the `PlaybackState(...)`.
  - **`previous()`**: replace `if (built.isGap(at))` with `if (!built.isAyah(at))`.
  - **`connectionListener`**: add `override fun onExtrasChanged(controller: MediaController, extras: Bundle) { publish() }`.
  - **`stop()` and `onDisconnected`**: add `speech = null`.

- [ ] **Step 5: The harness.** In `RecitationHarnessReceiver.kt`:
  - In `"load"`, compute `val translation = intent.getStringExtra("translation")` and `val speech = translation?.let { buildSpokenTranslation(appContainer.quranRepository, appContainer.speechVoices, it, surah) }`. Log `"speech ${speech?.translationId} ${speech?.texts?.size}"`, then pass `speech = speech` to `player.load`.
  - Add a command:

```kotlin
                    // Read-aloud live (spec §2): `--es translation fr.hamidullah`, or `none` for off.
                    "speech" -> {
                        val surahNow = player.state.value.surah ?: surah
                        val id = intent.getStringExtra("translation")
                        val speech = id?.takeIf { it != "none" }?.let {
                            buildSpokenTranslation(appContainer.quranRepository, appContainer.speechVoices, it, surahNow)
                        }
                        Log.i(TAG, "setSpeech ${speech?.translationId}")
                        player.setSpeech(speech)
                    }
```

  - Make sure the state watcher logs `speaking`. `PlaybackState.toString()` includes it, so check the watcher prints the state object or add the field.

- [ ] **Step 6: Build and run the unit tests.** Run `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 7: Emulator smoke test** (the implementer does this if an emulator is attached; otherwise report NOT RUN).
  - Install with `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:installDebug`.
  - Sideload surah 1 of Alafasy. Push `001.taqa` to `/data/local/tmp`, then `run-as world.taqwa.app cp /data/local/tmp/001.taqa files/quran/audio/ar.alafasy/001.taqa`, then send the harness `reconcile` command.
  - Load it with `adb shell am broadcast -a world.taqwa.app.debug.RECITATION -n world.taqwa.app/world.taqwa.app.debug.RecitationHarnessReceiver --es cmd load --ei surah 1 --ei ayah 1 --es translation en.sahih`.
  - Read `adb logcat -d -s TaqwaHarness`. Expected: states with `speaking=true` between ayahs, and ayah 2 following.

- [ ] **Step 8: Commit.**

```bash
git add -A shared/src/androidMain androidApp/src/debug
git commit -m "android: read the translation after each ayah inside the recitation queue"
```

---

### Task 7: iOS — voices and the speech phase

**Files:**
- Modify: `shared/src/iosMain/kotlin/world/taqwa/app/recitation/SpeechVoices.ios.kt` (replace the stub)
- Modify: `shared/src/iosMain/kotlin/world/taqwa/app/recitation/RecitationPlayer.ios.kt`
- Modify: `iosApp/iosApp/iOSApp.swift` (harness: `translation=` on load, `speech`, `voices`)

**Interfaces:**
- Consumes: `VoicePick`, `VoiceCandidate`, `SpokenTranslation`, `RecitationQueue.isSpeech/isAyah/silenceMs`, `SurahTimeline.of(queue, ayahMs, speechMs)`, `buildSpokenTranslation`.
- Produces: `actual fun createSpeechVoices()` returning `IosSpeechVoices`. `SpeechVoice.id` is `AVSpeechSynthesisVoice.identifier` and `engine` is `""`.

- [ ] **Step 1: Replace `SpeechVoices.ios.kt`.**

```kotlin
package world.taqwa.app.recitation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVSpeechSynthesisVoice

actual fun createSpeechVoices(): SpeechVoices = IosSpeechVoices()

/**
 * Apple's voices on this phone (read-aloud spec §4). There is no Missing on iOS: its voice
 * downloads live in the Settings app, which cannot be linked to, so a language without an
 * installed voice is hidden and the switch appears once the voice is there.
 */
internal class IosSpeechVoices : SpeechVoices {

    override suspend fun status(language: String): VoiceStatus = withContext(Dispatchers.Main) {
        val candidates = AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .filterNot { excluded(it.identifier) }
            .map { voice ->
                val tag = voice.language
                VoiceCandidate(
                    id = voice.identifier,
                    language = tag.substringBefore('-'),
                    country = tag.substringAfter('-', ""),
                    offline = true,
                    installed = true,
                    // Apple's own voices ahead of the Eloquence set, then Apple's quality order.
                    quality = voice.quality.toInt() + if (voice.identifier.startsWith("com.apple.voice.")) 10 else 0,
                )
            }
        VoicePick.best(language, candidates)?.let { VoiceStatus.Ready(SpeechVoice("", it.id)) } ?: VoiceStatus.Unsupported
    }

    override fun installVoice(engine: String) = Unit

    /** Novelty voices ("Bells", "Bubbles") and the user's personal voice are not for scripture. */
    private fun excluded(id: String): Boolean =
        ".speech.synthesis.voice." in id || "personalvoice" in id.lowercase()
}
```

- [ ] **Step 2: The speech phase in `RecitationPlayer.ios.kt`.**
  - **Imports.** Add `platform.AVFAudio.AVSpeechSynthesizer`, `AVSpeechUtterance`, `AVSpeechSynthesisVoice`, `AVSpeechBoundary`, `AVSpeechUtteranceDefaultSpeechRate`, `AVSpeechSynthesizerDelegateProtocol`, `platform.darwin.NSObject` and `kotlinx.cinterop.ObjCSignatureOverride`.
  - **The delegate**, at file level:

```kotlin
/** Tells the player a translation finished by itself — not a stop, which cancels instead. */
private class SpeechEnd(private val onFinish: (AVSpeechUtterance) -> Unit) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
        onFinish(didFinishSpeechUtterance)
    }
}
```

  - **Fields:**

```kotlin
    /** Read-aloud (spec §5.6): what this surah reads, and what a rebuild needs to redo the queue. */
    private var speech: SpokenTranslation? = null
    private var gapMs = 0L
    private var ayahDurations: Map<Int, Long> = emptyMap()
    private var utterance: AVSpeechUtterance? = null
    private var speechStarted: TimeSource.Monotonic.ValueTimeMark? = null
    private var speechElapsedMs = 0L
    private val speechEnd = SpeechEnd { finished -> if (finished == utterance) onSpeechEnded() }
    private val synthesizer: AVSpeechSynthesizer by lazy { AVSpeechSynthesizer().also { it.delegate = speechEnd } }
```

  - **`load`**, with the signature ending `, speech: SpokenTranslation?)`:
    - Build `RecitationQueue(surah = surah, ayahs = split.files.keys.sorted(), gapMs = reciter.gapMs.toLong(), spoken = speech?.spoken.orEmpty())`.
    - Build the clock with `SurahTimeline.of(built, { split.durationsMs[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }`.
    - In the main-thread block, add `this@RecitationPlayer.speech = speech; gapMs = reciter.gapMs.toLong(); ayahDurations = split.durationsMs`.
  - **`go(index)`**: call `stopSpeaking()` right after `gapJob = null`. After the `if (built.isGap(at)) { … }` block, add:

```kotlin
        if (built.isSpeech(at)) {
            player?.pause()
            ayahPositionMs = ayahDurationMs
            gapStarted = null
            gapElapsedMs = 0L
            speechElapsedMs = 0L
            speechStarted = null
            publish()
            if (wantsPlay) speak((built.items[at] as QueueItem.Speech).n)
            return
        }
```

  - **The speech functions:**

```kotlin
    /** Reads the translation after ayah [n] (read-aloud spec §5.6); a voice that is gone skips it. */
    private fun speak(n: Int) {
        val spoken = speech
        val text = spoken?.texts?.get(n)
        val voice = spoken?.voice?.id?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            ?: spoken?.language?.let { AVSpeechSynthesisVoice.voiceWithLanguage(it) }
        if (text == null || voice == null) {
            onSpeechEnded()
            return
        }
        val next = AVSpeechUtterance(string = text)
        next.voice = voice
        next.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance = next
        speechStarted = TimeSource.Monotonic.markNow()
        synthesizer.speakUtterance(next)
        startTicker()
    }

    /** The translation read itself out: on to whatever follows it. */
    private fun onSpeechEnded() {
        utterance = null
        speechStarted = null
        val built = queue ?: return
        val following = at + 1
        if (following >= built.size) finish() else go(following)
    }

    /** Silences the voice without advancing: a stop reports cancel, never finish. */
    private fun stopSpeaking() {
        if (utterance == null) return
        utterance = null
        speechStarted = null
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    private fun speechElapsedNow(): Long = speechElapsedMs + (speechStarted?.elapsedNow()?.inWholeMilliseconds ?: 0L)
```

  - **`waitOutGap`**: replace `val next = built.next(at); if (next != null) go(next) else finish()` with `val following = at + 1; if (following < built.size) go(following) else finish()`. After a breath comes the translation, not the next ayah.
  - **`onItemEnded`**: replace `if (built.isGap(at)) return` with `if (!built.isAyah(at)) return`.
  - **`pause()`**: add before `player?.pause()`:

```kotlin
        if (queue?.isSpeech(at) == true && utterance != null) {
            speechElapsedMs = speechElapsedNow()
            speechStarted = null
            synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
```

  - **`play()`**: replace the gap line with:

```kotlin
        when {
            built.isGap(at) -> waitOutGap(built, built.silenceMs(at) - gapElapsedMs)
            built.isSpeech(at) -> if (utterance != null && synthesizer.paused) {
                speechStarted = TimeSource.Monotonic.markNow()
                synthesizer.continueSpeaking()
            } else {
                speak((built.items[at] as QueueItem.Speech).n)
            }
            else -> player?.play()
        }
```

  - **`previous()`**: replace `if (built.isGap(at)) 0L else ayahPositionMs` with `if (!built.isAyah(at)) 0L else ayahPositionMs`.
  - **`stop()`**: call `stopSpeaking()` next to `gapJob?.cancel()`, and add `speech = null`.
  - **`publish()`**: compute `val gap = built.isGap(at)` and `val speaking = built.isSpeech(at)`. The AVPlayer branch runs only `if (!gap && !speaking)`; otherwise `ayahPositionMs = ayahDurationMs`. `within` becomes `when { gap -> gapElapsedNow(); speaking -> speechElapsedNow().coerceAtMost(clock.durationOf(at)); else -> SurahTimeline.fitToSlot(ayahPositionMs, ayahDurationMs, clock.durationOf(at)) }`. Add `speaking = speaking,` to `PlaybackState(...)`.
  - **`setSpeech`**:

```kotlin
    actual fun setSpeech(speech: SpokenTranslation?) {
        val old = queue ?: return
        this.speech = speech
        val built = RecitationQueue(old.surah, old.ayahs, gapMs, speech?.spoken.orEmpty())
        val clock = SurahTimeline.of(built, { ayahDurations[it] ?: 0L }) { speech?.estimateMs(it) ?: 0L }
        val ayah = old.ayahAt(at)
        val inAyah = old.isAyah(at)
        queue = built
        timeline = clock
        if (inAyah) {
            // The ayah's own item carries on playing; only its place in the queue has moved.
            at = built.indexOfAyah(ayah) ?: 0
            publish()
            return
        }
        stopSpeaking()
        gapJob?.cancel()
        gapJob = null
        val next = built.next(built.indexOfAyah(ayah) ?: 0)
        if (next != null) go(next) else finish()
    }
```

- [ ] **Step 3: The Swift harness.** In `iOSApp.swift`'s `RecitationHarness.handle`:
  - In `case "load":`, when `value("translation")` is present, call `SpokenTranslationKt.buildSpokenTranslation(quran: AppContainerKt.appContainer.quranRepository, voices: AppContainerKt.appContainer.speechVoices, translationId: id, surah: surah) { spoken, _ in … }`. Inside the completion, call `player.load(…, speech: spoken) { _ in }` and `NSLog("TaqwaHarness speech \(spoken?.translationId ?? "none") \(spoken?.texts.count ?? 0)")`. Without the parameter, keep the existing call with `speech: nil`.
  - Add `case "speech":`. Build as above for the current surah (`player.state.value` gives `surah`), or pass `nil` when `translation=none`, then call `player.setSpeech(speech: spoken)`.
  - Add `case "voices":`. For each of `["en","ar","fr","tr","id","ur","bn"]`, call `AppContainerKt.appContainer.speechVoices.status(language: l) { status, _ in NSLog("TaqwaHarness voice \(l) \(String(describing: status))") }`.
  - The exact Swift names come from the generated header. If one differs, read it from `shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Headers/Shared.h` after building.

- [ ] **Step 4: Build.** Run `./scripts/ios-build.sh` (or `./gradlew :shared:compileKotlinIosSimulatorArm64` then the Xcode simulator build the script runs). Expected: BUILD SUCCEEDED. `./scripts/test.sh :shared:iosSimulatorArm64Test` passes.

- [ ] **Step 5: Simulator smoke test** (the implementer does this if a simulator is booted; otherwise report NOT RUN).
  - Install. Sideload Alafasy 001 into `<data container>/Library/Application Support/quran/audio/ar.alafasy/001.taqa`, then open `taqwa://recite/reconcile`.
  - Open `taqwa://recite/voices`, then `taqwa://recite/load?surah=1&ayah=1&reciter=ar.alafasy&translation=en.sahih`.
  - Expected in `log show --predicate 'eventMessage CONTAINS "TaqwaHarness"'`: voice statuses; states with `speaking=true` between ayahs.

- [ ] **Step 6: Commit.**

```bash
git add -A shared/src/iosMain iosApp/iosApp/iOSApp.swift
git commit -m "ios: read the translation after each ayah with the phone's voice"
```

---

### Task 8: Verification on the emulator, the S23 and the simulator (controller, not a subagent)

- [ ] Run `./scripts/test.sh`. Every suite must be green; record the counts.
- [ ] **Emulator (Pixel_8_Pro, Google engine):**
  - The harness `voices` command lists en Ready, and the rest Missing(com.google.android.tts) unless already installed.
  - Play Al-Fatiha with read-aloud from the real UI: the reading sheet switch, then play.
  - Timing in the log: ayah → breath → speaking → gap → next ayah.
  - Bar caption screenshot. Notification via `dumpsys media_session`: one item, title unchanged.
  - Pause and play during speech. Long-press next during speech. Toggle off mid-speech, which moves to the next ayah.
  - Switch to French: Missing → Get the voice → Google installer opens.
- [ ] **S23** (media volume held at 0 via `cmd media_session volume --stream 3 --set 0`; note the original and restore it):
  - Install the debug build over the existing debug-signed build with `adb -s R5CW219W15J install -r`. It keeps the data.
  - Run `voices`, then a short read-aloud playback with Samsung's default engine present.
  - Look for logcat errors.
- [ ] **Simulator:** `voices`, playback with speech, pause/continue, backgrounding with the home button.
- [ ] Add a docs/BUILD-LOG.md entry. Update the memory file `taqwa-project.md`.
- [ ] Run the whole-branch review (superpowers:requesting-code-review) before handing over.
