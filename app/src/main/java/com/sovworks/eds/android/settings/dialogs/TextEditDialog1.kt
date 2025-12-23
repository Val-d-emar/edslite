package com.sovworks.eds.android.settings.dialogs

import android.app.AlertDialog.Builder
import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor
import com.sovworks.eds.android.settings.views.PropertiesView

class TextEditDialog : DialogFragment() {
    interface TextResultReceiver {
        @Throws(Exception::class)
        fun setResult(text: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alert = Builder(activity)
        val mid = arguments.getInt(ARG_MESSAGE_ID)
        if (mid != 0) alert.setMessage(getString(mid))
        val inflater = LayoutInflater.from(activity)

        _input = inflater.inflate(
            arguments.getInt(ARG_EDIT_TEXT_RES_ID, R.layout.settings_edit_text),
            null
        ) as EditText
        _input!!.setText(
            if (savedInstanceState == null) arguments.getString(ARG_TEXT) else savedInstanceState.getString(
                ARG_TEXT
            )
        )
        alert.setView(_input)

        alert.setPositiveButton(
            getString(android.R.string.ok)
        ) { dialog, whichButton ->
            val host = PropertiesView.getHost(this@TextEditDialog)
            if (host != null) {
                val pe = host.propertiesView.getPropertyById(
                    arguments.getInt(PropertyEditor.ARG_PROPERTY_ID)
                )
                if (pe != null) try {
                    (pe as TextResultReceiver).setResult(_input!!.text.toString())
                } catch (e: Exception) {
                    Logger.showAndLog(activity, e)
                }
            }
        }

        /*alert.setNegativeButton(android.R.string.cancel,
        new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                // Canceled.
            }
        });*/
        return alert.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_TEXT, _input!!.text.toString())
    }

    private var _input: EditText? = null


    companion object {
        const val TAG: String = "TextEditDialog"

        const val ARG_TEXT: String = "com.sovworks.eds.android.ARG_TEXT"
        const val ARG_MESSAGE_ID: String = "com.sovworks.eds.android.ARG_MESSAGE_ID"
        const val ARG_EDIT_TEXT_RES_ID: String = "com.sovworks.eds.android.EDIT_TEXT_RES_ID"
    }
}
