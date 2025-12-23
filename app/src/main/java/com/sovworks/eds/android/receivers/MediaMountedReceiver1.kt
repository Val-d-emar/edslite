package com.sovworks.eds.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.locations.ExternalStorageLocation
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.LocationsManagerBase

class MediaMountedReceiver(private val _lm: LocationsManagerBase) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.debug("MediaMountedReceiver")
        val lm = _lm ?: return
        val mountPath = intent.dataString
        val loc = if (mountPath != null) ExternalStorageLocation(
            context,
            "ext storage",
            mountPath,
            null
        ) else null
        try {
            Thread.sleep(3000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Logger.debug("ACTION_USB_DEVICE_ATTACHED")
                lm.updateDeviceLocations()
                LocationsManager.broadcastLocationAdded(context, loc)
            }

            Intent.ACTION_MEDIA_MOUNTED -> {
                Logger.debug("ACTION_MEDIA_MOUNTED")
                lm.updateDeviceLocations()
                LocationsManager.broadcastLocationAdded(context, loc)
            }

            Intent.ACTION_MEDIA_UNMOUNTED -> {
                Logger.debug("ACTION_MEDIA_UNMOUNTED")
                lm.updateDeviceLocations()
                LocationsManager.broadcastLocationRemoved(context, loc)
            }

            Intent.ACTION_MEDIA_REMOVED -> {
                Logger.debug("ACTION_MEDIA_REMOVED")
                lm.updateDeviceLocations()
                LocationsManager.broadcastLocationRemoved(context, loc)
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Logger.debug("ACTION_USB_DEVICE_DETACHED")
                lm.updateDeviceLocations()
                LocationsManager.broadcastLocationRemoved(context, loc)
            }
        }
    }
}
