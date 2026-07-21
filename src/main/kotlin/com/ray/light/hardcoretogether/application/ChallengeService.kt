package com.ray.light.hardcoretogether.application

import com.ray.light.hardcoretogether.domain.Challenge
import com.ray.light.hardcoretogether.port.ChallengeState

/** Challengeの生成・進行・終了、ChallengeStateとの同期。 */
class ChallengeService(
    private val challenge: Challenge,
    private val state: ChallengeState,
) {
    val id: String get() = challenge.id

    fun elapsedSeconds(): Long = challenge.elapsedSeconds()

    fun tick(deltaNanos: Long) = challenge.tick(deltaNanos)

    fun end() {
        challenge.end()
        state.running = false
    }
}
