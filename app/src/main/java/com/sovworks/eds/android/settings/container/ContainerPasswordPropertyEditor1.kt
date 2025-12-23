package com.sovworks.eds.android.settings.container

import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.settings.ButtonPropertyEditor
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.Openable

class ContainerPasswordPropertyEditor(createEDSLocationFragment: CreateEDSLocationFragment?) :
    ButtonPropertyEditor(
        createEDSLocationFragment,
        R.string.container_password,
        0,
        R.string.change
    ), PasswordReceiver {
    override fun onPasswordEntered(dlg: PasswordDialog) {
        hostFragment.state.putParcelable(Openable.PARAM_PASSWORD, SecureBuffer(dlg.password))
    }

    override fun onPasswordNotEntered(dlg: PasswordDialog?) {}

    override fun onButtonClick() {
        val args = Bundle()
        args.putBoolean(PasswordDialog.ARG_HAS_PASSWORD, true)
        args.putBoolean(PasswordDialog.ARG_VERIFY_PASSWORD, true)
        args.putInt(ARG_PROPERTY_ID, id)
        args.putString(PasswordDialog.ARG_RECEIVER_FRAGMENT_TAG, hostFragment.tag)
        val pd = PasswordDialog()
        pd.arguments = args
        pd.show(host.fragmentManager, PasswordDialog.TAG)
    }

    val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
