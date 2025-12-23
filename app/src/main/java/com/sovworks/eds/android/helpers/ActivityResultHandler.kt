package com.sovworks.eds.android.helpers

class ActivityResultHandler {
    fun addResult(r: Runnable) {
        if (_isResumed) r.run()
        else _receivers.add(r)
    }

    fun handle() {
        for (r in _receivers) r.run()
        _receivers.clear()
        _isResumed = true
    }

    fun onPause() {
        _isResumed = false
    }

    fun clear() {
        _receivers.clear()
    }

    private val _receivers: MutableList<Runnable> = ArrayList()
    private var _isResumed = false
}
