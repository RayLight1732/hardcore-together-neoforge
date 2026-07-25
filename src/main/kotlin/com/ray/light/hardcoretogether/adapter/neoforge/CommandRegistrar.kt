package com.ray.light.hardcoretogether.adapter.neoforge

import com.mojang.brigadier.arguments.StringArgumentType
import com.ray.light.hardcoretogether.HardcoreTogether
import com.ray.light.hardcoretogether.domain.ManualTrigger
import com.ray.light.hardcoretogether.port.ArchiveResult
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * Spec 4.2: /archive is the one command hardcore MOD still registers directly. Unlike
 * /savedata・/senpan (Gate reads records/ directly, specification.md 2.6節) or /start・/load
 * (need to work while hardcore isn't even running, 2.1節), /archive only ever makes sense
 * while hardcore is actively running - it pauses/resumes autosave on a live Minecraft server
 * process - so relaying it through Gate would add real complexity (new signals, a reader-thread
 * deadlock to avoid, Gate having to message a possibly-elsewhere-connected player) for no
 * practical benefit: an OP invoking it is expected to be on hardcore anyway.
 */
@EventBusSubscriber(modid = HardcoreTogether.ID)
object CommandRegistrar {

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
    }

    private fun archive(source: CommandSourceStack, name: String): Int {
        val runtime = HardcoreTogether.runtime ?: run {
            source.sendFailure(Component.literal("サーバーがまだ起動していません"))
            return 0
        }
        val elapsed = runtime.challengeService.elapsedSeconds()
        when (val result = runtime.archiveService.archive(name, elapsed)) {
            is ArchiveResult.Success -> {}
            is ArchiveResult.Rejected -> {
                source.sendFailure(Component.literal("アーカイブに失敗しました: ${result.reason}"))
                return 0
            }
            is ArchiveResult.TimedOut -> {
                source.sendFailure(Component.literal("アーカイブに失敗しました（Gateからの応答がタイムアウトしました）: $name"))
                return 0
            }
        }

        val triggerPlayer = (source.entity as? ServerPlayer)?.stringUUID ?: "console"
        runtime.recordService.appendSave(runtime.challengeService.id, elapsed, name, ManualTrigger(triggerPlayer))
        source.sendSuccess({ Component.literal("保存完了: $name") }, true)
        return 1
    }
}
