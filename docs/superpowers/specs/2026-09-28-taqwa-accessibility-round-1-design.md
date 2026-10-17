# Accessibility round 1 — design

28 September 2026. Branch `worktree-a11y-round-1` off `main` d5230081. Source: the 27 September
accessibility audit (80 findings, 3 blockers) and Mohamed's two rules, given on 28 September:

1. **Nothing visible changes.** At default settings the app looks and behaves exactly as it does
   today. Accessibility work is spoken-only (semantics), or only shows where large text is cut
   off today.
2. **The Quran is never read by a phone voice.** It is recited by one of the app's reciters or not
   at all. A screen reader gets the ayah's number and its translation, never its Arabic.

Mohamed approved the first five of a twelve-item list on 28 September ("let's do the first
five"). This document records what those five are and how each is built.

## Scope

| # | Item | Audit findings it closes |
|---|------|--------------------------|
| 1 | Compose Multiplatform 1.12.0 → 1.12.1 | pl-cmp-crash |
| 2 | The Mushaf page ignores the phone's font scale | vm-mushaf-scale (blocker) |
| 3 | Shared controls carry a name and a state | sr-back, sr-switch, sr-choice, sr-segmented, sr-previews, sr-roles (part) |
| 4 | The Prayer screen reads as sentences, Settings has headings | sr-prayer-rows, sr-countdown, sr-headings (part) |
| 5 | Recitation-only Quran for screen readers | sr-mushaf (blocker), pl-ayah-widget (blocker), pl-ayah-widget-ios, sr-quran-text, sr-ayah-card (part) |

Out of scope, by the rules: new colours, new controls, Mushaf zoom, page-turn buttons, undo,
vibrate-only adhan, focus rings, lock-screen changes, iOS text size. Items 6–12 of the list
(tasbeeh, Qibla, player bar, phone-driven high contrast, small labels, widget sentences,
large-text clipping) are the next round.

## 1. Compose Multiplatform 1.12.1

One line in `gradle/libs.versions.toml`. 1.12.1 (22 September 2026) fixes "crash when iOS reads
AccessibilityElement's properties after disposal" (#3403), which only VoiceOver, Voice Control,
Switch Control and Full Keyboard Access users can hit. Kotlin stays 2.4.10. The whole test suite
and both app builds must pass on it.

## 2. The Mushaf page and the font scale

**What happens today.** The page is fitted to the frame's width (`fittedSize`): whatever the
font scale, the size shrinks until the widest line fits. That is why 100 % and 130 % look
identical. From 150 % Android's non-linear font scaling (large sizes scale less than small ones)
breaks the linear assumption `fittedSize` makes: the size it picks still overflows, justification
runs out of room, and the words at the reading end of a line are cut.

**Design.** The page is a printed object sized by the frame, not by the phone's text setting, so
its measurement and drawing use a density whose `fontScale` is 1: `Density(density.density, 1f)`,
provided to the frame's column (lines, basmala, surah bands) and handed to `layoutPage`. Every
sp→px conversion inside the frame is then linear, `fittedSize` and `fitToHeight` are exact again,
and the page draws as it does at 100 % at every setting. The captions above and below the frame
(juz, surah name, page number) keep the phone's font scale: they are interface text.

At font scale 1.0 the pinned density equals the real one, so the page is pixel-identical.

*Amended after review.* Not every page is width-fitted: pages 1 and 2 (Al-Fatihah and the opening
of Al-Baqarah) have room to spare at their base size, so before this change they grew with the
phone's text setting, and they must keep doing so — the rule is "nothing visible changes", and for
a large-text user a smaller Al-Fatihah is a change. So the page's starting size is the size the
phone itself would have drawn the base at (`with(phoneDensity) { base.sp.toPx() } / density`,
Android's own non-linear curve), and only the measurement and drawing are linear. At 100 % that is
the base; a page that must shrink to its width shrinks exactly to it; a page with slack grows as it
did.

## 3. Shared controls

All touch behaviour stays as it is. Only semantics change.

- **Back chevron** (`BackChevron`, shared by every pushed screen, Qibla, Tasbeeh, the reader and
  the Mushaf): `Role.Button` and a "Back" description. New string `nav_a11y_back`.
- **iOS back gesture**: `SystemBackHandler.ios.kt` becomes CMP's `BackHandler`. VoiceOver's
  two-finger scrub calls `accessibilityPerformEscape`, which CMP 1.12 turns into an Escape key
  event and delivers to the innermost enabled `BackHandler` (verified in the 1.12 sources:
  `Accessibility.ios.kt` → `ComposeSceneMediator.performEscape` →
  `BackNavigationEventInput.onKeyEvent`). The app's existing handler in `App.kt` then pops the
  stack or returns to the Prayer tab, as Android's back button does.
  *Amended after implementation:* CMP 1.12 also installs a screen-edge swipe-back for everyone
  whenever any back handler is enabled (`UIKitNavigationEventInput`; only the end edge is
  configurable), which would hand sighted readers a new gesture — on the Mushaf an edge-started
  page swipe would leave the screen. So the iOS handler is enabled only while VoiceOver is
  running, kept current by the system's status notification. VoiceOver owns single-finger touches
  while it runs, so the gesture never reaches a sighted hand. Switch Control is not in the gate:
  it leaves direct touch working, so its users would meet the swipe. Keyboard users keep today's
  behaviour. VoiceOver does not run in the simulator, so the escape path is exercised there with
  a temporary, uncommitted override of the gate, and the real gesture on an iPhone.
- **Switches** (`TaqwaToggle` inside `TaqwaRow`, 11 rows): the row becomes one node with
  `Role.Switch`, a `ToggleableState` and a semantic `onClick` that flips it, so TalkBack says
  "Prayer notifications, switch, off" and a double-tap flips it. The drawn switch keeps its own
  click for touch and is hidden from semantics. `TaqwaRow` gains `toggle: RowToggle?`. The two
  standalone toggles (`DownloadSheet`, `LocationSettingsScreen`) get `toggleable` semantics on
  the switch itself with the row's label as description.
  *Amended after implementation, and corrected after the 17 October device check:* on Android,
  TalkBack 16 says the row's state, then its label, then the role word: "Off, Prayer
  notifications, Switch", and "Off, Morning and evening adhkar, Half an hour after Fajr and Asr,
  Switch" on a row with a subtitle. "On" and "Off" are Compose's own state words for a switch.
  The first amendment (85f35a37) said the opposite: that TalkBack speaks no role word on a row
  with text children. That came from the 29 September reading of the delegate, not from listening.
  Compose (ui-android 1.12.1) gives a node its role's class name or role description only when no
  semantics children were merged into it
  (`AndroidComposeViewAccessibilityDelegateCompat.android.kt:631`), a row's label is such a child,
  and the 29 September device pass saw `android.view.View` on every row. The rows are still
  `View` in the 17 October dumps, but that reading did not match what TalkBack 16 actually said.
  On rows, segments and tabs with text children it said "Switch", "Radio button" and "Tab", the
  position in a group ("2 of 3"), and the state before the label (the device check's `ttsOutput`
  logs, recorded in the build log's accessibility section). Why the reading failed is not
  established. The same holds for every row, segment and tab below. The emulator's TalkBack is not
  the owner's, so the listening checklist (plan Task 12 Step 2b) should confirm this on his phone.
  The standalone toggles came out differently. The download sheet's row was already tappable as a
  whole, so it becomes the `toggleable(role = Switch)` itself. The location row is a `TaqwaRow`
  with `toggle`. In both, the drawn switch is hidden. A touch on the drawn switch still flips it
  and a touch on the label still does not (`ControlSemanticsTest`).
- **Option rows** (`TaqwaRow` with a `CheckMark`, 6 places): `TaqwaRow` gains `selected: Boolean?`;
  when set the row is `selectable(role = RadioButton)` and the containing card is a
  `selectableGroup()`, so TalkBack says "Dark, radio button, selected, 2 of 3".
  *Amended after Task 6, and corrected after the 17 October device check:* TalkBack 16 says the
  state, the label, "Radio button" and the position, in that order: "Selected, System, Radio
  button, 1 of 3, In list, 3 items" on entering the Theme card, then "Not selected, Light, Radio
  button, 2 of 3". The time-format sheet is the same ("Selected, 24-hour, 18:32, Radio button,
  1 of 2"). So the group's collection info is spoken, and the design's sentence holds in its words
  but not its order (see Switches). A selected radio button also loses its click on Android
  (delegate :826–834). That costs nothing where choosing again does nothing (theme, the
  high-latitude rule). Where choosing again would also close a sheet or fold a list
  (remind-before, the time format, the translation list), that close is lost, but back, the scrim
  and a swipe still close the sheet. This is kept as standard radio behaviour. VoiceOver says
  "selected" and "button", and ignores both the role and the group.
- **Audio option rows** (`AudioOptionRow`, sound / adhan voice / reciter sheets): the row is
  `selectable(role = RadioButton)` in a `selectableGroup()`; the preview button gets a
  description in the two prayer-sound sheets ("Hear Takbir"). New string `sound_a11y_preview`.
  *Amended after Task 6:* the row is `selectable(selected)` with **no role**, because on Android
  the role would cost the chosen row its click. Picking the chosen reciter again still does
  something: it offers the download again while the surah is missing. *Corrected after the
  17 October device check:* this amendment also said the role was never spoken on a row with text
  children. TalkBack 16 does speak it (see Switches), so these rows, having no role, are not
  expected to say "Radio button" as the option rows do. No audio row was heard on 17 October.
- **Calculation method** (`TimetableChoiceRow` on the timetable and Other methods screens,
  added by Task 6): built the same way, `selectable(selected)` with no role, in
  `selectableGroup()` cards. Its selected row still acts, because it confirms a paused timetable.
  Like the audio rows, it was not heard on 17 October.
- **Segmented controls** and **tab bar**: each option/tab is `selectable(role = Tab)` in a
  `selectableGroup()`.
  *Corrected after the 17 October device check:* on Android TalkBack 16 says "selected", the label,
  "Tab" and the position: "selected, Automatic, Tab, 1 of 3, In list, 3 items" on the Asr segment,
  and "selected, Prayer, Tab, 1 of 3, In list, 3 items" on the tab bar. An unselected segment or tab
  says no "not selected": "Standard, Tab, 2 of 3", "Quran, Tab, 2 of 3". The selected segment
  or tab offers no click there. That costs nothing: choosing it again does nothing, and the bar
  is only shown on tab roots.
- **Sheet drag handle** (`SheetDragHandle`, every `TaqwaBottomSheet`), *added by the closing fix
  wave:* material3 wraps the handle in a clickable node with the sheet's dismiss and expand
  actions and takes the name from the handle. The app's drawn pill had none, so every sheet began
  with a clickable node that had no name. It now carries "Drag handle", material3's own words, in
  its own translations. New string `sheet_a11y_drag_handle`.

## 4. The Prayer screen and headings

- **Prayer rows**: each `PrayerRow` is one node: description "Fajr, 5:10" (name, then any pill
  — Jumuʿah, Set by rule — then the time) and a state: "Next prayer", "Current prayer" or
  "Passed". Sub-lines (the other Asr, where timetables differ) stay separate stops beneath the
  row. The Arabic name beside the Latin one is decoration and is not spoken. `PrayerTimeline`
  gains `next: Prayer?`; a row is next when its status is UPCOMING and its prayer is the
  countdown's. New strings `today_a11y_prayer_next`, `today_a11y_prayer_current`,
  `today_a11y_prayer_passed`.
  *Amended after Task 7:* `next` is the countdown's own `PrayerTime`, and a row is next only
  when both its prayer and its instant match. Before today's Fajr the countdown can be counting
  to yesterday's Isha or Maghrib, which is no row of today's, while today's Isha is still
  upcoming. Matching by prayer alone marked that row Next. Since 11 October the keys are
  `prayer_a11y_*` (Today became Prayer). Every spoken time is the string the screen draws (12- or
  24-hour, the locale's own AM/PM).
- **Countdown ring**: one description, "Fajr in 4 hours 10 minutes, at 5:10", from whole minutes
  so it changes once a minute, never a live region. Under a minute: "Fajr in under a minute, at
  5:10". New strings `today_a11y_countdown`, `today_a11y_countdown_soon`,
  `duration_hours_minutes`, plurals `a11y_hours` and `a11y_minutes` (spoken, not the "min"
  abbreviation `minutes_count` uses).
  *Amended after Task 7:* the keys are `prayer_a11y_countdown` and `prayer_a11y_countdown_soon`.
  The clock time spliced into the sentence is `isolatedClock(clock)`. That is byte-identical at
  24-hour and wrapped in FSI/PDI at 12-hour, as every other spliced clock in the app is.
- **Headings**: `SettingsScaffold`'s title and every `SectionLabel` carry `heading()`, so
  TalkBack's heading navigation and the VoiceOver rotor can jump between sections on every
  settings screen.
  *Added with Task 8* (from main's adhkar merge): the time-format sheet's two options carry the
  selected state in a `selectableGroup()` and keep `TaqwaRow`'s radio role. On Android the chosen
  clock therefore offers no click, which loses only a close of the sheet; choosing it again would
  just store it again. The Prayer header's Adhkar and Tasbeeh glyphs are
  `Role.Button`. The Quran search's Surahs and Ayahs labels are `SectionLabel`s too, and so they
  are headings. They are interface text, not Quran text.

## 5. Recitation-only Quran

The rule everywhere ayah text appears: the Arabic is invisible to screen readers; the ayah is
named by number; where a translation is shown it follows, tagged with its own language.

- **Ayah cards** (translation view): the card is one node whose spoken text is
  "Ayah 255. <translation>" (`quran_ayah_n`, then the translation in a
  `SpanStyle(localeList = LocaleList(translationLanguage))` run). State: "Bookmarked" and/or
  "Being read aloud" when they apply; `selected` when selected. Double-tap selects, as a tap
  does. One custom action, "Play" (or "Pause" when this ayah is playing), starts the chosen
  reciter from that ayah — the same call the visible Play makes. Transliteration is not spoken.
  *Amended after Task 9:* the card **never sets the semantic `selected`**. On Android that key
  made every unselected card (285 of Al-Baqarah's 286) say "Not selected" and turned each card
  into a checkable node,
  and any state of the card's own took the place of Android's "Selected". Instead, selection is
  the first of the card's states, in its own words: "Selected, Bookmarked, Being read aloud", as
  each applies (new string `quran_a11y_selected`). "Being read aloud" is the phone's voice reading
  the translation, never the Quran. The card has **four** custom actions, offered on every card:
  Play (Pause only while this ayah is playing *and* the voice is going; a paused ayah resumes on
  Play), Bookmark or Remove bookmark, Copy and Share. Only the drawn Arabic, the transliteration
  and the translation row are cleared from semantics, not the whole card. The selected card's
  row of buttons therefore keeps its own nodes, for Voice Access and Voice Control ("tap Copy"),
  which do not offer custom actions by name.
- **Basmala header** (reader) and the **size preview** (reading sheet): hidden.
- **Search hits** and **bookmark rows**: the Arabic line is hidden; the reference and the
  translation remain.
- **Mushaf**: each ayah gets one invisible stop on the first line it appears on, over the span of
  its first run of words, labelled "Ayah 255", with the same double-tap (select) and Play action.
  Later runs of the same ayah on the page get no stop, so a long ayah is one stop, not three.
  Stops on one line are ordered right to left with `traversalIndex` inside a per-line traversal
  group. The basmala line is hidden. Surah bands (the surah's name) stay readable: names are not
  recitation. The pure part — which runs are first — lives in `MushafLayout.kt` and is unit
  tested.
  *Amended after Task 10:* a double-tap toggles the selection, as a tap does, so a stop states
  "Selected" while its ayah is selected (`quran_a11y_selected`, as on the card). It never sets the
  semantic `selected`. A stop has **no** "Being read aloud" state for the ayah being recited.
  On the cards that phrase means the phone's voice reading the translation, so on the Mushaf it
  would suggest a phone voice reading the Quran. A state that moved with the reciter could also be
  announced over the recitation. The recited ayah is marked by its action alone, which says Pause
  while the voice is going on it (Play once paused there), as on the card. *Added by the closing
  fix wave:* the page number under the frame says "Page 42" and a band's ayah count says
  "286 ayahs", in the interface's digits. Both had been bare numerals (new string
  `quran_a11y_page_n`, plurals `quran_a11y_ayah_count`).
- **Widgets**: the Android card's bitmap gets a content description; the iOS card becomes one
  accessibility element. Both say "Ayah of the day, Ar-Raʿd, ayah 28. <translation>" (surah name
  in the interface's script; translation only when the widget shows one). The sentence is
  assembled by one function in `:widgetcore` so the two platforms cannot drift. New strings:
  Android `widget_ayah_a11y_reference`, iOS `ayah_widget_a11y_reference`.
- **Settings preview** of the ayah widget: the Arabic is hidden.
- **Surah names and dhikr** stay readable (not recitation).
- **Cost, told to Mohamed**: braille users lose the Arabic too; there is no braille-only label.

Language tagging is honoured by VoiceOver (CMP 1.12 maps `localeList` to
`UIAccessibilitySpeechAttributeLanguage`; verified in the sources). Whether Compose on Android
emits a `LocaleSpan` for it is checked in the plan; if not, TalkBack reads the translation in the
interface voice, as it does today.
*Amended after the closing review:* it does. ui-text-android 1.12.1 turns the run's `localeList`
into a `LocaleSpan` (`AndroidAccessibilitySpannableString.android.kt:186`), and the delegate's
`setText` hands that text to TalkBack. Whether TalkBack switches voice for it is still to be
heard on a device.

## Strings

Every Compose string goes into all eight folders (`values`, `-ar`, `-bn`, `-fr`, `-id`, `-in`,
`-tr`, `-ur`; `values-in` identical to `values-id`), Android app strings into seven, iOS
`Localizable.strings` into seven `.lproj` folders. `scripts/check-strings.sh` must stay clean.
Compose resources never carry a backslash-escaped apostrophe; Android app resources escape or use
’. Bengali is written in the file's own NFC codepoints. The translations are mine and are listed
for Mohamed's review in the report.

## Verification

- Unit tests for every pure rule (first runs, countdown parts, spoken ayah, widget sentence).
- Compose UI tests on the iOS simulator target for the shared controls' semantics, if the
  `compose.uiTest` harness runs there within a bounded effort (plan task 2); otherwise device
  dumps alone.
- Android: `uiautomator dump` on a throwaway emulator (port 5558, `-read-only`) shows
  content-desc, checkable/checked/selected for each control; screenshots at 100 % prove nothing
  visible moved; the Mushaf at 200 % shows no cut words. The S23 for a final real-device pass.
- iOS: a dedicated simulator; the accessibility tree read with a launch-argument-gated dumper
  kept out of the repository; the escape path exercised by calling `accessibilityPerformEscape`
  on the root after pushing a screen.
- `scripts/test.sh`, `scripts/check-strings.sh`, `:androidApp:assembleDebug` and
  `scripts/ios-build.sh` green before each commit that touches them.
- *Added by the closing review:* the iOS UI tests read Compose's semantics tree, not UIKit's or
  Android's mapping of it. So the branch merges only after the listening checklist in plan Task 12
  (Step 2b) has been run with TalkBack and VoiceOver on real phones.

## Risks

- `clearAndSetSemantics` on a card removes everything beneath it: every state a card used to
  expose (bookmarked, read aloud) must be re-added as state, which the design does.
  *Amended after Task 9:* it also removed the selected card's row of buttons, so the card is not
  cleared as a whole. Only the pieces it draws are, and the states are still re-added on the card.
- Compose sorts a row's nodes left-to-right unless the root view is RTL; the Mushaf stops need
  `traversalIndex` on every platform, and iOS honours it (`Accessibility.ios.kt:1990`).
- The iOS `BackHandler` is deprecated in favour of `NavigationEventHandler` but works in 1.12;
  it is one line to swap later.
- Widget strings on iOS are looked up by hand (the Swift file is also compiled into the app,
  where the extension's strings table is absent), as the placeholder already does.
