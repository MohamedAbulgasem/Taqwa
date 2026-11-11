package world.taqwa.app.wear.complication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.w3c.dom.Element
import world.taqwa.app.wear.complication.TestXml.file
import world.taqwa.app.wear.complication.TestXml.parse

/**
 * The Taqwa face's always-on frame, read from its watch face XML as a file. Every dimming there is a
 * Part's or a Group's alpha in an AMBIENT Variant (Ruling 25: the runtime ignores a Variant inside a
 * Font or a Stroke), so what the frame lights is held here by where the Variants are. Google's
 * limit is 15% of the screen lit. It is measured on the emulator (spec 3.4, tools/watchface/lit.py)
 * with the project's own threshold for a lit pixel, any channel over 8 (plan 1, Task 13).
 */
class FaceAlwaysOnTest {

    @Test
    fun theMoonWindowsFillIsBlackAlwaysOnAndItsHairlineStays() {
        // Ruling 46: the moon window's near-black fill, #070908, is lit by the measure (its 9 is over 8),
        // and it was 2.0-2.3 points of the Moon's always-on share, which reached 15.5% just after Fajr
        // and Isha with both slots filled (plan 1 Task 13). Always-on it is black: the fill alone sits
        // in a PartDraw of its own with an AMBIENT alpha 0 (nothing is drawn under the window
        // always-on), and the window's hairline is a second PartDraw of the same rectangle, drawn over
        // it as before and kept always-on. Awake, both are drawn at full alpha, so nothing changes.
        val face = parse(file("taqwa.faceXml", "../watchface/src/main/res/raw/watchface.xml"))
        val fills = elements(face, "Fill").filter { it.getAttribute("color") == "#FF070908" && (it.parentNode as Element).tagName == "RoundRectangle" }
        assertEquals(2, fills.size, "the moon window's fill, in the window and in the risen window of option (d)")
        for (fill in fills) {
            val window = fill.parentNode as Element
            val part = window.parentNode as Element
            val where = "the window at y=${window.getAttribute("y")}"
            assertEquals("PartDraw", part.tagName, "$where: its fill is a PartDraw's")
            assertEquals(emptyList(), children(window, "Stroke").map { it.getAttribute("color") }, "$where: the fill's rectangle has no stroke, which would go dark with it")
            assertEquals(listOf("RoundRectangle", "Variant"), children(part).map { it.tagName }, "$where: the fill's PartDraw holds the fill and its Variant alone")
            assertFalse(part.hasAttribute("alpha"), "$where: the fill is drawn awake as before")
            assertEquals(listOf("AMBIENT alpha 0"), variants(part), "$where: the fill goes black always-on")

            val hairline = next(part)
            assertEquals("PartDraw", hairline.tagName, "$where: the hairline is the next PartDraw")
            assertEquals(emptyList(), variants(hairline), "$where: the hairline stays always-on")
            assertEquals(listOf("RoundRectangle"), children(hairline).map { it.tagName }, "$where: the hairline is the window's own shape alone")
            val rect = children(hairline).single()
            for (name in listOf("x", "y", "width", "height", "cornerRadiusX", "cornerRadiusY")) {
                assertEquals(window.getAttribute(name), rect.getAttribute(name), "$where: the hairline's $name")
            }
            assertEquals(emptyList(), children(rect, "Fill").map { it.getAttribute("color") }, "$where: the hairline has no fill of its own")
            val stroke = children(rect, "Stroke").single()
            assertEquals("#FF232825", stroke.getAttribute("color"), "$where: the hairline's colour")
            assertEquals("2.25", stroke.getAttribute("thickness"), "$where: the hairline's thickness")
            assertFalse(hairline.hasAttribute("alpha"), "$where: the hairline is drawn awake as before")

            // The fill's and the hairline's PartDraws lie in the same groups, so an AMBIENT Variant on
            // any of them, up to the Moon's windows group under the scene, would dim the hairline (and
            // the moon) with the fill, whatever each PartDraw's own Variants say (Ruling 49).
            for (group in ancestors(part)) {
                assertEquals(
                    emptyList(),
                    variants(group).filter { it.startsWith("AMBIENT ") },
                    "$where: ${"${group.tagName} ${group.getAttribute("name")}".trim()} above it carries no always-on Variant",
                )
            }
        }
    }

    /** The elements [e] lies in, from its parent up to the scene's own child (the scene left out). */
    private fun ancestors(e: Element): List<Element> =
        generateSequence(e.parentNode as Element) { it.parentNode as? Element }.takeWhile { it.tagName != "Scene" }.toList()

    private fun elements(root: Element, tag: String): List<Element> {
        val nodes = root.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun children(e: Element, tag: String? = null): List<Element> =
        (0 until e.childNodes.length).map { e.childNodes.item(it) }.filterIsInstance<Element>().filter { tag == null || it.tagName == tag }

    private fun variants(e: Element): List<String> =
        children(e, "Variant").map { "${it.getAttribute("mode")} ${it.getAttribute("target")} ${it.getAttribute("value")}" }

    private fun next(e: Element): Element {
        var node = e.nextSibling
        while (node != null && node !is Element) node = node.nextSibling
        return checkNotNull(node) { "nothing after ${e.tagName}" }
    }
}
