package com.sovworks.eds.container

import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.security.MessageDigest


interface VolumeLayout : EncryptedFileLayout {
    fun initNew()

    @Throws(IOException::class, ApplicationException::class)
    fun readHeader(input: RandomAccessIO?): Boolean

    @Throws(IOException::class, ApplicationException::class)
    fun writeHeader(output: RandomAccessIO?)

    @Throws(ApplicationException::class, IOException::class)
    fun formatFS(output: RandomAccessIO?, fsInfo: FileSystemInfo)

    fun setEngine(enc: FileEncryptionEngine?)

    var hashFunc: MessageDigest?

    fun setPassword(password: ByteArray?)

    fun setNumKDFIterations(num: Int)

    @JvmField
    val supportedEncryptionEngines: List<FileEncryptionEngine>

    @JvmField
    val supportedHashFuncs: List<MessageDigest>

    fun setOpeningProgressReporter(reporter: ContainerOpeningProgressReporter?)
}