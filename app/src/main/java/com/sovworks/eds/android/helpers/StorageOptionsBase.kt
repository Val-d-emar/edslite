package com.sovworks.eds.android.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import com.sovworks.eds.android.Logger
import com.sovworks.eds.android.R
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.fs.util.StringPathUtil
import com.sovworks.eds.fs.util.Util
import com.sovworks.eds.settings.Settings
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.regex.Pattern

@SuppressLint("NewApi")
abstract class StorageOptionsBase
internal constructor(val context: Context) {
    class StorageInfo {
        @JvmField
        var label: String? = null
        @JvmField
        var path: String? = null
        var dev: String? = null
        var type: String? = null
        var flags: Array<String?>
        @JvmField
        var isExternal: Boolean = false
        var isReadOnly: Boolean = false
    }

    private fun buildStoragesList(): List<StorageInfo> {
        val res = ArrayList<StorageInfo>()
        var extStoragesCounter = 1
        val si =
            defaultStorage
        if (si != null) {
            res.add(si)
            if (si.isExternal) extStoragesCounter++
        }
        addFromMountsFile(res, extStoragesCounter)
        return res
    }

    fun readAllMounts(): List<StorageInfo> {
        return parseMountsFile(readMountsFile())
    }

    @get:SuppressLint("ObsoleteSdkInt")
    private val defaultStorage: StorageInfo?
        get() {
            val defPathState = Environment.getExternalStorageState()
            if (Environment.MEDIA_MOUNTED == defPathState || Environment.MEDIA_MOUNTED_READ_ONLY == defPathState) {
                val info =
                    StorageInfo()
                if (VERSION.SDK_INT < VERSION_CODES.GINGERBREAD ||
                    (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB && !Environment.isExternalStorageRemovable()) ||
                    (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB && (!Environment.isExternalStorageRemovable() || Environment.isExternalStorageEmulated()))
                ) info.label = context.getString(R.string.built_in_memory_card)
                else {
                    info.isExternal = true
                    info.label = context.getString(R.string.external_storage) + " 1"
                }
                info.path = Environment.getExternalStorageDirectory().path
                return info
            }
            return null
        }

    protected fun readMountsFile(): String {
        return readMounts()
    }

    private fun addFromMountsFile(storages: MutableCollection<StorageInfo>, extCounter: Int): Int {
        var extCounter = extCounter
        val mounts = parseMountsFile(readMountsFile())
        if (mounts.isEmpty()) return extCounter
        val settings: Settings = UserSettings.getSettings(context)
        for (si in mounts) {
            if (si.type == "vfat" || si.path!!.startsWith("/mnt/") || si.path!!.startsWith("/storage/")) {
                if (isStorageAdded(storages, si.dev, si.path!!)) continue
                if ((si.dev!!.startsWith("/dev/block/vold/") &&
                            (!si.path!!.startsWith("/mnt/secure") && !si.path!!.startsWith("/mnt/asec") && !si.path!!.startsWith(
                                "/mnt/obb"
                            ) && !si.dev!!.startsWith("/dev/mapper") && (si.type != "tmpfs"))
                            ) || ((si.dev!!.startsWith("/dev/fuse") || si.dev!!.startsWith("/mnt/media")) && si.path!!.startsWith(
                        "/storage/"
                    ) && !si.path!!.startsWith("/storage/emulated")
                            )
                ) {
                    si.label = context.getString(R.string.external_storage) + " " + extCounter
                    if (checkMountPoint(settings, si)) {
                        storages.add(si)
                        extCounter++
                    }
                }
            }
        }
        return extCounter
    }

    fun parseMountsFile(mountsStr: String?): ArrayList<StorageInfo> {
        val res = ArrayList<StorageInfo>()
        if (mountsStr == null || mountsStr.isEmpty()) return res
        val p = Pattern.compile(
            "^([^\\s]+)\\s+([^\\s+]+)\\s+([^\\s+]+)\\s+([^\\s+]+).*?$",
            Pattern.MULTILINE
        )
        val m = p.matcher(mountsStr)
        while (m.find()) {
            val dev = m.group(1)
            val mountPath = m.group(2)
            val type = m.group(3)
            val flags: Array<String?> =
                m.group(4).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val si = StorageInfo()
            si.path = mountPath
            si.dev = dev
            si.type = type
            si.flags = flags
            si.isExternal = true
            si.isReadOnly = Arrays.asList(*flags).contains("ro")
            res.add(si)
        }
        return res
    }

    protected fun checkMountPoint(s: Settings?, si: StorageInfo): Boolean {
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) return true
        val f = File(si.path)
        return f.isDirectory && !si.path!!.startsWith("/mnt/media_rw")
    }

    companion object {
        private fun readMounts(): String {
            try {
                return readMountsStd()
            } catch (e: IOException) {
                Logger.log(e)
            }
            return ""
        }

        @JvmStatic
        @Synchronized
        fun getStoragesList(context: Context): List<StorageInfo>? {
            if (_storagesList == null) loadStorageList(context)
            return _storagesList
        }

        @JvmStatic
        fun reloadStorageList(context: Context) {
            _storagesList = loadStorageList(context)
        }

        private fun loadStorageList(context: Context): List<StorageInfo> {
            val so: StorageOptionsBase = StorageOptions(context)
            return so.buildStoragesList()
        }

        fun getDefaultDeviceLocation(context: Context): StorageInfo? {
            for (si in getStoragesList(
                context
            )!!) if (!si.isExternal) return si
            return if (!getStoragesList(context)!!.isEmpty()) getStoragesList(
                context
            )!![0] else null
        }

        private var _storagesList: List<StorageInfo>? = null

        private fun isStorageAdded(
            storages: Collection<StorageInfo>,
            devPath: String?,
            mountPath: String
        ): Boolean {
            val dpu = StringPathUtil(devPath)
            val mpu = StringPathUtil(mountPath)
            for (si in storages) {
                val spu = StringPathUtil(si.path)
                if (spu == mpu || spu == dpu) return true
                if (((mountPath.startsWith("/mnt/media_rw/") && si.path!!.startsWith("/storage/")) ||
                            (si.path!!.startsWith("/mnt/media_rw/") && mountPath.startsWith("/storage/"))) &&
                    spu.fileName == mpu.fileName
                ) return true
            }
            return false
        }

        @Throws(IOException::class)
        fun readMountsStd(): String {
            Logger.debug("StorageOptions: trying to get mounts using std fs.")
            val finp = FileInputStream("/proc/mounts")
            val inp: InputStream = BufferedInputStream(finp)
            try {
                return Util.readFromFile(inp)
            } finally {
                inp.close()
            }
        }
    }
}
