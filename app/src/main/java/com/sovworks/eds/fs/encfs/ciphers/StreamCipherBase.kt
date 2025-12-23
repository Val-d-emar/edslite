package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.EncryptionEngineException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import kotlin.math.min

open class StreamCipherBase(base: EncryptionEngine) : CipherBase(base) {
    override var iV: ByteArray?
        get() = super.iV
        set(iv) {
            _iv = if (iv == null) 0 else ByteBuffer.wrap(iv)
                .order(ByteOrder.BIG_ENDIAN).getLong()
        }

    @Throws(EncryptionEngineException::class)
    override fun encrypt(data: ByteArray, offset: Int, len: Int) {
        shuffleBytes(data, offset, len)
        val iv = ByteArray(ivSize)
        ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN).putLong(_iv)
        super.setIV(iv)
        super.encrypt(data, offset, len)
        flipBytes(data, offset, len)
        shuffleBytes(data, offset, len)
        ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN).putLong(_iv + 1)
        super.setIV(iv)
        super.encrypt(data, offset, len)
    }

    @Throws(EncryptionEngineException::class)
    override fun decrypt(data: ByteArray, offset: Int, len: Int) {
        val iv = ByteArray(ivSize)
        ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN).putLong(_iv + 1)
        super.setIV(iv)
        super.decrypt(data, offset, len)
        unshuffleBytes(data, offset, len)
        flipBytes(data, offset, len)
        ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN).putLong(_iv)
        super.setIV(iv)
        super.decrypt(data, offset, len)
        unshuffleBytes(data, offset, len)
    }

    private var _iv: Long = 0

    companion object {
        private fun shuffleBytes(buf: ByteArray, offset: Int, count: Int) {
            for (i in 0..<count - 1) buf[i + offset + 1] =
                (buf[i + offset + 1].toInt() xor buf[i + offset].toInt()).toByte()
        }

        private fun unshuffleBytes(buf: ByteArray, offset: Int, count: Int) {
            for (i in count - 1 downTo 1) buf[i + offset] =
                (buf[i + offset].toInt() xor buf[i + offset - 1].toInt()).toByte()
        }

        private fun flipBytes(buf: ByteArray, offset: Int, count: Int) {
            var offset = offset
            val revBuf = ByteArray(64)

            var bytesLeft = count
            while (bytesLeft > 0) {
                val toFlip = min(revBuf.size.toDouble(), bytesLeft.toDouble()).toInt()

                for (i in 0..<toFlip) revBuf[i] = buf[toFlip + offset - (i + 1)]
                System.arraycopy(revBuf, 0, buf, offset, toFlip)
                bytesLeft -= toFlip
                offset += toFlip
            }
            Arrays.fill(revBuf, 0.toByte())
        }
    }
}
