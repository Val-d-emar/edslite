package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.BlockCipherNative
import com.sovworks.eds.crypto.CipherFactory
import com.sovworks.eds.crypto.blockciphers.AES
import com.sovworks.eds.crypto.modes.XTS

class AESXTS @JvmOverloads constructor(private val _keySize: Int = 64) :
    XTS(object : CipherFactory {
        override fun getNumberOfCiphers(): Int {
            return 1
        }

        override fun createCipher(typeIndex: Int): BlockCipherNative {
            return AES(_keySize / 2)
        }
    }) {
    override fun getKeySize(): Int {
        return _keySize
    }

    override fun getCipherName(): String {
        return NAME
    }

    companion object {
        const val NAME: String = "aes"
    }
}

