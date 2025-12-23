package com.sovworks.eds.android.service

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.locations.Location
import java.io.IOException
import java.util.concurrent.CancellationException

class ActionSendTask : PrepareTempFilesTask() {
    class Param(i: Intent, context: Context?) : FilesTaskParam(i, context) {
        override fun forceOverwrite(): Boolean {
            return true
        }

        val mimeType: String? = i.getStringExtra(ARG_MIME_TYPE)
    }

    override var param: FileOperationParam?
        get() = super.getParam() as Param
        set(param) {
            super.param = param
        }

    override fun onCompleted(result: Result) {
        try {
            val tmpFilesList = result.result as List<Location>
            sendFiles(_context!!, tmpFilesList, param.mimeType)
        } catch (ignored: CancellationException) {
        } catch (e: Throwable) {
            reportError(e)
        } finally {
            super.onCompleted(result)
        }
    }

    override fun initParam(i: Intent): FilesTaskParam {
        return Param(i, _context)
    }

    companion object {
        const val ARG_MIME_TYPE: String = "com.sovworks.eds.android.ARG_MIME_TYPE"

        fun sendFiles(context: Context, files: List<Location>?, mimeType: String?) {
            if (files == null || files.isEmpty()) return

            val uris = ArrayList<Uri?>()
            for (l in files) try {
                var uri = l.getDeviceAccessibleUri(l.currentPath)
                if (uri == null) uri = MainContentProvider.getContentUriFromLocation(l)
                uris.add(uri)
            } catch (e: IOException) {
                Logger.log(e)
            }
            sendFiles(context, uris, mimeType, null)
        }

        fun sendFiles(
            context: Context,
            uris: ArrayList<Uri?>?,
            mime: String?,
            clipData: ClipData?
        ) {
            if (uris == null || uris.isEmpty()) return

            val actionIntent =
                Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
            actionIntent.setType(mime ?: "*/*")
            if (uris.size > 1) actionIntent.putExtra(Intent.EXTRA_STREAM, uris)
            else actionIntent.putExtra(Intent.EXTRA_STREAM, uris[0])
            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN && clipData != null) {
                actionIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                actionIntent.clipData = clipData
            }

            val startIntent =
                Intent.createChooser(actionIntent, context.getString(R.string.send_files_to))
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(startIntent)
        }
    }
}