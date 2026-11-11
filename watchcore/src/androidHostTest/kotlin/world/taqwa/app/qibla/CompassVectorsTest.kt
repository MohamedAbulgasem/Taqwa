package world.taqwa.app.qibla

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Keeps `watchcore/vectors/compass-vectors.json` in step with the compass rules the phone and both
 * watches point by ([QiblaAngles], [TrueNorth], [CompassAccuracyRules], [HeadingFilter],
 * [CompassAccuracyGate], [QiblaGuidance], [QiblaGuidanceTracker] and [WatchQibla]), for the Apple
 * Watch's Swift port (plan 4 Task 9), as `TasbeehVectorsTest` keeps the tasbeeh rules'. Every answer
 * in the file is the code's own, worked out here as the test runs, so it cannot hold a wish.
 *
 * The dial's runs are `WatchQiblaTest`'s, every one, the review cases of plan 3's Task 2 among them
 * (Ruling 58 item 4: the turn told at most once in 1,500 ms from the last telling, the left turn's
 * margin as the right's, a searching spell on a boot clock that starts far from 0, a heading that is
 * not a number), and `WatchDialParityTest`'s scripted run on the Cape Town sample's bearing. The
 * tracker's sequences are `QiblaGuidanceTest`'s, a delta that is not finite among them, and the
 * gate's runs are `CompassAccuracyGateTest`'s.
 *
 * The file is rewritten with `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`, and
 * the test fails when it differs. Read the diff before you commit it.
 */
class CompassVectorsTest {

    @Test
    fun compassVectorsAreUpToDate() {
        val directory = if (File("src/androidHostTest").isDirectory) File("vectors") else File("watchcore/vectors")
        val file = File(directory, FILE)
        if (System.getProperty("taqwa.updateVectors") != null) {
            directory.mkdirs()
            file.writeText(vectorsText)
            return
        }
        if (!file.exists()) fail("${file.path} is missing. $HOW_TO_WRITE")
        if (file.readText() != vectorsText) {
            val found = file.readText().lines()
            val wanted = vectorsText.lines()
            val line = (0 until maxOf(found.size, wanted.size)).first { found.getOrNull(it) != wanted.getOrNull(it) }
            fail("${file.path} is not what the rules answer, first at line ${line + 1}:\n  file:  ${found.getOrNull(line)}\n  rules: ${wanted.getOrNull(line)}\n$HOW_TO_WRITE")
        }
    }

    /**
     * The dial's runs reach every state the watch draws, both turns, both reasons, a buzz, a reset
     * and a heading that is not a number, so a port held to them is held in each.
     */
    @Test
    fun theDialRunsReachEveryStateBothTurnsAndBothReasons() {
        val steps = vectors.getValue("dial").jsonArray.flatMap { it.jsonObject.getValue("steps").jsonArray.map { step -> step.jsonObject } }
        val states = steps.filter { it.string("do") == "read" }.map { it.getValue("state").jsonObject }
        assertEquals(setOf("Searching", "Aligned", "Low", "BestEffort"), states.map { it.string("kind") }.toSet())
        assertEquals(setOf("LEFT", "RIGHT"), states.mapNotNull { it["turn"]?.jsonPrimitive?.content }.toSet())
        assertEquals(setOf("CALIBRATION", "INTERFERENCE"), states.mapNotNull { it["reason"]?.jsonPrimitive?.content }.toSet())
        assertTrue(states.any { it["kind"]?.jsonPrimitive?.content == "Searching" && it["heading"]?.jsonPrimitive?.content == "NaN" }, "a heading that is not a number")
        assertTrue(steps.any { it.string("do") == "reset" }, "a reset")
        assertTrue(steps.count { it["buzz"]?.jsonPrimitive?.content == "true" } >= 5, "buzzes")
    }

    /**
     * Ruling 58 item 4: the searching spells on a boot clock that starts far from 0 tell a new
     * turn only 1,500 ms after the last telling, to the right and to the left alike.
     */
    @Test
    fun theSearchingSpellsRunOnABootClockFarFromNought() {
        for (name in listOf("searchingTellsANewTurnOnceInOneAndAHalfSecondsAndOnlyPastItsMargin", "searchingTellsALeftTurnByTheSameClockAndMarginAsARightOne")) {
            val run = vectors.getValue("dial").jsonArray.map { it.jsonObject }.single { it.string("name") == name }
            val reads = run.getValue("steps").jsonArray.map { it.jsonObject }.filter { it.string("do") == "read" }
            assertEquals(10_000L, reads.first().getValue("reading").jsonObject.getValue("t").jsonPrimitive.long, name)
            val told = reads.zipWithNext().filter { (a, b) -> a.turnTold() != b.turnTold() }.map { (_, b) -> b.getValue("reading").jsonObject.getValue("t").jsonPrimitive.long }
            assertEquals(listOf(11_500L, 13_000L), told, name)
        }
    }

    private fun JsonObject.turnTold(): String {
        val state = getValue("state").jsonObject
        return "${state["turn"]?.jsonPrimitive?.content} ${state["degrees"]?.jsonPrimitive?.content}"
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        const val FILE = "compass-vectors.json"
        const val HOW_TO_WRITE = "To write it again: ./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors, and read the diff."
        const val STEP_MS = 50L

        val ABOUT = "Test vectors for the Apple Watch's Swift port of the compass rules in :watchcore's qibla package (QiblaAngles, " +
            "TrueNorth, CompassAccuracyRules, HeadingFilter, CompassAccuracyGate, QiblaGuidance, QiblaGuidanceTracker and " +
            "WatchQibla, the phone's own), written by CompassVectorsTest.kt from the code's answers (plan 4, Task 9). " +
            "A number that is not finite is written as the string \"NaN\", \"Infinity\" or \"-Infinity\"; every other number is a " +
            "JSON number. angleDelta: QiblaAngles.angleDelta(a, b). isAligned: QiblaAngles.isAligned(heading, bearing), with " +
            "wasAligned the three-argument call (Dev 1's B40). trueNorth: TrueNorth.fromMagneticOrInvalid(magnetic, declination), " +
            "declination null for none; correct: TrueNorth.correct. iosSampleIsLow: CompassAccuracyRules.iosSampleIsLow(" +
            "trueHeading, accuracy). filter: HeadingFilter(smoothing) sequences, smoothing null for the default; each step " +
            "updates with raw and gives out, or resets. gate: CompassAccuracyGate() runs, each step an update(t, low, reason) or " +
            "a reset, with the state it leaves (kind Good, Low or BestEffort, and reason). guidanceOf: QiblaGuidance.of(delta). " +
            "guidanceTracker: QiblaGuidanceTracker() sequences, each step an update(t, delta) or a reset, with the guidance it " +
            "answers or null. dial: WatchQibla(bearing, HeadingFilter(smoothing)) runs, smoothing null for the default, each step " +
            "a reading ({heading, t, low, reason?}) or a reset, with the DialStep it answers: the state (kind Searching, Aligned, " +
            "Low or BestEffort; heading, turn and degrees, reason) and buzz. A step starts from what the step before left. " +
            "Nobody edits this file: change the rules, then rewrite it with ./gradlew :watchcore:testAndroidHostTest " +
            "-Ptaqwa.updateVectors and commit the result."

        val vectorsText: String by lazy { compassVectors() }

        val vectors: JsonObject by lazy { Json.parseToJsonElement(vectorsText).jsonObject }

        /** A number as the file writes it: a JSON number, or one of the three names of a number that is not finite. */
        fun number(value: Double): JsonPrimitive = if (value.isFinite()) JsonPrimitive(value) else JsonPrimitive(value.toString())

        fun number(value: Double?): JsonElement = value?.let { number(it) } ?: JsonNull

        // --- QiblaAngles, TrueNorth and the iOS rules ---------------------------------------------

        val deltaPairs = listOf(
            350.0 to 10.0, 10.0 to 350.0, 0.0 to 180.0, 180.0 to 0.0, 359.0 to 1.0, 1.0 to 359.0, 0.0 to 0.0,
            23.35 to 18.35, 340.0 to 23.35, 23.35 to 340.0, -10.0 to 10.0, 720.0 to 10.0, 10.0 to 720.0,
            190.0 to 10.0, 10.0 to 190.0, 179.9 to -179.9, 359.99 to 0.01, 0.01 to 359.99, 23.0 to 203.0, 203.0 to 23.0,
        )

        fun angleDeltaLine(pair: Pair<Double, Double>): String = buildJsonObject {
            put("a", number(pair.first))
            put("b", number(pair.second))
            put("delta", number(QiblaAngles.angleDelta(pair.first, pair.second)))
        }.toString()

        /** The headings 4.99, 5.0 and 5.01 off, and with memory 6.99, 7.0 and 7.01 off and 4.99 and 5.01 without, either way of two bearings, the second across the seam. */
        fun isAlignedLines(): List<String> = listOf(23.0, 1.0).flatMap { bearing ->
            val plain = listOf(4.99, 5.0, 5.01).flatMap { off -> listOf(bearing + off, bearing - off) }.map { heading ->
                buildJsonObject {
                    put("heading", number(heading))
                    put("bearing", number(bearing))
                    put("aligned", QiblaAngles.isAligned(heading, bearing))
                }.toString()
            }
            val remembered = (listOf(6.99, 7.0, 7.01).map { it to true } + listOf(4.99, 5.01).map { it to false }).flatMap { (off, was) ->
                listOf(bearing + off, bearing - off).map { heading ->
                    buildJsonObject {
                        put("heading", number(heading))
                        put("bearing", number(bearing))
                        put("wasAligned", was)
                        put("aligned", QiblaAngles.isAligned(heading, bearing, was))
                    }.toString()
                }
            }
            plain + remembered
        }

        /** Cape Town's sample (declination -27, bearing 23): magnetic 50 is true 23, 25 is true 358, 7 is 340 and 147 is 120. */
        val trueNorthCases: List<Pair<Double, Double?>> = listOf(
            48.0 to -25.0, 50.0 to -27.0, 25.0 to -27.0, 7.0 to -27.0, 147.0 to -27.0, 0.0 to -27.0,
            -1.0 to -27.0, -0.5 to -27.0, 10.0 to null, -1.0 to null, 350.0 to 25.0, 0.0 to 0.0, 359.5 to 0.5, 359.9 to 3.0,
        )

        fun trueNorthLine(case: Pair<Double, Double?>): String = buildJsonObject {
            put("magnetic", number(case.first))
            put("declination", number(case.second))
            put("heading", number(TrueNorth.fromMagneticOrInvalid(case.first, case.second)))
        }.toString()

        val correctCases = listOf(-30.0 to 10.0, 720.0 to 0.0, 10.0 to -370.0, 0.0 to 360.0, 23.0 to 0.0)

        fun correctLine(case: Pair<Double, Double>): String = buildJsonObject {
            put("magnetic", number(case.first))
            put("declination", number(case.second))
            put("heading", number(TrueNorth.correct(case.first, case.second)))
        }.toString()

        val lownessCases = listOf(
            23.0 to 45.0, 23.0 to 45.01, 23.0 to -1.0, -1.0 to 10.0, 0.0 to 0.0, 359.9 to 44.99, -0.01 to 10.0, 23.0 to 0.0,
        )

        fun lownessLine(case: Pair<Double, Double>): String = buildJsonObject {
            put("trueHeading", number(case.first))
            put("accuracy", number(case.second))
            put("low", CompassAccuracyRules.iosSampleIsLow(case.first, case.second))
        }.toString()

        // --- HeadingFilter ------------------------------------------------------------------------

        /** A filter's step: a raw heading, or null for a reset. */
        class FilterRun(val name: String, val smoothing: Double?, val raws: List<Double?>)

        val filterRuns = listOf(
            FilterRun("theFirstReadingPassesAsItIs", null, listOf(90.0)),
            FilterRun("aNewReadingIsApproachedFifteenPerCentAtATime", null, listOf(0.0) + List(6) { 100.0 }),
            FilterRun("acrossTheSeamUpward", null, listOf(359.0) + List(10) { 1.0 }),
            FilterRun("acrossTheSeamDownward", null, listOf(1.0) + List(10) { 359.0 }),
            FilterRun("aHalfTurnGoesClockwise", null, listOf(0.0, 180.0, 180.0, 180.0)),
            FilterRun("aNegativeFirstReadingPassesAsItIs", null, listOf(-1.0, 23.0, 23.0)),
            FilterRun("aHeadingThatIsNotANumberIsKept", null, listOf(23.0, Double.NaN, 23.0)),
            FilterRun("resetForgetsThePreviousReading", null, listOf(90.0, 90.0, null, 10.0)),
            FilterRun("aFilterThatDoesNotSmooth", 1.0, listOf(340.0, 300.0, 3.35, 358.35)),
            FilterRun("aFilterOfAThird", 0.3, listOf(350.0) + List(5) { 10.0 }),
        )

        fun filterJson(run: FilterRun): String {
            val filter = run.smoothing?.let { HeadingFilter(it) } ?: HeadingFilter()
            val steps = run.raws.map { raw ->
                if (raw == null) {
                    filter.reset()
                    buildJsonObject { put("do", "reset") }.toString()
                } else {
                    buildJsonObject {
                        put("do", "update")
                        put("raw", number(raw))
                        put("out", number(filter.update(raw)))
                    }.toString()
                }
            }
            return runJson(buildJsonObject {
                put("name", run.name)
                put("smoothing", number(run.smoothing))
            }, steps)
        }

        // --- CompassAccuracyGate ------------------------------------------------------------------

        /** A gate's step: an update, or a reset. */
        class GateStep(val t: Long?, val low: Boolean = false, val reason: CompassLowReason? = null)

        fun low(t: Long, reason: CompassLowReason? = CompassLowReason.CALIBRATION) = GateStep(t, low = true, reason = reason)
        fun good(t: Long) = GateStep(t)
        val reset = GateStep(null)

        val calibration = CompassLowReason.CALIBRATION
        val interference = CompassLowReason.INTERFERENCE

        /** `CompassAccuracyGateTest`'s sequences, each a run on a fresh gate. */
        val gateRuns: List<Pair<String, List<GateStep>>> = listOf(
            "aShortBurstOfLowSamplesIsNotWorthATellingOff" to listOf(low(0), low(500), low(999)),
            "aFullSecondOfLowSamplesEntersLow" to listOf(low(0), low(1_000)),
            "aLowSampleWithNoReasonIsCalibration" to listOf(low(0, null), low(1_000, null)),
            "oneGoodSampleRestartsTheLowRun" to listOf(low(0), good(900), low(950), low(1_000), low(1_950)),
            "leavingLowTakesLongerThanEnteringIt" to listOf(low(0), low(1_000), good(1_100), good(2_500), good(2_600)),
            "aLowSampleDuringTheRecoveryDwellRestartsIt" to listOf(low(0), low(1_000), good(1_100), low(2_000), good(2_100), good(3_400), good(3_600)),
            "interferenceOutranksCalibrationWithinOneLowRun" to listOf(low(0), low(500, interference), low(1_000)),
            "aReasonIsForgottenWhenAGoodSampleEndsTheRun" to listOf(low(0, interference), low(1_000, interference), good(1_100), good(2_700), low(5_000), low(6_000)),
            "twentySecondsOfUnbrokenLowSamplesIsBestEffort" to listOf(low(0), low(19_999), low(20_000)),
            "bestEffortCarriesTheReason" to listOf(low(0, interference), low(25_000, interference)),
            "aRecoveredCompassLeavesBestEffortUnderTheSameRuleAsLow" to listOf(low(0), low(20_000), good(21_000), good(22_400), good(22_600)),
            "aBrokenLowRunNeverReachesBestEffort" to (0 until 6).flatMap { block ->
                (0 until 5).map { low(block * 6_000L + it * 1_000L) } + good(block * 6_000L + 5_000L)
            },
            "resetForgetsEverything" to listOf(low(0, interference), low(20_000, interference), reset, low(20_100, interference)),
            "givingUpIsOneWay" to listOf(low(0), low(20_000), good(20_100), low(20_200), low(21_200), low(22_500), good(23_000), good(24_400), good(24_600)),
            "theReasonDeEscalatesWithoutRestartingTheRun" to listOf(low(0, interference), low(1_000, interference), low(1_100), low(1_900), low(2_000), low(20_000)),
            "theReasonEscalatesTheMomentInterferenceAppears" to listOf(low(0), low(1_000), low(1_500, interference), low(2_400)),
            "aTimestampFromBeforeTheRunRestartsIt" to listOf(low(1_000), low(1_500), low(900), low(1_800), low(1_900)),
            "aBackwardsClockDuringRecoveryCostsAnotherDwell" to listOf(low(0), low(1_000), good(3_000), good(1_500), good(2_900), good(3_100)),
        )

        fun stateJson(state: CompassAccuracyState): JsonObject = buildJsonObject {
            when (state) {
                CompassAccuracyState.Good -> put("kind", "Good")
                is CompassAccuracyState.Low -> {
                    put("kind", "Low")
                    put("reason", state.reason.name)
                }
                is CompassAccuracyState.BestEffort -> {
                    put("kind", "BestEffort")
                    put("reason", state.reason.name)
                }
            }
        }

        fun gateJson(run: Pair<String, List<GateStep>>): String {
            val gate = CompassAccuracyGate()
            val steps = run.second.map { step ->
                val t = step.t
                if (t == null) {
                    gate.reset()
                    buildJsonObject {
                        put("do", "reset")
                        put("state", stateJson(gate.state()))
                    }.toString()
                } else {
                    val state = gate.update(t, step.low, step.reason)
                    buildJsonObject {
                        put("do", "update")
                        put("t", t)
                        put("low", step.low)
                        step.reason?.let { put("reason", it.name) }
                        put("state", stateJson(state))
                    }.toString()
                }
            }
            return runJson(buildJsonObject { put("name", run.first) }, steps)
        }

        // --- QiblaGuidance and its tracker --------------------------------------------------------

        val guidanceDeltas = listOf(28.0, -44.0, 6.0, 179.9, -179.9, 0.0, -0.0, 7.4999, 7.5, -7.5, 22.5, 37.5, 172.5, 180.0, -180.0, 52.4, -52.6)

        fun guidanceJson(guidance: Guidance?): JsonElement = guidance?.let {
            buildJsonObject {
                put("turn", it.turn.name)
                put("degrees", it.degrees)
            }
        } ?: JsonNull

        fun guidanceOfLine(delta: Double): String {
            val guidance = QiblaGuidance.of(delta)
            return buildJsonObject {
                put("delta", number(delta))
                put("turn", guidance.turn.name)
                put("degrees", guidance.degrees)
            }.toString()
        }

        /** A tracker's step: an update at [t] with [delta], or a reset when [t] is null. */
        class TrackerStep(val t: Long?, val delta: Double?)

        fun at(t: Long, delta: Double?) = TrackerStep(t, delta)
        val forget = TrackerStep(null, null)

        /** `QiblaGuidanceTest`'s sequences, each on a fresh tracker. */
        val trackerRuns: List<Pair<String, List<TrackerStep>>> = listOf(
            "theTrackerHoldsWhatItSaysAgainstAWobblingHand" to listOf(
                at(0, 31.0), at(100, 34.0), at(200, 27.0), at(500, 46.0), at(1_600, 46.0), at(3_200, 36.0), at(3_300, 33.0),
            ),
            "theTrackerHoldsALeftTurnAgainstAWobblingHandAsItDoesARightOne" to listOf(
                at(0, -31.0), at(100, -34.0), at(200, -27.0), at(500, -46.0), at(1_600, -46.0), at(3_200, -36.0), at(3_300, -33.0),
            ),
            "nullClearsItAndTheNextDeltaIsTakenAtOnce" to listOf(at(0, 31.0), at(100, null), at(200, -88.0)),
            "aDeltaThatIsNotANumberIsNoDirectionAsNullIs" to listOf(
                at(0, Double.NaN), at(100, 31.0), at(200, Double.NaN), at(300, -88.0), at(400, Double.POSITIVE_INFINITY),
                at(500, Double.NEGATIVE_INFINITY), at(600, -88.0),
            ),
            "aSignChangeAfterTheIntervalTurnsLeft" to listOf(at(0, 31.0), at(1_499, -31.0), at(1_500, -31.0)),
            "aStepMovesOnlyMoreThanTenAndAHalfDegreesFromTheOneItSays" to listOf(at(0, 31.0), at(1_500, 40.5), at(1_550, 40.6)),
            "aLeftStepMovesOnlyMoreThanTenAndAHalfDegreesFromTheOneItSaysToo" to listOf(at(0, -31.0), at(1_500, -40.5), at(1_550, -40.6)),
            "theIntervalRunsFromTheLastTakeNotFromZero" to listOf(at(0, 31.0), at(1_600, 46.0), at(2_000, 20.0), at(3_099, 20.0), at(3_100, 20.0)),
            "aHeadingWobblingAcrossTheBackIsSaidOnceInTheInterval" to listOf(
                at(10_000, 179.0), at(10_050, -179.0), at(10_100, 179.0), at(11_499, -179.0), at(11_500, -179.0), at(11_550, 179.0),
            ),
            "resetForgetsWhatItSaidSoTheNextDeltaIsTakenAtOnce" to listOf(at(0, 31.0), forget, at(100, 46.0)),
        )

        fun trackerJson(run: Pair<String, List<TrackerStep>>): String {
            val tracker = QiblaGuidanceTracker()
            val steps = run.second.map { step ->
                val t = step.t
                if (t == null) {
                    tracker.reset()
                    buildJsonObject { put("do", "reset") }.toString()
                } else {
                    val guidance = tracker.update(t, step.delta)
                    buildJsonObject {
                        put("do", "update")
                        put("t", t)
                        put("delta", number(step.delta))
                        put("guidance", guidanceJson(guidance))
                    }.toString()
                }
            }
            return runJson(buildJsonObject { put("name", run.first) }, steps)
        }

        // --- WatchQibla ---------------------------------------------------------------------------

        /** A dial run's readings, one every 50 ms from [start], and its resets (null). */
        class DialRun(val name: String, val bearing: Double, val smoothing: Double? = null, start: Long = 0L) {
            private var at = start
            val steps = mutableListOf<CompassReading?>()

            fun good(heading: Double, millis: Long = STEP_MS) = add(millis) { CompassReading(heading, it, isLowAccuracy = false) }

            fun low(heading: Double, reason: CompassLowReason, millis: Long = STEP_MS) =
                add(millis) { CompassReading(heading, it, isLowAccuracy = true, lowReason = reason) }

            fun reset() = apply { steps += null }

            private fun add(millis: Long, reading: (Long) -> CompassReading) = apply {
                repeat((millis / STEP_MS).toInt()) {
                    steps += reading(at)
                    at += STEP_MS
                }
            }
        }

        /** `WatchQiblaTest`'s runs on its bearing, 23.35, and `WatchDialParityTest`'s scripted run on the Cape Town sample's, 23.0. */
        fun dialRuns(): List<DialRun> {
            val bearing = 23.35
            return listOf(
                DialRun("aGoodHeadingWithinFiveDegreesIsAlignedAndBuzzesOnce", bearing).good(21.0).good(22.0).good(24.0),
                DialRun("leavingAndComingBackBuzzesAgain", bearing).good(21.0).good(60.0, 2_000).good(23.0, 2_000),
                DialRun("onceAlignedItHoldsToSevenDegrees", bearing).good(23.0, 2_000).good(29.5, 3_000).good(31.0, 3_000),
                DialRun("searchingSaysWhichWayToTurnToTheRight", bearing).good(340.0, 1_000),
                DialRun("searchingSaysWhichWayToTurnToTheLeft", bearing).good(60.0, 1_000),
                DialRun("searchingTellsANewTurnOnceInOneAndAHalfSecondsAndOnlyPastItsMargin", bearing, smoothing = 1.0, start = 10_000L)
                    .good(340.0, 500).good(300.0, 950).good(300.0).good(300.0).good(3.35, 1_450).good(3.35).good(358.35, 3_000),
                DialRun("searchingTellsALeftTurnByTheSameClockAndMarginAsARightOne", bearing, smoothing = 1.0, start = 10_000L)
                    .good(66.7, 500).good(106.7, 950).good(106.7).good(106.7).good(43.35, 1_450).good(43.35).good(48.35, 3_000),
                DialRun("anAlignedDialTellsTheTurnAfreshWhenItSearchesAgain", bearing, smoothing = 1.0).good(340.0).good(23.0).good(60.0),
                DialRun("aLowDialTellsTheTurnAfreshWhenItSearchesAgain", bearing, smoothing = 1.0)
                    .good(340.0).low(0.0, calibration, 1_050).good(347.35, 1_550),
                DialRun("aSpellOfLowForgetsTheAlignment", bearing).good(23.0).low(23.0, calibration, 1_050).good(23.0, 1_550),
                DialRun("aSecondOfLowSamplesIsLowWithItsReason", bearing).low(0.0, interference, 1_050),
                DialRun("twentySecondsLowIsBestEffortAndStaysUntilGood", bearing)
                    .low(0.0, calibration, 20_050).good(23.0).low(0.0, interference, 2_000).good(23.0, 1_550),
                DialRun("aHeadingThatIsNotANumberSearchesWithNoTurnUntilReset", bearing)
                    .good(23.0).good(Double.NaN).good(23.0).reset().good(23.0),
                DialRun("aLowHeadingThatIsNotANumberInTheGatesFirstSecond", bearing).low(Double.NaN, calibration),
                DialRun("aHeadingThatIsInfiniteIsNoTurn", bearing).good(Double.POSITIVE_INFINITY).good(23.0),
                DialRun("resetForgetsTheHeadingAndTheGuidance", bearing).good(340.0, 500).reset().good(300.0),
                DialRun("resetForgetsTheAlignment", bearing).good(23.0).reset().good(23.0),
                DialRun("resetForgetsTheGate", bearing).low(0.0, calibration, 1_050).reset().good(23.0),
                DialRun("aScriptedRunThroughEveryStateOnCapeTownsBearing", 23.0)
                    .good(200.0, 1_000).good(23.0, 2_000).good(300.0, 2_000).good(23.0, 2_000)
                    .low(23.0, calibration, 1_200).good(23.0, 2_000).low(23.0, interference, 21_000).good(23.0, 3_000),
            )
        }

        fun dialStateJson(state: DialState): JsonObject = buildJsonObject {
            when (state) {
                DialState.NoSensor -> put("kind", "NoSensor")
                is DialState.Searching -> {
                    put("kind", "Searching")
                    put("heading", number(state.heading))
                    state.guidance?.let {
                        put("turn", it.turn.name)
                        put("degrees", it.degrees)
                    }
                }
                is DialState.Aligned -> {
                    put("kind", "Aligned")
                    put("heading", number(state.heading))
                }
                is DialState.Low -> {
                    put("kind", "Low")
                    put("reason", state.reason.name)
                }
                is DialState.BestEffort -> {
                    put("kind", "BestEffort")
                    put("reason", state.reason.name)
                }
            }
        }

        fun dialJson(run: DialRun): String {
            val dial = run.smoothing?.let { WatchQibla(run.bearing, filter = HeadingFilter(it)) } ?: WatchQibla(run.bearing)
            val steps = run.steps.map { reading ->
                if (reading == null) {
                    dial.reset()
                    buildJsonObject { put("do", "reset") }.toString()
                } else {
                    val step = dial.onReading(reading)
                    buildJsonObject {
                        put("do", "read")
                        put("reading", buildJsonObject {
                            put("heading", number(reading.trueHeadingDegrees))
                            put("t", reading.timestampMillis)
                            put("low", reading.isLowAccuracy)
                            reading.lowReason?.let { put("reason", it.name) }
                        })
                        put("state", dialStateJson(step.state))
                        put("buzz", step.buzz)
                    }.toString()
                }
            }
            return runJson(buildJsonObject {
                put("name", run.name)
                put("bearing", number(run.bearing))
                put("smoothing", number(run.smoothing))
            }, steps)
        }

        // --- The file -----------------------------------------------------------------------------

        /** A run's head, and its steps one a line. */
        fun runJson(head: JsonObject, steps: List<String>): String = buildString {
            append("    ${head.toString().removeSuffix("}")},\"steps\": [\n")
            steps.forEachIndexed { index, step -> append("      $step" + (if (index < steps.lastIndex) "," else "") + "\n") }
            append("    ]}")
        }

        fun StringBuilder.section(name: String, lines: List<String>, last: Boolean = false) {
            appendLine("  \"$name\": [")
            lines.forEachIndexed { index, line ->
                val indented = if (line.startsWith("    ")) line else "    $line"
                appendLine(indented + if (index < lines.lastIndex) "," else "")
            }
            appendLine("  ]" + if (last) "" else ",")
        }

        fun compassVectors(): String = buildString {
            appendLine("{")
            appendLine("  \"about\": ${JsonPrimitive(ABOUT)},")
            appendLine("  \"alignedEnterDegrees\": ${number(QiblaAngles.ALIGNED_ENTER_DEGREES)},")
            appendLine("  \"alignedExitDegrees\": ${number(QiblaAngles.ALIGNED_EXIT_DEGREES)},")
            appendLine("  \"iosMaxHeadingErrorDegrees\": ${number(CompassAccuracyRules.IOS_MAX_HEADING_ERROR_DEGREES)},")
            appendLine("  \"guidanceStep\": ${QiblaGuidance.STEP},")
            section("angleDelta", deltaPairs.map(::angleDeltaLine))
            section("isAligned", isAlignedLines())
            section("trueNorth", trueNorthCases.map(::trueNorthLine))
            section("correct", correctCases.map(::correctLine))
            section("iosSampleIsLow", lownessCases.map(::lownessLine))
            section("filter", filterRuns.map(::filterJson))
            section("gate", gateRuns.map(::gateJson))
            section("guidanceOf", guidanceDeltas.map(::guidanceOfLine))
            section("guidanceTracker", trackerRuns.map(::trackerJson))
            section("dial", dialRuns().map(::dialJson), last = true)
            appendLine("}")
        }
    }
}
