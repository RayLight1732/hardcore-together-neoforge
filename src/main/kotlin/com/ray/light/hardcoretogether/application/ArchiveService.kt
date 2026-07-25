package com.ray.light.hardcoretogether.application

import com.ray.light.hardcoretogether.port.ArchiveGateway
import com.ray.light.hardcoretogether.port.ArchiveResult
import java.time.Instant

/** アーカイブ名の採番とArchiveGatewayの呼び出し。 */
class ArchiveService(private val gateway: ArchiveGateway) {

    /** 自動アーカイブ（ボスキル等）用：名前を自前で採番してgatewayへ渡す。名前は同期的に返せる。 */
    fun archiveWithGeneratedName(elapsedSeconds: Long, onResult: (ArchiveResult) -> Unit): String {
        val name = timestampName()
        gateway.archive(name, elapsedSeconds, onResult)
        return name
    }

    /** 手動アーカイブ（/archiveコマンド）用：名前は呼び出し側が指定する。 */
    fun archive(name: String, elapsedSeconds: Long, onResult: (ArchiveResult) -> Unit) =
        gateway.archive(name, elapsedSeconds, onResult)

    private fun timestampName(): String =
        Instant.now().toString().replace(":", "-").substringBefore(".")
}
