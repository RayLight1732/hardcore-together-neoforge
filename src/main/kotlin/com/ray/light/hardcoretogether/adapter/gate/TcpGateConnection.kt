package com.ray.light.hardcoretogether.adapter.gate

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ray.light.hardcoretogether.HardcoreTogether
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
    private val pendingArchiveCompletions = ConcurrentHashMap<String, CompletableFuture<Void?>>()

    fun connect() {
        repeat(CONNECT_RETRIES) { attempt ->
            try {
                val s = Socket(HOST, port)
                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
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
                socket = null
                writer = null
            }
        }
    }

    private fun handleMessage(line: String) {
        val json = JsonParser.parseString(line).asJsonObject
        when (json.get("type")?.asString) {
            "archive-complete" -> {
                val name = json.get("name").asString
                pendingArchiveCompletions.remove(name)?.complete(null)
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

    /** Sends archive-request and blocks until the matching archive-complete arrives (or times out). */
    fun sendArchiveRequestAndAwait(name: String, elapsedTime: Long, createdAt: String): Boolean {
        val future = CompletableFuture<Void?>()
        pendingArchiveCompletions[name] = future
        send(JsonObject().apply {
            addProperty("type", "archive-request")
            addProperty("name", name)
            addProperty("elapsedTime", elapsedTime)
            addProperty("createdAt", createdAt)
        })
        return try {
            future.get(ARCHIVE_COMPLETE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            true
        } catch (e: TimeoutException) {
            pendingArchiveCompletions.remove(name)
            HardcoreTogether.LOGGER.error("Timed out waiting for archive-complete for '$name'")
            false
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
