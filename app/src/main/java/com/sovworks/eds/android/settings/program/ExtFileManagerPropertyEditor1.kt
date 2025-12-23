package com.sovworks.eds.android.settings.program

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.providers.ContainersDocumentProvider
import com.sovworks.eds.android.settings.ChoiceDialogPropertyEditor
import com.sovworks.eds.android.settings.PropertyEditor.Host
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import com.sovworks.eds.android.settings.fragments.ProgramSettingsFragmentBase
import com.sovworks.eds.settings.SettingsCommon.ExternalFileManagerInfo
import org.json.JSONException

@SuppressLint("CommitPrefEdits", "ApplySharedPref")
class ExtFileManagerPropertyEditor(f: ProgramSettingsFragmentBase) : ChoiceDialogPropertyEditor(
    f,
    R.string.use_external_file_manager,
    R.string.use_external_file_manager_desc,
    f.tag
) {
    override val host: Host?
        get() = super.getHost() as ProgramSettingsFragmentBase

    override fun load() {
        if (!host.getPropertiesView().isPropertyEnabled(id)) return
        loadExtBrowserInfo()
        loadChoiceStrings()
        super.load()
    }

    override fun loadValue(): Int {
        return getSelectionFromExtFMInfo(
            host.getSettings().getExternalFileManagerInfo()
        )
    }

    @SuppressLint("CommitPrefEdits")
    override fun saveValue(value: Int) {
        val info = getExtFMInfoFromSelection(value)
        saveExtInfo(host.getSettings(), info)
    }

    override val entries: List<String?>
        get() = _choiceStrings

    private class ExternalBrowserInfo {
        var resolveInfo: ResolveInfo? = null
        var action: String? = null
        var mime: String? = null
        var label: String? = null

        override fun toString(): String {
            return label!!
        }
    }

    private val _extBrowserInfo = ArrayList<ExternalBrowserInfo>()
    private val _choiceStrings = ArrayList<String?>()

    private fun loadExtBrowserInfo() {
        _extBrowserInfo.clear()

        var testPath = Uri.fromFile(host.getContext().filesDir)
        addMatches(_extBrowserInfo, Intent.ACTION_VIEW, testPath, "resource/folder")
        addMatches(_extBrowserInfo, Intent.ACTION_MEDIA_MOUNTED, testPath, null)

        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            //Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            //i.addCategory(Intent.CATEGORY_OPENABLE);
            //addMatches(_extBrowserInfo, i);
            testPath = DocumentsContract.buildTreeDocumentUri(
                ContainersDocumentProvider.AUTHORITY,
                "id"
            )
            addMatches(_extBrowserInfo, Intent.ACTION_VIEW, testPath, Document.MIME_TYPE_DIR)
            //addMatches(_extBrowserInfo, Intent.ACTION_VIEW, testPath, "resource/folder");
        }
    }

    private fun loadChoiceStrings() {
        _choiceStrings.clear()
        _choiceStrings.add(host.getString(R.string.builtin_file_manager))
        for (i in _extBrowserInfo) _choiceStrings.add(i.label)
    }

    private fun getSelectionFromExtFMInfo(info: ExternalFileManagerInfo?): Int {
        if (info != null) for (i in _extBrowserInfo.indices) {
            val item = _extBrowserInfo[i]
            if (info.packageName == item.resolveInfo!!.activityInfo.packageName &&
                info.className == item.resolveInfo!!.activityInfo.name &&
                info.action == item.action &&
                info.mimeType == item.mime
            ) return i + 1
        }
        return 0
    }

    private fun getExtFMInfoFromSelection(selection: Int): ExternalFileManagerInfo? {
        val idx = selection - 1
        if (idx < 0 || idx >= _extBrowserInfo.size) return null

        val item = _extBrowserInfo[idx]
        val res = ExternalFileManagerInfo()
        res.packageName = item.resolveInfo!!.activityInfo.packageName
        res.className = item.resolveInfo!!.activityInfo.name
        res.action = item.action
        res.mimeType = item.mime
        return res
    }

    private fun addMatches(
        matches: MutableList<ExternalBrowserInfo>,
        action: String,
        data: Uri?,
        mime: String?
    ) {
        val intent = Intent(action)
        if (data != null && mime != null) intent.setDataAndType(data, mime)
        else if (data != null) intent.setData(data)
        else if (mime != null) intent.setType(mime)
        addMatches(matches, intent)
    }

    private fun addMatches(matches: MutableList<ExternalBrowserInfo>, intent: Intent) {
        val ignoredPackage = host.getContext().applicationContext.packageName
        val pacMan = host.getContext().packageManager
        val allMatches = pacMan.queryIntentActivities(intent, 0)
        for (match in allMatches) {
            if (match.activityInfo != null && (match.activityInfo.applicationInfo.packageName != ignoredPackage) && !isFileManagerAdded(
                    matches,
                    match
                )
            ) {
                val eb = ExternalBrowserInfo()
                eb.resolveInfo = match
                eb.action = intent.action
                eb.mime = if (intent.type == null) "" else intent.type
                eb.label = match.loadLabel(pacMan).toString()
                if (intent.data != null && ContentResolver.SCHEME_CONTENT == intent.data!!
                        .scheme
                ) eb.label += " (content provider browser)"
                matches.add(eb)
            }
        }
    }

    private fun isFileManagerAdded(matches: List<ExternalBrowserInfo>, m: ResolveInfo): Boolean {
        for (match in matches) {
            if (match.resolveInfo!!.activityInfo.packageName == m.activityInfo.packageName && match.resolveInfo!!.activityInfo.name == m.activityInfo.name) return true
        }
        return false
    }

    companion object {
        fun saveExtInfo(settings: UserSettings, info: ExternalFileManagerInfo?) {
            if (info == null) settings.sharedPreferences.edit()
                .remove(UserSettingsCommon.Companion.EXTERNAL_FILE_MANAGER).commit()
            else {
                try {
                    settings.sharedPreferences.edit()
                        .putString(UserSettingsCommon.Companion.EXTERNAL_FILE_MANAGER, info.save())
                        .commit()
                } catch (e: JSONException) {
                    Logger.log(e)
                }
            }
        }
    }
}
