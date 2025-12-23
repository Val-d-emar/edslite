package com.sovworks.eds.crypto

import java.io.IOException

class EncDecException : IOException {
    constructor(msg: String?) : super(msg)

    constructor(cause: EncryptionEngineException) : super(cause.message)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
