package com.sovworks.eds.crypto

import com.sovworks.eds.fs.encfs.macs.MACCalculator
import com.sovworks.eds.fs.util.TransInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays


class MACInputStream(
    base: InputStream?,
    private val _macCalc: MACCalculator,
    blockSize: Int,
    private val _macBytes: Int,
    private val _randBytes: Int,
    private val _forceDecode: Boolean
) : TransInputStream(base, blockSize - _macBytes - _randBytes) {
    @Synchronized
    @Throws(IOException::class)
    override fun close(closeBase: Boolean) {
        Arrays.fill(_buffer, 0.toByte())
        Arrays.fill(_transBuffer, 0.toByte())
        super.close(closeBase)
    }

    fun setAllowEmptyParts(`val`: Boolean) {
        _allowEmptyParts = `val`
    }

    private val _transBuffer: ByteArray
    private val _overhead = _macBytes + _randBytes
    protected var _allowEmptyParts: Boolean = true


    init {
        _transBuffer = ByteArray(_bufferSize + _overhead)
    }

    @Throws(IOException::class)
    override fun readFromBaseAndTransformBuffer(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long
    ): Int {
        val br = readFromBase(_transBuffer, offset, count + _overhead)
        return if (br > 0) transformBufferFromBase(_transBuffer, offset, br, bufferPosition, buf)
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
        return MACFile.Companion.getMACCheckedBuffer(
            baseBuffer,
            offset,
            count,
            bufferPosition,
            dstBuffer,
            _macCalc,
            _macBytes,
            _randBytes,
            _allowEmptyParts,
            _forceDecode
        )
    }
}
