package com.ray.light.hardcoretogether.application

import com.ray.light.hardcoretogether.port.ArchiveGateway
import com.ray.light.hardcoretogether.port.ArchiveResult
import java.time.Instant

/** アーカイブ名の採番とArchiveGatewayの呼び出し。 */
class ArchiveService(private val gateway: ArchiveGateway) {

    /** 自動アーカイブ（ボスキル等）用：名前を自前で採番し、結果も返す。 */
    fun archiveWithGeneratedName(elapsedSeconds: Long): Pair<String, ArchiveResult> {
        val name = timestampName()
        val result = gateway.archive(name, elapsedSeconds)
        return name to result
    }

    /** 手動アーカイブ（/archiveコマンド）用：名前は呼び出し側が指定する。 */
    fun archive(name: String, elapsedSeconds: Long): ArchiveResult = gateway.archive(name, elapsedSeconds)

    private fun timestampName(): String =
        Instant.now().toString().replace(":", "-").substringBefore(".")
}
