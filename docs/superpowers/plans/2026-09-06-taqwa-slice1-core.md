# Taqwa Slice 1, Plan 1 of 2 — Core: Prayer Times, Today & Settings

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working Taqwa app on Android and iOS that shows correct prayer times for the user's location on a themed Today screen, with onboarding, a city fallback, and the full settings tree.

**Architecture:** One Kotlin Multiplatform module with Compose Multiplatform UI. All logic and all UI live in `commonMain`; `expect`/`actual` exists only for the DataStore path and the location provider. Prayer mathematics comes from `adhan2`, a Kotlin Multiplatform library — we wrap it in our own domain types rather than leaking its API through the app. Navigation is a sealed-class backstack in a `StateFlow`, not a library.

**Tech Stack:** Kotlin 2.2.20, Compose Multiplatform 1.12.0, adhan2 0.0.7, kotlinx-datetime 0.7.1, kotlinx-coroutines, AndroidX DataStore Preferences 1.1.7, Gradle version catalogs.

**Out of scope for this plan** (covered by Plan 2): notifications and scheduling, the qibla compass screen, home screen widgets, RTL and localisation.

## Global Constraints

Every task's requirements implicitly include these. Values are copied verbatim from the spec.

- **Package identifier:** `world.taqwa.app`
- **minSdk 26**, targetSdk latest stable, **iOS 16.0+**
- **Light palette:** bg `#FBFAF7`, surface `#FFFFFF`, hairline `#E7E5DD`, text primary `#16160F`, text secondary `#6F6E62`, text tertiary `#A5A498`, accent `#B5820B`, ring `#E3A21C`
- **Dark palette:** bg `#0B0D0C`, surface `#131614`, hairline `#232825`, text primary `#F1F3F1`, text secondary `#8C948F`, text tertiary `#5A625D`, accent `#F0B429`, ring `#F0B429`
- **Amber is the only colour.** No success green, no error red, no second accent. Errors and warnings use the accent, weight and plain language.
- **Latin type:** Manrope, bundled. **Arabic type:** system default, never bundled.
- **Tabular figures** on every displayed time.
- **All tap targets ≥44pt.** Buttons are full-width pills, ~46pt tall; the secondary action is a plain centred text link, never a second button.
- **Selection mark** is a drawn vector check (stroke 2.6, round caps), never the `✓` character.
- **Cards:** radius 18dp, 1px hairline border, no shadow.
- **No network calls anywhere in slice 1.** Nothing contacts a server.
- Every pure function lives in `commonMain` and is tested without a device.

---

## File Structure

```
gradle/libs.versions.toml                     version catalog — single source of dependency truth
settings.gradle.kts                           module registration
build.gradle.kts                              root plugin declarations
shared/build.gradle.kts                   KMP targets, source sets, Android config

shared/src/commonMain/kotlin/world/taqwa/app/
  App.kt                                      root composable; wires container + backstack
  di/AppContainer.kt                          manual dependency graph
  nav/Screen.kt                               sealed navigation destinations
  nav/Navigator.kt                            StateFlow backstack

  design/Palette.kt                           raw colour tokens, light and dark
  design/TaqwaTheme.kt                        MaterialTheme wrapper + LocalTaqwaColors
  design/Type.kt                              Manrope font family + text styles
  design/components/TaqwaButton.kt            pill button + secondary text link
  design/components/TaqwaCard.kt              hairline card + row + divider
  design/components/CheckMark.kt              drawn vector check
  design/components/CountdownRing.kt          the ring motif

  domain/Prayer.kt                            Prayer enum, PrayerTime, DayPrayerTimes
  domain/GeoLocation.kt                       coordinates + timezone + display name
  domain/PrayerSettings.kt                    calculation settings value type
  domain/TimelineState.kt                     PrayerStatus, TimelineRow, TodayState

  prayer/PrayerTimesEngine.kt                 wraps adhan2; our types in, our types out
  prayer/HighLatitudeSelector.kt              auto rule selection by latitude
  prayer/TimelineBuilder.kt                   pure (times, now) -> TodayState
  prayer/CalculationMethodDefaults.kt         country -> method mapping

  hijri/UmmAlQuraCalendar.kt                  Gregorian <-> Hijri conversion
  hijri/HijriFormatter.kt                     display formatting + user offset

  city/City.kt                                city record
  city/CityRepository.kt                      loads and searches the bundled database

  location/LocationProvider.kt                expect declaration + permission state
  location/LocationRepository.kt              resolution policy, caching, 5km rule

  settings/SettingsKeys.kt                    DataStore keys
  settings/SettingsRepository.kt              typed flows + writers
  settings/DataStoreFactory.kt                expect declaration

  feature/today/TodayViewModel.kt             state + clock tick
  feature/today/TodayScreen.kt                ring + timeline
  feature/today/TodayEmptyStates.kt           location denied, high latitude note
  feature/onboarding/OnboardingScreen.kt      three-step flow
  feature/settings/SettingsRootScreen.kt
  feature/settings/PrayerTimesSettingsScreen.kt
  feature/settings/LocationSettingsScreen.kt
  feature/settings/CitySearchScreen.kt
  feature/settings/AppearanceSettingsScreen.kt

shared/src/commonMain/composeResources/
  font/Manrope-{Light,Regular,SemiBold,ExtraBold}.ttf
  files/cities.csv                            generated, bundled

shared/src/androidMain/kotlin/world/taqwa/app/
  MainActivity.kt
  settings/DataStoreFactory.android.kt
  location/LocationProvider.android.kt

shared/src/iosMain/kotlin/world/taqwa/app/
  MainViewController.kt
  settings/DataStoreFactory.ios.kt
  location/LocationProvider.ios.kt

shared/src/commonTest/kotlin/world/taqwa/app/     mirrors the above
iosApp/                                                Xcode project
tools/build-city-db.py                                 GeoNames -> cities.csv
```

---

### Task 1: Project scaffold that builds and runs on both platforms

Nothing is testable until both platforms build, so scaffolding is one task ending in a running app.

**Files:**
- Create: `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- Create: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`
- Create: `androidApp/src/androidMain/kotlin/world/taqwa/app/MainActivity.kt`
- Create: `androidApp/src/androidMain/AndroidManifest.xml`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/MainViewController.kt`
- Create: `iosApp/` Xcode project
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/SmokeTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `App()` composable entry point; Gradle modules `:shared` and `:androidApp`; version catalog aliases used by every later task

- [ ] **Step 1: Start from the JetBrains template and upgrade it**

The kmp.jetbrains.com wizard endpoint returns 404. Use JetBrains' maintained template instead —
it ships a working Xcode project and Gradle wrapper, which are the parts that are painful to
hand-roll:

```bash
git clone --depth 1 https://github.com/JetBrains/compose-multiplatform-ios-android-template /tmp/kmp-tmpl
cp -R /tmp/kmp-tmpl/{shared,androidApp,iosApp,gradle,gradlew,gradlew.bat,build.gradle.kts,settings.gradle.kts,gradle.properties} .
rm -rf /tmp/kmp-tmpl
```

The template pins Kotlin 1.9.21, which **cannot consume adhan2** — a library compiled with
Kotlin 2.x metadata is unreadable by a 1.9 compiler. Upgrading is mandatory, not cosmetic.

Module layout is the template's three-module split, and the plan's paths assume it:
`shared/` (the KMP module: all logic, all UI, all tests), `androidApp/` (the Android host and
`MainActivity`), `iosApp/` (the Xcode project). Do **not** rename `shared` — the Xcode build
phase invokes `:shared:embedAndSignAppleFrameworkForXcode` by name, and renaming means editing
`project.pbxproj`.

Set `rootProject.name = "Taqwa"` in `settings.gradle.kts`. Set both namespaces to
`world.taqwa.app` (use `world.taqwa.app.shared` for the library module) and `applicationId` to
`world.taqwa.app`.

In `iosApp/Configuration/Config.xcconfig` set:

```
TEAM_ID=36383TYK26
BUNDLE_ID=world.taqwa.app
APP_NAME=Taqwa
```

- [ ] **Step 1b: Upgrade the toolchain**

In `gradle.properties`, replace the version block:

```properties
#Versions
kotlin.version=2.2.20
agp.version=8.7.3
compose.version=1.12.0

#Android
android.useAndroidX=true
android.targetSdk=35
android.compileSdk=35
android.minSdk=26
```

Bump the wrapper: `./gradlew wrapper --gradle-version 8.11.1`

Kotlin 2.0 and later require the Compose compiler plugin to be applied explicitly. Add to
`settings.gradle.kts` `pluginManagement.plugins`:

```kotlin
id("org.jetbrains.kotlin.plugin.compose").version(kotlinVersion)
```

and apply `id("org.jetbrains.kotlin.plugin.compose")` in both `shared/build.gradle.kts` and
`androidApp/build.gradle.kts`. Without it the build fails with "Compose Compiler plugin not
found", which is the single most likely failure in this task.

Also pin the resource package so generated imports are deterministic — add to
`shared/build.gradle.kts`:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "world.taqwa.app.resources"
}
```

Every later task imports `world.taqwa.app.resources.Res`.

- [ ] **Step 2: Pin the version catalog**

Replace `gradle/libs.versions.toml` with:

```toml
[versions]
kotlin = "2.2.20"
compose-multiplatform = "1.12.0"
agp = "8.7.3"
android-minSdk = "26"
android-compileSdk = "35"
android-targetSdk = "35"
adhan = "0.0.7"
kotlinx-datetime = "0.7.1"
kotlinx-coroutines = "1.10.2"
datastore = "1.1.7"
androidx-activity = "1.9.3"

[libraries]
adhan2 = { module = "com.batoulapps.adhan:adhan2", version.ref = "adhan" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

> If Gradle fails to resolve any of these, run `./gradlew :shared:dependencies --configuration commonMainCompileClasspath` and bump the failing version to the newest release on Maven Central. The catalog is the single place to change it.

- [ ] **Step 3: Declare dependencies in the shared source set**

In `shared/build.gradle.kts`, inside `kotlin { sourceSets { ... } }`:

```kotlin
commonMain.dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(libs.adhan2)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)
}
commonTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.test)
}
androidMain.dependencies {
    implementation(libs.androidx.activity.compose)
}
```

- [ ] **Step 4: Write the smoke test**

`shared/src/commonTest/kotlin/world/taqwa/app/SmokeTest.kt`:

```kotlin
package world.taqwa.app

import com.batoulapps.adhan2.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun adhanLibraryIsOnTheClasspath() {
        val makkah = Coordinates(21.4225, 39.8262)
        assertEquals(21.4225, makkah.latitude)
    }
}
```

This proves the multiplatform dependency actually resolves for both targets before any real code depends on it.

- [ ] **Step 5: Run the test on both targets**

Run: `./gradlew :shared:allTests`
Expected: PASS. If `Coordinates` cannot be resolved, the adhan2 artifact did not publish for one of your targets — check `./gradlew :shared:dependencies` before going further.

- [ ] **Step 6: Replace App.kt with a placeholder that proves rendering**

```kotlin
package world.taqwa.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Taqwa")
            }
        }
    }
}
```

- [ ] **Step 7: Build and run on both platforms**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Then open `iosApp/iosApp.xcodeproj` in Xcode and run on an iOS 16+ simulator. Expected: the word "Taqwa" centred on screen.

Both must work before continuing. A broken iOS build discovered at Task 12 costs a day.

- [ ] **Step 8: Commit**

```bash
git add gradle settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat shared androidApp iosApp
git commit -m "feat: KMP + Compose Multiplatform scaffold building on Android and iOS"
```

---

### Task 2: Colour tokens and theming

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/Palette.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/TaqwaTheme.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/design/PaletteTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `TaqwaColors` data class with properties `background`, `surface`, `hairline`, `textPrimary`, `textSecondary`, `textTertiary`, `accent`, `ring`; `LightColors`, `DarkColors`; `ThemeMode` enum `SYSTEM`/`LIGHT`/`DARK`; `TaqwaTheme(mode, content)` composable; `LocalTaqwaColors` composition local

- [ ] **Step 1: Write the failing test**

`PaletteTest.kt`:

```kotlin
package world.taqwa.app.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PaletteTest {
    @Test
    fun lightAccentIsDeepenedForContrastOnPaper() {
        assertEquals(Color(0xFFB5820B), LightColors.accent)
    }

    @Test
    fun darkAccentIsFullStrength() {
        assertEquals(Color(0xFFF0B429), DarkColors.accent)
    }

    @Test
    fun accentDiffersBetweenModes() {
        assertNotEquals(LightColors.accent, DarkColors.accent)
    }

    @Test
    fun darkIsNotAnInvertedLightMode() {
        assertEquals(Color(0xFF0B0D0C), DarkColors.background)
        assertEquals(Color(0xFF131614), DarkColors.surface)
        assertNotEquals(DarkColors.background, DarkColors.surface)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*PaletteTest*"`
Expected: FAIL — `Unresolved reference: LightColors`

- [ ] **Step 3: Write Palette.kt**

```kotlin
package world.taqwa.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TaqwaColors(
    val background: Color,
    val surface: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val ring: Color,
)

val LightColors = TaqwaColors(
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFFFFFF),
    hairline = Color(0xFFE7E5DD),
    textPrimary = Color(0xFF16160F),
    textSecondary = Color(0xFF6F6E62),
    textTertiary = Color(0xFFA5A498),
    accent = Color(0xFFB5820B),
    ring = Color(0xFFE3A21C),
)

val DarkColors = TaqwaColors(
    background = Color(0xFF0B0D0C),
    surface = Color(0xFF131614),
    hairline = Color(0xFF232825),
    textPrimary = Color(0xFFF1F3F1),
    textSecondary = Color(0xFF8C948F),
    textTertiary = Color(0xFF5A625D),
    accent = Color(0xFFF0B429),
    ring = Color(0xFFF0B429),
)
```

- [ ] **Step 4: Write TaqwaTheme.kt**

```kotlin
package world.taqwa.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalTaqwaColors = staticCompositionLocalOf { LightColors }

@Composable
fun TaqwaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalTaqwaColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    background = colors.background,
                    surface = colors.surface,
                    onBackground = colors.textPrimary,
                    onSurface = colors.textPrimary,
                    primary = colors.accent,
                )
            } else {
                lightColorScheme(
                    background = colors.background,
                    surface = colors.surface,
                    onBackground = colors.textPrimary,
                    onSurface = colors.textPrimary,
                    primary = colors.accent,
                )
            },
            typography = TaqwaTypography(),
            content = content,
        )
    }
}
```

> `TaqwaTypography()` does not exist yet — Task 3 creates it. Until then, temporarily pass `MaterialTheme.typography` and revert in Task 3. Note this in the commit message so it is not forgotten.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :shared:allTests --tests "*PaletteTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/design shared/src/commonTest/kotlin/world/taqwa/app/design
git commit -m "feat: colour tokens and light/dark theming"
```

---

### Task 3: Typography — Manrope and the text scale

**Files:**
- Create: `shared/src/commonMain/composeResources/font/Manrope-Light.ttf`, `-Regular.ttf`, `-SemiBold.ttf`, `-ExtraBold.ttf`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/Type.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/design/TaqwaTheme.kt` — restore `TaqwaTypography()`

**Interfaces:**
- Consumes: `TaqwaColors` from Task 2
- Produces: `@Composable fun manropeFamily(): FontFamily`; `@Composable fun TaqwaTypography(): Typography`; `TaqwaText` object exposing `countdown`, `screenTitle`, `rowLabel`, `rowTime`, `sectionLabel`, `caption` text styles

- [ ] **Step 1: Add the font files**

Download Manrope from Google Fonts (OFL) and place the four static weights under `composeResources/font/`. Use static weights rather than the variable font — Compose Multiplatform's variable font support differs across targets and this is not the place to discover that.

Record the licence:

```bash
mkdir -p shared/src/commonMain/composeResources/font
curl -sL -o /tmp/manrope.zip "https://fonts.google.com/download?family=Manrope"
# unzip the four static weights into composeResources/font/
```

- [ ] **Step 2: Write Type.kt**

```kotlin
package world.taqwa.app.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import world.taqwa.app.resources.Manrope_ExtraBold
import world.taqwa.app.resources.Manrope_Light
import world.taqwa.app.resources.Manrope_Regular
import world.taqwa.app.resources.Manrope_SemiBold
import world.taqwa.app.resources.Res

@Composable
fun manropeFamily(): FontFamily = FontFamily(
    Font(Res.font.Manrope_Light, FontWeight.Light),
    Font(Res.font.Manrope_Regular, FontWeight.Normal),
    Font(Res.font.Manrope_SemiBold, FontWeight.SemiBold),
    Font(Res.font.Manrope_ExtraBold, FontWeight.ExtraBold),
)

object TaqwaText {
    val countdown = TextStyle(fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.02).em)
    val screenTitle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.02).em)
    val rowLabel = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val rowTime = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp)
    val sectionLabel = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.14.em)
    val caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp)
}

@Composable
fun TaqwaTypography(): Typography {
    val family = manropeFamily()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
```

Add the missing import for `em`: `androidx.compose.ui.unit.em`.

**Arabic never uses this family.** Any composable rendering an Arabic string passes `fontFamily = FontFamily.Default` so the OS face is used. Task 11 applies this in the timeline.

- [ ] **Step 3: Restore TaqwaTypography in the theme**

In `TaqwaTheme.kt`, change `typography = MaterialTheme.typography` back to `typography = TaqwaTypography()`.

- [ ] **Step 4: Verify tabular figures render without jitter**

Add `shared/src/commonTest/kotlin/world/taqwa/app/design/TypeTest.kt`:

```kotlin
package world.taqwa.app.design

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeTest {
    @Test
    fun countdownUsesLightWeightForTheBigNumeral() {
        assertEquals(FontWeight.Light, TaqwaText.countdown.fontWeight)
    }

    @Test
    fun rowLabelAndRowTimeShareASize() {
        assertEquals(TaqwaText.rowLabel.fontSize, TaqwaText.rowTime.fontSize)
    }
}
```

Run: `./gradlew :shared:allTests --tests "*TypeTest*"` — Expected: PASS.

Tabular alignment itself cannot be unit-tested; verify it visually in Task 11 by watching the countdown tick from `1:12` to `1:11` and confirming nothing shifts horizontally.

- [ ] **Step 5: Build both platforms**

Run: `./gradlew :androidApp:assembleDebug` — Expected: BUILD SUCCESSFUL. Then run the iOS app in Xcode and confirm "Taqwa" now renders in Manrope rather than the system font. If the font does not apply on iOS, the generated `Res.font` accessor name does not match the filename — check `build/generated/compose/resourceGenerator`.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/composeResources shared/src/commonMain/kotlin/world/taqwa/app/design shared/src/commonTest/kotlin/world/taqwa/app/design
git commit -m "feat: bundle Manrope and define the type scale"
```

---

### Task 4: Settings storage

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/settings/DataStoreFactory.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/settings/DataStoreFactory.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/settings/DataStoreFactory.ios.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsKeys.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/settings/SettingsRepository.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/domain/Prayer.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/domain/PrayerSettings.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `ThemeMode` from Task 2
- Produces:
  - `enum class AsrMadhab { STANDARD, HANAFI }`
  - `enum class HighLatitudePreference { AUTOMATIC, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE }`
  - `enum class CalculationMethodId { MUSLIM_WORLD_LEAGUE, ISNA, EGYPTIAN, UMM_AL_QURA, KARACHI, TEHRAN, DUBAI, KUWAIT, QATAR, SINGAPORE, TURKEY, MOONSIGHTING_COMMITTEE }`
  - `data class PrayerSettings(method, madhab, highLatitude, hijriOffsetDays, showSunrise, minuteAdjustments)`
  - `class SettingsRepository(store: DataStore<Preferences>)` exposing `val themeMode: Flow<ThemeMode>`, `val prayerSettings: Flow<PrayerSettings>`, `val onboardingComplete: Flow<Boolean>`, and `suspend fun setThemeMode(ThemeMode)`, `setPrayerSettings(PrayerSettings)`, `setOnboardingComplete(Boolean)`
  - `expect fun dataStoreDirectory(): String` and `fun createDataStore(): DataStore<Preferences>`

- [ ] **Step 1: Write the failing test**

```kotlin
package world.taqwa.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {

    private fun repo(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.createWithPath { "build/test-$name.preferences_pb".toPath() }
    )

    @Test
    fun themeModeDefaultsToSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, repo("theme-default").themeMode.first())
    }

    @Test
    fun themeModeRoundTrips() = runTest {
        val r = repo("theme-roundtrip")
        r.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, r.themeMode.first())
    }

    @Test
    fun remindBeforeDefaultsToNever() = runTest {
        assertEquals(0, repo("remind").prayerSettings.first().remindBeforeMinutes)
    }

    @Test
    fun madhabDefaultsToStandard() = runTest {
        assertEquals(AsrMadhab.STANDARD, repo("madhab").prayerSettings.first().madhab)
    }

    @Test
    fun showSunriseDefaultsToOff() = runTest {
        assertEquals(false, repo("sunrise").prayerSettings.first().showSunrise)
    }

    @Test
    fun unknownStoredValueFallsBackToDefaultRatherThanCrashing() = runTest {
        val r = repo("corrupt")
        r.setThemeMode(ThemeMode.DARK)
        // simulate a value written by a future version
        r.writeRawThemeForTest("PLAID")
        assertEquals(ThemeMode.SYSTEM, r.themeMode.first())
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*SettingsRepositoryTest*"`
Expected: FAIL — `Unresolved reference: SettingsRepository`

- [ ] **Step 3: Write the domain settings types**

`PrayerSettings` refers to `Prayer`, so the enum is defined here. Task 5 adds `PrayerTime` and
`DayPrayerTimes` to the same file.

`domain/Prayer.kt`:

```kotlin
package world.taqwa.app.domain

enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/** The five that are prayed. Sunrise marks the end of the Fajr window and is never notified. */
val ObligatoryPrayers = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
```

`domain/PrayerSettings.kt`:

```kotlin
package world.taqwa.app.domain

enum class AsrMadhab { STANDARD, HANAFI }

enum class HighLatitudePreference { AUTOMATIC, MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE }

enum class CalculationMethodId {
    MUSLIM_WORLD_LEAGUE, ISNA, EGYPTIAN, UMM_AL_QURA, KARACHI, TEHRAN,
    DUBAI, KUWAIT, QATAR, SINGAPORE, TURKEY, MOONSIGHTING_COMMITTEE
}

data class PrayerSettings(
    val method: CalculationMethodId = CalculationMethodId.MUSLIM_WORLD_LEAGUE,
    val madhab: AsrMadhab = AsrMadhab.STANDARD,
    val highLatitude: HighLatitudePreference = HighLatitudePreference.AUTOMATIC,
    val hijriOffsetDays: Int = 0,
    val showSunrise: Boolean = false,
    val remindBeforeMinutes: Int = 0,
    val minuteAdjustments: Map<Prayer, Int> = emptyMap(),
)
```

- [ ] **Step 4: Write the expect/actual DataStore factory**

`commonMain/settings/DataStoreFactory.kt`:

```kotlin
package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

internal const val SETTINGS_FILE = "taqwa.preferences_pb"

expect fun dataStoreDirectory(): String

fun createDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { "${dataStoreDirectory()}/$SETTINGS_FILE".toPath() }
```

`androidMain/settings/DataStoreFactory.android.kt`:

```kotlin
package world.taqwa.app.settings

import android.content.Context

lateinit var appContext: Context

actual fun dataStoreDirectory(): String = appContext.filesDir.path
```

`iosMain/settings/DataStoreFactory.ios.kt`:

```kotlin
package world.taqwa.app.settings

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun dataStoreDirectory(): String {
    val url: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )!!
    return requireNotNull(url.path)
}
```

In `MainActivity.kt`, set `appContext = applicationContext` before calling `App()`.

- [ ] **Step 5: Write the repository**

`settings/SettingsRepository.kt`:

```kotlin
package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import world.taqwa.app.design.ThemeMode
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.PrayerSettings

private object Keys {
    val THEME = stringPreferencesKey("theme_mode")
    val METHOD = stringPreferencesKey("calculation_method")
    val MADHAB = stringPreferencesKey("asr_madhab")
    val HIGH_LAT = stringPreferencesKey("high_latitude")
    val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
    val SHOW_SUNRISE = booleanPreferencesKey("show_sunrise")
    val REMIND_BEFORE = intPreferencesKey("remind_before_minutes")
    val ONBOARDED = booleanPreferencesKey("onboarding_complete")
}

/** Reads a stored enum name, falling back to [fallback] when the value is absent or unrecognised. */
private inline fun <reified E : Enum<E>> String?.toEnumOr(fallback: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

class SettingsRepository(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> =
        store.data.map { it[Keys.THEME].toEnumOr(ThemeMode.SYSTEM) }

    val onboardingComplete: Flow<Boolean> =
        store.data.map { it[Keys.ONBOARDED] ?: false }

    val prayerSettings: Flow<PrayerSettings> = store.data.map { p ->
        PrayerSettings(
            method = p[Keys.METHOD].toEnumOr(CalculationMethodId.MUSLIM_WORLD_LEAGUE),
            madhab = p[Keys.MADHAB].toEnumOr(AsrMadhab.STANDARD),
            highLatitude = p[Keys.HIGH_LAT].toEnumOr(HighLatitudePreference.AUTOMATIC),
            hijriOffsetDays = p[Keys.HIJRI_OFFSET] ?: 0,
            showSunrise = p[Keys.SHOW_SUNRISE] ?: false,
            remindBeforeMinutes = p[Keys.REMIND_BEFORE] ?: 0,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[Keys.ONBOARDED] = value }
    }

    suspend fun setPrayerSettings(settings: PrayerSettings) {
        store.edit {
            it[Keys.METHOD] = settings.method.name
            it[Keys.MADHAB] = settings.madhab.name
            it[Keys.HIGH_LAT] = settings.highLatitude.name
            it[Keys.HIJRI_OFFSET] = settings.hijriOffsetDays
            it[Keys.SHOW_SUNRISE] = settings.showSunrise
            it[Keys.REMIND_BEFORE] = settings.remindBeforeMinutes
        }
    }

    /** Test-only hook for the forward-compatibility case. */
    internal suspend fun writeRawThemeForTest(raw: String) {
        store.edit { it[Keys.THEME] = raw }
    }
}
```

The `toEnumOr` helper is the point of the last test: a settings file written by a newer version must never crash an older build.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :shared:allTests --tests "*SettingsRepositoryTest*"`
Expected: PASS, 6 tests. The defaults asserted here are exactly the spec's defaults table.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/settings shared/src/commonMain/kotlin/world/taqwa/app/domain shared/src/androidMain shared/src/iosMain shared/src/commonTest
git commit -m "feat: multiplatform settings storage with spec defaults"
```

---

### Task 5: Prayer times engine

**Files:**
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/domain/Prayer.kt` (append to Task 4's file)
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/domain/GeoLocation.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/prayer/PrayerTimesEngine.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/prayer/PrayerTimesEngineTest.kt`

**Interfaces:**
- Consumes: `PrayerSettings`, `AsrMadhab`, `CalculationMethodId`, `HighLatitudePreference` from Task 4
- Produces:
  - `data class PrayerTime(val prayer: Prayer, val instant: Instant)` (`Prayer` itself comes from Task 4)
  - `data class DayPrayerTimes(val date: LocalDate, val times: List<PrayerTime>, val highLatitudeRuleApplied: HighLatitudePreference?)` with `fun time(p: Prayer): Instant`
  - `data class GeoLocation(val latitude: Double, val longitude: Double, val timeZoneId: String, val cityName: String?, val countryCode: String?)`
  - `class PrayerTimesEngine` with `fun timesFor(location: GeoLocation, date: LocalDate, settings: PrayerSettings): DayPrayerTimes`

- [ ] **Step 1: Probe the adhan2 API shape before writing against it**

The published API surface is documented but the exact return types matter. Write `PrayerTimesEngineTest.kt` with only this test first:

```kotlin
package world.taqwa.app.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlin.test.Test
import kotlin.test.assertTrue

class AdhanApiProbeTest {
    @Test
    fun adhanReturnsOrderedInstantsForLondon() {
        val times = PrayerTimes(
            coordinates = Coordinates(51.5074, -0.1278),
            dateComponents = DateComponents(2026, 9, 6),
            calculationParameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters,
        )
        assertTrue(times.fajr < times.sunrise)
        assertTrue(times.sunrise < times.dhuhr)
        assertTrue(times.dhuhr < times.asr)
        assertTrue(times.asr < times.maghrib)
        assertTrue(times.maghrib < times.isha)
    }
}
```

- [ ] **Step 2: Run the probe**

Run: `./gradlew :shared:allTests --tests "*AdhanApiProbeTest*"`

Expected: PASS. If it fails to compile, the property or constructor names differ in 0.0.7 — open the resolved sources (`./gradlew :shared:dependencies` then inspect the artifact, or use the IDE's decompiler) and correct the names here and everywhere below **before** continuing. Do not guess.

- [ ] **Step 3: Write the domain types**

Append to `domain/Prayer.kt` — the enum and `ObligatoryPrayers` already exist from Task 4:

```kotlin
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class PrayerTime(val prayer: Prayer, val instant: Instant)

data class DayPrayerTimes(
    val date: LocalDate,
    val times: List<PrayerTime>,
    val highLatitudeRuleApplied: HighLatitudePreference?,
) {
    fun time(p: Prayer): Instant =
        times.firstOrNull { it.prayer == p }?.instant
            ?: error("No time computed for $p on $date")
}
```

`domain/GeoLocation.kt`:

```kotlin
package world.taqwa.app.domain

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val cityName: String? = null,
    val countryCode: String? = null,
)
```

- [ ] **Step 4: Write the engine**

`prayer/PrayerTimesEngine.kt`:

```kotlin
package world.taqwa.app.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.CalculationMethodId
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import world.taqwa.app.domain.PrayerTime
import kotlin.time.Duration.Companion.minutes

class PrayerTimesEngine {

    fun timesFor(location: GeoLocation, date: LocalDate, settings: PrayerSettings): DayPrayerTimes {
        val effectiveRule = HighLatitudeSelector.select(settings.highLatitude, location.latitude)

        val params = settings.method.toAdhan().parameters.copy(
            madhab = when (settings.madhab) {
                AsrMadhab.STANDARD -> Madhab.SHAFI
                AsrMadhab.HANAFI -> Madhab.HANAFI
            },
            highLatitudeRule = effectiveRule.toAdhan(),
        )

        val computed = PrayerTimes(
            coordinates = Coordinates(location.latitude, location.longitude),
            dateComponents = DateComponents(date.year, date.monthNumber, date.dayOfMonth),
            calculationParameters = params,
        )

        fun adjusted(p: Prayer, base: kotlinx.datetime.Instant) =
            PrayerTime(p, base + (settings.minuteAdjustments[p] ?: 0).minutes)

        return DayPrayerTimes(
            date = date,
            times = listOf(
                adjusted(Prayer.FAJR, computed.fajr),
                adjusted(Prayer.SUNRISE, computed.sunrise),
                adjusted(Prayer.DHUHR, computed.dhuhr),
                adjusted(Prayer.ASR, computed.asr),
                adjusted(Prayer.MAGHRIB, computed.maghrib),
                adjusted(Prayer.ISHA, computed.isha),
            ),
            highLatitudeRuleApplied =
                if (settings.highLatitude == HighLatitudePreference.AUTOMATIC &&
                    effectiveRule != HighLatitudePreference.MIDDLE_OF_NIGHT
                ) effectiveRule else null,
        )
    }
}

private fun CalculationMethodId.toAdhan(): CalculationMethod = when (this) {
    CalculationMethodId.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
    CalculationMethodId.ISNA -> CalculationMethod.NORTH_AMERICA
    CalculationMethodId.EGYPTIAN -> CalculationMethod.EGYPTIAN
    CalculationMethodId.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
    CalculationMethodId.KARACHI -> CalculationMethod.KARACHI
    CalculationMethodId.TEHRAN -> CalculationMethod.TEHRAN
    CalculationMethodId.DUBAI -> CalculationMethod.DUBAI
    CalculationMethodId.KUWAIT -> CalculationMethod.KUWAIT
    CalculationMethodId.QATAR -> CalculationMethod.QATAR
    CalculationMethodId.SINGAPORE -> CalculationMethod.SINGAPORE
    CalculationMethodId.TURKEY -> CalculationMethod.TURKEY
    CalculationMethodId.MOONSIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE
}

private fun HighLatitudePreference.toAdhan(): HighLatitudeRule = when (this) {
    HighLatitudePreference.SEVENTH_OF_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
    HighLatitudePreference.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
    else -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
}
```

> The adhan2 enum constant names in `toAdhan()` are the likely spellings but are not verified. The probe in Step 2 compiles this file; fix any name mismatch there rather than guessing twice.

- [ ] **Step 5: Add the golden-value tests**

Append to `PrayerTimesEngineTest.kt`:

```kotlin
package world.taqwa.app.prayer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import world.taqwa.app.domain.AsrMadhab
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerTimesEngineTest {

    private val engine = PrayerTimesEngine()
    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")

    private fun localHourMinute(instant: kotlinx.datetime.Instant, zone: String): Pair<Int, Int> {
        val t = instant.toLocalDateTime(TimeZone.of(zone))
        return t.hour to t.minute
    }

    @Test
    fun timesAreStrictlyOrderedThroughTheDay() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        val instants = d.times.map { it.instant }
        assertEquals(instants.sorted(), instants)
    }

    @Test
    fun dhuhrIsNearSolarNoonInLondon() {
        val d = engine.timesFor(london, LocalDate(2026, 9, 6), PrayerSettings())
        val (h, _) = localHourMinute(d.time(Prayer.DHUHR), "Europe/London")
        assertTrue(h in 12..13, "Dhuhr fell at hour $h, expected 12 or 13 local")
    }

    @Test
    fun hanafiAsrIsLaterThanStandardAsr() {
        val date = LocalDate(2026, 9, 6)
        val standard = engine.timesFor(london, date, PrayerSettings(madhab = AsrMadhab.STANDARD))
        val hanafi = engine.timesFor(london, date, PrayerSettings(madhab = AsrMadhab.HANAFI))
        assertTrue(hanafi.time(Prayer.ASR) > standard.time(Prayer.ASR))
    }

    @Test
    fun minuteAdjustmentsShiftOnlyTheNamedPrayer() {
        val date = LocalDate(2026, 9, 6)
        val base = engine.timesFor(london, date, PrayerSettings())
        val shifted = engine.timesFor(
            london, date, PrayerSettings(minuteAdjustments = mapOf(Prayer.FAJR to 5)),
        )
        assertEquals(base.time(Prayer.FAJR).epochSeconds + 300, shifted.time(Prayer.FAJR).epochSeconds)
        assertEquals(base.time(Prayer.ISHA), shifted.time(Prayer.ISHA))
    }

    @Test
    fun southernHemisphereAndDateLineDoNotBreakOrdering() {
        val auckland = GeoLocation(-36.8485, 174.7633, "Pacific/Auckland", "Auckland", "NZ")
        val d = engine.timesFor(auckland, LocalDate(2026, 1, 15), PrayerSettings())
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
    }
}
```

`hanafiAsrIsLaterThanStandardAsr` is the highest-value test here: it is the one assertion that fails loudly if the madhab mapping is inverted, which is an easy and invisible mistake.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :shared:allTests --tests "*PrayerTimes*"`
Expected: PASS, 6 tests including the probe.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/prayer shared/src/commonMain/kotlin/world/taqwa/app/domain shared/src/commonTest/kotlin/world/taqwa/app/prayer
git commit -m "feat: prayer times engine wrapping adhan2"
```

---

### Task 6: High-latitude rule selection

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/prayer/HighLatitudeSelector.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/prayer/HighLatitudeSelectorTest.kt`

**Interfaces:**
- Consumes: `HighLatitudePreference` from Task 4
- Produces: `object HighLatitudeSelector { fun select(preference: HighLatitudePreference, latitude: Double): HighLatitudePreference }` — returns a concrete rule, never `AUTOMATIC`

- [ ] **Step 1: Write the failing test**

```kotlin
package world.taqwa.app.prayer

import world.taqwa.app.domain.HighLatitudePreference
import kotlin.test.Test
import kotlin.test.assertEquals

class HighLatitudeSelectorTest {

    @Test
    fun explicitChoiceIsAlwaysHonoured() {
        assertEquals(
            HighLatitudePreference.TWILIGHT_ANGLE,
            HighLatitudeSelector.select(HighLatitudePreference.TWILIGHT_ANGLE, 71.0),
        )
    }

    @Test
    fun automaticUsesMiddleOfNightInTheTropics() {
        assertEquals(
            HighLatitudePreference.MIDDLE_OF_NIGHT,
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 21.4),
        )
    }

    @Test
    fun automaticEngagesSeventhRuleAtLondonLatitude() {
        assertEquals(
            HighLatitudePreference.SEVENTH_OF_NIGHT,
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 51.5),
        )
    }

    @Test
    fun automaticUsesTwilightAngleAtTromso() {
        assertEquals(
            HighLatitudePreference.TWILIGHT_ANGLE,
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 69.65),
        )
    }

    @Test
    fun southernLatitudesMirrorNorthern() {
        assertEquals(
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, 55.0),
            HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, -55.0),
        )
    }

    @Test
    fun selectNeverReturnsAutomatic() {
        (-90..90 step 5).forEach { lat ->
            val r = HighLatitudeSelector.select(HighLatitudePreference.AUTOMATIC, lat.toDouble())
            kotlin.test.assertNotEquals(HighLatitudePreference.AUTOMATIC, r)
        }
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*HighLatitudeSelectorTest*"`
Expected: FAIL — `Unresolved reference: HighLatitudeSelector`

- [ ] **Step 3: Implement**

```kotlin
package world.taqwa.app.prayer

import world.taqwa.app.domain.HighLatitudePreference
import kotlin.math.abs

/**
 * Above roughly 48 degrees the sun stops dropping far enough below the horizon for true
 * Fajr and Isha, so a substitution rule is required. Thresholds follow common practice:
 * seventh-of-the-night from 48 degrees, twilight angle from 65 where nights are shortest.
 */
object HighLatitudeSelector {

    private const val SEVENTH_THRESHOLD = 48.0
    private const val TWILIGHT_THRESHOLD = 65.0

    fun select(preference: HighLatitudePreference, latitude: Double): HighLatitudePreference {
        if (preference != HighLatitudePreference.AUTOMATIC) return preference
        val lat = abs(latitude)
        return when {
            lat >= TWILIGHT_THRESHOLD -> HighLatitudePreference.TWILIGHT_ANGLE
            lat >= SEVENTH_THRESHOLD -> HighLatitudePreference.SEVENTH_OF_NIGHT
            else -> HighLatitudePreference.MIDDLE_OF_NIGHT
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:allTests --tests "*HighLatitudeSelectorTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 5: Add the Tromsø integration test**

Append to `PrayerTimesEngineTest.kt`:

```kotlin
    @Test
    fun tromsoInJuneStillProducesOrderedTimesAndReportsItsRule() {
        val tromso = GeoLocation(69.6492, 18.9553, "Europe/Oslo", "Tromsø", "NO")
        val d = engine.timesFor(tromso, LocalDate(2026, 6, 21), PrayerSettings())
        assertEquals(d.times.map { it.instant }.sorted(), d.times.map { it.instant })
        assertEquals(
            world.taqwa.app.domain.HighLatitudePreference.TWILIGHT_ANGLE,
            d.highLatitudeRuleApplied,
        )
    }
```

Run: `./gradlew :shared:allTests --tests "*PrayerTimesEngineTest*"` — Expected: PASS.

This is the test that proves the app does not show absurd times to a user in Tromsø on the longest day of the year.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/prayer shared/src/commonTest/kotlin/world/taqwa/app/prayer
git commit -m "feat: automatic high-latitude rule selection"
```

---

### Task 7: Timeline state and countdown

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/domain/TimelineState.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/prayer/TimelineBuilder.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/prayer/TimelineBuilderTest.kt`

**Interfaces:**
- Consumes: `DayPrayerTimes`, `Prayer`, `PrayerTime` from Task 5
- Produces:
  - `enum class PrayerStatus { PASSED, CURRENT, UPCOMING }`
  - `data class TimelineRow(val prayer: Prayer, val instant: Instant, val status: PrayerStatus)`
  - `data class TodayState(val rows: List<TimelineRow>, val next: PrayerTime, val countdown: Duration, val ringProgress: Float)`
  - `object TimelineBuilder { fun build(today: DayPrayerTimes, tomorrow: DayPrayerTimes, now: Instant, showSunrise: Boolean): TodayState }`

- [ ] **Step 1: Write the failing test**

```kotlin
package world.taqwa.app.prayer

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineBuilderTest {

    /** 2026-09-06 London-ish times expressed as UTC instants, one hour apart for clarity. */
    private fun day(date: LocalDate, startEpoch: Long) = DayPrayerTimes(
        date = date,
        times = listOf(
            PrayerTime(Prayer.FAJR, Instant.fromEpochSeconds(startEpoch)),
            PrayerTime(Prayer.SUNRISE, Instant.fromEpochSeconds(startEpoch + 3600)),
            PrayerTime(Prayer.DHUHR, Instant.fromEpochSeconds(startEpoch + 7200)),
            PrayerTime(Prayer.ASR, Instant.fromEpochSeconds(startEpoch + 10800)),
            PrayerTime(Prayer.MAGHRIB, Instant.fromEpochSeconds(startEpoch + 14400)),
            PrayerTime(Prayer.ISHA, Instant.fromEpochSeconds(startEpoch + 18000)),
        ),
        highLatitudeRuleApplied = null,
    )

    private val today = day(LocalDate(2026, 9, 6), 1_000_000)
    private val tomorrow = day(LocalDate(2026, 9, 7), 1_086_400)

    @Test
    fun sunriseIsHiddenUnlessRequested() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_000_100), false)
        assertEquals(5, s.rows.size)
        assertTrue(s.rows.none { it.prayer == Prayer.SUNRISE })
    }

    @Test
    fun sunriseAppearsWhenRequested() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_000_100), true)
        assertEquals(6, s.rows.size)
    }

    @Test
    fun partitionsIntoPassedCurrentAndUpcoming() {
        // 30 minutes after Asr
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_012_600), false)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.FAJR }.status)
        assertEquals(PrayerStatus.PASSED, s.rows.first { it.prayer == Prayer.DHUHR }.status)
        assertEquals(PrayerStatus.CURRENT, s.rows.first { it.prayer == Prayer.ASR }.status)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.MAGHRIB }.status)
        assertEquals(PrayerStatus.UPCOMING, s.rows.first { it.prayer == Prayer.ISHA }.status)
    }

    @Test
    fun exactlyOneRowIsCurrent() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_012_600), false)
        assertEquals(1, s.rows.count { it.status == PrayerStatus.CURRENT })
    }

    @Test
    fun nextIsTheFollowingObligatoryPrayerNotSunrise() {
        // just after Fajr — the next notified prayer is Dhuhr, not Sunrise
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_000_100), false)
        assertEquals(Prayer.DHUHR, s.next.prayer)
    }

    @Test
    fun countdownCountsDownToTheNextPrayer() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_007_200 - 600), false)
        assertEquals(600, s.countdown.inWholeSeconds)
    }

    @Test
    fun afterIshaTheNextPrayerIsTomorrowsFajr() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_018_100), false)
        assertEquals(Prayer.FAJR, s.next.prayer)
        assertEquals(tomorrow.time(Prayer.FAJR), s.next.instant)
    }

    @Test
    fun beforeFajrNothingHasPassedYet() {
        val s = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(999_000), false)
        assertTrue(s.rows.none { it.status == PrayerStatus.PASSED })
        assertEquals(Prayer.FAJR, s.next.prayer)
    }

    @Test
    fun ringProgressRunsFromZeroToOneAcrossTheInterval() {
        val quarter = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_003_600), false)
        assertTrue(quarter.ringProgress in 0.2f..0.4f, "was ${quarter.ringProgress}")
        val almost = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(1_007_100), false)
        assertTrue(almost.ringProgress > 0.9f, "was ${almost.ringProgress}")
    }

    @Test
    fun ringProgressIsAlwaysWithinBounds() {
        listOf(999_000L, 1_000_000L, 1_012_600L, 1_018_100L, 1_085_000L).forEach { t ->
            val p = TimelineBuilder.build(today, tomorrow, Instant.fromEpochSeconds(t), false).ringProgress
            assertTrue(p in 0f..1f, "progress $p out of bounds at $t")
        }
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*TimelineBuilderTest*"`
Expected: FAIL — `Unresolved reference: TimelineBuilder`

- [ ] **Step 3: Write the state types**

`domain/TimelineState.kt`:

```kotlin
package world.taqwa.app.domain

import kotlinx.datetime.Instant
import kotlin.time.Duration

enum class PrayerStatus { PASSED, CURRENT, UPCOMING }

data class TimelineRow(
    val prayer: Prayer,
    val instant: Instant,
    val status: PrayerStatus,
)

data class TodayState(
    val rows: List<TimelineRow>,
    val next: PrayerTime,
    val countdown: Duration,
    val ringProgress: Float,
)
```

- [ ] **Step 4: Implement the builder**

`prayer/TimelineBuilder.kt`:

```kotlin
package world.taqwa.app.prayer

import kotlinx.datetime.Instant
import world.taqwa.app.domain.DayPrayerTimes
import world.taqwa.app.domain.ObligatoryPrayers
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.PrayerTime
import world.taqwa.app.domain.TimelineRow
import world.taqwa.app.domain.TodayState
import kotlin.time.Duration

object TimelineBuilder {

    fun build(
        today: DayPrayerTimes,
        tomorrow: DayPrayerTimes,
        now: Instant,
        showSunrise: Boolean,
    ): TodayState {
        val visible = today.times.filter { showSunrise || it.prayer != Prayer.SUNRISE }

        // "Current" is the most recent obligatory prayer whose time has arrived.
        val currentPrayer = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .lastOrNull { it.instant <= now }
            ?.prayer

        val rows = visible.map { pt ->
            TimelineRow(
                prayer = pt.prayer,
                instant = pt.instant,
                status = when {
                    pt.prayer == currentPrayer -> PrayerStatus.CURRENT
                    pt.instant <= now -> PrayerStatus.PASSED
                    else -> PrayerStatus.UPCOMING
                },
            )
        }

        val next = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .firstOrNull { it.instant > now }
            ?: PrayerTime(Prayer.FAJR, tomorrow.time(Prayer.FAJR))

        val previous = ObligatoryPrayers
            .map { PrayerTime(it, today.time(it)) }
            .lastOrNull { it.instant <= now }
            ?.instant
            ?: (today.time(Prayer.FAJR) - (next.instant - today.time(Prayer.FAJR)))

        val total = (next.instant - previous).inWholeSeconds
        val elapsed = (now - previous).inWholeSeconds
        val progress = if (total <= 0L) 0f else (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

        return TodayState(
            rows = rows,
            next = next,
            countdown = next.instant - now,
            ringProgress = progress,
        )
    }
}
```

Note `Duration` is imported for the return type but not referenced directly; remove the import if the compiler warns.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :shared:allTests --tests "*TimelineBuilderTest*"`
Expected: PASS, 10 tests.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/domain/TimelineState.kt shared/src/commonMain/kotlin/world/taqwa/app/prayer/TimelineBuilder.kt shared/src/commonTest/kotlin/world/taqwa/app/prayer/TimelineBuilderTest.kt
git commit -m "feat: prayer timeline state and countdown"
```

---

### Task 8: Hijri date

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/hijri/UmmAlQuraCalendar.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/hijri/HijriFormatter.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/hijri/UmmAlQuraCalendarTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `data class HijriDate(val year: Int, val month: Int, val day: Int)`
  - `object UmmAlQuraCalendar { fun fromGregorian(date: LocalDate): HijriDate }`
  - `object HijriFormatter { fun monthNameEnglish(month: Int): String; fun format(date: HijriDate): String }`

kotlinx-datetime has no Hijri calendar, so this is the tabular civil (Kuwaiti) algorithm — an arithmetic approximation of Umm al-Qura, which is exactly why the spec provides a ±1 day user offset.

- [ ] **Step 1: Write the failing test**

```kotlin
package world.taqwa.app.hijri

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UmmAlQuraCalendarTest {

    @Test
    fun theHijraEpochMapsToTheFirstOfMuharramYearOne() {
        val h = UmmAlQuraCalendar.fromGregorian(LocalDate(622, 7, 19))
        assertEquals(1, h.year)
        assertEquals(1, h.month)
    }

    @Test
    fun aKnownModernDateConverts() {
        // 2026-09-06 falls in Rabi' al-Awwal 1448 by the tabular calendar.
        val h = UmmAlQuraCalendar.fromGregorian(LocalDate(2026, 9, 6))
        assertEquals(1448, h.year)
        assertEquals(3, h.month)
    }

    @Test
    fun monthIsAlwaysOneToTwelve() {
        var d = LocalDate(2024, 1, 1)
        repeat(1200) {
            val h = UmmAlQuraCalendar.fromGregorian(d)
            assertTrue(h.month in 1..12, "month ${h.month} for $d")
            assertTrue(h.day in 1..30, "day ${h.day} for $d")
            d = d.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
    }

    @Test
    fun conversionIsMonotonic() {
        var previous = UmmAlQuraCalendar.fromGregorian(LocalDate(2026, 1, 1))
        var d = LocalDate(2026, 1, 2)
        repeat(500) {
            val h = UmmAlQuraCalendar.fromGregorian(d)
            val a = previous.year * 10000 + previous.month * 100 + previous.day
            val b = h.year * 10000 + h.month * 100 + h.day
            assertTrue(b >= a, "went backwards at $d")
            previous = h
            d = d.plus(1, kotlinx.datetime.DateTimeUnit.DAY)
        }
    }

    @Test
    fun monthNamesCoverAllTwelve() {
        (1..12).forEach { assertTrue(HijriFormatter.monthNameEnglish(it).isNotBlank()) }
    }
}
```

> The expected values in `aKnownModernDateConverts` must be checked against a reference before this test is trusted. Verify with `python3 -c "from hijri_converter import Gregorian; print(Gregorian(2026,9,6).to_hijri())"` (`pip install hijri-converter`) and correct the assertion to whatever the reference reports. A test that encodes your own bug is worse than no test.

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*UmmAlQuraCalendarTest*"`
Expected: FAIL — `Unresolved reference: UmmAlQuraCalendar`

- [ ] **Step 3: Implement the conversion**

```kotlin
package world.taqwa.app.hijri

import kotlinx.datetime.LocalDate

data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Tabular civil ("Kuwaiti") Hijri calendar. An arithmetic approximation of Umm al-Qura that can
 * differ from local moonsighting by a day, which is why the user is given a plus or minus one
 * day offset in settings.
 */
object UmmAlQuraCalendar {

    private fun gregorianToJulianDay(y: Int, m: Int, d: Int): Long {
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return (d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045)
    }

    fun fromGregorian(date: LocalDate): HijriDate {
        val jd = gregorianToJulianDay(date.year, date.monthNumber, date.dayOfMonth)
        val l0 = jd - 1948440L + 10632L
        val n = (l0 - 1) / 10631L
        var l = l0 - 10631L * n + 354L
        val j = ((10985L - l) / 5316L) * ((50L * l) / 17719L) + (l / 5670L) * ((43L * l) / 15238L)
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L) - (j / 16L) * ((15238L * j) / 43L) + 29L
        val month = ((24L * l) / 709L).toInt()
        val day = (l - (709L * month) / 24L).toInt()
        val year = (30L * n + j - 30L).toInt()
        return HijriDate(year = year, month = month, day = day)
    }
}
```

- [ ] **Step 4: Implement the formatter**

```kotlin
package world.taqwa.app.hijri

private val MONTHS = listOf(
    "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
    "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
    "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
)

object HijriFormatter {
    fun monthNameEnglish(month: Int): String =
        MONTHS.getOrNull(month - 1) ?: error("Hijri month out of range: $month")

    fun format(date: HijriDate): String =
        "${date.day} ${monthNameEnglish(date.month)} ${date.year}"
}
```

The user's `hijriOffsetDays` is applied by shifting the **Gregorian** date before conversion, not by shifting the Hijri day number — shifting the Hijri day can produce day 31 or day 0. Apply it at the call site in Task 11.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :shared:allTests --tests "*UmmAlQura*"`
Expected: PASS, 5 tests. If `aKnownModernDateConverts` fails, first re-check your reference value from Step 1 before touching the algorithm.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/hijri shared/src/commonTest/kotlin/world/taqwa/app/hijri
git commit -m "feat: tabular Hijri calendar conversion and formatting"
```

---

### Task 9: Bundled city database

**Files:**
- Create: `tools/build-city-db.py`
- Create: `shared/src/commonMain/composeResources/files/cities.csv`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/city/City.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/city/CityRepository.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/city/CityRepositoryTest.kt`

**Interfaces:**
- Consumes: `GeoLocation` from Task 5
- Produces:
  - `data class City(val name: String, val region: String, val countryCode: String, val latitude: Double, val longitude: Double, val timeZoneId: String)` with `fun toGeoLocation(): GeoLocation`
  - `class CityRepository(private val loadCsv: suspend () -> String)` with `suspend fun search(query: String, limit: Int = 30): List<City>`

- [ ] **Step 1: Write the generator**

`tools/build-city-db.py`:

```python
#!/usr/bin/env python3
"""Convert GeoNames cities15000 into the compact CSV Taqwa bundles.

Source: https://download.geonames.org/export/dump/cities15000.zip  (CC BY 4.0)
Output columns: name,region,countryCode,lat,lon,timezone
"""
import csv, io, sys, urllib.request, zipfile

URL = "https://download.geonames.org/export/dump/cities15000.zip"
OUT = "shared/src/commonMain/composeResources/files/cities.csv"

with urllib.request.urlopen(URL) as resp:
    zf = zipfile.ZipFile(io.BytesIO(resp.read()))
    raw = zf.read("cities15000.txt").decode("utf-8")

rows = []
for line in raw.splitlines():
    f = line.split("\t")
    # 1 name, 8 country code, 10 admin1 code, 4 lat, 5 lon, 17 timezone, 14 population
    rows.append((f[1], f[10], f[8], f[4], f[5], f[17], int(f[14] or 0)))

rows.sort(key=lambda r: -r[6])  # population descending: London GB outranks London CA

with open(OUT, "w", newline="", encoding="utf-8") as fh:
    w = csv.writer(fh)
    w.writerow(["name", "region", "country", "lat", "lon", "tz"])
    for r in rows:
        w.writerow(r[:6])

print(f"wrote {len(rows)} cities to {OUT}", file=sys.stderr)
```

Run: `python3 tools/build-city-db.py`
Expected: roughly 25,000 rows, file around 1.5 MB.

Sorting by population descending is what makes the search useful: a user typing "lond" gets London, United Kingdom first rather than Londonderry.

- [ ] **Step 2: Write the failing test**

```kotlin
package world.taqwa.app.city

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CityRepositoryTest {

    private val csv = """
        name,region,country,lat,lon,tz
        London,England,GB,51.50853,-0.12574,Europe/London
        Londrina,Parana,BR,-23.31028,-51.16278,America/Sao_Paulo
        London,Ontario,CA,42.98339,-81.23304,America/Toronto
        Londonderry,Northern Ireland,GB,54.99721,-7.30917,Europe/London
        Cairo,Cairo,EG,30.06263,31.24967,Africa/Cairo
    """.trimIndent()

    private val repo = CityRepository { csv }

    @Test
    fun searchIsCaseInsensitiveAndPrefixMatched() = runTest {
        val results = repo.search("lond")
        assertEquals(4, results.size)
    }

    @Test
    fun mostPopulousMatchComesFirst() = runTest {
        val first = repo.search("lond").first()
        assertEquals("London", first.name)
        assertEquals("GB", first.countryCode)
    }

    @Test
    fun regionDistinguishesTheElevenLondons() = runTest {
        val londons = repo.search("london").filter { it.name == "London" }
        assertEquals(setOf("England", "Ontario"), londons.map { it.region }.toSet())
    }

    @Test
    fun timezoneSurvivesIntoTheGeoLocation() = runTest {
        val g = repo.search("cairo").first().toGeoLocation()
        assertEquals("Africa/Cairo", g.timeZoneId)
        assertEquals("EG", g.countryCode)
    }

    @Test
    fun blankQueryReturnsNothingRatherThanEverything() = runTest {
        assertTrue(repo.search("   ").isEmpty())
    }

    @Test
    fun limitIsRespected() = runTest {
        assertEquals(2, repo.search("lond", limit = 2).size)
    }
}
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*CityRepositoryTest*"`
Expected: FAIL — `Unresolved reference: CityRepository`

- [ ] **Step 4: Implement**

`city/City.kt`:

```kotlin
package world.taqwa.app.city

import world.taqwa.app.domain.GeoLocation

data class City(
    val name: String,
    val region: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
) {
    fun toGeoLocation() = GeoLocation(
        latitude = latitude,
        longitude = longitude,
        timeZoneId = timeZoneId,
        cityName = name,
        countryCode = countryCode,
    )
}
```

`city/CityRepository.kt`:

```kotlin
package world.taqwa.app.city

/**
 * Searches the bundled GeoNames extract. The CSV is pre-sorted by population descending, so
 * preserving file order in the results ranks the London everyone means above the others.
 */
class CityRepository(private val loadCsv: suspend () -> String) {

    private var cache: List<City>? = null

    private suspend fun cities(): List<City> = cache ?: parse(loadCsv()).also { cache = it }

    suspend fun search(query: String, limit: Int = 30): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return cities().asSequence()
            .filter { it.name.lowercase().startsWith(q) }
            .take(limit)
            .toList()
    }

    private fun parse(csv: String): List<City> =
        csv.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val f = line.split(",")
                if (f.size < 6) return@mapNotNull null
                City(
                    name = f[0],
                    region = f[1],
                    countryCode = f[2],
                    latitude = f[3].toDoubleOrNull() ?: return@mapNotNull null,
                    longitude = f[4].toDoubleOrNull() ?: return@mapNotNull null,
                    timeZoneId = f[5],
                )
            }
            .toList()
}
```

Injecting `loadCsv` as a lambda is what keeps this testable in `commonTest` with no file system and no resource loader.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :shared:allTests --tests "*CityRepositoryTest*"`
Expected: PASS, 6 tests.

- [ ] **Step 6: Wire the real resource loader**

In `di/AppContainer.kt` (created in Task 13), construct it as:

```kotlin
val cityRepository = CityRepository {
    Res.readBytes("files/cities.csv").decodeToString()
}
```

- [ ] **Step 7: Add the GeoNames attribution**

Append to `assets/audio/../../docs` — specifically create `docs/ATTRIBUTION.md`:

```markdown
# Attribution

- **Prayer time calculation** — [Adhan](https://github.com/batoulapps/adhan-kotlin) by Batoul Apps, MIT licence.
- **City database** — [GeoNames](https://www.geonames.org/) `cities15000`, CC BY 4.0.
- **Manrope** typeface — SIL Open Font Licence 1.1.
- **Adhan and takbir audio** — "Beautiful adhan" by Adam-synagda, CC0 1.0, via Wikimedia Commons.
```

This file is the source for the in-app Attribution screen built in Task 14. CC BY 4.0 makes the GeoNames credit a licence obligation, not a nicety.

- [ ] **Step 8: Commit**

```bash
git add tools shared/src/commonMain/composeResources/files shared/src/commonMain/kotlin/world/taqwa/app/city shared/src/commonTest/kotlin/world/taqwa/app/city docs/ATTRIBUTION.md
git commit -m "feat: bundled offline city database with population-ranked search"
```

---

### Task 10: Location provider

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/location/LocationProvider.kt`
- Create: `shared/src/androidMain/kotlin/world/taqwa/app/location/LocationProvider.android.kt`
- Create: `shared/src/iosMain/kotlin/world/taqwa/app/location/LocationProvider.ios.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/location/LocationRepository.kt`
- Modify: `androidApp/src/androidMain/AndroidManifest.xml`
- Modify: `iosApp/iosApp/Info.plist`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/location/LocationRepositoryTest.kt`

**Interfaces:**
- Consumes: `GeoLocation` from Task 5, `SettingsRepository` from Task 4
- Produces:
  - `enum class LocationPermission { NOT_REQUESTED, GRANTED, DENIED }`
  - `interface LocationProvider { suspend fun permission(): LocationPermission; suspend fun requestPermission(): LocationPermission; suspend fun currentCoordinates(): Pair<Double, Double>? }`
  - `expect fun createLocationProvider(): LocationProvider`
  - `class LocationRepository(provider: LocationProvider)` with `suspend fun permission(): LocationPermission`, `suspend fun requestPermission(): LocationPermission`, `suspend fun currentCoordinates(): Pair<Double, Double>?`
  - `LocationRepository.Companion.shouldRecompute(old: GeoLocation, new: Pair<Double, Double>): Boolean` and `distanceMetres(...)` — pure, hence testable without a device

  The chosen location itself (GPS-derived or manually picked city) is persisted by `SettingsRepository` and read by `TodayViewModel`; this class only talks to the sensor.

- [ ] **Step 1: Write the failing test for the 5 km policy**

The policy is the only part worth unit-testing; the platform calls are thin.

```kotlin
package world.taqwa.app.location

import world.taqwa.app.domain.GeoLocation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationRepositoryTest {

    private val london = GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB")

    @Test
    fun walkingAcrossTownDoesNotTriggerRecomputation() {
        // ~2 km east
        assertFalse(LocationRepository.shouldRecompute(london, 51.5074 to -0.0990))
    }

    @Test
    fun travellingBeyondFiveKilometresTriggersRecomputation() {
        // ~15 km east
        assertTrue(LocationRepository.shouldRecompute(london, 51.5074 to 0.0885))
    }

    @Test
    fun crossingContinentsAlwaysTriggersRecomputation() {
        assertTrue(LocationRepository.shouldRecompute(london, 21.4225 to 39.8262))
    }

    @Test
    fun theSamePointNeverTriggersRecomputation() {
        assertFalse(LocationRepository.shouldRecompute(london, 51.5074 to -0.1278))
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*LocationRepositoryTest*"`
Expected: FAIL — `Unresolved reference: LocationRepository`

- [ ] **Step 3: Write the common interface and the distance policy**

`location/LocationProvider.kt`:

```kotlin
package world.taqwa.app.location

enum class LocationPermission { NOT_REQUESTED, GRANTED, DENIED }

interface LocationProvider {
    suspend fun permission(): LocationPermission
    suspend fun requestPermission(): LocationPermission
    /** Latitude to longitude, or null when unavailable. */
    suspend fun currentCoordinates(): Pair<Double, Double>?
}

expect fun createLocationProvider(): LocationProvider
```

`location/LocationRepository.kt` — the companion holds the pure policy so the test needs no instance:

```kotlin
package world.taqwa.app.location

import world.taqwa.app.domain.GeoLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationRepository(private val provider: LocationProvider) {

    companion object {
        private const val EARTH_RADIUS_METRES = 6_371_000.0
        private const val RECOMPUTE_THRESHOLD_METRES = 5_000.0

        fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            fun rad(d: Double) = d * kotlin.math.PI / 180.0
            val dLat = rad(lat2 - lat1)
            val dLon = rad(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
            return EARTH_RADIUS_METRES * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        fun shouldRecompute(old: GeoLocation, new: Pair<Double, Double>): Boolean =
            distanceMetres(old.latitude, old.longitude, new.first, new.second) > RECOMPUTE_THRESHOLD_METRES
    }

    suspend fun permission(): LocationPermission = provider.permission()

    suspend fun requestPermission(): LocationPermission = provider.requestPermission()

    /**
     * Returns fresh coordinates, or null when the permission is absent or the fix failed.
     * The timezone is not resolved here — the caller keeps the previously known zone, or the
     * zone of the manually chosen city.
     */
    suspend fun currentCoordinates(): Pair<Double, Double>? = provider.currentCoordinates()
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:allTests --tests "*LocationRepositoryTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Implement the Android provider**

```kotlin
package world.taqwa.app.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import world.taqwa.app.settings.appContext
import kotlin.coroutines.resume

private class AndroidLocationProvider : LocationProvider {

    override suspend fun permission(): LocationPermission {
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) LocationPermission.GRANTED else LocationPermission.NOT_REQUESTED
    }

    override suspend fun requestPermission(): LocationPermission {
        // The Activity-scoped request is driven from Compose via rememberLauncherForActivityResult
        // in OnboardingScreen; by the time this is called the result is already reflected here.
        return permission()
    }

    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return null
        val last = listOfNotNull(
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
        ).maxByOrNull { it.time }
        return last?.let { it.latitude to it.longitude }
    }
}

actual fun createLocationProvider(): LocationProvider = AndroidLocationProvider()
```

Add to `AndroidManifest.xml` inside `<manifest>`:

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

Coarse location is sufficient — prayer times do not change meaningfully within a few hundred metres, and asking for less is both faster to grant and honest.

- [ ] **Step 6: Implement the iOS provider**

```kotlin
package world.taqwa.app.location

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import kotlin.coroutines.resume

private class IosLocationProvider : LocationProvider {

    private val manager = CLLocationManager()

    private fun map(status: CLAuthorizationStatus): LocationPermission = when (status) {
        kCLAuthorizationStatusAuthorizedWhenInUse,
        kCLAuthorizationStatusAuthorizedAlways -> LocationPermission.GRANTED
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted -> LocationPermission.DENIED
        kCLAuthorizationStatusNotDetermined -> LocationPermission.NOT_REQUESTED
        else -> LocationPermission.NOT_REQUESTED
    }

    override suspend fun permission(): LocationPermission = map(CLLocationManager.authorizationStatus())

    override suspend fun requestPermission(): LocationPermission =
        suspendCancellableCoroutine { cont ->
            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    if (cont.isActive) cont.resume(map(CLLocationManager.authorizationStatus()))
                }
            }
            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
        }

    override suspend fun currentCoordinates(): Pair<Double, Double>? {
        if (permission() != LocationPermission.GRANTED) return null
        val loc = manager.location ?: return null
        var lat = 0.0
        var lon = 0.0
        loc.coordinate.useContents { lat = latitude; lon = longitude }
        return lat to lon
    }
}

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()
```

Add the import `kotlinx.cinterop.useContents` and opt in with `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` on the class.

Add to `iosApp/iosApp/Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Taqwa uses your location to calculate accurate prayer times on your device. Your location is never sent anywhere.</string>
```

**Never request `Always` authorisation.** Background location is not needed and asking for it damages both trust and App Review outcomes.

- [ ] **Step 7: Persist the chosen location**

`TodayViewModel` reads the location from storage, so it has to be written there. Add to
`SettingsRepository`, using the same key/fallback style as Task 4:

```kotlin
private val LAT = doublePreferencesKey("location_latitude")
private val LON = doublePreferencesKey("location_longitude")
private val TZ = stringPreferencesKey("location_timezone")
private val CITY = stringPreferencesKey("location_city")
private val COUNTRY = stringPreferencesKey("location_country")

val location: Flow<GeoLocation?> = store.data.map { p ->
    val lat = p[LAT]
    val lon = p[LON]
    val tz = p[TZ]
    if (lat == null || lon == null || tz == null) null
    else GeoLocation(lat, lon, tz, p[CITY], p[COUNTRY])
}

suspend fun setLocation(location: GeoLocation) {
    store.edit {
        it[LAT] = location.latitude
        it[LON] = location.longitude
        it[TZ] = location.timeZoneId
        location.cityName?.let { n -> it[CITY] = n }
        location.countryCode?.let { c -> it[COUNTRY] = c }
    }
}
```

Import `androidx.datastore.preferences.core.doublePreferencesKey`. A location is null until
onboarding resolves one, which is exactly what drives `TodayUiState.NeedsLocation`.

Add a test to `SettingsRepositoryTest`:

```kotlin
    @Test
    fun locationIsNullUntilOneIsChosen() = runTest {
        assertEquals(null, repo("loc-empty").location.first())
    }

    @Test
    fun locationRoundTripsIncludingTimezone() = runTest {
        val r = repo("loc-roundtrip")
        r.setLocation(GeoLocation(51.5074, -0.1278, "Europe/London", "London", "GB"))
        val got = r.location.first()!!
        assertEquals("Europe/London", got.timeZoneId)
        assertEquals("London", got.cityName)
    }
```

Run: `./gradlew :shared:allTests --tests "*SettingsRepositoryTest*"` — Expected: PASS, 8 tests.

- [ ] **Step 8: Verify on both devices**

Build and run each platform. On first launch nothing should prompt yet — the prompt belongs to onboarding in Task 13. Confirm the app still starts and that neither manifest change broke the build.

Run: `./gradlew :androidApp:assembleDebug` — Expected: BUILD SUCCESSFUL, plus a successful Xcode run.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app shared/src/androidMain shared/src/iosMain iosApp shared/src/commonTest/kotlin/world/taqwa/app
git commit -m "feat: location provider with when-in-use permission and 5km recompute policy"
```

---

### Task 11: Shared components — ring, card, button, check

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/CountdownRing.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/TaqwaCard.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/TaqwaButton.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/design/components/CheckMark.kt`

**Interfaces:**
- Consumes: `LocalTaqwaColors`, `TaqwaText` from Tasks 2 and 3
- Produces:
  - `@Composable fun CountdownRing(progress: Float, label: String, countdown: String, clockTime: String, modifier: Modifier)`
  - `@Composable fun TaqwaCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun TaqwaRow(label: String, value: String?, onClick: (() -> Unit)?, trailing: @Composable (() -> Unit)?)`
  - `@Composable fun TaqwaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier)`
  - `@Composable fun TaqwaTextLink(text: String, onClick: () -> Unit, modifier: Modifier)`
  - `@Composable fun CheckMark(modifier: Modifier)`

- [ ] **Step 1: Write CountdownRing.kt**

```kotlin
package world.taqwa.app.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText

@Composable
fun CountdownRing(
    progress: Float,
    label: String,
    countdown: String,
    clockTime: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTaqwaColors.current
    Box(modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(196.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.hairline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.ring,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercase(),
                style = TaqwaText.sectionLabel.copy(fontSize = 11.sp),
                color = colors.accent,
                textAlign = TextAlign.Center,
            )
            Text(text = countdown, style = TaqwaText.countdown, color = colors.textPrimary)
            Text(text = clockTime, style = TaqwaText.caption, color = colors.textSecondary)
        }
    }
}
```

- [ ] **Step 2: Write CheckMark.kt**

The spec forbids the `✓` character, so this is drawn:

```kotlin
package world.taqwa.app.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors

@Composable
fun CheckMark(modifier: Modifier = Modifier) {
    val accent = LocalTaqwaColors.current.accent
    Canvas(modifier.size(20.dp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.17f, w * 0.52f)
            lineTo(w * 0.40f, w * 0.75f)
            lineTo(w * 0.83f, w * 0.27f)
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = w * 0.115f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
```

- [ ] **Step 3: Write TaqwaButton.kt**

```kotlin
package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors

/** Full-width pill, 48dp tall — above the 44pt minimum with room for large-text settings. */
@Composable
fun TaqwaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.textPrimary)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.background, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

/** The secondary action is never a second button. */
@Composable
fun TaqwaTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTaqwaColors.current
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
```

- [ ] **Step 4: Write TaqwaCard.kt**

```kotlin
package world.taqwa.app.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText

@Composable
fun TaqwaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalTaqwaColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
fun CardDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalTaqwaColors.current.hairline))
}

@Composable
fun TaqwaRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalTaqwaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = TaqwaText.rowLabel, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    value,
                    style = TaqwaText.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            trailing?.invoke()
        }
    }
}
```

- [ ] **Step 5: Build both platforms**

Run: `./gradlew :androidApp:assembleDebug` — Expected: BUILD SUCCESSFUL. Also run in Xcode; Compose Canvas behaves differently on iOS and a drawing bug is cheaper to find now than inside a screen.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/design/components
git commit -m "feat: shared design system components"
```

---

### Task 12: Today screen

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/TodayViewModel.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/TodayScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/today/PrayerTimeline.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/feature/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `TimelineBuilder`, `PrayerTimesEngine`, `SettingsRepository`, `LocationRepository`, `UmmAlQuraCalendar`, all components from Task 11
- Produces:
  - `sealed interface TodayUiState { data object Loading; data object NeedsLocation; data class Ready(val location, val hijri: String, val today: TodayState, val highLatitudeNote: String?) }`
  - `highLatitudeNote` carries whichever of the two high-latitude cases applies; the polar-day wording wins when both are true, because in that case every time was substituted, not only Fajr and Isha.
  - `class TodayViewModel(engine: PrayerTimesEngine, settings: SettingsRepository, locationOf: suspend () -> GeoLocation?, now: () -> Instant)` with `val state: StateFlow<TodayUiState>`, `fun start(scope: CoroutineScope)` and `suspend fun refresh()`
  - `@Composable fun TodayScreen(state: TodayUiState, onOpenQibla: () -> Unit, onOpenSettings: () -> Unit, onChooseCity: () -> Unit, onAllowLocation: () -> Unit)`

- [ ] **Step 1: Write the failing test**

```kotlin
package world.taqwa.app.feature.today

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayViewModelTest {

    @Test
    fun withoutALocationTheScreenAsksForOneRatherThanShowingNothing() = runTest {
        val vm = todayViewModelWithNoLocation()
        assertEquals(TodayUiState.NeedsLocation, vm.state.first { it !is TodayUiState.Loading })
    }

    @Test
    fun withALocationTheScreenIsReadyAndNamesTheNextPrayer() = runTest {
        val vm = todayViewModelForLondon(now = Instant.parse("2026-09-06T14:30:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertEquals(5, ready.today.rows.size)
        assertTrue(ready.hijri.isNotBlank())
    }

    @Test
    fun tromsoSurfacesTheHighLatitudeNote() = runTest {
        val vm = todayViewModelForTromso(now = Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(ready.highLatitudeNote != null)
        assertTrue(ready.highLatitudeNote!!.contains("one-seventh") ||
                   ready.highLatitudeNote!!.contains("twilight"))
    }

    @Test
    fun theHijriOffsetShiftsTheDisplayedDate() = runTest {
        val zero = todayViewModelForLondon(hijriOffset = 0).state.first { it is TodayUiState.Ready }
        val plus = todayViewModelForLondon(hijriOffset = 1).state.first { it is TodayUiState.Ready }
        assertTrue((zero as TodayUiState.Ready).hijri != (plus as TodayUiState.Ready).hijri)
    }
}
```

Write the three `todayViewModel*` helpers in the same file, constructing a `TodayViewModel` with a fake `LocationRepository` and a fixed clock. Fakes keep this test device-free.

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*TodayViewModelTest*"`
Expected: FAIL — `Unresolved reference: TodayViewModel`

- [ ] **Step 3: Write the view model**

```kotlin
package world.taqwa.app.feature.today

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import world.taqwa.app.domain.GeoLocation
import world.taqwa.app.domain.HighLatitudePreference
import world.taqwa.app.domain.TodayState
import world.taqwa.app.hijri.HijriFormatter
import world.taqwa.app.hijri.UmmAlQuraCalendar
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.prayer.TimelineBuilder
import world.taqwa.app.settings.SettingsRepository

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NeedsLocation : TodayUiState
    data class Ready(
        val location: GeoLocation,
        val hijri: String,
        val today: TodayState,
        val highLatitudeNote: String?,
    ) : TodayUiState
}

class TodayViewModel(
    private val engine: PrayerTimesEngine,
    private val settings: SettingsRepository,
    private val locationOf: suspend () -> GeoLocation?,
    private val now: () -> Instant,
) {
    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                refresh()
                // The timeline is time-dependent: a pip must fill and a row must dim the
                // moment a prayer arrives, with no pull-to-refresh.
                delay(1_000)
            }
        }
    }

    suspend fun refresh() {
        val location = locationOf()
        if (location == null) {
            _state.value = TodayUiState.NeedsLocation
            return
        }
        val prefs = settings.prayerSettings.first()
        val zone = TimeZone.of(location.timeZoneId)
        val instant = now()
        val localDate = instant.toLocalDateTime(zone).date

        val today = engine.timesFor(location, localDate, prefs)
        val tomorrow = engine.timesFor(location, localDate.plus(1, DateTimeUnit.DAY), prefs)

        val hijri = UmmAlQuraCalendar.fromGregorian(
            localDate.plus(prefs.hijriOffsetDays, DateTimeUnit.DAY),
        )

        _state.value = TodayUiState.Ready(
            location = location,
            hijri = HijriFormatter.format(hijri),
            today = TimelineBuilder.build(today, tomorrow, instant, prefs.showSunrise),
            highLatitudeNote = noteFor(today),
        )
    }

    /**
     * Two distinct cases, and conflating them would be the silent fudging the spec exists to
     * prevent. An ordinary seasonal adjustment substitutes only Fajr and Isha. True polar day or
     * night means adhan2 could not compute the day at all, so EVERY time on screen — Maghrib
     * included — came from a different latitude. The polar case therefore wins.
     */
    private fun noteFor(day: DayPrayerTimes): String? = when {
        day.nearestLatitudeFallbackApplied ->
            "The sun does not rise or set here today. All times are calculated for the " +
                "nearest latitude where it does."
        day.highLatitudeRuleApplied == HighLatitudePreference.SEVENTH_OF_NIGHT ->
            "The sun never sets far enough here. Fajr and Isha use the one-seventh rule."
        day.highLatitudeRuleApplied == HighLatitudePreference.TWILIGHT_ANGLE ->
            "The sun never sets far enough here. Fajr and Isha use the twilight angle rule."
        day.highLatitudeRuleApplied != null ->
            "Fajr and Isha use the middle of the night rule at this latitude."
        else -> null
    }
}
```

**Add a fourth test** to `TodayViewModelTest` covering the polar case, since it is the one a Nordic
user actually hits in June:

```kotlin
    @Test
    fun polarDaySaysEveryTimeWasSubstitutedNotJustFajrAndIsha() = runTest {
        val vm = todayViewModelForTromso(now = Instant.parse("2026-06-21T12:00:00Z"))
        val ready = vm.state.first { it is TodayUiState.Ready } as TodayUiState.Ready
        assertTrue(ready.highLatitudeNote!!.contains("does not rise or set"))
        assertTrue(ready.highLatitudeNote!!.contains("All times"))
    }
```

The Hijri offset is applied to the **Gregorian** date before conversion, never to the Hijri day number — shifting the Hijri day can produce day 0 or day 31.

- [ ] **Step 4: Write the timeline composable**

`feature/today/PrayerTimeline.kt` — the rail, the pips, the dimming:

```kotlin
package world.taqwa.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.taqwa.app.design.LocalTaqwaColors
import world.taqwa.app.design.TaqwaText
import world.taqwa.app.domain.Prayer
import world.taqwa.app.domain.PrayerStatus
import world.taqwa.app.domain.TimelineRow

private fun englishName(p: Prayer) = when (p) {
    Prayer.FAJR -> "Fajr"; Prayer.SUNRISE -> "Sunrise"; Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"; Prayer.MAGHRIB -> "Maghrib"; Prayer.ISHA -> "Isha"
}

private fun arabicName(p: Prayer) = when (p) {
    Prayer.FAJR -> "الفجر"; Prayer.SUNRISE -> "الشروق"; Prayer.DHUHR -> "الظهر"
    Prayer.ASR -> "العصر"; Prayer.MAGHRIB -> "المغرب"; Prayer.ISHA -> "العشاء"
}

@Composable
fun PrayerTimeline(rows: List<TimelineRow>, formatTime: (TimelineRow) -> String) {
    val colors = LocalTaqwaColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    .alpha(if (row.status == PrayerStatus.PASSED) 0.44f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                    when (row.status) {
                        PrayerStatus.CURRENT -> Box(
                            Modifier.size(14.dp).clip(CircleShape).background(colors.accent),
                        )
                        PrayerStatus.PASSED -> Box(
                            Modifier.size(10.dp).clip(CircleShape).background(colors.textTertiary),
                        )
                        PrayerStatus.UPCOMING -> Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .border(1.5.dp, colors.hairline, CircleShape),
                        )
                    }
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            englishName(row.prayer),
                            style = TaqwaText.rowLabel,
                            color = if (row.status == PrayerStatus.CURRENT) colors.accent else colors.textPrimary,
                            fontWeight = if (row.status == PrayerStatus.CURRENT) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                        Text(
                            arabicName(row.prayer),
                            // Arabic always uses the OS face, never Manrope.
                            fontFamily = FontFamily.Default,
                            fontSize = 14.sp,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        formatTime(row),
                        style = TaqwaText.rowTime,
                        color = if (row.status == PrayerStatus.CURRENT) colors.accent else colors.textSecondary,
                        fontWeight = if (row.status == PrayerStatus.CURRENT) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
```

The vertical rail itself is drawn as a 1dp `Box` behind the pips column; add it as a `Box` with `Modifier.width(1.dp).background(colors.hairline)` positioned in the 26dp gutter, inset 12dp top and bottom so it starts and ends at the first and last pip.

- [ ] **Step 5: Write TodayScreen.kt**

Compose the header (location, Hijri date, two icon buttons), `CountdownRing`, `PrayerTimeline`, and — when `highLatitudeNote` is non-null — a `TaqwaCard` containing the note with the accent-coloured heading "At this latitude". When the state is `NeedsLocation`, render the empty state: heading "No location yet", the explanatory paragraph from the spec, a `TaqwaPrimaryButton("Choose a city")` and a `TaqwaTextLink("Allow location instead")`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :shared:allTests --tests "*TodayViewModelTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 7: Verify the countdown does not jitter**

Run the app on both platforms with a location set. Watch the countdown tick across a minute boundary. The digits must not shift horizontally. If they do, Manrope's tabular figures are not being applied — add `fontFeatureSettings = "tnum"` to `TaqwaText.countdown`.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app/feature/today shared/src/commonTest/kotlin/world/taqwa/app/feature/today
git commit -m "feat: Today screen with countdown ring and prayer timeline"
```

---

### Task 13: Navigation, onboarding and the app container

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/nav/Screen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/nav/Navigator.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/di/AppContainer.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/onboarding/OnboardingScreen.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt`
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/nav/NavigatorTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 2–12
- Produces:
  - `sealed interface Screen` with `Onboarding`, `Today`, `Settings`, `PrayerTimesSettings`, `MethodPicker`, `HighLatitudePicker`, `ManualAdjustments`, `LocationSettings`, `CitySearch`, `Appearance`, `Attribution`
  - `class Navigator(start: Screen)` with `val backStack: StateFlow<List<Screen>>`, `fun push(Screen)`, `fun pop(): Boolean`, `fun replaceAll(Screen)`, `val current: Screen`
  - `class AppContainer` exposing `settingsRepository`, `cityRepository`, `locationRepository`, `prayerTimesEngine`

- [ ] **Step 1: Write the failing navigator test**

```kotlin
package world.taqwa.app.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun startsOnTheGivenScreen() {
        assertEquals(Screen.Today, Navigator(Screen.Today).current)
    }

    @Test
    fun pushAndPopReturnToWhereYouWere() {
        val n = Navigator(Screen.Today)
        n.push(Screen.Settings)
        n.push(Screen.Appearance)
        assertEquals(Screen.Appearance, n.current)
        assertTrue(n.pop())
        assertEquals(Screen.Settings, n.current)
    }

    @Test
    fun popAtTheRootIsRefusedSoTheAppNeverEmptiesItself() {
        val n = Navigator(Screen.Today)
        assertFalse(n.pop())
        assertEquals(Screen.Today, n.current)
    }

    @Test
    fun replaceAllClearsHistory() {
        val n = Navigator(Screen.Onboarding)
        n.push(Screen.Settings)
        n.replaceAll(Screen.Today)
        assertEquals(Screen.Today, n.current)
        assertFalse(n.pop())
    }
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*NavigatorTest*"`
Expected: FAIL — `Unresolved reference: Navigator`

- [ ] **Step 3: Implement navigation**

`nav/Screen.kt`:

```kotlin
package world.taqwa.app.nav

sealed interface Screen {
    data object Onboarding : Screen
    data object Today : Screen
    data object Settings : Screen
    data object PrayerTimesSettings : Screen
    data object MethodPicker : Screen
    data object HighLatitudePicker : Screen
    data object ManualAdjustments : Screen
    data object LocationSettings : Screen
    data object CitySearch : Screen
    data object Appearance : Screen
    data object Attribution : Screen
}
```

`nav/Navigator.kt`:

```kotlin
package world.taqwa.app.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Slice 1 has a root screen and a handful of pushed children, so a list in a StateFlow is the
 * whole requirement. Revisit when the tab bar arrives in slice 2.
 */
class Navigator(start: Screen) {

    private val _backStack = MutableStateFlow(listOf(start))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    val current: Screen get() = _backStack.value.last()

    fun push(screen: Screen) {
        _backStack.value = _backStack.value + screen
    }

    /** Returns false at the root so the caller can let the platform handle the back gesture. */
    fun pop(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.value = _backStack.value.dropLast(1)
        return true
    }

    fun replaceAll(screen: Screen) {
        _backStack.value = listOf(screen)
    }
}
```

- [ ] **Step 4: Write the app container**

```kotlin
package world.taqwa.app.di

import world.taqwa.app.city.CityRepository
import world.taqwa.app.location.LocationRepository
import world.taqwa.app.location.createLocationProvider
import world.taqwa.app.prayer.PrayerTimesEngine
import world.taqwa.app.settings.SettingsRepository
import world.taqwa.app.settings.createDataStore
import world.taqwa.app.resources.Res

/** Manual construction. A DI framework earns its place when there is a graph worth managing. */
class AppContainer {
    val settingsRepository = SettingsRepository(createDataStore())
    val cityRepository = CityRepository { Res.readBytes("files/cities.csv").decodeToString() }
    val locationRepository = LocationRepository(createLocationProvider())
    val prayerTimesEngine = PrayerTimesEngine()
}
```

- [ ] **Step 5: Write the onboarding flow**

Three steps held in local state, each using `TaqwaPrimaryButton` plus `TaqwaTextLink`, with the exact copy from the spec:

1. Welcome — the ring mark, "Taqwa", and "Prayer times, qibla and the Quran. Free forever. No ads, no account, works offline." Primary: "Get started".
2. Location — "Where are you?" and "Prayer times depend on your exact position. Everything is calculated on your device — your location never leaves your phone." Primary: "Use my location" (calls `locationRepository.requestPermission()`); link: "Choose a city instead" (pushes `Screen.CitySearch`).
3. Notifications — "Never miss a prayer" and "A notification at each prayer time. You pick the sound for every prayer separately — and can change it whenever you like." Primary: "Enable notifications"; link: "Not now".

Step 3's primary button is a **no-op placeholder in this plan** — it advances the flow and nothing more. Plan 2 wires it to the real permission request. Do not request notification permission here without a scheduler behind it; a granted permission that produces no notifications is worse than not asking.

On completion call `settingsRepository.setOnboardingComplete(true)` and `navigator.replaceAll(Screen.Today)`.

- [ ] **Step 6: Wire App.kt**

```kotlin
package world.taqwa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import world.taqwa.app.design.TaqwaTheme
import world.taqwa.app.di.AppContainer
import world.taqwa.app.nav.Navigator
import world.taqwa.app.nav.Screen

@Composable
fun App(container: AppContainer) {
    val themeMode by container.settingsRepository.themeMode
        .collectAsState(initial = world.taqwa.app.design.ThemeMode.SYSTEM)
    val navigator = remember { Navigator(Screen.Today) }
    val backStack by navigator.backStack.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!container.settingsRepository.onboardingComplete.first()) {
            navigator.replaceAll(Screen.Onboarding)
        }
    }

    TaqwaTheme(themeMode) {
        when (backStack.last()) {
            Screen.Onboarding -> { /* OnboardingScreen(...) */ }
            Screen.Today -> { /* TodayScreen(...) */ }
            else -> { /* settings screens, Task 14 */ }
        }
    }
}
```

Fill in each branch with the real composables as they exist; the `else` branch is completed in Task 14.

- [ ] **Step 7: Run the tests and both apps**

Run: `./gradlew :shared:allTests` — Expected: all tests PASS.
Then run both platforms. First launch shows onboarding; completing it lands on Today; relaunching goes straight to Today.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app shared/src/commonTest/kotlin/world/taqwa/app/nav
git commit -m "feat: navigation backstack, app container and onboarding flow"
```

---

### Task 14: Settings screens

**Files:**
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/SettingsRootScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/PrayerTimesSettingsScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/LocationSettingsScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/CitySearchScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AppearanceSettingsScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/feature/settings/AttributionScreen.kt`
- Create: `shared/src/commonMain/kotlin/world/taqwa/app/prayer/CalculationMethodDefaults.kt`
- Modify: `shared/src/commonMain/kotlin/world/taqwa/app/App.kt` — fill the remaining branches
- Test: `shared/src/commonTest/kotlin/world/taqwa/app/prayer/CalculationMethodDefaultsTest.kt`

**Interfaces:**
- Consumes: `TaqwaCard`, `TaqwaRow`, `CheckMark`, `SettingsRepository`, `CityRepository`, `Navigator`
- Produces: `object CalculationMethodDefaults { fun forCountry(countryCode: String?): CalculationMethodId }`

- [ ] **Step 1: Write the failing test for method auto-detection**

```kotlin
package world.taqwa.app.prayer

import world.taqwa.app.domain.CalculationMethodId
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculationMethodDefaultsTest {

    @Test
    fun saudiArabiaUsesUmmAlQura() =
        assertEquals(CalculationMethodId.UMM_AL_QURA, CalculationMethodDefaults.forCountry("SA"))

    @Test
    fun turkeyUsesDiyanet() =
        assertEquals(CalculationMethodId.TURKEY, CalculationMethodDefaults.forCountry("TR"))

    @Test
    fun northAmericaUsesIsna() {
        assertEquals(CalculationMethodId.ISNA, CalculationMethodDefaults.forCountry("US"))
        assertEquals(CalculationMethodId.ISNA, CalculationMethodDefaults.forCountry("CA"))
    }

    @Test
    fun theSubcontinentUsesKarachi() {
        listOf("PK", "IN", "BD").forEach {
            assertEquals(CalculationMethodId.KARACHI, CalculationMethodDefaults.forCountry(it))
        }
    }

    @Test
    fun southeastAsiaUsesSingapore() {
        listOf("ID", "MY", "SG").forEach {
            assertEquals(CalculationMethodId.SINGAPORE, CalculationMethodDefaults.forCountry(it))
        }
    }

    @Test
    fun libyaFallsBackToMuslimWorldLeague() =
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, CalculationMethodDefaults.forCountry("LY"))

    @Test
    fun anUnknownOrAbsentCountryFallsBackRatherThanCrashing() {
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, CalculationMethodDefaults.forCountry(null))
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, CalculationMethodDefaults.forCountry("ZZ"))
    }

    @Test
    fun lowercaseCountryCodesAreAccepted() =
        assertEquals(CalculationMethodId.EGYPTIAN, CalculationMethodDefaults.forCountry("eg"))
}
```

- [ ] **Step 2: Run it to confirm it fails**

Run: `./gradlew :shared:allTests --tests "*CalculationMethodDefaultsTest*"`
Expected: FAIL — `Unresolved reference: CalculationMethodDefaults`

- [ ] **Step 3: Implement**

```kotlin
package world.taqwa.app.prayer

import world.taqwa.app.domain.CalculationMethodId

object CalculationMethodDefaults {

    private val BY_COUNTRY = mapOf(
        "SA" to CalculationMethodId.UMM_AL_QURA,
        "TR" to CalculationMethodId.TURKEY,
        "US" to CalculationMethodId.ISNA,
        "CA" to CalculationMethodId.ISNA,
        "EG" to CalculationMethodId.EGYPTIAN,
        "PK" to CalculationMethodId.KARACHI,
        "IN" to CalculationMethodId.KARACHI,
        "BD" to CalculationMethodId.KARACHI,
        "ID" to CalculationMethodId.SINGAPORE,
        "MY" to CalculationMethodId.SINGAPORE,
        "SG" to CalculationMethodId.SINGAPORE,
        "AE" to CalculationMethodId.DUBAI,
        "KW" to CalculationMethodId.KUWAIT,
        "QA" to CalculationMethodId.QATAR,
        "IR" to CalculationMethodId.TEHRAN,
    )

    fun forCountry(countryCode: String?): CalculationMethodId =
        countryCode?.uppercase()?.let(BY_COUNTRY::get) ?: CalculationMethodId.MUSLIM_WORLD_LEAGUE
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :shared:allTests --tests "*CalculationMethodDefaultsTest*"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Build the settings root**

`SettingsRootScreen.kt` — three `TaqwaCard` groups separated by section labels, using the spec's exact wording:

- **PRAYER**: `TaqwaRow("Location", value = cityName)`, `TaqwaRow("Prayer times", value = methodDisplayName)`, `TaqwaRow("Notifications", value = "5 on")` — the notifications row navigates nowhere in this plan; make it non-clickable with the value "Coming soon" and wire it in Plan 2.
- **APP**: `TaqwaRow("Appearance", value = themeName)`, `TaqwaRow("Language", value = "English")` — the language row is non-clickable in this plan.
- **ABOUT**: `TaqwaRow("About Taqwa")`, `TaqwaRow("Attribution & licences")`.

- [ ] **Step 6: Build the prayer times settings screen and its three pickers**

`PrayerTimesSettingsScreen` — a card of pushable rows (Calculation method, High latitude rule,
Manual adjustments), then a segmented control for Asr madhab (Standard / Hanafi), then a segmented
control for Hijri date (−1 day / Umm al-Qura / +1 day) with the resulting date rendered live
beneath it via `HijriFormatter`, then a card with the "Show sunrise" toggle. Every change writes
through `settingsRepository.setPrayerSettings(...)` immediately — there is no save button.

The three pushable rows need destinations. All three are the same shape: a single `TaqwaCard` of
rows, the selected one carrying `CheckMark()` as trailing content, writing on tap and popping.

```kotlin
@Composable
fun MethodPickerScreen(current: CalculationMethodId, onPick: (CalculationMethodId) -> Unit) {
    TaqwaCard {
        CalculationMethodId.entries.forEachIndexed { i, id ->
            if (i > 0) CardDivider()
            TaqwaRow(
                label = methodDisplayName(id),
                onClick = { onPick(id) },
                trailing = { if (id == current) CheckMark() },
            )
        }
    }
}

fun methodDisplayName(id: CalculationMethodId): String = when (id) {
    CalculationMethodId.MUSLIM_WORLD_LEAGUE -> "Muslim World League"
    CalculationMethodId.ISNA -> "ISNA (North America)"
    CalculationMethodId.EGYPTIAN -> "Egyptian General Authority"
    CalculationMethodId.UMM_AL_QURA -> "Umm al-Qura (Makkah)"
    CalculationMethodId.KARACHI -> "University of Islamic Sciences, Karachi"
    CalculationMethodId.TEHRAN -> "Institute of Geophysics, Tehran"
    CalculationMethodId.DUBAI -> "Dubai"
    CalculationMethodId.KUWAIT -> "Kuwait"
    CalculationMethodId.QATAR -> "Qatar"
    CalculationMethodId.SINGAPORE -> "Singapore"
    CalculationMethodId.TURKEY -> "Diyanet (Türkiye)"
    CalculationMethodId.MOONSIGHTING_COMMITTEE -> "Moonsighting Committee"
}
```

`HighLatitudePickerScreen` is identical over `HighLatitudePreference.entries`, with display names
"Automatic", "Middle of the night", "One-seventh of the night", "Twilight angle". When
`AUTOMATIC` is selected, render beneath the card which concrete rule that resolves to at the
user's latitude, via `HighLatitudeSelector.select(...)` — otherwise "Automatic" tells the user
nothing.

`ManualAdjustmentsScreen` is a card of five rows, one per obligatory prayer, each with a stepper
from −59 to +59 minutes writing into `PrayerSettings.minuteAdjustments`. Show the adjusted time
beside each stepper so the effect is visible while adjusting.

**Defect to fix here, found during Task 4:** `SettingsRepository` has no DataStore key for
`minuteAdjustments`, so offsets are held in memory and silently lost on relaunch. Add persistence
before building the screen — serialise the map as a single string preference, e.g.
`"FAJR:5,ISHA:-3"`, parsed back with the same tolerant fallback style as `toEnumOr` (an
unparseable entry is skipped, never thrown). Add a test asserting a non-empty
`minuteAdjustments` map survives a round trip, and one asserting a malformed stored value
yields an empty map rather than crashing.

- [ ] **Step 7: Build the location and city search screens**

Location: a "Use my location" toggle, a card showing city, country and **IANA timezone**, a "Choose a city instead" row, and the privacy note "Coordinates are stored on your device and used only to calculate times. Nothing is sent anywhere."

City search: a text field wired to `cityRepository.search(query)` with results in a `TaqwaCard`, **each row showing the region beneath the name**. Selecting a city writes it as the manual location and pops back.

- [ ] **Step 8: Build appearance and attribution**

Appearance: one card with three rows — System, Light, Dark — the selected one carrying `CheckMark()` as its trailing content. Three rows rather than a segmented control, because it reads correctly with a screen reader and matches the rest of settings.

The widget background row is **not** in this plan; it arrives with the widgets in Plan 2.

Attribution: render the contents of `docs/ATTRIBUTION.md` as static text. GeoNames CC BY 4.0 makes this a licence obligation.

- [ ] **Step 9: Fill the remaining App.kt branches and verify end to end**

Run: `./gradlew :shared:allTests` — Expected: all tests PASS.

Then walk both platforms through: onboarding → decline location → choose a city → correct times appear → open settings → switch to Hanafi and confirm Asr moves later → switch theme to Dark and confirm the whole app changes → change the Hijri offset and confirm the header date changes → force-quit and relaunch and confirm every setting persisted.

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/world/taqwa/app shared/src/commonTest/kotlin/world/taqwa/app/prayer
git commit -m "feat: settings tree with calculation method, madhab, hijri offset and appearance"
```

---

## Definition of done for this plan

Both platforms build and run. A user can complete onboarding, grant location or pick a city, and see correct prayer times on a themed Today screen whose timeline dims prayers as they pass and whose countdown ticks without jitter. Every setting in the spec's defaults table persists across a relaunch. Tromsø in June shows a rule note rather than absurd times. `./gradlew :shared:allTests` is green.

Not yet present, by design: notifications, the qibla screen, widgets, RTL and localisation. Those are Plan 2.

## Carried forward to Plan 2

1. The onboarding notifications button is a no-op placeholder — Task 13, Step 5.
2. The Settings → Notifications row is inert — Task 14, Step 5.
3. The Appearance screen has no widget background row — Task 14, Step 8.
4. `Res.readBytes` loads the city CSV on every cold start; if that measures slow on a low-end Android device, move it to a background dispatcher with a warm cache.
5. **Verify Compose Multiplatform's Arabic shaping on iOS** while building Task 12's timeline. It is the risk that decides whether slice 2's Quran reader can use Compose at all, and Task 12 is the first place Arabic renders. Report what you observe.
