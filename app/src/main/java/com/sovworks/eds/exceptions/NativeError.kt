package com.sovworks.eds.exceptions

class NativeError(var errno: Int) : Exception() {
    companion object {
        const val ENOENT: Int = -2
        const val EIO: Int = -5
        const val EBADF: Int = -9

        private const val serialVersionUID = 1L
    }
}
