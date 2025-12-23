package com.sovworks.eds.truecrypt

import com.sovworks.eds.android.Logger.Companion.debug
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.container.VolumeLayoutBase
import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.EncryptionEngineException
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.engines.AESXTS
import com.sovworks.eds.crypto.hash.RIPEMD160
import com.sovworks.eds.crypto.hash.Whirlpool
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.exceptions.HeaderCRCException
import com.sovworks.eds.exceptions.WrongContainerVersionException
import com.sovworks.eds.fs.RandomAccessIO
import com.sovworks.eds.fs.RandomStorageAccess.length
import com.sovworks.eds.fs.util.Util.readBytes
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.zip.CRC32

open class StdLayout : VolumeLayoutBase() {
    override fun initNew() {
        super.initNew()
        if (hashFunc == null) try {
            hashFunc = MessageDigest.getInstance("SHA-512")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("SHA-512 is not available", e)
        }
        if (_encEngine == null) setEngine(AESXTS())
    }

    protected val backupHeaderOffset: Long
        get() = _inputSize - 2 * headerSize

    @Throws(IOException::class, ApplicationException::class)
    override fun writeHeader(output: RandomAccessIO) {
        checkWriteHeaderPrereqs()
        val headerData = encodeHeader()
        encryptAndWriteHeaderData(output, headerData)
        prepareEncryptionEngineForPayload()
    }

    @Throws(IOException::class, ApplicationException::class)
    override fun readHeader(input: RandomAccessIO): Boolean {
        checkReadHeaderPrereqs()
        _inputSize = input.length()
        input.seek(headerOffset)
        val hs = effectiveHeaderSize
        val encryptedHeader = ByteArray(hs + this.encryptedHeaderPartOffset)
        if (readBytes(input, encryptedHeader, hs) != hs) return false
        if (isUnsupportedHeaderType(encryptedHeader)) return false
        val salt = getSaltFromHeader(encryptedHeader)
        if (selectAlgosAndDecodeHeader(encryptedHeader, salt)) {
            prepareEncryptionEngineForPayload()
            return true
        }
        return false
    }

    override fun getEncryptedDataSize(fileSize: Long): Long {
        return _volumeSize
    }

    override val supportedEncryptionEngines: List<FileEncryptionEngine>
        get() = EncryptionEnginesRegistry.getSupportedEncryptionEngines()

    override val supportedHashFuncs: List<MessageDigest>?
        get() {
            val l =
                ArrayList<MessageDigest>()
            try {
                l.add(MessageDigest.getInstance("SHA-512"))
            } catch (ignored: NoSuchAlgorithmException) {
            }
            l.add(RIPEMD160())
            l.add(Whirlpool())
            return l
        }

    fun setContainerSize(containerSize: Long) {
        _inputSize = containerSize
        _volumeSize = calcVolumeSize(containerSize)
    }

    protected class KeyHolder {
        var key: ByteArray?
            get() = _key
            set(key) {
                if (key != null) close()
                _key = key
            }

        fun close() {
            if (_key != null) Arrays.fill(_key, 0.toByte())
        }

        private var _key: ByteArray?
    }

    override var encryptedDataOffset: Long
        protected set
    protected var _volumeSize: Long = 0
    protected var _inputSize: Long = 0

    init {
        encryptedDataOffset = (2 * headerSize).toLong()
    }

    @Throws(EncryptionEngineException::class)
    protected fun prepareEncryptionEngineForPayload() {
        _encEngine!!.key = _masterKey
        _encEngine!!.init()
    }

    protected fun isUnsupportedHeaderType(encryptedHeader: ByteArray?): Boolean {
        return false
    }

    protected fun getSaltFromHeader(headerData: ByteArray): ByteArray {
        val salt = ByteArray(encryptedHeaderPartOffset)
        System.arraycopy(headerData, 0, salt, 0, encryptedHeaderPartOffset)
        return salt
    }

    protected fun isValidSign(headerData: ByteArray): Boolean {
        val sig: ByteArray = this.headerSignature
        val offset: Int = this.encryptedHeaderPartOffset
        for (i in sig.indices) if (headerData[offset + i] != sig[i]) return false
        return true
    }

    @Throws(ApplicationException::class)
    protected fun selectAlgosAndDecodeHeader(
        encryptedHeaderData: ByteArray,
        salt: ByteArray?
    ): Boolean {
        if (hashFunc == null) {
            for (md in supportedHashFuncs!!) {
                val ee = tryHashFunc(encryptedHeaderData, salt, md)
                if (ee != null) {
                    setEngine(ee)
                    hashFunc = md
                    return true
                }
            }
        } else {
            val ee = tryHashFunc(encryptedHeaderData, salt, hashFunc!!)
            if (ee != null) {
                setEngine(ee)
                return true
            }
        }
        return false
    }

    @Throws(ApplicationException::class)
    protected fun tryHashFunc(
        encryptedHeaderData: ByteArray, salt: ByteArray?, hashFunc: MessageDigest
    ): FileEncryptionEngine? {
        debug(
            String.format("Using %s hash function to derive the key", hashFunc.algorithm)
        )
        val prevKey = KeyHolder()
        try {
            if (_encEngine != null) {
                if (tryEncryptionEngine(
                        encryptedHeaderData,
                        salt,
                        hashFunc,
                        _encEngine!!,
                        prevKey
                    )
                ) return _encEngine
            } else {
                for (ee in supportedEncryptionEngines) {
                    if (tryEncryptionEngine(
                            encryptedHeaderData,
                            salt,
                            hashFunc,
                            ee,
                            prevKey
                        )
                    ) return ee
                }
            }
        } finally {
            prevKey.close()
        }
        return null
    }

    @Throws(ApplicationException::class)
    protected fun tryEncryptionEngine(
        encryptedHeaderData: ByteArray,
        salt: ByteArray?,
        hashFunc: MessageDigest,
        ee: EncryptionEngine,
        prevKey: KeyHolder
    ): Boolean {
        debug(
            String.format(
                "Trying to decrypt the header using %s encryption engine",
                getEncEngineName(ee)
            )
        )
        if (_openingProgressReporter != null) {
            _openingProgressReporter!!.setCurrentKDFName(hashFunc.algorithm)
            _openingProgressReporter!!.setCurrentEncryptionAlgName(ee.cipherName)
        }
        var key = prevKey.key
        if (key == null || key.size < ee.keySize) {
            key = deriveHeaderKey(ee, hashFunc, salt)
            prevKey.key = key
        }
        if (decryptAndDecodeHeader(encryptedHeaderData, ee, key)) return true
        else ee.close()
        return false
    }

    @Throws(ApplicationException::class)
    protected fun decryptAndDecodeHeader(
        encryptedHeader: ByteArray,
        ee: EncryptionEngine,
        key: ByteArray?
    ): Boolean {
        var decryptedHeader: ByteArray? = null
        try {
            decryptedHeader = decryptHeader(encryptedHeader, ee, key)
            if (decryptedHeader == null) return false

            if (_masterKey != null) Arrays.fill(_masterKey, 0.toByte())
            _masterKey = ByteArray(ee.keySize)
            decodeHeader(decryptedHeader)
            return true
        } finally {
            if (decryptedHeader != null) Arrays.fill(decryptedHeader, 0.toByte())
        }
    }

    protected open fun getMKKDFNumIterations(hashFunc: MessageDigest): Int {
        val an = hashFunc.algorithm
        if ("ripemd160".equals(an, ignoreCase = true)) return 2000
        return 1000
    }

    @Throws(EncryptionEngineException::class)
    protected fun decryptHeader(
        encryptedData: ByteArray,
        ee: EncryptionEngine,
        key: ByteArray?
    ): ByteArray? {
        ee.iV = ByteArray(ee.iVSize)
        ee.key = key
        ee.init()
        val header = encryptedData.clone()
        val ofs: Int = this.encryptedHeaderPartOffset
        try {
            ee.decrypt(header, ofs, header.size - ofs)
        } catch (e: EncryptionEngineException) {
            return null
        }
        return if (isValidSign(header)) header else null
    }

    @Throws(ApplicationException::class, IOException::class)
    protected fun encryptAndWriteHeaderData(output: RandomAccessIO, headerData: ByteArray) {
        val salt = getSaltFromHeader(headerData)
        val key = deriveHeaderKey(_encEngine!!, hashFunc!!, salt)
        encryptHeader(headerData, key)
        Arrays.fill(key, 0.toByte())
        writeHeaderData(output, headerData)
    }

    @Throws(ApplicationException::class, IOException::class)
    protected fun writeHeaderData(output: RandomAccessIO, encryptedHeaderData: ByteArray) {
        writeHeaderData(output, encryptedHeaderData, headerOffset)
        writeHeaderData(output, encryptedHeaderData, backupHeaderOffset)
    }

    @Throws(ApplicationException::class, IOException::class)
    protected fun writeHeaderData(
        output: RandomAccessIO,
        encryptedHeaderData: ByteArray,
        offset: Long
    ) {
        output.seek(offset)
        output.write(
            encryptedHeaderData, 0, encryptedHeaderData.size - this.encryptedHeaderPartOffset
        )
    }

    @Throws(ApplicationException::class)
    protected fun deriveHeaderKey(
        ee: EncryptionEngine,
        md: MessageDigest,
        salt: ByteArray?
    ): ByteArray {
        var keySize = ee.keySize
        if (_encEngine == null) {
            for (eng in supportedEncryptionEngines) if (eng.keySize > keySize) keySize = eng.keySize
        }
        return deriveKey(keySize, md, _password, salt, getMKKDFNumIterations(md))
    }

    @Throws(ApplicationException::class)
    protected fun encryptHeader(headerData: ByteArray, key: ByteArray?) {
        _encEngine!!.key = key
        _encEngine!!.init()
        _encEngine!!.iV = ByteArray(_encEngine!!.iVSize)
        val encOffs: Int = this.encryptedHeaderPartOffset
        _encEngine!!.encrypt(headerData, encOffs, headerData.size - encOffs)
    }

    protected open val minCompatibleProgramVersion: Short
        get() = EdsContainer.COMPATIBLE_TC_VERSION

    @Throws(EncryptionEngineException::class)
    protected fun encodeHeader(): ByteArray {
        val encPartOffset: Int = this.encryptedHeaderPartOffset
        val bb = ByteBuffer.allocate(effectiveHeaderSize + encPartOffset)
        bb.order(ByteOrder.BIG_ENDIAN)
        val salt = ByteArray(encryptedHeaderPartOffset)
        random.nextBytes(salt)
        bb.put(salt)
        bb.put(this.headerSignature)
        bb.putShort(CURRENT_HEADER_VERSION)
        bb.putShort(minCompatibleProgramVersion)

        val mk = ByteArray(DATA_KEY_AREA_MAX_SIZE.toInt())
        System.arraycopy(_masterKey, 0, mk, 0, _masterKey!!.size)
        val crc = CRC32()
        crc.update(mk)
        bb.putInt(crc.value.toInt())

        bb.position(bb.position() + 16)
        bb.putLong(calcHiddenVolumeSize(_volumeSize))
        bb.putLong(_volumeSize)
        bb.putLong(encryptedDataOffset)
        bb.putLong(_volumeSize)
        // write flags
        bb.putInt(0)
        bb.putInt(SECTOR_SIZE)

        crc.reset()
        crc.update(bb.array(), encPartOffset, HEADER_CRC_OFFSET - encPartOffset)
        bb.position(HEADER_CRC_OFFSET.toInt())
        bb.putInt(crc.value.toInt())
        bb.position(DATA_AREA_KEY_OFFSET.toInt())
        bb.put(mk)
        Arrays.fill(mk, 0.toByte())

        return bb.array()
    }

    protected fun calcHiddenVolumeSize(volumeSize: Long): Long {
        return 0
    }

    protected fun calcVolumeSize(containerSize: Long): Long {
        val numSectors = containerSize / SECTOR_SIZE
        return ((if (containerSize % SECTOR_SIZE == 0L) numSectors else numSectors + 1) * SECTOR_SIZE
                - RESERVED_HEADER_SIZE)
    }

    protected fun loadVolumeSize(headerData: ByteBuffer): Long {
        val vs = headerData.getLong(VOLUME_SIZE_OFFSET)
        return if (vs == 0L) _inputSize - encryptedDataOffset else vs
    }

    @Throws(ApplicationException::class)
    protected fun decodeHeader(data: ByteArray) {
        val encPartOffset: Int = this.encryptedHeaderPartOffset
        val bb = ByteBuffer.wrap(data)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.position(encPartOffset + this.headerSignature.length)
        // offset 68
        val headerVersion = bb.getShort()
        if (headerVersion < MIN_ALLOWED_HEADER_VERSION || headerVersion > CURRENT_HEADER_VERSION) throw WrongContainerVersionException()

        val crc = CRC32()
        crc.update(data, encPartOffset, HEADER_CRC_OFFSET - encPartOffset)
        if (crc.value.toInt() != bb.getInt(HEADER_CRC_OFFSET.toInt())) throw HeaderCRCException()

        // offset 70
        val programVer = bb.getShort().toInt()
        if (programVer > EdsContainer.COMPATIBLE_TC_VERSION) throw WrongContainerVersionException()

        // offset 72
        val volumeKeyAreaCRC32 = bb.getInt()
        // offset+=4;
        // skip volume creation time
        // offset+=8;
        // skip header creation time
        // offset+=8;
        // offset 92
        // hidden volume size
        // Util.bytesToLong(data,offset);
        // if(size!=0)
        //	throw new UnsupportedContainerTypeException();
        // offset+=8;
        // offset 100
        // Volume data size
        // Util.bytesToLong(data,offset);
        // offset+=8;
        // offset 108
        encryptedDataOffset = bb.getLong(108)
        // offset+=8;
        // offset 116
        // Encrypted area length
        // Util.bytesToLong(data,offset);
        // offset+=8;
        // offset 124
        // offset = 124;
        // Flags
        // Util.unsignedIntToLong(data,offset);
        // offset+=4;
        // Sector size
        // Util.unsignedIntToLong(data,offset);
        // _sectorSize = 512;
        _volumeSize = loadVolumeSize(bb)
        crc.reset()
        crc.update(bb.array(), DATA_AREA_KEY_OFFSET.toInt(), DATA_KEY_AREA_MAX_SIZE.toInt())
        if (crc.value.toInt() != volumeKeyAreaCRC32) throw HeaderCRCException()
        bb.position(DATA_AREA_KEY_OFFSET.toInt())
        bb[_masterKey]
    }

    val headerOffset: Long
        get() = 0

    protected val effectiveHeaderSize: Int
        get() = 512

    companion object {
        val headerSize: Int = 64 * 1024
            get() = Companion.field

        protected val RESERVED_HEADER_SIZE: Int =
            4 * headerSize // header + hidden volume header + backup header + backup hidden volume

        // header
        protected const val MIN_ALLOWED_HEADER_VERSION: Short = 3
        protected const val CURRENT_HEADER_VERSION: Short = 5
        protected const val HEADER_CRC_OFFSET: Short = 252
        protected const val DATA_KEY_AREA_MAX_SIZE: Short = 256
        protected const val DATA_AREA_KEY_OFFSET: Short = 256
        protected val encryptedHeaderPartOffset: Int = 64
            get() = Companion.field
        protected const val VOLUME_SIZE_OFFSET: Int = 116

        protected open val headerSignature: ByteArray =
            byteArrayOf('T'.code.toByte(), 'R'.code.toByte(), 'U'.code.toByte(), 'E'.code.toByte())
            get() = Companion.field
    }
}
