package com.sovworks.eds.fs.util

import java.io.IOException
import java.io.OutputStream

class PipedOutputStream(private val _input: PipedInputStream) : OutputStream() {
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
            val nb = _input.write(buf, offset, len)
            if (nb == -1) throw IOException("Input stream is closed")
            offset += nb
            len -= nb
        }
    }

    override fun flush() {
        _input.notifyBuffer()
    }

    @Throws(IOException::class)
    override fun close() {
        _input.finWrite()
    }

    private val _oneByteBuffer = ByteArray(1)
}
