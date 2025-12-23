package com.sovworks.eds.android.filemanager.comparators

import com.sovworks.eds.android.helpers.CachedPathInfo
import java.io.IOException
import java.util.regex.Pattern

class FileNamesNumericComparator<T : CachedPathInfo?>(asc: Boolean) : FileNamesComparator<T>(asc) {
    @Throws(IOException::class)
    override fun compareImpl(o1: T, o2: T): Int {
        var n1 = o1!!.name
        if (n1 == null) n1 = ""
        var n2 = o2!!.name
        if (n2 == null) n2 = ""

        var b1 = false
        var b2 = false
        var v1 = 0
        var v2 = 0
        var m = PATTERN.matcher(n1)
        if (m.find()) {
            v1 = m.group(1).toInt()
            b1 = true
        }
        m = PATTERN.matcher(n2)
        if (m.find()) {
            v2 = m.group(1).toInt()
            b2 = true
        }
        if (b1 && b2) {
            val res = _asc * v1.compareTo(v2)
            return if (res == 0) super.compareImpl(o1, o2) else res
        }
        if (b1) return -_asc
        if (b2) return _asc
        return super.compareImpl(o1, o2)
    }

    companion object {
        private val PATTERN: Pattern = Pattern.compile("((?:-|\\+)?[0-9]+)", 0)
    }
}
