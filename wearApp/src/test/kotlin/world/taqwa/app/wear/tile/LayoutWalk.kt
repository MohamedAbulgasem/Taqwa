package world.taqwa.app.wear.tile

import androidx.wear.protolayout.expression.proto.DynamicProto
import androidx.wear.protolayout.proto.LayoutElementProto
import androidx.wear.protolayout.proto.ModifiersProto
import androidx.wear.protolayout.proto.TypesProto

/**
 * A Tile's layout read back as the renderer is sent it, in the order it lays its elements out and a
 * screen reader walks them: every element, each Text's words, each element's speech and tap, and
 * every dynamic value the renderer binds (and charges to its quota). Anything else in the layout
 * (an image, an arc, a spannable) throws, so that a layout grown a new kind of element cannot slip
 * past the tests unread.
 */
internal class LayoutWalk(root: LayoutElementProto.LayoutElement) {

    /** One element: its [kind] and its [depth] below the root, and what it says and does. */
    data class Node(
        val kind: String,
        val depth: Int,
        val text: TypesProto.StringProp?,
        val modifiers: ModifiersProto.Modifiers?,
        val children: List<LayoutElementProto.LayoutElement>,
        val element: LayoutElementProto.LayoutElement,
    ) {
        val description: TypesProto.StringProp? get() = modifiers?.takeIf { it.hasSemantics() }?.semantics?.contentDescription
        val state: TypesProto.StringProp? get() = modifiers?.takeIf { it.hasSemantics() }?.semantics?.stateDescription
    }

    val nodes: List<Node> = buildList { walk(root, 0) }

    /** The Texts, in order. */
    val texts: List<Node> get() = nodes.filter { it.kind == "text" }

    /** Every dynamic string the renderer binds: the Texts' and the speech's. */
    val dynamicStrings: List<DynamicProto.DynamicString>
        get() = nodes.flatMap { node ->
            listOfNotNull(node.text, node.description, node.state).filter { it.hasDynamicValue() }.map { it.dynamicValue }
        }

    /** Every dynamic condition the renderer binds: whether an element is shown (`Modifiers.visible`). */
    val dynamicBools: List<DynamicProto.DynamicBool>
        get() = nodes.mapNotNull { node -> node.modifiers?.takeIf { it.hasVisible() && it.visible.hasDynamicValue() }?.visible?.dynamicValue }

    /**
     * What a renderer may charge this layout: each binding's nodes, its fixed values too (as Google
     * documents, "Wear OS considers constant expressions to be dynamic expressions, too", though
     * the emulator's renderer charges only the operations, P3.8 Step 0), and one more for the
     * binding itself, which a renderer may or may not count. The strictest count there is. A
     * hidden element's values are bound and charged as well.
     */
    val quotaCost: Int get() = dynamicStrings.sumOf { DynamicNodes.of(it).all + 1 } + dynamicBools.sumOf { DynamicNodes.of(it).all + 1 }

    private fun MutableList<Node>.walk(element: LayoutElementProto.LayoutElement, depth: Int) {
        val node = when {
            element.hasBox() -> Node("box", depth, null, element.box.modifiers.takeIf { element.box.hasModifiers() }, element.box.contentsList, element)
            element.hasRow() -> Node("row", depth, null, element.row.modifiers.takeIf { element.row.hasModifiers() }, element.row.contentsList, element)
            element.hasColumn() -> Node("column", depth, null, element.column.modifiers.takeIf { element.column.hasModifiers() }, element.column.contentsList, element)
            element.hasText() -> Node("text", depth, element.text.text, element.text.modifiers.takeIf { element.text.hasModifiers() }, emptyList(), element)
            element.hasSpacer() -> Node("spacer", depth, null, element.spacer.modifiers.takeIf { element.spacer.hasModifiers() }, emptyList(), element)
            else -> throw UnsupportedOperationException("the walk does not know ${element.innerCase}: teach it before using it")
        }
        add(node)
        for (child in node.children) walk(child, depth + 1)
    }
}
