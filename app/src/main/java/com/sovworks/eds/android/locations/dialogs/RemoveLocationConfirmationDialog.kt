package com.sovworks.eds.android.locations.dialogs

import android.app.DialogFragment
import android.app.FragmentManager
import android.os.Bundle
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.ConfirmationDialog
import com.sovworks.eds.android.locations.fragments.LocationListBaseFragment
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

class RemoveLocationConfirmationDialog : ConfirmationDialog() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _loc = LocationsManager.getLocationsManager(activity).getFromBundle(arguments, null)
    }

    override fun onYes() {
        if (_loc == null) return
        val f =
            fragmentManager.findFragmentByTag(LocationListBaseFragment.TAG) as LocationListBaseFragment
                ?: return
        f.removeLocation(_loc)
    }

    override val title: String
        get() = getString(
            R.string.do_you_really_want_to_remove_from_the_list,
            if (_loc == null) "" else _loc!!.title
        )

    private var _loc: Location? = null

    companion object {
        const val TAG: String = "RemoveLocationConfirmationDialog"

        @JvmStatic
        fun showDialog(fm: FragmentManager?, loc: Location) {
            val f: DialogFragment = RemoveLocationConfirmationDialog()
            val b = Bundle()
            LocationsManager.storePathsInBundle(b, loc, null)
            f.arguments = b
            f.show(fm, TAG)
        }
    }
}
