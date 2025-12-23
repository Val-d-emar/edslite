package com.sovworks.eds.android.activities

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import com.sovworks.eds.android.helpers.CompatHelper
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.settings.UserSettings

abstract class SettingsBaseActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        Util.setTheme(this)
        super.onCreate(savedInstanceState)
        if (UserSettings.getSettings(this).isFlagSecureEnabled) CompatHelper.setWindowFlagSecure(
            this
        )
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .add(android.R.id.content, getSettingsFragment(), SETTINGS_FRAGMENT_TAG)
                .commit()
        }
    }

    protected abstract val settingsFragment: Fragment

    companion object {
        const val SETTINGS_FRAGMENT_TAG: String =
            "com.sovworks.eds.android.locations.SETTINGS_FRAGMENT"
    }
}
