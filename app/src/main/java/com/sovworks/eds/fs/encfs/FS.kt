package com.sovworks.eds.fs.encfs

import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.android.helpers.ProgressReporter
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.kdf.HMACSHA1KDF
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.exceptions.WrongPasswordException
import com.sovworks.eds.fs.encfs.codecs.data.AESDataCodecInfo
import com.sovworks.eds.fs.encfs.codecs.name.BlockCSNameCodecInfo
import com.sovworks.eds.fs.encfs.codecs.name.BlockNameCodecInfo
import com.sovworks.eds.fs.encfs.codecs.name.NullNameCodecInfo
import com.sovworks.eds.fs.encfs.codecs.name.StreamNameCodecInfo
import com.sovworks.eds.fs.util.FileSystemWrapper
import com.sovworks.eds.fs.util.StringPathUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.security.DigestException
import java.security.SecureRandom
import java.util.Arrays

class FS : FileSystemWrapper {
    constructor(rootPath: com.sovworks.eds.fs.Path, config: Config, password: ByteArray) : super(
        rootPath.fileSystem
    ) {
        this.config = config
        encFSRootPath = rootPath
        _encryptionKey = ByteArray(config.dataCodecInfo.fileEncDec.getKeySize())
        val sr = SecureRandom()
        sr.nextBytes(_encryptionKey)
        encryptVolumeKeyAndWriteConfig(password)
        _rootPath = RootPath()
    }

    @JvmOverloads
    constructor(
        rootPath: com.sovworks.eds.fs.Path,
        password: ByteArray,
        progressReporter: ContainerOpeningProgressReporter? = null
    ) : super(rootPath.fileSystem) {
        setOpeningProgressReporter(progressReporter)
        config = readConfig(rootPath)
        encFSRootPath = rootPath
        var derivedKey: ByteArray? = null
        try {
            if (_progressReporter != null) {
                _progressReporter!!.setCurrentEncryptionAlgName(config.dataCodecInfo.name)
                _progressReporter!!.setCurrentKDFName("SHA1")
            }
            derivedKey = deriveKey(password)
            _encryptionKey = decryptVolumeKey(derivedKey)
            _rootPath = RootPath()
        } catch (e: DigestException) {
            throw ApplicationException("Failed deriving the key", e)
        } finally {
            if (derivedKey != null) Arrays.fill(derivedKey, 0.toByte())
        }
    }

    fun setOpeningProgressReporter(r: ContainerOpeningProgressReporter?) {
        _progressReporter = r
    }


    @Throws(ApplicationException::class, IOException::class)
    fun encryptVolumeKeyAndWriteConfig(password: ByteArray) {
        val sr = SecureRandom()
        val salt = ByteArray(20)
        sr.nextBytes(salt)
        config.salt = salt
        var derivedKey: ByteArray? = null
        try {
            derivedKey = deriveKey(password)
            config.encryptedVolumeKey = encryptVolumeKey(derivedKey)
        } catch (e: DigestException) {
            throw ApplicationException("Failed deriving the key", e)
        } finally {
            if (derivedKey != null) Arrays.fill(derivedKey, 0.toByte())
        }
        config.write(encFSRootPath)
    }

    override fun getRootPath(): Path {
        return _rootPath
    }

    @Synchronized
    @Throws(IOException::class)
    override fun getPath(pathString: String): Path {
        return getPathFromRealPath(base.getPath(pathString))!!
    }

    @Throws(IOException::class)
    override fun close(force: Boolean) {
        if (_encryptionKey != null) {
            Arrays.fill(_encryptionKey, 0.toByte())
            _encryptionKey = null
        }
        /*
        try
        {
            super.close(force);
        }
        finally
        {
            if(_encryptionKey!=null)
            {
                Arrays.fill(_encryptionKey, (byte) 0);
                _encryptionKey = null;
            }
        }*/
    }

    @Synchronized
    @Throws(IOException::class)
    fun getPathFromRealPath(realPath: com.sovworks.eds.fs.Path?): Path? {
        if (realPath == null) return null
        if (realPath == encFSRootPath) return _rootPath
        var p = getCachedPath(realPath)
        if (p == null) {
            p = Path(
                this,
                realPath,
                config.nameCodecInfo,
                _encryptionKey
            )
            _cache[realPath] = p
        }
        return p
    }

    @Throws(IOException::class)
    fun getCachedPath(realPath: com.sovworks.eds.fs.Path): Path? {
        return _cache[realPath]
    }

    private inner class RootPath : Path(
        this@FS,
        encFSRootPath, config.nameCodecInfo, _encryptionKey
    ) {
        init {
            decodedPath = StringPathUtil()
            encodedPath = StringPathUtil()
        }

        @Throws(IOException::class)
        override fun isRootDirectory(): Boolean {
            return true
        }

        @get:Synchronized
        override val chainedIV: ByteArray?
            get() = ByteArray(namingCodecInfo.encDec.ivSize)

        @Throws(IOException::class)
        override fun getParentPath(): Path? {
            return null
        }
    }

    val encFSRootPath: com.sovworks.eds.fs.Path
    private val _cache: MutableMap<com.sovworks.eds.fs.Path, Path> = HashMap()
    private val _rootPath: RootPath
    private var _encryptionKey: ByteArray?
    var config: Config
        private set
    private var _progressReporter: ContainerOpeningProgressReporter? = null

    @Throws(EncryptionEngineException::class, DigestException::class)
    private fun deriveKey(password: ByteArray): ByteArray {
        return deriveKey(
            password,
            config.salt,
            config.kdfIterations,
            config.keySize,
            config.dataCodecInfo.fileEncDec.getIVSize(),
            _progressReporter
        )
    }

    @Throws(EncryptionEngineException::class, WrongPasswordException::class)
    private fun decryptVolumeKey(derivedKey: ByteArray?): ByteArray {
        val encryptedVolumeKey = config.encryptedVolumeKey
        var checksum = 0
        for (i in 0..<KEY_CHECKSUM_BYTES) checksum =
            (checksum shl 8) or (encryptedVolumeKey!![i].toInt() and 0xFF)
        val volumeKey =
            Arrays.copyOfRange(encryptedVolumeKey, KEY_CHECKSUM_BYTES, encryptedVolumeKey!!.size)
        val ee = config.dataCodecInfo.streamEncDec
        try {
            ee.setKey(derivedKey)
            ee!!.init()
            ee.setIV(
                ByteBuffer.allocate(ee.getIVSize()).putLong(checksum.toLong() and 0xFFFFFFFFL)
                    .array()
            )
            ee.decrypt(volumeKey, 0, volumeKey.size)
        } finally {
            ee!!.close()
        }

        val cc = config.dataCodecInfo.checksumCalculator
        try {
            cc!!.init(derivedKey!!)
            val checksum2 = cc.calc32(volumeKey, 0, volumeKey.size)
            if (checksum2 != checksum) throw WrongPasswordException()
        } finally {
            cc!!.close()
        }
        return volumeKey
    }

    @Throws(EncryptionEngineException::class)
    private fun encryptVolumeKey(derivedKey: ByteArray?): ByteArray {
        val dataCodec = config.dataCodecInfo
        val volumeKey = _encryptionKey
        var checksum: Int
        val cc = dataCodec.checksumCalculator
        try {
            cc!!.init(derivedKey!!)
            checksum = cc.calc32(volumeKey, 0, volumeKey!!.size)
        } finally {
            cc!!.close()
        }

        val res = ByteArray(volumeKey!!.size + KEY_CHECKSUM_BYTES)
        System.arraycopy(volumeKey, 0, res, KEY_CHECKSUM_BYTES, volumeKey.size)

        val ee = dataCodec.streamEncDec
        try {
            ee.setKey(derivedKey)
            ee!!.init()
            ee.setIV(
                ByteBuffer.allocate(ee.getIVSize()).putLong(checksum.toLong() and 0xFFFFFFFFL)
                    .array()
            )
            ee.encrypt(res, KEY_CHECKSUM_BYTES, volumeKey.size)
        } finally {
            ee!!.close()
        }

        for (i in 1..KEY_CHECKSUM_BYTES) {
            res[KEY_CHECKSUM_BYTES - i] = checksum.toByte()
            checksum = checksum shr 8
        }
        return res
    }

    @Throws(IOException::class, ApplicationException::class)
    private fun readConfig(rootFolderPath: com.sovworks.eds.fs.Path): Config {
        val cfg = Config()
        cfg.read(rootFolderPath)
        return cfg
    }

    companion object {
        val supportedDataCodecs: Iterable<DataCodecInfo>
            get() = Arrays.asList(*_supportedDataCodecs)

        val supportedNameCodecs: Iterable<NameCodecInfo>
            get() = Arrays.asList(*_supportedNameCodecs)

        const val KEY_CHECKSUM_BYTES: Int = 4

        @Throws(EncryptionEngineException::class, DigestException::class)
        fun deriveKey(
            password: ByteArray,
            salt: ByteArray,
            numIterations: Int,
            keySize: Int,
            ivSize: Int,
            pr: ProgressReporter?
        ): ByteArray {
            val kdf = HMACSHA1KDF()
            kdf.setProgressReporter(pr)
            return kdf.deriveKey(
                password,
                salt,
                numIterations,
                keySize + ivSize
            )
        }

        private val _supportedDataCodecs = arrayOf<DataCodecInfo>(AESDataCodecInfo())
        private val _supportedNameCodecs = arrayOf<NameCodecInfo>(
            BlockNameCodecInfo(),
            BlockCSNameCodecInfo(),
            StreamNameCodecInfo(),
            NullNameCodecInfo()
        )
    }
}
