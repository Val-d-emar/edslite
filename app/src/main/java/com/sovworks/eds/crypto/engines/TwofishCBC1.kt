package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.blockciphers.Twofish
import com.sovworks.eds.crypto.modes.CBC

class TwofishCBC : CBC(object : CipherFactory {
    override fun getNumberOfCiphers(): Int {
        return 1
    }

    override fun createCipher(typeIndex: Int): BlockCipherNative {
        return Twofish()
    }
}) {
    override fun getCipherName(): String {
        return "twofish"
    }


    override fun getKeySize(): Int {
        return 32
    }
}

