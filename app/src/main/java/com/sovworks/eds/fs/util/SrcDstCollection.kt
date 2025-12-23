package com.sovworks.eds.fs.util

import android.os.Parcelable
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.locations.Location
import java.io.IOException

interface SrcDstCollection : Iterable<SrcDst?>, Parcelable {
    interface SrcDst {
        @get:Throws(IOException::class)
        val srcLocation: Location?

        @get:Throws(IOException::class)
        val dstLocation: Location?
    }
}
