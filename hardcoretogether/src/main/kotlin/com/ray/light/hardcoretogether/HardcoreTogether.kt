package com.ray.light.hardcoretogether

import com.ray.light.hardcoretogether.adapter.neoforge.HardcoreConfig
import com.ray.light.hardcoretogether.adapter.neoforge.Runtime
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.event.server.ServerStartedEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Composition root. Stays an object because NeoForge's @Mod/@EventBusSubscriber require a
 * static entry point, but it holds nothing beyond the current server session's Runtime -
 * all business state and logic live in domain/application/port/adapter (see
 * hardcoretogether-architecture.md).
 */
@Mod(HardcoreTogether.ID)
@EventBusSubscriber(modid = HardcoreTogether.ID)
object HardcoreTogether {
    const val ID = "hardcoretogether"

    val LOGGER: Logger = LogManager.getLogger(ID)

    @Volatile
    var runtime: Runtime? = null
        private set

    init {
        LOGGER.log(Level.INFO, "Hardcore Together loading")
        ModList.get().getModContainerById(ID).ifPresentOrElse(
            { it.registerConfig(ModConfig.Type.COMMON, HardcoreConfig.SPEC) },
            { LOGGER.error("Could not find mod container for $ID; boss config will not load") },
        )
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        val built = Runtime.create(event.server)
        runtime = built
        built.archiveGateway.connect()
        built.archiveGateway.sendReady(built.challengeState.running)
    }
}
