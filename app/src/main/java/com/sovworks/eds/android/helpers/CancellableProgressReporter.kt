package com.sovworks.eds.android.helpers

import io.reactivex.functions.Cancellable
import java.util.concurrent.atomic.AtomicBoolean

class CancellableProgressReporter : ProgressReporter, Cancellable {
    override fun setText(text: CharSequence?) {
    }

    override fun setProgress(progress: Int) {
    }

    override val isCancelled: Boolean
        get() = _isCancelled.get()

    @Throws(Exception::class)
    override fun cancel() {
        _isCancelled.set(true)
    }

    private val _isCancelled = AtomicBoolean()
}
