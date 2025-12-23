package com.sovworks.eds.android

import android.os.Build
import android.os.Build.VERSION
import android.os.Process
import android.util.Log
import com.sovworks.eds.android.PRNGFixes.LinuxPRNGSecureRandom
import com.sovworks.eds.android.PRNGFixes.LinuxPRNGSecureRandomProvider
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi
import java.security.Security

/*
* This software is provided 'as-is', without any express or implied
* warranty.  In no event will Google be held liable for any damages
* arising from the use of this software.
*
* Permission is granted to anyone to use this software for any purpose,
* including commercial applications, and to alter it and redistribute it
* freely, as long as the origin is not misrepresented.
*/

/**
 * Fixes for the output of the default PRNG having low entropy.
 *
 * The fixes need to be applied via [.apply] before any use of Java
 * Cryptography Architecture primitives. A good place to invoke them is in the
 * application's `onCreate`.
 */
object PRNGFixes {
    private const val VERSION_CODE_JELLY_BEAN = 16
    private const val VERSION_CODE_JELLY_BEAN_MR2 = 18
    private val BUILD_FINGERPRINT_AND_DEVICE_SERIAL =
        buildFingerprintAndDeviceSerial

    /**
     * Applies all fixes.
     *
     * @throws SecurityException if a fix is needed but could not be applied.
     */
    fun apply() {
        applyOpenSSLFix()
        installLinuxPRNGSecureRandom()
    }

    /**
     * Applies the fix for OpenSSL PRNG having low entropy. Does nothing if the
     * fix is not needed.
     *
     * @throws SecurityException if the fix is needed but could not be applied.
     */
    @Throws(SecurityException::class)
    private fun applyOpenSSLFix() {
        if ((VERSION.SDK_INT < VERSION_CODE_JELLY_BEAN)
            || (VERSION.SDK_INT > VERSION_CODE_JELLY_BEAN_MR2)
        ) {
            // No need to apply the fix
            return
        }

        try {
            // Mix in the device- and invocation-specific seed.
            Class.forName("org.apache.harmony.xnet.provider.jsse.NativeCrypto")
                .getMethod("RAND_seed", ByteArray::class.java)
                .invoke(null, *generateSeed())

            // Mix output of Linux PRNG into OpenSSL's PRNG
            val bytesRead = Class.forName(
                "org.apache.harmony.xnet.provider.jsse.NativeCrypto"
            )
                .getMethod("RAND_load_file", String::class.java, Long::class.javaPrimitiveType)
                .invoke(null, "/dev/urandom", 1024) as Int
            if (bytesRead != 1024) {
                throw IOException(
                    "Unexpected number of bytes read from Linux PRNG: "
                            + bytesRead
                )
            }
        } catch (e: Exception) {
            throw SecurityException("Failed to seed OpenSSL PRNG", e)
        }
    }

    /**
     * Installs a Linux PRNG-backed `SecureRandom` implementation as the
     * default. Does nothing if the implementation is already the default or if
     * there is not need to install the implementation.
     *
     * @throws SecurityException if the fix is needed but could not be applied.
     */
    @Throws(SecurityException::class)
    private fun installLinuxPRNGSecureRandom() {
        if (VERSION.SDK_INT > VERSION_CODE_JELLY_BEAN_MR2) {
            // No need to apply the fix
            return
        }

        // Install a Linux PRNG-based SecureRandom implementation as the
        // default, if not yet installed.
        val secureRandomProviders =
            Security.getProviders("SecureRandom.SHA1PRNG")
        if ((secureRandomProviders == null)
            || (secureRandomProviders.size < 1)
            || (LinuxPRNGSecureRandomProvider::class.java != secureRandomProviders[0].javaClass)
        ) {
            Security.insertProviderAt(LinuxPRNGSecureRandomProvider(), 1)
        }

        // Assert that new SecureRandom() and
        // SecureRandom.getInstance("SHA1PRNG") return a SecureRandom backed
        // by the Linux PRNG-based SecureRandom implementation.
        val rng1 = SecureRandom()
        if (LinuxPRNGSecureRandomProvider::class.java != rng1.provider.javaClass) {
            throw SecurityException(
                "new SecureRandom() backed by wrong Provider: "
                        + rng1.provider.javaClass
            )
        }

        val rng2: SecureRandom
        try {
            rng2 = SecureRandom.getInstance("SHA1PRNG")
        } catch (e: NoSuchAlgorithmException) {
            throw SecurityException("SHA1PRNG not available", e)
        }
        if (LinuxPRNGSecureRandomProvider::class.java != rng2.provider.javaClass) {
            throw SecurityException(
                ("SecureRandom.getInstance(\"SHA1PRNG\") backed by wrong"
                        + " Provider: " + rng2.provider.javaClass)
            )
        }
    }

    /**
     * Generates a device- and invocation-specific seed to be mixed into the
     * Linux PRNG.
     */
    private fun generateSeed(): ByteArray {
        try {
            val seedBuffer = ByteArrayOutputStream()
            val seedBufferOut =
                DataOutputStream(seedBuffer)
            seedBufferOut.writeLong(System.currentTimeMillis())
            seedBufferOut.writeLong(System.nanoTime())
            seedBufferOut.writeInt(Process.myPid())
            seedBufferOut.writeInt(Process.myUid())
            seedBufferOut.write(BUILD_FINGERPRINT_AND_DEVICE_SERIAL)
            seedBufferOut.close()
            return seedBuffer.toByteArray()
        } catch (e: IOException) {
            throw SecurityException("Failed to generate seed", e)
        }
    }

    private val deviceSerialNumber: String?
        /**
         * Gets the hardware serial number of this device.
         *
         * @return serial number or `null` if not available.
         */
        get() {
            // We're using the Reflection API because Build.SERIAL is only available
            // since API Level 9 (Gingerbread, Android 2.3).
            return try {
                Build::class.java.getField("SERIAL")[null] as String
            } catch (ignored: Exception) {
                null
            }
        }

    private val buildFingerprintAndDeviceSerial: ByteArray
        get() {
            val result = StringBuilder()
            val fingerprint = Build.FINGERPRINT
            if (fingerprint != null) {
                result.append(fingerprint)
            }
            val serial = deviceSerialNumber
            if (serial != null) {
                result.append(serial)
            }
            try {
                return result.toString().toByteArray(charset("UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                throw RuntimeException("UTF-8 encoding not supported")
            }
        }

    /**
     * `Provider` of `SecureRandom` engines which pass through
     * all requests to the Linux PRNG.
     */
    private class LinuxPRNGSecureRandomProvider : Provider(
        "LinuxPRNG",
        1.0,
        "A Linux-specific random number provider that uses"
                + " /dev/urandom"
    ) {
        init {
            // Although /dev/urandom is not a SHA-1 PRNG, some apps
            // explicitly request a SHA1PRNG SecureRandom and we thus need to
            // prevent them from getting the default implementation whose output
            // may have low entropy.
            put("SecureRandom.SHA1PRNG", LinuxPRNGSecureRandom::class.java.name)
            put("SecureRandom.SHA1PRNG ImplementedIn", "Software")
        }

        companion object {
            /**
             *
             */
            private const val serialVersionUID = 1L
        }
    }

    /**
     * [SecureRandomSpi] which passes all requests to the Linux PRNG
     * (`/dev/urandom`).
     */
    class LinuxPRNGSecureRandom : SecureRandomSpi() {
        /**
         * Whether this engine instance has been seeded. This is needed because
         * each instance needs to seed itself if the client does not explicitly
         * seed it.
         */
        private var mSeeded = false

        override fun engineSetSeed(bytes: ByteArray) {
            try {
                val out: OutputStream
                synchronized(sLock) {
                    out =
                        urandomOutputStream
                }
                out.write(bytes)
                out.flush()
            } catch (e: IOException) {
                // On a small fraction of devices /dev/urandom is not writable.
                // Log and ignore.
                Log.w(
                    PRNGFixes::class.java.simpleName,
                    "Failed to mix seed into " + URANDOM_FILE
                )
            } finally {
                mSeeded = true
            }
        }

        override fun engineNextBytes(bytes: ByteArray) {
            if (!mSeeded) {
                // Mix in the device- and invocation-specific seed.
                engineSetSeed(generateSeed())
            }

            try {
                val `in`: DataInputStream
                synchronized(sLock) {
                    `in` =
                        urandomInputStream
                }
                synchronized(`in`) {
                    `in`.readFully(bytes)
                }
            } catch (e: IOException) {
                throw SecurityException(
                    "Failed to read from " + URANDOM_FILE, e
                )
            }
        }

        override fun engineGenerateSeed(size: Int): ByteArray {
            val seed = ByteArray(size)
            engineNextBytes(seed)
            return seed
        }

        private val urandomInputStream: DataInputStream
            get() {
                synchronized(sLock) {
                    if (sUrandomIn == null) {
                        // NOTE: Consider inserting a BufferedInputStream between
                        // DataInputStream and FileInputStream if you need higher
                        // PRNG output performance and can live with future PRNG
                        // output being pulled into this process prematurely.
                        try {
                            sUrandomIn = DataInputStream(
                                FileInputStream(URANDOM_FILE)
                            )
                        } catch (e: IOException) {
                            throw SecurityException(
                                ("Failed to open "
                                        + URANDOM_FILE + " for reading"),
                                e
                            )
                        }
                    }
                    return sUrandomIn!!
                }
            }

        @get:Throws(IOException::class)
        private val urandomOutputStream: OutputStream
            get() {
                synchronized(sLock) {
                    if (sUrandomOut == null) {
                        sUrandomOut =
                            FileOutputStream(URANDOM_FILE)
                    }
                    return sUrandomOut!!
                }
            }

        companion object {
            /*
         * IMPLEMENTATION NOTE: Requests to generate bytes and to mix in a seed
         * are passed through to the Linux PRNG (/dev/urandom). Instances of
         * this class seed themselves by mixing in the current time, PID, UID,
         * build fingerprint, and hardware serial number (where available) into
         * Linux PRNG.
         *
         * Concurrency: Read requests to the underlying Linux PRNG are
         * serialized (on sLock) to ensure that multiple threads do not get
         * duplicated PRNG output.
         */
            /**
             *
             */
            private const val serialVersionUID = 1L

            private val URANDOM_FILE = File("/dev/urandom")

            private val sLock = Any()

            /**
             * Input stream for reading from Linux PRNG or `null` if not yet
             * opened.
             *
             * @GuardedBy("sLock")
             */
            private var sUrandomIn: DataInputStream? = null

            /**
             * Output stream for writing to Linux PRNG or `null` if not yet
             * opened.
             *
             * @GuardedBy("sLock")
             */
            private var sUrandomOut: OutputStream? = null
        }
    }
}