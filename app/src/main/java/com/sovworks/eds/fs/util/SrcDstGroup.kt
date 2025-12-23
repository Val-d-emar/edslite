package com.sovworks.eds.fs.util

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst

class SrcDstGroup(private val _srcDsts: Collection<SrcDstCollection?>) : SrcDstCollection {
    override fun iterator(): MutableIterator<SrcDst> {
        return object : MutableIterator<SrcDst?> {
            override fun remove() {
                throw UnsupportedOperationException()
            }

            override fun next(): SrcDst {
                if (!_hasNext) throw NoSuchElementException()
                _hasNext = false
                return _next!!
            }

            override fun hasNext(): Boolean {
                if (_hasNext) return true

                while (_curCol == null || !_curCol!!.hasNext()) {
                    if (!_cols.hasNext()) return false
                    _curCol = _cols.next()!!.iterator()
                }
                _next = _curCol!!.next()
                _hasNext = true
                return true
            }

            private var _hasNext = false
            private var _next: SrcDst? = null
            private var _curCol: Iterator<SrcDst?>? = null
            private val _cols = _srcDsts.iterator()
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        val size = _srcDsts.size
        dest.writeInt(size)
        for (c in _srcDsts) dest.writeParcelable(c, flags)
    }

    companion object {
        val CREATOR: Creator<SrcDstGroup> = object : Creator<SrcDstGroup?> {
            override fun createFromParcel(`in`: Parcel): SrcDstGroup? {
                try {
                    val cols = ArrayList<SrcDstCollection?>()
                    val size = `in`.readInt()
                    for (i in 0..<size) cols.add(`in`.readParcelable<Parcelable>(javaClass.classLoader) as SrcDstCollection?)
                    return SrcDstGroup(cols)
                } catch (e: Exception) {
                    log(e)
                    return null
                }
            }

            override fun newArray(size: Int): Array<SrcDstGroup?> {
                return arrayOfNulls(size)
            }
        }
    }
}
