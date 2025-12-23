package com.sovworks.eds.fs.util

import com.sovworks.eds.android.Logger
import com.sovworks.eds.fs.DataInput
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

abstract class TransInputStream(protected val _base: InputStream, protected val _bufferSize: Int) :
    InputStream(),
    DataInput {
    @Synchronized
    @Throws(IOException::class)
    override fun read(buf: ByteArray, offset: Int, count: Int): Int {
        log("read %d %d %d", buf.size, offset, count)
        val currentBuffer = currentBuffer
        if (_bytesLeft <= 0) return -1
        val avail = min(spaceInBuffer.toDouble(), _bytesLeft.toDouble()).toInt()
        val read = min(avail.toDouble(), count.toDouble()).toInt()
        System.arraycopy(currentBuffer, positionInBuffer, buf, offset, read)
        setCurrentBufferRead(read)
        // if(LOG_MORE)
        // Log.d("EDS ClusterChainIO",String.format("ClusterChainIO read: file=%s read %d
        // bytes",_path.getPathString(),avail));
        return read
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val buf = ByteArray(1)
        return if (read(buf, 0, 1) == 1) (buf[0].toInt() and 0xFF) else -1
    }

    @Throws(IOException::class)
    override fun close() {
        close(true)
    }

    @Throws(IOException::class)
    open fun close(closeBase: Boolean) {
        if (closeBase) _base.close()
    }

    protected var _buffer: ByteArray = ByteArray(_bufferSize)
    protected var _currentPosition: Long = 0
    protected var _bytesLeft: Int = 0

    @Throws(IOException::class)
    protected abstract fun transformBufferFromBase(
        baseBuffer: ByteArray?, offset: Int, count: Int, bufferPosition: Long, dstBuffer: ByteArray?
    ): Int

    @get:Throws(IOException::class)
    protected val currentBuffer: ByteArray
        get() {
            if (_bytesLeft <= 0) _bytesLeft =
                readFromBaseAndTransformBuffer(_buffer, 0, _bufferSize, bufferPosition)
            return _buffer
        }

    @Throws(IOException::class)
    protected open fun readFromBaseAndTransformBuffer(
        buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long
    ): Int {
        val br = readFromBase(buf, offset, count)
        return transformBufferFromBase(buf, offset, br, bufferPosition, buf)
    }

    @Throws(IOException::class)
    protected fun readFromBase(buf: ByteArray?, offset: Int, count: Int): Int {
        var t = 0
        while (t < count) {
            val n = _base.read(buf, offset + t, count - t)
            if (n < 0) return t
            t += n
        }
        return t
    }

    protected val bufferPosition: Long
        get() = _currentPosition - (_currentPosition % _bufferSize)

    protected fun log(msg: String, vararg params: Any?) {
        if (ENABLE_DEBUG_LOG && GlobalConfig.isDebug()) Logger.log(
            String.format(
                "TransInputStream: $msg", *params
            )
        )
    }

    protected fun setCurrentBufferRead(numBytes: Int) {
        _currentPosition += numBytes.toLong()
        _bytesLeft -= numBytes
    }

    protected val positionInBuffer: Int
        get() = (_currentPosition % _bufferSize).toInt()

    protected val spaceInBuffer: Int
        get() = _bufferSize - positionInBuffer

    companion object {
        var ENABLE_DEBUG_LOG: Boolean = false
    }
}
