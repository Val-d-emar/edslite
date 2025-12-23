package com.sovworks.eds.exceptions

class UnsupportedContainerTypeException : ApplicationException {
    constructor() : super("Unsupported container type")

    constructor(msg: String?) : super(msg)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
