package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.fs.encfs.NameCodec

class NullNameCipher : NameCodec {
    override fun encodeName(plaintextName: String): String {
        return plaintextName
    }

    override fun decodeName(encodedName: String): String {
        return encodedName
    }

    override fun init(key: ByteArray) {
    }

    override fun close() {
    }

    override fun setIV(iv: ByteArray) {
    }

    override fun getChainedIV(plaintextName: String): ByteArray {
        return null
    }

    override fun getIV(): ByteArray {
        return null
    }

    override fun getIVSize(): Int {
        return 0
    }
}
