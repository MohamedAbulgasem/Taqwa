# Security policy

Taqwa's promise is that nothing about you leaves your phone. There is no account, no analytics
and no tracking, and there is no server behind the app at all. The one network use is
downloading the Quran recitations you ask for. A security problem is anything that could break
that promise or harm the people who use the app, and reports are very welcome.

## Reporting a vulnerability

Report it privately, never in a public issue:

- **On GitHub:** [Report a vulnerability](https://github.com/MohamedAbulgasem/Taqwa/security/advisories/new),
  on this repository's Security tab. Only you and the maintainer see the report.
- **Without a GitHub account:** email support@taqwa.world with "Security" in the subject.

Please include:

- what you found and what an attacker could do with it;
- the steps to reproduce it;
- the platform and app version (Settings › About Taqwa).

A proof of concept helps. A fix is welcome but not expected.

## What happens next

Taqwa is maintained by one person, so these timelines are honest rather than fast:

- **Within 7 days:** you get a reply saying the report has been read.
- **Once it is confirmed:** the fix goes into the next release on both stores. Store review
  adds a few days on top of the work.
- **When the fixed version is on both stores:** you are told, and credited in the release notes
  and the advisory if you would like to be.

Please keep the problem private until the fixed version is on both stores, or for 90 days from
your report if that comes first. If a fix needs longer, you will be told why.

There is no bug bounty. Taqwa is free and has no budget for rewards, but credit is gladly given.

## Supported versions

Only the latest release on Google Play and the App Store receives fixes. There are no older
maintained versions; fixes land on `main` and ship in the next release.

## Scope

In scope:

- **The apps:** Android (`world.taqwa.app`) and iOS (`world.taqwa.ios`).
- **The website, taqwa.world:** static pages built from this repository, including the `/get/`
  download links, and the GitHub Actions workflows that build and deploy it.
- **The recitation download path:** the recitations and reciter list in the public
  [Taqwa-data](https://github.com/MohamedAbulgasem/Taqwa-data) repository on GitHub, and how the
  app fetches and checks them. The app keeps a downloaded surah only if its SHA-256 matches the
  reciter list.

Of most interest:

- **Data leaving the phone:** anything that sends off the phone what the
  [privacy policy](PRIVACY.md) says stays on it, such as your location, settings, bookmarks,
  counts or the crash report.
- **Tampered downloads:** any way to make the app download, keep or play something other than
  the published recitation.
- **Other apps reading Taqwa's data:** any way another app on the same phone could read it, for
  example through the widgets' shared storage or an exported Android component.
- **The website:** anything on taqwa.world that runs another site's script or loads content from
  another host.

Out of scope:

- **Third-party services:** GitHub, Google Play, the App Store, Cloudflare and the phone's own
  text-to-speech engine have their own security programmes. Report to them; if it affects Taqwa,
  tell us as well.
- **Physical or root access:** problems that need a rooted or jailbroken phone, or access to an
  unlocked phone.
- **Automated scans:** findings with no demonstrated impact.
- **Prayer times:** a time that looks wrong is not a security issue. Use the
  [prayer times form](https://github.com/MohamedAbulgasem/Taqwa/issues/new?template=prayer-times.yml)
  instead. If Taqwa shows a prayer earlier than the published timetable it says it follows, that
  is the most serious kind of bug the app can have, and it is handled first.
