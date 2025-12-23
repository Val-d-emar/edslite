package com.sovworks.eds.fs.errors

import java.io.IOException

class WrongImageFormatException(msg: String?) : IOException(msg) {
    companion object {
        /**  */
        private const val serialVersionUID = 1L
    }
}
