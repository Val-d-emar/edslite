package com.sovworks.eds.fs.util

import com.sovworks.eds.android.Logger
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException
import kotlin.math.min

abstract class BufferedRandomAccessIO(base: RandomAccessIO?, protected val _bufferSize: Int) :
    RandomAccessIOWrapper(base) {
    @Throws(IOException::class)
    override fun getFilePointer(): Long {
        return _currentPosition
    }

    @Throws(IOException::class)
    override fun close() {
        close(true)
    }

    @Synchronized
    @Throws(IOException::class)
    open fun close(closeBase: Boolean) {
        if (closeBase) super.close()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun read(buf: ByteArray, offset: Int, count: Int): Int {
        log("read %d %d %d", buf.size, offset, count)
        if (_currentPosition >= _length) return -1
        if (count > 0) {
            val currentBuffer = currentBuffer
            val avail =
                min(spaceInBuffer.toDouble(), (_length - _currentPosition).toDouble()).toLong()
            val read = min(avail.toDouble(), count.toDouble()).toInt()
            System.arraycopy(currentBuffer, positionInBuffer, buf, offset, read)
            setCurrentBufferRead(read)
            // if(LOG_MORE)
            // Log.d("EDS ClusterChainIO",String.format("ClusterChainIO read: file=%s read %d
            // bytes",_path.getPathString(),avail));
            return read
        }

        return 0
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val buf = ByteArray(1)
        return if (read(buf, 0, 1) == 1) (buf[0].toInt() and 0xFF) else -1
    }

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

    @Synchronized
    @Throws(IOException::class)
    override fun seek(position: Long) {
        log("seek %d", position)
        require(position >= 0)
        _currentPosition = position
        // DEBUG
        // Log.d("EncryptedRAF.seek",String.format("current sector offset = %d, is_buffer_empty=%s,
        // is_buffer_changed=%s ", _currentSectorOffset,_is_buffer_empty,_is_buffer_changed));
    }

    @Throws(IOException::class)
    override fun length(): Long {
        return _length
    }

    @Synchronized
    @Throws(IOException::class)
    override fun flush() {
        writeCurrentBuffer()
        super.flush()
    }

    protected var _currentPosition: Long = 0
    protected var _length: Long = 0

    @get:Throws(IOException::class)
    protected abstract val currentBuffer: ByteArray

    protected abstract val bufferPosition: Long

    protected fun log(msg: String, vararg params: Any?) {
        if (ENABLE_DEBUG_LOG && GlobalConfig.isDebug()) Logger.log(
            String.format(
                "EncryptedFile: $msg", *params
            )
        )
    }

    protected open fun setCurrentBufferWritten(numBytes: Int) {
        _currentPosition += numBytes.toLong()
        if (_currentPosition > _length) _length = _currentPosition
    }

    protected fun setCurrentBufferRead(numBytes: Int) {
        _currentPosition += numBytes.toLong()
    }

    protected val positionInBuffer: Int
        get() = (_currentPosition % _bufferSize).toInt()

    @Throws(IOException::class)
    protected open fun writeCurrentBuffer() {
        val buf = currentBuffer
        val base = base
        base!!.seek(bufferPosition)
        base.write(buf, 0, buf.size)
    }

    protected val spaceInBuffer: Int
        get() = _bufferSize - positionInBuffer

    companion object {
        var ENABLE_DEBUG_LOG: Boolean = false
    }
}
