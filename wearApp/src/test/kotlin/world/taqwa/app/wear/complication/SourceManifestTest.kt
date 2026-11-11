package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element
import world.taqwa.app.wear.complication.TestXml.file
import world.taqwa.app.wear.complication.TestXml.parse

/**
 * What the system's pickers are told about the sources, and what the Taqwa face asks of them: the
 * watch app's manifest and the face's watch face XML, read as files. Neither is code, so neither
 * would fail a build when they part ways; a slot that names a source which no longer serves its
 * type falls back to its system fallback (nothing, EMPTY) without a word (Task 8, fix round 1).
 */
class SourceManifestTest {

    private val android = "http://schemas.android.com/apk/res/android"
    private val supportedTypes = "android.support.wearable.complications.SUPPORTED_TYPES"
    private val hidden = "android.support.wearable.complications.HIDDEN"

    /** Each source in the manifest by its class name, with its meta-data by name. */
    private val sources: Map<String, Map<String, String>> by lazy {
        val services = parse(file("taqwa.wearManifest", "src/main/AndroidManifest.xml")).getElementsByTagName("service")
        (0 until services.length).associate { i ->
            val service = services.item(i) as Element
            val metas = service.getElementsByTagName("meta-data")
            val meta = (0 until metas.length).associate { j ->
                val m = metas.item(j) as Element
                m.getAttributeNS(android, "name") to m.getAttributeNS(android, "value")
            }
            service.getAttributeNS(android, "name").removePrefix(".complication.") to meta
        }
    }

    private fun typesOf(source: String): List<String> =
        assertNotNull(sources[source], "$source is in the manifest")[supportedTypes].orEmpty().split(',').map { it.trim() }

    private fun isHidden(source: String): Boolean = sources[source]?.get(hidden) == "true"

    @Test
    fun theDayDialIsTheTaqwaFacesAloneAndNoPickerOffersIt() {
        // The dial's ranged value is packed geometry (ComplicationFields.packDial): another face's
        // ranged slot would draw it as a share that stands still and means nothing (spec 2.3).
        assertEquals(listOf("RANGED_VALUE"), typesOf("DayDialSource"))
        assertTrue(isHidden("DayDialSource"), "the dial's source is hidden from every picker")
        assertEquals(listOf("WEIGHTED_ELEMENTS"), typesOf("DaySource"), "the day a person can pick is the weighted one alone")
        for ((source, _) in sources.filterKeys { it != "DayDialSource" }) {
            assertFalse(isHidden(source), "$source is one any face can be given")
        }
    }

    @Test
    fun everyFixedSlotOfTheFaceNamesASourceThatServesItsType() {
        // Task 8 fix round 1's guard, kept beside the screen reader's boxes: slots 1, 3, 6 and 7 have
        // no room now, but the scene still draws the Sun's dial, the Moon's windows and the countdown
        // from their data, so a renamed source or a type a source no longer serves would empty them
        // as quietly as it empties slots 0 and 2.
        val face = parse(file("taqwa.faceXml", "../watchface/src/main/res/raw/watchface.xml"))
        val slots = face.getElementsByTagName("ComplicationSlot")
        val fixed = (0 until slots.length).map { slots.item(it) as Element }.filter { it.getAttribute("isCustomizable") == "FALSE" }
        assertEquals(listOf("0", "1", "2", "3", "6", "7"), fixed.map { it.getAttribute("slotId") }.sorted(), "the face's fixed slots")
        for (slot in fixed) {
            val id = slot.getAttribute("slotId")
            val policy = slot.getElementsByTagName("DefaultProviderPolicy").item(0) as Element
            val provider = policy.getAttribute("primaryProvider")
            val type = policy.getAttribute("primaryProviderType")
            assertTrue(provider.startsWith("world.taqwa.app/world.taqwa.app.wear.complication."), "slot $id: $provider is one of the watch app's sources")
            assertTrue(type in slot.getAttribute("supportedTypes").split(' '), "slot $id takes $type")
            val source = provider.substringAfterLast('.')
            assertTrue(type in typesOf(source), "slot $id: $source serves $type, which the slot asks it for")
        }
    }

    @Test
    fun aScreenReaderIsGivenTheTimeThePrayerTheDatesAndTheWearersSlotsEachOnce() {
        // The runtime gives a screen reader each slot with data at its bounding box, after the
        // time, in the order the slots are declared; the system makes one item of every box, and
        // where two are the same it builds all its items again at every change of any slot's data
        // (the ring's, every second), so TalkBack said the item it was on again every second (Task
        // 12, on the Wear OS 6 emulator). So no two boxes are the same, and none is the face's
        // square, which the Moon's clock has; the slots the scene draws from have no room (0 by 0,
        // each at a place of its own) and are no item; and the two that are read are slot 0, the
        // next prayer, and then slot 2, the dates: on a prayer face the prayer comes first in the
        // swipe (Ruling 42, which withdrew Ruling 40's dates first; a touch is heard on a watch).
        val face = parse(file("taqwa.faceXml", "../watchface/src/main/res/raw/watchface.xml"))
        val slots = face.getElementsByTagName("ComplicationSlot")
        val order = (0 until slots.length).map { slots.item(it) as Element }
        fun n(e: Element, name: String) = e.getAttribute(name).toInt()
        val boxes = order.map { slot ->
            val shape = listOf("BoundingBox", "BoundingOval").map { slot.getElementsByTagName(it) }.first { it.length > 0 }.item(0) as Element
            val x = n(slot, "x") + n(shape, "x")
            val y = n(slot, "y") + n(shape, "y")
            slot.getAttribute("slotId") to listOf(x, y, x + n(shape, "width"), y + n(shape, "height"))
        }
        // A box is cut to the screen before the system compares it, so two that differ only past
        // the face's edge are one: every box lies inside the face (seen when slots 0 and 2 were
        // given 451 and 452).
        assertTrue(boxes.all { (_, b) -> b.all { it in 0..450 } }, "every box lies inside the face: $boxes")
        assertEquals(boxes.size, boxes.map { it.second }.toSet().size, "every box is its own: $boxes")
        assertTrue(boxes.none { it.second == listOf(0, 0, 450, 450) }, "no box is the face's square: $boxes")
        val read = boxes.filter { (_, b) -> b[2] > b[0] && b[3] > b[1] }.map { it.first }
        assertEquals(listOf("0", "2", "4", "5"), read, "the slots a screen reader is given, in its order")
        for ((id, b) in boxes.filter { it.first in setOf("0", "2") }) {
            // within a pixel of the face's square on every side: they draw across the face
            assertTrue(b[0] <= 1 && b[1] <= 1 && b[2] >= 449 && b[3] >= 449, "slot $id spans the face: $b")
        }
    }

    @Test
    fun theWearersSlotsComeLastAndEachHoldsItsOwnPlaceAlone() {
        // The runtime gives a tap to the last slot, in the face's order, whose box holds it, and the
        // fixed slots 0 and 2 span the face: a wearer's slot declared before one of them never had a
        // tap (Task 10's review, when 6 and 7 spanned it too). And a wearer's box is where the editor outlines the
        // slot and where a tap opens its source, so it keeps off the time and the lines above it.
        val face = parse(file("taqwa.faceXml", "../watchface/src/main/res/raw/watchface.xml"))
        val slots = face.getElementsByTagName("ComplicationSlot")
        val order = (0 until slots.length).map { slots.item(it) as Element }
        val wearers = order.filter { it.getAttribute("isCustomizable") == "TRUE" }
        assertEquals(listOf("4", "5"), wearers.map { it.getAttribute("slotId") }, "the wearer's slots")
        val lastFixed = order.indexOfLast { it.getAttribute("isCustomizable") == "FALSE" }
        for (slot in wearers) {
            assertTrue(order.indexOf(slot) > lastFixed, "slot ${slot.getAttribute("slotId")} is declared after every fixed slot")
        }
        val boxes = wearers.map { slot ->
            val box = (slot.getElementsByTagName("BoundingBox").item(0) as Element)
            fun n(e: Element, name: String) = e.getAttribute(name).toInt()
            val x = n(slot, "x") + n(box, "x")
            val y = n(slot, "y") + n(box, "y")
            listOf(x, y, x + n(box, "width"), y + n(box, "height"))
        }
        for ((i, b) in boxes.withIndex()) {
            // below the countdown, the lowest line of the centre (faces.js LAYOUT_COMPS c = 159 units)
            assertTrue(b[1] >= 159 * 2.25 + 4, "slot ${wearers[i].getAttribute("slotId")}'s box starts below the countdown: $b")
        }
        assertTrue(boxes[0][2] <= boxes[1][0], "the two boxes do not overlap: $boxes")
    }
}
