package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertiesHostWithLocation
import com.sovworks.eds.android.settings.SwitchPropertyEditor
import com.sovworks.eds.locations.EDSLocation

class OpenInReadOnlyModePropertyEditor(host: PropertiesHostWithLocation?) :
    SwitchPropertyEditor(host, R.string.open_read_only, 0) {
    override fun getHost(): PropertiesHostWithLocation {
        return super.getHost() as PropertiesHostWithLocation
    }

    override fun saveValue(value: Boolean) {
        location.externalSettings.setOpenReadOnly(value)
        if (host.propertiesView.isInstantSave) location.saveExternalSettings()
    }

    override fun loadValue(): Boolean {
        return location.externalSettings.shouldOpenReadOnly()
    }

    private val location: EDSLocation
        get() = host.targetLocation as EDSLocation
}
