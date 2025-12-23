package com.sovworks.eds.android.locations.opener.fragments

import android.os.Bundle
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.errors.WrongPasswordOrBadContainerException
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.exceptions.WrongPasswordException
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable

open class LocationOpenerFragmentCommon : LocationOpenerBaseFragment(), PasswordReceiver {
    open class OpenLocationTaskFragment :
        com.sovworks.eds.android.locations.opener.fragments.LocationOpenerBaseFragment.OpenLocationTaskFragment() {
        @Throws(Exception::class)
        override fun procLocation(state: TaskState?, location: Location, param: Bundle) {
            try {
                openLocation(location as Openable, param)
                regLocation(location)
            } catch (e: WrongPasswordException) {
                throw WrongPasswordOrBadContainerException(_context)
            }
            super.procLocation(state, location, param)
        }

        @Throws(Exception::class)
        protected open fun openLocation(location: Openable, param: Bundle) {
            if (location.isOpen) return

            location.setOpeningProgressReporter(_openingProgressReporter)

            if (param.containsKey(Openable.PARAM_PASSWORD)) location.setPassword(
                param.getParcelable(
                    Openable.PARAM_PASSWORD
                )
            )
            if (param.containsKey(Openable.PARAM_KDF_ITERATIONS)) location.setNumKDFIterations(
                param.getInt(
                    Openable.PARAM_KDF_ITERATIONS
                )
            )

            location.open()
        }

        protected fun regLocation(location: Openable) {
            _locationsManager!!.regOpenedLocation(location)
        }
    }

    override fun onPasswordEntered(dlg: PasswordDialog) {
        usePassword(getPasswordDialogResultBundle(dlg))
    }

    override fun onPasswordNotEntered(dlg: PasswordDialog?) {
        finishOpener(false, targetLocation)
    }

    override fun getOpenLocationTask(): TaskFragment {
        return OpenLocationTaskFragment()
    }

    protected fun getAskPasswordArgs(): Bundle {
        val args = Bundle()
        args.putString(PasswordDialogBase.ARG_RECEIVER_FRAGMENT_TAG, tag)
        val loc = targetLocation
        LocationsManager.storePathsInBundle(args, loc, null)
        return args
    }

    override fun getTargetLocation(): Openable? {
        return super.getTargetLocation() as Openable
    }

    override fun openLocation() {
        val ol = targetLocation
        var defaultArgs = arguments
        if (defaultArgs == null) defaultArgs = Bundle()
        if (ol!!.isOpen) super.openLocation()
        else if (needPasswordDialog(ol, defaultArgs)) askPassword()
        else startOpeningTask(initOpenLocationTaskParams(targetLocation!!))
    }

    override fun initOpenLocationTaskParams(location: Location): Bundle? {
        val args = super.initOpenLocationTaskParams(location)
        val defaultArgs = arguments
        if (defaultArgs != null) {
            if (defaultArgs.containsKey(Openable.PARAM_PASSWORD)
                && !args!!.containsKey(Openable.PARAM_PASSWORD)
            ) {
                val `val` = defaultArgs.getString(Openable.PARAM_PASSWORD)
                if (`val` != null) args.putParcelable(
                    Openable.PARAM_PASSWORD,
                    SecureBuffer(`val`.toCharArray())
                )
            }
            if (defaultArgs.containsKey(Openable.PARAM_KDF_ITERATIONS)
                && !args!!.containsKey(Openable.PARAM_KDF_ITERATIONS)
            ) args.putInt(Openable.PARAM_KDF_ITERATIONS, args.getInt(Openable.PARAM_KDF_ITERATIONS))
        }
        return args
    }

    protected fun usePassword(passwordDialogResultBundle: Bundle) {
        val args = initOpenLocationTaskParams(targetLocation!!)
        updateOpenLocationTaskParams(args!!, passwordDialogResultBundle)
        startOpeningTask(args)
    }

    protected fun updateOpenLocationTaskParams(args: Bundle, passwordDialogResultBundle: Bundle) {
        if (passwordDialogResultBundle.containsKey(Openable.PARAM_PASSWORD)) {
            val sb = passwordDialogResultBundle.getParcelable<SecureBuffer>(Openable.PARAM_PASSWORD)
            if (sb != null && (sb.length > 0 || !args.containsKey(Openable.PARAM_PASSWORD))) args.putParcelable(
                Openable.PARAM_PASSWORD,
                sb
            )
        }
        if (passwordDialogResultBundle.containsKey(Openable.PARAM_KDF_ITERATIONS)) args.putInt(
            Openable.PARAM_KDF_ITERATIONS,
            passwordDialogResultBundle.getInt(Openable.PARAM_KDF_ITERATIONS)
        )
    }

    protected fun askPassword() {
        val pd = PasswordDialog()
        pd.arguments = getAskPasswordArgs()
        pd.show(fragmentManager, PasswordDialog.TAG)
    }

    protected fun needPasswordDialog(ol: Openable, defaultArgs: Bundle?): Boolean {
        var defaultArgs = defaultArgs
        if (defaultArgs == null) defaultArgs = Bundle()
        return (ol.requirePassword() && !defaultArgs.containsKey(Openable.PARAM_PASSWORD)) ||
                (ol.requireCustomKDFIterations() && !defaultArgs.containsKey(Openable.PARAM_KDF_ITERATIONS))
    }

    protected fun getPasswordDialogResultBundle(pd: PasswordDialog): Bundle {
        val res = Bundle()
        res.putAll(pd.options)
        res.putParcelable(Openable.PARAM_PASSWORD, SecureBuffer(pd.password))
        return res
    }
}
