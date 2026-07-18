package com.ray.light.hardcoretogether.config

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Spec 4.4: bosses.checkpoint / bosses.clear, both multi-value lists.
 * A mob id registered in both lists is treated as clear (spec 4.4: "同一IDを両方に
 * 重複登録した場合はclear側を優先").
 */
enum class BossCategory { CHECKPOINT, CLEAR, NONE }

object BossConfig {
    private val BUILDER = ModConfigSpec.Builder()

    private val CHECKPOINT_BOSSES: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("Mobs that trigger an automatic checkpoint save when killed. The challenge keeps running.")
        .defineListAllowEmpty(
            listOf("bosses", "checkpoint"),
            {
                listOf(
                    "twilightforest:naga",
                    "twilightforest:lich",
                    "twilightforest:hydra",
                    "twilightforest:knight_phantom",
                )
            },
            { "" },
            { it is String },
        )

    private val CLEAR_BOSSES: ModConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment("Mobs that trigger an automatic archive and end the challenge as a clear when killed.")
        .defineListAllowEmpty(
            listOf("bosses", "clear"),
            { listOf("twilightforest:ur_ghast", "twilightforest:alpha_yeti") },
            { "" },
            { it is String },
        )

    val SPEC: ModConfigSpec = BUILDER.build()

    fun categoryOf(mobId: String): BossCategory = when {
        CLEAR_BOSSES.get().contains(mobId) -> BossCategory.CLEAR
        CHECKPOINT_BOSSES.get().contains(mobId) -> BossCategory.CHECKPOINT
        else -> BossCategory.NONE
    }
}
