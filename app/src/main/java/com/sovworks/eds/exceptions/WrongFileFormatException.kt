package com.sovworks.eds.exceptions

class WrongFileFormatException : ApplicationException {
    constructor() : super("Wrong password or unsupported container format")

    constructor(msg: String?) : super(msg)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
