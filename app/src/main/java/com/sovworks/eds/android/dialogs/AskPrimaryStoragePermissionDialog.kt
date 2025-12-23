package com.sovworks.eds.android.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.DialogInterface
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.fragments.ExtStorageWritePermisisonCheckFragment


class AskPrimaryStoragePermissionDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val builder = Builder(activity)
        builder.setMessage(R.string.storage_permission_desc)
            .setPositiveButton(
                R.string.grant
            ) { dialog: DialogInterface, id: Int ->
                dialog.dismiss()
                val stateFragment =
                    fragmentManager.findFragmentByTag(ExtStorageWritePermisisonCheckFragment.TAG) as ExtStorageWritePermisisonCheckFragment
                stateFragment?.requestExtStoragePermission()
            }
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog: DialogInterface?, id: Int ->
                val stateFragment =
                    fragmentManager.findFragmentByTag(ExtStorageWritePermisisonCheckFragment.TAG) as ExtStorageWritePermisisonCheckFragment
                stateFragment?.cancelExtStoragePermissionRequest()
            }
        return builder.create()
    }

    companion object {
        @JvmStatic
        fun showDialog(fm: FragmentManager?) {
            val newFragment: DialogFragment = AskPrimaryStoragePermissionDialog()
            newFragment.show(fm, "AskPrimaryStoragePermissionDialog")
        }
    }
}
