package world.taqwa.timetables

import java.io.File

/** Where the app's files are, handed to the tests by the build (see `build.gradle.kts`). */
object TestPaths {
    val repoRoot: File = File(System.getProperty("taqwa.repoRoot") ?: error("taqwa.repoRoot not set"))
    val appResources: File = repoRoot.resolve("shared/src/commonMain/composeResources")
    val appFiles: File = appResources.resolve("files")
}
