package com.ray.light.hardcoretogether.archive

import com.ray.light.hardcoretogether.gate.GateClient
import net.minecraft.server.MinecraftServer
import java.time.Instant

/**
 * Spec 2.5: the save-off -> save-all flush -> archive-request -> archive-complete -> save-on
 * bracket, shared by the manual /archive command (4.2) and automatic boss-kill archiving (4.3).
 */
object ArchiveOps {

    /** Runs the full archive bracket and returns whether Gate confirmed completion. */
    fun performArchive(server: MinecraftServer, name: String, elapsedTime: Long): Boolean {
        dispatch(server, "save-off")
        dispatch(server, "save-all flush")
        val completed = GateClient.sendArchiveRequestAndAwait(
            name = name,
            elapsedTime = elapsedTime,
            createdAt = Instant.now().toString(),
        )
        dispatch(server, "save-on")
        return completed
    }

    /** Spec 4.3: boss-triggered archive names are a timestamp (collisions are handled by Gate). */
    fun timestampName(): String =
        Instant.now().toString().replace(":", "-").substringBefore(".")

    private fun dispatch(server: MinecraftServer, command: String) {
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
    }
}
