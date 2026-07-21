package com.ray.light.hardcoretogether.adapter.neoforge

import com.ray.light.hardcoretogether.domain.BossCategory
import net.neoforged.neoforge.common.ModConfigSpec

/**
 * hardcoretogetherの唯一のModConfigSpec（NeoForgeはMOD1つにつきCOMMON設定ファイル1つのため、
 * ボス設定とストレージ設定を1つのオブジェクトにまとめる。TOML上は[bosses]/[storage]でセクション分け）。
 *
 * Spec 4.4: bosses.checkpoint / bosses.clear, both multi-value lists.
 * A mob id registered in both lists is treated as clear (spec 4.4: "同一IDを両方に
 * 重複登録した場合はclear側を優先").
 */
object HardcoreConfig {
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

    /**
     * Spec 4.5 / 2.6: records/<challengeId>.json is written here by this MOD, but is also read
     * directly (as a different process, on the same host) by Gate for /savedata・/senpan. Both
     * sides must therefore agree on this path - if this value is changed, Gate's own config
     * (whatever it uses to locate hardcore's records directory) must be updated to match.
     */
    private val RECORDS_DIR: ModConfigSpec.ConfigValue<String> = BUILDER
        .comment(
            "Directory for per-challenge event log JSON files (records/<challengeId>.json, spec 4.5).",
            "Relative paths are resolved against this server's own working directory; absolute paths are used as-is.",
            "Gate reads this same directory directly (spec 2.6) for /savedata and /senpan, so if you change this,",
            "update Gate's own configuration to point at the same location.",
        )
        .define("storage.recordsDir", "records")

    val SPEC: ModConfigSpec = BUILDER.build()

    fun categoryOf(mobId: String): BossCategory = when {
        CLEAR_BOSSES.get().contains(mobId) -> BossCategory.CLEAR
        CHECKPOINT_BOSSES.get().contains(mobId) -> BossCategory.CHECKPOINT
        else -> BossCategory.NONE
    }

    fun recordsDir(): String = RECORDS_DIR.get()
}
