package com.sovworks.eds.fs.encfs

import android.os.ParcelFileDescriptor
import com.sovworks.eds.container.EncryptedFileLayout
import com.sovworks.eds.crypto.EncryptedFile
import com.sovworks.eds.crypto.EncryptedInputStream
import com.sovworks.eds.crypto.EncryptedOutputStream
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.MACFile
import com.sovworks.eds.crypto.MACFile.Companion.calcVirtPosition
import com.sovworks.eds.crypto.MACInputStream
import com.sovworks.eds.crypto.MACOutputStream
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.File.AccessMode
import com.sovworks.eds.fs.File.AccessMode.Read
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.fs.File.AccessMode.ReadWriteTruncate
import com.sovworks.eds.fs.File.AccessMode.Write
import com.sovworks.eds.fs.File.AccessMode.WriteAppend
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.encfs.ciphers.BlockAndStreamCipher
import com.sovworks.eds.fs.util.FileWrapper
import com.sovworks.eds.fs.util.RandomAccessInputStream
import com.sovworks.eds.fs.util.RandomAccessOutputStream
import com.sovworks.eds.fs.util.Util
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

class File(
    path: Path?,
    realFile: File?,
    private val _encryptionInfo: DataCodecInfo?,
    private val _encryptionKey: ByteArray,
    externalIV: ByteArray?,
    private val _fileBlockSize: Int,
    private val _enableIVHeader: Boolean,
    private val _allowEmptyParts: Boolean,
    private val _macBytes: Int,
    private val _randBytes: Int,
    private val _forceDecode: Boolean
) : FileWrapper(path, realFile) {
    override fun getPath(): Path {
        return super.path as Path
    }

    @Throws(IOException::class)
    override fun getName(): String {
        return path.decodedPath.fileName
    }

    override fun getFileDescriptor(accessMode: AccessMode): ParcelFileDescriptor? {
        return null
    }

    @Throws(IOException::class)
    override fun getRandomAccessIO(accessMode: AccessMode): RandomAccessIO {
        val base = super.getRandomAccessIO(accessMode)
        try {
            when (accessMode) {
                Read -> {
                    if (_enableIVHeader && getBase().size < Header.SIZE) return base
                    return initEncryptedFile(
                        base,
                        initFileLayout(if (_enableIVHeader) RandomAccessInputStream(base) else null)
                    )
                }

                ReadWrite -> {
                    if (path.exists() && getBase().size >= Header.SIZE) return initEncryptedFile(
                        base,
                        initFileLayout(if (_enableIVHeader) RandomAccessInputStream(base) else null)
                    )
                    return initEncryptedFile(
                        base,
                        initFileLayout((if (_enableIVHeader) RandomAccessOutputStream(base) else null)!!)
                    )
                }

                ReadWriteTruncate, Write -> return initEncryptedFile(
                    base,
                    initFileLayout((if (_enableIVHeader) RandomAccessOutputStream(base) else null)!!)
                )

                WriteAppend -> {
                    require(!_enableIVHeader) { "Can't write header in WriteAppend mode" }
                    return initEncryptedFile(
                        base,
                        initFileLayout((null as RandomAccessOutputStream?)!!)
                    )
                }

                else -> throw IllegalArgumentException("Wrong access mode")
            }
        } catch (e: Throwable) {
            base.close()
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        val base = super.getOutputStream()
        try {
            val fl = initFileLayout(base)
            val out = EncryptedOutputStream(base, fl)
            out.setAllowEmptyParts(_allowEmptyParts)
            if (_macBytes > 0 || _randBytes > 0) {
                val mac = _encryptionInfo.getChecksumCalculator()
                mac!!.init(_encryptionKey)
                return MACOutputStream(
                    out,
                    mac!!,
                    _fileBlockSize,
                    _macBytes,
                    _randBytes
                )
            }
            return out
        } catch (e: Throwable) {
            base.close()
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        val base = super.getInputStream()
        try {
            val fl = initFileLayout(base)
            val inp = EncryptedInputStream(base, fl)
            inp.setAllowEmptyParts(_allowEmptyParts)
            if (_macBytes > 0 || _randBytes > 0) {
                val mac = _encryptionInfo.getChecksumCalculator()
                mac!!.init(_encryptionKey)
                val minp = MACInputStream(
                    inp,
                    mac!!,
                    _fileBlockSize,
                    _macBytes,
                    _randBytes,
                    _forceDecode
                )
                minp.setAllowEmptyParts(_allowEmptyParts)
                return minp
            }
            return inp
        } catch (e: Throwable) {
            base.close()
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun getSize(): Long {
        var size = super.getSize()
        if (_enableIVHeader && size >= Header.SIZE) size -= Header.SIZE.toLong()
        if (_randBytes > 0 || _macBytes > 0) size =
            calcVirtPosition(size, _fileBlockSize - _randBytes - _macBytes, _randBytes + _macBytes)

        return size
    }

    @Throws(IOException::class)
    override fun rename(newName: String) {
        val newEncodedPath = path.parentPath!!.calcCombinedEncodedParts(newName)
        if (_externalIV != null || path.namingCodecInfo.useChainedNamingIV()) {
            val newFile = path.parentPath!!.directory.createFile(newName)
            val out = newFile.outputStream
            try {
                copyToOutputStream(out, 0, 0, null)
            } finally {
                out.close()
            }
            delete()
            setPath(newFile.path)
        } else super.rename(newEncodedPath!!.fileName)
    }

    @Throws(IOException::class)
    override fun moveTo(newParent: Directory) {
        if (_externalIV != null || path.namingCodecInfo.useChainedNamingIV()) {
            val newFile = newParent.createFile(name)
            val out = newFile.outputStream
            try {
                copyToOutputStream(out, 0, 0, null)
            } finally {
                out.close()
            }
            delete()
            setPath(newFile.path)
        } else super.moveTo(newParent)
    }

    @Throws(IOException::class)
    override fun getPathFromBasePath(basePath: com.sovworks.eds.fs.Path): com.sovworks.eds.fs.Path {
        return path.fileSystem.getPathFromRealPath(basePath)!!
    }

    @Throws(FileNotFoundException::class)
    protected fun initEncryptedFile(base: RandomAccessIO, fl: EncryptedFileLayout): RandomAccessIO {
        val ef: EncryptedFile = object : EncryptedFile(base, fl, 1) {
            @Synchronized
            @Throws(IOException::class)
            override fun close(closeBase: Boolean) {
                try {
                    super.close(closeBase)
                } finally {
                    _layout.close()
                }
            }
        }
        ef.setAllowSkip(_allowEmptyParts)
        if (_macBytes > 0 || _randBytes > 0) {
            val mac = _encryptionInfo.getChecksumCalculator()
            mac!!.init(_encryptionKey)
            val mf = MACFile(
                ef,
                mac!!,
                _fileBlockSize,
                _macBytes,
                _randBytes,
                _forceDecode
            )
            mf.setAllowSkip(_allowEmptyParts)
            return mf
        }
        return ef
    }


    class Header {
        fun load(data: ByteArray) {
            iV = data
        }

        fun save(): ByteArray {
            return iV.clone()
        }

        fun initNew() {
            iV = ByteArray(8)
            val sr = SecureRandom()
            sr.nextBytes(iV)
        }

        var iV: ByteArray

        companion object {
            const val SIZE: Int = 8
        }
    }

    private class FileLayout(
        override val engine: FileEncryptionEngine,
        private val _encryptedDataOffset: Int,
        private val _fileIV: ByteArray?
    ) :
        EncryptedFileLayout {
        override val encryptedDataOffset: Long
            get() = _encryptedDataOffset.toLong()

        override fun getEncryptedDataSize(fileSize: Long): Long {
            return fileSize - _encryptedDataOffset
        }

        override fun setEncryptionEngineIV(eng: FileEncryptionEngine, decryptedVolumeOffset: Long) {
            val iv = ByteArray(eng.getIVSize())
            ByteBuffer.wrap(iv).order(ByteOrder.BIG_ENDIAN)
                .putLong(decryptedVolumeOffset / engine.getFileBlockSize())
            if (_fileIV != null) for (i in _fileIV.indices) iv[i] =
                (iv[i].toInt() xor _fileIV[i].toInt()).toByte()
            eng.setIV(iv)
        }

        @Throws(IOException::class)
        override fun close() {
            engine.close()
        }
    }

    private val _externalIV = externalIV?.clone()

    @Throws(IOException::class, ApplicationException::class)
    private fun initFileLayout(out: OutputStream): FileLayout {
        val h: Header?
        if (_enableIVHeader) {
            h = initNewHeader()
            writeHeader(out, h)
        } else h = null
        return initFileLayout(h)
    }

    @Throws(IOException::class, EncryptionEngineException::class)
    private fun initFileLayout(inp: InputStream?): FileLayout {
        return initFileLayout(if (_enableIVHeader) readHeader(inp) else null)
    }

    @Throws(EncryptionEngineException::class)
    private fun initFileLayout(h: Header?): FileLayout {
        val ee: FileEncryptionEngine = BlockAndStreamCipher(
            _encryptionInfo.getFileEncDec(),
            _encryptionInfo.getStreamEncDec()
        )
        ee.setKey(_encryptionKey)
        ee.init()
        return if (h == null) FileLayout(ee, 0, null) else FileLayout(
            ee, Header.SIZE,
            h.iV
        )
    }

    private fun initNewHeader(): Header {
        val header = Header()
        header.initNew()
        return header
    }

    @Throws(IOException::class)
    fun readHeader(input: InputStream?): Header {
        val buf = ByteArray(Header.SIZE)
        if (Util.readBytes(input, buf) != Header.SIZE) throw IOException("Failed reading header")
        val ee = _encryptionInfo.getStreamEncDec()
        try {
            ee.setKey(_encryptionKey)
            ee!!.init()
            ee.setIV(_externalIV)
            ee!!.decrypt(buf, 0, buf.size)
        } catch (e: EncryptionEngineException) {
            throw IOException(e)
        } finally {
            ee!!.close()
        }
        val header = Header()
        header.load(buf)
        return header
    }

    @Throws(IOException::class)
    fun writeHeader(output: OutputStream, header: Header) {
        val data = header.save()
        val ee = _encryptionInfo.getStreamEncDec()
        try {
            ee.setKey(_encryptionKey)
            ee!!.init()
            ee.setIV(_externalIV)
            ee!!.encrypt(data, 0, data.size)
        } catch (e: EncryptionEngineException) {
            throw IOException(e)
        } finally {
            ee!!.close()
        }
        output.write(data)
    }
}
