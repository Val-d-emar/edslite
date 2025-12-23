package com.sovworks.eds.fs.util

import java.io.File
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern

class StringPathUtil : Comparable<StringPathUtil> {
    constructor() {
        _components = ArrayList()
    }

    constructor(pathString: String?) {
        _components = splitPath(pathString)
    }

    constructor(vararg components: String?) {
        _components = Arrays.asList(*components)
    }

    constructor(components: List<String>) {
        _components = ArrayList(components)
    }

    constructor(p1: StringPathUtil, vararg components: String?) : this(
        p1,
        Arrays.asList<String>(*components)
    )

    constructor(p1: StringPathUtil, components: List<String>) {
        _components = ArrayList(p1._components)
        _components.addAll(components)
    }

    constructor(part: String, parts: StringPathUtil) {
        _components = ArrayList(parts._components)
        _components.add(0, part)
    }

    constructor(p1: StringPathUtil, p2: StringPathUtil) : this(p1, p2._components)

    override fun equals(o: Any?): Boolean {
        if (o is StringPathUtil) {
            val ocomponents: List<String> = o._components
            if (ocomponents.size != _components.size) return false
            for (i in ocomponents.indices) if (!ocomponents[i].equals(
                    _components[i],
                    ignoreCase = true
                )
            ) return false

            return true
        }

        if (o is String) return equals(StringPathUtil(o))

        return super.equals(o)
    }

    override fun hashCode(): Int {
        var res = 0
        for (c in _components) res = res xor c.lowercase(Locale.getDefault()).hashCode()
        return res
    }

    fun combine(part: String?): StringPathUtil {
        return combine(StringPathUtil(part))
    }

    fun combine(part: StringPathUtil): StringPathUtil {
        return StringPathUtil(this, part)
    }

    val isEmpty: Boolean
        get() = _components.isEmpty()

    val isSpecial: Boolean
        get() {
            val n = fileName
            return n == "." || n == ".."
        }

    val components: Array<String>
        get() = _components.toTypedArray<String>()

    val numComponents: Int
        get() = _components.size

    val parentPath: StringPathUtil
        get() {
            if (_components.size < 2) return StringPathUtil()
            return StringPathUtil(_components.subList(0, _components.size - 1))
        }

    fun getSubPath(numToRemove: Int): StringPathUtil {
        if (_components.size < numToRemove + 1) return StringPathUtil()
        return StringPathUtil(_components.subList(numToRemove, _components.size))
    }

    fun getSubPath(parentPath: StringPathUtil): StringPathUtil {
        return getSubPath(parentPath._components.size)
    }

    val fileName: String
        get() = if (!_components.isEmpty()) _components[_components.size - 1] else ""

    val fileNameWithoutExtension: String
        get() = getFileNameWithoutExtension(fileName)

    val fileExtension: String
        get() = getFileExtension(fileName)

    fun isParentDir(subPath: StringPathUtil): Boolean {
        val s = _components.size
        if (subPath._components.size <= s) return false

        for (i in 0..<s) if (!_components[i].equals(
                subPath._components[i],
                ignoreCase = true
            )
        ) return false

        return true
    }

    override fun toString(): String {
        return joinPath(_components, 0, _components.size)
    }

    override fun compareTo(other: StringPathUtil): Int {
        return toString().compareTo(other.toString())
    }

    protected val _components: MutableList<String>

    companion object {
        fun splitPath(path: String?): MutableList<String> {
            val res = ArrayList<String>()
            if (path != null) for (s in path.split(Pattern.quote(File.separator).toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()) {
                if (!s.trim { it <= ' ' }.isEmpty()) res.add(s)
            }
            return res
        }

        fun joinPath(vararg components: String?): String {
            return joinPath(Arrays.asList(*components), 0, components.size)
        }

        fun joinPath(components: List<String>, off: Int, count: Int): String {
            if (count == 0) return File.separator
            val res = StringBuilder(File.separator)
            for (i in 0..<count) {
                res.append(components[i + off])
                res.append(File.separatorChar)
            }
            res.deleteCharAt(res.length - 1)
            return res.toString()
        }

        fun getSubPath(srcPath: String?, numToRemove: Int): String {
            val components: List<String> = splitPath(srcPath)
            if (components.size < numToRemove + 1) return ""
            return joinPath(components, numToRemove, components.size - numToRemove)
        }

        fun getSubPath(srcPath: String?, parentPath: String?): String {
            return getSubPath(srcPath, splitPath(parentPath).size)
        }

        fun getFileNameWithoutExtension(fn: String): String {
            val dotIndex = fn.lastIndexOf('.')
            return if (dotIndex > 0) fn.substring(0, dotIndex) else fn
        }

        fun getFileExtension(fn: String): String {
            val dotIndex = fn.lastIndexOf('.')
            return if (dotIndex > 0) fn.substring(dotIndex + 1) else ""
        }
    }
}
