package com.sovworks.eds.android.settings.container

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.SwitchPropertyEditor

abstract class FillFreeSpacePropertyEditorBase(createContainerFragment: CreateContainerFragmentBase?) :
    SwitchPropertyEditor(
        createContainerFragment,
        R.string.fill_free_space_with_random_data,
        0
    ) {
    override fun createView(parent: ViewGroup): View {
        val view = super.createView(parent)
        _titleTextView = view.findViewById<View>(R.id.title_edit) as TextView
        return view
    }

    override fun saveValue(value: Boolean) {
        hostFragment.state.putBoolean(CreateContainerTaskFragmentBase.ARG_FILL_FREE_SPACE, value)
    }


    override fun loadValue(): Boolean {
        _titleTextView!!.setText(R.string.fill_free_space_with_random_data)
        return hostFragment.state.getBoolean(CreateContainerTaskFragmentBase.ARG_FILL_FREE_SPACE)
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase

    protected var _titleTextView: TextView? = null
}
