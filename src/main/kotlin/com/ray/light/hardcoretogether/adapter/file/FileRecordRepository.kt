package com.ray.light.hardcoretogether.adapter.file

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ray.light.hardcoretogether.domain.RecordEvent
import com.ray.light.hardcoretogether.domain.describe
import com.ray.light.hardcoretogether.port.RecordRepository
import java.nio.file.Files
import java.nio.file.Path

/**
 * Spec 4.5: records/<challengeId>.json。MinecraftServerではなく`dir: Path`を取るだけの
 * 純粋なファイルI/Oクラス（Minecraft/NeoForgeの型に一切依存しない）。
 */
class FileRecordRepository(private val dir: Path) : RecordRepository {
    init {
        Files.createDirectories(dir)
    }

    private fun fileFor(challengeId: String): Path = dir.resolve("$challengeId.json")

    private fun readRaw(challengeId: String): JsonObject {
        val path = fileFor(challengeId)
        if (!Files.exists(path)) {
            return JsonObject().apply {
                addProperty("challengeId", challengeId)
                addProperty("lastKnownElapsedTime", 0L)
                add("events", JsonArray())
            }
        }
        Files.newBufferedReader(path).use { return JsonParser.parseReader(it).asJsonObject }
    }

    private fun writeRaw(challengeId: String, raw: JsonObject) {
        Files.newBufferedWriter(fileFor(challengeId)).use { GSON.toJson(raw, it) }
    }

    @Synchronized
    override fun appendEvent(challengeId: String, event: RecordEvent) {
        val raw = readRaw(challengeId)
        raw.getAsJsonArray("events").add(event.describe().toGson())
        raw.addProperty("lastKnownElapsedTime", event.elapsedTime)
        writeRaw(challengeId, raw)
    }

    @Synchronized
    override fun updateLastKnownElapsedTime(challengeId: String, elapsedSeconds: Long) {
        val raw = readRaw(challengeId)
        raw.addProperty("lastKnownElapsedTime", elapsedSeconds)
        writeRaw(challengeId, raw)
    }

    @Synchronized
    override fun lastKnownElapsedTime(challengeId: String): Long =
        readRaw(challengeId).get("lastKnownElapsedTime").asLong

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
    }
}
