package com.sovworks.eds.android.locations.tasks

import android.net.Uri
import android.os.Bundle
import com.sovworks.eds.locations.LocationsManager

open class AddExistingContainerTaskFragment : AddExistingContainerTaskFragmentBase() {
    companion object {
        fun newInstance(
            containerLocationUri: Uri?, storeLink: Boolean, containerFormatName: String?
        ): AddExistingContainerTaskFragment {
            val args = Bundle()
            args.putBoolean(ARG_STORE_LINK, storeLink)
            args.putParcelable(LocationsManager.PARAM_LOCATION_URI, containerLocationUri)
            args.putString(
                CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT,
                containerFormatName
            )
            val f = AddExistingContainerTaskFragment()
            f.arguments = args
            return f
        }
    }
}
