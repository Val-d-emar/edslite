package com.sovworks.eds.fs.exfat

import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException

internal class ExFatRAIO(
    private val _exfat: ExFat,
    private var _fileHandle: Long,
    private var _position: Long,
    private val _mode: AccessMode
) :
    RandomAccessIO {
    @Throws(IOException::class)
    override fun seek(position: Long) {
        synchronized(_exfat._sync) {
            _position = position
        }
    }

    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        return _position
    }

    @Throws(IOException::class)
    override fun length(): Long {
        synchronized(_exfat._sync) {
            val res = _exfat.getSize(_fileHandle)
            if (res < 0) throw IOException("Failed getting node size.")
            return res
        }
    }

    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        if (_mode == Read) throw IOException("Read-only mode")
        synchronized(_exfat._sync) {
            val res = _exfat.truncate(_fileHandle, newLength)
            if (res != 0) throw IOException("Truncate failed. Error code = $res")
            if (_position > newLength) _position = newLength
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        _obBuf[0] = (b and 0xFF).toByte()
        write(_obBuf, 0, 1)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (_mode == Read) throw IOException("Read-only mode")
        synchronized(_exfat._sync) {
            val res = _exfat.write(_fileHandle, b, off, len, _position)
            if (res < 0) throw IOException("Write failed. Result = $res")
            _position += res.toLong()
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        synchronized(_exfat._sync) {
            val res = _exfat.flush(_fileHandle)
            if (res != 0) throw IOException("Flush failed. Error code = $res")
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun read(): Int {
        val cnt = read(_obBuf, 0, 1)
        return if (cnt <= 0) -1 else (_obBuf[0].toInt() and 0xFF)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, count: Int): Int {
        synchronized(_exfat._sync) {
            val res = _exfat.read(_fileHandle, b, off, count, _position)
            if (res < 0) throw IOException("Read failed. Result = $res")
            if (res == 0 && count > 0) return -1
            _position += res.toLong()
            return res
        }
    }

    @Throws(IOException::class)
    override fun close() {
        synchronized(_exfat._sync) {
            if (_fileHandle != 0L) {
                val res = _exfat.closeFile(_fileHandle)
                if (res != 0) throw IOException("Close failed. Error code = $res")
                _fileHandle = 0
            }
        }
    }

    private val _obBuf = ByteArray(1)
}
