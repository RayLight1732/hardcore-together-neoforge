package com.ray.light.hardcoretogether.adapter.neoforge

import com.ray.light.hardcoretogether.HardcoreTogether
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent

/**
 * NeoForge requires an object (or a manually-registered instance) for event subscription;
 * all actual state and logic live in Runtime.deathCountdown, this just forwards.
 */
@EventBusSubscriber(modid = HardcoreTogether.ID)
object DeathEventListener {
    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        HardcoreTogether.runtime?.deathCountdown?.onLivingDeath(event)
    }
}
