package com.sovworks.eds.fs

import java.io.IOException

interface RandomStorageAccess {
    @Throws(IOException::class)
    fun seek(position: Long)

    @get:Throws(IOException::class)
    val filePointer: Long

    @Throws(IOException::class)
    fun length(): Long
}
