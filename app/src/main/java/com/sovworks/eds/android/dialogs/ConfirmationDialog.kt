package com.sovworks.eds.android.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.Util

abstract class ConfirmationDialog : DialogFragment() {
    interface Receiver {
        fun onYes()
        fun onNo()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.setDialogStyle(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle): Dialog {
        val builder = Builder(activity)
        builder.setMessage(title)
            .setCancelable(false)
            .setPositiveButton(
                R.string.yes
            ) { dialog: DialogInterface?, id: Int ->
                onYes()
                dismiss()
            }
            .setNegativeButton(
                R.string.no
            ) { dialog: DialogInterface?, id: Int ->
                onNo()
                dismiss()
            }

        return builder.create()
    }

    protected fun onNo() {
        val rec = receiver
        rec?.onNo()
    }

    protected open fun onYes() {
        val rec = receiver
        rec?.onYes()
    }

    protected abstract val title: String?

    protected val receiver: Receiver?
        get() {
            val args = arguments
            val tag =
                args?.getString(ARG_RECEIVER_TAG)
            if (tag != null) {
                val f = fragmentManager.findFragmentByTag(tag)
                if (f is Receiver) return f
            } else {
                val act = activity
                if (act is Receiver) return act
            }
            return null
        }

    companion object {
        const val ARG_RECEIVER_TAG: String = "com.sovworks.eds.android.RECEIVER_TAG"
    }
}
