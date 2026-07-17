package com.ray.light.hardcoretogether.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.ray.light.hardcoretogether.HardcoreTogether
import com.ray.light.hardcoretogether.archive.ArchiveOps
import com.ray.light.hardcoretogether.records.RecordEvent
import com.ray.light.hardcoretogether.records.RecordStore
import com.ray.light.hardcoretogether.records.Trigger
import com.ray.light.hardcoretogether.state.ChallengeState
import com.ray.light.hardcoretogether.timer.ElapsedTimeTracker
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.time.Instant

/** Spec 4.2: /archive, /savedata, /senpan. /start and /load live on Gate, not here (2.1). */
@EventBusSubscriber(modid = HardcoreTogether.ID)
object ModCommands {

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("archive")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .executes { ctx -> archive(ctx.source, StringArgumentType.getString(ctx, "name")) },
                ),
        )

        event.dispatcher.register(
            Commands.literal("savedata")
                .executes { ctx -> savedata(ctx.source) },
        )

        event.dispatcher.register(
            Commands.literal("senpan")
                .then(Commands.literal("list").executes { ctx -> senpanList(ctx.source) })
                .then(Commands.literal("count").executes { ctx -> senpanCount(ctx.source) }),
        )
    }

    private fun archive(source: CommandSourceStack, name: String): Int {
        val server = source.server
        val elapsed = ElapsedTimeTracker.elapsedSeconds()
        val completed = ArchiveOps.performArchive(server, name, elapsed)

        if (!completed) {
            source.sendFailure(Component.literal("アーカイブに失敗しました（名前の重複、またはGateからの応答がタイムアウトしました）: $name"))
            return 0
        }

        val state = ChallengeState.get(server)
        val triggerPlayer = (source.entity as? ServerPlayer)?.stringUUID ?: "console"
        RecordStore.appendEvent(
            server,
            state.challengeId,
            RecordEvent.save(
                elapsedTime = elapsed,
                timestamp = Instant.now().toString(),
                archiveName = name,
                trigger = Trigger.manual(triggerPlayer),
            ),
        )
        source.sendSuccess({ Component.literal("保存完了: $name") }, true)
        return 1
    }

    private fun savedata(source: CommandSourceStack): Int {
        val files = RecordStore.listAll(source.server)
        source.sendSuccess({ Component.literal("挑戦記録一覧（challengeId数: ${files.size}）") }, false)
        for (file in files) {
            for (e in file.events) {
                val detail = when (e.type) {
                    "save" -> "trigger=${e.trigger?.kind}:${e.trigger?.mobId ?: e.trigger?.player}"
                    "death" -> "戦犯=${e.deadPlayer?.name} (${e.killLog})"
                    "clear" -> "trigger=${e.trigger?.mobId}"
                    else -> ""
                }
                source.sendSuccess(
                    { Component.literal("[${file.challengeId.take(8)}] ${e.type} @ ${ElapsedTimeTracker.formatted(e.elapsedTime)} - $detail") },
                    false,
                )
            }
        }
        return 1
    }

    private fun deathEvents(source: CommandSourceStack): List<RecordEvent> =
        RecordStore.listAll(source.server).flatMap { it.events }.filter { it.type == "death" }

    private fun senpanList(source: CommandSourceStack): Int {
        val deaths = deathEvents(source)
        source.sendSuccess({ Component.literal("戦犯一覧") }, false)
        for (d in deaths) {
            source.sendSuccess({ Component.literal("${d.deadPlayer?.name}: ${d.killLog} (@${ElapsedTimeTracker.formatted(d.elapsedTime)})") }, false)
        }
        return 1
    }

    private fun senpanCount(source: CommandSourceStack): Int {
        val counts = deathEvents(source)
            .groupingBy { it.deadPlayer?.name ?: "unknown" }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        source.sendSuccess({ Component.literal("戦犯カウント") }, false)
        for ((name, count) in counts) {
            source.sendSuccess({ Component.literal("$name: ${count}回") }, false)
        }
        return 1
    }
}
