package com.sovworks.eds.android.locations.closer.fragments

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.helpers.TempFilesMonitor.Companion.getMonitor
import com.sovworks.eds.android.helpers.WipeFilesTask.Companion.wipeFilesRnd
import com.sovworks.eds.android.providers.ContainersDocumentProviderBase
import com.sovworks.eds.android.service.FileOpsService
import com.sovworks.eds.android.service.LocationsService
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.util.SrcDstRec
import com.sovworks.eds.fs.util.SrcDstSingle
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.Openable
import java.io.IOException


open class OpenableLocationCloserFragment : LocationCloserBaseFragment() {
    class CloseLocationTaskFragment :
        com.sovworks.eds.android.locations.closer.fragments.LocationCloserBaseFragment.CloseLocationTaskFragment() {
        @Throws(Exception::class)
        override fun procLocation(state: TaskState?, location: Location) {
            val fc = arguments.getBoolean(
                LocationCloserBaseFragment.Companion.ARG_FORCE_CLOSE,
                UserSettings.getSettings(_context).alwaysForceClose()
            )
            try {
                closeLocation(_context!!, location as Openable, fc)
            } catch (e: Exception) {
                if (fc) Logger.log(e)
                else throw e
            }
        }
    }

    override val closeLocationTask: TaskFragment
        get() = CloseLocationTaskFragment()

    companion object {
        @Throws(IOException::class)
        fun wipeMirror(context: Context, location: Location) {
            val mirrorLocation = FileOpsService.getMirrorLocation(
                UserSettings.getSettings(context).workDir,
                context,
                location.id
            )
            if (mirrorLocation.currentPath.exists()) {
                val sdr = SrcDstRec(
                    SrcDstSingle(
                        mirrorLocation,
                        null
                    )
                )
                sdr.setIsDirLast(true)
                wipeFilesRnd(
                    null,
                    getMonitor(context).syncObject,
                    true,
                    sdr
                )
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun closeLocation(context: Context, location: Openable, forceClose: Boolean) {
            try {
                location.close(forceClose)
            } catch (e: Exception) {
                if (forceClose) Logger.log(e)
                else throw e
            }
            makePostCloseCheck(context, location)
            wipeMirror(context, location)
        }

        fun makePostCloseCheck(context: Context, loc: Location) {
            if (loc is Openable && LocationsManager.isOpen(loc)) return
            val lm = LocationsManager.getLocationsManager(context)
            LocationsManager.broadcastLocationChanged(context, loc)
            lm.unregOpenedLocation(loc)
            if (VERSION.SDK_INT >= VERSION_CODES.KITKAT && loc is EDSLocation) ContainersDocumentProviderBase.notifyOpenedLocationsListChanged(
                context
            )

            if (!lm.hasOpenLocations()) {
                lm.broadcastAllContainersClosed()
                LocationsService.stopService(context)
            }
        }
    }
}
