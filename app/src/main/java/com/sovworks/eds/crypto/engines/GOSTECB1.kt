package com.sovworks.eds.crypto.engines

import com.sovworks.eds.crypto.blockciphers.GOST
import com.sovworks.eds.crypto.modes.ECB

class GOSTECB : ECB(GOST()) {
    override fun getCipherName(): String {
        return "gost"
    }
}
