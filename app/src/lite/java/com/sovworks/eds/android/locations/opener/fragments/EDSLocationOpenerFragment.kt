package com.sovworks.eds.android.locations.opener.fragments

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.LocationOpenerResultReceiver
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerFragmentCommon.getTargetLocation
import com.sovworks.eds.android.providers.ContainersDocumentProviderBase.Companion.notifyOpenedLocationsListChanged
import com.sovworks.eds.android.service.LocationsService
import com.sovworks.eds.android.service.LocationsServiceBase.Companion.registerInactiveContainerCheck
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable

open class EDSLocationOpenerFragment : LocationOpenerFragment(), LocationOpenerResultReceiver {
    class OpenLocationTaskFragment

        :
        com.sovworks.eds.android.locations.opener.fragments.LocationOpenerFragmentCommon.OpenLocationTaskFragment() {
        @Throws(Exception::class)
        override fun openLocation(location: Openable, param: Bundle) {
            if (!location.isOpen) {
                super.openLocation(location, param)
                if (location is EDSLocation) {
                    LocationsService.registerInactiveContainerCheck(_context, location)
                    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) notifyOpenedLocationsListChanged(
                        _context!!
                    )
                }
            }
        }
    }

    override fun onTargetLocationOpened(openerArgs: Bundle?, location: Location?) {
        openLocation()
    }

    override fun onTargetLocationNotOpened(openerArgs: Bundle?) {
        finishOpener(false, null)
    }

    override fun openLocation() {
        val cbl = getTargetLocation()
        val baseLocation = cbl!!.location
        if (baseLocation is Openable && !baseLocation.isOpen) {
            val f = getDefaultOpenerForLocation(baseLocation)
            val b = Bundle()
            LocationsManager.storePathsInBundle(b, baseLocation, null)
            b.putString(PARAM_RECEIVER_FRAGMENT_TAG, tag)
            f.arguments = b
            fragmentManager.beginTransaction().add(f, getOpenerTag(baseLocation)).commit()
        } else super.openLocation()
    }

    protected override fun getTargetLocation(): EDSLocation? {
        return super.getTargetLocation() as EDSLocation?
    }

    protected override fun getOpenLocationTask(): TaskFragment {
        return OpenLocationTaskFragment()
    }
}
