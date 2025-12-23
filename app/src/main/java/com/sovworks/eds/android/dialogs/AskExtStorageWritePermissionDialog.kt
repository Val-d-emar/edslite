package com.sovworks.eds.android.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.DialogInterface
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.opener.fragments.ExternalStorageOpenerFragment
import com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment


class AskExtStorageWritePermissionDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val builder = Builder(activity)
        builder.setMessage(R.string.ext_storage_write_permission_request)
            .setPositiveButton(
                R.string.grant
            ) { dialog: DialogInterface, id: Int ->
                dialog.dismiss()
                val f = recFragment
                if (f != null) f.showSystemDialog()
            }
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog: DialogInterface?, id: Int ->
                val f =
                    recFragment
                if (f != null) f.setDontAskPermissionAndOpenLocation()
            }
        return builder.create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        val f = recFragment
        if (f != null) f.cancelOpen()
    }

    private val recFragment: ExternalStorageOpenerFragment
        get() {
            return fragmentManager.findFragmentByTag
            (
                    arguments.getString(
                        LocationOpenerBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG
                    )
                    ) as ExternalStorageOpenerFragment?
        }

    companion object {
        @JvmStatic
        fun showDialog(fm: FragmentManager?, openerTag: String?) {
            val args = Bundle()
            args.putString(LocationOpenerBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, openerTag)
            val newFragment: DialogFragment = AskExtStorageWritePermissionDialog()
            newFragment.arguments = args
            newFragment.show(fm, "AskExtStorageWritePermissionDialog")
        }
    }
}
