# Taqwa — overnight build log

Autonomous build of slice 1, started 2026-09-06 03:07 SAST on branch `slice-1-core`.

## Environment (verified before starting)

- JDK 17.0.14, Android SDK at `~/Library/Android/sdk` (platforms 31–36, build-tools 33–35)
- Xcode 16.0, iOS 18.0 SDK, simulators for iOS 18.0 / 18.6 / 26.2
- Physical Android `LoopPhone` (CSLPGYZA2606002729) connected and authorised
- Physical iPhone paired; Apple Development signing identities present (team 36383TYK26)
- **Limitation:** tooling can drive iOS *simulators* only, not the physical iPhone. Visual
  verification happens on the simulator; the physical iPhone gets an install to try by hand.

## Decisions taken while you were asleep

**Scaffolding source changed.** The plan said to use the kmp.jetbrains.com wizard; that endpoint
now returns 404. Switched to JetBrains' `compose-multiplatform-ios-android-template`, which ships
a working Xcode project and Gradle wrapper.

**Toolchain upgrade is mandatory, not cosmetic.** That template pins Kotlin 1.9.21. A library
compiled with Kotlin 2.x metadata cannot be read by a 1.9 compiler, so adhan2 0.0.7 would be
unusable. Task 1 upgrades to Kotlin 2.2.20 / CMP 1.12.0 / AGP 8.7.3.

**Module layout follows the template:** `shared/` (all KMP logic, UI and tests), `androidApp/`
(Android host + MainActivity), `iosApp/` (Xcode project). The plan's paths were rewritten to
match. `shared` is deliberately NOT renamed to `composeApp` — the Xcode build phase invokes
`:shared:embedAndSignAppleFrameworkForXcode` by name, and renaming it means editing
`project.pbxproj` at 3am for no functional gain.

**Working on branch `slice-1-core`, not `main`.** Merged when the work is verified.

## Progress

(appended as tasks complete)
