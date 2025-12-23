package com.sovworks.eds.crypto

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.encfs.macs.MACCalculator
import com.sovworks.eds.fs.util.TransRandomAccessIO
import java.io.IOException
import java.security.SecureRandom
import java.util.Arrays

class MACFile(
    base: RandomAccessIO,
    private val _macCalc: MACCalculator,
    blockSize: Int,
    private val _macBytes: Int,
    private val _randBytes: Int,
    private val _forceDecode: Boolean
) : TransRandomAccessIO(base, blockSize - _macBytes - _randBytes) {
    @Synchronized
    @Throws(IOException::class)
    override fun close(closeBase: Boolean) {
        try {
            super.close(closeBase)
        } finally {
            _macCalc.close()
            Arrays.fill(_transBuffer, 0.toByte())
        }
    }

    override fun calcBasePosition(position: Long): Long {
        val blockNum = (position + _bufferSize - 1) / _bufferSize
        return position + blockNum * _overhead
    }

    override fun calcVirtPosition(basePosition: Long): Long {
        return calcVirtPosition(basePosition, _bufferSize, _overhead)
    }

    @Throws(IOException::class)
    override fun readFromBaseAndTransformBuffer(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long
    ): Int {
        val bc = readFromBase(_transBuffer, offset, count + _overhead, bufferPosition)
        return if (bc > 0) transformBufferFromBase(_transBuffer, offset, bc, bufferPosition, buf)
        else 0
    }

    @Throws(IOException::class)
    override fun transformBufferFromBase(
        baseBuffer: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        dstBuffer: ByteArray
    ): Int {
        return getMACCheckedBuffer(
            baseBuffer,
            offset,
            count,
            bufferPosition,
            dstBuffer,
            _macCalc,
            _macBytes,
            _randBytes,
            _allowSkip,
            _forceDecode
        )
    }

    @Throws(IOException::class)
    override fun transformBufferAndWriteToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long
    ) {
        transformBufferToBase(buf, offset, count, bufferPosition, _transBuffer)
        writeToBase(_transBuffer, offset, count + _overhead, bufferPosition)
    }

    @Throws(IOException::class)
    override fun transformBufferToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        baseBuffer: ByteArray
    ) {
        makeMACCheckedBuffer(
            buf,
            offset,
            count,
            baseBuffer,
            _macCalc,
            _macBytes,
            _randBytes,
            _random!!
        )
    }

    private val _transBuffer: ByteArray
    private val _overhead = _macBytes + _randBytes
    private val _random =
        if (_randBytes > 0) SecureRandom() else null

    init {
        _transBuffer = ByteArray(_bufferSize + _overhead)
        try {
            _length = calcVirtPosition(base.length())
        } catch (ignored: IOException) {
        }
    }

    companion object {
        @JvmStatic
        fun calcVirtPosition(realPos: Long, blockSize: Int, overhead: Int): Long {
            val blockSizeWithOverhead = blockSize + overhead
            val blockNum = (realPos + blockSizeWithOverhead - 1) / blockSizeWithOverhead
            return realPos - blockNum * overhead
        }

        @Throws(IOException::class)
        fun getMACCheckedBuffer(
            baseBuffer: ByteArray,
            offset: Int,
            count: Int,
            bufferPosition: Long,
            dstBuffer: ByteArray,
            macCalc: MACCalculator,
            macBytes: Int,
            randBytes: Int,
            allowSkip: Boolean,
            forceDecode: Boolean
        ): Int {
            val resCount = count - macBytes - randBytes
            System.arraycopy(baseBuffer, offset + macBytes + randBytes, dstBuffer, offset, resCount)
            if (macBytes == 0 || (allowSkip && count > macBytes && EncryptedFile.Companion.isBufferEmpty(
                    baseBuffer,
                    offset,
                    count
                ))
            ) return resCount
            var fail: Byte = 0
            val mac = macCalc.calcChecksum(baseBuffer, offset + macBytes, count - macBytes)
            for (i in 0..<macBytes) fail =
                (fail.toInt() or (mac[i].toInt() xor baseBuffer[macBytes - i - 1].toInt())).toByte()
            if (fail.toInt() != 0) {
                val msg = "MAC comparison failure for the block at $bufferPosition"
                if (forceDecode) log(msg)
                else throw IOException(msg)
            }
            return resCount
        }

        @Throws(IOException::class)
        fun makeMACCheckedBuffer(
            buf: ByteArray,
            offset: Int,
            count: Int,
            baseBuffer: ByteArray,
            macCalc: MACCalculator,
            macBytes: Int,
            randBytes: Int,
            random: SecureRandom
        ) {
            System.arraycopy(buf, offset, baseBuffer, offset + macBytes + randBytes, count)
            if (randBytes > 0) {
                val rb = ByteArray(randBytes)
                random.nextBytes(rb)
                System.arraycopy(rb, 0, baseBuffer, offset + macBytes, randBytes)
            }
            if (macBytes > 0) {
                val mac = macCalc.calcChecksum(baseBuffer, offset + macBytes, count + randBytes)
                for (i in 0..<macBytes) baseBuffer[offset + i] = mac[macBytes - i - 1]
            }
        }
    }
}
