package com.sovworks.eds.android.locations.fragments

import android.os.Bundle
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.opener.fragments.ContainerOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment
import com.sovworks.eds.android.settings.container.ContainerFormatHintPropertyEditor
import com.sovworks.eds.android.settings.container.EncEngineHintPropertyEditor
import com.sovworks.eds.android.settings.container.HashAlgHintPropertyEditor
import com.sovworks.eds.android.tasks.ChangeContainerPasswordTask
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable

open class ContainerSettingsFragmentBase : EDSLocationSettingsFragment() {
    override fun getLocation(): ContainerLocation? {
        return super.getLocation() as ContainerLocation
    }

    val currentContainerFormat: ContainerFormatInfo?
        get() {
            val supportedFormats =
                location!!.supportedFormats
            return if (supportedFormats.size == 1) supportedFormats[0] else EdsContainer.findFormatByName(
                supportedFormats,
                location!!.externalSettings.containerFormatName
            )
        }

    override fun createChangePasswordTaskInstance(): TaskFragment {
        return ChangeContainerPasswordTask()
    }

    override fun getLocationOpener(): LocationOpenerBaseFragment {
        return ContainerOpenerFragment()
    }

    override fun createStdProperties(ids: MutableCollection<Int?>) {
        super.createStdProperties(ids)
        createHintProperties(ids)
    }

    override fun getChangePasswordTaskArgs(dlg: PasswordDialog): Bundle {
        val args = Bundle()
        args.putAll(dlg.options)
        args.putParcelable(Openable.PARAM_PASSWORD, SecureBuffer(dlg.password))
        LocationsManager.storePathsInBundle(args, location, null)
        return args
    }

    protected fun createHintProperties(ids: MutableCollection<Int?>) {
        ids.add(_propertiesView!!.addProperty(ContainerFormatHintPropertyEditor(this)))
        ids.add(_propertiesView!!.addProperty(EncEngineHintPropertyEditor(this)))
        ids.add(_propertiesView!!.addProperty(HashAlgHintPropertyEditor(this)))
    }
}
