package com.sovworks.eds.crypto

class DummyEncryptionEngine : FileEncryptionEngine {
    override fun close() {
    }

    @Throws(EncryptionEngineException::class)
    override fun init() {
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray?, offset: Int, len: Int) {
    }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray?, offset: Int, len: Int) {
    }

    override val fileBlockSize: Int
        get() = 512

    override val encryptionBlockSize: Int
        get() = 0

    override var iV: ByteArray?
        get() = null
        set(iv) {
        }

    override val iVSize: Int
        get() = 0

    override fun setIncrementIV(`val`: Boolean) {
    }

    override val keySize: Int
        get() = 0

    override var key: ByteArray?
        get() = null
        set(key) {
        }

    override val cipherName: String
        get() = "plain"

    override val cipherModeName: String
        get() = "plain"
}
