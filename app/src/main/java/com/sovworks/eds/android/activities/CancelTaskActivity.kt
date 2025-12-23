package com.sovworks.eds.android.activities

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import com.sovworks.eds.android.R.layout
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.service.FileOpsService

class CancelTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Util.setDialogStyle(this)
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(layout.cancel_task_activity)
    }

    fun onYesClick(v: View?) {
        FileOpsService.cancelTask(this)
    }

    fun onNoClick(v: View?) {
        finish()
    }

    companion object {
        const val ACTION_CANCEL_TASK = "com.sovworks.eds.android.CANCEL_TASK"

        @JvmStatic
        fun getCancelTaskIntent(context: Context?, taskId: Int): Intent {
            val i = Intent(context, CancelTaskActivity::class.java)
            i.setAction(ACTION_CANCEL_TASK)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra(FileOpsService.INTENT_PARAM_TASK_ID, taskId)
            return i
        }

        fun getCancelTaskPendingIntent(context: Context?, taskId: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                taskId,
                getCancelTaskIntent(context, taskId),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
