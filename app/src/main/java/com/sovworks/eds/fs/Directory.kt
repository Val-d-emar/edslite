package com.sovworks.eds.fs

import java.io.Closeable
import java.io.IOException

interface Directory : FSRecord {
    interface Contents : Iterable<Path?>, Closeable

    @Throws(IOException::class)
    fun createDirectory(name: String?): Directory?

    @Throws(IOException::class)
    fun createFile(name: String?): File?

    @Throws(IOException::class)
    fun list(): Contents?

    @get:Throws(IOException::class)
    val totalSpace: Long

    @get:Throws(IOException::class)
    val freeSpace: Long
}
