package com.ray.light.hardcoretogether.adapter.neoforge

import com.ray.light.hardcoretogether.adapter.file.FileRecordRepository
import com.ray.light.hardcoretogether.adapter.gate.ArchiveGatewayImpl
import com.ray.light.hardcoretogether.application.ArchiveService
import com.ray.light.hardcoretogether.application.ChallengeApplicationService
import com.ray.light.hardcoretogether.application.ChallengeService
import com.ray.light.hardcoretogether.application.RecordService
import com.ray.light.hardcoretogether.domain.Challenge
import com.ray.light.hardcoretogether.port.ArchiveGateway
import com.ray.light.hardcoretogether.port.ChallengeState
import net.minecraft.server.MinecraftServer
import java.nio.file.Path

/**
 * Composition root's per-server-session bundle. HardcoreTogether (the NeoForge entry point)
 * stays an object because NeoForge's event bus requires it, but everything with real
 * business state lives in here, constructed once in ServerStartedEvent.
 */
class Runtime private constructor(
    private val server: MinecraftServer,
    val challengeState: ChallengeState,
    val archiveGateway: ArchiveGateway,
    val challengeService: ChallengeService,
    val archiveService: ArchiveService,
    val recordService: RecordService,
    val applicationService: ChallengeApplicationService,
    val deathCountdown: DeathCountdown,
) {
    private var lastTickNanos = System.nanoTime()

    fun onServerTick() {
        val now = System.nanoTime()
        val deltaNanos = now - lastTickNanos
        lastTickNanos = now

        applicationService.onServerTick(deltaNanos)
        deathCountdown.onServerTick(server)
    }

    companion object {
        fun create(server: MinecraftServer): Runtime {
            val challengeState = SavedDataChallengeState.get(server)
            val recordRepository = FileRecordRepository(resolveRecordsDir(server))
            val archiveGateway = ArchiveGatewayImpl(server)

            val challenge = Challenge(
                id = challengeState.challengeId,
                running = challengeState.running,
                elapsedSeconds = recordRepository.lastKnownElapsedTime(challengeState.challengeId),
            )

            val challengeService = ChallengeService(challenge, challengeState)
            val archiveService = ArchiveService(archiveGateway)
            val recordService = RecordService(recordRepository)
            val applicationService = ChallengeApplicationService(challengeService, archiveService, recordService, archiveGateway)
            val deathCountdown = DeathCountdown(applicationService, challengeState)

            return Runtime(
                server = server,
                challengeState = challengeState,
                archiveGateway = archiveGateway,
                challengeService = challengeService,
                archiveService = archiveService,
                recordService = recordService,
                applicationService = applicationService,
                deathCountdown = deathCountdown,
            )
        }

        /**
         * Spec 4.5 / 2.6: HardcoreConfig.recordsDir()以外にこの値を持たない - Gate側の設定と
         * 一致させる必要があるため、パスの解決規則もここ1箇所に閉じる。相対パスはこのサーバーの
         * 作業ディレクトリ基準、絶対パスはそのまま使う。
         */
        private fun resolveRecordsDir(server: MinecraftServer): Path {
            val configured = Path.of(HardcoreConfig.recordsDir())
            return if (configured.isAbsolute) configured else server.serverDirectory.resolve(configured)
        }
    }
}
