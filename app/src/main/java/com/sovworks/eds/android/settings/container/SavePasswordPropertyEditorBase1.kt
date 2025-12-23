package com.sovworks.eds.android.settings.container

import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragment
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragmentBase
import com.sovworks.eds.android.settings.SwitchPropertyEditor
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.Openable

open class SavePasswordPropertyEditorBase(settingsFragment: EDSLocationSettingsFragmentBase?) :
    SwitchPropertyEditor(settingsFragment, R.string.save_password, R.string.save_password_desc),
    PasswordReceiver {
    override fun getHost(): EDSLocationSettingsFragment {
        return super.getHost() as EDSLocationSettingsFragment
    }

    override fun loadValue(): Boolean {
        val loc = host.location
        return !loc!!.requirePassword()
    }

    override fun saveValue(value: Boolean) {
    }

    override fun onPasswordEntered(dlg: PasswordDialog) {
        val settings = host.location!!.externalSettings
        val sb = SecureBuffer(dlg.password)
        val data = sb.dataArray
        settings.password = data
        SecureBuffer.eraseData(data)
        sb.close()
        host.saveExternalSettings()
    }

    override fun onPasswordNotEntered(dlg: PasswordDialog?) {
        _switchButton.isChecked = false
    }

    override fun onChecked(isChecked: Boolean): Boolean {
        val loc: Openable? = host.location
        if (isChecked) {
            val args = Bundle()
            args.putBoolean(PasswordDialog.ARG_HAS_PASSWORD, loc!!.hasPassword())
            args.putString(PasswordDialog.ARG_RECEIVER_FRAGMENT_TAG, host.tag)
            args.putInt(ARG_PROPERTY_ID, id)
            val pd = PasswordDialog()
            pd.arguments = args
            pd.show(host.fragmentManager, PasswordDialog.TAG)
            return true
        } else {
            host.location!!.externalSettings.password = null
            host.saveExternalSettings()
            return true
        }
    }
}
