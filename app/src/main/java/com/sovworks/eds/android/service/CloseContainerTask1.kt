package com.sovworks.eds.android.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat.Builder
import com.sovworks.eds.android.R
import com.sovworks.eds.android.locations.closer.fragments.OMLocationCloserFragment
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.locations.EDSLocation
import com.sovworks.eds.locations.LocationsManager

class CloseContainerTask : ServiceTaskWithNotificationBase() {
    @Throws(Throwable::class)
    override fun doWork(context: Context, i: Intent): Any? {
        super.doWork(context, i)
        val cont =
            LocationsManager.getLocationsManager(context).getFromIntent(i, null) as EDSLocation
        if (cont != null) OMLocationCloserFragment.unmountAndClose(
            context,
            cont,
            UserSettings.getSettings(context).alwaysForceClose()
        )
        return null
    }

    override fun initNotification(): Builder {
        return super.initNotification().setContentTitle(_context!!.getString(R.string.closing))
    }
}
