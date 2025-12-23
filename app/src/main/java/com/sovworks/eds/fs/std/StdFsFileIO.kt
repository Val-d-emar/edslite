package com.sovworks.eds.fs.std

import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWriteTruncate
import com.sovworks.eds.fs.File.AccessMode.WriteAppend
import com.sovworks.eds.fs.RandomAccessIO
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.io.SyncFailedException

class StdFsFileIO(f: File?, mode: AccessMode) :
    RandomAccessFile(f, if (mode == Read) "r" else "rw"),
    RandomAccessIO {
    init {
        if (mode == ReadWriteTruncate) setLength(0)
        else if (mode == WriteAppend) seek(length())
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            val fd = fd
            fd?.sync()
        } catch (ignored: SyncFailedException) {
        }
        super.close()
    }

    @Throws(IOException::class)
    override fun flush() {
    }
}
