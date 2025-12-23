package com.sovworks.eds.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.SettingsCommon.LocationShortcutWidgetInfo

class LocationShortcutWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        setWidgetsState(context, appWidgetManager, appWidgetIds, null)
    }

    /*@Override
	public void onEnabled (Context context)	
	{
		context.startService(new Intent(context, OperationsService.class));		
	}
	
	@Override
	public void onDisabled (Context context)
	{
		context.stopService(new Intent(context, OperationsService.class));
	}*/
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (LocationsManager.BROADCAST_LOCATION_CHANGED == intent.action) setWidgetsState(
            context,
            intent.getParcelableExtra(LocationsManager.PARAM_LOCATION_URI)
        )
    }

    private fun setWidgetsState(context: Context, locationUri: Uri?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(
                context,
                LocationShortcutWidget::class.java
            )
        )
        setWidgetsState(context, appWidgetManager, appWidgetIds, locationUri)
    }

    private fun setWidgetsState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        locationUri: Uri?
    ) {
        val lm = LocationsManager.getLocationsManager(context)
        if (lm == null) setWidgetsState(context, appWidgetManager, appWidgetIds, false)
        else try {
            val settings = UserSettings.getSettings(context)
            for (widgetId in appWidgetIds) {
                val widgetInfo = settings.getLocationShortcutWidgetInfo(widgetId)
                if (widgetInfo != null) {
                    val widgetLoc = lm.findExistingLocation(Uri.parse(widgetInfo.locationUriString))
                    if (widgetLoc != null) {
                        if (locationUri != null) {
                            val changedLoc = lm.getLocation(locationUri)
                            if (changedLoc != null && changedLoc.id == widgetLoc.id) setWidgetLayout(
                                context,
                                appWidgetManager,
                                widgetId,
                                widgetInfo,
                                widgetLoc
                            )
                        } else setWidgetLayout(
                            context,
                            appWidgetManager,
                            widgetId,
                            widgetInfo,
                            widgetLoc
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setWidgetLayout(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        widgetInfo: LocationShortcutWidgetInfo,
        loc: Location?
    ) {
        setWidgetLayout(
            context,
            appWidgetManager,
            widgetId,
            widgetInfo,
            LocationsManager.isOpen(loc)
        )
    }

    private fun setWidgetsState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        isOpen: Boolean
    ) {
        val settings = UserSettings.getSettings(context)
        for (widgetId in appWidgetIds) {
            val widgetInfo = settings.getLocationShortcutWidgetInfo(widgetId)
            if (widgetInfo != null) setWidgetLayout(
                context,
                appWidgetManager,
                widgetId,
                widgetInfo,
                isOpen
            )
        }
    }

    companion object {
        fun setWidgetLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            prefs: LocationShortcutWidgetInfo,
            isContainerOpen: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(R.id.widgetTitleTextView, prefs.widgetTitle)
            //TypedValue typedValue = new TypedValue();
            //context.getTheme().resolveAttribute(isContainerOpen ? R.attr.widgetUnlockedIcon : R.attr.widgetLockedIcon, typedValue, true);
            //views.setImageViewResource(R.id.widgetLockImageButton, typedValue.resourceId);
            views.setImageViewResource(
                R.id.widgetLockImageButton,
                if (isContainerOpen) R.drawable.widget_unlocked else R.drawable.widget_locked
            )

            val intent = Intent(context, FileManagerActivity::class.java)
            intent.setData(Uri.parse(prefs.locationUriString))
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetLockImageButton, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
