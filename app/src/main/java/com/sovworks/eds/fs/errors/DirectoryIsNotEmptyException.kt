package com.sovworks.eds.fs.errors

import java.io.IOException

class DirectoryIsNotEmptyException : IOException {
    constructor(path: String) {
        this.path = path
    }

    constructor(path: String, msg: String?) : super(msg) {
        this.path = path
    }

    val path: String

    companion object {
        private const val serialVersionUID = 1L
    }
}
