package com.sovworks.eds.android.locations.fragments

import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.LocationsManager

open class ContainerListFragmentBase : LocationListBaseFragment() {
    override fun loadLocations() {
        _locationsList!!.clear()
        for (loc in LocationsManager.getLocationsManager(activity)
            .getLoadedEDSLocations(true)) _locationsList!!.add(ContainerInfo(loc))
    }

    override val defaultLocationType: String?
        get() = ContainerBasedLocation.URI_SCHEME

    private inner class ContainerInfo(ci: EDSLocation) : LocationInfo() {
        init {
            location = ci
        }

        override fun hasSettings(): Boolean {
            return true
        }

        override val icon: Drawable?
            get() = if ((location as EDSLocation).isOpenOrMounted) this.openedContainerIcon else this.closedContainerIcon
    }

    @get:Synchronized
    private val openedContainerIcon: Drawable?
        get() {
            if (_openedContainerIcon == null) {
                val typedValue = TypedValue()
                activity.theme.resolveAttribute(R.attr.lockOpenIcon, typedValue, true)
                _openedContainerIcon =
                    activity.resources.getDrawable(typedValue.resourceId)
            }
            return _openedContainerIcon
        }

    @get:Synchronized
    private val closedContainerIcon: Drawable?
        get() {
            if (_closedContainerIcon == null) {
                val typedValue = TypedValue()
                activity.theme.resolveAttribute(R.attr.lockIcon, typedValue, true)
                _closedContainerIcon =
                    activity.resources.getDrawable(typedValue.resourceId)
            }
            return _closedContainerIcon
        }

    companion object {
        private var _openedContainerIcon: Drawable? = null
        private var _closedContainerIcon: Drawable? = null
    }
}
