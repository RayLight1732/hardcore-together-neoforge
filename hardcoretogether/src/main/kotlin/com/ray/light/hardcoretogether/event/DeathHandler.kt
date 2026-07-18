package com.ray.light.hardcoretogether.event

import com.ray.light.hardcoretogether.HardcoreTogether
import com.ray.light.hardcoretogether.archive.ArchiveOps
import com.ray.light.hardcoretogether.config.BossCategory
import com.ray.light.hardcoretogether.config.BossConfig
import com.ray.light.hardcoretogether.gate.GateClient
import com.ray.light.hardcoretogether.records.PlayerRef
import com.ray.light.hardcoretogether.records.RecordEvent
import com.ray.light.hardcoretogether.records.RecordStore
import com.ray.light.hardcoretogether.records.Trigger
import com.ray.light.hardcoretogether.state.ChallengeState
import com.ray.light.hardcoretogether.timer.ElapsedTimeTracker
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.GameType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import java.time.Instant

/**
 * Spec 4.3: player death (party wipe after a countdown) and boss-kill (checkpoint/clear)
 * handling. Both are driven off LivingDeathEvent, gated on ChallengeState.running.
 */
object DeathHandler {
    private const val COUNTDOWN_SECONDS = 10
    private const val COUNTDOWN_TICKS = COUNTDOWN_SECONDS * 20

    private var countdownActive = false
    private var countdownTicksRemaining = 0
    private var pendingRespawn: ServerPlayer? = null

    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        val level = event.entity.level() as? ServerLevel ?: return
        val server = level.server
        val state = ChallengeState.get(server)
        if (!state.running) return

        val entity = event.entity
        if (entity is ServerPlayer) {
            handlePlayerDeath(server, state, entity, event)
            return
        }

        val mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)?.toString() ?: return
        val category = BossConfig.categoryOf(mobId)
        if (category != BossCategory.NONE) {
            handleBossKill(server, state, mobId, category)
        }
    }

    /** Call once per server tick from HardcoreTogether's tick listener. */
    fun onServerTick(server: MinecraftServer) {
        pendingRespawn?.let { player ->
            resetAndSpectate(server, player)
            pendingRespawn = null
        }

        if (!countdownActive) return

        if (countdownTicksRemaining % 20 == 0) {
            broadcastCountdown(server, countdownTicksRemaining / 20)
        }
        if (countdownTicksRemaining <= 0) {
            finishWipe(server)
        } else {
            countdownTicksRemaining--
        }
    }

    private fun handlePlayerDeath(server: MinecraftServer, state: ChallengeState, player: ServerPlayer, event: LivingDeathEvent) {
        // Spec 4.3 step 1: force a spectator respawn next tick, regardless of whether this
        // death is the one that starts the wipe countdown.
        pendingRespawn = player

        // Spec 4.3 step 2: only the first death of a wipe is recorded / starts the countdown.
        if (countdownActive) return

        val killLog = event.source.getLocalizedDeathMessage(player).string
        RecordStore.appendEvent(
            server,
            state.challengeId,
            RecordEvent.death(
                elapsedTime = ElapsedTimeTracker.elapsedSeconds(),
                timestamp = Instant.now().toString(),
                deadPlayer = PlayerRef(player.stringUUID, player.gameProfile.name),
                killLog = killLog,
            ),
        )

        for (p in server.playerList.players) {
            sendTitle(p, Component.literal(player.gameProfile.name), Component.literal("が死亡...！"), 0, 40, 10)
            p.playNotifySound(SoundEvents.PLAYER_HURT, SoundSource.MASTER, 1f, 0.8f)
        }

        countdownActive = true
        countdownTicksRemaining = COUNTDOWN_TICKS
    }

    private fun broadcastCountdown(server: MinecraftServer, secondsLeft: Int) {
        for (p in server.playerList.players) {
            sendTitle(p, Component.literal(secondsLeft.toString()), Component.literal("全滅まで"), 0, 20, 4)
            p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1f, 1f)
        }
    }

    private fun finishWipe(server: MinecraftServer) {
        countdownActive = false
        for (p in server.playerList.players) {
            if (p.gameMode.gameModeForPlayer == GameType.SURVIVAL) {
                p.setGameMode(GameType.SPECTATOR)
            }
            sendTitle(p, Component.literal("全滅！"), Component.literal("/lobbyで退出"), 0, 120, 10)
            p.playNotifySound(SoundEvents.ENDER_DRAGON_DEATH, SoundSource.MASTER, 1f, 1f)
        }

        val state = ChallengeState.get(server)
        state.setRunning(false)
        GateClient.sendRunningChanged(false)
    }

    private fun resetAndSpectate(server: MinecraftServer, oldPlayer: ServerPlayer) {
        // PlayerList#respawn returns a NEW ServerPlayer instance; oldPlayer is no longer valid after this.
        val player = server.playerList.respawn(oldPlayer, false, Entity.RemovalReason.KILLED)
        player.setGameMode(GameType.SPECTATOR)
        player.foodData.foodLevel = 20
        player.foodData.setSaturation(5f)
    }

    private fun handleBossKill(server: MinecraftServer, state: ChallengeState, mobId: String, category: BossCategory) {
        val name = ArchiveOps.timestampName()
        val elapsed = ElapsedTimeTracker.elapsedSeconds()
        val completed = ArchiveOps.performArchive(server, name, elapsed)
        if (!completed) {
            HardcoreTogether.LOGGER.error("Boss-kill archive '$name' did not complete in time; continuing anyway")
        }

        val trigger = Trigger.boss(mobId)
        when (category) {
            BossCategory.CHECKPOINT -> {
                RecordStore.appendEvent(
                    server,
                    state.challengeId,
                    RecordEvent.save(elapsed, Instant.now().toString(), name, trigger),
                )
            }
            BossCategory.CLEAR -> {
                RecordStore.appendEvent(
                    server,
                    state.challengeId,
                    RecordEvent.clear(elapsed, Instant.now().toString(), trigger),
                )
                state.setRunning(false)
                GateClient.sendRunningChanged(false)
            }
            BossCategory.NONE -> Unit
        }
    }

    private fun sendTitle(player: ServerPlayer, title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) {
        player.connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
        player.connection.send(ClientboundSetTitleTextPacket(title))
        player.connection.send(ClientboundSetSubtitleTextPacket(subtitle))
    }
}
