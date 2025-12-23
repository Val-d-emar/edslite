package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.DataOutput
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.RandomStorageAccess
import java.io.IOException
import java.io.OutputStream

open class RandomAccessOutputStream(private val _io: RandomAccessIO) : OutputStream(), DataOutput,
    RandomStorageAccess {
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
    override fun write(data: Int) {
        _io.write(data)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        _io.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return _io.length()
    }
}
