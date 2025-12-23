package com.sovworks.eds.android.filemanager.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.FileManagerFragment
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.fragments.FilePropertiesFragment
import com.sovworks.eds.android.filemanager.fragments.FilePropertiesFragment.Companion
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks
import com.sovworks.eds.fs.Path
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

class FilePropertiesFragment : Fragment(), FileManagerFragment {
    class CalcPropertiesTaskFragment : TaskFragment() {
        public override fun initTask(activity: Activity) {
            try {
                val df =
                    fragmentManager.findFragmentByTag(FileListDataFragment.Companion.TAG) as FileListDataFragment
                _paths =
                    if (df != null && df.isAdded) ArrayList(df.selectedPaths)
                    else ArrayList()
                if (df != null && _paths!!.size == 0 && arguments.containsKey(ARG_CURRENT_PATH)) _paths!!.add(
                    df.location.fs.getPath(
                        arguments.getString(ARG_CURRENT_PATH)
                    )
                )
            } catch (e: Exception) {
                Logger.showAndLog(activity, e)
            }
        }

        override fun getTaskCallbacks(activity: Activity): TaskCallbacks? {
            val fm = fragmentManager ?: return null
            val f =
                fm.findFragmentByTag(FilePropertiesFragment.TAG) as FilePropertiesFragment
                    ?: return null
            return f.calcPropertiesCallbacks
        }

        @Throws(Exception::class)
        override fun doWork(state: TaskState) {
            val info = FilesInfo()
            val pathsIterator = _paths!!.iterator()
            while (pathsIterator.hasNext()) {
                if (state.isTaskCancelled) break
                val p = pathsIterator.next()!!
                calcPath(state, info, p)
                pathsIterator.remove()
            }
            state.setResult(info)
        }

        private var _paths: ArrayList<Path?>? = null

        private var _lastUpdate: Long = 0

        private fun calcPath(state: TaskState, info: FilesInfo, rec: Path) {
            info.filesCount++
            if (info.path == null) info.path = rec.pathDesc
            else if (!info.path!!.endsWith(", ...")) info.path += ", ..."
            try {
                if (rec.isFile) {
                    info.totalSize += rec.file.size
                    val mdt = rec.file.lastModified
                    if (info.lastModDate == null
                        || mdt.after(info.lastModDate)
                    ) info.lastModDate = mdt
                } else if (rec.isDirectory) {
                    val dc = rec.directory.list()
                    try {
                        for (p in dc) {
                            if (state.isTaskCancelled) break
                            calcPath(state, info, p)
                        }
                    } finally {
                        dc.close()
                    }
                }
            } catch (ignored: IOException) {
            }
            val curTime = System.currentTimeMillis()
            if (curTime - _lastUpdate > 500) {
                state.updateUI(info.copy())
                _lastUpdate = curTime
            }
        }

        companion object {
            const val TAG: String = "CalcPropertiesTaskFragment"

            fun newInstance(args: Bundle?): CalcPropertiesTaskFragment {
                val f = CalcPropertiesTaskFragment()
                f.arguments = args
                return f
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            _lastInfo = FilesInfo()
            _lastInfo!!.load(savedInstanceState)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState == null) startCalcTask()
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val view = inflater.inflate(
            R.layout.file_properties_fragments,
            container, false
        )
        _sizeTextView = view
            .findViewById<View>(R.id.selectionPropertiesSizeTextView) as TextView
        _numberOfFilesTextView = view
            .findViewById<View>(R.id.selectionPropertiesNumberOfFilesTextView) as TextView
        _fullPathTextView = view.findViewById<View>(R.id.fullPathTextView) as TextView
        _modDateTextView = view
            .findViewById<View>(R.id.lastModifiedTextView) as TextView
        if (_lastInfo != null) updateUI(_lastInfo!!, true)
        return view
    }

    @SuppressLint("NewApi")
    override fun onStop() {
        val fa = activity
        if (fa != null) {
            if (!fa.isChangingConfigurations || (fa is FileManagerActivity)) cancelCalcTask()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (_lastInfo != null) _lastInfo!!.save(outState)
    }

    private class FilesInfo : Cloneable {
        var path: String? = null
        var filesCount: Int = 0
        var totalSize: Long = 0
        var lastModDate: Date? = null

        fun save(b: Bundle) {
            b.putString("path", path)
            b.putInt("count", filesCount)
            b.putLong("size", totalSize)
            if (lastModDate != null) b.putString(
                "mod_date",
                SimpleDateFormat.getDateTimeInstance().format(lastModDate)
            )
        }

        fun load(b: Bundle) {
            path = b.getString("path")
            filesCount = b.getInt("count")
            totalSize = b.getLong("size")
            try {
                val s = b.getString("mod_date")
                if (s != null) lastModDate = SimpleDateFormat.getDateTimeInstance().parse(s)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
        }


        fun copy(): FilesInfo? {
            return try {
                clone() as FilesInfo
            } catch (e: CloneNotSupportedException) {
                null
            }
        }
    }

    private var _sizeTextView: TextView? = null
    private var _numberOfFilesTextView: TextView? = null
    private var _fullPathTextView: TextView? = null
    private var _modDateTextView: TextView? = null
    private var _lastInfo: FilesInfo? = null

    private val calcPropertiesCallbacks: TaskCallbacks = object : TaskCallbacks {
        override fun onUpdateUI(state: Any) {
            _lastInfo = state as FilesInfo
            updateUI(_lastInfo!!, false)
        }

        override fun onSuspendUI(args: Bundle) {
        }

        override fun onResumeUI(args: Bundle) {
        }

        override fun onPrepare(args: Bundle) {
        }

        override fun onCompleted(args: Bundle, result: Result) {
            try {
                if (!result.isCancelled) {
                    _lastInfo = result.result as FilesInfo
                    updateUI(_lastInfo!!, true)
                }
            } catch (e: Throwable) {
                Logger.showAndLog(activity, e)
            }
        }
    }

    private fun updateUI(info: FilesInfo, isLast: Boolean) {
        val ctx = activity ?: return
        var tmp = Formatter
            .formatFileSize(ctx, info.totalSize)
        if (!isLast) tmp = ">=$tmp"
        _sizeTextView!!.text = tmp

        tmp = info.filesCount.toLong().toString()
        if (!isLast) tmp = ">=$tmp"
        _numberOfFilesTextView!!.text = tmp

        if (info.lastModDate != null) {
            val df = DateFormat
                .getDateFormat(ctx)
            val tf = DateFormat
                .getTimeFormat(ctx)
            tmp = (df.format(info.lastModDate) + " "
                    + tf.format(info.lastModDate))
            if (!isLast) tmp = ">=$tmp"
        } else tmp = ""
        _modDateTextView!!.text = tmp
        _fullPathTextView!!.text = if (info.path != null) info.path else ""
    }

    private fun cancelCalcTask() {
        val fm = fragmentManager ?: return
        val tf = fm.findFragmentByTag(CalcPropertiesTaskFragment.TAG) as TaskFragment
        tf?.cancel()
    }

    private fun startCalcTask() {
        cancelCalcTask()
        val fm = fragmentManager
        fm?.beginTransaction()?.add(
            CalcPropertiesTaskFragment.newInstance(
                arguments
            ), CalcPropertiesTaskFragment.TAG
        )?.commit()
    }

    companion object {
        const val TAG: String = "FilePropertiesFragment"

        const val ARG_CURRENT_PATH: String = "current_path"

        fun newInstance(currentPath: Path?): FilePropertiesFragment {
            val args = Bundle()
            if (currentPath != null) args.putString(ARG_CURRENT_PATH, currentPath.pathString)
            val f = FilePropertiesFragment()
            f.arguments = args
            return f
        }
    }
}
