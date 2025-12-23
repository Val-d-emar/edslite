package com.sovworks.eds.crypto.modes

import com.sovworks.eds.crypto.BlockCipher
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine
import java.util.Arrays

abstract class ECB protected constructor(protected val _cipher: BlockCipher) :
    FileEncryptionEngine {
    @Synchronized
    @Throws(EncryptionEngineException::class)
    override fun init() {
        closeCipher()
        if (_key == null) throw EncryptionEngineException("Encryption key is not set")
        _cipher.init(_key)
    }

    override fun getFileBlockSize(): Int {
        return _cipher.blockSize
    }

    override fun setIV(iv: ByteArray) {
    }

    override fun getIV(): ByteArray {
        return null
    }

    override fun setIncrementIV(`val`: Boolean) {
    }

    override fun getIVSize(): Int {
        return 0
    }

    override fun setKey(key: ByteArray?) {
        clearKey()
        _key = key?.copyOf(keySize)
    }

    override fun getKeySize(): Int {
        return _cipher.keySize
    }

    override fun close() {
        closeCipher()
        clearAll()
    }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray, offset: Int, len: Int) {
        if (len == 0) return
        val blockSize = _cipher.blockSize
        if (len % blockSize != 0 || (offset + len) > data.size) throw EncryptionEngineException("Wrong buffer length")
        val numBlocks = len / blockSize
        val block = ByteArray(blockSize)
        for (i in 0..<numBlocks) {
            val sp = offset + blockSize * i
            System.arraycopy(data, sp, block, 0, blockSize)
            _cipher.encryptBlock(block)
            System.arraycopy(block, 0, data, sp, blockSize)
        }
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray, offset: Int, len: Int) {
        if (len == 0) return
        val blockSize = _cipher.blockSize
        if (len % blockSize != 0 || (offset + len) > data.size) throw EncryptionEngineException("Wrong buffer length")
        val numBlocks = len / blockSize
        val block = ByteArray(blockSize)
        for (i in 0..<numBlocks) {
            val sp = offset + blockSize * i
            System.arraycopy(data, sp, block, 0, blockSize)
            _cipher.decryptBlock(block)
            System.arraycopy(block, 0, data, sp, blockSize)
        }
    }

    override fun getKey(): ByteArray {
        return _key!!
    }

    override fun getCipherModeName(): String {
        return "ecb"
    }

    override fun getEncryptionBlockSize(): Int {
        return _cipher.blockSize
    }

    protected var _key: ByteArray?


    protected fun closeCipher() {
        _cipher.close()
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
}

