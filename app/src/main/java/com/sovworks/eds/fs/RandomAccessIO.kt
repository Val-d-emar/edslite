package com.sovworks.eds.fs

import java.io.Closeable
import java.io.IOException

interface RandomAccessIO : Closeable, RandomStorageAccess, DataInput,
    DataOutput {
    @Throws(IOException::class)
    fun setLength(newLength: Long)
}
