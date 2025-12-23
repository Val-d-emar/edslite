package com.sovworks.eds.fs

import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface File : FSRecord {
    enum class AccessMode {
        Read,
        Write,
        WriteAppend,
        ReadWrite,
        ReadWriteTruncate
    }

    interface ProgressInfo {
        fun setProcessed(num: Long)

        val isCancelled: Boolean
    }

    @get:Throws(IOException::class)
    val inputStream: InputStream?

    @get:Throws(IOException::class)
    val outputStream: OutputStream?

    @Throws(IOException::class)
    fun getRandomAccessIO(accessMode: AccessMode?): RandomAccessIO?

    @get:Throws(IOException::class)
    val size: Long

    @Throws(IOException::class)
    fun getFileDescriptor(accessMode: AccessMode?): ParcelFileDescriptor?

    @Throws(IOException::class)
    fun copyToOutputStream(
        output: OutputStream?,
        offset: Long,
        count: Long,
        progressInfo: ProgressInfo?
    )

    @Throws(IOException::class)
    fun copyFromInputStream(
        input: InputStream?,
        offset: Long,
        count: Long,
        progressInfo: ProgressInfo?
    )
}
