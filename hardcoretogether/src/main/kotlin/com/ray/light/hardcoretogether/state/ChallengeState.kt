package com.ray.light.hardcoretogether.state

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
class ChallengeState private constructor(
    running: Boolean,
    challengeId: String,
) : SavedData() {

    var running: Boolean = running
        private set

    var challengeId: String = challengeId
        private set

    fun setRunning(value: Boolean) {
        if (running != value) {
            running = value
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
        private fun create(): ChallengeState = ChallengeState(running = true, challengeId = UUID.randomUUID().toString())

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): ChallengeState =
            ChallengeState(
                running = tag.getBoolean("running"),
                challengeId = tag.getString("challengeId"),
            )

        private val FACTORY = Factory(::create, ::load)

        fun get(server: MinecraftServer): ChallengeState =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, ID)
    }
}
