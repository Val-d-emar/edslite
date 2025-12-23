package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.io.OutputStream

class OutputStreamBasedRandomAccessIO(private val _base: OutputStream) : RandomAccessIO {
    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
    }

    @Throws(IOException::class)
    override fun close() {
        _base.close()
    }

    @Throws(IOException::class)
    override fun read(): Int {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        _base.write(b)
        _curPos++
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        _base.write(b, off, len)
        _curPos += len.toLong()
    }

    @Throws(IOException::class)
    override fun flush() {
        _base.flush()
    }

    @Throws(IOException::class)
    override fun seek(position: Long) {
        if (_curPos != position) throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        return _curPos
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return _curPos
    }

    private var _curPos: Long = 0
}
