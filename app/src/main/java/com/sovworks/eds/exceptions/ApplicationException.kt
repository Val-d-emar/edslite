package com.sovworks.eds.exceptions

open class ApplicationException : Exception {
    constructor()

    constructor(msg: String?, cause: Throwable?) : super(msg, cause)

    constructor(msg: String?) : super(msg)

    var code: Int = 0

    companion object {
        const val CODE_CONTAINER_IS_SYNCING: Int = 1
        const val CODE_CONTAINER_MOUNT_FAILED: Int = 2

        /**
         *
         */
        private const val serialVersionUID = 1L
    }
}
