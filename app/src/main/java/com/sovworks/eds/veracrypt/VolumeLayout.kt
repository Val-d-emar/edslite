package com.sovworks.eds.veracrypt

import com.sovworks.eds.truecrypt.StdLayout
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class VolumeLayout : StdLayout() {
    override fun setNumKDFIterations(num: Int) {
        _numIterations = num
    }

    @Throws(IOException::class)
    override fun close() {
        super.close()
        _numIterations = 0
    }

    override val supportedHashFuncs: List<MessageDigest>?
        get() {
            val l = super.supportedHashFuncs
            try {
                l.add(MessageDigest.getInstance("SHA256"))
            } catch (ignored: NoSuchAlgorithmException) {
            }
            return l
        }

    override fun getMKKDFNumIterations(hashFunc: MessageDigest): Int {
        return if (_numIterations > 0)
            getKDFIterationsFromPIM(_numIterations)
        else
            if ("ripemd160".equals(hashFunc.algorithm, ignoreCase = true)) 655331 else 500000
    }

    private var _numIterations = 0

    companion object {
        fun getKDFIterationsFromPIM(pim: Int): Int {
            return 15000 + pim * 1000
        }

        protected val headerSignature: ByteArray =
            byteArrayOf('V'.code.toByte(), 'E'.code.toByte(), 'R'.code.toByte(), 'A'.code.toByte())
            get() = Companion.field
        val minCompatibleProgramVersion: Short = 0x010b
            get() = Companion.field
    }
}
