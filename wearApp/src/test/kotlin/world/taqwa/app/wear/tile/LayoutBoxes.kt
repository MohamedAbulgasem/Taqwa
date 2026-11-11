package world.taqwa.app.wear.tile

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.wear.protolayout.proto.AlignmentProto
import androidx.wear.protolayout.proto.DimensionProto
import androidx.wear.protolayout.proto.LayoutElementProto
import androidx.wear.protolayout.proto.ModifiersProto

/**
 * Where a Tile's texts fall from its top, in the drawing's units, laid out as the renderer lays
 * ProtoLayout out: a Box sets each child at its vertical alignment inside its padding (centred
 * unless it says otherwise), a Row sets its children side by side at its alignment, a Column one
 * below the other, a Spacer is its height, and a wrapped Text is as tall as its line in its fonts
 * ([TileMeasure.extent]), its baseline its ascent below its top. A box clips what runs past its
 * padding, as an Android view group does, so each [Placed] text carries the room its boxes leave
 * it: the renderer cuts whatever of its line runs outside.
 *
 * It knows only what the Tile uses; anything else throws.
 */
internal class LayoutBoxes(root: LayoutElementProto.LayoutElement, private val screen: TileScreen, private val measure: TileMeasure) {

    /** A text: its line from [top] to [bottom] with its [baseline], and the room from [roomTop] to [roomBottom] its boxes leave it. */
    data class Placed(val text: String, val top: Float, val baseline: Float, val bottom: Float, val roomTop: Float, val roomBottom: Float) {
        val cut: Boolean get() = top < roomTop - EPSILON || bottom > roomBottom + EPSILON
    }

    private val unit = screen.units.dp

    val texts: List<Placed> = buildList {
        val height = WIDTH_UNITS * screen.heightDp / screen.widthDp
        place(root, 0f, height, 0f, height)
    }

    private fun MutableList<Placed>.place(element: LayoutElementProto.LayoutElement, top: Float, available: Float, roomTop: Float, roomBottom: Float) {
        when {
            element.hasText() -> {
                val text = element.text
                val line = measure.extent(text.text.value, sizeOf(text), 600)
                val (padTop, padBottom) = padding(text.modifiers.takeIf { text.hasModifiers() })
                val inTop = top + padTop
                add(Placed(text.text.value, inTop, inTop + line.above, inTop + line.tall, roomTop, roomBottom))
            }
            element.hasSpacer() -> Unit
            element.hasBox() -> {
                val box = element.box
                val height = heightOf(element, available)
                val (padTop, padBottom) = padding(box.modifiers.takeIf { box.hasModifiers() })
                val inTop = top + padTop
                val inBottom = top + height - padBottom
                val align = if (box.hasVerticalAlignment()) box.verticalAlignment.value else AlignmentProto.VerticalAlignment.VERTICAL_ALIGN_CENTER
                for (child in box.contentsList) {
                    val h = heightOf(child, inBottom - inTop)
                    place(child, at(align, inTop, inBottom, h), inBottom - inTop, maxOf(roomTop, inTop), minOf(roomBottom, inBottom))
                }
            }
            element.hasRow() -> {
                val row = element.row
                val height = heightOf(element, available)
                val (padTop, padBottom) = padding(row.modifiers.takeIf { row.hasModifiers() })
                val inTop = top + padTop
                val inBottom = top + height - padBottom
                val align = if (row.hasVerticalAlignment()) row.verticalAlignment.value else AlignmentProto.VerticalAlignment.VERTICAL_ALIGN_CENTER
                for (child in row.contentsList) {
                    val h = heightOf(child, inBottom - inTop)
                    place(child, at(align, inTop, inBottom, h), inBottom - inTop, maxOf(roomTop, inTop), minOf(roomBottom, inBottom))
                }
            }
            element.hasColumn() -> {
                val column = element.column
                val height = heightOf(element, available)
                val (padTop, padBottom) = padding(column.modifiers.takeIf { column.hasModifiers() })
                var y = top + padTop
                val inBottom = top + height - padBottom
                for (child in column.contentsList) {
                    val h = heightOf(child, inBottom - y)
                    place(child, y, h, maxOf(roomTop, top + padTop), minOf(roomBottom, inBottom))
                    y += h
                }
            }
            else -> throw UnsupportedOperationException("the boxes do not know ${element.innerCase}")
        }
    }

    private fun at(align: AlignmentProto.VerticalAlignment, top: Float, bottom: Float, height: Float): Float = when (align) {
        AlignmentProto.VerticalAlignment.VERTICAL_ALIGN_TOP -> top
        AlignmentProto.VerticalAlignment.VERTICAL_ALIGN_BOTTOM -> bottom - height
        else -> (top + bottom - height) / 2
    }

    /** How tall [element] is drawn in [available] units. */
    private fun heightOf(element: LayoutElementProto.LayoutElement, available: Float): Float = when {
        element.hasText() -> measure.extent(element.text.text.value, sizeOf(element.text), 600).tall +
            padding(element.text.modifiers.takeIf { element.text.hasModifiers() }).let { it.first + it.second }
        element.hasSpacer() -> element.spacer.height.linearDimension.value / unit
        element.hasBox() -> container(element.box.height, available, element.box.modifiers.takeIf { element.box.hasModifiers() }) { inner ->
            element.box.contentsList.maxOfOrNull { heightOf(it, inner) } ?: 0f
        }
        element.hasRow() -> container(element.row.height, available, element.row.modifiers.takeIf { element.row.hasModifiers() }) { inner ->
            element.row.contentsList.maxOfOrNull { heightOf(it, inner) } ?: 0f
        }
        element.hasColumn() -> container(element.column.height, available, element.column.modifiers.takeIf { element.column.hasModifiers() }) { inner ->
            element.column.contentsList.sumOf { heightOf(it, inner).toDouble() }.toFloat()
        }
        else -> throw UnsupportedOperationException("the boxes do not know ${element.innerCase}")
    }

    private fun container(dimension: DimensionProto.ContainerDimension, available: Float, modifiers: ModifiersProto.Modifiers?, content: (Float) -> Float): Float {
        val (padTop, padBottom) = padding(modifiers)
        return when {
            dimension.hasLinearDimension() -> dimension.linearDimension.value / unit
            dimension.hasExpandedDimension() -> available
            else -> content(available - padTop - padBottom) + padTop + padBottom
        }
    }

    private fun padding(modifiers: ModifiersProto.Modifiers?): Pair<Float, Float> {
        if (modifiers == null || !modifiers.hasPadding()) return 0f to 0f
        return modifiers.padding.top.value / unit to modifiers.padding.bottom.value / unit
    }

    /** A Text's size in units, back through the screen's own density, as `WatchUnits.sp` made it. */
    private fun sizeOf(text: LayoutElementProto.Text): Float {
        val sp = text.fontStyle.getSize(0).value
        return with(Density(screen.density, screen.fontScale)) { sp.sp.toDp().value } / unit
    }

    private companion object {
        const val WIDTH_UNITS = 200f
        const val EPSILON = 1e-3f
    }
}
