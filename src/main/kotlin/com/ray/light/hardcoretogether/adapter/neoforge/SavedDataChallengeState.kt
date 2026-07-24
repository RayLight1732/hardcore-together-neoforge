package com.ray.light.hardcoretogether.adapter.neoforge

import com.ray.light.hardcoretogether.port.ChallengeState
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Spec 4.1: running + challengeId live in SavedData (part of the world save folder, so it
 * naturally rewinds with /load). Elapsed time and the dead-player UUID are deliberately NOT
 * stored here (see spec 4.1 / 4.5 for why).
 */
class SavedDataChallengeState private constructor(
    running: Boolean,
    override val challengeId: String,
) : SavedData(), ChallengeState {

    override var running: Boolean = running
        set(value) {
            if (field != value) {
                field = value
                setDirty()
            }
        }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putBoolean("running", running)
        tag.putString("challengeId", challengeId)
        return tag
    }

    companion object {
        private const val ID = "hardcoretogether_challenge"

        // Spec 4.1: running defaults to true on fresh creation (a brand new /start challenge).
        // setDirty() must be called explicitly: the primary constructor's parameter initializer
        // assigns the backing field directly and bypasses the `running` setter below, so without
        // this the SavedData is never marked dirty and world/data/hardcoretogether_challenge.dat
        // is never written until the challenge ends.
        private fun create(): SavedDataChallengeState =
            SavedDataChallengeState(running = true, challengeId = UUID.randomUUID().toString()).apply { setDirty() }

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): SavedDataChallengeState =
            SavedDataChallengeState(
                running = tag.getBoolean("running"),
                challengeId = tag.getString("challengeId"),
            )

        private val FACTORY = Factory(::create, ::load)

        fun get(server: MinecraftServer): SavedDataChallengeState =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, ID)
    }
}
