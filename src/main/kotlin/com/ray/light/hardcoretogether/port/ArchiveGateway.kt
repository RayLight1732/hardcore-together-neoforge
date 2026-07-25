package com.ray.light.hardcoretogether.port

/**
 * archive-requestの結果（protocol-mod-manager.md 3.3〜3.5節）。
 * `Rejected`は`archive-rejected`受信（名前重複等）による即時失敗、`TimedOut`はどちらの応答も
 * 一定時間届かなかった場合のフォールバック、`NotConnected`はGateへ未接続のため送信自体ができな
 * かった（または送信後に接続が切れた）場合で、区別する（同節の応答待ちの規約、
 * architecture-neoforge.md「`/archive`アーカイブ経路の非同期化」節）。
 */
sealed class ArchiveResult {
    data object Success : ArchiveResult()
    data class Rejected(val reason: String) : ArchiveResult()
    data object TimedOut : ArchiveResult()
    data object NotConnected : ArchiveResult()
}

/** Gateとの永続TCP+NDJSON接続、および save-off/flush/archive-request/save-on の全体ブラケット。 */
interface ArchiveGateway {
    fun connect()
    fun sendReady(running: Boolean)
    fun sendRunningChanged(running: Boolean)

    /**
     * save-off → save-all flush → archive-request を送って即座に返る（メインスレッドをブロック
     * しない）。結果は`onResult`へ非同期に通知される。呼び出し元がMinecraftServerの状態に触れる
     * 場合、`onResult`は必ずメインスレッドへ処理を委譲してから使うこと（実装側の責務）。
     */
    fun archive(name: String, elapsedTime: Long, onResult: (ArchiveResult) -> Unit)
}
