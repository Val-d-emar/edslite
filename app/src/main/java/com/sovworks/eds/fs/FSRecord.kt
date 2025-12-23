package com.sovworks.eds.fs

import java.io.IOException
import java.util.Date

interface FSRecord {
    val path: Path?

    @JvmField
    @get:Throws(IOException::class)
    val name: String?

    @Throws(IOException::class)
    fun rename(newName: String?)

    @get:Throws(IOException::class)
    @set:Throws(IOException::class)
    var lastModified: Date?

    @Throws(IOException::class)
    fun delete()

    @Throws(IOException::class)
    fun moveTo(newParent: Directory?)
}
