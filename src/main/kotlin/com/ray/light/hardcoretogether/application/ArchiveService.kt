package com.ray.light.hardcoretogether.application

import com.ray.light.hardcoretogether.port.ArchiveGateway
import java.time.Instant

/** アーカイブ名の採番とArchiveGatewayの呼び出し。 */
class ArchiveService(private val gateway: ArchiveGateway) {

    /** 自動アーカイブ（ボスキル等）用：名前を自前で採番し、完了したかどうかも返す。 */
    fun archiveWithGeneratedName(elapsedSeconds: Long): Pair<String, Boolean> {
        val name = timestampName()
        val completed = gateway.archive(name, elapsedSeconds)
        return name to completed
    }

    /** 手動アーカイブ（/archiveコマンド）用：名前は呼び出し側が指定する。 */
    fun archive(name: String, elapsedSeconds: Long): Boolean = gateway.archive(name, elapsedSeconds)

    private fun timestampName(): String =
        Instant.now().toString().replace(":", "-").substringBefore(".")
}
