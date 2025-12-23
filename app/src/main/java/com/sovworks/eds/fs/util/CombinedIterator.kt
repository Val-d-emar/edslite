package com.sovworks.eds.fs.util

class CombinedIterator<T>(iters: Iterable<MutableIterator<T>>) :
    MutableIterator<T> {
    override fun hasNext(): Boolean {
        return currentIter != null
    }

    override fun next(): T {
        val iter = currentIter
        checkNotNull(iter) { "No more elements" }
        return iter.next()
    }

    override fun remove() {
        checkNotNull(_iter) { "Current element is not set" }
        _iter!!.remove()
    }

    private val currentIter: Iterator<T>?
        get() {
            if (_iter == null || !_iter!!.hasNext()) {
                _iter = null
                while (_iterIter.hasNext()) {
                    _iter = _iterIter.next()
                    if (_iter!!.hasNext()) break
                    else _iter = null
                }
            }
            return _iter
        }

    private val _iterIter =
        iters.iterator()
    private var _iter: MutableIterator<T>? = null
}
