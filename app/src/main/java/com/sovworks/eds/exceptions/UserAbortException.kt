package com.sovworks.eds.exceptions


class UserAbortException : ApplicationException {
    constructor()

    constructor(msg: String?) : super(msg)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
