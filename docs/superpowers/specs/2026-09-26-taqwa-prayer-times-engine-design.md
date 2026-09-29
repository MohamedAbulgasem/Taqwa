# Taqwa prayer times that are never early — design

**Approved 27 September 2026** (draft 3 with Mohamed's answers in §10) · implemented on branch
`prayer-engine` the same day and completed 28 September 2026 (the pending decisions, rulings
R73–R92 in §12); tested by the owner on his phones on 28 September 2026 and squashed into main.
Draft 2 holds the full investigation. The engine does the heavy work, the app stays as simple as it
is today, the website shows the detail later.

Mockups: https://claude.ai/artifact/Dk3p2Upgwn2DfhsJTjiJfP · Research: `docs/research/2026-09-prayer-times/`
· Official data: `tools/timetables/official/` (MANIFEST.tsv, 658 tables).

## 1. The promise

1. **Never early.** No prayer is shown beginning before the local Sunni authority begins it, or,
   where there is none, before the timetable most local mosques follow. Sunrise and the end of eating
   are never after theirs (the end of eating is shown in About until the Ramadan release).
2. **Close.** Measured at each place's reference point against its authority's own timetable: at most
   1 minute late where the method is rebuilt exactly (class A), 2 where a detail is still unverified
   (B), 3 where Taqwa uses the authority's method before a full proof (D with data). Where mosques
   differ with no majority, 1 minute after the latest of them. Places not yet measured say so. The
   known exceptions are in §6.3.
3. **Simple.** The Prayer screen keeps its design. What a curious user wants to know is one tap away;
   what a user wants to change is in Settings.
4. **One engine** for the app and taqwa.world, with identical times.
5. **Sunni only.**

## 2. The app (launch release)

### 2.1 Prayer screen

Unchanged: the header, the ring, the five times, the Qibla card, the exact-alarms card, portrait and
landscape, and the widgets and notifications (they show the new times). One time per prayer, one Asr.

| Element | Shows | What it is |
|---|---|---|
| **ⓘ after the date** | Everywhere | A glyph at the end of the date text, adding no line; the city-and-date block is the 48 dp touch target. Opens a small card: "Diyanet timetable · Diyanet's times for İstanbul, reproduced and checked by Taqwa · About these times ›" (or "Cautious times", "{Authority} method, not yet fully checked", "Calculated by Taqwa"). |
| **Source line** | Only in cautious-times places (§3.6) | One tertiary line below the prayer list, "Cautious times ›", 48 dp touch height; opens About. (Decision §10.1.) |
| **Set by rule** | Where a high-latitude rule set a time (§3.8) | The outlined pill Jumuʿah uses, inline after the prayer's name; no extra height. On a polar day, one line under the list instead: today's translated sentence ("The sun does not rise or set here today…"), opening About. (Decision §10.6.) |
| **Sunni times card** | Once, in the registry's Shia-regions list (Iran, Iraq, Bahrain, Azerbaijan, Lebanon, Kuwait, Saudi Arabia's Eastern Province, northern Yemen, Gilgit-Baltistan, Kurram, Hazarajat) | "These are Sunni prayer times. In Shia (Ja'fari) practice, Maghrib and iftar are later." Below the prayer list, then gone. The site uses the same list. |
| **Cautious-times card** | Once, in cautious-times places (§10.9) | "Mosques here follow different timetables. Taqwa shows each prayer once they have all begun it." Actions "Match my mosque" and "OK". Below the prayer list; if the Sunni card also applies, this one comes first and the other waits for the next launch. |
| **Clock line** | Only when the phone's zone data or hand-set clock disagrees with a change the app knows (§3.9) | One line above the ring, "Your phone's clock may be an hour off ›", opening a sheet with the fix. Landscape: top of the right pane. |
| **Other Asr** | When "Show both Asr times" is on | "Standard {time}" under Asr. |
| **Where timetables differ** | When that switch is on, in cautious-times places | Under the current prayer, before the earliest next start: "Islamic Foundation's Isha begins at {its time}"; after it: "Islamic Foundation's Isha has begun · Taqwa shows it from {Taqwa's time}", about 20 min later in Toronto in October. |
| **Current prayer** | Everywhere | Today's rule (the latest prayer that has begun is highlighted), except that Fajr stops being highlighted at sunrise and Asr at sunset. On the Prayer screen only; widgets change in the Ramadan release. (Decision §10.7.) |

Removed: the "AT THIS LATITUDE" card and its copy (Set by rule replaces it). Not in this release:
suhoor, iftar, imsak, suhoor alerts, Ramadan widgets (§7). No times-changed card and no "prayers I
already prayed" text: only closed testers ever saw the old times, and build 33's tester notes explain
the change.

### 2.2 Settings › Prayer times

| Row | What it does |
|---|---|
| **Timetable** (was "Calculation method") | Automatic first, naming what it uses ("Automatic · London Unified"). Then the timetables used near the user, each with today's Fajr, Maghrib and Isha; then "Other methods" (MWL, ISNA, Umm al-Qura …); then **Match my mosque**. Stored as `Automatic` or `Entry(id)`; each entry has a scope (unit, city, country or global). Outside a scoped entry's area Automatic applies, with one line in Settings, and the choice returns when the user does. Choosing a timetable that begins any prayer before Automatic in the next year shows: "Its {prayer} begins {n} minutes before {Automatic's name} on some days. Follow it only if your mosque does." |
| **Pray Fajr 5 minutes later** | Saudi Arabia only, off, inside the Timetable card: "Some scholars advise waiting a few minutes after Umm al-Qura's Fajr. The Fajr shown and its alert move; stop eating at Umm al-Qura's Fajr, 5 minutes before." Only while the effective timetable is Umm al-Qura in Saudi Arabia. |
| **High latitude rule** | Only when Timetable is one of the Other methods (authority timetables bring their own rule): Automatic, middle of the night, one-seventh, twilight angle. |
| **Asr** | Automatic, Standard, Hanafi; the note names the school by its shadow ("Automatic: twice the shadow, the later time, because Toronto's majority school isn't known"). |
| **Show both Asr times** | Off by default. |
| **Show where timetables differ** | Cautious-times places only, off by default. |
| **Manual adjustments** | Rows keep today's clock time. The first step below the timetable asks once per prayer: "Show Maghrib before Diyanet's time? A prayer prayed before its time must be repeated, and a fast broken before it must be made up. Only do this if your mosque's timetable is earlier." Buttons "Keep Diyanet's time" (primary) and "Use −2 min"; {source} is the authority, "the cautious time" or "Taqwa's time"; the fast sentence only for Maghrib. It asks again, more strongly, when the value passes the timetable's own margin. Stored with the registry entry; under another entry the row reads "−2 min · paused (set for Diyanet) ›" until re-confirmed. Floors: Maghrib at sunset, Dhuhr after transit, Asr at the Standard Asr. |
| Hijri date, Show sunrise | Unchanged. |
| **About these times** | New, at the foot. |

**Match my mosque.** Fields "Fajr begins" and "Isha begins", note "use the adhan time, not the
jamāʿah". Candidates: the place's listed timetables and Other methods. A match is both times equal or
at most 1 minute later, never earlier; an exact match wins, then the latest. It shows that timetable's
Dhuhr, Asr and Maghrib for today ("check these against the board") and the earlier-than warning before
"Use this timetable". One time matches: it names the timetable and the time that differs. Both off by
exactly 60 minutes: "your board may show winter time". None: "No timetable Taqwa knows matches both
times", with the list and Manual adjustments.

### 2.3 About these times

One screen, filled from the registry and the proof stamp:

- **Authority, checked** (Diyanet, Umm al-Qura, London Unified …): who publishes the times; how Taqwa
  reproduces them; how it was checked and through which date, or, for a unit with no official days,
  "not measured for {district}"; what is not verified; timetables some local mosques follow instead;
  "Taqwa is not affiliated with {authority}."
- **Authority, not yet fully checked** (class D with an authority): "{Authority} publishes the prayer
  times for {place}. Taqwa calculates them the way {authority} does, with a few safety minutes." With
  data: "Checked on {n} days so far: never before {authority}'s, up to {worst} minutes after." Without:
  "Taqwa has not yet compared them with {authority}'s own timetable."
- **Cautious times**: the timetables combined, by name; each prayer once all have begun it; Maghrib
  by the majority's own minutes where there is one (any later Maghrib among listed timetables is
  named); today's table saying which timetable decides each time; Match my mosque.
- **Calculated by Taqwa** (no authority known): the method in plain words, the Asr school and why,
  "check your mosque".

Each has "If you are fasting: stop eating by {endOfEating}" (in İstanbul, Diyanet's imsak) and the
Sunni note where it applies.

### 2.4 Words and access

- French Standard Asr: "Majoritaire" becomes "Standard". Notes name the school by its shadow.
- Terms Mohamed approves before translation: Cautious times (ar الأحوط, ur احتیاطی اوقات, tr İhtiyatlı
  vakitler), Set by rule (ar تقديري, ur تقدیری, tr takdirî), Calculated by Taqwa. Other new strings go
  through the usual native review.
- The ⓘ and the source line have one screen-reader label ("Times by Diyanet, İstanbul. Opens whose
  times these are"), isolated segments for right-to-left, mirrored chevrons.
- Checked at 200 % font on 360 dp and 408×760 dp screens, in en and ar: the ⓘ adds no line to the date
  block; the line, pills and Asr control wrap cleanly.

## 3. The engine

### 3.1 Shape

- **Pure Kotlin in `shared/commonMain`** (`world.taqwa.app.prayer`), no dependencies. It replaces
  adhan2 for prayer times; adhan2 stays for the Qibla bearing until ported.
- **One entry point** for the Prayer screen, notifications, widget mirror, Tahajjud and
  `tools/timetables`: `dayTimes(place: Place, date: LocalDate, settings: PrayerSettings)`; `Place` is
  lat, lon, time zone and the units resolved for that point (§3.5).
- **One bounded, thread-safe cache inside the engine**, keyed on the point, date, the settings that
  change times and the registry-plus-core hash. The one-second tick only redraws.
- **Budget**: the worst cautious-times place-day in at most 2 ms on a low-end Android phone, asserted by
  a benchmark test.

### 3.2 Astronomy core

NOAA/Meeus apparent solar position iterated to each event's own moment, with the sun models
authorities use: `EXACT` (default), `DAILY(0h UT)` (Diyanet, Oman, Jamiatul Ulama), `CLASSIC` (noon
declination: MUIS, JAKIM), Umm al-Qura's declination bias, ESA's and MARW's Asr. Horizons per place
(−0°50′ default; Kemenag −1°/−2°; Jordan; JAKIM highland zones; Habous and INM by elevation, with
INM's and JAKIM's dip also applied to the Fajr and Isha angles). Deterministic on every target: tests
in `shared/commonTest` run on the JVM, Android and iOS against a committed golden vector of about 2,000
place-days.

### 3.3 The day

| Field | Meaning |
|---|---|
| `fajr`, `sunrise`, `dhuhr`, `asr`, `maghrib`, `isha` | What the app shows: when each prayer begins in the authority's own words. Where it prints one morning time, that time; where it says the prayer begins at a later column, that column (Kosovo: "Sabahu … fillon 20 min. pas imsakut", so Fajr = imsak + 20); never a column it labels as a mosque or congregation time (DUM RT: "Совершается в мечетях"). DUM RT's Dhuhr is its printed 12:00 until the meaning is confirmed (slice 2), since that is never early. |
| `sunset` | The astronomical sunset, for the invariants and the Maghrib cap. |
| `asrOther`, `ishaOther` | The other school's time. |
| `earliestStart` | Per prayer, the earliest member's start (cautious places), for "Show where timetables differ". |
| `endOfEating` | The authority's printed column where it has one (Kosovo imsak, Tatarstan end of suhoor, Bangladesh sehri, Jamiatul Ulama's Fajr − 5), otherwise its dawn without its start margin at the earliest point of its zone; never after `fajr`. In About now; on the Prayer screen from the Ramadan release. |
| `imsak` | The authority's printed precaution, for the Ramadan release and the site. |
| `ends` | For the screen's highlight (Fajr and Asr only, §2.1), About and the site: Fajr at sunrise, Dhuhr at the Standard Asr, Asr at sunset, Maghrib at the earlier of the earliest Isha in play and red twilight (17°), Isha at `endOfEating`. |
| flags, provenance | Set by rule (which), Ramadan rule, adjustment; registry entry, version, class and the timetables that decided each time. |

- **A day** is the prayers that belong to date D: Fajr to Maghrib fall on D; Isha may fall after local
  midnight (Edmonton in early May, Santiago de Compostela in June) but before D+1's end of eating.
  TimelineBuilder, NotificationPlanner and the widget mirror work across yesterday, today and tomorrow,
  so an Isha after midnight still shows as next. Tests: Edmonton 4 May, Santiago 21 June.
- **Rounding**, once, inside the engine: a start is ⌈model + m⌉; sunrise, `endOfEating`, `imsak` and
  `ends` are ⌊model − m⌋; m is a signed margin in seconds fitted to reproduce the authority's minutes
  and rounding. Every instant is a whole minute; everything downstream prints it.
- **Invariants** (`endOfEating ≤ fajr < sunrise`, `maghrib ≥ sunset`, `isha > maghrib`, previous
  `isha < endOfEating`) are asserted in tests and the gate on the unadjusted day. At runtime a violation
  never throws: it resolves to the safe side, sets a flag and keeps the order.
- **Adjustments** apply after, with the floors of §2.2 and the order kept. They never move
  `endOfEating` or sunrise later.

### 3.4 Methods as data

The registry (`prayer/authority/`) holds each authority's published method: short name (≤ 20
characters, translated), angles, horizon, sun model, the authority's minutes and rounding, our margins
with the core hash they were fitted on, Asr and Isha school, end-of-eating kind, Ramadan rules,
high-latitude rule, reference points, scope, `validFrom`/`validTo` per version (JAKIM 2019, Diyanet
Europe 2023, ESA 2025, Uzbekistan 2026 …), each printed column's meaning quoted from the authority, and
a monitor source. Large tables are compact resources.

### 3.5 Places

- **Store only the point** (lat, lon, zone; the city id for a picked city). Resolve country, admin-1
  and each authority's unit (JAKIM zone, Kemenag kab/kota, Diyanet ilçe, INM delegation, Algerian
  wilaya …) from a spatial index built at build time, memoised per data version. The registry entry is
  looked up on every call, never stored.
- **Where the time is computed**: the unit's reference point; the zone's latest point for starts where
  the authority works that way (JAKIM, Bahrain); Umm al-Qura at the city and the user's point. Always
  also at the user's own point: a start never earlier, sunrise and end of eating never later.
- **Unknown reference point or horizon**: the unit's safe edge and the deepest plausible horizon; no
  "at most" figure is claimed for that unit.
- **Near a unit or country border**: the envelope over the candidates within the location's
  uncertainty (the fix's reported accuracy, or 15 km for a picked city); the gate measures the lateness
  this adds at edge samples.
- **Before the index answers** (receivers at boot): the country authority's method at the point, with a
  margin covering its largest unit.

### 3.6 Majorities and cautious times

- **A clear majority** (a timetable followed by about two-thirds or more of a place's Sunni mosques,
  with evidence) is followed alone, like an authority (decision §10.2): London follows London Unified
  (37 of 55 sampled mosques), Montreal ISNA (12 of 16), Ottawa ISNA (6 of 6).
- **No majority**: cautious times over the **members**, published timetables in the manifest each
  followed by roughly a tenth or more of the place's mosques. Each prayer starts at the latest member;
  sunrise and the end of eating at the earliest. **Maghrib is capped** at the most-followed member's own
  minutes (decided 26 Sep): the latest sunset definition plus physical margins, never the sum of every
  mosque's precaution. The stamp records the cap's gap to each listed timetable.
- Where members agree within 2 minutes over the whole year, the place shows the most-followed
  member's name; the times stay the latest.
- Example at launch: Toronto (no majority; the Islamic Foundation of Toronto, the Islamic Institute of
  Toronto and MAC Masjid Toronto).

### 3.7 Asr school

The local majority's, per region, each with its evidence, checked by the gate against every mosque
table held for the place: the authority's where there is one (Diyanet's is the Standard time although
most Turks are Hanafi); Hanafi in South Asia, Afghanistan, Central Asia, Bashkortostan, Gauteng and
KwaZulu-Natal, Iran's Sistan-Baluchestan, Golestan and Khorasan, and the Chicago metro (all eight
tables held are Hanafi, Arab mosques included); Standard in the Arab world, South-east Asia, the rest
of the US, Montreal, Ottawa and the Cape (MJC). Where the majority school is not known (the UK, the rest
of Canada), the later time: it is within Asr in all four schools, though late in the preferred time
for Shafi'is and Hanbalis, so Standard is one tap away.

### 3.8 High latitudes

1. The authority's own rule exactly where and when it applies it (Diyanet Europe's 2021 rule, EMB,
   DUM RT's summer window, DUM RF, IRN, QMDB, London Unified), fitted on its tables.
2. Otherwise the real time whenever the sign occurs.
3. A missing Fajr or Isha: the MWL Fiqh Council's proportion from 45° (1986), ramped so no start jumps
   earlier overnight.
4. No sunrise or sunset: the nearest latitude where they occur.

Two forms of rule 1 were added while building. An **authority clock rule** (`ClockRule`, ruling R82)
is an authority's own times for a date where its calendar leaves the sun in a place's polar seasons:
IRN's Tromsø calendar keeps Makkah's times ("Makkah time") where the sun gives none. The engine
applies it last, after the rate limit, at every point it computes at; a time the rule gives that no
day in order can show (a Fajr after the sun has risen, a "sunrise" at the sun's lowest point) is
declared in `notFollowed`, the gate counts that cell as declared, and About says so. A
**DawnOrMiddle end of eating** (rulings R75, R87) is an authority's own dawn angle where the sun
reaches it and the middle of the night where it does not (the practice of the UK's Karachi-family
and Germany's 18° calendars), instead of the MWL proportion, so the fast never begins after theirs.

Today's app applies one-seventh of the night every day above 48°, which puts Isha up to about 2 hours
before the real nightfall in summer; this ends that.

### 3.9 Time zones and clocks

The engine returns instants; the screen draws them in the phone's zone. A small table shared with the
site lists each change with its direction (Morocco −1 h from 20 Sep 2026, British Columbia +1 h from
1 Nov 2026, Kazakhstan −1 h from 1 Mar 2024) and expires. The clock line shows when the phone's offset
under its own zone data differs from the true one, or when the clock was set by hand (Android's
automatic-time setting; on both platforms the server's `Date` header when online). The sheet says which
way the times are off ("may be an hour early" for British Columbia, "late" for Morocco) and how to fix
it, and says alerts are right only when the clock is automatic; when it cannot tell (iOS offline): "If
you set the clock by hand, prayer times may be an hour {early/late}. Turn on automatic date and time."
Tests: British Columbia and Casablanca, each with stale data plus a hand-set clock, and with automatic
time.

## 4. Rules and their basis

| Rule | Basis (details in `fiqh-rulings.md`) |
|---|---|
| A start is never before the authority's or the local majority's. | A prayer before its time must be repeated in all four schools (al-Mughni 1/237; al-Majmu' 3/79); Ibn Uthaymeen: invalid even by a minute. |
| Where trusted timetables differ with no majority, pray at the latest. | Ibn Uthaymeen, Jalasat Ramadaniyya. |
| Stop eating at the earliest, called a precaution. | IslamQA 311727, 221219; the doubt rule allows eating while unsure (al-Mughni 3/35). |
| Follow the local authority or majority; Taqwa makes no rulings. | IslamQA 66891; Ibn Baz 15636. |
| Safety minutes are small and physical, never at Maghrib for their own sake. | MWL Fiqh Council 1986 (Dhuhr, Asr, Maghrib, Isha +2; Fajr and sunrise −2); Ibn Hajar, Fath al-Bari 4/199. |
| The optional later Fajr in Saudi Arabia. | Ibn Uthaymeen (about the calendar before its December 2008 move to 18.5°); islamweb 294715, 296551. |
| Sunni only; say so where Shia are many. | Owner's decision; Sistani, Islamic Laws 722. |
| Say whose times they are and how sure Taqwa is. | The muezzin is entrusted (Abu Dawud 517); calendars are probable (Ibn Baz 1620). |

No scholar has reviewed these; Mohamed asked and found no one. About cites the sources, the app follows
only published authority methods and local majorities, and corrections are invited through
support@taqwa.world.

## 5. Proving it

- **Data.** `tools/timetables/official/`; the repository is public, so only the manifest and openly
  licensed sets (MUIS, DUM RT, London Prayer Times) are committed. The manifest becomes typed (entry,
  point, zone, method version, each column's meaning quoted from the authority, status) with a
  validation test. The Levant and Iraq captures are normalised into it.
- **Fitting** runs on the shipping Kotlin core, up to a cut-off date; the **proof** uses only later
  data. Perpetual tables are proven over a leap cycle.
- **The gate**, per unit, on every valid official day: each start at or after the official, sunrise and
  end of eating at or before, lateness within the unit's `lateLimit` (the class default or a recorded
  exception with its reason, §6.3). It also samples GPS points inside each unit, near its edges, for
  never-early and lateness; checks each Automatic Asr against the place's mosque tables; and checks that
  no class-D Fajr begins before the Fajr of most mosque tables held for the place.
- **Stamps** per unit: places, dates, place-days, Ramadan coverage, worst minutes, class,
  `provenThrough`, keyed on a hash of the engine and registry. A unit with no official days has no "at
  most" figure. About states the range and, after it, "not yet checked after {date}". `release.sh` and
  `ios-release.sh` refuse to build on a stale or red stamp; the Ramadan release also refuses unless every
  A/B unit's proof covers Ramadan 1448 or the gate has demoted it.
- **The monitor** (slice 2): a scheduled local job, not public CI, that fetches each entry's newest
  published table (and Tripoli's daily from api.ifta.ly) and runs the gate on it; a failure opens a task,
  and a registry-only release follows within days.

| Class | Evidence | Shown as (ⓘ card) | Most after the authority |
|---|---|---|---|
| A | method rebuilt or open table; a year incl. Ramadan at 3+ spread places; never early on held-out data | "{Authority} timetable" | 1 |
| B | as A, with a reference point or season not yet verified | "{Authority} timetable" | 2 |
| C | cautious times over proven members, no majority | "Cautious times" (and the source line) | 1 after the latest member |
| D, authority known | its method with the tested safety minutes | "{Authority} method" | 3 where measured; otherwise not claimed |
| D, none | §6.2 (c) | "Calculated by Taqwa" | not claimed |

## 6. The registry at launch

### 6.1 Expected classes

| Class | Places |
|---|---|
| A / B | Saudi Arabia (Umm al-Qura), Türkiye (Diyanet), Singapore (MUIS), Malaysia's checked JAKIM zones, Indonesia's kab/kota with a fitted horizon (Kemenag), Egypt (ESA), Dubai (IACAD), Qatar (Doha: the later of the calendar and the ministry API, Maghrib + 3 all year), Morocco's fitted Habous cities, Algiers, Djelfa and Adrar (MARW), Tatarstan (Dhuhr B until §3.3's question is answered), Bosnia, Kosovo, Gauteng and KwaZulu-Natal (Jamiatul Ulama), London (London Unified's open table, and beyond its published years a curve fitted to past years with margins) |
| C | Toronto and Canada outside Montreal and Ottawa, the UK outside London, Ireland, France, Belgium, the Netherlands, the Nordics, Cape Town, Australia, Gaza |
| D | Everywhere else, per §6.2 |

### 6.2 What class D computes

| Case | Places | Method | ⓘ card |
|---|---|---|---|
| (a) An authority's unit not yet checked | Other JAKIM zones, Kemenag kab/kota without a fitted horizon, Diyanet ilçe beyond the checked districts, Algerian wilayas (per §10.4), DUM RF, the Uzbek Board | That authority's method at the unit's safe edge | "{Authority} method" |
| (a) A known authority, data thin | Jordan (18°/18°, sunrise −7, Maghrib +8), the PA (Maghrib +7), Lebanon (Asr +2, Maghrib +7, Isha 18° +1), Iraq (Dhuhr +5, Asr +5, Maghrib +4), Oman (18°/18°, Dhuhr, Asr, Maghrib +5, rounded up), Libya (national method at each city: Isha 18.5° +1, Dhuhr +4, Maghrib +5; in the east and south the end of eating at the 19.5° dawn mosques there use), Kuwait, Bahrain, Tunisia (INM's elevation), Bangladesh (Islamic Foundation: dawn at the district's east edge, adhan and iftar at the west), Kyrgyzstan, Dagestan, Montenegro, Sana'a's published points | The minutes tested never earlier in the research, refitted on the Kotlin core | "{Authority} method" |
| (b) A majority convention, no authority | The US (ISNA 15°/15°; Chicago metro 18°/15°, Hanafi), Ottawa and Montreal (ISNA), Pakistan and India (Karachi 18°/18°, Hanafi) | The convention with the MWL 1986 minutes | "{Method} method" |
| (c) Nothing known | Iran, most of Africa south of the Sahara, China … | Fajr prayer at the latest dawn convention in use in the region (18° where none is known), end of eating 2 minutes before the earliest; Isha at the latest convention + 2; sunrise −2; Dhuhr, Asr, Maghrib +2 | "Calculated by Taqwa" |
| (d) Always | Everywhere in D | Every published data point for the place (authority or mosque table) is a floor for starts and a ceiling for sunrise | — |

Asr per §3.7; high latitudes per §3.8.

### 6.3 Where 3 minutes can't be met

| Case | At launch | How late | What closes it |
|---|---|---|---|
| Cautious times | Yes | After the earliest member by up to about 20 min in Toronto (Isha, the latest member's against the earliest's, in early October); within 1 of the latest | By design; Match my mosque gives one timetable. |
| Morocco sunrise and Maghrib | No: slice 3 fits each city's horizon from Habous's tables | Until then up to about 8 min (sunrise as early) | Slice 3. |
| Indonesia, kab/kota without a fitted horizon | No: slice 3 fits them from the open myQuran API | Until then Maghrib about 4, sunrise about 4 early | Slice 3. |
| Algeria outside the base cities | Per §10.4 | Fallback: Oran up to 7 (Fajr, Isha), 6 (Maghrib), 5 (Asr); Tamanrasset up to 25, 22, 24; sunrise up to 6 and 22 early. Recommended fit: Oran 0–3; Tamanrasset 0–3, Asr up to 6. Other wilayas not measured | Slice 3, re-proven on the 1449 calendar. |
| Western Libya, Lebanon (D) | Yes | Up to about 5 (Libya), 4 (Lebanon) | A full Tripoli year from the monitor; Lebanon's perpetual table as parameters. |
| Tatarstan Dhuhr | Yes | Up to about 14 if 12:00 turns out to be a mosque time | The slice 2 question. |
| Bosnia south of Sarajevo | Yes | Up to about 16 at Fajr: IZ's town offsets have a sign error, so its printed dawn is before the real one; Taqwa keeps the real dawn and About says so | None: the authority's error. (Decision §10.8.) |
| High-latitude estimated days | Yes | Depends on the rule | The authority's own rule where one exists. |
| Hanafi and Standard Asr | Yes | 45–80 min apart | Not lateness: a school. |

## 7. Releases

**Launch release** (build 32 stays on manual release until it ships). Rough effort, for planning:

1. **Engine core** (about 1 week): astronomy, the day model, rounding, methods as data, goldens,
   benchmark; adhan2 out of prayer times. No visible change.
2. **Proof** (about 2 weeks): typed manifest, Levant and Iraq normalised, fitting on the Kotlin core,
   held-out proof, per-unit stamps, release checks, the monitor, the DUM RT Dhuhr question.
3. **Registry and places** (about 2 weeks): spatial index and unit tables, reference points, majority
   and cautious members, Asr regions, high-latitude rules, the class D table, Shia-regions list, Tehran
   removed; Kemenag horizons, Moroccan city horizons, Algerian wilayas (per §10.4).
4. **App** (about 1–2 weeks): ⓘ and card, source line, Set by rule and the polar line, Sunni card, clock
   line, the highlight change, About, Settings (Timetable, Match my mosque, Saudi option, high-latitude
   picker for Other methods, Asr, the two switches, adjustment dialog), migration, strings in seven
   languages; a new build and a fresh App Store submission.

Earliest launch: mid-November 2026. To launch sooner: Match my mosque's type-in and the "where
timetables differ" switch can follow in a point release; the Moroccan, Indonesian and Algerian fits can
ship as class D with their lateness stated.

**Ramadan release** (a few weeks before 1 Ramadan 1448, about 8 February 2027): "Suhoor ends" on the
Prayer screen before dawn in the fasting window, iftar, the printed imsak as a precaution, the suhoor
alert, Ramadan widgets and the widget highlight change, Tahajjud ending at the end of eating, "I'm
fasting today".

**Website** (after the app): city pages only for proven places, each saying "{Authority} timetable ·
reproduced and checked by Taqwa · Taqwa is not affiliated with {authority}" and a proof sentence from
the stamp; the "How Taqwa checks" page from the stamps; the detailed view (both Asr, suhoor, "rule"
marks); no link to the other month; past days folded at every width; "Print or save as PDF" for each
month from a print stylesheet, so every script prints correctly. Built 29 September 2026 (design and
details in `docs/superpowers/specs/2026-09-29-city-pages-design.md`): "proven" means every day a page
shows lies inside the days the gate actually checked, for the place's unit or, for cautious times,
every member there (ruling R115; the stamps record those days); a cautious place shows two proof
tiles — days checked, and "0 starts before the timetable that decides it" — with no "at most" figure
and a sentence without a date (rulings R105, R111, R112), in the app's About screen as on the site.

## 8. Migration (launch release)

Versioned, run before any reader, per-field writes, each case tested:

- **Method.** If `calculation_method_user_chosen` is not true: Automatic, whatever is stored (today's
  app writes a country default on every location save). If true: TEHRAN and the authority stand-ins
  (SINGAPORE in SG, MY, ID, BN; KARACHI in BD; TURKEY in TR; UMM_AL_QURA in SA; EGYPTIAN in EG; DUBAI,
  KUWAIT, QATAR at home; ISNA in US and CA) become Automatic; any other becomes `Entry(global id)`.
  Tests: UK with MWL and no flag → Automatic; PK with KARACHI and no flag → Automatic; flag with
  TEHRAN → Automatic; flag with MWL → Entry(MWL).
- **Remove** `applyCountryDefaultMethod`, `CalculationMethodDefaults` and their three call sites, so a
  location save never writes a method.
- **Asr**: a stored Hanafi stays; Standard or nothing becomes Automatic.
- **Kept overrides that begin earlier** (a chosen timetable or a negative adjustment) stay stored but
  are applied no earlier than Automatic's times until re-confirmed; Settings shows them as paused.
- The high-latitude preference is kept for Other methods and ignored otherwise.
- Notifications rescheduled on first launch, `MY_PACKAGE_REPLACED` and iOS background refresh; on iOS
  the old build's pending alerts can fire until the app next runs (testers only).

## 9. Decided

| Question | Decision |
|---|---|
| Indonesia's Fajr | Kemenag, the majority; Muhammadiyah is a named timetable. |
| Maghrib minutes | Capped at the most-followed timetable's own. |
| Later Fajr after Umm al-Qura | "Pray Fajr 5 minutes later", Saudi Arabia only, off by default. |
| Scholar review | None available; sources in About, corrections invited. |
| Suhoor, iftar, imsak, alerts, Ramadan widgets | Ramadan release; About gives the end of eating until then. |
| Both Asr times; "ends by" lines | Two switches, off by default. |
| "Your adjustment" tag | Dropped. |
| Sunni card, clock warning | Kept, each only where it applies. |

## 10. Answered (27 September 2026)

1. **Source line**: the ⓘ everywhere, and "Cautious times" only where mosques differ with no majority.
2. **Clear majorities**: a timetable followed by about two-thirds or more of mosques is followed alone
   (London = London Unified).
3. **Eastern and southern Libya**: Mohamed got the adhan times on 27 September: Benghazi Fajr 5:02,
   Maghrib 18:31; Sabha Fajr 5:28, Maghrib 18:54. These are a 19.5° dawn (5:02:21 and 5:28:52) and
   sunset + 1 minute (18:30:02 and 18:53:14), earlier than the national calendar (in Benghazi by about 5 min at Fajr
   and 3 at Maghrib). So the east and south do not pray later; the national method computed at each city is
   never early there, and the 19.5° dawn in use there is taken as the end of eating (Ramadan release).
   No + 20 interim. Superseded on 28 September by ruling R73 (§12): the east and south follow the
   local adhan for Fajr and Maghrib too.
4. **Algeria**: each wilaya is calculated at its own seat with the ministry's rebuilt method, checked
   against its tables.
5. **Fasting before the Ramadan release**: accepted; About shows the end of eating.
6. **Set by rule**: kept, inline after the name.
7. **Current prayer**: Fajr stops being highlighted at sunrise and Asr at sunset, on the Prayer screen
   now and in the widgets in the Ramadan release.
8. **Southern Bosnia** (left to Claude): keep the real dawn and say so in About.
9. **Where mosques disagree**: C. In cautious-times places a once-only card below the prayer list:
   "Mosques here follow different timetables. Taqwa shows each prayer once they have all begun it.
   Match my mosque ›" with "OK".

## 11. Risks

- **Size.** Four slices before launch (about six weeks), and much of the world starts at class D:
  honest, a little later than the mosque, never earlier.
- **Thin data** in most of the Arab world outside Saudi Arabia, Türkiye, Egypt and the Maghreb, and in
  South Asia.
- **Big moves** for testers who update: Hanafi Asr in South Asia, Chicago and the UK (45–80 min later
  than today), summer Isha above 48° (up to about 2 hours later), Maghrib in Iran. New users see the right
  times from the start.
- **Copying**: the engine rebuilds methods from published tables and ships none, except openly
  licensed data (MUIS, DUM RT, London Prayer Times).

## 12. What changed while building (27–28 September 2026)

Rulings made against the gate and the archive while implementing this design, each refining or
replacing a point above. R-numbers refer to the build ledger
(`.superpowers/sdd/2026-09-27-prayer-engine/progress.md`). R73 onwards are the completion of
28 September, when the owner delegated the pending decisions ("make the best judgement calls";
never early first, good accuracy where most Muslims live).

- **R17/R22/R24**: §3.8's 7-day "ramp" is replaced by a flat rate limit — a missing sign's Isha
  estimate may not move earlier than the day before by more than 20 minutes, nor the end of eating
  later by more than 20 minutes, checked over the full lookback and applied to a value borrowed
  from a substitute latitude as much as to a local estimate; Fajr moving later stays uncapped.
- **R18/R19/R23**: polar-day handling (§3.8) triggers only when the place itself has no sunrise or
  no sunset that day, not a broader condition; a place's own real sign always wins over the
  substitute latitude's estimate, and a place computes its own Fajr or Isha independently whenever
  its own night gives it one.
- **R28**: every day-of-year curve (twilight curves, LUPT, IFI) is indexed on a 366-slot leap-year
  reference and stored as an envelope over neighbouring days, so the four-year leap cycle can never
  drift a start early.
- **R30/R44/R45**: a "point table" concept — a cautious member or single-authority timetable
  carries its own reference point (`fixedPoint`); past its proven reach, the point still bounds the
  ends only (`ENDS_ONLY`) while starts fall back to the user's own point at the entry's edge margin.
- **R37/R41/R57**: §6.3's late-limit exceptions live in the registry itself, per event, per unit or
  entry, each with a reason, so the figure shown in About and the figure the gate checks are the
  same one; an exception needed on most of an entry's events demotes the whole entry's class
  instead.
- **R39**: an authority whose Fajr follows a curve or a night rule gets its own end-of-eating rule
  from a separately fitted curve, never derived from the late-start curve.
- **R40**: a unit's radius (§3.5) is set from how far its own point can be later than a nearby point
  before crossing the entry's class limit, not the much smaller reading a literal application of
  §3.5 gave, which had put some of the app's own reference cities a class too low.
- **R42**: a fitted per-day curve is recorded as the derived parameter it is (the authority's
  effective twilight angle per day), never as the printed table itself; each curve file states its
  source and that the underlying table is not committed.
- **R50**: §2.2's "Other methods" are global by design; every other registry entry is in scope only
  where Automatic would offer it nearby or in its own countries, so a named timetable is not offered
  somewhere it was never meant to reach.
- **R52/R70**: §8's paused-timetable behaviour is a stored key (`prayer_timetable_confirmed`) plus
  the Automatic entry it was confirmed against (`prayer_timetable_confirmed_under`); travelling to a
  place whose Automatic entry differs re-pauses the choice until it is confirmed there too.
- **R58**: the astronomy core's twilight search retries from the sun's lower culmination before
  reporting a crossing missing, and DUM RT's summer rule (§3.8) is keyed to each town's own missing
  dawn rather than a fixed calendar window.
- **R61/R65**: at DUM RT's edge (no fixed point), a night whose lowest sun sits within about
  two-tenths of a degree of 18° takes the safe side of the values either side of that band, rather
  than whichever the raw search happened to land on.
- **R62**: a rate-limited Isha is never held later than the next day's end of eating, and never
  earlier than the rule's own unlimited estimate.
- **R63**: About's "at most N minutes" (§2.3) reads the stamp's own measured worst per event, not
  just the class or a registry constant, so it updates itself as the proof does.
- **R64**: Norway is split from Sweden in the Nordic cautious group (§6.1) — `no.cautious` pairs
  Diyanet Europe with IRN, `se.cautious` with Rabita — instead of one shared Nordic entry.
- **R67**: an authority's own stated clock-time rule (a fixed Dhuhr, a floor) is a method parameter
  that may be committed with a KDoc citing the authority's rule, distinct from a copied table row.
- **R69**: withdraws this design's earlier "already public" exception for a restricted table's
  printed time (§5): nothing in the tree, including this document, may show one — the spec is not
  public until it is pushed.
- **R71**: Umm al-Qura's Ramadan/lag date list (§6.2) runs out after 2030; past it, the engine takes
  the later of that day and the day before for starts and the earlier for ends.
- **R73**: eastern and southern Libya (§10.3) follow the local adhan the owner observed, not the
  national calendar: Fajr at the 19.5° dawn and Maghrib at sunset + 1 min (an exact fit at Benghazi
  and Sabha, 700 km apart), Dhuhr, Asr and Isha the national method, the end of eating the 19.5°
  dawn; those units are class D, checked only at the owner's two observations, which the gate holds
  as an open table; About claims no figure for a start not checked at a unit.
- **R74**: Sudan keeps 18.3°/18.0° (an exact fit to the one verified government table) and gains a
  Ramadan-only Isha floor of Maghrib + 90 min on the entry's Ramadan dates (a 2026 imsakiya
  circulating under the Fiqh Academy's name), recorded as a late limit of 13–19 min.
- **R75**: the UK outside London (`gb.cautious`) is never early against every non-faulty calendar of a
  62-calendar Mawaqit survey: families followed by about a tenth or more become members (§3.6) — a
  late-dawn member (a 366-slot curve from the latest printed Fajr of the nine-calendar late-dawn
  family, about 12.4° in winter to 8° in summer), Karachi's end of eating at the 18.6° dawn or the
  middle of the night where it is not reached, a Scotland variant with Glasgow's Asr minutes;
  Hanafi stays the default; outliers are recorded. Cost: summer Fajr 15–28 min later in England
  (Plymouth 33–37), the end of eating 47–147 min earlier from May to August.
- **R76/R77/R78**: the US and Montreal/Ottawa mosque-table floors stay (never early against every
  held mosque outranks 5–7 min at Dhuhr and Maghrib); Chicago stays cautious (four of seven tables
  at 15°, no majority); Canada's three families stay and apply beyond the held tables; IISC Calgary
  (one mosque, 10°/10°) is not a member and is recorded as a known early case.
- **R79**: France's 15° family (summer Isha up to 38 min later) and the Nordic summer lateness
  (Diyanet Europe's takdir beyond its city tables) stay: never early, the cost recorded; Timetable
  and Match my mosque are the user's way out.
- **R80**: the end of eating under GMP and ICCI is the authority's own dawn as an end (§3.3: its
  printed Fajr without the start margin, an end curve fitted to no late end), floored at the
  Moonsighting rule's own dawn across the GMP reach, instead of R39's 18° dawn.
- **R81**: R42's per-day envelope curves stay; harmonics would only add lateness.
- **R82**: Tromsø follows IRN's Makkah-time rule as an authority clock rule (§3.8): IRN wherever the
  sun allows, IRN's whole Makkah day where the sun neither rises nor sets, Fajr kept before a real
  sunrise on about fifty summer days and declared not followed, Maghrib never before a real sunset,
  the rule's dates widened a day either way until IRN's 2027 calendar is held; the rule lives in the
  Tromsø unit only and Automatic follows IRN within its reach. The rule's own dawn ends the fast
  where its Fajr is shown; a whole rule day is not polar; a paused timetable pairs Fajr with its own
  day's sunrise. Cost: summer Maghrib up to 314 and Isha 238 min after IRN's (the real sunset).
- **R83**: the notification planner skips a Maghrib or Isha start whose window the next Fajr has
  already closed (R72's nights), and "next" on the Prayer screen is never a passed instant there.
- **R84/R85/R86**: the golden vector (§3.2) is about 2,000 place-days at invented grid points, every
  entry kind, generated on the JVM and checked on Android and iOS, in minutes from UTC midnight; the
  benchmark (§3.1) is a host guard on the worst cautious place-days plus an emulator measurement;
  `release.sh` and `ios-release.sh` refuse to build on a stale or red stamp (`checkStamps`, which
  needs no archive).
- **R87/R88**: a continental spot check (54 Mawaqit calendars in France, Belgium, the Netherlands,
  Germany and Canada, each survey repeatable): the Netherlands gains a Maghrib floor by month,
  France gains Diyanet Europe as a member, Belgium's near-horizon "Fajr family" were congregation
  times (faults), Germany gains a late-dawn member and an 18° DawnOrMiddle member. Belgium's Maghrib
  keeps §3.6's cap at EMB's own minutes (EMB is followed by 42 of 64 Brussels mosques); the Diyanet
  calendars' earlier Maghrib is recorded. Recorded, not fixed: Diyanet Europe beyond its city tables.
- **R89**: a point table's final end, after every clamp, bounds the ends at every point in its reach.
- **R90**: no repair pulls Fajr before a real dawn. At the polar edge an authority's sunrise
  precaution comes before another member's dawn, and IRN's summer rule puts its own Fajr after its
  sunrise; the engine keeps Fajr before the sunrise it shows, moving the sunrise later to the minute
  after Fajr (never past the sun's own) before it moves Fajr, and where the sun has risen by that
  Fajr it moves Fajr to the minute before and declares it not followed; a cautious day pairs the Fajr
  shown with its own member's sunrise. What remains: on up to nine days a place in the week the sun
  stops setting the sun has risen by every member's Fajr (declared), and the members' sun models can
  disagree on whether it set at all.
- **R91**: About's cautious text names the Maghrib cap on a day it decided the Maghrib (§2.3, §3.6):
  the most-followed timetable's own minutes rather than the later Maghrib of the members named.
- **R92**: R62's cap on a limited Isha reads the next day's end of eating as shown, after that day's
  clock rule, band, table bound and R90, so a limited Isha never lands on the end shown.
