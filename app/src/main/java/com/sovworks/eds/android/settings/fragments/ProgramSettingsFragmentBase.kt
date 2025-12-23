package com.sovworks.eds.android.settings.fragments

import android.content.Intent
import android.content.SharedPreferences.Editor
import android.net.Uri
import android.os.Bundle
import com.sovworks.eds.android.EdsApplication
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.dialogs.MasterPasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialog
import com.sovworks.eds.android.dialogs.PasswordDialogBase.PasswordReceiver
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase.Companion.getSelectPathIntent
import com.sovworks.eds.android.fragments.PropertiesFragmentBase
import com.sovworks.eds.android.settings.ButtonPropertyEditor
import com.sovworks.eds.android.settings.CategoryPropertyEditor
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.android.settings.IntPropertyEditor
import com.sovworks.eds.android.settings.MultilineTextPropertyEditor
import com.sovworks.eds.android.settings.PathPropertyEditor
import com.sovworks.eds.android.settings.SwitchPropertyEditor
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.android.settings.program.ExtFileManagerPropertyEditor
import com.sovworks.eds.android.settings.program.InstallExFatModulePropertyEditor
import com.sovworks.eds.crypto.SecureBuffer
import com.sovworks.eds.fs.util.PathUtil
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.settings.SettingsCommon
import com.sovworks.eds.settings.SettingsCommon.InvalidSettingsPassword
import java.io.IOException
import java.util.Arrays

abstract class ProgramSettingsFragmentBase : PropertiesFragmentBase(), PasswordReceiver {
    override fun onCreate(savedInstanceState: Bundle?) {
        settings = UserSettings.getSettings(activity)
        super.onCreate(savedInstanceState)
    }

    fun editSettings(): Editor {
        return settings!!.sharedPreferences.edit()
    }

    //master password is set
    override fun onPasswordEntered(dlg: PasswordDialog) {
        var data = dlg.password
        if (data != null && data.size == 0) data = null

        EdsApplication.setMasterPassword(if (data == null) null else SecureBuffer(data))
        try {
            settings!!.saveSettingsProtectionKey()
        } catch (ignored: InvalidSettingsPassword) {
        }
    }

    //master password is not set
    override fun onPasswordNotEntered(dlg: PasswordDialog?) {
    }

    var settings: UserSettings? = null
        protected set

    override fun createProperties() {
        propertiesView.isInstantSave = true
        createCategories()
        _propertiesView!!.setPropertiesState(false)
        _propertiesView!!.setPropertyState(R.string.main_settings, true)
    }

    protected fun createCategories() {
        val commonPropertiesList: MutableList<Int> = ArrayList()
        propertiesView.addProperty(object :
            CategoryPropertyEditor(this, R.string.main_settings, 0) {
            override fun load() {
                enableProperties(commonPropertiesList, isExpanded)
            }
        })
        createCommonProperties(commonPropertiesList)
    }

    protected fun createCommonProperties(commonPropertiesIds: MutableList<Int>) {
        commonPropertiesIds.add(
            propertiesView.addProperty(
                object : ChoiceDialogPropertyEditor(
                    this, R.string.theme, R.string.theme_desc,
                    tag
                ) {
                    override fun loadValue(): Int {
                        return if (settings!!.currentTheme == SettingsCommon.THEME_DEFAULT) 0 else 1
                    }

                    override fun saveValue(value: Int) {
                        editSettings().putInt(
                            UserSettingsCommon.Companion.THEME,
                            if (value == 0) SettingsCommon.THEME_DEFAULT else SettingsCommon.THEME_DARK
                        ).commit()
                    }

                    override val entries: List<String?>
                        get() = Arrays.asList(
                            getString(R.string.default_theme),
                            getString(R.string.dark_theme)
                        )
                })
        )
        commonPropertiesIds.add(propertiesView.addProperty(object : ButtonPropertyEditor(
            this,
            R.string.master_password,
            0,
            R.string.enter_master_password
        ) {
            override fun onButtonClick() {
                val args = Bundle()
                args.putBoolean(MasterPasswordDialog.ARG_VERIFY_PASSWORD, true)
                args.putString(MasterPasswordDialog.ARG_RECEIVER_FRAGMENT_TAG, tag)
                args.putString(
                    MasterPasswordDialog.ARG_LABEL,
                    getString(R.string.enter_new_password)
                )
                val mpd = MasterPasswordDialog()
                mpd.arguments = args
                mpd.show(fragmentManager, MasterPasswordDialog.TAG)
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object :
            SwitchPropertyEditor(this, R.string.show_previews, 0) {
            override fun loadValue(): Boolean {
                return settings!!.showPreviews()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(UserSettingsCommon.Companion.SHOW_PREVIEWS, value)
                    .commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : SwitchPropertyEditor(
            this,
            R.string.disable_wide_screen_layouts,
            R.string.disable_wide_screen_layouts_desc
        ) {
            override fun loadValue(): Boolean {
                return settings!!.disableLargeSceenLayouts()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(
                    UserSettingsCommon.Companion.DISABLE_WIDE_SCREEN_LAYOUTS,
                    value
                ).commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : SwitchPropertyEditor(
            this,
            R.string.never_save_history,
            R.string.never_save_history_desc
        ) {
            override fun loadValue(): Boolean {
                return settings!!.neverSaveHistory()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(UserSettingsCommon.Companion.NEVER_SAVE_HISTORY, value)
                    .commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : ChoiceDialogPropertyEditor(
            this, R.string.internal_image_viewer_mode, R.string.internal_image_viewer_mode_desc,
            tag
        ) {
            override fun loadValue(): Int {
                return settings!!.internalImageViewerMode
            }

            override fun saveValue(value: Int) {
                if (value >= 0) editSettings().putInt(
                    UserSettingsCommon.Companion.USE_INTERNAL_IMAGE_VIEWER,
                    value
                ).commit()
                else editSettings().remove(UserSettingsCommon.Companion.USE_INTERNAL_IMAGE_VIEWER)
                    .commit()
            }

            override val entries: List<String?>
                get() {
                    val modes =
                        resources.getStringArray(R.array.image_viewer_use_mode)
                    return Arrays.asList(*modes)
                }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : PathPropertyEditor(
            this, R.string.temp_data_path, R.string.temp_data_path_desc,
            tag
        ) {
            override fun loadText(): String? {
                return settings!!.workDir
            }

            override fun saveText(text: String) {
                if (text.trim { it <= ' ' }.length > 0) {
                    try {
                        val loc = LocationsManager.getLocationsManager(
                            activity
                        ).getLocation
                        (Uri.parse(text))
                        PathUtil.makeFullPath(loc.currentPath)
                        editSettings().putString(UserSettingsCommon.Companion.WORK_DIR, text)
                            .commit()
                    } catch (e: Exception) {
                        Logger.showAndLog(activity, e)
                    }
                } else editSettings().remove(UserSettingsCommon.Companion.WORK_DIR).commit()
            }

            @get:Throws(IOException::class)
            override val selectPathIntent: Intent
                get() {
                    val i: Intent = FileManagerActivity.getSelectPathIntent(
                        host.context,
                        null,
                        false,
                        false,
                        true,
                        true,
                        true,
                        false
                    )
                    i.putExtra(FileManagerActivity.EXTRA_ALLOW_BROWSE_DOCUMENT_PROVIDERS, true)
                    return i
                }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(ExtFileManagerPropertyEditor(this)))
        commonPropertiesIds.add(propertiesView.addProperty(object : IntPropertyEditor(
            this, R.string.max_temporary_file_size, R.string.max_temporary_file_size_desc,
            tag
        ) {
            override val dialogViewResId: Int
                get() = R.layout.settings_edit_num_lim4

            override fun loadValue(): Int {
                return settings!!.maxTempFileSize
            }

            override fun saveValue(value: Int) {
                if (value >= 0) editSettings().putInt(
                    UserSettingsCommon.Companion.MAX_FILE_SIZE_TO_OPEN,
                    value
                ).commit()
                else editSettings().remove(UserSettingsCommon.Companion.MAX_FILE_SIZE_TO_OPEN)
                    .commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : SwitchPropertyEditor(
            this,
            R.string.overwrite_temp_files_with_random_data,
            R.string.overwrite_temp_files_with_random_data_desc
        ) {
            override fun loadValue(): Boolean {
                return settings!!.wipeTempFiles()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(UserSettingsCommon.Companion.WIPE_TEMP_FILES, value)
                    .commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : MultilineTextPropertyEditor(
            this, R.string.extension_mime_override, R.string.extension_mime_override_desc,
            tag
        ) {
            override fun loadText(): String? {
                return settings!!.extensionsMimeMapString
            }

            override fun saveText(text: String) {
                if (text != null) editSettings().putString(
                    UserSettingsCommon.Companion.EXTENSIONS_MIME,
                    text
                ).commit()
                else editSettings().remove(UserSettingsCommon.Companion.EXTENSIONS_MIME).commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object :
            SwitchPropertyEditor(this, R.string.debug_log, 0) {
            override fun loadValue(): Boolean {
                return !settings!!.disableDebugLog()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(UserSettingsCommon.Companion.DISABLE_DEBUG_LOG, !value)
                    .commit()
                if (!value) {
                    Logger.closeLogger()
                    Logger.disableLog(true)
                } else try {
                    Logger.disableLog(false)
                    Logger.initLogger()
                } catch (e: IOException) {
                    Logger.showErrorMessage(context, e)
                }
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object :
            SwitchPropertyEditor(this, R.string.disable_modified_files_backup, 0) {
            override fun loadValue(): Boolean {
                return settings!!.disableModifiedFilesBackup()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(
                    UserSettingsCommon.Companion.DISABLE_MODIFIED_FILES_BACKUP,
                    value
                ).commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object :
            SwitchPropertyEditor(this, R.string.hide_eds_screen_from_other_apps, 0) {
            override fun loadValue(): Boolean {
                return settings!!.isFlagSecureEnabled
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(
                    UserSettingsCommon.Companion.IS_FLAG_SECURE_ENABLED,
                    value
                ).commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : SwitchPropertyEditor(
            this,
            R.string.always_force_close_containers,
            R.string.always_force_close_containers_desc
        ) {
            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(UserSettings.FORCE_UNMOUNT, value).commit()
            }

            override fun loadValue(): Boolean {
                return settings!!.alwaysForceClose()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object : SwitchPropertyEditor(
            this,
            R.string.dont_use_content_provider,
            R.string.dont_use_content_provider_desc
        ) {
            override fun loadValue(): Boolean {
                val value = settings!!.dontUseContentProvider()
                propertiesView.setPropertyState(
                    R.string.force_temp_files,
                    propertiesView.isPropertyEnabled(id) && !value
                )
                return value
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(
                    UserSettingsCommon.Companion.DONT_USE_CONTENT_PROVIDER,
                    value
                ).commit()
                propertiesView.setPropertyState(R.string.force_temp_files, !value)
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(object :
            SwitchPropertyEditor(this, R.string.force_temp_files, R.string.force_temp_files_desc) {
            override fun loadValue(): Boolean {
                return settings!!.forceTempFiles()
            }

            override fun saveValue(value: Boolean) {
                editSettings().putBoolean(UserSettingsCommon.Companion.FORCE_TEMP_FILES, value)
                    .commit()
            }
        }))
        commonPropertiesIds.add(propertiesView.addProperty(InstallExFatModulePropertyEditor(this)))
    }

    protected fun enableProperties(propIds: Iterable<Int>, enable: Boolean) {
        propertiesView.setPropertiesState(propIds, enable)
        propertiesView.loadProperties()
    }
}
