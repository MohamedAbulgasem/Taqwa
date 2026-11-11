package world.taqwa.app.wear.complication

import androidx.wear.protolayout.expression.proto.DynamicProto
import androidx.wear.protolayout.expression.proto.DynamicProto.ArithmeticOpType
import androidx.wear.protolayout.expression.proto.DynamicProto.ComparisonOpType
import androidx.wear.protolayout.expression.proto.DynamicProto.DurationPartType
import java.time.Duration
import java.time.Instant

/**
 * Evaluates a dynamic value the way the watch's system does, for the parts the ring and the
 * countdown are made of, so that the tests can read what they say at any instant without a watch.
 * The system's own evaluator (`protolayout-expression-pipeline`) needs a main looper and a clock to
 * bind to, and the rules it applies are Java's plain ones, which are the ones used here, on the
 * same `java.time` types: `Duration.between`, truncating division, `%`, `Duration.getSeconds` and
 * `Duration.toMinutes`.
 *
 * The platform's clock keeps its milliseconds: `platformTimeWithSecondsPrecision` says how often
 * it is read (once a second), and not that it is rounded, which the emulator showed (a countdown
 * built on a floor of the time left turned over at 14:58:00.000 and not at 14:58:01). So [now] is
 * an instant, milliseconds and all.
 *
 * Anything the evaluator does not know throws, so that a countdown built from a new kind of
 * expression cannot slip past the tests unread.
 */
internal class DynamicEvaluator(private val now: Instant) {

    /** The platform's clock at [epochSeconds] and [millis] milliseconds into that second. */
    constructor(epochSeconds: Long, millis: Int = 0) : this(Instant.ofEpochSecond(epochSeconds, millis * 1_000_000L))

    fun string(node: DynamicProto.DynamicString): String = when {
        node.hasFixed() -> node.fixed.value
        node.hasConditionalOp() -> with(node.conditionalOp) {
            if (bool(condition)) string(valueIfTrue) else string(valueIfFalse)
        }
        node.hasConcatOp() -> string(node.concatOp.inputLhs) + string(node.concatOp.inputRhs)
        else -> unknown(node.innerCase)
    }

    fun float(node: DynamicProto.DynamicFloat): Float = when {
        node.hasFixed() -> node.fixed.value
        node.hasInt32ToFloatOperation() -> int32(node.int32ToFloatOperation.input).toFloat()
        node.hasConditionalOp() -> with(node.conditionalOp) {
            if (bool(condition)) float(valueIfTrue) else float(valueIfFalse)
        }
        node.hasArithmeticOperation() -> with(node.arithmeticOperation) {
            val left = float(inputLhs)
            val right = float(inputRhs)
            when (operationType) {
                ArithmeticOpType.ARITHMETIC_OP_TYPE_ADD -> left + right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_SUBTRACT -> left - right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_MULTIPLY -> left * right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_DIVIDE -> left / right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_MODULO -> left % right
                else -> unknown(operationType)
            }
        }
        else -> unknown(node.innerCase)
    }

    private fun int32(node: DynamicProto.DynamicInt32): Int = when {
        node.hasFixed() -> node.fixed.value
        node.hasConditionalOp() -> with(node.conditionalOp) {
            if (bool(condition)) int32(valueIfTrue) else int32(valueIfFalse)
        }
        node.hasArithmeticOperation() -> with(node.arithmeticOperation) {
            val left = int32(inputLhs)
            val right = int32(inputRhs)
            when (operationType) {
                ArithmeticOpType.ARITHMETIC_OP_TYPE_ADD -> left + right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_SUBTRACT -> left - right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_MULTIPLY -> left * right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_DIVIDE -> left / right
                ArithmeticOpType.ARITHMETIC_OP_TYPE_MODULO -> left % right
                else -> unknown(operationType)
            }
        }
        node.hasDurationPart() -> with(node.durationPart) {
            val duration = duration(input)
            when (durationPart) {
                DurationPartType.DURATION_PART_TYPE_TOTAL_SECONDS -> duration.seconds.toInt()
                DurationPartType.DURATION_PART_TYPE_TOTAL_MINUTES -> duration.toMinutes().toInt()
                else -> unknown(durationPart)
            }
        }
        else -> unknown(node.innerCase)
    }

    fun bool(node: DynamicProto.DynamicBool): Boolean = when {
        node.hasInt32Comparison() -> with(node.int32Comparison) {
            compare(operationType, int32(inputLhs).compareTo(int32(inputRhs)))
        }
        node.hasFloatComparison() -> with(node.floatComparison) {
            compare(operationType, float(inputLhs).compareTo(float(inputRhs)))
        }
        else -> unknown(node.innerCase)
    }

    private fun compare(type: ComparisonOpType, order: Int): Boolean = when (type) {
        ComparisonOpType.COMPARISON_OP_TYPE_EQUALS -> order == 0
        ComparisonOpType.COMPARISON_OP_TYPE_NOT_EQUALS -> order != 0
        ComparisonOpType.COMPARISON_OP_TYPE_LESS_THAN -> order < 0
        ComparisonOpType.COMPARISON_OP_TYPE_LESS_THAN_OR_EQUAL_TO -> order <= 0
        ComparisonOpType.COMPARISON_OP_TYPE_GREATER_THAN -> order > 0
        ComparisonOpType.COMPARISON_OP_TYPE_GREATER_THAN_OR_EQUAL_TO -> order >= 0
        else -> unknown(type)
    }

    private fun duration(node: DynamicProto.DynamicDuration): Duration = when {
        node.hasFixed() -> Duration.ofSeconds(node.fixed.seconds)
        node.hasBetween() -> Duration.between(instant(node.between.startInclusive), instant(node.between.endExclusive))
        else -> unknown(node.innerCase)
    }

    private fun instant(node: DynamicProto.DynamicInstant): Instant = when {
        node.hasFixed() -> Instant.ofEpochSecond(node.fixed.epochSeconds)
        node.hasPlatformSource() -> now
        else -> unknown(node.innerCase)
    }

    private fun unknown(what: Any): Nothing =
        throw UnsupportedOperationException("the test evaluator does not know $what: teach it before using it")
}
