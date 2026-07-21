package com.ray.light.hardcoretogether.adapter.neoforge

import com.ray.light.hardcoretogether.HardcoreTogether
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent

@EventBusSubscriber(modid = HardcoreTogether.ID)
object TickListener {
    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        HardcoreTogether.runtime?.onServerTick()
    }
}
