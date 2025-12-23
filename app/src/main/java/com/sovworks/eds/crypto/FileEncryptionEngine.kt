package com.sovworks.eds.crypto

interface FileEncryptionEngine : EncryptionEngine {
    /**
     * Returns encryption sector size
     * @return encryption sector size
     */
    @JvmField
    val fileBlockSize: Int

    @JvmField
    val encryptionBlockSize: Int

    fun setIncrementIV(`val`: Boolean)
}
