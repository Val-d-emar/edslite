package com.sovworks.eds.util

abstract class IteratorConverter<S, T> protected constructor(private val _srcIterator: MutableIterator<S>) :
    MutableIterator<T> {
    override fun hasNext(): Boolean {
        return _srcIterator.hasNext()
    }

    override fun next(): T {
        return convert(_srcIterator.next())
    }

    override fun remove() {
        _srcIterator.remove()
    }

    val srcIter: Iterator<S>
        get() = _srcIterator

    protected abstract fun convert(src: S): T
}
