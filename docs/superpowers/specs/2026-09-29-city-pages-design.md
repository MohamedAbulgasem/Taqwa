# Taqwa city prayer-time pages on the never-early engine — design

**29 September 2026.** The rebuild of taqwa.world's city pages, held since 25 September (docs/BUILD-LOG.md,
"The city pages held"), on the engine that replaced adhan2 on 28 September (`docs/superpowers/specs/
2026-09-26-taqwa-prayer-times-engine-design.md`, "the spec" below; its §7 "Website" paragraph is binding).
Direction B of the 28–29 September design round ("show the working"), with C's month timetable, as the
owner chose. Plan: `docs/superpowers/plans/2026-09-29-city-pages.md`.

Inputs: `.superpowers/sdd/2026-09-27-prayer-engine/site-design/` (brief, context, critique, the B and C
artboards; not in the repository), the site under `site/`, the generator `tools/timetables`, the app's
`AboutTimesScreen.kt` and `strings.xml`.

## 0. The owner's decision and the rulings this design records

The owner, 29 September 2026: "Some great options, I think we should go with B with C's timetable design
and we need to make the 'Where these times come from' section be collapsed by default but expands on tap
or click and we should use the official 'get the app' components for the android and iOS install buttons.
… you can make the 'get the app' without the store buttons for now and maybe just a coming soon for now
until we launch and keep a note so on launch we don't forget to show it in there along with the ones on
home screen."

Controller's rulings (binding):

- **R96 — layout.** Direction B: times first as the app's Prayer screen card, with B's quiet foot inside
  today's card ("This card is the app's Prayer screen…"; the app link follows R97's coming-soon state);
  B's desktop layout. The month tables are C's (a real `<table>`, past days folded, `hreflang` language
  links). B's phone fold fixed: today's six times readable on the first screen of a 390 × 844 phone.
- **R97 — "Where these times come from".** B's explainer (the three steps, the stat tiles, the minute
  ruler, and for a cautious place the members and "which timetable decides today") is collapsed by default
  and expands on tap or click (native `<details>`/`<summary>`; works without JavaScript; keyboard and
  screen-reader accessible). Spec §7's authority line and the stamp's proof sentence stay visible as the
  summary, never inside the fold. The ruler's bars and every day total on the page come from the same
  stamp count.
- **R98 — get the app.** The install buttons are the official store badge components the site already has
  (`site/stores.json`, the home page's badges, the Smart App Banner). Until launch every "get the app"
  place on the city pages shows its coming-soon state with no store links; filling `stores.json` at launch
  switches city pages and home page to the official badges with no other change. The launch step goes into
  `docs/STORE-CHECKLIST.md`, saying where the city pages' app sections are.
- **R99 — text amber.** Small amber text on the light theme uses `--accent-text` #8A6407 (4.5:1 on the
  page, on white and on the today tint); #B5820B stays for large numerals, bars and rings; the dark theme
  keeps its amber. Site only.
- **R100 — the cautious proof sentence.** A cautious place's page shows its stamp's proof sentence like
  every other page; the app's About screen for cautious places gains the same sentence and tiles, with
  strings in all seven locales (§10).
- **R101 — which cities.** City pages only for proven places, "proven" defined exactly from the stamps
  and applied to `cities.tsv`; the rest stay held (§2).
- **R102 — spec §7's other must-haves:** the "How Taqwa checks" page from the stamps; the detailed view
  (both Asr times, end of eating, set-by-rule marks); no link to the other month; past days folded at every
  width; "Print or save as PDF" per month from a print stylesheet that prints every script; seven languages
  (en, ar, ur RTL; tr, id, fr, bn) with logical CSS properties and the ring mirrored in RTL; contrast 4.5:1,
  44 px targets; new copy in all seven languages, reusing the app's strings wherever the app already says it.

Global rules carried from the engine work: the repository is public, so no printed time from any
restricted official table appears anywhere (ruling R69) — the site shows only the engine's own computed
times and the stamps' statistics; Taqwa commits carry no Co-Authored-By trailer.

## 1. What this builds

- The city page rebuilt (§3): the app's Prayer screen as a card, then the authority line and the proof
  sentence as the summary of a folded explainer, then C's month tables with a detailed view and per-month
  printing, then one app section, then the other cities.
- A "How Taqwa checks" page per language, generated from the stamps (§5).
- The index and the home strip come back with the cities that pass the proven rule (§2, §6): **41 cities
  on 29 September, 38 from the first build of 1 October** (ruling R115: only days the gate checked), out of
  the 196 rows in `site/cities.tsv`; 47 rows are un-held there and the generator holds the others of them
  until their tables cover every day shown (§2.1).
- The generator applies the proven rule at every build and drops a city the stamps no longer cover, so a
  page never shows a day the gate has not proven (§9.2).
- The app's cautious About screen gains the proof tiles and sentence (§10).

## 2. Which cities: the proven rule (R101)

Spec §7 says "city pages only for proven places". Read from the stamps (spec §5; `tools/timetables/
official/stamps/<entry>.json`, one per gated entry, statistics only), **a city is proven for a page when
all of the following hold at the city's own point, as the engine resolves it for a user there who has
changed nothing:**

1. **Class.** The resolution's class (`Resolution.entryClass`, which a unit can demote — outside an
   authority's checked units an A/B entry is D there) is **A, B or C**. A and B are the classes the spec
   calls checked ("{Authority} timetable"); C is cautious times whose own stamp is a gate over every
   member's printed table (ruling R38), so a green C stamp proves the combined times never early against
   every timetable followed there. D ("{Authority} method, not yet fully checked", "Calculated by Taqwa")
   is not proven, whatever its stamp holds.
2. **Measured here.** `Resolution.measured` is true (the app would otherwise say "Not measured for
   {unit}" and claim no figure).
3. **A green stamp for the resolved entry**, i.e. a stamp file exists and its `broken` is 0; where the
   stamp carries `units`, the resolution's `unitId` is one of them and that unit's `broken` is 0.
4. **Every day the page shows was checked (ruling R115, amended after the review of Tasks 1–3):** each
   shown date lies inside the days the gate actually compared, which each stamp records as runs of dates —
   for class A/B the place's unit's runs (or the entry's, where the stamp has no units), for class C every
   member's runs at the place (the cautious stamp's rows within class C's reach of the city, else the
   member's own unit covering it). A stamp's first–last span is not enough: tables have holes (every
   Diyanet capture lacks 26 October – 31 December 2026; Toronto's members lack 1–6 and 28–30 November).
   Past days of the current month are shown (folded) and printed, so they count. The held reason names the
   first unchecked date and the timetable.

Ambiguities and how they were read (the reading that never publishes an unproven time): a stamp that
starts inside the current month (Diyanet's begins 25 September 2026) holds the city until the month
turns; a cautious entry proven only by a Mawaqit survey (`gb.cautious`, `de.cautious`, `nl.cautious` …)
has no stamp days of its own at most places and is held wherever it is not measured; a cautious page
shows no "at most" figure at all (ruling R105: a cautious entry's worst is entry-wide — Oslo would have
read Trondheim's 148 minutes), in the app as on the site.

**The rule runs in the generator on every build** (§9.2), so a stamp that ends before the next month
holds the city automatically from the first of the month (London Unified's stamp ends 31 December 2026:
London's page goes on 1 December unless a 2027 table is gated first) and a stamp that begins inside the
current month publishes it on the first of the next (İstanbul, Ankara on 1 October). The build prints one
GitHub Actions notice per city held by the rule and one warning per published city whose stamp ends
before the last day of the month after the two shown, a month's notice to run the gate on the new table.

### 2.1 Published (41 on 29 September; 38 from 1 October)

*Final, under ruling R115 (the release build of 29 September):* **41 published** — the nine Saudi
cities, Dubai, Doha, Muscat, Tunis, Sfax, Algiers, Singapore, the nine Indonesian cities, the four
Malaysian ones, Bandar Seri Begawan, Johannesburg, Pretoria, Durban, London, Sarajevo, Tirana, Kazan,
Dublin, Toronto, Mississauga and Chicago. Held by R115 although un-held in `cities.tsv`: İstanbul,
Ankara, Paris, Oslo, Brussels and Antwerp (their Diyanet-family tables begin on 25 September, so 1–24
September is unchecked; from 1 October the Diyanet hole of 26 October – 31 December holds them until a
table covers it). From the 06:07 UTC build of 1 October **38**: Toronto, Mississauga (their members lack
1–6 November) and Chicago (November checked against one member only) are held too. The paragraph and
table below are the planner's and Task 5's earlier counts, kept for the record.

*Amended at Task 5 (29 September, after the night's Diyanet Europe and Kemenag merges changed the
stamps):* the rule run over every row of `cities.tsv` on the new stamps publishes **43 tonight and 47
from 1 October** — the table below plus Surabaya, Medan, Semarang, Palembang and Yogyakarta (Kemenag's
five newly fitted kota; `id.kemenag` now 6,570 place-days at 18 places) and, from 1 October, Brussels and
Antwerp (`be.cautious`, whose stamp now runs 25 September 2026 – 31 December 2027, so it is held like
Diyanet's until the month turns). On the same stamps Paris reads 1,157 · 3 · 31 Dec 2027 and Oslo
2,648 · 5 · 31 Dec 2027. `site/cities.tsv` is the record of what is un-held; §2.2's groups are otherwise
unchanged.

Per city: the entry, the class at the city, the figures the page shows (place-days at places, checked
through, at most N minutes after over the starts, read as the app's About reads them: the unit's own row
where the stamp has units, else the entry's), and the page languages from `cities.tsv`.

| City | Entry (unit) | Class | Days · places · through · at most | Languages |
|---|---|---|---|---|
| Makkah, Madinah, Riyadh, Jeddah, Dammam, Taif, Tabuk, Abha, Buraydah | `sa.ummalqura` | A | 13,146 · 12 · 31 Dec 2030 · 2 min | as listed per row (Makkah and Madinah in all seven) |
| Dubai | `ae.iacad.dubai` (Dubai) | B | 20,067 · 12 · 31 Dec 2029 · 2 | en ar ur bn |
| Doha | `qa.calendarhouse` (Doha) | B | 2,963 · 8 · 31 Dec 2026 · 2 | en ar ur bn |
| Muscat | `om.mara` (Muscat) | B | 488 · 1 · 30 Sep 2031 · 1 | en ar ur |
| Tunis, Sfax | `tn.inm` (Tunis; Sfax) | B | 1,519 · 7 · 31 Dec 2026 · 1 | en ar fr |
| Algiers | `dz.marw` (Algiers) | B | 1,783 · 5 · 5 Jun 2027 · 1 | en ar fr |
| İstanbul, Ankara — **from 1 October** | `tr.diyanet` (İstanbul; Ankara) | A | 8,712 · 22 · 31 Dec 2027 · 1 | en tr |
| Singapore | `sg.muis` | B | 1,096 · 1 · 31 Dec 2026 · 3 | en |
| Jakarta, Bandung, Makassar, Banda Aceh | `id.kemenag` (Kota Jakarta; Kota Bandung; Kota Makassar; Kota Banda Aceh) | B | 4,745 · 13 · 31 Dec 2026 · 1 | en id |
| Kuala Lumpur, George Town, Johor Bahru, Kota Bharu | `my.jakim` (WLY01; PNG01; JHR02; KTN01) | B | 9,489 · 13 · 31 Dec 2026 · 1–2 | en |
| Bandar Seri Begawan | `bn.mora` (Brunei-Muara) | B | 355 · 1 · 31 Dec 2026 · 2 | en |
| Johannesburg, Pretoria, Durban | `za.jamiat` (each its own unit) | B | 14,973 · 9 · 31 Dec 2029 · 1–2 | en |
| London | `gb.london.lupt` (London (M25)) | B | 4,015 · 11 · 31 Dec 2026 · 5 | en ur bn ar |
| Sarajevo | `ba.iz` (Sarajevo) | B | 1,095 · 3 · 31 Dec 2026 · 2 | en |
| Tirana | `al.kmsh` (Tirana) | A | 780 · 3 · 31 Dec 2027 · 1 | en |
| Kazan | `ru.dumrt` (Kazan) | B | 6,935 · 19 · 31 Dec 2026 · 2 | en |
| Paris | `fr.cautious` | C | 365 · 1 · 31 Dec 2026 · 38 (Isha, after the earliest timetable) | en fr ar |
| Dublin | `ie.cautious` | C | 365 · 1 · 31 Dec 2026 · 5 | en ar |
| Oslo | `no.cautious` | C | 1,095 · 2 · 31 Dec 2027 · 148 (Trondheim's Isha; see above) | en ur |
| Chicago | `us.chicago` | C | 1,101 · 7 · 31 Dec 2026 · 31 (Fajr) | en ar ur |
| Toronto, Mississauga | `ca.toronto` | C | 1,400 · 6 · 31 Dec 2026 · 7 | en ur ar; en ur |

The featured languages on the home strip stay as `cities.tsv` has them.

### 2.2 Held (158 tonight), by reason

- **Class D, the authority's method not yet fully checked** (the app says so; no page): Abu Dhabi, Sharjah,
  Al Ain (`ae.awqaf`); Kuwait City; Manama; Salalah; Irbid; Beirut, Tripoli, Sidon; Damascus, Aleppo, Homs;
  Baghdad, Mosul, Erbil; Mansoura, Tanta, Assiut (outside ESA's checked units); all ten Libyan cities
  (`ly.awqaf`, class D everywhere, ruling R73); Sousse; Oran (measured, class D at its unit), Constantine;
  Marrakesh, Fes, Agadir; Nouakchott; Khartoum, Omdurman, Port Sudan; İzmir, Bursa, Konya, Antalya,
  Gaziantep, Diyarbakır (outside Diyanet's checked districts); Tashkent, Samarkand, Bukhara; all eight
  Pakistani and seven Indian cities (`pk.karachi`, `in.karachi`); Dhaka, Chittagong, Sylhet, Khulna,
  Rajshahi; Surabaya, Medan, Semarang, Palembang, Yogyakarta (outside Kemenag's fitted kab/kota);
  Pristina; Moscow; Vienna; Zürich, Geneva; New York, Los Angeles, Houston, Dearborn, Washington DC,
  Philadelphia, Dallas, Atlanta, Minneapolis (`us.isna`); Montreal, Ottawa (`ca.isna`); Auckland.
- **Calculated by Taqwa (class D, no authority):** Sanaa, Aden; Malé; Lagos, Kano, Abuja, Nairobi,
  Mombasa, Dar es Salaam, Zanzibar, Addis Ababa, Mogadishu, Djibouti, Dakar, Touba, Bamako, Niamey,
  Ouagadougou, Conakry, Abidjan, N'Djaména, Accra.
- **Class A/B but the stamp does not cover the days shown:** Amman, Zarqa (`jo.awqaf` ends 27 Dec 2025);
  Hebron, Nablus (`ps.iftaa` ends 19 Mar 2026); Cairo, Alexandria, Giza, Port Said, Suez, Luxor, Aswan
  (`eg.esa` ends 30 Sep 2026); Casablanca, Rabat, Tangier (`ma.habous` ends 12 Oct 2026).
- **Cautious, not measured at the city** (the members' proof does not reach it): Gaza; Birmingham,
  Manchester, Bradford, Leicester, Glasgow (`gb.cautious`, survey-proven); Marseille, Lyon, Toulouse,
  Lille, Strasbourg; Amsterdam, Rotterdam, The Hague; Berlin, Hamburg, Cologne, Frankfurt, Munich;
  Stockholm, Malmö, Copenhagen (`se.cautious`); Calgary, Edmonton, Vancouver (`ca.cautious`); Sydney,
  Melbourne (`au.cautious`).
- **Cautious, the stamp does not cover the days shown:** Cape Town (`za.cape`, September 2026 only);
  Brussels, Antwerp (`be.cautious`, 25 Sep – 25 Oct 2026).

`site/cities.tsv` keeps every held row commented out under its reason, as today, and un-holds the rows
of §2.1 (İstanbul and Ankara included: the generator holds them for two days and publishes them on
1 October). A held row comes back by deleting its `# ` once its stamp qualifies; a row the rule rejects
at build time is dropped with a notice, never published.

## 3. The city page

One template for every class, in seven languages, at `/prayer-times/<slug>/` (English) and
`/<lang>/prayer-times/<slug>/`. Structure, classes and ids are named here because five tasks share them.

### 3.1 Phone (390 wide), top to bottom

1. **Header and language row** — unchanged (`base.html`): brand, nav (Prayer times · Support on phones),
   the language row with every language's endonym; a twin links to the same city, a language without a
   twin to its index (existing `picker_to_city`); links carry `hreflang`. Language links are 44 px tall
   (`padding-block: 10px`, B's fix).
2. **Breadcrumb** `nav.crumbs` — "Prayer times › United Kingdom › London" (existing).
3. **h1** "Prayer times in London" (36 px on phones; existing `h1` sentence).
4. **Date line** `p.city-meta` — "United Kingdom · Monday, 28 September 2026 · 15 Rabiʼ al-Thani 1448";
   the long date and Hijri are live spans.
5. **The Today card** `section.today` (the app's Prayer screen; surface, hairline, radius 22):
   - `.today-head`: on phones a **compact row** — a 64 px ring (`.ring-box`, the same SVG, the arc live)
     beside the text block `.ring-text`: label "Asr in" (11 px, `--accent-text`), the countdown "1:22:19"
     (30 px, Manrope Light, tabular) and "at 16:50" (14 px `--t2`). This is A's proven form, adopted to
     fix B's fold: the six rows start about 390 px from the top and end about 630 px down on a 390 × 844
     phone (header 62, the language row 70 on two lines, breadcrumb 46, h1 44, date 34, card margin and
     padding 44, the compact head 72 and its gap 14, then six rows of 40), so all six times sit in the
     first screen with room for the foot.
     Without script the text block is the calendar leaf (weekday / day / month), as today.
   - `ol.tl`: **six rows**, Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha (the app's list with sunrise shown;
     sunrise at 15 px, the others 17 px), each: the dot, the name (700), the Arabic name beside it
     (`em lang="ar"`, omitted on Arabic-script pages), the Jumuʿah `.tag` after Dhuhr on Fridays, and the
     time on the far side (tabular). The current prayer's name and time in `--accent-text` 800 with the
     14 px amber dot; past rows `--t2` 500. **Current-prayer rule as the app's** (spec §2.1): the last
     obligatory prayer that has begun, except that Fajr stops being current at sunrise and Asr at sunset;
     before Fajr nothing is current. Set-by-rule pill (`.tag`, "Set by rule") after the prayer's name
     where the day's `setByRule` names it; on a polar day the app's `today_polar_line` under the list.
   - `.today-foot` (hairline above): the mihrab glyph, "**This card is the app's Prayer screen.** Same
     minutes, same countdown." (14 px) and a quiet 40 px pill **"Get Taqwa"** → `#app` on the same page
     (R98: the app section carries the coming-soon state until launch, the badges after).
   - `p.tt-stale` (hidden): the existing stale line, with "Get Taqwa" → `#app`.
6. **Qibla line** `.qibla-line` — the 40 px dial, "**Qibla 118° from north** · 4,793 km to Makkah"; in
   Makkah "The Kaaba is here, in the Masjid al-Haram." (existing sentences).
7. **"Where these times come from"** — `details.whence#about` (§3.3). Its `summary` is always visible:
   line 1 the authority line, line 2 the proof sentence, line 3 the label with a chevron.
8. **The months** `section.months` (§3.4): the detailed-view switch, then September and October, each
   with its table and its print button, then the notes.
9. **The app** `section.app#app` (§3.5).
10. **After** `section.after`: chips — other cities in the country, cities nearby, "All cities", "How
    Taqwa checks", "Support".
11. Footer (unchanged).

### 3.2 Desktop (≥ 721 px; B's desktop)

- `.city-hero` grid `1.12fr / 0.88fr`, gap 48: left `.city-copy` (breadcrumb, h1 54 px, date line, the
  Qibla line, then `.pitch`: "**The same engine, in your pocket.** The adhan at these exact minutes,
  offline, free, no ads." with the stores block under it — badges when live, the coming-soon line until
  then); right the Today card with B's 196 px ring (the countdown inside the ring) and the same foot.
  On phones `.pitch` is hidden (the app section carries it) and the Qibla line sits under the card
  (`order`).
- `details.whence` full width under the hero, hairline above; open, the three steps stand in three
  columns (B), the tiles and the ruler in the third.
- The months full width; the switch row with the label left and the switch right; each month's head has
  the title and Hijri span left and the print button right; the table shows the Hijri column (≥ 1040 px).
- The app section and, beside it, "More prayer times" (chips: All cities, How Taqwa checks) and
  "Questions" (Support, support@taqwa.world) in a `1.25fr / 0.75fr` grid (B).

### 3.3 "Where these times come from" (R97)

`<details class="whence" id="about">` — closed by default, no `open` attribute; the native element gives
tap/click, Enter/Space and screen-reader "collapsed/expanded" for free. The chevron rotates via
`details[open] .chev`. `summary` has `list-style: none` and its own 44 px-tall padding; the marker is
hidden (`summary::-webkit-details-marker { display: none }`).

**The summary** (visible, three lines, `--t2` 14/15 px, the title in `--t1` 700):

- Checked (A/B): `<b>{London Unified timetable}</b> · reproduced and checked by Taqwa · Taqwa is not
  affiliated with London Unified.` — the title is the app's `today_whose_checked_title`, the middle a site
  sentence (`reproduced`), the tail the app's `about_not_affiliated`.
- Cautious (C): `<b>Cautious times</b> · MJC, Cape community calendar and Jamiatul Ulama, combined and
  checked by Taqwa · Taqwa is not affiliated with any of them.` — `timetable_cautious`, the members joined
  with the language's list comma, site sentences `combined` and `not_affiliated_any`.
- **Proof sentence** (spec §7 "a proof sentence from the stamp"): checked, the app's
  `about_checked_through` — "Checked against London Unified's published timetable through 31 December
  2026."; cautious, the app's new `about_cautious_checked` (§10) — "Checked against the timetables
  followed here." (no date: ruling R112, until the stamps hold each member's own last date).
- Label: "Where these times come from" + chevron.

**The body, checked places (B's three steps, `ol.steps`):**

1. **Who publishes them** (`about_who_publishes`) — "{Authority} publishes the prayer times used in
   {unit or city}. Taqwa is not affiliated with {authority}." (`about_who_publishes_body` +
   `about_not_affiliated`; the unit label is the resolution's `unitName`, e.g. "London (M25)", else the
   city).
2. **How Taqwa reproduces them** (`about_how_reproduces`) — "Taqwa ships no copy of the table; it
   calculates every day again from the sun." (site sentence `no_copy`) then the app's
   `about_method_intro` filled with the method description generated the way the app generates it
   (§9.1): "The way London Unified calculates: dawn (Fajr) at a twilight angle that changes through the
   year, between 11.9° and 16.3° below the horizon, nightfall (Isha) …, its own minutes (Sunrise −3,
   Dhuhr +5, Maghrib +3), and each start rounded up to the next minute (sunrise and the end of eating
   down)."
3. **How it was checked** (`about_how_checked`) — "Then Taqwa replays every checked day against
   {authority}'s published timetable." (`replays`); the **three tiles** (`.tiles`, three equal cells,
   value 22 px 800 over a 12 px label): `4015` / "days at 11 places" (`about_stat_days_at_places`; the
   app's digits, no thousands separator: ruling R107),
   `0` / "starts before London Unified's" (`about_stat_never_before`, the 0 in `--accent`, a large
   numeral), `5 min` / "at most after" (`about_stat_minutes_value`, `about_stat_at_most_after`); the
   **minute ruler** (§3.3.1); then the link "How Taqwa checks every city ›" → the checks page.

**The body, cautious places:**

1. **Who publishes them** — the app's `about_cautious_body` ("Mosques in Cape Town follow different
   timetables in good faith, and none is followed by most. Taqwa combines MJC, Cape community calendar,
   Jamiatul Ulama: each prayer once all have begun it."), then the members as a list (`.members`: name
   in 700, the first tagged "the most followed"), then `not_affiliated_any`.
2. **How Taqwa combines them** (site heading `combine_heading`) — `combine_body`: "Taqwa rebuilds each of
   them and combines them: each prayer once all have begun it, sunrise and the end of eating at the
   earliest of them, and Maghrib capped at the most-followed timetable's own minutes." On a day the cap
   decided Maghrib, the app's `about_cautious_maghrib_cap` sentence follows (live per day). Then B's
   **Fajr ruler** for today (§3.3.1, cautious form). Then **"Which timetable decides each time today"**
   (`about_which_decides`): six rows (`.decides`), each the prayer name, the shown time (700, tabular) and
   chips — filled chips for the members that decide it (the latest to begin it; for sunrise the earliest),
   outlined chips "MJC 5:04" for the others; the legend line `decides_legend` under it, then the app's
   `about_stop_eating` for today; then `match_prompt` + "**Match my mosque ›**" (`timetable_match_mosque`)
   → `#app`.
3. **How it was checked** — `replays_cautious` ("Then Taqwa replays every checked day against each of the
   published timetables."); two tiles with the cautious labels: `1400` / "days at 6 places" (Toronto);
   `0` / "starts before the timetable that decides it" (`about_stat_never_before_decider`, §10; ruling
   R111: "before any of them" is false where the Maghrib cap decides); no "at most" tile (ruling R105:
   a cautious entry's worst is entry-wide and would misattribute another place's spread); the link to
   the checks page.

The site's copy for a cautious page's "which decides" table and ruler is the built day's without
script; `timetable.js` rewrites both for the reader's today from the per-day member times (§9.3).

#### 3.3.1 The minute ruler (R97's single-count rule)

Checked places: B's histogram of the stamp's `events.fajr.late` buckets (0, 1, 2, 3+ minutes after the
authority's) as an inline SVG, **bars drawn and labelled as shares of that event's checked count**
("31 %", "34 %", "24 %", "11 %"), the hatched "before" box labelled "0 days" (the one true count of early
starts) and the amber line "London Unified's minute". Caption: "Where Taqwa's Fajr fell on each checked
day, in minutes after London Unified's". No absolute day count appears on the ruler: the stamp's
`checked` (4,046 for London) is not its `placeDays` (4,015; the gate checks some place-days more than
once), so the only day total on the page is the tile's `placeDays`, and the ruler shows proportions of
its own count. `role="img"` with an `aria-label` reading the caption and the four shares. In RTL the
bars run from the right (the generator lays the SVG out mirrored; the text is never flipped).

Cautious places: B's "Fajr today, minute by minute" — a scale from the end of eating (earliest member's
dawn) through each member's Fajr to the Fajr shown, hatched "not all have begun it", the shown minute
in amber. Drawn by `timetable.js` for the reader's today from the per-day member times; without script
the built day's sentence stands in its place: "Stop eating by 4:59 · MJC and Jamiatul Ulama begin Fajr at
5:04 · Cape community calendar at 5:11 · Taqwa shows Fajr at 5:11, once all have begun it."

### 3.4 The months (C's tables)

**The switch** `.dv-switch`: "**Detailed view** — Both Asr times, the end of eating, and days set by
rule" and a 60 × 44 px `button[role=switch][aria-checked]`; script only (hidden until the script runs);
it toggles `class="dv"` on `<main>`; nothing is stored. Off by default.

**Each month** `section.month#m-YYYY-MM` with `aria-labelledby`: `.month-head` — h2 "October 2026",
the Hijri span "Rabiʼ al-Thani – Jumada al-Ula 1448", and the **print button** (`button.print`, 40 px
quiet pill with the printer glyph, "Print or save as PDF"; script only). **No link to the other month**
(the existing jump pill goes). Then `.tt-wrap > table.tt` (C's real table):

- `caption.sr` "Prayer times in London · October 2026"; `thead` with `th[scope=col]`: Date, Hijri
  (≥ 1040 px), Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha; heads 11 px on desktop, **10 px on phones**
  (never 9.5), uppercase, `--t2`.
- Rows `tr[data-i]`: `th[scope=row].d` (day number 700 + short weekday), `td.h` Hijri, six `td.t`, each
  `span.v` the time, then in the **detailed view** a second line `small.dv`: under Fajr "eat by 5:26"
  (`eat_by` + the end of eating), under Asr "Standard 16:02" (the other school's name from the app's
  `madhab_*` + its time; the page's own school stays the main figure), and after a time set by rule the
  outlined pill "Set by rule" (`.tag.dv`). The detail lines exist in the markup at every width and are
  shown only under `main.dv` (one markup, one check, one print rule). Friday rows: weekday and Dhuhr in
  `--accent-text` 700 with the Jumuʿah pill (≥ 1040 px). Today's row `.is-today` tinted
  (`color-mix(var(--ring) 14%, var(--surface))`, `aria-current="date"`), the day number in
  `--accent-text`. Past rows `--t3`.
- **Past days folded at every width** (script; the no-script state is every row): when today is the 4th
  or later of the first month, the past rows get `hidden` and a fold row `tr.fold` is inserted first:
  `td[colspan]` holding a 44 px `button[aria-expanded=false]` "Earlier this month, 1–27" with the amber
  "+"; the tap shows the rows and removes the fold row.
- Notes under the month (14 px `--t2`): the clock change where one falls in it (existing sentence); in
  the detailed view the note `note_detailed` ("In the detailed view, the small time under Fajr is when to
  stop eating on a fasting day, and the one under Asr is the Standard Asr. A day set by rule, when Fajr or
  Isha follows the app's rule for high latitudes rather than the sun's angle, carries a mark.") plus
  `note_no_rule` ("None is set by rule this month or next.") when no shown day is; the Ramadan note
  (existing) where it applies.
- After the last month: `note_engine` — "From the same engine as the app, rebuilt every day. Fridays are
  marked for Jumuʿah. Updated 28 September 2026."

### 3.5 The app section (R98)

`section.app#app` (surface card): the icon tile, h2 "The same engine, in your pocket", "Taqwa calls you
to every prayer in London, works offline, and never shows an ad. These exact minutes, and the adhan when
they arrive.", four checked points (Adhan at the exact time, with a reminder before it · Widgets with the
next prayer and a live countdown · Qibla corrected to true north, and the whole Quran offline · Free
forever. No ads, no account, no tracking; a cautious page swaps the first two for "Match my mosque:
follow one of the timetables here alone" and "Show where timetables differ, prayer by prayer"), then
**the stores block**, then "For Android and iPhone. In English, Arabic, French, Turkish, Indonesian, Urdu
and Bengali."

**The stores block** is one function shared by the home hero, the desktop `.pitch` and this section
(`site/stores.py`): per live store the official badge (`assets/badges/…`, sized as today, alt "Get it on
Google Play" / "Download on the App Store"); with no store live, the grey line "Coming to Google Play and
the App Store. Source on GitHub." (the existing `stores.*` words). The city pages never show the beta
pill. Filling `site/stores.json` therefore switches every city page's two app places and the home hero at
once, and adds Safari's Smart App Banner to every page (existing `app_banner`). `docs/STORE-CHECKLIST.md`
§1 ("The site's store badges") and §4 name the city pages' sections so launch does not forget them.

### 3.6 Copy

Every sentence is one of: an app string read from `strings.xml` by the generator (`AppStrings`), filled
in Kotlin and written into the document per language, so the site says exactly what the app says; or a
site sentence in each language's `site/pages/<lang>/meta.json` under `timetable`. The seven `timetable`
blocks carry the same keys (the build fails otherwise). New site keys and their English:

| Key | English |
|---|---|
| `description_checked` | "{fajr}, {sunrise}, {dhuhr}, {asr}, {maghrib} and {isha} in {city} for every day of {month}: {method}'s times, reproduced and checked by Taqwa." |
| `description_cautious` | "… for every day of {month}: cautious times, each prayer once the timetables followed here have all begun it." |
| `reproduced` / `combined` | "reproduced and checked by Taqwa" / "combined and checked by Taqwa" |
| `not_affiliated_any` | "Taqwa is not affiliated with any of them." |
| `whence` | "Where these times come from" |
| `no_copy` | "Taqwa ships no copy of the table; it calculates every day again from the sun." |
| `replays` / `replays_cautious` | "Then Taqwa replays every checked day against {authority}'s published timetable." / "Then Taqwa replays every checked day against each of the published timetables." |
| `ruler_caption` | "Where Taqwa's {fajr} fell on each checked day, in minutes after {authority}'s" |
| `ruler_minute` / `ruler_before` / `ruler_same` / `ruler_one` / `ruler_two` / `ruler_three` | "{authority}'s minute" / "before" / "same minute" / "1 min" / "2 min" / "3 or more" |
| `ruler_none` | "0 days" |
| `ruler_alt` | "A minute ruler. No checked day falls before {authority}'s minute; {p0} on the same minute, {p1} one minute after, {p2} two, {p3} three or more." |
| `fajr_ruler_caption` / `fajr_ruler_stop` / `fajr_ruler_not_all` / `fajr_ruler_shown` | "{fajr} today, minute by minute" / "Stop eating by" / "not all have begun it" / "{fajr} shown by Taqwa" |
| `fajr_ruler_text` | "Stop eating by {eat} · {list} · Taqwa shows {fajr} at {shown}, once all have begun it." (`{list}` = "MJC and Jamiatul Ulama begin {fajr} at 5:04 · Cape community calendar at 5:11", built from `begins_at` "{names} at {time}") |
| `members_intro` / `most_followed` | "The timetables followed here:" / "the most followed" |
| `combine_heading` / `combine_body` | "How Taqwa combines them" / "Taqwa rebuilds each of them and combines them: each prayer once all have begun it, sunrise and the end of eating at the earliest of them, and Maghrib capped at the most-followed timetable's own minutes." |
| `decides_legend` | "decides today, the latest to begin it (for sunrise, the earliest)." |
| `match_prompt` | "Your mosque follows one of them? The app can follow it alone:" |
| `how_checks_link` | "How Taqwa checks every city" |
| `detailed_title` / `detailed_body` | "Detailed view" / "Both Asr times, the end of eating, and days set by rule" |
| `eat_by` | "eat by" |
| `print_month` | "Print or save as PDF" |
| `note_detailed` / `note_no_rule` | as §3.4 |
| `note_engine` | "From the same engine as the app, rebuilt every day. Fridays are marked for Jumuʿah. Updated {date}." |
| `card_foot` | "This card is the app's Prayer screen. Same minutes, same countdown." (the first sentence in 700) |
| `pitch_title` / `pitch_body` | "The same engine, in your pocket." / "The adhan at these exact minutes, offline, free, no ads." |
| `app_title` / `app_body` | "The same engine, in your pocket" / "Taqwa calls you to every prayer in {city}, works offline, and never shows an ad. These exact minutes, and the adhan when they arrive." |
| `app_point_adhan` / `app_point_widgets` / `app_point_qibla` / `app_point_free` / `app_point_match` / `app_point_differ` | as §3.5 (the bold lead is the text before the first comma or colon; the markup wraps it) |
| `app_platforms` | "For Android and iPhone. In English, Arabic, French, Turkish, Indonesian, Urdu and Bengali." |
| `more_times` / `questions` | "More prayer times" / "Questions" |
| `checks_*` | the checks page (§5) |

Removed keys: `method`, `asr`, `why`, `note`, `high_latitude`, `cta_label`, `cta_title`, `cta_body`,
`description`. App strings the document carries, filled per language: `today_whose_checked_title`,
`timetable_cautious`, `about_not_affiliated`, `about_checked_through`, `about_cautious_checked`,
`about_who_publishes`, `about_who_publishes_body`, `about_how_reproduces`, `about_method_intro` (+ the
`about_method_*` parts), `about_how_checked`, `about_stat_days_at_places`, `about_stat_never_before`,
`about_stat_never_before_decider`, `about_stat_minutes_value`, `about_stat_at_most_after`,
`about_cautious_body`, `about_cautious_maghrib_cap`,
`about_which_decides`, `about_stop_eating`, `timetable_match_mosque`, `today_set_by_rule`,
`today_polar_line`, `today_next_in`, `today_jumuah`, `madhab_standard`, `madhab_hanafi`, the prayer names
and the authority short names. Translations of new site sentences follow the app's terms (Cautious times =
الأوقات الأحوط / İhtiyatlı vakitler / احتیاطی اوقات / সতর্কতামূলক সময় / Horaires par précaution; Set by rule
= تقديري / Takdirî / تقدیری) and the site's existing register in each language.

## 4. States

- **Default (checked place).** As §3.1–3.5 with the explainer closed.
- **Cautious place.** The summary's cautious line and sentence; the explainer's cautious body; the Today
  card gains the app's tertiary line "Cautious times ›" under the list (opens the details: it is a link to
  `#about` and the script also opens the element); the app section's cautious points.
- **Detailed view on.** `main.dv`: the second lines and set-by-rule pills show in both tables; the note.
- **The explainer open.** Native `details[open]`; on desktop three columns.
- **Fold open.** The past rows shown, the fold row gone; the print stylesheet prints every row whatever
  the fold.
- **RTL (ar, ur).** `dir="rtl"` from `base.html`; every new rule uses logical properties
  (`inset-inline-start`, `padding-inline`, `margin-inline-start`, `text-align: start`); the ring mirrors
  (`html[dir=rtl] .ring { transform: scaleX(-1) }`, existing); the ruler is generated mirrored; the
  breadcrumb separator is `‹`; links whose text is a sentence keep the page direction (existing rules
  extended to the new classes: `.whence a`, `.app a`, `.after a`, `.tiles`, `.decides`).
- **Print** (`@media print`, and `html[data-print="m-YYYY-MM"]` set by the month's button before
  `window.print()` and removed on `afterprint`): light colours only (`color-scheme: light`; black text, no
  tints, hairlines in grey), the header, language row, breadcrumb, Today card, explainer, switch, app
  section, chips and footer hidden; the h1 and date line kept; each month on its own page
  (`break-before: page`), every row shown (`tr[hidden] { display: table-row }`, the fold row hidden), the
  Hijri column shown, the detail lines only when the view is on; with `data-print` set only that month's
  section prints. Fonts: Manrope where loaded, the system faces for Arabic, Urdu and Bengali as on screen,
  so every script prints as it displays. Without script the buttons are hidden and the browser's own
  print gives both months.
- **No script.** The Today card is the built day's calendar leaf and times; every row shown; the switch
  and print buttons hidden; the details work; the cautious ruler is its sentence; the decides table is
  the built day's.
- **Stale** (the page older than its data): the existing stale line and dashes in the ring.
- **Dark.** The existing dark tokens; `--accent-text` is #F0B429 there.

## 5. The "How Taqwa checks" page

`/prayer-times/how-taqwa-checks/` per language (the slug is reserved: `cities.tsv` may not use it),
linked from every city page (the explainer's link and the "How Taqwa checks" chip), from the index and
listed in the sitemap with `hreflang` twins. A prose page (`main.wrap.prose.checks`) generated from the
document's `proof` block (§9.2), sentences in `meta.json` (`checks_*`):

1. h1 "How Taqwa checks its prayer times"; lede "No prayer is shown beginning before the timetable the
   mosques near you follow. Here is how that is checked."
2. **One rule** — "A start is never before the authority's own time, and sunrise and the end of eating
   never after it. Where mosques follow different timetables with no majority, Taqwa shows each prayer
   once they have all begun it (cautious times)."
3. **The gate** — "Before every release Taqwa replays its engine against every official table it holds:
   {tables} published tables, {days} place-days across {entries} timetables. Starts shown early: {early}.
   Sunrises or ends of eating shown late: {late}." (tonight: 654 tables, 155,886 place-days, 71
   timetables, 0, 0 — read from the gate files and stamps at build time, never typed).
4. **Where mosques follow families of calendars** — "a survey of {calendars} mosque calendars stands in
   for the gate, each checked at its own mosque" (125, from `official/survey/*/calendars.tsv`).
5. **Which places have a page** — the proven rule in plain words (§2) and a table of the published
   timetables: name, kind ("Reproduced and checked" / "Cautious times"), place-days, places, checked
   through, at most (the entry's worst over the starts). Rows come from `proof.published`.
6. **Read it yourself** — links to the stamps folder and the engine's design in the public repository
   (`https://github.com/MohamedAbulgasem/Taqwa/tree/main/tools/timetables/official/stamps`,
   `…/blob/main/docs/superpowers/specs/2026-09-26-taqwa-prayer-times-engine-design.md`), and the line that
   the engine ships no table and reproduces each authority's published method.

## 6. The index and the home strip

The index (`/prayer-times/`) is unchanged in shape: h1, lede, the search, regions → countries → cities;
it gains one line under the lede — "Every city here has a timetable Taqwa has rebuilt and checked over
every day shown. **How Taqwa checks ›**" (`checks_index_line`) — and the checks page's link. The home
strip (`<!-- prayer-times -->`) is unchanged; its count is the published count. The header and footer
"Prayer times" links return with the section (existing `PRAYER_TIMES_LIVE`).

## 7. Get-the-app states (R98)

| Place | Until launch (`stores.json` all null) | After (an address set) |
|---|---|---|
| Home hero (existing) | beta pill while the beta section exists + "Coming to Google Play and the App Store. Source on GitHub." | the official badges (+ the remaining store's "Coming to…") |
| City page, Today card foot | "Get Taqwa" → `#app` | unchanged (the badges are in `#app`) |
| City page, desktop `.pitch` | "Coming to Google Play and the App Store. Source on GitHub." | the badges |
| City page, `section.app` | the same line | the badges |
| Every page `<head>` | nothing | Safari's Smart App Banner once `app_store` is set (existing) |

One function renders all of them (§3.5). `docs/STORE-CHECKLIST.md` gets the note in §1's store-badges
bullet and §4's site bullet: "the city pages' app sections (`site/timetables.py`: the Today card's foot
and `section.app`, plus the desktop pitch) switch with the same edit — open one city page after the build
and check both places show the badge."

## 8. Colour, type and access (R99, R102)

- New token `--accent-text`: light `#8A6407`, dark `#F0B429`. Used for every amber that is text below
  24 px (links, the ring label, the current prayer's name and time, `.tag` pills, Friday marks, today's day
  number, the fold's "+", the chips' "All cities"); `--accent` #B5820B stays for the tiles' large "0",
  the ruler's bars and marks, and the dial; `--ring` for the arc and the now-dot. Measured contrast of
  #8A6407: 5.15:1 on `--bg`, 5.38 on `--surface`, 4.83 on the today tint. `--t3` text stays only where
  it is decorative (dots) or ≥ 11 px uppercase labels the site already uses; the table heads move to
  `--t2` on phones.
- Targets: language links, the summary, the switch (60 × 44), the fold button, the print buttons (40 px
  visual, 44 px hit through padding), the chips (40 px visual, 44 px hit), the foot pill, "Match my
  mosque".
- Semantics: `details/summary`; `table/caption/th[scope]`; `aria-current="date"` on today's row;
  `role="switch"` + `aria-checked` + `aria-labelledby`; `aria-expanded` on the fold; `role="img"` +
  `aria-label` on rulers; the ring `aria-hidden`; the dial `aria-hidden` with the text beside it.
- Type: Manrope; Arabic, Urdu and Bengali the system faces with no tracking (existing rules extended to
  the new labels); table heads 10 px on phones (11 px Arabic-script/Bengali), 11 px desktop.

## 9. Data and code

### 9.1 The document (`tools/timetables` → `_data/timetables.json`)

Per city (`Document.city`), added: `entryClass` ("A" | "B" | "C"), `measured` (true), `unitId`,
`unitName` (null for an entry without units), `proof` = `{placeDays, places, ramadanDays, first,
through, atMost, fajrShares: {"0","1","2","3+"} (fractions of that event's checked count, four decimals),
cautious: bool}` where `atMost` is the worst lateness over the starts at the unit where the stamp has
units, else the entry's (the app's `measuredStartsWorst`), and per day, added: `asrOther`, `endOfEating`,
`imsak` (epoch or null), `sunset` (epochs), `setByRule` (prayer indices), `polar`; for a cautious entry
per day `members` = one list per member of seven epochs (six prayers + end of eating) in the resolution's
order, and `capped` (a member's Maghrib is later than the shown one: the cap decided it). Per page
language, added: `otherSchool` (the other school's name), `strings` (every app sentence of §3.6 filled
for this city: `whoseTitle`, `whoseLine` pieces, `notAffiliated`, `checkedThrough`, `whoPublishes`,
`whoPublishesBody`, `howReproduces`, `methodIntro` (the description generated as `methodDescription`
does: daily sun, Fajr angle or curve range, Isha angle/curve/after-Maghrib, horizon dip, the authority's
minutes, the rounding note, joined with the language's list comma), `howChecked`, `statDays`,
`statNever`, `statMinutes`, `statAtMost`, `cautiousBody`, `whichDecides`, `matchMosque`, `setByRule`,
`polarLine`, `stopEating` per day is built from `about_stop_eating` + the day's clock), `members`
(names), and per day `asrOther`, `endOfEating`, `imsak`, and for cautious pages `members` clocks; `days[i]`
also carries `setByRule` as localized prayer names.

Document-level, added: `proof` = `{entries, placeDays, heldOutDays, ramadanDays, earlyStarts, lateEnds,
brokenStamps, tables (gate rows), surveyCalendars, published: [{entry, class, placeDays, places, first,
through, atMost, names: {lang: short name}}]}`; `held`: `[{slug, reason}]` (informational).

### 9.2 The proven rule in code

`tools/timetables/src/jvmMain/kotlin/world/taqwa/timetables/Proven.kt`: reads the stamps
(`official/stamps/<entry>.json` through the existing JSON reader) once, and for each city returns
`Verdict.Published(figure)` or `Verdict.Held(reason)` from the resolution (`EngineDay.effective`), the
stamp and the two months' first and last days, exactly as §2. `Main` prints `::notice::{slug}: held —
{reason}` per held city and `::warning::{slug}: {entry}'s stamp ends {date}; the page after next month
would be held` where `last` < the last day of the month after the two shown, writes only published cities,
and refuses (exit 1) a `cities.tsv` row whose slug is `how-taqwa-checks`. A Kotlin test covers each clause
with real stamps (London published; Cairo held for coverage; Tripoli held for class; Cape Town held for
coverage; Birmingham held as not measured; a fake unit id held).

### 9.3 The site (`site/`)

- `site/stores.py` (new): `load()`, `badges(cfg, lang, root)`, `block(cfg, lang, *, beta: bool)`,
  `app_banner()`; `build.py` uses it for the hero and the banner; `timetables.py` for the city pages.
- `site/timetables.py`: the page per §3, the checks page per §5, the index line; `check_page` asks for two
  tables with a row per day and six `td.t` per row, the live data, the details element, and on the checks
  page the totals; `build.py`'s check adds: every language's `timetable` block has the same keys as
  English, and no city slug is `how-taqwa-checks`.
- `site/assets/timetable.js`: the current-prayer rule with sunrise and sunset; the fold at every width;
  the detailed-view switch; the print buttons (`data-print`, `window.print()`, `afterprint`); the
  cautious decides table and Fajr ruler for the reader's today; the cautious line opening the details.
  Still no request and no storage.
- `site/assets/style.css`: the tokens, the compact Today head on phones, the details, the tiles, the
  rulers, the switch, the tables, the print stylesheet, the app section — all with logical properties.
- `site/pages/<lang>/meta.json`: the keys of §3.6 in all seven languages.
- `site/cities.tsv`: §2.

### 9.4 The workflow

Unchanged: `./gradlew -p tools/timetables jvmTest generate` then `python3 site/build.py --check`; the
proven rule's notices appear in the run summary. The stamps are inputs of the generator (the
`generate` task declares `official/stamps/*.json` and `official/gate/*.tsv`, `official/survey/**` as
inputs, so a stamp refresh rebuilds the pages).

## 10. The app change (R100)

`AboutTimesScreen.kt`'s cautious template gains, after the "Which timetable decides each time today"
table and before "Match my mosque", a divider, the heading `about_how_checked`, the `StatTilesRow` with
`stamp.placeDays` / `about_stat_days_at_places(places)` and `0` / `about_stat_never_before_decider` —
two tiles, no "at most" figure (ruling R105) — then the sentence `about_cautious_checked`, without a
date (ruling R112); nothing when there is no stamp, the place is not measured, or the stamp has unit
rows and the place's own unit row is incomplete (as the checked template does).
`AboutTimesUiState.Cautious` carries the `ProofStamp?` like the other two.

New strings (all seven locales, `values-in` synced): `about_stat_never_before_decider` = "starts before
the timetable that decides it" (ruling R111; the heading above says "Which timetable decides each time
today"); `about_cautious_checked` = "Checked against the timetables followed here." The figures are the
stamp's (Toronto: 1400 / 6 · 0). The visible UI at default settings changes only by these lines
(accessibility rule of 28 September kept).

## 11. Verification

Each site task: `CI=true ./gradlew --no-daemon -p tools/timetables jvmTest` (JVM only; `CI` skips the
archived gate rows the tasks do not touch; the full gate runs with `TAQWA_OFFICIAL=/Users/mohamedabulgasem/
Desktop/Workspace/apps/Taqwa-official`), `./gradlew --no-daemon -p tools/timetables generate`, `python3
site/build.py --check`, and headless-Chrome screenshots of London (en, light and dark), Toronto (en) and
London (ar, RTL) at 390 × 844 and 1440 × 900, the implementer looking at each (the deterministic colour
scheme flag is `--blink-settings=preferredColorScheme=0` for dark and `=1` for light; the Mac's own theme
otherwise leaks in). The app task: `./gradlew :shared:testAndroidHostTest --tests
world.taqwa.app.feature.settings.AboutTimesUiStateTest`, `scripts/check-strings.sh`, and one screenshot
of Toronto's About screen on `emulator-5556`.

## 12. Out of scope, and follow-ups

- The support page's "A prayer time looks wrong" section still describes the pre-engine app ("pick the
  method your mosque follows"); the new city page no longer links to it. Rewrite it with the app's
  Timetable / Match my mosque wording in a later pass.
- The home page's and the store listing's "Prayer times" bullets (pre-engine wording) — separate.
- Per-city share cards; a per-place cautious figure (units in cautious stamps); the monitor that would
  extend London Unified's stamp into 2027 before 1 December.
- The Ramadan release's suhoor/iftar rows; the app's "Show where timetables differ" on the site.
- Any change to the app's colours, the engine or the stamps.
