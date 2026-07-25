package com.ray.light.hardcoretogether.adapter.gate

import com.ray.light.hardcoretogether.port.ArchiveGateway
import com.ray.light.hardcoretogether.port.ArchiveResult
import net.minecraft.server.MinecraftServer
import java.time.Instant

/**
 * ArchiveGateway port implementation. Composes TcpGateConnection (the NDJSON wire protocol,
 * the only place that actually knows this is TCP) with the local save-off -> save-all flush
 * -> ... -> save-on bracket (spec 2.5), which requires MinecraftServer but has nothing to do
 * with how Gate is talked to.
 */
class ArchiveGatewayImpl(
    private val server: MinecraftServer,
    private val connection: TcpGateConnection,
) : ArchiveGateway {

    override fun connect() = connection.connect()

    override fun sendReady(running: Boolean) = connection.sendReady(running)

    override fun sendRunningChanged(running: Boolean) = connection.sendRunningChanged(running)

    override fun archive(name: String, elapsedTime: Long): ArchiveResult {
        dispatch("save-off")
        dispatch("save-all flush")
        val result = connection.sendArchiveRequestAndAwait(name, elapsedTime, Instant.now().toString())
        dispatch("save-on")
        return result
    }

    private fun dispatch(command: String) {
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
    }
}
