package com.sovworks.eds.container

import com.sovworks.eds.crypto.FileEncryptionEngine
import java.io.Closeable


interface EncryptedFileLayout : Closeable {
    @JvmField
    val encryptedDataOffset: Long

    fun getEncryptedDataSize(fileSize: Long): Long

    @JvmField
    val engine: FileEncryptionEngine?

    fun setEncryptionEngineIV(eng: FileEncryptionEngine, decryptedVolumeOffset: Long)
}