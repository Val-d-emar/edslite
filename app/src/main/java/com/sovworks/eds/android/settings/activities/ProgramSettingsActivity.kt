package com.sovworks.eds.android.settings.activities

import android.app.Fragment
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.settings.fragments.ProgramSettingsFragment

class ProgramSettingsActivity : SettingsBaseActivity() {
    override fun getSettingsFragment(): Fragment {
        return ProgramSettingsFragment()
    }
}
