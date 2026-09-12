# Taqwa Tasbeeh

Status: **decided 11 September 2026** with Mohamed, after the design round in the "Taqwa Tasbeeh Concepts" page. Decided there: direction A (the ring), the misbaha icon as the entry point, the post-prayer set counted continuously to 100 with the displayed dhikr switching at each part and a distinctive haptic at each part's end, the three-part reminder row under the ring, no lifetime tallies, and no translation under an Arabic interface.

## 1. What it is

A dhikr counter, one tap anywhere on the page per count, opened from a misbaha glyph at the top corner of the Prayer screen opposite the city. It shows the dhikr being said, the count against its target inside the Prayer screen's own ring, and the round; it remembers where you were; it never keeps a score.

Non-goals: totals per day or lifetime (dhikr is not a score); a widget; notifications or reminders; audio; sharing; any statistics screen.

## 2. Presets

Built in, in this order, with stable ids. Arabic is written with tashkeel and is the same in both interfaces; transliteration and meaning are English resources shown only under a Latin interface.

| id | parts | Arabic | transliteration | meaning |
|---|---|---|---|---|
| `after_prayer` | 33 · 33 · 34 (= 100) | سُبْحَانَ ٱللَّٰهِ · ٱلْحَمْدُ لِلَّٰهِ · ٱللَّٰهُ أَكْبَرُ | SubhanAllah · Alhamdulillah · Allahu Akbar | Glory be to Allah · All praise is due to Allah · Allah is the Greatest |
| `subhanallah` | 100 | سُبْحَانَ ٱللَّٰهِ | SubhanAllah | Glory be to Allah |
| `alhamdulillah` | 100 | ٱلْحَمْدُ لِلَّٰهِ | Alhamdulillah | All praise is due to Allah |
| `allahu_akbar` | 100 | ٱللَّٰهُ أَكْبَرُ | Allahu Akbar | Allah is the Greatest |
| `astaghfirullah` | 100 | أَسْتَغْفِرُ ٱللَّٰهَ | Astaghfirullah | I seek Allah's forgiveness |
| `la_ilaha_illallah` | 100 | لَا إِلَٰهَ إِلَّا ٱللَّٰهُ | La ilaha illallah | There is no god but Allah |
| `subhanallahi_wa_bihamdihi` | 100 | سُبْحَانَ ٱللَّٰهِ وَبِحَمْدِهِ | SubhanAllahi wa bihamdihi | Glory and praise be to Allah |

Chip labels: English "After prayer", "SubhanAllah", "Alhamdulillah", "Allahu Akbar", "Astaghfirullah", "La ilaha illallah", "SubhanAllahi wa bihamdihi"; Arabic «بعد الصلاة», «سبحان الله», «الحمد لله», «الله أكبر», «أستغفر الله», «لا إله إلا الله», «سبحان الله وبحمده». `after_prayer` is the default on first open.

**Amended 11 September 2026: every single-dhikr built-in counts to 100.** `subhanallah`,
`alhamdulillah` and `allahu_akbar` were 33, 33 and 34 — the post-prayer set's three parts carried
out of the set and made into chips of their own. On the screen that reads as a chip that silently
changes the target: counting the set and tapping SubhanAllah to see the phrase alone moved the
count from "of 100" to "of 33" without a word, and a set half done looked nearly finished. 33 is a
third of a set rather than a number anything is said in on its own, and the other three singles
already counted to 100. The 33 · 33 · 34 stays where it belongs — inside `after_prayer`, whose
parts, ticks and part haptics are unchanged. A saved count is stored per preset and a target is not
stored at all, so someone left at 20 of 33 reopens at 20 of 100 with their twenty taps intact.
A custom preset keeps whatever target its phrase was given; only the built-ins are levelled.

**Custom presets** are added from the plus at the screen's top corner: a bottom sheet with a phrase field (any script, up to 60 characters, shown exactly as typed, no transliteration or meaning) and a target from 1 to 1000. They appear after the built-ins, in creation order, with ids `custom_<epochMillis>`, and can be deleted from the same sheet when opened on an existing custom preset (long-press its chip). Deleting the selected preset selects `after_prayer`.

**Amended 11 September 2026: the sheet's target defaults to 100, and opening it on an existing
phrase is an edit.** Adding a new dhikr prefills the target field with `100` — the number every
built-in now counts to — with Add live as soon as a phrase is typed; anyone who means 33 types over
a prefilled field, which is one gesture rather than a decision. Long-pressing a custom chip opens
the same two fields prefilled with that phrase's own wording and target, headed «Edit dhikr» /
«تعديل الذكر» with a Save button under Add's own validity rule and Delete beneath it: a target typed
out of habit can be corrected without deleting the phrase and its count. Saving keeps the id, and so
the chip's place and the count stored under it. **The clamp rule:** where the new target is below the
count already reached, the store clamps the stored count to the target inside the same write — 8
against a new target of 5 reads 5 of 5, a closed ring — and the round is left as it was, having been
counted against the target it was counted under. The view model clamps the same way when it derives
what the screen shows, so a stored count can never be drawn past its own ring.

## 3. The engine

`tasbeeh/TasbeehEngine.kt` in `shared/commonMain`, pure and unit-tested:

```kotlin
data class Dhikr(val id: String, val arabic: String, val transliteration: String?, val meaning: String?)
data class DhikrPart(val dhikr: Dhikr, val count: Int)
data class TasbeehPreset(val id: String, val parts: List<DhikrPart>, val custom: Boolean = false) {
    val total: Int get() = parts.sumOf { it.count }
}
data class TasbeehState(val presetId: String, val count: Int, val round: Int) // count 0..total, round from 1
sealed interface TapEvent { object Tick; data class PartComplete(val partIndex: Int); object SetComplete }

object TasbeehEngine {
    fun tap(state: TasbeehState, preset: TasbeehPreset): Pair<TasbeehState, TapEvent>
    fun currentPart(state: TasbeehState, preset: TasbeehPreset): Int   // index of the part the next count belongs to
    fun reset(state: TasbeehState): TasbeehState                       // count 0, round 1
}
```

- **Counting is continuous within a round.** For `after_prayer` the number runs 1 to 100; the displayed dhikr is the part the *next* count belongs to, so it switches to Alhamdulillah the moment 33 is reached and to Allahu Akbar at 66. `currentPart` for `count == total` is the last part (the completed set is shown whole until the next tap).
- **Events.** `tap` from `count < total` increments and returns `PartComplete(i)` when the new count is the cumulative end of part `i` and `i` is not the last part, `SetComplete` when it equals `total`, `Tick` otherwise. `tap` from `count == total` starts the next round: `count = 1`, `round + 1`, event `Tick`.
- **Reset** returns to count 0, round 1, same preset.
- Single-part presets are the same machine with one part, so `PartComplete` never fires for them.

**Amended 12 September 2026: a completed set rolls over by itself.** The hundred used to sit
on the ring until the next tap took it to 1 of round 2, which read as if the first dhikr of the
new round had been skipped, and a reader who had finished had to tap once more to see a fresh
ring. Now the ring stays closed for `TasbeehViewModel.SET_COMPLETE_HOLD_MS` (1 s: long enough
for the set haptic and the full ring to register) and then the counter rolls over to **count 0,
`round + 1`** on its own — `TasbeehEngine.nextRound`, pure like the rest — and that state is
written at once. A tap inside the hold is the reader carrying on: it opens the next round at 1
exactly as before and cancels the rollover, so nothing lands on top of the 1. Leaving the
screen, switching chips, or editing a phrase mid-hold settles the rollover first, so disk holds
0 of the next round rather than the hundred. A hundred that is nonetheless on disk (the debounce
wrote it and the process died inside the hold; an install from before this change) opens as
the hundred it was and rolls over after the same hold. Rolling over is not a count: no haptic.

## 4. What the screen shows

`Screen.Tasbeeh`, reached from the Prayer header and by Back returning to it.

- **Top row.** The back chevron at the start; at the end, a plus glyph (44 dp target) that opens the custom-dhikr sheet.

  **Amended 11 September 2026: the screen names itself.** Between the two buttons, beside the
  chevron, the row carries "Tasbeeh" / «التسبيح» (`tasbeeh_title`) in the reader header's own
  treatment — `TaqwaText.rowLabel` at extra-bold, 4 dp of gutter either side, centred vertically
  against the chevron, taking the width left between the two 44 dp targets and ellipsised rather
  than wrapped. Not the app's 24 sp `screenTitle`: the subject of this screen is the ring in the
  middle of it, and a title big enough to compete with the count would push the ring down the page.
  Nothing else in the row moves, and the dhikr, the ring and the stack are where they were to the
  pixel. Arriving here from the Prayer header's misbaha glyph, the only thing that said what had
  been opened was the dhikr itself.
- **The dhikr**, centred: the Arabic in the interface's Arabic face at 30 sp; beneath it, under a Latin interface only, the transliteration in the caption style (secondary) and the meaning at 12 sp (tertiary). Under an Arabic interface the Arabic stands alone. A custom preset shows its phrase alone in both.
- **The ring**, 196 dp, the `CountdownRing`'s track and arc extracted into a shared `RingArc` so both screens draw the same stroke and corner. The arc fills `count / total`, animated over 250 ms on each tap. For a multi-part preset two 1 dp ticks in the track colour mark the part boundaries on the track (at 33 % and 66 % for the post-prayer set). Inside: «ROUND n» in the section-label style in the accent; the count in Manrope Light 56 sp with tabular figures (the `TABULAR` feature the countdown uses), which bumps to 1.06× and back over 120 ms on each tap; beneath it "of N" / «من N» in the caption style, N being the preset's total.
- **The part reminder**, only for multi-part presets, directly under the ring: the three parts in the section-label style at 10 sp with `·` between, each with a dot before it — completed parts a filled dot in the tertiary colour, the current part a filled dot and text in the accent, upcoming parts a hollow dot in the tertiary colour. Latin interface: transliterations in capitals; Arabic interface: the Arabic chip labels.
- **The hint** "Tap anywhere to count" / «اضغط في أي مكان للعدّ» in 12 sp tertiary, shown only while `count == 0` and `round == 1`.
- **The chips**, one horizontally scrolling row: every preset in §2's order, the selected one filled in the accent with white text, the others bordered in the hairline on the surface colour; 44 dp tall targets. Selecting a preset switches to its own remembered state (each preset keeps its own count and round). Long-press on a custom chip opens its sheet with Delete.
- **Reset**, a text button at the bottom in the secondary colour: opens a `TaqwaBottomSheet` with one line «Reset the count for this dhikr?» / «إعادة العدّ لهذا الذكر؟» and a Reset row; the sheet exists because a mistap on Reset after ninety counts is the one thing on this screen that cannot be undone by counting.
- **The tap surface** is everything between the top row and the chips, one clickable region with no ripple; the chips, Reset and the two top buttons are outside it. A tap counts even while the ring is still animating the previous one.

  **Amended 11 September 2026: the landscape body.** Turned sideways the screen is two panes: the
  counting one from the start edge to a **220 dp** chips column, and the column itself. The tap
  surface is the whole of the start pane — full width to the column and full height under the header
  row — with the counter stack merely centred inside it, so the empty margins above, below and beside
  it count too. The panes were equal once, which meant half a landscape screen did nothing at all:
  a thumb aiming at the middle landed on a chip or on the dead air beside one. The column is a fixed
  220 dp rather than a share of the screen because it needs the width of its longest label —
  "SubhanAllahi wa bihamdihi", 198 dp of chip — and not one pixel more; every pixel it does not take
  is counting surface. Its chips run down rather than across, with the plus above them and Reset
  below. The header row of chevron and name sits over the counting pane and is **not** part of the
  tap surface — a name that counts when it is read is a name you stop reading — and the ring is
  sized against the height left under that row (down from 196 dp where the screen is too short for
  it), so the dhikr, the reminder row and the hint all keep their place rather than being pushed off
  the bottom edge.
- **Haptics.** `Haptics` gains three calls beside the compass's `tick()`: `count()` — the lightest available (Android `EFFECT_TICK` on API 29+, else 20 ms; iOS light impact); `partComplete()` — two pulses (Android waveform 0/40/60/40 ms; iOS two medium impacts 70 ms apart); `setComplete()` — three, the last long (Android 0/60/70/60/70/140 ms; iOS notification success then a heavy impact 120 ms later). Each event maps to exactly one call; a tap that starts a new round is a `count()`.
- **Screen stays awake** while this screen is shown: a `KeepScreenOn()` composable, `expect`/`actual` — Android sets `LocalView.current.keepScreenOn` for the composition's life, iOS sets `UIApplication.sharedApplication.idleTimerDisabled` and restores it on dispose.
- **Arabic interface** mirrors everything as the rest of the app does; the count's digits follow the app's digit choice like every other number.

## 5. The entry point

On the Prayer screen's header, a 44 dp button at the end of the city-and-dates row, vertically aligned with the city's line, drawing the **misbaha glyph** from the design round in `Glyphs.kt` at the tab bar's 22 dp and stroke: eight beads as filled 1.05-unit circles on a 5.2-unit radius around (8, 7.2) on the 16-unit grid, the head bead at (8, 12.9) at 1.45, and a two-stroke tassel beneath. Tint `textPrimary`; content description "Tasbeeh" / «التسبيح». It pushes `Screen.Tasbeeh`. The landscape body places the same button at the end of its own header column.

## 6. Persistence

`settings/TasbeehStore.kt` over the app's DataStore, beside `BookmarkStore`:

- `tasbeeh_selected` (string, preset id), `tasbeeh_state_<presetId>` (string `count:round`) for every preset that has been counted, `tasbeeh_custom` (string set of `id␟phrase␟target`, U+001F separated as the widget mirrors do).
- Written 300 ms after the last tap, and immediately when the screen leaves composition, so a hundred taps do not mean a hundred writes and a quick exit loses nothing.
- Read once when the screen opens. Malformed entries are ignored, never thrown on.

Nothing is aggregated: no daily count, no lifetime count, nowhere.

## 7. Tests

`commonTest`: `TasbeehEngineTest` — the post-prayer sequence yields `PartComplete(0)` at 33, `PartComplete(1)` at 66, `SetComplete` at 100 and `Tick` everywhere else; `currentPart` is 0 through 32, 1 through 65, 2 through 100; the tap after 100 gives count 1, round 2, `Tick`; a 33-count preset gives `SetComplete` at 33 and no `PartComplete`; a 1-count custom preset completes on the first tap; `reset`. `TasbeehStoreTest` (DataStore harness as `SettingsRepositoryTest`) — selected preset, per-preset state, custom presets round-trip, malformed custom entry ignored, delete. `TasbeehViewModelTest` — event → haptic call mapping, preset switch restores that preset's state, debounced write. `Glyphs`: the misbaha draws (a smoke test if one exists for the others).

Device (S23, iPhone 12, emulator, simulator): the header icon in English and Arabic; the full post-prayer set on a phone with the three haptics felt distinct; the switch of dhikr at 33 and 66; round 2; a single preset; a custom preset added, used and deleted; Reset with its sheet; leaving mid-count and returning; screen not dimming for two minutes; dark theme; Arabic interface with no translation and mirrored layout; landscape.

## 8. Release

Ships as **0.7.0 (9)**, a minor bump.
