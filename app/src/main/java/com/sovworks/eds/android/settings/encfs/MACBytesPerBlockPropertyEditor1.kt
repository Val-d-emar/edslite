package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.SwitchPropertyEditor

class MACBytesPerBlockPropertyEditor(hostFragment: CreateEDSLocationFragment?) :
    SwitchPropertyEditor(
        hostFragment,
        R.string.mac_bytes_per_block,
        R.string.mac_bytes_per_block_descr
    ) {
    override fun loadValue(): Boolean {
        return hostFragment.state.getInt(CreateEncFsTaskFragment.ARG_MAC_BYTES, 0) > 0
    }

    override fun saveValue(value: Boolean) {
        if (value) hostFragment.state.putInt(CreateEncFsTaskFragment.ARG_MAC_BYTES, 8)
        else hostFragment.state.remove(CreateEncFsTaskFragment.ARG_MAC_BYTES)
    }


    protected val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
