package com.ray.light.hardcoretogether.port

/**
 * archive-requestの結果（protocol-mod-manager.md 3.3〜3.5節）。
 * `Rejected`は`archive-rejected`受信（名前重複等）による即時失敗、`TimedOut`はどちらの応答も
 * 一定時間届かなかった場合のフォールバックで、区別する（同節の応答待ちの規約）。
 */
sealed class ArchiveResult {
    data object Success : ArchiveResult()
    data class Rejected(val reason: String) : ArchiveResult()
    data object TimedOut : ArchiveResult()
}

/** Gateとの永続TCP+NDJSON接続、および save-off/flush/archive-request/save-on の全体ブラケット。 */
interface ArchiveGateway {
    fun connect()
    fun sendReady(running: Boolean)
    fun sendRunningChanged(running: Boolean)

    /** save-off → save-all flush → archive-request → archive-complete/archive-rejected待ち → save-on を実行する。 */
    fun archive(name: String, elapsedTime: Long): ArchiveResult
}
