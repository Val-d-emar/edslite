package com.sovworks.eds.crypto.kdf

import android.annotation.SuppressLint
import com.sovworks.eds.crypto.EncryptionEngineException
import java.security.MessageDigest
import java.util.Locale

@SuppressLint("DefaultLocale")
class HashBasedPBKDF2 @JvmOverloads constructor(
    private val _md: MessageDigest, private val _blockSize: Int = guessMDBlockSize(
        _md
    )
) :
    PBKDF() {
    @Throws(EncryptionEngineException::class)
    override fun initHMAC(password: ByteArray): HMAC {
        _md.reset()
        return HMAC(password, _md, _blockSize)
    }

    companion object {
        @SuppressLint("DefaultLocale")
        private fun guessMDBlockSize(md: MessageDigest): Int {
            val mdn = md.algorithm.lowercase(Locale.getDefault())
            if (mdn == "sha-512" || mdn == "sha512") return 128
            return 64


            /*
		if(mdn.equals("md5") 
				|| mdn.equals("sha-0") 
				|| mdn.equals("sha-1") 
				|| mdn.equals("sha-224") 
				|| mdn.equals("sha-256")
				|| mdn.equals("whirlpool")
				|| mdn.equals("ripemd160"))
			return 64;*/
        }
    }
}
