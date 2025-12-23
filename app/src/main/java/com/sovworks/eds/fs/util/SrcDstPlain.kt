package com.sovworks.eds.fs.util

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable.Creator
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import java.io.IOException

class SrcDstPlain : ArrayList<SrcDst?>(), SrcDstCollection {
    fun add(srcLoc: Location?, dstLoc: Location?) {
        super.add(SrcDstSimple(srcLoc, dstLoc))
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        val size = size
        dest.writeInt(size)
        for (i in 0..<size) {
            val c = get(i)
            try {
                dest.writeParcelable(c.getSrcLocation().locationUri, flags)
            } catch (e: IOException) {
                dest.writeParcelable(Uri.EMPTY, flags)
            }
            try {
                dest.writeParcelable(
                    if (c.getDstLocation() == null) Uri.EMPTY else c.getDstLocation().locationUri,
                    flags
                )
            } catch (e: IOException) {
                dest.writeParcelable(Uri.EMPTY, flags)
            }
        }
    }

    private class SrcDstSimple(
        override val srcLocation: Location?,
        override val dstLocation: Location?
    ) :
        SrcDst

    companion object {
        fun fromPaths(
            srcLoc: Location, dstLoc: Location?, srcPaths: Collection<Path?>?
        ): SrcDstCollection {
            if (srcPaths == null) return SrcDstSingle(srcLoc, dstLoc)
            val res = SrcDstPlain()
            for (p in srcPaths) {
                val l = srcLoc.copy()
                l.currentPath = p
                res.add(l, dstLoc)
            }
            return res
        }

        val CREATOR: Creator<SrcDstPlain> = object : Creator<SrcDstPlain?> {
            override fun createFromParcel(`in`: Parcel): SrcDstPlain? {
                val res = SrcDstPlain()
                try {
                    val lm = LocationsManagerBase.getLocationsManager(null, false)
                    val size = `in`.readInt()
                    for (i in 0..<size) {
                        var u = `in`.readParcelable<Uri>(javaClass.classLoader)
                        val srcLoc = lm?.getLocation(u)
                        u = `in`.readParcelable(javaClass.classLoader)
                        val dstLoc = if (Uri.EMPTY == u) null else lm?.getLocation(u)
                        if (srcLoc != null) res.add(srcLoc, dstLoc)
                    }
                } catch (e: Exception) {
                    log(e)
                }
                return res
            }

            override fun newArray(size: Int): Array<SrcDstPlain?> {
                return arrayOfNulls(size)
            }
        }

        private const val serialVersionUID = 1L
    }
}
