package com.sovworks.eds.fs.util

import java.io.IOException
import java.io.OutputStream
import kotlin.math.min

class DirectPipedOutputStream(private val _input: DirectPipedInputStream) : OutputStream() {
    @Synchronized
    @Throws(IOException::class)
    override fun write(oneByte: Int) {
        _oneByteBuffer[0] = oneByte.toByte()
        write(_oneByteBuffer, 0, 1)
    }

    @Throws(IOException::class)
    override fun write(buf: ByteArray, offset: Int, len: Int) {
        var offset = offset
        var len = len
        if (len <= 0) return
        while (len > 0) {
            var nb = 0
            val dest = _input.buffer
            try {
                if (dest == null) throw IOException("Input stream is closed")
                nb = min(len.toDouble(), _input.requestedBytes.toDouble()).toInt()
                System.arraycopy(buf, offset, dest, _input.offset, nb)
                offset += nb
                len -= nb
            } finally {
                _input.releaseBuffer(nb)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        _input.finWrite()
    }

    private val _oneByteBuffer = ByteArray(1)
}
