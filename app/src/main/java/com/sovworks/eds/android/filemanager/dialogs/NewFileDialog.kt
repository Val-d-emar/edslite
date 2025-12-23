package com.sovworks.eds.android.filemanager.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.tasks.CreateNewFile

class NewFileDialog : DialogFragment() {
    interface Receiver {
        fun makeNewFile(name: String?, type: Int)
    }

    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val ft = arguments.getInt(ARG_TYPE)

        val alert = Builder(activity)

        //alert.setMessage(getString(R.string.enter_new_file_name));

        // Set an EditText view to get user input
        val input = EditText(activity)
        input.setSingleLine()
        input.hint = getString(
            if (ft == CreateNewFile.FILE_TYPE_FOLDER)
                R.string.enter_new_folder_name
            else
                R.string.enter_new_file_name
        )
        alert.setView(input)

        alert.setPositiveButton(
            getString(android.R.string.ok)
        ) { dialog: DialogInterface, whichButton: Int ->
            val r =
                fragmentManager.findFragmentByTag(
                    arguments.getString(ARG_RECEIVER_TAG)
                ) as Receiver
            r?.makeNewFile(
                input.text.toString(),
                arguments.getInt(ARG_TYPE)
            )
            dialog.dismiss()
        }

        alert.setNegativeButton(
            android.R.string.cancel
        ) { dialog: DialogInterface?, whichButton: Int -> }

        return alert.create()
    }

    companion object {
        private const val ARG_TYPE = "com.sovworks.eds.android.TYPE"
        private const val ARG_RECEIVER_TAG = "com.sovworks.eds.android.RECEIVER_TAG"

        @JvmStatic
        fun showDialog(fm: FragmentManager?, type: Int, receiverTag: String?) {
            val newFragment: DialogFragment = NewFileDialog()
            val b = Bundle()
            b.putInt(ARG_TYPE, type)
            b.putString(ARG_RECEIVER_TAG, receiverTag)
            newFragment.arguments = b
            newFragment.show(fm, "NewFileDialog")
        }
    }
}
