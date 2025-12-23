package com.sovworks.eds.android.settings.container

import android.content.Intent
import android.net.Uri
import com.sovworks.eds.android.R
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getSelectPathIntent
import com.sovworks.eds.android.locations.fragments.CreateContainerFragment
import com.sovworks.eds.android.locations.fragments.CreateContainerFragmentBase
import com.sovworks.eds.android.locations.fragments.CreateEDSLocationFragment
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.settings.PathPropertyEditor
import java.io.IOException

abstract class PathToContainerPropertyEditorBase(createEDSLocationFragment: CreateContainerFragmentBase) :
    PathPropertyEditor(
        createEDSLocationFragment,
        R.string.path_to_container,
        0,
        createEDSLocationFragment.tag
    ) {
    override fun onTextChanged(newValue: String) {
        super.onTextChanged(newValue)
        hostFragment.activity.invalidateOptionsMenu()
    }

    protected val hostFragment: CreateContainerFragment
        get() = host as CreateContainerFragment


    @Throws(IOException::class)
    override fun getSelectPathIntent(): Intent {
        val addExisting =
            hostFragment.state.getBoolean(CreateEDSLocationFragment.ARG_ADD_EXISTING_LOCATION)
        val isEncFs = hostFragment.isEncFsFormat
        val i: Intent = FileManagerActivity.getSelectPathIntent(
            host.context,
            null,
            false,
            true,
            isEncFs || addExisting,
            !addExisting,
            true,
            true
        )
        i.putExtra(FileManagerActivity.EXTRA_ALLOW_SELECT_FROM_CONTENT_PROVIDERS, true)
        return i
    }

    override fun saveText(text: String) {
        hostFragment.state.putParcelable(
            CreateContainerTaskFragmentBase.ARG_LOCATION,
            Uri.parse(text)
        )
    }

    override fun loadText(): String? {
        val uri =
            hostFragment.state.getParcelable<Uri>(CreateContainerTaskFragmentBase.ARG_LOCATION)
        return uri?.toString()
    }
}
