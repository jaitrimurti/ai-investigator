package com.wasserbell.aiinvestigator.data.repository

import java.io.File

actual suspend fun readLocalConfigFile(): ByteArray? {
    val file = File("config.txt")
    return if (file.exists() && file.isFile) {
        file.readBytes()
    } else {
        null
    }
}
