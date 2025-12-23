package com.sovworks.eds.crypto.blockciphers

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.EncryptionEngineException

class AES @JvmOverloads constructor(private val _keySize: Int = 32) : BlockCipherNative {
    @Throws(EncryptionEngineException::class)
    override fun init(key: ByteArray) {
        require(key.size == keySize) {
            String.format(
                "Wrong key length. Required: %d. Provided: %d",
                _keySize,
                key.size
            )
        }
        _contextPtr = initContext(key)
        if (_contextPtr == 0L) throw EncryptionEngineException("AES context initialization failed")
    }

    @Throws(EncryptionEngineException::class)
    override fun encryptBlock(data: ByteArray) {
        if (_contextPtr == 0L) throw EncryptionEngineException("Cipher is closed")
        encrypt(data, _contextPtr)
    }

    @Throws(EncryptionEngineException::class)
    override fun decryptBlock(data: ByteArray) {
        if (_contextPtr == 0L) throw EncryptionEngineException("Cipher is closed")
        decrypt(data, _contextPtr)
    }

    override fun close() {
        if (_contextPtr != 0L) {
            closeContext(_contextPtr)
            _contextPtr = 0
        }
    }

    @Throws(EncryptionEngineException::class)
    override fun getNativeInterfacePointer(): Long {
        return _contextPtr
    }

    override fun getKeySize(): Int {
        return _keySize
    }

    override fun getBlockSize(): Int {
        return 16
    }

    private var _contextPtr: Long = 0

    private external fun initContext(key: ByteArray): Long
    private external fun closeContext(contextPtr: Long)
    private external fun encrypt(data: ByteArray, contextPtr: Long)
    private external fun decrypt(data: ByteArray, contextPtr: Long)

    companion object {
        init {
            System.loadLibrary("edsaes")
        }
    }
}

