package com.ray.light.hardcoretogether.adapter.file

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.ray.light.hardcoretogether.domain.StructuredValue

/**
 * StructuredValue → Gson JsonElementの汎用変換。新しいTrigger/RecordEventが増えても、
 * このファイルは変更しない（Trigger/RecordEventごとの知識は一切持たない）。
 *
 * 読み取り方向（JsonElement → StructuredValue）は持たない：records/<challengeId>.jsonの
 * 一覧読み取り（/savedata・/senpan）はGateが直接ファイルを読むため（specification.md 2.6節）、
 * MOD側は書き込み専用でよい。
 */
fun StructuredValue.toGson(): JsonElement = when (this) {
    is StructuredValue.Str -> JsonPrimitive(value)
    is StructuredValue.Num -> JsonPrimitive(value)
    is StructuredValue.Bool -> JsonPrimitive(value)
    is StructuredValue.Arr -> JsonArray().apply { items.forEach { add(it.toGson()) } }
    is StructuredValue.Obj -> JsonObject().apply { fields.forEach { (k, v) -> add(k, v.toGson()) } }
}
