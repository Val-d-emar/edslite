package com.sovworks.eds.crypto

interface BlockCipherNative : BlockCipher {
    @get:Throws(EncryptionEngineException::class)
    val nativeInterfacePointer: Long
}
