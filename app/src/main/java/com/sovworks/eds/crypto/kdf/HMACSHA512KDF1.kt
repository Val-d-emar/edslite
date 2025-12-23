package com.sovworks.eds.crypto.kdf

import com.sovworks.eds.crypto.EncryptionEngineException
import java.security.NoSuchAlgorithmException

class HMACSHA512KDF : PBKDF() {
    @Throws(EncryptionEngineException::class)
    override fun initHMAC(password: ByteArray): HMAC {
        try {
            return HMACSHA512(password)
        } catch (e: NoSuchAlgorithmException) {
            val e1 = EncryptionEngineException()
            e1.initCause(e)
            throw e1
        }
    }
}