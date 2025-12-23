package com.sovworks.eds.android.settings

import com.sovworks.eds.android.settings.PropertyEditor.Host
import com.sovworks.eds.locations.Location

interface PropertiesHostWithLocation : Host {
    val targetLocation: Location?
}
