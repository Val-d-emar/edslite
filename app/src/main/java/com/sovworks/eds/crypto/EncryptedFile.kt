package com.sovworks.eds.crypto

import com.sovworks.eds.container.EncryptedFileLayout
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.TransRandomAccessIO
import java.io.IOException
import kotlin.math.min


open class EncryptedFile @JvmOverloads constructor(
	base: RandomAccessIO,
	@JvmField protected val _layout: EncryptedFileLayout,
	bufferSizeInBlocks: Int = DEFAULT_BUFFER_SIZE_IN_BLOCKS
) :
    TransRandomAccessIO(base, bufferSizeInBlocks * _layout.engine.getFileBlockSize()) {
    @JvmOverloads
    constructor(
        pathToFile: Path,
        mode: AccessMode?,
        layout: EncryptedFileLayout,
        bufferSizeInBlocks: Int = DEFAULT_BUFFER_SIZE_IN_BLOCKS
    ) : this(pathToFile.file.getRandomAccessIO(mode), layout, bufferSizeInBlocks)

    protected val _dataOffset: Long = _layout.encryptedDataOffset
    protected val _fileBlockSize: Int = _layout.engine.getFileBlockSize()
    protected var _transBuffer: ByteArray = ByteArray(_bufferSize)

    init {
        try {
            _length = calcVirtPosition(base.length())
        } catch (ignored: IOException) {
        }
    }

    override fun calcBasePosition(position: Long): Long {
        return position + _dataOffset
    }

    override fun calcVirtPosition(basePosition: Long): Long {
        return basePosition - _dataOffset
    }

    @Throws(IOException::class)
    override fun transformBufferFromBase(
        baseBuffer: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        dstBuffer: ByteArray
    ): Int {
        if (baseBuffer != dstBuffer) System.arraycopy(baseBuffer, offset, dstBuffer, offset, count)

        if (!_allowSkip) decryptBuffer(dstBuffer, offset, count, bufferPosition)
        else {
            var i = 0
            while (i < count) {
                val curSize = min((count - i).toDouble(), _fileBlockSize.toDouble()).toInt()
                if (curSize != _fileBlockSize || !isBufferEmpty(
                        dstBuffer,
                        offset + i,
                        curSize
                    )
                ) decryptBuffer(dstBuffer, offset + i, curSize, bufferPosition + i)
                i += curSize
            }
        }
        return count
    }

    @Throws(IOException::class)
    protected fun decryptBuffer(buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long) {
        val ee = _layout.engine
        _layout.setEncryptionEngineIV(ee!!, bufferPosition)
        try {
            ee.decrypt(buf, offset, count)
        } catch (e: EncryptionEngineException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun transformBufferAndWriteToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long
    ) {
        transformBufferToBase(buf, offset, count, bufferPosition, _transBuffer)
        writeToBase(_transBuffer, offset, count, bufferPosition)
    }

    @Throws(IOException::class)
    override fun transformBufferToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        baseBuffer: ByteArray
    ) {
        System.arraycopy(buf, offset, baseBuffer, offset, count)
        if (!_allowSkip) encryptBuffer(baseBuffer, offset, count, bufferPosition)
        else {
            var i = 0
            while (i < count) {
                val curSize = min((count - i).toDouble(), _fileBlockSize.toDouble()).toInt()
                if (curSize != _fileBlockSize || !isBufferEmpty(
                        baseBuffer,
                        offset + i,
                        curSize
                    )
                ) encryptBuffer(baseBuffer, offset + i, curSize, bufferPosition + i)
                i += curSize
            }
        }
    }

    @Throws(IOException::class)
    protected fun encryptBuffer(buf: ByteArray?, offset: Int, count: Int, bufferPosition: Long) {
        val ee = _layout.engine
        _layout.setEncryptionEngineIV(ee!!, bufferPosition)
        try {
            ee.encrypt(buf, offset, count)
        } catch (e: EncryptionEngineException) {
            throw IOException(e)
        }
    }

    companion object {
        fun isBufferEmpty(buf: ByteArray, offset: Int, count: Int): Boolean {
            for (i in 0..<count) if (buf[offset + i].toInt() != 0) return false
            return true
        }

        private const val DEFAULT_BUFFER_SIZE_IN_BLOCKS = 16
    }
}
