package com.sovworks.eds.crypto

import android.util.SparseArray
import com.sovworks.eds.container.VolumeLayout
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.util.Arrays
import kotlin.math.min

open class EncryptedFileWithCache @JvmOverloads constructor(
    base: RandomAccessIO,
    layout: VolumeLayout,
    private val _maxNumberCachedBuffers: Int = DEFAULT_NUM_CACHED_BUFFERS,
    bufferSizeInBlocks: Int = DEFAULT_BUFFER_SIZE_IN_BLOCKS
) :
    EncryptedFile(base, layout, bufferSizeInBlocks) {
    constructor(
        pathToFile: Path,
        mode: AccessMode?,
        layout: VolumeLayout
    ) : this(
        pathToFile.file.getRandomAccessIO(mode),
        layout,
        DEFAULT_NUM_CACHED_BUFFERS,
        DEFAULT_BUFFER_SIZE_IN_BLOCKS
    )

    constructor(
        pathToFile: Path,
        mode: AccessMode?,
        layout: VolumeLayout,
        maxNumberCachedBuffers: Int,
        bufferSizeInBlocks: Int
    ) : this(
        pathToFile.file.getRandomAccessIO(mode),
        layout,
        maxNumberCachedBuffers,
        bufferSizeInBlocks
    )

    @Synchronized
    @Throws(IOException::class)
    override fun flush() {
        writeCurrentBuffer()
        flushCachedChanges()
        base.flush()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close(closeBase: Boolean) {
        try {
            writeCurrentBuffer()
            flushCachedChanges()
            for (i in 0..<_cache.size()) {
                val ci = _cache.valueAt(i)
                Arrays.fill(ci.buffer, 0.toByte())
            }
            _cache.clear()
            Arrays.fill(_buffer, 0.toByte())
        } finally {
            if (closeBase) base.close()
        }
    }

    @Throws(IOException::class)
    override fun loadCurrentBuffer() {
        if (_isBufferLoaded) return
        val bp = bufferPosition
        var space = min((_length - bp).toDouble(), _bufferSize.toDouble()).toInt()
        if (space < 0) space = 0
        val bufIndex = bufferIndex
        var ci = _cache[bufIndex]
        if (ci == null) {
            ci = reserveCacheSlot(bufIndex)
            if (space > 0) space = readFromBaseAndTransformBuffer(ci.buffer, 0, space, bp)
            Arrays.fill(ci.buffer, space, _bufferSize, 0.toByte())
        } else ci.refCount++

        System.arraycopy(ci.buffer, 0, _buffer, 0, _bufferSize)
        _isBufferChanged = false
        _isBufferLoaded = true
    }

    @Throws(IOException::class)
    override fun writeCurrentBuffer() {
        if (!_isBufferChanged) return
        val ci = _cache[bufferIndex]
        System.arraycopy(_buffer, 0, ci.buffer, 0, _bufferSize)
        ci.isChanged = true
        _isBufferChanged = false
    }

    private class CachedSectorInfo
        (bufSize: Int) {
        var refCount: Int = 1
        val buffer: ByteArray = ByteArray(bufSize)
        var isChanged: Boolean = false
    }

    private val _cache =
        SparseArray<CachedSectorInfo>(_maxNumberCachedBuffers)

    private val bufferIndex: Int
        get() = (bufferPosition / _bufferSize).toInt()

    @Throws(IOException::class)
    private fun flushCachedChanges() {
        val cs = _cache.size()
        for (i in 0..<cs) {
            val ci = _cache.valueAt(i)
            if (ci.isChanged) writeCachedBuffer(_cache.keyAt(i), ci)
        }
    }

    @Throws(IOException::class)
    private fun writeCachedBuffer(bufIndex: Int, ci: CachedSectorInfo) {
        val bp = bufIndex.toLong() * _bufferSize
        transformBufferAndWriteToBase(
            ci.buffer,
            0,
            min(_bufferSize.toDouble(), (_length - bp).toDouble()).toInt(),
            bp
        )
        ci.isChanged = false
    }

    @Throws(IOException::class)
    private fun reserveCacheSlot(bufIndex: Int): CachedSectorInfo {
        val cs = _cache.size()
        if (cs < _maxNumberCachedBuffers) {
            val ci = CachedSectorInfo(_buffer.size)
            _cache.put(bufIndex, ci)
            return ci
        }

        var minRefsBufIndex = -1
        var minRefs = 0
        for (i in 0..<cs) {
            val oldBufIndex = _cache.keyAt(i)
            val ci = _cache.valueAt(i)
            if (minRefs == 0 || ci.refCount < minRefs) {
                minRefs = ci.refCount
                minRefsBufIndex = oldBufIndex
            }
        }
        val ci = _cache[minRefsBufIndex]
        if (ci.isChanged) writeCachedBuffer(minRefsBufIndex, ci)
        _cache.remove(minRefsBufIndex)
        ci.refCount = 1
        _cache.put(bufIndex, ci)
        return ci
    }

    companion object {
        private const val DEFAULT_NUM_CACHED_BUFFERS = 25
        private const val DEFAULT_BUFFER_SIZE_IN_BLOCKS = 40
    }
}
