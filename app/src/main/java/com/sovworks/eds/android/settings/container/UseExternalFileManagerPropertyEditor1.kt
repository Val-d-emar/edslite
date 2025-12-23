package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertiesHostWithLocation
import com.sovworks.eds.android.settings.SwitchPropertyEditor
import com.sovworks.eds.locations.Location

class UseExternalFileManagerPropertyEditor(host: PropertiesHostWithLocation?) :
    SwitchPropertyEditor(host, R.string.use_external_file_manager, 0) {
    override fun getHost(): PropertiesHostWithLocation {
        return super.getHost() as PropertiesHostWithLocation
    }

    override fun saveValue(value: Boolean) {
        location.externalSettings.setUseExtFileManager(value)
        if (host.propertiesView.isInstantSave) location.saveExternalSettings()
    }

    override fun loadValue(): Boolean {
        return location.externalSettings.useExtFileManager()
    }

    private val location: Location
        get() = host.targetLocation
}
