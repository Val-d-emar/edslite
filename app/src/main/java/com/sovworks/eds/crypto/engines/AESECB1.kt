package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.blockciphers.AES
import com.sovworks.eds.crypto.modes.ECB

class AESECB @JvmOverloads constructor(keySize: Int = 32) : ECB(AES(keySize)) {
    override fun getCipherName(): String {
        return "aes"
    }
}

