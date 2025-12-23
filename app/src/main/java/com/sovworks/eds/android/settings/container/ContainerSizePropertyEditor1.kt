package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.IntPropertyEditor

class ContainerSizePropertyEditor(createContainerFragment: CreateContainerFragmentBase) :
    IntPropertyEditor(
        createContainerFragment,
        R.string.container_size,
        R.string.container_size_desc,
        createContainerFragment.tag
    ) {
    override fun loadValue(): Int {
        return hostFragment.state.getInt(CreateContainerTaskFragmentBase.ARG_SIZE, 10)
    }

    override fun saveValue(value: Int) {
        hostFragment.state.putInt(CreateContainerTaskFragmentBase.ARG_SIZE, value)
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase
}
