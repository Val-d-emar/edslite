package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.IntPropertyEditor

class BlockSizePropertyEditor(hostFragment: CreateEDSLocationFragment) : IntPropertyEditor(
    hostFragment,
    R.string.block_size,
    R.string.block_size_descr,
    hostFragment.tag
) {
    override fun loadValue(): Int {
        return hostFragment.state.getInt(CreateEncFsTaskFragment.ARG_BLOCK_SIZE, 1024)
    }

    override fun saveValue(value: Int) {
        var value = value
        value -= value % 64
        if (value < 64) value = 64
        if (value > 4096) value = 4096
        hostFragment.state.putInt(CreateEncFsTaskFragment.ARG_BLOCK_SIZE, value)
    }


    protected val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
