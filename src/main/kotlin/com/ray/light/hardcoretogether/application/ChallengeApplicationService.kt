package com.ray.light.hardcoretogether.application

import com.ray.light.hardcoretogether.domain.PlayerRef
import com.ray.light.hardcoretogether.domain.Trigger
import com.ray.light.hardcoretogether.port.ArchiveGateway

/**
 * 薄いオーケストレーション。公開APIは起きたことの種類ではなく、結果として何を記録するかで分ける。
 * archiveGatewayはrunning-changed通知の送信専用に持つ（アーカイブそのものはArchiveService経由）。
 */
class ChallengeApplicationService(
    private val challengeService: ChallengeService,
    private val archiveService: ArchiveService,
    private val recordService: RecordService,
    private val archiveGateway: ArchiveGateway,
) {
    private var nanosSinceLastPersist: Long = 0L

    fun onServerTick(deltaNanos: Long) {
        challengeService.tick(deltaNanos)
        nanosSinceLastPersist += deltaNanos
        if (nanosSinceLastPersist >= PERSIST_INTERVAL_NANOS) {
            recordService.updateLastKnownElapsedTime(challengeService.id, challengeService.elapsedSeconds())
            nanosSinceLastPersist = 0L
        }
    }

    /** @return アーカイブがタイムアウトせず完了したか。呼び出し側はfalseならログ等でよい（記録自体は必ず行う）。 */
    fun recordCheckpoint(trigger: Trigger): Boolean {
        val (name, completed) = archiveService.archiveWithGeneratedName(challengeService.elapsedSeconds())
        recordService.appendSave(challengeService.id, challengeService.elapsedSeconds(), name, trigger)
        return completed
    }

    fun recordClear(trigger: Trigger): Boolean {
        val (name, completed) = archiveService.archiveWithGeneratedName(challengeService.elapsedSeconds())
        recordService.appendClear(challengeService.id, challengeService.elapsedSeconds(), trigger)
        endChallenge()
        return completed
    }

    fun onPlayerDeath(player: PlayerRef, killLog: String) {
        recordService.appendDeath(challengeService.id, challengeService.elapsedSeconds(), player, killLog)
        endChallenge()
    }

    private fun endChallenge() {
        challengeService.end()
        archiveGateway.sendRunningChanged(false)
    }

    companion object {
        private const val PERSIST_INTERVAL_NANOS = 10_000_000_000L // spec 4.1: 数秒間隔（例：10秒ごと）
    }
}
