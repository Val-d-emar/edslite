package com.sovworks.eds.fs.util

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable.Creator
import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.util.SrcDstCollection.SrcDst
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManagerBase
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.Arrays

class SrcDstRec(private val _topElement: SrcDst) : SrcDstCollection, SrcDst {
    fun setIsDirLast(`val`: Boolean) {
        _dirLast = `val`
    }

    override fun iterator(): MutableIterator<SrcDst> {
        return observeTree(this, _dirLast)
            .subscribeOn(Schedulers.newThread())
            .blockingIterable(100)
            .iterator()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        try {
            val srcLocation = _topElement.srcLocation
            val dstLocation = _topElement.dstLocation
            dest.writeParcelable(srcLocation!!.locationUri, flags)
            dest.writeParcelable(
                if (dstLocation == null) Uri.EMPTY else dstLocation.locationUri,
                flags
            )
            dest.writeByte((if (_dirLast) 1 else 0).toByte())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @get:Throws(IOException::class)
    override val srcLocation: Location?
        get() = _topElement.srcLocation

    @get:Throws(IOException::class)
    override val dstLocation: Location?
        get() = _topElement.dstLocation

    private var _subElements: List<SrcDstRec>? = null
    private var _dirLast = false

    private val subElements: List<SrcDstRec>
        get() {
            if (_subElements == null) {
                try {
                    _subElements = listSubElements()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            return _subElements
        }

    @Throws(IOException::class)
    private fun listSubElements(): List<SrcDstRec> {
        val res = ArrayList<SrcDstRec>()
        val srcLocation = _topElement.srcLocation
        val startPath = srcLocation!!.currentPath
        if (startPath == null || !startPath.isDirectory) return res

        val directory = startPath.directory
        val directoryName = directory.name
        val dc = directory.list()
        try {
            for (subPath in dc) {
                val subSrcLocation = srcLocation.copy()
                subSrcLocation.currentPath = subPath
                val subSrcDst: SrcDst =
                    object : SrcDst {
                        @get:Throws(IOException::class)
                        override val srcLocation: Location?
                            get() = subSrcLocation

                        @get:Throws(IOException::class)
                        override val dstLocation: Location?
                            get() {
                                if (_dstLocationCache == null) _dstLocationCache =
                                    calcSubDestLocation(_topElement, directoryName)
                                return _dstLocationCache
                            }

                        private var _dstLocationCache: Location? = null
                    }
                res.add(SrcDstRec(subSrcDst))
            }
        } finally {
            dc.close()
        }
        return res
    }

    companion object {
        fun fromPathsNoDest(
            srcLoc: Location, dirLast: Boolean, vararg srcPaths: Path?
        ): SrcDstCollection {
            return fromPaths(srcLoc, null, dirLast, *srcPaths)
        }

        fun fromPathsNoDest(
            srcLoc: Location, dirLast: Boolean, srcPaths: Collection<Path?>?
        ): SrcDstCollection {
            return fromPaths(srcLoc, null, dirLast, srcPaths)
        }

        fun fromPaths(
            srcLoc: Location,
            dstLoc: Location?,
            vararg srcPaths: Path?
        ): SrcDstCollection {
            return fromPaths(srcLoc, dstLoc, false, Arrays.asList(*srcPaths))
        }

        fun fromPaths(
            srcLoc: Location, dstLoc: Location?, dirLast: Boolean, vararg srcPaths: Path?
        ): SrcDstCollection {
            return fromPaths(srcLoc, dstLoc, dirLast, Arrays.asList(*srcPaths))
        }

        fun fromPaths(
            srcLoc: Location, dstLoc: Location?, dirLast: Boolean, srcPaths: Collection<Path?>?
        ): SrcDstCollection {
            if (srcPaths == null) return SrcDstSingle(srcLoc, dstLoc)
            val res = ArrayList<SrcDstCollection?>(srcPaths.size)
            for (p in srcPaths) {
                val nextSrcLoc = srcLoc.copy()
                nextSrcLoc.currentPath = p
                val sdr = SrcDstRec(SrcDstSingle(nextSrcLoc, dstLoc))
                sdr.setIsDirLast(dirLast)
                res.add(sdr)
            }
            return SrcDstGroup(res)
        }

        val CREATOR: Creator<SrcDstRec> = object : Creator<SrcDstRec?> {
            override fun createFromParcel(`in`: Parcel): SrcDstRec? {
                try {
                    val lm = LocationsManagerBase.getLocationsManager(null, false)
                    var u = `in`.readParcelable<Uri>(javaClass.classLoader)
                    val srcLoc = lm?.getLocation(u)
                    u = `in`.readParcelable(javaClass.classLoader)
                    val dstLoc = if (Uri.EMPTY == u) null else lm?.getLocation(u)
                    val dirLast = `in`.readByte().toInt() == 1
                    if (srcLoc != null) {
                        val sdr = SrcDstRec(SrcDstSingle(srcLoc, dstLoc))
                        sdr.setIsDirLast(dirLast)
                        return sdr
                    }
                    return null
                } catch (e: Exception) {
                    log(e)
                    return null
                }
            }

            override fun newArray(size: Int): Array<SrcDstRec?> {
                return arrayOfNulls(size)
            }
        }

        private fun observeTree(tree: SrcDstRec, isDirLast: Boolean): Observable<SrcDst> {
            return Observable.create { emitter: ObservableEmitter<SrcDst> ->
                if (!isDirLast) emitter.onNext(tree)
                for (sdc in tree.subElements) observeTree(sdc, isDirLast)
                    .subscribe(
                        { value: SrcDst -> emitter.onNext(value) },
                        { error: Throwable? ->
                            emitter.onError(
                                error!!
                            )
                        })
                if (isDirLast) emitter.onNext(tree)
                emitter.onComplete()
            }
        }

        @Throws(IOException::class)
        private fun calcSubDestLocation(
            parentSrcDst: SrcDst, nextDirName: String
        ): Location? {
            var loc: Location? = parentSrcDst.dstLocation ?: return null
            try {
                loc = loc.copy()
                val dstSubPath = PathUtil.getDirectory(loc.currentPath, nextDirName).path
                loc.currentPath = dstSubPath
                return loc
            } catch (e: IOException) {
                log(e)
            }
            return null
        }
    }
}
