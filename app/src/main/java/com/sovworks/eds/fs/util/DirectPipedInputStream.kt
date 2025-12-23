package com.sovworks.eds.fs.util

import java.io.IOException
import java.io.InputStream

class DirectPipedInputStream : InputStream() {
    @Synchronized
    @Throws(IOException::class)
    override fun read(): Int {
        val res = read(_oneByteBuffer, 0, 1)
        return if (res < 0) res else _oneByteBuffer[0].toInt()
    }

    @Throws(IOException::class)
    override fun read(buf: ByteArray, offset: Int, len: Int): Int {
        synchronized(_sync) {
            if (_finWrite) return -1
            _actualBytes = -1
            _buffer = buf
            this.offset = offset
            requestedBytes = len
            (_sync as Object).notify()
        }
        while (true) {
            synchronized(_sync) {
                if (_actualBytes > 0) return _actualBytes
                if (_finWrite) return -1
                try {
                    (_sync as Object).wait()
                } catch (e: InterruptedException) {
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        synchronized(_sync) {
            _finRead = true
            (_sync as Object).notify()
        }
    }

    val buffer: ByteArray?
        get() {
            while (!_finRead) {
                synchronized(_sync) {
                    if (_buffer != null) break
                    try {
                        (_sync as Object).wait()
                    } catch (e: InterruptedException) {
                    }
                }
            }
            return _buffer
        }

    fun releaseBuffer(availableBytes: Int) {
        synchronized(_sync) {
            _buffer = null
            _actualBytes = availableBytes
            (_sync as Object).notify()
        }
    }

    fun finWrite() {
        synchronized(_buffer!!) {
            _finWrite = true
            (_buffer as Object).notify()
        }
    }

    private val _sync = Any()

    private var _buffer: ByteArray?
    private val _oneByteBuffer = ByteArray(1)
    var offset: Int = 0
        private set
    var requestedBytes: Int = 0
        private set
    private var _actualBytes = 0
    private var _finWrite = false
    private var _finRead = false
}
