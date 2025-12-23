package com.sovworks.eds.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.locations.activities.CloseLocationsActivity
import com.sovworks.eds.locations.LocationsManager

class CloseAllContainersWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        setWidgetsState(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (LocationsManager.BROADCAST_LOCATION_CHANGED == intent.action) setWidgetsState(context)
    }

    private fun setWidgetsState(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(
                context,
                CloseAllContainersWidget::class.java
            )
        )
        setWidgetsState(context, appWidgetManager, appWidgetIds)
    }

    private fun setWidgetsState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        val haveOpenContainers = haveOpenContainers(context)
        for (widgetId in widgetIds) setWidgetLayout(
            context,
            appWidgetManager,
            widgetId,
            haveOpenContainers
        )
    }

    private fun haveOpenContainers(context: Context): Boolean {
        val lm = LocationsManager.getLocationsManager(context)
        if (lm != null) {
            for (cbl in lm.getLoadedEDSLocations(false)) if (cbl.isOpenOrMounted) return true
        }
        return false
    }

    companion object {
        fun setWidgetLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            haveOpenContainers: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.close_all_containers_widget)
            //TypedValue typedValue = new TypedValue();
            //context.getTheme().resolveAttribute(haveOpenContainers ? R.attr.widgetUnlockedAllIcon : R.attr.widgetLockedAllIcon, typedValue, true);
            //views.setImageViewResource(R.id.widgetLockImageButton, typedValue.resourceId);
            views.setImageViewResource(
                R.id.containersClosedImageButton,
                if (haveOpenContainers) R.drawable.widget_unlocked_all else R.drawable.widget_locked_all
            )

            val i: Intent
            if (haveOpenContainers) {
                i = Intent(context, CloseLocationsActivity::class.java)
                LocationsManager.storeLocationsInIntent(
                    i,
                    LocationsManager.getLocationsManager(context, true).locationsClosingOrder
                )
            } else {
                i = Intent(context, FileManagerActivity::class.java)
                i.setAction(Intent.ACTION_MAIN)
            }

            val pendingIntent =
                PendingIntent.getActivity(context, widgetId, i, PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.containersClosedImageButton, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
