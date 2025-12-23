package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine

class BlockAndStreamCipher(
    private val _blockCipher: FileEncryptionEngine,
    private val _streamCipher: EncryptionEngine
) :
    FileEncryptionEngine {
    override val fileBlockSize: Int
        get() = _blockCipher.fileBlockSize

    override val encryptionBlockSize: Int
        get() = _blockCipher.encryptionBlockSize

    override fun setIncrementIV(`val`: Boolean) {
        _blockCipher.setIncrementIV(`val`)
    }

    @Throws(EncryptionEngineException::class)
    override fun init() {
        _blockCipher.init()
        _streamCipher.init()
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray?, offset: Int, len: Int) {
        if (len == _blockCipher.fileBlockSize) _blockCipher.decrypt(data, offset, len)
        else _streamCipher.decrypt(data, offset, len)
    }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray?, offset: Int, len: Int) {
        if (len == _blockCipher.fileBlockSize) _blockCipher.encrypt(data, offset, len)
        else _streamCipher.encrypt(data, offset, len)
    }

    override var iV: ByteArray?
        get() = _streamCipher.iV
        set(iv) {
            _blockCipher.iV = iv
            _streamCipher.iV = iv
        }

    override val iVSize: Int
        get() = _blockCipher.iVSize

    override var key: ByteArray?
        get() = _blockCipher.key
        set(key) {
            _blockCipher.key = key
            _streamCipher.key = key
        }

    override val keySize: Int
        get() = _blockCipher.keySize

    override fun close() {
        _blockCipher.close()
        _streamCipher.close()
    }

    override val cipherName: String
        get() = _blockCipher.cipherName

    override val cipherModeName: String
        get() = _blockCipher.cipherModeName
}
