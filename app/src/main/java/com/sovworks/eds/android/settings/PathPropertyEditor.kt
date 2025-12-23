package com.sovworks.eds.android.settings

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getSelectPathIntent
import com.sovworks.eds.android.settings.PropertyEditor.Host
import java.io.IOException

abstract class PathPropertyEditor(
    host: Host?,
    titleResId: Int,
    descResId: Int,
    hostFragmentTag: String?
) :
    TextPropertyEditor(
        host,
        R.layout.settings_path_editor,
        titleResId,
        descResId,
        hostFragmentTag
    ) {
    override fun createView(parent: ViewGroup?): View {
        val view = super.createView(parent)
        val b = view!!.findViewById<View>(android.R.id.button2) as Button
        b?.setOnClickListener(object : OnClickListener {
            override fun onClick(v: View) {
                try {
                    requestActivity(
                        this.selectPathIntent,
                        SELECT_PATH_REQ_CODE
                    )
                } catch (e: Exception) {
                    Logger.showAndLog(host.context, e)
                }
            }
        })
        return view
    }

    override fun onPropertyRequestResult(propertyRequestCode: Int, resultCode: Int, data: Intent?) {
        if (propertyRequestCode == SELECT_PATH_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) onPathSelected(
            data
        )
    }

    protected fun onPathSelected(result: Intent) {
        val uri = result.data
        onTextChanged(uri?.toString() ?: "")
    }

    @get:Throws(IOException::class)
    protected open val selectPathIntent: Intent
        get() = FileManagerActivity.getSelectPathIntent(
            host.context,
            null,
            false,
            false,
            true,
            true,
            true,
            false
        )

    companion object {
        private const val SELECT_PATH_REQ_CODE = 1
    }
}
