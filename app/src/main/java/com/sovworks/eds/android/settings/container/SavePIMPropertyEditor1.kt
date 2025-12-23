package com.sovworks.eds.android.settings.container

import android.app.DialogFragment
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragment
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragmentBase
import com.sovworks.eds.android.settings.SwitchPropertyEditor
import com.sovworks.eds.android.settings.dialogs.TextEditDialog
import com.sovworks.eds.android.settings.dialogs.TextEditDialog.TextResultReceiver

class SavePIMPropertyEditor(settingsFragment: EDSLocationSettingsFragmentBase?) :
    SwitchPropertyEditor(settingsFragment, R.string.remember_kdf_iterations_multiplier, 0),
    TextResultReceiver {
    override fun getHost(): EDSLocationSettingsFragment {
        return super.getHost() as EDSLocationSettingsFragment
    }

    override fun loadValue(): Boolean {
        val loc = host.location
        return !loc!!.requireCustomKDFIterations()
    }

    @Throws(Exception::class)
    override fun setResult(text: String) {
        var `val` = if (text.isEmpty()) 0 else text.toInt()
        if (`val` < 0) `val` = 0
        else if (`val` > 100000) `val` = 100000
        host.location!!.externalSettings.customKDFIterations = `val`
        host.saveExternalSettings()
    }

    override fun saveValue(value: Boolean) {
    }

    override fun onChecked(isChecked: Boolean): Boolean {
        if (isChecked) {
            startChangeValueDialog()
            return true
        } else {
            host.location!!.externalSettings.customKDFIterations = -1
            host.saveExternalSettings()
            return true
        }
    }

    protected fun startChangeValueDialog() {
        val args = initDialogArgs()
        val df: DialogFragment = TextEditDialog()
        df.arguments = args
        df.show(host.fragmentManager, TextEditDialog.Companion.TAG)
    }

    protected val dialogViewResId: Int
        get() = R.layout.settings_edit_num

    protected fun initDialogArgs(): Bundle {
        val b = Bundle()
        b.putInt(ARG_PROPERTY_ID, id)
        b.putInt(TextEditDialog.Companion.ARG_MESSAGE_ID, _titleResId)
        b.putInt(TextEditDialog.Companion.ARG_EDIT_TEXT_RES_ID, dialogViewResId)
        b.putString(ARG_HOST_FRAGMENT_TAG, host.tag)
        return b
    }
}
