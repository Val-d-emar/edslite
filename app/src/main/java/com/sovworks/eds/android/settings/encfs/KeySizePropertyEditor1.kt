package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor

class KeySizePropertyEditor(hostFragment: CreateEDSLocationFragment) : ChoiceDialogPropertyEditor(
    hostFragment,
    R.string.key_size,
    R.string.key_size_descr,
    hostFragment.tag
) {
    override fun loadValue(): Int {
        return (hostFragment.state.getInt(CreateEncFsTaskFragment.ARG_KEY_SIZE, 16) * 8 - 128) / 64
    }

    override fun saveValue(value: Int) {
        hostFragment.state.putInt(CreateEncFsTaskFragment.ARG_KEY_SIZE, (128 + value * 64) / 8)
    }

    override fun getEntries(): List<String> {
        val res = ArrayList<String>()
        var i = 128
        while (i <= 256) {
            res.add(i.toString())
            i += 64
        }
        return res
    }

    protected val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
