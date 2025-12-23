package com.sovworks.eds.android.helpers

interface ProgressReporter {
    fun setText(text: CharSequence?)
    fun setProgress(progress: Int)
    @JvmField
    val isCancelled: Boolean
}
