package com.sovworks.eds.android.activities

import android.app.Fragment
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.sovworks.eds.android.Logger.Companion.showAndLog
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getSelectPathIntent
import com.sovworks.eds.android.fragments.PropertiesFragmentBase
import com.sovworks.eds.android.settings.PathPropertyEditor
import com.sovworks.eds.android.settings.PropertyEditor
import com.sovworks.eds.android.settings.TextPropertyEditor
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.widgets.LocationShortcutWidget
import com.sovworks.eds.locations.Location
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.locations.LocationsManagerBase.Companion.getLocationsManager
import com.sovworks.eds.locations.LocationsManagerBase.getDefaultLocationFromPath
import com.sovworks.eds.locations.Openable
import com.sovworks.eds.settings.SettingsCommon.LocationShortcutWidgetInfo
import java.io.IOException

class LocationShortcutWidgetConfigActivity : LocationShortcutWidgetConfigActivityBase() {
    class MainFragment : PropertiesFragmentBase() {
        inner class TargetPathPropertyEditor :
            PathPropertyEditor(this@MainFragment, R.string.target_path, 0, tag) {
            override fun loadText(): String? {
                return _state.getString(ARG_URI)
            }

            @Throws(Exception::class)
            override fun saveText(text: String) {
                _state.putString(ARG_URI, text)
            }

            @get:Throws(IOException::class)
            override val selectPathIntent: Intent
                get() = FileManagerActivity.getSelectPathIntent(
                    getContext(), null, false, true, true, false, true, true
                )
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setHasOptionsMenu(true)
        }

        override fun createProperties() {
            _propertiesView!!.addProperty(
                object : TextPropertyEditor(this, R.string.enter_widget_title, 0, tag) {
                    override fun loadText(): String? {
                        return _state.getString(ARG_TITLE)
                    }

                    @Throws(Exception::class)
                    override fun saveText(text: String) {
                        _state.putString(ARG_TITLE, text)
                    }
                })
            _propertiesView!!.addProperty(pathPE)
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.widget_config_menu, menu)
        }

        override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.confirm -> {
                    createWidget()
                    return true
                }

                else -> return super.onOptionsItemSelected(menuItem)
            }
        }

        private val _state = Bundle()

        protected val pathPE: PropertyEditor
            get() = TargetPathPropertyEditor()

        private fun createWidget() {
            try {
                _propertiesView!!.saveProperties()
                val title = _state.getString(ARG_TITLE)
                val path = _state.getString(ARG_URI)
                if (title == null || title.trim { it <= ' ' }
                        .isEmpty() || path == null || path.trim { it <= ' ' }.isEmpty()) return

                initWidgetFields(title, path)

                val resultValue = Intent()
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                activity.setResult(RESULT_OK, resultValue)
                activity.finish()
            } catch (e: Exception) {
                showAndLog(activity, e)
            }
        }

        @Throws(Exception::class)
        private fun initWidgetFields(title: String, path: String) {
            val widgetId = widgetId
            val target: Location =
                LocationsManager.getLocationsManager(activity).getDefaultLocationFromPath(path)
            val info = LocationShortcutWidgetInfo()
            info.widgetTitle = title
            info.locationUriString = target.locationUri.toString()
            UserSettings.getSettings(getContext()).setLocationShortcutWidgetInfo(widgetId, info)
            LocationShortcutWidget.setWidgetLayout(
                getContext(),
                AppWidgetManager.getInstance(getContext()),
                widgetId,
                info,
                (target !is Openable) || target.isOpen
            )
        }

        private val widgetId: Int
            get() = activity
                .intent
                .getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

        companion object {
            private const val ARG_TITLE = "title"
            private const val ARG_URI = "uri"
        }
    }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setResult(RESULT_CANCELED)
    }

    override val settingsFragment: Fragment
        get() = MainFragment()
}
