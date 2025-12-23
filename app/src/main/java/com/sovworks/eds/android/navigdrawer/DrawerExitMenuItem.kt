package com.sovworks.eds.android.navigdrawer

import android.app.Fragment
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.CloseLocationReceiver
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.Companion.getCloserTag
import com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.Companion.getDefaultCloserForLocation
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager

class DrawerExitMenuItem(drawerController: DrawerControllerBase?) :
    DrawerMenuItemBase(drawerController) {
    class ExitFragment : Fragment(), CloseLocationReceiver {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            closeNextOrExit()
        }

        override fun onTargetLocationClosed(location: Location?, closeTaskArgs: Bundle?) {
            closeNextOrExit()
        }

        override fun onTargetLocationNotClosed(location: Location?, closeTaskArgs: Bundle?) {
        }

        private fun closeNextOrExit() {
            val it: Iterator<Location> = LocationsManager.getLocationsManager(
                activity
            ).locationsClosingOrder.iterator()
            if (it.hasNext()) launchCloser(it.next())
            else exit()
        }

        private fun launchCloser(loc: Location) {
            val args = Bundle()
            args.putString(LocationCloserBaseFragment.PARAM_RECEIVER_FRAGMENT_TAG, TAG)
            LocationsManager.storePathsInBundle(args, loc, null)
            val closer: Fragment = getDefaultCloserForLocation(loc)
            closer.arguments = args
            fragmentManager.beginTransaction().add(
                closer,
                getCloserTag(loc)
            ).commit()
        }

        private fun exit() {
            FileOpsService.clearTempFolder(activity.applicationContext, true)
            activity.finish()
        }

        companion object {
            const val TAG: String = "com.sovworks.eds.android.ExitFragment"
        }
    }

    override val title: String
        get() = drawerController.mainActivity.getString(R.string.stop_service_and_exit)

    override fun onClick(view: View, position: Int) {
        super.onClick(view, position)
        drawerController.getMainActivity
        ().getFragmentManager
        ().beginTransaction
        ().add
        (ExitFragment(), ExitFragment.Companion.TAG).commit
        ()
    }

    override val icon: Drawable?
        get() = getIcon(drawerController.mainActivity)

    companion object {
        @Synchronized
        private fun getIcon(context: Context): Drawable? {
            if (_icon == null) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.exitIcon, typedValue, true)
                _icon = context.resources.getDrawable(typedValue.resourceId)
            }
            return _icon
        }

        private var _icon: Drawable? = null
    }
}
