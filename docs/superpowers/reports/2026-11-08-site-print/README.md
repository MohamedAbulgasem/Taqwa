# Printing a city page: the foot over the rows (8 Nov 2026)

Mohamed printed taqwa.world/prayer-times/cape-town-south-africa/ from his Mac and from his phone.
On sheet 1 the print foot (the Taqwa mark, "Cautious times · MJC, Cape community calendar… Taqwa
is not affiliated with any of them" and the address) was drawn over the rows for the 21st and 22nd,
and those rows were squashed. Sheet 2 started with the heading row again.

The fix changes only `site/assets/style.css`, and only how a page prints. No prayer time, date or
word changed. The screen layout is identical, element for element.

## Cause

There are two faults. Either one alone puts the foot over the rows.

1. **The phone table leaked into print.** The phone rules were `@media (max-width: 720px)`. Two
   kinds of print fall under 720 CSS px:
   - **Chrome with any print-dialog margin on A4.** A4 is 794 px; 0.4 in margins leave 717 px.
   - **Every iPhone print.** In iOS printing, media queries follow the phone's screen (402 px),
     not the paper. This was measured in iOS Safari with a probe page.

   The month then printed with the phone's two-line rows (the weekday under the date, centred
   cells), about twice as tall, and a 30-day month no longer fitted one sheet.
2. **The foot had no room of its own.** It was `position: fixed; bottom: 8mm` with
   `@page { margin: 0 }` and no bottom padding. Once a month reached the bottom of a sheet, its
   rows ran under the foot.
   - In Chrome, this happens whenever the month spills: from fault 1, the dialog's Scale, or the
     detailed view on Letter.
   - WebKit (Safari, every iPhone browser) prints a fixed box **once**, on one sheet, at a height
     set by the window. It does so over whatever rows are there, even when the month fits:
     Cape Town's 27th–29th on Letter, London's 28–30 December.

Three WebKit facts shape the fix. All three were measured.
- **WebKit splits a table row across sheets and repeats no heading row**, even with
  `tr { break-inside: avoid }`. Seen on a 120-row test table, and in iOS Safari's own preview of
  Urdu Toronto before the fix (`ios/before-letter-ur-toronto-december-button.png`).
- **WebKit supports no unprefixed `box-decoration-break`**, so a sheet can't be given its own padding.
- **An iPhone's sheet is short.** Ruler pages printed in iOS Safari measure about 643 × 860 CSS px
  on A4 and 662 × 803 on US Letter.

## Fix (`site/assets/style.css`)

- **Phone rules are for screens.** The three city-page blocks became
  `@media screen and (max-width: 720px)`. On a screen this is the same query; on paper it never
  matches.
- **The foot is in the flow by default.** It follows the months, kept whole, 3 mm below them.
  Nothing can sit under it in any engine.
- **Every engine:** `.tt tr { break-inside: avoid }` and `.month-head { break-after: avoid }`.
- **Chrome 130+ and Firefox.** The condition is
  `@supports (box-decoration-break: clone) and (not (font: -apple-system-body))`. The second
  half keeps WebKit out even once it learns the property: it prints a fixed box once, over the
  rows.
  - The foot is fixed, so every sheet repeats it at its bottom.
  - City pages get `padding-bottom: 26mm` with `box-decoration-break: clone`, so every sheet
    reserves that room and no day can enter it. The tallest foot (two lines of "whose times these
    are") is 16 mm, set 8 mm up.
  - The cloned 12 mm top padding is each sheet's top margin. A month that runs on starts 12 mm
    down, not at the paper's edge.
  - **Narrow sheets** (the dialog's Scale above about 110%, or Custom margins):
    - Cells may wrap, and the detailed view's lines go under their times, so every column stays
      on the paper.
    - Under 540 px (Scale about 150% and up) the Hijri column and the Jumuʿah pill give way, as on
      a phone; the side margins narrow to 8 mm and the reserve grows to 32 mm.
  - **Right-to-left pages** get a left-to-right root around a right-to-left body. Chrome prints
    an RTL page shifted sideways at any Scale other than 100% (a bare `<html dir="rtl">` page does
    it too), and loses its first or last columns off the paper. At 100% this change moves the page
    by 0.25 pt.
- **Everyone else (WebKit)** must fit a month and its foot on one sheet. So the month is set
  tighter: 11 px table text instead of 12, 1 px × 5 px cells, line-height 1.15, slightly smaller
  headings, and a heading row that may wrap (French "LEVER DU SOLEIL"). The 12 mm top padding
  stays, because with print-dialog margins WebKit draws the sheet higher and 8 mm cut the title.
- **Chrome's sheet at default settings looks as before.**

## Evidence

All of it was printed from the built site (`python3 site/build.py`), with timetables generated
for 8 Nov 2026 10:00 UTC. The tools are in `harness/`:
- `matrix.py`: the checks below.
- `cdp_print.py`: Chrome print-to-PDF over DevTools.
- `wkprint.swift`: WebKit print-to-PDF through WKWebView, Safari's engine.
- `pdftool.swift`: PDF text with boxes, and PNGs.
- `measure.py`, `foot.py`, `overflow.py`: print-layout measurements.
- `content_diff.py`, `layout_diff.py`: the content and screen proofs.

Each matrix scenario is printed five times: as it is, then with only the foot, everything but the
foot, only the day rows, or only the heading rows visible. `visibility` moves nothing, so the
copies locate each part on every sheet. The checks:
- no text line meets the foot's box;
- every day prints whole, or, on narrow sheets where rows wrap on purpose, every time prints;
- every sheet with days has a heading row;
- no text runs off the paper;
- Chrome: the foot is on every sheet;
- WebKit: every month sits on one sheet;
- no blank sheet.

Chrome's dialog margins are modelled as smaller paper, because `@page { margin: 0 }` overrides
`printToPDF`'s own margins.

### Reproduced, then fixed

| Case | Files | Before | After |
|---|---|---|---|
| Chrome, A4, 0.4 in dialog margins (Mohamed's sheet) | `*/chrome-cape-town-a4-margins-0.4in*` | 2 sheets, phone rows, foot over the 21st–22nd | 1 sheet, foot clear |
| Chrome, A4, Scale 150% | `*/chrome-cape-town-a4-scale-1.5*` | sheet 1 ends under the foot | breaks after the 25th, heading repeated, foot clear |
| Chrome, A4, Scale 150%, French detailed view | `*/chrome-fr-paris-detailed-a4-scale-1.5*` | phone rows, foot over rows | cells wrap, every column on the paper |
| Chrome, A4, Scale 125%, Arabic | `*/chrome-ar-makkah-a4-scale-1.25*` | shifted off the paper (dates gone), foot over a row | in place, every column, foot clear |
| Chrome, A4, 1 in Custom margins, Urdu detailed view | `*/chrome-ur-chicago-detailed-a4-custom-margins-1in*` | phone rows, foot over rows | cells wrap, all eight values per day, foot clear |
| Chrome, Letter, 0.4 in, Urdu | `*/chrome-ur-toronto-letter-margins-0.4in*` | one month a sheet | unchanged |
| macOS WebKit, Letter, 36 pt | `*/webkit-cape-town-letter-margins-36pt*` | foot over the 27th–29th | foot after the month |
| macOS WebKit, A4, 18 pt | `*/webkit-london-a4-margins-18pt*` | foot only on sheet 2, over 28–30 Dec | foot after December |
| macOS WebKit, A4, 18 pt, Urdu | `*/webkit-ur-toronto-a4-margins-18pt*` | foot over the last rows | foot after December |
| iOS 27 Safari, A4 | `ios/before-a4-cape-town.png`, `ios/after-a4-*.png` | 2 sheets, phone rows, foot across sheet 1 | 1 sheet per month (Cape Town, French Paris, Arabic Makkah, Urdu Toronto's December) |
| iOS 27 Safari, US Letter | `ios/before-letter-*.png`, `ios/after-letter-*.png` | 2 sheets, foot over rows 11–12, no heading on sheet 2 | 1 sheet each, foot below, room to spare |

The iOS images are iOS Safari's own print preview. They came from a new simulator, created for
this check and deleted afterwards.

### Final runs

**Chrome 157, default dialog**
- 18 pages: Cape Town; Paris (en, fr, ar); Toronto (en, ur, ar); Mississauga (ur); Chicago (ar);
  London (en, ur, bn); Makkah (ar, bn, id); Istanbul (tr); Jakarta (id); Oslo (ur).
- Modes: the browser's print, each month's own Print button, and the detailed view. Papers: A4 and
  Letter.
- **132 / 132 pass:** every day whole, the heading on every sheet, the foot on every sheet and
  never touched, and every month on one sheet.

**Chrome, narrow sheets**
- LTR (7 pages, en fr bn id tr): Custom margins 0, 0.75 and 1 in × Scale 80–200%. **630
  scenarios.** Every check passes except one count: in all 90 Bengali London scenarios the PDF text
  shows fewer times than expected, because PDFKit breaks Bengali numerals apart. That count is the
  same at every setting (326 / 163 / 440 by mode), and it is the same in the A4 100% print, where
  every row is verified whole. Before the fix: 468 of 504 comparable scenarios failed.
- RTL (6 ar/ur pages): margins 0, 0.5 and 1 in × Scale 80–200%. **612 / 648 pass.** The 36 left
  need 1 in margins and 200% together, a text column about 300 px wide. Before the fix, Arabic and
  Urdu failed at every Scale other than 100%.

**macOS WebKit (WKWebView, Safari 27's engine)**
- The same 18 pages and modes, A4 and Letter, margins 0 and 36 pt. **232 / 264 pass** every check.
- The other 32 are the Bengali pages, whose WebKit PDFs carry no text for Bengali numerals. They
  pass on geometry: one sheet per month, the foot after the last month and never touched. Two were
  also checked by eye.

**iOS model**
- All 133 city pages measured in print layout at the iPhone's sheet widths, with the WebKit rules.
- Tallest sheet on A4: 800 of 860 px (7% spare), French Algiers. On Letter: 786 of 803 px (2%),
  Urdu Toronto's December printed by its button.
- iOS Safari itself printed that page with about 11% to spare
  (`ios/after-letter-ur-toronto-december-button.png`).

### Independent verify lens

A workflow of independent agents tried to break the first version of the fix.
- **Coverage:**
  - Chrome: about 51,000 PDFs, all 133 pages, scales, A5/Legal, landscape, Chrome's header and
    footer.
  - WebKit: 1,472 scenarios on all 133 pages, all passing.
  - A code review, and independent screen and content proofs.
- **Its skeptics reproduced every serious finding.** All of them are fixed here:
  - columns cut off on narrow Chrome sheets (now cells wrap);
  - RTL pages shifted at any Chrome Scale (now the LTR root);
  - a future Safari unprefixing `box-decoration-break` (now the `-apple-system-body` guard);
  - French tables past the text column on an iPhone (now the heading may wrap);
  - the reserve applied to every page (now city pages only).
- **Its minor findings are under "Left as they are".**

### Nothing else changed

- **Content.** Every built file was compared before and after. All 195 HTML files are identical
  once the stylesheet's cache token (`style.css?v=…`) is ignored, and that includes all 249 tables
  and 7,152 day rows, byte for byte. The only other changed file is `assets/style.css`.
- **Screen.** In screen media, with the clock frozen, the box and 24 computed properties
  (display, font, colours, margins, paddings, borders, grid…) of every element were compared before
  and after:
  - 14 pages: city pages in all 7 languages, the English, Arabic and Urdu home pages, the index,
    support, privacy and how-Taqwa-checks;
  - 11 widths from 320 to 1440 px, in light and dark.

  **308 / 308 layouts identical** (219,494 element boxes). Pixel screenshots carried a few hundred
  pixels of rasterisation noise under load (the same in a before-vs-before control), so layout is
  the proof. The same tool in print media reports every page as different, as it should.
- `python3 site/build.py --check`: links, assets, structured data, sitemap and timetables all good.
- `./gradlew -p tools/timetables generate -Pofficial=…`: runs. `git status` shows no tracked file
  changed but `site/assets/style.css`.

## Left as they are

**Trade-offs and limits, none against the brief:**
- **WebKit prints the foot once**, after the last month. A whole-page print of a two-month page has
  it on the second sheet only; a month printed by its own button always has it.
- **WebKit's month is a little tighter than Chrome's**: 11 px text against 12. Urdu Nastaliq
  descenders can touch the row rules there; the text stays legible.
- **Wasted sheets in Chrome on US Letter.** With the detailed view, 6 of 132 default prints (mostly
  Urdu) put the last lines of the detailed-view note on a second sheet with the foot. With 0.4 in
  Custom margins, a 31st or a clock-change note can go to a sheet of its own. No row is covered or
  split, and the heading and foot are right.
- **An iPhone on US Letter with the detailed view** can put the foot alone on a second sheet.
- **Extremes past the brief:**
  - 1 in margins with 200% Scale on RTL pages;
  - A5 paper;
  - landscape on WebKit, where the foot can go to a second sheet.

**Not tested:**
- Firefox (not installed). It takes Chrome's path.
- The macOS Safari app itself. Its engine was tested through WKWebView, but not its own default
  margins.
- Real printers. Only print-to-PDF.
