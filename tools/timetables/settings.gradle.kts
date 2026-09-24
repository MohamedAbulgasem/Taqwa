// The website's timetable generator: a build of its own, so the app's build never sees it, run
// with the app's wrapper from the repository root:
//
//     ./gradlew -p tools/timetables generate
//
// It reads the app's version catalog, so Kotlin, adhan2 and kotlinx-datetime are always exactly
// the versions the app ships with.
rootProject.name = "timetables"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
