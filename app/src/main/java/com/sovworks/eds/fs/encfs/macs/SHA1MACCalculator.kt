package com.sovworks.eds.fs.encfs.macs

import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.kdf.HMACSHA1
import com.sovworks.eds.fs.encfs.ciphers.CipherBase.Companion.getKeyFromBuf
import java.security.DigestException
import java.security.NoSuchAlgorithmException
import java.util.Arrays

class SHA1MACCalculator(private val _keySize: Int) : MACCalculator() {
    override fun init(key: ByteArray) {
        val k = getKeyFromBuf(key, _keySize)
        try {
            _hmac = HMACSHA1(k)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } finally {
            Arrays.fill(k, 0.toByte())
        }
    }

    override fun close() {
        _hmac!!.close()
    }

    override fun calcChecksum(buf: ByteArray, offset: Int, count: Int): ByteArray {
        val data: ByteArray
        if (isChainedIVEnabled) {
            val iv = chainedIV
            data = ByteArray(count + 8)
            System.arraycopy(buf, offset, data, 0, count)
            for (i in 0..7) data[count + i] = iv!![7 - i]
        } else data = Arrays.copyOfRange(buf, offset, offset + count)
        try {
            val mac = ByteArray(_hmac!!.digestLength)
            _hmac!!.calcHMAC(data, 0, data.size, mac)
            val cut = ByteArray(8)
            for (i in 0..<mac.size - 1) cut[i % cut.size] =
                (cut[i % cut.size].toInt() xor mac[i].toInt()).toByte()
            if (isChainedIVEnabled) chainedIV = cut.clone()
            return cut
        } catch (e: DigestException) {
            throw RuntimeException(e)
        } catch (e: EncryptionEngineException) {
            throw RuntimeException(e)
        } finally {
            Arrays.fill(data, 0.toByte())
        }
    }

    private var _hmac: HMACSHA1? = null
}
