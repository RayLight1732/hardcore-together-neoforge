package com.ray.light.hardcoretogether.domain

/**
 * "kind"タグと、それ以外のデータ(fields)を分離する。describe()はoverride不可な拡張関数として
 * 両者を合成するため、"kind"キーの存在はinterfaceの契約として構造的に保証される
 * （fun describe(): StructuredValue.Objを各実装に自由にoverrideさせる形だと、"kind"を
 * 書き忘れてもコンパイルは通ってしまう）。
 */
interface Trigger {
    val kind: String
    fun fields(): StructuredValue.Obj
}

fun Trigger.describe(): StructuredValue.Obj =
    StructuredValue.Obj(mapOf("kind" to StructuredValue.of(kind)) + fields().fields)

data class BossTrigger(val mobId: String) : Trigger {
    override val kind = "boss"
    override fun fields() = StructuredValue.obj("mobId" to StructuredValue.of(mobId))
}

data class ManualTrigger(val player: String) : Trigger {
    override val kind = "manual"
    override fun fields() = StructuredValue.obj("player" to StructuredValue.of(player))
}
