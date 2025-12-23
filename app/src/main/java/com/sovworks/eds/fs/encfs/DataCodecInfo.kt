package com.sovworks.eds.fs.encfs

import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.fs.encfs.macs.MACCalculator

interface DataCodecInfo : AlgInfo {
    val fileEncDec: FileEncryptionEngine
    val streamEncDec: EncryptionEngine?
    val checksumCalculator: MACCalculator?
}
