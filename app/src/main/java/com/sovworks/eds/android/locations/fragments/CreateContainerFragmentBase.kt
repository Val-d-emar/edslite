package com.sovworks.eds.android.locations.fragments

import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.os.Parcelable
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.fragments.TaskFragment
import com.sovworks.eds.android.locations.tasks.AddExistingContainerTaskFragment
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragment
import com.sovworks.eds.android.locations.tasks.CreateContainerTaskFragmentBase
import com.sovworks.eds.android.locations.tasks.CreateEncFsTaskFragment
import com.sovworks.eds.android.settings.PropertyEditor
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.container.ContainerFormatPropertyEditor
import com.sovworks.eds.android.settings.container.ContainerPasswordPropertyEditor
import com.sovworks.eds.android.settings.container.ContainerSizePropertyEditor
import com.sovworks.eds.android.settings.container.EncryptionAlgorithmPropertyEditor
import com.sovworks.eds.android.settings.container.FileSystemTypePropertyEditor
import com.sovworks.eds.android.settings.container.FillFreeSpacePropertyEditor
import com.sovworks.eds.android.settings.container.HashingAlgorithmPropertyEditor
import com.sovworks.eds.android.settings.container.PIMPropertyEditor
import com.sovworks.eds.android.settings.container.PathToContainerPropertyEditor
import com.sovworks.eds.android.settings.encfs.BlockSizePropertyEditor
import com.sovworks.eds.android.settings.encfs.DataCodecPropertyEditor
import com.sovworks.eds.android.settings.encfs.EnableEmptyBlocksPropertyEditor
import com.sovworks.eds.android.settings.encfs.ExternalFileIVPropertyEditor
import com.sovworks.eds.android.settings.encfs.FilenameIVChainingPropertyEditor
import com.sovworks.eds.android.settings.encfs.KeySizePropertyEditor
import com.sovworks.eds.android.settings.encfs.MACBytesPerBlockPropertyEditor
import com.sovworks.eds.android.settings.encfs.NameCodecPropertyEditor
import com.sovworks.eds.android.settings.encfs.NumKDFIterationsPropertyEditor
import com.sovworks.eds.android.settings.encfs.RandBytesPerBlockPropertyEditor
import com.sovworks.eds.android.settings.encfs.UniqueIVPropertyEditor
import com.sovworks.eds.container.ContainerFormatInfo
import com.sovworks.eds.container.EDSLocationFormatter
import com.sovworks.eds.container.EdsContainer
import com.sovworks.eds.container.VolumeLayout
import java.io.File

abstract class CreateContainerFragmentBase : CreateEDSLocationFragment(), PasswordReceiver {
    fun changeUniqueIVDependentOptions() {
        val show = isEncFsFormat && !_state.getBoolean(
            CreateEDSLocationFragmentBase.Companion.ARG_ADD_EXISTING_LOCATION,
            false
        ) && _state.getBoolean(CreateEncFsTaskFragment.ARG_UNIQUE_IV, true) &&
                _state.getBoolean(CreateEncFsTaskFragment.ARG_CHAINED_NAME_IV, true)
        _propertiesView!!.setPropertyState(R.string.enable_filename_to_file_iv_chain, show)
    }

    val isEncFsFormat: Boolean
        get() = EDSLocationFormatter.FORMAT_ENCFS == _state.getString(
            CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT
        )

    val selectedVolumeLayout: VolumeLayout?
        get() {
            val info = currentContainerFormatInfo
            return info?.volumeLayout
        }

    override fun createAddExistingLocationTask(): TaskFragment {
        return AddExistingContainerTaskFragment.newInstance(
            _state.getParcelable<Parcelable>(CreateContainerTaskFragmentBase.ARG_LOCATION) as Uri?,
            !UserSettings.getSettings(activity).neverSaveHistory(),
            _state.getString(CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT)
        )
    }

    override fun createCreateLocationTask(): TaskFragment {
        return if (isEncFsFormat) CreateEncFsTaskFragment() else CreateContainerTaskFragment()
    }

    override fun showCreateNewLocationProperties() {
        val uri = _state.getParcelable<Uri>(CreateContainerTaskFragmentBase.ARG_LOCATION)
        if (uri == null) {
            var path: File?
            path =
                if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
                )
                else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!path.exists() && !path.mkdirs()) path = context.filesDir
            if (path != null) path = File(path, "new container.eds")
            if (path != null) {
                _state.putParcelable(
                    CreateContainerTaskFragmentBase.ARG_LOCATION,
                    Uri.parse(path.path)
                )
                activity.invalidateOptionsMenu()
            }
        }

        super.showCreateNewLocationProperties()
        _propertiesView!!.setPropertyState(R.string.container_format, true)
    }

    override fun onPasswordEntered(dlg: PasswordDialog) {
        val propertyId = dlg.arguments.getInt(PropertyEditor.ARG_PROPERTY_ID)
        val pr = propertiesView.getPropertyById(propertyId) as PasswordReceiver
        pr?.onPasswordEntered(dlg)
    }

    override fun onPasswordNotEntered(dlg: PasswordDialog) {
        val propertyId = dlg.arguments.getInt(PropertyEditor.ARG_PROPERTY_ID)
        val pr = propertiesView.getPropertyById(propertyId) as PasswordReceiver
        pr?.onPasswordNotEntered(dlg)
    }

    override fun createProperties() {
        super.createProperties()
        if (!_state.containsKey(CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT)) _state.putString(
            CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT,
            EdsContainer.getSupportedFormats()[0].formatName
        )
    }

    override fun createNewLocationProperties() {
        _propertiesView!!.addProperty(ContainerFormatPropertyEditor(this))
        _propertiesView!!.addProperty(PathToContainerPropertyEditor(this))
        _propertiesView!!.addProperty(ContainerPasswordPropertyEditor(this))
        createContainerProperties()
        createEncFsProperties()
    }

    protected val currentContainerFormatInfo: ContainerFormatInfo?
        get() = EdsContainer.findFormatByName(_state.getString(CreateContainerTaskFragmentBase.ARG_CONTAINER_FORMAT))

    protected fun createContainerProperties() {
        _propertiesView!!.addProperty(PIMPropertyEditor(this))
        _propertiesView!!.addProperty(ContainerSizePropertyEditor(this))
        _propertiesView!!.addProperty(EncryptionAlgorithmPropertyEditor(this))
        _propertiesView!!.addProperty(HashingAlgorithmPropertyEditor(this))
        _propertiesView!!.addProperty(FileSystemTypePropertyEditor(this))
        _propertiesView!!.addProperty(FillFreeSpacePropertyEditor(this))
    }

    private fun createEncFsProperties() {
        _propertiesView!!.addProperty(DataCodecPropertyEditor(this))
        _propertiesView!!.addProperty(NameCodecPropertyEditor(this))
        _propertiesView!!.addProperty(KeySizePropertyEditor(this))
        _propertiesView!!.addProperty(BlockSizePropertyEditor(this))
        _propertiesView!!.addProperty(UniqueIVPropertyEditor(this))
        _propertiesView!!.addProperty(FilenameIVChainingPropertyEditor(this))
        _propertiesView!!.addProperty(ExternalFileIVPropertyEditor(this))
        _propertiesView!!.addProperty(EnableEmptyBlocksPropertyEditor(this))
        _propertiesView!!.addProperty(MACBytesPerBlockPropertyEditor(this))
        _propertiesView!!.addProperty(RandBytesPerBlockPropertyEditor(this))
        _propertiesView!!.addProperty(NumKDFIterationsPropertyEditor(this))
    }
}
