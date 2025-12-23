package com.sovworks.eds.crypto

import com.sovworks.eds.crypto.modes.XTS
import com.sovworks.eds.fs.RandomAccessIO
import java.io.File
import java.io.IOException

class LocalEncryptedFileXTS(
    pathToFile: String,
    readOnly: Boolean,
    private val _dataOffset: Long,
    xts: XTS
) :
    RandomAccessIO {
    @Throws(IOException::class)
    override fun close() {
        if (_contextPointer == 0L) throw IOException("File is closed")
        close(_contextPointer)
        _contextPointer = 0
    }

    @Throws(IOException::class)
    override fun seek(position: Long) {
        if (_contextPointer == 0L) throw IOException("File is closed")

        seek(_contextPointer, _dataOffset + position)
    }

    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        if (_contextPointer == 0L) throw IOException("File is closed")

        return getPosition(_contextPointer) - _dataOffset
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return _file.length() - _dataOffset
    }

    @Throws(IOException::class)
    override fun read(): Int {
        return if (read(_oneByteBuf, 0, 1) != -1) _oneByteBuf[0].toInt() and 0xff else -1
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (off + len > b.size) throw IndexOutOfBoundsException()

        if (_contextPointer == 0L) throw IOException("File is closed")

        val res = read(_contextPointer, b, off, len)
        if (res < 0) throw IOException("Failed reading data")
        return res
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        _oneByteBuf[0] = (b and 0xFF).toByte()
        write(_oneByteBuf, 0, 1)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (off + len > b.size) throw IndexOutOfBoundsException()

        if (_contextPointer == 0L) throw IOException("File is closed")

        if (write(_contextPointer, b, off, len) != 0) throw IOException("Failed writing data")
    }

    @Throws(IOException::class)
    override fun flush() {
        if (_contextPointer == 0L) throw IOException("File is closed")
        flush(_contextPointer)
    }

    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        if (_contextPointer == 0L) throw IOException("File is closed")

        require(newLength >= 0) { "newLength < 0" }

        if (ftruncate(
                _contextPointer,
                newLength + _dataOffset
            ) != 0
        ) throw IOException("Failed truncating file")

        val filePointer = filePointer
        if (filePointer > newLength) seek(newLength)
    }

    private val _oneByteBuf = ByteArray(1)
    private var _contextPointer: Long
    private val _file = File(pathToFile)

    init {
        _contextPointer = initContext(pathToFile, readOnly, xts.xTSContextPointer)
        if (_contextPointer == 0L) throw IOException("Context initialization failed")

        seek(0)
    }

    companion object {
        init {
            System.loadLibrary("localxts")
        }

        private external fun flush(contextPointer: Long)
        private external fun close(contextPointer: Long)
        private external fun getPosition(contextPointer: Long): Long
        private external fun seek(contextPointer: Long, newPosition: Long)
        private external fun ftruncate(contextPointer: Long, newLength: Long): Int
        private external fun initContext(
            pathToFile: String,
            readOnly: Boolean,
            xtsContext: Long
        ): Long

        private external fun read(contextPointer: Long, buf: ByteArray, off: Int, len: Int): Int
        private external fun write(contextPointer: Long, buf: ByteArray, off: Int, len: Int): Int
    }
}
