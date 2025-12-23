package com.sovworks.eds.android.locations.activities

import android.app.Fragment
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.locations.EncFsLocationBase
import com.sovworks.eds.android.locations.LUKSLocation
import com.sovworks.eds.android.locations.TrueCryptLocation
import com.sovworks.eds.android.locations.VeraCryptLocation
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragment
import com.sovworks.eds.android.locations.fragments.EncFsSettingsFragment

class LocationSettingsActivity : SettingsBaseActivity() {
    override fun getSettingsFragment(): Fragment {
        return createLocationFragment
    }

    private val createLocationFragment: Fragment
        get() {
            val uri = intent.data
            if (uri == null || uri.scheme == null) throw RuntimeException("Location uri is not set")
            return when (uri.scheme) {
                EncFsLocationBase.URI_SCHEME -> EncFsSettingsFragment()
                VeraCryptLocation.URI_SCHEME, TrueCryptLocation.URI_SCHEME, LUKSLocation.URI_SCHEME, ContainerBasedLocation.URI_SCHEME -> ContainerSettingsFragment()
                else -> throw RuntimeException("Unknown location type")
            }
        }
}
