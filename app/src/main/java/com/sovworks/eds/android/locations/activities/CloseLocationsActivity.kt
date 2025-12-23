package com.sovworks.eds.android.locations.activities

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.dialogs.MasterPasswordDialog.Companion.checkMasterPasswordIsSet
import com.sovworks.eds.android.dialogs.MasterPasswordDialog.Companion.checkSettingsKey
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.helpers.ActivityResultHandler
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.CloseLocationReceiver
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

class CloseLocationsActivity : Activity() {
    class MainFragment : Fragment(), CloseLocationReceiver, PasswordReceiver {
        override fun onCreate(state: Bundle?) {
            super.onCreate(state)
            _locationsManager = LocationsManager.getLocationsManager(activity)
            _failedToClose = state != null && state.getBoolean(ARG_FAILED_TO_CLOSE_ALL)
            if (checkMasterPasswordIsSet(activity, fragmentManager, tag)) startClosingLocations(
                state
            )
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            LocationsManager.storeLocationsInBundle(outState, _targetLocations)
            outState.putBoolean(ARG_FAILED_TO_CLOSE_ALL, _failedToClose)
        }

        override fun onTargetLocationClosed(location: Location, closeTaskArgs: Bundle) {
            if (!_targetLocations!!.isEmpty()) _targetLocations!!.removeAt(0)
            closeNextLocation()
        }

        override fun onTargetLocationNotClosed(location: Location, closeTaskArgs: Bundle) {
            _failedToClose = true
            if (!_targetLocations!!.isEmpty()) _targetLocations!!.removeAt(0)
            closeNextLocation()
        }

        override fun onPause() {
            _resHandler.onPause()
            super.onPause()
        }

        override fun onResume() {
            super.onResume()
            _resHandler.handle()
        }

        private val _resHandler = ActivityResultHandler()
        private var _targetLocations: ArrayList<Location>? = null
        private var _locationsManager: LocationsManager? = null
        private var _failedToClose = false

        private fun startClosingLocations(state: Bundle?) {
            try {
                if (state == null) {
                    val i = activity.intent
                    if (i != null && (i.data != null || i.hasExtra(LocationsManager.PARAM_LOCATION_URIS))) _targetLocations =
                        _locationsManager!!.getLocationsFromIntent(i)
                    else {
                        _targetLocations = ArrayList()
                        for (l in _locationsManager!!.locationsClosingOrder) _targetLocations!!.add(
                            l
                        )
                    }
                } else _targetLocations = _locationsManager!!.getLocationsFromBundle(state)
                closeNextLocation()
            } catch (e: Exception) {
                Logger.showAndLog(activity, e)
            }
        }

        private fun closeNextLocation() {
            if (_targetLocations!!.isEmpty()) {
                activity.setResult(if (_failedToClose) RESULT_CANCELED else RESULT_OK)
                activity.finish()
            } else {
                val loc = _targetLocations!![0]
                val closerTag = LocationCloserBaseFragment.getCloserTag(loc)
                if (fragmentManager.findFragmentByTag(closerTag) != null) return
                val args = Bundle()
                args.putString(LocationCloserBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, tag)
                LocationsManager.storePathsInBundle(args, loc, null)
                val i = activity.intent
                if (i.hasExtra(LocationCloserBaseFragment.ARG_FORCE_CLOSE)) args.putBoolean(
                    LocationCloserBaseFragment.ARG_FORCE_CLOSE,
                    i.getBooleanExtra(LocationCloserBaseFragment.ARG_FORCE_CLOSE, false)
                )
                val closer = LocationCloserBaseFragment.getDefaultCloserForLocation(loc)
                closer.arguments = args
                fragmentManager.beginTransaction().add(closer, closerTag).commit()
            }
        }

        //master passsword is set
        override fun onPasswordEntered(dlg: PasswordDialog?) {
            startClosingLocations(null)
        }

        //master passsword is not set
        override fun onPasswordNotEntered(dlg: PasswordDialog?) {
            if (checkSettingsKey(activity)) startClosingLocations(null)
            else activity.finish()
        }

        companion object {
            const val TAG: String =
                "com.sovworks.eds.android.locations.activities.CloseLocationsActivity.MainFragment"

            private const val ARG_FAILED_TO_CLOSE_ALL = "com.sovworks.eds.android.FAILED_TO_CLOSE"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.setTheme(this)
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (savedInstanceState == null) fragmentManager.beginTransaction()
            .add(MainFragment(), MainFragment.TAG).commit()
    }
}
