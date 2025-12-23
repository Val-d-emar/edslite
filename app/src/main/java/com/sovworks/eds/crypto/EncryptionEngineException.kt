package com.sovworks.eds.crypto

import com.sovworks.eds.exceptions.ApplicationException

class EncryptionEngineException : ApplicationException {
    constructor()

    constructor(msg: String?, cause: Throwable?) : super(msg, cause)

    constructor(msg: String?) : super(msg)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
