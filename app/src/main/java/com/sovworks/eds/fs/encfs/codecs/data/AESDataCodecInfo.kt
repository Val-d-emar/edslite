package com.sovworks.eds.fs.encfs.codecs.data

import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.fs.encfs.AlgInfo
import com.sovworks.eds.fs.encfs.Config
import com.sovworks.eds.fs.encfs.DataCodecInfo
import com.sovworks.eds.fs.encfs.ciphers.AESCBCFileCipher
import com.sovworks.eds.fs.encfs.ciphers.AESCFBStreamCipher
import com.sovworks.eds.fs.encfs.macs.MACCalculator
import com.sovworks.eds.fs.encfs.macs.SHA1MACCalculator

class AESDataCodecInfo : DataCodecInfo {
    override fun getFileEncDec(): FileEncryptionEngine {
        return AESCBCFileCipher(_config!!.keySize, _config!!.blockSize)
    }

    override fun getStreamEncDec(): EncryptionEngine {
        return AESCFBStreamCipher(_config!!.keySize)
    }

    override fun getChecksumCalculator(): MACCalculator {
        return SHA1MACCalculator(_config!!.keySize)
    }

    override fun select(config: Config): AlgInfo {
        val info = AESDataCodecInfo()
        info._config = config
        return info
    }

    override fun getName(): String {
        return NAME
    }

    override fun getDescr(): String {
        return "AES: 16 byte block cipher"
    }

    override fun getVersion1(): Int {
        return 3
    }

    override fun getVersion2(): Int {
        return 0
    }

    private var _config: Config? = null

    companion object {
        const val NAME: String = "ssl/aes"
    }
}
