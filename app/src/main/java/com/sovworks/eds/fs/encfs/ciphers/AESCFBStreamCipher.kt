package com.sovworks.eds.fs.encfs.ciphers

import com.sovworks.eds.crypto.engines.AESCFB

class AESCFBStreamCipher(keySize: Int) : StreamCipherBase(AESCFB(keySize))

