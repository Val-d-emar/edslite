package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException

open class RandomAccessIOWrapper(val base: RandomAccessIO?) : RandomAccessIO {
    @Throws(IOException::class)
    override fun close() {
        base!!.close()
    }

    @Throws(IOException::class)
    override fun seek(position: Long) {
        base!!.seek(position)
    }

    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        return base!!.filePointer
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return base!!.length()
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return base!!.read()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return base!!.read(b, off, len)
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        base!!.write(b)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        base!!.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        base!!.flush()
    }

    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        base!!.setLength(newLength)
    }
}
