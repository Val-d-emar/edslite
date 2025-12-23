package com.sovworks.eds.crypto

import com.sovworks.eds.fs.encfs.macs.MACCalculator
import com.sovworks.eds.fs.util.TransOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays


class MACOutputStream(
    base: OutputStream?,
    private val _macCalc: MACCalculator,
    blockSize: Int,
    private val _macBytes: Int,
    private val _randBytes: Int
) : TransOutputStream(base, blockSize - _macBytes - _randBytes) {
    @Synchronized
    @Throws(IOException::class)
    override fun close(closeBase: Boolean) {
        try {
            super.close(closeBase)
        } finally {
            Arrays.fill(_buffer, 0.toByte())
            Arrays.fill(_transBuffer, 0.toByte())
        }
    }

    private val _transBuffer: ByteArray
    private val _overhead = _macBytes + _randBytes
    protected val _random: SecureRandom? =
        if (_randBytes > 0) SecureRandom() else null

    init {
        _transBuffer = ByteArray(_bufferSize + _overhead)
    }

    @Throws(IOException::class)
    override fun transformBufferAndWriteToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long
    ) {
        transformBufferToBase(buf, offset, count, bufferPosition, _transBuffer)
        writeToBase(_transBuffer, offset, count + _overhead)
    }

    @Throws(IOException::class)
    override fun transformBufferToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        baseBuffer: ByteArray
    ) {
        MACFile.Companion.makeMACCheckedBuffer(
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
}
