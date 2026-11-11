package world.taqwa.app.wear.tile

import androidx.wear.protolayout.expression.proto.DynamicProto

/**
 * Counts the nodes of a dynamic value as a Tile renderer binds them (erratum p3 #6). The renderer
 * builds one node for every operation and every value of the expression it is sent, and charges
 * them to a fixed quota (`DynamicTypeEvaluator.bind`, `tryAcquireQuota`); over it, the dynamic value
 * is dropped and its static value stays, a countdown that never moves.
 *
 * [all] counts every node, the fixed values as well: what the Wear OS documentation counts
 * ("constants count as expressions"), and what a renderer that shares no repeated part of an
 * expression binds. It is the count the Tile is held to, the strictest a renderer can make.
 * [dynamic] leaves the fixed values out, as protolayout-expression-pipeline 1.4.2 charges them
 * (`DynamicDataNode.FIXED_NODE_COST` is 0), for the record.
 *
 * Anything the counter does not know throws, as the test evaluator does, so that an expression
 * built from a new kind of node cannot slip past it uncounted.
 */
internal class DynamicNodes private constructor() {
    var all = 0
        private set
    var dynamic = 0
        private set

    private fun node(fixed: Boolean) {
        all++
        if (!fixed) dynamic++
    }

    private fun string(node: DynamicProto.DynamicString) {
        when {
            node.hasFixed() -> node(fixed = true)
            node.hasConditionalOp() -> with(node.conditionalOp) {
                node(fixed = false)
                bool(condition)
                string(valueIfTrue)
                string(valueIfFalse)
            }
            node.hasConcatOp() -> with(node.concatOp) {
                node(fixed = false)
                string(inputLhs)
                string(inputRhs)
            }
            else -> unknown(node.innerCase)
        }
    }

    private fun int32(node: DynamicProto.DynamicInt32) {
        when {
            node.hasFixed() -> node(fixed = true)
            node.hasConditionalOp() -> with(node.conditionalOp) {
                node(fixed = false)
                bool(condition)
                int32(valueIfTrue)
                int32(valueIfFalse)
            }
            node.hasArithmeticOperation() -> with(node.arithmeticOperation) {
                node(fixed = false)
                int32(inputLhs)
                int32(inputRhs)
            }
            node.hasDurationPart() -> {
                node(fixed = false)
                duration(node.durationPart.input)
            }
            else -> unknown(node.innerCase)
        }
    }

    private fun float(node: DynamicProto.DynamicFloat) {
        when {
            node.hasFixed() -> node(fixed = true)
            node.hasInt32ToFloatOperation() -> {
                node(fixed = false)
                int32(node.int32ToFloatOperation.input)
            }
            node.hasArithmeticOperation() -> with(node.arithmeticOperation) {
                node(fixed = false)
                float(inputLhs)
                float(inputRhs)
            }
            node.hasConditionalOp() -> with(node.conditionalOp) {
                node(fixed = false)
                bool(condition)
                float(valueIfTrue)
                float(valueIfFalse)
            }
            else -> unknown(node.innerCase)
        }
    }

    private fun bool(node: DynamicProto.DynamicBool) {
        when {
            node.hasFixed() -> node(fixed = true)
            node.hasInt32Comparison() -> with(node.int32Comparison) {
                node(fixed = false)
                int32(inputLhs)
                int32(inputRhs)
            }
            node.hasFloatComparison() -> with(node.floatComparison) {
                node(fixed = false)
                float(inputLhs)
                float(inputRhs)
            }
            else -> unknown(node.innerCase)
        }
    }

    private fun duration(node: DynamicProto.DynamicDuration) {
        when {
            node.hasFixed() -> node(fixed = true)
            node.hasBetween() -> with(node.between) {
                node(fixed = false)
                instant(startInclusive)
                instant(endExclusive)
            }
            else -> unknown(node.innerCase)
        }
    }

    private fun instant(node: DynamicProto.DynamicInstant) {
        when {
            node.hasFixed() -> node(fixed = true)
            node.hasPlatformSource() -> node(fixed = false)
            else -> unknown(node.innerCase)
        }
    }

    private fun unknown(what: Any): Nothing =
        throw UnsupportedOperationException("the node counter does not know $what: teach it before using it")

    companion object {
        /** The nodes of [value]. */
        fun of(value: DynamicProto.DynamicString): DynamicNodes = DynamicNodes().apply { string(value) }

        /** The nodes of the condition [value]. */
        fun of(value: DynamicProto.DynamicBool): DynamicNodes = DynamicNodes().apply { bool(value) }

        /** The nodes of [values] together, as one layout binds them. */
        fun of(values: List<DynamicProto.DynamicString>): DynamicNodes = DynamicNodes().apply { values.forEach { string(it) } }
    }
}
