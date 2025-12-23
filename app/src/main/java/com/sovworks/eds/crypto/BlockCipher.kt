package com.sovworks.eds.crypto

interface BlockCipher {
    @Throws(EncryptionEngineException::class)
    fun init(key: ByteArray?)

    @Throws(EncryptionEngineException::class)
    fun encryptBlock(data: ByteArray?)

    @Throws(EncryptionEngineException::class)
    fun decryptBlock(data: ByteArray?)
    fun close()
    val keySize: Int
    val blockSize: Int
}
