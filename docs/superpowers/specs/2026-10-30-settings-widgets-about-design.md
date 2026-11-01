# Settings: a Widgets screen, a slimmer About section, and About Taqwa as a profile

Owner-approved on 30 October 2026 from the design canvas https://claude.ai/artifact/KTMdQmmQZ9HE1Pzc3v2big
(five artboards: Settings, About Taqwa, Widgets · Android, Widgets · Android when the home screen said no,
Widgets · iPhone). The canvas is the visual source of truth for spacing, type and colour; this document
is the behaviour and the decisions behind it.

## Why

- People who skip the widget step in onboarding never find the widgets again: today they live at the
  bottom of Appearance, under the theme. Widgets get their own row and screen, and the screen makes
  adding a missing widget one tap (Android) or a few clear, illustrated steps (iPhone, or an Android
  home screen that refuses apps' requests).
- The Settings root is busy. The About section keeps one row; Rate, Share and Attribution & licences
  move into About Taqwa, which becomes the app's profile page.

## Goals

- Settings root: APP is Appearance, Widgets, Language; ABOUT is About Taqwa alone.
- A Widgets screen that is honest about what is on the home screen, never offers what is already
  there, and never leaves a tap that does nothing.
- About Taqwa redesigned (option B) with every row it and the root had, and nothing lost.

## Non-goals and anti-goals

- No change to the widgets themselves (widgetcore, Glance, WidgetKit), to onboarding's widget step,
  or to how pinning and add paths are decided (`WidgetPinRequester`, `widgetOffer`, `WidgetAddPath`
  stay as they are; the screen is a new consumer of them). Placement itself changed in review round 2
  (31 Oct): the iPhone reports its Lock Screen widget apart from the prayer widget, and a read that
  cannot tell is no answer rather than "both placed" (see the root and the offer below). Onboarding's
  widget step never read placement, so it is unchanged. One name changed by the owner's decision
  (31 Oct): the iPhone's widget gallery lists the prayer widget as "Prayer times" in each language,
  its card's name, instead of "Taqwa", so the iPhone steps name it as the card does. Android's picker
  names stay as they are.
- No prompt, badge or dot anywhere that nags: the root row's value is information, not a call to act
  (the quiet-app rule, privacy spec §6.5: Rate and Share stay rows the reader comes to).
- A failure: a status that claims a widget is missing, or that it is there, while placement is unknown;
  a button that does nothing on a launcher that refuses pins; text that truncates in any of the seven
  languages.

## Settings root

- APP card: Appearance (theme name), **Widgets**, Language. The Widgets value says how many of the two
  home-screen widgets (prayer, ayah) are on a home screen, read the way Appearance reads placement today
  (on every ON_START, off the main thread):
  - both placed: "All added"; one: "1 of 2 added" (digits localised); none: "Not added";
  - placement not read yet, or the platform cannot tell (no AppWidgetManager, a WidgetKit failure, or
    no answer within its 2 s wait): no value at all. A read that cannot tell after one that answered
    keeps that answer, so an iPhone's pull of Control Center (a resume) does not take the value away.
    (Review round 2: the source's old `WidgetPlacement.Unknown` was the same value as both placed, so
    it read "All added" and put a check on every card. The source now answers null, no answer.)
  The iPhone's Lock Screen widget is not counted: the row counts the home screen. Placement does report
  it (review round 2), as its own kind, `TaqwaLockScreenWidget`, never folded into the prayer widget,
  for the Lock Screen card's own check.
- ABOUT card: About Taqwa with the version value, alone. The Attribution, Rate and Share rows and the
  code that served them move to About Taqwa.

## Widgets screen (new, `WidgetsSettingsScreen`)

Order, top to bottom, all in the settings idiom (`SettingsScaffold`, `SectionLabel`, `SettingsCard`,
`SettingsNote`, 24 dp gutter):

1. Title "Widgets", then an intro note: "The next prayer and today’s ayah on your home screen, without
   opening Taqwa." (iPhone: "…on your Home Screen and Lock Screen…").
2. **WIDGET BACKGROUND first** (the owner's change: it is the setting people change most): a
   four-option segmented control (`TaqwaSegmented` or the same look) — System, Light, Dark,
   Translucent (Android) / Frosted (iPhone), the existing `widgetBackgroundTitle` labels. Under it a
   note: the translucent/frosted subtitle when that option is chosen, otherwise "For every Taqwa widget.
   System follows your phone’s light and dark setting." Rules: it must read to TalkBack and VoiceOver
   as a group of radio options with the selected one announced (the reason the old screen used check
   rows); no label may truncate or ellipsise in any language at 360 dp — a label may wrap to two lines
   (centred, the segment growing) and at large font scales must still be complete. If the four
   segments cannot meet that, fall back to a 2 × 2 grid of the same pills and say so in the build log.
3. **PRAYER TIMES** card: the existing `WidgetPreview` (both sizes on the wallpaper swatch) inset 12 dp
   at the top of the card; under it the name "Prayer times" and a line ("The next prayer with a live
   countdown, and today’s times. Small or wide." / iPhone "…Small or medium."); then the offer:
4. **AYAH OF THE DAY** card: the existing `AyahWidgetPreview` the same way; "Ayah of the day"; "A new
   ayah each day, with its translation. Wide or large." / iPhone "…Medium or large."; then the offer.
5. **LOCK SCREEN** card, iPhone only: a dark stage with the current time and a lock-screen accessory
   drawn from the widget mirror as the real rectangular accessory draws it (TaqwaWidgetViews.swift): the
   next prayer and its time on the first line, then "then" and the prayer after it ("then Isha · العشاء")
   on a smaller line under them. The accessory shows no countdown, so the picture has none (review round
   2; it was "in h:mm" here, which the widget never says). Its first line is the accessory's own
   "Maghrib in" phrase beside the time, in every language (the owner's decision, 31 Oct); "Next prayer at a glance";
   "Under the clock on your Lock Screen, round or wide."; then its own status: nothing while placement
   is unknown, the check and "On your Lock Screen" when a Taqwa Lock Screen widget is placed, and
   otherwise a "Show me how" row that expands the lock-screen steps. Nothing can ask iOS for it, so it
   never has a button. (Review round 2: it once had no status, on the false premise that placement
   does not track it.) The Lead's follow-ups (31 Oct): with large text the picture's lines shrink as
   the accessory's do, to 60 % of their size, and only then wrap, so none is cut; and on the dark
   theme the steps' Lock Screen phones are outlined in the dark ink, since their slate is lost on the
   dark tiles (1.1:1) and stands out on the light ones (13.6:1).

**The offer** under a widget, from `widgetOffer(...)` exactly as Appearance decides it today:
- `None` (placed): a status line, the amber check glyph and "On your home screen" (iPhone "On your Home
  Screen"), secondary text.
- placement not read yet, or a read that cannot tell (no AppWidgetManager, an iOS WidgetKit failure or
  its 2 s timeout): nothing, no check, no button and no steps (the line or button fades in when the
  answer arrives, as today). A re-read that cannot tell keeps the last answer.
- `Pin`: the primary pill button "Add to home screen" (`TaqwaPrimaryButton`), which calls `askToPin`;
  taken → nothing more (the next ON_RESUME read flips it to the status line); refused → that widget
  joins the saved refused set and its card shows the steps panel instead.
- `Instructions` (iPhone always, Android after a refusal): the steps panel —
  - Android (launcher refused): a warm tinted panel inside the card: "Your home screen doesn’t let apps
    add widgets." / "It only takes a moment by hand:" then three numbered steps with small
    illustrations: touch and hold an empty spot (or pinch); tap Widgets; find Taqwa, then drag
    <widget name> into place.
  - iPhone, `IOS_HOLD_ICON`: "HOW TO ADD IT", three numbered illustrated steps: touch and hold the
    Taqwa icon on your Home Screen; choose <widget name> and a size; it lands on your Home Screen, drag it
    wherever you like; then the fallback line "No widgets when you hold the icon? Touch and hold an empty
    part of the Home Screen, tap Edit at the top (on iOS 27 and later, a brush), then Add Widget, and
    search for Taqwa." (Review round 2: the corner button shows its title on iOS 26, "Edit" in English
    but "+" in French and Turkish, and a brush from iOS 27 in every language, seen on the 26.2, 27.0
    and 27.1 simulators; each language names its own title and the brush.)
  - iPhone, `IOS_PLUS_BUTTON`: the same panel with that path's three steps (touch and hold an empty part
    of the Home Screen; tap the + in the top corner; search for Taqwa and choose <widget name>), and no
    fallback line. The + of iOS 16 and 17 opens the gallery straight away: there is no Edit menu there.
  - When more than one card needs steps, the first shows them expanded and each later one shows a
    "Show me how" row that expands the same steps with its own widget name (no wall of repeated text).
  - A panel that replaces a refused button is announced to TalkBack (as `AnnouncedWhen` does today).
    Review round 2, heard on the emulator's TalkBack 16: the announcement spoke, but the tapped button
    was gone and TalkBack then put its focus on another card's title. So while a screen reader runs,
    a panel the reader's own tap opened takes focus instead of being announced: a refused button's at
    its first words ("Your home screen doesn't let apps add widgets."), a "Show me how" panel at its
    first step. Without a screen reader nothing takes focus, since moving focus scrolls (the owner's
    rule: the screen is unchanged at default settings). "Show me how" carries the expand action, which
    TalkBack speaks as "collapsed"; the prayer card's preview is hidden from screen readers, as the
    Lock Screen's picture is; the step number's disc grows with the text size. The Lead's follow-up
    (31 Oct): under a screen reader no panel is a live region, since a refused panel that had taken
    focus became one when another card's tap moved the focus on; how a panel opens is decided once,
    as it appears, and the screen forgets the tap once focus has moved, so steps that go and come back
    later take no focus.

## Appearance

Theme card and its note only. The widget background, previews and add rows leave it; its parameters
and route arguments shrink accordingly.

## About Taqwa (option B, the profile)

- Back chevron, no large title text: the hero's name is the screen's heading (the pane is still titled
  "About Taqwa" for accessibility).
- Hero, centred: the app icon tile at 88 dp (the existing `AppIconTile`, scaled, 22 dp corners) — a tap
  opens taqwa.world, as today; "Taqwa" at 30 sp ExtraBold; the tagline; a small outlined chip
  "Version 1.3.0".
- Two outlined pill buttons side by side: Rate Taqwa (star glyph in the accent; opens the store as the
  root row does today) and Share Taqwa (share glyph; the same share text as today).
- PRIVACY card: the three promises, each with a 36 dp rounded square of the accent at low alpha holding
  an accent stroke glyph (phone, cloud with a down arrow, a circle with a slash), title and body; then
  a Privacy policy link row.
- OPEN SOURCE card: Source code (GitHub), Licence (GPL-3.0), Attribution & licences (in-app, chevron).
- HELP card: Report a problem (the mail with the crash report, as today), Website (taqwa.world).
- External-link rows keep their glyph and mirroring; the share and star glyphs are not mirrored.

## Text

About thirty new strings, English first, then Arabic, Bengali, French, Indonesian, Turkish and Urdu,
with the `values-in` twin from `tools/sync-indonesian.py`; `scripts/check-strings.sh` clean. Use each
platform's own localised words for its UI ("Home Screen", "Lock Screen", "Customize", "Edit", "Add
Widget" on iPhone; "Widgets" on Android), consistent with the existing onboarding widget strings in each
language. Keys no screen uses any more are removed in every language.

## Accessibility, layout, platforms

Headings on section labels; a11y labels on icon-only targets; touch targets ≥ 48 dp; RTL (Arabic, Urdu)
mirrors as the rest of Settings does; light and dark; 360 dp and 384 dp widths; font scale 1.0 and 1.3.

## Constraints from parallel work (the Lead, 30 Oct 2026)

- `SettingsScaffold` in SettingsRootScreen.kt: leave its signature and its scroll modifier alone
  (notifications-layout-b adds an optional `anchor` there). About's title-less layout is its own small
  composable, not a change to `SettingsScaffold`.
- The three privacy promise rows keep their string keys and texts untouched (`about_stays_*`,
  `about_online_*`, `about_nothing_*`; watch-companion rewrites them under Ruling 63). The online row
  reads `about_online_body` here with a `TODO(watch-companion merge)` to call `aboutOnlineBody(isIosPlatform)`;
  no new key may start with `about_online_body`; no location string is edited.
- The root keeps a recognisable App group and About group with room for a Watch section between them
  (watch-companion adds `WatchFaceRow()` after Language, later a `WatchSection()` between the groups).
- New strings go in as one block after the existing about_/settings_ keys in every values*/strings.xml,
  not at the end of the file.
- Devices: an Android emulator on port 5572 or higher (never 5562, 5564–5568, or 5558), started only
  when needed and stopped by serial; its own iPhone simulator device; never the watch simulators.

## Left to the builder

Composable decomposition, file layout beyond `WidgetsSettingsScreen.kt`, the glyph drawings (in the
house style of `Glyphs.kt`), the exact illustration shapes in the steps, and test seams.
