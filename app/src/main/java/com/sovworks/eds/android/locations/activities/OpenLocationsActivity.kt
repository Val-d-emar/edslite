package com.sovworks.eds.android.locations.activities

import android.app.Fragment
import android.os.Bundle
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.helpers.AppInitHelper
import com.sovworks.eds.android.helpers.AppInitHelperBase.Companion.createObservable
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.LocationOpenerResultReceiver
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.trello.rxlifecycle2.components.RxActivity
import io.reactivex.functions.Action
import java.util.concurrent.CancellationException

class OpenLocationsActivity : RxActivity() {
    class MainFragment : Fragment(), LocationOpenerResultReceiver {
        override fun onCreate(state: Bundle?) {
            super.onCreate(state)
            _locationsManager = LocationsManager.getLocationsManager(activity)
            try {
                _targetLocations = if (state == null)
                    _locationsManager.getLocationsFromIntent(activity.intent)
                else
                    _locationsManager.getLocationsFromBundle(state)
            } catch (e: Exception) {
                Logger.showAndLog(activity, e)
            }
            onFirstStart()
        }


        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            LocationsManager.storeLocationsInBundle(outState, _targetLocations)
        }

        override fun onTargetLocationOpened(openerArgs: Bundle, location: Location) {
            if (!_targetLocations!!.isEmpty()) _targetLocations!!.removeAt(0)
            openNextLocation()
        }

        override fun onTargetLocationNotOpened(openerArgs: Bundle) {
            if (!_targetLocations!!.isEmpty()) _targetLocations!!.removeAt(0)
            if (_targetLocations!!.isEmpty()) {
                activity.setResult(RESULT_CANCELED)
                activity.finish()
            } else openNextLocation()
        }

        protected fun onFirstStart() {
            openNextLocation()
        }

        private var _targetLocations: ArrayList<Location>? = null
        private var _locationsManager: LocationsManager? = null

        private fun openNextLocation() {
            if (_targetLocations!!.isEmpty()) {
                activity.setResult(RESULT_OK)
                activity.finish()
            } else {
                val loc = _targetLocations!![0]
                val openerTag = LocationOpenerBaseFragment.getOpenerTag(loc)
                if (fragmentManager.findFragmentByTag(openerTag) != null) return
                loc.externalSettings.isVisibleToUser = true
                loc.saveExternalSettings()
                val args = Bundle()
                setOpenerArgs(args, loc)
                val opener = LocationOpenerBaseFragment.getDefaultOpenerForLocation
                (loc)
                opener.arguments = args
                fragmentManager.beginTransaction
                ().add
                (
                        opener,
                openerTag
                ).commit
                ()
            }
        }

        protected fun setOpenerArgs(args: Bundle, loc: Location) {
            args.putAll(activity.intent.extras)
            args.putString(LocationOpenerBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, tag)
            LocationsManager.storePathsInBundle(args, loc, null)
        }

        companion object {
            const val TAG: String =
                "com.sovworks.eds.android.locations.activities.OpenLocationsActivity.MainFragment"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Util.setTheme(this)
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        AppInitHelper.createObservable
        (this).compose
        (bindToLifecycle<Any>()).subscribe
        (Action { this.addMainFragment() }, io.reactivex.functions.Consumer<kotlin.Throwable?> { err: Throwable? ->
            if (err !is CancellationException) Logger.log(err)
        })
    }

    protected fun createFragment(): MainFragment {
        return MainFragment()
    }

    protected fun addMainFragment() {
        val fm = fragmentManager
        if (fm.findFragmentByTag(MainFragment.TAG) == null) fm.beginTransaction()
            .add(createFragment(), MainFragment.TAG).commit()
    }
}
