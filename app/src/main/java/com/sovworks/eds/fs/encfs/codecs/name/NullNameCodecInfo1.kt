package com.sovworks.eds.fs.encfs.codecs.name

import com.sovworks.eds.fs.encfs.NameCodec
import com.sovworks.eds.fs.encfs.ciphers.NullNameCipher

class NullNameCodecInfo : NameCodecInfoBase() {
    override fun getEncDec(): NameCodec {
        return NullNameCipher()
    }

    override fun getName(): String {
        return NAME
    }

    override fun getDescr(): String {
        return "Null: No encryption of filenames"
    }

    override fun getVersion1(): Int {
        return 1
    }

    override fun getVersion2(): Int {
        return 0
    }

    override fun createNew(): NameCodecInfoBase {
        return NullNameCodecInfo()
    }

    companion object {
        const val NAME: String = "nameio/null"
    }
}
