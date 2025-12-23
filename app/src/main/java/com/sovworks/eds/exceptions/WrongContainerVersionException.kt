package com.sovworks.eds.exceptions


class WrongContainerVersionException : ApplicationException {
    constructor() : super("Unsupported container version")

    constructor(msg: String?) : super(msg)

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
