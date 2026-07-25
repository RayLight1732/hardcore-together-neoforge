package com.ray.light.hardcoretogether.adapter.gate

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ray.light.hardcoretogether.HardcoreTogether
import com.ray.light.hardcoretogether.port.ArchiveResult
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Spec 5 / 5.1: persistent TCP socket to Gate, one NDJSON line per message. The MOD connects
 * out (Gate is the long-lived side); the same connection carries every signal in both
 * directions for the lifetime of this server process.
 *
 * This class only knows the wire protocol - it has no MinecraftServer dependency and no
 * knowledge of save-off/save-on. That bracket is a separate local-server concern owned by
 * ArchiveGatewayImpl, which composes this class.
 */
class TcpGateConnection(private val port: Int = DEFAULT_PORT) {
    private val gson = Gson()
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    /**
     * Only non-null while connected - created alongside the reader thread on a successful
     * connect(), shut down in onDisconnected(). There is no reconnect logic, so this scheduler's
     * lifetime is tied to this one connection, not to the server process (architecture-neoforge.md
     * 「接続断時：保留中リクエストを即座に解決してからタイムアウト検出用スレッドを止める」節).
     */
    private var timeoutScheduler: ScheduledExecutorService? = null

    /**
     * protocol-mod-manager.md 1節・3.3〜3.5節：archive-request/archive-complete/archive-rejectedは
     * requestId（UUID）で相関する。同一接続上で複数のarchive-requestが並行して未処理になりうる
     * （手動/archive実行中に自動アーカイブが割り込む等）ため、nameや到着順には頼らない。
     */
    private val pendingArchiveResponses = ConcurrentHashMap<String, (ArchiveResult) -> Unit>()

    fun connect() {
        repeat(CONNECT_RETRIES) { attempt ->
            try {
                val s = Socket(HOST, port)
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                timeoutScheduler = Executors.newSingleThreadScheduledExecutor {
                    Thread(it, "hardcoretogether-archive-timeout").apply { isDaemon = true }
                }
                startReaderThread(s)
                HardcoreTogether.LOGGER.info("Connected to Hardcore Together Gate at $HOST:$port")
                return
            } catch (e: IOException) {
                HardcoreTogether.LOGGER.warn("Gate connection attempt ${attempt + 1}/$CONNECT_RETRIES failed: ${e.message}")
                Thread.sleep(CONNECT_RETRY_DELAY_MS)
            }
        }
        HardcoreTogether.LOGGER.error("Could not connect to Hardcore Together Gate after $CONNECT_RETRIES attempts")
    }

    private fun startReaderThread(s: Socket) {
        thread(isDaemon = true, name = "hardcoretogether-gate-reader") {
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    handleMessage(line)
                }
            } catch (e: IOException) {
                HardcoreTogether.LOGGER.warn("Gate connection reader stopped: ${e.message}")
            } finally {
                onDisconnected()
            }
        }
    }

    /**
     * Runs once, on the reader thread, the moment disconnection is detected. There is no
     * reconnect logic, so this connection is done for the rest of this server session: any
     * still-pending archive-request can never receive a real response now, so resolve it
     * immediately instead of leaving its caller to wait out the full timeout, then shut down the
     * timeout scheduler - nothing will ever schedule a new task on it again.
     */
    private fun onDisconnected() {
        socket = null
        writer = null
        pendingArchiveResponses.keys.toList().forEach { requestId ->
            pendingArchiveResponses.remove(requestId)?.invoke(ArchiveResult.NotConnected)
        }
        timeoutScheduler?.shutdownNow()
        timeoutScheduler = null
    }

    private fun handleMessage(line: String) {
        val json = JsonParser.parseString(line).asJsonObject
        when (json.get("type")?.asString) {
            "archive-complete" -> {
                val requestId = json.get("requestId").asString
                pendingArchiveResponses.remove(requestId)?.invoke(ArchiveResult.Success)
            }
            "archive-rejected" -> {
                val requestId = json.get("requestId").asString
                val reason = json.get("reason").asString
                pendingArchiveResponses.remove(requestId)?.invoke(ArchiveResult.Rejected(reason))
            }
        }
    }

    private fun send(obj: JsonObject) {
        val w = writer ?: run {
            HardcoreTogether.LOGGER.warn("Not connected to Gate, dropping message: $obj")
            return
        }
        w.println(gson.toJson(obj))
    }

    fun sendReady(running: Boolean) {
        send(JsonObject().apply {
            addProperty("type", "ready")
            addProperty("running", running)
        })
    }

    fun sendRunningChanged(running: Boolean) {
        send(JsonObject().apply {
            addProperty("type", "running-changed")
            addProperty("running", running)
        })
    }

    /**
     * Sends archive-request and returns immediately - never blocks the caller. The result
     * (archive-complete/archive-rejected via requestId correlation, a timeout fallback, or an
     * immediate NotConnected when there is no connection to send on) is reported to onResult
     * asynchronously, possibly from the reader thread or the timeout scheduler thread. Callers
     * that touch MinecraftServer state from onResult must hop back to the main thread themselves
     * (architecture-neoforge.md「デッドロックの教訓」).
     */
    fun sendArchiveRequest(name: String, elapsedTime: Long, createdAt: String, onResult: (ArchiveResult) -> Unit) {
        val scheduler = timeoutScheduler
        if (writer == null || scheduler == null) {
            HardcoreTogether.LOGGER.warn("Not connected to Gate, failing archive-request for '$name' immediately")
            onResult(ArchiveResult.NotConnected)
            return
        }

        val requestId = UUID.randomUUID().toString()
        pendingArchiveResponses[requestId] = onResult
        send(JsonObject().apply {
            addProperty("type", "archive-request")
            addProperty("requestId", requestId)
            addProperty("name", name)
            addProperty("elapsedTime", elapsedTime)
            addProperty("createdAt", createdAt)
        })
        try {
            scheduler.schedule(
                { pendingArchiveResponses.remove(requestId)?.invoke(ArchiveResult.TimedOut) },
                ARCHIVE_COMPLETE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        } catch (e: RejectedExecutionException) {
            // Raced with onDisconnected() shutting the scheduler down right after the null
            // check above; onDisconnected()'s drain may have already resolved this request (in
            // which case remove() below is a harmless no-op), otherwise resolve it here.
            pendingArchiveResponses.remove(requestId)?.invoke(ArchiveResult.NotConnected)
        }
    }

    companion object {
        private const val HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 25585
        private const val CONNECT_RETRIES = 5
        private const val CONNECT_RETRY_DELAY_MS = 2000L
        private const val ARCHIVE_COMPLETE_TIMEOUT_SECONDS = 60L
    }
}
