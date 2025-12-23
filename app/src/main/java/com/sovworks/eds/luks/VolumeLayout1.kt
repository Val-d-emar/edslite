package com.sovworks.eds.luks

import android.annotation.SuppressLint
import com.sovworks.eds.android.Logger.Companion.debug
import com.sovworks.eds.android.helpers.ContainerOpeningProgressReporter
import com.sovworks.eds.container.VolumeLayoutBase
import com.sovworks.eds.crypto.AF
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.engines.AESCBC
import com.sovworks.eds.crypto.engines.AESXTS
import com.sovworks.eds.crypto.engines.GOSTCBC
import com.sovworks.eds.crypto.engines.GOSTXTS
import com.sovworks.eds.crypto.engines.SerpentCBC
import com.sovworks.eds.crypto.engines.SerpentXTS
import com.sovworks.eds.crypto.engines.TwofishCBC
import com.sovworks.eds.crypto.engines.TwofishXTS
import com.sovworks.eds.crypto.hash.RIPEMD160
import com.sovworks.eds.crypto.hash.Whirlpool
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.exceptions.UnsupportedContainerTypeException
import com.sovworks.eds.exceptions.WrongPasswordException
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.util.Util.readBytes
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.DigestException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import kotlin.math.min

class VolumeLayout : VolumeLayoutBase() {
    override fun initNew() {
        if (_encEngine == null) setEngine(AESXTS())
        super.initNew()
        if (hashFunc == null) try {
            hashFunc = MessageDigest.getInstance("SHA1")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed getting sha1 instance")
        }
        _activeKeyslotIndex = 0
        if (_uuid == null) _uuid = UUID.randomUUID()
        _keySlots.clear()
        for (i in 0..<NUM_KEY_SLOTS) {
            val ks = KeySlot()
            ks.init(i)
            _keySlots.add(ks)
        }

        if (_payloadOffsetSector == 0 && !_isDetachedHeader) {
            val ks = KeySlot()
            ks.init(NUM_KEY_SLOTS)
            _payloadOffsetSector =
                sizeRoundUp(ks.keyMaterialOffsetSector, DEFAULT_DISK_ALIGNMENT / SECTOR_SIZE)
        }
    }

    @Throws(IOException::class, ApplicationException::class)
    override fun readHeader(input: RandomAccessIO): Boolean {
        checkReadHeaderPrereqs()

        val header = ByteArray(HEADER_SIZE)
        input.seek(headerOffset)
        if (readBytes(input, header, HEADER_SIZE) != HEADER_SIZE) return false
        for (i in MAGIC.indices) if (header[i] != MAGIC[i]) return false

        val mki = deserializeHeaderData(header)
        var actSlot = 0
        for (i in _keySlots.indices) {
            val ks = _keySlots[i]
            if (ks.isActive) {
                if (_openingProgressReporter != null) (_openingProgressReporter as ProgressReporter).setCurrentSlot(
                    actSlot++
                )
                if (tryPassword(input, ks, mki, _password)) {
                    _activeKeyslotIndex = i
                    _volumeSize = calcVolumeSize(input.length())
                    return true
                }
            }
        }
        throw WrongPasswordException()
    }

    @Throws(IOException::class, ApplicationException::class)
    override fun writeHeader(output: RandomAccessIO) {
        checkWriteHeaderPrereqs()
        for (ks in _keySlots) ks.isActive = false
        writeKey(output, _keySlots[_activeKeyslotIndex], _password)
        writeHeaderData(output)
    }

    override val supportedHashFuncs: List<MessageDigest>
        get() {
            val l =
                ArrayList<MessageDigest>()
            try {
                l.add(MessageDigest.getInstance("SHA1"))
            } catch (ignored: NoSuchAlgorithmException) {
            }
            try {
                l.add(MessageDigest.getInstance("SHA-512"))
            } catch (ignored: NoSuchAlgorithmException) {
            }
            try {
                l.add(MessageDigest.getInstance("SHA-256"))
            } catch (ignored: NoSuchAlgorithmException) {
            }
            l.add(RIPEMD160())
            l.add(Whirlpool())
            return l
        }

    override val supportedEncryptionEngines: List<FileEncryptionEngine>
        get() = Arrays.asList(
            AESXTS(),
            SerpentXTS(),
            TwofishXTS(),
            GOSTXTS(),
            AESCBC(),
            SerpentCBC(),
            TwofishCBC(),
            GOSTCBC()
        )

    override fun setEncryptionEngineIV(eng: FileEncryptionEngine, decryptedVolumeOffset: Long) {
        val block = decryptedVolumeOffset / eng.fileBlockSize
        eng.iV = getIVFromBlockIndex(block)
    }

    @Throws(IOException::class, ApplicationException::class)
    fun writeKey(output: RandomAccessIO, keyIndex: Int, password: ByteArray?) {
        checkWriteHeaderPrereqs()
        writeKey(output, _keySlots[keyIndex], password)
    }

    override val encryptedDataOffset: Long
        get() = (_payloadOffsetSector * SECTOR_SIZE).toLong()

    override fun getEncryptedDataSize(fileSize: Long): Long {
        return _volumeSize
    }

    fun setContainerSize(containerSize: Long) {
        _volumeSize = calcVolumeSize(containerSize)
    }

    fun setActiveKeyslot(keyslotIndex: Int) {
        _activeKeyslotIndex = keyslotIndex
    }

    fun findCipher(cipherName: String, modeName: String, keySize: Int): FileEncryptionEngine? {
        if (cipherName.equals("aes", ignoreCase = true)
            && modeName.equals("xts-plain64", ignoreCase = true)
            && keySize == 32
        ) return AESXTS(keySize)

        return findCipher(cipherName, modeName)
    }

    override fun findHashFunc(name: String): MessageDigest? {
        var name = name
        if (name.equals("sha512", ignoreCase = true)) name = "SHA-512"
        else if (name.equals("sha256", ignoreCase = true)) name = "SHA-256"
        return super.findHashFunc(name)
    }

    override fun setOpeningProgressReporter(reporter: ContainerOpeningProgressReporter?) {
        if (reporter != null) _openingProgressReporter = ProgressReporter(reporter)
        else super.setOpeningProgressReporter(reporter)
    }

    protected inner class KeySlot {
        fun init(slotIndex: Int) {
            isActive = false
            passwordIterations = SLOT_ITERATIONS_MIN
            salt = ByteArray(MK_SALT_SIZE)
            random.nextBytes(salt)
            numStripes = NUM_AF_STRIPES
            val af = AF(hashFunc!!, _masterKey!!.size)
            val blocksPerStripeSet = af.calcNumRequiredSectors(numStripes)
            var sector = KEY_MATERIAL_OFFSET / SECTOR_SIZE
            for (i in 0..<slotIndex) sector =
                sizeRoundUp(sector + blocksPerStripeSet, KEY_MATERIAL_OFFSET / SECTOR_SIZE)
            keyMaterialOffsetSector = sector
        }

        fun serialize(bb: ByteBuffer) {
            bb.putInt(if (isActive) KEY_ENABLED_SIG else KEY_DISABLED_SIG)
            bb.putInt(passwordIterations)
            bb.put(salt)
            bb.putInt(keyMaterialOffsetSector)
            bb.putInt(numStripes)
        }

        fun deserialize(bb: ByteBuffer) {
            val act = bb.getInt()
            isActive = act == KEY_ENABLED_SIG
            passwordIterations = bb.getInt()
            salt = ByteArray(MK_SALT_SIZE)
            bb[salt]
            keyMaterialOffsetSector = bb.getInt()
            numStripes = bb.getInt()
        }

        var isActive: Boolean = false
        var passwordIterations: Int = 0
        var salt: ByteArray
        var keyMaterialOffsetSector: Int = 0
        var numStripes: Int = 0
    }

    protected inner class MKInfo {
        @Throws(ApplicationException::class)
        fun init() {
            iterations = MK_ITERATIONS_MIN
            keyLength = _masterKey!!.size
            salt = ByteArray(MK_SALT_SIZE)
            random.nextBytes(salt)
            digest = deriveKey(MK_DIGEST_SIZE, hashFunc, _masterKey, salt, iterations)
        }

        @Throws(ApplicationException::class)
        fun isValidKey(key: ByteArray?): Boolean {
            val keyDigest = deriveKey(MK_DIGEST_SIZE, hashFunc, key, salt, iterations)
            return keyDigest.contentEquals(digest)
        }

        fun serialize(bb: ByteBuffer) {
            bb.putInt(keyLength)
            bb.put(digest)
            bb.put(salt)
            bb.putInt(iterations)
        }

        fun deserialize(bb: ByteBuffer) {
            keyLength = bb.getInt()
            digest = ByteArray(MK_DIGEST_SIZE)
            bb[digest]
            salt = ByteArray(MK_SALT_SIZE)
            bb[salt]
            iterations = bb.getInt()
        }

        var iterations: Int = 0
        var keyLength: Int = 0
        var salt: ByteArray
        var digest: ByteArray
    }

    protected inner class ProgressReporter(private val _base: ContainerOpeningProgressReporter) :
        ContainerOpeningProgressReporter {
        override fun setCurrentKDFName(name: String?) {
            _base.setCurrentKDFName(name)
        }

        override fun setCurrentEncryptionAlgName(name: String?) {
            _base.setCurrentEncryptionAlgName(name)
        }

        override fun setContainerFormatName(name: String?) {
            _base.setContainerFormatName(name)
        }

        override fun setIsHidden(`val`: Boolean) {
            _base.setIsHidden(`val`)
        }

        override fun setText(text: CharSequence?) {
            _base.setText(text)
        }

        override fun setProgress(progress: Int) {
            var progress = progress
            if (_numberActiveSlots > 0) progress = ((_currentSlot.toFloat() / _numberActiveSlots
                    + ((if (_ksProcessed) 80f else 0f)
                    + progress.toFloat() * (if (_ksProcessed) 0.2f else 0.8f))
                    / (100 * _numberActiveSlots))
                    * 100).toInt()
            _base.setProgress(progress)
        }

        override val isCancelled: Boolean
            get() = _base.isCancelled

        fun setCurrentSlot(i: Int) {
            _currentSlot = i
            if (_currentSlot == 0) {
                _numberActiveSlots = 0
                for (ks in _keySlots) if (ks.isActive) _numberActiveSlots++
            }
        }

        fun setKSProcessed(`val`: Boolean) {
            _ksProcessed = `val`
        }

        private var _currentSlot = 0
        private var _numberActiveSlots = 0
        private var _ksProcessed = false
    }

    protected var _uuid: UUID? = null
    protected var _payloadOffsetSector: Int = 0
    protected var _activeKeyslotIndex: Int = 0
    protected var _isDetachedHeader: Boolean = false
    protected val _keySlots: MutableList<KeySlot> = ArrayList()
    protected var _volumeSize: Long = 0

    protected fun calcVolumeSize(containerSize: Long): Long {
        return containerSize - _payloadOffsetSector * SECTOR_SIZE
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun writeHeaderData(output: RandomAccessIO) {
        val headerData = serializeHeaderData()
        output.seek(headerOffset)
        output.write(headerData, 0, headerData.size)
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun writeKey(output: RandomAccessIO, ks: KeySlot, password: ByteArray?) {
        try {
            val derivedKey =
                deriveKey(_encEngine!!.keySize, hashFunc, password, ks.salt, ks.passwordIterations)
            val af = AF(hashFunc!!, derivedKey.size)
            val afSize = af.calcNumRequiredSectors(ks.numStripes) * AF.SECTOR_SIZE
            val afKey = ByteArray(afSize)
            af.split(_masterKey!!, 0, afKey, 0, ks.numStripes)
            _encEngine!!.key = derivedKey
            _encEngine!!.init()
            _encEngine!!.iV = ByteArray(_encEngine!!.iVSize)
            _encEngine!!.encrypt(afKey, 0, afKey.size)

            output.seek((ks.keyMaterialOffsetSector * SECTOR_SIZE).toLong())
            output.write(afKey, 0, afKey.size)
            ks.isActive = true
        } catch (e: DigestException) {
            throw ApplicationException("Key setup failed", e)
        } finally {
            _encEngine!!.key = _masterKey
            _encEngine!!.init()
        }
    }

    @Throws(ApplicationException::class)
    protected fun serializeHeaderData(): ByteArray {
        val bb = ByteBuffer.allocate(HEADER_SIZE)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.put(MAGIC)
        bb.putShort(1.toShort())
        bb.put(cipherName)
        bb.put(cipherModeName)
        bb.put(hashspecName)
        bb.putInt(_payloadOffsetSector)

        val mki = MKInfo()
        mki.init()
        mki.serialize(bb)

        bb.put(uUIDBytes)
        for (ks in _keySlots) ks.serialize(bb)
        return bb.array()
    }

    @Throws(ApplicationException::class)
    protected fun deserializeHeaderData(headerData: ByteArray): MKInfo {
        val bb = ByteBuffer.wrap(headerData)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.position(MAGIC.size)
        val ver = bb.getShort()
        if (ver > 1) throw UnsupportedContainerTypeException("Unsupported container format version $ver")
        var buf = ByteArray(MAX_CIPHERNAME_LEN)
        bb[buf]
        val cipherName = String(buf).trim { it <= ' ' }
        buf = ByteArray(MAX_CIPHERMODENAME_LEN)
        bb[buf]
        val modeName = String(buf).trim { it <= ' ' }

        buf = ByteArray(MAX_HASHSPEC_LEN)
        bb[buf]
        val hfName = String(buf).trim { it <= ' ' }

        hashFunc = findHashFunc(hfName)
        if (hashFunc == null) throw ApplicationException(
            String.format(
                "Unsupported hash algorithm: %s",
                hfName
            )
        )
        _payloadOffsetSector = bb.getInt()

        val mki = MKInfo()
        mki.deserialize(bb)

        setEngine(findCipher(cipherName, modeName, mki.keyLength))
        if (_encEngine == null) throw ApplicationException(
            String.format("Unsupported cipher/mode: %s-%s", cipherName, modeName)
        )

        val uuidBytes = ByteArray(UUID_LENGTH)
        bb[uuidBytes]
        val uuidStr = String(uuidBytes).trim { it <= ' ' }
        _uuid = UUID.fromString(uuidStr)

        _keySlots.clear()
        for (i in 0..<NUM_KEY_SLOTS) {
            val ks = KeySlot()
            ks.deserialize(bb)
            _keySlots.add(ks)
        }
        return mki
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun tryPassword(
        io: RandomAccessIO,
        ks: KeySlot,
        mki: MKInfo,
        password: ByteArray?
    ): Boolean {
        io.seek((ks.keyMaterialOffsetSector * SECTOR_SIZE).toLong())
        val af = AF(hashFunc!!, mki.keyLength)
        val afSize = af.calcNumRequiredSectors(ks.numStripes) * SECTOR_SIZE
        val afKey = ByteArray(afSize)
        if (readBytes(io, afKey, afKey.size) != afKey.size) throw EOFException()

        if (_openingProgressReporter != null) {
            _openingProgressReporter!!.setCurrentKDFName(hashFunc!!.algorithm)
            _openingProgressReporter!!.setCurrentEncryptionAlgName(
                getEncEngineName(_encEngine!!)
            )
            (_openingProgressReporter as ProgressReporter).setKSProcessed(false)
        }

        debug(
            String.format("Using %s hash function to derive the key", hashFunc!!.algorithm)
        )
        val key =
            deriveKey(_encEngine!!.keySize, hashFunc, password, ks.salt, ks.passwordIterations)

        debug(
            String.format(
                "Using %s encryption engine", getEncEngineName(
                    _encEngine!!
                )
            )
        )
        _encEngine!!.key = key
        _encEngine!!.init()
        // _encEngine.setIV(ks.keyMaterialOffsetSector);
        _encEngine!!.iV = ByteArray(_encEngine!!.iVSize)
        _encEngine!!.decrypt(afKey, 0, afKey.size)

        val mk = ByteArray(mki.keyLength)
        try {
            af.merge(afKey, 0, mk, 0, ks.numStripes)
        } catch (e: DigestException) {
            throw ApplicationException("AF merge failed", e)
        }
        if (_openingProgressReporter != null) (_openingProgressReporter as ProgressReporter).setKSProcessed(
            true
        )
        if (mki.isValidKey(mk)) {
            _masterKey = mk
            _encEngine!!.key = _masterKey
            _encEngine!!.init()
            return true
        }
        Arrays.fill(mk, 0.toByte())
        return false
    }

    protected val cipherName: ByteArray
        get() {
            val cn = _encEngine!!.cipherName
            val res =
                ByteArray(MAX_CIPHERNAME_LEN)
            System.arraycopy(
                cn.toByteArray(),
                0,
                res,
                0,
                min(
                    cn.length.toDouble(),
                    MAX_CIPHERNAME_LEN.toDouble()
                ).toInt()
            )
            return res
        }

    protected val cipherModeName: ByteArray
        get() {
            val cn = _encEngine!!.cipherModeName
            val res =
                ByteArray(MAX_CIPHERMODENAME_LEN)
            System.arraycopy(
                cn.toByteArray(),
                0,
                res,
                0,
                min(
                    cn.length.toDouble(),
                    MAX_CIPHERMODENAME_LEN.toDouble()
                ).toInt()
            )
            return res
        }

    protected val uUIDBytes: ByteArray
        get() {
            val uuidStr = _uuid.toString()
            val res =
                ByteArray(UUID_LENGTH)
            System.arraycopy(
                uuidStr.toByteArray(),
                0,
                res,
                0,
                min(
                    uuidStr.length.toDouble(),
                    UUID_LENGTH.toDouble()
                ).toInt()
            )
            return res
        }

    @get:SuppressLint("DefaultLocale")
    protected val hashspecName: ByteArray
        get() {
            var cn = hashFunc!!.algorithm.lowercase(Locale.getDefault())
            when (cn) {
                "sha-512" -> cn = "sha512"
                "sha-256" -> cn = "sha256"
                "sha-1" -> cn = "sha1"
            }
            val res =
                ByteArray(MAX_HASHSPEC_LEN)
            System.arraycopy(
                cn.toByteArray(),
                0,
                res,
                0,
                min(
                    cn.length.toDouble(),
                    MAX_HASHSPEC_LEN.toDouble()
                ).toInt()
            )
            return res
        }

    protected val headerOffset: Long
        get() = 0

    companion object {
        protected fun sizeRoundUp(size: Int, block: Int): Int {
            val s = (size + (block - 1)) / block
            return s * block
        }

        private const val NUM_KEY_SLOTS = 8
        private const val KEY_DISABLED_SIG = 0x0000DEAD
        private const val KEY_ENABLED_SIG = 0x00AC71F3
        private const val SECTOR_SIZE = 512
        private const val MAX_CIPHERNAME_LEN = 32
        private const val MAX_CIPHERMODENAME_LEN = 32
        private const val MAX_HASHSPEC_LEN = 32
        private const val MK_SALT_SIZE = 32
        private const val MK_ITERATIONS_MIN = 1000
        private const val SLOT_ITERATIONS_MIN = 5000
        private const val MK_DIGEST_SIZE = 20
        private const val UUID_LENGTH = 40
        private const val HEADER_SIZE = 1024
        private const val KEY_MATERIAL_OFFSET = 4096
        private const val NUM_AF_STRIPES = 4000
        private const val DEFAULT_DISK_ALIGNMENT = 1024 * 1024

        private val MAGIC = byteArrayOf(
            'L'.code.toByte(),
            'U'.code.toByte(),
            'K'.code.toByte(),
            'S'.code.toByte(),
            0xba.toByte(),
            0xbe.toByte()
        )
    }
}
