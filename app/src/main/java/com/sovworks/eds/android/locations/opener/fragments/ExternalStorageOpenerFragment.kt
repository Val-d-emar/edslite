package com.sovworks.eds.android.locations.opener.fragments

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.widget.Toast
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.AskExtStorageWritePermissionDialog
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.DocumentTreeLocation
import com.sovworks.eds.android.locations.ExternalStorageLocation
import com.sovworks.eds.android.locations.opener.fragments.ExternalStorageOpenerFragment.CheckLocationWritableTaskFragment.Companion
import com.sovworks.eds.android.locations.opener.fragments.ExternalStorageOpenerFragment.CheckLocationWritableTaskFragment.ResultType.AskPermission
import com.sovworks.eds.android.locations.opener.fragments.ExternalStorageOpenerFragment.CheckLocationWritableTaskFragment.ResultType.DontAskPermission
import com.sovworks.eds.android.locations.opener.fragments.ExternalStorageOpenerFragment.CheckLocationWritableTaskFragment.ResultType.OK
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import java.io.File
import java.io.IOException

open class ExternalStorageOpenerFragment : LocationOpenerBaseFragment() {
    class CheckLocationWritableTaskFragment : TaskFragment() {
        internal enum class ResultType {
            OK,
            AskPermission,
            DontAskPermission
        }

        override fun initTask(activity: Activity?) {
            _lm = LocationsManager.getLocationsManager(activity)
        }

        @Throws(Throwable::class)
        override fun doWork(state: TaskState) {
            val loc = targetLocation
            val docTreeLocation = getDocTreeLocation(
                _lm!!, loc
            )
            if (docTreeLocation != null && docTreeLocation.fs.rootPath.exists()) state.setResult(OK)
            else state.setResult(if (isWritable(loc)) DontAskPermission else AskPermission)
        }

        private fun isWritable(loc: ExternalStorageLocation): Boolean {
            try {
                val res = File.createTempFile("eds", null, File(loc.rootPath))
                res.delete()
                return true
            } catch (ignored: IOException) {
            }
            return false
        }

        override fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
            val f =
                fragmentManager.findFragmentByTag(arguments.getString(OpenLocationTaskFragment.Companion.ARG_OPENER_TAG)) as ExternalStorageOpenerFragment
            return if (f == null) null else object : TaskCallbacks {
                override fun onPrepare(args: Bundle?) {
                }

                override fun onUpdateUI(state: Any?) {
                }

                override fun onResumeUI(args: Bundle?) {
                }

                override fun onSuspendUI(args: Bundle?) {
                }

                override fun onCompleted(args: Bundle?, result: Result?) {
                    try {
                        if (result!!.result === OK) f.openLocation()
                        else if (result!!.result === AskPermission) {
                            f.askWritePermission()
                            return
                        }
                    } catch (e: Throwable) {
                        Logger.log(e)
                    }
                    f.setDontAskPermission()
                    f.openLocation()
                }
            }
        }

        @get:Throws(Exception::class)
        private val targetLocation: ExternalStorageLocation
            get() {
                val locationUri =
                    arguments.getParcelable<Uri>(LocationsManager.PARAM_LOCATION_URI)
                return _lm!!.getLocation(locationUri) as ExternalStorageLocation
            }

        private var _lm: LocationsManager? = null

        companion object {
            const val TAG: String = "CheckLocationWritableTaskFragment"
        }
    }

    private fun setDontAskPermission() {
        val loc: ExternalStorageLocation? = targetLocation
        loc!!.externalSettings.setDontAskWritePermission(true)
        loc.saveExternalSettings()
    }

    fun setDontAskPermissionAndOpenLocation() {
        setDontAskPermission()
        openLocation()
    }

    fun cancelOpen() {
        finishOpener(false, null)
    }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    fun showSystemDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_CODE_ADD_LOCATION)
        Toast.makeText(activity, R.string.select_root_folder_tip, Toast.LENGTH_LONG).show()
    }

    @TargetApi(VERSION_CODES.KITKAT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE_ADD_LOCATION) {
            if (resultCode == Activity.RESULT_OK) {
                val treeUri = data.data
                if (treeUri != null) {
                    try {
                        activity.contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: SecurityException) {
                        Logger.log(e)
                    }

                    val loc = DocumentTreeLocation(activity.applicationContext, treeUri)
                    loc.externalSettings.isVisibleToUser = false
                    loc.saveExternalSettings()
                    LocationsManager.getLocationsManager(activity).addNewLocation(loc, true)
                    val tloc: ExternalStorageLocation? = targetLocation
                    tloc!!.externalSettings.documentsAPIUriString = loc.locationUri.toString()
                    tloc.saveExternalSettings()
                }
                openLocation()
            }
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun openLocation() {
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            val loc: ExternalStorageLocation? = targetLocation
            val docUri = loc!!.externalSettings.documentsAPIUriString
            if (docUri != null) {
                val lm = LocationsManager.getLocationsManager(activity)
                try {
                    val docLoc = lm.getLocation(Uri.parse(docUri))
                    finishOpener(true, docLoc)
                    return
                } catch (e: Exception) {
                    Logger.showAndLog(activity, e)
                }
            } else if (!loc.externalSettings.dontAskWritePermission()) {
                startCheckWritableTask(loc)
                return
            }
        }
        super.openLocation()
    }

    override val targetLocation: Location?
        get() = super.getTargetLocation() as ExternalStorageLocation

    fun getCheckWritableTaskTag(loc: Location): String {
        return CheckLocationWritableTaskFragment.TAG + loc.id
    }

    private fun startCheckWritableTask(loc: Location) {
        val args = Bundle()
        LocationsManager.storePathsInBundle(args, loc, null)
        args.putString(
            OpenLocationTaskFragment.Companion.ARG_OPENER_TAG,
            tag
        )
        val f: TaskFragment = CheckLocationWritableTaskFragment()
        f.arguments = args
        fragmentManager.beginTransaction().add(f, getCheckWritableTaskTag(loc)).commit()
    }

    private fun askWritePermission() {
        AskExtStorageWritePermissionDialog.showDialog(fragmentManager, tag)
    }

    companion object {
        private fun getDocTreeLocation(
            lm: LocationsManager,
            extLoc: ExternalStorageLocation
        ): DocumentTreeLocation? {
            val docUri = extLoc.externalSettings.documentsAPIUriString
            if (docUri != null) {
                try {
                    return lm.getLocation(Uri.parse(docUri)) as DocumentTreeLocation
                } catch (e: Exception) {
                    Logger.log(e)
                }
            }
            return null
        }

        private const val REQUEST_CODE_ADD_LOCATION = Activity.RESULT_FIRST_USER
    }
}
