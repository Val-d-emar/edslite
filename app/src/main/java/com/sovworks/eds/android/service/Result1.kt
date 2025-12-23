package com.sovworks.eds.android.service

class Result {
    @JvmOverloads
    constructor(result: Any? = null) {
        _result = result
        error = null
        isCancelled = false
    }

    constructor(error: Throwable?, isCancelled: Boolean) {
        _result = null
        this.error = error
        this.isCancelled = isCancelled
    }

    @get:Throws(Throwable::class)
    val result: Any?
        get() {
            if (error != null) throw error
            return _result
        }

    val isCancelled: Boolean
    val error: Throwable?
    private val _result: Any?
}