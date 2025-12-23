package com.sovworks.eds.android.locations.activities

import android.app.Fragment
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.locations.TrueCryptLocation
import com.sovworks.eds.android.locations.VeraCryptLocation
import com.sovworks.eds.android.locations.fragments.CreateContainerFragment

class CreateLocationActivity : SettingsBaseActivity() {
    override fun getSettingsFragment(): Fragment {
        return createLocationFragment
    }

    private val createLocationFragment: Fragment
        get() = when (intent.getStringExtra(EXTRA_LOCATION_TYPE)) {
            VeraCryptLocation.URI_SCHEME, TrueCryptLocation.URI_SCHEME, ContainerBasedLocation.URI_SCHEME -> CreateContainerFragment()
            else -> throw RuntimeException("Unknown location type")
        }

    companion object {
        const val EXTRA_LOCATION_TYPE = "com.sovworks.eds.android.LOCATION_TYPE"
    }
}
