package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine.setIncrementIV
import com.sovworks.eds.crypto.engines.AESCBC
import com.sovworks.eds.crypto.modes.CBC.getEncryptionBlockSize
import com.sovworks.eds.crypto.modes.CBC.getFileBlockSize
import com.sovworks.eds.crypto.modes.CBC.setIncrementIV

class AESCBCFileCipher(keySize: Int, fileBlockSize: Int) :
    CipherBase(AESCBC(keySize, fileBlockSize)), FileEncryptionEngine {
    override val fileBlockSize: Int
        get() = base.getFileBlockSize()

    override val encryptionBlockSize: Int
        get() = base.getEncryptionBlockSize()

    override fun setIncrementIV(`val`: Boolean) {
        base.setIncrementIV(`val`)
    }

    override val base: EncryptionEngine?
        get() = super.getBase() as AESCBC
}
