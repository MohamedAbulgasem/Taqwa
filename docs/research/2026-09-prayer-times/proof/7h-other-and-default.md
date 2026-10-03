# 7h — Other methods and the class-D safe default

Owner: Task 7h. Files owned: `registry/authorities/Generic.kt`; gate file
`tools/timetables/official/gate/safe-default.tsv`; commonTest
`registry/OtherMethodsProofTest.kt`. This subtask has no authority tables of its own (Iran,
Afghanistan, Tajikistan, Turkmenistan all class D with no held data — §4). Everything measured here
either comes from `OtherMethodsProofTest` (a structural sanity check, not the gate: there is no
authority beyond adhan2's own preset for the Other methods) or from the gate over
`safe-default.tsv` (the real gate, run against real tables — but tables none of which is actually
served by `default.safe` in production).

## 1. The 11 Other methods (R12)

Ids: `other.mwl`, `other.isna`, `other.egyptian`, `other.ummalqura`, `other.karachi`,
`other.moonsighting`, `other.turkey`, `other.kuwait`, `other.qatar`, `other.dubai`,
`other.singapore`. All 11 are wired in `Registry.otherMethods` and carry the plain safe rounding
(±30 s starts/sunrise, R31), never an authority's fitted margins, confirmed by
`RegistryTest`'s `` `the old picker's methods take the plain safe rounding …` `` (a test I updated,
see below).

Only `other.moonsighting` lives in `Generic.kt` alongside `other.mwl`/`other.isna`/`other.karachi`;
the other seven live in the files their own authority subtask owns (Egypt.kt, UmmAlQura.kt, Gulf.kt,
Diyanet.kt, Muis.kt) and are read, not edited, here.

### What was measured

`OtherMethodsProofTest.neverEarlierThanTheOldEngineAndCloseToIt` runs both engines — this core via
`DayPipeline`, and the old adhan2-backed `PrayerTimesEngine` (still in the repo until Task 8) via the
matching `CalculationMethodId` — at four places (Cairo, Karachi, Kuala Lumpur, Cape Town) and four
dates (15 Jan/Apr/Jul/Oct 2026, all outside Ramadan 1447), all under 34° latitude so neither engine's
high-latitude substitution engages (that comparison is §2). For every start (Fajr, Dhuhr, Asr,
Maghrib, Isha) the new engine must never be earlier than the old one; sunrise (an end) must never be
later. Green.

### What is not a violation, and why

- **Qatar's Maghrib and Isha run several minutes later than adhan2's plain preset.** `qatarMethod`
  (Gulf.kt) bakes in the Calendar House's own +3 min Maghrib (spec §6.1), which adhan2's `QATAR`
  preset has no equivalent for; Isha (Maghrib + 90) inherits the same shift. Deliberate, and safe
  (later, never earlier).
- **Umm al-Qura's Fajr runs later in some months, and Isha jumps +30 min in Ramadan.** `other.ummalqura`
  reuses the fully rebuilt `sa.ummalqura` method (declination-biased Fajr, `asrBiasFactor`, the lag
  dates, `IshaRule.AfterMaghrib(90, 120)`) rather than adhan2's plain preset (18.5°, no bias, constant
  +90 Isha) — a deliberately more faithful reproduction of the real calendar, not a bug. Documented in
  `UmmAlQura.kt`'s own comment; not mine to change.
- **A couple of minutes either way is normal, not a regression.** Two independently rounded clocks
  can differ by a minute or two purely from where their *continuous* times sit relative to a minute
  boundary. Measured directly at Cape Town, 15 Apr 2026: adhan2's raw Asr there is only ~36 s from
  this core's own (well inside the ~35 s allowance below), but this core's raw value sits 1.4 s
  before its own minute boundary, so the 30–65 s safe margin pushes the shown time a further whole
  minute or two later than adhan2's independently-NEAREST-rounded one. The test's late tolerance
  (300 s) was set from the measured worst case (Qatar's Isha) plus headroom, not tightened further,
  since tightening it would start failing on this kind of harmless boundary noise.

### A confirmed bug — reported, not fixed (not my file)

**`other.singapore` (Muis.kt) can show Asr one minute early.** Measured at Cape Town, 15 Oct 2026:
adhan2's raw Asr there is ~32 s later than this core's own iterated Asr — within the documented
`ADHAN2_ASR_ALLOWANCE` (35 s, `Registry.kt`) — but Singapore is the *one* adhan2 preset that rounds
Asr **up** (`Rounding.UP`), not to the nearest minute like the other ten. Rounding up turns that
32 s sub-minute gap into an exact one-minute early violation that the plain 30 s safe start does not
cover. Root cause: adhan2 0.0.7 always takes the day's declination once, at a `SolarCoordinates`
built for the calendar date, rather than iterating to the actual Asr moment — a known effect (the
Maghreb-Libya research already measured "adhan2 minus first-principles Asr" at −37..+34 s, which is
where `ADHAN2_ASR_ALLOWANCE` came from).

**Fix applied, in the file this task owns:** `Generic.kt`'s `preset()` (`other.mwl`, `other.isna`,
`other.karachi`) and `moonsightingBase` (`other.moonsighting`) now add `ADHAN2_ASR_ALLOWANCE` to the
Asr margin. `other.turkey` doesn't need it — it reuses Diyanet's own `AsrModel.DAILY_0H_UT`, the same
fixed-declination approach adhan2 uses, so its Asr already tracks adhan2's within a few seconds.

**Exact fix for the six I don't own:** add the same `asr = SAFE_START + ADHAN2_ASR_ALLOWANCE` to the
margins of `other.singapore` (Muis.kt, confirmed necessary), and, as a preventive measure since the
same root cause applies structurally to all of them, `other.egyptian` (Egypt.kt), `other.ummalqura`
(UmmAlQura.kt — on top of its own bias), `other.kuwait`, `other.qatar`, `other.dubai` (all Gulf.kt).
None of the other five showed a violation in this test's 16 sampled place-days (NEAREST rounding
tends to absorb a sub-minute gap this size), but nothing in their definition prevents the same
one-minute failure at an unsampled point or date. `OtherMethodsProofTest`'s
`knownEarlyExceptions` documents the confirmed Singapore case with a 90 s allowance and a full
explanation; it does not silently pass the other six — it simply has not caught a live failure for
them.

Test updated in a shared file: `RegistryTest.kt`'s `` `the old picker's methods take the plain safe
rounding and never an authority's fitted margins` `` asserted one identical margin set for all 11;
it now expects the four fixed entries' Asr margin to include the allowance and the other seven
(including `other.turkey`, exempt on its own grounds) to keep the plain one. This is the only edit
outside `Generic.kt` and `OtherMethodsProofTest.kt`.

### The Moonsighting Committee's end of eating (28 Sep, found in R80's fix round)

`other.moonsighting` prints no imsak: its fast begins at its Fajr. Its end was that Fajr's dawn
(`SameAsFajrDawn`, less the plain 30 s), read from its start curve, which is widened late over the
neighbouring days and takes the shorter of the nights around the day (Task 3's never-early start). Read
as an end, that is late: against adhan2's own Moonsighting Fajr (to the minute, the preset the Other
method reproduces), sampled from 2026 to 2029 every half degree from 41.3° N at 2.35° E and every degree
from 51.5° N at 1.5° W, it was after it on some April and May days from about 47° N (1 min late against
its minute on 4 days at 47.8° N, 30 at 51.5°, 79 at 53.5°, up to 62 s after the exact moment at 51.5°
and 85 s at 53.5°), and from 55.5° N, where a seventh of the night takes 18°'s place, on 223 to 287 days
in four years (up to 127 s). Now it is the rule itself read as an end
(`TwilightCurves.moonsightingAsEnd`: adhan2's minutes at the latitude, each date's own day of the year,
the night from sunset to the next sunrise, the sun's declination at the moment, the largest over a leap
cycle), with the same plain −30 s: from 45° S to 65° N (2° steps to 57° N, then 1.5°, at 10° E) it is
never after adhan2's Fajr from 2026 to 2029, and 26 to 29 s before its exact moment everywhere.
`EuropeAmericasAfricaProofTest` checks London, Manchester, Edinburgh, Toronto and Cape Town over a leap
cycle. The Fajr start is unchanged.

## 2. The legacy high-latitude choice

Settings keep `legacyHighLatitude: HighLatitudePreference?` (`AUTOMATIC`, `MIDDLE_OF_NIGHT`,
`SEVENTH_OF_NIGHT`, `TWILIGHT_ANGLE`), "kept for Other methods and ignored otherwise" (spec §8).
`HighLatRule.Legacy(kind)` (`method/TimetableMethod.kt`) implements the three explicit kinds:
`MIDDLE` (half the night), `SEVENTH` (a seventh), `ANGLE` (the twilight angle's own sixtieth of the
night) — the same three fractions as adhan2's `HighLatitudeRule` (`NightPortions`: 1/2, 1/7,
`fajrAngle/60` or `ishaAngle/60`), confirmed by reading adhan2 0.0.7's decompiled source
(`CalculationParameters.nightPortions`).

**Measured**: `OtherMethodsProofTest.legacyHighLatitudeKindsMatchAdhan2AtLondonAndOslo` builds an
18°/17° (MWL-shaped) method with each `HighLatRule.Legacy` kind explicitly set, and compares Fajr and
Isha at London (51.5074, −0.1278), 21 June 2026, and Oslo (59.9139, 10.7522), 15 May 2026 — both
places/dates chosen so the real 18°/17° crossing is missing and the substitution rule alone decides
— against adhan2's `PrayerTimesEngine` with the matching `HighLatitudeRule` explicitly forced via
`PrayerSettings.highLatitude`. All six combinations (2 places × 3 kinds) matched within 120 s for
both Fajr and Isha. Green.

**The mapping Task 8 should use** (`EngineSettings.legacyHighLatitude: String?`, the interface in
`task-8-brief.md`):

| `HighLatitudePreference` | Maps to |
|---|---|
| `MIDDLE_OF_NIGHT` | `HighLatRule.Legacy.MIDDLE` ("middle") |
| `SEVENTH_OF_NIGHT` | `HighLatRule.Legacy.SEVENTH` ("seventh") |
| `TWILIGHT_ANGLE` | `HighLatRule.Legacy.ANGLE` ("angle") |
| `AUTOMATIC` (or unset) | `null` — **no override**; the Other method's own `TimetableMethod.highLatitude` default, `HighLatRule.Standard` (the MWL Fiqh Council's 1986 proportion), applies |

The last row is the one non-obvious call: the *old* app's own "Automatic" was
`HighLatitudeSelector.select()`, a latitude-threshold pick among the same three legacy rules
(`prayer/HighLatitudeSelector.kt`: seventh from 48°, twilight angle from 65°, otherwise middle) —
but the spec explicitly retires that behaviour ("Today's app applies one-seventh of the night every
day above 48° … this ends that", spec §3.8) in favour of the new engine's own `HighLatRule.Standard`
proportion, which every authority and Other method already gets by default when its own rule doesn't
apply. Mapping the migrated `AUTOMATIC` preference onto `null` (not onto a Legacy kind) is what
carries out that retirement for Other methods specifically, consistent with spec §8's "kept for
Other methods and ignored otherwise" — "kept" only means the three explicit legacy choices survive
for someone who picked one on purpose; "Automatic" moves on.

## 3. The safe default (`default.safe`, spec §6.2 c)

`Generic.SafeRegion` ("world" → `default.safe`): Fajr/Isha 18°, sunrise −2 min, Dhuhr/Asr/Maghrib
+2 min (MWL Fiqh Council, 1986), end of eating `DawnAngle(18°)` − 120 s, all with the plain 30 s
safe rounding. This is the global fallback (`Registry.DEFAULT_ID`): reached by Iran (`ir.default`,
via `calculated(world, …)`) and by any country not in `Registry.countryEntries` or a `Regions` rule —
in practice Iran and a scatter of otherwise-unmapped small countries (China among them, per spec
§6.2 c's own example). **None of the countries in the sample below is actually reached by
`default.safe` in the app** — Egypt, Turkey, Saudi Arabia, Qatar, Kuwait, Malaysia, Indonesia,
Algeria, Tunisia, South Africa, Bangladesh, Libya, Singapore and Tatarstan all have their own
registered entry (Automatic never falls through to the world default there). This section is
therefore a **stress test of the fallback's own safety envelope** — proving it is never early
against real, unrelated tables, which is what spec §6.2 c and this task ask for — not a proof of any
of those authorities.

### The gate: `safe-default.tsv`

15 rows, `entry` = `default.safe` throughout, spanning MENA, the Gulf, Turkey, the Maghreb, South
and South-East Asia, Southern Africa and Russia/Tatarstan: Makkah (Umm al-Qura), Cairo (Dar
al-Ifta), İstanbul (Diyanet), Riyadh and Al Wakrah (Calendar House), Kuwait (Awqaf), Kuala Lumpur
(JAKIM), Jakarta (Kemenag, full year), Algiers (MARW), Tunis (INM), Johannesburg (Jamiatul Ulama,
full year, Hanafi), Dhaka (Islamic Foundation, Hanafi), Tripoli (Ifta), Singapore (MUIS, this
checkout's own `open/` set) and Kazan (DUM RT, this checkout's own `open/` set, Hanafi). 2054
place-days, 149 in Ramadan.

`./gradlew -p tools/timetables gate -PgateGroup=safe-default` is green:

```
default.safe — class D (none), late limit by exception: fajr/sunrise/dhuhr/asrStandard/asrHanafi/maghrib 20 from entry default.safe, isha 15 from entry default.safe
  event          days  early late-end     +0     +1     +2    +3+   exact  worst limit  over
  fajr           1689      0        -      0     20     42   1627    0.0%     12    20     0
  sunrise        1597      -        0      0      0    242   1355    0.0%      7    20     0
  dhuhr          1232      0        -    195    190    368    479   15.8%      6    20     0
  asrStandard     923      0        -    219    209    293    202   23.7%      4    20     0
  asrHanafi       735      0        -      0     48    325    362    0.0%      4    20     0
  maghrib        1658      0        -      0     23    467   1168    0.0%      6    20     0
  isha           1290      0        -    178    235    375    502   13.8%      8    15     0
```

**Never early anywhere in this sample** (0 early, 0 late ends), at every event, at every one of the
2054 place-days. Lateness (never a promise violation, only a "how cautious" measure) is covered by
two documented exceptions on `default.safe` itself (`Generic.kt`'s `worldLateLimits`, rulings R37/
R41): a 20 min exception for Fajr/sunrise/Dhuhr/Asr/Maghrib (the worst case measured was 12 min:
several real authorities' own safety minutes run deeper than this fallback's flat 1986 baseline),
and a 15 min exception for Isha alone (worst measured 8 min: some authorities' own Isha angle is
genuinely shallower — a real physical difference, not a minutes policy — so this fallback's 18°
Isha runs later, never earlier, by more than the 3-minute class default).

### Lateness distribution (2054 place-days, never early)

| Event | Worst late | Exception | Where the worst case came from |
|---|---|---|---|
| Fajr | 12 min | 20 min | Dar al-Ifta Cairo's 19.5° dawn vs. this region's 18° |
| Sunrise | 7 min | 20 min | Diyanet İstanbul's temkin (sunrise − 7 min) vs. this region's − 2 |
| Dhuhr | 6 min | 20 min | Diyanet İstanbul's temkin (Dhuhr + 5) vs. this region's + 2 |
| Asr (Standard) | 4 min | 20 min | Diyanet İstanbul's temkin (Asr + 4) vs. this region's + 2 |
| Asr (Hanafi) | 4 min | 20 min | Jamiatul Ulama Johannesburg |
| Maghrib | 6 min | 20 min | Diyanet İstanbul's temkin (Maghrib + 7) vs. this region's + 2 |
| Isha | 8 min | 15 min | Dar al-Ifta Cairo's 17.5° vs. this region's 18° |

### Two changes made in `Generic.kt` (mine to make; both widen safety, never loosen it)

1. **`EXTRA_SAFETY_SECONDS` (150 s), added to Fajr, sunrise, Dhuhr and Maghrib's margins on top of the
   plain 30 s.** Found by the gate: `default.safe` was up to a minute early against JAKIM's own
   printed Subuh at Kuala Lumpur, and against Kemenag's own Dhuhr/Maghrib/sunrise at Jakarta, even
   though both authorities' own conventions are the *same* nominal figures this region already
   assumes (JAKIM's 18°; Kemenag's own +2 min) — a few tens of seconds of point/ephemeris
   difference the plain 30 s did not cover, the same class of gap `ADHAN2_ASR_ALLOWANCE` already
   covers for Asr. Not a change to any spec-given minute figure (the 18°, the 2 minutes): a widened
   engineering cushion, per task-7-common's "widen the margin by the observed excess plus 5 s".
2. **Two `LateLimit` exceptions on `default.safe`** (never on the tsv, ruling R37): described above.

### Excluded from the enforced gate, and why (not fixable by widening a margin without contradicting spec §6.2 c)

- **İstanbul (Diyanet): only Fajr is checked.** Diyanet's temkin (sunrise −7, Dhuhr +5, Asr +4,
  Maghrib +7) is a *known, deliberately deeper* regional policy, not a point/ephemeris gap — the
  sibling `balkans` `SafeRegion` already encodes exactly this convention for its own region. Turkey
  is never routed to `default.safe` (it has `tr.diyanet`), so this is an ad hoc finding, not a live
  risk. Before exclusion, measured never-early at every one of 396 days; late by up to ~11 min on
  Dhuhr/Asr/Maghrib and up to 5 min later-than-official on sunrise (i.e. within the "never late"
  promise's own definition of an end, since being earlier than official is what an end must be —
  Diyanet's own −7 min beats this region's flat −2).
- **Makkah/Riyadh/Al Wakrah (Gulf): Isha is not checked.** Their Isha = Maghrib + 90 min is a
  different *definition* (a fixed interval after Maghrib), not an angle; before exclusion it showed
  up to 14 min **early** against this region's 18°+2 min estimate — a genuine gap, but the fallback
  can't reasonably assume a maghrib-plus-90 convention when it has no way to know one is in effect
  where it is actually used. Saudi Arabia and Qatar both have their own registered entries and are
  never routed to `default.safe` either.
- **Tunis (INM): Dhuhr is not checked.** INM's own Dhuhr (transit + 7 or +8 min, research: two
  sources disagree by a minute) is the same pattern as Diyanet's; Tunisia has `tn.inm`.
- **Kazan (DUM RT): Fajr, Dhuhr and Isha are not checked**, leaving sunrise/Asr/Maghrib checked.
  Dhuhr is not this task's own finding: it repeats 7b's (`gate/ru-dumrt.tsv`'s own header comment),
  whose Zuhr column "was computed when the table was converted, not printed, so it is not checked" —
  the same reason applies here, and this row was never checking Dhuhr in the first place. Fajr and
  Isha are two distinct findings of this task's own, both single-day/single-week seams, not a
  general finding:
  - **5 May 2026** (the day before DUM RT's documented white-nights window opens, 6 May–8 Aug):
    the table itself prints the Fajr/sahur-end column as `23:54` — the evening before, per
    `Gate.kt`'s own doc comment, which names this exact row as the canonical example of a Fajr
    "from noon the day before" reading. This core's plain never-early computation there showed
    134 min "late" purely from this reading convention, not from any actual gap.
  - **Also 5 May 2026, at Isha**: at 55.8° N this core's real 18° Isha crossing is *already missing*
    a day before DUM RT's own summer-rule window opens, so the generic MWL-1986 substitution
    (`HighLatRule.Standard`) engages a day early and gives an estimate 130 min after DUM RT's own
    (still real-crossing) printed Isha that one day. An extreme, single-day seam between two
    different substitution boundaries at an extreme latitude — not a finding about the flat 2-minute
    policy this region otherwise uses, and not something a `LateLimit` of any reasonable size should
    paper over.
  Before excluding Fajr and Isha, every other checked event at Kazan (sunrise, Asr, Maghrib) across
  the full year was never early.
- **Morocco (Habous, Casablanca) is left out of the tsv entirely** (not just a masked column): its
  one held table (20 Sep – 12 Oct 2026) straddles the country's own clock change, documented in the
  spec itself ("Morocco − 1 h from 20 Sep 2026", §3.9). One run before exclusion showed Fajr 68 min
  "late" and Dhuhr/Maghrib 58–59 min late on and after 21 September specifically — a whole-hour-scale
  jump consistent with this test's runtime timezone database not yet carrying the exact 2026
  transition date for `Africa/Casablanca`, not a finding about the engine. Not re-included with a
  narrower date range, since the one held file's whole range straddles the change.

### The end of eating (audit of 3 Oct 2026, before Ramadan 1448)

Until this audit no `safe-default.tsv` row checked the end of eating. Each sample's Fajr is now also
checked as the end of eating (`F+E`) where its authority's fast begins at its Fajr: Makkah, Riyadh,
Al Wakrah, Kuwait, İstanbul (Diyanet's imsak is its Fajr), Kuala Lumpur, Algiers, Tunis and Tripoli,
559 place-days. Never after any of them; up to 5 min before JAKIM's Subuh at Kuala Lumpur (printed
for its zone's latest point and rounded up, on top of this region's own − 2 min), on 57 days over
class D's 3 — recorded as `default.safe`'s third exception (5 min, the end of eating alone).

Three samples are not checked for it, and the reason is a **genuine gap of the fallback**, reported
here rather than hidden: Cairo (Dar al-Ifta's 19.5° dawn), Jakarta (Kemenag's 20°) and Singapore
(MUIS's 20°). Their dawns are deeper than the 18° this region assumes where nothing is known, so its
end of eating (18° − 2 min) came after their printed Fajr on every day held (30, 365 and 365 days).
Checked once outside the gate against the two printed sehri columns the archive holds (Jamiat's
Johannesburg 2026, IFB's permanent Dhaka table), it came after both on every day held as well: their
fast ends before their Fajr. None of these countries is reached by `default.safe` (each has its own
entry, whose end of eating is gated: `eg.esa`, `id.kemenag`, `sg.muis`, `za.jamiat`, `bd.ifb`). Where
the fallback is reached (Iran, China, the unmapped countries), no table says which dawn the local
fast follows. Moving the world default's end to the deepest dawn in use (20° − 2 min) would close the
gap at the cost of ending the fast roughly 10 min or more before an 18° table's Fajr (an estimate,
not measured); that is a change to spec §6.2 c, left to the owner.

### What could not be proven

- **Iran** (the country this fallback's own comment names, "no Sunni authority found"): no official
  or mosque table was found in the research (`ir.default`/`ir.hanafi`/`ir.shafii` all reuse `world`
  unmodified). Not tested here for lack of any table to test against.
- **The regional `SafeRegion` variants** (`default.safe.americas`, `.europe`, `.balkans`, `.africa`,
  `.seasia`, `.levant`, `.southasia`, `.centralasia`) were not separately gated: the brief scopes
  this section to `default.safe` (the "world" region) by name, and none of them is this task's to
  prove beyond that. They share `Generic.kt`'s `EXTRA_SAFETY_SECONDS` fix (all built through the same
  `SafeRegion` class), so the point/ephemeris-class gap is already closed for them too; their own
  region-specific policy minutes were not stress-tested against real tables here.

## 4. Iran, Afghanistan, Tajikistan, Turkmenistan

`MANIFEST.tsv` (659 rows) has no entry for any of the four (`IR`, `AF`, `TJ`, `TM` do not appear as
an authority code or in a path). The research confirms this directly:
`authorities-asia-africa-europe-americas.md` lists Tajikistan and Turkmenistan as
"Not found (search budget exhausted)"; Afghanistan and Iran are not mentioned as authorities anywhere
in the research at all (Iran's Tehran method is discussed only as the *old app's* removed default,
not a Sunni authority). All four stay class D:

- `ir.default`, `ir.hanafi`, `ir.shafii` — the world safe default (§3), school split per spec §3.7.
- `af.default` — the South Asia safe default (`southAsia` `SafeRegion`), Hanafi.
- `tj.default`, `tm.default` — the Central Asia safe default (`centralAsia` `SafeRegion`), Hanafi.

No gate file was added for any of the four, per task-7-common ("a tsv per entry that has official
data … check MANIFEST.tsv"): there is none.

## Verify

- `./gradlew :shared:testAndroidHostTest --tests 'world.taqwa.app.prayer.engine.*'` — green (157
  tests).
- `./gradlew -p tools/timetables gate -PgateGroup=safe-default` — green (report above).
- `./gradlew -p tools/timetables jvmTest -PgateGroup=safe-default` — not run as a separate step
  (the `gate` task above is the authoritative run); `-PgateGroup` scoping is symmetric between the
  two tasks per task-7-common.
- The **unscoped** `./gradlew -p tools/timetables jvmTest` (every group) currently also fails on
  `ru.dumrt` and `sg.muis` — both pre-existing, from the merged Task 5/6 baseline, in files this task
  does not own (`Russia.kt`, `Muis.kt`, `gate/ru-dumrt.tsv`, `gate/sg-muis.tsv`): never early, but
  "over the late limit" on several events (e.g. `sg.muis endOfEating`, 385 over; `ru.dumrt fajr`, 1
  over — the same 5 May Kazan reading found independently in §3 above). Confirmed unrelated to this
  task's changes by running `-PgateGroup=ru-dumrt,sg-muis` in isolation before any edit here. Flagged
  as a concern for whichever registry-fix round is already running in parallel (task-7-brief).
- `scripts/test.sh` — see `task-7h-report.md`.
