package com.ray.light.hardcoretogether.records

/**
 * Spec 4.5: one JSON file per challengeId, containing a running lastKnownElapsedTime
 * checkpoint plus a chronological event log (save / death / clear).
 */
data class Trigger(
    val kind: String, // "boss" | "manual"
    val mobId: String? = null,
    val player: String? = null,
) {
    companion object {
        fun boss(mobId: String) = Trigger(kind = "boss", mobId = mobId)
        fun manual(player: String) = Trigger(kind = "manual", player = player)
    }
}

data class PlayerRef(
    val uuid: String,
    val name: String,
)

data class RecordEvent(
    val type: String, // "save" | "death" | "clear"
    val elapsedTime: Long,
    val timestamp: String,
    val archiveName: String? = null,
    val trigger: Trigger? = null,
    val deadPlayer: PlayerRef? = null,
    val killLog: String? = null,
) {
    companion object {
        fun save(elapsedTime: Long, timestamp: String, archiveName: String, trigger: Trigger) =
            RecordEvent(type = "save", elapsedTime = elapsedTime, timestamp = timestamp, archiveName = archiveName, trigger = trigger)

        fun death(elapsedTime: Long, timestamp: String, deadPlayer: PlayerRef, killLog: String) =
            RecordEvent(type = "death", elapsedTime = elapsedTime, timestamp = timestamp, deadPlayer = deadPlayer, killLog = killLog)

        fun clear(elapsedTime: Long, timestamp: String, trigger: Trigger) =
            RecordEvent(type = "clear", elapsedTime = elapsedTime, timestamp = timestamp, trigger = trigger)
    }
}

data class ChallengeRecordFile(
    val challengeId: String,
    var lastKnownElapsedTime: Long = 0L,
    val events: MutableList<RecordEvent> = mutableListOf(),
)
