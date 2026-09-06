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

### Task 1 — scaffold — COMPLETE, verified on both platforms

Green on: `:shared:allTests` (JVM), `:shared:iosSimulatorArm64Test` (native, `tests="1" failures="0"`),
`:androidApp:assembleDebug`, and an Xcode simulator build. The Android APK is **installed and
running on your LoopPhone** (`topResumedActivity=world.taqwa.app/.MainActivity`, no crash), and the
iOS app renders in the iPhone 17 Pro simulator.

Five environment blockers had to be solved to get there. All are recorded because you will hit
them again on a fresh machine.

**1. adhan2 requires JDK 21, not 17.** Its JVM artifact is compiled to class file major version
65. The smoke test failed with `UnsupportedClassVersionError` until fixed. The system JDK here is
Temurin 17, so `gradle.properties` now pins `org.gradle.java.home` to the JDK 21 bundled with
Android Studio, and both modules use `jvmToolchain(21)`. This is exactly what Task 1's probe step
was designed to catch.

**2. Compose Multiplatform 1.12.0 requires AGP 9.1+.** The scaffold had pinned AGP 8.7.3, which
fails with "requires Android Gradle plugin 9.1.0 or higher". Now on AGP 9.4.0, which in turn
required Gradle 9.7.1 — and bumping the wrapper had to be done by editing
`gradle-wrapper.properties` directly, because `./gradlew wrapper` could not itself configure a
build using AGP 9 on Gradle 8.

**3. AGP 9 broke the KMP plugin combination.** Since AGP 9.0, `com.android.library` and
`com.android.application` are incompatible with `org.jetbrains.kotlin.multiplatform`. Google
documents `android.builtInKotlin=false` and `android.newDsl=false` as the supported bypass, and
that is what is set. The long-term fix is migrating `shared` to the
`com.android.kotlin.multiplatform.library` plugin — a DSL rewrite, worth doing when the app is
otherwise stable, not at 4am.

**4. AGP 9 dropped the legacy `sourceSets["main"]` accessor.** The template used it; it now throws
a `ClassCastException`. Removed — with `kotlin.mpp.androidSourceSetLayoutVersion=2`,
`src/androidMain/{AndroidManifest.xml,res}` is already the default.

**5. `xcode-select` points at Xcode 16.0 on macOS 26.5 — and Xcode 26.2 is installed.**
This is the one you should fix by hand. Xcode 16.0's simulator tooling fails on this OS with
`Failed to launch AssetCatalogSimulatorAgent via CoreSimulator spawn`, and no choice of simulator
runtime helps. Builds now force the right toolchain via `DEVELOPER_DIR`, wrapped in
`scripts/ios-build.sh`. **To fix it permanently, run:**

```bash
sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"
```

I could not run that myself — it needs your password.

Also had to install Android SDK platform 37 (AGP 9 requires `compileSdk` >= 37). Note the new
package naming: it is `platforms;android-37.0`, not `platforms;android-37`.

**Landed versions:** Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.4.0, Gradle 9.7.1,
JDK 21, compileSdk 37, minSdk 26, targetSdk 35. The `iosX64` target was dropped — CMP 1.12.0 no
longer publishes Intel simulator artifacts.

**One limitation reached:** the simulator control tooling needs your explicit device permission,
which I cannot grant on your behalf. Visual verification is done with `xcrun simctl` screenshots
instead, which works fine.
