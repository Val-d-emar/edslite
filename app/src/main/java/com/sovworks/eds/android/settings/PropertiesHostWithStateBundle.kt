package com.sovworks.eds.android.settings

import android.os.Bundle
import com.sovworks.eds.android.settings.PropertyEditor.Host

interface PropertiesHostWithStateBundle : Host {
    val state: Bundle
}
