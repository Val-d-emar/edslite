package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.SwitchPropertyEditor

class ExternalFileIVPropertyEditor(hostFragment: CreateEDSLocationFragment?) :
    SwitchPropertyEditor(
        hostFragment,
        R.string.enable_filename_to_file_iv_chain,
        R.string.enable_filename_to_file_iv_chain_descr
    ) {
    override fun loadValue(): Boolean {
        return hostFragment.state.getBoolean(CreateEncFsTaskFragment.ARG_EXTERNAL_IV, false)
    }

    override fun saveValue(value: Boolean) {
        hostFragment.state.putBoolean(CreateEncFsTaskFragment.ARG_EXTERNAL_IV, value)
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase
}
