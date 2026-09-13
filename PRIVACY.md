# Taqwa privacy policy

_Last updated 13 September 2026. Applies to Taqwa 0.12.0 and later on Android and iOS._

Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline.
The one thing it uses the internet for is downloading Quran recitations, and only when you
ask it to.

## What stays on your phone

Everything the app knows is stored on your device and nowhere else:

- your location, or the city you chose, used to compute prayer times and the Qibla;
- your settings: calculation method, notification choices, adhan voice, theme, reciter;
- your Quran bookmarks and reading position;
- your Tasbeeh counts;
- any recitations you have downloaded.

None of this is sent to us or to anyone. There is no server behind Taqwa and no account to
create. Deleting the app deletes all of it.

## Location

The app asks for your location only to compute prayer times and the Qibla direction, and
only if you allow it. Picking a city from the built-in list works just as well and needs no
permission. Your location is used on the device and never transmitted.

## Recitation downloads

Recitations are fetched one surah at a time from Taqwa's public data repository on GitHub
(github.com/MohamedAbulgasem/Taqwa-data), and only when you tap Download, tap Play on a
surah you have not downloaded, or choose "Download the whole Quran". By default this
happens over Wi-Fi only; you can allow mobile data in Settings › Quran › Recitation.

Like any download, the request shows GitHub your IP address and which file you asked for,
which reveals the reciter and surah. It carries nothing that identifies you: no account, no
device identifier, no cookie. GitHub's own privacy statement covers what GitHub keeps about
such requests: docs.github.com/site-policy/privacy-policies/github-general-privacy-statement.

Once you have used recitation, the app also checks once a day for an updated list of
reciters, from the same repository. That request is the same for everyone and reveals
nothing but your IP address. Until you first use recitation, the app makes no network
request at all.

Downloaded recitations can be deleted per reciter in Settings › Quran › Recitation ›
Downloads.

## Backups

Taqwa opts out of Android's app backup, so your location and settings are not copied to
Google. On iOS, your settings, bookmarks, counts and downloaded recitations are excluded
from iCloud backup; the only thing of Taqwa's that is backed up is the widget's small cache
of upcoming prayer times, and that backup is encrypted by Apple and never visible to us.

## What Taqwa never does

- No account, sign-in or profile.
- No ads and no advertising identifier.
- No analytics, crash reporting or usage statistics.
- No third-party SDK that talks to the internet.
- No sale or sharing of data, because there is none to sell or share.

## Children

Taqwa collects no data from anyone, of any age.

## Changes

If a future version needs the network for anything new, this page will say so before that
version ships, with the date above updated.

## Contact

Open an issue at github.com/MohamedAbulgasem/Taqwa/issues or email algiriany93@gmail.com.
