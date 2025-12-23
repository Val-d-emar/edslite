package com.sovworks.eds.crypto

interface CipherFactory {
    fun createCipher(typeIndex: Int): BlockCipherNative?
    val numberOfCiphers: Int
}
