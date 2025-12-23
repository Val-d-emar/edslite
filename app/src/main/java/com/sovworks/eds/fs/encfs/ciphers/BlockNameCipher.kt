package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.fs.encfs.B64
import com.sovworks.eds.fs.encfs.NameCodec
import com.sovworks.eds.fs.encfs.macs.MACCalculator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

class BlockNameCipher(
    private val _cipher: FileEncryptionEngine,
    private val _hmac: MACCalculator,
    private val _caseSensitive: Boolean
) :
    NameCodec {
    override fun encodeName(plaintextName: String): String {
        val plain = plaintextName.toByteArray()
        val len = plain.size //calcLengthIncBlocs(plain.length);
        val blockSize = _cipher.encryptionBlockSize
        var padding = blockSize - len % blockSize
        if (padding == 0) padding = blockSize
        val res = ByteArray(calcEncodedLength(len + padding + 2))
        System.arraycopy(plain, 0, res, 2, len)
        Arrays.fill(res, len + 2, len + padding + 2, padding.toByte())
        _hmac.chainedIV = _iv
        val mac = _hmac.calc16(res, 2, len + padding)
        _chainedIV = _hmac.chainedIV
        ByteBuffer.wrap(res).order(ByteOrder.BIG_ENDIAN).putShort(mac)
        val iv = ByteArray(_cipher.iVSize)
        ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN).putLong(mac.toLong() and 0xFFFFL)
        if (_iv != null) for (i in _iv.indices) iv[i] = (iv[i].toInt() xor _iv[i]
            .toInt()).toByte()
        _cipher.iV = iv
        try {
            _cipher.encrypt(res, 2, len + padding)
        } catch (e: EncryptionEngineException) {
            throw RuntimeException("Encryption failed", e)
        }
        if (_caseSensitive) {
            B64.changeBase2Inline(res, 0, len + padding + 2, 8, 5, true)
            return B64.B32ToString(res, 0, res.size)
        } else {
            B64.changeBase2Inline(res, 0, len + padding + 2, 8, 6, true)
            return B64.B64ToString(res, 0, res.size)
        }
    }

    override fun decodeName(encodedName: String): String {
        val buf: ByteArray
        if (_caseSensitive) {
            val tmp = B64.StringToB32(encodedName)
            buf = ByteArray(B64.B32ToB256Bytes(tmp.size))
            B64.changeBase2Inline(tmp, 0, tmp.size, 5, 8, false, 0, 0, buf, 0)
        } else {
            val tmp = B64.StringToB64(encodedName)
            buf = ByteArray(B64.B64ToB256Bytes(tmp.size))
            B64.changeBase2Inline(tmp, 0, tmp.size, 6, 8, false, 0, 0, buf, 0)
        }
        require(!(buf.size - 2 < _cipher.encryptionBlockSize)) { "Encoded name is too short: $encodedName" }
        val mac = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).getShort()
        val iv = ByteArray(_cipher.iVSize)
        ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN).putLong(mac.toLong() and 0xFFFFL)
        if (_iv != null) for (i in _iv.indices) iv[i] = (iv[i].toInt() xor _iv[i]
            .toInt()).toByte()
        _cipher.iV = iv
        try {
            _cipher.decrypt(buf, 2, buf.size - 2)
        } catch (e: EncryptionEngineException) {
            throw RuntimeException("Encryption failed", e)
        }
        try {
            val padding = buf[buf.size - 1].toInt()
            val finalSize = buf.size - padding - 2
            require(!(padding > _cipher.encryptionBlockSize || finalSize < 0)) { "Failed decoding name. Wrong padding. Name=$encodedName" }

            _hmac.chainedIV = _iv
            val mac2 = _hmac.calc16(buf, 2, buf.size - 2)
            _chainedIV = _hmac.chainedIV
            require(mac == mac2) { "Failed decoding name. Checksum mismatch. Name=$encodedName" }
            return String(buf, 2, finalSize)
        } finally {
            Arrays.fill(buf, 0.toByte())
        }
    }

    override fun init(key: ByteArray) {
        _cipher.key = key
        try {
            _cipher.init()
        } catch (e: EncryptionEngineException) {
            throw RuntimeException("Failed initializing cipher", e)
        }
        _hmac.init(key)
    }

    override fun close() {
        _cipher.close()
        _hmac.close()
    }

    override fun setIV(iv: ByteArray) {
        _iv = iv
    }

    override fun getChainedIV(plaintextName: String): ByteArray {
        if (_chainedIV == null) _chainedIV = calcChainedIV(plaintextName)
        return _chainedIV
    }

    override fun getIV(): ByteArray {
        return _iv
    }

    override fun getIVSize(): Int {
        return 8
    }

    private var _iv: ByteArray
    private var _chainedIV: ByteArray

    /*private int calcLengthIncBlocs(int plainLength)
    {
        int bs = _cipher.getEncryptionBlockSize();
        return ((plainLength + bs)/bs) * bs;
       // int len = ((plainLength + bs)/bs) * bs + 2; //num blocks + 2 checksum bytes
       // return calcEncodedLength(len);
    }*/
    private fun calcEncodedLength(plainLength: Int): Int {
        return if (_caseSensitive) B64.B256ToB32Bytes(plainLength) else B64.B256ToB64Bytes(
            plainLength
        )
    }

    private fun calcChainedIV(plainTextName: String): ByteArray {
        val plain = plainTextName.toByteArray()
        val len = plain.size //calcLengthIncBlocs(plain.length);
        val blockSize = _cipher.encryptionBlockSize
        var padding = blockSize - len % blockSize
        if (padding == 0) padding = blockSize
        val res = ByteArray(calcEncodedLength(len + padding + 2))
        System.arraycopy(plain, 0, res, 2, len)
        Arrays.fill(res, len + 2, len + padding + 2, padding.toByte())
        _hmac.chainedIV = _iv
        _hmac.calc64(res, 2, len + padding)
        return _hmac.chainedIV
    }
}
