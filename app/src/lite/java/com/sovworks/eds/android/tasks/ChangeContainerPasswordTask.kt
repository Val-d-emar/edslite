package com.sovworks.eds.android.tasks

import android.os.Bundle
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.LocationsManager

object ChangeContainerPasswordTask : ChangeContainerPasswordTaskBase() {
    fun newInstance(
        container: ContainerLocation?, passwordDialogResult: Bundle?
    ): ChangeContainerPasswordTask {
        val args = Bundle()
        args.putAll(passwordDialogResult)
        LocationsManager.storePathsInBundle(args, container, null)
        val f: ChangeContainerPasswordTask = ChangeContainerPasswordTask()
        f.arguments = args
        return f
    }
}
