package com.sovworks.eds.fs.encfs

interface NameCodecInfo : AlgInfo {
    val encDec: NameCodec
    fun useChainedNamingIV(): Boolean
}
