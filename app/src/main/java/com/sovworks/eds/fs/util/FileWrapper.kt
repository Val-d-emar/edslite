package com.sovworks.eds.fs.util

import android.os.ParcelFileDescriptor
import com.sovworks.eds.fs.FSRecord
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

abstract class FileWrapper(path: Path, base: File) :
    FSRecordWrapper(path, base), File {
    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        return base.getInputStream()
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        return base.getOutputStream()
    }

    @Throws(IOException::class)
    override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
        return base.getRandomAccessIO(accessMode)
    }

    @Throws(IOException::class)
    override fun getSize(): Long {
        return base.getSize()
    }

    @Throws(IOException::class)
    override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor? {
        return base.getFileDescriptor(accessMode)
    }

    @Throws(IOException::class)
    override fun copyToOutputStream(
        output: OutputStream, offset: Long, count: Long, progressInfo: ProgressInfo
    ) {
        Util.copyFileToOutputStream(
            output,
            this, offset, count, progressInfo
        )
    }

    @Throws(IOException::class)
    override fun copyFromInputStream(
        input: InputStream, offset: Long, count: Long, progressInfo: ProgressInfo
    ) {
        Util.copyFileFromInputStream(
            input,
            this, offset, count, progressInfo
        )
    }

    override val base: FSRecord?
        get() = super.getBase() as File
}
