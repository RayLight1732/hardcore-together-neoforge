package com.ray.light.hardcoretogether.domain

data class PlayerRef(
    val uuid: String,
    val name: String,
)

/**
 * "type"タグ("save"/"death"/"clear")とtype以外のデータ(fields)を分離する。理由はTrigger.ktと同じ:
 * describe()をoverride不可な拡張関数にすることで、"type"キーの存在を構造的に保証する。
 */
sealed class RecordEvent(val elapsedTime: Long, val timestamp: String) {
    abstract val type: String
    abstract fun fields(): StructuredValue.Obj

    class Save(
        elapsedTime: Long,
        timestamp: String,
        val archiveName: String,
        val trigger: Trigger,
    ) : RecordEvent(elapsedTime, timestamp) {
        override val type = "save"
        override fun fields() = StructuredValue.obj(
            "archiveName" to StructuredValue.of(archiveName),
            "trigger" to trigger.describe(),
        )
    }

    class Clear(
        elapsedTime: Long,
        timestamp: String,
        val trigger: Trigger,
    ) : RecordEvent(elapsedTime, timestamp) {
        override val type = "clear"
        override fun fields() = StructuredValue.obj("trigger" to trigger.describe())
    }

    class Death(
        elapsedTime: Long,
        timestamp: String,
        val deadPlayer: PlayerRef,
        val killLog: String,
    ) : RecordEvent(elapsedTime, timestamp) {
        override val type = "death"
        override fun fields() = StructuredValue.obj(
            "deadPlayer" to StructuredValue.obj(
                "uuid" to StructuredValue.of(deadPlayer.uuid),
                "name" to StructuredValue.of(deadPlayer.name),
            ),
            "killLog" to StructuredValue.of(killLog),
        )
    }
}

fun RecordEvent.describe(): StructuredValue.Obj = StructuredValue.Obj(
    mapOf(
        "type" to StructuredValue.of(type),
        "elapsedTime" to StructuredValue.of(elapsedTime),
        "timestamp" to StructuredValue.of(timestamp),
    ) + fields().fields
)
