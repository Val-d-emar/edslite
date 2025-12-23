package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.DataInput
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.RandomStorageAccess
import java.io.IOException
import java.io.InputStream

open class RandomAccessInputStream(private val _io: RandomAccessIO) : InputStream(), DataInput,
    RandomStorageAccess {
    @Throws(IOException::class)
    override fun read(): Int {
        return _io.read()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return _io.read(b, off, len)
    }

    @Throws(IOException::class)
    override fun close() {
        _io.close()
    }

    @Throws(IOException::class)
    override fun seek(position: Long) {
        _io.seek(position)
    }

    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        return _io.filePointer
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return _io.length()
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        var n = n
        val pos = _io.filePointer
        val left = _io.length() - pos
        if (n > left) n = left
        seek(pos + n)
        return n
    }
}
