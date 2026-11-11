# Watch companion: the execution map for plans 2–5

> **For agentic workers:** this is the Lead's schedule for plans 2–5, not a plan of its own. Each task is built from its own plan with superpowers:subagent-driven-development; this file says in which worktree, after what, beside what, and on which device. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** the shortest build of plans 2–5 with no loss of quality. Every task keeps its failing test first, its two independent reviewers and its Google or Apple validators. Only the order, the worktrees and the turns on the devices change.

**Shape:**
- Three lane worktrees branch off `watch-companion`: Lane P (the phones and the shared core), Lane W (the Galaxy watch app and the face's tools, the Wear emulator's main user) and Lane A (the Apple Watch, plan 4's own worktree). `site` becomes the integration worktree.
- Code and reviews run side by side in the lanes. Device steps take turns through locks.
- Lanes take each other's work only through `watch-companion`, by merge, so every reviewed commit keeps its hash.
- By a rough count of task sizes (not measured), the build reaches candidate C's gate in about 23 task-lengths on three lanes and about 30 on two, against about 55 with the plans one after another.

**Plans:**
- `docs/superpowers/plans/2026-10-23-watch-companion-2-phone-feed.md` (P2)
- `docs/superpowers/plans/2026-10-24-watch-companion-3-wear-app.md` (P3)
- `docs/superpowers/plans/2026-10-24-watch-companion-4-watchos.md` (P4)
- `docs/superpowers/plans/2026-10-24-watch-companion-5-ship.md` (P5)

`P2.3` means plan 2's Task 3.

**Spec:** `docs/superpowers/specs/2026-10-19-taqwa-watch-companion-design.md`.

**Ledger:** `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site/.superpowers/sdd/2026-10-19-watch-companion-1-galaxy-face/progress.md`, under a heading "Execution map" for the Lead's lines, and each plan's own heading for its tasks.

**Binding rulings:** 22 (the re-request safety net and its 12-hour gate), 36 (the Moon's option (d)), 38 (screen readers are a ship gate), 39 as amended (the city and the bearing to 1°, never coordinates, only to his own paired watch; "Only your own devices can see your location." in seven languages; one relay sentence in the policy), 41 (the Moon's filled always-on hands) and **42**, which replaced Ruling 40 on 24 October. Under 42 the face's reading order is the time, the prayer with its countdown and time, then the dates (slots declared 7, 0, 1, 2, 3, 6, 4, 5, WIP `07e511be`). Plans 2–5 now cite 42.

## Global Constraints

**Quality, unchanged by this map:**
- A task runs exactly as its plan writes it: the failing test, the code, its plan's checks and validators, two independent reviewers with different lenses, fix rounds until clean or a Lead's ruling, then its commit. No task drops or defers a step to go faster, and no red step is committed.
- **The validators stay in every task that touches what they check:**
  - Google's `wff-validator` and the Watch Face Push validator (`tools/watchface/validate.sh`), for any face change;
  - plan 2's three library checks (merged permissions, manifest additions, the tracker diff), for any new dependency;
  - `lint`, and the merged-manifest permission checks;
  - Apple's builds and the bundle checks (`scripts/ios-build.sh`, `scripts/check-watch-bundle.sh`, `scripts/watch-build.sh test`);
  - `tools/i18n-check.py`;
  - `scripts/test.sh`, which runs `swift test` first once P4.2 is in.
- The one split this map makes is the one plan 2 already allows: P2.3's Steps 4 (its `pass.sh` run) and 6 wait for P2.2 (P2.3b below), and each half gets its own review.

**How many lanes at once** (Mohamed's rule of 18 October, `usage-budget-pacing`):
- At most 2–3 workflows at once, counting the Devs' sessions. The default here is three watch lanes while no Dev workflow runs, and two while Dev 1 or Dev 2 runs one. Question 1 below asks whether three may run beside Dev 1.
- `get_usage` is checked before each wave, and 5 % of the weekly limit is kept for the release. The weekly reset is Friday 09:00 SAST (the last was 23 October).
- Never `SendMessage` a running workflow agent: brief it through its script.
- The Mac mini has 24 GB. At most two Android emulators of these lanes run at once: `emulator-5558`, and `emulator-5562` only during a paired step.

**Worktrees and branches:**
- `site`, on `watch-companion`, is integration only once plan 1's face workflow (`wf_d675c184-3e6`) ends. Only the Lead commits there: the merges, the plan documents, and nothing else.
- The lanes are made from `watch-companion`'s head, after the Lead has committed plans 2–5 and this map there:
  - `~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/watch-feed`, branch `watch-feed` (Lane P);
  - `…/watch-wear`, branch `watch-wear` (Lane W);
  - `…/watch-apple`, branch `watch-apple` (Lane A, plan 4's own).
- Each new worktree gets a copy of `site`'s `local.properties`, which is git-ignored. Nothing else of `site`'s ignored files is copied: no `keystore.properties` exists in any worktree.
- The ledger lives in `site` alone (git-ignored). Lanes append to it by its absolute path with `>>`, one line per write, and never rewrite or `Edit` it, so two lanes cannot clobber each other's lines.
- Git identity: the repository's local config (Mohamed Abulgasem <mohamed.abulgasem.ly@gmail.com>) holds in every worktree. No `Co-Authored-By` and no AI line, ever. Nothing is pushed until Mohamed says so in chat.

**Integration:**
- A lane takes others' work with `git merge watch-companion` into its own branch, at the start of a task that needs it, never by rebase.
- The Lead merges a closed task into `watch-companion` in `site` with `git merge --no-ff <lane>`. Then he runs `scripts/test.sh`, and `scripts/ios-build.sh` too when the task touched `iosApp/`, `shared/` or `scripts/`. He records the merge in the ledger. A red merge is fixed in `site` before any lane takes it.
- `main` reaches `watch-companion` by merge too (`git merge main`), at the main-merge points below. The squash to `main` at the end (P5's M13) leaves none of the merge history, as for every Taqwa branch.
- **Generated files are never merged by hand.** On a conflict in `watchcore/vectors/**`, `tools/watchface/phone-forms.json`, `watchface/src/main/res/raw/watchface.xml`, the generated fonts or `iosApp/iosApp.xcodeproj/project.pbxproj`, take one side, regenerate on the merged tree with the generator (`-Ptaqwa.updateVectors`, the face generators, `tools/add-watch-targets.rb`), run the generator's check and `scripts/test.sh`, and commit the regeneration in the merge.
- A task that regenerates vectors takes `watch-companion` first, and regenerates last, just before its commit.

**Devices:**
- **Locks.** A device step takes its lock with `mkdir ~/.cache/taqwa-locks/<name>.lock`, which is atomic, and writes `owner` inside it (lane, task, step, start time). A lane that cannot take it waits, polling every 60 s, and logs the wait in the ledger. It holds the lock for its device step only. Before releasing it, the step leaves the device as plan 1 left it: Arch, Amber, the wearer's slots empty, automatic time, TalkBack off, font scale 1.0, the watch in English, the sample feed. Then `rm -r` releases it. A lock older than 6 hours whose owner is gone is the Lead's alone to clear.
- **The locks:**
  - `wear-5558`: `Taqwa_Wear_OS_6`, `emulator-5558`;
  - `phone-5562`: `Taqwa_Phone_Wear`, `emulator-5562`, always taken after `wear-5558` for a paired step;
  - `ios-pair`: the iPhone 17 Pro `2D01CE58-7F12-45FC-8DB0-D63330AE9BB1` with the Series 12 (46mm) `C803D94B-7239-435B-9CEB-2A6AD75F141F`;
  - `sim-se3`: the SE 3 (40mm) `FCA316A7-A49B-4795-9610-7D61FD958A8A`, unpaired.
- **Never touched:** `emulator-5556` (the Lead's phone), `emulator-5560` (Dev 2's), the plugged-in phone `KB2604110095`, the work iPhone 12, and his or her phones and watches before plan 5's device passes. Stop an emulator only with `adb -s emulator-55xx emu kill`, never with `pkill`.
- **Kotlin/Native's simulator tests** (`scripts/test.sh`, `:shared:iosSimulatorArm64Test`, `:watchcore:allTests`) boot their own default simulator. They are not locked by default. If a run fails to boot its device, or finds it busy, while another lane's run is on, re-run it under an `ios-sim-tests` lock, and the Lead makes that lock the rule for every lane.

## Review Focus

What most often breaks when work runs side by side:
1. **Two lanes on one device**, or a device left in another lane's state: TalkBack on, an Arabic watch, a held sample, font scale 1.3. Expected: the locks, the state put back before each release, and a shot taken under someone else's state is void and taken again.
2. **Fixtures and vectors regenerated on two branches.** Expected: the order P2.3a, then P3.3, then P3.10, then P4.3, then P5.5 for `Fixtures.kt` and the samples, each regenerated after taking `watch-companion`, and generated files never hand-merged.
3. **A Dev's branch reaching `main` while a lane is mid-task.** Expected: `main` is merged only at the points below, and a lane takes it at the start of its next task, never in the middle of one.
4. **A merge that is green on each branch but red together:** two edits to one `strings.xml`, the catalog, the Wear manifest or `TaqwaWearApp.kt`. Expected: `scripts/test.sh` in `site` after every merge, before any lane takes it.
5. **The ledger overwritten** by two agents at once. Expected: append-only writes with `>>`.

## The graph

"Needs" are the hard dependencies: the plans' own, plus the file overlaps this map orders. "Soft" ones are merges that only avoid a conflict later.

**Gates outside plans 2–5:**

| Id | What | Needs |
|---|---|---|
| G12 | Plan 1's Task 12 closed under Ruling 42 (the face workflow, running now) | — |
| G13 | Plan 1's Task 13 closed (Ruling 41, the Moon's filled always-on hands) | G12 |
| MM1 | Dev 1's `fix/qibla-b20-b21-b40-b41` on `main`, then `git merge main` into `watch-companion`. Dev 1's `fix/widgets-b16-b35-b36-b37` (which touches `PrayerNaming.kt` and `iOSApp.swift`) and `fix/cautious-wording` (strings and site pages) come with it if they have landed | Dev 1 |
| MM2 | Build 39 cut on `main`, then merged into `watch-companion` | Mohamed's release of 39 |
| SIGN | Mohamed's one sign-in on `emulator-5562` (P2's question 2) | the AVD, from P2.7 Step 1 |

**Plan 2, the feed:**

| Task | Needs | Lane | Writes, beyond its own new files | Devices |
|---|---|---|---|---|
| P2.1 Words | none (soft: MM1, for `PrayerNaming.kt`) | P | `shared` `i18n/`, `hijri/`; `widgetcore` `PrayerNaming.kt`; `shared/build.gradle.kts` (the `:watchcore` line) | K/N tests |
| P2.2 Forms, Urdu digits | P2.1, G13 | W | `tools/watchface/*-fit.py`, `subset-noto.py`, `README.md`; `tools/face-centre.py`, `face-moon.py`; `watchface/` fonts and `watchface.xml`; `shared/build.gradle.kts` (test input) | `wear-5558` (Step 8) |
| P2.3a Builder, contract (Steps 1–3, Step 4's tests, Steps 5 and 7) | P2.1, G12 (soft: MM1, for `localizedGroupedKm`) | P | `watchcore` `WatchLinkContract.kt`, `Fixtures.kt`, `vectors/`, `WatchFeed.kt` (KDoc); `shared/build.gradle.kts` (test input) | K/N tests |
| P2.3b Step 4's `pass.sh`, Step 6 (`store.sh`, the Urdu shots) | P2.3a, P2.2 | W | `tools/watchface/store.sh`, `README.md`; report shots | `wear-5558` |
| P2.4 Sync | P2.3a (soft: MM1, for `PrayerRoutes.kt`) | P | `notifications/*`, `AppContainer.kt`, `SettingsKeys.kt`, `TasbeehViewModel.kt`, `PrayerRoutes.kt`; the interim `WatchLink.{android,ios}.kt`; `shared/build.gradle.kts` and `shared/compose-stability.conf` (Ruling 45); its Ruling 50 follow-up also `watchcore` `WatchLinkContract.kt` (the delivery and the receivers' rules) | K/N tests |
| P2.5 Android sender | P2.4 | P | `gradle/libs.versions.toml` (`play-services-wearable`); `shared/build.gradle.kts`; `androidApp` manifest and `res/` | — |
| P2.6 Wear receiver | P2.3a, Ruling 50 (its "[Lead/int R50]" ledger line) | W | `wearApp` manifest, `res/`, `FeedStore.kt`, `TaqwaWearApp.kt`, `StatusActivity.kt`, `src/debug`, `src/release`; the catalog (P2.5's line, word for word) | — |
| P2.7 Android pass | P2.2, P2.3b, P2.5, P2.6, SIGN | W | `scripts/phone-emulator.sh`, `wear-pair.sh`; `WatchFeedDebug.kt`; `androidApp/src/debug/`; `tools/watchface/README.md` | `wear-5558` + `phone-5562` (+ 5564) |
| P2.8 iPhone sender | P2.4 (soft: MM1, for `iOSApp.swift`) | P | `shared` iosMain `watchfeed/`, `DataStoreFactory.ios.kt` (comment); `iosApp/iosApp/iOSApp.swift` | `ios-pair` (Step 5) |

**Plan 3, the Galaxy watch app:**

| Task | Needs | Lane | Writes, beyond its own new files | Devices |
|---|---|---|---|---|
| P3.1 Tasbeeh rules move | none | W | moves `shared` `tasbeeh/` to `:watchcore`; `TasbeehViewModel.kt`, `TasbeehStore.kt`; `shared/build.gradle.kts` (P2.1's line, identical) | K/N tests |
| P3.2 Compass rules move, with B40 | P3.1, MM1 | P | moves `shared` `qibla/` rules to `:watchcore`; `QiblaMath.kt`, `CompassSource.kt`, `HeadingFilter.kt` | K/N tests |
| P3.3 The screens' words in the feed | P3.1, P2.3a | P | `watchcore` `WatchFeed.kt`, `WatchRules.kt`, `Fixtures.kt`, `vectors/` (with `phone/`) | K/N tests |
| P3.4 The frame | P3.3, P2.6 | W | `wearApp` manifest, `TaqwaWearApp.kt`, `FeedStore.kt`, `StatusActivity.kt` (deleted), `res/values-*`, `src/debug`; `tools/watchface/ref.py`, `talkback.sh`, `README.md` | `wear-5558` |
| P3.5 Today | P3.4 | W | `wearApp` `today/` | `wear-5558` |
| P3.6 Tasbeeh | P3.4 | W | `wearApp` `tasbeeh/`; `tools/watchface/README.md` (the rotary's sign) | `wear-5558` |
| P3.7 Qibla | P3.4, P3.2 | P | `wearApp` `qibla/` | `wear-5558` |
| P3.8 Tile | P3.5 | W | `wearApp` `tile/`, manifest, `build.gradle.kts`, `FeedStore.kt`, `TaqwaWearApp.kt`, `DebugFeedReceiver.kt`; the catalog | `wear-5558` |
| P3.9 Full pass | P3.5–P3.8, P3.11 | W | report; `tools/watchface/README.md`; spec 2.4–2.6 | `wear-5558`, long |
| P3.10 The phone fills the words | P3.3, P2.5, P2.8 | P | `shared` `watchfeed/` builder, words, inputs, `WorstFeeds.kt`, the size tests (`AndroidWatchLinkTest`, `IosWatchLinkTest`); `Fixtures.kt`, `vectors/`; plan 2's table; `TaqwaWearApp.kt` (a check) | K/N tests |
| P3.11 The face says 12-hour times | P3.3, G12 | W | `wearApp` `complication/ComplicationFields.kt`, `NextPrayerSource.kt` and their tests | `wear-5558` (Step 4) |

**Plan 4, the Apple Watch** (every task in Lane A, `watch-apple`; the Lead merges it into `watch-companion` after each one):

| Task | Needs | Writes outside `iosApp/`, beyond `tools/add-watch-targets.rb` | Devices |
|---|---|---|---|
| P4.1 Vectors for the port | P2.3a, Ruling 50 (the receivers' rules in `contract`) | `watchcore` `SwiftPortVectorsTest`, `vectors/feed-vectors.json` | — |
| P4.2 The Swift core | P4.1 | `scripts/test.sh`, `.github/workflows/ios.yml`, `.gitignore`, `README.md` | `swift test` on the Mac |
| P4.4 The targets | P4.2, P5.1 | `scripts/ios-build.sh`, `watch-build.sh`, `check-watch-bundle.sh`, `version-files.sh`'s `IOS_PLISTS`; `tools/watch-art.py`, `i18n-check.py`; `ios.yml`; the `pbxproj` (by the script) | `sim-se3` |
| P4.5 Receiving the feed | P4.4 | — | `sim-se3` |
| P4.5B The link end to end | P4.5, P2.8; never beside P4.10 | report | `ios-pair` |
| P4.3 Words, clock and ring digits | P4.2, P3.10 | `watchcore` `WatchScreenWords.kt`, `Fixtures.kt`, vectors; `shared` reader, `WatchWords.kt`; plan 2's table | — |
| P4.6 Complications, Smart Stack | P4.3, P4.5 | report | `sim-se3`, `ios-pair` (Steps 6–7) |
| P4.7 Pages, Today | P4.6 | — | `sim-se3` |
| P4.8 Tasbeeh, the crown | P4.7, P3.1, P3.6 | `watchcore` tasbeeh vectors | `sim-se3` |
| P4.9 Qibla | P4.7, P3.2, P3.7 | `watchcore` compass vectors | `sim-se3` |
| P4.10 Add to Apple Watch | P2.8; never beside P4.5B | `shared` `watchface/`, `feature/settings/WatchRows.kt`, `SettingsRootScreen.kt`, Compose strings; `iOSApp.swift` | `ios-pair` |
| P4.11 The simulator pass | P4.1–P4.10 | report, plan 4's "as built" | `ios-pair`, `sim-se3` |

P4.10 may run before P4.8 and P4.9 while they wait for P3.6 or P3.7.

**Plan 5, ship:**

| Task | Needs | Lane | Writes, beyond its own new files | Devices |
|---|---|---|---|---|
| P5.1 One version | G12 | P | `scripts/bump-version.sh`, `test.sh`; `tests.yml`; `wearApp/build.gradle.kts` (comment); `watchface/build.gradle.kts` | — |
| P5.2 The face key, token and gate | G13 | W | `tools/watchface/validate.sh`, `README.md`; `tools/face-art.py`; `docs/STORE-CHECKLIST.md` §3 | `wear-5558` (Step 3, brief) |
| P5.3 R8, staging, unsigned | P2.7, P3.9 | W | `wearApp/build.gradle.kts`, manifest; `androidApp/build.gradle.kts`; the catalog; `WearMainActivity.kt`, `WearManifestTest.kt` | `wear-5558` + `phone-5562` |
| P5.4 The face inside the app | P5.1, P5.2, P5.3 | W | `wearApp/build.gradle.kts`, manifest, `permissions.txt`, `WearManifestTest.kt`; `watchface/build.gradle.kts`; plan 1's `tools/watchface/*.sh` | `wear-5558` |
| P5.5 Watch Face Push | P5.4 | W | the catalog, `wearApp/build.gradle.kts`, manifest, `TaqwaWearApp.kt`, Today, `DailyNudge.kt`; `watchcore` `WatchFeed.kt`, `WatchLinkContract.kt`, vectors; `shared` `WatchWords.kt`, `WatchFeedBuilder.kt` | `wear-5558` + `phone-5562` |
| P5.6 The phone's watch rows | P5.5, P4.10 | P | `WatchRows.kt`, `SettingsRootScreen.kt`, `SettingsRoutes.kt`, Compose strings; the catalog, `shared/build.gradle.kts` | `phone-5562` + `wear-5558`, `ios-pair` |
| P5.7 The release path | P5.1–P5.5, P4.11 | site | `scripts/release.sh`, `ios-release.sh`, `check-watch-bundle.sh`, `ios-build.sh`; `docs/STORE-CHECKLIST.md` | the Lead's archive, with Mohamed's OK |
| P5.8 The watch apps' own words | P5.5, P3.4, P4.4 | W | `wearApp` and `watchface` `res/values-*`; plan 4's `.lproj`; `tools/i18n-check.py` | `wear-5558` (Step 4) |
| P5.9 The privacy text | P2.3a (its Step 7 report), MM1 (`fix/cautious-wording`); M3 to close | P | the `InfoPlist.strings`, Compose strings, `PRIVACY*.md`, `site/pages/`, `docs/store/listing/`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `docs/STORE-PRIVACY.md`, `scripts/test.sh` | — |
| P5.10 README, site, store copy, notes | P5.9 | P | `README.md`, `site/pages/`, `docs/store/listing/`, `tools/store-listing-check.py` | the browser pane |
| P5.11 Native review | P5.6, P5.8, P5.9, P5.10, plans 2–4 closed; M8 to close | P | every file with a draft marker; `docs/BUILD-LOG.md` | — |
| P5.12 The emulator gates | P5.3–P5.7, P3.11 | site | `complication/` log lines, `WristCompass.kt`, `tile_preview.png` | 5566, 5568, `wear-5558` (12–30 h), `phone-5562` |
| The cut gate, then candidate C | P5.1–P5.8, P5.10, P5.12 (Steps 0, 1, 3, and the call 6 follow-up), P5.9 with M3, P5.11 with M8, MM2 | Lead | `bump-version.sh V C` | — |
| P5.13 His Galaxy, both phones | C | site | the smoke tests | his hands, over wireless debugging |
| P5.14 Only if check 4 says so | P5.13 | site | the face's slot order | `wear-5558` |
| P5.15 Her Apple Watch | C uploaded, M6, M10a | site | the smoke test; `Taqwa.watchface` for C+1 | her devices |
| P5.16 Screenshots | Galaxy: a P5.13 sitting; Apple: P4.11 and P5.11 | site | screenshots, README and site images | his Galaxy; a simulator |
| P5.17 The stores | everything above | site | `docs/STORE-CHECKLIST.md`, the ledger | Play Console and App Store Connect, his |

The critical path runs through Lane W's device chain: P3.1, P2.2, P2.6, P2.3b, P3.4, P3.5, P3.6, P3.8, P3.11, P3.9, P2.7, P5.3, P5.4, P5.5, P5.8, then P5.12 and the cut gate. Lane P keeps it fed (P2.3a and P3.3 before P3.4), and Lane A runs beside both.

## The waves

| Wave | Starts when | Lane P (`watch-feed`) | Lane W (`watch-wear`) | Lane A (`watch-apple`) | Also running |
|---|---|---|---|---|---|
| 0 | now | the Lead's Steps 1–3 (no model cost) | — | — | the face workflow, Dev 1 |
| 1 | now | P2.1 | — | — | the face workflow (G12, G13), Dev 1 |
| 2 | the face workflow ends (G12, G13) | P2.3a, then P5.1 | P3.1, P2.2, P5.2 | — | Dev 1 |
| 3 | P2.3a and P3.1 merged | P3.3, P2.4, P2.8, P2.5 | P2.6, P2.3b | P4.1, P4.2, P4.4 (after P5.1), P4.5, as a third lane if the cap allows | |
| 4 | P3.3 and P2.6 merged; MM1 for P3.2 | P3.2, P3.10, P3.7 | P3.4, P3.5, P3.6, P3.8, P3.11 | P4.5B (after P2.8), P4.3 (after P3.10), P4.6, P4.7 | |
| 5 | P3.4 to P3.8 merged | P5.9, P5.10 | P3.9, then P2.7 (after SIGN) | P4.8, P4.9, P4.10, P4.11 | |
| 6 | P2.7, P3.9 and P4.10 merged | P5.6 (after P5.5), then P5.11 (after P5.8) | P5.3, P5.4, P5.5, P5.8 | closed | |
| 7 | every lane merged and closed | `site`, one lane: P5.7, P5.12 (the 12-hour observation started at day's end and left overnight), the cut gate and C, then P5.13, P5.15 and P5.16 side by side, C+1, P5.17 | | | |

**With two lanes only** (a Dev workflow running, or Mohamed's answer to question 1): Lane A does not open. Lane P runs P4.1, P4.2, P4.4 and P4.5 in `watch-apple` straight after P2.5, then P3.2 and P3.10, then the rest of plan 4, and P3.7 moves to Lane W after P3.6.

**In a lane, the order is a priority, not a wait.** When a task's "Needs" are not merged yet, the lane takes its next ready task and comes back to it.

## Merge order into `watch-companion`

Tasks merge as they close, with these orders held:
1. P2.1 before P2.2, P2.3a and P3.1 are merged. P3.1's `shared/build.gradle.kts` line is P2.1's, word for word, and merges clean.
2. P2.3a before P3.3: one `Fixtures.kt` and one set of vectors, regenerated after the merge (Review Focus 2).
3. P2.6 before P3.4. P3.4 then moves P2.6's `StatusActivity.onResume` lines into `TaqwaWearApp.onAppOpened`, deletes `fingerprintFile` in `FeedStore.clear()`, and writes P2.7's three line edits into the ledger (P3 Task 4 Step 6).
4. P5.1 before P4.4, so P4.4 puts its two plists in `IOS_PLISTS`.
5. P3.10 before P4.3; P3.1 and P3.6 before P4.8; P3.2 and P3.7 before P4.9; P2.8 before P4.5B and P4.10.
6. Every plan 4 task right after it closes. Once P4.2 is in, every lane's `scripts/test.sh` runs `swift test` first, and a lane task that changes a vector's answer brings the port along before its commit (P4's "Where it runs").
7. P3.11 before P5.12; P4.10 and P5.5 before P5.6; P5.1 to P5.5 and P4.11 before P5.7.
8. Lanes close in the order W, A, P. P5.11 is Lane P's last task, then the lanes are merged and `site` runs Wave 7.

## The main-merge points

- **MM1**, once Dev 1's Qibla branch is on `main` (he pings when it is). Also needed before P5.9, for `fix/cautious-wording`'s site pages and strings.
  - The Lead runs `git merge main` in `site` between merges, with no lane mid-merge.
  - Lanes take it at their next task start. P3.2 cannot start before it.
  - It also settles three files early: `PrayerRoutes.kt` (P2.4), `PrayerNaming.kt` (P2.1) and `iOSApp.swift` (P2.8, P4.5B, P4.10).
  - Dev 1 stays out of `wearApp/`, `watchface/`, `watchcore/`, plan 2's phone hooks, the `pbxproj`'s structure and the promise strings, and pings before any `pbxproj` change (the ledger, 24 October).
- **MM2**, once build 39 is cut. It must be in before the cut gate, so candidate C carries 39's fixes. P5.1's `check-versions.sh` and its test read the version from the files, not from literals.
- **`a11y-round-2`** (still docs only) builds its Qibla guidance on P3.2's `QiblaGuidance`. So its Task 9 waits for the watch release's squash to `main`, or takes `watch-companion`, as the Lead rules when it resumes.

## Shared files, and how they merge

Two lanes edit each of these, at different lines. The Lead merges them, and `scripts/test.sh` proves the merge:
- **`gradle/libs.versions.toml`:** P2.5/P2.6 (the same line), P3.8, P5.3, P5.5, P5.6. Each new entry goes in alphabetical order inside its section, never at the section's end, so two additions rarely touch.
- **`shared/build.gradle.kts`:** P2.1 = P3.1 (one line), P2.2, P2.3a, P2.4 (Ruling 45's `stabilityConfigurationFiles`), P2.5, P5.6.
- **`wearApp/build.gradle.kts`:** P2.6, P3.8, P5.1 (a comment), P5.3, P5.4, P5.5.
- **`wearApp/src/main/AndroidManifest.xml`:** P2.6, P3.4, P3.8, P5.3, P5.4, P5.5. From P5.3 on, `wearApp/permissions.txt` is the one list of its permissions.
- **`TaqwaWearApp.kt`:** P2.6, P3.4, P3.8, P3.10 (a check), P5.5, P5.12. **`FeedStore.kt`:** P2.6, P3.4, P3.8.
- **`watchcore` `WatchFeed.kt`:** P2.3a (KDoc), P3.3, P5.5. **`WatchLinkContract.kt`:** P2.3a, Ruling 50 (Lane P, after P2.4), P5.5; P2.6, P2.8, P4.1 and P4.5 build on its receivers' rules without changing it. **`WatchScreenWords.kt`:** P3.3, P4.3. **`Fixtures.kt` and `vectors/`:** P2.3a, P3.3, P3.10, P4.1, P4.3, P4.8, P4.9, P5.5 (Review Focus 2).
- **`shared` `watchfeed/WatchFeedBuilder.kt`, `WatchWords.kt`:** P2.1/P2.3a, P3.10, P4.3, P5.5.
- **`TasbeehViewModel.kt`:** P2.4, P3.1. **`PrayerRoutes.kt`:** P2.4 and Dev 1. **`iosApp/iosApp/iOSApp.swift`:** P2.8, P4.5B, P4.10 and Dev 1.
- **`iosApp/iosApp.xcodeproj/project.pbxproj`:** Lane A alone, and only through `tools/add-watch-targets.rb`. Plan 2's Task 8 deliberately avoids it.
- **`scripts/test.sh`:** P4.2, P5.1, P5.9. **`scripts/ios-build.sh`:** P4.4, P5.7. **`scripts/bump-version.sh` and `version-files.sh`:** P5.1, then P4.4.
- **`tools/watchface/README.md`:** P2.2, P2.3b, P2.7, P3.4, P3.6, P3.9, P5.2, P5.4. **`tools/i18n-check.py`:** P4.4, P5.8.
- **The Compose `values*/strings.xml`:** P4.10, P5.6, P5.9, and Dev 1's cautious wording. **`wearApp` `res/values*/strings.xml`:** P3.4, P5.5, P5.8. Indonesian is `values-in` alone in Android resources, and `values-id` with its `values-in` twin in the Compose resources.
- **`README.md`:** P4.2, P5.9, P5.10, P5.16. **`site/pages/`:** P5.9, P5.10, P5.16, and Dev 1's cautious wording.
- **`complication/` sources:** P3.11, then P5.12.
- **`settings.gradle.kts`:** no task of plans 2–5 changes it (`:watchcore`, `:wearApp` and `:watchface` are plan 1's).

## The devices, task by task

- **`wear-5558`:** plan 1's G12 and G13 until the face workflow ends. Then P2.2 Step 8; P2.3b; P5.2 Step 3; P3.4–P3.8 Step 5; P3.11 Step 4; P3.9; P2.7; P5.3 Step 4; P5.4 Step 5; P5.5 Step 7; P5.6 Step 6; P5.8 Step 4; P5.12 Steps 0, 2 and 3; P5.14.
- **`phone-5562`:** P2.7 (and the pairing), P5.3 Step 4, P5.5 Step 7, P5.6 Step 6, P5.12 Steps 2–3. Lane W stops it by its serial whenever a no-feed check runs, because a paired phone refills an empty watch (P3's Global Constraints).
- **5564, 5566, 5568:** P2.7's 0b and P5.6's check 5 (5564); P5.12 Step 1 (5566, then 5568). All three are downloads that need the Lead's OK (Step 1 below).
- **`ios-pair`:** P2.8 Step 5, P4.5B, P4.6 Steps 6–7, P4.10 Step 5, P4.11, P5.6 Step 7.
- **`sim-se3`:** Lane A's UI tests and shots, P4.4 to P4.11.
- **His Galaxy, the S23 and the iPhone 12** (his hands), **her iPhone and Apple Watch:** P5.13, P5.15 and P5.16, after candidate C.

---

## The Lead's steps

- [ ] **Step 1: Wave 0, the rulings and the downloads.** In the ledger, under "Execution map (24 Oct)":
  - Rule every Lead's call of plans 2–5 at its default, one line per plan: P2's calls 1–7, P3's 1–16, P4's 1–8 and P5's 1–5 and 7. P5's call 6 waits for P5.12's API 30 run. A call he rules otherwise changes only the task it names, as each plan says.
  - Record his OK for the downloads, and start them in the background now, so no lane waits on one:
    - `system-images;android-36;default;arm64-v8a` (P2.7's 0b, about 1.5 GB);
    - `system-images;android-33;android-wear;arm64-v8a` and `system-images;android-30;android-wear;arm64-v8a` (P5.12);
    - the `xcodeproj` gem (P4's call 2).
  - Record that Ruling 42 replaced 40 in every plan's text (this map's last section).
- [ ] **Step 2: Ask Mohamed** the questions at the end of this file in one message, with M1 (the face key) and a heads-up for the emulator sign-in (SIGN).
- [ ] **Step 3: Commit the plans and make the first lane.** In `site`, between two of the face workflow's commits, commit plans 2–5 and this map, docs only. Use `git commit -F` with the message "Docs: plans 2–5 of the watch companion and their execution map", then check `git log -1` (no trailer). Then:

  ```bash
  source ~/.zprofile; source ~/.zshrc
  cd ~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site
  mkdir -p ~/.cache/taqwa-locks
  git worktree add -b watch-feed ../watch-feed watch-companion
  cp local.properties ../watch-feed/
  ```

  Launch Lane P on P2.1, with this map's Global Constraints in its script.
- [ ] **Step 4: When the face workflow ends** (G12 and G13 in the ledger), make Lane W the same way (`watch-wear`), and launch it on P3.1. Lane P goes on to P2.3a.
- [ ] **Step 5: When P2.3a is merged and the cap allows,** make `watch-apple` with plan 4's own command and launch Lane A on P4.1. Otherwise follow "With two lanes only".
- [ ] **Step 6: Each closed task:**

  ```bash
  cd ~/Desktop/Workspace/apps/Taqwa/.claude/worktrees/site
  git merge --no-ff <lane-branch> -m "Merge <lane-branch>: plan <n> Task <m>, <the task's title>"
  source ~/.zprofile; source ~/.zshrc; scripts/test.sh
  ```

  - Add `scripts/ios-build.sh` when the task touched `iosApp/`, `shared/` or `scripts/`.
  - Expected: a clean merge, or only generated files in conflict, regenerated as the Global Constraints say; and PASS.
  - Ledger: `<time> merged <lane> P<n>.<m> at <sha>, test.sh green`.
  - A red merge is fixed in `site` before any lane takes `watch-companion`.
- [ ] **Step 7: Before each wave,** `get_usage`, the lanes' count against the cap, and the 5 % reserve, in one ledger line. When P3.8 starts, about two tasks before P2.7 needs SIGN, remind Mohamed of the sign-in.
- [ ] **Step 8: At MM1 and MM2,** `git merge main` in `site` with no lane mid-merge. Run `scripts/test.sh` and `scripts/ios-build.sh`, then record it in the ledger, and tell each lane to take `watch-companion` at its next task.
- [ ] **Step 9: Wave 7.** Once Lanes W, A and P are merged and closed, run P5.7 onwards in `site`. Plan the 12-hour observation (P5.12 Step 2) to start at the end of a day, with no other use of `emulator-5558` until it ends.
- [ ] **Step 10: After the push** (P5's M13, his OK), apply his cleanup rule to every worktree this map made: `git worktree remove` for `watch-feed`, `watch-wear` and `watch-apple`, and `git branch -d` for each of their branches. Then delete `watch-companion` (and `watch-companion-pre-rebase`) once `main` holds the squash, and ff-pull the main checkout. `site` itself stays: it is the long-lived worktree that holds the git-ignored ledgers, this one included, so it only moves to whatever branch comes next.

## What only Mohamed can answer

Each has the default the tasks build, so no task waits on it.

1. **How many watch lanes beside Dev 1?** You asked for 2–3 workflows at once, the Devs counted (18 October). Three watch lanes beside a Dev 1 workflow make four, within the weekly reserve you set. Default: three watch lanes while no Dev workflow runs, and two while one does. That is about 23 task-lengths to candidate C at best, and 30 with two.
2. **The emulator sign-in** (P2's question 2): one sign-in in the test phone emulator's Play Store, the Pixel Watch app, and Android Studio's Pair Wearable. Default: a throwaway Google account of yours, done when P2.7 starts (you get a reminder two tasks ahead).
3. **The privacy wording** (P2's question 7, M3): the drafts for the lines Ruling 39 does not word, in P2.3's report. Default: the drafts as written. The earlier you approve them, the earlier candidate C can be cut, because the cut gate waits on them. The Backups sentence stays (P4, Ruling 39).
4. **Location turned off** (P2's question 5): the watch keeps its last feed until it runs out, up to a month, with no "forget" message. Default: keep.
5. **His own phrases** (P2's question 4): up to 100, as many as fit the 60,000-byte budget. Default: yes.
6. **The countdown's draft wording** in French, Turkish, Indonesian, Urdu and Bengali (P2's question 3) rides in development builds until the native review. Default: yes.
7. **Plan 3's six defaults** (P3's questions 1–6, P5's question 12), judged on your Galaxy: the phrase list by a swipe up; a Reset row; the watch's own screen timeout; "Hold your wrist level" past 40°; the compass's bad states with the phone's titles alone; no time and no page dots. Default: each as built.
8. **Her Apple Watch and iPhone** (P4's question 1, P5's question 1): the model, its watchOS and her iOS. Default: an SE 3, as the spec says; watchOS 10.0 as the oldest supported; TestFlight on her iPhone in an external group "Family" with her Apple ID's email.
9. **When the Smart Stack lifts Taqwa** (P4's question 2). Default: the 30 minutes before each prayer.
10. **Colour on the Apple Watch** (P4's question 4). Default: amber only, with no colour setting.
11. **The shared Apple face's file** (P4's question 3, P5's Task 15 check 8): made on her watch, staged on Cape Town with the other slots off. Default: yes, in candidate C+1.
12. **The iOS Simulator tool's permission:** P4.6 Steps 6–7 drive the watch simulator's face editor, which asks you once per device. Default: you grant it when asked; if not, that step moves to her watch in P5.15.
13. **Your Galaxy's phone** (P5's question 2). Default: the S23.
14. **The default face** (P5's question 3): the Taqwa face is in the face list as soon as the watch app installs, never chosen for you. Default: yes.
15. **Signing the face each release** (P5's question 4): you type the face key's password, about a minute a release. Default: yes.
16. **Plan 1's three face polish items** (P5's question 5). Default: the Ramadan build.
17. **The Moon's seconds sweep** (P5's question 6), after your two battery days. Default: 15 Hz, unless the Moon day costs 3 points of battery more than the Arch day; then 5 Hz.
18. **Store screenshots** (P5's question 7). Default: the Galaxy shots from your watch in seven languages, and the Apple Watch shots from the simulator.
19. **The Wear OS release** (P5's question 8). Default: three days on the closed track with `taqwa-testers`, then production with the phone.
20. **Exodus** (P5's question 9). Default: both the local check before release and the public report after.
21. **`androidx.wear:wear-remote-interactions`** on the phone, once it passes the three library checks (P5's question 10). Default: yes.
22. **The "Add the Taqwa face" rows** (P5's question 11). Default: a phone row and Today's chip until the one activation is spent, then neither.
23. **The release order.** Default: build 39 ships first, and the watch release follows as 1.4.0 (40). Build 39 shipped as 1.3.0 (39).

**What only he can do, and when** (P5's M-rows):
- **now:** M1, the face key (`scripts/make-face-key.sh`, then two backups); M2, the answers above.
- **once P2.3's report exists:** M3, the privacy wording.
- **when P2.7 starts:** SIGN.
- **with the Lead's archive in P5.7:** his OK to archive and export-check, which registers the watch bundle ids.
- **before candidate C:** M8, his English and Arabic reading.
- **after C:** M4 (pairing his Galaxy), M5 (signing the face), M6a, M6 and M9–M14, in P5 Task 17's order.

## What this map changed in plans 2–5 (24 October)

- **Ruling 42 in place of 40:**
  - P2: the rulings line, the reading-order bullet, "Not findings".
  - P3: the rulings, and Task 11's "no order changes".
  - P4: its reading-order ruling now says the prayer comes before the dates, and Task 7's VoiceOver.
  - P5:
    - the rulings and the Global Constraint, now 7, 0, 1, 2, 3, 6, 4, 5 and `aScreenReaderIsGivenTheTimeThePrayerTheDatesAndTheWearersSlotsEachOnce`;
    - Task 13's check 4;
    - Task 14, turned round: it now runs only if a touch on the Galaxy finds the dates over the prayer, and reopens option (b), as Ruling 42 allows;
    - the ship gate and the risks.
- **When plan 1's last tasks gate them:**
  - P2.1 may start now; P2.3 waits for plan 1's Task 12, and P2.2 for its Task 13.
  - P3.1 and P3.2 may start before plan 1 closes.
  - P5.2 and P5.4 wait for plan 1's Task 13.
- **Missing dependencies:** P5.4 on P5.2 (`push-token.sh`); P5.6 on P5.5 (`FACE_CAPABILITY`, `FACE_URI`).
- **Dev 1's B40:**
  - P3 says Dev 1, not Dev 2. P3.2 waits for his Qibla branch on `main`, and moves `isAligned(heading, bearing, wasAligned)` and the 5°/7° constants into `QiblaAngles` unchanged, with a hold test and the parity test on his view model.
  - P4.9 ports the hysteresis and puts it in the compass vectors.
- **Plan 3's order:** Tasks 2 and 3 may run side by side after Task 1. Task 3 still needs Task 1, because its words test reads `TasbeehPresets` in `:watchcore`.
- **The Friday:** P3.5's `aFridayNamesJumuah` used day 5, which is Thursday 22 October in the sample. It is day 6, Friday 23 October, as P4 has it.
- **Indonesian in Android resources:** P3.4 makes `values-in` alone, as `androidApp` and `tools/i18n-check.py` have it, not `values-id` and `values-in`. P5.8 then adds to plan 3's folders rather than creating them.
- **`WatchTime` in the Swift port:** P4.3, P4.6 and P4.7 key the clock by the six-valued `WatchTime`, Sunrise included, as P3's hand-off asks, and P4.3's clock vectors cover all six times.
- **The version list:** P4.4 puts its plists in P5.1's `IOS_PLISTS`, since P5.1 lands first. P5.1's and P5.4's checks read the current version instead of 1.2.0 (38).
- **`VIBRATE`:** P2.6's permission checks accept plan 3's `VIBRATE` if P3.4 lands first.
- **The Backups sentence:** P2 now agrees with P4 and P5 that it stays, whatever the simulator check finds.
- **Integration:**
  - P4 now takes `watch-companion` by merge, never by rebase, and is merged after every task.
  - P2 and P3 allow the lane worktrees, with the ledger kept in `site`.
  - P5.5 adds its words' row to plan 2's table, and runs `swift test`.
- **Smaller fixes:**
  - P3.7's emulator headings hold for the sample's declination both before and after P2.3 rounds it.
  - P3.10 Step 4 only checks what P3.4 already moved.
  - P2.8 names the Series 12's UDID.
  - P2's header-type rule allows plan 3's moved tasbeeh and compass types.
  - P5.10's Wear OS 3 line no longer waits for P5.12.
  - P5.13 cites sections, not stale line numbers.
