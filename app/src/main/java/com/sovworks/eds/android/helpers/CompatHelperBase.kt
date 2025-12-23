package com.sovworks.eds.android.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.text.ClipboardManager
import android.view.WindowManager.LayoutParams
import com.sovworks.eds.android.R
import com.sovworks.eds.fs.Path
import java.io.IOException

@SuppressLint("NewApi")
open class CompatHelperBase {
    companion object {
        @JvmStatic
		fun setWindowFlagSecure(act: Activity) {
            if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) act.window.setFlags(
                LayoutParams.FLAG_SECURE,
                LayoutParams.FLAG_SECURE
            )
        }

        fun restartActivity(activity: Activity) {
            if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) activity.recreate()
            else {
                val intent = activity.intent
                activity.overridePendingTransition(0, 0)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.finish()
                activity.overridePendingTransition(0, 0)
                activity.startActivity(intent)
            }
        }

        @JvmStatic
		fun storeTextInClipboard(context: Context, text: String?) {
            if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB) {
                @Suppress("deprecation") val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.text = text
            } else {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = ClipData.newPlainText("Text", text)
                clipboard.setPrimaryClip(clip)
            }
        }

        @Throws(IOException::class)
        fun loadBitmapRegion(path: Path, sampleSize: Int, regionRect: Rect?): Bitmap? {
            val options = Options()
            options.inSampleSize = sampleSize
            val data = path.file.inputStream
            try {
                return if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD_MR1) {
                    val decoder = BitmapRegionDecoder.newInstance(data, true)
                    try {
                        decoder!!.decodeRegion(regionRect, options)
                    } finally {
                        decoder!!.recycle()
                    }
                } else BitmapFactory.decodeStream(data, null, options)
            } finally {
                data.close()
            }
        }

        private var serviceRunningNotificationsChannelId: String? = null

        @JvmStatic
		@Synchronized
        fun getServiceRunningNotificationsChannelId(context: Context): String? {
            if (serviceRunningNotificationsChannelId == null) {
                serviceRunningNotificationsChannelId = "com.sovworks.eds.SERVICE_RUNNING_CHANNEL2"
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        serviceRunningNotificationsChannelId,
                        context.getString(R.string.service_notifications_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                    channel.enableLights(false)
                    channel.enableVibration(false)
                    val notificationManager = context.getSystemService(
                        NotificationManager::class.java
                    )
                    notificationManager.createNotificationChannel(channel)
                }
            }
            return serviceRunningNotificationsChannelId
        }

        private var fileOperationsNotificationsChannelId: String? = null

        @JvmStatic
		@Synchronized
        fun getFileOperationsNotificationsChannelId(context: Context): String? {
            if (fileOperationsNotificationsChannelId == null) {
                fileOperationsNotificationsChannelId = "com.sovworks.eds.FILE_OPERATIONS_CHANNEL2"
                if (VERSION.SDK_INT >= VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        fileOperationsNotificationsChannelId,
                        context.getString(R.string.file_operations_notifications_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                    channel.enableLights(false)
                    channel.enableVibration(false)
                    val notificationManager = context.getSystemService(
                        NotificationManager::class.java
                    )
                    notificationManager.createNotificationChannel(channel)
                }
            }
            return fileOperationsNotificationsChannelId
        }
    }
}
