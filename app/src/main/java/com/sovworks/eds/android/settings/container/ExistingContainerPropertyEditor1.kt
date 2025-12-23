package com.sovworks.eds.android.settings.container

import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import com.sovworks.eds.android.R
import com.sovworks.eds.android.fragments.PropertiesFragmentBase.getPropertiesView
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragmentBase
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragmentBase.showAddExistingLocationProperties
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragmentBase.showCreateNewLocationProperties
import com.sovworks.eds.android.settings.PropertyEditorBase

class ExistingContainerPropertyEditor(createEDSLocationFragment: CreateEDSLocationFragmentBase?) :
    PropertyEditorBase(
        createEDSLocationFragment,
        R.layout.settings_create_new_or_existing_container,
        R.string.create_new_container_or_add_existing_container,
        0
    ) {
    override fun createView(parent: ViewGroup): View {
        val view = super.createView(parent)
        view.findViewById<View>(R.id.create_new_container_button)
            .setOnClickListener(object : OnClickListener {
                override fun onClick(view: View) {
                    this.hostFragment.showCreateNewLocationProperties()
                    this.hostFragment.getPropertiesView().loadProperties()
                }
            })
        view.findViewById<View>(R.id.add_existing_container_button)
            .setOnClickListener(object : OnClickListener {
                override fun onClick(view: View) {
                    this.hostFragment.showAddExistingLocationProperties()
                    host.propertiesView.loadProperties()
                }
            })
        return view
    }

    protected val hostFragment: CreateEDSLocationFragment
        get() = host as CreateEDSLocationFragment
}
