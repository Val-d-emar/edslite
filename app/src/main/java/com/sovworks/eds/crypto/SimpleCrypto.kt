package com.sovworks.eds.crypto

import android.annotation.SuppressLint
import com.sovworks.eds.crypto.engines.AESCTR
import com.sovworks.eds.crypto.kdf.HMACSHA512KDF
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Arrays

@SuppressLint("TrulyRandom")
object SimpleCrypto {
    fun getStrongKeyBytes(srcKey: ByteArray, salt: ByteArray): ByteArray {
        //PBKDF2WithHmacSHA1 is not available on the GALAXY Tab.
//		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
//		PBEKeySpec spec = new PBEKeySpec(passwd, salt, 100, 128);
//		SecretKey secret = factory.generateSecret(spec);
//		return new SecretKeySpec(secret.getEncoded(),"AES");
        val kdf = HMACSHA512KDF()
        try {
            return kdf.deriveKey(srcKey, salt, 100, 32)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun charsToBytes(chars: CharArray?): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = Charset.forName("UTF-8").encode(charBuffer)
        val bytes = ByteArray(byteBuffer.limit() - byteBuffer.position())
        System.arraycopy(byteBuffer.array(), byteBuffer.position(), bytes, 0, bytes.size)
        Arrays.fill(charBuffer.array(), '\u0000') // clear sensitive data
        SecureBuffer.Companion.eraseData(byteBuffer.array())
        return bytes
    }

    @JvmStatic
    fun calcStringMD5(s: String): String {
        try {
            return toHexString(MessageDigest.getInstance("MD5").digest(s.toByteArray()))
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun encrypt(key: SecureBuffer, data: ByteArray): String {
        val decKey = key.dataArray ?: throw RuntimeException("key is closed")
        try {
            return toHexString(encrypt(decKey, data, 0, data.size))
        } finally {
            SecureBuffer.Companion.eraseData(decKey)
        }
    }

    @JvmStatic
    fun encrypt(key: SecureBuffer, cleartext: String): String {
        return encrypt(key, cleartext.toByteArray())
    }

    fun decrypt(key: ByteArray?, encrypted: String): ByteArray {
        val enc = toByte(encrypted)
        if (enc.size < IV_SIZE) throw RuntimeException("Encrypted data is too small.")
        return decrypt(key, enc, 0, enc.size)
    }

    @JvmStatic
    fun decrypt(key: SecureBuffer, encrypted: String): ByteArray {
        val decKey = key.dataArray ?: throw RuntimeException("key is closed")
        try {
            return decrypt(decKey, encrypted)
        } finally {
            SecureBuffer.Companion.eraseData(decKey)
        }
    }

    fun encryptWithPassword(passwd: SecureBuffer, cleartext: ByteArray): String {
        val key = passwd.dataArray ?: throw RuntimeException("key is closed")
        try {
            return encryptWithPassword(key, cleartext)
        } finally {
            SecureBuffer.Companion.eraseData(key)
        }
    }

    fun decryptWithPassword(passwd: SecureBuffer, encrypted: String): ByteArray {
        val key = passwd.dataArray ?: throw RuntimeException("key is closed")
        try {
            return decryptWithPassword(key, encrypted)
        } finally {
            SecureBuffer.Companion.eraseData(key)
        }
    }

    fun encryptWithPassword(passwd: ByteArray, cleartext: ByteArray): String {
        return toHexString(encryptWithPasswordBytes(passwd, cleartext))
    }

    @SuppressLint("TrulyRandom")
    fun encryptWithPasswordBytes(passwd: ByteArray, cleartext: ByteArray): ByteArray {
        val sr = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        sr.nextBytes(salt)
        val key = getStrongKeyBytes(passwd, salt)
        try {
            val enc = encrypt(key, cleartext, 0, cleartext.size)
            val res = ByteArray(SALT_SIZE + enc.size)
            System.arraycopy(salt, 0, res, 0, SALT_SIZE)
            System.arraycopy(enc, 0, res, SALT_SIZE, enc.size)
            return res
        } finally {
            SecureBuffer.Companion.eraseData(key)
        }
    }

    fun decryptWithPassword(passwd: ByteArray, encrypted: String): ByteArray {
        return decryptWithPasswordBytes(passwd, toByte(encrypted))
    }

    fun decryptWithPasswordBytes(passwd: ByteArray, encrypted: ByteArray): ByteArray {
        if (encrypted.size < SALT_SIZE + IV_SIZE) throw RuntimeException("Encrypted data is too small.")
        val salt = ByteArray(SALT_SIZE)
        System.arraycopy(encrypted, 0, salt, 0, SALT_SIZE)
        val key = getStrongKeyBytes(passwd, salt)
        try {
            return decrypt(key, encrypted, SALT_SIZE, encrypted.size - SALT_SIZE)
        } finally {
            SecureBuffer.Companion.eraseData(key)
        }
    }

    fun toHex(txt: String): String {
        return toHexString(txt.toByteArray())
    }

    fun fromHex(hex: String): String {
        return String(toByte(hex))
    }

    fun toByte(hexString: String): ByteArray {
        val len = hexString.length / 2
        val result = ByteArray(len)
        for (i in 0..<len) result[i] = hexString.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        return result
    }

    fun toHex(buf: ByteArray?): CharArray {
        if (buf == null) return CharArray(0)
        val result = CharBuffer.allocate(buf.size * 2)
        for (aBuf in buf) appendHex(result, aBuf)
        return result.array()
    }

    @JvmStatic
    fun toHexString(buf: ByteArray?): String {
        return String(toHex(buf))
    }

    private const val SALT_SIZE = 8
    private const val IV_SIZE = 16
    private const val HEX = "0123456789ABCDEF"

    @get:Throws(Exception::class)
    private val cipher: EncryptionEngine
        get() = AESCTR()

    fun encrypt(key: ByteArray?, clear: ByteArray, offset: Int, count: Int): ByteArray {
        try {
            val iv = ByteArray(IV_SIZE)
            val sr = SecureRandom()
            sr.nextBytes(iv)
            val ee = cipher
            ee.key = key
            ee.iv = iv
            ee.init()
            try {
                val res = ByteArray(IV_SIZE + count)
                System.arraycopy(iv, 0, res, 0, IV_SIZE)
                System.arraycopy(clear, offset, res, IV_SIZE, count)
                ee.encrypt(res, IV_SIZE, count)
                return res
            } finally {
                ee.close()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun decrypt(key: ByteArray?, encrypted: ByteArray, offset: Int, count: Int): ByteArray {
        try {
            val iv = ByteArray(IV_SIZE)
            System.arraycopy(encrypted, offset, iv, 0, IV_SIZE)
            val ee = cipher
            ee.key = key
            ee.iv = iv
            ee.init()
            try {
                val res = ByteArray(count - IV_SIZE)
                System.arraycopy(encrypted, offset + IV_SIZE, res, 0, res.size)
                ee.decrypt(res, 0, res.size)
                return res
            } finally {
                ee.close()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun appendHex(sb: CharBuffer, b: Byte) {
        sb.append(HEX[(b.toInt() shr 4) and 0x0f]).append(HEX[b.toInt() and 0x0f])
    }
}