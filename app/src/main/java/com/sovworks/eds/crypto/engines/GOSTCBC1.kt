package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.blockciphers.GOST
import com.sovworks.eds.crypto.modes.CBC

class GOSTCBC : CBC(object : CipherFactory {
    override fun getNumberOfCiphers(): Int {
        return 1
    }

    override fun createCipher(typeIndex: Int): BlockCipherNative {
        return GOST()
    }
}) {
    override fun getCipherName(): String {
        return "gost"
    }


    override fun getKeySize(): Int {
        return 32
    }
}

