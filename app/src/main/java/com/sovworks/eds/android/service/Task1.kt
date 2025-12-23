package com.sovworks.eds.android.service

import android.content.Context
import android.content.Intent

interface Task {
    @Throws(Throwable::class)
    fun doWork(context: Context, i: Intent): Any?
    fun onCompleted(result: Result)
    fun cancel()
}
