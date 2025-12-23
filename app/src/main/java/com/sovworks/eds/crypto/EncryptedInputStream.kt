package com.sovworks.eds.crypto

import com.sovworks.eds.container.EncryptedFileLayout
import com.sovworks.eds.fs.util.TransInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays


class EncryptedInputStream(base: InputStream?, protected val _layout: EncryptedFileLayout) :
    TransInputStream(base, _layout.engine.getFileBlockSize()) {
    @Synchronized
    @Throws(IOException::class)
    override fun close(closeBase: Boolean) {
        try {
            super.close(closeBase)
        } finally {
            _layout.close()
            Arrays.fill(_buffer, 0.toByte())
        }
    }

    fun setAllowEmptyParts(`val`: Boolean) {
        _allowEmptyParts = `val`
    }

    protected val _engine: FileEncryptionEngine? = _layout.engine
    protected var _allowEmptyParts: Boolean = true

    @Throws(IOException::class)
    override fun transformBufferFromBase(
        baseBuffer: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        dstBuffer: ByteArray
    ): Int {
        if (_allowEmptyParts && count == _bufferSize && EncryptedFile.Companion.isBufferEmpty(
                baseBuffer,
                offset,
                count
            )
        ) return count
        val ee = _layout.engine
        _layout.setEncryptionEngineIV(ee!!, bufferPosition)
        try {
            ee.decrypt(baseBuffer, offset, count)
        } catch (e: EncryptionEngineException) {
            throw IOException(e)
        }
        return count
    }
}
