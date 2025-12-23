package com.sovworks.eds.android.filemanager.comparators

import com.sovworks.eds.android.helpers.CachedPathInfo
import java.io.IOException
import java.util.Date

class ModDateComparator<T : CachedPathInfo?>(asc: Boolean) : FileNamesComparator<T>(asc) {
    @Throws(IOException::class)
    override fun compareImpl(o1: T, o2: T): Int {
        var aDate = o1!!.modificationDate
        if (aDate == null) aDate = Date()
        var bDate = o2!!.modificationDate
        if (bDate == null) bDate = Date()
        val res = _asc * aDate.compareTo(bDate)
        return if (res == 0) super.compareImpl(o1, o2) else res
    }
}
