package com.ray.light.hardcoretogether

import com.ray.light.hardcoretogether.config.BossConfig
import com.ray.light.hardcoretogether.event.DeathHandler
import com.ray.light.hardcoretogether.gate.GateClient
import com.ray.light.hardcoretogether.state.ChallengeState
import com.ray.light.hardcoretogether.timer.ElapsedTimeTracker
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Main mod class. See specification.md sections 4-5 for the design this implements:
 * challenge state persistence, boss-triggered checkpoints/clears, the per-challenge
 * event log, real-time elapsed tracking, and the Gate signal channel.
 */
@Mod(HardcoreTogether.ID)
@EventBusSubscriber(modid = HardcoreTogether.ID)
object HardcoreTogether {
    const val ID = "hardcoretogether"

    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        LOGGER.log(Level.INFO, "Hardcore Together loading")
        ModList.get().getModContainerById(ID).ifPresentOrElse(
            { it.registerConfig(ModConfig.Type.COMMON, BossConfig.SPEC) },
            { LOGGER.error("Could not find mod container for $ID; boss config will not load") },
        )
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        val state = ChallengeState.get(server)
        ElapsedTimeTracker.start(server, state.challengeId)
        GateClient.connect()
        GateClient.sendReady(state.running)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server
        val state = ChallengeState.get(server)
        ElapsedTimeTracker.onServerTick(server, state.challengeId, state.running)
        DeathHandler.onServerTick(server)
    }
}
