package com.sovworks.eds.container

import android.annotation.SuppressLint
import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.kdf.HashBasedPBKDF2
import com.sovworks.eds.crypto.modes.CBC
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.FileSystemInfo
import com.sovworks.eds.fs.RandomAccessIO
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.Locale
import java.util.Random
import java.util.concurrent.CancellationException


abstract class VolumeLayoutBase : VolumeLayout {
    override fun initNew() {
        checkNotNull(_encEngine) { "Encryption engine is not set" }
        if (_masterKey != null) Arrays.fill(_masterKey, 0.toByte())
        _masterKey = ByteArray(_encEngine!!.keySize)
        random.nextBytes(_masterKey)
    }

    @Throws(IOException::class, ApplicationException::class)
    override fun readHeader(input: RandomAccessIO?): Boolean {
        checkNotNull(_password) { "Password is not set" }
        return false
    }


    @Throws(ApplicationException::class, IOException::class)
    override fun formatFS(output: RandomAccessIO?, fsInfo: FileSystemInfo) {
        fsInfo.makeNewFileSystem(output)
    }


    override fun setPassword(password: ByteArray?) {
        if (_password != null) Arrays.fill(_password, 0.toByte())
        _password = password
    }

    override fun setNumKDFIterations(num: Int) {
    }

    override fun getEngine(): FileEncryptionEngine? {
        return _encEngine
    }

    override fun setEngine(engine: FileEncryptionEngine?) {
        if (_encEngine != null) _encEngine!!.close()
        _encEngine = engine
        _invertIV =
            _encEngine != null && CBC.NAME.equals(_encEngine!!.cipherModeName, ignoreCase = true)
    }

    @Throws(IOException::class)
    override fun close() {
        if (_masterKey != null) {
            Arrays.fill(_masterKey, 0.toByte())
            _masterKey = null
        }
        if (_password != null) {
            Arrays.fill(_password, 0.toByte())
            _password = null
        }
        engine = null
    }

    override fun setEncryptionEngineIV(eng: FileEncryptionEngine, decryptedVolumeOffset: Long) {
        val block = (decryptedVolumeOffset + encryptedDataOffset) / eng.fileBlockSize
        eng.iv = getIVFromBlockIndex(block)
    }

    override val supportedEncryptionEngines: List<FileEncryptionEngine?>?
        get() = emptyList<FileEncryptionEngine>()

    override val supportedHashFuncs: List<MessageDigest?>?
        get() = emptyList<MessageDigest>()

    override fun setOpeningProgressReporter(reporter: ContainerOpeningProgressReporter?) {
        _openingProgressReporter = reporter
    }

    fun findCipher(cipherName: String, modeName: String): FileEncryptionEngine? {
        return findCipher(supportedEncryptionEngines, cipherName, modeName) as FileEncryptionEngine?
    }

    open fun findHashFunc(name: String): MessageDigest? {
        return findHashFunc(supportedHashFuncs, name)
    }

    @JvmField
	protected var _encEngine: FileEncryptionEngine? = null
    override var hashFunc: MessageDigest? = null
    @JvmField
	protected var _masterKey: ByteArray?
    @JvmField
	protected var _password: ByteArray?
    @JvmField
	protected var _openingProgressReporter: ContainerOpeningProgressReporter? = null

    @get:Synchronized
    @get:SuppressLint("TrulyRandom")
    val random: Random
        get() {
            if (_sr == null) _sr = SecureRandom()
            return _sr!!
        }

    private var _sr: Random? = null
    private var _invertIV = false

    @Throws(ApplicationException::class)
    protected fun deriveKey(
        keySize: Int,
        hashFunc: MessageDigest?,
        password: ByteArray?,
        salt: ByteArray?,
        numIterations: Int
    ): ByteArray {
        val kdf = HashBasedPBKDF2(hashFunc)
        kdf.setProgressReporter(_openingProgressReporter)
        try {
            return kdf.deriveKey(password, salt, numIterations, keySize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApplicationException("Failed deriving key", e)
        }
    }

    protected fun checkWriteHeaderPrereqs() {
        check(!(_encEngine == null || hashFunc == null || _password == null || _masterKey == null)) { "Header data is not initialized" }
    }

    protected fun checkReadHeaderPrereqs() {
        checkNotNull(_password) { "The password is not set" }
    }

    protected fun getIVFromBlockIndex(blockIndex: Long): ByteArray {
        return ByteBuffer.allocate
        (_encEngine!!.ivSize).order
        (if (_invertIV) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).putLong
        (blockIndex).array
        ()
    }

    companion object {
        fun findCipher(
            engines: Iterable<EncryptionEngine>,
            cipherName: String,
            modeName: String
        ): EncryptionEngine? {
            for (eng in engines) if (cipherName.equals(
                    eng.cipherName,
                    ignoreCase = true
                ) && modeName.equals(eng.cipherModeName, ignoreCase = true)
            ) return eng
            return null
        }

        fun findHashFunc(engines: Iterable<MessageDigest>, name: String): MessageDigest? {
            var name = name
            name = name.lowercase(Locale.getDefault())
            for (eng in engines) {
                val algName = eng.algorithm.lowercase(Locale.getDefault())
                if (algName.contains(name)) return eng
            }
            return null
        }

        @JvmStatic
		fun getEncEngineName(ee: EncryptionEngine): String {
            return String.format("%s-%s", ee.cipherName, ee.cipherModeName)
        }

        fun findEncEngineByName(
            engines: Iterable<EncryptionEngine>,
            name: String?
        ): EncryptionEngine? {
            for (eng in engines) if (getEncEngineName(eng).equals(
                    name,
                    ignoreCase = true
                )
            ) return eng
            return null
        }

        protected const val SECTOR_SIZE: Int = 512
    }
}
