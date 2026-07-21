package com.ray.light.hardcoretogether.domain

/**
 * JSON非依存の汎用木構造。Map/Listと同格の「ネスト可能な汎用値」であり、
 * この型自体はGson/JSONという表現形式を一切知らない（それらへの変換はadapter層の責務）。
 */
sealed interface StructuredValue {
    data class Str(val value: String) : StructuredValue
    data class Num(val value: Long) : StructuredValue
    data class Bool(val value: Boolean) : StructuredValue
    data class Arr(val items: List<StructuredValue>) : StructuredValue
    data class Obj(val fields: Map<String, StructuredValue>) : StructuredValue

    companion object {
        fun of(value: String): StructuredValue = Str(value)
        fun of(value: Long): StructuredValue = Num(value)
        fun of(value: Boolean): StructuredValue = Bool(value)
        fun obj(vararg fields: Pair<String, StructuredValue>): Obj = Obj(fields.toMap())
    }
}
