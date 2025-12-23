package com.sovworks.eds.android.settings.activities

import android.app.Fragment
import android.content.Intent
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.settings.PropertiesHostWithStateBundle
import com.sovworks.eds.android.settings.fragments.OpeningOptionsFragment

class OpeningOptionsActivity : SettingsBaseActivity() {
    override fun onBackPressed() {
        val frag =
            fragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG) as PropertiesHostWithStateBundle
        try {
            frag.propertiesView.saveProperties()
            val res = Intent()
            res.putExtras(frag.state)
            setResult(RESULT_OK, res)
            super.onBackPressed()
        } catch (e: Exception) {
            Logger.showAndLog(this, e)
        }
    }

    override fun getSettingsFragment(): Fragment {
        return openingOptionsFragment
    }

    private val openingOptionsFragment: Fragment
        get() = OpeningOptionsFragment()
}
