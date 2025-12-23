package com.sovworks.eds.android.service

import android.content.Intent
import com.sovworks.eds.locations.Location
import java.util.concurrent.CancellationException

internal class StartTempFileTask : PrepareTempFilesTask() {
    override fun onCompleted(result: Result) {
        try {
            val tmpFilesList = result.result as List<Location>
            for (f in tmpFilesList) FileOpsService.startFileViewer(_context, f)
        } catch (ignored: CancellationException) {
        } catch (e: Throwable) {
            reportError(e)
        } finally {
            super.onCompleted(result)
        }
    }

    override fun initParam(i: Intent): FilesTaskParam {
        return object : FilesTaskParam(i, _context) {
            override fun forceOverwrite(): Boolean {
                return true
            }
        }
    }
}