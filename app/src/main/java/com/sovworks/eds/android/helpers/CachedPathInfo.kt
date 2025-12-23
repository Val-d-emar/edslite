package com.sovworks.eds.android.helpers

import com.sovworks.eds.fs.Path
import java.io.IOException
import java.util.Date

interface CachedPathInfo {
    @JvmField
    val path: Path?
    val pathDesc: String?
    @JvmField
    val name: String?
    @JvmField
    val isFile: Boolean
    @JvmField
    val isDirectory: Boolean
    @JvmField
    val modificationDate: Date?
    @JvmField
    val size: Long

    @Throws(IOException::class)
    fun init(path: Path?)
}
