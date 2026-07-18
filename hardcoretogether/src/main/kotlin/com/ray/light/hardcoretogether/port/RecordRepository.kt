package com.ray.light.hardcoretogether.port

import com.ray.light.hardcoretogether.domain.RecordEvent

/**
 * records/<challengeId>.jsonの読み書き。実装は`dir: Path`を取る（MinecraftServerではない）。
 * 一覧の読み取り（/savedata・/senpan相当）はGateが同一ホスト前提でこのファイルを直接読むため
 * （specification.md 2.6節）、MOD側のportに読み取り一覧APIは持たない。
 */
interface RecordRepository {
    fun appendEvent(challengeId: String, event: RecordEvent)
    fun updateLastKnownElapsedTime(challengeId: String, elapsedSeconds: Long)
    fun lastKnownElapsedTime(challengeId: String): Long
}
