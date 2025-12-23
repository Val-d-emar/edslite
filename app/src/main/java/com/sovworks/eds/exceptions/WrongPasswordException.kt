package com.sovworks.eds.exceptions

class WrongPasswordException : ApplicationException {
    constructor() : super("Wrong password")

    constructor(msg: String?) : super(msg)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
