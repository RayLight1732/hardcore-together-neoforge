package com.ray.light.hardcoretogether.adapter.gate

import com.ray.light.hardcoretogether.port.ArchiveGateway
import com.ray.light.hardcoretogether.port.ArchiveResult
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.common.IOUtilities
import java.time.Instant

/**
 * ArchiveGateway port implementation. Composes TcpGateConnection (the NDJSON wire protocol,
 * the only place that actually knows this is TCP) with the local save-off -> save-all flush
 * -> ... -> save-on bracket (spec 2.5), which requires MinecraftServer but has nothing to do
 * with how Gate is talked to.
 *
 * archive() never blocks: requests are queued and processed one at a time, only issuing the
 * next request's flush after the previous one's response has been received. This is not just
 * to keep in-flight count bookkeeping simple - flushing while Manager may still be mid-copy for
 * an earlier request would let the earlier archive pick up newer file state than it should
 * (architecture-neoforge.md「`ArchiveGatewayImpl`：flushもリクエスト間で直列化する」節). save-off
 * brackets the whole queue (fires once when it goes empty->non-empty, save-on once it drains).
 */
class ArchiveGatewayImpl(
    private val server: MinecraftServer,
    private val connection: TcpGateConnection,
) : ArchiveGateway {

    private data class QueuedArchive(val name: String, val elapsedTime: Long, val onResult: (ArchiveResult) -> Unit)

    // Touched only from the main thread: archive() is always called from there (CommandRegistrar,
    // DeathCountdown), and processNext()'s continuation runs inside server.execute{}.
    private val queue = ArrayDeque<QueuedArchive>()
    private var saveOffActive = false
    private var inFlight = false

    override fun connect() = connection.connect()

    override fun sendReady(running: Boolean) = connection.sendReady(running)

    override fun sendRunningChanged(running: Boolean) = connection.sendRunningChanged(running)

    override fun archive(name: String, elapsedTime: Long, onResult: (ArchiveResult) -> Unit) {
        queue.addLast(QueuedArchive(name, elapsedTime, onResult))
        if (!saveOffActive) {
            saveOffActive = true
            dispatch("save-off")
        }
        if (!inFlight) processNext()
    }

    private fun processNext() {
        val next = queue.removeFirstOrNull()
        if (next == null) {
            if (saveOffActive) {
                saveOffActive = false
                dispatch("save-on")
            }
            return
        }
        inFlight = true
        dispatch("save-all flush")
        // save-all flush is fully synchronous for vanilla's own chunk/level saving, but NeoForge
        // routes SavedData writes (world/data/*.dat - random_sequences.dat, data attachments,
        // etc; see SavedData.save()) through its own background IOUtilities worker instead of
        // waiting for them inline. Without this, Manager's copy can rarely race a file that's
        // still mid-write there, well after flush has already returned and logged completion.
        IOUtilities.waitUntilIOWorkerComplete()
        connection.sendArchiveRequest(next.name, next.elapsedTime, Instant.now().toString()) { result ->
            server.execute {
                inFlight = false
                next.onResult(result)
                processNext()
            }
        }
    }

    private fun dispatch(command: String) {
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
    }
}
