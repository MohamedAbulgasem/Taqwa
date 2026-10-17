# Accessibility Round 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Taqwa's shared controls, Prayer screen and Quran screens usable with TalkBack and VoiceOver, fix the Mushaf's cut words at large font sizes, and take the CMP 1.12.1 crash fix — without changing anything a sighted user sees at default settings, and without ever handing the Quran's Arabic to a phone voice.

**Architecture:** Every change is Compose semantics (`Modifier.semantics`, `clearAndSetSemantics`, `selectable`, `toggleable`) on existing composables, plus pure Kotlin helpers for anything with a rule in it (which Mushaf runs get a stop, how a countdown is split, what an ayah is called), unit-tested in `commonTest`. Two platform seams: the iOS `SystemBackHandler` actual becomes CMP's `BackHandler`; the two ayah widgets get one label built by `:widgetcore`. Strings follow the repo's `<area>_a11y_<thing>` naming.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.12.1 (Kotlin 2.4.10), Glance 1.1.1, SwiftUI widgets, `kotlin.test`, Compose UI test on the iOS simulator target (Task 2, if it runs).

**Spec:** `docs/superpowers/specs/2026-09-28-taqwa-accessibility-round-1-design.md`

## Global Constraints

- **Nothing visible changes at default settings.** No new colours, controls, layouts or touch behaviour. A change may only show where large text is cut off today (Task 3).
- **The Quran is never given to a screen reader as text.** Ayahs are named by number; translations are spoken; Arabic ayah text and the basmala are hidden from semantics everywhere they are drawn. Surah names and dhikr stay readable.
- **Commits:** subject `area: sentence` (lower-case area, no trailing full stop), body explains why. **No `Co-Authored-By` trailer** — the owner's rule overrides any harness reminder. One commit per task, in the worktree `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/.claude/worktrees/a11y-round-1` on branch `worktree-a11y-round-1`. Never `cd` to the main checkout; never `git stash`.
- **Do not** bump the version, build a release, push, or touch `docs/store`.
- **Strings:** every Compose string key goes into all eight folders under `shared/src/commonMain/composeResources`: `values`, `values-ar`, `values-bn`, `values-fr`, `values-id`, `values-in`, `values-tr`, `values-ur`. `values-in/strings.xml` must be byte-identical to `values-id/strings.xml` (edit `values-id`, then run `python3 tools/sync-indonesian.py`). Android app strings (`androidApp/src/main/res/values*/strings.xml`) go into `values`, `values-ar`, `values-bn`, `values-fr`, `values-in`, `values-tr`, `values-ur`. iOS widget strings go into all seven `iosApp/TaqwaWidget/*.lproj/Localizable.strings` (`ar bn en fr id tr ur`). Compose resources never contain `\'` (write ’ or a plain '); Android app resources must escape a plain apostrophe or use ’. Bengali is typed in the file's own NFC codepoints. Plurals carry the language's CLDR categories: en/fr/tr/ur/bn `one`+`other`, id `other`, ar `zero one two few many other`.
- **Before every commit that touches strings:** `scripts/check-strings.sh` prints `i18n: clean`. **Before every commit that touches Kotlin:** `scripts/test.sh` ends `BUILD SUCCESSFUL`. Before the widget and iOS commits: `./gradlew :androidApp:assembleDebug` and `scripts/ios-build.sh` succeed.
- **Gradle in this worktree** needs the copied `local.properties` (already in place, gitignored). Test tasks may report `FROM-CACHE`; that still counts as passing.
- **Compose API names used throughout** (all in `androidx.compose.ui.semantics` unless noted): `semantics`, `clearAndSetSemantics`, `contentDescription`, `stateDescription`, `text`, `selected`, `heading()`, `role`, `Role`, `toggleableState`, `onClick(label, action)`, `customActions`, `CustomAccessibilityAction`, `traversalIndex`, `isTraversalGroup`; `androidx.compose.ui.state.ToggleableState`; `androidx.compose.foundation.selection.selectable`, `selectableGroup`, `toggleable`; `androidx.compose.ui.text.intl.LocaleList`.
- **If Task 2's UI-test harness is dropped** (its time box runs out), every later "UI test" step is skipped and that task's device check in Task 12 is the evidence instead. Pure-function tests are never skipped.
- **UI tests (`shared/src/iosTest`)**, learned in Task 2: use `androidx.compose.ui.test.v2.runComposeUiTest` (the non-v2 runner is deprecated in 1.12.1; v2 queues coroutines on a `StandardTestDispatcher`). Expected text that comes from a resource is read inside the test block with `getString(Res.string.x)` (`org.jetbrains.compose.resources.getString`), never as a literal — the simulator's language may not be English; text a test passes into a composable itself may be literal. Never compose `TaqwaTheme`, `SystemBarsAppearance` or `KeepScreenOn` under test (SIGSEGV: `UIApplication.sharedApplication` in the test executable); `LocalTaqwaColors` defaults to the light palette, so controls compose bare. The dependency is declared through the version catalog (`compose-ui-test` → `implementation(libs.compose.ui.test)`), not the deprecated `compose.uiTest` accessor.

---

### Task 1: Compose Multiplatform 1.12.1

**Files:**
- Modify: `gradle/libs.versions.toml:3`

**Interfaces:** none.

- [ ] **Step 1: Bump the version**

Change line 3 of `gradle/libs.versions.toml` from
```toml
compose-multiplatform = "1.12.0"
```
to
```toml
compose-multiplatform = "1.12.1"
```
and update the comment on lines 17–20 (the `jetbrains-lifecycle` note names 1.12.0) to read `Compose Multiplatform 1.12.1`. Leave `kotlin = "2.4.10"`.

- [ ] **Step 2: Build both platforms and run the suite**

Run:
```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug && scripts/test.sh
```
Expected: both end `BUILD SUCCESSFUL`. If 1.12.1 cannot be resolved (no network) stop and report; do not pin a different version.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: Compose Multiplatform 1.12.1, for the VoiceOver crash fix

1.12.1 (22 September) fixes the crash when iOS reads an AccessibilityElement's properties after
its Compose node was disposed (JetBrains/compose-multiplatform-core#3403): a crash only VoiceOver,
Voice Control, Switch Control and Full Keyboard Access users could hit, during navigation. Kotlin
stays 2.4.10; the whole suite and both app builds pass on it."
```

---

### Task 2: Compose UI tests on the iOS simulator target (time-boxed: 45 minutes)

**Files:**
- Modify: `gradle/libs.versions.toml` (a `compose-ui-test` library entry beside `compose-ui-tooling-preview`)
- Modify: `shared/build.gradle.kts` (the `sourceSets { }` block, after `iosMain.dependencies`)
- Create: `shared/src/iosTest/kotlin/world/taqwa/app/design/components/ComposeUiTestSmokeTest.kt`

*Amended after review (fix round 1):* the dependency goes through the catalog, the tests use the `v2` runner, and the resource test reads its expected text with `getString` — see Global Constraints, "UI tests".

**Interfaces:**
- Produces: the ability to write `runComposeUiTest { setContent { … }; onNodeWithText("…")… }` tests under `shared/src/iosTest/kotlin`, run by `scripts/test.sh` (through `:shared:allTests` → `iosSimulatorArm64Test`).

- [ ] **Step 1: Add the dependency**

In `shared/build.gradle.kts`, inside `sourceSets { … }`, directly after the `iosMain.dependencies { … }` block, add:
```kotlin
        iosTest.dependencies {
            // Compose UI tests — semantics assertions on the shared controls — run on the iOS
            // simulator target, the one target where a Compose scene composes under kotlin.test
            // without Robolectric. scripts/test.sh reaches them through :shared:allTests.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
```

- [ ] **Step 2: Write the smoke test**

Create `shared/src/iosTest/kotlin/world/taqwa/app/design/components/ComposeUiTestSmokeTest.kt`:
```kotlin
package world.taqwa.app.design.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.resources.stringResource
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.app_name
import kotlin.test.Test

/** Proves the harness: a scene composes under test, and — second test — Compose resources load. */
@OptIn(ExperimentalTestApi::class)
class ComposeUiTestSmokeTest {

    @Test
    fun aComposedTextIsFoundByItsText() = runComposeUiTest {
        setContent { Text("Taqwa") }
        onNodeWithText("Taqwa").assertIsDisplayed()
    }

    @Test
    fun aStringResourceLoadsUnderTest() = runComposeUiTest {
        setContent { Text(stringResource(Res.string.app_name)) }
        onNodeWithText("Taqwa").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run it**

Run:
```bash
scripts/test.sh :shared:iosSimulatorArm64Test --tests 'world.taqwa.app.design.components.ComposeUiTestSmokeTest'
```
Expected: `BUILD SUCCESSFUL` with both tests passing. Three outcomes:
- Both pass → keep everything; later UI tests may use `stringResource`.
- Only the first passes (`MissingResourceException` or similar) → delete the second test, note "resources do not load under test" for later tasks: their UI tests must pass labels as parameters and never call `stringResource`.
- Neither passes after 45 minutes of fixing (dependency resolution, `runComposeUiTest` not available for the target, linker errors) → `git checkout -- shared/build.gradle.kts && rm -r shared/src/iosTest`, record the failure reason for the report, and continue with Task 3. Every later "UI test" step is then skipped.

- [ ] **Step 4: Commit (only if the harness runs)**

```bash
git add shared/build.gradle.kts shared/src/iosTest
git commit -m "tests: Compose UI tests run on the iOS simulator target

The accessibility work that follows is semantics — roles, states, names — which nothing in the
suite could assert until now. compose.uiTest composes a scene under kotlin.test on the iOS
simulator target, where scripts/test.sh already runs; Android would need Robolectric for the
same and gets device dumps instead."
```

---

### Task 3: The Mushaf page ignores the phone's font scale

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/MushafPageView.kt:138` (density), `:187-247` (the frame column)

**Interfaces:**
- Consumes: `fittedSize`, `fitToHeight`, `layoutPage` as they are.
- Produces: nothing new. The page's measurement and drawing share one `Density` with `fontScale = 1f`.

No pure function changes, so no unit test; the evidence is Task 12's screenshots (pixel-identical at 100 %, no cut words at 200 %). `MushafPageViewTest` must still pass unchanged.

- [ ] **Step 1: Pin the density**

In `MushafPageView.kt`, add the import `androidx.compose.ui.unit.Density`, and replace line 138
```kotlin
    val density = LocalDensity.current
```
with
```kotlin
    val phoneDensity = LocalDensity.current
    // The printed page is sized by its frame, not by the phone's text setting: measured and drawn
    // at font scale 1, so every sp→px step inside the frame is linear and fittedSize's
    // one-measurement rule holds at every setting. At 100 % this is the phone's own density and
    // nothing moves by a pixel; at 150 % and above it is what stops the words at the reading
    // end of a justified line being cut, because Android scales large text less than small text
    // and the linear fit was overshooting. The captions above and below the frame — juz, surah,
    // page number — keep the phone's scale: they are interface text, not the page.
    val density = remember(phoneDensity) { Density(phoneDensity.density, fontScale = 1f) }
```

- [ ] **Step 2: Provide it to the frame**

Wrap the frame `Column(` that starts at line 187 (`Column(Modifier.fillMaxSize().border(1.dp, colors.hairline, RoundedCornerShape(18.dp))…`) — and ends at the closing brace after the `page.lines.forEachIndexed { … }` block (line 247) — in
```kotlin
                    CompositionLocalProvider(LocalDensity provides density) {
                        Column( … unchanged … ) { … unchanged … }
                    }
```
The `if (marked != null) { ReferencePill(…) }` that follows stays outside the provider (the pill is interface text). `PageCaption` above and the page-number `Text` below are outside too.

- [ ] **Step 3: Run the tests**

Run: `scripts/test.sh`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/MushafPageView.kt
git commit -m "mushaf: the page keeps its width-fitted size at any font scale

The page is fitted to its frame's width, so the phone's font scale never made it bigger — 100 %
and 130 % were identical — but from 150 % Android's non-linear scaling (large text grows less
than small text) broke fittedSize's linear assumption: the size it chose still overflowed, the
justified lines ran out of room, and the words at the reading end were cut (audit vm-mushaf-scale,
seen on Al-Baqarah page 4 at 150 % and 200 %). The frame now measures and draws at font scale 1,
which at 100 % is the same density as before. Captions and the page number keep the phone's."
```

---

### Task 4: The back chevron has a name, and VoiceOver's escape gesture goes back on iOS

**Files:**
- Modify: 8 × `shared/src/commonMain/composeResources/values*/strings.xml` (new key `nav_a11y_back`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt:128-158` (`BackChevron`)
- Modify: `shared/src/iosMain/kotlin/world/taqwa/app/nav/SystemBackHandler.ios.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/nav/SystemBackHandler.kt` (KDoc only)
- Test (if the harness runs and resources load): `shared/src/iosTest/kotlin/world/taqwa/app/design/components/ControlSemanticsTest.kt`

**Interfaces:**
- Produces: `Res.string.nav_a11y_back`. `BackChevron(onBack)` unchanged in signature.

- [ ] **Step 1: Add the string to the eight files**

In each `strings.xml`, directly after the `language_name` string near the top, add a block. `values/strings.xml`:
```xml

    <!-- Navigation: what a screen reader calls the drawn back chevron. -->
    <string name="nav_a11y_back">Back</string>
```
The other languages, same key: `values-ar` `رجوع`; `values-bn` `ফিরে যান`; `values-fr` `Retour`; `values-id` `Kembali`; `values-tr` `Geri`; `values-ur` `واپس`. Then `python3 tools/sync-indonesian.py` writes `values-in`. Run `scripts/check-strings.sh` → `i18n: clean`.

- [ ] **Step 2: UI test (only if Task 2 kept resources)**

Create `shared/src/iosTest/kotlin/world/taqwa/app/design/components/ControlSemanticsTest.kt` (later tasks add to it):
```kotlin
package world.taqwa.app.design.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.getString
import world.taqwa.app.feature.settings.BackChevron
import world.taqwa.app.resources.Res
import world.taqwa.app.resources.nav_a11y_back
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ControlSemanticsTest {

    @Test
    fun theBackChevronIsANamedButton() = runComposeUiTest {
        var backed = false
        setContent { BackChevron(onBack = { backed = true }) }
        // The resource, not a literal: the simulator's language decides what "Back" is.
        val back = getString(Res.string.nav_a11y_back)
        onNodeWithContentDescription(back)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performClick()
        assertTrue(backed)
    }
}
```
`BackChevron` is `internal` in module `shared`; the test source set sees it. Run:
```bash
scripts/test.sh :shared:iosSimulatorArm64Test --tests 'world.taqwa.app.design.components.ControlSemanticsTest'
```
Expected: FAIL — no node with content description "Back".

- [ ] **Step 3: Name the chevron**

In `SettingsRootScreen.kt` add imports `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `world.taqwa.app.resources.nav_a11y_back`. Replace the body's opening of `BackChevron` (lines 129–137) with:
```kotlin
internal fun BackChevron(onBack: () -> Unit) {
    val colors = LocalTaqwaColors.current
    val back = stringResource(Res.string.nav_a11y_back)
    Box(
        Modifier
            .padding(start = 8.dp, top = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack)
            // A drawn chevron has no words of its own, and it is the first thing a screen reader
            // meets on every pushed screen: "Back", not "Unlabelled".
            .semantics { contentDescription = back },
        contentAlignment = Alignment.Center,
    ) {
```
Everything after (the `pointsLeft` canvas) is unchanged.

- [ ] **Step 4: The iOS back handler**

Replace the whole of `shared/src/iosMain/kotlin/world/taqwa/app/nav/SystemBackHandler.ios.kt` with:
```kotlin
package world.taqwa.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * iOS has no back button, but it has a back *gesture* for VoiceOver users: the two-finger scrub,
 * which UIKit delivers as `accessibilityPerformEscape`. Compose Multiplatform 1.12 turns that —
 * and a hardware keyboard's Escape — into an Escape key event and hands it to the innermost
 * enabled [BackHandler] (`ComposeSceneMediator.performEscape` → `BackNavigationEventInput`), so
 * the app's one handler in App.kt pops the stack, or returns to the Prayer tab, exactly as the
 * Android back button does. The on-screen chevron is unchanged. Before this the actual was empty
 * and the scrub did nothing (audit sr-back).
 *
 * `BackHandler` is deprecated in favour of `NavigationEventHandler`; it works in 1.12 and is a
 * one-line seam to swap once the replacement's API settles.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) =
    BackHandler(enabled = enabled, onBack = onBack)
```
`org.jetbrains.compose.ui:ui-backhandler` 1.12 is already on the iOS compile classpath (via material3 → navigationevent); no dependency change.

*Amended after implementation (fix rounds 1–2):* the handler is enabled only while VoiceOver is running — `BackHandler(enabled = enabled && voiceOver, …)` with `voiceOver` from `UIAccessibilityIsVoiceOverRunning()`, refreshed by a `UIAccessibilityVoiceOverStatusDidChangeNotification` observer in a `DisposableEffect` (re-read once after the observer registers) — because CMP 1.12 turns any enabled back handler into a screen-edge swipe-back for everyone (spec §3, amended). Switch Control is deliberately not in the gate: it leaves direct touch working. The committed file is the reference. In the common `SystemBackHandler.kt`, replace the last KDoc sentence (`So this is one more small expect/actual seam rather than a shared call.`) with: `So this is one more small expect/actual seam rather than a shared call: Android's activity BackHandler, and on iOS the common one, which VoiceOver's escape gesture reaches.`

- [ ] **Step 5: Run the tests and the iOS compile**

Run:
```bash
scripts/test.sh && ./gradlew :shared:compileKotlinIosSimulatorArm64
```
Expected: `BUILD SUCCESSFUL` twice; the UI test from Step 2 (if present) now passes.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/composeResources shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt shared/src/commonMain/kotlin/world/taqwa/app/nav/SystemBackHandler.kt shared/src/iosMain/kotlin/world/taqwa/app/nav/SystemBackHandler.ios.kt shared/src/iosTest
git commit -m "nav: the back chevron has a name, and VoiceOver's escape gesture goes back on iOS

Every pushed screen — settings pages, Qibla, Tasbeeh, the reader, the Mushaf — opened on a control
TalkBack called Unlabelled and VoiceOver called Button (audit sr-back). The chevron is now a
Button named Back in all seven languages. On iOS SystemBackHandler was a no-op, so VoiceOver's
two-finger scrub did nothing; it is now CMP's BackHandler, which 1.12 feeds from
accessibilityPerformEscape, so the scrub walks the same stack the chevron does."
```

---

### Task 5: A switch row is one switch to a screen reader

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/TaqwaToggle.kt` (moved from `SettingsRootScreen.kt:186-226`, made public, given `toggleable` semantics, plus `RowToggle`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt` (delete `TaqwaToggle`, lines 186–226, and its now-unused imports `animateDpAsState`, `getValue`)
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/TaqwaCard.kt:83-145` (`TaqwaRow` gains `toggle`)
- Modify the 12 rows that pass a toggle as `trailing`: `feature/settings/NotificationSettingsScreen.kt:132-142` and `:199-202`; `feature/settings/RecitationSettingsScreen.kt:107`, `:115`, `:133`; `feature/settings/PrayerTimesSettingsScreen.kt:133`, `:176`, `:187`, `:224`; `feature/settings/LocationSettingsScreen.kt:63-77`; `feature/quran/ReadingSheet.kt:228-236` and `:306`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/feature/recitation/DownloadSheet.kt:199-218` (the standalone auto-download row)
- Add imports of `world.taqwa.app.design.components.TaqwaToggle` where a settings file still calls it (`LocationSettingsScreen`, `NotificationSettingsScreen` … only if a call remains after the rewrite; `ReadingSheet.kt` and `DownloadSheet.kt` currently import it from `feature.settings` — change those imports)
- Test: `ControlSemanticsTest.kt` (harness) — no resources needed

**Interfaces:**
- Produces: `class RowToggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)` in `world.taqwa.app.design.components`; `TaqwaRow(…, toggle: RowToggle? = null)`; `TaqwaToggle(checked, onCheckedChange)` now public in `world.taqwa.app.design.components`.

- [ ] **Step 1: UI test**

Add to `ControlSemanticsTest.kt` (imports: `androidx.compose.ui.test.assertCountEquals`, `assertIsOff`, `isToggleable`, `onAllNodes`, `onNodeWithText`; `world.taqwa.app.design.components.RowToggle`, `TaqwaRow`):
```kotlin
    @Test
    fun aSwitchRowIsOneSwitchThatFlipsOnActivation() = runComposeUiTest {
        var checked = false
        setContent { TaqwaRow("Prayer notifications", toggle = RowToggle(checked) { checked = it }) }
        // One toggleable node — the row — and not a second, nameless one for the drawn switch.
        onAllNodes(isToggleable()).assertCountEquals(1)
        onNodeWithText("Prayer notifications")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            // The semantic action, not a touch: the row has no pointer input by design (only the
            // drawn switch does), and this action is what TalkBack's click and VoiceOver's
            // activate invoke.
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(checked)
    }
```
(*Amended after implementation:* `performClick()` taps the row's centre, where there is deliberately nothing to tap.) Run the class. Expected: FAIL to compile (`toggle` and `RowToggle` unknown).

- [ ] **Step 2: Move and upgrade TaqwaToggle**

Create `shared/src/commonMain/kotlin/world/taqwa/app/design/components/TaqwaToggle.kt`:
```kotlin
package world.taqwa.app.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors

/**
 * A switch drawn at the end of a [TaqwaRow], spoken as the row itself: the row becomes one switch
 * node carrying its label, its on/off state and a double-tap that flips it, while touch stays on
 * the drawn switch alone (see [TaqwaRow]'s `toggle`).
 */
class RowToggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)

/**
 * A switch in the app's own palette. Material3's would arrive with its own colour scheme and a
 * different silhouette from everything else on these screens.
 *
 * On its own it is a toggleable with the switch role, so a screen reader at least says "switch,
 * off"; inside a [TaqwaRow] it is hidden and the row speaks for it.
 */
@Composable
fun TaqwaToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalTaqwaColors.current
    val knobStart by animateDpAsState(if (checked) 22.dp else 3.dp, label = "knob")
    // Spec §92: 44 pt minimum. The switch is still drawn 48×28 — the outer box only widens what
    // a finger has to hit, so nothing on the screen moves or changes shape.
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            // The knob sliding is the feedback; a rectangular ripple around a pill looked like a
            // box lighting up behind it.
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accent else colors.hairline),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = knobStart)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.background else colors.surface),
            )
        }
    }
}
```
Delete lines 186–226 of `SettingsRootScreen.kt` (the KDoc `A switch in the app's own palette…` through the end of `TaqwaToggle`) and remove the imports it alone used (`androidx.compose.animation.core.animateDpAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.foundation.layout.defaultMinSize`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.draw.clip` — keep any that `BackChevron` still uses: `CircleShape`, `clip`, `size` stay).

- [ ] **Step 3: Give TaqwaRow a toggle**

In `TaqwaCard.kt`, add imports `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.onClick`, `androidx.compose.ui.semantics.role`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.toggleableState`, `androidx.compose.ui.state.ToggleableState`. Add the parameter after `trailing`:
```kotlin
    trailing: @Composable (() -> Unit)? = null,
    /**
     * A switch at the row's end. Drawn and touched exactly as `trailing = { TaqwaToggle(…) }` was —
     * only the switch itself takes a tap — but *spoken* as one thing: the row is a single switch
     * node carrying the label, the on/off state and a double-tap that flips it, where a screen
     * reader used to meet the label and then a nameless 48 dp button.
     */
    toggle: RowToggle? = null,
) {
```
Then, after `val clickModifier = when { … }`, add:
```kotlin
    // Semantics only: the pointer input stays on the drawn switch, so nothing about touch changes.
    val toggleSemantics = if (toggle == null) {
        Modifier
    } else {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = ToggleableState(toggle.checked)
            onClick {
                toggle.onCheckedChange(!toggle.checked)
                true
            }
        }
    }
    val trailingContent: (@Composable () -> Unit)? = if (toggle == null) {
        trailing
    } else {
        {
            // Hidden from semantics: the row speaks for it, and a second, nameless switch node
            // would put a screen reader's focus on an empty box.
            Box(Modifier.clearAndSetSemantics {}) { TaqwaToggle(toggle.checked, toggle.onCheckedChange) }
        }
    }
```
In the `Layout(content = { … })`, replace `if (trailing != null) { Box(Modifier.layoutId(TRAILING_ID)) { trailing() } }` with `if (trailingContent != null) { Box(Modifier.layoutId(TRAILING_ID)) { trailingContent() } }`. In the `modifier =` chain, insert `.then(toggleSemantics)` between `.defaultMinSize(minHeight = 48.dp)` and `.then(clickModifier)`.

- [ ] **Step 4: Rewrite the twelve rows**

Each `trailing = { TaqwaToggle(a, b) }` (or the multi-line form) becomes `toggle = RowToggle(a, b)`, keeping the lambda bodies exactly. For example `NotificationSettingsScreen.kt:132-142` becomes:
```kotlin
            TaqwaRow(
                stringResource(Res.string.notifications_master_toggle),
                toggle = RowToggle(settings.enabled) { enabled ->
                    if (enabled && !notificationsGranted) requestPermission() else onToggleEnabled(enabled)
                },
            )
```
and `LocationSettingsScreen.kt:63-77` keeps its comment and becomes `toggle = RowToggle(locationSource == LocationSource.GPS) { wantsGps -> if (wantsGps) requestLocation() else onChooseCity() }`. Add `import world.taqwa.app.design.components.RowToggle` to each of the six files; change `ReadingSheet.kt`'s and `DownloadSheet.kt`'s `import world.taqwa.app.feature.settings.TaqwaToggle` to `world.taqwa.app.design.components.TaqwaToggle` (DownloadSheet still calls it; ReadingSheet no longer does — drop the import there if unused).

- [ ] **Step 5: The download sheet's own row**

In `DownloadSheet.kt` lines 199–218, replace `.clickable(interactionSource = …, indication = null, onClick = { onAutoDownload(!autoDownload) })` with
```kotlin
            .toggleable(
                value = autoDownload,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onAutoDownload,
            )
```
(imports `androidx.compose.foundation.selection.toggleable`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.clearAndSetSemantics`; drop `clickable` if now unused) and replace `TaqwaToggle(autoDownload, onAutoDownload)` with `Box(Modifier.clearAndSetSemantics {}) { TaqwaToggle(autoDownload, onAutoDownload) }`. The row was already tappable as a whole, so touch is unchanged.

- [ ] **Step 6: Run the tests**

Run: `scripts/test.sh`
Expected: `BUILD SUCCESSFUL`; the Step 1 test passes.

- [ ] **Step 7: Commit**

```bash
git add -A shared/src
git commit -m "settings: a switch row is one switch to a screen reader

Prayer notifications, Tahajjud, GPS, sunrise, both Asr, transliteration, read-aloud, mobile data
and auto-download were unreadable: TalkBack met the label, then a nameless 48 dp button, and
flipping it said nothing (audit sr-switch). The row is now one node with the switch role, its
on/off state and a double-tap that flips it, while touch stays on the drawn switch alone, so
nothing sighted changes. TaqwaToggle moves to design/components, where a design component
belongs, and is a toggleable on its own for the download sheet's row."
```

---

### Task 6: Pickers, segmented controls and tabs say which option is selected

**Files:**
- Modify: 8 × `composeResources/values*/strings.xml` (new key `sound_a11y_preview`)
- Modify: `design/components/TaqwaCard.kt` (`TaqwaRow` gains `selected`)
- Modify: `feature/settings/SettingsRootScreen.kt:181-184` (`SettingsCard` gains `modifier`)
- Modify option rows: `feature/settings/NotificationSettingsScreen.kt:228-238`; `feature/settings/AppearanceSettingsScreen.kt:127-137` and `:143-154`; `feature/settings/PrayerTimesSettingsScreen.kt:351-361`; `feature/quran/ReadingSheet.kt:252-295`
- Modify: `feature/settings/AudioOptionRow.kt:76-86`; `feature/settings/SoundSheet.kt:92-103`; `feature/settings/AdhanVoiceSheet.kt:60-71`; `feature/recitation/ReciterPicker.kt:57-68`
- Modify: `design/components/TaqwaSegmented.kt:36-59`; `design/components/TaqwaTabBar.kt:138-170`
- Test: `ControlSemanticsTest.kt`

**Interfaces:**
- Produces: `TaqwaRow(…, selected: Boolean? = null)`; `SettingsCard(modifier: Modifier = Modifier, content)`; `Res.string.sound_a11y_preview` (one placeholder: the option's name).

- [ ] **Step 1: Strings**

After `nav_a11y_back` in each of the eight files add (`values`):
```xml
    <!-- The sound and adhan-voice sheets' preview buttons; %1$s is the option's own name. -->
    <string name="sound_a11y_preview">Hear %1$s</string>
```
`values-ar` `استمع إلى %1$s`; `values-bn` `%1$s শুনুন`; `values-fr` `Écouter %1$s`; `values-id` `Dengarkan %1$s`; `values-tr` `Dinle: %1$s`; `values-ur` `%1$s سنیں`. Sync Indonesian; `scripts/check-strings.sh` → clean.

- [ ] **Step 2: UI tests**

Add to `ControlSemanticsTest.kt` (imports: `assertIsSelected`, `assertIsNotSelected`, `isSelectable`; `TaqwaSegmented`):
```kotlin
    @Test
    fun anOptionRowIsARadioButtonWithItsState() = runComposeUiTest {
        setContent {
            TaqwaRow("Dark", onClick = {}, selectable = true, selected = true)
            TaqwaRow("Light", onClick = {}, selectable = true, selected = false)
        }
        onNodeWithText("Dark")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        onNodeWithText("Light").assertIsNotSelected()
    }

    @Test
    fun aSegmentedControlExposesTheSelectedSegmentAsATab() = runComposeUiTest {
        var picked = -1
        setContent { TaqwaSegmented(listOf("Surah", "Juz", "Bookmarks"), selectedIndex = 1) { picked = it } }
        onNodeWithText("Juz")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        onNodeWithText("Bookmarks").assertIsNotSelected().performClick()
        assertTrue(picked == 2)
        onAllNodes(isSelectable()).assertCountEquals(3)
    }
```
Run the class. Expected: the first fails to compile (`selected` unknown); the second fails on the Tab role.

- [ ] **Step 3: TaqwaRow's selected**

In `TaqwaCard.kt` add import `androidx.compose.foundation.selection.selectable`, and the parameter after `toggle`:
```kotlin
    /**
     * An option row's selected state. Set, the row is a radio button in the group its card declares
     * with `Modifier.selectableGroup()`, so a screen reader says which option is chosen and how many
     * there are; the drawn check mark is unchanged. Leave null for navigation rows.
     */
    selected: Boolean? = null,
```
and make `selected != null` the first branch of `clickModifier`:
```kotlin
    val clickModifier = when {
        selected != null -> Modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.RadioButton,
            onClick = onClick ?: {},
        )
        onClick == null -> Modifier
        …unchanged…
```
In `SettingsRootScreen.kt`, `SettingsCard` becomes:
```kotlin
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    TaqwaCard(modifier.padding(horizontal = SettingsGutter), content = content)
}
```

- [ ] **Step 4: The six option-row sites**

Each `TaqwaRow(… selectable = true, trailing = { if (X == Y) CheckMark() })` gains `selected = X == Y` (keep `selectable = true` and the trailing check mark), and its enclosing card declares the group (import `androidx.compose.foundation.selection.selectableGroup`):
- `NotificationSettingsScreen.kt:228` `SettingsCard {` → `SettingsCard(Modifier.selectableGroup()) {`; row: `selected = minutes == settings.remindBeforeMinutes`.
- `AppearanceSettingsScreen.kt:127` and `:143` → `SettingsCard(Modifier.selectableGroup()) {`; rows: `selected = mode == current` and `selected = value == widgetBackground`.
- `PrayerTimesSettingsScreen.kt:351` → `SettingsCard(Modifier.selectableGroup()) {`; row: `selected = preference == current`.
- `ReadingSheet.kt:252` `TaqwaCard {` → `TaqwaCard(Modifier.selectableGroup()) {`; rows: `selected = off` and `selected = info.id == effectiveId`.

- [ ] **Step 5: Audio option rows**

In `AudioOptionRow.kt` replace the row's `.clickable(interactionSource = …, indication = null) { onSelect() }` (lines 81–84) with
```kotlin
            // No ripple: the radio moving is the feedback, as on every option row. A selectable,
            // so a screen reader hears the radio role and which of the sheet's options is chosen.
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
            ) { onSelect() }
```
(import `androidx.compose.foundation.selection.selectable`, `androidx.compose.ui.semantics.Role`; drop `clickable` if unused — the preview `Box` still uses `clickable`, so keep it). Containers: `SoundSheet.kt:92` `TaqwaCard(Modifier.padding(top = 16.dp))` → `TaqwaCard(Modifier.padding(top = 16.dp).selectableGroup())`; `AdhanVoiceSheet.kt:60` → `.selectableGroup()` appended the same way; `ReciterPicker.kt:57` `TaqwaCard {` → `TaqwaCard(Modifier.selectableGroup()) {`. Preview names: `SoundSheet.kt:100` add `previewDescription = stringResource(Res.string.sound_a11y_preview, soundDisplayName(sound)),`; `AdhanVoiceSheet.kt:68` add `previewDescription = stringResource(Res.string.sound_a11y_preview, adhanVoiceDisplayName(voice)),` (import `world.taqwa.app.resources.sound_a11y_preview` in both).

- [ ] **Step 6: Segmented control and tab bar**

`TaqwaSegmented.kt`: import `androidx.compose.foundation.selection.selectable`, `selectableGroup`, `androidx.compose.ui.semantics.Role`; append `.selectableGroup()` to the `Row`'s modifier (after `.border(…)`); replace the option `Box`'s `.clickable(interactionSource = …, indication = null, onClick = { onSelect(index) })` with
```kotlin
                    .selectable(
                        selected = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
```
`TaqwaTabBar.kt`: same imports; append `.selectableGroup()` to the `Row` modifier in `TaqwaTabBar` (after `.height(BarHeight)`); in `TabItem` replace `.clickable(interactionSource = …, indication = null, onClick = onSelect)` with `.selectable(selected = selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onSelect)`. Remove the `clickable` imports if unused.

- [ ] **Step 7: Run the tests and the string check**

Run: `scripts/check-strings.sh && scripts/test.sh`
Expected: `i18n: clean`, `BUILD SUCCESSFUL`, both Step 2 tests pass.

- [ ] **Step 8: Commit**

```bash
git add -A shared/src
git commit -m "settings: pickers, segmented controls and tabs say which option is selected

Theme, calculation method, high-latitude rule, remind-before, translation, the sound and adhan
voice sheets, the reciter picker, the Asr madhab and Hijri switches, Surah | Juz | Bookmarks,
Translation | Mushaf and the three tabs all showed the current choice with a drawn mark and
nothing else, so a blind user could not confirm which calculation method their times use (audit
sr-choice, sr-segmented). Option rows are radio buttons in a selectable group, segments and tabs
are tabs, and the six preview buttons name what they play. Touch and drawing are unchanged."
```

---

### Task 7: Each prayer is one spoken line, and the countdown is a sentence

**Files:**
- Modify: 8 × `composeResources/values*/strings.xml` (five strings, two plurals)
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/CountdownSpeech.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/today/CountdownSpeechTest.kt`
- Modify: `feature/today/PrayerTimeline.kt:76-131` (`next` parameter), `:133-253` (`PrayerRow`)
- Modify: `design/components/CountdownRing.kt:22-56` (`description`)
- Modify: `feature/today/TodayScreen.kt:478-515` (`Countdown`, `TimelineCard`)

**Interfaces:**
- Produces: `internal data class CountdownParts(val hours: Int, val minutes: Int)` with `val underAMinute: Boolean`; `internal fun countdownParts(countdown: Duration): CountdownParts`; `internal fun prayerRowDescription(name: String, pills: List<String>, time: String): String`; `PrayerTimeline(…, next: Prayer? = null, …)`; `CountdownRing(…, description: String? = null)`.
- Strings: `today_a11y_prayer_next`, `today_a11y_prayer_current`, `today_a11y_prayer_passed`, `today_a11y_countdown` (3 placeholders: prayer, duration, clock time), `today_a11y_countdown_soon` (2: prayer, clock time), `duration_hours_minutes` (2), plurals `a11y_hours`, `a11y_minutes` (1 each).

- [ ] **Step 1: Strings**

In `values/strings.xml`, after `today_next_in` (line 42), add:
```xml
    <!-- Spoken only. What a screen reader says after a prayer row's name and time. -->
    <string name="today_a11y_prayer_next">Next prayer</string>
    <string name="today_a11y_prayer_current">Current prayer</string>
    <string name="today_a11y_prayer_passed">Passed</string>
    <!-- Spoken only. The countdown ring as one sentence: %1$s the prayer, %2$s a duration built from
         a11y_hours / a11y_minutes / duration_hours_minutes, %3$s the clock time. -->
    <string name="today_a11y_countdown">%1$s in %2$s, at %3$s</string>
    <string name="today_a11y_countdown_soon">%1$s in under a minute, at %2$s</string>
    <!-- Joins the hours and the minutes of a spoken duration. -->
    <string name="duration_hours_minutes">%1$s %2$s</string>
    <plurals name="a11y_hours">
        <item quantity="one">%1$s hour</item>
        <item quantity="other">%1$s hours</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="one">%1$s minute</item>
        <item quantity="other">%1$s minutes</item>
    </plurals>
```
The other languages (same keys, same place, after their `today_next_in`):

`values-ar`:
```xml
    <string name="today_a11y_prayer_next">الصلاة التالية</string>
    <string name="today_a11y_prayer_current">الصلاة الحالية</string>
    <string name="today_a11y_prayer_passed">انقضت</string>
    <string name="today_a11y_countdown">%1$s بعد %2$s، الساعة %3$s</string>
    <string name="today_a11y_countdown_soon">%1$s بعد أقل من دقيقة، الساعة %2$s</string>
    <string name="duration_hours_minutes">%1$s و%2$s</string>
    <plurals name="a11y_hours">
        <item quantity="zero">%1$s ساعة</item>
        <item quantity="one">%1$s ساعة</item>
        <item quantity="two">%1$s ساعتان</item>
        <item quantity="few">%1$s ساعات</item>
        <item quantity="many">%1$s ساعة</item>
        <item quantity="other">%1$s ساعة</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="zero">%1$s دقيقة</item>
        <item quantity="one">%1$s دقيقة</item>
        <item quantity="two">%1$s دقيقتان</item>
        <item quantity="few">%1$s دقائق</item>
        <item quantity="many">%1$s دقيقة</item>
        <item quantity="other">%1$s دقيقة</item>
    </plurals>
```
`values-bn`:
```xml
    <string name="today_a11y_prayer_next">পরবর্তী নামাজ</string>
    <string name="today_a11y_prayer_current">বর্তমান নামাজ</string>
    <string name="today_a11y_prayer_passed">পার হয়ে গেছে</string>
    <string name="today_a11y_countdown">%2$s পরে %1$s, %3$s-এ</string>
    <string name="today_a11y_countdown_soon">এক মিনিটেরও কম সময়ে %1$s, %2$s-এ</string>
    <string name="duration_hours_minutes">%1$s %2$s</string>
    <plurals name="a11y_hours">
        <item quantity="one">%1$s ঘণ্টা</item>
        <item quantity="other">%1$s ঘণ্টা</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="one">%1$s মিনিট</item>
        <item quantity="other">%1$s মিনিট</item>
    </plurals>
```
`values-fr`:
```xml
    <string name="today_a11y_prayer_next">Prochaine prière</string>
    <string name="today_a11y_prayer_current">Prière en cours</string>
    <string name="today_a11y_prayer_passed">Passée</string>
    <string name="today_a11y_countdown">%1$s dans %2$s, à %3$s</string>
    <string name="today_a11y_countdown_soon">%1$s dans moins d’une minute, à %2$s</string>
    <string name="duration_hours_minutes">%1$s %2$s</string>
    <plurals name="a11y_hours">
        <item quantity="one">%1$s heure</item>
        <item quantity="other">%1$s heures</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="one">%1$s minute</item>
        <item quantity="other">%1$s minutes</item>
    </plurals>
```
`values-id` (then sync to `values-in`):
```xml
    <string name="today_a11y_prayer_next">Salat berikutnya</string>
    <string name="today_a11y_prayer_current">Salat saat ini</string>
    <string name="today_a11y_prayer_passed">Sudah lewat</string>
    <string name="today_a11y_countdown">%1$s dalam %2$s, pukul %3$s</string>
    <string name="today_a11y_countdown_soon">%1$s kurang dari satu menit lagi, pukul %2$s</string>
    <string name="duration_hours_minutes">%1$s %2$s</string>
    <plurals name="a11y_hours">
        <item quantity="other">%1$s jam</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="other">%1$s menit</item>
    </plurals>
```
`values-tr`:
```xml
    <string name="today_a11y_prayer_next">Sonraki namaz</string>
    <string name="today_a11y_prayer_current">Şu anki namaz</string>
    <string name="today_a11y_prayer_passed">Geçti</string>
    <string name="today_a11y_countdown">%2$s sonra %1$s, saat %3$s</string>
    <string name="today_a11y_countdown_soon">Bir dakikadan az sonra %1$s, saat %2$s</string>
    <string name="duration_hours_minutes">%1$s %2$s</string>
    <plurals name="a11y_hours">
        <item quantity="one">%1$s saat</item>
        <item quantity="other">%1$s saat</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="one">%1$s dakika</item>
        <item quantity="other">%1$s dakika</item>
    </plurals>
```
`values-ur`:
```xml
    <string name="today_a11y_prayer_next">اگلی نماز</string>
    <string name="today_a11y_prayer_current">موجودہ نماز</string>
    <string name="today_a11y_prayer_passed">گزر چکی</string>
    <string name="today_a11y_countdown">%2$s بعد %1$s، %3$s بجے</string>
    <string name="today_a11y_countdown_soon">ایک منٹ سے کم میں %1$s، %2$s بجے</string>
    <string name="duration_hours_minutes">%1$s %2$s</string>
    <plurals name="a11y_hours">
        <item quantity="one">%1$s گھنٹہ</item>
        <item quantity="other">%1$s گھنٹے</item>
    </plurals>
    <plurals name="a11y_minutes">
        <item quantity="one">%1$s منٹ</item>
        <item quantity="other">%1$s منٹ</item>
    </plurals>
```
Run `python3 tools/sync-indonesian.py && scripts/check-strings.sh` → `i18n: clean`. (`duration_hours_minutes` has no Latin letters, so the same-as-English note does not fire.)

- [ ] **Step 2: Failing unit tests**

Create `shared/src/commonTest/kotlin/world/taqwa/app/feature/today/CountdownSpeechTest.kt`:
```kotlin
package world.taqwa.app.feature.today

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CountdownSpeechTest {

    @Test
    fun aCountdownSplitsIntoWholeHoursAndMinutes() {
        assertEquals(CountdownParts(4, 10), countdownParts(4.hours + 10.minutes + 2.seconds))
        assertEquals(CountdownParts(0, 59), countdownParts(59.minutes + 59.seconds))
        assertEquals(CountdownParts(1, 0), countdownParts(60.minutes))
    }

    @Test
    fun underAMinuteIsItsOwnCase() {
        assertTrue(countdownParts(30.seconds).underAMinute)
        assertFalse(countdownParts(1.minutes).underAMinute)
        // A countdown that has just crossed zero speaks as "under a minute", never as negative.
        assertEquals(CountdownParts(0, 0), countdownParts((-5).seconds))
    }

    @Test
    fun aPrayerRowReadsNameThenPillsThenTime() {
        assertEquals("Fajr, 5:10", prayerRowDescription("Fajr", emptyList(), "5:10"))
        assertEquals("Dhuhr, Jumuʿah, 12:30", prayerRowDescription("Dhuhr", listOf("Jumuʿah"), "12:30"))
        assertEquals(
            "Isha, Set by rule, 23:10",
            prayerRowDescription("Isha", listOf("Set by rule"), "23:10"),
        )
    }
}
```
Run: `scripts/test.sh :shared:testAndroidHostTest --tests 'world.taqwa.app.feature.today.CountdownSpeechTest'`
Expected: compile failure (`CountdownParts` unknown).

- [ ] **Step 3: The pure helpers**

Create `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/CountdownSpeech.kt`:
```kotlin
package world.taqwa.app.feature.today

import kotlin.time.Duration

/**
 * The whole hours and minutes of a countdown, as the spoken sentence needs them (accessibility
 * round 1, design §4): the ring shows seconds, but a screen reader is told "Fajr in 4 hours 10
 * minutes", so the description changes once a minute rather than sixty times.
 */
internal data class CountdownParts(val hours: Int, val minutes: Int) {
    /** Nothing left to count in minutes: the sentence says "under a minute" instead of "0 minutes". */
    val underAMinute: Boolean get() = hours == 0 && minutes == 0
}

/** Truncated to whole minutes, as the ring's own `inWholeMinutes` reading is; never negative. */
internal fun countdownParts(countdown: Duration): CountdownParts {
    val minutes = countdown.inWholeMinutes.coerceAtLeast(0L)
    return CountdownParts((minutes / 60L).toInt(), (minutes % 60L).toInt())
}

/**
 * One prayer row as a screen reader hears it: the name, any pill after it (Jumuʿah, Set by rule),
 * then the time — the visible order, as one line instead of three stops (audit sr-prayer-rows).
 */
internal fun prayerRowDescription(name: String, pills: List<String>, time: String): String =
    (listOf(name) + pills + time).joinToString(", ")
```
Run the test class again. Expected: PASS.

- [ ] **Step 4: The prayer rows**

In `PrayerTimeline.kt`: imports `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.stateDescription`, `world.taqwa.app.resources.today_a11y_prayer_current`, `today_a11y_prayer_next`, `today_a11y_prayer_passed`. Add to `PrayerTimeline`'s parameters, after `subLines`:
```kotlin
    /** The countdown's prayer, so its row can say "Next prayer"; a row is next only while UPCOMING. */
    next: Prayer? = null,
```
Pass it on: `PrayerRow(row, arabicAlone, row.prayer in setByRule, formatTime, isNext = row.status == PrayerStatus.UPCOMING && row.prayer == next)`. Give `PrayerRow` the parameter `isNext: Boolean` (last), and inside it, before the `Row(`, add:
```kotlin
    // One stop per prayer: name, pills, time, then its state — where a screen reader used to
    // walk three nodes per prayer and hear which was next from nothing (audit sr-prayer-rows).
    // The Arabic name beside the Latin one is decoration for the eye and is not spoken.
    val pills = listOfNotNull(
        if (row.isJumuah) stringResource(Res.string.today_jumuah) else null,
        if (setByRule) stringResource(Res.string.today_set_by_rule) else null,
    )
    val description = prayerRowDescription(localizedPrayerName(row.prayer), pills, formatTime(row))
    val state = when {
        row.status == PrayerStatus.CURRENT -> stringResource(Res.string.today_a11y_prayer_current)
        row.status == PrayerStatus.PASSED -> stringResource(Res.string.today_a11y_prayer_passed)
        isNext -> stringResource(Res.string.today_a11y_prayer_next)
        else -> null
    }
```
and change the row's modifier from `Modifier.fillMaxWidth(),` to
```kotlin
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                if (state != null) stateDescription = state
            },
```

- [ ] **Step 5: The ring**

`CountdownRing.kt`: imports `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.contentDescription`; add the parameter `description: String? = null,` after `diameter`, with the KDoc line `/** One sentence for a screen reader in place of the three fragments inside; null leaves the texts as they are. */`, and change the `Box(modifier.size(diameter), …)` to
```kotlin
    Box(
        modifier
            .size(diameter)
            .then(if (description == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = description }),
        contentAlignment = Alignment.Center,
    ) {
```
`TodayScreen.kt`: imports `org.jetbrains.compose.resources.pluralStringResource`, `world.taqwa.app.resources.a11y_hours`, `a11y_minutes`, `duration_hours_minutes`, `today_a11y_countdown`, `today_a11y_countdown_soon`. Rewrite `Countdown`:
```kotlin
@Composable
private fun Countdown(
    state: TodayUiState.Ready,
    zone: TimeZone,
    format: PlatformFormat,
    diameter: Dp = CountdownRingSize,
) {
    val name = localizedPrayerName(state.today.next.prayer)
    val clock = formatClock(state.today.next.instant, zone, format)
    // "FAJR IN", "4:10:02" and "5:10" read as three fragments, one of them like a clock time
    // (audit sr-countdown); this is the same fact as one sentence, from whole minutes so it
    // changes once a minute. Not a live region: the ring is read when reached, never announced.
    val parts = countdownParts(state.today.countdown)
    val description = if (parts.underAMinute) {
        stringResource(Res.string.today_a11y_countdown_soon, name, clock)
    } else {
        val hours = pluralStringResource(Res.plurals.a11y_hours, parts.hours, format.localizedDigits(parts.hours))
        val minutes = pluralStringResource(Res.plurals.a11y_minutes, parts.minutes, format.localizedDigits(parts.minutes))
        val duration = when {
            parts.hours == 0 -> minutes
            parts.minutes == 0 -> hours
            else -> stringResource(Res.string.duration_hours_minutes, hours, minutes)
        }
        stringResource(Res.string.today_a11y_countdown, name, duration, clock)
    }
    CountdownRing(
        progress = state.today.ringProgress,
        label = stringResource(Res.string.today_next_in, name),
        countdown = CountdownFormatter.countdown(
            state.today.countdown,
            format,
            CountdownFormatter.ARABIC_INDIC_DIGITS_VERIFIED_TABULAR,
        ),
        clockTime = clock,
        diameter = diameter,
        description = description,
    )
}
```
In `TimelineCard`, pass `next = state.today.next.prayer,` after `subLines = …`.

- [ ] **Step 6: Run everything**

Run: `scripts/check-strings.sh && scripts/test.sh`
Expected: clean, `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -A shared/src
git commit -m "prayer: each prayer is one spoken line, and the countdown is a sentence

Five prayers took about fifteen swipes — name, Arabic name, time — and nothing said which was
next or current; the ring read FAJR IN, then 4:10:02 like a clock time, then 5:10 (audit
sr-prayer-rows, sr-countdown). Each row is now one node, \"Asr, 3:42\" with the state Next
prayer, Current prayer or Passed, and the ring one sentence, \"Fajr in 4 hours 10 minutes, at
5:10\", from whole minutes so it changes once a minute. Nothing drawn changes."
```

---

### Task 8: Settings titles and section labels are headings

**Files:**
- Modify: `feature/settings/SettingsRootScreen.kt:111-116` (title) and `:160-168` (`SectionLabel`)

**Interfaces:** none.

- [ ] **Step 1: Add the headings**

Imports `androidx.compose.ui.semantics.heading`, `androidx.compose.ui.semantics.semantics` (the latter already added in Task 4). The title `Text` in `SettingsScaffold` gets `.semantics { heading() }` appended to its `modifier`; `SectionLabel`'s `Text` likewise:
```kotlin
        modifier = Modifier.padding(start = SettingsGutter + 4.dp, bottom = 8.dp).semantics { heading() },
```
Add the comment above the title's modifier: `// Headings, so TalkBack's heading navigation and the VoiceOver rotor can jump from section to section instead of walking every row (audit sr-headings).`

- [ ] **Step 2: Run the tests, commit**

Run: `scripts/test.sh` → `BUILD SUCCESSFUL`.
```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt
git commit -m "settings: titles and section labels are headings

The app had no headings, so heading navigation found nothing and a long settings page could
only be walked row by row (audit sr-headings). Every settings screen's title and every section
label now carry the heading semantic; nothing drawn changes."
```

---

### Task 9: Ayah cards name the ayah and read the translation; the Arabic is for the reciters

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/AyahSpeech.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/AyahSpeechTest.kt`
- Modify: `feature/quran/AyahCard.kt` (whole card semantics, four callbacks)
- Modify: `feature/quran/ReaderScreen.kt:330-378` (pass the callbacks), `:317-328` (hide the basmala)
- Modify: `feature/quran/ReadingSheet.kt:210-224` (hide the preview), `feature/quran/QuranRootScreen.kt:797-806` (search hit) and `:892-906` (bookmark row), `feature/settings/AyahWidgetPreview.kt:138-152` (preview)

**Interfaces:**
- Produces: `internal fun spokenAyah(ayahLabel: String, translation: String?, translationLanguage: String): AnnotatedString`; `AyahCard(…, onPlay: () -> Unit = {}, onBookmark: () -> Unit = {}, onCopy: () -> Unit = {}, onShare: () -> Unit = {})`.
- Consumes: `Res.string.quran_ayah_n`, `recitation_play`, `recitation_a11y_pause`, `quran_action_bookmark`, `quran_remove_bookmark`, `quran_action_copy`, `quran_action_share`, `quran_action_bookmarked`, `quran_a11y_read_aloud` (all existing).

- [ ] **Step 1: Failing test**

Create `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/AyahSpeechTest.kt`:
```kotlin
package world.taqwa.app.feature.quran

import androidx.compose.ui.text.intl.LocaleList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AyahSpeechTest {

    @Test
    fun anAyahIsItsNumberThenItsTranslation() {
        val spoken = spokenAyah("Ayah 255", "Allah — there is no deity except Him.", "en")
        assertEquals("Ayah 255. Allah — there is no deity except Him.", spoken.text)
    }

    @Test
    fun theTranslationRunCarriesItsOwnLanguage() {
        val spoken = spokenAyah("Verset 255", "Allah ! Point de divinité à part Lui.", "fr")
        val runs = spoken.spanStyles.filter { it.item.localeList != null }
        assertEquals(1, runs.size)
        assertEquals(LocaleList("fr"), runs.single().item.localeList)
        assertEquals("Verset 255. ".length, runs.single().start)
        assertEquals(spoken.text.length, runs.single().end)
    }

    @Test
    fun withoutATranslationOnlyTheNumberIsSpoken() {
        val spoken = spokenAyah("Ayah 1", null, "en")
        assertEquals("Ayah 1", spoken.text)
        assertTrue(spoken.spanStyles.isEmpty())
        assertEquals("Ayah 1", spokenAyah("Ayah 1", "  ", "en").text)
    }
}
```
Run: `scripts/test.sh :shared:testAndroidHostTest --tests 'world.taqwa.app.feature.quran.AyahSpeechTest'` → compile failure.

- [ ] **Step 2: The helper**

Create `shared/src/commonMain/kotlin/world/taqwa/app/feature/quran/AyahSpeech.kt`:
```kotlin
package world.taqwa.app.feature.quran

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle

/**
 * What a screen reader gets for an ayah (accessibility round 1, design §5): its number, then the
 * translation in its own language — and never the Arabic. The Quran is recited by one of the
 * app's reciters or not at all; a phone voice reading Uthmani text is not a recitation, so the
 * Arabic is hidden from semantics wherever it is drawn and the card offers Play instead.
 *
 * The translation's run is tagged with [translationLanguage] so a voice for that language reads
 * it: VoiceOver honours the tag (CMP maps it to `UIAccessibilitySpeechAttributeLanguage`);
 * TalkBack reads it in the interface's voice, as it always has.
 */
internal fun spokenAyah(ayahLabel: String, translation: String?, translationLanguage: String): AnnotatedString =
    buildAnnotatedString {
        append(ayahLabel)
        if (!translation.isNullOrBlank()) {
            append(". ")
            withStyle(SpanStyle(localeList = LocaleList(translationLanguage))) { append(translation) }
        }
    }
```
Run the test class → PASS.

- [ ] **Step 3: The card**

In `AyahCard.kt` add imports: `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.customActions`, `androidx.compose.ui.semantics.selected`, `androidx.compose.ui.semantics.stateDescription`, `androidx.compose.ui.semantics.text`, `world.taqwa.app.i18n.LocalPlatformFormat`, `world.taqwa.app.resources.quran_action_bookmark`, `quran_action_copy`, `quran_action_share`, `quran_ayah_n`, `quran_remove_bookmark`, `recitation_a11y_pause`, `recitation_play`. Add parameters after `onClick`:
```kotlin
    onClick: () -> Unit,
    /**
     * The four actions of the row, as screen-reader actions on every card (design §5): the row
     * itself is inside the card's cleared semantics, and a blind reader should not have to select
     * a card and then find a row to play, keep, copy or share an ayah. Play starts the chosen
     * reciter from this ayah — the same call the visible button makes.
     */
    onPlay: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
) {
```
After `val bookmarkedLabel = …` add:
```kotlin
    val format = LocalPlatformFormat.current
    val ayahLabel = stringResource(Res.string.quran_ayah_n, format.localizedDigits(ayahNumber))
    val readAloudLabel = stringResource(Res.string.quran_a11y_read_aloud)
    val playLabel = stringResource(if (playing) Res.string.recitation_a11y_pause else Res.string.recitation_play)
    val bookmarkLabel = stringResource(if (bookmarked) Res.string.quran_remove_bookmark else Res.string.quran_action_bookmark)
    val copyLabel = stringResource(Res.string.quran_action_copy)
    val shareLabel = stringResource(Res.string.quran_action_share)
    // The whole card is one node and this is all of it: the ayah's number, then the translation
    // in its own language (never the Arabic — design §5), its states, and the four actions.
    // clearAndSetSemantics drops everything drawn inside, so each state the badge and the
    // read-aloud mark used to carry is restated here.
    val spoken = remember(ayahLabel, translation, translationLanguage) { spokenAyah(ayahLabel, translation, translationLanguage) }
    val states = listOfNotNull(if (bookmarked) bookmarkedLabel else null, if (speaking) readAloudLabel else null)
    // Read into a val: inside the semantics block `selected` would otherwise name the receiver's
    // own property, not this card's parameter.
    val isSelected = selected
```
Change the `TaqwaCard(Modifier.clickable(…))` call to:
```kotlin
    TaqwaCard(
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                text = spoken
                this.selected = isSelected
                if (states.isNotEmpty()) stateDescription = states.joinToString(", ")
                customActions = listOf(
                    CustomAccessibilityAction(playLabel) { onPlay(); true },
                    CustomAccessibilityAction(bookmarkLabel) { onBookmark(); true },
                    CustomAccessibilityAction(copyLabel) { onCopy(); true },
                    CustomAccessibilityAction(shareLabel) { onShare(); true },
                )
            },
    ) {
```
(the `clearAndSetSemantics` sits *after* `clickable` so the click's own semantics survive and merge with it). Leave the two `Canvas` `.semantics { contentDescription = … }` in place — they are inside the cleared subtree and harmless.

- [ ] **Step 4: The reader passes the actions**

In `ReaderScreen.kt` `AyahCard(` call (line 332), after `onClick = …` add:
```kotlin
                            onPlay = {
                                if (playingAyah == ayah.number) recitation.onToggle() else recitation.onPlayAyah(ready.surah.number, ayah.number)
                            },
                            onBookmark = { onToggleBookmark(ayah.number) },
                            onCopy = { shareTextFor(ayah.number)?.let { clipboard.setText(AnnotatedString(it)) } },
                            onShare = { shareTextFor(ayah.number)?.let(::shareText) },
```
The basmala `Text` (line 319) gets `.clearAndSetSemantics {}` appended to its modifier with the comment `// Quran text: not for a phone voice (design §5).` (import `androidx.compose.ui.semantics.clearAndSetSemantics`).

- [ ] **Step 5: Hide the Arabic elsewhere**

Append `.clearAndSetSemantics {}` (same import and one-line comment in each file) to the modifier of: `ReadingSheet.kt` the preview `Text` (line 222: `modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clearAndSetSemantics {}`); `QuranRootScreen.kt` the search hit's Arabic `Text` (line 805: `modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}`) and the bookmark row's (line 904, same); `AyahWidgetPreview.kt` the card's Arabic `Text` (line 150). Surah names in the Mushaf font (`SurahRow`, `ContinueReadingCard`, headers) are left alone: they are names, not recitation.

- [ ] **Step 6: Run the tests, commit**

Run: `scripts/test.sh` → `BUILD SUCCESSFUL`.
```bash
git add -A shared/src
git commit -m "quran: a screen reader names each ayah and reads its translation; the Arabic is for the reciters

The owner's rule: the Quran is recited by one of the app's reciters or not at all, never read by a
phone voice. Each ayah card is now one node — \"Ayah 255\", then the translation tagged with its
own language, then Bookmarked or Being read aloud — with Play, Bookmark, Copy and Share as
screen-reader actions on every card, so nothing needs a selection first (audit sr-ayah-card,
sr-quran-text). The basmala, the reading sheet's size preview, search hits, bookmark rows and the
widget preview hide their Arabic the same way. Transliteration is not spoken either: a phone
voice reading it would be a mangled recitation. Nothing drawn changes."
```

---

### Task 10: Each Mushaf ayah is one invisible stop, with Play

**Files:**
- Modify: `feature/quran/MushafLayout.kt` (append `AyahStop`, `ayahStops`)
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/quran/MushafPageViewTest.kt` (append tests)
- Modify: `feature/quran/MushafPageView.kt` (`onPlayAyah` parameter, stops per line, `MushafTextLine`, hidden basmala)
- Modify: `feature/quran/MushafScreen.kt:256-291` (pass `onPlayAyah`)

**Interfaces:**
- Produces: `internal data class AyahStop(val lineIndex: Int, val words: IntRange, val surah: Int, val ayah: Int)`; `internal fun ayahStops(page: MushafPage): List<AyahStop>`; `MushafPageView(…, onPlayAyah: (Int, Int) -> Unit = { _, _ -> })`.
- Consumes: `MushafPage`, `MushafLine`, `LineWord`, `LineType` from `world.taqwa.app.quran`; `PlacedLine.positions`.

- [ ] **Step 1: Failing tests**

Append to `MushafPageViewTest.kt` (imports `world.taqwa.app.quran.LineType`, `LineWord`, `MushafLine`, `MushafPage`):
```kotlin
    // --- ayahStops ---

    private fun word(surah: Int, ayah: Int, index: Int) = LineWord(index, surah, ayah, index, "w")
    private fun textLine(line: Int, words: List<LineWord>) = MushafLine(
        line, LineType.TEXT, words.first().surah, words.joinToString(" ") { it.text },
        words.first().surah, words.first().ayah, words.last().surah, words.last().ayah, endsSurah = false, words = words,
    )

    @Test
    fun `every ayah gets one stop over its first run and none over the lines it runs on to`() {
        val page = MushafPage(
            3, 2, 5, 1,
            listOf(
                textLine(1, listOf(word(2, 5, 0), word(2, 5, 1), word(2, 6, 2))),
                textLine(2, listOf(word(2, 6, 0), word(2, 7, 1), word(2, 7, 2), word(2, 8, 3))),
                textLine(3, listOf(word(2, 8, 0), word(2, 8, 1))),
            ),
        )
        assertEquals(
            listOf(
                AyahStop(0, 0..1, 2, 5),
                AyahStop(0, 2..2, 2, 6),
                AyahStop(1, 1..2, 2, 7),
                AyahStop(1, 3..3, 2, 8),
            ),
            ayahStops(page),
        )
    }

    @Test
    fun `surah bands and the basmala get no stop, and a new surah's first ayah does`() {
        val band = MushafLine(1, LineType.SURAH, 112, null, null, null, null, null, endsSurah = false, words = emptyList())
        val basmala = MushafLine(2, LineType.BASMALA, 112, null, null, null, null, null, endsSurah = false, words = emptyList())
        val page = MushafPage(604, 112, 1, 30, listOf(band, basmala, textLine(3, listOf(word(112, 1, 0), word(112, 2, 1)))))
        assertEquals(listOf(AyahStop(2, 0..0, 112, 1), AyahStop(2, 1..1, 112, 2)), ayahStops(page))
    }

    @Test
    fun `the fixture pages give one stop per distinct ayah in reading order`() {
        for (page in listOf(MUSHAF_PAGE_1, MUSHAF_PAGE_2, MUSHAF_PAGE_3)) {
            val ayahs = page.lines.filter { it.type == LineType.TEXT }.flatMap { it.words }.map { it.surah to it.ayah }.distinct()
            assertEquals(ayahs, ayahStops(page).map { it.surah to it.ayah }, "page ${page.number}")
        }
    }
```
Run: `scripts/test.sh :shared:testAndroidHostTest --tests 'world.taqwa.app.feature.quran.MushafPageViewTest'` → compile failure.

- [ ] **Step 2: The pure rule**

Append to `MushafLayout.kt`:
```kotlin

/** One ayah's first run of words on a page: the one stop a screen reader gets for it. */
internal data class AyahStop(val lineIndex: Int, val words: IntRange, val surah: Int, val ayah: Int)

/**
 * The first run of every ayah on the page, in reading order (accessibility round 1, design §5):
 * a screen reader gets one stop per ayah, over the words it starts with on this page, and none
 * over the lines it runs on to — so an ayah three lines long is one stop, not three. The stop
 * carries the ayah's number, never its text; the Quran is for the reciters.
 */
internal fun ayahStops(page: MushafPage): List<AyahStop> {
    val seen = HashSet<Pair<Int, Int>>()
    val stops = mutableListOf<AyahStop>()
    page.lines.forEachIndexed { lineIndex, line ->
        if (line.type != LineType.TEXT) return@forEachIndexed
        var start = 0
        while (start < line.words.size) {
            val first = line.words[start]
            var end = start
            while (end + 1 < line.words.size && line.words[end + 1].surah == first.surah && line.words[end + 1].ayah == first.ayah) end++
            if (seen.add(first.surah to first.ayah)) stops += AyahStop(lineIndex, start..end, first.surah, first.ayah)
            start = end + 1
        }
    }
    return stops
}
```
Run the test class → PASS.

- [ ] **Step 3: The stops on the page**

`MushafPageView.kt`: imports `androidx.compose.foundation.layout.matchParentSize` is a `BoxScope` member (no import), plus `androidx.compose.ui.layout.Layout`, `androidx.compose.ui.semantics.CustomAccessibilityAction`, `clearAndSetSemantics`, `contentDescription`, `customActions`, `isTraversalGroup`, `onClick`, `semantics`, `traversalIndex`, `androidx.compose.ui.unit.Constraints`, `world.taqwa.app.resources.quran_ayah_n`, `kotlin.math.roundToInt`. Add the parameter to `MushafPageView`, after `onPlay`:
```kotlin
    /** Play from *this* ayah, for the screen-reader action on each ayah's stop (design §5). */
    onPlayAyah: (Int, Int) -> Unit = { _, _ -> },
```
After `val lit = …` add:
```kotlin
    // One invisible stop per ayah, over its first run of words (design §5): the page's own rule,
    // computed once per page, then handed to each line for the runs that fall on it.
    val stopsByLine = remember(page) { ayahStops(page).groupBy { it.lineIndex } }
```
In the `LineType.TEXT` branch pass `stops = stopsByLine[index].orEmpty(), onPlayAyah = onPlayAyah,` to `MushafTextLine`. The `LineType.BASMALA` `BasicText` gets `.clearAndSetSemantics {}` appended to its modifier (`// Quran text: not for a phone voice (design §5).`).

Rewrite `MushafTextLine`'s signature and body:
```kotlin
@Composable
private fun MushafTextLine(
    line: MushafLine,
    placed: PlacedLine,
    lineHeight: Dp,
    highlighted: Pair<Int, Int>?,
    playing: Pair<Int, Int>?,
    stops: List<AyahStop>,
    onTapAyah: (Int, Int) -> Unit,
    onPlayAyah: (Int, Int) -> Unit,
) {
    val colors = LocalTaqwaColors.current
    val format = LocalPlatformFormat.current
    val playLabel = stringResource(Res.string.recitation_play)
    val field = colors.accent.copy(alpha = 0.16f)
    val voiceField = colors.accent.copy(alpha = 0.28f)
    val runs = remember(line, highlighted) { … unchanged … }
    val voiceRuns = remember(line, playing) { … unchanged … }
    // The line is a traversal group and each stop carries its order, because Compose sorts a
    // row's nodes left to right when the *root* view is LTR — an English interface — and this
    // page reads right to left whatever the interface does.
    Box(Modifier.fillMaxWidth().height(lineHeight).semantics { isTraversalGroup = true }) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(line, placed) { … unchanged … },
        ) { … unchanged drawing … }
        if (stops.isNotEmpty()) {
            // Invisible, touch-transparent boxes over each stop's words, placed in absolute pixels
            // (`place`, not `placeRelative`: the positions are already right-to-left). A screen
            // reader's double-tap selects the ayah as a tap does; Play starts the reciter there.
            Layout(
                modifier = Modifier.matchParentSize(),
                content = {
                    stops.forEachIndexed { order, stop ->
                        val label = stringResource(Res.string.quran_ayah_n, format.localizedDigits(stop.ayah))
                        Box(
                            Modifier.semantics {
                                contentDescription = label
                                traversalIndex = order.toFloat()
                                onClick { onTapAyah(stop.surah, stop.ayah); true }
                                customActions = listOf(CustomAccessibilityAction(playLabel) { onPlayAyah(stop.surah, stop.ayah); true })
                            },
                        )
                    }
                },
            ) { measurables, constraints ->
                fun span(stop: AyahStop) = placed.positions[stop.words.last].left to placed.positions[stop.words.first].right
                val placeables = measurables.mapIndexed { i, measurable ->
                    val (left, right) = span(stops[i])
                    measurable.measure(Constraints.fixed((right - left).roundToInt().coerceAtLeast(1), constraints.maxHeight))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { i, placeable -> placeable.place(span(stops[i]).first.roundToInt(), 0) }
                }
            }
        }
    }
}
```
`MushafScreen.kt`: after `onPlay = { … }` (line 290) add
```kotlin
                            onPlayAyah = { surah, ayah ->
                                if (recitation.playing == surah to ayah) recitation.onToggle() else recitation.onPlayAyah(surah, ayah)
                            },
```

- [ ] **Step 4: Run the tests, commit**

Run: `scripts/test.sh` → `BUILD SUCCESSFUL`.
```bash
git add -A shared/src
git commit -m "mushaf: each ayah is one invisible stop for a screen reader, with Play

In Mushaf mode a screen reader found the header, the captions and the page number and not one
ayah: every line was a Canvas with a raw tap (audit sr-mushaf, a blocker). Each ayah now has one
invisible, touch-transparent stop over its first run of words on the page — \"Ayah 255\", never
the text, by the owner's rule — which a double-tap selects as a tap does and whose Play starts
the chosen reciter there. Stops are ordered right to left within each line whatever the
interface's direction. Which runs get a stop is a pure rule in MushafLayout, tested."
```

---

### Task 11: The ayah widgets tell a screen reader their reference and translation

**Files:**
- Create: `widgetcore/src/commonMain/kotlin/world/taqwa/app/widget/AyahWidgetSpeech.kt`
- Test: `widgetcore/src/commonTest/kotlin/world/taqwa/app/widget/AyahWidgetSpeechTest.kt`
- Modify: 7 × `androidApp/src/main/res/values*/strings.xml` (new key `widget_ayah_a11y_reference`)
- Modify: `androidApp/src/main/kotlin/world/taqwa/app/widget/TaqwaAyahGlanceWidget.kt:198-249`
- Modify: 7 × `iosApp/TaqwaWidget/*.lproj/Localizable.strings` (new key `ayah_widget_a11y_reference`)
- Modify: `iosApp/TaqwaWidget/TaqwaAyahWidget.swift:313-326` and the placeholder lookup (`:438-444`)

**Interfaces:**
- Produces: `object AyahWidgetSpeech { fun label(widgetName: String, reference: String, translation: String?): String }` (exported to Swift as `AyahWidgetSpeech.shared.label(widgetName:reference:translation:)` through the `widgetcore` framework).

- [ ] **Step 1: Failing test**

Create `widgetcore/src/commonTest/kotlin/world/taqwa/app/widget/AyahWidgetSpeechTest.kt`:
```kotlin
package world.taqwa.app.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class AyahWidgetSpeechTest {

    @Test
    fun theCardIsItsNameItsReferenceThenItsTranslation() {
        assertEquals(
            "Ayah of the day, Ar-Ra’d, ayah 28. Those who have believed and whose hearts are assured.",
            AyahWidgetSpeech.label("Ayah of the day", "Ar-Ra’d, ayah 28", "Those who have believed and whose hearts are assured."),
        )
    }

    @Test
    fun withoutATranslationTheSentenceStopsAtTheReference() {
        assertEquals("آية اليوم, الرعد، الآية ٢٨", AyahWidgetSpeech.label("آية اليوم", "الرعد، الآية ٢٨", null))
        assertEquals("Ayah of the day, Ar-Ra’d, ayah 28", AyahWidgetSpeech.label("Ayah of the day", "Ar-Ra’d, ayah 28", " "))
    }
}
```
Run: `scripts/test.sh :widgetcore:testAndroidHostTest --tests 'world.taqwa.app.widget.AyahWidgetSpeechTest'` → compile failure.

- [ ] **Step 2: The shared sentence**

Create `widgetcore/src/commonMain/kotlin/world/taqwa/app/widget/AyahWidgetSpeech.kt`:
```kotlin
package world.taqwa.app.widget

/**
 * The one sentence both ayah widgets give a screen reader (accessibility round 1, design §5): the
 * widget's name, the ayah's reference, then its translation when the card shows one — and never
 * the Arabic, which is for the reciters. Here in `:widgetcore` so Glance and WidgetKit cannot
 * drift apart; each platform supplies the localized name and reference from its own resources.
 */
object AyahWidgetSpeech {
    fun label(widgetName: String, reference: String, translation: String?): String {
        val head = "$widgetName, $reference"
        return if (translation.isNullOrBlank()) head else "$head. $translation"
    }
}
```
Run the test → PASS.

- [ ] **Step 3: Android strings and Glance**

Add to each Android `strings.xml` after `widget_ayah_description` (`values`):
```xml
    <!-- Spoken only: the ayah widget's reference for a screen reader. %1$s the surah, %2$s the ayah number. -->
    <string name="widget_ayah_a11y_reference">%1$s, ayah %2$s</string>
```
`values-ar` `%1$s، الآية %2$s`; `values-bn` `%1$s, আয়াত %2$s`; `values-fr` `%1$s, verset %2$s`; `values-in` `%1$s, ayat %2$s`; `values-tr` `%1$s, %2$s. ayet`; `values-ur` `%1$s، آیت %2$s`. Run `scripts/check-strings.sh` → clean.

In `TaqwaAyahGlanceWidget.kt` (import `world.taqwa.app.R`), inside `AyahWidgetContent` after the `bitmap` is remembered, add:
```kotlin
        // What TalkBack says for the card (design §5): the widget's name, the reference in the
        // interface's script and digits, then the translation when the card shows one. The
        // bitmap itself stays decorative; this is the one label the tap target carries. Before
        // this the whole card was an image marked decorative and TalkBack had only the widget's
        // name (audit pl-ayah-widget, a blocker).
        val surahName = if (UiLanguage.of(render.languageTag).arabicScript) entry.surahArabic else entry.surahLatin
        val ayahNumber = WidgetDigits.localize(entry.ayah.toString(), render.arabicIndicDigits, render.languageTag)
        val label = AyahWidgetSpeech.label(
            context.getString(R.string.widget_ayah_label),
            context.getString(R.string.widget_ayah_a11y_reference, surahName, ayahNumber),
            if (render.showTranslation) entry.translation else null,
        )
```
and change `contentDescription = null,` on the `Image` to `contentDescription = label,`.

- [ ] **Step 4: iOS strings and SwiftUI**

Add to each `Localizable.strings` after `ayah_widget_placeholder` (`en.lproj`):
```
/* Spoken only: the ayah widget's reference for VoiceOver. %1$@ the surah, %2$@ the ayah number. */
"ayah_widget_a11y_reference" = "%1$@, ayah %2$@";
```
`ar` `"%1$@، الآية %2$@"`; `bn` `"%1$@, আয়াত %2$@"`; `fr` `"%1$@, verset %2$@"`; `id` `"%1$@, ayat %2$@"`; `tr` `"%1$@, %2$@. ayet"`; `ur` `"%1$@، آیت %2$@"`.

In `TaqwaAyahWidget.swift`, make the placeholder's lookup reusable — replace the `placeholderText` computed property's body with a call to a new helper, and add the label:
```swift
    /// A string from the extension's own table, or [fallback] when this file is running inside the
    /// app (the `-taqwaWidgetPreview` route), where that table is absent.
    private func localized(_ key: String, fallback: String) -> String {
        let localized = Bundle.main.localizedString(forKey: key, value: key, table: nil)
        return localized == key ? fallback : localized
    }

    private var placeholderText: String {
        let arabic = Locale.current.language.languageCode?.identifier.hasPrefix("ar") ?? false
        return localized("ayah_widget_placeholder", fallback: arabic ? "افتح تقوى مرة لتحميل آية اليوم" : "Open Taqwa once to load today's ayah")
    }

    /// The card as VoiceOver hears it (design §5): the widget's name, the reference in the
    /// interface's script and digits, then the translation when the card shows one — never the
    /// Arabic. The same sentence Android's card carries, built by `:widgetcore`.
    private func accessibilityLabel(_ ayah: AyahPoolEntry) -> String {
        let name = entry.arabicUi ? ayah.surahArabic : ayah.surahLatin
        let number = WidgetDigits.shared.localize(text: "\(ayah.ayah)", nativeDigits: entry.arabicIndicDigits, languageTag: entry.languageTag)
        let reference = String(format: localized("ayah_widget_a11y_reference", fallback: "%1$@, ayah %2$@"), name, number)
        return AyahWidgetSpeech.shared.label(
            widgetName: localized("ayah_widget_name", fallback: "Ayah of the day"),
            reference: reference,
            translation: showsTranslation ? ayah.translation : nil
        )
    }
```
and in `card(_:)` append to the `VStack`'s modifiers, after `.frame(maxWidth: .infinity, maxHeight: .infinity)`:
```swift
        // One element with one label: the Arabic, the translation and the footer are otherwise
        // five separate stops, the Arabic read by whatever voice VoiceOver has (audit pl-ayah-widget-ios).
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel(ayah))
```

- [ ] **Step 5: Build both widgets, run the suite, commit**

Run:
```bash
scripts/check-strings.sh && scripts/test.sh && ./gradlew :androidApp:assembleDebug && scripts/ios-build.sh
```
Expected: clean; `BUILD SUCCESSFUL` ×2; `xcodebuild` ends `** BUILD SUCCEEDED **` and prints the appex size line under 15 MB.
```bash
git add -A widgetcore/src androidApp/src iosApp/TaqwaWidget
git commit -m "widgets: the ayah widget tells a screen reader its reference and translation

The Android card was one bitmap marked decorative, so TalkBack had nothing but the widget's name
for a widget whose one job is today's ayah (audit pl-ayah-widget, a blocker); the iOS card read
its raw Uthmani text and 13:28 like a clock. Both now carry one sentence — the widget's name,
\"Ar-Ra’d, ayah 28\" in the interface's script and digits, then the translation when the card
shows one — built by :widgetcore so the two cannot drift, and never the Arabic, by the owner's
rule. Nothing drawn changes."
```

---

### Task 12: Device verification, build log, report

**Files:**
- Modify: `docs/BUILD-LOG.md` (append a section)
- Scratchpad only (never committed): `a11y/ax.py` (exists), an iOS accessibility-tree dumper patch, screenshots and dumps under `a11y/round1/`

**Interfaces:** none.

- [ ] **Step 1: Android on a throwaway emulator**

```bash
./gradlew :androidApp:assembleDebug
/Users/mohamedabulgasem/Library/Android/sdk/emulator/emulator -avd Pixel_8_Pro -port 5558 -no-window -gpu swiftshader_indirect -no-snapshot -read-only &
adb -s emulator-5558 wait-for-device
adb -s emulator-5558 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```
Then, with the scratchpad's `a11y/ax.py` (dump/tap/screen/shot against emulator-5558), record a `uiautomator dump` and a screenshot of: Settings root (headings do not show in dumps; check the rows), Notifications (switch rows: `checkable="true" checked=…` on a node whose text is the label, and no separate unlabelled 48 dp node), Appearance (option rows `selected=…`), the Prayer screen (each prayer row a single node with `content-desc="Fajr, 5:10"`, the ring `content-desc` a sentence), the reader on Al-Baqarah (a card node with `text` starting "Ayah 255." and no Arabic in `text`/`content-desc`), the Mushaf (page 1: nodes `content-desc="Ayah 1"`… in order; no Arabic text nodes), Quran root tabs (`selected`), and the ayah widget if placed (`content-desc` sentence — placing a widget on the emulator's launcher is optional; the label is also visible in the Glance session log). Save everything under the scratchpad `a11y/round1/android/`.

Font scale: `adb -s emulator-5558 shell settings put system font_scale 1.0` → screenshot Mushaf page 4 (Al-Baqarah); compare with a screenshot of the same page from the main build (install the previous APK from `/Users/mohamedabulgasem/Desktop/Workspace/apps/Taqwa/androidApp/build/outputs/apk/debug/` if present, else skip) — they must be pixel-identical or differ only in the status bar. Then `font_scale 2.0` → screenshot: no word cut at the left edge, no merged words. Restore `font_scale 1.0`. Kill the emulator: `adb -s emulator-5558 emu kill`.

- [ ] **Step 2: The S23 (real device, TalkBack dump)**

First tell the peer sessions (`SendMessage` to `Taqwa (Lead)` and `Taqwa (Dev 1)`): the S23 gets the `a11y-round-1` build installed for ten minutes, then its font scale is put back. Then `adb -s R5CW219W15J install -r …apk`, the same dumps and screenshots at font scale 1.0 and 2.0 (Mushaf page 4, Prayer screen, Notifications), restore `font_scale 1.0`. Save under `a11y/round1/s23/`. Do not enable TalkBack on the S23 by adb; the owner listens himself in the morning.

- [ ] **Step 2b: The listening checklist (closing review, 17 October; the merge waits for it)**

The only device pass so far (29 September) covered Tasks 1–6, and nothing has run under real
VoiceOver: the simulator cannot run it. The iOS UI tests read Compose's semantics tree, not
UIKit's or Android's mapping of it, so they cannot see what is listed below. Run it with TalkBack
on an Android phone and VoiceOver on an iPhone, the owner listening or with his OK, and note each
result in the build log.

From the tasks' own notes (ledger "for Task 12" lines):
- Task 4: VoiceOver's two-finger scrub goes back from a pushed screen and from the reader. Use the
  real gesture on an iPhone; the simulator can only reach it through a temporary, uncommitted
  override of the gate. A focused text field (Quran search, city search) may take the Escape
  first. With VoiceOver off there is no edge swipe-back, because the handler is gated on
  VoiceOver.
- Task 6: on Android a selected radio button or tab offers no double-tap (delegate :826–834), and
  no test exercises that. The selected timetable method and the chosen reciter still act on a
  double-tap. On the 17 October emulator TalkBack 16 spoke the role words ("Switch", "Radio
  button", "Tab"), the position ("2 of 3") and the state before the label, against the spec's
  first reading (§3). Confirm what TalkBack says on the owner's phone.
- Task 7: on a prayer row, the order of the label and its state ("Fajr, 5:10, Next prayer").
  FSI/PDI are silent in speech and braille at 12-hour. The ring's description changes once a
  minute and is not announced.
- Task 8: TalkBack heading navigation and the VoiceOver rotor jump between section labels.
  "Adhkar, button" and "Tasbeeh, button". The chosen time format says "Selected".
- Task 9: unselected cards do not say "Not selected". A selected, bookmarked card says
  "Selected, Bookmarked". Voice Access "tap Copy" and iOS Voice Control on the selected card's
  row. The reading order. Whether TalkBack switches voice on the translation's LocaleSpan
  (Compose does pass it on, as AndroidAccessibilitySpannableString.android.kt:186).
- Task 10: TalkBack explore-by-touch and the right-to-left order of stops under an English
  interface. VoiceOver's walk order inside each line's traversal group. Stops in landscape and on
  short frames. A second double-tap drops "Selected". The recited ayah's stop offers Pause and
  has no state.
- Task 11: TalkBack on the Android widget (the label is on the Image inside the clickable card).
  VoiceOver reads the iOS widget as one element.
- Closing wave: every sheet's first stop says "Drag handle" with Dismiss among its actions. The
  Mushaf's page number says "Page 42", and a surah band says "286 ayahs".

Added by the closing review:
- (a) A sighted touch on the drawn switch of a toggle row still flips it, and a touch on the label
  does not. ControlSemanticsTest now covers this in Compose. Confirm it on a phone with no screen
  reader running.
- (b) iOS Voice Control "Tap <name>" on a switch row that has no whole-row tap: Prayer
  notifications, Tahajjud, Use my location, Show sunrise, both Asr times, Saudi Fajr,
  transliteration, Read aloud. If Voice Control taps the element's centre rather than calling
  accessibilityActivate, nothing happens, because the drawn switch is no longer an element of its
  own. This is plausible but unverified. If it fails, the remedy is the owner's call, since any
  whole-row tap would change touch.
- (c) TalkBack with its focus on an ayah card while read-aloud moves from ayah to ayah: whether the
  card's changing state ("Being read aloud") is spoken over the phone's voice or the reciter.

- [ ] **Step 3: iOS on a dedicated simulator**

```bash
xcrun simctl create "Taqwa a11y" "iPhone 17 Pro"   # note the UDID it prints
xcrun simctl boot <udid>
IOS_DEST="platform=iOS Simulator,id=<udid>" scripts/ios-build.sh
```
Apply the dumper patch (kept in the scratchpad as `a11y/round1/ios-a11y-dump.patch`, never committed): a new file `shared/src/iosMain/kotlin/world/taqwa/app/AccessibilityDump.ios.kt` that, when `NSProcessInfo.processInfo.arguments` contains `-taqwaA11yDump`, walks `UIApplication.sharedApplication.keyWindow` every 3 s — `accessibilityElementCount()`/`accessibilityElementAtIndex()` on each container, printing depth, `accessibilityLabel`, `accessibilityValue`, `accessibilityTraits`, `accessibilityHint`, custom action names and `accessibilityFrame` — and, when the arguments contain `-taqwaA11yEscapeAfter=N`, calls `accessibilityPerformEscape()` on the first element after N seconds. `MainViewController()` calls `installAccessibilityDump()` once. Install and launch with the console attached:
```bash
xcrun simctl install <udid> build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app
xcrun simctl launch --console-pty <udid> world.taqwa.ios -taqwaA11yDump
```
Copy the main checkout's `taqwa.preferences_pb` into the sim's app container (`xcrun simctl get_app_container <udid> world.taqwa.ios data`, then `Library/Application Support/Taqwa/`) to skip onboarding, relaunch, and drive with the iOS simulator MCP tool (tap/text). Record the tree for: Settings root, Notifications (row with the ToggleButton trait, value on/off), Appearance (Selected trait on one row), the Prayer screen (row labels, ring sentence), the reader (card label "Ayah 255. …", custom actions Play/Bookmark/Copy/Share; the label's language attribute cannot be seen in the dump — note it), the Mushaf (stop labels in order), and the escape path: `xcrun simctl openurl <udid> 'taqwa://ayah/2/255'` (tap "Open" on the alert with the MCP tool) then relaunch with `-taqwaA11yEscapeAfter=8` and confirm the dump shows the Quran root after the escape. Save under `a11y/round1/ios/`. Revert the patch (`git checkout -- shared/src/iosMain/kotlin/world/taqwa/app/MainViewController.kt && rm shared/src/iosMain/kotlin/world/taqwa/app/AccessibilityDump.ios.kt`), confirm `git status` is clean, `xcrun simctl shutdown <udid> && xcrun simctl delete <udid>`.

- [ ] **Step 4: The build log**

Append to `docs/BUILD-LOG.md` a section `## Accessibility round 1 (28 September 2026), branch \`worktree-a11y-round-1\`` in the file's own voice: the owner's two rules, the five items and what each commit did, what was verified on which device with the concrete evidence (dump excerpts, the Mushaf at 200 %), what could not be verified (whatever Step 2b could not run; the language tag is honoured by VoiceOver, and Compose passes it to TalkBack as a LocaleSpan, which TalkBack may or may not voice), the seven-language strings written without a native reviewer, and what is next (items 6–12). Commit:
```bash
git add docs/BUILD-LOG.md docs/superpowers/specs/2026-09-28-taqwa-accessibility-round-1-design.md docs/superpowers/plans/2026-09-28-taqwa-accessibility-round-1.md
git commit -m "build log: accessibility round 1"
```

- [ ] **Step 5: Final checks and the report**

Run `scripts/check-strings.sh && scripts/test.sh && git status --short && git log --oneline main..HEAD`. Expected: clean, green, no uncommitted files, twelve-or-so commits. Update the memory file `taqwa-accessibility-audit.md` (what shipped on the branch, what is left). Then the report to Mohamed: the branch and its commits, per item what changed and the evidence (attach the before/after Mushaf screenshots and the key dump lines), the strings to review per language, the honest limits (what Step 2b could not cover; whether TalkBack voices the language tag), the S23 state (which build is on it, font scale restored), and the offer to merge (squashed, with a freshly written message and no Co-Authored-By, per the ledger's ruling, so that no commit on main fails to build and the fixup! commit folds) and to start items 6–12.
