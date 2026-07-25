package com.ray.light.hardcoretogether.adapter.neoforge

import com.ray.light.hardcoretogether.HardcoreTogether
import com.ray.light.hardcoretogether.application.ChallengeApplicationService
import com.ray.light.hardcoretogether.domain.BossCategory
import com.ray.light.hardcoretogether.domain.BossTrigger
import com.ray.light.hardcoretogether.domain.PlayerRef
import com.ray.light.hardcoretogether.port.ArchiveResult
import com.ray.light.hardcoretogether.port.ChallengeState
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.core.component.DataComponents
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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent

/**
 * Spec 4.3: player death (party wipe after a countdown) and boss-kill (checkpoint/clear)
 * handling. Both are gated on ChallengeState.running. This holds per-server mutable
 * countdown state, so it must be an instance (created once per server start in Runtime),
 * not a singleton object like the pre-refactor DeathHandler was.
 */
class DeathCountdown(
    private val applicationService: ChallengeApplicationService,
    private val challengeState: ChallengeState,
) {
    private var countdownActive = false
    private var countdownTicksRemaining = 0

    fun onLivingDeath(event: LivingDeathEvent) {
        val level = event.entity.level() as? ServerLevel ?: return
        val server = level.server
        if (!challengeState.running) return

        val entity = event.entity
        if (entity is ServerPlayer) {
            handlePlayerDeath(server, entity, event)
            return
        }

        val mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)?.toString() ?: return
        val category = HardcoreConfig.categoryOf(mobId)
        if (category != BossCategory.NONE) {
            handleBossKill(level, entity, mobId, category)
        }
    }

    /** Call once per server tick from Runtime.onServerTick. */
    fun onServerTick(server: MinecraftServer) {
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

    private fun handlePlayerDeath(server: MinecraftServer, player: ServerPlayer, event: LivingDeathEvent) {
        // Spec 4.3 step 1: cancel the vanilla death entirely (no drops, no respawn-screen
        // round trip) and switch the same entity to spectator in place. This must happen
        // synchronously, before this tick's entity-data sync reaches the client - the health
        // value was already dropped to <=0 by the damage that triggered this event, and once a
        // sync packet carrying that value reaches the client it renders the death screen, whose
        // "respawn" button is the very thing that trips vanilla's hardcore ban (see spec 5.3).
        // Restoring health here, before that sync happens, means the client never sees it.
        event.isCanceled = true
        val deathMessage = player.combatTracker.deathMessage
        server.playerList.broadcastSystemMessage(deathMessage, false)
        player.setHealth(player.maxHealth)
        player.foodData.foodLevel = 20
        player.foodData.setSaturation(5f)
        player.setGameMode(GameType.SPECTATOR)

        // Spec 4.3 step 2: only the first death of a wipe is recorded / starts the countdown.
        if (countdownActive) return

        applicationService.onPlayerDeath(
            PlayerRef(player.stringUUID, player.gameProfile.name),
            deathMessage.string,
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
    }

    private fun handleBossKill(level: ServerLevel, entity: LivingEntity, mobId: String, category: BossCategory) {
        val trigger = BossTrigger(mobId)
        val bossName = entity.type.description
        val pos = entity.position()
        val onResult: (ArchiveResult) -> Unit = { result ->
            when (result) {
                is ArchiveResult.Success -> {}
                is ArchiveResult.Rejected ->
                    HardcoreTogether.LOGGER.error("Boss-kill archive for '$mobId' was rejected: ${result.reason}; continuing anyway")
                is ArchiveResult.TimedOut ->
                    HardcoreTogether.LOGGER.error("Boss-kill archive for '$mobId' did not complete in time; continuing anyway")
                is ArchiveResult.NotConnected ->
                    HardcoreTogether.LOGGER.error("Boss-kill archive for '$mobId' failed: not connected to Gate; continuing anyway")
            }
        }
        when (category) {
            BossCategory.CHECKPOINT -> {
                announceCheckpoint(level, pos, bossName)
                applicationService.recordCheckpoint(trigger, onResult)
            }
            BossCategory.CLEAR -> {
                announceClear(level, pos, bossName)
                applicationService.recordClear(trigger, onResult)
            }
            BossCategory.NONE -> return
        }
    }

    private fun announceCheckpoint(level: ServerLevel, pos: Vec3, bossName: Component) {
        val server = level.server
        server.playerList.broadcastSystemMessage(
            Component.literal("").append(bossName).append(Component.literal("を討伐！チェックポイントを保存しました")),
            false,
        )
        for (p in server.playerList.players) {
            sendTitle(p, Component.literal("チェックポイント達成"), bossName, 0, 60, 20)
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1f, 1f)
        }
        spawnFirework(level, pos, 0)
    }

    private fun announceClear(level: ServerLevel, pos: Vec3, bossName: Component) {
        val server = level.server
        server.playerList.broadcastSystemMessage(
            Component.literal("").append(bossName).append(Component.literal("を討伐！挑戦クリア！")),
            false,
        )
        for (p in server.playerList.players) {
            sendTitle(p, Component.literal("挑戦クリア！"), Component.literal("/lobbyで退出"), 0, 120, 10)
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f)
        }
        // flightDurationを0〜CLEAR_FIREWORK_MAX_FLIGHT_DURATIONへ散らし、爆発タイミングを
        // 約5秒間に分散させる（FireworkRocketEntityの寿命は概ね10*(1+flightDuration)tick）。
        repeat(CLEAR_FIREWORK_COUNT) {
            spawnFirework(level, pos, (0..CLEAR_FIREWORK_MAX_FLIGHT_DURATION).random())
        }
    }

    private fun spawnFirework(level: ServerLevel, pos: Vec3, flightDuration: Int) {
        val explosion = FireworkExplosion(
            FIREWORK_SHAPES.random(),
            IntList.of(FIREWORK_COLORS.random().fireworkColor, FIREWORK_COLORS.random().fireworkColor),
            IntList.of(),
            true,
            true,
        )
        val stack = ItemStack(Items.FIREWORK_ROCKET)
        stack.set(DataComponents.FIREWORKS, Fireworks(flightDuration, listOf(explosion)))
        level.addFreshEntity(FireworkRocketEntity(level, pos.x, pos.y, pos.z, stack))
    }

    private fun sendTitle(player: ServerPlayer, title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) {
        player.connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
        player.connection.send(ClientboundSetTitleTextPacket(title))
        player.connection.send(ClientboundSetSubtitleTextPacket(subtitle))
    }

    companion object {
        private const val COUNTDOWN_SECONDS = 10
        private const val COUNTDOWN_TICKS = COUNTDOWN_SECONDS * 20

        private const val CLEAR_FIREWORK_COUNT = 8
        private const val CLEAR_FIREWORK_MAX_FLIGHT_DURATION = 9 // 10*(1+9)tick ≈ 5秒付近まで散らす

        private val FIREWORK_SHAPES = FireworkExplosion.Shape.entries.toTypedArray()
        private val FIREWORK_COLORS = DyeColor.entries.toTypedArray()
    }
}
