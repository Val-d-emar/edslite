package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragment
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.SwitchPropertyEditor

class FilenameIVChainingPropertyEditor(hostFragment: CreateContainerFragmentBase?) :
    SwitchPropertyEditor(
        hostFragment,
        R.string.enable_filename_iv_chain,
        R.string.enable_filename_iv_chain_descr
    ) {
    override fun loadValue(): Boolean {
        hostFragment.changeUniqueIVDependentOptions()
        return hostFragment.state.getBoolean(CreateEncFsTaskFragment.ARG_CHAINED_NAME_IV, true)
    }

    override fun saveValue(value: Boolean) {
        hostFragment.state.putBoolean(CreateEncFsTaskFragment.ARG_CHAINED_NAME_IV, value)
        hostFragment.changeUniqueIVDependentOptions()
    }

    protected val hostFragment: CreateContainerFragment
        get() = host as CreateContainerFragment
}
