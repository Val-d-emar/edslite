package com.sovworks.eds.crypto.modes

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine
import java.util.Arrays

abstract class CBC protected constructor(
    protected val _cf: CipherFactory,
    private val _fileBlockSize: Int = 512
) :
    FileEncryptionEngine {
    @Synchronized
    @Throws(EncryptionEngineException::class)
    override fun init() {
        closeCiphers()
        closeContext()

        _cbcContextPointer = initContext()
        if (_cbcContextPointer == 0L) throw EncryptionEngineException("CBC context initialization failed")

        addBlockCiphers(_cf)

        if (_key == null) throw EncryptionEngineException("Encryption key is not set")

        var keyOffset = 0
        for (p in _blockCiphers) {
            val ks = p.keySize
            val tmp = ByteArray(ks)
            try {
                System.arraycopy(_key, keyOffset, tmp, 0, ks)
                p.init(tmp)
                attachNativeCipher(_cbcContextPointer, p.nativeInterfacePointer)
            } finally {
                Arrays.fill(tmp, 0.toByte())
            }
            keyOffset += ks
        }
    }

    override fun getFileBlockSize(): Int {
        return _fileBlockSize
    }

    override fun getEncryptionBlockSize(): Int {
        return 16
    }

    override fun setIV(iv: ByteArray) {
        _iv = iv
    }

    override fun getIV(): ByteArray {
        return _iv
    }

    override fun getIVSize(): Int {
        return 16
    }

    override fun setKey(key: ByteArray?) {
        clearKey()
        _key = key?.copyOf(keySize)
    }

    override fun getKeySize(): Int {
        var res = 0
        for (c in _blockCiphers) res += c.keySize
        return res
    }

    override fun close() {
        closeCiphers()
        closeContext()
        clearAll()
    }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray, offset: Int, len: Int) {
        if (_cbcContextPointer == 0L) throw EncryptionEngineException("Engine is closed")
        if (len % encryptionBlockSize != 0 || (offset + len) > data.size) throw EncryptionEngineException(
            "Wrong buffer length"
        )
        if (encrypt(
                data,
                offset,
                len,
                _iv,
                _cbcContextPointer,
                _incrementIV
            ) != 0
        ) throw EncryptionEngineException("Failed encrypting data")
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray, offset: Int, len: Int) {
        if (_cbcContextPointer == 0L) throw EncryptionEngineException("Engine is closed")
        if (len % encryptionBlockSize != 0 || (offset + len) > data.size) throw EncryptionEngineException(
            "Wrong buffer length"
        )

        if (decrypt(
                data,
                offset,
                len,
                _iv,
                _cbcContextPointer,
                _incrementIV
            ) != 0
        ) throw EncryptionEngineException("Failed decrypting data")
    }

    override fun getKey(): ByteArray {
        return _key!!
    }

    override fun getCipherModeName(): String {
        return NAME
    }

    override fun setIncrementIV(`val`: Boolean) {
        _incrementIV = `val`
    }

    protected var _iv: ByteArray
    protected var _key: ByteArray?
    protected val _blockCiphers: ArrayList<BlockCipherNative> = ArrayList()

    protected fun closeCiphers() {
        for (p in _blockCiphers) p.close()
        _blockCiphers.clear()
    }

    protected fun closeContext() {
        if (_cbcContextPointer != 0L) {
            closeContext(_cbcContextPointer)
            _cbcContextPointer = 0
        }
    }

    private var _cbcContextPointer: Long = 0
    private var _incrementIV = true

    private external fun initContext(): Long
    private external fun closeContext(contextPointer: Long)
    private external fun attachNativeCipher(
        contextPointer: Long,
        nativeCipherInterfacePointer: Long
    )

    private external fun encrypt(
        data: ByteArray,
        offset: Int,
        len: Int,
        iv: ByteArray,
        contextPointer: Long,
        incrementIV: Boolean
    ): Int

    private external fun decrypt(
        data: ByteArray,
        offset: Int,
        len: Int,
        iv: ByteArray,
        contextPointer: Long,
        incrementIV: Boolean
    ): Int

    private fun addBlockCiphers(cipherFactory: CipherFactory) {
        for (i in 0..<cipherFactory.numberOfCiphers) _blockCiphers.add(cipherFactory.createCipher(i))
    }

    private fun clearAll() {
        clearKey()
    }

    private fun clearKey() {
        if (_key != null) {
            Arrays.fill(_key, 0.toByte())
            _key = null
        }
    }

    companion object {
        const val NAME: String = "cbc-plain"

        init {
            System.loadLibrary("edscbc")
        }
    }
}

