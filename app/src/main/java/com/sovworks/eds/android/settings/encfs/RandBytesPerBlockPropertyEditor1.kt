package com.sovworks.eds.android.settings.encfs

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor

class RandBytesPerBlockPropertyEditor(hostFragment: CreateEDSLocationFragment) :
    ChoiceDialogPropertyEditor(
        hostFragment,
        R.string.add_rand_bytes,
        R.string.add_rand_bytes_descr,
        hostFragment.tag
    ) {
    override fun loadValue(): Int {
        return hostFragment.state.getInt(CreateEncFsTaskFragment.ARG_RAND_BYTES, 0)
    }

    override fun saveValue(value: Int) {
        hostFragment.state.putInt(CreateEncFsTaskFragment.ARG_RAND_BYTES, value)
    }

    override fun getEntries(): List<String> {
        val res = ArrayList<String>()
        res.add(host.context.getString(R.string.disable))
        for (i in 1..8) res.add(i.toString())
        return res
    }


    protected val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
