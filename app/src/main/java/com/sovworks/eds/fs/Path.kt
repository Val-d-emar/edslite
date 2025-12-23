package com.sovworks.eds.fs

import java.io.IOException

interface Path : Comparable<Path?> {
    @JvmField
    val fileSystem: FileSystem?

    @JvmField
    val pathString: String?

    @Throws(IOException::class)
    fun exists(): Boolean

    @JvmField
    @get:Throws(IOException::class)
    val isFile: Boolean

    @JvmField
    @get:Throws(IOException::class)
    val isDirectory: Boolean

    @JvmField
    val pathDesc: String?

    @get:Throws(IOException::class)
    val isRootDirectory: Boolean

    @Throws(IOException::class)
    fun combine(part: String?): Path?

    @Throws(IOException::class)
    fun getDirectory(): Directory?

    @Throws(IOException::class)
    fun getFile(): File?

    @get:Throws(IOException::class)
    val parentPath: Path?
}
