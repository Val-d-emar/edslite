package com.sovworks.eds.android.filemanager.comparators

import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.records.LocRootDirRecord
import com.sovworks.eds.android.helpers.CachedPathInfo
import java.io.IOException

open class FileNamesComparator<T : CachedPathInfo?>(asc: Boolean) : Comparator<T> {
    override fun compare(o1: T, o2: T): Int {
        var res: Int
        try {
            res = compareDirs(o1, o2)
            if (res == 0) res = compareImpl(o1, o2)
            return if (res == 0) compareImpl(o1, o2) else res
        } catch (e: IOException) {
            Logger.log(e)
            return 0
        }
    }

    protected var _asc: Int = if (asc) 1 else -1

    @Throws(IOException::class)
    protected open fun compareDirs(o1: T, o2: T): Int {
        val n1 = o1!!.name
        val n2 = o2!!.name
        if (".." == n1 || o1 is LocRootDirRecord) return -1
        if (".." == n2 || o2 is LocRootDirRecord) return 1
        if (o1.isFile && o2.isFile) return 0
        if (o1.isFile) return 1
        if (o2.isFile) return -1
        return 0
    }

    @Throws(IOException::class)
    protected open fun compareImpl(o1: T, o2: T): Int {
        var n1 = o1!!.name
        if (n1 == null) n1 = ""
        var n2 = o2!!.name
        if (n2 == null) n2 = ""
        val res = _asc * n1.compareTo(n2, ignoreCase = true)
        return if (res == 0) _asc * o1.path.pathString.compareTo(
            o2.path.pathString,
            ignoreCase = true
        ) else res
    }
}
