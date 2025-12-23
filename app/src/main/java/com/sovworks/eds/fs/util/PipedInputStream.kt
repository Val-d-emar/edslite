package com.sovworks.eds.fs.util

import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class PipedInputStream : InputStream() {
    @Synchronized
    @Throws(IOException::class)
    override fun read(): Int {
        val res = read(_oneByteBuffer, 0, 1)
        return if (res < 0) res else _oneByteBuffer[0].toInt()
    }

    @Throws(IOException::class)
    override fun read(buf: ByteArray, offset: Int, len: Int): Int {
        synchronized(_buffer) {
            var avail = availByteCountR
            if (avail >= 0) {
                avail = min(avail.toDouble(), len.toDouble()).toInt()
                System.arraycopy(_buffer, _rp, buf, offset, avail)
                incReadPos(avail)
                return avail
            }
        }
        return -1
    }

    @Throws(IOException::class)
    override fun close() {
        synchronized(_buffer) {
            _finRead = true
            (_buffer as Object).notify()
        }
    }

    fun write(buf: ByteArray, offset: Int, len: Int): Int {
        synchronized(_buffer) {
            var avail = availByteCountW
            if (avail >= 0) {
                avail = min(avail.toDouble(), len.toDouble()).toInt()
                System.arraycopy(buf, offset, _buffer, _wp, avail)
                incWritePos(avail)
                return avail
            }
        }
        return -1
    }

    fun notifyBuffer() {
        synchronized(_buffer) {
            (_buffer as Object).notify()
        }
    }

    fun finWrite() {
        synchronized(_buffer) {
            _finWrite = true
            (_buffer as Object).notify()
        }
    }

    private val _oneByteBuffer = ByteArray(1)
    private val _buffer = ByteArray(200 * 1024)
    private var _rp = 0
    private var _wp = 0
    private var _nbr = 0
    private var _finWrite = false
    private var _finRead = false

    private val availByteCountW: Int
        get() {
            while (_nbr == _buffer.size) try {
                (_buffer as Object).wait()
                if (_finWrite || _finRead) return -1
            } catch (e: InterruptedException) {
                return -1
            }
            val dif = _rp - _wp
            if (dif <= 0) return _buffer.size - _wp
            return dif
        }

    private val availByteCountR: Int
        get() {
            while (_nbr == 0 && !_finWrite) try {
                (_buffer as Object).wait()
                if (_finRead) return -1
            } catch (ignored: InterruptedException) {
            }
            if (_nbr == 0 && _finWrite) return -1
            val dif = _wp - _rp
            if (dif == 0) return min(_nbr.toDouble(), (_buffer.size - _rp).toDouble())
                .toInt()
            if (dif < 0) return _buffer.size - _rp
            return dif
        }

    private fun incWritePos(count: Int) {
        _wp += count
        if (_wp >= _buffer.size) _wp -= _buffer.size
        _nbr += count
        if (_nbr == _buffer.size) (_buffer as Object).notify()
    }

    private fun incReadPos(count: Int) {
        _rp += count
        if (_rp >= _buffer.size) _rp -= _buffer.size
        _nbr -= count
        (_buffer as Object).notify()
    }
}
