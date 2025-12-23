package com.sovworks.eds.android.filemanager.dialogs

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import com.sovworks.eds.android.R
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import java.util.concurrent.CancellationException

object LoadingProgressDialog {
    fun createObservable(context: Context, isCancellable: Boolean): Completable {
        return Completable.create { emitter: CompletableEmitter ->
            val dialog = makeProgressDialog(context)
            dialog.setCancelable(isCancellable)
            if (isCancellable) dialog.setOnCancelListener { dialogInterface: DialogInterface? ->
                throw CancellationException()
            }
            emitter.setCancellable { dialog.dismiss() }
            if (!emitter.isDisposed) dialog.show()
        }
    }

    private fun makeProgressDialog(context: Context): Dialog {
        val dialog = ProgressDialog(context)
        dialog.setMessage(context.getString(R.string.loading))
        dialog.isIndeterminate = true
        return dialog
    }
}
