package com.sovworks.eds.fs.util

import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.util.Arrays
import kotlin.math.min

open class TransRandomAccessIO(base: RandomAccessIO?, bufferSize: Int) :
    BufferedRandomAccessIO(base, bufferSize) {
    @Synchronized
    @Throws(IOException::class)
    override fun close(closeBase: Boolean) {
        try {
            writeCurrentBuffer()
            super.close(closeBase)
        } finally {
            Arrays.fill(_buffer, 0.toByte())
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(buf: ByteArray, offset: Int, count: Int) {
        if (!_allowSkip && _currentPosition > _length) fillFreeSpace()
        super.write(buf, offset, count)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun setLength(newLength: Long) {
        if (!_allowSkip && newLength > _length - 1) {
            seek(newLength - 1)
            write(0)
        } else {
            _length = newLength
            super.setLength(calcBasePosition(newLength))
        }
    }

    fun setAllowSkip(`val`: Boolean) {
        _allowSkip = `val`
    }

    protected var _buffer: ByteArray = ByteArray(_bufferSize)
    protected var _allowSkip: Boolean = false
    protected var _isBufferLoaded: Boolean = false
    protected var _isBufferChanged: Boolean = false

    override fun setCurrentBufferWritten(numBytes: Int) {
        super.setCurrentBufferWritten(numBytes)
        _isBufferChanged = true
    }

    @get:Throws(IOException::class)
    override val currentBuffer: ByteArray
        get() {
            if (_isBufferLoaded) {
                val dif = _currentPosition - bufferPosition
                if (dif < 0 || dif >= _bufferSize) {
                    writeCurrentBuffer()
                    bufferPosition = calcBufferPosition()
                    _isBufferLoaded = false
                }
            } else bufferPosition = calcBufferPosition()
            loadCurrentBuffer()
            return _buffer
        }

    @Throws(IOException::class)
    override fun writeCurrentBuffer() {
        if (!_isBufferChanged) return
        val bp = bufferPosition
        val count = min((_length - bp).toInt().toDouble(), _bufferSize.toDouble()).toInt()
        transformBufferAndWriteToBase(_buffer, 0, count, bp)
        _isBufferChanged = false
    }

    @Throws(IOException::class)
    protected open fun loadCurrentBuffer() {
        if (_isBufferLoaded) return
        val bp = bufferPosition
        val space = min((_length - bp).toDouble(), _bufferSize.toDouble()).toInt()
        if (space > 0) {
            val act = readFromBaseAndTransformBuffer(_buffer, 0, space, bp)
            Arrays.fill(_buffer, act, _bufferSize, 0.toByte())
        }
        _isBufferChanged = false
        _isBufferLoaded = true
    }

    @Throws(IOException::class)
    protected open fun readFromBaseAndTransformBuffer(
        buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long
    ): Int {
        val bc = readFromBase(buf, offset, count, bufferPosition)
        return transformBufferFromBase(buf, offset, bc, bufferPosition, buf)
    }

    @Throws(IOException::class)
    protected open fun transformBufferAndWriteToBase(
        buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long
    ) {
        transformBufferToBase(buf, offset, count, bufferPosition, buf)
        writeToBase(buf, offset, count, bufferPosition)
    }

    @Throws(IOException::class)
    protected open fun transformBufferToBase(
        buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long, baseBuffer: ByteArray?
    ) {
    }

    @Throws(IOException::class)
    protected open fun transformBufferFromBase(
        baseBuffer: ByteArray?, offset: Int, count: Int, bufferPosition: Long, dstBuffer: ByteArray?
    ): Int {
        return count
    }

    @Throws(IOException::class)
    protected fun writeToBase(buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long) {
        base.seek(calcBasePosition(bufferPosition))
        base.write(buf, offset, count)
    }

    @Throws(IOException::class)
    protected fun readFromBase(
        buf: ByteArray?,
        offset: Int,
        count: Int,
        bufferPosition: Long
    ): Int {
        base.seek(calcBasePosition(bufferPosition))
        return readFullyEncrypted(buf, offset, count)
        // if(bc != count)
        //	throw new IOException("Got " + bc + " bytes instead of " + count);
    }

    protected open fun calcBasePosition(position: Long): Long {
        return position
    }

    protected open fun calcVirtPosition(basePosition: Long): Long {
        return basePosition
    }

    @Throws(IOException::class)
    protected fun readFullyEncrypted(buf: ByteArray?, off: Int, len: Int): Int {
        var t = 0
        while (t < len) {
            val n = base.read(buf, off + t, len - t)
            if (n < 0) return t
            t += n
        }
        return t
    }

    @Throws(IOException::class)
    protected fun fillFreeSpace() {
        var pos = _length
        val rem = (_length % _bufferSize).toInt()
        if (rem != 0) pos += (_bufferSize - rem).toLong()
        val tbuf = ByteArray(_bufferSize)
        // this method should be called when the current buffer is modified so
        // the ending position would be the start position of the current buffer
        val bp = bufferPosition
        while (pos < bp) {
            transformBufferAndWriteToBase(tbuf, 0, _bufferSize, pos)
            pos += _bufferSize.toLong()
        }
    }

    override var bufferPosition: Long = 0
        private set

    private fun calcBufferPosition(): Long {
        return _currentPosition - (_currentPosition % _bufferSize)
    }
}
