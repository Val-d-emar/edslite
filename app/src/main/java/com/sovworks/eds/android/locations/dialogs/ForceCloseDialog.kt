package com.sovworks.eds.android.locations.dialogs

import android.app.DialogFragment
import android.app.FragmentManager
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.ConfirmationDialog
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment

class ForceCloseDialog : ConfirmationDialog() {
    override fun onYes() {
        var closerArgs = arguments.getBundle(ARG_CLOSER_ARGS)
        if (closerArgs == null) closerArgs = Bundle()
        closerArgs.putBoolean(LocationCloserBaseFragment.ARG_FORCE_CLOSE, true)
        val closer = instantiate(
            activity,
            arguments.getString(ARG_CLOSER_CLASS_NAME),
            closerArgs
        ) as LocationCloserBaseFragment
        fragmentManager.beginTransaction().add(
            closer,
            arguments.getString(LocationCloserBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG)
        ).commit()
    }

    override val title: String
        get() = getString(
            R.string.force_close_request,
            arguments.getString(ARG_LOCATION_TITLE)
        )

    companion object {
        const val ARG_LOCATION_TITLE: String = "com.sovworks.eds.android.LOCATION_TITLE"
        const val ARG_CLOSER_ARGS: String = "com.sovworks.eds.android.CLOSER_ARGS"
        const val ARG_CLOSER_CLASS_NAME: String = "com.sovworks.eds.android.CLOSER_CLASS_NAME"

        const val TAG: String = "ForceCloseDialog"

        fun showDialog(
            fm: FragmentManager?,
            closerTag: String?,
            locTitle: String?,
            closerClassName: String?,
            closerArgs: Bundle?
        ) {
            val f: DialogFragment = ForceCloseDialog()
            val b = Bundle()
            b.putString(ARG_LOCATION_TITLE, locTitle)
            b.putString(ARG_CLOSER_CLASS_NAME, closerClassName)
            if (closerArgs != null) b.putBundle(ARG_CLOSER_ARGS, closerArgs)
            b.putString(LocationCloserBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, closerTag)
            f.arguments = b
            f.show(fm, TAG)
        }
    }
}
