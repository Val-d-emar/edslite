package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.kdf.HMAC
import com.sovworks.eds.crypto.kdf.HMACSHA1
import java.security.NoSuchAlgorithmException
import java.util.Arrays

open class CipherBase(private val _base: EncryptionEngine) : EncryptionEngine {
    @Throws(EncryptionEngineException::class)
    override fun init() {
        clearHMAC()
        try {
            _hmac = HMACSHA1(_keyPart!!)
        } catch (e: NoSuchAlgorithmException) {
            throw EncryptionEngineException("Failed initializing cipher", e)
        }
        _base.init()
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray?, offset: Int, len: Int) {
        _base.decrypt(data, offset, len)
    }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray?, offset: Int, len: Int) {
        _base.encrypt(data, offset, len)
    }

    override var iV: ByteArray
        get() = iVFromBuf
        set(iv) {
            val buf = _ivPart!!.copyOf(_ivPart!!.size + 8)
            for (i in 0..7) buf[_ivPart!!.size + i] = iv[7 - i]
            val hmac = ByteArray(_hmac!!.digestLength)
            try {
                _hmac!!.calcHMAC(buf, 0, buf.size, hmac)
                _base.iV = Arrays.copyOfRange(hmac, 0, _base.iVSize)
            } catch (e: Exception) {
                log(e)
            } finally {
                Arrays.fill(buf, 0.toByte())
            }
        }

    override val iVSize: Int
        get() = _base.iVSize

    override var key: ByteArray?
        get() = _key
        set(key) {
            clearKey()
            if (key != null) {
                _key = key.copyOf(keySize)
                _keyPart = keyFromBuf
                _ivPart = iVFromBuf
                _base.key = _keyPart
            }
        }

    override val keySize: Int
        get() = _base.keySize + iVSize

    override fun close() {
        clearAll()
        _base.close()
    }

    override val cipherName: String
        get() = _base.cipherName

    override val cipherModeName: String
        get() = _base.cipherModeName

    protected open val base: EncryptionEngine?
        get() = _base

    private var _key: ByteArray?
    private var _keyPart: ByteArray?
    private var _ivPart: ByteArray?
    private var _hmac: HMAC? = null

    private fun clearAll() {
        clearKey()
        clearHMAC()
    }

    private fun clearHMAC() {
        if (_hmac != null) {
            _hmac!!.close()
            _hmac = null
        }
    }

    private fun clearKey() {
        if (_key != null) {
            Arrays.fill(_key, 0.toByte())
            Arrays.fill(_ivPart, 0.toByte())
            Arrays.fill(_keyPart, 0.toByte())
            _keyPart = null
            _ivPart = _keyPart
            _key = _ivPart
        }
    }

    private val iVFromBuf: ByteArray
        get() = getIVFromBuf(_key!!, _base.keySize)

    private val keyFromBuf: ByteArray
        get() = getKeyFromBuf(_key!!, _base.keySize)

    companion object {
        private fun getIVFromBuf(buf: ByteArray, keySize: Int): ByteArray {
            val res = ByteArray(buf.size - keySize)
            System.arraycopy(buf, keySize, res, 0, res.size)
            return res
        }

        @JvmStatic
        fun getKeyFromBuf(buf: ByteArray, keySize: Int): ByteArray {
            val res = ByteArray(keySize)
            System.arraycopy(buf, 0, res, 0, res.size)
            return res
        }
    }
}
