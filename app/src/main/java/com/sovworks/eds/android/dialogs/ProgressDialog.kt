package com.sovworks.eds.android.dialogs

import android.app.DialogFragment
import android.app.FragmentManager
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.sovworks.eds.android.R
import com.sovworks.eds.android.helpers.Util

class ProgressDialog : DialogFragment() {
    fun setProgress(progress: Int) {
        if (_progressBar != null) _progressBar!!.progress = progress
    }

    fun setTitle(title: CharSequence?) {
        if (_titleTextView != null) _titleTextView!!.text = title
    }

    fun setText(text: CharSequence?) {
        if (_statusTextView != null) _statusTextView!!.text = text
    }

    fun setOnCancelListener(listener: OnCancelListener?) {
        _cancelListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.setDialogStyle(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val v = inflater.inflate(R.layout.progress_dialog, container)
        _titleTextView = v.findViewById(android.R.id.text1)
        _statusTextView = v.findViewById(android.R.id.text2)
        _progressBar = v.findViewById(android.R.id.progress)
        setTitle(arguments.getString(ARG_TITLE))
        return v
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (_cancelListener != null) _cancelListener!!.onCancel(dialog)
    }

    private var _cancelListener: OnCancelListener? = null
    private var _statusTextView: TextView? = null
    private var _titleTextView: TextView? = null
    private var _progressBar: ProgressBar? = null


    companion object {
        const val TAG: String = "ProgressDialog"
        const val ARG_TITLE: String = "com.sovworks.eds.android.TITLE"

        @JvmStatic
		fun showDialog(fm: FragmentManager?, title: String?): ProgressDialog {
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            val d = ProgressDialog()
            d.arguments = args
            d.show(fm, TAG)
            return d
        }
    }
}
