package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragment
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.container.VolumeLayoutBase
import com.sovworks.eds.crypto.EncryptionEngine

class EncEngineHintPropertyEditor(containerSettingsFragment: ContainerSettingsFragmentBase) :
    ChoiceDialogPropertyEditor(
        containerSettingsFragment,
        R.string.encryption_algorithm,
        R.string.encryption_alg_desc,
        containerSettingsFragment.tag
    ) {
    override fun getHost(): ContainerSettingsFragment {
        return super.getHost() as ContainerSettingsFragment
    }

    override fun saveValue(value: Int) {
        if (value == 0) host.getLocation()!!.externalSettings.encEngineName = null
        else host.getLocation()!!.externalSettings.encEngineName =
            getEncEngineName(supportedEncEngines[value - 1])
        host.saveExternalSettings()
    }

    override fun loadValue(): Int {
        val name = host.getLocation()!!.externalSettings.encEngineName
        if (name != null) {
            val i = findEngineIndexByName(name)
            if (i >= 0) return i + 1
        }
        return 0
    }

    override fun getEntries(): ArrayList<String> {
        val entries = ArrayList<String>()
        entries.add("-")
        for (ee in supportedEncEngines) entries.add(getEncEngineName(ee))
        return entries
    }

    private fun getEncEngineName(ee: EncryptionEngine): String {
        return VolumeLayoutBase.getEncEngineName(ee)
    }

    private fun findEngineIndexByName(name: String): Int {
        var i = 0
        for (ee in supportedEncEngines) {
            if (name.equals(getEncEngineName(ee), ignoreCase = true)) return i
            i++
        }
        return -1
    }

    private val supportedEncEngines: List<EncryptionEngine>
        get() {
            val cfi = host.currentContainerFormat
            return if (cfi != null)
                cfi.volumeLayout.supportedEncryptionEngines
            else emptyList()
        }
}
