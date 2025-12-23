package com.sovworks.eds.android.filemanager.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment
import com.sovworks.eds.fs.util.StringPathUtil

class RenameFileDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val alert = Builder(activity)
        alert.setMessage(getString(R.string.enter_new_file_name))

        // Set an EditText view to get user input
        val filename = arguments.getString(ARG_FILENAME)
        val input = EditText(activity)
        input.id = android.R.id.edit
        input.setSingleLine()
        input.setText(filename)
        val spu = StringPathUtil(filename)
        val fnWoExt = spu.fileNameWithoutExtension
        if (fnWoExt.length > 0) input.setSelection(0, fnWoExt.length)
        alert.setView(input)

        alert.setPositiveButton(
            getString(android.R.string.ok)
        ) { dialog: DialogInterface?, whichButton: Int -> renameFile(input.text.toString()) }

        alert.setNegativeButton(
            android.R.string.cancel
        ) { dialog: DialogInterface?, whichButton: Int -> }

        return alert.create()
    }

    private fun renameFile(newName: String) {
        val frag =
            fragmentManager.findFragmentByTag(FileListViewFragment.TAG) as FileListViewFragment
        if (frag != null) {
            val prevName = arguments.getString(ARG_CURRENT_PATH)
            frag.renameFile(prevName, newName)
        }
    }

    companion object {
        const val TAG: String = "RenameFileDialog"
        @JvmStatic
        fun showDialog(fm: FragmentManager?, currentPath: String?, fileName: String?) {
            val newFragment: DialogFragment = RenameFileDialog()
            val b = Bundle()
            b.putString(ARG_CURRENT_PATH, currentPath)
            b.putString(ARG_FILENAME, fileName)
            newFragment.arguments = b
            newFragment.show(fm, TAG)
        }

        private const val ARG_CURRENT_PATH = "com.sovoworks.eds.android.PATH"
        private const val ARG_FILENAME = "com.sovoworks.eds.android.FILENAME"
    }
}
