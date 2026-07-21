package com.ray.light.hardcoretogether.port

/** running/challengeIdの読み書き。実装はNeoForgeのSavedDataを包む（adapter/neoforge）。 */
interface ChallengeState {
    var running: Boolean
    val challengeId: String
}
