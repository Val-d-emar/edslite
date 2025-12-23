package com.sovworks.eds.android.tasks

import android.os.Bundle
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.locations.EncFsLocationBase
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.locations.LocationsManager
import java.io.IOException

class ChangeEncFsPasswordTask : ChangeEDSLocationPasswordTask() {
    @Throws(IOException::class, ApplicationException::class)
    override fun changeLocationPassword() {
        val loc = _location as EncFsLocationBase
        val sb = Util.getPassword(arguments, LocationsManager.getLocationsManager(_context))
        val pd = sb.dataArray
        try {
            loc.encFs!!.encryptVolumeKeyAndWriteConfig(pd)
        } finally {
            SecureBuffer.eraseData(pd)
            sb.close()
        }
    }

    companion object {
        const val TAG: String = "com.sovworks.eds.android.tasks.ChangeContainerPasswordTask"

        //public static final String ARG_FIN_ACTIVITY = "fin_activity";
        fun newInstance(
            container: EncFsLocationBase,
            passwordDialogResult: Bundle?
        ): ChangeEncFsPasswordTask {
            val args = Bundle()
            args.putAll(passwordDialogResult)
            LocationsManager.storePathsInBundle(args, container, null)
            val f = ChangeEncFsPasswordTask()
            f.arguments = args
            return f
        }
    }
}
