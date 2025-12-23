package com.sovworks.eds.truecrypt

import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.VolumeLayout
import com.sovworks.eds.crypto.EncryptedFileWithCache
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.security.SecureRandom
import java.util.Random

open class FormatInfo : ContainerFormatInfo {
    override val formatName: String?
        get() = FORMAT_NAME

    override val volumeLayout: VolumeLayout
        get() = StdLayout()

    override fun hasHiddenContainerSupport(): Boolean {
        return false
    }

    override fun hasKeyfilesSupport(): Boolean {
        return false
    }

    override fun hasCustomKDFIterationsSupport(): Boolean {
        return false
    }

    override val maxPasswordLength: Int
        get() = 64

    override val hiddenVolumeLayout: VolumeLayout?
        get() = null

    override val openingPriority: Int
        get() = 3

    @Throws(IOException::class, ApplicationException::class)
    override fun formatContainer(
        io: RandomAccessIO,
        layout: VolumeLayout?,
        fsType: FileSystemInfo
    ) {
        val tcLayout = (layout as StdLayout?)
        io.seek(tcLayout!!.getEncryptedDataSize(io.length()) + tcLayout.encryptedDataOffset - 1)
        io.write(0)
        prepareHeaderLocations(random, io, tcLayout)
        tcLayout.writeHeader(io)
        val et = getEncryptedRandomAccessIO(io, tcLayout)
        tcLayout.formatFS(et, fsType)
        et.close()
    }

    override fun toString(): String {
        return formatName!!
    }

    protected val random: Random
        get() = SecureRandom()

    @Throws(IOException::class)
    protected fun getEncryptedRandomAccessIO(
        base: RandomAccessIO,
        layout: VolumeLayout
    ): RandomAccessIO {
        return object : EncryptedFileWithCache(base, layout) {
            @Throws(IOException::class)
            override fun close() {
                close(false)
            }
        }
    }

    @Throws(IOException::class)
    protected fun prepareHeaderLocations(sr: Random, io: RandomAccessIO, layout: StdLayout) {
        writeRandomData(sr, io, layout.headerOffset, getReservedHeadersSpace(layout).toLong())
    }

    @Throws(IOException::class)
    protected fun writeRandomData(sr: Random, io: RandomAccessIO, start: Long, length: Long) {
        io.seek(start)
        val tbuf = ByteArray(8 * 512)
        var i = 0
        while (i < length) {
            sr.nextBytes(tbuf)
            io.write(tbuf, 0, tbuf.size)
            i += tbuf.size
        }
    }

    protected fun getReservedHeadersSpace(layout: StdLayout): Int {
        return 2 * layout.getHeaderSize()
    }

    companion object {
        const val FORMAT_NAME: String = "TrueCrypt"
    }
}
