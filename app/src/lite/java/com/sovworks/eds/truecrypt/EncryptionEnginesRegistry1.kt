package com.sovworks.eds.truecrypt

import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.crypto.engines.AESXTS
import com.sovworks.eds.crypto.engines.SerpentXTS
import com.sovworks.eds.crypto.engines.TwofishXTS
import java.util.Arrays

object EncryptionEnginesRegistry {
    val supportedEncryptionEngines: List<FileEncryptionEngine>
        get() = Arrays.asList<FileEncryptionEngine>(AESXTS(), SerpentXTS(), TwofishXTS())

    fun getEncEngineName(eng: EncryptionEngine): String {
        if (eng is AESXTS) return "AES"
        if (eng is SerpentXTS) return "Serpent"
        if (eng is TwofishXTS) return "Twofish"
        return java.lang.String.format("%s-%s", eng.cipherName, eng.cipherModeName)
    }
}
