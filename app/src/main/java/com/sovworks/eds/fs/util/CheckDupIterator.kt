package com.sovworks.eds.fs.util

class CheckDupIterator<T>(base: MutableIterator<T>) : FilteredIterator<T>(base) {
    override fun isValidItem(item: T): Boolean {
        if (_previousItems.contains(item)) return false
        _previousItems.add(item)
        return true
    }

    private val _previousItems: MutableSet<T> = HashSet()
}
