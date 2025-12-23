package com.sovworks.eds.crypto.hash

import java.security.MessageDigest

class RIPEMD160 : MessageDigest("ripemd160") {
    fun close() {
        if (_contextPtr != 0L) {
            freeContext(_contextPtr)
            _contextPtr = 0
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        close()
    }

    override fun engineGetDigestLength(): Int {
        return DIGEST_LENGTH
    }

    override fun engineDigest(): ByteArray {
        val res = ByteArray(DIGEST_LENGTH)
        finishDigest(_contextPtr, res)
        engineReset()
        return res
    }

    override fun engineReset() {
        resetDigest(_contextPtr)
    }

    override fun engineUpdate(input: Byte) {
        updateDigestByte(_contextPtr, input)
    }

    override fun engineUpdate(input: ByteArray, offset: Int, len: Int) {
        updateDigest(_contextPtr, input, offset, len)
    }

    private var _contextPtr: Long

    init {
        _contextPtr = initContext()
        engineReset()
    }

    private external fun initContext(): Long
    private external fun freeContext(contextPtr: Long)
    private external fun resetDigest(contextPtr: Long)
    private external fun updateDigestByte(contextPtr: Long, data: Byte)
    private external fun updateDigest(contextPtr: Long, data: ByteArray, offset: Int, len: Int)
    private external fun finishDigest(contextPtr: Long, result: ByteArray)


    companion object {
        init {
            System.loadLibrary("edsripemd160")
        }

        private const val DIGEST_LENGTH = 20
    }
}