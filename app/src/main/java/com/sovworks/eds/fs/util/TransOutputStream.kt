package com.sovworks.eds.fs.util

import com.sovworks.eds.android.Logger
import com.sovworks.eds.fs.DataOutput
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException
import java.io.OutputStream
import kotlin.math.min

abstract class TransOutputStream(
    protected val _base: OutputStream,
    protected val _bufferSize: Int
) : OutputStream(),
    DataOutput {
    @Throws(IOException::class)
    override fun write(b: Int) {
        val buf = byteArrayOf(b.toByte())
        write(buf, 0, 1)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(buf: ByteArray, offset: Int, count: Int) {
        var offset = offset
        var count = count
        log("write %d %d %d", buf.size, offset, count)
        while (count > 0) {
            val currentBuffer = currentBuffer
            val written = min(spaceInBuffer.toDouble(), count.toDouble()).toInt()
            System.arraycopy(buf, offset, currentBuffer, positionInBuffer, written)
            offset += written
            count -= written
            setCurrentBufferWritten(written)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        close(true)
    }

    @Throws(IOException::class)
    override fun flush() {
        writeCurrentBuffer()
        _base.flush()
    }

    @Throws(IOException::class)
    open fun close(closeBase: Boolean) {
        writeCurrentBuffer()
        if (closeBase) _base.close()
    }

    protected var _buffer: ByteArray = ByteArray(_bufferSize)
    protected var bufferPosition: Long = 0
    protected var positionInBuffer: Int = 0

    @Throws(IOException::class)
    protected abstract fun transformBufferToBase(
        buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long, baseBuffer: ByteArray?
    )

    @get:Throws(IOException::class)
    protected val currentBuffer: ByteArray
        get() {
            if (positionInBuffer >= _bufferSize) {
                transformBufferAndWriteToBase(
                    _buffer,
                    0,
                    positionInBuffer,
                    bufferPosition
                )
                bufferPosition += positionInBuffer.toLong()
                positionInBuffer = 0
            }
            return _buffer
        }

    @Throws(IOException::class)
    protected fun writeCurrentBuffer() {
        if (positionInBuffer > 0) {
            transformBufferAndWriteToBase(_buffer, 0, positionInBuffer, bufferPosition)
            bufferPosition += positionInBuffer.toLong()
            positionInBuffer = 0
        }
    }

    @Throws(IOException::class)
    protected open fun transformBufferAndWriteToBase(
        buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long
    ) {
        transformBufferToBase(buf, offset, count, bufferPosition, buf)
        writeToBase(buf, offset, count)
    }

    @Throws(IOException::class)
    protected fun writeToBase(buf: ByteArray?, offset: Int, count: Int) {
        _base.write(buf, offset, count)
    }

    protected fun log(msg: String, vararg params: Any?) {
        if (ENABLE_DEBUG_LOG && GlobalConfig.isDebug()) Logger.log(
            String.format(
                "TransInputStream: $msg", *params
            )
        )
    }

    protected fun setCurrentBufferWritten(numBytes: Int) {
        positionInBuffer += numBytes
    }

    protected val spaceInBuffer: Int
        get() = _bufferSize - positionInBuffer

    companion object {
        var ENABLE_DEBUG_LOG: Boolean = false
    }
}
