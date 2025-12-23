package com.sovworks.eds.luks

import android.annotation.SuppressLint
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.crypto.EncryptedFile
import com.sovworks.eds.crypto.EncryptedFileWithCache
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.security.SecureRandom

abstract class FormatInfoBase : ContainerFormatInfo {
    override val volumeLayout: com.sovworks.eds.container.VolumeLayout
        get() = VolumeLayout()

    override fun hasHiddenContainerSupport(): Boolean {
        return false
    }

    override fun hasKeyfilesSupport(): Boolean {
        return false
    }

    override fun hasCustomKDFIterationsSupport(): Boolean {
        return false
    }

    override val hiddenVolumeLayout: com.sovworks.eds.container.VolumeLayout?
        get() = null

    override val openingPriority: Int
        get() = 1

    override fun toString(): String {
        return formatName!!
    }

    @SuppressLint("TrulyRandom")
    @Throws(IOException::class, ApplicationException::class)
    override fun formatContainer(
        io: RandomAccessIO,
        layout: com.sovworks.eds.container.VolumeLayout?,
        fsInfo: FileSystemInfo
    ) {
        val vl = (layout as VolumeLayout?)
        val len: Long = vl!!.getEncryptedDataSize(io.length()) + vl.getEncryptedDataOffset()
        io.setLength(len)
        val sr = SecureRandom()
        prepareHeaderLocations(sr, io, vl)
        vl.writeHeader(io)
        val et: EncryptedFile = EncryptedFileWithCache(io, vl)
        vl.formatFS(et, fsInfo)
        et.close(false)
    }

    @Throws(IOException::class)
    protected fun prepareHeaderLocations(
        sr: SecureRandom, io: RandomAccessIO, layout: VolumeLayout
    ) {
        writeRandomData(sr, io, 0, layout.getEncryptedDataOffset())
    }

    @Throws(IOException::class)
    protected fun writeRandomData(sr: SecureRandom, io: RandomAccessIO, start: Long, length: Long) {
        io.seek(start)
        val tbuf = ByteArray(8 * 512)
        var i = 0
        while (i < length) {
            sr.nextBytes(tbuf)
            io.write(tbuf, 0, tbuf.size)
            i += tbuf.size
        }
    }

    companion object {
        val formatName: String = "LUKS"
            get() = Companion.field
        val maxPasswordLength: Int = 8192 * 1000
            get() = Companion.field
    }
}
