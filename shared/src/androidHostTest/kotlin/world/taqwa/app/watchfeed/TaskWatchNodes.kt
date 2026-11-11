package world.taqwa.app.watchfeed

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import world.taqwa.app.watch.WatchLinkContract

/** One message a [FakeWatchNodes] or a [TaskWatchNodes] was asked to send. */
internal class NodeMessage(val nodeId: String, val path: String, val data: ByteArray)

/**
 * The Data Layer's nodes as a test tells them (plan 2 Task 5), for the link's own rules:
 * - [watches] answers, in turn, each of [answers] (null: no Wear API on this phone), and once none
 *   is left, [steady]; [watchesThrows] throws instead;
 * - [send] records each message in [sends], then answers [onSend] for its node: queued, gone, or a
 *   throw.
 */
internal class FakeWatchNodes(var steady: List<WatchNode> = emptyList()) : WatchNodes {
    val answers: ArrayDeque<List<WatchNode>?> = ArrayDeque()
    var watchesThrows: Exception? = null
    var watchesAsked: Int = 0
        private set
    var onSend: (nodeId: String) -> NodeSend = { NodeSend.QUEUED }
    val sends: MutableList<NodeMessage> = mutableListOf()

    override suspend fun watches(): List<WatchNode>? {
        watchesAsked++
        watchesThrows?.let { throw it }
        return if (answers.isNotEmpty()) answers.removeFirst() else steady
    }

    override suspend fun send(nodeId: String, path: String, data: ByteArray): NodeSend {
        sends += NodeMessage(nodeId, path, data)
        return onSend(nodeId)
    }
}

/**
 * [PlayWatchNodes] itself over Play services tasks the test hands it (plan 2 Task 5), so the real
 * `await()` and the real reading of each answer's code space are on the sync's path:
 * - each capability query takes the next of [capabilityTasks], and once none is left a task
 *   answering [nearby] at once; [capabilityAsked] counts the queries;
 * - each message takes the next of [sendTasks], and once none is left a task queued at once
 *   (`sendMessage`'s request id); [sends] records each one asked for;
 * - Play services' availability is [availability] (`ConnectionResult`'s codes).
 */
internal class TaskWatchNodes(private val availability: Int = ConnectionResult.SUCCESS) {
    val capabilityTasks: ArrayDeque<Task<CapabilityInfo>> = ArrayDeque()
    val sendTasks: ArrayDeque<Task<Int>> = ArrayDeque()
    var nearby: List<WatchNode> = emptyList()
    var capabilityAsked: Int = 0
        private set
    val sends: MutableList<NodeMessage> = mutableListOf()

    val nodes: WatchNodes = PlayWatchNodes(
        availability = { availability },
        capability = {
            capabilityAsked++
            capabilityTasks.removeFirstOrNull() ?: FakeTask.succeeded(capabilityOf(nearby))
        },
        message = { nodeId, path, data ->
            sends += NodeMessage(nodeId, path, data)
            sendTasks.removeFirstOrNull() ?: FakeTask.succeeded(sends.size)
        },
    )
}

/** What `getCapability(taqwa_watch, FILTER_REACHABLE)` answers when [nodes] are reachable. */
internal fun capabilityOf(nodes: List<WatchNode>, name: String = WatchLinkContract.WATCH_CAPABILITY): CapabilityInfo =
    object : CapabilityInfo {
        override fun getName(): String = name

        override fun getNodes(): Set<Node> = nodes.map(::nodeOf).toSet()
    }

/** A Data Layer node as Play services describes it. */
internal fun nodeOf(node: WatchNode): Node = object : Node {
    override fun getId(): String = node.id

    override fun getDisplayName(): String = "Watch ${node.id}"

    override fun isNearby(): Boolean = node.nearby
}
