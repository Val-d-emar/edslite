package com.sovworks.eds.fs.errors

import java.io.IOException

class FileInUseException : IOException {
    constructor()

    constructor(msg: String?) : super(msg)

    companion object {
        /**  */
        private const val serialVersionUID = 1L
    }
}
