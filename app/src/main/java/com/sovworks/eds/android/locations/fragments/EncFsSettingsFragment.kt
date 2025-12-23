package com.sovworks.eds.android.locations.fragments

import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.EncFsLocationBase
import com.sovworks.eds.android.locations.opener.fragments.EDSLocationOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.tasks.ChangeEncFsPasswordTask

class EncFsSettingsFragment : EDSLocationSettingsFragment() {
    override fun getLocation(): EncFsLocationBase? {
        return super.getLocation() as EncFsLocationBase
    }

    override fun createChangePasswordTaskInstance(): TaskFragment {
        return ChangeEncFsPasswordTask()
    }

    override fun getLocationOpener(): LocationOpenerBaseFragment {
        return EDSLocationOpenerFragment()
    }
}
