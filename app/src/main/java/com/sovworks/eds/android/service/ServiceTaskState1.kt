package com.sovworks.eds.android.service

interface ServiceTaskState {
    val isCancelled: Boolean

    fun updateUI()

    var result: Any?

    val param: Any?

    val taskId: Int
}