package com.sovworks.eds.android.settings.container

import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragment
import com.sovworks.eds.android.locations.fragments.EDSLocationSettingsFragmentBase
import com.sovworks.eds.android.settings.ButtonPropertyEditor
import com.sovworks.eds.android.tasks.ChangeContainerPasswordTask
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

class ChangePasswordPropertyEditor(settingsFragment: EDSLocationSettingsFragmentBase?) :
    ButtonPropertyEditor(
        settingsFragment,
        R.string.change_container_password,
        0,
        R.string.enter_new_password
    ),
    PasswordReceiver {
    override fun getHost(): EDSLocationSettingsFragment {
        return super.getHost() as EDSLocationSettingsFragment
    }

    override fun onButtonClick() {
        val args = Bundle()
        args.putBoolean(PasswordDialog.ARG_HAS_PASSWORD, true)
        args.putBoolean(PasswordDialog.ARG_VERIFY_PASSWORD, true)
        args.putInt(ARG_PROPERTY_ID, id)
        val loc: Location? = host.location
        LocationsManager.storePathsInBundle(args, loc, null)
        args.putString(PasswordDialog.ARG_RECEIVER_FRAGMENT_TAG, host.tag)
        val pd = PasswordDialog()
        pd.arguments = args
        pd.show(host.fragmentManager, PasswordDialog.TAG)
    }

    override fun onPasswordEntered(dlg: PasswordDialog) {
        host.resHandler.addResult {
            host.fragmentManager.beginTransaction
            ().add
            (
                    host.getChangePasswordTask(dlg),
            ChangeContainerPasswordTask.TAG).commit
            ()
        }
    }

    override fun onPasswordNotEntered(dlg: PasswordDialog?) {}
}
