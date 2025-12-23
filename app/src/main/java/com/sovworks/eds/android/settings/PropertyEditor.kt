package com.sovworks.eds.android.settings

import android.app.FragmentManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.settings.views.PropertiesView

interface PropertyEditor {
    interface Host {
        val context: Context
        fun startActivityForResult(intent: Intent?, requestCode: Int)
        val propertiesView: PropertiesView

        //void updateView();
        val fragmentManager: FragmentManager?
    }

    var id: Int
    fun load()

    @Throws(Exception::class)
    fun save()
    fun save(b: Bundle)
    fun load(b: Bundle)
    fun getView(parent: ViewGroup?): View?
    val host: Host?
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean
    fun onClick()
    var startPosition: Int

    companion object {
        const val ARG_PROPERTY_ID: String = "com.sovworks.eds.android.PROPERTY_ID"
        const val ARG_HOST_FRAGMENT_TAG: String = TaskFragment.ARG_HOST_FRAGMENT
    }
}
