package world.taqwa.app.tasbeeh

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import world.taqwa.app.watch.WatchScreenWords
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Keeps `watchcore/vectors/tasbeeh-vectors.json` in step with the tasbeeh rules the phone and both
 * watches count by ([TasbeehEngine], [TasbeehPresets]), for the Apple Watch's Swift port (plan 4
 * Task 8), as `VectorsTest` keeps the rules' vectors. The file holds the seven built-ins in the
 * phone's order, with their Arabic, transliterations and parts, the constants the port copies, and
 * sequences of taps, turns back, next rounds and resets, each step with the state it leaves and
 * what a tap did. Every answer in it is the engine's own, so it cannot hold a wish.
 *
 * The file is rewritten with `./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors`, and
 * the test fails when it differs. Read the diff before you commit it.
 */
class TasbeehVectorsTest {

    @Test
    fun tasbeehVectorsAreUpToDate() {
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
     * The undo the watches take a count back by, as plan 4 Task 8 pins it: one count less within
     * the round, the count read as the counter shows it (held to the target); nothing at 0; the
     * round never changed. Every turn back in the file is held to it, so a change to
     * [TasbeehEngine.undo] that breaks it fails here before it reaches a vector.
     */
    @Test
    fun everyTurnBackTakesOneCountWithinTheRound() {
        var seen = 0
        for (sequence in sequences()) {
            val total = sequence.getValue("preset").jsonObject.getValue("parts").jsonArray.sumOf { it.jsonPrimitive.int }
            var count = sequence.getValue("start").jsonObject.int("count")
            var round = sequence.getValue("start").jsonObject.int("round")
            for (step in sequence.getValue("steps").jsonArray.map { it.jsonObject }) {
                if (step.string("do") == "undo") {
                    seen++
                    val shown = minOf(count, total)
                    val name = sequence.string("name")
                    assertEquals(round, step.int("round"), "$name: a turn back never changes the round")
                    if (shown > 0) {
                        assertEquals(shown - 1, step.int("count"), "$name: one count less")
                        assertTrue(step.getValue("undone").jsonPrimitive.boolean, name)
                    } else {
                        assertEquals(count, step.int("count"), "$name: nothing at 0")
                        assertEquals(false, step.getValue("undone").jsonPrimitive.boolean, name)
                    }
                }
                count = step.int("count")
                round = step.int("round")
            }
        }
        assertTrue(seen >= 15, "the file turns back $seen times")
    }

    /**
     * The built-ins in the file are [TasbeehPresets.builtIn] in its order, the order the words'
     * names are sent in ([WatchScreenWords.ENGLISH]'s `presets`, which the feed's `words` vectors
     * carry as `presetIds`): so the watch's phrase list, held to this file, lists them as the phone
     * does (plan 4 erratum p4 #13).
     */
    @Test
    fun theBuiltInsAreThePhonesInItsOrder() {
        val ids = vectors.getValue("builtIn").jsonArray.map { it.jsonObject.string("id") }
        assertEquals(TasbeehPresets.builtIn.map { it.id }, ids)
        assertEquals(WatchScreenWords.ENGLISH.presets.keys.toList(), ids)
        assertEquals(TasbeehPresets.DEFAULT_ID, ids.first())
    }

    private fun sequences(): List<JsonObject> = vectors.getValue("sequences").jsonArray.map { it.jsonObject }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private companion object {
        const val FILE = "tasbeeh-vectors.json"
        const val HOW_TO_WRITE = "To write it again: ./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors, and read the diff."

        val ABOUT = "Test vectors for the Apple Watch's Swift port of the tasbeeh rules (TasbeehEngine and TasbeehPresets in " +
            ":watchcore, the phone's own), written by TasbeehVectorsTest.kt from the engine's answers (plan 4, Task 8). " +
            "builtIn: the seven presets in the phone's order, each part's dhikr id, Arabic, transliteration and count; " +
            "defaultId, setCompleteHoldMs and maxTarget are the constants of the same names. " +
            "sequences: a preset ({id, parts: [count], custom}), a start ({count, round}) and steps, each one action: " +
            "tap (TasbeehEngine.tap), undo (TasbeehEngine.undo, a turn back: undone is false where it answered null and the " +
            "state stays), nextRound or reset. Each step gives the state it leaves (count, round) and the part on screen " +
            "after it (TasbeehEngine.currentPart); a tap also gives its event: tick, part:<index> or set. " +
            "A step starts from the state the step before left. A start may hold a count above its target (a target lowered " +
            "on the phone): a tap there opens the next round at 1, and a turn back takes one from the target. " +
            "Nobody edits this file: change the rules, then rewrite it with " +
            "./gradlew :watchcore:testAndroidHostTest -Ptaqwa.updateVectors and commit the result."

        val vectorsText: String by lazy { tasbeehVectors() }

        val vectors: JsonObject by lazy { Json.parseToJsonElement(vectorsText).jsonObject }

        private sealed interface Action {
            val name: String
        }

        private data object Tap : Action {
            override val name = "tap"
        }

        private data object Undo : Action {
            override val name = "undo"
        }

        private data object NextRound : Action {
            override val name = "nextRound"
        }

        private data object Reset : Action {
            override val name = "reset"
        }

        private class Sequence(val name: String, val preset: TasbeehPreset, val count: Int, val round: Int, val actions: List<Action>)

        private fun builtIn(id: String) = TasbeehPresets.builtIn.single { it.id == id }

        private fun taps(n: Int): List<Action> = List(n) { Tap }

        private fun sequenceList(): List<Sequence> {
            val set = TasbeehPresets.default
            val subhanallah = builtIn("subhanallah")
            return listOf(
                Sequence("theSetFrom0To101Taps", set, 0, 1, taps(101)),
                Sequence("undoAt0", set, 0, 1, listOf(Undo, Undo, Tap)),
                Sequence("undoAt0OfALaterRound", set, 0, 3, listOf(Undo, Tap)),
                Sequence("undoAt33", set, 33, 1, listOf(Undo, Tap)),
                Sequence("undoAt34", set, 34, 1, listOf(Undo, Undo, Tap, Tap)),
                Sequence("undoAt66", set, 66, 1, listOf(Undo, Tap)),
                Sequence("undoAt100", set, 100, 1, listOf(Undo, Tap)),
                Sequence("nextRoundAfterTheSet", set, 99, 1, listOf(Tap, NextRound, Tap, Undo)),
                Sequence("aTapAt100", set, 100, 2, listOf(Tap)),
                Sequence("subhanallahsHundred", subhanallah, 0, 1, taps(101)),
                Sequence("aTapAboveTheTarget", subhanallah, 150, 1, listOf(Tap)),
                Sequence("aTurnBackAboveTheTarget", subhanallah, 150, 1, listOf(Undo, Tap)),
                Sequence("hisSeven", TasbeehPresets.custom("custom_7", "Seven", 7), 0, 1, taps(8) + listOf(Undo, Undo)),
                Sequence("hisOne", TasbeehPresets.custom("custom_1", "One", 1), 0, 1, listOf(Tap, Tap, Undo, Undo, Tap)),
                Sequence("hisNought", TasbeehPresets.custom("custom_0", "Nought", 0), 0, 1, listOf(Tap, Undo, NextRound, Reset)),
                Sequence("resetFrom64OfRound3", set, 64, 3, listOf(Reset, Tap)),
            )
        }

        private fun eventName(event: TapEvent): String = when (event) {
            TapEvent.Tick -> "tick"
            is TapEvent.PartComplete -> "part:${event.partIndex}"
            TapEvent.SetComplete -> "set"
        }

        private fun sequenceJson(sequence: Sequence): String {
            var state = TasbeehState(sequence.preset.id, sequence.count, sequence.round)
            val steps = sequence.actions.map { action ->
                var event: TapEvent? = null
                var undone: Boolean? = null
                state = when (action) {
                    Tap -> TasbeehEngine.tap(state, sequence.preset).also { event = it.second }.first
                    Undo -> TasbeehEngine.undo(state, sequence.preset).also { undone = it != null } ?: state
                    NextRound -> TasbeehEngine.nextRound(state)
                    Reset -> TasbeehEngine.reset(state)
                }
                buildJsonObject {
                    put("do", action.name)
                    put("count", state.count)
                    put("round", state.round)
                    put("part", TasbeehEngine.currentPart(state, sequence.preset))
                    event?.let { put("event", eventName(it)) }
                    undone?.let { put("undone", it) }
                }.toString()
            }
            val head = buildJsonObject {
                put("name", sequence.name)
                put("preset", buildJsonObject {
                    put("id", sequence.preset.id)
                    put("parts", buildJsonArray { sequence.preset.parts.forEach { add(JsonPrimitive(it.count)) } })
                    put("custom", sequence.preset.custom)
                })
                put("start", buildJsonObject {
                    put("count", sequence.count)
                    put("round", sequence.round)
                })
            }.toString().removeSuffix("}")
            return buildString {
                append("    $head,\"steps\": [\n")
                steps.forEachIndexed { index, step -> append("      $step" + (if (index < steps.lastIndex) "," else "") + "\n") }
                append("    ]}")
            }
        }

        private fun builtInJson(preset: TasbeehPreset): String = buildJsonObject {
            put("id", preset.id)
            put("parts", JsonArray(preset.parts.map { part ->
                buildJsonObject {
                    put("dhikr", part.dhikr.id)
                    put("arabic", part.dhikr.arabic)
                    put("transliteration", part.dhikr.transliteration)
                    put("count", part.count)
                }
            }))
        }.toString()

        private fun tasbeehVectors(): String = buildString {
            appendLine("{")
            appendLine("  \"about\": ${JsonPrimitive(ABOUT)},")
            appendLine("  \"defaultId\": ${JsonPrimitive(TasbeehPresets.DEFAULT_ID)},")
            appendLine("  \"setCompleteHoldMs\": ${TasbeehEngine.SET_COMPLETE_HOLD_MS},")
            appendLine("  \"maxTarget\": ${TasbeehPresets.MAX_TARGET},")
            appendLine("  \"builtIn\": [")
            TasbeehPresets.builtIn.forEachIndexed { index, preset ->
                appendLine("    ${builtInJson(preset)}" + if (index < TasbeehPresets.builtIn.lastIndex) "," else "")
            }
            appendLine("  ],")
            appendLine("  \"sequences\": [")
            val sequences = sequenceList()
            sequences.forEachIndexed { index, sequence ->
                appendLine(sequenceJson(sequence) + if (index < sequences.lastIndex) "," else "")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
}
