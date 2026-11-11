package world.taqwa.app.watchfeed

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Settings › Watch's source as a test tells it (plan 5 Task 6): [targets] answers [answer], and
 * [open] records each call in [opened] and answers [opens]. Each suspends only as its lambda does,
 * by `delay` or on a `CompletableDeferred`, so a bound around it is cut on virtual time alone
 * (plan 2's "Tests on virtual time"). [changes] is [changed]: nothing, unless a test says the watch
 * side changed.
 */
internal class FakeWatchTargetsSource(
    var answer: suspend () -> WatchTargets = { WatchTargets.NONE },
    var opens: suspend (WatchTarget, WatchAction) -> Boolean = { _, _ -> true },
    var changed: Flow<Unit> = emptyFlow(),
) : WatchTargetsSource {
    var asked: Int = 0
        private set
    val opened: MutableList<Pair<WatchTarget, WatchAction>> = mutableListOf()

    override suspend fun targets(): WatchTargets {
        asked++
        return answer()
    }

    override suspend fun open(target: WatchTarget, action: WatchAction): Boolean {
        opened += target to action
        return opens(target, action)
    }

    override fun changes(): Flow<Unit> = changed
}
