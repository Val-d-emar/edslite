package com.sovworks.eds.fs.util

import android.os.ParcelFileDescriptor
import java.io.IOException

class PFDRandomAccessIO(private val _pfd: ParcelFileDescriptor) : FDRandomAccessIO(_pfd.fd) {
    @Throws(IOException::class)
    override fun close() {
        _pfd.close()
        super.close()
    }
}
