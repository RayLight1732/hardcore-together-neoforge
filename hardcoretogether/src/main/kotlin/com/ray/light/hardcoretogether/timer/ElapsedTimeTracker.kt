package com.ray.light.hardcoretogether.timer

import com.ray.light.hardcoretogether.records.RecordStore
import net.minecraft.server.MinecraftServer

/**
 * Spec 4.1: real-time (not tick-count) elapsed time, seeded from and periodically flushed
 * back to the challenge record's lastKnownElapsedTime so it survives crashes without
 * living inside SavedData (which would rewind on /load - see spec 4.1/4.5).
 */
object ElapsedTimeTracker {
    private const val PERSIST_INTERVAL_NANOS = 10_000_000_000L // 10s, per spec 4.1 "数秒間隔（例：10秒ごと）"

    private var accumulatedNanos: Long = 0L
    private var lastTickNanos: Long = 0L
    private var lastPersistNanos: Long = 0L

    fun start(server: MinecraftServer, challengeId: String) {
        val file = RecordStore.load(server, challengeId)
        accumulatedNanos = file.lastKnownElapsedTime * 1_000_000_000L
        lastTickNanos = System.nanoTime()
        lastPersistNanos = lastTickNanos
    }

    /** Call once per server tick. Only accumulates while running=true. */
    fun onServerTick(server: MinecraftServer, challengeId: String, running: Boolean) {
        val now = System.nanoTime()
        if (running) {
            accumulatedNanos += (now - lastTickNanos)
        }
        lastTickNanos = now

        if (now - lastPersistNanos >= PERSIST_INTERVAL_NANOS) {
            RecordStore.updateLastKnownElapsedTime(server, challengeId, elapsedSeconds())
            lastPersistNanos = now
        }
    }

    /** Current elapsed time in whole seconds, for use in record events at the exact moment of an event. */
    fun elapsedSeconds(): Long = accumulatedNanos / 1_000_000_000L

    fun formatted(seconds: Long = elapsedSeconds()): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, secs)
    }
}
