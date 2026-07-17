package com.ray.light.hardcoretogether.records

import com.google.gson.GsonBuilder
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Spec 4.5: records/<challengeId>.json lives next to world/ in the server's own working
 * directory (NOT inside world/, so /start's save-folder wipe never touches it), and is
 * managed entirely by the MOD via plain local file I/O - Gate is not involved.
 */
object RecordStore {
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    private fun recordsDir(server: MinecraftServer): Path {
        val dir = server.serverDirectory.resolve("records")
        Files.createDirectories(dir)
        return dir
    }

    private fun fileFor(server: MinecraftServer, challengeId: String): Path =
        recordsDir(server).resolve("$challengeId.json")

    @Synchronized
    fun load(server: MinecraftServer, challengeId: String): ChallengeRecordFile {
        val path = fileFor(server, challengeId)
        if (!Files.exists(path)) {
            return ChallengeRecordFile(challengeId = challengeId)
        }
        Files.newBufferedReader(path).use { reader ->
            return GSON.fromJson(reader, ChallengeRecordFile::class.java)
        }
    }

    @Synchronized
    fun save(server: MinecraftServer, file: ChallengeRecordFile) {
        val path = fileFor(server, file.challengeId)
        Files.newBufferedWriter(path).use { writer ->
            GSON.toJson(file, writer)
        }
    }

    /** Appends an event and bumps lastKnownElapsedTime to the event's own elapsed time. */
    @Synchronized
    fun appendEvent(server: MinecraftServer, challengeId: String, event: RecordEvent) {
        val file = load(server, challengeId)
        file.events.add(event)
        file.lastKnownElapsedTime = event.elapsedTime
        save(server, file)
    }

    /** Periodic heartbeat write (spec 4.1): updates only lastKnownElapsedTime. */
    @Synchronized
    fun updateLastKnownElapsedTime(server: MinecraftServer, challengeId: String, elapsedTime: Long) {
        val file = load(server, challengeId)
        file.lastKnownElapsedTime = elapsedTime
        save(server, file)
    }

    /** Spec 4.5: /savedata and /senpan scan every challengeId file. */
    @Synchronized
    fun listAll(server: MinecraftServer): List<ChallengeRecordFile> {
        val dir = recordsDir(server)
        return Files.list(dir).use { stream ->
            stream
                .filter { it.toString().endsWith(".json") }
                .map { path ->
                    Files.newBufferedReader(path).use { reader ->
                        GSON.fromJson(reader, ChallengeRecordFile::class.java)
                    }
                }
                .toList()
        }
    }
}
