package world.taqwa.app.recitation

/**
 * Decides whether a notification that several workers share is worth posting again
 * (spec §18.1).
 *
 * Android sheds a package's notifications above five a second, and every post is a binder
 * transaction to the system and to every listener. The download notification is one id written
 * by as many workers as WorkManager runs at once, each on its own progress clock, so the limit
 * has to be held across all of them and not per worker: one gate, for the process.
 *
 * Two rules. What the shade would show has to have **changed** — a batch line reads "12 of 114
 * surahs" for minutes at a time, and re-posting it says nothing. And posts are at least
 * [minIntervalMs] apart. A change that arrives too soon is not lost: the caller asks again on its
 * next progress event, and the content still differs from what was last posted.
 */
class PostGate(private val minIntervalMs: Long) {
    private var lastContent: String? = null
    private var lastAt = 0L

    /** True — and recorded as posted — when [content] should go to the shade at [nowMs]. */
    fun shouldPost(content: String, nowMs: Long): Boolean {
        val first = lastContent == null
        if (!first && (content == lastContent || nowMs - lastAt < minIntervalMs)) return false
        lastContent = content
        lastAt = nowMs
        return true
    }

    /**
     * Notes a post the caller had to make whatever the gate thought — a worker's first
     * `setForeground`, which WorkManager needs — so that the progress event a moment behind it
     * does not post the same thing again.
     */
    fun record(content: String, nowMs: Long) {
        lastContent = content
        lastAt = nowMs
    }

    /** Forgets what was posted: the notification has gone, and the next one is a first. */
    fun reset() {
        lastContent = null
        lastAt = 0L
    }
}
