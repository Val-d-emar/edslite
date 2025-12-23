package com.sovworks.eds.android.filemanager.comparators

import com.sovworks.eds.android.helpers.CachedPathInfo
import java.io.IOException

class FileSizesComparator<T : CachedPathInfo?>(asc: Boolean) : FileNamesComparator<T>(asc) {
    @Throws(IOException::class)
    override fun compareDirs(o1: T, o2: T): Int {
        val res = super.compareDirs(o1, o2)
        return if (res == 0 && (!o1!!.isFile || !o2!!.isFile))
            super.compareImpl(o1, o2)
        else
            res
    }

    @Throws(IOException::class)
    override fun compareImpl(o1: T, o2: T): Int {
        val aSize = o1!!.size
        val bSize = o2!!.size
        return if (aSize == bSize)
            super.compareImpl(o1, o2)
        else
            (if (aSize < bSize)
                -_asc
            else
                _asc
                    )
    }
}
