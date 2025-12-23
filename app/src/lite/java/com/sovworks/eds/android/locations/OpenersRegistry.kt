package com.sovworks.eds.android.locations

import com.sovworks.eds.android.locations.opener.fragments.ContainerOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.EDSLocationOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.EncFSOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerFragment
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.Openable

object OpenersRegistry {
    fun getDefaultOpenerForLocation(location: Location?): LocationOpenerBaseFragment {
        if (location is ContainerLocation) return ContainerOpenerFragment()
        if (location is EncFsLocationBase) return EncFSOpenerFragment()
        if (location is EDSLocation) return EDSLocationOpenerFragment()
        if (location is Openable) return LocationOpenerFragment()
        return LocationOpenerBaseFragment()
    }
}
