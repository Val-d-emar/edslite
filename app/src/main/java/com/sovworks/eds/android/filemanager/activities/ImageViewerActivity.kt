package com.sovworks.eds.android.filemanager.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.Window
import android.view.WindowManager.LayoutParams
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.fragments.FileListDataFragment
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment
import com.sovworks.eds.android.filemanager.fragments.PreviewFragment.Host
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.android.helpers.CachedPathInfo
import com.sovworks.eds.android.helpers.CachedPathInfoBase
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.Settings
import java.util.NavigableSet
import java.util.TreeSet

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "InlinedApi")
class ImageViewerActivity : Activity(), Host {
    class RestorePathsTask : TaskFragment() {
        override fun initTask(activity: Activity) {
            _loc = (activity as ImageViewerActivity).location
            _pathStrings =
                activity.getIntent().getStringArrayListExtra(LocationsManager.PARAM_PATHS)
            _settings = UserSettings.getSettings(activity)
        }

        @Throws(Exception::class)
        override fun doWork(state: TaskState) {
            val paths = Util.restorePaths(
                _loc!!.fs, _pathStrings
            )
            val res: TreeSet<CachedPathInfo> =
                TreeSet<Any?>(FileListDataFragment.getComparator<CachedPathInfo>(_settings))
            for (p in paths) {
                val cpi = CachedPathInfoBase()
                cpi.init(p)
                res.add(cpi)
            }
            state.setResult(res)
        }

        override fun getTaskCallbacks(activity: Activity): TaskCallbacks {
            return (activity as ImageViewerActivity).restorePathsTaskCallbacks
        }

        private var _loc: Location? = null
        private var _pathStrings: ArrayList<String>? = null
        private var _settings: Settings? = null

        companion object {
            const val TAG: String = "RestorePathsTask"

            fun newInstance(): RestorePathsTask {
                return RestorePathsTask()
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        com.sovworks.eds.android.helpers.Util.setTheme(this)
        super.onCreate(savedInstanceState)
        val us = UserSettings.getSettings(this)
        if (us.isFlagSecureEnabled) CompatHelper.setWindowFlagSecure(this)
        if (us.isImageViewerFullScreenModeEnabled) enableFullScreen()
        _location = LocationsManager.getLocationsManager(this).getFromIntent(intent, null)
        fragmentManager.beginTransaction().add(RestorePathsTask.newInstance(), RestorePathsTask.TAG)
            .commit()
    }

    override fun getCurrentFiles(): NavigableSet<out CachedPathInfo> {
        return _files!!
    }

    override fun getLocation(): Location {
        return _location!!
    }

    override fun getFilesListSync(): Any {
        return Any()
    }

    override fun onToggleFullScreen() {
        if (VERSION.SDK_INT < VERSION_CODES.KITKAT) CompatHelper.restartActivity(
            this
        )
    }

    val restorePathsTaskCallbacks: TaskCallbacks
        get() = object : ProgressDialogTaskFragmentCallbacks(this, R.string.loading) {
            override fun onCompleted(args: Bundle, result: Result) {
                super.onCompleted(args, result)
                try {
                    _files = result.result as TreeSet<CachedPathInfo>
                } catch (e: Throwable) {
                    Logger.showAndLog(_context, result.error)
                }
                if (this.previewFragment == null) showFragment(
                    intent.getStringExtra(
                        INTENT_PARAM_CURRENT_PATH
                    )
                )
            }
        }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val pf = fragmentManager.findFragmentByTag(PreviewFragment.TAG) as PreviewFragment
        pf?.updateImageViewFullScreen()
    }

    private var _files: TreeSet<CachedPathInfo>? = null
    private var _location: Location? = null

    private val previewFragment: PreviewFragment?
        get() = fragmentManager.findFragmentByTag(PreviewFragment.TAG) as PreviewFragment

    private fun showFragment(currentImagePathString: String?) {
        val f = PreviewFragment.newInstance(currentImagePathString)
        fragmentManager.beginTransaction().add(android.R.id.content, f, PreviewFragment.TAG)
            .commit()
    }

    private fun enableFullScreen() {
        if (VERSION.SDK_INT < VERSION_CODES.KITKAT) {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window.setFlags(LayoutParams.FLAG_FULLSCREEN, LayoutParams.FLAG_FULLSCREEN)
        }
        invalidateOptionsMenu()
    }

    companion object {
        const val INTENT_PARAM_CURRENT_PATH: String = "current_path"
    }
}
