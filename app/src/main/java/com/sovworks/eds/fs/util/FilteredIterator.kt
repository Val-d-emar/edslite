package com.sovworks.eds.fs.util

abstract class FilteredIterator<T>(private val _base: MutableIterator<T>) :
    MutableIterator<T?> {
    /**
     * Returns true if there is at least one more element, false otherwise.
     *
     * @see .next
     */
    @Synchronized
    override fun hasNext(): Boolean {
        if (!_hasNext) setNext()
        return _hasNext
    }

    /**
     * Returns the next object and advances the iterator.
     *
     * @return the next object.
     * @throws NoSuchElementException if there are no more elements.
     * @see .hasNext
     */
    @Synchronized
    override fun next(): T? {
        if (!hasNext()) throw NoSuchElementException()
        _hasNext = false
        return _nextItem
    }

    /**
     * Removes the last object returned by `next` from the collection. This method can only be
     * called once between each call to `next`. Do not call `hasNext` between calls to
     * `next` and `remove`
     *
     * @throws UnsupportedOperationException if removing is not supported by the collection being
     * iterated.
     * @throws IllegalStateException if `next` has not been called, or `remove` has
     * already been called after the last call to `next`.
     */
    @Synchronized
    override fun remove() {
        check(_hasNext)
        _base.remove()
    }

    val baseIterator: Iterator<T>
        get() = _base

    protected abstract fun isValidItem(item: T): Boolean

    private var _hasNext = false
    private var _nextItem: T? = null

    private fun setNext() {
        _hasNext = false
        while (_base.hasNext()) {
            val nextItem = _base.next()
            if (isValidItem(nextItem)) {
                _nextItem = nextItem
                _hasNext = true
                break
            }
        }
    }
}
