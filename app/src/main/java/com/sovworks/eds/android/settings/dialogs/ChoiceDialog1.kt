package com.sovworks.eds.android.settings.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.os.Bundle
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.android.settings.PropertyEditor
import com.sovworks.eds.android.settings.views.PropertiesView

class ChoiceDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val host = PropertiesView.getHost(this@ChoiceDialog)
        val pe =
            host.propertiesView.getPropertyById(arguments.getInt(PropertyEditor.ARG_PROPERTY_ID)) as ChoiceDialogPropertyEditor
        val variants = arguments.getStringArrayList(ARG_VARIANTS)
        val strings: Array<String?> = variants?.toTypedArray<String>()
            ?: arrayOfNulls(0)
        val builder = Builder(activity)
            .setTitle(arguments.getString(ARG_TITLE))
            .setSingleChoiceItems(
                strings, pe.selectedEntry
            ) { dialog, item ->
                pe.selectedEntry = item
                dialog.dismiss()
            }
        return builder.create()
    }

    companion object {
        const val ARG_VARIANTS: String = "com.sovworks.eds.android.VARIANTS"
        const val ARG_TITLE: String = "com.sovworks.eds.android.TITLE"

        const val TAG: String = "ChoiceDialog"

        fun showDialog(
            fm: FragmentManager?,
            propertyId: Int,
            title: String?,
            variants: List<String>,
            hostFragmentTag: String?
        ) {
            val args = Bundle()
            args.putStringArrayList(ARG_VARIANTS, ArrayList(variants))
            args.putString(ARG_TITLE, title)
            args.putInt(PropertyEditor.ARG_PROPERTY_ID, propertyId)
            if (hostFragmentTag != null) args.putString(
                PropertyEditor.ARG_HOST_FRAGMENT_TAG,
                hostFragmentTag
            )
            val newFragment: DialogFragment = ChoiceDialog()
            newFragment.arguments = args
            newFragment.show(fm, TAG)
        }
    }
}
