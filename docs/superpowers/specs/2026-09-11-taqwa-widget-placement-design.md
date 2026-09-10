# Taqwa: offering the widget you have not added

Status: **decided 11 September 2026** with Mohamed, after he asked whether the app can tell which widgets are on the home screen and whether the Appearance previews could offer to add a missing one.

## 1. Goal

The Appearance screen already shows what each widget looks like. When a widget is *not* on the home screen, that preview should also offer to put it there; when it is, it should say nothing at all. Today the only place widgets are offered is the onboarding page, which is one tap to skip and never returns, so a user who skips it has no route to a widget except knowing their launcher.

Non-goals: adding a widget on iOS (no API exists — see §4); a widget "manager" screen; counting instances or removing them; changing the onboarding page's own copy.

**Dropped from the original scope, deliberately:** skipping the onboarding widget page when a widget is already placed. On Android a widget is removed with the app, so during first-run onboarding there can never be one; the only way to reach that state is clearing app data with widgets left behind, which is rare enough not to pay for.

## 2. What the app can know

Android's `AppWidgetManager.getAppWidgetIds(provider)` returns the placed instances of one provider. No permission, no user-visible effect. The app already relies on it to decide whether to keep the refresh alarms armed (`anyWidgetPlaced`, `anyAyahWidgetPlaced`), so the detection this feature needs is code that already exists and is already trusted.

iOS's `WidgetCenter.getCurrentConfigurations` returns the widget kinds and families currently placed, asynchronously. It is not used yet.

Both are read on demand, never cached across a resume: the user adds and removes widgets *outside* the app, so any cached answer is wrong precisely when it matters.

## 3. The seam

`widget/WidgetPlacement.kt` in `shared/commonMain`:

```kotlin
/** Which Taqwa widgets are on a home screen right now. */
data class WidgetPlacement(val prayer: Boolean, val ayah: Boolean) {
    companion object { val Unknown = WidgetPlacement(prayer = true, ayah = true) }
}

interface WidgetPlacementSource {
    /** Suspend because iOS answers asynchronously; Android answers immediately. */
    suspend fun current(): WidgetPlacement
}

expect fun createWidgetPlacementSource(): WidgetPlacementSource
```

`Unknown` reports both as placed, so the offer is hidden while the answer is unknown or unavailable. Offering to add a widget somebody already has is worse than staying quiet: it makes the screen look wrong to the person best placed to notice.

- **Android** (`androidMain`): a `androidWidgetPlacementHook: (() -> WidgetPlacement)?` set from `TaqwaApplication.onCreate` beside the existing hooks, because the receiver classes live in `androidApp` and `shared` cannot see them. Null hook → `Unknown`. `prayer` is true when either prayer provider has an instance; `ayah` when the ayah provider has one.
- **iOS** (`iosMain`): `iosWidgetPlacementHook: ((completion: (Boolean, Boolean) -> Unit) -> Unit)?`, set from `iOSApp.swift` next to the widget-refresh hook, calling `WidgetCenter.shared.getCurrentConfigurations` and reporting `prayer` for kinds `TaqwaHomeWidget` or `TaqwaLockScreenWidget` and `ayah` for `TaqwaAyahWidget`. Kotlin bridges it with `suspendCancellableCoroutine`; a null hook, a failure or no answer within 2 seconds gives `Unknown`.

## 4. Adding one

`WidgetPinRequester` gains the widget being asked for, since there are now two answers:

```kotlin
enum class PinnableWidget { PRAYER, AYAH }

interface WidgetPinRequester {
    fun isSupported(widget: PinnableWidget): Boolean
    fun requestPin(widget: PinnableWidget)
}
```

Android pins `TaqwaMediumWidgetReceiver` for `PRAYER` (the widget onboarding already offers) and `TaqwaAyahWidgetReceiver` for `AYAH`, through the existing `androidWidgetPinHook`, which grows the same parameter. Support is the launcher's `isRequestPinAppWidgetSupported`, unchanged. iOS returns false for both: WidgetKit has no call that places a widget, by design, and a button that does nothing is worse than a sentence that explains.

## 5. What the Appearance screen shows

Directly beneath each preview, in the same horizontal inset, one of three things:

- **Placed** — nothing. No row, no note. The preview is then only a preview, as it is today.
- **Not placed, and the launcher takes pin requests** — a single tappable row in the card idiom the settings screens already use, labelled **"Add to home screen"** / «إضافة إلى الشاشة الرئيسية», with the chevron those rows carry. Tapping it calls `requestPin` for that widget; the launcher shows its own confirmation sheet, which is system UI and is not reported back, so the app does nothing further and the row simply disappears the next time placement is read.
- **Not placed, and it cannot be asked for** (iOS, or an Android launcher without pin support) — a caption line in the secondary colour, no row and nothing tappable, giving the platform's own steps:
  - iOS, hold-icon path: "Touch and hold the Taqwa icon, then choose a widget." / «اضغط مطولًا على أيقونة تقوى ثم اختر ودجة.»
  - iOS, plus-button path: "Touch and hold the home screen, tap the plus, then search for Taqwa." / «اضغط مطولًا على الشاشة الرئيسية ثم اضغط على علامة الزائد وابحث عن تقوى.»
  - Android without pin support: "Touch and hold the home screen, then choose Widgets." / «اضغط مطولًا على الشاشة الرئيسية ثم اختر الودجات.»

  The existing `widgetAddPath` already distinguishes those three cases; these are new, shorter strings than onboarding's, which open by explaining what the widget is for — redundant under a picture of it.

**When placement is read.** In `AppearanceSettingsScreen`, on entering composition and again on every `Lifecycle.Event.ON_START`, so a user who leaves to add the widget and comes back sees the row gone. The state starts at `Unknown`, i.e. showing nothing, so the row fades in when the answer arrives rather than flashing away when it turns out to be unnecessary.

## 6. Tests

`commonTest`: `WidgetPlacement.Unknown` hides both offers; the three-way choice per widget (placed → nothing; not placed and pinnable → the row; not placed and not pinnable → the caption for each of the three add paths) as a pure function `widgetOffer(placed, pinnable, addPath)` returning a small sealed result, so the branching is tested without a composable. `androidUnitTest`: the Android source maps a null hook to `Unknown`. **Amended 11 September 2026:** the iOS bridge's timeout has no test. `shared` has no `iosTest` source set, and standing one up to hold a single test of a two-second fallback is a poor trade; the bridge is compile-verified and exercised by the simulator round instead, and this is recorded rather than quietly dropped.

Device: on the emulator, the Appearance screen with no widgets placed shows both rows; adding the ayah widget from the launcher and returning leaves only the prayer row; adding a prayer widget leaves neither. On the simulator, both previews show the iOS caption and no row.

## 7. Release

Ships as **0.6.0 (8)** — a minor bump, a visible new offer rather than a fix.
