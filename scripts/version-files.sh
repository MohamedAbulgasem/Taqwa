# The files that carry the app's version, and the one list of them: scripts/bump-version.sh writes
# them and scripts/check-versions.sh checks that they agree. Both source this file from the
# repository's root, so the paths are from there.
#
# The phone (androidApp) holds the release's name N and code C. The others follow it
# (scripts/bump-version.sh says why):
#   - the Galaxy watch app (wearApp): N and WEAR_OFFSET + C (spec 2.1);
#   - the Taqwa face (watchface): N and C;
#   - every plist in IOS_PLISTS, the iPhone app's and its extensions': N and C (spec 4.1);
#   - every language's settings_version_value, the name Settings shows: N.
PHONE_GRADLE=androidApp/build.gradle.kts
WEAR_GRADLE=wearApp/build.gradle.kts
FACE_GRADLE=watchface/build.gradle.kts
# Spec 2.1's 100000, which plan 5's Tasks 4, 5 and 7 rely on: scripts/test-bump-version.sh holds it.
WEAR_OFFSET=100000

# Every Info.plist under iosApp/ that carries a CFBundleVersion: the iPhone app, its widget, the
# Apple Watch app and the watch's widgets (plan 4, Task 4). check-versions.sh fails on a plist that
# carries a version and is not listed.
IOS_PLISTS=(
  iosApp/iosApp/Info.plist
  iosApp/TaqwaWidget/Info.plist
  iosApp/TaqwaWatch/Info.plist
  iosApp/TaqwaWatchWidgets/Info.plist
)

# The resource system needs the version string in every language.
COMPOSE_STRINGS=(shared/src/commonMain/composeResources/values*/strings.xml)
