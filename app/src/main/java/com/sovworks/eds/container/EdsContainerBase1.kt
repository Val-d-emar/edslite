package com.sovworks.eds.container

import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.crypto.EncryptedFileWithCache
import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.LocalEncryptedFileXTS
import com.sovworks.eds.crypto.modes.XTS
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.exceptions.WrongFileFormatException
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.fs.FileSystem
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.exfat.ExFat
import com.sovworks.eds.fs.fat.FatFS
import com.sovworks.eds.fs.std.StdFs
import com.sovworks.eds.fs.std.StdFsPath
import java.io.Closeable
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections


abstract class EdsContainerBase(
    val pathToContainer: Path,
    var containerFormat: ContainerFormatInfo?,
    var volumeLayout: VolumeLayout?
) :
    Closeable {
    @Synchronized
    @Throws(IOException::class, ApplicationException::class)
    fun open(password: ByteArray?) {
        Logger.Companion.debug("Opening container at " + pathToContainer.pathString)
        val t = openFile()
        try {
            if (containerFormat == null) {
                if (tryLayout(t, password, false) || tryLayout(t, password, true)) return
            } else {
                if (tryLayout(containerFormat!!, t, password, false) || tryLayout(
                        containerFormat!!,
                        t,
                        password,
                        true
                    )
                ) return
            }
        } finally {
            t.close()
        }

        throw WrongFileFormatException()
    }

    @get:Throws(IOException::class, UserException::class)
    val encryptedFS: FileSystem?
        get() = getEncryptedFS(false)

    @Throws(IOException::class)
    fun initEncryptedFile(isReadOnly: Boolean): RandomAccessIO {
        if (volumeLayout == null) throw IOException("The container is closed")
        val enc: EncryptionEngine? = volumeLayout.getEngine()
        return if (allowLocalXTS())
            LocalEncryptedFileXTS(
                pathToContainer.pathString,
                isReadOnly,
                volumeLayout.getEncryptedDataOffset(),
                enc as XTS?
            )
        else
            EncryptedFileWithCache(
                pathToContainer, if (isReadOnly) Read else ReadWrite,
                volumeLayout
            )
    }

    @Synchronized
    @Throws(IOException::class, UserException::class)
    fun getEncryptedFS(isReadOnly: Boolean): FileSystem? {
        if (_fileSystem == null) {
            val io = getEncryptedFile(isReadOnly)
            _fileSystem = loadFileSystem(io, isReadOnly)
        }
        return _fileSystem
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        if (_fileSystem != null) {
            _fileSystem!!.close(true)
            _fileSystem = null
        }

        if (_encryptedFile != null) {
            _encryptedFile!!.close()
            _encryptedFile = null
        }

        if (volumeLayout != null) {
            volumeLayout!!.close()
            volumeLayout = null
        }
    }

    fun setEncryptionEngineHint(eng: FileEncryptionEngine?) {
        _encryptionEngine = eng
    }

    fun setHashFuncHint(hf: MessageDigest?) {
        _messageDigest = hf
    }

    fun setNumKDFIterations(num: Int) {
        _numKDFIterations = num
    }

    fun setProgressReporter(r: ContainerOpeningProgressReporter?) {
        _progressReporter = r
    }

    @Throws(IOException::class)
    fun getEncryptedFile(isReadOnly: Boolean): RandomAccessIO {
        if (_encryptedFile == null) _encryptedFile = initEncryptedFile(isReadOnly)
        return _encryptedFile!!
    }

    protected var _fileSystem: FileSystem? = null
    protected var _encryptedFile: RandomAccessIO? = null
    protected var _numKDFIterations: Int = 0
    protected var _progressReporter: ContainerOpeningProgressReporter? = null
    protected var _encryptionEngine: FileEncryptionEngine? = null

    protected var _messageDigest: MessageDigest? = null

    protected abstract val formats: List<ContainerFormatInfo>

    @Throws(IOException::class)
    protected fun openFile(): RandomAccessIO {
        return pathToContainer.file.getRandomAccessIO(Read)
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun tryLayout(
        containerFile: RandomAccessIO?,
        password: ByteArray?,
        isHidden: Boolean
    ): Boolean {
        val cfs = formats
        if (cfs.size > 1) Collections.sort(
            cfs
        ) { lhs, rhs ->
            lhs.openingPriority.compareTo(
                rhs.openingPriority
            )
        }

        for (cf in cfs) {
            //Don't try too slow container formats
            if (cf.openingPriority < 0) continue
            if (tryLayout(cf, containerFile, password, isHidden)) return true
        }
        return false
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun tryLayout(
        cf: ContainerFormatInfo,
        containerFile: RandomAccessIO?,
        password: ByteArray?,
        isHidden: Boolean
    ): Boolean {
        if (isHidden && !cf.hasHiddenContainerSupport()) return false
        Logger.Companion.debug(
            String.format(
                "Trying %s container format%s",
                cf.formatName,
                if (isHidden) " (hidden)" else ""
            )
        )
        if (_progressReporter != null) {
            _progressReporter!!.setContainerFormatName(cf.formatName)
            _progressReporter!!.setIsHidden(isHidden)
        }
        val vl = if (isHidden) cf.hiddenVolumeLayout else cf.volumeLayout
        vl!!.setOpeningProgressReporter(_progressReporter)
        if (_encryptionEngine != null) vl.engine = _encryptionEngine
        if (_messageDigest != null) vl.hashFunc = _messageDigest

        vl.setPassword(cutPassword(password, cf.maxPasswordLength))
        if (cf.hasCustomKDFIterationsSupport() && _numKDFIterations > 0) vl.setNumKDFIterations(
            _numKDFIterations
        )
        if (vl.readHeader(containerFile)) {
            containerFormat = cf
            volumeLayout = vl
            return true
        } else if (isHidden && (_encryptionEngine != null || _messageDigest != null)) {
            vl.engine = null
            vl.hashFunc = null
            if (vl.readHeader(containerFile)) {
                containerFormat = cf
                volumeLayout = vl
                return true
            }
        }
        vl.close()
        return false
    }

    protected fun getLayouts(isHidden: Boolean): Iterable<VolumeLayout> {
        val vll: MutableList<VolumeLayout> = ArrayList()
        for (cf in formats) {
            val vl = if (isHidden) cf.hiddenVolumeLayout else cf.volumeLayout
            if (vl != null) vll.add(vl)
        }
        return vll
    }

    protected fun allowLocalXTS(): Boolean {
        return pathToContainer is StdFsPath
                && volumeLayout.getEngine() is XTS
                && pathToContainer.getFileSystem() is StdFs
                && (pathToContainer.getFileSystem() as StdFs).rootDir.isEmpty
    }

    companion object {
        fun findFormatByName(
            supportedFormats: List<ContainerFormatInfo>,
            name: String?
        ): ContainerFormatInfo? {
            if (name != null) for (cfi in supportedFormats) {
                if (cfi.formatName.equals(name, ignoreCase = true)) return cfi
            }
            return null
        }

        fun cutPassword(pass: ByteArray?, maxLength: Int): ByteArray? {
            var pass = pass
            if (pass != null) {
                if (maxLength > 0 && pass.size > maxLength) {
                    val tmp: ByteArray = pass
                    pass = ByteArray(maxLength)
                    System.arraycopy(tmp, 0, pass, 0, maxLength)
                } else pass = pass.clone()
            }
            return pass
        }

        @Throws(IOException::class, UserException::class)
        fun loadFileSystem(io: RandomAccessIO, isReadOnly: Boolean): FileSystem {
            if (ExFat.isExFATImage(io)) {
                if (ExFat.isModuleInstalled()) return ExFat(io, isReadOnly)
                if (ExFat.isModuleIncompatible()) throw UserException(
                    "Please update the exFAT module.",
                    R.string.update_exfat_module
                )
                throw UserException(
                    "Please install the exFAT module",
                    R.string.exfat_module_required
                )
            }

            val fs = FatFS.getFat(io)
            if (isReadOnly) fs.setReadOnlyMode(true)
            return fs
        }


        const val COMPATIBLE_TC_VERSION: Short = 0x700
    }
}



