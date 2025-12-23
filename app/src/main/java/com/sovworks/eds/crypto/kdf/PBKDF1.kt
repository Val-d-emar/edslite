package com.sovworks.eds.crypto.kdf

import com.sovworks.eds.android.helpers.ProgressReporter
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.hash.RIPEMD160
import com.sovworks.eds.crypto.hash.Whirlpool
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.DigestException
import java.security.MessageDigest
import java.util.Arrays
import java.util.concurrent.CancellationException

abstract class PBKDF {
    @Throws(EncryptionEngineException::class, DigestException::class)
    fun deriveKey(srcKey: ByteArray, salt: ByteArray, keyLen: Int): ByteArray {
        return deriveKey(srcKey, salt, defaultIterationsCount, keyLen)
    }

    @Throws(EncryptionEngineException::class, DigestException::class)
    fun deriveKey(srcKey: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val hmac = initHMAC(srcKey)
        try {
            val digestLength = hmac.digestLength
            val u = ByteArray(digestLength)
            val res = ByteArray(keyLen)
            val l =
                if (keyLen % digestLength != 0) 1 + keyLen / digestLength else keyLen / digestLength
            val r = keyLen - (l - 1) * digestLength
            _finishedIterations = 0
            _totalIterations = iterations * l
            var b = 1
            while (b < l) {
                deriveKey(hmac, srcKey, salt, iterations, u, b)
                System.arraycopy(u, 0, res, (b - 1) * digestLength, digestLength)
                b++
            }

            deriveKey(hmac, srcKey, salt, iterations, u, b)
            System.arraycopy(u, 0, res, (b - 1) * digestLength, r)
            Arrays.fill(u, 0.toByte())
            return res
        } finally {
            hmac.close()
        }
    }

    fun setProgressReporter(r: ProgressReporter?) {
        _progressReporter = r
    }

    protected var _progressReporter: ProgressReporter? = null

    private var _finishedIterations = 0
    private var _totalIterations = 0

    @Throws(DigestException::class, EncryptionEngineException::class)
    protected fun calcHMAC(hmac: HMAC, key: ByteArray?, message: ByteArray, result: ByteArray) {
        hmac.calcHMAC(message, 0, message.size, result)
    }

    @Throws(DigestException::class, EncryptionEngineException::class)
    protected fun deriveKey(
        hmac: HMAC,
        key: ByteArray?,
        salt: ByteArray,
        iterations: Int,
        u: ByteArray,
        block: Int
    ) {
        val digestLength = hmac.digestLength
        val bb = ByteBuffer.allocate(COUNTER_LENGTH)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putInt(block)

        val init = ByteArray(salt.size + COUNTER_LENGTH)
        System.arraycopy(salt, 0, init, 0, salt.size)
        System.arraycopy(bb.array(), 0, init, salt.size, COUNTER_LENGTH)

        val j = ByteArray(digestLength)
        calcHMAC(hmac, key, init, j)
        System.arraycopy(j, 0, u, 0, digestLength)

        var prevPrc = -1
        val k = ByteArray(digestLength)
        for (c in 1..<iterations) {
            calcHMAC(hmac, key, j, k)
            for (i in 0..<digestLength) {
                u[i] = (u[i].toInt() xor k[i].toInt()).toByte()
                j[i] = k[i]
            }
            if (_progressReporter != null) {
                val prc = (((_finishedIterations++).toFloat() * 100) / _totalIterations).toInt()
                if (prc != prevPrc) {
                    prevPrc = prc
                    _progressReporter!!.setProgress(prc)
                }
                if (_progressReporter!!.isCancelled) throw CancellationException()
            }
        }
        Arrays.fill(j, 0.toByte())
        Arrays.fill(k, 0.toByte())
    }

    @Throws(EncryptionEngineException::class)
    protected abstract fun initHMAC(srcKey: ByteArray): HMAC

    protected open val defaultIterationsCount: Int
        get() = 1000

    companion object {
        val availablePBKDFS: Iterable<PBKDF>
            get() = Arrays.asList(
                HMACSHA512KDF(),
                HMACRIPEMD160KDF(),
                HMACWhirlpoolKDF()
            )

        private const val COUNTER_LENGTH = 4
    }
}

internal class HMACSHA512(key: ByteArray) :
    HMAC(key, MessageDigest.getInstance("SHA-512"), SHA512_BLOCK_SIZE) {
    companion object {
        private const val SHA512_BLOCK_SIZE = 128
    }
}

internal class HMACRIPEMD160(key: ByteArray) :
    HMAC(key, RIPEMD160(), RIPEMD160_BLOCK_SIZE) {
    companion object {
        private const val RIPEMD160_BLOCK_SIZE = 64
    }
}

internal class HMACWhirlpool(key: ByteArray) : HMAC(key, Whirlpool(), WHIRLPOOL_BLOCK_SIZE) {
    companion object {
        private const val WHIRLPOOL_BLOCK_SIZE = 64
    }
}