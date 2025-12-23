package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException

open class FDRandomAccessIO : RandomAccessIO {
    constructor(fd: Int) {
        fD = fd
    }

    @Throws(IOException::class)
    override fun close() {
        if (fD >= 0) {
            close(fD)
            fD = -1
        }
    }

    @Throws(IOException::class)
    override fun seek(position: Long) {
        if (fD < 0) throw IOException("File is closed")

        seek(fD, position)
    }

    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        if (fD < 0) throw IOException("File is closed")

        return getPosition(fD)
    }

    @Throws(IOException::class)
    override fun length(): Long {
        if (fD < 0) throw IOException("File is closed")

        return getSize(fD)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun read(): Int {
        val buf = ByteArray(1)
        return if (read(buf, 0, 1) != -1) buf[0].toInt() and 0xff else -1
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off + len > b.size) throw IndexOutOfBoundsException()

        if (fD < 0) throw IOException("File is closed")

        val res = read(fD, b, off, len)
        if (res < 0) throw IOException("Failed reading data")
        if (res == 0) return -1
        return res
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (off + len > b.size) throw IndexOutOfBoundsException()

        if (fD < 0) throw IOException("File is closed")

        if (write(fD, b, off, len) != 0) throw IOException("Failed writing data")
    }

    @Throws(IOException::class)
    override fun flush() {
        if (fD < 0) throw IOException("File is closed")
        flush(fD)
    }

    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        if (fD < 0) throw IOException("File is closed")

        require(newLength >= 0) { "newLength < 0" }

        if (ftruncate(fD, newLength) != 0) throw IOException("Failed truncating file")

        val filePointer = filePointer
        if (filePointer > newLength) seek(newLength)
    }

    protected constructor()

    var fD: Int = -1
        protected set

    companion object {
        init {
            System.loadLibrary("fdraio")
        }

        private external fun flush(fd: Int)

        private external fun getSize(fd: Int): Long

        private external fun close(fd: Int)

        private external fun getPosition(fd: Int): Long

        private external fun seek(fd: Int, newPosition: Long)

        private external fun ftruncate(fd: Int, newLength: Long): Int

        private external fun read(fd: Int, buf: ByteArray, off: Int, len: Int): Int

        private external fun write(fd: Int, buf: ByteArray, off: Int, len: Int): Int
    }
}
