package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.blockciphers.AES
import com.sovworks.eds.crypto.modes.CTR

class AESCTR @JvmOverloads constructor(private val _keySize: Int = 32) :
    CTR(object : CipherFactory {
        override fun getNumberOfCiphers(): Int {
            return 1
        }

        override fun createCipher(typeIndex: Int): BlockCipherNative {
            return AES(_keySize)
        }
    }) {
    override fun getCipherName(): String {
        return "aes"
    }


    override fun getKeySize(): Int {
        return _keySize
    }
}

