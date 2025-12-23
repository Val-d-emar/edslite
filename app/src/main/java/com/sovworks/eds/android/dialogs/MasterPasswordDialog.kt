package com.sovworks.eds.android.dialogs

import android.app.FragmentManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import com.sovworks.eds.android.EdsApplication
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.settings.GlobalConfig
import com.sovworks.eds.settings.SettingsCommon.InvalidSettingsPassword
import com.trello.rxlifecycle2.components.RxActivity
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject

class MasterPasswordDialog : PasswordDialog() {
    override fun loadLabel(): String? {
        return getString(R.string.enter_master_password)
    }

    override fun hasPassword(): Boolean {
        return true
    }

    override fun onCancel(dialog: DialogInterface) {
        EdsApplication.clearMasterPassword()
        super.onCancel(dialog)
    }

    override fun onPasswordEntered() {
        EdsApplication.setMasterPassword(SecureBuffer(password))
        if (checkSettingsKey(activity)) {
            val args = arguments
            if (args != null && args.getBoolean(ARG_IS_OBSERVABLE)) _passwordCheckSubject.onNext(
                true
            )
            else super.onPasswordEntered()
        } else onPasswordNotEntered()
    }

    override fun onPasswordNotEntered() {
        val args = arguments
        if (args != null && args.getBoolean(ARG_IS_OBSERVABLE)) _passwordCheckSubject.onNext(false)
        else super.onPasswordNotEntered()
    }

    private val _passwordCheckSubject: Subject<Boolean> = BehaviorSubject.create()

    companion object {
        const val TAG: String = "com.sovworks.eds.android.dialogs.MasterPasswordDialog"
        const val ARG_IS_OBSERVABLE: String = "com.sovworks.eds.android.IS_OBSERVABLE"

        @JvmStatic
        fun getObservable(activity: RxActivity): Single<Boolean> {
            val s = UserSettings.getSettings(activity)
            val curTime = SystemClock.elapsedRealtime()
            val lastActTime: Long = EdsApplication.getLastActivityTime()
            if (curTime - lastActTime > GlobalConfig.CLEAR_MASTER_PASS_INACTIVITY_TIMEOUT) {
                Logger.debug("Clearing settings protection key")
                EdsApplication.clearMasterPassword()
                s.clearSettingsProtectionKey()
            }
            EdsApplication.updateLastActivityTime()
            try {
                s.settingsProtectionKey
            } catch (e: InvalidSettingsPassword) {
                val fm = activity.fragmentManager
                val mpd = fm.findFragmentByTag(TAG) as MasterPasswordDialog
                if (mpd == null) {
                    val masterPasswordDialog = MasterPasswordDialog()
                    val args = Bundle()
                    args.putBoolean(ARG_IS_OBSERVABLE, true)
                    masterPasswordDialog.arguments = args
                    return masterPasswordDialog._passwordCheckSubject.doOnSubscribe
                    (Consumer<Disposable> { subscription: Disposable? ->
                        masterPasswordDialog.show(
                            fm,
                            TAG
                        )
                    }).firstOrError
                    ()
                }
                return mpd._passwordCheckSubject.firstOrError
                ()
            }
            return Single.just(true)
        }

        @JvmStatic
        fun checkSettingsKey(context: Context?): Boolean {
            val settings = UserSettings.getSettings(context)
            try {
                val check =
                    settings.getProtectedString(UserSettingsCommon.SETTINGS_PROTECTION_KEY_CHECK)
                if (check == null) settings.saveSettingsProtectionKey()
                EdsApplication.updateLastActivityTime()
                return true
            } catch (ignored: InvalidSettingsPassword) {
                settings.clearSettingsProtectionKey()
                Toast.makeText(context, R.string.invalid_master_password, Toast.LENGTH_LONG).show()
            }
            return false
        }

        @JvmStatic
        fun checkMasterPasswordIsSet(
            context: Context?,
            fm: FragmentManager?,
            receiverFragmentTag: String?
        ): Boolean {
            val s = UserSettings.getSettings(context)
            val curTime = SystemClock.elapsedRealtime()
            val lastActTime: Long = EdsApplication.getLastActivityTime()
            if (curTime - lastActTime > GlobalConfig.CLEAR_MASTER_PASS_INACTIVITY_TIMEOUT) {
                Logger.debug("Clearing settings protection key")
                EdsApplication.clearMasterPassword()
                s.clearSettingsProtectionKey()
            }
            EdsApplication.updateLastActivityTime()
            try {
                s.settingsProtectionKey
            } catch (e: InvalidSettingsPassword) {
                val mpd = MasterPasswordDialog()
                if (receiverFragmentTag != null) {
                    val args = Bundle()
                    args.putString(
                        PasswordDialogBase.Companion.ARG_RECEIVER_FRAGMENT_TAG,
                        receiverFragmentTag
                    )
                    mpd.arguments = args
                }
                mpd.show(fm, TAG)
                return false
            }
            return true
        }
    }
}
