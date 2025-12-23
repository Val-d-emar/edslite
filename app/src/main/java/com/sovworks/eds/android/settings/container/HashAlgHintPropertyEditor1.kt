package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragment
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import java.security.MessageDigest

class HashAlgHintPropertyEditor(containerSettingsFragment: ContainerSettingsFragmentBase) :
    ChoiceDialogPropertyEditor(
        containerSettingsFragment,
        R.string.hash_algorithm,
        R.string.hash_alg_desc,
        containerSettingsFragment.tag
    ) {
    override fun getHost(): ContainerSettingsFragment {
        return super.getHost() as ContainerSettingsFragment
    }

    override fun saveValue(value: Int) {
        if (value == 0) host.getLocation()!!.externalSettings.hashFuncName = null
        else host.getLocation()!!.externalSettings.hashFuncName =
            getHashFuncName(supportedHashFuncs[value - 1])
        host.saveExternalSettings()
    }

    override fun loadValue(): Int {
        val name = host.getLocation()!!.externalSettings.hashFuncName
        if (name != null) {
            val i = findEngineIndexByName(name)
            if (i >= 0) return i + 1
        }
        return 0
    }

    override fun getEntries(): ArrayList<String> {
        val entries = ArrayList<String>()
        entries.add("-")

        for (hf in supportedHashFuncs) entries.add(getHashFuncName(hf))
        return entries
    }

    private fun getHashFuncName(hf: MessageDigest): String {
        return hf.algorithm
    }

    private fun findEngineIndexByName(name: String): Int {
        var i = 0
        for (md in supportedHashFuncs) {
            if (name.equals(getHashFuncName(md), ignoreCase = true)) return i
            i++
        }
        return -1
    }

    private val supportedHashFuncs: List<MessageDigest>
        get() {
            val cfi = host.currentContainerFormat
            return if (cfi != null)
                cfi.volumeLayout.supportedHashFuncs
            else
                ArrayList()
        }
}
