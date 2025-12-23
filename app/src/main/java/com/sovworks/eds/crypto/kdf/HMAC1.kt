package com.sovworks.eds.crypto.kdf

import com.sovworks.eds.crypto.EncryptionEngineException
import java.security.DigestException
import java.security.MessageDigest
import java.util.Arrays

open class HMAC
    (key: ByteArray, protected val _md: MessageDigest, blockSize: Int) {
    val digestLength: Int
        get() = _md.digestLength

    @Throws(DigestException::class, EncryptionEngineException::class)
    fun calcHMAC(data: ByteArray, dataOffset: Int, dataLen: Int, out: ByteArray) {
        _md.reset()
        for (i in _key.indices) _block[i] = (_key[i].toInt() xor 0x36).toByte()
        Arrays.fill(_block, _key.size, _block.size, 0x36.toByte())

        _md.update(_block)
        _md.update(data, dataOffset, dataLen)
        _md.digest(_digest, 0, _digest.size)

        for (i in _key.indices) _block[i] = (_key[i].toInt() xor 0x5C).toByte()
        Arrays.fill(_block, _key.size, _block.size, 0x5C.toByte())
        _md.update(_block)
        _md.update(_digest)
        _md.digest(_digest, 0, _digest.size)
        System.arraycopy(_digest, 0, out, 0, _digest.size)
    }

    fun close() {
        _md.reset()
        Arrays.fill(_key, 0.toByte())
        Arrays.fill(_digest, 0.toByte())
        Arrays.fill(_block, 0.toByte())
    }

    protected val _digest: ByteArray = ByteArray(digestLength)
    protected val _block: ByteArray = ByteArray(blockSize)
    protected val _key: ByteArray =
        if (key.size > _block.size) _md.digest(key) else key.clone()
}