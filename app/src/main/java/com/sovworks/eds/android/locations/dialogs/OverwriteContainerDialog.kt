package com.sovworks.eds.android.locations.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.SettingsBaseActivity
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment

class OverwriteContainerDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val args = arguments
        val resId = args?.getInt(
            ARG_REQUEST_RES_ID,
            R.string.do_you_want_to_overwrite_existing_file
        )
            ?: R.string.do_you_want_to_overwrite_existing_file
        val builder = Builder(activity)
        builder.setMessage(resId)
            .setPositiveButton(
                R.string.yes
            ) { dialog, id ->
                dialog.dismiss()
                doOverwrite()
            }
            .setNegativeButton(
                R.string.no
            ) { dialog, id -> dialog.cancel() }
        return builder.create()
    }

    protected fun doOverwrite() {
        val f =
            fragmentManager.findFragmentByTag(SettingsBaseActivity.SETTINGS_FRAGMENT_TAG) as CreateEDSLocationFragment
        if (f != null) {
            f.setOverwrite(true)
            f.startCreateLocationTask()
        }
    }

    companion object {
        @JvmOverloads
        fun showDialog(fm: FragmentManager?, requestResId: Int = 0) {
            val newFragment: DialogFragment = OverwriteContainerDialog()
            if (requestResId > 0) {
                val args = Bundle()
                args.putInt(ARG_REQUEST_RES_ID, requestResId)
                newFragment.arguments = args
            }
            newFragment.show(
                fm,
                "com.sovworks.eds.android.locations.dialogs.OverwriteContainerDialog"
            )
        }

        private const val ARG_REQUEST_RES_ID = "com.sovworks.eds.android.TEXT_ID"
    }
}
