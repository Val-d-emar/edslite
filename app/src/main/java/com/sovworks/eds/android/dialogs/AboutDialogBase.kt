package com.sovworks.eds.android.dialogs

import android.app.DialogFragment
import android.app.FragmentManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.TextView
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.activities.VersionHistory
import com.sovworks.eds.android.helpers.Util
import com.sovworks.eds.android.providers.MainContentProvider
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.locations.DeviceBasedLocation
import com.sovworks.eds.locations.Location
import com.sovworks.eds.settings.GlobalConfig
import com.sovworks.eds.util.exec.ExecuteExternalProgram
import java.io.IOException
import java.util.Date
import java.util.Locale

abstract class AboutDialogBase : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.setDialogStyle(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle
    ): View? {
        val v = inflater.inflate(R.layout.about_dialog, container)
        val verName = getVersionName(activity)
        val aboutMessage = String.format(
            "%s v%s\n%s",
            resources.getString(R.string.eds),
            verName,
            resources.getString(R.string.about_message)
        )

        (v.findViewById<View>(R.id.about_text_view) as TextView).text = aboutMessage
        v.findViewById<View>(R.id.show_version_history_button).setOnClickListener {
            startActivity(
                Intent(
                    activity,
                    VersionHistory::class.java
                )
            )
        }
        v.findViewById<View>(R.id.contact_support_button).setOnClickListener {
            try {
                sendSupportRequest()
            } catch (e: Throwable) {
                Logger.showAndLog(activity, e)
            }
        }
        v.findViewById<View>(R.id.get_program_log).setOnClickListener {
            try {
                saveDebugLog()
            } catch (e: Throwable) {
                Logger.showAndLog(activity, e)
            }
        }
        return v
    }

    override fun onResume() {
        super.onResume()
        setWidthHeight()
    }

    protected fun setWidthHeight() {
        val w = dialog.window
        w?.setLayout(calcWidth(), calcHeight())
    }

    protected fun calcWidth(): Int {
        return resources.getDimensionPixelSize(R.dimen.about_dialog_width)
    }

    protected fun calcHeight(): Int {
        return LayoutParams.WRAP_CONTENT
        //return getResources().getDimensionPixelSize(R.dimen.about_dialog_heigh);
    }

    protected val subjectString: String
        get() = "EDS support"

    private fun sendSupportRequest() {
        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.setType("plain/text")
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(GlobalConfig.SUPPORT_EMAIL))
        val subj = subjectString
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subj)
        emailIntent.putExtra(Intent.EXTRA_TEXT, Util.getSystemInfoString())
        startActivity(Intent.createChooser(emailIntent, "Send mail"))
    }

    @Throws(IOException::class, ApplicationException::class)
    private fun saveDebugLog() {
        val ctx: Context = activity
        var out = ctx.getExternalFilesDir(null)
        if (out == null || !out.canWrite()) out = ctx.filesDir
        val loc: Location = DeviceBasedLocation(
            UserSettings.getSettings(ctx),
            StringPathUtil(out!!.path).combine(
                String.format(
                    Locale.US,
                    "eds-log-%1\$tY%1\$tm%1\$td%1\$tH%1\$tM%1\$tS.txt",
                    Date()
                )
            ).toString()
        )
        dumpLog(loc)
        sendLogFile(loc)
    }

    @Throws(IOException::class, ApplicationException::class)
    protected fun dumpLog(logLocation: Location) {
        ExecuteExternalProgram.executeAndReadString(
            "logcat",
            "-df",
            logLocation.currentPath.pathString
        )
    }

    private fun sendLogFile(logLocation: Location) {
        val ctx: Context = activity
        val uri = MainContentProvider.getContentUriFromLocation(logLocation)
        val actionIntent = Intent(Intent.ACTION_SEND)
        actionIntent.setType("text/plain")
        actionIntent.putExtra(Intent.EXTRA_STREAM, uri)
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
            val cp = ClipData.newUri(
                ctx.contentResolver,
                ctx.getString(R.string.get_program_log),
                uri
            )
            actionIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            actionIntent.clipData = cp
        }

        val startIntent = Intent.createChooser(
            actionIntent,
            ctx.getString(R.string.save_log_file_to)
        )
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(startIntent)
    }

    companion object {
        @JvmStatic
		fun showDialog(fm: FragmentManager?) {
            val newFragment: DialogFragment = AboutDialog()
            newFragment.show(fm, "AboutDialog")
        }

        fun getVersionName(context: Context): String? {
            try {
                return context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: NameNotFoundException) {
                Logger.log(e)
                return ""
            }
        }
    }
}
