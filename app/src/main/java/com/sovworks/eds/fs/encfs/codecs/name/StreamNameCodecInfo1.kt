package com.sovworks.eds.fs.encfs.codecs.name

import com.sovworks.eds.fs.encfs.NameCodec
import com.sovworks.eds.fs.encfs.ciphers.StreamNameCipher

class StreamNameCodecInfo : NameCodecInfoBase() {
    override fun getEncDec(): NameCodec {
        val dci = config.dataCodecInfo
        return StreamNameCipher(dci.streamEncDec, dci.checksumCalculator)
    }

    override fun getName(): String {
        return NAME
    }

    override fun getDescr(): String {
        return "Stream: Stream encoding, keeps filenames as short as possible"
    }

    override fun getVersion1(): Int {
        return 2
    }

    override fun getVersion2(): Int {
        return 1
    }

    override fun createNew(): NameCodecInfoBase {
        return StreamNameCodecInfo()
    }

    companion object {
        const val NAME: String = "nameio/stream"
    }
}
