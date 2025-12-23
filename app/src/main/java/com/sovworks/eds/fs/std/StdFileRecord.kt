package com.sovworks.eds.fs.std

import android.os.ParcelFileDescriptor
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.ProgressInfo
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.Util
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class StdFileRecord(path: StdFsPath) : StdFsRecord(path), File {
    init {
        require(!(path.exists() && !path.javaFile.isFile)) { "StdFileRecord error: path must point to a file" }
    }

    @Throws(IOException::class)
    override fun getSize(): Long {
        return _path.javaFile.length()
    }

    @Throws(IOException::class)
    override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(
            _path.javaFile, Util.getParcelFileDescriptorModeFromAccessMode(accessMode)
        )
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

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        return FileInputStream(_path.javaFile)
        // return new RandomAccessInputStream(getRandomAccessIO(AccessMode.Read));
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        return FileOutputStream(_path.javaFile)
    }

    @Throws(IOException::class)
    override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
        return StdFsFileIO(_path.javaFile, accessMode)
    }
}
