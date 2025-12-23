package com.sovworks.eds.android.helpers

import com.sovworks.eds.android.Logger.Companion.log
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.MasterPasswordDialog
import com.sovworks.eds.android.errors.UserException
import com.sovworks.eds.android.filemanager.fragments.ExtStorageWritePermisisonCheckFragment
import com.trello.rxlifecycle2.components.RxActivity
import io.reactivex.CompletableEmitter
import java.util.concurrent.CancellationException

class AppInitHelper internal constructor(activity: RxActivity, emitter: CompletableEmitter) :
    AppInitHelperBase(activity, emitter) {
    fun startInitSequence() {
        MasterPasswordDialog.getObservable(_activity)
            .flatMapCompletable { isValidPassword: Boolean ->
                if (isValidPassword) return@flatMapCompletable ExtStorageWritePermisisonCheckFragment.getObservable(
                    _activity
                )
                throw UserException(_activity, R.string.invalid_master_password)
            }
            .compose(_activity.bindToLifecycle<Any>())
            .subscribe(
                {
                    convertLegacySettings()
                    _initFinished.onComplete()
                },
                { err: Throwable? ->
                    if (err !is CancellationException) log(err)
                })
    }
}
