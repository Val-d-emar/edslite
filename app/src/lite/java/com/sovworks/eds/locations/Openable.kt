package com.sovworks.eds.locations

import com.sovworks.eds.android.helpers.ProgressReporter
import com.sovworks.eds.crypto.SecureBuffer
import java.io.IOException

interface Openable : Location {
    fun setPassword(pass: SecureBuffer)

    fun hasPassword(): Boolean

    fun requirePassword(): Boolean

    fun hasCustomKDFIterations(): Boolean

    fun requireCustomKDFIterations(): Boolean

    fun setNumKDFIterations(num: Int)

    fun setOpenReadOnly(readOnly: Boolean)

    val isOpen: Boolean

    @Throws(Exception::class)
    fun open()

    @Throws(IOException::class)
    fun close(force: Boolean)

    fun setOpeningProgressReporter(pr: ProgressReporter?)

    companion object {
        const val PARAM_PASSWORD: String = "com.sovworks.eds.android.PASSWORD"
        const val PARAM_KDF_ITERATIONS: String = "com.sovworks.eds.android.KDF_ITERATIONS"
    }
}
