# The Taqwa face for "Add to Apple Watch"

`Taqwa.watchface`, when it is here, is the face Settings offers as **Add to Apple Watch** (plan 4
Task 10, spec 4.5): one of Apple's own faces with Taqwa's complications already in place, handed to
ClockKit's `CLKWatchFaceLibrary.addWatchFace(at:)`, which shows the system's add-face sheet.

**It is absent until it is made on a real Apple Watch,** and while it is absent the row is never
shown. That is the expected state of this folder today: only this README.

## Apple's form for the offer, and the Lead's open call

Apple's [Sharing an Apple Watch face](https://developer.apple.com/documentation/clockkit/sharing-an-apple-watch-face)
asks two things of an app that offers a face:

> Always display both the watch face preview with an official Add Apple Watch Face button.

> Only add watch faces in response to the user tapping an Add Apple Watch Face button.

The same page says to share the face by email when it is offered in an app, because email is the
one share that carries the face's preview image along with the `.watchface` file.

The row as built (plan 4 Task 10) is a text row in Settings, "Add to Apple Watch" with the
external-link glyph, with no preview and no official button. Nobody has ruled on that yet: **the
Lead rules before the file is bundled** (plan 4's hand-off to plan 5), either

- **keep the Settings row** as a recorded deviation from Apple's guidance, or
- **show the face's preview** (the image the email share gives) **and Apple's Add Apple Watch Face
  badge** once the file lands, the badge's tap calling `addWatchFace(at:)`.

The row stays hidden while this folder holds no file, so nothing ships either way until then.

## How the build uses it

- iosApp's "Watch face" build phase (`tools/add-watch-targets.rb`) copies
  `iosApp/iosApp/WatchFace/Taqwa.watchface` into `Taqwa.app/` when the file exists, and removes a
  copy an earlier build left when it does not. This README is in no build phase.
- `iosApp/iosApp/WatchFaceShare.swift` offers the face while the session is activated, a watch is
  paired and `Bundle.main.url(forResource: "Taqwa", withExtension: "watchface")` finds the file.
  Its decisions are logged as `TaqwaWatch face …`:

  ```
  xcrun simctl spawn <phone udid> log show --last 5m --predicate 'eventMessage CONTAINS "TaqwaWatch face"'
  ```

## How the file is made

The face is the one the mockups draw (`docs/superpowers/specs/2026-10-19-watch-companion/mockups.html`,
"Taqwa on Apple's own faces"): **Modular**, Taqwa's rectangular in the middle, its circular at the
bottom left, amber, the other slots off.

1. On an Apple Watch with Taqwa's watch app installed from a TestFlight or store build (plan 5
   Task 15: her watch, through her iPhone, never the work iPhone 12), and with the phone's city set
   to **Cape Town**: a shared face carries a snapshot of the face as that watch drew it, and each
   slot's data, and this file ships in every copy of the iPhone app from a public repository.
   Apple's page also says the system does not recognise complications from a development build,
   so the debug app cannot make it.
2. Build the face above in the watch's face editor, then share it (touch and hold the face, the
   share button, then the face's own share sheet) **by email, to an address of the team's own**:
   only the email carries the face's preview with the file (above), and the preview is needed if
   the Lead rules for Apple's form.
3. Inspect both before committing them: unzip a copy of the file and check that the snapshot shows
   Cape Town and nothing personal, and that no slot but Taqwa's two holds data; look at the
   preview for the same.
4. Commit it here as `Taqwa.watchface`, with the preview only if the Lead has ruled for Apple's
   form (the build copies the `.watchface` file alone today). Then, on a paired iPhone: Settings ›
   App shows **Add to Apple Watch**, and a tap brings up the add-face sheet.

## Testing without the real file

A placeholder (any file named `Taqwa.watchface`) makes the row show, and a tap answers
`invalidFile` (`TaqwaWatch face refused: invalidFile code=2 …` in the log) with the row's failure
line. Delete it before any commit: `git status` must show this folder clean of it.

The simulators cannot make the real file (plan 4 Task 10 Step 5, 31 October 2026, report
`docs/superpowers/reports/2026-10-31-watchos-add-to-apple-watch.md`): the watchOS 27.0 Series 12's
face switcher offers Edit and no Share, and the iOS 27.0 simulator's Watch app aborts at launch,
so neither of Apple's two ways to share a face exists there, although Apple's page says a face can
be shared from the watchOS simulator. The file is plan 5's (plan 4's question 3), and so is the
check that a tap brings up the add-face sheet with the real file.
