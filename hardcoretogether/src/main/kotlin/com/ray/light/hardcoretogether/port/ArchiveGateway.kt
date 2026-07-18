package com.ray.light.hardcoretogether.port

/** Gateとの永続TCP+NDJSON接続、および save-off/flush/archive-request/save-on の全体ブラケット。 */
interface ArchiveGateway {
    fun connect()
    fun sendReady(running: Boolean)
    fun sendRunningChanged(running: Boolean)

    /** save-off → save-all flush → archive-request → archive-complete待ち → save-on を実行する。 */
    fun archive(name: String, elapsedTime: Long): Boolean
}
