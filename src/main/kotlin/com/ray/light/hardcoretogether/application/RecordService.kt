package com.ray.light.hardcoretogether.application

import com.ray.light.hardcoretogether.domain.PlayerRef
import com.ray.light.hardcoretogether.domain.RecordEvent
import com.ray.light.hardcoretogether.domain.Trigger
import com.ray.light.hardcoretogether.port.RecordRepository
import java.time.Instant

/** RecordEventの組み立てとRecordRepositoryへの保存。 */
class RecordService(private val repository: RecordRepository) {

    fun appendSave(challengeId: String, elapsedSeconds: Long, archiveName: String, trigger: Trigger) =
        repository.appendEvent(challengeId, RecordEvent.Save(elapsedSeconds, Instant.now().toString(), archiveName, trigger))

    fun appendClear(challengeId: String, elapsedSeconds: Long, trigger: Trigger) =
        repository.appendEvent(challengeId, RecordEvent.Clear(elapsedSeconds, Instant.now().toString(), trigger))

    fun appendDeath(challengeId: String, elapsedSeconds: Long, player: PlayerRef, killLog: String) =
        repository.appendEvent(challengeId, RecordEvent.Death(elapsedSeconds, Instant.now().toString(), player, killLog))

    fun updateLastKnownElapsedTime(challengeId: String, elapsedSeconds: Long) =
        repository.updateLastKnownElapsedTime(challengeId, elapsedSeconds)
}
