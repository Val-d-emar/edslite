package com.sovworks.eds.android.service

import android.content.Context
import android.content.Intent
import com.sovworks.eds.android.EdsApplication.Companion.stopProgram
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.util.SrcDstCollection
import com.sovworks.eds.fs.util.SrcDstPlain
import com.sovworks.eds.fs.util.SrcDstRec
import com.sovworks.eds.fs.util.SrcDstSingle
import java.io.IOException
import java.util.concurrent.CancellationException

class ClearTempFolderTask : WipeFilesTask(true) {
    override var param: FileOperationParam?
        get() = super.getParam() as Param
        set(param) {
            super.param = param
        }

    override fun initParam(i: Intent): FileOperationParam {
        return Param(i, _context!!)
    }

    override fun onCompleted(result: Result) {
        if (param.shouldExitProgram()) {
            try {
                removeNotification()
                result.result
                stopProgram(_context, true)
            } catch (ignored: CancellationException) {
            } catch (e: Throwable) {
                reportError(e)
                stopProgram(_context, false)
            }
        } else super.onCompleted(result)
    }

    class Param(i: Intent, private val _context: Context) : FileOperationParam(i) {
        fun shouldExitProgram(): Boolean {
            return intent.getBooleanExtra(ARG_EXIT_PROGRAM, false)
        }

        override fun loadRecords(i: Intent): SrcDstCollection? {
            try {
                return getMirrorFiles(_context)
            } catch (e: IOException) {
                Logger.log(e)
            }

            return null
        }
    }

    companion object {
        const val ARG_EXIT_PROGRAM: String = "com.sovworks.eds.android.EXIT_PROGRAM"

        @Throws(IOException::class)
        fun getMirrorFiles(context: Context?): SrcDstCollection {
            val loc = FileOpsService.getSecTempFolderLocation(
                UserSettings.getSettings(context).workDir,
                context
            )
            if (loc.currentPath != null && loc.currentPath.exists()) {
                val sdr = SrcDstRec(
                    SrcDstSingle(
                        FileOpsService.getSecTempFolderLocation(
                            UserSettings.getSettings(context).workDir,
                            context
                        ),
                        null
                    )
                )
                sdr.setIsDirLast(true)
                return sdr
            } else return SrcDstPlain()
        }
    }
}