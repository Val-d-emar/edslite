package com.sovworks.eds.android.helpers

import android.app.Activity
import android.app.DialogFragment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.os.Build
import android.os.Build.VERSION
import android.util.TypedValue
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.Path
import com.sovworks.eds.settings.SettingsCommon
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedInputStream
import java.io.IOException
import kotlin.math.max

open class UtilBase {
    companion object {
        fun setDialogStyle(df: DialogFragment) {
            val typedValue = TypedValue()
            df.activity.theme.resolveAttribute(R.attr.dialogStyle, typedValue, true)
            df.setStyle(DialogFragment.STYLE_NO_TITLE, typedValue.resourceId)
        }

        fun setDialogStyle(act: Activity) {
            val theme = UserSettings.getSettings(act.applicationContext).currentTheme
            act.setTheme(if (theme == SettingsCommon.THEME_DARK) R.style.Dialog_Dark else R.style.Dialog)
        }

        val systemInfoString: String
            get() = String.format(
                "Build.BOARD: %s\n",
                Build.BOARD
            ) + String.format(
                "Build.BOOTLOADER: %s\n",
                Build.BOOTLOADER
            ) + String.format(
                "Build.BRAND: %s\n",
                Build.BRAND
            ) + String.format(
                "Build.CPU_ABI: %s\n",
                Build.CPU_ABI
            ) + String.format("Build.CPU_ABI2: %s\n", Build.CPU_ABI2) + String.format(
                "Build.DEVICE: %s\n",
                Build.DEVICE
            ) + String.format(
                "Build.DISPLAY: %s\n",
                Build.DISPLAY
            ) + String.format("Build.HARDWARE: %s\n", Build.HARDWARE) + String.format(
                "Build.ID: %s\n",
                Build.ID
            ) + String.format(
                "Build.MODEL: %s\n",
                Build.MODEL
            ) + String.format(
                "Build.MANUFACTURER: %s\n",
                Build.MANUFACTURER
            ) + String.format(
                "Build.PRODUCT: %s\n",
                Build.PRODUCT
            ) + String.format(
                "Build.TAGS: %s\n",
                Build.TAGS
            ) + String.format(
                "Build.TYPE: %s\n",
                Build.TYPE
            ) + String.format(
                "Build.VERSION.RELEASE: %s\n",
                VERSION.RELEASE
            ) + String.format(
                "os.name: %s\n",
                System.getProperty("os.name")
            ) + String.format(
                "os.arch: %s\n",
                System.getProperty("os.arch")
            ) + String.format("os.version: %s\n", System.getProperty("os.version"))

        fun storeElementsToString(elements: Collection<*>?): String {
            val ja = JSONArray(elements)
            return ja.toString()
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun loadStringArrayFromString(s: String?): List<String> {
            val res = ArrayList<String>()
            if (s != null && s.length > 0) {
                val ja = JSONArray(s)
                for (i in 0..<ja.length()) res.add(ja.getString(i))
            }
            return res
        }

        @Throws(IOException::class)
        fun loadDownsampledImage(path: Path, reqWidth: Int, reqHeight: Int): Bitmap? {
            val options = Options()
            options.inJustDecodeBounds = true
            var data = BufferedInputStream(path.file.inputStream)
            try {
                BitmapFactory.decodeStream(data, null, options)
            } finally {
                data.close()
            }
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            data = BufferedInputStream(path.file.inputStream)
            try {
                return BitmapFactory.decodeStream(data, null, options)
            } finally {
                data.close()
            }
        }

        private fun calculateInSampleSize(options: Options, reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                inSampleSize = max(
                    Math.round(height.toFloat() / reqHeight.toFloat()).toDouble(),
                    Math.round(width.toFloat() / reqWidth.toFloat()).toDouble()
                ).toInt()
            }
            return inSampleSize
        }
    }
}

