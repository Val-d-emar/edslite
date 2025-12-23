package com.sovworks.eds.android.helpers

import android.app.Activity
import android.app.Dialog
import android.app.DialogFragment
import android.app.ProgressDialog
import android.os.Bundle
import com.sovworks.eds.android.fragments.TaskFragment.Result
import com.sovworks.eds.android.fragments.TaskFragment.TaskCallbacks

open class ProgressDialogTaskFragmentCallbacks(
    @JvmField protected val _context: Activity,
    private val _dialogTextResId: Int
) :
    TaskCallbacks {
    class Dialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle): android.app.Dialog {
            val dialog = ProgressDialog(activity)
            dialog.setMessage(arguments.getString(ARG_DIALOG_TEXT))
            dialog.isIndeterminate = true
            dialog.setCancelable(false)
            return dialog
        }

        companion object {
            const val TAG: String = "ProgressDialog"
            const val ARG_DIALOG_TEXT: String = "dialog_text"

            fun newInstance(dialogText: String?): Dialog {
                val args = Bundle()
                args.putString(ARG_DIALOG_TEXT, dialogText)
                val d = Dialog()
                d.arguments = args
                return d
            }
        }
    }

    override fun onPrepare(args: Bundle?) {
    }

    override fun onResumeUI(args: Bundle?) {
        _dialog = initDialog(args)
        if (_dialog != null) _dialog!!.show(_context.fragmentManager, Dialog.TAG)
    }

    override fun onSuspendUI(args: Bundle?) {
        if (_dialog != null) _dialog!!.dismissAllowingStateLoss()
    }

    override fun onUpdateUI(state: Any?) {
    }

    override fun onCompleted(args: Bundle?, result: Result?) {
    }

    protected fun initDialog(args: Bundle?): DialogFragment {
        return Dialog.newInstance(_context.getText(_dialogTextResId).toString())
    }

    private var _dialog: DialogFragment? = null
}
