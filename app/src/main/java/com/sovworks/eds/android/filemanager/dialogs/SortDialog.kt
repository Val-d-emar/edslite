package com.sovworks.eds.android.filemanager.dialogs

import android.annotation.SuppressLint
import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.app.FragmentManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.RadioGroup
import com.sovworks.eds.android.R

class SortDialog : DialogFragment() {
    interface SortingReceiver {
        fun applySort(sortMode: Int)
    }

    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        @SuppressLint("InflateParams") val v =
            activity.layoutInflater.inflate(R.layout.sort_dialog, null)
        val listView = v.findViewById<ListView>(android.R.id.list)
        val sortDirection = v.findViewById<RadioGroup>(R.id.sort_group)
        listView.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_list_item_single_choice,
            activity.resources.getStringArray(
                arguments.getInt(ARG_SORT_LABELS_RES_ID, R.array.sort_mode)
            )
        )
        val sortMode = arguments.getInt(ARG_SORT_MODE)
        listView.setItemChecked(sortMode / 2, true)
        val asc = sortMode % 2 == 0
        sortDirection.check(if (asc) R.id.sort_asc else R.id.sort_desc)

        val alert = Builder(activity)
        alert.setTitle(R.string.sort)
            .setView(v)
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, whichButton ->
                var pos = listView.checkedItemPosition
                if (pos == ListView.INVALID_POSITION) pos = -1
                applySort(pos, sortDirection.checkedRadioButtonId == R.id.sort_asc)
                dialog.dismiss()
            }
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog, whichButton ->
                // Canceled.
            }
        return alert.create()
    }


    protected fun applySort(listPos: Int, isAscending: Boolean) {
        val sortMode = listPos * 2 + (if (isAscending) 0 else 1)
        val rft = arguments.getString(ARG_RECEIVER_FRAGMENT_TAG)
        if (rft != null) {
            val sr = fragmentManager.findFragmentByTag(rft) as SortingReceiver
            sr?.applySort(sortMode)
        } else if (activity is SortingReceiver) (activity as SortingReceiver).applySort(sortMode)
    }

    companion object {
        @JvmStatic
        fun showDialog(fm: FragmentManager?, sortMode: Int, receiverFragmentTag: String?) {
            showDialog(fm, sortMode, R.array.sort_mode, receiverFragmentTag)
        }

        fun showDialog(
            fm: FragmentManager?,
            sortMode: Int,
            sortLabelsResId: Int,
            receiverFragmentTag: String?
        ) {
            val newFragment: DialogFragment = SortDialog()
            val b = Bundle()
            b.putInt(ARG_SORT_MODE, sortMode)
            b.putInt(ARG_SORT_LABELS_RES_ID, sortLabelsResId)
            if (receiverFragmentTag != null) b.putString(
                ARG_RECEIVER_FRAGMENT_TAG,
                receiverFragmentTag
            )
            newFragment.arguments = b
            newFragment.show(fm, "SortDialog")
        }

        private const val ARG_SORT_MODE = "com.sovworks.eds.android.SORT_MODE"
        private const val ARG_SORT_LABELS_RES_ID = "com.sovworks.eds.android.SORT_LABELS_RES_ID"
        private const val ARG_RECEIVER_FRAGMENT_TAG =
            "com.sovworks.eds.android.RECEIVER_FRAGMENT_TAG"
    }
}
