package com.sovworks.eds.android.tasks

import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.File.AccessMode.ReadWrite
import com.sovworks.eds.locations.ContainerLocation
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import java.io.IOException

abstract class ChangeContainerPasswordTaskBase : ChangeEDSLocationPasswordTask() {
    //public static final String ARG_FIN_ACTIVITY = "fin_activity";
    @Throws(IOException::class, ApplicationException::class)
    override fun changeLocationPassword() {
        val cont = _location as ContainerLocation
        setContainerPassword(cont)
        val io = cont.location.currentPath.file.getRandomAccessIO(ReadWrite)
        try {
            val vl = cont.edsContainer.volumeLayout
            vl.writeHeader(io)
        } finally {
            io.close()
        }
    }

    @Throws(IOException::class)
    protected fun setContainerPassword(container: ContainerLocation) {
        val vl = container.edsContainer.volumeLayout
        val args = arguments
        val sb = Util.getPassword(args, LocationsManager.getLocationsManager(_context))
        vl.setPassword(sb.dataArray)
        sb.close()
        if (args.containsKey(Openable.PARAM_KDF_ITERATIONS)) vl.setNumKDFIterations(
            args.getInt(
                Openable.PARAM_KDF_ITERATIONS
            )
        )
    }

    companion object {
        const val TAG: String = "com.sovworks.eds.android.tasks.ChangeContainerPasswordTask"
    }
}
