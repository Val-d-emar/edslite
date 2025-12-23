package com.sovworks.eds.fs.exfat

import android.os.ParcelFileDescriptor
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWriteTruncate
import com.sovworks.eds.fs.File.AccessMode.Write
import com.sovworks.eds.fs.File.AccessMode.WriteAppend
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.RandomAccessInputStream
import com.sovworks.eds.fs.util.RandomAccessOutputStream
import com.sovworks.eds.fs.util.Util
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class ExFatFile(exFat: ExFat, path: ExFatPath) : ExFatRecord(exFat, path), File {
    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        return RandomAccessInputStream(getRandomAccessIO(Read))
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        return RandomAccessOutputStream(getRandomAccessIO(Write))
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
        synchronized(_exFat._sync) {
            var fs = _path.attr
            if (accessMode == Read && (fs == null || fs.isDir)) throw FileNotFoundException()
            if (fs == null) {
                val res = _exFat.makeFile(_path.pathString)
                if (res != 0) throw IOException("Failed creating file. Error code = $res")
                fs = _path.attr
                if (fs == null) throw IOException("File node is null")
            }
            var startPos: Long = 0
            if (accessMode == WriteAppend) startPos = fs.size

            val handle = _exFat.openFile(_path.pathString)
            if (handle == 0L) throw IOException("Failed getting file handle")
            if (accessMode == Write || accessMode == ReadWriteTruncate) {
                val res = _exFat.truncate(handle, 0)
                if (res != 0) {
                    _exFat.closeFile(res.toLong())
                    throw IOException("Failed truncating file. Error code = $res")
                }
            }
            return ExFatRAIO(_exFat, handle, startPos, accessMode)
        }
    }

    @Throws(IOException::class)
    override fun delete() {
        synchronized(_exFat._sync) {
            val res = _exFat.delete(_path.pathString)
            if (res != 0) throw IOException("Delete failed. Error code = $res")
        }
    }

    @Throws(IOException::class)
    override fun getSize(): Long {
        return _path.attr.size
    }

    @Throws(IOException::class)
    override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor? {
        return null
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
}
