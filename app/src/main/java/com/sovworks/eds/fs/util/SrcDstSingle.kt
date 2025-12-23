package com.sovworks.eds.fs.util

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable.Creator
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import java.io.IOException

class SrcDstSingle(private val _srcLocation: Location, override val dstLocation: Location?) :
    SrcDstCollection, SrcDst {
    override fun iterator(): MutableIterator<SrcDst> {
        return object : MutableIterator<SrcDst?> {
            override fun remove() {
                throw UnsupportedOperationException()
            }

            override fun next(): SrcDst {
                if (_shown) throw NoSuchElementException()
                _shown = true
                return this@SrcDstSingle
            }

            override fun hasNext(): Boolean {
                return !_shown
            }

            private var _shown = false
        }
    }

    @get:Throws(IOException::class)
    override val srcLocation: Location?
        get() = _srcLocation

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(_srcLocation.locationUri, flags)
        dest.writeParcelable(if (dstLocation == null) Uri.EMPTY else dstLocation.locationUri, flags)
    }

    companion object {
        val CREATOR: Creator<SrcDstSingle> = object : Creator<SrcDstSingle?> {
            override fun createFromParcel(`in`: Parcel): SrcDstSingle? {
                try {
                    val lm = LocationsManagerBase.getLocationsManager(null, false)
                    var u = `in`.readParcelable<Uri>(javaClass.classLoader)
                    val srcLoc = lm?.getLocation(u)
                    u = `in`.readParcelable(javaClass.classLoader)
                    val dstLoc = if (Uri.EMPTY == u) null else lm?.getLocation(u)
                    return if (srcLoc != null) SrcDstSingle(srcLoc, dstLoc) else null
                } catch (e: Exception) {
                    log(e)
                    return null
                }
            }

            override fun newArray(size: Int): Array<SrcDstSingle?> {
                return arrayOfNulls(size)
            }
        }
    }
}
