package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.SwitchPropertyEditor

class EnableEmptyBlocksPropertyEditor(hostFragment: CreateEDSLocationFragment?) :
    SwitchPropertyEditor(
        hostFragment,
        R.string.allow_empty_blocks,
        R.string.allow_empty_blocks_descr
    ) {
    override fun loadValue(): Boolean {
        return hostFragment.state.getBoolean(CreateEncFsTaskFragment.ARG_ALLOW_EMPTY_BLOCKS, true)
    }

    override fun saveValue(value: Boolean) {
        hostFragment.state.putBoolean(CreateEncFsTaskFragment.ARG_ALLOW_EMPTY_BLOCKS, value)
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase
}
