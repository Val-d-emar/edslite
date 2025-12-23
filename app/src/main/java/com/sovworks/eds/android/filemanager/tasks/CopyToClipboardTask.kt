package com.sovworks.eds.android.filemanager.tasks

import android.app.Activity
import android.content.ClipData
import android.content.ClipData.Item
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.fs.Path
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.GlobalConfig

class CopyToClipboardTask : TaskFragment() {
    public override fun initTask(activity: Activity) {
        _context = activity.applicationContext
    }

    protected var _context: Context? = null

    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        if (GlobalConfig.isDebug()) Logger.debug("CopyToClipboardTask args: $arguments")
        val paths = ArrayList<Path>()
        val location: Location = LocationsManager.getLocationsManager
        (_context).getFromBundle
        (
                arguments,
        paths
        )
        val clip = makeClipData(_context!!, location, paths)
        if (clip == null) {
            Logger.debug("CopyToClipboardTask: no paths")
            return
        }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard?.setPrimaryClip(clip)
        if (GlobalConfig.isDebug()) Logger.debug("CopyToClipboardTask: clip has been set: $clip")
    }

    override fun getTaskCallbacks(activity: Activity): TaskCallbacks {
        return object : TaskCallbacks {
            override fun onUpdateUI(state: Any) {
            }

            override fun onPrepare(args: Bundle) {
            }

            override fun onResumeUI(args: Bundle) {
            }

            override fun onSuspendUI(args: Bundle) {
            }

            override fun onCompleted(args: Bundle, result: Result) {
                try {
                    result.result
                    val f =
                        fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment
                    if (f != null) {
                        Logger.debug("CopyToClipboard task onCompleted: updating options menu")
                        f.updateOptionsMenu()
                    }
                } catch (e: Throwable) {
                    Logger.showAndLog(activity, e)
                }
            }
        }
    }

    companion object {
        const val TAG: String = "CopyToClipboardTask"

        fun newInstance(loc: Location, paths: Collection<Path?>?): CopyToClipboardTask {
            val args = Bundle()
            LocationsManager.storePathsInBundle(args, loc, paths)
            val f = CopyToClipboardTask()
            f.arguments = args
            return f
        }

        fun makeClipData(context: Context, location: Location, paths: Iterable<Path>): ClipData? {
            val pi = paths.iterator()
            if (!pi.hasNext()) return null
            var path = pi.next()
            val cr = context.contentResolver
            val clip = ClipData.newUri(
                cr,
                path.pathString,
                MainContentProvider.getContentUriFromLocation(location, path)
            )
            while (pi.hasNext()) {
                path = pi.next()
                clip.addItem(
                    Item(
                        MainContentProvider.getContentUriFromLocation(
                            location,
                            path
                        )
                    )
                )
            }
            return clip
        }
    }
}