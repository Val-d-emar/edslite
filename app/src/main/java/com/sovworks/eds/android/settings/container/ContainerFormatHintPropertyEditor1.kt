package com.sovworks.eds.android.settings.container

import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragment
import com.sovworks.eds.android.locations.fragments.ContainerSettingsFragmentBase
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.truecrypt.FormatInfo

class ContainerFormatHintPropertyEditor(containerSettingsFragment: ContainerSettingsFragmentBase) :
    ChoiceDialogPropertyEditor(
        containerSettingsFragment,
        R.string.container_format,
        R.string.container_format_desc,
        containerSettingsFragment.tag
    ) {
    override fun getHost(): ContainerSettingsFragment {
        return super.getHost() as ContainerSettingsFragment
    }

    override fun loadValue(): Int {
        val cfi = host.currentContainerFormat
        if (cfi == null) {
            showHints(null)
            return 0
        }
        showHints(cfi)
        val supportedFormats = host.getLocation()!!
            .supportedFormats
        var i = 0
        val l = supportedFormats.size
        while (i < l) {
            if (cfi.formatName.equals(
                    supportedFormats[i].formatName,
                    ignoreCase = true
                )
            ) return if (l > 1) i + 1 else i
            i++
        }
        return 0
    }

    override fun saveValue(value: Int) {
        val selectedFormat = getSelectedFormat(value)
        host.getLocation()!!.externalSettings.containerFormatName = selectedFormat?.formatName
        host.saveExternalSettings()
        host.propertiesView.loadProperties()
    }

    override fun getEntries(): ArrayList<String> {
        val supportedFormats = host.getLocation()!!
            .supportedFormats
        val entries = ArrayList<String>()
        if (supportedFormats.size > 1) entries.add("-")
        for (cfi in supportedFormats) entries.add(cfi.formatName)
        return entries
    }

    private fun getSelectedFormat(pos: Int): ContainerFormatInfo? {
        val supportedFormats = host.getLocation()!!
            .supportedFormats
        if (supportedFormats.size == 1) return supportedFormats[0]
        if (pos <= 0 || pos >= supportedFormats.size + 1) return null
        return supportedFormats[pos - 1]
    }

    private fun showHints(selectedFormat: ContainerFormatInfo?) {
        if (selectedFormat == null) disableAlgHints()
        else {
            val cfn = selectedFormat.formatName
            if (cfn.equals(
                    FormatInfo.FORMAT_NAME,
                    ignoreCase = true
                ) || cfn.equals(
                    com.sovworks.eds.veracrypt.FormatInfo.formatName,
                    ignoreCase = true
                )
            ) enableAlgHints()
            else disableAlgHints()
        }
    }

    private fun disableAlgHints() {
        host.propertiesView.setPropertyState(R.string.encryption_algorithm, false)
        host.propertiesView.setPropertyState(R.string.hash_algorithm, false)
    }

    private fun enableAlgHints() {
        host.propertiesView.setPropertyState(R.string.encryption_algorithm, true)
        host.propertiesView.setPropertyState(R.string.hash_algorithm, true)
    }
}
