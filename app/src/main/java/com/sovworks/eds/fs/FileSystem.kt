package com.sovworks.eds.fs

import java.io.IOException

interface FileSystem {
    @JvmField
    @get:Throws(IOException::class)
    val rootPath: Path?

    @Throws(IOException::class)
    fun getPath(pathString: String?): Path?

    @Throws(IOException::class)
    fun close(force: Boolean)

    val isClosed: Boolean
}
