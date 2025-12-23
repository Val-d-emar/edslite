package com.sovworks.eds.android.fragments

import android.annotation.SuppressLint
import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.PropertyEditor.Host
import com.sovworks.eds.android.settings.views.PropertiesView

abstract class PropertiesFragmentBase : Fragment(), Host {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val view = inflater.inflate(R.layout.properties_fragment, container, false)
        _propertiesView = view.findViewById(R.id.list)
        createProperties()
        initProperties(savedInstanceState)
        return view
    }

    @SuppressLint("Override")
    override fun getContext(): Context {
        return activity
    }

    override fun getPropertiesView(): PropertiesView {
        return _propertiesView!!
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (!propertiesView.onActivityResult(requestCode, resultCode, data)) super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
    }

    @JvmField
    protected var _propertiesView: PropertiesView? = null

    protected open fun initProperties(state: Bundle?) {
        try {
            if (state == null) _propertiesView!!.loadProperties()
            else _propertiesView!!.loadProperties(state)
        } catch (e: Throwable) {
            Logger.showAndLog(activity, e)
        }
    }

    protected abstract fun createProperties()

    protected fun setPropertiesEnabled(propertyIds: Iterable<Int?>, enabled: Boolean) {
        _propertiesView!!.setPropertiesState(propertyIds, enabled)
    }
}
