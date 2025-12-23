package com.sovworks.eds.android.locations.tasks

import android.app.Activity
import android.content.Context
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.container.ContainerFormatterBase
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

abstract class AddExistingEDSLocationTaskFragment : TaskFragment() {
    override fun initTask(activity: Activity) {
        _context = activity.applicationContext
    }

    protected var _context: Context? = null

    @Throws(Exception::class)
    override fun doWork(state: TaskState) {
        val lm = LocationsManager.getLocationsManager(_context)
        val location = lm.getFromBundle(arguments, null)
        state.setResult(findOrCreateEDSLocation(lm, location, arguments.getBoolean(ARG_STORE_LINK)))
    }

    override fun getTaskCallbacks(activity: Activity?): TaskCallbacks? {
        val f =
            fragmentManager.findFragmentByTag(SettingsBaseActivity.SETTINGS_FRAGMENT_TAG) as CreateEDSLocationFragment
        return f?.addExistingEDSLocationTaskCallbacks
    }

    @Throws(Exception::class)
    protected fun findOrCreateEDSLocation(
        lm: LocationsManager,
        locationLocation: Location,
        storeLink: Boolean
    ): EDSLocation {
        val loc = createEDSLocation(locationLocation)
        val exCont = lm.findExistingLocation(loc) as EDSLocation
        if (exCont != null) {
            if (lm.isStoredLocation(exCont.id) && exCont.javaClass == loc.javaClass) {
                exCont.externalSettings.isVisibleToUser = true
                if (storeLink) exCont.saveExternalSettings()
                return exCont
            } else lm.removeLocation(exCont)
        }
        addEDSLocation(lm, loc, storeLink)
        setLocationSettings(loc, storeLink)
        return loc
    }

    protected fun setLocationSettings(loc: EDSLocation, storeLink: Boolean) {
        loc.externalSettings.title =
            ContainerFormatterBase.makeTitle(loc, LocationsManager.getLocationsManager(_context))
        loc.externalSettings.isVisibleToUser = true
        if (storeLink) loc.saveExternalSettings()
    }

    @Throws(Exception::class)
    protected fun addEDSLocation(lm: LocationsManager, loc: EDSLocation?, storeLink: Boolean) {
        lm.replaceLocation(loc, loc, storeLink)
    }

    @Throws(Exception::class)
    protected abstract fun createEDSLocation(locationLocation: Location): EDSLocation

    companion object {
        protected const val ARG_STORE_LINK: String = "com.sovworks.eds.android.STORE_LINK"
    }
}
