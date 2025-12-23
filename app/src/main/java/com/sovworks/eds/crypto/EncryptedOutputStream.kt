package com.sovworks.eds.crypto

import com.sovworks.eds.container.EncryptedFileLayout
import com.sovworks.eds.fs.util.TransOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Arrays


class EncryptedOutputStream(base: OutputStream?, protected val _layout: EncryptedFileLayout) :
    TransOutputStream(base, _layout.engine.getFileBlockSize()) {
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
    protected var _allowEmptyParts: Boolean = false

    @Throws(IOException::class)
    override fun transformBufferToBase(
        buf: ByteArray,
        offset: Int,
        count: Int,
        bufferPosition: Long,
        baseBuffer: ByteArray
    ) {
        if (_allowEmptyParts && count == _bufferSize && EncryptedFile.Companion.isBufferEmpty(
                buf,
                offset,
                count
            )
        ) return
        val ee = _layout.engine
        _layout.setEncryptionEngineIV(ee!!, bufferPosition)
        try {
            ee.encrypt(buf, offset, count)
        } catch (e: EncryptionEngineException) {
            throw IOException(e)
        }
    }
}
