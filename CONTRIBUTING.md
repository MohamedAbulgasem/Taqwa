# Contributing to Taqwa

Contributions are welcome, from a typo in the Arabic strings to an authority's timetable. Taqwa
is maintained by one person, so agreeing on the approach before you spend time on it saves
everyone work.

## Where to start

- **A prayer time doesn't match your mosque.** Use the
  [prayer times form](https://github.com/MohamedAbulgasem/Taqwa/issues/new?template=prayer-times.yml).
  These reports are among the most useful of all.
- **Something is broken.** Use the
  [bug form](https://github.com/MohamedAbulgasem/Taqwa/issues/new?template=bug.yml).
- **Wording or a translation is wrong.** Use the
  [wording form](https://github.com/MohamedAbulgasem/Taqwa/issues/new?template=wording.yml), or
  send a pull request for the app's own strings ([Translations](#translations) below).
- **An authority's timetable Taqwa doesn't follow yet.** Open an issue with where it is published.
- **A security problem.** Report it privately, as [SECURITY.md](SECURITY.md) describes, never in a
  public issue.
- **Questions and help.** See [taqwa.world/support](https://taqwa.world/support/), or email
  support@taqwa.world. Email is also the way in without a GitHub account.

Before suggesting a feature, check the [Roadmap](README.md#roadmap). The mosque finder, zakat
calculator, hadith collections, community features and anything that needs a server are
deliberately out of scope.

## The constraints

No network calls beyond the recitation downloads the person asked for, no third-party SDKs that
phone home, no new colour outside the palette in `Palette.kt`, and every language must be checked
as carefully as English, the right-to-left ones (Arabic, Urdu) especially. If a change touches the
UI, include screenshots of English and Arabic in both themes.

These are what Taqwa is, so a change that breaks one is not merged, however good it is
otherwise. In more detail:

- **Nothing leaves the phone.** No analytics, no crash-reporting SDKs, no account. There is no
  server behind Taqwa and there will not be one. Exodus Privacy's report finds no trackers in the
  app, and it has to stay that way.
- **Never early.** Where Taqwa holds the timetable a place follows, no prayer may start before
  the published time on any day checked. Engine changes are held to the gate, under
  [How to contribute](#how-to-contribute).
- **The Quran is recited, never synthesised.** The Quran is only ever heard in a reciter's voice.
  The phone's text-to-speech reads translations and tafsir, never the Quran itself.
- **Religious text is as published.** The Quran's Arabic is the [Tanzil](https://tanzil.net) text,
  verbatim, and is never edited here. Translations, tafsir and adhkar come from the sources
  credited in [docs/ATTRIBUTION.md](docs/ATTRIBUTION.md), and a correction needs a source (the
  [wording form](https://github.com/MohamedAbulgasem/Taqwa/issues/new?template=wording.yml) says which).
- **Nothing interrupts.** The app never asks for a review and never nags.

## How to contribute

1. **Open an issue first** for anything beyond a small fix, so the approach can be agreed before
   you spend time on it. Bug reports are most useful with the device, OS version, language, and a
   screenshot; without a GitHub account, email support@taqwa.world instead.
2. **Fork and branch** from `main`.
3. **Keep [the constraints](#the-constraints).**
4. **Run `./scripts/test.sh`** and add tests for domain logic. Prayer times are held to the gate:
   `./gradlew -p tools/timetables jvmTest gate` replays the engine against every official
   timetable held and writes the proof stamps in `tools/timetables/official/stamps/`, and
   `./gradlew -p tools/timetables checkStamps`, which the release scripts run first, refuses a
   stale or red stamp. Most of those timetables may not be republished, so they live in a local
   archive and the maintainer runs the gate; `checkStamps` needs no archive and tells you whether
   a change needs a new gate run. To propose an authority's timetable, open an issue with where it
   is published. Qibla changes need known-answer tests against published values.
5. **Open a pull request** against `main` describing what changed and why. Small, focused PRs are
   reviewed quickly; large ones are split.

The requirements and build commands are in the README under
[Building](README.md#building). One trap when adding tests: a comma inside a backtick test name
compiles on Android but breaks the iOS target, so keep commas out of test names.

## Translations

Translations: one `strings.xml` per language under `shared/src/commonMain/composeResources/`
(`values/` for English, then `values-ar`, `values-fr`, `values-tr`, `values-id`, `values-ur`,
`values-bn`), and `scripts/check-strings.sh` checks that every file carries the same keys.
Indonesian is edited in `values-id`. Android needs an identical copy in `values-in`, so run
`python3 tools/sync-indonesian.py` after editing it; the check fails if the two drift apart. New
languages are welcome as long as every string is covered, and the website and privacy policy
should follow in the same language.

A language also has text in a few other places:

- **Android-only strings:** `androidApp/src/main/res/values-<lang>/strings.xml`.
- **iOS permission prompts:** `iosApp/iosApp/<lang>.lproj/InfoPlist.strings`.
- **The website:** `site/pages/<lang>/`.
- **The privacy policy:** `PRIVACY.<lang>.md`.
- **The store listings:** `docs/store/listing/<lang>.md`.

Some wording rules apply in every language:

- Prayer times are never called "accurate" or "exact". Say where they come from instead.
- No text promises that a notification arrives at a set moment, because phones can delay them.
- Where an authority's timetable is named, the text says Taqwa is not affiliated with it.
- English is British English.

## Licence

Taqwa is free software under the [GPL-3.0](LICENSE). By opening a pull request, you agree that
your contribution is licensed under the same terms.

Third-party material (text, audio, fonts, data) needs a licence that lets Taqwa bundle and
redistribute it. It must be credited in [docs/ATTRIBUTION.md](docs/ATTRIBUTION.md), in
[NOTICE](NOTICE) and in the app under Settings › Attribution & licences.
