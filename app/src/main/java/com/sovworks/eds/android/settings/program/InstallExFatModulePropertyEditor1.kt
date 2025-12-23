package com.sovworks.eds.android.settings.program

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getSelectPathIntent
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.helpers.ProgressDialogTaskFragmentCallbacks
import com.sovworks.eds.android.settings.ButtonPropertyEditor
import com.sovworks.eds.android.settings.PropertyEditor.Host
import com.sovworks.eds.fs.exfat.ExFat
import com.sovworks.eds.fs.std.StdFs
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.LocationsManagerBase
import com.sovworks.eds.settings.GlobalConfig
import java.io.IOException

class InstallExFatModulePropertyEditor(host: Host) :
    ButtonPropertyEditor(
        host,
        R.string.install_exfat_module,
        host.context.getString(R.string.install_exfat_module),
        host.context.getString(R.string.install_exfat_module_desc, GlobalConfig.EXFAT_MODULE_URL),
        host.context.getString(R.string.select_file)
    ) {
    class InstallExfatModuleTask : TaskFragment() {
        override fun initTask(activity: Activity) {
            _context = activity.applicationContext
        }

        @Throws(Exception::class)
        override fun doWork(uiUpdater: TaskState) {
            val moduleLocation = LocationsManager.getLocationsManager(_context).getFromBundle(
                arguments, null
            )
            if (!moduleLocation.currentPath.isFile) throw UserException(
                _context,
                R.string.file_not_found
            )

            val targetPath = ExFat.getModulePath()
            val targetFolderPath = StdFs.getStdFs().getPath(
                targetPath.parent
            )
            PathUtil.makeFullPath(targetFolderPath)
            Util.copyFile(
                moduleLocation.currentPath.file,
                targetFolderPath.directory,
                targetPath.name
            )
            if (!ExFat.isModuleInstalled() && !ExFat.isModuleIncompatible()) {
                ExFat.loadNativeLibrary()
                uiUpdater.setResult(true)
            } else uiUpdater.setResult(false)
        }

        override fun getTaskCallbacks(activity: Activity): TaskCallbacks? {
            return object : ProgressDialogTaskFragmentCallbacks(activity, R.string.loading) {
                override fun onCompleted(args: Bundle?, result: Result?) {
                    try {
                        if ((result!!.result as Boolean?)!!) Toast.makeText(
                            activity,
                            R.string.module_has_been_installed,
                            Toast.LENGTH_LONG
                        ).show()
                        else Toast.makeText(
                            activity,
                            R.string.restart_application,
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Throwable) {
                        Logger.showAndLog(_context, e)
                    }
                }
            }
        }

        private var _context: Context? = null

        companion object {
            const val TAG: String = "InstallExfatModuleTask"

            fun newInstance(moduleLocationUri: Uri?): InstallExfatModuleTask {
                val args = Bundle()
                args.putParcelable(LocationsManagerBase.PARAM_LOCATION_URI, moduleLocationUri)
                val f = InstallExfatModuleTask()
                f.arguments = args
                return f
            }
        }
    }

    override fun onButtonClick() {
        try {
            requestActivity(selectPathIntent, SELECT_PATH_REQ_CODE)
        } catch (e: Exception) {
            Logger.showAndLog(host.context, e)
        }
    }

    override fun onPropertyRequestResult(propertyRequestCode: Int, resultCode: Int, data: Intent?) {
        if (propertyRequestCode == SELECT_PATH_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) onPathSelected(
            data
        )
    }

    @get:Throws(IOException::class)
    private val selectPathIntent: Intent
        get() = FileManagerActivity.getSelectPathIntent(
            host.context,
            null,
            false,
            true,
            false,
            false,
            true,
            true
        )

    private fun onPathSelected(result: Intent) {
        val uri = result.data
        host.getFragmentManager
        ().beginTransaction
        ().add
        (InstallExfatModuleTask.newInstance(uri), InstallExfatModuleTask.Companion.TAG)
        .commit()
    }

    companion object {
        private const val SELECT_PATH_REQ_CODE = 1
    }
}
