package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.CreateContainerFragment
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.android.settings.encfs.DataCodecPropertyEditor
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.EDSLocationFormatter
import com.sovworks.eds.container.EdsContainer

abstract class ContainerFormatPropertyEditorBase(
    createContainerFragment: CreateContainerFragmentBase,
    descResId: Int
) :
    ChoiceDialogPropertyEditor(
        createContainerFragment,
        R.string.container_format,
        descResId,
        createContainerFragment.tag
    ) {
    override fun getEntries(): List<String> {
        val res = ArrayList<String>()
        for (i in EdsContainer.getSupportedFormats()) res.add(i.formatName)
        res.add(EDSLocationFormatter.FORMAT_ENCFS)
        return res
    }

    override fun loadValue(): Int {
        val formatId =
            getFormatIdFromName(hostFragment.state.getString(CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT))
        val addExisting =
            hostFragment.state.getBoolean(CreateEDSLocationFragment.ARG_ADD_EXISTING_LOCATION)
        val isEncFs = isEncFs(formatId)
        hostFragment.propertiesView.beginUpdate()
        try {
            updateEncFsProperties(isEncFs && !addExisting)
            updateContainerFormatProperties(
                !isEncFs && !addExisting,
                getSelectedContainerFormatInfo(formatId)!!
            )
            updateCommonProperties(addExisting)
        } finally {
            hostFragment.propertiesView.endUpdate(null)
        }
        //getHostFragment().getPropertiesView().loadProperties();
        return formatId
    }


    override fun saveValue(value: Int) {
        val addExisting =
            hostFragment.state.getBoolean(CreateEDSLocationFragment.ARG_ADD_EXISTING_LOCATION)
        hostFragment.propertiesView.beginUpdate()
        try {
            if (isEncFs(value)) {
                hostFragment.state.putString(
                    CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT,
                    EDSLocationFormatter.FORMAT_ENCFS
                )
                updateContainerFormatProperties(false, null)
                updateEncFsProperties(!addExisting)
            } else {
                val cfi = getSelectedContainerFormatInfo(value)
                if (cfi != null) hostFragment.state.putString(
                    CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT,
                    cfi.formatName
                )
                updateEncFsProperties(false)
                updateContainerFormatProperties(!addExisting, cfi!!)
            }
            updateCommonProperties(addExisting)
        } finally {
            hostFragment.propertiesView.endUpdate(null)
        }
        //getHostFragment().getPropertiesView().loadProperties();
    }

    private fun isEncFs(formatId: Int): Boolean {
        return formatId == EdsContainer.getSupportedFormats().size
    }

    protected val hostFragment: CreateContainerFragmentBase
        get() = host as CreateContainerFragment

    protected fun getFormatIdFromName(formatName: String?): Int {
        if (formatName == null) return 0
        val supportedFormats = EdsContainer.getSupportedFormats()
        if (EDSLocationFormatter.FORMAT_ENCFS == formatName) return supportedFormats.size
        for (i in supportedFormats.indices) {
            val cfi = supportedFormats[i]
            if (cfi.formatName.equals(formatName, ignoreCase = true)) return i
        }
        return 0
    }

    protected fun getSelectedContainerFormatInfo(formatId: Int): ContainerFormatInfo? {
        val fmts = EdsContainer.getSupportedFormats()
        return if (formatId >= fmts.size) null else fmts[formatId]
    }

    protected fun updateContainerFormatProperties(enable: Boolean, cfi: ContainerFormatInfo) {
        val pm = host.propertiesView
        pm.setPropertyState(R.string.container_size, enable)
        pm.setPropertyState(
            R.string.kdf_iterations_multiplier,
            enable && cfi.hasCustomKDFIterationsSupport()
        )
        pm.setPropertyState(R.string.encryption_algorithm, enable)
        pm.setPropertyState(R.string.hash_algorithm, enable)
        pm.setPropertyState(R.string.fill_free_space_with_random_data, enable)
        pm.setPropertyState(R.string.file_system_type, enable)
    }

    private fun updateEncFsProperties(enable: Boolean) {
        val pm = host.propertiesView
        pm.setPropertyState(DataCodecPropertyEditor.Companion.ID, enable)
        pm.setPropertyState(R.string.filename_encryption_algorithm, enable)
        pm.setPropertyState(R.string.block_size, enable)
        pm.setPropertyState(R.string.allow_empty_blocks, enable)
        pm.setPropertyState(R.string.enable_per_file_iv, enable)
        pm.setPropertyState(R.string.enable_filename_iv_chain, enable)
        pm.setPropertyState(R.string.key_size, enable)
        pm.setPropertyState(R.string.mac_bytes_per_block, enable)
        pm.setPropertyState(R.string.number_of_kdf_iterations, enable)
        pm.setPropertyState(R.string.add_rand_bytes, enable)
    }


    protected fun updateCommonProperties(addExisting: Boolean) {
        val pm = host.propertiesView
        pm.setPropertyState(R.string.path_to_container, true)
        pm.setPropertyState(R.string.container_password, !addExisting)
    }
}
