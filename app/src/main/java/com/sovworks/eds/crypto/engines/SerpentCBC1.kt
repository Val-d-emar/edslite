package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.blockciphers.Serpent
import com.sovworks.eds.crypto.modes.CBC

class SerpentCBC : CBC(object : CipherFactory {
    override fun getNumberOfCiphers(): Int {
        return 1
    }

    override fun createCipher(typeIndex: Int): BlockCipherNative {
        return Serpent()
    }
}) {
    override fun getCipherName(): String {
        return "serpent"
    }


    override fun getKeySize(): Int {
        return 32
    }
}

