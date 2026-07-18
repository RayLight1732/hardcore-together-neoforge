package com.ray.light.hardcoretogether.domain

/** Minecraftのtick単位すら知らない、「経過ナノ秒を渡されたら足すだけ」の純粋な値オブジェクト。 */
class Challenge(
    val id: String,
    running: Boolean,
    elapsedSeconds: Long,
) {
    var running: Boolean = running
        private set

    private var elapsedNanos: Long = elapsedSeconds * 1_000_000_000L

    fun elapsedSeconds(): Long = elapsedNanos / 1_000_000_000L

    fun tick(deltaNanos: Long) {
        if (running) elapsedNanos += deltaNanos
    }

    fun end() {
        running = false
    }
}
