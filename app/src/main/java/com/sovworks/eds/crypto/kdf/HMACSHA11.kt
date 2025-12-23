package com.sovworks.eds.crypto.kdf

import java.security.MessageDigest

class HMACSHA1(key: ByteArray) :
    HMAC(key, MessageDigest.getInstance("SHA1"), SHA1_BLOCK_SIZE) {
    companion object {
        const val SHA1_BLOCK_SIZE: Int = 64
    }
}
