package com.sovworks.eds.fs.errors

import java.io.IOException

object NoFreeSpaceLeftException : IOException() {
    private const val serialVersionUID = 1L
}
