package com.sovworks.eds.fs.encfs.codecs.name

import com.sovworks.eds.fs.encfs.AlgInfo
import com.sovworks.eds.fs.encfs.Config
import com.sovworks.eds.fs.encfs.NameCodecInfo

abstract class NameCodecInfoBase : NameCodecInfo {
    override fun useChainedNamingIV(): Boolean {
        return config!!.useChainedNameIV()
    }

    override fun select(config: Config): AlgInfo {
        val info = createNew()
        info.config = config
        return info
    }

    var config: Config? = null
        private set

    protected abstract fun createNew(): NameCodecInfoBase
}
