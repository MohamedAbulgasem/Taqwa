import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.inject.Inject

// The Taqwa watch app for Wear OS (spec 2.1): a plain Android application compiled by AGP's
// built-in Kotlin, as :androidApp is. It stores the feed the phone sends and serves it to the
// watch's surfaces; it reads the feed through :watchcore and works no time out for itself.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// ── Store signing ─────────────────────────────────────────────────────────────────
// The phone's rule (androidApp/build.gradle.kts), kept the same in both: the Wear app is signed with
// the phone's upload key, which `keystore.properties` at the repository root names (git-ignored),
// and Play re-signs both. Without the file the release is left unsigned.
// -Ptaqwa.unsignedRelease=true leaves it unsigned whatever the file says, without reading it: every
// release-type build but scripts/release.sh's passes it (plan 5 Task 3, scripts/test-unsigned-release.sh).
// Any value but true or false stops the build, so a misspelt one never signs.
val unsignedRelease: Boolean = when (val value = providers.gradleProperty("taqwa.unsignedRelease").orNull) {
    null, "false" -> false
    "true" -> true
    else -> error("taqwa.unsignedRelease is true or false, not '$value'")
}
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { !unsignedRelease && it.isFile }
    ?.let { file -> Properties().also { props -> file.inputStream().use { props.load(it) } } }

dependencies {
    implementation(project(":watchcore"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.activity.compose)
    implementation(libs.wear.lifecycle.runtime.compose)
    // The complication data sources (src/main/.../complication): the library that serves them,
    // and the dynamic values their ring and countdown are written in.
    implementation(libs.wear.complications.data.source)
    implementation(libs.wear.protolayout.expression)
    // The Tile (src/main/.../tile): its service, the layout it is drawn in, and the adapter that
    // answers the system's requests from a coroutine.
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.concurrent.futures.ktx)
    // The link to the phone (src/main/.../link): Play services' Wear Data Layer, through MessageClient
    // and CapabilityClient alone, the library the phone sends with (plan 2, Tasks 5 and 6).
    implementation(libs.play.services.wearable)
    // The launch screen (Theme.Taqwa.Starting): Wear OS 3 has no system splash screen, so this draws
    // it there, and hands to the system's from API 31.
    implementation(libs.androidx.core.splashscreen)
    // Watch Face Push (src/main/.../face, plan 5 Task 5): the Taqwa face kept current in its slot and
    // made active once, on the wearer's tap. Called only on Wear OS 6, where the system has it.
    implementation(libs.wear.watchfacepush)

    // kotlin.test over JUnit 4, the runner AGP's unit tests use: named outright, because the
    // plain kotlin-test artifact carries no @Test for an Android module to find.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.serialization.json)
    // runTest's virtual time, for the bounds a Play services call that never answers is cut by.
    testImplementation(libs.kotlinx.coroutines.test)
    // The countdown is a dynamic expression that only the watch's system evaluates. The tests read
    // it back as the protobuf it is built from, and evaluate it themselves (DynamicEvaluator).
    testImplementation(libs.wear.protolayout.proto)
    testImplementation(libs.wear.protolayout.protobuf)
    // A complication source run whole at the API levels it branches on (NextPrayerSourceTest,
    // Ruling 74 item 3): Robolectric's Android at 30 and at 33, with the app's resources (below).
    testImplementation(libs.robolectric)
}

android {
    namespace = "world.taqwa.app.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The phone's own id: the Data Layer connects a watch app to the phone app of the same
        // package and signature, so the feed can only reach the watch if the two match (spec 2.1).
        applicationId = "world.taqwa.app"
        // Wear OS 3, so that complications and Tiles reach the older Galaxy watches. The face
        // needs Wear OS 6 and is a module of its own.
        minSdk = 30
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Written by scripts/bump-version.sh: the phone's code with spec 2.1's offset
        // (watch = phone + 100000), so that Play never sees the two clash, and the phone's name.
        // scripts/check-versions.sh fails when they disagree.
        versionCode = 100045
        versionName = "1.6.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 on the code, as on the phone. Resources are kept whole, the phone's lesson
            // (androidApp/build.gradle.kts): shrinking them removed what is reached only by name.
            // proguard-rules.pro holds only rules a failure on a minified build asked for.
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // The store build as R8 makes it, signed with the debug key, so that it installs over the
        // debug build and the debug phone app's Data Layer reaches it (one package, one key). It has
        // the release build's code (src/release, below), none of src/debug's, and is never uploaded.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    // The staging build's seams are the release build's: no feed dropped, no layout lines from the
    // Qibla page, and the compass's line only on request, once its tag is set to DEBUG on the watch
    // (setprop log.tag.TaqwaWearQibla DEBUG, qiblaLogOn), as on a store build.
    sourceSets.getByName("staging").kotlin.srcDir("src/release/kotlin")
    // Robolectric reads the app's merged resources and manifest (its picker previews' words).
    testOptions.unitTests.isIncludeAndroidResources = true
    lint {
        // isShrinkResources = false is chosen, for the reason the release block gives: lint's
        // general advice to shrink them is the one warning it would otherwise give.
        disable += "NotShrinkingResources"
    }
}

/**
 * The debug build's sample feeds, copied byte for byte from :watchcore's committed vectors into a
 * directory of their own, which is the debug build's one extra asset directory.
 */
abstract class SampleFeedAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val samples: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        val into = output.get().asFile
        into.listFiles()?.forEach { it.deleteRecursively() }
        samples.files.forEach { it.copyTo(File(into, it.name), overwrite = true) }
    }
}

// The debug build starts with a sample feed so that the watch has something to show before a phone
// has sent one (src/debug, SampleFeed). The samples are the committed vectors of :watchcore,
// `Fixtures.capeTownSample()` and `capeTownSamples()` as the phone would send them, copied as they
// are, so the file in the APK is the file the Kotlin tests hold to the fixtures. Only those four go:
// the vectors' other files (feed-vectors.json, watch-rules-vectors.json, phone/) are test vectors,
// and test vectors stay out of every APK (Ruling 55 item 6). The release build never sees any.
val sampleFeedAssets = tasks.register<SampleFeedAssets>("sampleFeedAssets") {
    samples.from(rootProject.fileTree("watchcore/vectors") { include("sample-feed-capetown*.json") })
}
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(sampleFeedAssets, SampleFeedAssets::output)
    }
}

/**
 * The Taqwa face the watch app carries as Google's default watch face (plan 5 Task 4, the Lead's
 * call 1): the face's APK as assets/default_watchface.apk, and the validation token Google's
 * validator issues for that very APK (tools/watchface/push-token.sh) as @string/default_wf_token,
 * which the manifest names. The system installs the face into Taqwa's one slot with that token when
 * the app is installed. With no face to bundle, it writes no APK and an empty token, and says so.
 */
abstract class FaceBundleTask : DefaultTask() {
    /** The face's APK: one file, or none. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val face: ConfigurableFileCollection

    /** Why there is no face, for the warning, when [face] is empty. */
    @get:Input
    abstract val noFaceReason: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokenScript: RegularFileProperty

    /** The validators' pins (version and SHA-256) the token script runs: a new validator is a new token. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val validatorPins: RegularFileProperty

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @get:OutputDirectory
    abstract val res: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun bundle() {
        val assetsDir = assets.get().asFile.apply { deleteRecursively(); mkdirs() }
        val values = File(res.get().asFile.apply { deleteRecursively() }, "values").apply { mkdirs() }
        val apks = face.files.toList()
        if (apks.size > 1) throw GradleException("one face is bundled, not ${apks.size}: $apks")
        val apk = apks.singleOrNull()
        val token = if (apk == null) {
            logger.warn("w: :wearApp: the face is not bundled: ${noFaceReason.get()}")
            ""
        } else {
            if (!apk.isFile) throw GradleException("the face to bundle is missing: no file at ${apk.path}")
            apk.copyTo(File(assetsDir, "default_watchface.apk"))
            // The token is made from this very file, the one the app carries: the system refuses the
            // face with a token made from any other.
            val tokenFile = File(temporaryDir, "token").apply { delete() }
            val said = ByteArrayOutputStream()
            val result = exec.exec {
                commandLine("bash", tokenScript.get().asFile.path, apk.path, tokenFile.path)
                standardOutput = said
                errorOutput = said
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw GradleException("push-token.sh issued no token for ${apk.path}:\n$said")
            }
            logger.info(said.toString())
            tokenFile.readText().also { tokenFile.delete() }
        }
        File(values, "face_token.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!-- Written by FaceBundleTask (wearApp/build.gradle.kts): the validation token of the face in
            |     assets/default_watchface.apk, or empty when no face is bundled. Never edited by hand. -->
            |<resources>
            |    <string name="default_wf_token" translatable="false">${xmlString(token)}</string>
            |</resources>
            |""".trimMargin(),
        )
    }

    /** [text] as an Android string resource's value: XML-escaped, and with aapt's own escapes. */
    private fun xmlString(text: String): String {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
        return if (escaped.startsWith("@") || escaped.startsWith("?")) "\\$escaped" else escaped
    }
}

// Which face each build bundles (plan 5 Task 4):
// - -Ptaqwa.faceApk=<path>, when given, for every build: the release build's face, which
//   scripts/release-face.sh signs with the owner's face key, or a face plan 1's tools built
//   (tools/watchface/push-face.sh). A relative path is the repository root's.
// - Otherwise debug and staging bundle :watchface's debug build, signed with the throwaway test key
//   (-Ptaqwa.faceDebugKeystore, which :watchface reads too); without that key, as on CI, they
//   depend on no face build and bundle none.
// - Otherwise the release build bundles none, so that lint, CI, minifyReleaseWithR8 and plan 3's
//   assembleRelease runs still build. -Ptaqwa.storeRelease=true, which only scripts/release.sh and
//   its dry run pass, makes a release without its face fail (checkReleaseFace).
val faceApkGiven: File? = providers.gradleProperty("taqwa.faceApk").orNull?.let { rootProject.file(it) }
val faceDebugKeystore: File = providers.gradleProperty("taqwa.faceDebugKeystore").orNull?.let { File(it) }
    ?: File(System.getProperty("user.home"), ".android/taqwa-face-debug.keystore")
val faceDebugApk = files(rootProject.file("watchface/build/outputs/apk/debug/watchface-debug.apk"))
    .builtBy(":watchface:assembleDebug")
val storeRelease: Boolean = when (val value = providers.gradleProperty("taqwa.storeRelease").orNull) {
    null, "false" -> false
    "true" -> true
    else -> error("taqwa.storeRelease is true or false, not '$value'")
}
val checkReleaseFace = tasks.register("checkReleaseFace") {
    val given = faceApkGiven
    doLast {
        if (given == null || !given.isFile) {
            throw GradleException(
                "The store release carries the Taqwa face, and " +
                    (if (given == null) "no -Ptaqwa.faceApk names it" else "there is no face at ${given.path}") +
                    ": run scripts/release-face.sh, then scripts/release.sh",
            )
        }
    }
}
if (storeRelease) {
    tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach { dependsOn(checkReleaseFace) }
}
androidComponents {
    onVariants { variant ->
        val bundleFace = tasks.register<FaceBundleTask>("bundle${variant.name.replaceFirstChar { it.uppercase() }}Face") {
            tokenScript.set(rootProject.file("tools/watchface/push-token.sh"))
            validatorPins.set(rootProject.file("tools/watchface/lib-validators.sh"))
            noFaceReason.set("")
            when {
                faceApkGiven != null -> face.from(faceApkGiven)
                variant.buildType == "release" ->
                    noFaceReason.set("no -Ptaqwa.faceApk names the release face (scripts/release-face.sh signs it)")
                faceDebugKeystore.isFile -> face.from(faceDebugApk)
                else -> noFaceReason.set("no face debug key at ${faceDebugKeystore.path} (scripts/face-debug-key.sh makes it)")
            }
            if (storeRelease && variant.buildType == "release") dependsOn(checkReleaseFace)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(bundleFace, FaceBundleTask::assets)
        variant.sources.res?.addGeneratedSourceDirectory(bundleFace, FaceBundleTask::res)
    }
}

kotlin {
    jvmToolchain(21)
}

// The tests read the sample feed too (TestFeeds), and Gradle does not count a file outside the
// module as a test input on its own: a regenerated sample would otherwise leave them up to date,
// green from the file before.
val sampleFeed = rootProject.file("watchcore/vectors/sample-feed-capetown.json")
// The debug build's other samples (Arabic, a twelve-hour clock), which SampleFeedTest reads beside it.
val otherSamples = rootProject.fileTree("watchcore/vectors") { include("sample-feed-capetown-*.json") }
// The Tile's far-north nights (TilePlanTest, TileLayoutTest) read one of the phone's golden feeds,
// Tromsø in May, beside the samples (TestFeeds.phone), its lines the phone's Urdu, and its Cape Town
// days in every language (TileLayoutTest, PhoneFeedsTest) the phone's English.
val phoneFeeds = rootProject.fileTree("watchcore/vectors/phone") {
    include("phone-tromso-may-en.json", "phone-capetown-ur24.json", "phone-capetown-en.json")
}
// The longest city the phone can send (LongestCity, read by Today's and the Tile's tests): its
// bundle's cities and their translations.
val phoneCities = rootProject.file("shared/src/commonMain/composeResources/files")
// The Tile's countdown line is fitted in each of the phone's seven languages (TileLayoutTest,
// PhoneWords): the phone's prayer names and its countdown forms.
val phoneStrings = rootProject.file("shared/src/commonMain/composeResources")
val phoneForms = rootProject.file("tools/watchface/phone-forms.json")
// SourceManifestTest holds the manifest's sources to the face's fixed slots, LinkManifestTest its
// link to the contract, and FaceAlwaysOnTest the face's always-on dimming, so these are inputs too.
val wearManifest = file("src/main/AndroidManifest.xml")
// WearManifestTest holds the manifest's permissions to the one list of them.
val wearPermissions = file("permissions.txt")
// LinkManifestTest holds the watch's Data Layer capability to the link's contract, as the manifest's
// listener filters are held.
val wearCapabilities = file("src/main/res/values/wear.xml")
val faceXml = rootProject.file("watchface/src/main/res/raw/watchface.xml")
// FaceStringsTest holds the face's own words (its name and the face editor's names) in seven
// languages, to the names watchface.xml and the face's manifest show, and to the phone's and the
// watch app's words.
val faceRes = rootProject.file("watchface/src/main/res")
val faceManifest = rootProject.file("watchface/src/main/AndroidManifest.xml")
val wearMainActivity = file("src/main/kotlin/world/taqwa/app/wear/app/WearMainActivity.kt")
// The app's own resources (SystemStringsTest holds its strings in seven languages, FontCopiesTest its
// fonts), and what they are held to on the phone: androidApp's strings, :shared's Manrope and the
// phone's haptics (HapticPatternsTest). None is code a build would fail on when the two part.
val wearRes = file("src/main/res")
val phoneRes = rootProject.file("androidApp/src/main/res")
val phoneFonts = rootProject.file("shared/src/commonMain/composeResources/font")
val phoneHaptics = rootProject.file("shared/src/androidMain/kotlin/world/taqwa/app/qibla/Haptics.android.kt")
tasks.withType<Test>().configureEach {
    inputs.dir(wearRes)
        .withPropertyName("wearRes")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree(phoneRes) { include("values*/strings.xml") })
        .withPropertyName("phoneStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // WearManifestTest holds the watch's launcher icon to the phone's.
    inputs.files(fileTree(phoneRes) { include("mipmap-anydpi-v26/ic_launcher*.xml", "drawable/ic_launcher_*.xml", "values/ic_launcher_background.xml") })
        .withPropertyName("phoneIcon")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree(phoneFonts) { include("Manrope-*.ttf") })
        .withPropertyName("phoneFonts")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    inputs.file(phoneHaptics)
        .withPropertyName("phoneHaptics")
        .withPathSensitivity(PathSensitivity.NONE)
    systemProperty("taqwa.wearRes", wearRes.path)
    systemProperty("taqwa.phoneRes", phoneRes.path)
    systemProperty("taqwa.phoneFonts", phoneFonts.path)
    systemProperty("taqwa.phoneHaptics", phoneHaptics.path)
    inputs.file(sampleFeed)
        .withPropertyName("sampleFeed")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs.files(otherSamples)
        .withPropertyName("otherSamples")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    inputs.files(phoneFeeds)
        .withPropertyName("phoneFeeds")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    inputs.files(fileTree(phoneCities) { include("cities.csv", "city-names-*.csv") })
        .withPropertyName("phoneCities")
        .withPathSensitivity(PathSensitivity.NAME_ONLY)
    systemProperty("taqwa.phoneCities", phoneCities.path)
    inputs.file(wearManifest)
        .withPropertyName("wearManifest")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs.file(wearPermissions)
        .withPropertyName("wearPermissions")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs.file(faceXml)
        .withPropertyName("faceXml")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs.file(wearCapabilities)
        .withPropertyName("wearCapabilities")
        .withPathSensitivity(PathSensitivity.NONE)
    inputs.files(fileTree(phoneStrings) { include("values*/strings.xml") })
        .withPropertyName("phoneComposeStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(phoneForms)
        .withPropertyName("phoneForms")
        .withPathSensitivity(PathSensitivity.NONE)
    systemProperty("taqwa.phoneStrings", phoneStrings.path)
    systemProperty("taqwa.phoneForms", phoneForms.path)
    systemProperty("taqwa.sampleFeed", sampleFeed.path)
    systemProperty("taqwa.wearManifest", wearManifest.path)
    systemProperty("taqwa.wearPermissions", wearPermissions.path)
    systemProperty("taqwa.faceXml", faceXml.path)
    inputs.files(fileTree(faceRes) { include("values*/strings.xml") })
        .withPropertyName("faceStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(faceManifest)
        .withPropertyName("faceManifest")
        .withPathSensitivity(PathSensitivity.NONE)
    systemProperty("taqwa.faceRes", faceRes.path)
    systemProperty("taqwa.wearCapabilities", wearCapabilities.path)
    // WearManifestTest holds WearMainActivity's installSplashScreen() before super.onCreate.
    inputs.file(wearMainActivity)
        .withPropertyName("wearMainActivity")
        .withPathSensitivity(PathSensitivity.NONE)
    systemProperty("taqwa.wearMainActivity", wearMainActivity.path)
}

// QiblaInkTest holds the Qibla page's line spacing to the ink of the words the phone sends there:
// :shared's strings in its seven languages and its watch phrases (the tilt notice).
val sharedStrings = rootProject.file("shared/src/commonMain/composeResources")
val watchWords = rootProject.file("shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchWords.kt")
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(sharedStrings) { include("values*/strings.xml") })
        .withPropertyName("sharedStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(watchWords)
        .withPropertyName("watchWords")
        .withPathSensitivity(PathSensitivity.NONE)
    systemProperty("taqwa.sharedStrings", sharedStrings.path)
    systemProperty("taqwa.watchWords", watchWords.path)
}
