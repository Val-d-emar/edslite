package com.sovworks.eds.fs.encfs

interface NameCodec {
    fun encodeName(plaintextName: String?): String?
    fun decodeName(encodedName: String?): String?
    fun getChainedIV(plaintextName: String?): ByteArray?
    fun init(key: ByteArray?)
    fun close()
    var iV: ByteArray?
    val iVSize: Int
}
