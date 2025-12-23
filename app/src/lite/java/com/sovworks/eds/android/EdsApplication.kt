package com.sovworks.eds.android

import android.content.Context

class EdsApplication : EdsApplicationBase() {

    companion object {
        @JvmStatic
        fun stopProgram(context: Context?, exitProcess: Boolean) {
            stopProgramBase(context, exitProcess)
            if (exitProcess) exitProcess()
        }
    }
}
