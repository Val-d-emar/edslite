package com.sovworks.eds.android.settings.encfs

import android.app.Fragment
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.IntPropertyEditor
import com.sovworks.eds.android.settings.PropertiesHostWithStateBundle
import com.sovworks.eds.locations.Openable

class NumKDFIterationsPropertyEditor(hostFragment: PropertiesHostWithStateBundle) :
    IntPropertyEditor(
        hostFragment,
        R.string.number_of_kdf_iterations,
        R.string.number_of_kdf_iterations_descr,
        (hostFragment as Fragment).tag
    ) {
    override fun getHost(): PropertiesHostWithStateBundle {
        return super.getHost() as PropertiesHostWithStateBundle
    }

    override fun loadValue(): Int {
        return host.state.getInt(Openable.PARAM_KDF_ITERATIONS, 100000)
    }


    override fun saveValue(value: Int) {
        var value = value
        if (value < 1000) value = 1000
        host.state.putInt(Openable.PARAM_KDF_ITERATIONS, value)
    }
}
