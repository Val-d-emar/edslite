package com.sovworks.eds.crypto

interface EncryptionEngine {
    /**
     * Initializes engine
     * @throws EncryptionEngineException on init error
     */
    @Throws(EncryptionEngineException::class)
    fun init()

    /**
     * Decrypts buffer
     * @param data data bytes array
     * @throws EncryptionEngineException on init error
     */
    @Throws(EncryptionEngineException::class)
    fun decrypt(data: ByteArray?, offset: Int, len: Int)

    /**
     * Encrypts buffer
     * @param data data bytes array
     * @throws EncryptionEngineException
     */
    @Throws(EncryptionEngineException::class)
    fun encrypt(data: ByteArray?, offset: Int, len: Int)

    /**
     * Sets current iv
     * @param iv current iv
     */
    @JvmField
    var iV: ByteArray?

    @JvmField
    val iVSize: Int

    /**
     * Returns current encryption/decryption key
     * @return current encryption/decryption key
     */
    /**
     * Set encryption/decryption key
     * @param key encryption/decryption key
     */
    @JvmField
    var key: ByteArray?

    /**
     * Returns encryption/decryption key size
     * @return encryption/decryption key
     */
    @JvmField
    val keySize: Int

    fun close()

    @JvmField
    val cipherName: String
    @JvmField
    val cipherModeName: String
}
