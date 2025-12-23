package com.sovworks.eds.fs.encfs.codecs.name

import com.sovworks.eds.fs.encfs.NameCodec
import com.sovworks.eds.fs.encfs.ciphers.BlockNameCipher

class BlockCSNameCodecInfo : NameCodecInfoBase() {
    override fun getEncDec(): NameCodec {
        val dci = config.dataCodecInfo
        return BlockNameCipher(dci.fileEncDec, dci.checksumCalculator, true)
    }

    override fun getName(): String {
        return NAME
    }

    override fun getDescr(): String {
        return "Block32: Block encoding with base32 output for case-sensitive systems"
    }

    override fun getVersion1(): Int {
        return 4
    }

    override fun getVersion2(): Int {
        return 0
    }

    override fun createNew(): NameCodecInfoBase {
        return BlockCSNameCodecInfo()
    }

    companion object {
        const val NAME: String = "nameio/block32"
    }
}
