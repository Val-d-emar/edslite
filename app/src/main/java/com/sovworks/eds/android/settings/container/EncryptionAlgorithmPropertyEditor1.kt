package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.container.VolumeLayoutBase
import com.sovworks.eds.crypto.EncryptionEngine
import com.sovworks.eds.crypto.FileEncryptionEngine
import com.sovworks.eds.truecrypt.EncryptionEnginesRegistry

class EncryptionAlgorithmPropertyEditor(createContainerFragment: CreateContainerFragmentBase) :
    ChoiceDialogPropertyEditor(
        createContainerFragment,
        R.string.encryption_algorithm,
        0,
        createContainerFragment.tag
    ) {
    override fun loadValue(): Int {
        val algs = currentEncAlgList
        val encAlgName =
            hostFragment.state.getString(CreateContainerTaskFragmentBase.ARG_CIPHER_NAME)
        val encModeName =
            hostFragment.state.getString(CreateContainerTaskFragmentBase.ARG_CIPHER_MODE_NAME)
        if (encAlgName != null && encModeName != null) {
            val ee = VolumeLayoutBase.findCipher(algs, encAlgName, encModeName)
            return algs.indexOf(ee)
        } else if (!algs.isEmpty()) return 0
        else return -1
    }

    override fun saveValue(value: Int) {
        val algs = currentEncAlgList
        val ee = algs[value]
        hostFragment.state.putString(CreateContainerTaskFragmentBase.ARG_CIPHER_NAME, ee.cipherName)
        hostFragment.state.putString(
            CreateContainerTaskFragmentBase.ARG_CIPHER_MODE_NAME,
            ee.cipherModeName
        )
    }

    override fun getEntries(): ArrayList<String> {
        val res = ArrayList<String>()
        val supportedEngines = currentEncAlgList
        if (supportedEngines != null) {
            for (eng in supportedEngines) res.add(getEncEngineName(eng))
        }
        return res
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragmentBase

    private val currentEncAlgList: List<EncryptionEngine>
        get() {
            val vl =
                hostFragment.selectedVolumeLayout
            return if (vl != null) vl.supportedEncryptionEngines else emptyList<FileEncryptionEngine>()
        }

    companion object {
        fun getEncEngineName(eng: EncryptionEngine?): String {
            return EncryptionEnginesRegistry.getEncEngineName(eng)
        }
    }
}
