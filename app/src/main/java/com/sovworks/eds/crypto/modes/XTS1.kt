package com.sovworks.eds.crypto.modes

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine
import java.nio.ByteBuffer
import java.util.Arrays

abstract class XTS protected constructor(protected val _cf: CipherFactory) : FileEncryptionEngine {
    @Synchronized
    @Throws(EncryptionEngineException::class)
    override fun init() {
        closeCiphers()
        closeContext()

        xTSContextPointer = initContext()
        if (xTSContextPointer == 0L) throw EncryptionEngineException("XTS context initialization failed")

        addBlockCiphers(_cf)

        if (_key == null) throw EncryptionEngineException("Encryption key is not set")

        var keyOffset = 0
        val eeKeySize = keySize / 2
        for (p in _blockCiphers) {
            val ks = p.cipherA.keySize
            val tmp = ByteArray(ks)
            try {
                System.arraycopy(_key, keyOffset, tmp, 0, ks)
                p.cipherA.init(tmp)
                System.arraycopy(_key, eeKeySize + keyOffset, tmp, 0, ks)
                p.cipherB.init(tmp)
                attachNativeCipher(
                    xTSContextPointer,
                    p.cipherA.nativeInterfacePointer,
                    p.cipherB.nativeInterfacePointer
                )
            } finally {
                Arrays.fill(tmp, 0.toByte())
            }
            keyOffset += ks
        }
    }

    override fun getFileBlockSize(): Int {
        return SECTOR_SIZE
    }

    override fun setIV(iv: ByteArray) {
        _iv = ByteBuffer.wrap(iv).getLong()
    }

    override fun getIV(): ByteArray {
        return ByteBuffer.allocate(ivSize).putLong(_iv).array()
    }

    override fun getIVSize(): Int {
        return 16
    }

    override fun setKey(key: ByteArray?) {
        clearKey()
        _key = key?.copyOf(keySize)
    }

    override fun setIncrementIV(`val`: Boolean) {
        _incrementIV = `val`
    }

    override fun getKeySize(): Int {
        var res = 0
        for (c in _blockCiphers) res += c.cipherA.keySize
        return 2 * res
    }

    override fun close() {
        closeCiphers()
        closeContext()
        clearAll()
    }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray, offset: Int, len: Int) {
        if (xTSContextPointer == 0L) throw EncryptionEngineException("Engine is closed")
        if (len % encryptionBlockSize != 0 || (offset + len) > data.size) throw EncryptionEngineException(
            "Wrong buffer length"
        )
        if (encrypt(
                data,
                offset,
                len,
                _iv,
                xTSContextPointer
            ) != 0
        ) throw EncryptionEngineException("Failed encrypting data")
        if (_incrementIV) _iv += (len / fileBlockSize).toLong()
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray, offset: Int, len: Int) {
        if (xTSContextPointer == 0L) throw EncryptionEngineException("Engine is closed")
        if (len % encryptionBlockSize != 0 || (offset + len) > data.size) throw EncryptionEngineException(
            "Wrong buffer length"
        )

        if (decrypt(
                data,
                offset,
                len,
                _iv,
                xTSContextPointer
            ) != 0
        ) throw EncryptionEngineException("Failed decrypting data")
        if (_incrementIV) _iv += (len / fileBlockSize).toLong()
    }

    override fun getKey(): ByteArray {
        return _key!!
    }

    override fun getCipherModeName(): String {
        return "xts-plain64"
    }

    override fun getEncryptionBlockSize(): Int {
        return 16
    }

    protected class CipherPair
        (var cipherA: BlockCipherNative, var cipherB: BlockCipherNative)

    protected var _iv: Long = 0
    protected var _key: ByteArray?
    protected val _blockCiphers: ArrayList<CipherPair> = ArrayList()
    protected var _incrementIV: Boolean = false

    protected fun closeCiphers() {
        for (p in _blockCiphers) {
            p.cipherA.close()
            p.cipherB.close()
        }
        _blockCiphers.clear()
    }

    protected fun closeContext() {
        if (xTSContextPointer != 0L) {
            closeContext(xTSContextPointer)
            xTSContextPointer = 0
        }
    }

    var xTSContextPointer: Long = 0
        private set

    private external fun initContext(): Long
    private external fun closeContext(contextPointer: Long)
    private external fun attachNativeCipher(
        contextPointer: Long,
        nativeCipherInterfacePointer: Long,
        secNativeCipherInterfacePointer: Long
    )

    private external fun encrypt(
        data: ByteArray,
        offset: Int,
        len: Int,
        startSectorAddress: Long,
        contextPointer: Long
    ): Int

    private external fun decrypt(
        data: ByteArray,
        offset: Int,
        len: Int,
        startSectorAddress: Long,
        contextPointer: Long
    ): Int

    private fun addBlockCiphers(cipherFactory: CipherFactory) {
        for (i in 0..<cipherFactory.numberOfCiphers) _blockCiphers.add(
            CipherPair(
                cipherFactory.createCipher(
                    i
                ), cipherFactory.createCipher(i)
            )
        )
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
        private const val SECTOR_SIZE = 512

        init {
            System.loadLibrary("edsxts")
        }
    }
}

