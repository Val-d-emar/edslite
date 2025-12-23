package com.sovworks.eds.android.locations.activities

import android.R
import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.CompatHelperBase.Companion.setWindowFlagSecure
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.locations.ContainerBasedLocation
import com.sovworks.eds.android.locations.DocumentTreeLocation
import com.sovworks.eds.android.locations.fragments.ContainerListFragment
import com.sovworks.eds.android.locations.fragments.DocumentTreeLocationsListFragment
import com.sovworks.eds.android.settings.UserSettings

abstract class LocationListActivityBase : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        Util.setTheme(this)
        super.onCreate(savedInstanceState)
        if (UserSettings.getSettings(this).isFlagSecureEnabled) CompatHelper.setWindowFlagSecure(
            this
        )
        if (savedInstanceState == null) fragmentManager.beginTransaction
        ().add
        (R.id.content, getCreateLocationFragment(), LocationListBaseFragment.TAG).commit
        ()
    }

    protected val createLocationFragment: Fragment
        get() {
            return when (intent.getStringExtra(EXTRA_LOCATION_TYPE)) {
                ContainerBasedLocation.URI_SCHEME -> ContainerListFragment()
                DocumentTreeLocation.URI_SCHEME -> DocumentTreeLocationsListFragment()
                else -> throw RuntimeException("Unknown location type")
            }
        }

    companion object {
        const val EXTRA_LOCATION_TYPE: String = "com.sovworks.eds.android.LOCATION_TYPE"
    }
}
