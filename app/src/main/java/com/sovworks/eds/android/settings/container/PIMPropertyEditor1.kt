package com.sovworks.eds.android.settings.container

import android.app.Fragment
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.IntPropertyEditor
import com.sovworks.eds.android.settings.PropertiesHostWithStateBundle
import com.sovworks.eds.locations.Openable

class PIMPropertyEditor(hostFragment: PropertiesHostWithStateBundle) : IntPropertyEditor(
    hostFragment,
    R.string.kdf_iterations_multiplier,
    R.string.number_of_kdf_iterations_veracrypt_descr,
    (hostFragment as Fragment).tag
) {
    override fun getHost(): PropertiesHostWithStateBundle {
        return super.getHost() as PropertiesHostWithStateBundle
    }

    override fun loadValue(): Int {
        val `val` = host.state.getInt(Openable.PARAM_KDF_ITERATIONS, 0)
        return if (`val` < 0) 0 else `val`
    }


    override fun saveValue(value: Int) {
        var value = value
        if (value < 0) value = 0
        else if (value > 100000) value = 100000
        host.state.putInt(Openable.PARAM_KDF_ITERATIONS, value)
    }
}
